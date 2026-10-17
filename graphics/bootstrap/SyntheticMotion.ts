import type { MotionSignal } from '../motion/domain/MotionSignal';

/** Воспроизводимый fake источник для harness, без заявления анализа PCM. */
export class SyntheticMotion {
  private elapsed = 0;
  private phase = 0;
  private period = 24;
  private session = 1;
  bpm = 90;
  enabled = false;
  paused = false;
  switchSource(): void { this.session++; }
  advance(delta: number): MotionSignal {
    const dt = this.paused ? 0 : Math.min(Math.max(delta, 0), 0.1);
    const target = this.enabled ? 60 / this.bpm * 32 : 24;
    this.period += (target - this.period) * (1 - Math.exp(-dt / 4));
    this.elapsed += dt; this.phase = (this.phase + dt / this.period) % 1;
    return { sourceSessionId: `synthetic-${this.session}`, timestampSeconds: this.elapsed, bpm: this.enabled ? this.bpm : null,
      slowCycleSeconds: this.period, phase: this.phase, energy: this.enabled ? 0.35 + Math.sin(this.elapsed * 0.7) * 0.12 : 0,
      intensity: this.enabled ? 0.3 : 0, confidence: this.enabled ? 0.9 : 0, mode: this.enabled ? 'synced' : 'autonomous' };
  }
}
