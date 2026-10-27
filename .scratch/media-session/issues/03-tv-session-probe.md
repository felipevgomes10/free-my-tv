# 03: Medir sessões e comandos de mídia nos apps da TV

> **DESCARTADO em 2026-10-27.** Usuário: “Tem muitas limitações nisso, reverter essa feature”. Integração removida dos dois projetos e APK anterior restaurado na TV. O conteúdo abaixo é histórico do experimento; não é plano ativo nem recurso disponível.

Status: wontfix
Type: prototype
Blocked by: nenhum; recurso descartado pelo usuário.

## Entrega

Criar uma sondagem temporária das sessões reais e testar o caminho de controle no aparelho, conforme a [spec do Home](../spec.md). Ao liberar o ticket, classificar a preparação como `ready-for-agent`; operações que exigirem a pessoa ficam explicitadas no roteiro. Não modificar o Controller de produção.

## Aceitação

- [ ] Registrar versões da TV/Home e dos apps YouTube, Netflix, Prime Video, Globoplay, Disney+, Max e Crunchyroll; ausência/falta de acesso recebe `não avaliado`, com motivo.
- [ ] Para cada app disponível, capturar estados tocando/pausado/encerrado, troca de obra, identidade de sessão, metadados, posição/duração e ações anunciadas.
- [ ] Executar play/pause explícitos na sessão; confirmar efeito na TV e no estado Android em até 5 segundos, inclusive comandos repetidos no mesmo estado.
- [ ] Validar seleção entre sessões simultâneas, pausa da selecionada, entrada de outra tocando, destruição/recriação da sessão e comando com alvo antigo.
- [ ] Medir ações opcionais apenas quando anunciadas; separar capacidade anunciada, pedido aceito e comportamento observado.
- [ ] Gerar `tv-session-results.md` ao lado da spec com matriz por app/caminho/ação, limitações e evidências; sem credenciais no relatório.
- [ ] Remover hooks temporários de produção ao concluir e registrar o que foi mantido para reprodução do teste.

## Decisão obrigatória

Identificar ao menos um app com play/pause confiáveis e definir o conjunto comprovado antes de liberar 04. Falta de capa/título admite fallback; falta de controle exclui a sessão desse recurso. Não usar keycode global de alternância para simular suporte. App não avaliado continua pendente ou recebe recorte explícito na spec. Se nenhum caminho passar, encerrar e marcar a integração descartada como `wontfix`.

## Comments

**2026-10-27 — Avanço autorizado.** O usuário pediu “Esse vai demorar muito, passa pro proximo passo” sobre o teste de 15 minutos. Iniciar a sondagem na TV, mantendo a validação prolongada do iPhone pendente e sem liberar automaticamente integração ou release. Preferir uma ferramenta temporária via ADB para medir `MediaController`, sem instalar outro Home; registrar que a identidade shell e suas permissões não substituem a validação futura do caminho dentro do Home.

**2026-10-27 — Recorte autorizado e medido.** O usuário definiu que Netflix e YouTube bastam e os demais serão testados depois. [Resultados e limites](../tv-session-results.md), [ferramenta reproduzível](../probe/README.md). YouTube forneceu título/capa/duração e respondeu a comandos; Netflix respondeu a comandos sem metadata. Efeito no Netflix confirmado pela pessoa. O recorte substitui a exigência de medir todos os apps antes do contrato. Itens não executados de ciclo de vida/concorrência migram para validação integrada; não foram aprovados por essa conclusão.

## Encerramento — 2026-10-27

Revertido a pedido explícito do usuário após limitações de metadados/controles, dependência do áudio auxiliar e falha de ativação ao tentar ocultar o tempo. Nenhuma investigação adicional ou publicação será feita. Documentação e evidências preservadas como histórico. Ver `rollback.md` ao lado da spec.
