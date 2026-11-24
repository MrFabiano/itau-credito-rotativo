package br.com.creditorotativo.service;

import br.com.creditorotativo.application.*;
import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.rule.RuleEngine;
import br.com.creditorotativo.repository.EvaluationRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Service
public class EvaluateCreditService {

    /** Formato aceito: evita chaves gigantes ou com caracteres de controle (e injeção em logs). */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final RuleEngine engine;
    private final EvaluationObserver observer;
    private final EvaluationRepository repository;
    private final IdempotencyStore idempotencyStore;
    private final EvaluationEventPublisher eventPublisher;

    public EvaluateCreditService(RuleEngine engine, EvaluationObserver observer,
                                 EvaluationRepository repository, IdempotencyStore idempotencyStore,
                                 EvaluationEventPublisher eventPublisher) {
        this.engine = engine;
        this.observer = observer;
        this.repository = repository;
        this.idempotencyStore = idempotencyStore;
        this.eventPublisher = eventPublisher;
    }

    public CreditEvaluation evaluate(Customer customer) {
        long start = System.nanoTime();
        Decision decision = engine.evaluate(customer);
        // A latência medida é a da decisão; guardar o resultado não entra na conta do motor.
        observer.evaluated(decision, Duration.ofNanos(System.nanoTime() - start));

        CreditEvaluation evaluation = CreditEvaluation.of(customer, decision);
        repository.save(evaluation);
        publishEvent(customer, evaluation);
        return evaluation;
    }

    /**
     * Um evento por avaliação realmente criada (um replay idempotente não chega aqui, então não gera evento duplicado).
     * Falha ao publicar nunca derruba nem altera a decisão: é só contabilizada. Com outbox transacional (produção)
     * esse risco desaparece, pois o evento é gravado junto com a avaliação.
     */
    private void publishEvent(Customer customer, CreditEvaluation evaluation) {
        try {
            eventPublisher.publish(CreditEvaluatedEvent.of(customer, evaluation));
        } catch (RuntimeException e) {
            observer.eventPublicationFailed();
        }
    }

    /** Avalia o lote preservando a ordem de entrada. Cada cliente é independente (sem estado compartilhado). */
    public CreditReport evaluateBatch(List<Customer> customers) {
        return new CreditReport(customers.stream().map(c -> evaluate(c)).toList());
    }

    /**
     * Avaliação individual idempotente. Sem chave ({@code null}) o comportamento é o de sempre: cada chamada
     * cria uma nova avaliação. Com chave, repetir a mesma requisição devolve a avaliação original.
     */
    public Outcome<CreditEvaluation> evaluate(Customer customer, String idempotencyKey) {
        if (idempotencyKey == null) {
            return new Outcome<>(evaluate(customer), false);
        }
        IdempotencyStore.Result result = runIdempotent(
                idempotencyKey, RequestFingerprint.of("evaluate", List.of(customer)),
                () -> List.of(evaluate(customer)));
        return new Outcome<>(result.evaluations().getFirst(), result.replayed());
    }

    /** Lote idempotente: um retry do lote inteiro não duplica nenhuma avaliação nem o total do relatório. */
    public Outcome<CreditReport> evaluateBatch(List<Customer> customers, String idempotencyKey) {
        if (idempotencyKey == null) {
            return new Outcome<>(evaluateBatch(customers), false);
        }
        IdempotencyStore.Result result = runIdempotent(
                idempotencyKey, RequestFingerprint.of("batch", customers),
                () -> evaluateBatch(customers).evaluations());
        return new Outcome<>(new CreditReport(result.evaluations()), result.replayed());
    }

    private IdempotencyStore.Result runIdempotent(String key, String fingerprint,
                                                  Supplier<List<CreditEvaluation>> creator) {
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            // Não ecoa o valor recebido na mensagem.
            throw new IllegalArgumentException(
                    "Idempotency-Key inválida: use de 1 a 128 caracteres entre letras, números, '.', '_', ':' e '-'");
        }
        IdempotencyStore.Result result = idempotencyStore.execute(key, fingerprint, creator);
        if (result.replayed()) {
            observer.replayed();
        }
        return result;
    }
}
