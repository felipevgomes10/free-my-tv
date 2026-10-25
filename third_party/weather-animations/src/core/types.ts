export type Condition =
  | 'clear'
  | 'cloudy'
  | 'rain'
  | 'snow'
  | 'storm'
  | 'fog'
  | 'wind'
  | 'hail'
  | 'drizzle'
  | 'overcast'
  | 'mist'
  | 'haze'
  | 'smog'
  | 'smoke'
  | 'dust'
  | 'sleet'
  | 'showers'
  | 'freezing-rain'
  | 'flurries'
  | 'blizzard';
export type Intensity = 'light' | 'medium' | 'heavy';
export type TimeOfDay = 'day' | 'night';
export type Fidelity = 'subtle' | 'rich';
export type CelestialEvent = 'none' | 'sunrise' | 'sunset' | 'moonrise' | 'moonset';
export type MoonPhase =
  | 'new'
  | 'waxing-crescent'
  | 'first-quarter'
  | 'waxing-gibbous'
  | 'full'
  | 'waning-gibbous'
  | 'last-quarter'
  | 'waning-crescent';

export interface WeatherConfig {
  condition: Condition;
  intensity?: Intensity;
  time?: TimeOfDay;
  transitionMs?: number;
  fidelity?: Fidelity;
  moonPhase?: MoonPhase;
  celestialEvent?: CelestialEvent;
  celestialProgress?: number;
}

export type WeatherInputPrecipitationType = 'none' | 'rain' | 'drizzle' | 'snow' | 'sleet' | 'hail' | 'freezing-rain';
export type WeatherInputVisibility = 'clear' | 'haze' | 'mist' | 'fog' | 'smog' | 'smoke' | 'dust' | number;
export type WeatherInputPhenomenon =
  | Condition
  | 'thunderstorm'
  | 'partly-cloudy'
  | 'mostly-cloudy';

export interface WeatherInput {
  phenomenon?: WeatherInputPhenomenon | string;
  cloudCover?: number;
  precipitationType?: WeatherInputPrecipitationType | string;
  precipitationIntensity?: Intensity | number;
  windSpeed?: number;
  visibility?: WeatherInputVisibility;
  thunderstorm?: boolean;
  time?: TimeOfDay;
}

export interface ResolvedConfig {
  condition: Condition;
  intensity: Intensity;
  time: TimeOfDay;
  transitionMs: number;
  fidelity: Fidelity;
  moonPhase: MoonPhase;
  celestialEvent: CelestialEvent;
  celestialProgress: number;
}

export interface RGBColor {
  r: number;
  g: number;
  b: number;
}

export type ParticleKind = 'primary' | 'splash' | 'leaf' | 'droplet';

export interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  alpha: number;
  size: number;
  length: number;
  phase: number;
  depth: number;     // 0 = far (small/slow/faint), 1 = near (large/fast/strong)
  bounces: number;   // hail bounce count; ignored by other kinds
  kind: ParticleKind;
  active: boolean;
}

export interface CloudLobe {
  dx: number;
  dy: number;
  r: number;
  alpha?: number;  // undefined = 1.0; back-lobes set ~0.45
}

export interface CloudBlob {
  x: number;
  y: number;
  width: number;
  height: number;
  alpha: number;
  speed: number;
  layer: 0 | 1 | 2;
  lobes: CloudLobe[];
  sprite?: OffscreenCanvas;
  spriteCx?: number;
  spriteCy?: number;
  spriteKey?: string;
}

export const VALID_CONDITIONS: Condition[] = [
  'clear',
  'cloudy',
  'rain',
  'snow',
  'storm',
  'fog',
  'wind',
  'hail',
  'drizzle',
  'overcast',
  'mist',
  'haze',
  'smog',
  'smoke',
  'dust',
  'sleet',
  'showers',
  'freezing-rain',
  'flurries',
  'blizzard',
];
export const VALID_CELESTIAL_EVENTS: CelestialEvent[] = ['none', 'sunrise', 'sunset', 'moonrise', 'moonset'];
export const VALID_MOON_PHASES: MoonPhase[] = [
  'new',
  'waxing-crescent',
  'first-quarter',
  'waxing-gibbous',
  'full',
  'waning-gibbous',
  'last-quarter',
  'waning-crescent',
];

