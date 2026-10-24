# Weather footage and ambient sound

Weather's detail screen plays local footage behind the existing current conditions, 12 hours and
7 days. The compact Weather Widget stays silent. There is no streaming, API key or new service.

## All API conditions

Source: [Open-Meteo's WMO interpretation table](https://open-meteo.com/en/docs), checked 2026-10-24.
The current API documents **29 codes**, including heavy thunderstorm (97). `WeatherReport` keeps
the original code, independently of the eight `Condition` families used for icons and labels.

| WMO codes | Meaning | Scene / noise bed |
| --- | --- | --- |
| 0 | Clear sky | Sunlit trees / gentle breeze; starry sky / softer air at night |
| 1 | Mainly clear | Same clear-sky scenes, respecting `is_day` |
| 2 | Partly cloudy | Blue sky with clouds / breeze; clouds over the moon / softer wind at night |
| 3 | Overcast | Thick grey clouds / stronger wind |
| 45, 48 | Fog; depositing rime fog | Mist over a forest / soft, low wind |
| 51, 53, 55 | Light, moderate, dense drizzle | Ripples on a lake / fine, bright rain noise |
| 56, 57 | Light, dense freezing drizzle | Same drizzle scene and noise |
| 61, 63, 65 | Slight, moderate, heavy rain | Raindrops on a window / fuller rain noise |
| 66, 67 | Light, heavy freezing rain | Same rain scene and noise |
| 71, 73, 75 | Slight, moderate, heavy snowfall | Snow falling in pines / soft wind |
| 77 | Snow grains | Same snow scene and noise |
| 80, 81, 82 | Slight, moderate, violent rain showers | Rain scene and noise |
| 85, 86 | Slight, heavy snow showers | Snow scene and noise |
| 95, 97 | Thunderstorm; heavy thunderstorm | Storm clouds and distant lightning / low, swelling noise |
| 96, 99 | Thunderstorm with slight, heavy hail | Same thunderstorm scene and noise |
| Other / unavailable | Unknown conditions / no forecast | Static sky, silent |

Intensity and freezing/hail variants deliberately share a scene within their weather family;
there is no claim that generic rain footage shows freezing rain, or storm footage shows hail.
Day/night comes from the API's `is_day`, not the computer's clock. Precipitation and fog use
their own scene both day and night. Code 1 retains the existing partly-cloudy compact icon,
while the detail's footage uses its more precise “mainly clear” interpretation.

## Sources and licensing

All ten source pages explicitly offered the **Mixkit Stock Video Free License** when selected.
Restricted-license alternatives were excluded. These are modified clips incorporated into the
Home, not a stock-footage library. See [license](https://mixkit.co/license/#videoFree) and
[terms](https://mixkit.co/terms/). Original authors retain ownership of their footage.

| Asset | Source |
| --- | --- |
| `clear_day.mp4` | [Sun flares seen under two trees, 51110](https://mixkit.co/free-stock-video/sun-flares-seen-under-two-trees-51110/) |
| `clear_night.mp4` | [Many stars in the night sky, 4124](https://mixkit.co/free-stock-video/many-stars-in-the-night-sky-4124/) |
| `partly_cloudy_day.mp4` | [White clouds moving across a clear blue sky, 51102](https://mixkit.co/free-stock-video/white-clouds-moving-across-a-clear-blue-sky-51102/) |
| `partly_cloudy_night.mp4` | [Clouds passing over the moon, 3147](https://mixkit.co/free-stock-video/clouds-passing-over-the-moon-3147/) |
| `cloudy.mp4` | [Thick clouds speeding in the sky with the wind, 21585](https://mixkit.co/free-stock-video/thick-clouds-speeding-in-the-sky-with-the-wind-21585/) |
| `fog.mp4` | [Aerial tour above a cloud forest, 28341](https://mixkit.co/free-stock-video/aerial-tour-above-a-cloud-forest-28341/) |
| `drizzle.mp4` | [Rain falling on the water of a lake seen up close, 18312](https://mixkit.co/free-stock-video/rain-falling-on-the-water-of-a-lake-seen-up-18312/) |
| `rain.mp4` | [Window on a rainy day, 2846](https://mixkit.co/free-stock-video/window-on-a-rainy-day-2846/) |
| `snow.mp4` | [Snow falling in a pine forest, 3352](https://mixkit.co/free-stock-video/snow-falling-in-a-pine-forest-3352/) |
| `thunderstorm.mp4` | [Thunderstorm at night, 4422](https://mixkit.co/free-stock-video/thunderstorm-at-night-4422/) |

Every noise bed is generated for this project from seeded white noise with scene-specific
high/low-pass filters and a gentle swell; there are no recordings, music, voices or sharp
thunderclaps. These are atmospheric approximations, not field recordings. The source clips'
original audio is discarded. The catalog and filter parameters live in `config/weather-media.json`.

## Playback and contrast

- Ten 7-second H.264 Baseline 3.1, 1280×720, 24 fps loops with mono AAC sound, about **11.6 MiB total**.
- The preparation script cross-fades the last second into the first for both picture and noise.
- One Media3 ExoPlayer with native decoders and a non-focusable `TextureView`, filling the screen
  with center crop. Repeat mode prebuffers the next repetition without reopening the decoders or
  the audio output. AAC encoder delay/padding is trimmed for gapless sound. Compressed buffering
  is capped at 4 MiB, rather than the library's default video buffer (ADR 0004).
  Codec queueing is synchronous: the Android 12 emulator's asynchronous AAC adapter failed after
  repeated loops in extended testing. This still uses native decoding, with one video decoder.
  No WebView, live blur, media session, image cache or dependency on a remote media host.
- A uniform 62% black overlay limits even white video frames to 38% white. Weather's secondary
  text is 85% white, providing over 5:1 contrast against that worst case; top/bottom scrims add shade.
- The player is created only with a known forecast on the visible, settled Weather screen.
  Navigation, activity pause/sleep and surface destruction release decoder, sound, audio focus
  and `Surface`. Returning recreates it. A decoder failure leaves the static sky readable.
- “Silenciar ambiente” / “Ativar som ambiente” is a D-pad button below the current conditions.
  Muting preserves the video, releases audio focus and persists in the existing widget preferences.
  Audio stays quiet and yields to Assistant or other audio focus owners.

## Rebuilding and checking

Requires Python 3 and ffmpeg; it does not affect a TV:

```bash
python3 scripts/prepare-weather-media.py --sources-dir /tmp/weather-originals --download
```

Without `--download`, supply the ten source MP4 files named as in the catalog. Originals are
kept outside the repository. The generated, bundled MP4s are already present in the app's assets.

Debug builds expose a preview of the real Weather screen, with a fixture forecast, so all
conditions can be checked without changing the saved city or waiting for weather:

```bash
adb -s emulator-5554 shell am start -S \
  -n com.freemytv.home/.debug.WeatherPreviewActivity \
  --ei weather_code 61 --ez is_day true
```

Try 0 (day and night), 2 (day and night), 3, 45, 51, 61, 71 and 97. Preview is absent from the
release APK. JVM tests cover all 29 codes, day/night, unknown codes and mute persistence.

### Continuous-loop regression check

After installing a debug APK on an existing emulator:

```bash
python3 scripts/check-weather-loop.py --serial emulator-5554 --seconds 35
python3 scripts/check-weather-loop.py --serial emulator-5554 --weather-code 97 --night --seconds 35
```

This drives the actual Weather preview, measures delivered video frames across loop boundaries,
and fails on a hold above 125 ms, decoder reinitialization, audio output restart or an audio underrun. It requires at
least two repetitions. It refuses a physical TV and never starts an emulator. The probe exists
only in debug builds; release builds perform no per-frame position/clock queries or logging.

On the existing headless emulator (2026-10-24), the original `MediaPlayer` failed two runs with
99–138 ms boundary holds. With the same files and synchronous codec queueing, the rain regression
run measured 33–65 ms across three loops, one audio decoder, one video decoder, one audio output
start and no audio underruns. The thunderstorm run measured 65–78 ms with the same decoder/output
counts and no underruns. Both debug and optimized release builds pass, as do all 203 JVM tests.
The emulator has no audible output, so these
are frame timing and audio pipeline checks, not a listening test on the TCL.
