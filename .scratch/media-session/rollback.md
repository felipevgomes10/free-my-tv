# Media Session: recurso revertido

Status: wontfix
Data: 2026-10-27

Usuário: “Tem muitas limitações nisso, reverter essa feature”.

Removidos UI de ativação, áudio auxiliar, handlers da Media Session web, assinaturas/comandos de reprodução, leitura Android adicionada ao bridge, imagens de sessão e mudanças HTTP exclusivas do WAV. Contrato, fixtures, fake bridge e testes exclusivos também removidos. Os arquivos de produção existentes voltaram exatamente ao HEAD anterior ao experimento.

APK anterior `/tmp/fmtv-home-before-media-session.apk` restaurado na TV com `install -r`, preservando dados e pareamentos. Servidores temporários 8768/8769 encerrados. Specs/tickets marcados `wontfix`; pesquisa e evidências permanecem somente como histórico em `.scratch/media-session/`. Nenhum commit, push ou release foi feito.

A tentativa de ocultar tempo usou `duration: Infinity`. O WebKit documenta a recusa desse valor (https://bugs.webkit.org/show_bug.cgi?id=259642); o teste de regressão reproduziu cancelamento da ativação. A correção local em andamento não foi entregue e também foi descartada. Não foi confirmada no iPhone a causa exata da mensagem resumida como “Indisponivel”.

Ao reabrir o Controller, a seção de mídia na tela bloqueada deve desaparecer. Uma aba antiga ainda aberta precisa ser fechada/recarregada no telefone.

## Verificação da reversão

- APK instalado idêntico ao anterior: SHA-256 `4f351662f90eb91dd1d500d6eaffe6ed72a0d3bb3907d64b1e6ea4347fdf35a4`.
- Dez arquivos do Controller servidos pela TV iguais aos contidos no APK anterior; `tv-silence.wav` retorna 404.
- 317 testes JVM e 128 testes do Controller aprovados; build TypeScript/Vite concluído.
- Arquivos de produção existentes sem diff em ambos os repositórios. Somente histórico `.scratch/media-session/` permanece fora do HEAD.
- Portas temporárias 8768/8769 sem listeners.
- Release Android local recompilado sem a feature e sem WAV/assinaturas no bundle. A TV permanece com o APK original restaurado e conferido.
