import type { DeformationSnapshot, PaletteId, PaletteStop, SceneSettings, SceneSnapshot } from './Models';

/** Начальные бюджеты прототипа, а не измеренные возможности устройства. */
export const ProfileLimits = Object.freeze({
  particleCounts: Object.freeze({ low: 4000, medium: 10000, high: 20000 }),
  maxParticles: 20000, maxControls: 64, maxCoordinate: 4,
  maxControlDelta: 1, maxStrokeDelta: 0.2, minRadius: 0.02, maxRadius: 2,
  maxPayloadBytes: 262144, maxJsonDepth: 32, maxPendingRequests: 16, requestDeadlineMs: 3000,
  maxMotionHz: 30, maxSpeed: 2, maxAmplitude: 1, maxTouchSensitivity: 3,
  maxDeltaSeconds: 0.1, generatorVersion: 1, schemaVersion: 2,
});
const stops = (...colors: string[]): readonly PaletteStop[] => Object.freeze(
  colors.map((colorHex, index) => Object.freeze({ offset: index / (colors.length - 1), colorHex })),
);
export const PALETTES: Readonly<Record<PaletteId, readonly PaletteStop[]>> = Object.freeze({
  white: stops('#FFFFFF', '#FFFFFF'), pink: stops('#FF7BBF', '#FFD6EC'),
  lilac: stops('#9875FF', '#E1C7FF'),
  spectrum: stops('#FF306E', '#FFBE32', '#33E8CA', '#6585FF', '#D855FF'),
  custom: stops('#FFFFFF', '#FFFFFF'),
});
export const DEFAULT_SETTINGS: SceneSettings = Object.freeze({
  shapeId: 'orb', materialId: 'dots', colorHex: '#FFFFFF', paletteId: 'white',
  paletteStops: PALETTES.white, speed: 0.5, amplitude: 0.3, touchSensitivity: 1,
  musicSync: true, quality: 'medium', targetFps: 60,
});
export function requireValid(condition: unknown, code = 'INVALID_INPUT'): asserts condition {
  if (!condition) throw new Error(code);
}
export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
export function inRange(value: unknown, min: number, max: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max;
}
export function validCounter(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
}
export function validateSettings(value: unknown): asserts value is SceneSettings {
  requireValid(isRecord(value));
  requireValid(typeof value.shapeId === 'string' && ['orb', 'ribbon', 'cloud', 'pixel-cloud'].includes(value.shapeId));
  requireValid(typeof value.materialId === 'string' && ['dots', 'pixels', 'soft'].includes(value.materialId));
  requireValid(typeof value.colorHex === 'string' && /^#[0-9A-Fa-f]{6}$/.test(value.colorHex));
  requireValid(typeof value.paletteId === 'string' && ['white', 'pink', 'lilac', 'spectrum', 'custom'].includes(value.paletteId));
  requireValid(Array.isArray(value.paletteStops) && value.paletteStops.length >= 2 && value.paletteStops.length <= 8);
  let previous = -1;
  for (const stop of value.paletteStops) {
    requireValid(isRecord(stop) && inRange(stop.offset, 0, 1) && stop.offset > previous);
    requireValid(typeof stop.colorHex === 'string' && /^#[0-9A-Fa-f]{6}$/.test(stop.colorHex));
    previous = stop.offset;
  }
  requireValid(value.paletteStops[0].offset === 0 && previous === 1);
  requireValid(inRange(value.speed, 0, ProfileLimits.maxSpeed));
  requireValid(inRange(value.amplitude, 0, ProfileLimits.maxAmplitude));
  requireValid(inRange(value.touchSensitivity, 0, ProfileLimits.maxTouchSensitivity));
  requireValid(typeof value.musicSync === 'boolean');
  requireValid(value.fullSync === undefined || typeof value.fullSync === 'boolean');
  requireValid(typeof value.quality === 'string' && ['low', 'medium', 'high'].includes(value.quality));
  requireValid(value.targetFps === 30 || value.targetFps === 60);
}
export function validateShapeSnapshot(value: unknown): asserts value is DeformationSnapshot {
  requireValid(isRecord(value), 'SNAPSHOT_INVALID');
  requireValid(typeof value.shapeId === 'string' && ['orb', 'ribbon', 'cloud', 'pixel-cloud'].includes(value.shapeId), 'SNAPSHOT_INVALID');
  requireValid(inRange(value.seed, -2147483648, 2147483647) && Number.isInteger(value.seed), 'SNAPSHOT_INVALID');
  requireValid(value.generatorVersion === ProfileLimits.generatorVersion && validCounter(value.revision), 'SNAPSHOT_INVALID');
  requireValid(Array.isArray(value.controls) && value.controls.length <= ProfileLimits.maxControls, 'SNAPSHOT_INVALID');
  for (const control of value.controls) {
    requireValid(isRecord(control), 'SNAPSHOT_INVALID');
    requireValid(Array.isArray(control.center) && control.center.length === 3 && control.center.every(c => inRange(c, -4, 4)), 'SNAPSHOT_INVALID');
    requireValid(Array.isArray(control.delta) && control.delta.length === 3 && control.delta.every(c => inRange(c, -1, 1)), 'SNAPSHOT_INVALID');
    requireValid(Math.hypot(...control.delta as number[]) <= ProfileLimits.maxControlDelta, 'SNAPSHOT_INVALID');
    requireValid(inRange(control.radius, ProfileLimits.minRadius, ProfileLimits.maxRadius), 'SNAPSHOT_INVALID');
  }
}
export function validateSceneSnapshot(value: unknown): asserts value is SceneSnapshot {
  try {
    requireValid(isRecord(value)); validateSettings(value.settings);
    requireValid(Array.isArray(value.shapes) && value.shapes.length >= 1 && value.shapes.length <= 4);
    const ids = new Set<string>();
    for (const shape of value.shapes) {
      validateShapeSnapshot(shape); requireValid(!ids.has(shape.shapeId)); ids.add(shape.shapeId);
    }
    requireValid(ids.has(value.settings.shapeId));
  } catch { throw new Error('SNAPSHOT_INVALID'); }
}
