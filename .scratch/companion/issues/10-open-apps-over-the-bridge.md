# 10: Open Apps over the bridge

**What to build:** The bridge answers `list` of `open-apps` from `Multitasking.openApps()` with pictures and the `playing` flag, `open` with `Multitasking.switchTo`, and `close-app` with `Multitasking.close` (refused for a playing app; closing through Settings' Force stop, ADR 0010, which shows its cover on the TV). Pushes the list when an app opens or closes.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 08 (Apps and Inputs over the bridge), 09 (The Apps tab: Abertos)

**Status:** resolved

- [x] Open Apps are listed most recent first, with their pictures and `playing`
- [x] Closing a playing app is refused; closing another force stops it and pushes the list
- [x] Switching does what the App Switcher does
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `addOpenApps` lists `Multitasking.openApps()`, most recently used first, with the picture `app:<package>` and `playing`; switching to one is the `open` with `package` from ticket 08, which launches it, as the App Switcher's `switchTo` does. `close-app` goes through `Multitasking.close` (Force stop, ADR 0010), refused with `playing` for an app playing music and `not-open` for one that isn't open. Nothing tells the Home when an app opens or closes, so `watchOpenApps` looks every 5 s while a phone is ready and pushes the list when it changed; with no phone it does nothing. On the emulator, with the real page: Abertos showed Play Store, its ✕ force stopped it and the section went away, and opening it again on the TV brought it back on the phone within seconds. 4 new tests, 287 in all.
