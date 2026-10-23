# 11: Shortcut Buttons

**What to build:** Every Shortcut Button on the remote keeps working after Setup and can be reassigned. On the owner's TV, Netflix, Prime Video and YouTube are handled by the Stock Launcher (which Setup disables), while Globoplay, Disney+ and Input are handled by TCL's system policy (see ADR 0005). The Home runs an accessibility service that filters these key events before the system: by default each button opens its own app, and the Input button opens the Home with focus on the Input Tray.

A new Settings section, "Botões do controle", lists each Shortcut Button (Netflix, Globoplay, Prime Video, Disney+, YouTube, Input) plus "Home segurado" (fixed to Notificações), showing its current assignment and marking changed ones. Each button can be assigned any installed app, any Input, or the App Switcher.

The filter also handles long-press Home and opens the Notification Panel (from 09): the system's long-press intent can only reach system apps (see ADR 0005).

If the system turns the accessibility service off, the Home turns it back on using the secure-settings permission granted by Setup.

Key codes observed on the owner's remote: Netflix 472, Prime Video 473, YouTube 569, Globoplay 586, Disney+ 762, Input 471 (scan codes, mapped to TCL key codes by the system key layout).

Visual reference: `docs/design/settings-remote-buttons.png`.

**Blocked by:** 04 (Input Tray), 07 (Settings), 10 (App Switcher and Open App Limit)

**Status:** ready-for-agent

- [x] With default assignments, each Shortcut Button opens its own app even when the Stock Launcher is disabled (unit-tested by scan code; the emulator can't produce TCL scan codes, so the real remote is checked in 14)
- [x] Input opens the Home focused on the Input Tray
- [x] Each button can be reassigned to an app, an Input or the App Switcher, and the change persists
- [x] Long-press Home opens the Notification Panel; a short Home press is untouched
- [x] Keys not assigned through this feature pass through untouched
- [x] The Home re-enables its accessibility service when it finds it disabled and holds the permission to do so
- [x] Tests cover assignment resolution for every target kind and the pass-through rule
