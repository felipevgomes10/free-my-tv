# 02: Linking in Settings → FMTV Controller

**What to build:** A new **FMTV Controller** section in the Home's Settings where the Owner links the Home to the TV's own debugging bridge, once (ADR 0005 in free-my-tv-remote-controller).
- **The section:** it says whether the Home is linked, and offers "Vincular" (or "Vincular de novo" when linked).
- **Linking:** the Home connects to the TV's ADB at `127.0.0.1:5555` with its own key pair, generated once and kept in its private storage. Android shows "Allow debugging?"; the Owner ticks "always allow" and accepts with the Remote. Settings tells apart success, a refused or unanswered prompt, and network debugging being off, each with what to do.
- **The seam:** the ADB connection sits behind `TvPlatform`: link, report whether linked and why not, and send a key with a direction. `FakeTvPlatform` plays its part in tests. The real one uses the `dadb` library.
- **Setup status:** a new check that network debugging answers on `127.0.0.1:5555`.

Start by designing the FMTV Controller section and the Linking states in `design.pen`, in the style of Settings and APK Drop, and export them to `docs/design/`.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** 01 (Prototype: the Home reaches the TV Remote Service), resolved by ADR 0005

**Status:** ready-for-agent

- [ ] The FMTV Controller section and the Linking states match their new design
- [ ] On the emulator, Linking shows Android's "Allow debugging?" prompt; accepting it makes Settings show the Home as linked, and it stays linked after the Home restarts
- [ ] A refused prompt, an unanswered one and network debugging off each show their own message and let the Owner try again
- [ ] Setup status reports network debugging off
- [ ] Tests cover Linking's outcomes against `FakeTvPlatform`
