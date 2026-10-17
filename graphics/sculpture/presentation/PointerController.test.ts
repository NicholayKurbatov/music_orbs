import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PointerController } from './PointerController';
import type { SceneApplication } from '../application/SceneApplication';
import type { Stroke, Vec3 } from '../domain/Models';
class Canvas extends EventTarget {
  captured = new Set<number>();
  setPointerCapture(id: number) { this.captured.add(id); }
  hasPointerCapture(id: number) { return this.captured.has(id); }
  releasePointerCapture(id: number) { this.captured.delete(id); }
}
const event = (type: string, pointerId: number, clientX = 0, clientY = 0) => Object.assign(new Event(type, { cancelable: true }), { pointerId, clientX, clientY, button: 0 });
test('FT-25: pointer capture, второй палец, cancel и снятие listeners', () => {
  const canvas = new Canvas(), strokes: Stroke[] = [], anchors: (Vec3 | undefined)[] = [];
  const application: SceneApplication = { async handle() { return { type: 'ack' }; }, frame() {}, gesture(stroke) { strokes.push(stroke); } };
  const errors: unknown[] = [];
  const controller = new PointerController(canvas as unknown as HTMLCanvasElement,
    { pick(x, y, anchor) { anchors.push(anchor); return [x / 100, y / 100, 1]; } }, application, () => {}, error => errors.push(error));
  canvas.dispatchEvent(event('pointerdown', 1)); canvas.dispatchEvent(event('pointerdown', 2));
  canvas.dispatchEvent(event('pointermove', 2, 40, 30)); canvas.dispatchEvent(event('pointermove', 1, 10, 20));
  canvas.dispatchEvent(event('pointercancel', 1));
  assert.deepEqual(strokes.map(s => s.phase), ['begin', 'move', 'cancel']);
  assert.deepEqual(strokes[1].center, [0, 0, 1]); assert.deepEqual(strokes[1].delta, [0.1, 0.2, 0]);
  assert.deepEqual(anchors[1], [0, 0, 1]); assert.equal(canvas.captured.size, 0); assert.deepEqual(errors, []);
  controller.dispose(); canvas.dispatchEvent(event('pointerdown', 3)); assert.equal(strokes.length, 3);
});
test('FT-25: pointerup вне view завершает захваченный drag и позволяет следующий жест', () => {
  const canvas = new Canvas(), strokes: Stroke[] = [];
  const app: SceneApplication = { async handle() { return { type: 'ack' }; }, frame() {}, gesture(stroke) { strokes.push(stroke); } };
  let changed = 0;
  const controller = new PointerController(canvas as unknown as HTMLCanvasElement,
    { pick(x, y) { return [x / 100, y / 100, 1]; } }, app, () => { changed++; }, error => { throw error; });
  canvas.dispatchEvent(event('pointerdown', 4, 10, 20));
  assert.equal(canvas.hasPointerCapture(4), true);
  canvas.dispatchEvent(event('pointermove', 4, -100, 200));
  canvas.dispatchEvent(event('pointerup', 4, -100, 200));
  assert.equal(canvas.hasPointerCapture(4), false); assert.equal(changed, 1);
  canvas.dispatchEvent(event('pointerdown', 5, 20, 30)); canvas.dispatchEvent(event('lostpointercapture', 5));
  assert.deepEqual(strokes.map(s => [s.pointerId, s.phase]), [[4, 'begin'], [4, 'move'], [4, 'end'], [5, 'begin'], [5, 'cancel']]);
  assert.equal(changed, 2); controller.dispose();
});
