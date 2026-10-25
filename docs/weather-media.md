# Weather animation and ambient sound

Weather and the native Screensaver render their atmosphere locally in real time. There are no
video files, runtime media downloads or external 3D model files. The scene library is bundled in the APK. The compact
Weather Widget stays still and silent. Forecast polling remains every 30 minutes while collected;
this visual change does not turn Open-Meteo into a real-time feed.

## All API conditions

The mapping retains all 29 codes in [Open-Meteo's WMO table](https://open-meteo.com/en/docs).
Unknown conditions and unavailable forecasts use the static, silent sky.

| WMO codes | Scene | Ambient sound |
| --- | --- | --- |
| 0, 1 | Clear day: sun; clear night: moon and stars | Recorded birds and a light breeze; crickets at night |
| 2 | Shaded drifting clouds, with sun or moon according to `is_day` | More distant birds and wind; crickets with light gusts at night |
| 3 | Dense, layered overcast clouds | Wind gusts and rustling leaves |
| 45, 48 | Layered drifting fog | Very quiet wind and distant birds |
| 51, 53, 55, 56, 57 | Cloudy sky and fine rain particles | Individual water drops, with light rain underneath |
| 61, 63, 65, 66, 67, 80, 81, 82 | Cloudy sky and fuller rain particles | Recorded steady rain and water splashes |
| 71, 73, 75, 77, 85, 86 | Snowflakes drifting at different depths | Quiet, muffled wind without summer birds/insects |
| 95, 96, 97, 99 | Storm clouds, rain and restrained sheet lightning | Recorded rain and distant thunder, with controlled peaks |

Intensity, freezing and hail variants still share a family scene. Precipitation/overcast/fog keep
their own palette regardless of day/night; clear and partly cloudy follow the API's `is_day`.
These are stylized atmospheric scenes, not a physical simulation of every WMO variation.

## Rendering and contrast

`WeatherBackdrop` owns a native OpenGL ES 2.0 surface. `WeatherSceneRenderer` adapts the MIT
[Weather Animations](https://github.com/g3sthousen/weather-animations) 1.1.1 core, pinned to
`01a0ea7417593802e6a48ecd09aedef8f06539bf`. Its source remains under `third_party/weather-animations/`
for provenance; the complete notice ships in `assets/weather/LICENSE.txt`, with About attribution.

The port retains the atmospheric art direction: the same sky palettes and three cloud layers/lobes,
soft cloud silhouettes with warm tops/darker bases, proportioned sun/moon, twinkling stars, layered
fog, distinct fine drizzle/rain, depth-varying snow and branched lightning. It is a native adaptation
of 2D effects rather than real 3D or a claim of pixel-identical Canvas rendering. Unused weather
conditions, fidelity presets and moon phases/celestial events are not ported.

Cloud silhouettes are generated only when creating a scene, softened with a bounded separable
alpha blur on a half-size software bitmap,
uploaded directly as GLES2 single-channel alpha textures and immediately recycled on the CPU. Frames use shaders and
cached sprites/points/lines; there is no per-frame bitmap allocation or blur. Only the current and
incoming scene retain GPU textures. Old textures are deleted when the crossfade completes.

The native surface is capped at 640×360 with its aspect ratio preserved, behind full-resolution
Compose text. Choreographer requests at most 30 fps. A 1.2-second crossfade keeps the GPU context
and continuous timeline. Rapid forecast updates retain the latest queued scene until the current
crossfade finishes. There is no short movie boundary. Audio remains a separate native player.

The existing 62% black overlay limits even a white rendered pixel to 38% white; secondary text
is 85% white, giving over 5:1 worst-case contrast before the extra top/bottom scrims. A graphics
initialization failure leaves a static dark sky beneath the data.

The weather path uses no WebView, browser process, JavaScript, remote texture/video or model file.
Activity pause, sleep, navigation and disposal stop callbacks, pause the GL thread, discard its
context/buffers and release audio/focus. A lifecycle observer applies ON_PAUSE directly to the native
view, without waiting for the stopped window to recompose; window visibility is a second guard.
The native Screensaver shares this component and suspends
the Weather screen underneath it. After the pause is applied, the covered Home decor is hidden
so its full-HD buffers are released; a lifecycle-scope observer restores it when the dream ends.
Weather entry/exit and forecast data use direct UI layers instead of full-screen alpha/scale
snapshots; weather-family crossfades remain inside the small native surface. The surface owns no D-pad/touch focus or media session.

The WebView prototype rendered smoothly on the physical TV (~29.5 presented fps) but consumed
~283 MiB PSS including its separate renderer, exceeding ADR 0004. The owner selected this native
adaptation to preserve the memory budget. Native measurements below supersede prototype results.

## Sound and rebuilding

The former filtered-white-noise beds are replaced by ten mixes of **real nature recordings**:
clear day has birds, clear night has crickets, partly cloudy mixes those with wind, overcast adds
rustling leaves, drizzle emphasizes individual drops, rain has rain/splashes, and storms include
actual thunder. Fog and snow use quieter outdoor atmospheres; fog/snow themselves do not have a
universal audible sound. These are illustrative environments, not recordings of the owner's city.

Loops last 32–72 seconds. Each layer uses a continuous excerpt (no short clip repeated internally).
A three-second equal-power tail/head crossfade makes the loop; filtering, per-layer leveling and
peak compression happen **before** that join. A measured, fixed final gain preserves the seam and
keeps the strongest peaks below -4 dBTP before AAC encoding. Five-millisecond edge smoothing
handles the AAC startup window without inserting a silence interval or changing the loop duration. Thunder stays behind the rain instead
of producing abrupt loud claps. The rain excerpts omit the original recording's later train horn.

Output is local stereo AAC-LC at 44.1 kHz / 96 kbps, played by the existing Media3 player. Repeat
keeps its decoder/audio output open and honors AAC delay/padding. The compressed buffer stays
capped at 256 KiB; the TV streams from its APK instead of decoding entire recordings into RAM.
A family change selects its corresponding sound; only the graphics interpolate between families.

Sources, immutable download URLs, SHA-256 checksums, exact excerpts, mix levels and filters are in
`config/weather-audio.json`. Credits and license links ship as `assets/weather/CREDITS.txt`, in
file metadata and in this document. The two CC BY recordings are also credited in Settings → Sobre.
No Blanket/Noisekun application code is incorporated; the recordings have their own licenses.

“Silenciar ambiente” / “Ativar som ambiente” persists the owner's choice. Muting leaves graphics
running, sets the player volume to zero and abandons audio focus. A muted initial scene needs no
audio player. Ambient sound yields to Assistant and other audio-focus owners.

Rebuild with Python 3 and ffmpeg. Downloads are opt-in and originals stay outside the repository:

```bash
python3 scripts/prepare-weather-audio.py --sources-dir /tmp/weather-recordings --download
python3 scripts/check-weather-audio.py
```

Without `--download`, supply the catalog's exact source files in that directory. The preparation
script checks every SHA-256 digest before mixing, refuses excerpts that overrun a source recording,
and keeps the work offline when the files are present. The audio check decodes all outputs and
checks their duration, format, level, embedded attribution, uniqueness and loop seam.

### Recording credits

| Recording | Author | License | Distributed edit |
| --- | --- | --- | --- |
| [WoodThrushinMorningShawneeForestMay272012.wav](https://freesound.org/people/kvgarlic/sounds/156826/) | kvgarlic / Kevin Boucher | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Porrumentzio / Blanket |
| [Crickets Chirping At Night](https://soundbible.com/2083-Crickets-Chirping-At-Night.html) | Lisa Redfern | Public Domain | Blanket |
| [Wind blowing in a field in Texas, USA](https://freesound.org/people/felix.blume/sounds/217506/) | Félix Blume | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Mateus Felipe / Noisekun |
| [Autumn wind and dry leaves.wav](https://freesound.org/people/Stek59/sounds/457318/) | Stek59 | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Mateus Felipe / Noisekun |
| [rain ambience](https://freesound.org/people/alex36917/sounds/524605/) | alex36917 | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Porrumentzio / Blanket |
| [Pitter Patter](https://freesound.org/people/iainmccurdy/sounds/786919/) | iainmccurdy | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) | Original public high-quality preview |
| [Thunder-H2-without-windstoper.mp3](https://freesound.org/people/Borys_Kozielski/sounds/316831/) | Borysław Kozielski | [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) | Noisekun |

Free My TV adapts these recordings through excerpts, filtering, mixing, level/peak control, looping
and AAC encoding. All original authors retain their applicable rights; no endorsement is implied.
The upstream [Blanket credits](https://github.com/rafaelmardojai/blanket/blob/775f2a767230a9681850d1b1e085be58656f1382/SOUNDS_LICENSING.md)
and [Noisekun credits](https://github.com/mateusfg7/Noisekun/blob/d32024c67aba7c0577e1609944af00b9df1b7775/README.md#sounds)
record the original edits. SHA-pinned download URLs are in the catalog.

## Checking on the existing emulator

Debug builds and the separate optimized `weatherBenchmark` variant expose the real Weather screen
with fixture conditions. Published release builds exclude it. For performance measurements, build
`./gradlew :app:assembleWeatherBenchmark` and install `app/build/outputs/apk/weatherBenchmark/app-weatherBenchmark.apk`
on the existing emulator. It uses the same package/signing key and replaces the emulator APK only.
Do not publish this diagnostic APK. No Gradle/R8 compilation should run during cadence measurements.

Open the fixture:

```bash
adb -s emulator-5554 shell am start -W -S \
  -n com.freemytv.home/.debug.WeatherPreviewActivity \
  --ei weather_code 61 --ez is_day true
```

Try 0 and 2 by day/night, then 3, 45, 51, 61, 71 and 97. To model a live forecast without any
Activity pause, use the debug-only receiver after opening the fixture:

```bash
adb -s emulator-5554 shell am broadcast \
  -a com.freemytv.home.debug.WEATHER_FORECAST -p com.freemytv.home \
  --ei weather_code 71 --ez is_day true
```

`--activity-single-top` remains useful for manual navigation, but Android pauses/resumes a
resumed Activity around `onNewIntent`; that must legitimately release EGL/audio. It therefore
cannot test a live forecast's continuous context. Optional `--ez sound_muted false` overrides the saved
mute value for this debug session without changing preferences.

After installing a debug or optimized weatherBenchmark APK, with no Gradle/R8 build competing for CPU:

```bash
python3 scripts/check-weather-animation.py --serial emulator-5554
```

This refuses a physical TV and never starts an emulator. It checks GLES errors,
native submission cadence, a real 60-second audio repetition without decoder/output restart, and a live
rain → snow change without rebuilding the GPU context; screenshots capture the composited screen.
Debug timing measures CPU submission, not GPU completion/display presentation. Diagnostics
are disabled in the optimized release. The headless emulator cannot validate audible sound level.

The sections below record earlier implementations and validation; the current renderer is the
native Weather Animations adaptation described above.

## Historical validation: style restored — 2026-10-24

The atmospheric renderer predating the plastic/pastel experiment is restored. The recorded nature
ambience, mute behavior, native Screensaver and fixed Mín./Máx. daily columns are preserved.
The removed geometry-only unit test is no longer applicable; the remaining suite has 228 tests.

## Historical validation: recorded-audio update — 2026-10-24

All ten recorded mixes decode as stereo AAC-LC at 44.1 kHz, last 32–72 seconds and contain their
source credits in metadata. The bundled audio totals 6,389,782 bytes; the optimized APK is 9.38 MiB.
The decoded sample peaks range from -16.5 to -3.4 dBFS before the app's 0.45 playback volume.
Every decoded loop seam passes the local sample-difference check; the low-frequency snow seam
was corrected from 21.8× to 2.37× by the five-millisecond AAC edge treatment.

The source recordings are used under CC0, public-domain dedication or CC BY 4.0 as individually
credited above. Debug/release builds and all 228 remaining JVM tests pass. This headless emulator has no
audible output: decoder/loop continuity and the decoded files are checked technically; final sound
level on the TCL remains a listening check. Short previews live in ignored
`build/weather-audio-validation/` for listening on the computer.

The final native-player check runs through a complete 60-second rain repetition: one audio decoder,
no output restart at the repeat, zero underruns, and the expected new sound when changing to snow.
The same graphics surface survives throughout, with approximately 30 fps after warm-up.

The restored atmospheric renderer passes debug/release compilation, release lint-vital and all
228 remaining JVM tests. All ten restored scenes were checked on emulator-5554, with the
recorded audio and fixed Mín./Máx. layout preserved byte-for-byte.

## Native adapter regression

The JVM suite exercises the WMO mapping and native transition state: duplicate forecasts do not
restart the fade; a second/third forecast queues the latest target without jumping the visible
scene. Android validation uses `scripts/check-weather-animation.py` for native submission cadence,
GLES errors, one context across rain → snow, and a full audio repeat without a decoder restart.
Actual presentation and total memory are measured separately on the physical TV.
For a scene-only PSS comparison, first prime Home, then launch the optimized preview with
`-f 0x10008000` (new/clear task), followed by `--activity-single-top` for subsequent conditions.
This removes the extra diagnostic Home Activity/window beneath the preview; normal Weather
replaces its screen within one Activity. Confirm `ViewRootImpl: 1` once destruction settles.
The ordinary preview without this flag deliberately keeps its back stack and can exceed
the production memory footprint. Reinstall the release APK after hardware fixture checks.

### Physical TV validation — 2026-10-24

The optimized release is installed on the owner-authorized TCL, Android 12 / armeabi-v7a.
With recorded ambience enabled, Weather presents 29.18 fps (33.33 ms median frame interval) at 131.50 MiB total
app PSS, with no WebView or Chromium renderer. The native Screensaver uses 133.25 MiB;
the covered Home window is hidden, the first remote key dismisses it, and Home becomes
visible again. Both samples meet ADR 0004's 150 MB budget. These are measured samples,
not a universal memory ceiling for every app/system state.

To avoid cached full-HD render targets, Weather entry/exit and forecast-data replacement
do not animate whole-screen alpha/scale. The small GPU surface still blends weather families.
Debug/release compilation, release lint-vital and all 227 JVM tests pass. The existing
google_tv_31 headless emulator passes the native DreamService/key-dismissal check, with
its original screensaver settings and preferences restored afterward.

All ten optimized scene fixtures were also presented on the physical GPU, after priming Home
and clearing its extra diagnostic window: 29.19–29.53 fps and 126.72–134.18 MiB app PSS,
with no GLES errors. The first sample still reports a second root pending destruction; subsequent
samples confirm one root. Keeping both diagnostic Activities alive had reached 151.32 MiB;
that back-stack configuration differs from normal Weather. The final cloud masks use direct
single-channel uploads, avoiding unused RGB data and Skia mask-filter conversion. After the synchronous lifecycle/window pause fix, a complete
60-second rain repetition plus rain → snow passes again with one context, one decoder,
no underruns and no GLES errors on the existing emulator.

Ignored measurement evidence: `build/weather-tv-native-validation/result.json`,
`build/weather-tv-native-validation/dream/result.json` and
`build/weather-library-validation/dream/result.json`. Ten-scene GPU/PSS evidence is in
`build/weather-tv-native-validation/single-window-scenes/`; the final loop check is in
`build/weather-native-validation/final-continuity/`.
