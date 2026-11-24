package br.com.creditorotativo.application;

import java.util.UUID;

public class EvaluationNotFoundException extends RuntimeException {

    public EvaluationNotFoundException(UUID id) {
        super("Avaliação " + id + " não encontrada");
    }
}
