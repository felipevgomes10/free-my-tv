# 01: Minimal Home with App Tray

**What to build:** The first end-to-end slice of the Home. On a Google TV emulator (Android 12, API 31), pressing Home opens our Home instead of the Stock Launcher. It shows the Status Bar with the clock and date, and the App Tray with the owner's pinned apps, each shown by its App Banner (or its icon over a color taken from it when the app has no banner). Moving focus with the D-pad enlarges the focused tile and shows the app's name under it; pressing OK opens the app. Until the owner pins anything, the App Tray starts with a sensible default set of installed apps. The "Todos os apps" tile closes the tray (it does nothing yet; All Apps lands in 02).

This ticket also lays the ground every later ticket builds on: the Kotlin + Compose for TV project targeting `armeabi-v7a`, the single TV platform port through which all Home logic talks to Android, a fake of that port, the JVM test harness that drives screen state holders against the fake, and an install script for the emulator.

Visual reference: `docs/design/home-app-tray.png` (Top Shelf and Up Next Tray are Phase 2 and not built here; leave the top area as a plain backdrop). Respect ADR 0004: no live blur, translucent fills only.

**Blocked by:** None (can start immediately)

**Status:** ready-for-agent

- [x] The app declares itself as a HOME/launcher activity and opens when Home is pressed on the emulator
- [x] All Home logic reaches Android only through the TV platform port; the Android adapter behind it holds no decisions
- [x] A fake port and a JVM test harness exist; tests drive the Home state holder in glossary language (e.g. "the App Tray shows the pinned apps in the owner's order", "OK on a tile asks the platform to launch that app")
- [x] App Banners are used when present; apps without one show their icon over a derived color
- [x] Pinned apps and their order persist across Home restarts
- [x] The install script refuses any device that is not an emulator (it never targets the TV)
- [x] UI strings live in resources, in pt-BR only
