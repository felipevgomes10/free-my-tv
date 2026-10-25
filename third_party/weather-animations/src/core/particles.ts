import type { Particle, ResolvedConfig } from './types';
import { random } from './rng';
import { clamp } from './math';

export class ParticlePool {
  readonly particles: Particle[];
  private cursor = 0;

  constructor(size: number) {
    this.particles = Array.from({ length: size }, () => ({
      x: 0, y: 0, vx: 0, vy: 0,
      alpha: 0, size: 1, length: 1, phase: 0,
      depth: 0.5, bounces: 0, kind: 'primary' as const,
      active: false,
    }));
  }

  spawn(): Particle | null {
    const start = this.cursor;
    do {
      const p = this.particles[this.cursor];
      this.cursor = (this.cursor + 1) % this.particles.length;
      if (!p.active) {
        p.active = true;
        return p;
      }
    } while (this.cursor !== start);
    return null;
  }

  reset(): void {
    for (const p of this.particles) p.active = false;
    this.cursor = 0;
  }
}

const INTENSITY_SCALE = { light: 0.3, medium: 0.65, heavy: 1.0 } as const;

export function getIntensityScale(config: ResolvedConfig): number {
  return INTENSITY_SCALE[config.intensity];
}

const FIDELITY_SCALE = { subtle: 1, rich: 1.8 } as const;

export function getFidelityScale(config: ResolvedConfig): number {
  return FIDELITY_SCALE[config.fidelity];
}

/** Map depth (0 far … 1 near) to a scale between `far` and `near`. */
export function depthFactor(depth: number, far: number, near: number): number {
  return far + (near - far) * depth;
}

const SPLASH_LIFE = 0.15; // seconds

export function rainSplashes(intensity: ResolvedConfig['intensity']): boolean {
  return intensity !== 'light';
}

export function splashRadius(age: number, maxRadius: number, life: number): number {
  return maxRadius * clamp(age / life, 0, 1);
}

export function splashAlpha(age: number, life: number): number {
  return 1 - clamp(age / life, 0, 1);
}

/** Global wind value: base offset plus a slow sinusoidal gust. */
export function gustOffset(time: number, base: number, amp: number, freq: number): number {
  return base + amp * Math.sin(time * freq);
}

/** Reflect and damp vertical velocity for a hail bounce. vy is positive (falling). */
export function bounceVelocity(vy: number, damping: number): number {
  return -vy * damping;
}

/** Returns true when a hail stone should stop bouncing. */
export function shouldStopBouncing(vy: number, bounces: number, maxBounces: number, minVel: number): boolean {
  return bounces >= maxBounces || Math.abs(vy) < minVel;
}

export interface HailBounceConfig {
  enabled: boolean;
  maxBounces: number;
  damping: number;
  minVelocity: number;
  lateralJitter: number;
}

export function hailBounceConfig(intensity: ResolvedConfig['intensity'], rich: boolean): HailBounceConfig {
  if (intensity === 'light') {
    return { enabled: false, maxBounces: 0, damping: 0, minVelocity: 0, lateralJitter: 0 };
  }

  const base = intensity === 'medium'
    ? { maxBounces: 1, damping: 0.32, minVelocity: 90, lateralJitter: 35 }
    : { maxBounces: 2, damping: 0.42, minVelocity: 80, lateralJitter: 60 };

  return {
    enabled: true,
    maxBounces: base.maxBounces + (rich ? 1 : 0),
    damping: base.damping,
    minVelocity: base.minVelocity,
    lateralJitter: base.lateralJitter + (rich ? 30 : 0),
  };
}

const WIND_GUST_SPEED_MULTIPLIER: Record<ResolvedConfig['intensity'], number> = {
  light: 0.7,
  medium: 1,
  heavy: 1.35,
};

export function windGustSpeedMultiplier(intensity: ResolvedConfig['intensity']): number {
  return WIND_GUST_SPEED_MULTIPLIER[intensity];
}

