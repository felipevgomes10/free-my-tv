# Adapt Weather Animations to native GPU rendering
Type: task
Status: resolved

## Request and decisions

The owner selected g3sthousen/weather-animations, preserving the ten scenes, recorded sound/mute,
text contrast, polling, native Screensaver and fixed daily temperature columns. After the WebView
prototype measured 283 MiB on the physical TV, the owner explicitly chose: adapt the effects to
the native renderer and retain the memory limit. The owner authorized installation/validation on
the physical TV. The original installed APK is backed up at build/weather-tv-validation/previous-home.apk.

## Current implementation

WeatherSceneRenderer adapts the MIT 1.1.1 algorithms/palettes at upstream revision
01a0ea7417593802e6a48ecd09aedef8f06539bf to Kotlin/GLES2. Sky, sun/moon, three cloud layers,
fog, stars, distinct drizzle/rain, snow and branched lightning keep the atmospheric art direction.
Cloud silhouettes are generated/blurred once per scene, uploaded as half-size, single-channel GLES2 alpha masks, and
the CPU bitmaps recycled. Frames use GPU sprites/points/lines; only current/incoming scenes
retain textures. There is no WebView, JavaScript or Chromium renderer in the weather path.

The 640×360 maximum native surface stays below full-resolution text, at at most 30 fps. A 1.2s
crossfade finishes before the latest queued forecast, without rebuilding context or restarting the
visible timeline. Existing native ambience, mute, focus policy and Screensaver lifecycle remain.
Full MIT notice ships at assets/weather/LICENSE.txt; original TS core remains as source provenance.

## Native validation

- Debug APK compiles; all 227 JVM tests pass, including three transition/queued-forecast regressions.
- The same google_tv_31 headless emulator renders native rain at 29.3–30.0 fps after warmup,
  with one context across rain → snow, one audio decoder, one full audio repeat, the two expected
  family output starts, zero underruns/GLES errors, and the actual output bounded at 640×360.
- Cloud softening uses a bounded separable alpha blur only at scene creation, with direct byte
  upload to GLES2 GL_ALPHA textures (one byte per pixel). It avoids Skia extractAlpha/mask filters,
  which stalled a discarded trial on SwiftShader, and the unused RGB channels of an RGBA upload.
- All ten scenes plus the unknown-code fallback were rendered and visually reviewed on the existing
  emulator. Optimized release compilation and release lint-vital pass; APK is 9,832,141 bytes.
- Physical TCL release: 29.18 presented fps, 33.33 ms median interval, total app PSS 134,656 KiB
  (131.50 MiB), with recorded ambience enabled, no WebView renderer. Native Screensaver: 133.25 MiB, covered Home hidden,
  first key dismisses it and Home is visible again. Emulator DreamService check also passes,
  restoring exact original preferences and secure screensaver settings.
- Whole-screen Weather alpha/scale transitions and page Crossfade were removed: they retained
  large full-HD render targets. Family crossfades remain on the small GPU surface. The covered
  Home decor is hidden after Compose pauses its renderer/audio and restored when the dream ends.
- A final repeated Weather → Dream check caught 161.66 MiB with the covered view waiting for a
  recomposition to pause EGL/audio. The native view now receives lifecycle pause synchronously
  and also gates playback by window visibility. The final physical regression passes at 133.25 MiB with recorded ambience enabled;
  prior failing evidence is retained at build/weather-tv-native-validation/dream-before-direct-lifecycle/.
- All ten optimized fixtures present 29.19–29.53 fps at 126.72–134.18 MiB PSS on the physical GPU,
  with zero GLES errors. Home was primed, then its extra diagnostic Activity cleared: normal
  Weather replaces its screen in one Activity. Keeping two diagnostic Activities alive had
  reached 151.32 MiB. The initial sample reports a second root pending destruction; the rest
  confirm one. Detailed evidence: build/weather-tv-native-validation/single-window-scenes/.
- Final single-channel textures pass the full 60-second rain repeat and rain → snow check:
  one context, one decoder, two expected family output starts, zero underruns/GLES errors,
  bounded 640×360 output, at least 26.18 fps after warmup on SwiftShader. Evidence:
  build/weather-native-validation/final-continuity/. The optimized release was restored on the TV.

## Prior WebView prototype (superseded)

The original library core ran offline in a bounded local WebView, with full license notice and
no network/bridge. It passed ten-scene rendering, mute persistence, native DreamService/key
dismissal, and audio looping with one decoder and no underruns. All emulator settings were restored.
The exported fixture was excluded from release. Its HTML/JS bundle and adapter were removed.

The emulator used ANGLE/Vulkan SwiftShader and sustained 15–17 fps; the physical TCL presented
29.53 fps (33.33ms median). Physical PSS: app 216,346 KiB + renderer 73,782 KiB = 283.33 MiB.
This exceeds ADR 0004; it is not the accepted final implementation. Detailed ignored evidence:
build/weather-library-validation/ and build/weather-tv-validation/.

## Comments

The original/published TV app was 0.4.10/code21. The validation APK retains that version; the initial validation was not published. The owner subsequently authorized commit and GitHub publication, targeting v0.4.11. Existing unrelated working-tree edits were preserved.

## Answer

Resolved with the native GLES2 adaptation, preserving the ten scenes, sounds/mute and Screensaver.
Final release installed on the physical TV: 29.18 presented fps, 131.50 MiB PSS with sound enabled;
Weather → native Dream uses 133.25 MiB, hides the covered Home and returns on the first key.
The native lifecycle observer pauses EGL/audio synchronously before the stopped Compose window
can suspend, with window visibility as an additional guard. All 227 JVM tests, debug/release builds
and release lint-vital pass. Final emulator continuity check passes with one context, one decoder,
a complete repeat, no underruns/GLES errors and bounded output. The debug fixture now uses a
broadcast to model live forecasts: onNewIntent causes a legitimate Android pause/resume and
therefore cannot assert uninterrupted EGL/audio. Release excludes the fixture/receiver.

The physical TV's original sound-enabled choice was restored and verified through the visible
Silenciar ambiente label; no TV screensaver settings changed. Emulator screensaver preferences
and secure settings were restored after Dream checks. The initial validation was not published. The owner subsequently authorized committing and publishing the changes, targeting v0.4.11.
