package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Decision;
import br.com.creditorotativo.domain.model.RuleResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Evento de domínio "uma avaliação foi concluída", pensado para sistemas downstream (antifraude, cobrança,
 * analytics, auditoria regulatória).
 *
 * <p><b>Minimização de dados (LGPD):</b> o evento não leva nome nem CPF. {@code customerKey} é um identificador
 * pseudonimizado, usado como chave de partição para manter a ordem dos eventos por cliente.
 *
 * <p>Atenção: {@code customerKey} é um SHA-256 do CPF. O espaço de CPFs é pequeno, então esse hash pode ser
 * revertido por força bruta. Em produção deve ser um HMAC com segredo guardado em cofre (ver ADR-007).
 */
public record CreditEvaluatedEvent(
        UUID evaluationId,
        Instant evaluatedAt,
        String customerKey,
        String decision,
        BigDecimal approvedLimit,
        List<String> rejectedBy,
        String ruleSetVersion) {

    public CreditEvaluatedEvent {
        rejectedBy = List.copyOf(rejectedBy);
    }

    public static CreditEvaluatedEvent of(Customer customer, CreditEvaluation evaluation) {
        String customerKey = RequestFingerprint.sha256Hex(customer.cpf().digits());
        return switch (evaluation.decision()) {
            case Decision.Approved a -> new CreditEvaluatedEvent(
                    evaluation.id(), evaluation.evaluatedAt(), customerKey,
                    "APPROVED", a.limit().value(), List.of(), a.ruleSetVersion());
            case Decision.Rejected r -> new CreditEvaluatedEvent(
                    evaluation.id(), evaluation.evaluatedAt(), customerKey,
                    "REJECTED", BigDecimal.ZERO, r.reasons().stream().map(RuleResult::ruleName).toList(),
                    r.ruleSetVersion());
        };
    }
}