export function showerBurstFactor(time: number): number {
  const wave = Math.max(0, Math.sin(time * 1.7) + Math.sin(time * 4.1) * 0.25);
  return 0.18 + wave * wave * 1.15;
}

// --- Particle System ---

const POOL_SIZE = 900;

export class ParticleSystem {
  private pool: ParticlePool;
  private spawnAccum = 0;
  private time = 0;
  private config: ResolvedConfig | null = null;
  private width = 0;
  private height = 0;

  constructor() {
    this.pool = new ParticlePool(POOL_SIZE);
  }

  init(config: ResolvedConfig, width: number, height: number): void {
    this.config = config;
    this.width = width;
    this.height = height;
    this.pool.reset();
    this.spawnAccum = 0;
    this.time = 0;

    if (config.condition === 'clear' && config.time === 'night') {
      const count = Math.floor(120 * getIntensityScale(config));
      for (let i = 0; i < count; i++) spawnStar(this.pool, width, height);
    }
  }

  /**
   * Adapt to a new canvas size. New particles spawn against the updated
   * dimensions; existing particles (notably the static star field) are
   * rescaled proportionally so they keep filling the area without popping.
   */
  resize(width: number, height: number): void {
    const prevW = this.width;
    const prevH = this.height;
    this.width = width;
    this.height = height;
    if (prevW <= 0 || prevH <= 0) return;
    const sx = width / prevW;
    const sy = height / prevH;
    if (sx === 1 && sy === 1) return;
    for (const p of this.pool.particles) {
      if (!p.active) continue;
      p.x *= sx;
      p.y *= sy;
    }
  }

