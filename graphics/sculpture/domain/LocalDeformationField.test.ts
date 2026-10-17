import { test } from 'node:test';
import assert from 'node:assert/strict';
import { LocalDeformationField, deformationAt } from './LocalDeformationField';
import type { DeformationSnapshot, Stroke, Vec3 } from './Models';
import { ProfileLimits } from './ProfileLimits';
const initial: DeformationSnapshot = { shapeId: 'orb', seed: 7, generatorVersion: 1, revision: 0, controls: [] };
const stroke = (phase: Stroke['phase'], center: Vec3 = [0, 0, 0], delta: Vec3 = [0.1, 0, 0], pointerId = 1): Stroke => ({ phase, center, delta, pointerId, radius: 0.5 });

test('CT-02: локальный falloff, отделённый snapshot, revisions и reset', () => {
  const field = new LocalDeformationField(initial);
  field.apply(stroke('begin')); field.apply(stroke('end'));
  const value = field.snapshot();
  assert.equal(value.revision, 1);
  assert.equal(deformationAt([0, 0, 0], value.controls)[0], 0.1);
  assert.ok(deformationAt([0.2, 0, 0], value.controls)[0] < 0.1);
  assert.deepEqual(deformationAt([0.6, 0, 0], value.controls), [0, 0, 0]);
  (value.controls[0].delta as unknown as number[])[0] = 100;
  assert.equal(field.snapshot().controls[0].delta[0], 0.1);
  field.reset(); assert.equal(field.snapshot().revision, 2); assert.equal(field.snapshot().seed, 7); assert.deepEqual(field.snapshot().controls, []);
});
test('FT-25: cancel сохраняет изменения и чужой pointer игнорируется', () => {
  const field = new LocalDeformationField(initial);
  field.apply(stroke('begin')); field.apply(stroke('move', [0, 0, 0], [0.1, 0, 0], 2));
  assert.equal(field.snapshot().revision, 1);
  field.apply(stroke('cancel')); assert.equal(field.snapshot().controls.length, 1);
  assert.throws(() => field.apply(stroke('move')), /INVALID_INPUT/);
});
test('Контроль лимита поля атомарен; точное объединение не меняет функцию поля', () => {
  const field = new LocalDeformationField(initial);
  field.apply(stroke('begin')); field.apply(stroke('move')); field.apply(stroke('end'));
  assert.equal(field.snapshot().controls.length, 1); assert.equal(field.snapshot().controls[0].delta[0], 0.2);
  for (let i = 1; i < ProfileLimits.maxControls; i++) { field.apply(stroke('begin', [i / 100, 0, 0])); field.apply(stroke('end')); }
  const before = field.snapshot();
  assert.throws(() => field.apply(stroke('begin', [2, 0, 0])), /control limit/);
  assert.deepEqual(field.snapshot(), before);
  assert.throws(() => field.apply(stroke('begin', [NaN, 0, 0])), /INVALID_INPUT/);
  assert.deepEqual(field.snapshot(), before);
});
test('Предел полного смещения не зависит от количества частиц', () => {
  const controls = Array.from({ length: 10 }, () => ({ center: [0, 0, 0] as Vec3, delta: [0.2, 0, 0] as Vec3, radius: 0.5 }));
  assert.equal(Math.hypot(...deformationAt([0, 0, 0], controls)), 1);
  assert.ok(deformationAt([0.4, 0, 0], controls)[0] < deformationAt([0.1, 0, 0], controls)[0]);
});
