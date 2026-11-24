package br.com.creditorotativo.infrastructure.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    private MockFilterChain chainCapturing(AtomicReference<String> mdcDuringRequest) {
        return new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                mdcDuringRequest.set(MDC.get("traceId"));
            }
        };
    }

    @Test
    @DisplayName("durante a requisição o traceId está no MDC e a resposta o devolve no header")
    void putsTraceIdInMdcAndResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "pedido-0001");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, chainCapturing(seen));

        assertThat(seen.get()).isEqualTo("pedido-0001");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("pedido-0001");
    }

    @Test
    @DisplayName("sem header gera um traceId e o devolve")
    void generatesWhenMissing() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(new MockHttpServletRequest(), response, chainCapturing(seen));

        assertThat(seen.get()).matches("^[0-9a-f]{32}$");
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(seen.get());
    }

    @Test
    @DisplayName("header malicioso é descartado e não aparece na resposta")
    void discardsMaliciousHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "abc\nERROR forjado");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).matches("^[0-9a-f]{32}$").doesNotContain("forjado");
    }

    @Test
    @DisplayName("o MDC é limpo ao final, para a thread do pool não vazar o id para a próxima requisição")
    void clearsMdcAfterRequest() throws Exception {
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(MDC.get("traceId")).isNull();
    }

    @Test
    @DisplayName("o MDC é limpo mesmo quando a requisição lança exceção")
    void clearsMdcOnFailure() {
        MockFilterChain failing = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
                throw new IllegalStateException("falha");
            }
        };

        assertThatThrownBy(() -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        assertThat(MDC.get("traceId")).isNull();
    }
}