  update(delta: number): void {
    if (!this.config) return;
    this.time += delta;
    const cfg = this.config;
    const scale = getIntensityScale(cfg) * getFidelityScale(cfg);
    const w = this.width;
    const h = this.height;
    const rich = cfg.fidelity === 'rich';
    const gust = gustOffset(this.time, -20, 12, 0.4);

    for (const p of this.pool.particles) {
      if (!p.active) continue;
      p.x += p.vx * delta;
      p.y += p.vy * delta;
      p.phase += delta;

      if (p.kind === 'splash') {
        if (p.phase >= SPLASH_LIFE) p.active = false;
        continue;
      }

      if (p.kind === 'droplet') {
        p.vy += 500 * delta;
        p.alpha -= delta * 4;
        if (p.alpha <= 0 || p.y > h + 10) p.active = false;
        continue;
      }

      if (p.kind === 'leaf') {
        p.phase += delta * 6;
      }

      if (cfg.condition === 'snow' || (cfg.condition === 'sleet' && p.length === 0)) {
        p.vx = Math.sin(p.phase * 0.8) * 18;
      }
      if (cfg.condition === 'flurries') {
        p.vx = Math.sin(p.phase * 0.9 + p.depth * 3) * 26 + gust * depthFactor(p.depth, 0.35, 0.65);
      }
      if (cfg.condition === 'blizzard') {
        p.vx = (-135 + Math.sin(p.phase * 1.4 + p.depth * 4) * 22) * depthFactor(p.depth, 0.65, 1);
      }
      if (cfg.condition === 'rain'
        || cfg.condition === 'drizzle'
        || cfg.condition === 'showers'
        || cfg.condition === 'freezing-rain'
        || (cfg.condition === 'sleet' && p.length > 0)) {
        p.vx = gust * depthFactor(p.depth, 0.6, 1);
      }
      if (cfg.condition === 'clear' && cfg.time === 'night') {
        p.alpha = 0.5 + 0.5 * Math.sin(p.phase * 2.5 + p.size);
      }

      if (cfg.condition === 'wind' && p.kind === 'primary') {
        if (p.y > h + 30 || p.y < -30 || p.x < -p.length || p.x > w + 20) {
          p.active = false;
        }
        continue;
      }

      // Rain/storm impact → splash at the bottom edge (intensity-gated; rich = more, bigger).
      if ((cfg.condition === 'rain' || cfg.condition === 'storm' || cfg.condition === 'showers') && p.kind === 'primary' && p.y > h && rainSplashes(cfg.intensity)) {
        const threshold = rich ? 0.3 : 0.6;
        if (p.depth > threshold) {
          spawnSplash(this.pool, p.x, h, rich, cfg.intensity);
          if (rich) spawnDroplets(this.pool, p.x, h, 2 + Math.floor(random() * 3));
        }
        p.active = false;
        continue;
      }

      // Gravity pulls bounced hail stones back down.
      if (cfg.condition === 'hail' && p.bounces > 0) {
        p.vy += 980 * delta;
      }

      // Hail bounce — reflect and damp on ground hit; deactivate after max bounces or low velocity.
      if (cfg.condition === 'hail' && p.kind === 'primary' && p.y > h) {
        const bounce = hailBounceConfig(cfg.intensity, rich);
        if (bounce.enabled && !shouldStopBouncing(p.vy, p.bounces, bounce.maxBounces, bounce.minVelocity)) {
          p.y = h;
          p.vy = bounceVelocity(p.vy, bounce.damping);
          p.vx += (random() - 0.5) * bounce.lateralJitter;
          p.size *= 0.8;
          p.bounces += 1;
        } else {
          p.active = false;
        }
        continue;
      }

      if (p.y > h + 20 || p.x < -20 || p.x > w + 20 || p.y < -20) {
        p.active = false;
      }
    }

    const rates: Partial<Record<string, number>> = {
      rain: 120 * scale,
      drizzle: 54 * scale,
      showers: 132 * scale * showerBurstFactor(this.time),
      'freezing-rain': 100 * scale,
      'storm-rain': 280 * scale,
      snow: 30 * scale,
      flurries: 13 * scale,
      blizzard: 150 * scale,
      sleet: 120 * scale,
      wind: 36 * scale,
      hail: 90 * scale,
    };
    const condKey = cfg.condition === 'storm' ? 'storm-rain' : cfg.condition;
    const rate = rates[condKey];

    if (rate) {
      this.spawnAccum += rate * delta;
      while (this.spawnAccum >= 1) {
        this.spawnAccum -= 1;
        if (cfg.condition === 'rain') spawnRain(this.pool, w);
        else if (cfg.condition === 'drizzle') spawnDrizzle(this.pool, w);
        else if (cfg.condition === 'showers') spawnShowers(this.pool, w);
        else if (cfg.condition === 'freezing-rain') spawnFreezingRain(this.pool, w);
        else if (cfg.condition === 'storm') spawnStormRain(this.pool, w);
        else if (cfg.condition === 'snow') spawnSnow(this.pool, w);
        else if (cfg.condition === 'flurries') spawnFlurry(this.pool, w);
        else if (cfg.condition === 'blizzard') spawnBlizzard(this.pool, w);
        else if (cfg.condition === 'sleet') spawnSleet(this.pool, w);
        else if (cfg.condition === 'wind') spawnWind(this.pool, w, h, rich, cfg.intensity);
        else if (cfg.condition === 'hail') spawnHail(this.pool, w);
      }
    }
  }

  draw(ctx: CanvasRenderingContext2D, alpha: number, config?: ResolvedConfig): void {
    const cfg = config ?? this.config;
    if (!cfg) return;
    ctx.save();
    for (const p of this.pool.particles) {
      if (!p.active) continue;
      drawParticle(ctx, p, cfg, alpha);
    }
    ctx.restore();
  }
}

