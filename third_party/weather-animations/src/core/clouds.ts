import type { CloudBlob, CloudLobe, Intensity, ResolvedConfig } from './types';
import { random } from './rng';

const LAYER_SPEEDS = [0.006, 0.022, 0.06] as const;
const LAYER_COUNTS: Record<0 | 1 | 2, number> = { 0: 4, 1: 5, 2: 6 };

// Intensity drives cloud coverage (how many) and density (how opaque), so
// light/medium/heavy read differently even when there are no particles.
const INTENSITY_COUNT: Record<Intensity, number> = { light: 0.55, medium: 1, heavy: 1.5 };
const INTENSITY_ALPHA: Record<Intensity, number> = { light: 0.75, medium: 1, heavy: 1.2 };
const WIND_DRIFT_SPEED: Record<Intensity, number> = { light: 0.65, medium: 1, heavy: 1.35 };

function cloudColor(config: ResolvedConfig, alpha: number): string {
  if (config.condition === 'storm') {
    return config.time === 'night' ? `rgba(26,26,42,${alpha})` : `rgba(42,42,58,${alpha})`;
  }
  if (config.condition === 'hail') {
    return config.time === 'night' ? `rgba(40,52,50,${alpha})` : `rgba(70,86,82,${alpha})`;
  }
  if (config.condition === 'blizzard') {
    return config.time === 'night' ? `rgba(64,72,86,${alpha})` : `rgba(170,184,192,${alpha})`;
  }
  if (config.condition === 'sleet' || config.condition === 'freezing-rain') {
    return config.time === 'night' ? `rgba(62,78,88,${alpha})` : `rgba(126,140,148,${alpha})`;
  }
  if (config.condition === 'flurries') {
    return config.time === 'night' ? `rgba(70,82,110,${alpha})` : `rgba(205,216,224,${alpha})`;
  }
  if (config.condition === 'rain' || config.condition === 'drizzle' || config.condition === 'showers') {
    return config.time === 'night' ? `rgba(58,74,90,${alpha})` : `rgba(106,122,138,${alpha})`;
  }
  if (config.condition === 'overcast') {
    return config.time === 'night' ? `rgba(52,62,74,${alpha})` : `rgba(178,186,194,${alpha})`;
  }
  if (config.time === 'night') return `rgba(58,74,106,${alpha})`;
  return `rgba(208,216,224,${alpha})`;
}

function cloudAlpha(config: ResolvedConfig, layer: 0 | 1 | 2): number {
  const base = config.condition === 'storm' ? 0.9
    : config.condition === 'hail' ? 0.85
    : config.condition === 'blizzard' ? 0.9
    : config.condition === 'sleet' ? 0.78
    : config.condition === 'freezing-rain' ? 0.8
    : config.condition === 'rain' ? 0.75
    : config.condition === 'showers' ? 0.68
    : config.condition === 'drizzle' ? 0.58
    : config.condition === 'flurries' ? 0.48
    : config.condition === 'overcast' ? 0.94
    : config.condition === 'wind' ? 0.58
    : config.condition === 'cloudy' ? 0.7
    : 0.5;
  return Math.min(0.95, base * (1 - layer * 0.12) * INTENSITY_ALPHA[config.intensity]);
}

function shouldShowClouds(condition: string): boolean {
  return condition !== 'clear'
    && condition !== 'fog'
    && condition !== 'mist'
    && condition !== 'haze'
    && condition !== 'smog'
    && condition !== 'smoke'
    && condition !== 'dust';
}

