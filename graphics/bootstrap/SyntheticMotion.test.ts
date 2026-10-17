import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SyntheticMotion } from './SyntheticMotion';
test('Harness: reproducible BPM/source, непрерывность фазы и pause/resume', () => {
  const a = new SyntheticMotion(), b = new SyntheticMotion(); a.enabled = b.enabled = true;
  for (let i = 0; i < 100; i++) assert.deepEqual(a.advance(0.1), b.advance(0.1));
  const before = a.advance(0); a.bpm = 120; a.switchSource(); const after = a.advance(0);
  assert.equal(after.phase, before.phase); assert.notEqual(after.sourceSessionId, before.sourceSessionId);
  a.paused = true; assert.equal(a.advance(100).phase, before.phase);
  a.paused = false; assert.ok(a.advance(0.1).phase > before.phase);
});
