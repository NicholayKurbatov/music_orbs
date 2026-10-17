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
