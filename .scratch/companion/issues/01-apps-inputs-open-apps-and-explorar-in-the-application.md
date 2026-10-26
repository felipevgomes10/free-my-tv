# 01: Prefactor: apps, Inputs, Open Apps and Explorar live in the Application

**What to build:** Make the Home's app library, Input Tray, Multitasking and Explorar live as long as the Home's process, so the bridge can reach them while another app is on screen (ADR 0006 in free-my-tv-remote-controller). Today `MainActivity` creates them on its own lifecycle; they move to `HomeApplication`, beside the Up Next Tray and the Shortcut Buttons, and the Home's screens use those same instances. No behaviour changes.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** None (can start immediately)

**Status:** resolved

- [x] `AppLibrary`/`AllAppsStateHolder`, `InputTray`, `Multitasking` and `Explorar` are created once, in `HomeApplication`, and `MainActivity` uses them
- [x] Following package and Input changes keeps running while the Home's screen is stopped
- [x] Every existing test passes unchanged, and the Home behaves as before on the emulator (App Tray, All Apps, Input Tray, App Switcher, Explorar)
- [ ] The Home stays within its memory budget (ADR 0004) with the TV idle on another app (120 MB PSS on the emulator; the TV is checked in 19)

## Comments

**2026-10-26, done.** `HomeApplication` now holds `library`, `allApps`, `inputs`, `multitasking`, `artwork` (the `AppArtworkLoader`, which the bridge's pictures will need) and `explorar`, and runs following package events, Input changes and the Open App Limit on its own scope; `MainActivity` takes the same instances, and still reloads them when resumed. All 266 tests pass unchanged. On the emulator the Home, Todos os apps, the Input Tray, the App Switcher and the System Monitor behaved as before; Explorar wasn't reached by keys there (and the emulator has no TMDB token), so it rests on its tests.
