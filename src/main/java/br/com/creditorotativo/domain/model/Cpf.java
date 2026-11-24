package br.com.creditorotativo.domain.model;

/**
 * CPF validado (dígitos verificadores). Nunca expõe o número completo em {@link #toString()} nem em logs:
 * use {@link #masked()} para relatórios e respostas.
 */
public record Cpf(String digits) {

    public Cpf {
        if (digits == null) {
            throw new IllegalArgumentException("CPF é obrigatório");
        }
        digits = digits.replaceAll("\\D", "");
        if (!isValid(digits)) {
            throw new IllegalArgumentException("CPF inválido");
        }
    }

    public static Cpf of(String raw) {
        return new Cpf(raw);
    }

    /** Ex.: 123.456.789-09 -> ***.456.789-** */
    public String masked() {
        return "***." + digits.substring(3, 6) + "." + digits.substring(6, 9) + "-**";
    }

    private static boolean isValid(String d) {
        if (d.length() != 11 || d.chars().distinct().count() == 1) {
            return false;
        }
        return checkDigit(d, 9) == d.charAt(9) - '0' && checkDigit(d, 10) == d.charAt(10) - '0';
    }

    private static int checkDigit(String d, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (d.charAt(i) - '0') * (length + 1 - i);
        }
        return (sum * 10 % 11) % 10;
    }

    @Override
    public String toString() {
        return masked();
    }
}
