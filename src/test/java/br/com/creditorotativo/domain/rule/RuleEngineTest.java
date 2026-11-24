package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.Money;
import br.com.creditorotativo.domain.model.RuleResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static br.com.creditorotativo.domain.rule.Fixtures.customer;
import static br.com.creditorotativo.domain.rule.Fixtures.engine;
import static org.assertj.core.api.Assertions.assertThat;

/** Cenários de negócio ponta a ponta no domínio. Os valores esperados foram calculados à mão. */
class RuleEngineTest {

    @Test
    @DisplayName("aprova cliente saudável e libera 50% do disponível (faixa 800+)")
    void approvesHealthyCustomer() {
        Decision decision = engine().evaluate(Fixtures.healthyCustomer());

        assertThat(decision).isInstanceOfSatisfying(Decision.Approved.class, approved -> {
            assertThat(approved.limit()).isEqualTo(Money.of("4000.00"));
            assertThat(approved.ruleSetVersion()).isEqualTo("test-v1");
        });
    }

    @Test
    @DisplayName("aplica faixa de score, teto de gastos e penalidade por atraso em cadeia")
    void appliesPoliciesInSequence() {
        // score 760 -> 35% de 12000 = 4200; gastos 1,5x média 2170 = 3255; 2 atrasos -> x0,6 = 1953
        Customer c = customer(760, "800.00", "12000.00", 2, "2000.00", "2200.00", "2100.00", "2300.00", "2250.00");

        Decision decision = engine().evaluate(c);

        assertThat(decision).isInstanceOfSatisfying(Decision.Approved.class, approved -> {
            assertThat(approved.limit()).isEqualTo(Money.of("1953.00"));
            assertThat(approved.limitAdjustments()).hasSize(3);
        });
    }

    @Test
    @DisplayName("arredonda para baixo no limite calculado")
    void roundsDownInFinalLimit() {
        // gastos médios 9833.33 x 1,5 = 14749.995 -> 14749.99 (não 14750.00)
        Customer c = customer(900, "2000.00", "90000.00", 0,
                "9000.00", "9500.00", "9800.00", "10000.00", "10200.00", "10500.00");

        assertThat(engine().evaluate(c)).isInstanceOfSatisfying(Decision.Approved.class,
                approved -> assertThat(approved.limit()).isEqualTo(Money.of("14749.99")));
    }

    @Test
    @DisplayName("reprovação agrega TODOS os motivos, não só o primeiro")
    void rejectionAggregatesAllReasons() {
        Customer c = customer(430, "900.00", "3000.00", 4, "1000.00", "1100.00", "900.00");

        assertThat(engine().evaluate(c)).isInstanceOfSatisfying(Decision.Rejected.class, rejected ->
                assertThat(rejected.reasons()).extracting(RuleResult::ruleName)
                        .containsExactly("MinimumScore", "MaxLatePayments"));
    }

    @Test
    @DisplayName("é determinístico: mesma entrada e mesma versão geram a mesma decisão")
    void isDeterministic() {
        Customer c = Fixtures.healthyCustomer();
        assertThat(engine().evaluate(c)).isEqualTo(engine().evaluate(c));
    }

    @Test
    @DisplayName("limite calculado igual a zero vira reprovação (nunca aprova R$ 0,00)")
    void zeroLimitBecomesRejection() {
        // 3 atrasos já reprova; usamos penalidade alta via 2 atrasos com política de 50% por atraso
        CreditPolicy harsh = new CreditPolicy("harsh", 500, 2, Fixtures.policy().maxUtilization(), 3,
                Fixtures.policy().maxSpendingSpikeRatio(), Fixtures.policy().minAvailableLimit(),
                Fixtures.policy().spendingMultiplier(), new java.math.BigDecimal("0.50"),
                Fixtures.policy().ceiling(), Fixtures.policy().scoreBands());
        RuleEngine engine = new RuleEngine(Fixtures.rules(harsh), Fixtures.limitPolicies(harsh), harsh.version());
        Customer c = customer(700, "500.00", "5000.00", 2, "1000.00", "1000.00", "1000.00");

        assertThat(engine.evaluate(c)).isInstanceOfSatisfying(Decision.Rejected.class, rejected ->
                assertThat(rejected.reasons()).extracting(RuleResult::ruleName).containsExactly("LimitCalculation"));
    }

    @Test
    @DisplayName("EXTENSIBILIDADE: nova regra entra sem alterar o motor")
    void newRuleRequiresNoEngineChange() {
        EligibilityRule blockList = new EligibilityRule() {
            @Override
            public String name() {
                return "BlockList";
            }

            @Override
            public RuleResult evaluate(Customer customer) {
                return RuleResult.fail(name(), "Cliente em lista de bloqueio");
            }
        };
        List<EligibilityRule> rules = new ArrayList<>(Fixtures.rules(Fixtures.policy()));
        rules.add(blockList);
        RuleEngine engine = new RuleEngine(rules, Fixtures.limitPolicies(Fixtures.policy()), "test-v2");

        assertThat(engine.evaluate(Fixtures.healthyCustomer())).isInstanceOfSatisfying(Decision.Rejected.class, r -> {
            assertThat(r.reasons()).extracting(RuleResult::ruleName).containsExactly("BlockList");
            assertThat(r.ruleSetVersion()).isEqualTo("test-v2");
        });
    }

    @Test
    @DisplayName("EXTENSIBILIDADE: política mal escrita não consegue aumentar o limite")
    void badPolicyCannotIncreaseLimit() {
        LimitPolicy generous = new LimitPolicy() {
            @Override
            public String name() {
                return "Generous";
            }

            @Override
            public Money apply(Customer customer, Money current) {
                return current.plus(Money.of("1000000.00"));
            }
        };
        List<LimitPolicy> policies = new ArrayList<>(Fixtures.limitPolicies(Fixtures.policy()));
        policies.add(generous);
        RuleEngine engine = new RuleEngine(Fixtures.rules(Fixtures.policy()), policies, "test-v3");

        assertThat(engine.evaluate(Fixtures.healthyCustomer())).isInstanceOfSatisfying(Decision.Approved.class,
                approved -> assertThat(approved.limit()).isEqualTo(Money.of("4000.00")));
    }
}
