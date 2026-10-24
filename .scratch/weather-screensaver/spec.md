# Weather as the TV's screensaver

**Status:** resolved

## Owner request

Settings gains “Descanso de tela”, with “Usar tela de clima como descanso de tela”. Only when
enabled, “Mostra tela de clima após” offers 2, 5 and 10 minutes. The owner confirmed this should
work across the TV, respecting playback. Setup disables the default screensaver; Restore brings
it back.

## Implementation

- `ScreensaverSettings` owns persisted enabled/delay choices, defaulting to off and 5 minutes.
- The settings panel uses the existing toggle and value-row controls; OK cycles 2/5/10.
- Android's native `DreamService` handles global inactivity and dismissal back to the exact app
  beneath it. It reuses the real Weather screen and existing loop/mute preference, without
  controls that would mislead the owner (the first key exits the dream).
- `ScreensaverSession` prevents showing over playing media and ends a running screensaver when
  playback begins. Native keep-screen-on policy remains authoritative.
- The dream fetches weather even when the compact widget is hidden. Its data collection and
  decoder end with the dream. Home's own widgets and weather decoder stop while it is covered,
  since a dream window can cover a still-resumed activity.
- Setup grants the `WRITE_SETTINGS` app-op, backs up six settings on the TV with matching journal
  inverses, disables automatic dreaming and selects the Home's dream. It never disables the
  Google dream package and refuses an old APK without `WeatherDreamService`.
- Enabling applies the selected screen-off delay, enables dreaming on inactivity and clears
  the developer “stay awake” override. Disabling restores original power timers and that override,
  while leaving the default screensaver disabled as requested.
- Restore disables the Home's preference, ends an active dream, and restores original component,
  enabled state, timers and stay-awake override. Without the journal it uses the backups on the TV.
  Originally unset or disabled settings stay that way. Repeated Setup preserves original backups.
- A new Preparação check reports missing screensaver preparation. See ADR 0011.

## Validation

- [x] D-pad UI: delay row absent while off, present while on; 2/5/10 choices persist.
- [x] 32 Python Setup/Restore tests, including idempotence, changes after Setup, originally missing
  settings, old-APK refusal and restoring with or without a journal.
- [x] Native dream activated over a paused app on the existing `emulator-5554`; any key returned
  to that app. Accelerated native idle testing used a temporary 10-second timeout.
- [x] Active playback prevented the weather dream staying active.
- [x] Emulator Restore recovered original component, enabled flag, timers and WRITE_SETTINGS app-op.
- [x] All 211 JVM tests pass, including playback guards and a hidden compact widget.
- [x] Final native test over Home's Weather: three player starts, two releases, one active player;
  a key returned to Weather. Restore recovered all six original settings, including stay-awake.
- [x] Optimized release APK and release lint-vital pass.

No changes are applied to the physical TV. Its next Home update must be installed before
rerunning Setup; the script refuses to replace the default screensaver before the APK supports it.
