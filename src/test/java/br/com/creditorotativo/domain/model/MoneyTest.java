package br.com.creditorotativo.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MoneyTest {

    @Test
    @DisplayName("arredonda sempre para baixo (nunca a favor de uma liberação maior)")
    void roundsDown() {
        assertThat(Money.of("10.999")).isEqualTo(Money.of("10.99"));
        assertThat(Money.of("100").multiply(new BigDecimal("0.3333"))).isEqualTo(Money.of("33.33"));
        assertThat(Money.of("0.019").toString()).isEqualTo("0.01");
    }

    @Test
    void comparesAndMin() {
        assertThat(Money.of("10.00").min(Money.of("5.00"))).isEqualTo(Money.of("5.00"));
        assertThat(Money.of("10.00").isGreaterThan(Money.of("9.99"))).isTrue();
        assertThat(Money.of("0.00").isZero()).isTrue();
    }

    @Test
    void sums() {
        assertThat(Money.of("1.10").plus(Money.of("2.20"))).isEqualTo(Money.of("3.30"));
    }
}
