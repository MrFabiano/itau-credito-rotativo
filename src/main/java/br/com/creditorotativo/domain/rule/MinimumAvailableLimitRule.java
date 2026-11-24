package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import br.com.creditorotativo.domain.model.RuleResult;
import org.springframework.stereotype.Component;

/** Sem limite disponível mínimo não há base para calcular um rotativo relevante. */
@Component
public class MinimumAvailableLimitRule implements EligibilityRule {

    private final CreditPolicy policy;

    public MinimumAvailableLimitRule(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "MinimumAvailableLimit";
    }

    @Override
    public int order() {
        return 50;
    }

    @Override
    public RuleResult evaluate(Customer customer) {
        Money minimum = Money.of(policy.minAvailableLimit());
        if (customer.availableLimit().isLessThan(minimum)) {
            return RuleResult.fail(name(),
                    "Limite disponível " + customer.availableLimit() + " abaixo do mínimo " + minimum);
        }
        return RuleResult.pass(name(), "Limite disponível " + customer.availableLimit() + " atende ao mínimo");
    }
}
