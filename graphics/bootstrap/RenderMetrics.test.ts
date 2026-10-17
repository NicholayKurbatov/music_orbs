import { test } from 'node:test';
import assert from 'node:assert/strict';
import { RenderMetrics } from './RenderMetrics';

test('actual submissions: интервалы и CPU duration независимы; slow threshold строгий', () => {
  const metrics = new RenderMetrics(0, 25);
  metrics.recordPresent(0, 2); metrics.recordPresent(16, 19); metrics.recordPresent(41, 45); metrics.recordPresent(71, 76);
  const result = metrics.stop(100);
  assert.equal(result.submissions, 4); assert.equal(result.submissionIntervals.count, 3);
  assert.equal(result.submissionIntervals.meanMs, (16 + 25 + 30) / 3);
  assert.equal(result.presentCpuDuration.meanMs, 3.5); assert.equal(result.presentCpuDuration.p95Ms, 5);
  assert.equal(result.slowIntervals, 1); assert.equal(result.slowFraction, 1 / 3); assert.equal(result.submissionFps, 40);
});

test('pause/context loss не создают fake submissions или огромный resume interval', () => {
  const metrics = new RenderMetrics(0, 25);
  metrics.recordPresent(0, 1); metrics.setPaused(true, 10); metrics.recordPresent(20, 21);
  metrics.setPaused(false, 1010); metrics.recordPresent(1020, 1021);
  metrics.contextLost(1030); metrics.recordPresent(1040, 1041); metrics.contextRestored(2030);
  metrics.recordPresent(2040, 2041); metrics.renderError(2050);
  const result = metrics.stop(2060);
  assert.equal(result.submissions, 3); assert.equal(result.submissionIntervals.count, 0);
  assert.ok(Math.abs(result.activeSeconds - 0.06) < 1e-12);
  assert.deepEqual(result.counters, { contextLosses: 1, contextRestores: 1, renderErrors: 1, skippedPaused: 1, skippedContextLost: 1 });
});

test('bounded histograms и minute buckets не растут с числом кадров/длительностью', () => {
  const metrics = new RenderMetrics(0, 25);
  for (let i = 0; i < 100000; i++) metrics.recordPresent(i * 16, i * 16 + 1);
  metrics.recordPresent(200 * 60000, 200 * 60000 + 1500);
  const result = metrics.stop(201 * 60000);
  assert.equal(result.submissions, 100001); assert.equal(result.minuteBucketLimit, 180);
  assert.equal(result.minuteOverflow, true); assert.ok(result.minutes.length <= 180);
  assert.ok(result.submissionIntervals.bins!.length <= 4001); assert.ok(result.presentCpuDuration.bins!.length <= 4001);
  assert.equal(result.presentCpuDuration.maxMs, 1500);
  assert.ok(JSON.stringify(result).length < 250000);
});

test('initial pause, atomic invalid input и freeze после stop', () => {
  const metrics = new RenderMetrics(100, 25, true);
  metrics.setPaused(false, 1100); metrics.recordPresent(1110, 1112);
  assert.throws(() => metrics.recordPresent(1111, 1113), /INVALID_INPUT/);
  assert.throws(() => metrics.read(NaN), /INVALID_INPUT/);
  const stopped = metrics.stop(1120); metrics.recordPresent(1200, 1201); metrics.contextLost(1300);
  assert.deepEqual(metrics.read(2000, true), stopped); assert.equal(stopped.submissions, 1); assert.equal(stopped.activeSeconds, 0.02);
});
