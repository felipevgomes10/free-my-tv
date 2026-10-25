# Restore the atmospheric animation style
Status: resolved

The owner rejected the plastic/pastel look and asked to return to the previous animations.
Restore the procedural sky, layered clouds, sun/moon, stars, fog, distant ridges and perspective
rain/snow particles. Remove plastic meshes/materials and return to the original 640×360 budget
without a depth buffer. Preserve the recorded nature audio, mute, Screensaver, 29-code mapping,
continuous transitions and the fixed Mín./Máx. daily columns.

## Answer

Restored the previous cloud/sky shader and the 768-particle perspective rain/snow renderer,
with its 640×360 surface and no depth buffer. Removed the plastic materials/meshes and their
geometry-only test. All 228 remaining JVM tests, debug/release builds and release lint-vital pass.
All ten scenes and the unknown-condition fallback were captured on the same emulator; the
restored shaders render successfully. Captures: build/weather-animation-validation/.

SHA-256 comparisons confirm that all ten recorded audio assets, their player/catalog and the
Mín./Máx. Weather screen are unchanged. The optimized APK includes only the restored sky and
particle shaders, plus the exact preserved recordings. No physical TV or publication changes.
