# ADR-003: Precisão monetária e falha segura (fail-closed)

**Status:** aceita

## Contexto
O requisito de precisão pede evitar liberações indevidas e bloqueios incorretos.

## Decisão
- Dinheiro é sempre `Money` sobre `BigDecimal`, 2 casas, `RoundingMode.DOWN`. Nunca `double`.
  Em crédito, o erro de arredondamento nunca deve favorecer uma liberação maior.
- Decisão determinística e versionada: mesma entrada + mesma `ruleSetVersion` = mesma saída, o que permite
  reproduzir qualquer decisão em auditoria.
- Falha segura: sem faixa de score correspondente o limite é zero; limite final zero vira **reprovação**
  (nunca "aprovado com R$ 0,00"); na evolução com dependências externas, indisponibilidade nunca aprova.
- Invariantes verificadas por testes de propriedade (limite > 0, ≤ disponível, ≤ teto).

## Consequências
+ Comportamento previsível e auditável.
− Pode negar clientes na fronteira por centavos; é o lado seguro do erro.
− Em indisponibilidade de dependência, a disponibilidade é sacrificada em favor da integridade da decisão.
