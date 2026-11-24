package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.RuleResult;
import org.springframework.stereotype.Component;

/** Atrasos recentes são o preditor mais direto de inadimplência futura. */
@Component
public class MaxLatePaymentsRule implements EligibilityRule {

    private final CreditPolicy policy;

    public MaxLatePaymentsRule(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "MaxLatePayments";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public RuleResult evaluate(Customer customer) {
        if (customer.latePayments() > policy.maxLatePayments()) {
            return RuleResult.fail(name(),
                    customer.latePayments() + " atrasos, máximo permitido " + policy.maxLatePayments());
        }
        return RuleResult.pass(name(), customer.latePayments() + " atrasos dentro do permitido");
    }
}
