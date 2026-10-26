# 10: Open Apps over the bridge

**What to build:** The bridge answers `list` of `open-apps` from `Multitasking.openApps()` with pictures and the `playing` flag, `open` with `Multitasking.switchTo`, and `close-app` with `Multitasking.close` (refused for a playing app; closing through Settings' Force stop, ADR 0010, which shows its cover on the TV). Pushes the list when an app opens or closes.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 08 (Apps and Inputs over the bridge), 09 (The Apps tab: Abertos)

**Status:** ready-for-agent

- [ ] Open Apps are listed most recent first, with their pictures and `playing`
- [ ] Closing a playing app is refused; closing another force stops it and pushes the list
- [ ] Switching does what the App Switcher does
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
