package br.com.creditorotativo.messaging;

import br.com.creditorotativo.application.CreditEvaluatedEvent;
import br.com.creditorotativo.application.EvaluationEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEvaluationEventPublisher implements EvaluationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingEvaluationEventPublisher.class);

    @Override
    public void publish(CreditEvaluatedEvent event) {
        log.info("CreditEvaluated evaluationId={} decision={} ruleSetVersion={}",
                event.evaluationId(), event.decision(), event.ruleSetVersion());
    }
}
