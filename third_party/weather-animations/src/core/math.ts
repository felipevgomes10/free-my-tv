import type { RGBColor } from './types';

export function lerp(a: number, b: number, t: number): number {
  const tc = Math.max(0, Math.min(1, t));
  return a + (b - a) * tc;
}

export function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value));
}

export function easeInOutCubic(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - (-2 * t + 2) ** 3 / 2;
}

export function hexToRgb(hex: string): RGBColor {
  const h = hex.replace('#', '');
  return {
    r: parseInt(h.substring(0, 2), 16),
    g: parseInt(h.substring(2, 4), 16),
    b: parseInt(h.substring(4, 6), 16),
  };
}

export function lerpColor(a: RGBColor, b: RGBColor, t: number): RGBColor {
  return {
    r: Math.round(lerp(a.r, b.r, t)),
    g: Math.round(lerp(a.g, b.g, t)),
    b: Math.round(lerp(a.b, b.b, t)),
  };
}

export function rgbToString(c: RGBColor): string {
  return `rgb(${c.r},${c.g},${c.b})`;
}