function drawParticle(ctx: CanvasRenderingContext2D, p: Particle, cfg: ResolvedConfig, systemAlpha: number): void {
  if (p.kind === 'droplet') {
    ctx.globalAlpha = systemAlpha * p.alpha;
    ctx.fillStyle = 'rgba(180,205,225,1)';
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fill();
    return;
  }

  if (p.kind === 'splash') {
    const r = splashRadius(p.phase, p.size, SPLASH_LIFE);
    const fade = splashAlpha(p.phase, SPLASH_LIFE);
    const heavy = cfg.intensity === 'heavy';
    ctx.globalAlpha = systemAlpha * fade * (heavy ? 0.9 : 0.75);
    ctx.strokeStyle = 'rgba(180,205,225,1)';
    ctx.lineWidth = heavy ? 2 : 1.5;
    ctx.beginPath();
    ctx.ellipse(p.x, p.y, r, r * 0.35, 0, 0, Math.PI * 2);
    ctx.stroke();
    return;
  }

  if (p.kind === 'leaf') {
    ctx.save();
    ctx.globalAlpha = systemAlpha * p.alpha;
    ctx.translate(p.x, p.y);
    ctx.rotate(p.phase);
    ctx.fillStyle = 'rgba(150,170,90,1)';
    ctx.beginPath();
    ctx.ellipse(0, 0, p.size, p.size * 0.5, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    return;
  }

  ctx.globalAlpha = systemAlpha * p.alpha;

  if (cfg.condition === 'rain'
    || cfg.condition === 'storm'
    || cfg.condition === 'drizzle'
    || cfg.condition === 'showers'
    || cfg.condition === 'freezing-rain'
    || (cfg.condition === 'sleet' && p.length > 0)) {
    ctx.strokeStyle = cfg.condition === 'storm'
      ? 'rgba(180,200,220,0.8)'
      : cfg.condition === 'freezing-rain'
        ? 'rgba(215,238,245,0.78)'
        : cfg.condition === 'showers'
          ? 'rgba(155,190,220,0.68)'
      : cfg.condition === 'sleet'
        ? 'rgba(190,210,222,0.72)'
        : cfg.condition === 'drizzle'
          ? 'rgba(175,198,215,0.48)'
          : 'rgba(160,190,220,0.7)';
    ctx.lineWidth = cfg.condition === 'storm' ? 1.5 : cfg.condition === 'drizzle' ? 0.75 : cfg.condition === 'freezing-rain' ? 0.8 : 1;
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.lineTo(p.x + p.vx * 0.04, p.y + p.length);
    ctx.stroke();
  } else if (cfg.condition === 'snow'
    || cfg.condition === 'flurries'
    || cfg.condition === 'blizzard'
    || (cfg.condition === 'sleet' && p.length === 0)) {
    if (p.depth < 0.5) {
      const g = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.size);
      g.addColorStop(0, `rgba(255,255,255,${p.alpha})`);
      g.addColorStop(1, 'rgba(255,255,255,0)');
      ctx.fillStyle = g;
    } else {
      ctx.fillStyle = `rgba(255,255,255,${p.alpha})`;
    }
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fill();
  } else if (cfg.condition === 'clear' && cfg.time === 'night') {
    ctx.fillStyle = `rgba(255,255,240,${p.alpha})`;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fill();
    if (p.length > 0 && cfg.fidelity === 'rich') {
      ctx.globalAlpha = systemAlpha * p.alpha * 0.6;
      ctx.strokeStyle = 'rgba(255,255,240,1)';
      ctx.lineWidth = 0.75;
      const g = p.size * 3;
      ctx.beginPath();
      ctx.moveTo(p.x - g, p.y); ctx.lineTo(p.x + g, p.y);
      ctx.moveTo(p.x, p.y - g); ctx.lineTo(p.x, p.y + g);
      ctx.stroke();
    }
  } else if (cfg.condition === 'wind') {
    const curve = -p.length * (0.04 + p.depth * 0.08) + Math.sin(p.phase * 0.7 + p.depth * 5) * p.length * 0.035;
    const endDrift = Math.sin(p.phase * 0.9 + p.length) * 2.5;
    ctx.lineCap = 'round';
    ctx.strokeStyle = 'rgba(215,232,245,1)';
    ctx.lineWidth = p.size;
    ctx.beginPath();
    ctx.moveTo(p.x, p.y);
    ctx.quadraticCurveTo(p.x + p.length * 0.52, p.y + curve, p.x + p.length, p.y + endDrift);
    ctx.stroke();
  } else if (cfg.condition === 'hail') {
    ctx.fillStyle = `rgba(225,235,245,${p.alpha})`;
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.size, 0, Math.PI * 2);
    ctx.fill();
    // faint rim shimmer
    ctx.globalAlpha = systemAlpha * p.alpha * 0.5;
    ctx.strokeStyle = 'rgba(255,255,255,0.9)';
    ctx.lineWidth = 0.75;
    ctx.beginPath();
    ctx.arc(p.x - p.size * 0.25, p.y - p.size * 0.25, p.size * 0.6, 0, Math.PI * 2);
    ctx.stroke();
  }
}

