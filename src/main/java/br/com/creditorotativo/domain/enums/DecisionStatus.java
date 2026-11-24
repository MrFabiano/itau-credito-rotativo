package br.com.creditorotativo.domain.enums;

import br.com.creditorotativo.domain.model.Decision;

/** Filtro de consulta por resultado da decisão. */
public enum DecisionStatus {
    APPROVED, REJECTED;

    public boolean matches(Decision decision) {
        return (this == APPROVED) == decision.isApproved();
    }
}