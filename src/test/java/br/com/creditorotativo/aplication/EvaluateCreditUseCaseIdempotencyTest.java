package br.com.creditorotativo.aplication;

import br.com.creditorotativo.application.*;
import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.rule.Fixtures;
import br.com.creditorotativo.application.IdempotencyConflictException;
import br.com.creditorotativo.infrastructure.persistence.InMemoryEvaluationRepository;
import br.com.creditorotativo.infrastructure.persistence.InMemoryIdempotencyStore;
import br.com.creditorotativo.service.EvaluateCreditService;
import br.com.creditorotativo.service.QueryCreditEvaluationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Regras de idempotência do caso de uso, com o motor real e armazenamento em memória (sem Spring). */
class EvaluateCreditUseCaseIdempotencyTest {

    private final AtomicInteger evaluations = new AtomicInteger();
    private final AtomicInteger replays = new AtomicInteger();

    private EvaluateCreditService evaluateCreditService;
    private QueryCreditEvaluationsService query;

    @BeforeEach
    void setUp() {
        InMemoryEvaluationRepository repository = new InMemoryEvaluationRepository(1000);
        EvaluationObserver observer = new EvaluationObserver() {
            @Override
            public void evaluated(Decision decision, Duration elapsed) {
                evaluations.incrementAndGet();
            }

            @Override
            public void replayed() {
                replays.incrementAndGet();
            }
        };
        evaluateCreditService = new EvaluateCreditService(Fixtures.engine(), observer, repository,
                new InMemoryIdempotencyStore(1000), event -> { });
        query = new QueryCreditEvaluationsService(repository);
    }

    private long stored() {
        return query.search(null, 0, 100).totalElements();
    }

    @Test
    @DisplayName("mesma chave e mesma requisição: devolve a mesma avaliação e não grava outra")
    void replayReturnsOriginal() {
        Customer customer = Fixtures.healthyCustomer();

        Outcome<CreditEvaluation> first = evaluateCreditService.evaluate(customer, "key-1");
        Outcome<CreditEvaluation> second = evaluateCreditService.evaluate(customer, "key-1");

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.value().id()).isEqualTo(first.value().id());
        assertThat(stored()).isEqualTo(1);
        assertThat(evaluations.get()).as("o motor só roda uma vez").isEqualTo(1);
        assertThat(replays.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("sem chave: cada chamada cria uma nova avaliação (comportamento padrão)")
    void withoutKeyEveryCallCreates() {
        Customer customer = Fixtures.healthyCustomer();

        Outcome<CreditEvaluation> first = evaluateCreditService.evaluate(customer, null);
        Outcome<CreditEvaluation> second = evaluateCreditService.evaluate(customer, null);

        assertThat(second.value().id()).isNotEqualTo(first.value().id());
        assertThat(stored()).isEqualTo(2);
    }

    @Test
    @DisplayName("chaves diferentes criam avaliações diferentes")
    void differentKeysCreateDifferentEvaluations() {
        Customer customer = Fixtures.healthyCustomer();

        evaluateCreditService.evaluate(customer, "key-1");
        evaluateCreditService.evaluate(customer, "key-2");

        assertThat(stored()).isEqualTo(2);
    }

    @Test
    @DisplayName("mesma chave com requisição diferente é conflito e não grava nada")
    void sameKeyDifferentRequestConflicts() {
        evaluateCreditService.evaluate(Fixtures.healthyCustomer(), "key-1");
        Customer other = Fixtures.customer(700, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00");

        assertThatThrownBy(() -> evaluateCreditService.evaluate(other, "key-1")).isInstanceOf(IdempotencyConflictException.class);
        assertThat(stored()).isEqualTo(1);
    }

    @Test
    @DisplayName("a mesma chave não pode ser reaproveitada entre o endpoint individual e o de lote")
    void keyCannotBeReusedAcrossOperations() {
        Customer customer = Fixtures.healthyCustomer();
        evaluateCreditService.evaluate(customer, "key-1");

        assertThatThrownBy(() -> evaluateCreditService.evaluateBatch(List.of(customer), "key-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("lote: retry do lote inteiro devolve as mesmas avaliações e não duplica o relatório")
    void batchReplayDoesNotDuplicateReport() {
        List<Customer> batch = List.of(
                Fixtures.healthyCustomer(),
                Fixtures.customer(300, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00"));

        Outcome<CreditReport> first = evaluateCreditService.evaluateBatch(batch, "batch-1");
        Outcome<CreditReport> second = evaluateCreditService.evaluateBatch(batch, "batch-1");

        assertThat(second.replayed()).isTrue();
        assertThat(second.value().evaluations()).isEqualTo(first.value().evaluations());
        assertThat(second.value().totalApprovedAmount()).isEqualTo(first.value().totalApprovedAmount());
        assertThat(stored()).as("relatório global continua com 2, não 4").isEqualTo(2);
    }

    @Test
    @DisplayName("lote com a ordem dos clientes alterada é outra requisição (conflito)")
    void batchOrderMatters() {
        Customer a = Fixtures.healthyCustomer();
        Customer b = Fixtures.customer(300, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00");
        evaluateCreditService.evaluateBatch(List.of(a, b), "batch-1");

        assertThatThrownBy(() -> evaluateCreditService.evaluateBatch(List.of(b, a), "batch-1"))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("chave com formato inválido é rejeitada sem ecoar o valor")
    void invalidKeyIsRejected() {
        Customer customer = Fixtures.healthyCustomer();

        for (String invalid : new String[]{"", " ", "com espaço", "a".repeat(129), "chave\ncom\nquebra", "chave;ruim"}) {
            assertThatThrownBy(() -> evaluateCreditService.evaluate(customer, invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageNotContaining("espaço")
                    .hasMessageNotContaining("quebra");
        }
        assertThat(stored()).isZero();
    }
}
