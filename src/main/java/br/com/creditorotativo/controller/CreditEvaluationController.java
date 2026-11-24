package br.com.creditorotativo.controller;

import br.com.creditorotativo.application.CreditReport;
import br.com.creditorotativo.domain.enums.DecisionStatus;

import br.com.creditorotativo.application.Outcome;
import br.com.creditorotativo.service.EvaluateCreditService;
import br.com.creditorotativo.service.QueryCreditEvaluationsService;
import br.com.creditorotativo.application.ReportFormatter;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.controller.request.BatchRequest;
import br.com.creditorotativo.controller.request.CustomerRequest;
import br.com.creditorotativo.controller.response.EvaluationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Apenas POST (criar avaliação) e GET (consultar). Sem PUT/PATCH/DELETE: decisões são registros imutáveis.
 *
 * <p>Os POSTs aceitam o header opcional {@code Idempotency-Key}. Repetir a mesma requisição com a mesma chave
 * devolve a resposta original (header {@code Idempotent-Replayed: true}) sem criar outro registro.
 */
@RestController
@RequestMapping("/v1/credit-evaluations")
public class CreditEvaluationController {

    static final String TEXT_CSV = "text/csv";
    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    private final EvaluateCreditService evaluateCreditService;
    private final QueryCreditEvaluationsService queryCreditEvaluationsService;

    public CreditEvaluationController(EvaluateCreditService evaluateCreditService, QueryCreditEvaluationsService queryCreditEvaluationsService) {
        this.evaluateCreditService = evaluateCreditService;
        this.queryCreditEvaluationsService = queryCreditEvaluationsService;
    }

    /** Caminho online: uma decisão por requisição, meta de resposta abaixo de 1s. */
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvaluationResponse> evaluate(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CustomerRequest request) {
        var outcome = evaluateCreditService.evaluate(request.toDomain(), idempotencyKey);
        return created(EvaluationResponse.from(outcome.value()), outcome.replayed());
    }

    @PostMapping(value = "/batch", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<EvaluationResponse.Batch> evaluateBatch(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody BatchRequest request) {
        Outcome<CreditReport> outcome = evaluateAll(request, idempotencyKey);
        return created(EvaluationResponse.Batch.from(outcome.value()), outcome.replayed());
    }

    /** Mesmo lote, relatório em CSV. Rota própria: evita ambiguidade de negociação de conteúdo sem header Accept. */
    @PostMapping(value = "/batch/csv", produces = TEXT_CSV)
    public ResponseEntity<String> evaluateBatchCsv(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody BatchRequest request) {
        Outcome<CreditReport> outcome = evaluateAll(request, idempotencyKey);
        return created(ReportFormatter.toCsv(outcome.value()), outcome.replayed());
    }

    /** Consulta uma avaliação já realizada. 404 se não existir. */
    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public EvaluationResponse findById(@PathVariable UUID id) {
        return EvaluationResponse.from(queryCreditEvaluationsService.findById(id));
    }

    /** Lista avaliações, da mais recente para a mais antiga. Filtro opcional {@code decision=APPROVED|REJECTED}. */
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public EvaluationResponse.Paged list(
            @RequestParam(required = false) DecisionStatus decision,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return EvaluationResponse.Paged.from(queryCreditEvaluationsService.search(decision, page, size));
    }

    private Outcome<CreditReport> evaluateAll(BatchRequest request, String idempotencyKey) {
        List<Customer> customers = request.customers().stream().map(CustomerRequest::toDomain).toList();
        return evaluateCreditService.evaluateBatch(customers, idempotencyKey);
    }

    /** Sempre 201, inclusive no replay: o cliente recebe exatamente a mesma resposta da primeira vez. */
    private static <T> ResponseEntity<T> created(T body, boolean replayed) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
        if (replayed) {
            response.header(IDEMPOTENT_REPLAYED, "true");
        }
        return response.body(body);
    }
}
