# ADR-002: Strategy com descoberta automática, em vez de Chain of Responsibility ou motor de regras externo

**Status:** aceita

## Contexto
É preciso adicionar e alterar regras sem refatorar o código base. Também é preciso explicar cada decisão.

## Decisão
- Duas abstrações: `EligibilityRule` ("pode?") e `LimitPolicy` ("quanto?"), cada regra um `@Component`.
- O `RuleEngine` recebe todas por injeção de lista e as ordena por `order()`.
- Avalia **todas** as regras (sem curto-circuito) e devolve todos os motivos.
- Cada `LimitPolicy` só pode **reduzir** o limite; o motor aplica `min` como defesa em profundidade.
- Limiares em `application.yml` (`CreditPolicy`), com versão gravada em cada decisão.

## Alternativas
- **Chain of Responsibility:** adequada quando a ordem muda o resultado ou há desvio de fluxo. Aqui as regras são
  independentes, então a cadeia só adicionaria acoplamento e esconderia motivos após o primeiro bloqueio.
- **Drools / DMN:** poderosos, mas com curva de aprendizado e operação desproporcionais. Fica como evolução se
  analistas de negócio precisarem editar regras sem deploy.
- **Fail-fast:** mais rápido em teoria, mas o ganho é desprezível em memória e perde-se explicabilidade.

## Consequências
+ Regra nova = classe nova; testes isolados; relatório com todos os motivos.
+ O domínio usa o estereótipo `@Component` do Spring para a descoberta: concessão consciente de pureza,
  limitada a essa anotação e verificada pelo `ArchitectureTest`.
− Ordem entre regras eliminatórias só afeta a ordem dos motivos; ordem entre políticas de limite importa pouco por serem
  redutoras, mas arredondamentos intermediários podem diferir em centavos.
