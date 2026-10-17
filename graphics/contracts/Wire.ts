import type { MotionSignal } from '../motion/domain/MotionSignal';
import type { SceneCommand } from '../sculpture/application/SceneApplication';
import type { SceneSnapshot } from '../sculpture/domain/Models';
import { inRange, isRecord, ProfileLimits, requireValid, validCounter, validateSceneSnapshot, validateSettings } from '../sculpture/domain/ProfileLimits';

export interface SessionSnapshot extends SceneSnapshot { readonly schemaVersion: 2; readonly revision: number; }
export type WireType = 'ready' | 'command' | 'ack' | 'snapshot' | 'motion' | 'error';
export interface Envelope {
  readonly protocolVersion: 1; readonly bridgeSessionId: string; readonly sequence: number;
  readonly type: WireType; readonly requestId?: string; readonly payload: unknown;
}
export const NO_SOURCE = 'none';
export function validId(value: unknown): value is string {
  return typeof value === 'string' && /^[A-Za-z0-9._:-]{1,128}$/.test(value);
}
export function validateMotion(value: unknown): asserts value is MotionSignal {
  requireValid(isRecord(value) && validId(value.sourceSessionId));
  requireValid(inRange(value.timestampSeconds, 0, Number.MAX_SAFE_INTEGER));
  requireValid(value.bpm === null || inRange(value.bpm, 40, 240));
  requireValid(inRange(value.slowCycleSeconds, 1, 120) && inRange(value.phase, 0, 1) && value.phase < 1);
  for (const name of ['energy', 'intensity', 'confidence']) requireValid(inRange(value[name], 0, 1));
  requireValid(value.mode === 'autonomous' || value.mode === 'synced');
  requireValid(value.mode !== 'synced' || (value.bpm !== null && value.sourceSessionId !== NO_SOURCE && (value.confidence as number) >= 0.65));
  requireValid(value.sourceSessionId !== NO_SOURCE || (value.mode === 'autonomous' && value.bpm === null));
}
export function validateCommand(value: unknown): asserts value is SceneCommand {
  requireValid(isRecord(value));
  switch (value.type) {
    case 'configure': validateSettings(value.settings); break;
    case 'restore': validateSceneSnapshot(value.snapshot); break;
    case 'reset': case 'snapshot': case 'pause': case 'resume': break;
    default: throw new Error('INVALID_INPUT');
  }
}
export function validateSession(value: unknown): asserts value is SessionSnapshot {
  requireValid(isRecord(value) && value.schemaVersion === 2 && validCounter(value.revision), 'SNAPSHOT_INVALID');
  validateSceneSnapshot(value);
}
/** v1 имела только colorHex; миграция не меняет seed, controls или revision. */
export function migrateSession(value: unknown): SessionSnapshot {
  requireValid(isRecord(value), 'SNAPSHOT_INVALID');
  const migrated = structuredClone(value);
  if (migrated.schemaVersion === 1) {
    requireValid(isRecord(migrated.settings), 'SNAPSHOT_INVALID');
    const color = migrated.settings.colorHex;
    migrated.settings.paletteId = 'custom';
    migrated.settings.paletteStops = [{ offset: 0, colorHex: color }, { offset: 1, colorHex: color }];
    migrated.schemaVersion = 2;
  }
  validateSession(migrated); return migrated;
}
export function validateEnvelope(value: unknown): asserts value is Envelope {
  requireValid(isRecord(value));
  requireValid(value.protocolVersion === 1, 'PROTOCOL_MISMATCH');
  requireValid(validId(value.bridgeSessionId) && validCounter(value.sequence));
  if (value.requestId !== undefined) requireValid(validId(value.requestId));
  switch (value.type) {
    case 'ready':
      requireValid(isRecord(value.payload) && inRange(value.payload.runtimeTimestampSeconds, 0, Number.MAX_SAFE_INTEGER));
      if (value.payload.browserTimestampSeconds !== undefined) requireValid(inRange(value.payload.browserTimestampSeconds, 0, Number.MAX_SAFE_INTEGER));
      if (value.payload.sourceSessionId !== undefined) requireValid(validId(value.payload.sourceSessionId));
      break;
    case 'command': requireValid(validId(value.requestId)); validateCommand(value.payload); break;
    case 'snapshot': requireValid(validId(value.requestId)); validateSceneSnapshot(value.payload); break;
    case 'ack': requireValid(validId(value.requestId) && isRecord(value.payload)); break;
    case 'motion': validateMotion(value.payload); break;
    case 'error':
      requireValid(isRecord(value.payload) && typeof value.payload.code === 'string' && /^[A-Z_]{1,40}$/.test(value.payload.code));
      requireValid(typeof value.payload.message === 'string' && value.payload.message.length <= 256); break;
    default: throw new Error('INVALID_INPUT');
  }
}
export function decodeEnvelope(json: string): Envelope {
  requireValid(new TextEncoder().encode(json).byteLength <= ProfileLimits.maxPayloadBytes, 'INVALID_INPUT');
  const value: unknown = JSON.parse(json); validateEnvelope(value); return value;
}
export function encodeEnvelope(value: Envelope): string {
  validateEnvelope(value); const json = JSON.stringify(value);
  requireValid(new TextEncoder().encode(json).byteLength <= ProfileLimits.maxPayloadBytes); return json;
}
