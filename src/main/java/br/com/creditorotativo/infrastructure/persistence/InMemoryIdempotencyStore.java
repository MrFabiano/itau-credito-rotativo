package br.com.creditorotativo.infrastructure.persistence;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.application.IdempotencyConflictException;
import br.com.creditorotativo.application.IdempotencyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Idempotência em memória. O método é {@code synchronized}: "verificar se a chave existe" e "criar" acontecem
 * numa única seção crítica, então duas requisições simultâneas com a mesma chave nunca criam duas avaliações
 * (sem a janela de race condition do check-then-act). O trecho protegido é curto, pois a decisão é em memória.
 *
 * <p>Limitações assumidas (ver ADR-006): vale só para esta instância e some ao reiniciar; o limite de entradas
 * descarta as chaves mais antigas, e um retry após o descarte criaria uma nova avaliação; não há TTL.
 * Em produção, a garantia é uma constraint única no banco compartilhado entre as réplicas.
 */
@Component
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private record Entry(String fingerprint, List<CreditEvaluation> evaluations) {
    }

    private final Map<String, Entry> entries;

    public InMemoryIdempotencyStore(@Value("${credit.idempotency.max-entries:10000}") int maxEntries) {
        this.entries = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public synchronized Result execute(String key, String fingerprint, Supplier<List<CreditEvaluation>> creator) {
        Entry existing = entries.get(key);
        if (existing != null) {
            if (!existing.fingerprint().equals(fingerprint)) {
                throw new IdempotencyConflictException();
            }
            return new Result(existing.evaluations(), true);
        }
        // Se o creator lançar exceção, nada é gravado: o cliente pode repetir a mesma chave.
        List<CreditEvaluation> created = List.copyOf(creator.get());
        entries.put(key, new Entry(fingerprint, created));
        return new Result(created, false);
    }
}
