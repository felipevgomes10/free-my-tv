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

**Status:** ready-for-agent

- [ ] Linking succeeds through the TV's "Allow debugging?" prompt and Settings shows the Home as linked
- [ ] After a reboot of the TV, with the owner's agreement, whether network debugging and Linking are still there is written under Comments
- [ ] The QR code opens the Web Remote in Safari; after adding it to the Home Screen, Pairing works and the TV shows the phone's name
- [ ] Whether the Home Screen app's storage is separate from Safari's on this iOS is written under Comments
- [ ] The Home Screen shows FMTV Controller with its icon, and it opens standalone in portrait
- [ ] With the TV off, FMTV Controller still opens, on Sem conexão rather than Safari's error page
- [ ] The Touchpad and every button act inside the Home, Netflix and YouTube, close to the Remote's speed (each key's time written under Comments)
- [ ] Holding on the Touchpad enters Edit Mode; Home twice opens the App Switcher; holding Home opens the Notification Panel
- [ ] Whether the haptic tick works on this iPhone is written under Comments
- [ ] Turning the TV off works; whether turning it on works is written under Comments
- [ ] Revoking the iPhone stops it at once and its Web Remote offers to pair again
- [ ] The Home stays within its memory budget with the bridge running