/** Whether the sky is clear enough for a sun/moon rise/set event to read at all. */
function skyAllowsCelestialEvent(config: Pick<ResolvedConfig, 'condition' | 'intensity'>): boolean {
  return config.condition === 'clear'
    || config.condition === 'wind'
    || (config.condition === 'cloudy' && config.intensity !== 'heavy')
    || (config.condition === 'haze' && config.intensity !== 'heavy');
}

export function isCelestialEventVisible(
  event: CelestialEvent,
  config: Pick<ResolvedConfig, 'condition' | 'intensity' | 'time'>,
): boolean {
  if (event === 'none') return true;

  // Sun events read only by day, moon events only by night; the sky gate is shared.
  const requiredTime = event === 'sunrise' || event === 'sunset' ? 'day' : 'night';
  return skyAllowsCelestialEvent(config) && config.time === requiredTime;
}

export function isFidelityEffective(config: Pick<ResolvedConfig, 'condition'>): boolean {
  return config.condition !== 'cloudy'
    && config.condition !== 'fog'
    && config.condition !== 'overcast'
    && config.condition !== 'mist'
    && config.condition !== 'haze'
    && config.condition !== 'smog'
    && config.condition !== 'smoke'
    && config.condition !== 'dust';
}

export function resolveConfig(config: WeatherConfig): ResolvedConfig {
  const condition: Condition = VALID_CONDITIONS.includes(config.condition as Condition)
    ? config.condition
    : 'clear';
  const moonPhase: MoonPhase = VALID_MOON_PHASES.includes(config.moonPhase as MoonPhase)
    ? config.moonPhase as MoonPhase
    : 'full';
  const intensity: Intensity = isValidIntensity(config.intensity) ? config.intensity : 'medium';
  const time: TimeOfDay = isValidTimeOfDay(config.time) ? config.time : 'day';
  const requestedCelestialEvent: CelestialEvent = VALID_CELESTIAL_EVENTS.includes(config.celestialEvent as CelestialEvent)
    ? config.celestialEvent as CelestialEvent
    : 'none';
  const celestialEvent = isCelestialEventVisible(requestedCelestialEvent, { condition, intensity, time })
    ? requestedCelestialEvent
    : 'none';
  const rawCelestialProgress = config.celestialProgress ?? 0.5;
  const celestialProgress = Math.min(1, Math.max(0, Number.isFinite(rawCelestialProgress) ? rawCelestialProgress : 0.5));
  return {
    condition,
    intensity,
    time,
    transitionMs: config.transitionMs ?? 1200,
    fidelity: isValidFidelity(config.fidelity) ? config.fidelity : 'subtle',
    moonPhase,
    celestialEvent,
    celestialProgress,
  };
}

function isValidIntensity(value: unknown): value is Intensity {
  return value === 'light' || value === 'medium' || value === 'heavy';
}

function isValidTimeOfDay(value: unknown): value is TimeOfDay {
  return value === 'day' || value === 'night';
}

function isValidFidelity(value: unknown): value is Fidelity {
  return value === 'subtle' || value === 'rich';
}

function normalizeIntensity(value: WeatherInput['precipitationIntensity'], fallback: Intensity = 'medium'): Intensity {
  if (isValidIntensity(value)) return value;
  if (typeof value !== 'number' || !Number.isFinite(value)) return fallback;
  if (value <= 0.33) return 'light';
  if (value <= 0.66) return 'medium';
  return 'heavy';
}

function cloudCoverIntensity(cloudCover: number): Intensity {
  if (cloudCover < 35) return 'light';
  if (cloudCover < 85) return 'medium';
  return 'heavy';
}

function windIntensity(windSpeed: number): Intensity {
  if (windSpeed < 20) return 'light';
  if (windSpeed < 45) return 'medium';
  return 'heavy';
}

function conditionFromPhenomenon(phenomenon: string | undefined): Condition | null {
  if (!phenomenon) return null;
  if (VALID_CONDITIONS.includes(phenomenon as Condition)) return phenomenon as Condition;
  if (phenomenon === 'thunderstorm') return 'storm';
  if (phenomenon === 'partly-cloudy' || phenomenon === 'mostly-cloudy') return 'cloudy';
  return null;
}

