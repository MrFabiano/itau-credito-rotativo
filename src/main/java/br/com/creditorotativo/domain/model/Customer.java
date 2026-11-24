package br.com.creditorotativo.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Perfil do cliente usado na avaliação.
 *
 * @param monthlySpending gastos mensais do mais antigo para o mais recente
 */
public record Customer(
        String name,
        Cpf cpf,
        int creditScore,
        Money currentInvoice,
        Money availableLimit,
        int latePayments,
        List<Money> monthlySpending) {

    public Customer {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(cpf, "cpf");
        Objects.requireNonNull(currentInvoice, "currentInvoice");
        Objects.requireNonNull(availableLimit, "availableLimit");
        if (latePayments < 0) {
            throw new IllegalArgumentException("latePayments não pode ser negativo");
        }
        monthlySpending = List.copyOf(Objects.requireNonNull(monthlySpending, "monthlySpending"));
    }

    /**
     * Limite total estimado = fatura atual + limite disponível.
     * Suposição documentada: o enunciado não informa o limite total do cartão.
     */
    public Money estimatedTotalLimit() {
        return currentInvoice.plus(availableLimit);
    }

    /** Fatura atual / limite total estimado. Zero se não houver limite. */
    public BigDecimal utilization() {
        Money total = estimatedTotalLimit();
        if (total.isZero()) {
            return BigDecimal.ONE;
        }
        return currentInvoice.value().divide(total.value(), 4, RoundingMode.HALF_EVEN);
    }

    public Money averageSpending() {
        if (monthlySpending.isEmpty()) {
            return Money.ZERO;
        }
        BigDecimal sum = monthlySpending.stream().map(Money::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(sum.divide(BigDecimal.valueOf(monthlySpending.size()), 2, RoundingMode.DOWN));
    }

    /** Média dos meses anteriores ao último. Zero se houver menos de 2 meses de histórico. */
    public Money averageSpendingBeforeLastMonth() {
        if (monthlySpending.size() < 2) {
            return Money.ZERO;
        }
        List<Money> previous = monthlySpending.subList(0, monthlySpending.size() - 1);
        BigDecimal sum = previous.stream().map(Money::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        return Money.of(sum.divide(BigDecimal.valueOf(previous.size()), 2, RoundingMode.DOWN));
    }

    public Money lastMonthSpending() {
        return monthlySpending.isEmpty() ? Money.ZERO : monthlySpending.get(monthlySpending.size() - 1);
    }
}
