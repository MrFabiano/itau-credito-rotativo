package br.com.creditorotativo.aplication;

import br.com.creditorotativo.application.*;
import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.rule.Fixtures;
import br.com.creditorotativo.infrastructure.persistence.InMemoryEvaluationRepository;
import br.com.creditorotativo.infrastructure.persistence.InMemoryIdempotencyStore;
import br.com.creditorotativo.service.EvaluateCreditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Publicação de eventos de domínio: um por avaliação, sem dado pessoal, sem afetar a decisão. */
class EvaluateCreditUseCaseEventsTest {

    private final List<CreditEvaluatedEvent> published = new ArrayList<>();
    private final AtomicInteger publishFailures = new AtomicInteger();

    private InMemoryEvaluationRepository repository;
    private EvaluateCreditService evaluateCreditService;

    @BeforeEach
    void setUp() {
        repository = new InMemoryEvaluationRepository(1000);
        evaluateCreditService = build(published::add);
    }

    private EvaluateCreditService build(EvaluationEventPublisher publisher) {
        EvaluationObserver observer = new EvaluationObserver() {
            @Override
            public void evaluated(Decision decision, Duration elapsed) {
            }

            @Override
            public void eventPublicationFailed() {
                publishFailures.incrementAndGet();
            }
        };
        return new EvaluateCreditService(Fixtures.engine(), observer, repository, new InMemoryIdempotencyStore(1000), publisher);
    }

    @Test
    @DisplayName("aprovação publica um evento com id, decisão, valor e versão das regras")
    void approvalPublishesEvent() {
        CreditEvaluation evaluation = evaluateCreditService.evaluate(Fixtures.healthyCustomer());

        assertThat(published).hasSize(1);
        CreditEvaluatedEvent event = published.getFirst();
        assertThat(event.evaluationId()).isEqualTo(evaluation.id());
        assertThat(event.decision()).isEqualTo("APPROVED");
        assertThat(event.approvedLimit()).isEqualByComparingTo(new BigDecimal("4000.00"));
        assertThat(event.rejectedBy()).isEmpty();
        assertThat(event.ruleSetVersion()).isEqualTo("test-v1");
    }

    @Test
    @DisplayName("reprovação publica as regras que barraram, com limite zero")
    void rejectionPublishesRules() {
        evaluateCreditService.evaluate(Fixtures.customer(300, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00"));

        assertThat(published.getFirst().decision()).isEqualTo("REJECTED");
        assertThat(published.getFirst().approvedLimit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(published.getFirst().rejectedBy()).containsExactly("MinimumScore");
    }

    @Test
    @DisplayName("LGPD: o evento não carrega nome nem CPF, e a chave do cliente é um hash")
    void eventHasNoPersonalData() {
        Customer customer = Fixtures.healthyCustomer();
        evaluateCreditService.evaluate(customer);

        CreditEvaluatedEvent event = published.getFirst();
        assertThat(event.toString()).doesNotContain(customer.name()).doesNotContain(customer.cpf().digits());
        assertThat(event.customerKey()).hasSize(64).doesNotContain(customer.cpf().digits());
    }

    @Test
    @DisplayName("o mesmo cliente gera sempre a mesma chave (mantém a ordem por cliente na partição)")
    void sameCustomerSameKey() {
        evaluateCreditService.evaluate(Fixtures.healthyCustomer());
        evaluateCreditService.evaluate(Fixtures.healthyCustomer());

        assertThat(published.get(0).customerKey()).isEqualTo(published.get(1).customerKey());
        assertThat(published.get(0).evaluationId()).isNotEqualTo(published.get(1).evaluationId());
    }

    @Test
    @DisplayName("replay idempotente NÃO publica evento duplicado")
    void replayDoesNotPublishAgain() {
        evaluateCreditService.evaluate(Fixtures.healthyCustomer(), "key-1");
        evaluateCreditService.evaluate(Fixtures.healthyCustomer(), "key-1");

        assertThat(published).hasSize(1);
    }

    @Test
    @DisplayName("lote publica um evento por cliente, e o retry do lote não republica")
    void batchPublishesOncePerCustomer() {
        List<Customer> batch = List.of(
                Fixtures.healthyCustomer(),
                Fixtures.customer(300, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00"));

        evaluateCreditService.evaluateBatch(batch, "batch-1");
        evaluateCreditService.evaluateBatch(batch, "batch-1");

        assertThat(published).hasSize(2);
    }

    @Test
    @DisplayName("falha ao publicar não derruba nem altera a decisão, e é contabilizada")
    void publishFailureDoesNotBreakDecision() {
        EvaluateCreditService failing = build(event -> {
            throw new IllegalStateException("broker fora do ar");
        });

        CreditEvaluation evaluation = failing.evaluate(Fixtures.healthyCustomer());

        assertThat(evaluation.decision().isApproved()).isTrue();
        assertThat(repository.findById(evaluation.id())).isPresent();
        assertThat(publishFailures.get()).isEqualTo(1);
    }
}
