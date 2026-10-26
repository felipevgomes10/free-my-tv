# 01: Prototype: the Home reaches the TV Remote Service

**What to build:** A throwaway prototype, on the owner's real TV with the owner present, that answers the Web Remote's riskiest question: can the Home send keys to other apps through the TV Remote Service (ADR 0001 in free-my-tv-remote-controller)?
- **Connect:** the Home opens a connection to the TV Remote Service at `127.0.0.1`.
- **Link:** Android shows its code on the TV, and the owner types it with the Remote into a bare dialog. The Home keeps the credential.
- **Send:** a debug-only trigger sends a few keys (right, OK, Back) while Netflix or YouTube is on screen.
- **Report:** write the findings in this file's Comments: what worked, the service's package name, how long a key takes to arrive, whether Linking survives a Home restart and a TV restart, and whether the Android TV emulator has the service.

If the TV refuses the connection or Linking, stop. ADR 0001 is reopened before any other ticket starts.

The spec is `.scratch/web-remote/spec.md` in free-my-tv-remote-controller. Tickets are numbered across both repositories: 01, 02, 06, 07 and 09 live in free-my-tv; 03, 04, 05 and 08 in free-my-tv-remote-controller.

**Blocked by:** None (can start immediately)

**Status:** needs-info

- [ ] The Home connects to the TV Remote Service at `127.0.0.1` on the real TV (refused: see Comments)
- [x] Linking succeeds with the code Android shows (through `127.0.0.1`, typed by the probe rather than the Remote)
- [ ] A right, an OK and a Back sent by the Home act inside Netflix or YouTube (not reached)
- [x] The findings, including the emulator's answer, are written under Comments
- [x] The prototype code is removed or kept only behind a debug-only switch (no code was added to the Home)

## Comments

**2026-10-26, the TV refuses keys from itself: ADR 0001 is reopened.** Probed from the Mac with the `androidtvremote2` client (protocol v2), reaching the TV's loopback through `adb forward`, so connections arrive at the TV Remote Service from `127.0.0.1` inside the TV, as the Home's would. No code was added to the Home.

- The service is `com.google.android.tv.remote.service`, listening on `::` (every address) at 6466 (commands) and 6467 (pairing), on the TCL (Android 12, "Smart TV Pro", sw 7.00.999235495) and on the Android TV emulator alike.
- **Pairing through `127.0.0.1` works.** Android showed its 6-character code ("Solicitação de pareamento de dispositivos") and accepted it.
- **The command port refuses every connection from the device itself.** From `127.0.0.1` it drops the TLS handshake (EOF) even with the paired certificate. On the emulator, a relay inside the device to its own IPv4 (`10.0.2.16`) and its own IPv6 were refused the same way, while the same relay to the pairing port worked.
- **From another device it works.** The certificate paired through `127.0.0.1` connected from the Mac to `192.168.100.18:6466` without pairing again, and read the TV's model and power state.
- So a bridge inside the Home can link, but can never send a key through the TV Remote Service. No key was sent to the real TV: someone opened YouTube during the probe.

**Leads for the reopened ADR**, measured without touching the screen:
- The TV's own ADB daemon listens on `::`, port 5555 (`service.adb.tcp.port=5555`, not persisted as `persist.adb.tcp.port`; the TV has been up 5 days, so whether it survives a reboot is unchecked). `cmd input keyevent 0` takes 50–80 ms over `adb shell`. A Home acting as an ADB client on `127.0.0.1:5555`, authorised once on the TV's "Allow debugging?" prompt, could inject every key.
- The TV Remote Service works from any other device on the network, so a bridge on an always-on machine would work as designed.

The TV now lists two paired "FMTV Controller probe" clients from these probes; they can be forgotten in Android's settings.
