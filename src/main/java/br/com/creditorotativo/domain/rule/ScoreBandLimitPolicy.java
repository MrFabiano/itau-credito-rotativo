package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import org.springframework.stereotype.Component;

/** Quanto melhor o score, maior a fração do limite disponível que pode virar rotativo. */
@Component
public class ScoreBandLimitPolicy implements LimitPolicy {

    private final CreditPolicy policy;

    public ScoreBandLimitPolicy(CreditPolicy policy) {
        this.policy = policy;
    }

    @Override
    public String name() {
        return "ScoreBand";
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public Money apply(Customer customer, Money current) {
        // Faixas já ordenadas da maior para a menor; sem faixa correspondente, libera zero (fail-closed).
        return policy.scoreBands().stream()
                .filter(band -> customer.creditScore() >= band.minScore())
                .findFirst()
                .map(band -> current.min(customer.availableLimit().multiply(band.factor())))
                .orElse(Money.ZERO);
    }
}
