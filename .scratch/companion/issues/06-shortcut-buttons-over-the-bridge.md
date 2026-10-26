# 06: The Shortcut Buttons over the bridge

**What to build:** The bridge answers `list` of `shortcuts` with each Shortcut Button and the name of its target (`ShortcutButtons.targetOf`), and `open` of a `shortcut` with `ShortcutButtons.perform`, as the Remote's button does; the list is pushed again when the Owner reassigns a button.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 05 (The Shortcut Buttons row)

**Status:** ready-for-agent

- [ ] The list names each button's target: an app's label, an Input's name, or the App Switcher
- [ ] Opening a button performs its target, as the Remote's button does
- [ ] Reassigning a button in Settings pushes the list again
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
