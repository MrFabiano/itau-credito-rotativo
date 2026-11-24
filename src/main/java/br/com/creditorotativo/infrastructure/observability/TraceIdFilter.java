package br.com.creditorotativo.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cria (ou reaproveita) um {@code traceId} por requisição e o coloca no MDC, de onde o padrão de log o lê.
 * Também o devolve no header {@code X-Trace-Id}, para o cliente citar o id ao reportar um problema.
 *
 * <p>Isto é um <b>identificador de correlação</b>, não tracing distribuído: não há spans nem propagação entre
 * serviços. A chave de MDC {@code traceId} é a mesma que o Micrometer Tracing usa, então migrar para OpenTelemetry
 * mantém logs e consultas compatíveis (ver docs/observability.md).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = TraceIds.resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            // A thread volta para o pool: sem limpar, o id vazaria para a próxima requisição.
            MDC.remove(MDC_KEY);
        }
    }
}
