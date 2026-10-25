// One seedable RNG for the whole engine. Tests seed it for reproducible frames;
// production leaves it on Math.random.
let current: () => number = Math.random;

export function random(): number {
  return current();
}

export function seedRng(seed: number): void {
  let s = seed >>> 0;
  current = () => {
    s = (s + 0x6d2b79f5) | 0;
    let t = Math.imul(s ^ (s >>> 15), 1 | s);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function resetRng(): void {
  current = Math.random;
}
