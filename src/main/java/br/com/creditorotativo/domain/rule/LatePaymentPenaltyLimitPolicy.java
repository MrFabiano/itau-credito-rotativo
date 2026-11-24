package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Cada atraso reduz o limite em uma fração fixa (mínimo zero). */
@Component
public class LatePaymentPenaltyLimitPolicy implements LimitPolicy {

    private final CreditPolicy policy;

    public LatePaymentPenaltyLimitPolicy(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "LatePaymentPenalty";
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public Money apply(Customer customer, Money current) {
        BigDecimal penalty = policy.latePenaltyPerOccurrence().multiply(BigDecimal.valueOf(customer.latePayments()));
        BigDecimal remaining = BigDecimal.ONE.subtract(penalty).max(BigDecimal.ZERO);
        return current.multiply(remaining);
    }
}
