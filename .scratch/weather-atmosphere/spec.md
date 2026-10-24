# Weather detail: footage and ambient noise

**Status:** resolved

## Request

Map every weather type the API may return. Show corresponding full-screen background video on
Weather's detail, with enough contrast for its data, and corresponding white-noise ambience
with a mute button. Find the video footage online. Reuse the owner's running headless emulator;
do not launch another emulator or apply this work to the physical TV.

## Implemented

- Open-Meteo's complete 29-code WMO table, including code 97 (heavy thunderstorm).
- Ten local scenes: clear and partly cloudy by day/night, overcast, fog, drizzle, rain, snow,
  thunderstorm. Freezing/intensity/hail variants share their weather family's scene.
- Exact current weather code preserved in `WeatherReport`; hourly/daily icons keep their existing
  `Condition` families. Unknown codes and unavailable forecasts use the static, silent sky.
- Licensed Mixkit footage, bundled as 7-second 720p/24fps H.264 Baseline loops with mono AAC.
  Filtered white-noise beds are generated per scene. Total assets: approximately 11.6 MiB.
- Center crop, constant dark scrim and extra top/bottom shading, with brighter secondary text.
- D-pad mute/unmute button under current conditions. Preference persists without changing city
  or widget visibility and without restarting forecast requests.
- One native player, only on the settled, resumed Weather detail. Releases decoder/surface/audio
  focus on navigation, pause/sleep or destruction. Muting releases audio focus while video continues.
- Catalog, source credits, full condition mapping and rebuilding instructions: `docs/weather-media.md`.

## Validation — 2026-10-24

- 203 JVM tests pass, including complete WMO coverage, day/night, unknown-code fallback and mute
  persistence. Debug and optimized release APKs build successfully; release lint-vital passes.
- Reused `emulator-5554` (`google_tv_31`, 1920×1080), already running headless. No emulator created.
- Every scene played in a real native decoder at 1280×720 in loop, with advancing frames.
- Visually reviewed all ten scenes with the real Weather layout. Captures and machine-readable
  scene checks are in ignored `build/weather-validation/`.
- D-pad mute sets native player volume to zero, offers “Ativar som ambiente”, saves the preference
  and releases audio focus. Unmute restores volume; leaving composition releases decoder/focus.
- Checked the normal Home → Weather flow using a forecast returned by the API.
- Opening Android Settings and putting the emulator to sleep release the decoder; returning and
  waking recreate it. Back to Home releases it. Checks await the emulator's activity transitions.

## Limits

The existing emulator runs with `-no-audio`: audio routing, levels and focus can be checked, but
the final sound level still needs listening on a real TV. This change has not been installed or
profiled on the owner's TCL. Its 150 MB performance target remains subject to that hardware check.
Prepared for publication as v0.4.2 at the owner's request.
