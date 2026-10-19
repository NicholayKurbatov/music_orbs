import type { MotionSignal } from './MotionSignal';

/** Скорость временной анимации; форма и фаза музыкального clock не меняются. */
export class MusicMotionResponse {
  private rate = 1;

  read(): number { return this.rate; }

  advance(deltaSeconds: number, signal: MotionSignal, enabled: boolean): number {
    if (!Number.isFinite(deltaSeconds) || deltaSeconds < 0) throw new Error('INVALID_INPUT: animation delta');
    const dt = Math.min(deltaSeconds, 0.1);
    const audible = enabled && signal.mode === 'synced' && signal.bpm !== null &&
      signal.confidence >= 0.65 && signal.energy > 0.01;
    // Один оборот на восемь долей при speed=1; интенсивность атак добавляет до 75%.
    const target = audible ? (2 * Math.PI / 0.12) * signal.bpm! / (60 * 8) * (1 + 0.75 * signal.intensity) : 1;
    const before = this.rate;
    const tau = target > before ? 0.15 : 0.8;
    const change = (target - before) * (1 - Math.exp(-dt / tau));
    this.rate += Math.max(-20 * dt, Math.min(20 * dt, change));
    return dt * (before + this.rate) / 2;
  }
}
