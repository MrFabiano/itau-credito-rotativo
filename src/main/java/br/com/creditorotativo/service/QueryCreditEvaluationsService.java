package br.com.creditorotativo.service;

import br.com.creditorotativo.application.CreditReport;
import br.com.creditorotativo.application.EvaluationNotFoundException;
import br.com.creditorotativo.application.Page;
import br.com.creditorotativo.domain.enums.DecisionStatus;
import br.com.creditorotativo.domain.model.CreditEvaluation;
import br.com.creditorotativo.repository.EvaluationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Lado de leitura (GET): consulta por id, listagem filtrada e paginada, e relatório consolidado. */
@Service
public class QueryCreditEvaluationsService {

    static final int MAX_PAGE_SIZE = 100;

    private final EvaluationRepository repository;

    public QueryCreditEvaluationsService(EvaluationRepository repository) {
        this.repository = repository;
    }

    public CreditEvaluation findById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new EvaluationNotFoundException(id));
    }

    /** @param status filtro opcional; {@code null} traz tudo */
    public Page<CreditEvaluation> search(DecisionStatus status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page deve ser maior ou igual a 0");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size deve estar entre 1 e " + MAX_PAGE_SIZE);
        }
        List<CreditEvaluation> filtered = repository.findAllNewestFirst().stream()
                .filter(e -> status == null || status.matches(e.decision()))
                .toList();

        long from = (long) page * size;
        List<CreditEvaluation> content = from >= filtered.size()
                ? List.of()
                : filtered.subList((int) from, (int) Math.min(from + size, filtered.size()));
        return new Page<>(content, page, size, filtered.size());
    }

    /** Relatório de todas as avaliações guardadas: aprovados, reprovados e valores liberados. */
    public CreditReport report() {
        return new CreditReport(repository.findAllNewestFirst());
    }
}
