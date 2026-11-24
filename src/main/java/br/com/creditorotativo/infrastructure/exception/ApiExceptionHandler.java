package br.com.creditorotativo.infrastructure.exception;

import br.com.creditorotativo.application.EvaluationNotFoundException;
import br.com.creditorotativo.application.IdempotencyConflictException;
import br.com.creditorotativo.infrastructure.observability.TraceIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Erros no padrão RFC 7807 (ProblemDetail). Estende {@link ResponseEntityExceptionHandler} para que erros
 * padrão do MVC (405, 415, corpo ilegível...) mantenham o status correto em vez de virar 500.
 * Mensagens nunca ecoam o valor recebido, para não vazar dados pessoais (ex.: CPF) em respostas ou logs.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Toda resposta de erro leva o traceId, para o cliente citá-lo e a equipe achar a requisição nos logs. */
    private static ProblemDetail withTrace(ProblemDetail problem) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        if (traceId != null) {
            problem.setProperty("traceId", traceId);
        }
        return problem;
    }

    /** Cobre também os erros padrão do MVC tratados pela classe base (405, 415, tipo inválido...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            withTrace(problem);
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requisição inválida");
        List<String> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().headers(headers).body(withTrace(problem));
    }

    /** Mesma Idempotency-Key com requisição diferente: 422, pois o servidor entendeu mas não pode processar. */
    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail idempotencyConflict(IdempotencyConflictException ex) {
        return withTrace(ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage()));
    }

    @ExceptionHandler(EvaluationNotFoundException.class)
    ProblemDetail notFound(EvaluationNotFoundException ex) {
        return withTrace(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage()));
    }

    /** Invariantes do domínio violadas (ex.: CPF inválido). */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail domainValidation(IllegalArgumentException ex) {
        return withTrace(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception ex) {
        log.error("Erro inesperado na avaliação de crédito", ex);
        return withTrace(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno"));
    }
}
