package br.com.creditorotativo.application;

/** Resultado de um caso de uso de escrita, indicando se veio de um replay idempotente. */
public record Outcome<T>(T value, boolean replayed) {
}
