# 06: Trailer

**What to build:** "Assistir trailer" on the details plays the Title's Trailer inside the Home, full screen, in YouTube's embedded player (ADR 0009), as in `docs/design/explorar-trailer.png`.
- **Which video:** an official Portuguese trailer, then any Portuguese trailer, then English, then a teaser. Without any, "Assistir trailer" is hidden.
- **The screen:** "Voltar aos detalhes", the title, "Trailer oficial", a progress bar with elapsed and total time, and 10 s back, play/pause and 10 s forward.
- **The player:** YouTube's own controls are off; the design's controls drive it through its JavaScript API. They hide after 4 s without a key and come back on any key.
- **Fallback:** a video that refuses embedding (errors 101 and 150), or a WebView failure, opens in the YouTube app instead.
- **Lifecycle:** Back, or the pill, returns to the details. Home or sleep stops the trailer. The WebView is destroyed on leaving the screen.

**Blocked by:** 04 (Title details)

**Status:** ready-for-agent

- [ ] The trailer chosen follows the order above; no trailer hides the button
- [ ] On the emulator a trailer plays with the design's controls, and seek, pause and the progress bar work
- [ ] A video that refuses embedding opens in the YouTube app
- [ ] Leaving the screen stops playback and frees the player
- [ ] Tests cover the trailer choice and the fallback decision
