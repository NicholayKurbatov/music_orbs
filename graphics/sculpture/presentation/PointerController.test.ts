import { test } from 'node:test';
import assert from 'node:assert/strict';
import { PointerController } from './PointerController';
import type { SceneApplication } from '../application/SceneApplication';
import type { Stroke, Vec3 } from '../domain/Models';
class Canvas extends EventTarget {
  captured = new Set<number>();
  listeners = new Map<string, Set<EventListenerOrEventListenerObject>>();
  override addEventListener(type: string, callback: EventListenerOrEventListenerObject | null, options?: AddEventListenerOptions | boolean) {
    if (callback) {
      if (!this.listeners.has(type)) this.listeners.set(type, new Set());
      this.listeners.get(type)!.add(callback);
    }
    super.addEventListener(type, callback, options);
  }
  override removeEventListener(type: string, callback: EventListenerOrEventListenerObject | null, options?: EventListenerOptions | boolean) {
    if (callback) this.listeners.get(type)?.delete(callback);
    super.removeEventListener(type, callback, options);
  }
  setPointerCapture(id: number) { this.captured.add(id); }
  hasPointerCapture(id: number) { return this.captured.has(id); }
  releasePointerCapture(id: number) {
    this.captured.delete(id);
    this.dispatchEvent(event('lostpointercapture', id, 'touch'));
  }
}
const event = (type: string, pointerId: number, pointerType: 'touch' | 'mouse' | 'pen', clientX = 0, clientY = 0) => Object.assign(new Event(type, { cancelable: true }), { pointerId, pointerType, clientX, clientY, button: 0 });
const touchcancel = (remainingTouches: number) => Object.assign(new Event('touchcancel'), { touches: Array.from({ length: remainingTouches }, (_, identifier) => ({ identifier })) });
function setup() {
  const canvas = new Canvas(), strokes: Stroke[] = [];
  let changed = 0;
  const application: SceneApplication = { async handle() { return { type: 'ack' }; }, frame() {}, gesture(stroke) { strokes.push(stroke); } };
  const controller = new PointerController(canvas as unknown as HTMLCanvasElement,
    { pick(x, y) { return [x / 100, y / 100, 1]; } }, application, () => { changed++; }, error => { throw error; });
  return { canvas, strokes, controller, changes: () => changed };
}
test('FT-25: pointer capture, второй палец, cancel и снятие listeners', () => {
  const canvas = new Canvas(), strokes: Stroke[] = [], anchors: (Vec3 | undefined)[] = [];
  const application: SceneApplication = { async handle() { return { type: 'ack' }; }, frame() {}, gesture(stroke) { strokes.push(stroke); } };
  const errors: unknown[] = [];
  const controller = new PointerController(canvas as unknown as HTMLCanvasElement,
    { pick(x, y, anchor) { anchors.push(anchor); return [x / 100, y / 100, 1]; } }, application, () => {}, error => errors.push(error));
  canvas.dispatchEvent(event('pointerdown', 1, 'touch')); canvas.dispatchEvent(event('pointerdown', 2, 'touch'));
  canvas.dispatchEvent(event('pointermove', 2, 'touch', 40, 30)); canvas.dispatchEvent(event('pointermove', 1, 'touch', 10, 20));
  canvas.dispatchEvent(event('pointercancel', 1, 'touch'));
  assert.deepEqual(strokes.map(s => s.phase), ['begin', 'move', 'cancel']);
  assert.deepEqual(strokes[1].center, [0, 0, 1]); assert.deepEqual(strokes[1].delta, [0.1, 0.2, 0]);
  assert.deepEqual(anchors[1], [0, 0, 1]); assert.equal(canvas.captured.size, 0); assert.deepEqual(errors, []);
  controller.dispose(); canvas.dispatchEvent(event('pointerdown', 3, 'touch')); assert.equal(strokes.length, 3);
});
test('FT-25: pointerup вне view завершает захваченный drag и позволяет следующий жест', () => {
  const canvas = new Canvas(), strokes: Stroke[] = [];
  const app: SceneApplication = { async handle() { return { type: 'ack' }; }, frame() {}, gesture(stroke) { strokes.push(stroke); } };
  let changed = 0;
  const controller = new PointerController(canvas as unknown as HTMLCanvasElement,
    { pick(x, y) { return [x / 100, y / 100, 1]; } }, app, () => { changed++; }, error => { throw error; });
  canvas.dispatchEvent(event('pointerdown', 4, 'touch', 10, 20));
  assert.equal(canvas.hasPointerCapture(4), true);
  canvas.dispatchEvent(event('pointermove', 4, 'touch', -100, 200));
  canvas.dispatchEvent(event('pointerup', 4, 'touch', -100, 200));
  assert.equal(canvas.hasPointerCapture(4), false); assert.equal(changed, 1);
  canvas.dispatchEvent(event('pointerdown', 5, 'touch', 20, 30)); canvas.dispatchEvent(event('lostpointercapture', 5, 'touch'));
  assert.deepEqual(strokes.map(s => [s.pointerId, s.phase]), [[4, 'begin'], [4, 'move'], [4, 'end'], [5, 'begin'], [5, 'cancel']]);
  assert.equal(changed, 2); controller.dispose();
});
test('FT-25: touchcancel without pointercancel releases primary and the next first DOWN starts a gesture', () => {
  const { canvas, strokes, controller, changes } = setup();
  canvas.dispatchEvent(event('pointerdown', 2, 'touch', 10, 20));
  canvas.dispatchEvent(event('pointermove', 2, 'touch', 20, 30));
  canvas.dispatchEvent(touchcancel(0));
  assert.deepEqual(strokes.map(s => [s.pointerId, s.phase]), [[2, 'begin'], [2, 'move'], [2, 'cancel']]);
  assert.equal(canvas.hasPointerCapture(2), false);
  assert.equal(changes(), 1);
  canvas.dispatchEvent(event('pointerdown', 4, 'touch', 30, 40));
  assert.deepEqual(strokes.at(-1), { pointerId: 4, phase: 'begin', center: [0.3, 0.4, 1], delta: [0, 0, 0.025], radius: 0.5 });
  canvas.dispatchEvent(event('pointerup', 4, 'touch'));
  assert.equal(changes(), 2);
  controller.dispose();
});
test('FT-25: cancellation of a secondary touch leaves the accepted primary active', () => {
  const { canvas, strokes, controller, changes } = setup();
  canvas.dispatchEvent(event('pointerdown', 2, 'touch'));
  canvas.dispatchEvent(event('pointerdown', 3, 'touch'));
  canvas.dispatchEvent(event('pointercancel', 3, 'touch'));
  canvas.dispatchEvent(touchcancel(1));
  assert.equal(changes(), 0);
  assert.deepEqual([...canvas.captured], [2]);
  canvas.dispatchEvent(event('pointermove', 2, 'touch', 10, 20));
  assert.deepEqual(strokes.map(s => [s.pointerId, s.phase]), [[2, 'begin'], [2, 'move']]);
  canvas.dispatchEvent(touchcancel(0));
  assert.equal(strokes.at(-1)?.phase, 'cancel');
  assert.equal(changes(), 1);
  controller.dispose();
});
for (const fallbackFirst of [false, true]) {
  test(`FT-25: pointercancel and touchcancel are idempotent (${fallbackFirst ? 'touch' : 'pointer'} first)`, () => {
    const { canvas, strokes, controller, changes } = setup();
    canvas.dispatchEvent(event('pointerdown', 2, 'touch'));
    const cancellations = [event('pointercancel', 2, 'touch'), touchcancel(0)];
    if (fallbackFirst) cancellations.reverse();
    for (const cancellation of cancellations) canvas.dispatchEvent(cancellation);
    canvas.dispatchEvent(event('lostpointercapture', 2, 'touch'));
    canvas.dispatchEvent(touchcancel(0));
    assert.deepEqual(strokes.map(s => s.phase), ['begin', 'cancel']);
    assert.equal(changes(), 1);
    assert.equal(canvas.captured.size, 0);
    controller.dispose();
  });
}
for (const pointerType of ['mouse', 'pen'] as const) {
  test(`FT-25: empty touchcancel cannot cancel an accepted ${pointerType} gesture`, () => {
    const { canvas, strokes, controller, changes } = setup();
    canvas.dispatchEvent(event('pointerdown', 7, pointerType));
    canvas.dispatchEvent(touchcancel(0));
    assert.equal(changes(), 0);
    assert.equal(canvas.hasPointerCapture(7), true);
    canvas.dispatchEvent(event('pointermove', 7, pointerType, 10, 20));
    canvas.dispatchEvent(event('pointerup', 7, pointerType));
    assert.deepEqual(strokes.map(s => s.phase), ['begin', 'move', 'end']);
    assert.equal(changes(), 1);
    controller.dispose();
  });
}
test('FT-25: dispose removes the touchcancel fallback listener', () => {
  const { canvas, controller } = setup();
  assert.equal(canvas.listeners.get('touchcancel')?.size, 1);
  controller.dispose();
  assert.equal(canvas.listeners.get('touchcancel')?.size, 0);
});
