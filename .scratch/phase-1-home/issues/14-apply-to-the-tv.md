# 14: Apply to the TV

**What to build:** Put Phase 1 on the owner's TCL (Google TV, Android 12, `192.168.100.18`) in one sitting, with the owner present, only after every other ticket is done. The owner approves the bloatware list package by package; Restore is dry-run first so the way back is known; then the Home is installed, Setup runs, and the owner walks through the checklist on the real TV. If anything critical fails, Restore runs immediately.

**How (prepared in 13):** all from the repo root on the Mac, with the TV at `192.168.100.18:5555`.

1. Review `config/bloatware-candidates.md` with the owner and copy each approved package into `config/bloatware-approved.txt`.
2. Build and install the Home: `./gradlew :app:assembleDebug`, then `adb -s 192.168.100.18:5555 install -r app/build/outputs/apk/debug/app-debug.apk` (debug-signed: later updates must be signed with the same key; `scripts/install-emulator.sh` refuses the TV on purpose).
3. Dry run: `scripts/setup.sh --serial 192.168.100.18:5555` and `scripts/restore.sh --serial 192.168.100.18:5555 --without-journal`; read both plans with the owner.
4. Apply: `scripts/setup.sh --serial 192.168.100.18:5555 --apply` (asks to type "sim"); the journal lands in `~/.freemytv/`.
5. Walk the checklist below; Settings → Preparação must show every item Ativo.
6. If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

**Blocked by:** 01, 02, 03, 04, 05, 06, 07, 08, 09, 10, 11, 12, 13 (all of Phase 1)

**Status:** ready-for-human

- [x] Owner approved the bloatware list
- [x] Restore dry run printed a plan that matches the Setup about to be applied
- [x] Pressing Home opens our Home; holding Home opens the Notification Panel; Home twice opens the App Switcher
- [x] Netflix, Globoplay, Prime Video, Disney+, YouTube and Input buttons all work
- [ ] Each Input switches correctly, including the antenna
- [ ] Chromecast, AirPlay and Miracast still receive a Cast
- [ ] Screensaver and Assistant still work
- [ ] The Home stays under 150 MB in use after an hour of normal use
- [x] Restore is known to work (rehearsed on the emulator) and its command is written down for the owner
