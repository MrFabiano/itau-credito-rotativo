package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.RuleResult;
import org.springframework.stereotype.Component;

/** Score de crédito abaixo do mínimo indica risco elevado de inadimplência. */
@Component
public class MinimumScoreRule implements EligibilityRule {

    private final CreditPolicy policy;

    public MinimumScoreRule(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "MinimumScore";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public RuleResult evaluate(Customer customer) {
        if (customer.creditScore() < policy.minScore()) {
            return RuleResult.fail(name(),
                    "Score " + customer.creditScore() + " abaixo do mínimo " + policy.minScore());
        }
        return RuleResult.pass(name(), "Score " + customer.creditScore() + " atende ao mínimo");
    }
}
