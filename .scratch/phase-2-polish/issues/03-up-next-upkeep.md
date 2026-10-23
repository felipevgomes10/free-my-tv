# 03: Up Next upkeep

**What to build:** The Up Next Tray keeps itself tidy and under the owner's control.
- **Leaving on its own:** an entry leaves once it is finished (position at 95% or more of its duration, or less than 2 minutes left), after 30 days without being watched, or when its app is uninstalled. The Tray holds at most 20 entries.
- **Kept out:** music and short media (Spotify, system sounds).
- **Edit Mode:** on this Tray it offers only "Remover da lista".
- **Missing access:** when the Home can't see what is playing (no notification access), Settings → Preparação says so, so the owner knows why the Tray is empty.

**Blocked by:** 02 (Up Next Tray from what played)

**Status:** ready-for-agent

- [ ] Finished entries leave at the 95% / 2-minute rule; untouched entries leave after 30 days; the 21st entry pushes out the oldest
- [ ] Uninstalling an app removes its entries
- [ ] Music and media shorter than a few minutes never create entries
- [ ] Holding OK on an entry offers "Remover da lista", which removes it
- [ ] Tests cover the finish rule at its boundaries, the expiry, the cap, the uninstall drop and the media filter
