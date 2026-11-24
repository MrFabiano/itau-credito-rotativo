package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import br.com.creditorotativo.domain.model.RuleResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Comportamento de gastos: exige histórico mínimo e barra picos abruptos de consumo
 * (último mês muito acima da média dos anteriores), que costumam anteceder uso desordenado do crédito.
 */
@Component
public class SpendingBehaviorRule implements EligibilityRule {

    private final CreditPolicy policy;

    public SpendingBehaviorRule(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "SpendingBehavior";
    }

    @Override
    public int order() {
        return 40;
    }

    @Override
    public RuleResult evaluate(Customer customer) {
        int months = customer.monthlySpending().size();
        if (months < policy.minHistoryMonths()) {
            return RuleResult.fail(name(),
                    "Histórico de " + months + " meses, mínimo exigido " + policy.minHistoryMonths());
        }

        Money baseline = customer.averageSpendingBeforeLastMonth();
        if (baseline.isZero()) {
            return RuleResult.pass(name(), "Sem base de comparação de gastos; histórico mínimo atendido");
        }

        Money allowed = baseline.multiply(policy.maxSpendingSpikeRatio());
        if (customer.lastMonthSpending().isGreaterThan(allowed)) {
            return RuleResult.fail(name(),
                    "Gasto do último mês " + customer.lastMonthSpending() + " excede "
                            + policy.maxSpendingSpikeRatio().stripTrailingZeros().toPlainString()
                            + "x a média anterior " + baseline);
        }
        return RuleResult.pass(name(), "Gastos recentes consistentes com o histórico");
    }
}
