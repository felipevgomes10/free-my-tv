import type { ResolvedConfig, CloudBlob } from './types';
import { applySky, lerpSky } from './sky';
import { initClouds, updateClouds, drawClouds } from './clouds';
import { drawAtmosphere, drawCelestial, updateAtmosphere, createAtmosphereState } from './atmosphere';
import type { AtmosphereState } from './atmosphere';
import { ParticleSystem } from './particles';
import { createTransition, updateTransition, getProgress, isComplete } from './transition';
import type { Transition } from './transition';

export interface EngineState {
  current: ResolvedConfig;
  currentClouds: CloudBlob[];
  currentPS: ParticleSystem;
  currentAtmo: AtmosphereState;
  transition: Transition | null;
  transClouds: CloudBlob[] | null;
  transPS: ParticleSystem | null;
  transAtmo: AtmosphereState | null;
}

export function createEngineState(config: ResolvedConfig, width: number, height: number): EngineState {
  const ps = new ParticleSystem();
  ps.init(config, width, height);
  return {
    current: config,
    currentClouds: initClouds(config, width, height),
    currentPS: ps,
    currentAtmo: createAtmosphereState(),
    transition: null,
    transClouds: null,
    transPS: null,
    transAtmo: null,
  };
}

export function startTransition(state: EngineState, to: ResolvedConfig, width: number, height: number): EngineState {
  const from = state.current;
  const newPS = new ParticleSystem();
  newPS.init(to, width, height);
  return {
    ...state,
    transition: createTransition(from, to),
    transClouds: initClouds(to, width, height),
    transPS: newPS,
    transAtmo: createAtmosphereState(),
  };
}

/** Propagate a new canvas size into the live particle systems. */
export function resizeEngine(state: EngineState, width: number, height: number): void {
  state.currentPS.resize(width, height);
  if (state.transPS) state.transPS.resize(width, height);
}

export function tickEngine(
  state: EngineState,
  delta: number,
  width: number,
  _height: number,
): EngineState {
  // Mutate in place — the state shape is fixed, so a fresh object every frame
  // is pure garbage. The subsystems below already mutate their own objects.
  updateAtmosphere(state.currentAtmo, state.current, delta);
  state.currentPS.update(delta);
  updateClouds(state.currentClouds, delta, width);

  if (state.transition) {
    const t = updateTransition(state.transition, delta);
    state.transition = t;

    if (state.transAtmo) updateAtmosphere(state.transAtmo, t.to, delta);
    if (state.transPS) state.transPS.update(delta);
    if (state.transClouds) updateClouds(state.transClouds, delta, width);

    if (isComplete(t)) {
      state.current = t.to;
      state.currentClouds = state.transClouds!;
      state.currentPS = state.transPS!;
      state.currentAtmo = state.transAtmo!;
      state.transition = null;
      state.transClouds = null;
      state.transPS = null;
      state.transAtmo = null;
    }
  }

  return state;
}

export function renderEngine(
  state: EngineState,
  skyEl: HTMLElement,
  ctx: CanvasRenderingContext2D,
  width: number,
  height: number,
): void {
  ctx.clearRect(0, 0, width, height);

  if (state.transition) {
    const progress = getProgress(state.transition);
    const from = state.transition.from;
    const to = state.transition.to;
    const fromAlpha = 1 - progress;

    lerpSky(skyEl, from, to, progress);

    drawCelestial(ctx, from, state.currentAtmo, fromAlpha, width, height);
    if (state.transAtmo) drawCelestial(ctx, to, state.transAtmo, progress, width, height);

    drawClouds(ctx, state.currentClouds, from, fromAlpha);
    drawAtmosphere(ctx, from, state.currentAtmo, fromAlpha, width, height);
    state.currentPS.draw(ctx, fromAlpha, from);

    if (state.transClouds) drawClouds(ctx, state.transClouds, to, progress);
    if (state.transAtmo) drawAtmosphere(ctx, to, state.transAtmo, progress, width, height);
    if (state.transPS) state.transPS.draw(ctx, progress, to);
  } else {
    applySky(skyEl, state.current, state.currentAtmo.lightningFlash);
    drawCelestial(ctx, state.current, state.currentAtmo, 1, width, height);
    drawClouds(ctx, state.currentClouds, state.current, 1);
    drawAtmosphere(ctx, state.current, state.currentAtmo, 1, width, height);
    state.currentPS.draw(ctx, 1, state.current);
  }
}
