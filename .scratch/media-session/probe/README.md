> **DESCARTADO.** Fontes históricas do experimento; servidor encerrado e integração removida em 2026-10-27.

# Sondagem de sessões na TV

Ferramenta temporária do ticket [03](../issues/03-tv-session-probe.md). Usa `MediaSessionManager`/`MediaController` num processo Java iniciado por ADB (`app_process`). Não instala APK, não modifica o Home, não concede permissões e não usa keycode global de alternância.

**Limite:** o processo executa com UID shell e usa `MEDIA_CONTENT_CONTROL`; o Home usa seu acesso a notificações. O comportamento do player pode ser medido aqui, mas autenticação, seleção, observação e controle dentro do Home precisam de validação própria na implementação. A inicialização refletida dos serviços foi ajustada para o Android 12 da TCL; esta ferramenta não promete portabilidade para outras versões.

## Executar

Requer Python 3, `adb`, JDK, Android SDK 35 e build-tools 35.0.1 instalados no Mac. O script usa `ANDROID_SDK_ROOT`, ou `~/Library/Android/sdk`, e o JDK em `/Library/Java/JavaVirtualMachines/openjdk.jdk/Contents/Home`.

Somente ler sessões:

```sh
python3 .scratch/media-session/probe/run.py \
  --serial 192.168.100.18:5555 \
  --output /tmp/fmtv-media-snapshot
```

Com um vídeo já reproduzindo, testar **pause / pause / play / play**, salvando evidências e restaurando o estado inicial:

```sh
python3 .scratch/media-session/probe/run.py \
  --serial 192.168.100.18:5555 \
  --package com.google.android.youtube.tv \
  --output /tmp/fmtv-youtube-check
```

Escolha um diretório novo por ensaio para preservar os anteriores. O comando com `--package` controla a reprodução e só aceita uma sessão ativa desse pacote, inicialmente tocando ou pausada. Sem `--package`, só lê. As demais ações anunciadas são registradas, mas não executadas.

## Contrato da sondagem

- O helper permanece vivo durante um ensaio, atribui um ID opaco à sessão Android e confere o token ativo e a identidade da obra antes de cada comando.
- Identidade da obra usa título/media ID quando fornecidos. Se ambos faltam, não é possível distinguir duas obras na mesma sessão; não anunciar essa proteção para esses apps.
- Comando cujo estado já está satisfeito é no-op. Para transição, somente despacha uma ação explicitamente anunciada e espera o estado correspondente por até 5 segundos. Capacidades podem mudar entre tocando e pausado, como medido no Netflix.
- O relatório distingue `dispatched` de `matched`. Mesmo `matched=true` é confirmação do estado Android; conferir também o efeito na TV, por imagem ou observação humana.
- Uma capa em bitmap pode ser exportada como JPEG de até 512 px. URIs são registradas apenas como presença/ausência; não baixar URLs arbitrárias nem registrar possíveis credenciais em URLs.
- A captura de tela pode vir vazia/protegida. Nesse caso, o JSON registra indisponibilidade e exige observação humana; não contornar a proteção.
- Ao terminar, o script fecha o processo e remove apenas `/data/local/tmp/fmtv-media-session-probe.zip`. O relatório local permanece no diretório de saída. Falha na restauração inicial aparece em `restoreError`; não afirmar restauração se esse campo existir.

`observations.json` registra retratos e efeitos. `before.png`, `paused.png`, `playing.png` e `artwork.jpg` existem somente quando disponíveis. A presença de um arquivo antigo em pasta reutilizada não comprova captura neste ensaio; usar as referências do JSON e pastas novas.
