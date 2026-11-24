package br.com.creditorotativo.domain.model;

/** Resultado da avaliação de uma regra de elegibilidade, sempre com motivo para auditoria. */
public record RuleResult(String ruleName, boolean passed, String reason) {

    public static RuleResult pass(String ruleName, String reason) {
        return new RuleResult(ruleName, true, reason);
    }

    public static RuleResult fail(String ruleName, String reason) {
        return new RuleResult(ruleName, false, reason);
    }
}
