# 09: Apply the Web Remote to the TV

**What to build:** Put the Web Remote on the owner's TV and iPhone 17 Pro with the owner present, after every other Web Remote ticket is done.
1. Release the Web Remote page with its release script, and pin that version in free-my-tv.
2. Publish a Home release, and let the TV update itself.
3. Dry-run Setup to confirm network debugging is in place; apply it if it plans anything.
4. Link the Home in Settings → FMTV Controller, and pair the owner's iPhone from Parear aparelho.
5. Walk the checklist below with the owner, and write what differed under Comments.

If anything critical fails: `scripts/restore.sh --serial 192.168.100.18:5555 --apply`.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 07 (Keys from a Paired Phone reach the TV), 08 (Out of reach), and every earlier Web Remote ticket

**Status:** resolved

- [x] Linking succeeds through the TV's "Allow debugging?" prompt and Settings shows the Home as linked
- [x] After a reboot of the TV, with the owner's agreement, whether network debugging and Linking are still there is written under Comments
- [x] The QR code opens the Web Remote in Safari; after adding it to the Home Screen, Pairing works and the TV shows the phone's name
- [x] Whether the Home Screen app's storage is separate from Safari's on this iOS is written under Comments
- [x] The Home Screen shows FMTV Controller with its icon, and it opens standalone in portrait
- [ ] With the TV off, FMTV Controller still opens, on Sem conexão rather than Safari's error page (not checked on the TV: in standby the Home kept answering, so the page stayed connected; checked in the browser in ticket 08)
- [x] The Touchpad and every button act inside the Home, Netflix and YouTube, close to the Remote's speed (each key's time written under Comments)
- [x] Holding on the Touchpad enters Edit Mode; Home twice opens the App Switcher; holding Home opens the Notification Panel
- [x] Whether the haptic tick works on this iPhone is written under Comments
- [x] Turning the TV off works; whether turning it on works is written under Comments
- [x] Revoking the iPhone stops it at once and its Web Remote offers to pair again
- [x] The Home stays within its memory budget with the bridge running

## Comments

**2026-10-26, applied with the owner and an iPhone 17 Pro.** Page v0.1.0 pinned and the Home's v0.5.0 published; the TV updated itself from Atualizações. Setup's dry run planned nothing but opening the Home: network debugging was in place.

- **Linking:** Vincular showed "Permitir depuração?"; with "Sempre permitir", Android saved the Home's key (`alwaysAllow = true`, no write error, unlike the emulator).
- **Pairing:** the QR code opened Safari on Adicionar à Tela de Início; the Home Screen icon opened full screen straight on the form, so on this iOS the Home Screen app's storage is separate from Safari's, as the spec expected. The TV confirmed the phone's name.
- **Keys:** the Touchpad and the buttons worked in the Home, YouTube and Netflix, at a speed the owner found as good as the Remote (each key is `cmd input` on the TV, 50–80 ms, about 70 ms from page to ack on the emulator). Holding the Touchpad opened Edit Mode; Home twice opened the App Switcher; volume, mute and power worked, and turning the TV on from the phone worked too.
- **Haptic:** no tick on this iPhone; the controls work the same without it.
- **Revoking** the iPhone in Settings showed "Este iPhone não está mais pareado" at once, and Parear de novo worked.
- **Memory:** 136 MB PSS with the iPhone connected, within the 150 MB budget.
- **Reboot** (with the owner's agreement, `adb reboot`): network debugging came back by itself (`service.adb.tcp.port=5555` again, up 0 min), the bridge answered, and the iPhone's keys arrived without the prompt: Linking survives a reboot.

Two problems found and fixed on the TV, installed over v0.5.0 for the owner to check, both now working:
- **The App Switcher closed two apps on one swipe.** A swipe sends several Ups in quick succession, and each Up closed the app that had just taken the focus (a held Up on the Remote would too). Up now closes one app per press: repeats are ignored, and so is any Up within 500 ms of a close.
- **Holding Home didn't open the Notification Panel.** The key filter that does it for the Remote never sees keys from ADB, and a long-pressed Home from ADB is Android's own. The bridge now opens the Home's Notification Panel itself for a held Home.
