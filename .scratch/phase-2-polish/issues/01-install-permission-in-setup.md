# 01: Install permission in Setup

**What to build:** Setup lets the Home install apps, the groundwork for APK Drop, Update Sources and the Home's self-update. It grants the Home the "install unknown apps" app-op over ADB, and Restore resets it, so Restore still returns the TV to how it was. The Home declares the permission. Settings → Preparação shows the new check. If a system update takes the grant away, the Setup Watchdog announces it, the way it does for every other part of Setup.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [ ] The Setup plan grants the install app-op, and the Restore plan resets it (dry run shows both)
- [ ] Settings → Preparação lists the install permission with the other checks
- [ ] Losing the grant raises one Setup notification, which is withdrawn once the grant is back
- [ ] Python tests cover the new step in Setup and its inverse in Restore; Kotlin tests cover the new check in the Setup Watchdog
