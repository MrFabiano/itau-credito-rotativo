package br.com.creditorotativo.controller;

import br.com.creditorotativo.service.QueryCreditEvaluationsService;
import br.com.creditorotativo.application.ReportFormatter;
import br.com.creditorotativo.controller.response.EvaluationResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Relatório consolidado (item 5 do desafio): aprovados, reprovados e valores liberados. */
@RestController
@RequestMapping("/v1/reports/credit-evaluations")
public class ReportController {

    private final QueryCreditEvaluationsService queryUseCase;

    public ReportController(QueryCreditEvaluationsService queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public EvaluationResponse.Batch report() {
        return EvaluationResponse.Batch.from(queryUseCase.report());
    }

    @GetMapping(value = "/csv", produces = CreditEvaluationController.TEXT_CSV)
    public String reportCsv() {
        return ReportFormatter.toCsv(queryUseCase.report());
    }
}
