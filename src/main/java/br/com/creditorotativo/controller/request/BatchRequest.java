package br.com.creditorotativo.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Lote de clientes. Limite de tamanho protege a API contra payloads abusivos. */
public record BatchRequest(@NotEmpty @Size(max = 5000) List<@Valid CustomerRequest> customers) {
}
