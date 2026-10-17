export interface Distribution {
  count: number;
  minMs: number | null;
  maxMs: number | null;
  meanMs: number | null;
  p50Ms: number | null;
  p95Ms: number | null;
  p99Ms: number | null;
  bucketWidthMs: number;
  overflowAtMs: number;
  percentileMethod: 'histogram-upper-bound';
  bins?: readonly [number, number][];
}

/** Ограниченная гистограмма; percentile — верхняя граница корзины, overflow — observed max. */
class Histogram {
  private readonly bins: Uint32Array;
  private count = 0;
  private sum = 0;
  private minimum = Infinity;
  private maximum = 0;
  constructor(private readonly width: number, private readonly overflow: number) { this.bins = new Uint32Array(Math.ceil(overflow / width) + 1); }
  add(value: number): void {
    this.bins[Math.min(this.bins.length - 1, Math.floor(value / this.width))]++;
    this.count++; this.sum += value; this.minimum = Math.min(this.minimum, value); this.maximum = Math.max(this.maximum, value);
  }
  summary(includeBins = false): Distribution {
    const percentile = (p: number): number | null => {
      if (!this.count) return null;
      const target = Math.ceil(this.count * p); let total = 0;
      for (let i = 0; i < this.bins.length; i++) {
        total += this.bins[i];
        if (total >= target) return i === this.bins.length - 1 ? this.maximum : Math.min((i + 1) * this.width, this.maximum);
      }
      return this.maximum;
    };
    return {
      count: this.count, minMs: this.count ? this.minimum : null, maxMs: this.count ? this.maximum : null, meanMs: this.count ? this.sum / this.count : null,
      p50Ms: percentile(0.5), p95Ms: percentile(0.95), p99Ms: percentile(0.99), bucketWidthMs: this.width, overflowAtMs: this.overflow, percentileMethod: 'histogram-upper-bound',
      ...(includeBins ? { bins: Array.from(this.bins.entries()).filter(([, count]) => count > 0) as [number, number][] } : {}),
    };
  }
}

interface Minute {
  minute: number;
  submissions: number;
  activeMs: number;
  slowIntervals: number;
  intervals: Histogram;
  cpu: Histogram;
}

export interface RenderMetricsSnapshot {
  active: boolean;
  paused: boolean;
  contextLost: boolean;
  durationSeconds: number;
  activeSeconds: number;
  submissions: number;
  submissionFps: number | null;
  intervalThresholdMs: number;
  slowIntervals: number;
  slowFraction: number | null;
  submissionIntervals: Distribution;
  presentCpuDuration: Distribution;
  counters: { contextLosses: number; contextRestores: number; renderErrors: number; skippedPaused: number; skippedContextLost: number };
  minuteBucketLimit: number;
  minuteOverflow: boolean;
  minutes: { minute: number; activeSeconds: number; submissions: number; submissionFps: number | null; slowIntervals: number; submissionIntervals: Distribution; presentCpuDuration: Distribution }[];
}

