package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.Money;
import br.com.creditorotativo.domain.model.RuleResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orquestra regras e políticas. Classe pura (sem Spring), determinística e sem I/O:
 * mesmo cliente + mesma versão de regras = mesma decisão.
 *
 * <p>Decisão de projeto: todas as regras são avaliadas (sem curto-circuito) para devolver
 * todos os motivos de reprovação. O custo é desprezível em memória e o ganho em auditoria é grande.
 */
public class RuleEngine {

    private final List<EligibilityRule> rules;
    private final List<LimitPolicy> policies;
    private final String ruleSetVersion;

    public RuleEngine(List<EligibilityRule> rules, List<LimitPolicy> policies, String ruleSetVersion) {
        this.rules = rules.stream().sorted(Comparator.comparingInt(EligibilityRule::order)).toList();
        this.policies = policies.stream().sorted(Comparator.comparingInt(LimitPolicy::order)).toList();
        this.ruleSetVersion = ruleSetVersion;
    }

    public Decision evaluate(Customer customer) {
        List<RuleResult> failures = rules.stream()
                .map(rule -> rule.evaluate(customer))
                .filter(result -> !result.passed())
                .toList();

        if (!failures.isEmpty()) {
            return new Decision.Rejected(failures, ruleSetVersion);
        }
        return calculateLimit(customer);
    }

    private Decision calculateLimit(Customer customer) {
        Money limit = customer.availableLimit();
        List<String> adjustments = new ArrayList<>();

        for (LimitPolicy policy : policies) {
            Money next = policy.apply(customer, limit);
            // Defesa em profundidade: uma política mal escrita não consegue aumentar o limite.
            next = next.min(limit);
            if (next.isLessThan(limit)) {
                adjustments.add(policy.name() + ": " + limit + " -> " + next);
            }
            limit = next;
        }

        if (limit.isZero()) {
            return new Decision.Rejected(
                    List.of(RuleResult.fail("LimitCalculation", "Limite calculado igual a zero após aplicação das políticas")),
                    ruleSetVersion);
        }
        return new Decision.Approved(limit, adjustments, ruleSetVersion);
    }
}
