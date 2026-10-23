# 14: Apply to the TV

**What to build:** Put Phase 1 on the owner's TCL (Google TV, Android 12, `192.168.100.18`) in one sitting, with the owner present, only after every other ticket is done. The owner approves the bloatware list package by package; Restore is dry-run first so the way back is known; then the Home is installed, Setup runs, and the owner walks through the checklist on the real TV. If anything critical fails, Restore runs immediately.

**How (prepared in 13):** all from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Review `config/bloatware-candidates.md` with the owner and copy each approved package into `config/bloatware-approved.txt`.
2. Build and install the Home: `./gradlew :app:assembleRelease`, then `adb -s 192.168.100.18:5555 install -r app/build/outputs/apk/release/app-release.apk` (the release build, signed with the debug key: the debug build is several times slower on the TV, and later updates must be signed with the same key; `scripts/install-emulator.sh` refuses the TV on purpose).
3. Dry run: `scripts/setup.sh --serial 192.168.100.18:5555` and `scripts/restore.sh --serial 192.168.100.18:5555 --without-journal`; read both plans with the owner.
4. Apply: `scripts/setup.sh --serial 192.168.100.18:5555 --apply` (asks to type "sim"); the journal lands in `~/.freemytv/`.
5. Walk the checklist below; Settings → Preparação must show every item Ativo.
6. If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13 (all of Phase 1)

**Status:** resolved

- [x] Owner approved the bloatware list
- [x] Restore dry run printed a plan that matches the Setup about to be applied
- [x] Pressing Home opens our Home; holding Home opens the Notification Panel; Home twice opens the App Switcher
- [x] Netflix, Globoplay, Prime Video, Disney+, YouTube and Input buttons all work
- [x] Each Input switches correctly, including the antenna
- [x] Chromecast, AirPlay and Miracast still receive a Cast
- [x] Screensaver and Assistant still work
- [x] The Home stays under 150 MB in use after an hour of normal use (waived by the owner: they will report it if they notice anything; readings were 92–108 MB)
- [x] Restore is known to work (rehearsed on the emulator) and its command is written down for the owner

## Comments

**2026-10-23, applied.** Setup is applied on the TV and the owner walked the checklist. On the way:

- The setup app's recovery screen took Home over once the Stock Launcher was off. Setup now disables `setupwraith` before `launcherx`.
- The TV lost Wi-Fi mid-Setup. The journal is now written step by step.
- Setup notifications are withdrawn once that part holds again.
- The System Monitor rankings never landed on the TV (measured inside a state update that kept retrying). Comma decimals in `dumpsys` are parsed too.
- The focus moved twice per press and the frame rate dropped. The TV now runs the release build (janky frames 76% → ~4%), and the key filter answers on its own thread.
- The App Switcher label now says "Aberto, não fecha sozinho".

Restore: `scripts/restore.sh --serial 192.168.100.18:5555 --apply` (journal in `~/.freemytv/`).

