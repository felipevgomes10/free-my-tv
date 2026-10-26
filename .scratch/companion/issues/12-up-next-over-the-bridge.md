# 12: Up Next over the bridge

**What to build:** The bridge answers `list` of `up-next` from the Up Next Tray, each entry with its picture (its local artwork served at `/pictures/{id}`), title, subtitle, app label, progress and time left, and `open` of an entry with `UpNextTray.select`, which resumes it as the Home does. Pushes when the tray changes.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 11 (The Assistir tab: Continuar assistindo)

**Status:** ready-for-agent

- [ ] Entries are listed as the Up Next Tray holds them, with pictures the bridge serves
- [ ] Opening an entry resumes it the way the Home's tray does
- [ ] A change in the tray pushes the list
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
