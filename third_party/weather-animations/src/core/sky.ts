import type { RGBColor, ResolvedConfig, Condition, TimeOfDay } from './types';
import { hexToRgb, lerp, lerpColor, rgbToString } from './math';

interface SkyPalette {
  top: RGBColor;
  bottom: RGBColor;
}

type PaletteKey = `${Condition}:${TimeOfDay}`;

const SKY_PALETTES: Record<PaletteKey, SkyPalette> = {
  'clear:day':    { top: hexToRgb('#1a6b9e'), bottom: hexToRgb('#87ceeb') },
  'clear:night':  { top: hexToRgb('#0a0a2e'), bottom: hexToRgb('#1a1a4e') },
  'cloudy:day':   { top: hexToRgb('#6b7a8d'), bottom: hexToRgb('#b0bec5') },
  'cloudy:night': { top: hexToRgb('#1c2331'), bottom: hexToRgb('#2d3a4a') },
  'rain:day':     { top: hexToRgb('#3d5166'), bottom: hexToRgb('#607d8b') },
  'rain:night':   { top: hexToRgb('#1a202c'), bottom: hexToRgb('#2d3748') },
  'snow:day':     { top: hexToRgb('#b0bec5'), bottom: hexToRgb('#e8eef4') },
  'snow:night':   { top: hexToRgb('#1a1a3a'), bottom: hexToRgb('#2d2d50') },
  'storm:day':    { top: hexToRgb('#1a1a2e'), bottom: hexToRgb('#16213e') },
  'storm:night':  { top: hexToRgb('#0a0a0f'), bottom: hexToRgb('#11111b') },
  'fog:day':      { top: hexToRgb('#c8cdd2'), bottom: hexToRgb('#e8eaed') },
  'fog:night':    { top: hexToRgb('#374151'), bottom: hexToRgb('#4b5563') },
  'wind:day':     { top: hexToRgb('#3f6f98'), bottom: hexToRgb('#a7c9df') },
  'wind:night':   { top: hexToRgb('#223b5c'), bottom: hexToRgb('#2a4778') },
  'hail:day':     { top: hexToRgb('#2f3b3a'), bottom: hexToRgb('#4a5a55') },
  'hail:night':   { top: hexToRgb('#141c1b'), bottom: hexToRgb('#212e2b') },
  'drizzle:day':  { top: hexToRgb('#718394'), bottom: hexToRgb('#b7c6cf') },
  'drizzle:night': { top: hexToRgb('#202936'), bottom: hexToRgb('#354455') },
  'overcast:day': { top: hexToRgb('#5f6b76'), bottom: hexToRgb('#9ea9b1') },
  'overcast:night': { top: hexToRgb('#1b222c'), bottom: hexToRgb('#303946') },
  'mist:day':     { top: hexToRgb('#b8c4ca'), bottom: hexToRgb('#e1e6e8') },
  'mist:night':   { top: hexToRgb('#303a46'), bottom: hexToRgb('#4a5560') },
  'haze:day':     { top: hexToRgb('#b7a98d'), bottom: hexToRgb('#e7d5b5') },
  'haze:night':   { top: hexToRgb('#323041'), bottom: hexToRgb('#50495c') },
  'smog:day':     { top: hexToRgb('#9e967d'), bottom: hexToRgb('#d2c6a5') },
  'smog:night':   { top: hexToRgb('#2f2d31'), bottom: hexToRgb('#4f493f') },
  'smoke:day':    { top: hexToRgb('#82776b'), bottom: hexToRgb('#b9aa99') },
  'smoke:night':  { top: hexToRgb('#29282c'), bottom: hexToRgb('#46403f') },
  'sleet:day':    { top: hexToRgb('#778690'), bottom: hexToRgb('#c4d0d6') },
  'sleet:night':  { top: hexToRgb('#202832'), bottom: hexToRgb('#3a4652') },
  'showers:day':  { top: hexToRgb('#5f7588'), bottom: hexToRgb('#a9bbc7') },
  'showers:night': { top: hexToRgb('#1f2a38'), bottom: hexToRgb('#34465a') },
  'freezing-rain:day': { top: hexToRgb('#6f8491'), bottom: hexToRgb('#c9d8dc') },
  'freezing-rain:night': { top: hexToRgb('#1d2832'), bottom: hexToRgb('#3a4b56') },
  'flurries:day': { top: hexToRgb('#a9bdc9'), bottom: hexToRgb('#e4edf1') },
  'flurries:night': { top: hexToRgb('#222b44'), bottom: hexToRgb('#3b4664') },
  'blizzard:day': { top: hexToRgb('#8799a5'), bottom: hexToRgb('#d6e1e6') },
  'blizzard:night': { top: hexToRgb('#161d2a'), bottom: hexToRgb('#313a4a') },
  'dust:day':     { top: hexToRgb('#b28f62'), bottom: hexToRgb('#dec18e') },
  'dust:night':   { top: hexToRgb('#3b3028'), bottom: hexToRgb('#5b4937') },
};

function getPalette(config: ResolvedConfig): SkyPalette {
  return SKY_PALETTES[`${config.condition}:${config.time}`];
}

function brighten(c: RGBColor, amount: number): RGBColor {
  return {
    r: Math.round(lerp(c.r, 255, amount)),
    g: Math.round(lerp(c.g, 255, amount)),
    b: Math.round(lerp(c.b, 255, amount)),
  };
}

export function applySky(el: HTMLElement, config: ResolvedConfig, flash = 0): void {
  const p = getPalette(config);
  const top = flash > 0 ? brighten(p.top, flash * 0.4) : p.top;
  const bottom = flash > 0 ? brighten(p.bottom, flash * 0.4) : p.bottom;
  el.style.background = `linear-gradient(to bottom, ${rgbToString(top)}, ${rgbToString(bottom)})`;
}

export function lerpSky(
  el: HTMLElement,
  from: ResolvedConfig,
  to: ResolvedConfig,
  progress: number,
): void {
  const fp = getPalette(from);
  const tp = getPalette(to);
  const top = rgbToString(lerpColor(fp.top, tp.top, progress));
  const bottom = rgbToString(lerpColor(fp.bottom, tp.bottom, progress));
  el.style.background = `linear-gradient(to bottom, ${top}, ${bottom})`;
}
