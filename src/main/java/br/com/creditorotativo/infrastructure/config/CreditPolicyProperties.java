package br.com.creditorotativo.infrastructure.config;

import br.com.creditorotativo.domain.rule.CreditPolicy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.List;

/**
 * Binding de {@code credit.policy.*} do application.yml. Fica na infraestrutura para o domínio
 * não depender de Spring Boot; {@link #toDomain()} converte para o objeto puro do domínio.
 */
@ConfigurationProperties("credit.policy")
public record CreditPolicyProperties(
        String version,
        int minScore,
        int maxLatePayments,
        BigDecimal maxUtilization,
        int minHistoryMonths,
        BigDecimal maxSpendingSpikeRatio,
        BigDecimal minAvailableLimit,
        BigDecimal spendingMultiplier,
        BigDecimal latePenaltyPerOccurrence,
        BigDecimal ceiling,
        List<ScoreBand> scoreBands) {

    public record ScoreBand(int minScore, BigDecimal factor) {
    }

    public CreditPolicy toDomain() {
        return new CreditPolicy(
                version, minScore, maxLatePayments, maxUtilization, minHistoryMonths,
                maxSpendingSpikeRatio, minAvailableLimit, spendingMultiplier,
                latePenaltyPerOccurrence, ceiling,
                scoreBands.stream().map(b -> new CreditPolicy.ScoreBand(b.minScore(), b.factor())).toList());
    }
}
