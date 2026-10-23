# 07: Settings

**What to build:** The gear in the Status Bar opens Settings, the Home's own settings screen, with a section list on the left and the selected section on the right:

- **Widgets**: an on/off toggle for the Weather Widget and for the System Monitor Widget, and the weather city (a city or "Automático"). Turning a Widget off makes it a Hidden Widget: it disappears from the Status Bar and stops gathering data.
- **Transmitir para esta TV**: the TV's name on the network and the status of each Cast Receiver — Chromecast, AirPlay and Miracast — as installed and enabled or not.
- **Preparação**: placeholder showing whether Setup is in effect (filled in by 12).
- **Sobre**: Home version, TV model, Android version and Security Patch.
- **Configurações do Android**: opens Android Settings.

Visual reference: `docs/design/settings.png`.

**Blocked by:** 05 (Weather Widget), 06 (System Monitor Widget)

**Status:** ready-for-agent

- [ ] Each Widget can be turned off and on; the change is immediate and persists
- [ ] Choosing a city or "Automático" updates the Weather Widget
- [ ] Cast status reflects the presence and enabled state of Chromecast, AirPlay and Miracast receivers
- [ ] "Configurações do Android" opens Android Settings
- [ ] Settings is built so later tickets can add sections (Botões do controle in 11) and rows (open app limit in 10)
- [ ] Tests cover widget toggling end to end with the Status Bar and the Cast status mapping
