package br.com.creditorotativo.infrastructure.observability;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Regra de resolução do identificador de correlação. Aceita o {@code X-Trace-Id} enviado pelo cliente apenas se
 * tiver formato seguro; caso contrário gera um novo. Nunca rejeita a requisição por causa do header, e nunca ecoa
 * um valor inválido: o id vai para logs e para a resposta, então aceitar texto livre permitiria injeção de linhas
 * falsas nos logs (log forging) ou de caracteres de controle no header.
 */
public final class TraceIds {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    private TraceIds() {
    }

    public static String resolve(String inbound) {
        if (inbound != null && VALID.matcher(inbound).matches()) {
            return inbound;
        }
        return generate();
    }

    /** 32 caracteres hexadecimais, o mesmo formato do traceId do W3C Trace Context / OpenTelemetry. */
    static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
