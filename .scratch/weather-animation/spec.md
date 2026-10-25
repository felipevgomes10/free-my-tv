# Weather animation
Status: resolved

## Current request

The owner selected the MIT Weather Animations library, then chose a native GPU adaptation
when the original WebView prototype exceeded ADR 0004's 150 MB memory budget. Installation
and validation on the physical TV are authorized. See [implementation issue](issues/02-use-weather-animations.md).
The earlier plastic/pastel style was rejected; [issue 01](issues/01-restore-atmospheric-style.md)
records its removal.

## Required behavior

- Adapt the selected library's atmospheric art direction to native GLES 2.0 without WebView.
- Retain all ten scenes and 29 WMO mappings, with clear/partly-cloudy day/night variants.
- Keep contrast, fixed daily Mín./Máx. columns, recorded ambience and persistent mute.
- Render continuously without a short video-loop boundary; blend weather-family changes.
- Share the renderer with the native Screensaver, releasing the covered Home graphics/audio.
- Keep Home under the target TV's 150 MB memory budget and validate actual GPU presentation.
- Keep forecast polling at its existing 30-minute interval; this is a visual/audio adaptation.

## Validation

Debug/release compilation, release lint-vital and 227 JVM tests pass. The optimized release
is installed on the physical TCL: Weather presents 29.18 fps at 131.50 MiB app PSS with recorded ambience enabled;
the native Screensaver uses 133.25 MiB and returns to Home on the first remote key.
No Chromium renderer is running for weather. Current/incoming cloud masks and frame output
are bounded, with no per-frame bitmap/blur work. Final scene/continuity evidence and
provenance are recorded in the implementation issue and [weather docs](../../docs/weather-media.md).

## Scope

Reuse the authorized google_tv_31 headless AVD; never create another emulator. The owner subsequently authorized committing and publishing these changes; the release target is v0.4.11. The original physical-TV APK remains backed up
locally, and unrelated prior working-tree edits are preserved. Earlier plastic/noise experiments
are historical; current sounds are the recordings documented in ../weather-sounds/spec.md.
