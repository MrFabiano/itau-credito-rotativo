package br.com.creditorotativo.application;

/**
 * Porta de saída para publicar eventos de avaliação. Hoje a implementação só registra log.
 *
 * <p>Em produção seria o padrão <b>outbox</b>: o evento é gravado na mesma transação da avaliação e um despachante
 * o publica no Kafka com retry e DLQ (ADR-004). Trocar o adaptador não exige mudar domínio nem caso de uso.
 *
 * <p>Contrato: publicar é efeito colateral e <b>não pode</b> impedir a decisão. O caso de uso protege a decisão
 * contra falhas aqui e as contabiliza na métrica {@code credit_events_publish_failures_total}.
 */
public interface EvaluationEventPublisher {

    void publish(CreditEvaluatedEvent event);
}
