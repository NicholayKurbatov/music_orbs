import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { AndroidDevice, appId, inspect, wait } from './android-device.mjs';

// Собственная синтетика: никакого recording, upstream audio или стороннего DSP в runner.
const rate = 48000;
const duration = 104;
const segments = [
  { id: 'tempo-90', start: 0, end: 24, bpm: 90, window: [12, 22], period: 60 / 90 * 32 },
  { id: 'tempo-120', start: 24, end: 60, bpm: 120, window: [50, 56], period: 16 },
  { id: 'silence', start: 60, end: 78, bpm: null, window: [72, 76], period: 24 },
  { id: 'irregular', start: 78, end: duration, bpm: null, window: [84, 102], period: 24 },
];
const tolerance = { bpm: 2, nativeCycleSeconds: .3, frequencySlope: .02, frequencyEpsilon: 1e-6,
  phaseResidualCycles: .015, sampleGapSeconds: 1, sampleIntervalMs: 50, maxSamples: 5000,
  confidence: .65, silenceEnergy: .01, minimumReliableSeconds: 2, earlyEofMarginSeconds: 2 };

function fixture() {
  const beats = [];
  for (const segment of segments.filter(value => value.bpm)) {
    for (let beat = 0; beat < (segment.end - segment.start) * segment.bpm / 60; beat++) {
      beats.push(segment.start + beat * 60 / segment.bpm);
    }
  }
  const irregular = segments.at(-1);
  const intervals = [.27, .84, .34, 1.2, .42, .99, .56, .31, 1.38, .7, .35];
  let position = irregular.start, interval = 0;
  while (position < irregular.end) { beats.push(position); position += intervals[interval++ % intervals.length]; }
  const samples = duration * rate;
  const wav = Buffer.alloc(44 + samples * 2);
  wav.write('RIFF', 0); wav.writeUInt32LE(wav.length - 8, 4); wav.write('WAVEfmt ', 8);
  wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20); wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(rate, 24); wav.writeUInt32LE(rate * 2, 28); wav.writeUInt16LE(2, 32); wav.writeUInt16LE(16, 34);
  wav.write('data', 36); wav.writeUInt32LE(samples * 2, 40);
  let beatIndex = -1, segmentIndex = 0;
  for (let index = 0; index < samples; index++) {
    const time = index / rate;
    while (segmentIndex + 1 < segments.length && time >= segments[segmentIndex].end) segmentIndex++;
    while (beatIndex + 1 < beats.length && beats[beatIndex + 1] <= time) beatIndex++;
    const sinceBeat = beatIndex < 0 ? Infinity : time - beats[beatIndex];
    const silent = segments[segmentIndex].id === 'silence';
    const pulse = !silent && sinceBeat < .04 ? .8 * Math.sin(2 * Math.PI * 1000 * sinceBeat) * (1 - sinceBeat / .04) : 0;
    const carrier = silent ? 0 : .035 * Math.sin(2 * Math.PI * 220 * time);
    wav.writeInt16LE(Math.round((pulse + carrier) * 32767), 44 + index * 2);
  }
  return { wav, metadata: { durationSeconds: duration, sampleRate: rate, channels: 1, bitsPerSample: 16,
    bytes: wav.length, sha256: crypto.createHash('sha256').update(wav).digest('hex'), segments,
    annotatedBeatSeconds: beats, origin: 'Node-generated sine pulses/carrier; synthetic test input, не аудиозапись.' } };
}