const INTENSITY_SPLASH = { light: 1.0, medium: 1.4, heavy: 2.0 } as const;

function spawnSplash(pool: ParticlePool, x: number, h: number, rich: boolean, intensity: ResolvedConfig['intensity']): void {
  const p = pool.spawn();
  if (!p) return;
  const scale = INTENSITY_SPLASH[intensity];
  p.x = x;
  p.y = h - 1;
  p.vx = 0;
  p.vy = 0;
  p.alpha = 1;
  p.size = (rich ? 8 + random() * 5 : 5 + random() * 3) * scale;
  p.length = 0;
  p.phase = 0;
  p.depth = 1;
  p.bounces = 0;
  p.kind = 'splash';
}

function spawnDroplets(pool: ParticlePool, x: number, h: number, count: number): void {
  for (let i = 0; i < count; i++) {
    const p = pool.spawn();
    if (!p) break;
    p.x = x + (random() - 0.5) * 6;
    p.y = h - 1;
    p.vx = (random() - 0.5) * 180;
    p.vy = -(50 + random() * 100);
    p.alpha = 0.6 + random() * 0.4;
    p.size = 1 + random() * 1.5;
    p.length = 0;
    p.phase = 0;
    p.depth = 1;
    p.bounces = 0;
    p.kind = 'droplet';
  }
}

