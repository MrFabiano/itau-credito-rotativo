package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Cpf;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.Money;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.Size;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de propriedade: em vez de exemplos, afirmam invariantes que valem para QUALQUER cliente.
 * São a rede de segurança contra "liberação indevida", o risco central do case.
 */
class RuleEnginePropertiesTest {

    private final RuleEngine engine = Fixtures.engine();
    private final CreditPolicy policy = Fixtures.policy();

    @Property
    void approvedLimitIsAlwaysPositiveAndBounded(
            @ForAll @IntRange(min = 0, max = 1000) int score,
            @ForAll @IntRange(min = 0, max = 50_000) int invoice,
            @ForAll @IntRange(min = 0, max = 100_000) int available,
            @ForAll @IntRange(min = 0, max = 10) int late,
            @ForAll @Size(max = 12) List<@IntRange(min = 0, max = 20_000) Integer> spending) {

        Customer customer = customer(score, invoice, available, late, spending);

        if (engine.evaluate(customer) instanceof Decision.Approved approved) {
            assertThat(approved.limit().isZero()).isFalse();
            assertThat(approved.limit()).isLessThanOrEqualTo(customer.availableLimit());
            assertThat(approved.limit()).isLessThanOrEqualTo(Money.of(policy.ceiling()));
        }
    }

    @Property
    void approvedImpliesEveryEligibilityRulePasses(
            @ForAll @IntRange(min = 0, max = 1000) int score,
            @ForAll @IntRange(min = 0, max = 50_000) int invoice,
            @ForAll @IntRange(min = 0, max = 100_000) int available,
            @ForAll @IntRange(min = 0, max = 10) int late,
            @ForAll @Size(max = 12) List<@IntRange(min = 0, max = 20_000) Integer> spending) {

        Customer customer = customer(score, invoice, available, late, spending);

        if (engine.evaluate(customer).isApproved()) {
            Fixtures.rules(policy).forEach(rule -> assertThat(rule.evaluate(customer).passed())
                    .as("regra %s deveria passar para cliente aprovado", rule.name()).isTrue());
        }
    }

    @Property
    void rejectionAlwaysExplainsWhy(
            @ForAll @IntRange(min = 0, max = 1000) int score,
            @ForAll @IntRange(min = 0, max = 50_000) int invoice,
            @ForAll @IntRange(min = 0, max = 100_000) int available,
            @ForAll @IntRange(min = 0, max = 10) int late,
            @ForAll @Size(max = 12) List<@IntRange(min = 0, max = 20_000) Integer> spending) {

        Customer customer = customer(score, invoice, available, late, spending);

        if (engine.evaluate(customer) instanceof Decision.Rejected rejected) {
            assertThat(rejected.reasons()).isNotEmpty();
        }
    }

    @Property
    void decisionIsDeterministic(
            @ForAll @IntRange(min = 0, max = 1000) int score,
            @ForAll @IntRange(min = 0, max = 50_000) int invoice,
            @ForAll @IntRange(min = 0, max = 100_000) int available,
            @ForAll @IntRange(min = 0, max = 10) int late,
            @ForAll @Size(max = 12) List<@IntRange(min = 0, max = 20_000) Integer> spending) {

        Customer customer = customer(score, invoice, available, late, spending);

        assertThat(engine.evaluate(customer)).isEqualTo(engine.evaluate(customer));
    }

    private static Customer customer(int score, int invoice, int available, int late, List<Integer> spending) {
        return new Customer(
                "Cliente Propriedade",
                Cpf.of(Fixtures.VALID_CPF),
                score,
                Money.of(String.valueOf(invoice)),
                Money.of(String.valueOf(available)),
                late,
                spending.stream().map(v -> Money.of(String.valueOf(v))).toList());
    }
}
