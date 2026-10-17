import type { MotionSignal } from './MotionSignal';

/** Локальная непрерывная фаза: задержанный пакет меняет скорость, а не позицию цикла. */
export class VisualPhase {
  private phase = 0;
  private initialized = false;
  private latest: MotionSignal = { sourceSessionId: 'none', timestampSeconds: 0, bpm: null, slowCycleSeconds: 24, phase: 0, energy: 0, intensity: 0, confidence: 0, mode: 'autonomous' };

  accept(signal: MotionSignal): void {
    if (!Number.isFinite(signal.phase) || signal.phase < 0 || signal.phase >= 1 || !Number.isFinite(signal.slowCycleSeconds) || signal.slowCycleSeconds <= 0) throw new Error('INVALID_INPUT: visual motion');
    if (!this.initialized) { this.phase = signal.phase; this.initialized = true; }
    this.latest = { ...signal };
  }

  setSource(sourceSessionId: string): void {
    if (!sourceSessionId) throw new Error('INVALID_INPUT: visual source');
    this.latest = { ...this.latest, sourceSessionId, timestampSeconds: 0, bpm: null, phase: this.phase, energy: 0, intensity: 0, confidence: 0, mode: 'autonomous' };
  }

  advance(deltaSeconds: number): MotionSignal {
    if (!Number.isFinite(deltaSeconds) || deltaSeconds < 0) throw new Error('INVALID_INPUT: visual delta');
    this.phase = (this.phase + Math.min(deltaSeconds, 0.1) / this.latest.slowCycleSeconds) % 1;
    return { ...this.latest, phase: this.phase };
  }
}
