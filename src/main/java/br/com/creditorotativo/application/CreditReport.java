package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.Money;

import java.util.List;

/** Relatório consolidado de um lote: aprovados, reprovados e valores liberados. */
public record CreditReport(List<CreditEvaluation> evaluations) {

    public CreditReport {
        evaluations = List.copyOf(evaluations);
    }

    public List<CreditEvaluation> approved() {
        return evaluations.stream().filter(e -> e.decision().isApproved()).toList();
    }

    public List<CreditEvaluation> rejected() {
        return evaluations.stream().filter(e -> !e.decision().isApproved()).toList();
    }

    public Money totalApprovedAmount() {
        return approved().stream()
                .map(e -> ((Decision.Approved) e.decision()).limit())
                .reduce(Money.ZERO, Money::plus);
    }
}
