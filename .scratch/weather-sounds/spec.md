# Recognizable Weather ambience
Status: resolved

The owner reports that the existing generated beds all sound like hiss. Replace them with
recognizable recorded weather/nature ambience, with distinct day/night, breeze, fog, drizzle,
rain, snow and storm identities. Keep local playback, a continuous loop, mute persistence,
existing sound focus/lifecycle and the new plastic 3D scenes.

Source recordings with explicit redistribution/remix permission and include provenance,
licenses, attribution and a reproducible preparation script. Avoid voices, music, traffic,
sudden loud thunder and conspicuous repeating short calls. Validate decoded loop seams and
levels, all ten outputs, app builds and playback on emulator-5554. Never alter the physical TV.

## Implemented

- Replaced all ten filtered-white-noise assets with mixes of openly licensed nature recordings.
- Clear day: birds and breeze. Clear night: crickets. Partly cloudy: more wind with birds/crickets.
- Overcast: gusts and leaves. Fog: quiet wind/distant birds. Snow: quiet muffled wind.
- Drizzle: individual water drops. Rain: steady rain and splashes. Storm: rain and distant thunder.
- Continuous 32–72-second excerpts, three-second tail/head crossfade, controlled peaks and fixed
  final loudness gain. A five-millisecond edge treatment prevents AAC startup-window clicks.
- Stereo AAC-LC, 44.1 kHz / 96 kbps; same player, mute persistence, audio focus and 256 KiB buffer.
- Source URLs pinned to commits, SHA-256 checksums, authors/licenses and mixing parameters in
  config/weather-audio.json. Reproducible opt-in downloads in scripts/prepare-weather-audio.py.
- Credits in app Settings → Sobre, bundled weather/CREDITS.txt, M4A metadata and docs/weather-media.md.
- Original source files remain in /tmp/freemytv-weather-audio-sources, outside the app and repo.

## Validation

- scripts/check-weather-audio.py decodes all ten assets, checks metadata, unique hashes, duration,
  sample format, levels and sample continuity at every seam. All pass. Total audio: 6,389,782 bytes.
- Captured a failing snow seam at 21.8× local sample differences; the corrected encoded asset is
  2.37×. The other corrected seams are 0.01–1.04×. Decoded peaks stay at or below -3.4 dBFS.
- Existing emulator-5554: actual 60-second rain repeat, one decoder, one output during the repeat,
  no underruns; switching to snow starts the corresponding new audio, retaining the graphics surface.
- Rendering after warm-up: approximately 30 fps throughout the 65-second playback check.
- All 229 JVM tests, debug/release builds and release lint-vital pass. Final optimized APK: 9.38 MiB.
- Verified that all ten exact new assets and credits are present in the final release APK.
- Checks/results and short MP3 previews: ignored build/weather-audio-validation/; native loop
  diagnostics: build/weather-animation-validation/.

## Limits

The emulator runs with no audible output. Recorded content is sourced from documented nature
recordings and decoded/level-checked; final speaker level on the TCL remains a listening check.
No physical-TV changes or GitHub publication were performed in this follow-up.
