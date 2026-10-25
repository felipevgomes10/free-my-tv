# Weather Animations native adaptation

Upstream: https://github.com/g3sthousen/weather-animations
Revision: `01a0ea7417593802e6a48ecd09aedef8f06539bf` (1.1.1).
Copyright (c) 2026 Marc. MIT; full notice in LICENSE and assets/weather/LICENSE.txt.

The original TypeScript core is retained here as provenance/reference. Android does not execute
it or embed WebView/React/JavaScript for weather. WeatherSceneRenderer.kt adapts the ten required
conditions: upstream sky.ts palettes, clouds.ts lobe/layer/opacity and drift rules, celestial
body proportions, layered fog, twinkling stars, depth-varying precipitation and branched lightning.
The port uses GLES 2.0, generates blurred cloud silhouettes only on scene creation, uploads them
as half-size, single-channel GLES2 alpha textures and recycles their CPU bitmaps. Live rendering is GPU sprites/points/lines.
It preserves the art direction rather than claiming pixel-identical Canvas output. Only the full
moon used by the app is ported; unused upstream conditions/events/fidelity presets are not.

A WebView prototype was measured at ~29.5 fps / 283 MiB app+renderer PSS on the physical TV.
The owner explicitly selected native adaptation to preserve the 150 MB memory budget. The
prototype's HTML/JS bundle and build/test adapter were removed from the app. See the local issue
.scratch/weather-animation/issues/02-use-weather-animations.md for validation and decisions.
