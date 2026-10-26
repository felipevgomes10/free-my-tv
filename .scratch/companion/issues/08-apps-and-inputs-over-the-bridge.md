# 08: Apps and Inputs over the bridge

**What to build:** The bridge answers `list` of `apps` (installed apps, App Tray first in its order, each with a `picture` for its App Banner or icon on colour, rendered by `AppArtworkLoader` as a 16:9 PNG) and of `inputs` (the Input Tray's, hidden ones left out, with the Owner's names), and `open` of an app (`AllAppsStateHolder.open`) or an Input (`InputTray.select`). Pushes when apps are installed or removed and when Inputs change.

The spec is `.scratch/companion/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 04, 06, 08, 10, 12, 14, 16, 18 and 19 live in free-my-tv; 03, 05, 07, 09, 11, 13, 15 and 17 in free-my-tv-remote-controller.

**Blocked by:** 04 (The bridge speaks protocol 2), 07 (The Apps tab: Todos os apps and Entradas)

**Status:** resolved

- [x] Apps are listed App Tray first, then the rest by name, each with a picture the bridge serves
- [x] Hidden Inputs are left out; the Owner's names are used
- [x] Opening an app or an Input does what the Home's own tiles do
- [x] Installing or removing an app pushes the list again
- [x] Tests read the examples and drive the bridge against `FakeTvPlatform`

## Comments

**2026-10-26, done, on `companion`.** `addApps` lists the App Tray first, in its order, then the rest by name, each with `inTray` and the picture `app:<package>`; opening launches the app, as its tile does, and refuses one that isn't installed. `addInputs` lists the Input Tray's Inputs (hidden ones are already left out by the tray) with the Owner's names, the antenna as Antena on TV aberta, and opens one with `InputTray.select`. `AppPicture.kt` renders the picture: the App Banner, or the icon centred on its colour in a 320×180 tile, as PNG. The lists are pushed when the library or the Inputs change. On the emulator, with the real page: the Apps tab showed the four Inputs and the apps with their banners, and tapping Settings opened it on the TV. 4 new tests, 283 in all.