function generateLobes(width: number, height: number, flat: boolean): CloudLobe[] {
  const rx = width / 2;
  const ry = height / 2;
  const aspect = rx / ry;
  const count = Math.max(4, Math.min(9, Math.round(2 + aspect * 1.8)));
  const spacing = flat ? 2.4 : 1.8;
  const step = spacing / Math.max(1, count - 1);
  const lobes: CloudLobe[] = [];
  for (let i = 0; i < count; i++) {
    const t = count === 1 ? 0.5 : i / (count - 1);
    const dxBase = (t - 0.5) * spacing;
    const dx = dxBase + (random() * 2 - 1) * step * 0.15;
    const centerBias = 1 - Math.abs(t - 0.5) * 2;
    const rise = flat ? 0.18 : 0.42;
    const r = Math.min(1.1, 0.62 + centerBias * rise + (random() * 0.30 - 0.15));
    const dy = (1 - r) + random() * 0.08 + (random() * 0.2 - 0.1);
    lobes.push({ dx, dy, r });
  }
  // Back-lobes add perceived volume depth behind the main silhouette (cumulus only).
  if (!flat && count >= 5) {
    const backCount = random() < 0.5 ? 1 : 2;
    for (let b = 0; b < backCount; b++) {
      const t = 0.3 + random() * 0.4;
      const dx = (t - 0.5) * spacing * 0.8;
      const r = Math.min(1.15, 0.85 + random() * 0.2);
      const dy = (1 - r) + 0.05 + random() * 0.1;
      lobes.push({ dx, dy, r, alpha: 0.45 });
    }
  }
  return lobes;
}

export function initClouds(config: ResolvedConfig, width: number, height: number): CloudBlob[] {
  if (!shouldShowClouds(config.condition)) return [];
  const blobs: CloudBlob[] = [];
  const countMul = INTENSITY_COUNT[config.intensity];

  if (config.condition === 'storm' || config.condition === 'blizzard') {
    // Cumulonimbus: few massive towers, towering from top of sky downward.
    // Fewer, taller, narrower than regular cumulus — no flat stratus shapes.
    const stormCounts: [number, number, number] = [2, 2, 3];
    for (const layer of [0, 1, 2] as const) {
      const count = Math.max(1, Math.round(stormCounts[layer] * countMul));
      const layerScale = 1 + layer * 0.4;
      for (let i = 0; i < count; i++) {
        const w = width * (config.condition === 'blizzard' ? 0.24 + random() * 0.16 : 0.18 + random() * 0.14) * layerScale;
        const h = height * (config.condition === 'blizzard' ? 0.18 + random() * 0.12 : 0.30 + random() * 0.20) * layerScale;
        blobs.push({
          x: (i / count) * width * 1.3 - width * 0.15,
          y: height * (layer * 0.07 + random() * 0.05) - h * 0.15,
          width: w,
          height: h,
          alpha: cloudAlpha(config, layer),
          speed: LAYER_SPEEDS[layer] * width * (config.condition === 'blizzard' ? 1.7 : 1),
          layer,
          lobes: generateLobes(w, h, false),
        });
      }
    }
    return blobs;
  }

  if (config.condition === 'wind') {
    const count = config.intensity === 'light' ? 3 : config.intensity === 'medium' ? 5 : 6;
    const layers: (0 | 1 | 2)[] = [0, 1, 2, 1, 2, 0];
    for (let i = 0; i < count; i++) {
      const layer = layers[i];
      const layerScale = 1 + layer * 0.28;
      const w = width * (0.20 + random() * 0.16) * layerScale;
      const h = height * (0.10 + random() * 0.06) * layerScale;
      blobs.push({
        x: (i / count) * width * 1.35 - width * 0.18 + (random() - 0.5) * width * 0.10,
        y: height * (0.06 + layer * 0.16 + random() * 0.07),
        width: w,
        height: h,
        alpha: cloudAlpha(config, layer),
        speed: width * (0.025 + layer * 0.012 + random() * 0.02) * WIND_DRIFT_SPEED[config.intensity],
        layer,
        lobes: generateLobes(w, h, random() < 0.45),
      });
    }
    return blobs;
  }

  if (config.condition === 'overcast') {
    const overcastCounts: [number, number, number] = [7, 8, 9];
    for (const layer of [0, 1, 2] as const) {
      const count = Math.max(4, Math.round(overcastCounts[layer] * countMul));
      const layerScale = 1 + layer * 0.25;
      for (let i = 0; i < count; i++) {
        const w = width * (0.32 + random() * 0.18) * layerScale;
        const h = height * (0.11 + random() * 0.07) * layerScale;
        blobs.push({
          x: (i / count) * width * 1.35 - width * 0.2 + (random() - 0.5) * width * 0.08,
          y: height * (0.02 + layer * 0.13 + random() * 0.06),
          width: w,
          height: h,
          alpha: cloudAlpha(config, layer),
          speed: LAYER_SPEEDS[layer] * width * 0.75,
          layer,
          lobes: generateLobes(w, h, true),
        });
      }
    }
    return blobs;
  }

  for (const layer of [0, 1, 2] as const) {
    const baseCount = LAYER_COUNTS[layer];
    const count = Math.max(2, Math.round(baseCount * countMul));
    for (let i = 0; i < count; i++) {
      const layerScale = 1 + layer * 0.35;
      const w = width * (0.22 + random() * 0.18) * layerScale;
      const h = height * (0.12 + random() * 0.08) * layerScale;
      blobs.push({
        x: (i / count) * width * 1.4 - width * 0.2,
        y: height * (0.04 + layer * 0.18 + random() * 0.08),
        width: w,
        height: h,
        alpha: cloudAlpha(config, layer),
        speed: LAYER_SPEEDS[layer] * width,
        layer,
        lobes: generateLobes(w, h, random() < 0.35),
      });
    }
  }
  return blobs;
}

