package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.RuleResult;

/**
 * Regra eliminatória: responde "este cliente pode receber crédito rotativo?".
 *
 * <p>Para adicionar uma regra nova basta criar uma classe {@code @Component} que implemente
 * esta interface. O {@link RuleEngine} não precisa ser alterado (Open/Closed).
 */
public interface EligibilityRule {

    String name();

    /** Ordem de avaliação (menor primeiro). Afeta apenas a ordem dos motivos no relatório. */
    default int order() {
        return 100;
    }

    RuleResult evaluate(Customer customer);
}
