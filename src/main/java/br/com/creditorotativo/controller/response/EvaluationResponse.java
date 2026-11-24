package br.com.creditorotativo.controller.response;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.application.CreditReport;
import br.com.creditorotativo.application.Page;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.RuleResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrato de saída. CPF sempre mascarado. */
public record EvaluationResponse(
        UUID id,
        Instant evaluatedAt,
        String name,
        String cpf,
        String decision,
        BigDecimal approvedLimit,
        List<String> reasons,
        List<String> limitAdjustments,
        String ruleSetVersion) {

    public static EvaluationResponse from(CreditEvaluation e) {
        return switch (e.decision()) {
            case Decision.Approved a -> new EvaluationResponse(
                    e.id(), e.evaluatedAt(), e.customerName(), e.maskedCpf(), "APPROVED", a.limit().value(),
                    List.of(), a.limitAdjustments(), a.ruleSetVersion());
            case Decision.Rejected r -> new EvaluationResponse(
                    e.id(), e.evaluatedAt(), e.customerName(), e.maskedCpf(), "REJECTED", BigDecimal.ZERO,
                    r.reasons().stream().map(RuleResult::reason).toList(), List.of(), r.ruleSetVersion());
        };
    }

    public record Batch(int total, int approved, int rejected, BigDecimal totalApprovedAmount,
                        List<EvaluationResponse> evaluations) {

        public static Batch from(CreditReport report) {
            return new Batch(
                    report.evaluations().size(),
                    report.approved().size(),
                    report.rejected().size(),
                    report.totalApprovedAmount().value(),
                    report.evaluations().stream().map(EvaluationResponse::from).toList());
        }
    }

    public record Paged(List<EvaluationResponse> content, int page, int size, long totalElements, int totalPages) {

        public static Paged from(Page<CreditEvaluation> page) {
            int totalPages = (int) Math.ceil((double) page.totalElements() / page.size());
            return new Paged(
                    page.content().stream().map(EvaluationResponse::from).toList(),
                    page.page(), page.size(), page.totalElements(), totalPages);
        }
    }
}
