package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.Decision;

import java.time.Duration;

/** Porta de saída para observabilidade: o caso de uso não conhece Micrometer, Prometheus ou logs. */
public interface EvaluationObserver {

    void evaluated(Decision decision, Duration elapsed);

    /** A publicação do evento falhou; a decisão já tinha sido tomada e gravada e não é afetada. */
    default void eventPublicationFailed() {
    }

    /** Uma requisição repetida foi respondida a partir da Idempotency-Key, sem reavaliar. */
    default void replayed() {
    }
}