export function updateClouds(blobs: CloudBlob[], delta: number, width: number): void {
  for (const b of blobs) {
    b.x += b.speed * delta;
    const bounds = cloudVisualBounds(b);
    if (b.x + bounds.minX > width) b.x = -bounds.maxX;
  }
}

export interface CloudVisualBounds {
  minX: number;
  maxX: number;
  minY: number;
  maxY: number;
}

export interface CloudSpriteMetrics {
  width: number;
  height: number;
  centerX: number;
  centerY: number;
  blurRadius: number;
}

export function cloudVisualBounds(b: Pick<CloudBlob, 'width' | 'height' | 'lobes'>): CloudVisualBounds {
  const rx = b.width / 2;
  const ry = b.height / 2;

  if (b.lobes.length === 0) {
    return { minX: -rx, maxX: rx, minY: -ry, maxY: ry };
  }

  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;

  for (const lobe of b.lobes) {
    const cx = lobe.dx * rx;
    const cy = lobe.dy * ry;
    const r = lobe.r * ry;
    minX = Math.min(minX, cx - r);
    maxX = Math.max(maxX, cx + r);
    minY = Math.min(minY, cy - r);
    maxY = Math.max(maxY, cy + r);
  }

  return { minX, maxX, minY, maxY };
}

export function cloudSpriteMetrics(b: Pick<CloudBlob, 'width' | 'height' | 'lobes'>): CloudSpriteMetrics {
  const bounds = cloudVisualBounds(b);
  const blurRadius = Math.max(2, b.height * 0.5 * 0.12);
  const edgePad = blurRadius * 4 + 2;
  return {
    width: Math.ceil(bounds.maxX - bounds.minX + edgePad * 2),
    height: Math.ceil(bounds.maxY - bounds.minY + edgePad * 2),
    centerX: edgePad - bounds.minX,
    centerY: edgePad - bounds.minY,
    blurRadius,
  };
}

