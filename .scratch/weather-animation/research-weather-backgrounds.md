# Pesquisa de fundos climáticos abertos

Data: 24/10/2026. Escopo: conjuntos prontos que representem as dez cenas do Free My TV, com licença verificável e adequação ao Android 12 da TV. Pesquisa documental e visual; nenhuma alteração no renderer do app ou medição de desempenho na TV.

## Resultado

O candidato mais completo confirmado é **g3sthousen/weather-animations**: o código possui as dez cenas necessárias, com licença MIT, incluindo garoa distinta, tempestade com relâmpagos e céu diurno/noturno. É Canvas 2D/CSS, com profundidade visual simulada, e não 3D real. A demo foi inspecionada no navegador: nuvens e chuva noturna renderizaram corretamente após a transição. [Demo](https://g3sthousen.github.io/weather-animations/), [licença](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/LICENSE), [código](https://github.com/g3sthousen/weather-animations/tree/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core).

A alternativa **xiaweizi/flutter_weather_bg** também oferece fundos completos, com quinze tipos e licença MIT. Cobre nove cenas diretamente; garoa precisaria reutilizar `lightRainy`. O código usa Flutter Canvas/CustomPainter, com imagens de nuvem, sol, chuva e neve. A composição noturna limpa inclui estrelas; `cloudyNight` tem paleta própria e nuvens, mas a camada de estrelas só é adicionada em `sunnyNight`. Não é 3D real. [README](https://github.com/xiaweizi/flutter_weather_bg/blob/64e7264accd27fe785515b7e016db77a97f3cf93/README.md), [licença](https://github.com/xiaweizi/flutter_weather_bg/blob/64e7264accd27fe785515b7e016db77a97f3cf93/LICENSE), [composição](https://github.com/xiaweizi/flutter_weather_bg/blob/64e7264accd27fe785515b7e016db77a97f3cf93/lib/bg/weather_bg.dart).

## Cobertura

| Cena atual | weather-animations | flutter_weather_bg |
| --- | --- | --- |
| Limpo de dia | `clear` + `day` | `sunny` |
| Limpo à noite | `clear` + `night`, lua e estrelas | `sunnyNight`, estrelas |
| Parcialmente nublado de dia | `cloudy` + `light` + `day` | `cloudy` |
| Parcialmente nublado à noite | `cloudy` + `light` + `night` | `cloudyNight` |
| Nublado | `overcast` | `overcast` |
| Neblina | `fog` | `foggy` |
| Garoa | `drizzle`, implementação própria | Adaptação: `lightRainy` |
| Chuva | `rain`, intensidades | `lightRainy` / `middleRainy` / `heavyRainy` |
| Neve | `snow`, intensidades | `lightSnow` / `middleSnow` / `heavySnow` |
| Tempestade | `storm`, chuva e relâmpagos | `thunder` |

Fontes: [tipos do primeiro](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/types.ts), [partículas](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/particles.ts), [atmosfera](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/atmosphere.ts), [tipos do Flutter](https://github.com/xiaweizi/flutter_weather_bg/blob/64e7264accd27fe785515b7e016db77a97f3cf93/lib/utils/weather_type.dart), [nuvens do Flutter](https://github.com/xiaweizi/flutter_weather_bg/blob/64e7264accd27fe785515b7e016db77a97f3cf93/lib/bg/weather_cloud_bg.dart).

## Limites para integração

O projeto atual é Kotlin/Compose e usa GLES2. Nenhuma das duas bibliotecas é uma dependência Android nativa que substitua diretamente o renderer atual.

- `weather-animations`: exige uma página local/WebView ou adaptação do desenho para o renderer nativo. Usa `OffscreenCanvas` e blur sem fallback no caminho de nuvens inspecionado, além de backing store dimensionado por DPR sem teto próprio. Compatibilidade do WebView instalado, resolução, RAM, CPU/GPU e transições precisam ser medidas. Canvas 2D não garante que toda a animação rode exclusivamente na GPU.
- `flutter_weather_bg`: incorporar Flutter exige outro runtime; adaptar os efeitos à renderização nativa é uma alternativa. O custo para a TV não foi medido. O repositório declara MIT, mas não foi encontrada proveniência individual das imagens nos arquivos inspecionados.
- As duas licenças MIT exigem preservar os avisos aplicáveis. As gravações de ambiente e o botão de mutar do app podem continuar independentes das animações.

Estas são inferências de integração baseadas no código, não resultados de benchmark. Detalhes da primeira biblioteca estão em [research-web-candidates.md](research-web-candidates.md).

## Outros projetos encontrados

| Projeto | Avaliação |
| --- | --- |
| [Vanta](https://github.com/tengbao/vanta) | Tem efeitos de nuvens/neblina, mas não o conjunto das dez cenas. |
| [Aether](https://github.com/Manabu-GT/Aether#compatibility) | Efeitos nativos Compose via AGSL, mas só funcionam no Android 13/API 33+. No Android 12 da TV o efeito é no-op. Também não oferece o céu completo para todos os estados. |
| [greywen/web-weather](https://github.com/greywen/web-weather) | Noite e garoa têm cobertura parcial; README declara MIT, mas faltam arquivo de licença e proveniência explícita dos assets na revisão consultada. Não selecionado. |
| [Atmoscene](https://github.com/afnan-hossain/atmoscene) | Alternativa SVG/CSS com muitas condições declaradas, arquivo MIT e avisos de Meteocons. A cobertura e o funcionamento de todas as cenas ainda não foram verificados no código/demo nesta pesquisa; não tratado como candidato homologado. |
| [Onirix no Sketchfab](https://sketchfab.com/onirix) | Cenas 3D de sol, chuva e neve encontradas; o conjunto pesquisado não cobre as dez condições. |

Não foi confirmado um pacote de cenas **3D reais** que reúna cobertura integral, licença aberta verificável e integração pronta/leves custos medidos para Android 12 TV. Isso não prova que tal pacote não exista.

## Recomendação

Usar a demo de `weather-animations` para decidir se o visual atmosférico atende à preferência do usuário. Se escolhido, avaliar uma adaptação ao GLES2 existente para manter controle do orçamento da TV; uma prova em WebView pode comparar o visual, mas não se deve prometer economia de RAM ou desempenho antes de medir. Não incorporar nenhuma biblioteca nesta etapa de pesquisa.

Prévia local da demo: `build/weather-asset-research/rain-night.png` (arquivo gerado, fora do código do app).
