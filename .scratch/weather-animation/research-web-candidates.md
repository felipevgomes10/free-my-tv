# Fundos climáticos prontos — candidatos web

Pesquisa em 24/10/2026, usando README, arquivos de licença e implementação dos próprios projetos.
Nenhum código do app foi alterado e não houve teste de desempenho na TV.

## Resultado

**`g3sthousen/weather-animations` cobre os dez cenários atuais no código e tem um arquivo MIT completo.** É o candidato mais completo entre estes dois para avaliar visualmente. Seu renderer é **Canvas 2D + CSS**, com profundidade simulada; não é uma coleção de cenas 3D/WebGL nem uma biblioteca Android nativa. [Licença](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/LICENSE), [renderer](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/index.ts).

**`greywen/web-weather` tem uma demonstração atmosférica ampla, mas não oferece cobertura equivalente pronta.** A noite limpa não tem lua/estrelas no renderer inspecionado e a garoa é tratada como chuva. Há também pendências na documentação das licenças. [Tipos](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/weather-types.ts), [Canvas](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/WeatherCanvas.tsx), [árvore](https://github.com/greywen/web-weather/tree/69ec744ffaafee2483ae997c0689129609102816).

## Cobertura dos dez cenários

| Cenário do Free My TV | `weather-animations` | `web-weather` |
| --- | --- | --- |
| Céu limpo de dia | `clear` + `day`, sol | `sunny`, sol |
| Céu limpo à noite | `clear` + `night`, lua e estrelas | Parcial: escurecimento; sem lua/estrelas no Canvas inspecionado |
| Parcialmente nublado de dia | `cloudy`, intensidade `light`, `day` | `cloudy` com `cloudCover` reduzido |
| Parcialmente nublado à noite | `cloudy`, intensidade `light`, `night`, lua atenuada | Parcial: nuvens/escurecimento; sem lua no renderer inspecionado |
| Nublado | `overcast`, implementação específica | `cloudy` com `cloudCover` elevado |
| Neblina | `fog`, plumas e densidade por intensidade | `foggy`, partículas e camadas CSS |
| Garoa | `drizzle`, partículas/traços próprios | Parcial: `rainy` com parâmetros reduzidos, sem tipo próprio |
| Chuva | `rain`, com respingos conforme intensidade | `rainy` |
| Neve | `snow`, partículas próprias | `snowy`, inclusive acúmulo |
| Tempestade | `storm`, chuva e relâmpagos ramificados | `rainy` + `thunder: true` |

Fontes de cobertura do primeiro candidato: [tipos e normalização](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/types.ts), [paletas de dia/noite](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/sky.ts), [nuvens](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/clouds.ts), [partículas, estrelas e garoa](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/particles.ts), [lua/neblina/relâmpagos](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/atmosphere.ts). Fontes do segundo: [configuração](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/WeatherProvider.tsx), [Canvas](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/WeatherCanvas.tsx), [nuvens](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/CloudOverlay.tsx), [neblina](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/FogOverlay.tsx).

## Licenças e arquivos externos

### `g3sthousen/weather-animations`

- `LICENSE` contém MIT, copyright 2026 Marc; `package.json` também declara MIT. A licença exige preservar os avisos aplicáveis. [LICENSE](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/LICENSE), [package.json](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/package.json).
- O núcleo gráfico inspecionado desenha por código; não carrega PNG/JPG, gravações de áudio ou texturas remotas. Nuvens, plumas e lua são desenhadas em Canvas/OffscreenCanvas. Isso reduz a questão de proveniência de imagens externas nesse núcleo, sem constituir auditoria jurídica completa. [nuvens](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/clouds.ts), [atmosfera](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/atmosphere.ts).
- O núcleo importa módulos internos; React fica no wrapper separado, embora o pacote declare React como peer dependency. Pode-se avaliar o core sem trocar a UI nativa do app por React. [core](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/index.ts), [pacote](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/package.json).

### `greywen/web-weather`

- O README declara apenas “MIT”; **não há arquivo LICENSE/NOTICE/COPYING na árvore consultada**, nem campo `license` em `package.json`. Isso é uma documentação de licença menos completa que a do primeiro candidato; não significa automaticamente proibição, mas precisa ser esclarecido antes de incorporar. [README](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/README.md#license), [árvore](https://github.com/greywen/web-weather/tree/69ec744ffaafee2483ae997c0689129609102816), [pacote](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/package.json).
- Inclui cinco texturas de nuvens/fumaça e quatro sons de granizo/relâmpagos/tempestade de areia. Não identifiquei créditos/licenças individuais na árvore e nos arquivos de consumo inspecionados. Os assets não devem ser presumidos MIT só por estarem no repositório. [imagens](https://github.com/greywen/web-weather/tree/69ec744ffaafee2483ae997c0689129609102816/public/images), [sons](https://github.com/greywen/web-weather/tree/69ec744ffaafee2483ae997c0689129609102816/public/sounds), [CSS que carrega texturas](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/app/globals.css), [áudio](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/useWeatherAudio.ts).
- Seu adaptador Open-Meteo omite WMO 97: cai no fallback `sunny`, sem habilitar trovão. Também mapeia garoa/chuva congelante 56/57/66/67 para `snowy`. Não convém copiar esse adaptador. [WeatherProvider.tsx](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/WeatherProvider.tsx).

## Integração na TV: fatos e inferências

**Fatos no código:** ambos usam `canvas.getContext('2d')`; não são renderizadores 3D nativos. `weather-animations` usa DOM, CSS, ResizeObserver, requestAnimationFrame e OffscreenCanvas, e dimensiona o backing store pelo `devicePixelRatio` sem teto próprio. Seu pool tem 900 partículas. As nuvens geram sprites em `OffscreenCanvas`, com blur e cache por condição/horário; não há fallback para ausência de `OffscreenCanvas` no caminho inspecionado. Durante a transição, os dois conjuntos de partículas/nuvens ficam vivos. Isso deve ser considerado no orçamento da TV, especialmente sem limite de DPR/resolução. `web-weather` é uma aplicação Next/React maior, com overlays CSS e áudio web. [core do primeiro](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/index.ts), [partículas](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/src/core/particles.ts), [Canvas do segundo](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/components/WeatherCanvas.tsx), [dependências do segundo](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/package.json).

**Inferência para o Free My TV:** o primeiro candidato poderia ser avaliado numa página local em WebView, ou seu desenho adaptado ao renderer nativo. Nenhuma opção é uma troca direta de arquivos no GLES2 atual. Usar WebView adicionaria um runtime e exigiria medir RAM, CPU/GPU, resolução, pausas e foco na TCL de 2 GB; Canvas 2D não garante o mesmo caminho de GPU do GLES nativo. As gravações e o botão de silenciar atuais podem permanecer separados. Não há medição nesta pesquisa que permita prometer 30 fps ou RAM menor.

## Demos e recomendação

As duas URLs responderam HTTP 200 nesta pesquisa. A abertura pelo extrator textual web falhou, então isso confirma acesso HTTP, não uma inspeção visual completa. O coordenador está conferindo a demo no navegador.

- Demo declarada pelo primeiro autor: <https://g3sthousen.github.io/weather-animations/>. [README](https://github.com/g3sthousen/weather-animations/blob/01a0ea7417593802e6a48ecd09aedef8f06539bf/README.md).
- Demo declarada pelo segundo autor: <https://weather.anhejin.cn/>. [README](https://github.com/greywen/web-weather/blob/69ec744ffaafee2483ae997c0689129609102816/README.md).

**Recomendação:** selecionar `weather-animations` para uma comparação visual inicial, pois a cobertura dos dez cenários e a licença MIT foram verificadas na implementação. A qualidade estética deve ser julgada em uma prévia, e desempenho precisa ser medido antes de trocar o renderer da TV. A existência dos dez caminhos de desenho foi confirmada no código, mas o funcionamento visual no navegador/TV também precisa passar pela prévia; não equivale a um pack já homologado no Android. `web-weather` serve como referência visual, mas tem lacunas de cena e proveniência que impedem recomendá-lo como conjunto pronto e integralmente verificado.
