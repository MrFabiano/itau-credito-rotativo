package br.com.creditorotativo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CreditoRotativoApplication {

    public static void main(String[] args) {
        SpringApplication.run(CreditoRotativoApplication.class, args);
    }
}
