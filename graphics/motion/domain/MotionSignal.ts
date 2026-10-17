/** Domain projection of the versioned transport DTO; no transport dependency. */
export interface MotionSignal {
  readonly sourceSessionId: string;
  readonly timestampSeconds: number;
  readonly bpm: number | null;
  readonly slowCycleSeconds: number;
  readonly phase: number;
  readonly energy: number;
  readonly intensity: number;
  readonly confidence: number;
  readonly mode: 'autonomous' | 'synced';
}

/** Проверка опубликованной проекции без зависимости от транспорта или renderer. */
export function validateMotionSignal(value: unknown): asserts value is MotionSignal {
  const valid = (condition: unknown) => { if (!condition) throw new Error('INVALID_INPUT: motion'); };
  valid(typeof value === 'object' && value !== null && !Array.isArray(value));
  const signal = value as Record<string, unknown>;
  const range = (candidate: unknown, min: number, max: number): candidate is number =>
    typeof candidate === 'number' && Number.isFinite(candidate) && candidate >= min && candidate <= max;
  valid(typeof signal.sourceSessionId === 'string' && /^[A-Za-z0-9._:-]{1,128}$/.test(signal.sourceSessionId));
  valid(range(signal.timestampSeconds, 0, Number.MAX_SAFE_INTEGER));
  valid(signal.bpm === null || range(signal.bpm, 40, 240));
  valid(range(signal.slowCycleSeconds, 1, 120) && range(signal.phase, 0, 1) && signal.phase < 1);
  for (const key of ['energy', 'intensity', 'confidence']) valid(range(signal[key], 0, 1));
  valid(signal.mode === 'autonomous' || signal.mode === 'synced');
  valid(signal.mode !== 'synced' || (signal.bpm !== null && signal.sourceSessionId !== 'none' && (signal.confidence as number) >= 0.65));
  valid(signal.sourceSessionId !== 'none' || (signal.mode === 'autonomous' && signal.bpm === null));
}
