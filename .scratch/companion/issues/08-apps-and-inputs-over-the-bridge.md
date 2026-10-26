# 08: Apps and Inputs over the bridge

**What to build:** The bridge answers `list` of `apps` (installed apps, App Tray first in its order, each with a `picture` for its App Banner or icon on colour, rendered by `AppArtworkLoader` as a 16:9 PNG) and of `inputs` (the Input Tray's, hidden ones left out, with the Owner's names), and `open` of an app (`AllAppsStateHolder.open`) or an Input (`InputTray.select`). Pushes when apps are installed or removed and when Inputs change.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 07 (The Apps tab: Todos os apps and Entradas)

**Status:** ready-for-agent

- [ ] Apps are listed App Tray first, then the rest by name, each with a picture the bridge serves
- [ ] Hidden Inputs are left out; the Owner's names are used
- [ ] Opening an app or an Input does what the Home's own tiles do
- [ ] Installing or removing an app pushes the list again
- [ ] Tests read the examples and drive the bridge against `FakeTvPlatform`
