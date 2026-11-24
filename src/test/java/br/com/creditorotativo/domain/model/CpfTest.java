package br.com.creditorotativo.domain.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CpfTest {

    @ParameterizedTest
    @ValueSource(strings = {"12345678909", "123.456.789-09"})
    void acceptsValidCpfWithOrWithoutFormatting(String raw) {
        assertThat(Cpf.of(raw).digits()).isEqualTo("12345678909");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678900", "11111111111", "123", "abc", ""})
    @NullSource
    void rejectsInvalidCpf(String raw) {
        assertThatThrownBy(() -> Cpf.of(raw)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverExposesFullNumberInMaskOrToString() {
        Cpf cpf = Cpf.of("12345678909");
        assertThat(cpf.masked()).isEqualTo("***.456.789-**");
        assertThat(cpf.toString()).doesNotContain("12345678909").isEqualTo(cpf.masked());
    }
}
