package br.com.creditorotativo.domain.rule;

import br.com.creditorotativo.domain.model.Customer;
import br.com.creditorotativo.domain.model.Money;

/**
 * Política de limite: responde "quanto?". Recebe o limite calculado até aqui e devolve um valor
 * que nunca pode ser maior que ele (cada política só reduz). Isso garante, por construção,
 * que nenhuma política nova consiga liberar mais do que as anteriores permitiram.
 */
public interface LimitPolicy {

    String name();

    default int order() {
        return 100;
    }

    /** @return valor menor ou igual a {@code current} */
    Money apply(Customer customer, Money current);
}
