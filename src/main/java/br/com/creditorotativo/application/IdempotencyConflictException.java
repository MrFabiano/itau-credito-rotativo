package br.com.creditorotativo.application;

/** A mesma Idempotency-Key foi reutilizada com uma requisição diferente: erro do cliente, não do servidor. */
public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("Idempotency-Key já utilizada com uma requisição diferente");
    }
}
