# 08: Apply Phase 2 to the TV

**What to build:** Put Phase 2 on the owner's TV with the owner present, after every other Phase 2 ticket is done. All steps run from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Dry-run Setup and Restore to see the new install-permission step. Then apply Setup again.
2. Install the current release build over the running Home, as in Phase 1's ticket 14.
3. Publish the first Phase 2 release with the release script.
4. Let the Home update itself from it. This first time, Android asks for confirmation.
5. Walk the checklist below with the owner.

If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06, 07 (all of Phase 2)

**Status:** ready-for-human

- [ ] Settings → Preparação shows every check Ativo, including the install permission
- [ ] Watching something in YouTube or Disney+ adds a titled entry; Netflix adds "Continuar no Netflix"; selecting entries resumes or opens the app
- [ ] The Top Shelf follows the App Tray focus, with the banner fallback for apps without entries
- [ ] An APK sent from the owner's phone installs after Android's confirmation
- [ ] A sideloaded app with an Update Source offers and installs its update
- [ ] The Home updates itself from a GitHub release, and the next update installs without asking
- [ ] The Home stays under 150 MB and smooth (janky frames under 10%) with the Top Shelf in use
