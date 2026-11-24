package br.com.creditorotativo.infrastructure.persistence;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.application.IdempotencyConflictException;
import br.com.creditorotativo.application.IdempotencyStore;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.rule.Fixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryIdempotencyStoreTest {

    private static List<CreditEvaluation> newEvaluation() {
        Customer customer = Fixtures.healthyCustomer();
        return List.of(CreditEvaluation.of(customer, Fixtures.engine().evaluate(customer)));
    }

    @Test
    @DisplayName("primeira chamada cria; a segunda com a mesma chave devolve o mesmo resultado sem criar")
    void secondCallReplays() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(100);
        AtomicInteger created = new AtomicInteger();

        IdempotencyStore.Result first = store.execute("k1", "fp", () -> {
            created.incrementAndGet();
            return newEvaluation();
        });
        IdempotencyStore.Result second = store.execute("k1", "fp", () -> {
            created.incrementAndGet();
            return newEvaluation();
        });

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.evaluations()).isEqualTo(first.evaluations());
        assertThat(created.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("mesma chave com conteúdo diferente é conflito")
    void sameKeyDifferentFingerprintConflicts() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(100);
        store.execute("k1", "fp-A", InMemoryIdempotencyStoreTest::newEvaluation);

        assertThatThrownBy(() -> store.execute("k1", "fp-B", InMemoryIdempotencyStoreTest::newEvaluation))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    @DisplayName("se a criação falhar nada é gravado e a mesma chave pode ser repetida")
    void failureIsNotStored() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(100);

        assertThatThrownBy(() -> store.execute("k1", "fp", () -> {
            throw new IllegalStateException("falha");
        })).isInstanceOf(IllegalStateException.class);

        IdempotencyStore.Result retry = store.execute("k1", "fp", InMemoryIdempotencyStoreTest::newEvaluation);
        assertThat(retry.replayed()).isFalse();
    }

    @Test
    @DisplayName("CONCORRÊNCIA: 50 requisições simultâneas com a mesma chave criam apenas UMA avaliação")
    void concurrentRequestsWithSameKeyCreateOnce() throws Exception {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(100);
        int threads = 50;
        AtomicInteger created = new AtomicInteger();
        CountDownLatch startTogether = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);

        List<Future<IdempotencyStore.Result>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                startTogether.await();
                return store.execute("same-key", "fp", () -> {
                    created.incrementAndGet();
                    return newEvaluation();
                });
            }));
        }
        startTogether.countDown();

        List<IdempotencyStore.Result> results = new ArrayList<>();
        for (Future<IdempotencyStore.Result> f : futures) {
            results.add(f.get(10, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(created.get()).as("quantas vezes a avaliação foi criada").isEqualTo(1);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().map(r -> r.evaluations().getFirst().id()).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("acima do limite descarta a chave mais antiga (limitação documentada)")
    void evictsOldestKeyBeyondLimit() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        store.execute("a", "fp", InMemoryIdempotencyStoreTest::newEvaluation);
        store.execute("b", "fp", InMemoryIdempotencyStoreTest::newEvaluation);
        store.execute("c", "fp", InMemoryIdempotencyStoreTest::newEvaluation); // descarta "a"

        assertThat(store.execute("b", "fp", InMemoryIdempotencyStoreTest::newEvaluation).replayed()).isTrue();
        assertThat(store.execute("a", "fp", InMemoryIdempotencyStoreTest::newEvaluation).replayed()).isFalse();
    }
}
