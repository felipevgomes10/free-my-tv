import type { WeatherConfig } from './types';
import { resolveConfig } from './types';
import { createEngineState, startTransition, tickEngine, renderEngine, resizeEngine } from './engine';
import type { EngineState } from './engine';

export { isCelestialEventVisible, isFidelityEffective, normalizeWeatherInput } from './types';
export type {
  WeatherConfig,
  WeatherInput,
  WeatherInputPhenomenon,
  WeatherInputPrecipitationType,
  WeatherInputVisibility,
  Condition,
  Intensity,
  TimeOfDay,
  Fidelity,
  MoonPhase,
  CelestialEvent,
} from './types';

export class WeatherScene {
  private destroyed = false;
  private skyEl: HTMLDivElement;
  private canvas: HTMLCanvasElement;
  private ctx: CanvasRenderingContext2D | null;
  private rafId: number | null = null;
  private lastTimestamp: number | null = null;
  private engineState: EngineState | null = null;
  private resizeObserver: ResizeObserver;
  private manual: boolean;
  /** Logical (CSS-pixel) size the engine draws against — decoupled from the DPR-scaled backing store. */
  private cssWidth = 0;
  private cssHeight = 0;
  private resizeScheduled = false;
  private motionQuery: MediaQueryList | null = null;
  private reducedMotion = false;
  private handleMotionChange = (): void => {
    this.reducedMotion = this.motionQuery?.matches ?? false;
    if (this.manual) return;
    if (this.reducedMotion) {
      this.stopLoop();
      this.renderStatic();
    } else if (this.engineState && this.rafId == null) {
      this.startLoop();
    }
  };

  constructor(private container: HTMLElement, opts: { manual?: boolean } = {}) {
    if (!container) throw new Error('WeatherScene: container element is required');
    this.manual = opts.manual ?? false;

    const pos = getComputedStyle(container).position;
    if (pos === 'static') container.style.position = 'relative';
    container.style.overflow = 'hidden';

    this.skyEl = document.createElement('div');
    this.skyEl.style.cssText = 'position:absolute;inset:0;';

    this.canvas = document.createElement('canvas');
    this.canvas.style.cssText = 'position:absolute;inset:0;';

    container.appendChild(this.skyEl);
    container.appendChild(this.canvas);

    this.ctx = this.canvas.getContext('2d');
    this.resizeCanvas();

    this.resizeObserver = new ResizeObserver(() => this.scheduleResize());
    this.resizeObserver.observe(container);

    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      this.motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.reducedMotion = this.motionQuery.matches;
      this.motionQuery.addEventListener('change', this.handleMotionChange);
    }
  }

  private resizeCanvas(): void {
    const { offsetWidth: w, offsetHeight: h } = this.container;
    const dpr = (typeof window !== 'undefined' && window.devicePixelRatio) || 1;
    this.cssWidth = w;
    this.cssHeight = h;
    // Render at device resolution for crisp output on retina/mobile, then let
    // CSS scale the element back down to its logical size.
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    this.canvas.style.width = `${w}px`;
    this.canvas.style.height = `${h}px`;
    // Resizing the backing store resets the context, so (re)apply the DPR
    // transform — all draw code works in logical CSS pixels.
    if (this.ctx) this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    if (this.engineState) {
      resizeEngine(this.engineState, w, h);
      // The loop repaints on its own; in static mode we must repaint here
      // because clearing the resized canvas otherwise leaves it blank.
      if (!this.manual && this.rafId == null) this.renderStatic();
    }
  }

  /** Coalesce bursts of ResizeObserver callbacks into one repaint per frame. */
  private scheduleResize(): void {
    if (this.resizeScheduled) return;
    this.resizeScheduled = true;
    requestAnimationFrame(() => {
      this.resizeScheduled = false;
      if (!this.destroyed) this.resizeCanvas();
    });
  }

  set(config: WeatherConfig): void {
    if (this.destroyed) return;
    const resolved = resolveConfig(config);
    const w = this.cssWidth;
    const h = this.cssHeight;

    if (!this.engineState || this.reducedMotion) {
      // Reduced motion: jump straight to the new state instead of animating a
      // cross-fade, since no loop runs to drive the transition.
      this.engineState = createEngineState(resolved, w, h);
    } else {
      this.engineState = startTransition(this.engineState, resolved, w, h);
    }

    if (this.manual) return;
    if (this.reducedMotion) this.renderStatic();
    else if (this.rafId == null) this.startLoop();
  }

  /** Deterministically tick + render N frames at a fixed delta (test hook). */
  advance(frames: number, dtSeconds = 1 / 60): void {
    if (this.destroyed || !this.engineState || !this.ctx) return;
    const w = this.cssWidth;
    const h = this.cssHeight;
    for (let i = 0; i < frames; i++) {
      this.engineState = tickEngine(this.engineState, dtSeconds, w, h);
    }
    renderEngine(this.engineState, this.skyEl, this.ctx, w, h);
  }

  destroy(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    this.stopLoop();
    this.resizeObserver.disconnect();
    this.motionQuery?.removeEventListener('change', this.handleMotionChange);
    this.skyEl.remove();
    this.canvas.remove();
    this.engineState = null;
  }

  private renderStatic(): void {
    if (this.engineState && this.ctx) {
      renderEngine(this.engineState, this.skyEl, this.ctx, this.cssWidth, this.cssHeight);
    }
  }

  private startLoop(): void {
    const tick = (timestamp: number) => {
      if (this.destroyed) return;
      const delta = this.lastTimestamp != null
        ? Math.min((timestamp - this.lastTimestamp) / 1000, 0.1)
        : 0;
      this.lastTimestamp = timestamp;

      if (this.engineState && this.ctx) {
        const w = this.cssWidth;
        const h = this.cssHeight;
        this.engineState = tickEngine(this.engineState, delta, w, h);
        renderEngine(this.engineState, this.skyEl, this.ctx, w, h);
      }

      this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  private stopLoop(): void {
    if (this.rafId != null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    this.lastTimestamp = null;
  }
}
