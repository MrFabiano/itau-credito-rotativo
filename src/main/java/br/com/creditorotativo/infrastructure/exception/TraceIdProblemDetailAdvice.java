package br.com.creditorotativo.infrastructure.exception;

import br.com.creditorotativo.infrastructure.observability.TraceIdFilter;
import org.jboss.logging.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Map;

@RestControllerAdvice
public class TraceIdProblemDetailAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ProblemDetail problem) {
            String traceId = MDC.get(TraceIdFilter.MDC_KEY).toString();
            Map<String, Object> properties = problem.getProperties();
            if (traceId != null && (properties == null || !properties.containsKey("traceId"))) {
                problem.setProperty("traceId", traceId);
            }
        }
        return body;
    }
}