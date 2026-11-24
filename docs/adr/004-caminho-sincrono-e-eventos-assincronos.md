# ADR-004: Decisão síncrona em memória; persistência e eventos fora do caminho crítico (proposta)

**Status:** proposta (não implementada nesta entrega)

## Contexto
Meta de decisão em até 1 segundo e picos de ~167 avaliações/s, com necessidade de auditoria, idempotência e
integração com outros sistemas.

## Decisão proposta
- O caminho síncrono só calcula (puro, em memória) e responde. Nenhum broker no caminho crítico.
- Auditoria e eventos na **mesma transação** local: decisão gravada + registro em tabela de **outbox**; um despachante
  publica `CreditEvaluated` no Kafka com retry idempotente e DLQ.
- Chave do evento = identificador do cliente (hash do CPF), preservando ordem por cliente na partição.
  Isso reduz concorrência, mas **não substitui** idempotência: o Kafka é at-least-once e rebalances reentregam.
- Idempotência em camadas: `Idempotency-Key` na API → verificação rápida na aplicação → **constraint única no banco
  como garantia final**, tratando a violação como sucesso idempotente.
- Se o banco estiver indisponível: responder 503 com `Retry-After`, em vez de decidir sem trilha de auditoria.

## Alternativas
- Publicar direto no Kafka após gravar no banco: sujeito a perder o evento ou duplicá-lo entre os dois sistemas.
- Exactly-once do Kafka: só cobre o próprio Kafka; efeitos externos (banco, HTTP) ainda exigem idempotência na aplicação.

## Consequências
+ Latência previsível; auditoria e integração confiáveis.
− Mais componentes (banco, broker, despachante) e monitoramento de lag do outbox.
