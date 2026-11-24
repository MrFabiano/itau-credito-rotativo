package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.RuleResult;

import java.util.stream.Collectors;

/** Formata o relatório em texto (leitura humana) e CSV (integração/planilha). */
public final class ReportFormatter {

    private ReportFormatter() {
    }

    public static String toText(CreditReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== RELATÓRIO DE CRÉDITO ROTATIVO ===\n");
        sb.append("Total avaliados: ").append(report.evaluations().size())
                .append(" | Aprovados: ").append(report.approved().size())
                .append(" | Reprovados: ").append(report.rejected().size())
                .append(" | Total liberado: R$ ").append(report.totalApprovedAmount()).append("\n\n");

        sb.append("--- APROVADOS ---\n");
        for (CreditEvaluation e : report.approved()) {
            Decision.Approved d = (Decision.Approved) e.decision();
            sb.append(String.format("%-24s %-16s R$ %s%n", e.customerName(), e.maskedCpf(), d.limit()));
            d.limitAdjustments().forEach(a -> sb.append("    ajuste: ").append(a).append("\n"));
        }

        sb.append("\n--- REPROVADOS ---\n");
        for (CreditEvaluation e : report.rejected()) {
            Decision.Rejected d = (Decision.Rejected) e.decision();
            sb.append(String.format("%-24s %-16s%n", e.customerName(), e.maskedCpf()));
            d.reasons().forEach(r -> sb.append("    [").append(r.ruleName()).append("] ").append(r.reason()).append("\n"));
        }
        return sb.toString();
    }

    public static String toCsv(CreditReport report) {
        StringBuilder sb = new StringBuilder("nome;cpf;decisao;valor_liberado;motivos;versao_regras\n");
        for (CreditEvaluation e : report.evaluations()) {
            switch (e.decision()) {
                case Decision.Approved a -> sb.append(row(e, "APROVADO", a.limit().toString(), "", a.ruleSetVersion()));
                case Decision.Rejected r -> sb.append(row(e, "REPROVADO", "0.00",
                        r.reasons().stream().map(RuleResult::reason).collect(Collectors.joining(" | ")),
                        r.ruleSetVersion()));
            }
        }
        return sb.toString();
    }

    private static String row(CreditEvaluation e, String decision, String amount, String reasons, String version) {
        return String.join(";", csv(e.customerName()), e.maskedCpf(), decision, amount, csv(reasons), version) + "\n";
    }

    /** Neutraliza separador e fórmulas de planilha (CSV injection) em campos vindos do usuário. */
    private static String csv(String value) {
        String safe = value.replace(";", ",").replace("\n", " ");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }
        return safe;
    }
}
