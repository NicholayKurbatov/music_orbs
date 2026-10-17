import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import assert from 'node:assert/strict';
import { AndroidDevice, appId, activity, inspect, wait } from './android-device.mjs';

if (process.argv.includes('--help')) { console.log('node scripts/android-audio-smoke.mjs [--serial emulator-5554]\nСистемный picker → synthetic WAV 90 BPM → native DSP → bridge → renderer; pause/resume/sync/stop/background/cancel/invalid file.'); process.exit(0); }
const device = new AndroidDevice('g3-audio');
const stamp = Date.now();
const filename = `music-orbs-90bpm-${stamp}.wav`;
const invalidName = `music-orbs-invalid-${stamp}.wav`;
const fixtureDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'music-orbs-g3-'));
let cdp;
let failure;
const sameFields = (left, right) => assert.deepEqual(left.shapes, right.shapes, 'Audio operation changed controls/seed/revision');

function wave() {
  const rate = 48000, duration = 60, samples = rate * duration;
  const data = Buffer.alloc(44 + samples * 2);
  data.write('RIFF', 0); data.writeUInt32LE(data.length - 8, 4); data.write('WAVEfmt ', 8);
  data.writeUInt32LE(16, 16); data.writeUInt16LE(1, 20); data.writeUInt16LE(1, 22);
  data.writeUInt32LE(rate, 24); data.writeUInt32LE(rate * 2, 28); data.writeUInt16LE(2, 32); data.writeUInt16LE(16, 34);
  data.write('data', 36); data.writeUInt32LE(samples * 2, 40);
  for (let index = 0; index < samples; index++) {
    const time = index / rate, inBeat = time % (60 / 90);
    const beat = inBeat < .04 ? .8 * Math.sin(2 * Math.PI * 1000 * inBeat) * (1 - inBeat / .04) : 0;
    const bed = .035 * Math.sin(2 * Math.PI * 220 * time);
    data.writeInt16LE(Math.round((beat + bed) * 32767), 44 + index * 2);
  }
  return data;
}
async function state() {
  try { return await cdp.evaluate(inspect); }
  catch { cdp.close(); cdp = await device.cdp(); return cdp.evaluate(inspect); }
}
async function until(predicate, description, seconds = 20) {
  const deadline = Date.now() + seconds * 1000;
  let latest;
  while (Date.now() < deadline) {
    latest = await state();
    if (predicate(latest)) return latest;
    await wait(250);
  }
  device.write('last-state.json', latest);
  throw new Error(`Condition timed out: ${description}; mode=${latest?.motion?.mode}; bpm=${latest?.motion?.bpm}; period=${latest?.motion?.slowCycleSeconds}`);
}
async function panelAudio() { await device.openPanel(); }
async function chooseFile(name) { await device.click('Выбрать аудиофайл', { scroll: true }); await device.chooseDocument(name); }
async function observeStart() {
  await cdp.evaluate(`(()=>{window.__audioSmoke=[];clearInterval(window.__audioSmokeTimer);window.__audioSmokeTimer=setInterval(()=>{const v=window.musicOrbs.visualState();if(window.__audioSmoke.length<4096)window.__audioSmoke.push({now:performance.now(),...v})},50);return true})()`);
}
async function checkSamples() {
  const samples = await cdp.evaluate('window.__audioSmoke || []');
  device.write('motion-samples.json', samples);
  assert(samples.length > 20, 'Too few phase observations');
  for (let index = 1; index < samples.length; index++) {
    const before = samples[index - 1], after = samples[index];
    const seconds = (after.now - before.now) / 1000;
    const delta = (after.phase - before.phase + 1) % 1;
    const maximum = seconds / Math.min(before.slowCycleSeconds, after.slowCycleSeconds) + .015;
    assert(delta <= maximum, `Discontinuous visual phase: delta=${delta}, allowed=${maximum}`);
  }
  device.step('phase-continuity', `${samples.length} наблюдений: нет скачка фазы сверх elapsed/minPeriod + 0.015.`);
}