function spawnRain(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 100) - 50;
  p.y = -10;
  p.vx = -20 * depthFactor(depth, 0.6, 1);
  p.vy = (650 + random() * 200) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.4 + random() * 0.4) * depthFactor(depth, 0.5, 1);
  p.size = 1;
  p.length = (14 + random() * 10) * depthFactor(depth, 0.5, 1);
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnDrizzle(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 80) - 40;
  p.y = -10;
  p.vx = -12 * depthFactor(depth, 0.5, 1);
  p.vy = (420 + random() * 160) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.22 + random() * 0.24) * depthFactor(depth, 0.55, 1);
  p.size = 0.75;
  p.length = (6 + random() * 6) * depthFactor(depth, 0.55, 1);
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnStormRain(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  p.x = random() * (w + 100) - 50;
  p.y = -10;
  p.vx = -35;
  p.vy = 900 + random() * 300;
  p.alpha = 0.5 + random() * 0.4;
  p.size = 1;
  p.length = 20 + random() * 14;
  p.phase = 0;
  p.depth = random();
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnShowers(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 120) - 60;
  p.y = -10;
  p.vx = -22 * depthFactor(depth, 0.6, 1);
  p.vy = (680 + random() * 230) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.42 + random() * 0.34) * depthFactor(depth, 0.5, 1);
  p.size = 1;
  p.length = (16 + random() * 10) * depthFactor(depth, 0.55, 1);
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnFreezingRain(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 90) - 45;
  p.y = -10;
  p.vx = -14 * depthFactor(depth, 0.55, 1);
  p.vy = (610 + random() * 170) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.45 + random() * 0.28) * depthFactor(depth, 0.82, 1);
  p.size = 0.8;
  p.length = (8 + random() * 8) * depthFactor(depth, 0.65, 1);
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnSnow(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * w;
  p.y = -10;
  p.vx = 0;
  p.vy = (40 + random() * 50) * depthFactor(depth, 0.5, 1);
  p.alpha = (0.6 + random() * 0.4) * depthFactor(depth, 0.5, 1);
  p.size = (1.5 + random() * 3) * depthFactor(depth, 0.5, 1);
  p.length = 0;
  p.phase = random() * Math.PI * 2;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnFlurry(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * w;
  p.y = -10;
  p.vx = (random() - 0.5) * 60;
  p.vy = (28 + random() * 46) * depthFactor(depth, 0.5, 1);
  p.alpha = (0.45 + random() * 0.35) * depthFactor(depth, 0.55, 1);
  p.size = (1 + random() * 2.2) * depthFactor(depth, 0.55, 1);
  p.length = 0;
  p.phase = random() * Math.PI * 2;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnBlizzard(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 260) + 80;
  p.y = random() * 80 - 20;
  p.vx = (-145 - random() * 80) * depthFactor(depth, 0.65, 1);
  p.vy = (95 + random() * 120) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.45 + random() * 0.4) * depthFactor(depth, 0.55, 1);
  p.size = (0.8 + random() * 1.7) * depthFactor(depth, 0.55, 1);
  p.length = 0;
  p.phase = random() * Math.PI * 2;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnSleet(pool: ParticlePool, w: number): void {
  if (random() < 0.58) {
    spawnSleetRain(pool, w);
  } else {
    spawnSleetPellet(pool, w);
  }
}

function spawnSleetRain(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 90) - 45;
  p.y = -10;
  p.vx = -16 * depthFactor(depth, 0.6, 1);
  p.vy = (560 + random() * 190) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.4 + random() * 0.35) * depthFactor(depth, 0.5, 1);
  p.size = 1;
  p.length = (8 + random() * 8) * depthFactor(depth, 0.55, 1);
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnSleetPellet(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * w;
  p.y = -10;
  p.vx = 0;
  p.vy = (120 + random() * 90) * depthFactor(depth, 0.5, 1);
  p.alpha = (0.55 + random() * 0.35) * depthFactor(depth, 0.55, 1);
  p.size = (1 + random() * 2.2) * depthFactor(depth, 0.55, 1);
  p.length = 0;
  p.phase = random() * Math.PI * 2;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnStar(pool: ParticlePool, w: number, h: number): void {
  const p = pool.spawn();
  if (!p) return;
  const hero = random() < 0.12;
  p.x = random() * w;
  p.y = random() * h * 0.7;
  p.vx = 0;
  p.vy = 0;
  p.alpha = 0.3 + random() * 0.7;
  p.size = hero ? 1.6 + random() * 1.2 : 0.5 + random() * 1.5;
  p.length = hero ? 1 : 0; // hero marker
  p.phase = random() * Math.PI * 2;
  p.depth = 0.5;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnWind(pool: ParticlePool, _w: number, h: number, rich: boolean, intensity: ResolvedConfig['intensity']): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  const speed = windGustSpeedMultiplier(intensity);
  p.x = -20;
  p.y = random() * h;
  p.vx = (180 + random() * 170) * depthFactor(depth, 0.6, 1) * speed;
  p.vy = (random() - 0.5) * (rich ? 16 : 9);
  p.alpha = (0.12 + random() * 0.24) * depthFactor(depth, 0.55, 1);
  p.size = (0.55 + random() * 0.85) * depthFactor(depth, 0.65, 1);
  p.length = (26 + random() * 48) * depthFactor(depth, 0.62, 1);
  p.phase = random() * Math.PI * 2;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}

function spawnHail(pool: ParticlePool, w: number): void {
  const p = pool.spawn();
  if (!p) return;
  const depth = random();
  p.x = random() * (w + 60) - 30;
  p.y = -10;
  p.vx = -8 * depthFactor(depth, 0.5, 1);
  p.vy = (700 + random() * 250) * depthFactor(depth, 0.55, 1);
  p.alpha = (0.7 + random() * 0.3) * depthFactor(depth, 0.5, 1);
  p.size = (2 + random() * 2.5) * depthFactor(depth, 0.5, 1);
  p.length = 0;
  p.phase = 0;
  p.depth = depth;
  p.bounces = 0;
  p.kind = 'primary';
}
