import test from 'node:test';
import assert from 'node:assert/strict';
import { MusicMotionResponse } from './MusicMotionResponse';
import type { MotionSignal } from './MotionSignal';

const signal = (bpm = 120, intensity = 0): MotionSignal => ({ sourceSessionId: 'track', timestampSeconds: 1,
  bpm, intensity, energy: 0.4, confidence: 0.95, mode: 'synced', slowCycleSeconds: 16, phase: 0.6 });
const settle = (response: MusicMotionResponse, frame: MotionSignal, enabled = true) => {
  for (let i = 0; i < 600; i++) response.advance(1 / 60, frame, enabled);
  return response.read();
};

test('FR-26: full sync follows slower tempo and accelerates for attacks at the same BPM', () => {
  const response = new MusicMotionResponse();
  const fast = settle(response, signal(150));
  const slow = settle(response, signal(60));
  assert.ok(Math.abs(fast / slow - 2.5) < 0.001);
  const before = response.read();
  response.advance(0.1, signal(60, 1), true);
  assert.ok(response.read() > before + 0.1);
  const accented = settle(response, signal(60, 1));
  assert.ok(Math.abs(accented / slow - 1.75) < 0.001);
  assert.ok(Math.abs(settle(response, signal(60)) - slow) < 0.001);
});

test('FR-26: no music, low confidence and disabled sync ease back to the old speed', () => {
  for (const [frame, enabled] of [
    [signal(), false], [{ ...signal(), mode: 'autonomous', bpm: null }, true],
    [{ ...signal(), energy: 0 }, true], [{ ...signal(), confidence: 0.5 }, true],
  ] as [MotionSignal, boolean][]) {
    const response = new MusicMotionResponse(); settle(response, signal());
    const before = response.read(); response.advance(0, frame, enabled); assert.equal(response.read(), before);
    assert.ok(Math.abs(settle(response, frame, enabled) - 1) < 0.001);
  }
  assert.equal(settle(new MusicMotionResponse(), signal(), false), 1);
});

test('NFR-02: trapezoid integration bounds changes without a phase jump or background leap', () => {
  const response = new MusicMotionResponse();
  for (let i = 0; i < 200; i++) {
    const before = response.read();
    const dt = response.advance(1 / 60, signal(i < 100 ? 240 : 40, i % 2), true);
    const after = response.read();
    assert.ok(Math.abs(after - before) <= 20 / 60 + 1e-12);
    assert.ok(Math.abs(dt - (before + after) / 120) < 1e-12);
  }
  assert.equal(response.advance(0, signal(), true), 0);
  const before = response.read();
  assert.throws(() => response.advance(NaN, signal(), true));
  assert.throws(() => response.advance(-1, signal(), true));
  assert.equal(response.read(), before);
  assert.ok(response.advance(3600, signal(), false) < 4);
});
