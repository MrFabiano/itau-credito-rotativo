package br.com.creditorotativo.application;

import java.util.List;

public record Page<T>(List<T> content, int page, int size, long totalElements) {

    public Page {
        content = List.copyOf(content);
    }
}
