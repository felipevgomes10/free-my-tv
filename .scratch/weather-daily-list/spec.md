# Align the daily Weather forecast
Status: resolved

The owner finds the floating temperature-range bars visually inconsistent and asks what they
mean. The current bar positions each day's min/max on the week's common scale, so the colored
segment intentionally starts at different positions. Its forced minimum width can also overrun
its track on equal-temperature days.

Simplify the forecast: remove the bars, retain the existing day/icon layout and temperature data,
and use two fixed-width, right-aligned columns with “Mín.” and “Máx.” headings. Keep skeleton
geometry aligned with the ready state. Preserve the weather animation, audio and other work.
Validate debug/release compilation and the real layout on the existing headless emulator only.

## Validation

- Removed RangeBar and its temperature-to-width/offset math, including the zero-range overflow case.
- Headers, every row and the loading skeleton share fixed 40 dp minimum/maximum columns,
  right-aligned, with the existing day/icon positions and maximum emphasis preserved.
- Debug and optimized release APKs compile; release lint-vital passes.
- Visually reviewed all seven rows on emulator-5554 at 1920×1080.
- Capture: ignored build/weather-daily-list-validation/forecast-columns.png.
- The emulator's global graphics capture became blocked after waking, even with the app stopped;
  restarted the same owner-authorized google_tv_31 AVD without loading its snapshot, retaining
  its installed app/data. No additional AVD or physical-TV changes.
