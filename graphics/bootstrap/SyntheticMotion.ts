import type { MotionSignal } from '../motion/domain/MotionSignal';

/** Воспроизводимый fake источник для harness, без заявления анализа PCM. */
export class SyntheticMotion {
  private elapsed = 0;
  private phase = 0;
  private frequency = 1 / 24;
  private session = 1;
  bpm = 90;
  enabled = false;
  paused = false;
  switchSource(): void { this.session++; }
  advance(delta: number, syncEnabled = true): MotionSignal {
    const dt = this.paused ? 0 : Math.min(Math.max(delta, 0), 0.1);
    const synced = this.enabled && syncEnabled;
    const target = synced ? this.bpm / (60 * 32) : 1 / 24;
    const change = Math.max(-0.02 * dt, Math.min(0.02 * dt, (target - this.frequency) * (1 - Math.exp(-dt / 3))));
    const nextFrequency = this.frequency + change;
    this.elapsed += dt; this.phase = (this.phase + dt * (this.frequency + nextFrequency) / 2) % 1;
    this.frequency = nextFrequency;
    return { sourceSessionId: `synthetic-${this.session}`, timestampSeconds: this.elapsed, bpm: synced ? this.bpm : null,
      slowCycleSeconds: 1 / this.frequency, phase: this.phase, energy: synced ? 0.35 + Math.sin(this.elapsed * 0.7) * 0.12 : 0,
      intensity: synced ? 0.3 : 0, confidence: synced ? 0.9 : 0, mode: synced ? 'synced' : 'autonomous' };
  }
}
