import { test } from 'node:test';
import assert from 'node:assert/strict';
import { ProceduralShapeFactory } from './ProceduralShapeFactory';
import type { ShapeId } from '../../domain/Models';
const factory = new ProceduralShapeFactory();
for (const shape of ['orb', 'ribbon', 'cloud', 'pixel-cloud'] as ShapeId[]) test(`CT-01 ${shape}: детерминизм, count, seed и независимость буферов`, () => {
  const a = factory.generate(shape, -1, 4000), b = factory.generate(shape, -1, 4000), other = factory.generate(shape, 2, 4000);
  assert.equal(a.positions.length, 12000); assert.ok(a.positions.every(Number.isFinite)); assert.deepEqual(a, b);
  assert.notDeepEqual(a, other); a.positions[0] = 99; assert.notEqual(b.positions[0], 99);
  assert.ok(factory.generate(shape, 1, 20000).positions.every(x => Math.abs(x) < 2));
});
test('Неверные входы отклоняются до генерации', () => {
  for (const count of [0, -1, 1.5, 20001, NaN]) assert.throws(() => factory.generate('orb', 0, count), /INVALID_INPUT/);
  assert.throws(() => factory.generate('orb', 2147483648, 1), /INVALID_INPUT/);
});
