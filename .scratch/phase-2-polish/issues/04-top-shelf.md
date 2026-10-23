# 04: Top Shelf

**What to build:** Focusing an app in the App Tray fills the top of the Home with what the owner last watched in it: the artwork, the app's name, the title and how much is left.
- **Several entries:** when the app has more than one Up Next entry, the Top Shelf cycles through them every few seconds while focus rests, with dots for the position.
- **Nothing to show:** when the app has no entries, the Top Shelf shows the app's banner, enlarged and blurred. The blur is computed once per image and cached, never live (ADR 0004).
- **Following focus:** fast moves across the App Tray don't load artwork for every tile passed.
- **Moving down:** going down to the Up Next or Input Tray shrinks and dims the Top Shelf.

Visual reference: `docs/design/home-app-tray.png` and `docs/design/home-input-tray.png`.

**Blocked by:** 02 (Up Next Tray from what played)

**Status:** ready-for-agent

- [ ] Focusing an app with entries shows its most recent one; with several, they cycle with dots
- [ ] Focusing an app without entries shows its enlarged, blurred banner
- [ ] Scrolling fast across the App Tray settles on the focused app without loading the ones passed
- [ ] Moving to the lower Trays shrinks and dims the Top Shelf
- [ ] The Home stays under 150 MB on the TV with the Top Shelf in use
- [ ] Tests cover the entries per app, the fallback, cycling while focus rests and the debounce
