# 01: Prove the App Switcher can overlay a live TV app

**Status:** ready-for-human

**Blocked by:** None.

**What to build:** A minimal, disposable prototype of a focusable App Switcher overlay, starting with the existing accessibility service as host. Establish the window mechanism and input ownership before building production UI. Follow [ADR 0016](../../../docs/adr/0016-the-controller-opens-the-app-switcher-as-an-overlay.md) and the [spec](../spec.md); this ticket does not implement the Controller command.

- [ ] Opening never launches `MainActivity` or sends Home; the app remains visibly live beneath a translucent surface with an uncovered area.
- [ ] On the target TCL, test YouTube, a subscription app with protected playback, and an app menu; record live visibility, audio/video and focus effects. Inspect the physical display for protected content.
- [ ] Arrows/OK reach only the overlay; Back dismisses it without relaunching or backing out of the app underneath.
- [ ] Home, service teardown and repeated opening leave no stacked window or trapped key input.
- [ ] Write `../prototype-results.md` with device/build, mechanism, observations, limitations and production recommendation; distinguish emulator results from TV results.
- [ ] If visibility or input requirements fail, record the blocker and revise the mechanism/ADR before unblocking ticket 02. A screenshot or opaque Home route is not an acceptable fallback.

## Comments

**2026-10-26:** Emulator mechanism and window/input checks passed; the physical TCL playback checks remain. See ../prototype-results.md.
