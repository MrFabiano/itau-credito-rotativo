package br.com.creditorotativo.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Resultado de uma avaliação, identificável e consultável depois (GET).
 * Guarda só o necessário: nome e CPF mascarado, nunca o CPF completo (minimização de dados, LGPD).
 */
public record CreditEvaluation(UUID id, Instant evaluatedAt, String customerName, String maskedCpf, Decision decision) {

    public static CreditEvaluation of(Customer customer, Decision decision) {
        return new CreditEvaluation(UUID.randomUUID(), Instant.now(), customer.name(), customer.cpf().masked(), decision);
    }
}
