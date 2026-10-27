# 08: Encaminhar saltos de segundos do cartão para o player da TV

> **DESCARTADO em 2026-10-27.** Usuário: “Tem muitas limitações nisso, reverter essa feature”. Integração removida dos dois projetos e APK anterior restaurado na TV. O conteúdo abaixo é histórico do experimento; não é plano ativo nem recurso disponível.

Status: wontfix
Type: bug

## Pedido

Após confirmar play/pause, o usuário relatou que avançar/retroceder não funcionavam e esclareceu que deseja saltar segundos no vídeo.

## Resultado

Implementados seekbackward/seekforward no Controller e posição absoluta no contrato, contrato aditivo com parâmetros validados e comando nativo direcionado à sessão/obra. Saltos relativos usam posição atual da TV, limite de 30 s e clamp no início/duração conhecida. YouTube publica seek: +10/-10 s observados em 156/140 ms. Netflix pausado não atingiu o alvo apesar do bit anunciado; durante reprodução os dois sentidos foram confirmados em 2046/1737 ms. Netflix publica seek somente enquanto toca; precisa de play antes dos saltos quando pausado.

138 testes do Controller; teste JVM cobre deslocamentos, clamp e recusa de parâmetros/alvos inválidos. Build e instalação registrados nos [resultados](../paired-device-results.md). Aceitação física do novo cartão continua no ticket 07; este ticket não aprova uso prolongado/release.

**Preferência posterior:** ocultar barra de tempo em todos os apps. O Controller declara timeline infinita e mantém seekto desativado; permanece com os botões de saltos relativos. A aparência final depende do iOS e será conferida em 07.

## Encerramento — 2026-10-27

Revertido a pedido explícito do usuário após limitações de metadados/controles, dependência do áudio auxiliar e falha de ativação ao tentar ocultar o tempo. Nenhuma investigação adicional ou publicação será feita. Documentação e evidências preservadas como histórico. Ver `rollback.md` ao lado da spec.
