package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Cpf;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Dados e montagem do motor compartilhados pelos testes (espelha os valores do application.yml). */
public final class Fixtures {

    public static final String VALID_CPF = "12345678909";

    private Fixtures() {
    }

    public static CreditPolicy policy() {
        return new CreditPolicy(
                "test-v1", 500, 2, new BigDecimal("0.85"), 3, new BigDecimal("2.0"),
                new BigDecimal("500.00"), new BigDecimal("1.5"), new BigDecimal("0.20"),
                new BigDecimal("15000.00"),
                List.of(
                        new CreditPolicy.ScoreBand(800, new BigDecimal("0.50")),
                        new CreditPolicy.ScoreBand(700, new BigDecimal("0.35")),
                        new CreditPolicy.ScoreBand(600, new BigDecimal("0.20")),
                        new CreditPolicy.ScoreBand(0, new BigDecimal("0.10"))));
    }

    public static List<EligibilityRule> rules(CreditPolicy policy) {
        return List.of(
                new MinimumScoreRule(policy),
                new MaxLatePaymentsRule(policy),
                new InvoiceUtilizationRule(policy),
                new SpendingBehaviorRule(policy),
                new MinimumAvailableLimitRule(policy));
    }

    public static List<LimitPolicy> limitPolicies(CreditPolicy policy) {
        return List.of(
                new ScoreBandLimitPolicy(policy),
                new SpendingCapLimitPolicy(policy),
                new LatePaymentPenaltyLimitPolicy(policy),
                new PolicyCeilingLimitPolicy(policy));
    }

    public static RuleEngine engine() {
        CreditPolicy policy = policy();
        return new RuleEngine(rules(policy), limitPolicies(policy), policy.version());
    }

    public static Customer customer(int score, String invoice, String available, int late, String... spending) {
        return new Customer(
                "Cliente Teste",
                Cpf.of(VALID_CPF),
                score,
                Money.of(invoice),
                Money.of(available),
                late,
                Arrays.stream(spending).map(Money::of).toList());
    }

    /** Cliente saudável: aprovado por todas as regras. */
    public static Customer healthyCustomer() {
        return customer(820, "1200.00", "8000.00", 0, "2800.00", "3000.00", "3100.00", "3200.00");
    }
}
