# ADR-001: Monolito modular com arquitetura hexagonal

**Status:** aceita

## Contexto
O requisito de vazão (10.000 avaliações/min ≈ 167/s) é pequeno para um motor de regras em memória. O gargalo real
tende a ser I/O (consulta de dados, persistência), não CPU.

## Decisão
Um único deployable, stateless, organizado em `domain` (puro), `application` (casos de uso) e `infrastructure`
(web, configuração, observabilidade). Dependências apontam para dentro; o `ArchitectureTest` garante isso.

## Alternativas
- **Microsserviços:** isolam falhas e times, mas adicionam latência de rede, consistência distribuída e custo
  operacional sem benefício para este escopo.
- **Camadas tradicionais (controller → service → repository):** mais simples, porém acopla regra de negócio a framework.

## Consequências
+ Domínio testável sem Spring; escala horizontalmente por réplicas; extração futura de serviços é possível.
− Disciplina de fronteiras depende de teste de arquitetura.
