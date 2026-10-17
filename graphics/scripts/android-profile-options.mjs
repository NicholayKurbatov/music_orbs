import { parseArgs } from 'node:util';

export function profileOptions(args) {
  const { values } = parseArgs({ args, strict: true, options: {
    run: { type: 'boolean', default: false }, 'screenshots-only': { type: 'boolean', default: false }, help: { type: 'boolean', default: false },
    durations: { type: 'string' }, serial: { type: 'string' }, output: { type: 'string' },
    quality: { type: 'string', default: 'medium' }, fps: { type: 'string', default: '60' },
    'budget-status': { type: 'string', default: 'proposal' }, 'p95-ms': { type: 'string', default: '20' },
    'slow-ms': { type: 'string', default: '25' }, 'slow-fraction': { type: 'string', default: '0.05' },
    'pss-growth-mib': { type: 'string', default: '32' }, 'restore-baseline': { type: 'string' },
  } });
  const durations = values.durations ? values.durations.split(',').map(Number) : [600, 600, 600, 600, 1800];
  if (durations.length !== 5 || durations.some(value => !Number.isFinite(value) || value < 0 || value > 10800)) throw new Error('--durations: пять длительностей в секундах, каждая 0..10800.');
  const smoke = !!values.durations && durations.every(value => value <= 30);
  const number = (key, min, max) => { const value = Number(values[key]); if (!Number.isFinite(value) || value < min || value > max) throw new Error(`Неверный --${key}`); return value; };
  if (!['medium', 'low', 'high'].includes(values.quality) || !['30', '60'].includes(values.fps)) throw new Error('Допустимы quality low/medium/high и fps 30/60.');
  if (!['proposal', 'accepted', 'unconfigured'].includes(values['budget-status'])) throw new Error('budget-status: proposal/accepted/unconfigured.');
  return {
    run: values.run, help: values.help, screenshotsOnly: values['screenshots-only'], serial: values.serial, output: values.output,
    restoreBaseline: values['restore-baseline'], quality: values.quality, fps: Number(values.fps), durations,
    smoke, scope: values['screenshots-only'] ? 'screenshots-only' : smoke ? 'short-software-smoke' : 'emulator-profiling',
    budgets: { status: values['budget-status'], p95IntervalMs: number('p95-ms', 0.25, 10000), slowIntervalMs: number('slow-ms', 0.25, 10000), maxSlowFraction: number('slow-fraction', 0, 1), maxPssGrowthMiB: number('pss-growth-mib', 0, 65536) },
  };
}

export function parseMemory(raw) {
  const pss = /TOTAL PSS:\s*(\d+)/.exec(raw)?.[1];
  const rss = /TOTAL RSS:\s*(\d+)/.exec(raw)?.[1];
  return { pssKiB: pss === undefined ? null : Number(pss), rssKiB: rss === undefined ? null : Number(rss), units: 'KiB' };
}

export function memorySummary(samples, segment) {
  if (segment !== undefined) samples = samples.filter(sample => sample.segment === segment);
  const available = samples.map(sample => sample.pssKiB).filter(value => value !== null);
  const initial = samples[0]?.pssKiB ?? null;
  const final = samples.at(-1)?.pssKiB ?? null;
  const peak = available.length ? Math.max(...available) : null;
  return { initialPssKiB: initial, finalPssKiB: final, peakPssKiB: peak,
    peakGrowthMiB: initial === null || peak === null ? null : (peak - initial) / 1024,
    complete: samples.length > 0 && available.length === samples.length };
}

export function segmentComplete(metrics, requestedSeconds) {
  return !!metrics && metrics.submissions > 0 && metrics.submissionIntervals.count > 0
    && !metrics.paused && !metrics.contextLost && !metrics.minuteOverflow
    && metrics.activeSeconds >= requestedSeconds - 0.1
    && metrics.durationSeconds - metrics.activeSeconds < 0.1
    && metrics.counters.contextLosses === 0 && metrics.counters.renderErrors === 0;
}

export function screenTimeoutPlan(raw, durations) {
  const text = raw.trim();
  if (text !== 'null' && (!/^\d+$/.test(text) || Number(text) > 2147483647)) throw new Error('Unknown screen_off_timeout; preserving device setting.');
  return { originalSystemTimeoutMs: text === 'null' ? null : Number(text),
    temporarySystemTimeoutMs: Math.max(5400000, Math.ceil((durations.reduce((a, b) => a + b, 0) + 600) * 1000)) };
}
