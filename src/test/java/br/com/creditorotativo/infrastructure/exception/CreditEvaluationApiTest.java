package br.com.creditorotativo.infrastructure.exception;

import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Teste de integração: contexto Spring real, regras descobertas automaticamente, configuração do application.yml. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class CreditEvaluationApiTest {

    private static final String HEALTHY = """
            {"name":"Ana Souza","cpf":"12345678909","creditScore":820,"currentInvoice":1200.00,
             "availableLimit":8000.00,"latePayments":0,"monthlySpending":[2800.00,3000.00,3100.00,3200.00]}
            """;

    private static final String REJECTED = HEALTHY.replace("\"creditScore\":820", "\"creditScore\":300");

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    // ---------- POST ----------

    @Test
    @DisplayName("POST aprova cliente saudável (201) e nunca devolve o CPF completo")
    void approvesAndMasksCpf() throws Exception {
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.approvedLimit").value(4000.00))
                .andExpect(jsonPath("$.cpf").value("***.456.789-**"))
                .andExpect(content().string(not(containsString("12345678909"))));
    }

    @Test
    @DisplayName("POST reprova com motivos explicados")
    void rejectsWithReasons() throws Exception {
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(REJECTED))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.decision").value("REJECTED"))
                .andExpect(jsonPath("$.approvedLimit").value(0))
                .andExpect(jsonPath("$.reasons[0]").value(containsString("300")));
    }

    @Test
    @DisplayName("POST com CPF inválido -> 400 sem ecoar o valor recebido")
    void invalidCpfReturns400() throws Exception {
        String body = HEALTHY.replace("12345678909", "11122233344");
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("11122233344"))));
    }

    @Test
    @DisplayName("POST com campos fora da faixa -> 400 com lista de erros")
    void validationErrorsReturn400() throws Exception {
        String body = HEALTHY.replace("\"creditScore\":820", "\"creditScore\":2000");
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0]").value(containsString("creditScore")));
    }

    @Test
    @DisplayName("lote de exemplo: 4 aprovados, 4 reprovados e total liberado conferido à mão")
    void sampleBatchProducesExpectedReport() throws Exception {
        mvc.perform(post("/v1/credit-evaluations/batch").contentType(MediaType.APPLICATION_JSON).content(sample()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.approved").value(4))
                .andExpect(jsonPath("$.rejected").value(4))
                .andExpect(jsonPath("$.totalApprovedAmount").value(22382.99));
    }

    @Test
    @DisplayName("relatório do lote em CSV")
    void batchCsv() throws Exception {
        mvc.perform(post("/v1/credit-evaluations/batch/csv").contentType(MediaType.APPLICATION_JSON).content(sample()))
                .andExpect(status().isCreated())
                .andExpect(content().string(containsString("nome;cpf;decisao;valor_liberado;motivos;versao_regras")))
                .andExpect(content().string(containsString("APROVADO")))
                .andExpect(content().string(containsString("REPROVADO")));
    }

    @Test
    @DisplayName("publica métricas de volume por decisão")
    void publishesMetrics() throws Exception {
        double before = registry.counter("credit.evaluations", "decision", "approved").count();

        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());

        assertThat(registry.counter("credit.evaluations", "decision", "approved").count()).isEqualTo(before + 1);
    }

    // ---------- GET ----------

    @Test
    @DisplayName("GET por id devolve exatamente a avaliação criada pelo POST")
    void getByIdReturnsCreatedEvaluation() throws Exception {
        String created = mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/v1/credit-evaluations/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.decision").value("APPROVED"))
                .andExpect(jsonPath("$.approvedLimit").value(4000.00))
                .andExpect(jsonPath("$.cpf").value("***.456.789-**"))
                .andExpect(jsonPath("$.evaluatedAt").isNotEmpty())
                .andExpect(content().string(not(containsString("12345678909"))));
    }

    @Test
    @DisplayName("GET por id inexistente -> 404 (ProblemDetail)")
    void getUnknownIdReturns404() throws Exception {
        mvc.perform(get("/v1/credit-evaluations/{id}", "00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("não encontrada")));
    }

    @Test
    @DisplayName("GET com id que não é UUID -> 400")
    void getMalformedIdReturns400() throws Exception {
        mvc.perform(get("/v1/credit-evaluations/{id}", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET lista filtra por decisão e pagina")
    void listFiltersByDecisionAndPaginates() throws Exception {
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(REJECTED))
                .andExpect(status().isCreated());

        mvc.perform(get("/v1/credit-evaluations").param("decision", "APPROVED").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(lessThanOrEqualTo(5))))
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("APPROVED"))))
                .andExpect(jsonPath("$.totalElements").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.size").value(5));

        mvc.perform(get("/v1/credit-evaluations").param("decision", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].decision", everyItem(is("REJECTED"))));
    }

    @Test
    @DisplayName("GET lista com parâmetros inválidos -> 400")
    void listWithInvalidParamsReturns400() throws Exception {
        mvc.perform(get("/v1/credit-evaluations").param("size", "1000")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/credit-evaluations").param("page", "-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/v1/credit-evaluations").param("decision", "MAYBE")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET relatório consolidado em JSON e CSV, com CPF sempre mascarado")
    void reportEndpoints() throws Exception {
        mvc.perform(post("/v1/credit-evaluations/batch").contentType(MediaType.APPLICATION_JSON).content(sample()))
                .andExpect(status().isCreated());

        mvc.perform(get("/v1/reports/credit-evaluations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(greaterThanOrEqualTo(8)))
                .andExpect(jsonPath("$.approved").value(greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$.totalApprovedAmount").isNumber())
                .andExpect(content().string(not(containsString("12345678909"))));

        mvc.perform(get("/v1/reports/credit-evaluations/csv"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("nome;cpf;decisao;valor_liberado;motivos;versao_regras")))
                .andExpect(content().string(not(containsString("12345678909"))));
    }

    // ---------- Idempotency-Key ----------

    @Test
    @DisplayName("POST repetido com a mesma Idempotency-Key devolve a mesma resposta e não cria outro registro")
    void idempotentPostReplaysOriginalResponse() throws Exception {
        String key = "it-" + java.util.UUID.randomUUID();
        long before = totalEvaluations();

        String first = mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Idempotent-Replayed"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        String second = mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat((String) JsonPath.read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        assertThat(second).isEqualTo(first);
        assertThat(totalEvaluations()).as("só um registro novo").isEqualTo(before + 1);
    }

    @Test
    @DisplayName("sem Idempotency-Key cada POST cria um registro novo")
    void postWithoutKeyIsNotIdempotent() throws Exception {
        long before = totalEvaluations();

        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());

        assertThat(totalEvaluations()).isEqualTo(before + 2);
    }

    @Test
    @DisplayName("mesma Idempotency-Key com corpo diferente -> 422")
    void sameKeyDifferentBodyReturns422() throws Exception {
        String key = "it-" + java.util.UUID.randomUUID();
        mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());

        mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(REJECTED))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Idempotency-Key")));
    }

    @Test
    @DisplayName("Idempotency-Key inválida -> 400 sem ecoar o valor e sem criar registro")
    void invalidKeyReturns400() throws Exception {
        long before = totalEvaluations();

        mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", "chave com espaço")
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("chave com espaço"))));

        assertThat(totalEvaluations()).isEqualTo(before);
    }

    @Test
    @DisplayName("lote repetido com a mesma chave não duplica o relatório")
    void idempotentBatchDoesNotDuplicateReport() throws Exception {
        String key = "it-" + java.util.UUID.randomUUID();
        long before = totalEvaluations();

        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/v1/credit-evaluations/batch").header("Idempotency-Key", key)
                            .contentType(MediaType.APPLICATION_JSON).content(sample()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.total").value(8))
                    .andExpect(jsonPath("$.totalApprovedAmount").value(22382.99));
        }

        assertThat(totalEvaluations()).as("8 novos registros, não 16").isEqualTo(before + 8);
    }

    // ---------- traceId ----------

    @Test
    @DisplayName("toda resposta traz X-Trace-Id; o enviado pelo cliente é preservado")
    void responseCarriesTraceId() throws Exception {
        mvc.perform(post("/v1/credit-evaluations").header("X-Trace-Id", "cliente-abc-123")
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "cliente-abc-123"));

        String generated = mvc.perform(post("/v1/credit-evaluations")
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andReturn().getResponse().getHeader("X-Trace-Id");
        assertThat(generated).matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("respostas de erro repetem o mesmo traceId no corpo (400, 404, 405 e 422)")
    void errorBodiesCarryTheSameTraceId() throws Exception {
        String bad = HEALTHY.replace("\"creditScore\":820", "\"creditScore\":2000");

        mvc.perform(post("/v1/credit-evaluations").header("X-Trace-Id", "trace-400")
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId").value("trace-400"));

        mvc.perform(get("/v1/credit-evaluations/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("X-Trace-Id", "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").value("trace-404"));

        mvc.perform(delete("/v1/credit-evaluations/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("X-Trace-Id", "trace-405"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.traceId").value("trace-405"));

        String key = "it-" + java.util.UUID.randomUUID();
        mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(HEALTHY)).andExpect(status().isCreated());
        mvc.perform(post("/v1/credit-evaluations").header("Idempotency-Key", key).header("X-Trace-Id", "trace-422")
                        .contentType(MediaType.APPLICATION_JSON).content(REJECTED))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.traceId").value("trace-422"));
    }

    @Test
    @DisplayName("X-Trace-Id malicioso é substituído e nunca ecoado")
    void maliciousTraceIdIsNeverEchoed() throws Exception {
        String echoed = mvc.perform(post("/v1/credit-evaluations").header("X-Trace-Id", "falso;ERROR forjado")
                        .contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("X-Trace-Id");

        assertThat(echoed).matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("o endpoint do Prometheus expõe as métricas de negócio")
    void prometheusEndpointExposesBusinessMetrics() throws Exception {
        mvc.perform(post("/v1/credit-evaluations").contentType(MediaType.APPLICATION_JSON).content(HEALTHY))
                .andExpect(status().isCreated());

        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("credit_evaluations_total")))
                .andExpect(content().string(containsString("credit_evaluation_duration_seconds_bucket")))
                .andExpect(content().string(containsString("application=\"credito-rotativo\"")));
    }

    // ---------- Só POST e GET ----------

    @Test
    @DisplayName("PUT, PATCH e DELETE não são permitidos (405)")
    void otherMethodsAreNotAllowed() throws Exception {
        String url = "/v1/credit-evaluations/00000000-0000-0000-0000-000000000000";
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(HEALTHY)).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(url).contentType(MediaType.APPLICATION_JSON).content(HEALTHY)).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(url)).andExpect(status().isMethodNotAllowed());
    }

    private long totalEvaluations() throws Exception {
        String json = mvc.perform(get("/v1/credit-evaluations").param("size", "1"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(json, "$.totalElements")).longValue();
    }

    private static String sample() throws Exception {
        return new String(new ClassPathResource("sample-customers.json").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }
}
