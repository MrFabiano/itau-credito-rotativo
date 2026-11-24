package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.RuleResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Cliente com fatura consumindo quase todo o limite tem pouca margem para absorver o rotativo. */
@Component
public class InvoiceUtilizationRule implements EligibilityRule {

    private final CreditPolicy policy;

    public InvoiceUtilizationRule(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "InvoiceUtilization";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public RuleResult evaluate(Customer customer) {
        BigDecimal utilization = customer.utilization();
        if (utilization.compareTo(policy.maxUtilization()) > 0) {
            return RuleResult.fail(name(),
                    "Utilização do limite " + percent(utilization) + " acima do máximo " + percent(policy.maxUtilization()));
        }
        return RuleResult.pass(name(), "Utilização do limite " + percent(utilization) + " dentro do permitido");
    }

    private static String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(1, java.math.RoundingMode.HALF_EVEN) + "%";
    }
}
