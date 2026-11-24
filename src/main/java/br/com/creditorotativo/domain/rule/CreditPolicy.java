package br.com.creditorotativo.domain.rule;

import java.math.BigDecimal;
import java.util.List;

/**
 * Parâmetros de negócio das regras. Ficam fora do código (application.yml) para que ajustar
 * um limiar seja mudança de configuração, não de código. {@code version} é gravada em cada decisão.
 */
public record CreditPolicy(
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

    /** Faixa de score: a partir de {@code minScore}, libera até {@code factor} do limite disponível. */
    public record ScoreBand(int minScore, BigDecimal factor) {
    }

    public CreditPolicy {
        scoreBands = scoreBands.stream()
                .sorted((a, b) -> Integer.compare(b.minScore(), a.minScore()))
                .toList();
    }
}
