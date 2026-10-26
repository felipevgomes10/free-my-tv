# 06: The Shortcut Buttons over the bridge

**What to build:** The bridge answers `list` of `shortcuts` with each Shortcut Button and the name of its target (`ShortcutButtons.targetOf`), and `open` of a `shortcut` with `ShortcutButtons.perform`, as the Remote's button does; the list is pushed again when the Owner reassigns a button.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 05 (The Shortcut Buttons row)

**Status:** resolved

- [x] The list names each button's target: an app's label, an Input's name, or the App Switcher
- [x] Opening a button performs its target, as the Remote's button does
- [x] Reassigning a button in Settings pushes the list again
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `addShortcuts` lists every button by its protocol id with the name of what it does (the app's label, the Input's name given by the Owner, Apps abertos, Entradas or Configurações), the name printed on the button when its app isn't installed, and opens it with `ShortcutButtons.perform`, refusing an app that isn't installed. The list is pushed when the Owner's assignments change. On the emulator, through the bridge: the six buttons listed, Netflix refused as not installed, and the Input button opened the Input Tray. 5 new tests, 279 in all. Found on the way: `webRemotePage` took a rebuilt local zip for up to date; a local zip is now always unpacked again.
