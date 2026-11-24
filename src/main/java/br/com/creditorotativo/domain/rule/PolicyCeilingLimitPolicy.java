package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import org.springframework.stereotype.Component;

/** Teto absoluto de política: limita a exposição por cliente independentemente das demais regras. */
@Component
public class PolicyCeilingLimitPolicy implements LimitPolicy {

    private final CreditPolicy policy;

    public PolicyCeilingLimitPolicy(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "PolicyCeiling";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public Money apply(Customer customer, Money current) {
        return current.min(Money.of(policy.ceiling()));
    }
}
