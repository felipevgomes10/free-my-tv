# Home: reprodução atual para a tela bloqueada do iPhone

> **DESCARTADO em 2026-10-27.** Usuário: “Tem muitas limitações nisso, reverter essa feature”. Integração removida dos dois projetos e APK anterior restaurado na TV. O conteúdo abaixo é histórico do experimento; não é plano ativo nem recurso disponível.

Status: wontfix
Data: 2026-10-27
Blocked by: nenhum; recurso descartado pelo usuário.

## Objetivo e limites

Permitir que o FMTV Controller apresente a sessão de mídia atual da TV e envie comandos explícitos, para alimentar a experiência experimental de tela bloqueada do iPhone. A [spec do Controller](../../../free-my-tv-remote-controller/.scratch/media-session/spec.md) define o fluxo completo, a sequência dos tickets e os critérios obrigatórios de aceitação; a [pesquisa](../../../free-my-tv-remote-controller/.scratch/media-session/viability.md) registra as incertezas do Safari.

O Home já observa `MediaSessionManager` em `MediaSessions.kt` e expõe `nowPlaying` por `TvPlatform`. `PlayingMedia` contém metadados e progresso, mas não representa por completo identidade da sessão, capacidades ou play/pause explícitos. `resumeSession()` e o `play-pause` global não substituem esse contrato.

Em 2026-10-27, o usuário pediu avançar sem esperar o teste de 15 minutos e considerou YouTube/Netflix suficientes. A sondagem e o contrato foram concluídos e a integração experimental implementada; [candidato instalado e verificações](paired-device-results.md). Os testes pendentes não foram considerados aprovados. A implementação vem desligada por padrão e o ticket 07 continua responsável pela experiência física integrada, sem declaração de estabilidade.

## Medição na TV antes da implementação

O ticket 03 registra em `tv-session-results.md` as versões da TV/Home e dos apps e cobre YouTube, Netflix, Prime Video, Globoplay, Disney+, Max e Crunchyroll, quando instalados. App ausente ou sem acesso de teste é `não avaliado`, com motivo; não é sucesso nem incompatibilidade provada. Concluir a avaliação dos apps disponíveis antes de fixar o escopo suportado; pendências exigem recorte explícito na spec.

Para cada app, medir sessão iniciada, tocando, pausada, encerrada e mudança de obra; campos presentes/ausentes; capacidades anunciadas e comportamento real de play/pause; ações opcionais apenas quando anunciadas. Confirmar na TV, incluindo comando repetido no mesmo estado, resposta atrasada e mudança de sessão antes de executar.

No mínimo um app precisa passar em play/pause explícitos para avançar. Falta de título ou capa admite fallback pelo app. Falta de controle confiável exclui a sessão desse recurso, com motivo registrado. Não ampliar o suporte por dedução a partir de outro app ou das listas de Continuar assistindo.

**Recorte de 2026-10-27:** o usuário considerou Netflix e YouTube suficientes; outros apps serão avaliados depois. [Medição concluída para os dois](tv-session-results.md), com os demais não avaliados. A primeira integração experimental ofereceu play/pause nesses pacotes; a extensão abaixo acrescenta saltos somente no YouTube. Netflix exige capacidades por estado e fallback de app; não promete título/capa/duração da obra. Cenários de ciclo de vida e uso prolongado ficam na validação integrada.

## Seleção e identidade da sessão

- Observar sessões atuais mesmo com outro app na frente; o bridge não depende de a tela do Home estar aberta. Reutilizar o observador de plataforma, evitando uma coleta de mídia por telefone.
- Priorizar a sessão que entrou em reprodução mais recentemente. Empates usam uma ordem estável definida pelo contrato. Manter a sessão escolhida quando pausada enquanto continuar viva e não surgir outra tocando; não selecionar qualquer sessão pausada antiga quando a escolhida desaparecer.
- Ao acabar a sessão, perder o acesso necessário ou não haver alvo elegível, enviar ausência explícita e o motivo. Não exibir dados armazenados em Watch Next/Up Next como reprodução atual.
- Gerar identidade opaca para cada sessão Android e invalidá-la ao destruí-la, recriá-la ou trocar o alvo escolhido. Identificar mudança de obra quando os dados do app permitirem e invalidar comandos dirigidos à obra anterior. Registrar o limite quando o app não identifica mudanças.
- Uma sessão sem play/pause confiáveis não é alvo elegível. Se a sessão prioritária não puder ser controlada, informar indisponibilidade; não controlar outro app em segundo plano como fallback.

## Publicação e comandos

