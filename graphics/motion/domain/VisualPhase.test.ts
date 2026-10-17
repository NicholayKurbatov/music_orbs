import { test } from 'node:test';
import assert from 'node:assert/strict';
import { VisualPhase } from './VisualPhase';
import type { MotionSignal } from './MotionSignal';
import { SyntheticMotion } from '../../bootstrap/SyntheticMotion';
const signal: MotionSignal = { sourceSessionId: 'source-one', timestampSeconds: 1, bpm: 90, slowCycleSeconds: 20, phase: 0.4, energy: 0.6, intensity: 0.3, confidence: 0.9, mode: 'synced' };

test('задержанные motion между RAF не откатывают визуальную фазу', () => {
  const visual = new VisualPhase(); visual.accept(signal);
  let previous = visual.advance(0).phase;
  for (let frame = 0; frame < 50; frame++) {
    if (frame % 10 === 0) {
      visual.accept({ ...signal, phase: 0.1, timestampSeconds: 2 + frame });
      assert.equal(visual.advance(0).phase, previous);
    }
    const current = visual.advance(0.016).phase;
    assert.ok(current > previous); previous = current;
  }
  assert.ok(Math.abs(previous - (0.4 + 50 * 0.016 / 20)) < 1e-12);
});

test('source switch сохраняет фазу и сглаженный период, старую энергию удаляет сразу', () => {
  const visual = new VisualPhase(); visual.accept({ ...signal, slowCycleSeconds: 16 });
  const before = visual.advance(0.05);
  visual.setSource('none'); const neutral = visual.advance(0);
  assert.equal(neutral.phase, before.phase); assert.equal(neutral.slowCycleSeconds, 16);
  assert.equal(neutral.energy, 0); assert.equal(neutral.intensity, 0); assert.equal(neutral.bpm, null); assert.equal(neutral.mode, 'autonomous');
  visual.setSource('source-two'); visual.accept({ ...signal, sourceSessionId: 'source-two', phase: 0.99, slowCycleSeconds: 17 });
  assert.equal(visual.advance(0).phase, before.phase);
  assert.ok(Math.abs(visual.advance(0.05).phase - before.phase - 0.05 / 17) < 1e-12);
});

test('resume delta ограничен, phase wrap непрерывен и NaN не применяется', () => {
  const visual = new VisualPhase(); visual.accept({ ...signal, phase: 0.999, slowCycleSeconds: 10 });
  assert.ok(Math.abs(visual.advance(100).phase - 0.009) < 1e-12);
  const before = visual.advance(0);
  assert.throws(() => visual.accept({ ...signal, slowCycleSeconds: NaN }), /INVALID_INPUT/);
  assert.throws(() => visual.advance(NaN), /INVALID_INPUT/);
  assert.deepEqual(visual.advance(0), before);
});

test('browser syncOff сохраняет фазу и плавно меняет частоту по native alpha/slope', () => {
  const fake = new SyntheticMotion(); fake.enabled = true;
  let before = fake.advance(0);
  for (let i = 0; i < 1500; i++) before = fake.advance(0.05);
  assert.ok(Math.abs(before.slowCycleSeconds - 60 / 90 * 32) < 1e-8);
  const unchanged = fake.advance(0, false);
  assert.equal(unchanged.phase, before.phase); assert.equal(unchanged.slowCycleSeconds, before.slowCycleSeconds);
  const next = fake.advance(0.05, false);
  const expectedFrequency = 1 / before.slowCycleSeconds + (1 / 24 - 1 / before.slowCycleSeconds) * (1 - Math.exp(-0.05 / 3));
  assert.ok(Math.abs(1 / next.slowCycleSeconds - expectedFrequency) < 1e-12);
  assert.ok(Math.abs(1 / next.slowCycleSeconds - 1 / before.slowCycleSeconds) <= 0.02 * 0.05);
  assert.equal(next.mode, 'autonomous'); assert.equal(next.energy, 0);
  assert.ok(next.slowCycleSeconds < 24);
  for (let i = 0; i < 1500; i++) before = fake.advance(0.05, false);
  assert.ok(Math.abs(before.slowCycleSeconds - 24) < 1e-8);
});
