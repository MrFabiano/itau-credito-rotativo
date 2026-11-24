package br.com.creditorotativo.infrastructure;

import br.com.creditorotativo.application.CreditReport;
import br.com.creditorotativo.application.ReportFormatter;
import br.com.creditorotativo.controller.request.BatchRequest;
import br.com.creditorotativo.controller.request.CustomerRequest;
import br.com.creditorotativo.service.EvaluateCreditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Executa o desafio de ponta a ponta sem HTTP: lê clientes fictícios, avalia, imprime o relatório
 * e grava report.csv. Ativado com {@code --spring.profiles.active=demo}.
 */
@Component
@Profile("demo")
public class DemoRunner implements ApplicationRunner {

    private final EvaluateCreditService evaluateCreditService;
    private final ObjectMapper mapper;

    public DemoRunner(EvaluateCreditService evaluateCreditService, ObjectMapper mapper) {
        this.evaluateCreditService = evaluateCreditService;
        this.mapper = mapper;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (InputStream in = new ClassPathResource("sample-customers.json").getInputStream()) {
            BatchRequest batch = mapper.readValue(in, BatchRequest.class);
            CreditReport report = evaluateCreditService.evaluateBatch(
                    batch.customers().stream().map(CustomerRequest::toDomain).toList());

            System.out.println(ReportFormatter.toText(report));
            Path csv = Path.of("report.csv");
            Files.writeString(csv, ReportFormatter.toCsv(report), StandardCharsets.UTF_8);
            System.out.println("Relatório CSV gravado em " + csv.toAbsolutePath());
        }
    }
}