1. Publicar retrato inicial após assinatura autenticada e em conexão `ready`; enviar mudanças e renovação no máximo a cada 5 segundos enquanto houver assinantes. Desconexão, revogação ou desativação cancela a assinatura.
2. Incluir os campos exigidos na spec do Controller e no contrato do ticket 04. Usar posição calculada a partir do estado Android e horário de observação, sem confundir relógio monotônico da TV com horário do iPhone. Posição/duração desconhecidas permanecem ausentes.
3. Play/pause são intenções explícitas. Aplicar à sessão identificada usando o caminho medido no ticket 03; chamar pause numa sessão já pausada ou play numa sessão já tocando mantém seu estado. Não converter em keycode global de alternância.
4. Validar identidade, ação disponível e parâmetros no momento da execução. Recusar sessão encerrada/substituída e seek inválido, sem entregar a outra sessão. Serializar operações por sessão e preservar as regras existentes de pareamento/Linking.
5. Separar aceitação do comando e efeito observado. A resposta de sucesso só ocorre quando a sessão alcança o estado pedido; se já está nele, sucesso sem efeito adicional. Limitar a espera a 5 segundos e definir timeout/erro no contrato. ACK de ADB ou retorno de `TransportControls` sozinho não comprova ação na TV. Mudanças posteriores continuam pelo fluxo de retratos.
6. Publicar somente capacidades anunciadas e confirmadas para aquele app/caminho. Próxima/anterior, avanço/retorno e seek são opcionais. Não modificar controles físicos existentes para suprir este experimento.
7. Servir imagens pelo mecanismo autenticado atual, com IDs opacos, limites de tamanho/cache e descarte de arte obsoleta. Sem título/capa, enviar ausência para o Controller aplicar o fallback. Não carregar arte grande ou rede no callback principal de mídia.
8. Não acumular comandos para conexões futuras nem entregar uma ação após revogação ou troca de alvo. Timeout pode coincidir com efeito tardio: registrar a atualização real subsequente, sem repetir automaticamente a ação.

## Compatibilidade e documentos existentes

Preservar os ADRs [0007](../../docs/adr/0007-up-next-from-media-sessions.md) e [0012](../../docs/adr/0012-up-next-from-watch-next.md): Up Next continua sendo histórico/Watch Next; sessão atual é outra necessidade. Preservar o transporte HTTP/WebSocket autenticado do Controller e a entrega de exemplos pelo bundle. A decisão de versão e os exemplos novos pertencem ao ticket 04; não alterar o protocolo antes dele.

Os testes JVM usam `FakeTvPlatform` e os exemplos compartilhados para verificar isolamento de sessão, permissões, ausência de sessão, capacidades, atraso, comando repetido, troca de obra, tempo limite e encerramento de assinatura. Eles não substituem as medições nos apps reais.

## Tickets deste repositório

- [03 — Medir sessões e comandos na TV](issues/03-tv-session-probe.md), liberado pelo usuário em paralelo às pendências de 02.
- [05 — Publicar reprodução e executar comandos](issues/05-playback-over-the-bridge.md), após 04.
- [07 — Aceitação integrada e decisão](issues/07-paired-device-acceptance.md), após 05 e 06.

Os demais tickets e o índice completo estão na spec do Controller. Cada ticket mantém seu `Status` de triagem e dependências; resultado negativo de experimento não libera implementação. Se os critérios da etapa falharem, registrar evidências e fechar o trabalho descartado como `wontfix`, removendo alterações experimentais que não devam permanecer.

## Fora do escopo

Releases, publicação e atualização automática dos dois projetos; novas permissões privilegiadas; alterar sessões de apps terceiros; substituir Watch Next; capturar/decodificar mídia da TV; controlar HDMI/antena sem sessão elegível; criar servidor público ou app iOS nativo.

## Extensão de 2026-10-27: saltos de segundos

Após confirmar play/pause na tela bloqueada integrada, o usuário pediu saltos no vídeo. O Controller encaminha seekforward/seekbackward com padrão de 10 segundos (offset do sistema limitado a 30 s) ; o contrato aceita posição absoluta, mas o cartão mantém seekto desativado conforme o pedido abaixo. Home calcula saltos relativos usando posição atual da TV, valida alvo/parâmetros e observa o resultado. Seek é publicado para YouTube com capacidade anunciada e para Netflix somente durante reprodução. Netflix pausado não atingiu o alvo; repetido tocando confirmou avanço/retorno em 2046/1737 ms. Quando pausado, é preciso dar play antes do salto. Próxima/anterior da fila ficam fora. Validação final do cartão com o novo candidato permanece no ticket 07.

## Preferência posterior: ocultar tempo em todos os apps

Usuário pediu ocultar a barra no cartão “Em todos os apps, inclusive YouTube”. A página não publica duração/posição finitas na Media Session: declara duração infinita e desativa seekto. Mantém play/pause, capa/título e saltos relativos. Isso substitui os critérios anteriores de mostrar progresso no cartão; o retrato da TV continua contendo tempo real para calcular saltos. A aparência do cartão pertence ao iOS; o resultado pode ser barra oculta ou aparência de mídia ao vivo e requer confirmação física. Não declarar ocultação visual garantida pela API.

## Encerramento — 2026-10-27

Revertido a pedido explícito do usuário após limitações de metadados/controles, dependência do áudio auxiliar e falha de ativação ao tentar ocultar o tempo. Nenhuma investigação adicional ou publicação será feita. Documentação e evidências preservadas como histórico. Ver `rollback.md` ao lado da spec.
