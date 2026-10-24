# 01: Remove the pause at weather loop boundaries

**Status:** resolved

## Problem

The owner sees the picture stall and hears the ambient sound cut whenever the seven-second
weather video repeats in v0.4.2. Checking that playback advances and `looping=true` did not test
continuity at the boundary.

## Reproduction

The debug `WeatherLoopProbe` measures delivered frames on the real `TextureView`, tracking the
playback position wrapping to zero. Run `python3 scripts/check-weather-loop.py --serial emulator-5554`
after installing a debug APK. The test refuses physical TVs and does not create an emulator.

Two baseline runs using `MediaPlayer` failed:
- Boundary gaps: 115.8, 128.7 ms.
- Boundary gaps: 137.6, 99.6 ms.

The video is 24 fps (41.7 ms per frame). The regression check rejects a boundary hold above
125 ms and requires at least two repetitions.

## Hypotheses and comparison

1. `MediaPlayer` restarting the stream at end of file: compare Media3's prebuffered repeat with
   the same MP4 files, retaining decoders and audio output.
2. AAC encoder delay/padding: the files have a 1024-sample priming packet and an MP4 edit list;
   use gapless extraction/trimming rather than replaying that priming at every repeat.
3. The edit itself: keep the files unchanged during the comparison to isolate playback.

The first ExoPlayer comparison measured 51.0 and 49.4 ms, initialized each decoder once, and
reported zero audio underruns. Buffering is limited to 4 MiB for ADR 0004. Release builds do
not perform per-frame position/clock queries or diagnostics. Mute/focus/lifecycle behavior is
kept in `WeatherBackdrop`.

Extended testing caught a failure in Android 12's asynchronous `c2.android.aac.decoder` adapter
after repeated loops. Synchronous codec queueing keeps native decoding and passed the final
35-second rain and thunderstorm checks with the original files unchanged:
- Rain: 65.2, 33.6, 49.8 ms across three boundaries.
- Thunderstorm: 64.8, 78.0, 76.2 ms across three boundaries.
- Each run initialized audio/video decoders once, started audio output once, and reported zero
  audio underruns. No decoder or audio-output restart at a loop boundary.

## Validation

- [x] Repeat the real frame/audio pipeline check after the final build, without a concurrent build.
- [x] Confirm mute, pause, resume and leaving Weather still release resources.
- [x] All 203 JVM tests pass.
- [x] Final optimized release build, including release lint-vital.

The existing emulator uses `-no-audio`: native audio continuity is checked via decoder lifetime
and underrun events, but final perceived continuity needs listening on the owner's TV.
