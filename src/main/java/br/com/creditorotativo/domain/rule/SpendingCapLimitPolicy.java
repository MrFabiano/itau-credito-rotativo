package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import org.springframework.stereotype.Component;

/** O rotativo não deve ser desproporcional ao padrão real de consumo do cliente. */
@Component
public class SpendingCapLimitPolicy implements LimitPolicy {

    private final CreditPolicy policy;

    public SpendingCapLimitPolicy(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "SpendingCap";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public Money apply(Customer customer, Money current) {
        return current.min(customer.averageSpending().multiply(policy.spendingMultiplier()));
    }
}
