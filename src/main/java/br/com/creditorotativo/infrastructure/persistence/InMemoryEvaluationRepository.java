package br.com.creditorotativo.infrastructure.persistence;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.repository.EvaluationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Armazenamento em memória, limitado em tamanho (descarta as mais antigas) para não crescer sem controle.
 * Suficiente para o case; em produção seria PostgreSQL (ver ADR-004/005).
 * Limitações assumidas: dados somem ao reiniciar e não são compartilhados entre réplicas.
 */
@Component
public class InMemoryEvaluationRepository implements EvaluationRepository {

    private final Map<UUID, CreditEvaluation> store;

    public InMemoryEvaluationRepository(@Value("${credit.store.max-entries:10000}") int maxEntries) {
        this.store = new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, CreditEvaluation> eldest) {
                return size() > maxEntries;
            }
        };
    }

    @Override
    public synchronized void save(CreditEvaluation evaluation) {
        store.put(evaluation.id(), evaluation);
    }

    @Override
    public synchronized Optional<CreditEvaluation> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public synchronized List<CreditEvaluation> findAllNewestFirst() {
        List<CreditEvaluation> all = new ArrayList<>(store.values()); // ordem de inserção = mais antiga primeiro
        Collections.reverse(all);
        return all;
    }
}
