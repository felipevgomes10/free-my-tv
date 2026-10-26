# 02: Linking in Settings → FMTV Controller

**What to build:** A new **FMTV Controller** section in the Home's Settings where the Owner links the Home to the TV's own debugging bridge, once (ADR 0005 in free-my-tv-remote-controller).
- **The section:** it says whether the Home is linked, and offers "Vincular" (or "Vincular de novo" when linked).
- **Linking:** the Home connects to the TV's ADB at `127.0.0.1:5555` with its own key pair, generated once and kept in its private storage. Android shows "Allow debugging?"; the Owner ticks "always allow" and accepts with the Remote. Settings tells apart success, a refused or unanswered prompt, and network debugging being off, each with what to do.
- **The seam:** the ADB connection sits behind `TvPlatform`: link, report whether linked and why not, and send a key with a direction. `FakeTvPlatform` plays its part in tests. The real one uses the `dadb` library.
- **Setup status:** a new check that network debugging answers on `127.0.0.1:5555`.

Start by designing the FMTV Controller section and the Linking states in `design.pen`, in the style of Settings and APK Drop, and export them to `docs/design/`.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 01 (Prototype: the Home reaches the TV Remote Service), resolved by ADR 0005

**Status:** resolved

- [x] The FMTV Controller section and the Linking states match their new design (the design.pen frames are in the editor; the file is committed once saved)
- [x] On the emulator, Linking shows Android's "Allow debugging?" prompt; accepting it makes Settings show the Home as linked, and it stays linked after the Home restarts
- [x] A refused prompt, an unanswered one and network debugging off each show their own message and let the Owner try again (refused and unanswered are one message: adbd keeps a refused connection unauthorised instead of closing it, so both end at the 60-second timeout)
- [x] Setup status reports network debugging off
- [x] Tests cover Linking's outcomes against `FakeTvPlatform`

## Comments

**2026-10-26, done.** `TvAdb` makes the Home's key pair once (in its private files, listed by Android as "FMTV Controller@Free My TV Home") and connects to `127.0.0.1:5555` with `dadb` 2.0.0; an unknown key makes Android show "Allow USB debugging?", and the Home waits up to 60 seconds. `WebRemoteLink` turns that into the section's states; looking (`refresh`) never shows the prompt, only Vincular does. The Setup check `NetworkDebugging` is a plain TCP reach of the port. 6 new tests, 245 in all.

On the emulator, from the Home itself: Vincular showed the prompt; Cancel ended, after the timeout, on "O aviso não foi aceito"; "Always allow" plus Allow showed "Vinculado", still linked after force-stopping the Home. Previews: `docs/design/settings-fmtv-controller-*.jpg`.
