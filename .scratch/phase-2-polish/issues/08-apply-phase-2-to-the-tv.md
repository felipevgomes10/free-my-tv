# 08: Apply Phase 2 to the TV

**What to build:** Put Phase 2 on the owner's TV with the owner present, after every other Phase 2 ticket is done. All steps run from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Dry-run Setup and Restore to see the new install-permission step. Then apply Setup again.
2. Install the current release build over the running Home, as in Phase 1's ticket 14.
3. Publish the first Phase 2 release with the release script.
4. Let the Home update itself from it. This first time, Android asks for confirmation.
5. Walk the checklist below with the owner.

If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06, 07 (all of Phase 2)

**Status:** resolved

- [x] Settings → Preparação shows every check Ativo, including the install permission
- [x] Watching something in YouTube or Disney+ adds a titled entry; Netflix adds "Continuar no Netflix"; selecting entries resumes or opens the app
- [x] The Top Shelf follows the App Tray focus; apps without entries leave it dark (the banner fallback was dropped on the TV, see Comments)
- [ ] An APK sent from the owner's phone installs after Android's confirmation (not confirmed on the TV: no app other than the Home was installed; checked end to end on the emulator; the owner will report if it fails)
- [x] A sideloaded app with an Update Source offers and installs its update (the Home, the TV's only sideloaded app, through the same path)
- [x] The Home updates itself from a GitHub release, and the next update installs without asking (0.2.0 asked; 0.2.1 and 0.2.2 didn't)
- [x] The Home stays under 150 MB and smooth (janky frames under 10%) with the Top Shelf in use (0.2.2: 89 MB PSS, 3.8% janky)

## Comments

**2026-10-23, applied.** Setup's new step (the install app-op) is applied and in the journal, so Restore undoes it first. `main` is on GitHub and the TV's Home updated itself through v0.2.0 (asked), v0.2.1 and v0.2.2 (silent), with Setup's grants and services intact after each update.

On the TV the first Top Shelf cost frames (10.9% janky, GPU 12–17 ms) and hid the UI: bright pictures sat under the Status Bar and ran on behind the App Tray, and the blurred-banner fallback turned Netflix's white banner into garbled red letters. v0.2.2 bakes each picture once, darkened and faded, drops the fallback (the top stays dark when nothing was watched), and brings janky frames to 3.8% with the GPU at 6–7 ms.

Netflix, Prime Video and Globoplay can't fill the Top Shelf: they don't say what is playing (ADR 0007). HBO Max does.

APK Drop couldn't be confirmed on the TV: the owner had no APK to send at the time. It was checked end to end on the emulator (wrong code, a non-APK, cancel, install).

Restore: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`. Releases: `scripts/release.sh VERSION`.