function sourceSamples(samples, sourceId) {
  const first = samples.find(value => value.sourceSessionId === sourceId);
  assert(first, 'No native source announcement observed');
  return samples.filter(value => value.observedAtMs >= first.observedAtMs)
    .map(value => ({ ...value, elapsed: (value.observedAtMs - first.observedAtMs) / 1000 }));
}
function normalized(value) {
  for (const key of ['observedAtMs', 'timestampSeconds', 'slowCycleSeconds', 'phase', 'energy', 'intensity', 'confidence']) {
    assert(Number.isFinite(value[key]), `Nonfinite motion ${key}`);
  }
  assert(value.timestampSeconds >= 0 && value.slowCycleSeconds > 0 && value.phase >= 0 && value.phase < 1);
  assert(['energy', 'intensity', 'confidence'].every(key => value[key] >= 0 && value[key] <= 1));
  assert(value.bpm === null || Number.isFinite(value.bpm) && value.bpm >= 40 && value.bpm <= 240);
  assert(/^[A-Za-z0-9._:-]{1,128}$/.test(value.sourceSessionId));
  assert(['autonomous', 'synced'].includes(value.mode));
  if (value.mode === 'synced') assert(value.bpm !== null && value.confidence >= tolerance.confidence && value.energy > tolerance.silenceEnergy);
  if (value.sourceSessionId === 'none') assert(value.mode === 'autonomous' && value.bpm === null);
}
function invariants(samples, sourceId) {
  const selected = sourceSamples(samples, sourceId);
  assert(selected.length >= 20, 'Too few passive phase observations');
  let maximumSlope = 0, maximumPhaseResidual = 0, largestGap = 0, ended = false;
  for (let index = 0; index < selected.length; index++) {
    const current = selected[index]; normalized(current);
    assert(current.sourceSessionId === sourceId || current.sourceSessionId === 'none', 'Unexpected/stale source UUID');
    if (current.sourceSessionId === 'none') {
      assert(current.elapsed >= duration - tolerance.earlyEofMarginSeconds, 'Source ended before fixture EOF'); ended = true;
    } else assert(!ended, 'Old source reappeared after EOF');
    if (!index) continue;
    const before = selected[index - 1];
    const seconds = (current.observedAtMs - before.observedAtMs) / 1000;
    assert(seconds > 0 && seconds <= tolerance.sampleGapSeconds, 'Insufficient observation coverage for phase claim');
    largestGap = Math.max(largestGap, seconds);
    const phaseDelta = (current.phase - before.phase + 1) % 1;
    const phaseLimit = seconds / Math.min(current.slowCycleSeconds, before.slowCycleSeconds);
    maximumPhaseResidual = Math.max(maximumPhaseResidual, phaseDelta - phaseLimit);
    assert(phaseDelta <= phaseLimit + tolerance.phaseResidualCycles, 'Visual phase discontinuity');
    // timestampSeconds — native runtime; browser performance.now используется только для интервалов наблюдения.
    if (current.sourceSessionId === before.sourceSessionId) {
      const nativeDelta = current.timestampSeconds - before.timestampSeconds;
      assert(nativeDelta >= 0, 'Native motion timestamp moved backwards');
      const frequencyDelta = Math.abs(1 / current.slowCycleSeconds - 1 / before.slowCycleSeconds);
      assert(frequencyDelta <= tolerance.frequencySlope * nativeDelta + tolerance.frequencyEpsilon, 'Frequency slope exceeds MotionPolicy');
      if (nativeDelta > 0) maximumSlope = Math.max(maximumSlope, frequencyDelta / nativeDelta);
    }
  }
  return { observations: selected.length, largestGapSeconds: largestGap, maximumFrequencySlope: maximumSlope, maximumPhaseResidualCycles: maximumPhaseResidual };
}
function checkpoint(samples, sourceId, segment) {
  const window = sourceSamples(samples, sourceId).filter(value => value.elapsed >= segment.window[0] && value.elapsed <= segment.window[1]);
  assert(window.length >= 20 && window.at(-1).elapsed - window[0].elapsed >= segment.window[1] - segment.window[0] - tolerance.sampleGapSeconds,
    `${segment.id}: incomplete observation window`);
  assert(window.every(value => value.sourceSessionId === sourceId), `${segment.id}: lost native source`);
  let longestReliable = 0, reliableStart = null;
  for (const value of window) {
    const reliable = value.mode === 'synced' && value.bpm !== null && value.confidence >= tolerance.confidence && value.energy > tolerance.silenceEnergy;
    if (segment.bpm) {
      if (reliable) {
        assert(Math.abs(value.bpm - segment.bpm) <= tolerance.bpm, `${segment.id}: confident BPM=${value.bpm} outside ±2`);
        reliableStart ??= value.observedAtMs;
        longestReliable = Math.max(longestReliable, (value.observedAtMs - reliableStart) / 1000);
      } else reliableStart = null;
    } else {
      assert(value.mode === 'autonomous' && value.bpm === null, `${segment.id}: fallback did not remain autonomous`);
      if (segment.id === 'silence') assert(value.energy <= tolerance.silenceEnergy, 'Silence produced nonsilent energy');
      if (segment.id === 'irregular') assert(value.confidence < tolerance.confidence, 'Irregular rhythm became falsely confident');
    }
  }
  if (segment.bpm) assert(longestReliable >= tolerance.minimumReliableSeconds, `${segment.id}: no sustained confident tempo`);
  else {
    let advance = 0;
    for (let index = 1; index < window.length; index++) advance += (window[index].phase - window[index - 1].phase + 1) % 1;
    assert(advance > .02, `${segment.id}: autonomous phase stopped`);
    if (segment.id === 'irregular') assert(window.some(value => value.energy > tolerance.silenceEnergy), 'Irregular test received no nonzero PCM energy');
  }
  const last = window.at(-1);
  assert(Math.abs(last.slowCycleSeconds - segment.period) <= tolerance.nativeCycleSeconds, `${segment.id}: cycle did not converge`);
  return { windowSeconds: segment.window, observations: window.length, sustainedReliableSeconds: longestReliable,
    last: { bpm: last.bpm, confidence: last.confidence, energy: last.energy, slowCycleSeconds: last.slowCycleSeconds, mode: last.mode } };
}

