# Sessões reais: YouTube e Netflix

> **Histórico de experimento descartado.** Recurso revertido em 2026-10-27 a pedido do usuário. Resultados abaixo não descrevem a versão atual.

Data: 2026-10-27. TCL G09 / Smart TV Pro, Android 12 (SDK 31), ADB `192.168.100.18:5555`, Home 0.6.8 (35). [Inventário](evidence/inventory.json).

O usuário definiu o recorte: **“Nao precisa testar em todos. Netflix e Youtube ja bastam, com o tempo eu testo os outros.”** Os demais apps ficam não avaliados, sem inferência de compatibilidade.

| App / versão | Metadados medidos | Play / pause | Evidência física |
| --- | --- | --- | --- |
| YouTube 7.27.302 | Título e autor; capa bitmap 640×360; duração 1.882.021 ms (31:22); posição e velocidade | Pause 146 ms; play 126 ms; repetição não alterna | Capturas mostram ícone de play na pausa e pause na reprodução; posição avança após retomar |
| Netflix 13.2.1 build 27207 | Metadata vazio: sem título, capa, media ID e duração; posição e estado presentes | Pause 315 ms; play 107 ms; repetição satisfeita tratada como no-op | Pessoa confirmou: “Sim, pausou e voltou a reproduzir” |

Evidências: [YouTube](evidence/youtube/observations.json), [capa real](evidence/youtube/artwork.jpg), [TV pausada](evidence/youtube/paused.png), [TV tocando](evidence/youtube/playing.png), [Netflix](evidence/netflix-dynamic-actions/observations.json). O estado inicial foi restaurado sem erro registrado nos dois ensaios.

## Implicações para o contrato

- Netflix anuncia `pause` enquanto toca e `play` quando pausado. Não exigir ambos os bits simultaneamente; capacidades são por estado. A primeira versão da sondagem recusava o segundo pause por falta do bit, embora a sessão já estivesse pausada; a sondagem foi corrigida para no-op antes dessa validação.
- Repetir uma intenção já satisfeita deve ser sucesso sem despacho. Nunca converter em tecla global de alternância.
- No Netflix usar nome/arte do app e omitir duração/progresso sem duração válida. Não buscar o título numa lista histórica e apresentá-lo como reprodução atual.
- YouTube fornece capa pronta; não é necessário baixar URL de imagem. Neste vídeo não havia media ID, mas o título identifica a obra. Netflix não permite distinguir obras dentro da mesma sessão por esses metadados.
- IDs inexistentes e identidade de obra divergente foram rejeitados pela sondagem. Destruição/recriação real da sessão, troca de obra e concorrência entre duas sessões tocando ainda precisam de verificação integrada; esses guardas não comprovam tais cenários físicos.
- Extensão posterior: seek +10/-10 s validado no YouTube (156/140 ms), conforme [resultados integrados](paired-device-results.md). Netflix pausado anuncia seek mas não confirmou o alvo; repetido durante reprodução confirmou avanço/retorno em 2046/1737 ms. Seek fica habilitado somente enquanto toca. Próxima/anterior não foram exercitados.

## Limites e continuidade

A [sondagem](probe/README.md) executou `MediaController` como **UID shell**, com seu acesso privilegiado. Não substitui testar o mesmo caminho dentro do Home e seu NotificationListener. O acesso do Home estava habilitado. Nenhum APK foi instalado nem arquivo de produção alterado pela sondagem; o helper remoto foi removido.

Prime Video, Globoplay, Disney+, Max e Crunchyroll estão instalados e **não avaliados**, por decisão do usuário. Spotify apareceu sem reprodução e sem metadata; não foi alvo de comandos.

O ticket 03 fica concluído para este recorte e libera definir o contrato experimental. As pendências prolongadas do iPhone, a validação do caminho no Home e os cenários integrados continuam registrados; não há validação de release.
