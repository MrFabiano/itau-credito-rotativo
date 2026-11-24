package br.com.creditorotativo.infrastructure.config;

import br.com.creditorotativo.domain.rule.CreditPolicy;
import br.com.creditorotativo.domain.rule.EligibilityRule;
import br.com.creditorotativo.domain.rule.LimitPolicy;
import br.com.creditorotativo.domain.rule.RuleEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Monta o motor com TODAS as regras e políticas encontradas no contexto.
 * Adicionar uma regra nova = criar uma classe {@code @Component}; nada aqui precisa mudar.
 */
@Configuration
public class RuleEngineConfig {

    @Bean
    CreditPolicy creditPolicy(CreditPolicyProperties properties) {
        return properties.toDomain();
    }

    @Bean
    RuleEngine ruleEngine(List<EligibilityRule> rules, List<LimitPolicy> policies, CreditPolicy creditPolicy) {
        return new RuleEngine(rules, policies, creditPolicy.version());
    }
}
