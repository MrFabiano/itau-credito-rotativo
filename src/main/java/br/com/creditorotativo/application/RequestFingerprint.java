package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.Customer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Resumo SHA-256 do conteúdo de uma requisição. Serve para distinguir "retry da mesma requisição"
 * (devolve a resposta original) de "mesma chave com corpo diferente" (erro 422).
 *
 * <p>Só o hash é guardado; o CPF completo nunca é armazenado. Inclui o tipo da operação, para que uma
 * mesma chave não possa ser reaproveitada entre o endpoint individual e o de lote.
 */
public final class RequestFingerprint {

    private static final char SEPARATOR = '\u0000';

    private RequestFingerprint() {
    }

    public static String of(String operation, List<Customer> customers) {
        StringBuilder canonical = new StringBuilder(operation);
        for (Customer c : customers) {
            canonical.append(SEPARATOR).append(c.name())
                    .append(SEPARATOR).append(c.cpf().digits())
                    .append(SEPARATOR).append(c.creditScore())
                    .append(SEPARATOR).append(c.currentInvoice())
                    .append(SEPARATOR).append(c.availableLimit())
                    .append(SEPARATOR).append(c.latePayments());
            c.monthlySpending().forEach(m -> canonical.append(SEPARATOR).append(m));
            canonical.append('\n');
        }
        return sha256Hex(canonical.toString());
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
