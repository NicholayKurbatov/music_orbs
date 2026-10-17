import { test } from 'node:test';
import assert from 'node:assert/strict';
import { profileOptions, parseMemory, memorySummary, segmentComplete, screenTimeoutPlan } from './android-profile-options.mjs';

test('default — план, выполнение требует --run; proposal не превращается в accepted', () => {
  const options = profileOptions([]);
  assert.equal(options.run, false); assert.equal(options.budgets.status, 'proposal');
  assert.equal(options.durations.reduce((a, b) => a + b), 4200);
  assert.equal(profileOptions(['--run']).budgets.status, 'proposal');
  assert.equal(profileOptions(['--run', '--budget-status', 'accepted']).run, true);
});
test('короткий smoke отделён от performance acceptance; неверные диапазоны отклонены', () => {
  assert.equal(profileOptions(['--run', '--durations', '2,2,2,2,3']).scope, 'short-software-smoke');
  assert.throws(() => profileOptions(['--durations', '1,2,3']), /пять/);
  assert.throws(() => profileOptions(['--durations', '1,2,3,4,NaN']), /пять/);
  assert.throws(() => profileOptions(['--slow-fraction', '2']), /slow-fraction/);
  assert.throws(() => profileOptions(['--fps', '120']), /fps/);
});
test('Android PSS/RSS parsing использует KiB и различает отсутствие измерения/нуль', () => {
  assert.deepEqual(parseMemory('TOTAL PSS: 120000 TOTAL RSS: 220000'), { pssKiB: 120000, rssKiB: 220000, units: 'KiB' });
  assert.deepEqual(parseMemory('TOTAL PSS: 0'), { pssKiB: 0, rssKiB: null, units: 'KiB' });
  assert.deepEqual(parseMemory('No process found'), { pssKiB: null, rssKiB: null, units: 'KiB' });
});

test('пропущенный initial PSS и пауза не становятся полным измерением', () => {
  assert.deepEqual(memorySummary([{ pssKiB: null }, { pssKiB: 2048 }]), {
    initialPssKiB: null, finalPssKiB: 2048, peakPssKiB: 2048, peakGrowthMiB: null, complete: false,
  });
  assert.equal(memorySummary([{ pssKiB: 1024 }, { pssKiB: 3072 }]).peakGrowthMiB, 2);
  const metrics = { submissions: 600, submissionIntervals: { count: 599 }, paused: false, contextLost: false,
    minuteOverflow: false, activeSeconds: 10, durationSeconds: 10, counters: { contextLosses: 0, renderErrors: 0 } };
  assert.equal(segmentComplete(metrics, 10), true);
  assert.equal(segmentComplete({ ...metrics, activeSeconds: 9 }, 10), false);
  assert.equal(segmentComplete({ ...metrics, counters: { contextLosses: 1, renderErrors: 0 } }, 10), false);
});

test('30-minute Orb PSS growth отделён от предыдущих пиков 70-minute run', () => {
  const samples = [{ segment: '1-orb', pssKiB: 1024 }, { segment: '4-pixel-cloud', pssKiB: 102400 },
    { segment: '5-orb-memory', pssKiB: 4096 }, { segment: '5-orb-memory', pssKiB: 5120 }, { segment: '5-orb-memory', pssKiB: 4608 }];
  assert.equal(memorySummary(samples).peakGrowthMiB, 99);
  assert.deepEqual(memorySummary(samples, '5-orb-memory'), {
    initialPssKiB: 4096, finalPssKiB: 4608, peakPssKiB: 5120, peakGrowthMiB: 1, complete: true,
  });
  assert.equal(memorySummary(samples, 'missing').complete, false);
});

test('screen timeout сохраняет исходное отсутствие/значение и покрывает длительный run с запасом', () => {
  assert.deepEqual(screenTimeoutPlan('60000\n', [600, 600, 600, 600, 1800]), { originalSystemTimeoutMs: 60000, temporarySystemTimeoutMs: 5400000 });
  assert.equal(screenTimeoutPlan('null', [2, 2, 2, 2, 3]).originalSystemTimeoutMs, null);
  assert.equal(screenTimeoutPlan('30000', [10800, 10800, 10800, 10800, 10800]).temporarySystemTimeoutMs, 54600000);
  assert.throws(() => screenTimeoutPlan('unknown', [1]), /preserving/);
});
