package br.com.creditorotativo.domain.model;

import java.util.List;

/**
 * Decisão final da avaliação. Sealed: o compilador força o tratamento dos dois casos.
 * Toda decisão carrega a versão do conjunto de regras, para ser reproduzível e auditável.
 */
public sealed interface Decision {

    String ruleSetVersion();

    /** Crédito rotativo liberado até {@code limit}. */
    record Approved(Money limit, List<String> limitAdjustments, String ruleSetVersion) implements Decision {
        public Approved {
            limitAdjustments = List.copyOf(limitAdjustments);
        }
    }

    /** Crédito rotativo negado. {@code reasons} contém todos os motivos (não só o primeiro). */
    record Rejected(List<RuleResult> reasons, String ruleSetVersion) implements Decision {
        public Rejected {
            reasons = List.copyOf(reasons);
        }
    }

    default boolean isApproved() {
        return this instanceof Approved;
    }
}
