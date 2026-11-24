package br.com.creditorotativo.infrastructure.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdsTest {

    @Test
    @DisplayName("sem header gera um id de 32 caracteres hexadecimais")
    void generatesWhenAbsent() {
        assertThat(TraceIds.resolve(null)).matches("^[0-9a-f]{32}$");
    }

    @Test
    @DisplayName("ids gerados são únicos")
    void generatedIdsAreUnique() {
        assertThat(TraceIds.resolve(null)).isNotEqualTo(TraceIds.resolve(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "pedido-0001", "req.2026_11-17", "4bf92f3577b34da6a3ce929d0e0e4736"})
    @DisplayName("aceita e preserva um id enviado pelo cliente com formato seguro")
    void keepsValidInboundId(String inbound) {
        assertThat(TraceIds.resolve(inbound)).isEqualTo(inbound);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "com espaço", "linha1\nlinha2", "falso\r\nERROR forjado", "a;b", "<script>",
            "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"})
    @DisplayName("valor inválido é descartado (nunca ecoado) e substituído por um id novo")
    void replacesInvalidInboundId(String inbound) {
        String resolved = TraceIds.resolve(inbound);

        assertThat(resolved).isNotEqualTo(inbound).matches("^[0-9a-f]{32}$");
    }
}
