package br.com.creditorotativo.controller.request;

import br.com.creditorotativo.domain.model.Cpf;
import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Contrato de entrada. Validação na borda; o domínio ainda se protege com suas próprias invariantes. */
public record CustomerRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank String cpf,
        @Min(0) @Max(1000) int creditScore,
        @NotNull @PositiveOrZero BigDecimal currentInvoice,
        @NotNull @PositiveOrZero BigDecimal availableLimit,
        @Min(0) int latePayments,
        @NotEmpty @Size(max = 24) List<@NotNull @PositiveOrZero BigDecimal> monthlySpending) {

    public Customer toDomain() {
        return new Customer(
                name,
                Cpf.of(cpf),
                creditScore,
                Money.of(currentInvoice),
                Money.of(availableLimit),
                latePayments,
                monthlySpending.stream().map(Money::of).toList());
    }
}
