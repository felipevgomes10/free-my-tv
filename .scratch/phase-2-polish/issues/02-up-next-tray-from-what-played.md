# 02: Up Next Tray from what played

**What to build:** A "Continuar assistindo" Tray on the Home, below the App Tray, built from what the Home sees playing on the TV (ADR 0007).
- Each entry shows the app's artwork, the title, how much is left ("Episódio 4, faltam 18 min") and a progress bar. The most recently watched comes first.
- Apps that name what is playing (YouTube, Disney+, Crunchyroll) get one entry per title. Watching a title again updates its entry instead of adding another. Apps that don't (Netflix, Prime Video, Globoplay) get one "Continuar no <app>" entry.
- Selecting an entry reopens it through the app's live media session, and otherwise opens the app.
- Entries survive a restart. The Tray is hidden when empty.

Visual reference: `docs/design/home-input-tray.png` (the Tray between the App Tray and the Input Tray) and `docs/design/home-app-tray.png`. Artwork is stored once, downscaled to 1080p at most (ADR 0004).

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] Playing something on the emulator or TV adds an entry with title, artwork and time left; a second play of the same title updates it
- [x] An untitled session becomes one "Continuar no <app>" entry per app
- [x] Selecting an entry resumes through a live session, or launches the app when the session is gone
- [x] Entries persist across a Home restart; the Tray is hidden when empty
- [x] Tests cover entry creation, de-duplication, untitled collapse, ordering and resume vs launch, through FakeTvPlatform
