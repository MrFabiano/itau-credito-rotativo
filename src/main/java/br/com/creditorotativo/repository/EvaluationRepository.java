package br.com.creditorotativo.repository;

import br.com.creditorotativo.domain.model.CreditEvaluation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Porta de saída para guardar avaliações. A implementação atual é em memória; trocar por JPA não afeta o caso de uso. */
public interface EvaluationRepository {

    void save(CreditEvaluation evaluation);

    Optional<CreditEvaluation> findById(UUID id);

    /** Da mais recente para a mais antiga. */
    List<CreditEvaluation> findAllNewestFirst();
}
