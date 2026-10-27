# 02: Implement the App Switcher overlay presentation

**Status:** ready-for-agent

**Blocked by:** [Home 01: Live overlay prototype](01-prove-live-overlay-on-tv.md).

**What to build:** The production overlay host and presentation, using the mechanism proved by ticket 01 and the existing `Multitasking` model. The [spec](../spec.md) defines its appearance, input ownership, app-close rules and lifecycle. Keep the existing full-screen switcher entry points working.

- [ ] An explicit platform operation returns shown/unavailable/busy/failure, with one active overlay and preserved focus on repeated opens.
- [ ] Shared switcher content retains Open Apps order, banners, count/limit, previous-app focus and empty state; its overlay presentation leaves the live app visible and uses no blur/capture.
- [ ] Back dismisses without launching the underlying app; selecting that app only dismisses; selecting another switches correctly and removes the window.
- [ ] Overlay navigation is consumed once, without leaking keys below; Home and app-changing Shortcut Buttons clean up before normal navigation. Volume/playback controls retain their meaning.
- [ ] The underlying app shows `Em uso` and cannot be closed from the overlay; existing protected/media rules and repeat protection remain.
- [ ] Closing an eligible background app through Settings restores the intended surface and overlay, without flashing Settings or bringing Home forward. Adapt the ADR 0010 return coordination as needed and document it.
- [ ] Stale/removed cards and automatic limit changes leave valid focus. Service loss, access revocation, sleep and competing navigation remove UI and release resources; disconnect alone leaves Remote dismissal working.
- [ ] State/action tests cover repeated opens, dismissal, switching, close protections, failures and cleanup; existing multitasking/shortcut tests pass. Check real window/input lifecycle on emulator and target TV.

## Comments
