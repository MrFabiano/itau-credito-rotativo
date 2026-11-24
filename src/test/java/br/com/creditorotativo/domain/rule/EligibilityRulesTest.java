package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static br.com.creditorotativo.domain.rule.Fixtures.customer;
import static br.com.creditorotativo.domain.rule.Fixtures.policy;
import static org.assertj.core.api.Assertions.assertThat;

/** Testes de fronteira: o erro típico em regra de crédito é o "maior ou igual" trocado por "maior". */
class EligibilityRulesTest {

    private final CreditPolicy policy = policy();

    @ParameterizedTest(name = "score {0} -> passa={1}")
    @CsvSource({"499,false", "500,true", "501,true", "0,false", "1000,true"})
    @DisplayName("MinimumScore: mínimo é inclusivo")
    void minimumScore(int score, boolean expected) {
        Customer c = customer(score, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00");
        assertThat(new MinimumScoreRule(policy).evaluate(c).passed()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} atrasos -> passa={1}")
    @CsvSource({"0,true", "2,true", "3,false", "10,false"})
    @DisplayName("MaxLatePayments: máximo é inclusivo")
    void maxLatePayments(int late, boolean expected) {
        Customer c = customer(700, "1000.00", "5000.00", late, "1000.00", "1000.00", "1000.00");
        assertThat(new MaxLatePaymentsRule(policy).evaluate(c).passed()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "fatura {0} / disponível {1} -> passa={2}")
    @CsvSource({
            "8500.00,1500.00,true",    // exatamente 85%
            "8501.00,1499.00,false",   // 85,01%
            "0.00,1000.00,true",
            "1000.00,0.00,false"       // 100% utilizado
    })
    @DisplayName("InvoiceUtilization: 85% é o limite máximo aceito")
    void invoiceUtilization(String invoice, String available, boolean expected) {
        Customer c = customer(700, invoice, available, 0, "1000.00", "1000.00", "1000.00");
        assertThat(new InvoiceUtilizationRule(policy).evaluate(c).passed()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "gastos {0} -> passa={1}")
    @CsvSource({
            "'1000.00;1000.00',false",                  // só 2 meses: histórico insuficiente
            "'1000.00;1000.00;1000.00',true",           // exatamente 3 meses
            "'1000.00;1000.00;2000.00',true",           // último mês = 2x a média anterior (limite)
            "'1000.00;1000.00;2001.00',false",          // acima de 2x: pico
            "'0.00;0.00;5000.00',true"                  // sem base de comparação: não bloqueia por pico
    })
    @DisplayName("SpendingBehavior: histórico mínimo e pico de gastos")
    void spendingBehavior(String spending, boolean expected) {
        Customer c = customer(700, "1000.00", "5000.00", 0, spending.split(";"));
        assertThat(new SpendingBehaviorRule(policy).evaluate(c).passed()).isEqualTo(expected);
    }

    @ParameterizedTest(name = "disponível {0} -> passa={1}")
    @CsvSource({"499.99,false", "500.00,true", "500.01,true"})
    @DisplayName("MinimumAvailableLimit: mínimo é inclusivo")
    void minimumAvailableLimit(String available, boolean expected) {
        Customer c = customer(700, "0.00", available, 0, "1000.00", "1000.00", "1000.00");
        assertThat(new MinimumAvailableLimitRule(policy).evaluate(c).passed()).isEqualTo(expected);
    }

    @org.junit.jupiter.api.Test
    @DisplayName("falha sempre carrega o motivo, com os valores envolvidos")
    void failureAlwaysHasReason() {
        Customer c = customer(430, "1000.00", "5000.00", 0, "1000.00", "1000.00", "1000.00");
        var result = new MinimumScoreRule(policy).evaluate(c);
        assertThat(result.passed()).isFalse();
        assertThat(result.reason()).contains("430").contains("500");
    }
}
