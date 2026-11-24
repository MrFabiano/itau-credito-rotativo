package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static br.com.creditorotativo.domain.rule.Fixtures.customer;
import static br.com.creditorotativo.domain.rule.Fixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;

class LimitPoliciesTest {

    private final CreditPolicy policy = policy();

    @ParameterizedTest(name = "score {0} libera {1} de 10000.00")
    @CsvSource({"900,5000.00", "800,5000.00", "799,3500.00", "700,3500.00", "699,2000.00", "600,2000.00", "599,1000.00", "0,1000.00"})
    @DisplayName("ScoreBand: faixa define a fração do limite disponível")
    void scoreBand(int score, String expected) {
        Customer c = customer(score, "0.00", "10000.00", 0, "1000.00");
        Money result = new ScoreBandLimitPolicy(policy).apply(c, c.availableLimit());
        assertThat(result).isEqualTo(Money.of(expected));
    }

    @Test
    @DisplayName("ScoreBand: sem faixa correspondente libera zero (fail-closed)")
    void scoreBandWithoutMatchingBandIsZero() {
        CreditPolicy noBands = new CreditPolicy("v", 0, 0, policy.maxUtilization(), 0, policy.maxSpendingSpikeRatio(),
                policy.minAvailableLimit(), policy.spendingMultiplier(), policy.latePenaltyPerOccurrence(),
                policy.ceiling(), java.util.List.of());
        Customer c = customer(900, "0.00", "10000.00", 0, "1000.00");
        assertThat(new ScoreBandLimitPolicy(noBands).apply(c, c.availableLimit())).isEqualTo(Money.ZERO);
    }

    @Test
    @DisplayName("SpendingCap: limita a 1,5x a média de gastos")
    void spendingCap() {
        Customer c = customer(700, "0.00", "10000.00", 0, "1000.00", "1000.00", "1000.00");
        assertThat(new SpendingCapLimitPolicy(policy).apply(c, c.availableLimit())).isEqualTo(Money.of("1500.00"));
    }

    @ParameterizedTest(name = "{0} atrasos -> {1}")
    @CsvSource({"0,1000.00", "1,800.00", "2,600.00", "5,0.00", "9,0.00"})
    @DisplayName("LatePaymentPenalty: -20% por atraso, nunca abaixo de zero")
    void latePenalty(int late, String expected) {
        Customer c = customer(700, "0.00", "1000.00", late, "1000.00");
        assertThat(new LatePaymentPenaltyLimitPolicy(policy).apply(c, Money.of("1000.00"))).isEqualTo(Money.of(expected));
    }

    @Test
    @DisplayName("PolicyCeiling: teto absoluto")
    void ceiling() {
        Customer c = customer(900, "0.00", "90000.00", 0, "1000.00");
        assertThat(new PolicyCeilingLimitPolicy(policy).apply(c, Money.of("40000.00"))).isEqualTo(Money.of("15000.00"));
        assertThat(new PolicyCeilingLimitPolicy(policy).apply(c, Money.of("100.00"))).isEqualTo(Money.of("100.00"));
    }
}