function selfCheck() {
  const { wav, metadata } = fixture();
  assert.equal(wav.toString('ascii', 0, 4), 'RIFF'); assert.equal(wav.toString('ascii', 8, 16), 'WAVEfmt ');
  assert.equal(wav.readUInt16LE(22), 1); assert.equal(wav.readUInt16LE(34), 16);
  assert.equal(wav.readUInt32LE(24), rate); assert.equal(wav.readUInt32LE(40), duration * rate * 2);
  for (let index = 60 * rate; index < 78 * rate; index++) assert.equal(wav.readInt16LE(44 + index * 2), 0);
  for (let index = 0; index < duration * rate; index++) assert(Math.abs(wav.readInt16LE(44 + index * 2)) <= .836 * 32767);
  const samples = []; let frequency = 1 / 24, phase = .9;
  for (let step = 0; step <= (duration + 4) * 20; step++) {
    const time = step / 20, segment = segments.find(value => time >= value.start && time < value.end);
    const target = segment?.bpm ? segment.bpm / (60 * 32) : 1 / 24;
    frequency += (target - frequency) * (1 - Math.exp(-.05 / 3)); phase = (phase + frequency * .05) % 1;
    samples.push({ observedAtMs: time * 1000, timestampSeconds: time + 100, sourceSessionId: segment ? 'synthetic-check' : 'none',
      bpm: segment?.bpm ?? null, slowCycleSeconds: 1 / frequency, phase, energy: segment?.id === 'silence' || !segment ? 0 : .2,
      intensity: .1, confidence: segment?.bpm ? .95 : 0, mode: segment?.bpm ? 'synced' : 'autonomous' });
  }
  invariants(samples, 'synthetic-check');
  for (const segment of segments) checkpoint(samples, 'synthetic-check', segment);
  const changed = (index, values) => samples.map((sample, current) => current === index ? { ...sample, ...values } : sample);
  assert.throws(() => invariants(changed(300, { phase: (samples[300].phase + .2) % 1 }), 'synthetic-check'));
  assert.throws(() => invariants(changed(300, { slowCycleSeconds: 5 }), 'synthetic-check'));
  assert.throws(() => invariants(changed(300, { timestampSeconds: 99 }), 'synthetic-check'));
  assert.throws(() => invariants(changed(300, { sourceSessionId: 'none', bpm: null, mode: 'autonomous' }), 'synthetic-check'));
  assert.throws(() => checkpoint(changed(300, { bpm: 100 }), 'synthetic-check', segments[0]));
  assert.throws(() => checkpoint(changed(1800, { bpm: 100, confidence: .95, mode: 'synced' }), 'synthetic-check', segments[3]));
  console.log(JSON.stringify({ result: 'pass', scope: 'tooling-only', nativeExecuted: false,
    fixture: { ...metadata, annotatedBeatSeconds: undefined, annotatedBeatCount: metadata.annotatedBeatSeconds.length },
    checks: ['WAV headers/bounds/zero silence', 'four annotated checkpoints', 'phase/slope/time/source positive and negative cases'] }, null, 2));
}

