package br.com.creditorotativo.infrastructure.observability;

import br.com.creditorotativo.application.EvaluationObserver;
import br.com.creditorotativo.domain.model.Decision;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Métricas de negócio e de latência. Tags de baixa cardinalidade (decisão e nome da regra);
 * nunca CPF, nome ou valores como tag/label.
 *
 * <ul>
 *   <li>{@code credit_evaluations_total{decision}}: volume e taxa de aprovação</li>
 *   <li>{@code credit_rule_rejections_total{rule}}: qual regra mais barra (detecta regra mal configurada)</li>
 *   <li>{@code credit_evaluation_duration_seconds}: histograma para p50/p95/p99 e SLO de 1s</li>
 * </ul>
 */
@Component
public class MicrometerEvaluationObserver implements EvaluationObserver {

    private final MeterRegistry registry;
    private final Timer timer;

    public MicrometerEvaluationObserver(MeterRegistry registry) {
        this.registry = registry;
        this.timer = Timer.builder("credit.evaluation.duration")
                .description("Tempo de decisão do motor de regras")
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(50), Duration.ofMillis(200), Duration.ofSeconds(1))
                .register(registry);
    }

    @Override
    public void evaluated(Decision decision, Duration elapsed) {
        timer.record(elapsed);
        registry.counter("credit.evaluations", "decision", decision.isApproved() ? "approved" : "rejected").increment();
        if (decision instanceof Decision.Rejected rejected) {
            rejected.reasons().forEach(r ->
                    registry.counter("credit.rule.rejections", "rule", r.ruleName()).increment());
        }
    }
}