/** JS submissions и CPU adapter time; это не измерение GPU/display FPS. Создаётся только явно. */
export class RenderMetrics {
  private readonly intervals = new Histogram(0.25, 1000);
  private readonly cpu = new Histogram(0.25, 1000);
  private readonly minutes = new Map<number, Minute>();
  private readonly maxMinutes = 180;
  private lastAccounting: number;
  private lastSubmission: number | null = null;
  private stopped: number | null = null;
  private activeMs = 0;
  private submissions = 0;
  private slowIntervals = 0;
  private overflow = false;
  private lost = false;
  private paused: boolean;
  private counters = { contextLosses: 0, contextRestores: 0, renderErrors: 0, skippedPaused: 0, skippedContextLost: 0 };
  constructor(private readonly started: number, private readonly threshold: number, paused = false, contextLost = false) {
    if (!Number.isFinite(started) || started < 0 || !Number.isFinite(threshold) || threshold <= 0) throw new Error('INVALID_INPUT: profiling options');
    this.lastAccounting = started; this.paused = paused; this.lost = contextLost;
  }
  get active(): boolean { return this.stopped === null; }
  recordPresent(start: number, end: number): void {
    if (!this.active) return;
    if (![start, end].every(Number.isFinite) || start < this.lastAccounting || end < start) throw new Error('INVALID_INPUT: profiling timestamps');
    this.account(start);
    if (this.paused) { this.counters.skippedPaused++; this.account(end); return; }
    if (this.lost) { this.counters.skippedContextLost++; this.account(end); return; }
    const minute = this.minute(start);
    this.submissions++; minute.submissions++;
    this.cpu.add(end - start); minute.cpu.add(end - start);
    if (this.lastSubmission !== null) {
      const interval = start - this.lastSubmission;
      this.intervals.add(interval); minute.intervals.add(interval);
      if (interval > this.threshold) { this.slowIntervals++; minute.slowIntervals++; }
    }
    this.lastSubmission = start;
    this.account(end);
  }
  setPaused(paused: boolean, now: number): void {
    if (!this.active) return;
    this.account(now);
    if (this.paused !== paused) this.lastSubmission = null;
    this.paused = paused;
  }
  contextLost(now: number): void {
    if (!this.active) return;
    this.account(now); this.counters.contextLosses++; this.lost = true; this.lastSubmission = null;
  }
  contextRestored(now: number): void {
    if (!this.active) return;
    this.account(now); this.counters.contextRestores++; this.lost = false; this.lastSubmission = null;
  }
  renderError(now: number): void { if (this.active) { this.account(now); this.counters.renderErrors++; } }
  read(now: number, includeBins = false): RenderMetricsSnapshot {
    if (this.active) this.account(now);
    const intervals = this.intervals.summary(includeBins);
    return {
      active: this.active, paused: this.paused, contextLost: this.lost, durationSeconds: ((this.stopped ?? now) - this.started) / 1000, activeSeconds: this.activeMs / 1000,
      submissions: this.submissions, submissionFps: this.activeMs > 0 ? this.submissions / (this.activeMs / 1000) : null,
      intervalThresholdMs: this.threshold, slowIntervals: this.slowIntervals, slowFraction: intervals.count ? this.slowIntervals / intervals.count : null,
      submissionIntervals: intervals, presentCpuDuration: this.cpu.summary(includeBins), counters: { ...this.counters },
      minuteBucketLimit: this.maxMinutes, minuteOverflow: this.overflow,
      minutes: [...this.minutes.values()].map(minute => ({ minute: minute.minute, activeSeconds: minute.activeMs / 1000, submissions: minute.submissions,
        submissionFps: minute.activeMs > 0 ? minute.submissions / (minute.activeMs / 1000) : null, slowIntervals: minute.slowIntervals,
        submissionIntervals: minute.intervals.summary(), presentCpuDuration: minute.cpu.summary() })),
    };
  }
  stop(now: number): RenderMetricsSnapshot {
    if (this.active) { this.account(now); this.stopped = now; }
    return this.read(this.stopped!, true);
  }
  private minute(now: number): Minute {
    const index = Math.floor((now - this.started) / 60000);
    if (index >= this.maxMinutes) this.overflow = true;
    const bucket = Math.min(index, this.maxMinutes - 1);
    let value = this.minutes.get(bucket);
    if (!value) {
      value = { minute: bucket, submissions: 0, activeMs: 0, slowIntervals: 0, intervals: new Histogram(1, 250), cpu: new Histogram(1, 250) };
      this.minutes.set(bucket, value);
    }
    return value;
  }
  private account(now: number): void {
    if (!Number.isFinite(now) || now < this.lastAccounting) throw new Error('INVALID_INPUT: profiling clock');
    if (!this.paused && !this.lost) {
      this.activeMs += now - this.lastAccounting;
      let cursor = this.lastAccounting;
      while (cursor < now) {
        if (cursor >= this.started + this.maxMinutes * 60000) { this.minute(cursor).activeMs += now - cursor; break; }
        const boundary = this.started + (Math.floor((cursor - this.started) / 60000) + 1) * 60000;
        const end = Math.min(now, boundary);
        this.minute(cursor).activeMs += end - cursor; cursor = end;
      }
    }
    this.lastAccounting = now;
  }
}
