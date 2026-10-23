# 08: Updates

**What to build:** The Updates icon in the Status Bar opens Updates, with three blocks:

1. **Sistema**: the Security Patch date, firmware and Android version, a warning in the attention color when the patch is more than 6 months old, and "Procurar atualização do sistema", which opens the system updater (on the owner's TV, Google's system update screen).
2. **Apps da Play Store**: whether Play Store auto-update is on, and a button that opens the Play Store's app management.
3. **Apps instalados por APK**: each app not installed by the Play Store, with its version. Associating an update source is Phase 2; this ticket only lists them.

The Updates icon in the Status Bar shows the attention dot whenever the patch warning is active.

Visual reference: `docs/design/updates.png`.

**Blocked by:** 01 (Minimal Home with App Tray)

**Status:** ready-for-agent

- [ ] The Security Patch age is computed from the reported patch date; above 6 months the warning and Status Bar dot appear
- [ ] "Procurar atualização do sistema" opens the system updater
- [ ] The Play Store block reflects auto-update state and opens app management
- [ ] Apps not installed by the Play Store are listed with their version
- [ ] Tests cover the 6-month rule at its boundary and the installer-source split