// Cloud shapes never change, only their position — so render each one to a sprite
// once and blit it every frame. Each lobe is a radial gradient (solid core → transparent
// rim) so overlaps merge smoothly; the cloud's overall translucency is applied at
// composite time via globalAlpha. Sprites are keyed on condition:time and rebuilt on change.
function buildSprite(b: CloudBlob, config: ResolvedConfig): OffscreenCanvas {
  const rx = b.width / 2;
  const ry = b.height / 2;
  const metrics = cloudSpriteMetrics(b);
  const blurRadius = metrics.blurRadius;
  const offW = metrics.width;
  const offH = metrics.height;
  const off = new OffscreenCanvas(offW, offH);
  const c = off.getContext('2d')!;
  const lx = metrics.centerX;
  const ly = metrics.centerY;
  b.spriteCx = lx;
  b.spriteCy = ly;

  if (b.lobes.length === 0) {
    // Thin wind streak: a soft, wispy horizontal ellipse (scaled circular gradient).
    c.filter = `blur(${blurRadius}px)`;
    c.translate(lx, ly);
    c.scale(rx / ry, 1);
    const g = c.createRadialGradient(0, 0, 0, 0, 0, ry);
    g.addColorStop(0, cloudColor(config, 1));
    g.addColorStop(0.4, cloudColor(config, 0.6));
    g.addColorStop(1, 'rgba(0,0,0,0)');
    c.fillStyle = g;
    c.beginPath();
    c.arc(0, 0, ry, 0, Math.PI * 2);
    c.fill();
    return off;
  }

  // Two-pass blob rendering: draw all lobes solid onto a temp canvas so their
  // overlapping regions merge into one unified solid shape, then blit the whole
  // result with blur. The blur softens only the outer silhouette — no individual
  // circles visible inside the body. All lobes draw at full opacity; varying
  // per-lobe alpha on the temp canvas creates hard steps that survive the blur.
  const tmp = new OffscreenCanvas(offW, offH);
  const tc = tmp.getContext('2d')!;
  tc.fillStyle = cloudColor(config, 1);
  for (const lobe of b.lobes) {
    const cx = lx + lobe.dx * rx;
    const cy = ly + lobe.dy * ry;
    tc.beginPath();
    tc.arc(cx, cy, lobe.r * ry, 0, Math.PI * 2);
    tc.fill();
  }
  c.filter = `blur(${blurRadius}px)`;
  c.drawImage(tmp, 0, 0);
  c.filter = 'none';

  // Vertical form shading, clipped to the cloud body: lit top, shaded base.
  c.globalCompositeOperation = 'source-atop';
  const day = config.time === 'day';
  const darkBase = config.condition === 'rain'
    || config.condition === 'drizzle'
    || config.condition === 'showers'
    || config.condition === 'freezing-rain'
    || config.condition === 'storm'
    || config.condition === 'hail'
    || config.condition === 'sleet'
    || config.condition === 'blizzard'
    || config.condition === 'overcast';
  const topLight = day ? 'rgba(255,244,214,0.22)' : 'rgba(190,205,235,0.10)';
  const baseShade = darkBase ? 'rgba(0,0,0,0.35)' : day ? 'rgba(0,0,0,0.16)' : 'rgba(0,0,0,0.28)';
  const shade = c.createLinearGradient(0, ly - ry * 1.1, 0, ly + ry * 1.1);
  shade.addColorStop(0, topLight);
  shade.addColorStop(0.5, 'rgba(255,255,255,0)');
  shade.addColorStop(1, baseShade);
  c.fillStyle = shade;
  c.fillRect(0, 0, offW, offH);
  c.globalCompositeOperation = 'source-over';

  return off;
}

export function drawClouds(
  ctx: CanvasRenderingContext2D,
  blobs: CloudBlob[],
  config: ResolvedConfig,
  alpha: number,
): void {
  if (blobs.length === 0) return;
  ctx.save();
  for (const b of blobs) {
    const key = `${config.condition}:${config.time}`;
    if (!b.sprite || b.spriteKey !== key) {
      b.sprite = buildSprite(b, config);
      b.spriteKey = key;
    }
    ctx.globalAlpha = alpha * b.alpha;
    ctx.drawImage(b.sprite, b.x - (b.spriteCx ?? 0), b.y - (b.spriteCy ?? 0));
  }
  ctx.restore();
}
