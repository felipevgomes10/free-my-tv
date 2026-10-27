# 05: Publicar sessão atual e comandos explícitos pelo bridge

> **DESCARTADO em 2026-10-27.** Usuário: “Tem muitas limitações nisso, reverter essa feature”. Integração removida dos dois projetos e APK anterior restaurado na TV. O conteúdo abaixo é histórico do experimento; não é plano ativo nem recurso disponível.

Status: wontfix
Type: task
Blocked by: nenhum; recurso descartado pelo usuário.

## Entrega

Implementar o contrato de 04 usando as sessões Android e o caminho comprovado em 03. Seguir a [spec do Home](../spec.md), promover a `ready-for-agent` somente após a dependência e manter o comportamento atual do Home/Up Next.

## Aceitação

- [ ] Observação compartilhada no processo funciona enquanto outro app aparece na TV; seleção/identidade e ausência seguem a spec.
- [ ] Somente assinatura autenticada e `ready` recebe retratos; inicial, mudanças, renovação em até 5 segundos e encerramento são exercitados.
- [ ] Play/pause são idempotentes e dirigidos à sessão; rejeitar alvo antigo, troca de obra identificável e ação sem suporte, sem usar `play-pause` global.
- [ ] Sucesso exige estado observado dentro de 5 segundos ou estado já satisfeito. Timeout e efeito tardio são distintos; nenhum replay automático.
- [ ] Imagem autenticada segue limites existentes e não reaproveita capa da obra anterior. Campos ausentes e perda de acesso são representados corretamente.
- [ ] Testes JVM com `FakeTvPlatform` e fixtures de 04 cobrem estados, permissões, comando repetido, concorrência/troca de sessão, parâmetros inválidos, timeout e assinatura encerrada.
- [ ] Build de desenvolvimento consome bundle local por caminho absoluto quando necessário; checks do Home pertinentes passam. Sem release nem atualização automática da TV neste ticket.

## Comments

**2026-10-27 — Implementado e candidato instalado.** `CurrentPlayback` serializa leitura/comandos por todos os aparelhos, `MediaSessions` mantém identidade e seleção da sessão real, e o bridge publica somente para assinantes autenticados/ready. Comando já satisfeito é no-op; troca de alvo/obra conhecida é recusada; sucesso depende do estado Android e o prazo nativo é de 4 segundos. Capa atual em bitmap usa `playback:` e cache autenticado; Netflix recebe fallback do app. Escopo inicial restrito aos pacotes YouTube/Netflix.

323 testes JVM passaram, incluindo casos novos de assinatura, revogação, alvo obsoleto, timeout, metadados ausentes e capacidades dinâmicas do Netflix. Build release passou e o APK local foi instalado com `-r`, sem publicar release. [Validação do candidato](../paired-device-results.md) separa entrega/HTTP, sondagem shell e experiência integrada ainda pendente no iPhone.

## Encerramento — 2026-10-27

Revertido a pedido explícito do usuário após limitações de metadados/controles, dependência do áudio auxiliar e falha de ativação ao tentar ocultar o tempo. Nenhuma investigação adicional ou publicação será feita. Documentação e evidências preservadas como histórico. Ver `rollback.md` ao lado da spec.