async function run() {
  const device = new AndroidDevice('motion-transitions');
  let cdp, failure, sourceId, originalScene, taskDirectory, taskFile, taskDeviceFile;
  let cursor = 0, pickerOpen = false, fixtureReaderMayExist = false, fixtureReaderClosed = false;
  const samples = [];
  const state = () => cdp.evaluate(inspect);
  async function drain() {
    const value = await cdp.evaluate(`(()=>{const p=window.__motionTransitions;return p?{samples:p.samples.slice(${cursor}),overflow:p.overflow,error:p.error}:null})()`);
    assert(value && !value.overflow && !value.error, `Passive observer failed: ${value?.error ?? 'missing/overflow'}`);
    samples.push(...value.samples); cursor += value.samples.length;
  }
  function savedMatches(baseline) {
    const raw = device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json']);
    assert(Buffer.byteLength(raw) <= 262144);
    const saved = JSON.parse(raw);
    assert.equal(saved.schemaVersion, 2); assert(Number.isSafeInteger(saved.revision) && saved.revision >= 0);
    // Native SessionSnapshot имеет schemaVersion/revision; restore ниже принимает только SceneSnapshot.
    assert.deepEqual(saved.shapes, baseline.shapes); assert.deepEqual(saved.settings, baseline.settings);
    return saved;
  }
  async function persistedMatches(expected) {
    let lastError;
    for (let attempt = 0; attempt < 40; attempt++) {
      try { return savedMatches(expected); } catch (error) { lastError = error; }
      await wait(250);
    }
    throw new Error(`Native store did not confirm scene: ${lastError?.message ?? 'unknown failure'}`);
  }
  function safeError(error) {
    let message = String(error?.message ?? error);
    for (const [value, replacement] of [[taskFile, '<fixture.wav>'], [taskDirectory, '<fixture-temp>'],
      [device.adb, 'adb'], [device.sdk, '<android-sdk>'], [process.env.USERPROFILE, '<user>']]) {
      if (value) message = message.replaceAll(value, replacement);
    }
    return message.slice(0, 500);
  }
  try {
    const { wav, metadata } = fixture();
    const filename = `music-orbs-transitions-${Date.now()}.wav`;
    taskDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'music-orbs-transitions-'));
    taskFile = path.join(taskDirectory, filename); taskDeviceFile = `/sdcard/Download/${filename}`;
    fs.writeFileSync(taskFile, wav);
    device.command(['push', taskFile, taskDeviceFile]);
    device.shell('am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', `file://${taskDeviceFile}`);
    device.report.fixture = { ...metadata, filename }; device.report.tolerances = tolerance;
    device.report.limits.push('Окна WAV привязаны приблизительно к первому наблюдаемому native source UUID; PlaybackPosition не экспортируется. Границы проверяются с запасом.',
      'Краткий неверный FeatureFrame не внедряется; проверяется реальный PCM tempo transition, не все подслучаи FT-16.',
      'CDP читает MotionSignal/snapshot; PCM и команды motion не внедряются. WAV находится только в TEMP/Downloads и не является записью.');
    await device.start(); cdp = await device.cdp();
    if ((await state()).motion.sourceSessionId !== 'none') {
      await device.openPanel(); await device.click('Стоп', { scroll: true }); await device.closePanel(); await wait(500);
    }
    assert.equal((await state()).motion.sourceSessionId, 'none', 'Audio stop precondition not reached');
    const candidate = structuredClone((await state()).snapshot);
    // Невалидный/неподдерживаемый store нельзя заменить подготовительной деформацией.
    // До подтверждения валидного v2 файла finally не выполняет restore/save.
    const originalPersisted = await persistedMatches(candidate);
    originalScene = candidate;
    device.write('original-scene.json', originalScene);
    device.write('original-persisted.json', originalPersisted);
    device.report.preparation = { originalCapturedAfterAudioStop: true, originalMusicSync: originalScene.settings.musicSync,
      originalHadDeformation: originalScene.shapes.some(shape => shape.controls.length), addedDeformation: false, enabledMusicSync: false };
    let baseline = structuredClone(originalScene);
    if (!baseline.shapes.some(shape => shape.controls.length)) {
      for (const x of [.5, .44, .56]) {
        device.shell('input', 'swipe', `${Math.round(device.width * x)}`, `${Math.round(device.height * .48)}`, `${Math.round(device.width * (x + .08))}`, `${Math.round(device.height * .45)}`, '650');
        await wait(400); baseline = (await state()).snapshot;
        if (baseline.shapes.some(shape => shape.controls.length)) break;
      }
    }
    assert(baseline.shapes.some(shape => shape.controls.length), 'Need nonempty deformation baseline');
    device.report.preparation.addedDeformation = !device.report.preparation.originalHadDeformation;
    if (!baseline.settings.musicSync) {
      await device.openPanel(); await device.click('Синхронизация с музыкой', { scroll: true }); await device.closePanel();
      device.report.preparation.enabledMusicSync = true;
    }
    baseline = (await state()).snapshot; assert(baseline.settings.musicSync);
    device.write('prepared-baseline.json', baseline);
    device.write('prepared-persisted-before.json', await persistedMatches(baseline));
    await cdp.evaluate(`(()=>{const p={samples:[],overflow:false,error:null};window.__motionTransitions=p;
      const capture=()=>{try{if(p.samples.length>=${tolerance.maxSamples}){p.overflow=true;clearInterval(p.timer);return}
      p.samples.push({observedAtMs:performance.now(),...window.musicOrbs.visualState()})}catch(e){p.error=String(e)}};
      capture();p.timer=setInterval(capture,${tolerance.sampleIntervalMs});return true})()`);
    await device.openPanel(); await device.click('Выбрать аудиофайл', { scroll: true }); pickerOpen = true;
    fixtureReaderMayExist = true;
    await device.chooseDocument(filename); pickerOpen = false;
    if (device.isPanelOpen()) await device.closePanel();
    const completed = new Set();
    const deadline = Date.now() + (duration + 40) * 1000;
    while (Date.now() < deadline) {
      await drain();
      if (!sourceId) {
        const firstSource = samples.find(value => value.sourceSessionId !== 'none');
        if (firstSource) {
          sourceId = firstSource.sourceSessionId;
          device.report.sourceSessionId = sourceId;
          device.report.observationAnchor = { browserObservedAtMs: firstSource.observedAtMs, nativeTimestampSeconds: firstSource.timestampSeconds,
            method: 'First sampled native source UUID, not exact AudioTrack position' };
        }
      }
      if (sourceId) {
        const selected = sourceSamples(samples, sourceId), last = selected.at(-1);
        for (const segment of segments) {
          if (!completed.has(segment.id) && last.elapsed >= segment.window[1] + .5) {
            const result = checkpoint(samples, sourceId, segment); invariants(samples, sourceId);
            const current = await state();
            assert.deepEqual(current.snapshot, baseline, `${segment.id}: audio changed scene snapshot`);
            assert(current.canvas.width > 0 && current.canvas.height > 0 && current.glError === 0);
            device.write(`${segment.id}.json`, { result, actual: current }); device.screenshot(segment.id);
            device.step(segment.id, segment.bpm ? `Native PCM дал BPM=${result.last.bpm.toFixed(3)}, period=${result.last.slowCycleSeconds.toFixed(3)}с; source/scene сохранены.`
              : `Native ${segment.id} → autonomous, confidence=${result.last.confidence.toFixed(3)}, energy=${result.last.energy.toFixed(5)}, фаза продолжилась.`);
            completed.add(segment.id);
          }
        }
        if (last.sourceSessionId === 'none' && completed.size === segments.length) {
          const eof = selected.find(value => value.sourceSessionId === 'none');
          if (last.observedAtMs - eof.observedAtMs >= 2200) {
            let phaseAdvance = 0;
            const after = selected.filter(value => value.observedAtMs >= eof.observedAtMs);
            for (let index = 1; index < after.length; index++) phaseAdvance += (after[index].phase - after[index - 1].phase + 1) % 1;
            assert(phaseAdvance > .02, 'EOF stopped autonomous phase');
            const final = await state(); assert.deepEqual(final.snapshot, baseline);
            device.write('after-eof.json', final); device.write('prepared-persisted-after.json', await persistedMatches(baseline));
            device.step('eof-autonomous-snapshot', 'Настоящий EOF инвалидировал UUID; автономная фаза продолжилась, подготовленный snapshot/native store сохранились.');
            break;
          }
        }
      }
      await wait(300);
    }
    assert.equal(completed.size, segments.length, 'Not all annotated native stages completed');
    assert(device.report.steps.some(value => value.id === 'eof-autonomous-snapshot'), 'Native EOF not observed before deadline');
    const checks = invariants(samples, sourceId); device.write('motion-invariants.json', checks);
    device.step('phase-and-frequency', `${checks.observations} наблюдений: фаза непрерывна, max frequency slope=${checks.maximumFrequencySlope.toFixed(6)}cycles/s².`);
  } catch (error) { failure = error; }
  finally {
    device.report.cleanup = { originalSceneCaptured: !!originalScene, originalSceneRestored: false,
      fixtureReaderClosed: !fixtureReaderMayExist, downloadsFixtureRemoved: !taskDeviceFile,
      hostFixtureRemoved: !taskFile, emptyTempDirectoryRemoved: !taskDirectory };
    if (cdp) {
      try { await drain(); } catch { }
      try { await cdp.evaluate('(()=>{const p=window.__motionTransitions;if(p)clearInterval(p.timer);delete window.__motionTransitions;return true})()'); } catch { }
      try {
        if (pickerOpen) { device.shell('input', 'keyevent', '4'); await wait(300); }
        if ((await state()).motion.sourceSessionId !== 'none') { await device.openPanel(); await device.click('Стоп', { scroll: true }); }
        if (device.isPanelOpen()) await device.closePanel();
      } catch (error) { device.report.cleanupStopWarning = safeError(error); }
    }
    // Восстанавливается исходная сцена, а не подготовленный nonempty/musicSync=true baseline.
    // CDP restore вызывает обычный scene-dirty/save; revision Native SessionSnapshot не откатывается.
    if (originalScene) {
      try {
        try { await state(); } catch { cdp?.close(); await device.start(); cdp = await device.cdp(); }
        await cdp.evaluate(`window.musicOrbs.command(${JSON.stringify({ type: 'restore', snapshot: originalScene })})`);
        assert.deepEqual((await state()).snapshot, originalScene);
        device.write('restored-original-before-restart.json', await persistedMatches(originalScene));
        cdp.close(); cdp = null;
        device.shell('am', 'force-stop', appId); fixtureReaderClosed = true;
        await device.start(); cdp = await device.cdp();
        for (let attempt = 0; attempt < 40; attempt++) {
          const opening = await state();
          try { assert.deepEqual(opening.snapshot, originalScene); break; } catch { }
          await wait(150);
        }
        const restored = await state();
        assert.equal(restored.motion.sourceSessionId, 'none'); assert.deepEqual(restored.snapshot, originalScene);
        device.write('restored-original-scene.json', restored.snapshot);
        device.write('restored-original-persisted.json', await persistedMatches(originalScene));
        device.report.cleanup.originalSceneRestored = true;
      } catch (error) { device.report.cleanupRestoreFailure = safeError(error); failure ||= error; }
    }
    // Только собственный сгенерированный WAV; удаление после закрытия local reader.
    if (fixtureReaderMayExist && !fixtureReaderClosed) {
      try { device.shell('am', 'force-stop', appId); fixtureReaderClosed = true; }
      catch (error) { device.report.cleanupReaderFailure = safeError(error); failure ||= error; }
    }
    device.report.cleanup.fixtureReaderClosed = !fixtureReaderMayExist || fixtureReaderClosed;
    if (taskDeviceFile && device.report.cleanup.fixtureReaderClosed) {
      try {
        assert(/^\/sdcard\/Download\/music-orbs-transitions-\d+\.wav$/.test(taskDeviceFile));
        device.shell('rm', '-f', '--', taskDeviceFile);
        device.report.cleanup.downloadsFixtureRemoved = true;
      } catch (error) { device.report.cleanupDownloadsFailure = safeError(error); failure ||= error; }
    }
    try {
      if (taskFile) {
        assert(path.dirname(taskFile) === taskDirectory && /^music-orbs-transitions-\d+\.wav$/.test(path.basename(taskFile)));
        if (fs.existsSync(taskFile)) fs.unlinkSync(taskFile);
        device.report.cleanup.hostFixtureRemoved = true;
      }
      if (taskDirectory) {
        assert(path.dirname(taskDirectory) === path.resolve(os.tmpdir()) && path.basename(taskDirectory).startsWith('music-orbs-transitions-'));
        fs.rmdirSync(taskDirectory); // Только пустая task-created папка, без recursive delete.
        device.report.cleanup.emptyTempDirectoryRemoved = true;
      }
    } catch (error) { device.report.cleanupHostFailure = safeError(error); failure ||= error; }
    cdp?.close();
    if (originalScene && !device.report.cleanup.originalSceneRestored) failure ||= new Error('Original scene cleanup was not confirmed');
    if (taskDeviceFile && !device.report.cleanup.downloadsFixtureRemoved) failure ||= new Error('Fixture cleanup was not confirmed');
    device.write('motion-samples.json', samples); device.finish(failure);
  }
}

if (process.argv.includes('--help')) {
  console.log('node scripts/android-motion-transitions.mjs [--serial emulator-5554]\n--self-check: только генерация/проверка WAV и проверяющих функций, без adb/device/файлов.\nОбычный запуск: native picker → PCM90→120 → silence → irregular → EOF; требуется свободный device lease.');
} else if (process.argv.includes('--self-check')) selfCheck();
else await run();