function intensityFromPhenomenon(phenomenon: string | undefined): Intensity | null {
  if (phenomenon === 'partly-cloudy') return 'light';
  if (phenomenon === 'mostly-cloudy') return 'medium';
  return null;
}

function conditionFromVisibility(visibility: WeatherInputVisibility | undefined): { condition: Condition; intensity: Intensity } | null {
  if (visibility === undefined || visibility === 'clear') return null;
  if (visibility === 'fog') return { condition: 'fog', intensity: 'medium' };
  if (visibility === 'mist') return { condition: 'mist', intensity: 'medium' };
  if (visibility === 'haze') return { condition: 'haze', intensity: 'medium' };
  if (visibility === 'smog') return { condition: 'smog', intensity: 'medium' };
  if (visibility === 'smoke') return { condition: 'smoke', intensity: 'medium' };
  if (visibility === 'dust') return { condition: 'dust', intensity: 'medium' };
  if (typeof visibility !== 'number' || !Number.isFinite(visibility)) return null;
  if (visibility < 1000) return { condition: 'fog', intensity: 'heavy' };
  if (visibility < 3000) return { condition: 'fog', intensity: 'medium' };
  if (visibility < 6000) return { condition: 'mist', intensity: 'medium' };
  if (visibility < 10000) return { condition: 'haze', intensity: 'light' };
  return null;
}

export function normalizeWeatherInput(input: WeatherInput): WeatherConfig {
  if (input.thunderstorm) {
    return {
      condition: 'storm',
      intensity: normalizeIntensity(input.precipitationIntensity),
      ...(input.time ? { time: input.time } : {}),
    };
  }

  if (input.precipitationType && input.precipitationType !== 'none') {
    const precipitationConditions: Condition[] = ['rain', 'drizzle', 'snow', 'sleet', 'hail', 'freezing-rain'];
    const condition = precipitationConditions.includes(input.precipitationType as Condition)
      ? input.precipitationType as Condition
      : null;
    if (condition) {
      return {
        condition,
        intensity: normalizeIntensity(input.precipitationIntensity),
        ...(input.time ? { time: input.time } : {}),
      };
    }
  }

  const visibility = conditionFromVisibility(input.visibility);
  if (visibility) {
    return {
      condition: visibility.condition,
      intensity: visibility.intensity,
      ...(input.time ? { time: input.time } : {}),
    };
  }

  if (typeof input.cloudCover === 'number' && Number.isFinite(input.cloudCover)) {
    const cloudCover = Math.max(0, Math.min(100, input.cloudCover));
    if (cloudCover >= 90) {
      return { condition: 'overcast', intensity: 'heavy', ...(input.time ? { time: input.time } : {}) };
    }
    if (cloudCover >= 15) {
      return { condition: 'cloudy', intensity: cloudCoverIntensity(cloudCover), ...(input.time ? { time: input.time } : {}) };
    }
  }

  const phenomenon = conditionFromPhenomenon(input.phenomenon);
  if (phenomenon && phenomenon !== 'clear' && phenomenon !== 'cloudy') {
    return {
      condition: phenomenon,
      intensity: intensityFromPhenomenon(input.phenomenon) ?? normalizeIntensity(input.precipitationIntensity),
      ...(input.time ? { time: input.time } : {}),
    };
  }

  if (typeof input.windSpeed === 'number' && Number.isFinite(input.windSpeed) && input.windSpeed >= 12) {
    return {
      condition: 'wind',
      intensity: windIntensity(input.windSpeed),
      ...(input.time ? { time: input.time } : {}),
    };
  }

  if (phenomenon) {
    return {
      condition: phenomenon,
      intensity: intensityFromPhenomenon(input.phenomenon)
        ?? (typeof input.cloudCover === 'number' ? cloudCoverIntensity(input.cloudCover) : normalizeIntensity(input.precipitationIntensity)),
      ...(input.time ? { time: input.time } : {}),
    };
  }

  return {
    condition: 'clear',
    intensity: typeof input.cloudCover === 'number' ? cloudCoverIntensity(Math.max(0, Math.min(100, input.cloudCover))) : 'medium',
    ...(input.time ? { time: input.time } : {}),
  };
}
