package br.com.creditorotativo.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário em reais. Sempre com 2 casas decimais e arredondamento {@link RoundingMode#DOWN}:
 * em decisão de crédito, o erro de arredondamento nunca pode ser a favor de uma liberação maior.
 */
public record Money(BigDecimal value) implements Comparable<Money> {

    public static final Money ZERO = new Money(BigDecimal.ZERO);

    public Money {
        Objects.requireNonNull(value, "value");
        value = value.setScale(2, RoundingMode.DOWN);
    }

    public static Money of(String amount) {
        return new Money(new BigDecimal(amount));
    }

    public static Money of(BigDecimal amount) {
        return new Money(amount);
    }

    public Money plus(Money other) {
        return new Money(value.add(other.value));
    }

    public Money multiply(BigDecimal factor) {
        return new Money(value.multiply(factor));
    }

    public Money min(Money other) {
        return compareTo(other) <= 0 ? this : other;
    }

    public boolean isGreaterThan(Money other) {
        return compareTo(other) > 0;
    }

    public boolean isLessThan(Money other) {
        return compareTo(other) < 0;
    }

    public boolean isZero() {
        return value.signum() == 0;
    }

    @Override
    public int compareTo(Money other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value.toPlainString();
    }
}
