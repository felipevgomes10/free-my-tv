import type { ResolvedConfig } from './types';
import { easeInOutCubic, clamp } from './math';

export interface Transition {
  from: ResolvedConfig;
  to: ResolvedConfig;
  elapsed: number;
}

export function createTransition(from: ResolvedConfig, to: ResolvedConfig): Transition {
  return { from, to, elapsed: 0 };
}

export function updateTransition(t: Transition, delta: number): Transition {
  return {
    ...t,
    elapsed: Math.min(t.elapsed + delta * 1000, t.to.transitionMs),
  };
}

export function getProgress(t: Transition): number {
  const raw = clamp(t.elapsed / t.to.transitionMs, 0, 1);
  return easeInOutCubic(raw);
}

export function isComplete(t: Transition): boolean {
  return t.elapsed >= t.to.transitionMs;
}
