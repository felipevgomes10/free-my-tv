# 12: Up Next over the bridge

**What to build:** The bridge answers `list` of `up-next` from the Up Next Tray, each entry with its picture (its local artwork served at `/pictures/{id}`), title, subtitle, app label, progress and time left, and `open` of an entry with `UpNextTray.select`, which resumes it as the Home does. Pushes when the tray changes.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 11 (The Assistir tab: Continuar assistindo)

**Status:** resolved

- [x] Entries are listed as the Up Next Tray holds them, with pictures the bridge serves
- [x] Opening an entry resumes it the way the Home's tray does
- [x] A change in the tray pushes the list
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `addUpNext` reads the tray again on each ask (`UpNextTray.refresh`, as when the Home comes to the front) and lists its entries with `minutesLeft` rounded up as the TV writes it, `progress`, and the picture `upnext:<key>` when the entry has artwork, or the app's banner otherwise; `open` with `upNext` goes through `UpNextTray.select`, refusing an entry no longer on the tray. `UpNextPicture.kt` sends the still as JPEG, decoded near 480 px wide; the picture route now names JPEG as `image/jpeg`. The list is pushed whenever the tray's entries change. On the emulator, with the real page: Continuar assistindo showed the four entries left from earlier tests, with time left, progress and a real still. 2 new tests, 289 in all.
