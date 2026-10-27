# 04: Validate the dedicated button and overlay together on the TV

**Status:** ready-for-human

**Blocked by:** [Home 03: Bridge command](03-open-app-switcher-command.md), [Controller 03: Integrated page bundle](../../../../free-my-tv-remote-controller/.scratch/app-switcher-overlay/issues/03-integrated-page-bundle.md).

**What to build:** A recorded acceptance run of the combined Home and Controller candidate on the target TCL with an iPhone and the Remote. Use the full matrix in the [spec](../spec.md); automated results alone cannot resolve this ticket. Use the existing TV-access/install workflow when a device is available; otherwise leave the TV criteria unchecked and record what access is missing.

- [ ] Record candidate builds/page checksum, TV model/Android version and phone/browser in `../acceptance-results.md`.
- [ ] One dedicated tap overlays YouTube, a subscription app with protected playback and an app menu while each underlying surface remains live and visible; record playback differences and any black frame or Home flash.
- [ ] Verify Back returns to the same state, switching to another app, selecting the underlying app, empty state and opening over Home.
- [ ] Verify phone/Remote arrows and OK do not leak to the app, Home and Shortcut Buttons clean up, and existing double/held Home, Abertos, playback and volume remain usable.
- [ ] Verify closing a background app, underlying/media/protected-app close guards, repeated opens/two phones, service loss, sleep/wake and disconnect/reconnect.
- [ ] Verify unavailable/busy/old-bridge failures, timeout/no replay, resource cleanup and Home memory against ADR 0004.
- [ ] Record pass/fail evidence and follow-up tickets for failures; resolve only when required visibility and input behaviour pass. Record release readiness and existing publication/pin steps; do not equate documentation or prototype completion with delivery to the TV.

## Comments

**2026-10-26:** Local automated/emulator validation is recorded in ../acceptance-results.md. Physical TCL/iPhone, protected playback and target memory checks have not run.
