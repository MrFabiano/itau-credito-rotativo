# Observabilidade

A aplicação expõe métricas para o **Prometheus**, correlaciona logs e respostas de erro por um `traceId` e traz regras de
alerta prontas. Este documento é o catálogo: o que é medido, por quê, como coletar, quando alertar e como investigar.

> **Status da verificação:** os nomes das métricas foram derivados do código e da convenção do Micrometer, e as regras
> passaram em validação de sintaxe, mas nada disto foi executado contra a aplicação rodando nem contra um Prometheus
> real. Siga a seção [Como verificar](#como-verificar) antes de confiar nos alertas.

## O que está implementado

| Pilar | Implementado | Onde |
|---|---|---|
| Métricas | Contadores e histograma de negócio, métricas HTTP e de JVM, endpoint Prometheus | `MicrometerEvaluationObserver`, Actuator |
| Logs | Padrão de log com `traceId` em toda linha de requisição; log do evento de avaliação sem dado pessoal | `application.yml`, `TraceIdFilter` |
| Correlação | `X-Trace-Id` aceito, validado e devolvido; `traceId` no corpo de toda resposta de erro | `TraceIdFilter`, `ApiExceptionHandler` |
| Health | `/actuator/health/liveness` e `/actuator/health/readiness` | Actuator |
| Alertas | 11 alertas e 4 regras de gravação em PromQL | `observability/alerts.yml` |
| Coleta | Configuração de scrape do Prometheus | `observability/prometheus.yml` |

**Não implementado:** tracing distribuído (OpenTelemetry), logs em JSON, roteamento de alertas (Alertmanager) e dashboards
como código. Ver [Limitações e evolução](#limitações-e-evolução).

## Como coletar

```bash
# Aplicação
mvn spring-boot:run

# Prometheus (binário local) com a configuração do projeto
prometheus --config.file=observability/prometheus.yml
```

O scrape lê `/actuator/prometheus` a cada 15 s. Todas as métricas levam o rótulo `application="credito-rotativo"`.

## Catálogo de métricas

| Métrica (Prometheus) | Tipo | Rótulos | Para que serve |
|---|---|---|---|
| `credit_evaluations_total` | contador | `decision` = `approved` ou `rejected` | Volume e taxa de aprovação |
| `credit_rule_rejections_total` | contador | `rule` (nome da regra) | Qual regra mais barra; detecta regra mal calibrada |
| `credit_evaluation_duration_seconds_*` | histograma | sem rótulos | Latência da decisão (p50, p95, p99) e SLO de 1 s. Faixas explícitas em 50 ms, 200 ms e 1 s |
| `credit_idempotent_replays_total` | contador | sem rótulos | Retries absorvidos pela `Idempotency-Key`; muitos indicam clientes com timeout |
| `credit_events_publish_failures_total` | contador | sem rótulos | Falhas ao publicar o evento de avaliação |
| `http_server_requests_seconds_*` | histograma | `method`, `uri`, `status`, `outcome` | Latência e erros vistos pelo cliente, por rota |
| `jvm_memory_*`, `jvm_gc_*`, `process_*` | vários | — | Saúde da JVM; o armazenamento em memória cresce até 10.000 avaliações |

**Cardinalidade:** os rótulos têm poucos valores possíveis. Nenhuma métrica usa CPF, nome, valor ou id de avaliação como
rótulo: isso explodiria a cardinalidade e vazaria dado pessoal.

**Duas latências diferentes:** `credit_evaluation_duration_seconds` mede só o motor de regras (em memória, tende a
microssegundos). `http_server_requests_seconds` mede a requisição inteira, o que o cliente sente. O requisito de 1 segundo
é de ponta a ponta, então o alerta principal usa a métrica HTTP.

## SLOs

| SLI | Objetivo | Medido por |
|---|---|---|
| Latência da decisão | 99,9% das decisões em até 1 s | `credit:decision_within_1s_ratio:rate5m` |
| Latência ponta a ponta | p99 do POST abaixo de 800 ms (folga de 200 ms sobre o requisito) | `http_server_requests_seconds_bucket` |
| Erros | Menos de 1% de respostas 5xx em 5 min | `http_server_requests_seconds_count` |
| Disponibilidade | Instância respondendo ao scrape | `up` |

Simplificação assumida: o alerta de erro dispara em 1% por 5 minutos, mais frouxo que um SLO de 99,9%. Alertas por taxa
de consumo do orçamento de erro, com várias janelas, são a evolução natural.

## Alertas

`page` acorda alguém; `ticket` é tratado em horário comercial. Definições completas em `observability/alerts.yml`.

| Alerta | Sev. | Dispara quando | O que fazer |
|---|---|---|---|
| `CreditoRotativoInstanciaForaDoAr` | page | `up == 0` por 1 min | Ver o processo e `/actuator/health/liveness` |
| `CreditoRotativoLatenciaHttpP99Alta` | page | p99 do POST > 800 ms por 5 min | Procurar gargalo (GC, CPU, dependência); comparar com `credit_evaluation_duration_seconds` |
| `CreditoRotativoSloDeLatenciaDaDecisao` | page | menos de 99,9% das decisões em ≤ 1 s por 10 min | Idem; checar pausas de GC |
| `CreditoRotativoTaxaDeErro5xxAlta` | page | 5xx > 1% das requisições por 5 min | Buscar o `traceId` nos logs de erro |
| `CreditoRotativoDesvioNaTaxaDeAprovacao` | ticket | aprovação dos últimos 15 min difere mais de 15 p.p. da média do dia, com mais de 100 avaliações | Verificar mudança de regra (`credit.policy.version`) ou de perfil dos clientes |
| `CreditoRotativoUmaRegraBarrandoDemais` | ticket | uma regra reprova mais de 50% das avaliações | Limiar mal calibrado ou dado de entrada incorreto |
| `CreditoRotativoFalhaAoPublicarEventos` | ticket | qualquer falha de publicação em 5 min | Sistemas downstream não foram avisados; a decisão continua correta |
| `CreditoRotativoCapacidadeProximaDoPico` | ticket | vazão > 8.000/min (80% do pico de 10.000/min) | Escalar réplicas |
| `CreditoRotativoMuitosRetriesDeClientes` | ticket | mais de 10% dos POSTs são retries idempotentes | Investigar latência e rede dos clientes |
| `CreditoRotativoMemoriaHeapAlta` | ticket | heap > 90% por 10 min | Revisar `credit.store.max-entries` ou o heap |
| `CreditoRotativoSemTrafego` | ticket | nenhuma avaliação em 30 min | Pode ser normal fora do horário; ajustar no Alertmanager |

O alerta **`DesvioNaTaxaDeAprovacao`** é o mais importante para o negócio: uma regra errada não gera erro técnico algum, mas
pode liberar crédito indevido ou bloquear clientes bons. Só a taxa de aprovação denuncia isso.

## Logs e `traceId`

Todo log emitido durante uma requisição leva o `traceId`:

```
  INFO [traceId=4bf92f3577b34da6a3ce929d0e0e4736] 12345 --- [nio-8080-exec-1] ...LoggingEvaluationEventPublisher : CreditEvaluated evaluationId=9f1c... decision=APPROVED ruleSetVersion=2026.11-v1
```

(exemplo ilustrativo; o restante da linha segue o padrão do Spring Boot)

**Fluxo de investigação:**
1. O cliente reporta um erro e informa o `traceId` (vem no header `X-Trace-Id` e no corpo `ProblemDetail` de qualquer erro).
2. Busca-se esse id nos logs: `grep 4bf92f35... app.log`.
3. Se o cliente enviou o próprio `X-Trace-Id`, ele é preservado, o que liga o log do cliente ao do servidor.

**Regras de segurança do id:** o `X-Trace-Id` recebido só é aceito se tiver de 1 a 64 caracteres entre `A-Z a-z 0-9 . _ -`.
Qualquer outro valor é descartado e substituído por um id novo, nunca rejeitado e nunca ecoado. Aceitar texto livre
permitiria forjar linhas de log (quebras de linha) ou injetar caracteres de controle no header.

**Dados pessoais:** os logs não contêm CPF completo, nome nem valores de crédito. O evento de avaliação registra apenas o
id da avaliação, a decisão e a versão das regras.

### O que o `traceId` é e o que não é

É um **identificador de correlação** gerado no filtro `TraceIdFilter`: liga as linhas de log e a resposta de uma mesma
requisição **dentro desta aplicação**. **Não é tracing distribuído:** não há spans, não há medição de tempo por etapa e
o id não é propagado para outros serviços (não lê nem escreve o `traceparent` do W3C Trace Context).

Foi escolhido assim de propósito: a chave de MDC `traceId` e o formato de 32 caracteres hexadecimais são os mesmos do
OpenTelemetry. Quando houver Micrometer Tracing, ele passa a preencher a mesma chave, e padrões de log, buscas e este
fluxo de investigação continuam valendo. Ver [ADR-008](adr/008-trace-id-por-filtro-e-opentelemetry-como-evolucao.md).

## Painéis sugeridos (Grafana)

| Painel | Consulta |
|---|---|
| Avaliações por minuto | `sum(rate(credit_evaluations_total[1m])) * 60` |
| Taxa de aprovação | `credit:approval_ratio:rate15m` |
| Rejeições por regra | `sum by (rule) (rate(credit_rule_rejections_total[5m]))` |
| Latência p50 / p95 / p99 (HTTP) | `histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{method="POST", uri="/v1/credit-evaluations"}[5m])))` |
| Respostas por status | `sum by (status) (rate(http_server_requests_seconds_count{uri=~"/v1/.*"}[5m]))` |
| Retries idempotentes | `sum(rate(credit_idempotent_replays_total[5m]))` |
| Heap em uso | `sum by (instance) (jvm_memory_used_bytes{area="heap"})` |

## Como verificar

Faça isto uma vez com a aplicação no ar, antes de confiar nas regras:

```bash
# 1. As métricas de negócio aparecem? (gere ao menos uma avaliação antes)
curl -s localhost:8080/actuator/prometheus | grep -E '^(credit_|# TYPE credit_)'

# 2. O bucket de 1 segundo tem o rótulo exatamente le="1.0"? (a regra de SLO depende desse texto)
curl -s localhost:8080/actuator/prometheus | grep 'credit_evaluation_duration_seconds_bucket' | grep -E 'le="(0.05|0.2|1.0)"'

# 3. O traceId volta no header e no erro?
curl -si -X DELETE localhost:8080/v1/credit-evaluations/00000000-0000-0000-0000-000000000000 -H 'X-Trace-Id: teste-123'

# 4. As regras são válidas para o Prometheus?
promtool check rules observability/alerts.yml
promtool check config observability/prometheus.yml
```

Se o passo 2 mostrar outro formato de `le` (por exemplo `1`), ajuste o `le="1.0"` em `alerts.yml`.

## Limitações e evolução

- **Tracing distribuído:** adicionar Micrometer Tracing com a ponte para OpenTelemetry e exportar por OTLP. Muda o `pom.xml`.
- **Logs em JSON:** o Spring Boot 3.4+ traz suporte nativo a logs estruturados; na versão atual (3.3) seria preciso o
  `logstash-logback-encoder`. Facilita busca e agregação em Elastic ou Loki.
- **Alertmanager:** os alertas têm severidade, mas não há roteamento (quem recebe `page` e `ticket`) nem silenciamento.
- **Dashboards como código** (JSON versionado) e **alertas por taxa de consumo de orçamento de erro**, em duas janelas.
- **Exemplars:** ligar o `traceId` a pontos do histograma de latência no Grafana.
- **Cobertura de logs:** hoje há poucos logs de aplicação (erro inesperado e evento de avaliação). Para operação real,
  incluir logs de auditoria de acesso quando houver autenticação.
