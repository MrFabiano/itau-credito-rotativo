package br.com.creditorotativo.application;

import br.com.creditorotativo.domain.model.CreditEvaluation;

import java.util.List;
import java.util.function.Supplier;

/**
 * Porta de saída da idempotência. O contrato é <b>atômico</b>: para uma mesma chave, o {@code creator} roda
 * no máximo uma vez, mesmo com requisições concorrentes. É o equivalente, em memória, de um
 * {@code INSERT} protegido por constraint única no banco.
 */
public interface IdempotencyStore {

    /**
     * @param key         chave enviada pelo cliente (já validada)
     * @param fingerprint resumo do conteúdo da requisição, para detectar reuso da chave com corpo diferente
     * @param creator     cria as avaliações; só é chamado se a chave ainda não existir
     * @return as avaliações criadas agora ({@code replayed=false}) ou as da primeira vez ({@code replayed=true})
     * @throws IdempotencyConflictException se a chave já existe com outro {@code fingerprint}
     */
    Result execute(String key, String fingerprint, Supplier<List<CreditEvaluation>> creator);

    record Result(List<CreditEvaluation> evaluations, boolean replayed) {
    }
}
