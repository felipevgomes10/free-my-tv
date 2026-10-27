# 07: Validar Controller completo com iPhone e TV e decidir continuidade

> **DESCARTADO em 2026-10-27.** Usuário: “Tem muitas limitações nisso, reverter essa feature”. Integração removida dos dois projetos e APK anterior restaurado na TV. O conteúdo abaixo é histórico do experimento; não é plano ativo nem recurso disponível.

Status: wontfix
Type: task
Blocked by: nenhum; recurso descartado pelo usuário.

## Entrega

Preparar e instalar um candidato local do Home com o Controller candidato, sem publicar release. A preparação pode ser executada pelo agente; promover o teste físico a `ready-for-human` quando o candidato estiver pronto. Registrar o fluxo completo nas duas formas de abertura do iPhone, conforme as [specs](../spec.md).

## Aceitação

- [ ] Registrar versões/identidade dos candidatos, bundle realmente servido pela TV, modelo/iOS e origem HTTP; preservar forma de retornar ao build anterior.
- [ ] Testar Safari e Controller adicionado à Tela de Início, com pareamento real. Repetir bloqueio de 1/5/15 minutos e pausa por 15 minutos; comando e efeito na TV em até 5 segundos, sem abrir a página.
- [ ] Percorrer a matriz de apps de 03: estado, título/capa/fallback, progresso e somente ações comprovadas. Confirmar no aparelho; resposta do bridge isolada não basta.
- [ ] Mudança de título, pausa pelo Controle físico e troca de sessão chegam ao telefone em até 5 segundos após observação pelo Home; ações antigas não atingem outra sessão/obra.
- [ ] Repetir perda de Wi-Fi, revogação, reconexão, fim da sessão, desativação, troca de app, chamada e música/podcast no iPhone. Registrar cartão obsoleto, comandos perdidos e qualquer disputa de mídia como falhas.
- [ ] Touchpad/OK/Back, play-pause atual, Multitarefas e abas continuam funcionando. Modo instalado é testado após fechar e reabrir pelo ícone.
- [ ] Escrever `paired-device-results.md` ao lado da spec com cenários, observações, tempos, limitações por app e decisão; refletir a decisão nas specs e no status dos tickets.

## Decisão obrigatória

Passar somente se ambos os modos cumprirem os critérios e todos os apps declarados suportados funcionarem; app não avaliado não pode ser anunciado como suportado. Uma limitação de metadados prevista pode passar com fallback correto; perda de play/pause ou estado incorreto não pode.

Em falha, corrigir e repetir cenários afetados ou retirar o recurso experimental e restaurar o candidato anterior quando necessário. Preservar a evidência e fechar trabalho descartado como `wontfix`, com motivo. Em sucesso, documentar exatamente o suporte comprovado e a natureza experimental. Releases continuam fora deste esforço.

## Comments

**2026-10-27 — Candidato pronto.** 05/06 implementados e testados; Home com o bundle final instalado por ADB preservando dados. APK anterior guardado em `/tmp/fmtv-home-before-media-session.apk`. [Resultados e roteiro do candidato](../paired-device-results.md). Usuário pediu seguir sem esperar os testes prolongados e limitou o recorte a YouTube/Netflix; os demais apps ficam não avaliados. Iniciar pelo teste curto no Controller real; não confundir com os testes anteriores do simulador. Duração/interrupções continuam pendentes, sem bloquear o uso experimental pedido, mas sem aprovação de estabilidade/release.

**2026-10-27 — Retorno do usuário.** Cartão e play/pause confirmados no candidato real. Usuário pediu saltos de segundos; ticket 08 implementa callbacks e habilita seek no YouTube e Netflix durante reprodução. O ensaio inicial com Netflix pausado falhou; repetido tocando confirmou os saltos em cerca de 2 s. Nova confirmação do cartão no iPhone ainda pendente; testes prolongados continuam pendentes.

## Encerramento — 2026-10-27

Revertido a pedido explícito do usuário após limitações de metadados/controles, dependência do áudio auxiliar e falha de ativação ao tentar ocultar o tempo. Nenhuma investigação adicional ou publicação será feita. Documentação e evidências preservadas como histórico. Ver `rollback.md` ao lado da spec.