try {
  const wav = wave();
  fs.writeFileSync(path.join(fixtureDirectory, filename), wav);
  fs.writeFileSync(path.join(fixtureDirectory, invalidName), 'This is intentionally invalid WAV input.');
  for (const name of [filename, invalidName]) {
    device.command(['push', path.join(fixtureDirectory, name), `/sdcard/Download/${name}`]);
    device.shell('am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', `file:///sdcard/Download/${name}`);
  }
  device.report.fixture = { filename, bpm: 90, sampleRate: 48000, channels: 1, durationSeconds: 60, bytes: wav.length, sha256: crypto.createHash('sha256').update(wav).digest('hex'), origin: 'Node-generated sine pulses and quiet carrier; не запись аудио.' };
  device.report.tolerances = { bpm: 2, steadyCycleSeconds: .3, phaseResidualCycles: .015 };
  await device.start();
  cdp = await device.cdp();
  if (device.isPanelOpen()) await device.closePanel();
  let initial = await state();
  let baselineGesture = false;
  assert(initial.canvas.width > 0 && initial.canvas.height > 0 && initial.glError === 0);
  // Реальный жест создаёт непустую форму для последующих инвариантов источника.
  for (const x of [.5, .44, .56]) {
    const before = await state();
    device.shell('input', 'swipe', `${Math.round(device.width * x)}`, `${Math.round(device.height * .48)}`, `${Math.round(device.width * (x + .08))}`, `${Math.round(device.height * .45)}`, '650');
    await wait(350);
    initial = await state();
    if (JSON.stringify(before.snapshot.shapes) !== JSON.stringify(initial.snapshot.shapes) && initial.snapshot.shapes.some(shape => shape.controls.length)) { baselineGesture = true; break; }
  }
  assert(baselineGesture, 'Cannot establish deformed baseline by real drag');
  const baseline = initial.snapshot;
  device.write('baseline.json', initial);
  await panelAudio();
  await chooseFile(filename);
  await observeStart();
  const synced = await until(value => value.motion.mode === 'synced' && Math.abs(value.motion.bpm - 90) <= 2 && Math.abs(value.motion.slowCycleSeconds - 60 / 90 * 32) <= .3, '90 BPM and cycle convergence', 22);
  assert(synced.motion.sourceSessionId !== 'none' && synced.motion.energy > .01);
  assert(synced.glError === 0);
  sameFields(baseline, synced.snapshot);
  device.write('playing-synced.json', synced); device.screenshot('playing-synced');
  device.step('ui-picker-pcm-dsp-bridge', `Настоящий OpenDocument: BPM=${synced.motion.bpm.toFixed(3)}, period=${synced.motion.slowCycleSeconds.toFixed(3)}, energy=${synced.motion.energy.toFixed(4)}.`);
  const firstSource = synced.motion.sourceSessionId;
  const advancing = await state(); await wait(700); const advanced = await state();
  assert((advanced.motion.phase - advancing.motion.phase + 1) % 1 > .005, 'Renderer phase did not advance');

  await device.click('Пауза', { scroll: true });
  const paused = await until(value => value.motion.mode === 'autonomous', 'pause fallback', 5);
  await wait(800);
  sameFields(baseline, paused.snapshot);
  await device.click('Продолжить');
  const resumed = await until(value => value.motion.mode === 'synced' && Math.abs(value.motion.bpm - 90) <= 2, 'resume fresh PCM', 12);
  assert.equal(resumed.motion.sourceSessionId, firstSource);
  sameFields(baseline, resumed.snapshot);
  device.write('resumed.json', resumed);
  device.step('pause-resume', 'Пауза переводит движение в autonomous; resume даёт свежий PCM и тот же sourceSessionId без потери формы.');

  await device.click('Синхронизация с музыкой', { scroll: 'up' });
  const off = await until(value => !value.snapshot.settings.musicSync && value.motion.mode === 'autonomous', 'sync disabled', 5);
  await wait(1200);
  const offLater = await state();
  assert(offLater.motion.slowCycleSeconds >= off.motion.slowCycleSeconds - .01, 'Autonomous period did not approach 24');
  sameFields(baseline, offLater.snapshot);
  device.write('sync-off.json', offLater);
  await device.click('Синхронизация с музыкой');
  const on = await until(value => value.snapshot.settings.musicSync && value.motion.mode === 'synced' && Math.abs(value.motion.bpm - 90) <= 2, 'sync enabled', 8);
  sameFields(baseline, on.snapshot);
  device.write('sync-on.json', on);
  device.step('sync-off-on', 'Переключение выполнено native Switch; автономный/музыкальный режимы и постоянное поле подтверждены CDP.');

  await device.click('Стоп', { scroll: true });
  const stopped = await until(value => value.motion.sourceSessionId === 'none' && value.motion.mode === 'autonomous', 'explicit stop', 5);
  sameFields(baseline, stopped.snapshot);
  device.write('stopped.json', stopped);
  device.step('stop-autonomous', 'Явный Стоп инвалидировал источник, форма сохранена, движение продолжилось.');
  await checkSamples();
  await cdp.evaluate('clearInterval(window.__audioSmokeTimer);true');

  await chooseFile(filename);
  const again = await until(value => value.motion.sourceSessionId !== 'none' && value.motion.energy > .01, 'second local session starts', 5);
  assert.notEqual(again.motion.sourceSessionId, firstSource);
  device.shell('input', 'keyevent', 'KEYCODE_HOME'); await wait(700);
  device.shell('am', 'start', '-W', '-n', activity); await wait(700);
  const background = await until(value => value.motion.sourceSessionId === 'none' && value.motion.mode === 'autonomous', 'background stops source', 5);
  sameFields(baseline, background.snapshot);
  device.write('after-background.json', background);
  device.step('audio-background', 'Второй UUID источника подтверждён; HOME остановил audio, возврат не перезапустил его скрытно.');
  // Bottom sheet переживает HOME. Закрываем его перед отдельной проверкой отмены.
  if (device.isPanelOpen()) await device.closePanel();
  await panelAudio();
  await device.click('Выбрать аудиофайл', { scroll: true });
  device.shell('input', 'keyevent', '4'); await wait(500);
  if (device.isPanelOpen()) await device.closePanel();
  await device.find('Выбор файла отменён', { attempts: 5 });
  sameFields(baseline, (await state()).snapshot);
  device.step('picker-cancel', 'Системный picker отменён; форма сохранена и источник не запущен.');
  try {
    await panelAudio();
    await chooseFile(invalidName);
    await device.closePanel();
    await device.find('Источник недоступен', { attempts: 6 });
    const invalid = await until(value => value.motion.sourceSessionId === 'none', 'unsupported source rejected', 5);
    sameFields(baseline, invalid.snapshot);
    device.write('unsupported.json', invalid);
    device.step('unsupported-file', 'Файл .wav с неверным содержимым выбран через picker, отклонён; форма сохранена.');
  } catch (error) {
    if (!String(error.message).includes(`UI element missing: ${invalidName}`)) throw error;
    device.step('unsupported-file', 'Системный MIME filter не показал неверный WAV; этот UI case не выполнен.', 'skipped');
    device.shell('input', 'keyevent', '4');
  }
  device.screenshot('completed');
} catch (error) { failure = error; }
finally {
  if (cdp) {
    try { device.write('motion-samples.json', await cdp.evaluate('window.__audioSmoke || []')); await cdp.evaluate('clearInterval(window.__audioSmokeTimer);true'); } catch { }
    cdp.close();
  }
  device.finish(failure);
}
