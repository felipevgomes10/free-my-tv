# Overlay window prototype — 2026-10-26

## Mechanism and evidence

The existing `KeyFilterService` hosts a focusable `TYPE_ACCESSIBILITY_OVERLAY` window. A `ComposeView` reuses `AppSwitcherScreen`, with a separate lifecycle/saved-state owner and a bottom translucent presentation. The top of the underlying app is uncovered. There is no screenshot, Activity launch or audio-focus request to open it. The [Android window reference](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_ACCESSIBILITY_OVERLAY) documents this window type.

On `emulator-5554` (Google TV arm64, Android 12), a ready, paired WebSocket session sent the shared `open-app-switcher` example and received `done` with ID 42. The pairing screen remained visible beneath the switcher. This verified the authenticated opening path before the emulator's existing ADB Linking needed renewing.

Further window/input checks used the permission-guarded, debug-only `AppSwitcherProbe` through the workstation's existing ADB connection. This isolates window validation from the Home's ADB Linking; it does not prove the complete phone-to-TV key path. The receiver is absent from the release manifest.

- Over the Play Store, the app remained the resumed Activity while the switcher window took input focus. [Screenshot](evidence/over-play-store.png).
- Repeated opening produced one window.
- Up did not close the underlying Play Store.
- Back removed the overlay; the same Activity record and app process remained.
- OK on the underlying app removed the overlay without restarting it.
- Home removed the overlay and opened the regular Home.
- Closing the background Play Store from above `FakePlayerActivity` ended its process, restored the overlay and preserved the fixture's original Activity record. Back afterwards still returned to that same Activity.

The first close experiment exposed a second Back reaching the fixture during Settings' closing animation. `ForceStopper` now waits for the particular Settings window to lose focus before another Back. The repeat passed. Temporary Settings windows use a separate document/task and are identified separately from windows that already existed.

## Reproduce the window checks

Install the debug candidate on an emulator with the Home's existing Setup/accessibility access, open another app, then run:

```sh
adb -s emulator-5554 shell am broadcast -n com.freemytv.home/.debug.AppSwitcherProbe
adb -s emulator-5554 shell input keyevent 4
```

The ordered broadcast answers `Shown`, `Unavailable` or `Busy`. It requires `WRITE_SECURE_SETTINGS` on the sender. This is test scaffolding, never a production Controller API.

## Remaining evidence

The physical TCL and an actual iPhone were not changed or tested. YouTube and subscription video apps were not installed in this emulator. Their playback, protected live surfaces, service revocation, sleep/wake and target memory budget remain acceptance checks in ticket 04. One-session ADB approval was authorized for the emulator, with Always allow left off; the Linking attempt did not establish the stable end-to-end key session needed to claim that path passed. No persistent debugging approval was granted.
