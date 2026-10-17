import fs from 'node:fs';
import path from 'node:path';
import assert from 'node:assert/strict';
import { AndroidDevice, root, appId, inspect, wait } from './android-device.mjs';

if (process.argv.includes('--help')) { console.log('node scripts/android-focus-smoke.mjs [--serial emulator-5554] [--noisy]\nПосле G3 и установки debug focus probe: настоящий LOSS_TRANSIENT → local pause → abandon без autoresume → явный resume. --noisy пробует shell AUDIO_BECOMING_NOISY; denied/неподтверждённая доставка отмечается skipped.'); process.exit(0); }
const device = new AndroidDevice('audio-focus');
const component = `${appId}/musicorbs.platform.audio.AudioFocusProbeService`;
let cdp, failure;
const state = () => cdp.evaluate(inspect);
function abandonProbe() {
  try { device.shell('am', 'stopservice', '-n', component); return 'explicit stopservice'; }
  catch (error) {
    // UIAutomator может занять дольше holdMs: служба тогда уже abandon/stopSelf.
    if (String(error.stderr).includes('was not running')) return 'automatic abandon after holdMs';
    throw error;
  }
}
async function until(predicate, label, seconds = 12) {
  const deadline = Date.now() + seconds * 1000;
  let latest;
  while (Date.now() < deadline) { latest = await state(); if (predicate(latest)) return latest; await wait(150); }
  device.write('last-state.json', latest); throw new Error(`Condition timed out: ${label}`);
}
async function noisyBroadcast(expected, seconds = 3) {
  let output;
  try { output = device.shell('am', 'broadcast', '-a', 'android.media.AUDIO_BECOMING_NOISY', '-p', appId); }
  catch (error) { output = `${error.stdout ?? ''}\n${error.stderr ?? ''}`; }
  device.write(`noisy-${expected}-broadcast.txt`, output);
  if (/SecurityException|Permission Denial|not allowed|protected broadcast/i.test(output)) return null;
  const deadline = Date.now() + seconds * 1000;
  while (Date.now() < deadline) {
    const value = await state();
    if (expected === 'local' ? value.motion.mode === 'autonomous' : value.motion.sourceSessionId === 'none') return value;
    await wait(150);
  }
  return null; // am result=0 не подтверждает доставку NOT_EXPORTED receiver из shell.
}
async function checkNoisy(baseline, sourceId) {
  const local = await noisyBroadcast('local');
  if (!local) {
    device.step('noisy-broadcast', 'Shell не смог подтвердить доставку системного AUDIO_BECOMING_NOISY (protected broadcast/NOT_EXPORTED receiver). Физическое отключение гарнитуры не проверено.', 'skipped');
    return;
  }
  assert.equal(local.motion.sourceSessionId, sourceId);
  assert.deepEqual(local.snapshot.shapes, baseline.shapes);
  device.write('noisy-local-paused.json', local);
  device.step('noisy-local-pause', 'Пакетный shell AUDIO_BECOMING_NOISY приостановил local source, UUID/поле сохранены. Это синтетическое событие, не физическая гарнитура.');
  await device.click('Выбрать микрофон', { scroll: true });
  // Если RECORD_AUDIO ещё не выдан этому APK, выбираем настоящий system grant.
  const grant = device.nodes().find(node => /\/permission_allow_foreground_only_button$|\/permission_allow_button$/.test(node['resource-id']));
  if (grant) { device.tap(grant); await wait(500); }
  const mic = await until(value => value.motion.sourceSessionId !== 'none' && value.motion.sourceSessionId !== sourceId, 'explicit microphone for noisy test', 7);
  assert.deepEqual(mic.snapshot.shapes, baseline.shapes);
  device.write('noisy-microphone-active.json', mic);
  const stopped = await noisyBroadcast('microphone');
  if (!stopped) { device.step('noisy-microphone-stop', 'Доставка shell события для mic не подтверждена; физический noisy path остаётся непроверенным.', 'skipped'); return; }
  assert.deepEqual(stopped.snapshot.shapes, baseline.shapes);
  device.write('noisy-microphone-stopped.json', stopped);
  device.step('noisy-microphone-stop', 'Shell AUDIO_BECOMING_NOISY остановил явный microphone, поле сохранено; физическая гарнитура не проверялась.');
}
try {
  // Фикстура создана G3 из синусоид, а не записана из источника пользователя.
  const results = path.join(root, 'tests', 'device', 'results');
  const previous = fs.readdirSync(results).filter(name => name.startsWith('g3-audio-')).sort().reverse()
    .map(name => { try { return JSON.parse(fs.readFileSync(path.join(results, name, 'report.json'), 'utf8')); } catch { return null; } })
    .find(report => report?.result === 'pass' && report.fixture?.sha256 === '9a35105ae1d1b32c113873db770fd161e9b0e8668ba3946543704da688a149ae');
  assert(previous, 'Сначала нужен успешный android-audio-smoke с синтетической фикстурой в Downloads.');
  const filename = previous.fixture.filename;
  assert(/^music-orbs-90bpm-\d+\.wav$/.test(filename));
  device.shell('test', '-f', `/sdcard/Download/${filename}`);
  device.report.fixture = previous.fixture;
  await device.start(); cdp = await device.cdp();
  let baseline = (await state()).snapshot;
  // Android instrumentation может удалить target APK/userdata: создаём новое
  // нетривиальное поле настоящим касанием, не подменяя snapshot через CDP.
  if (!baseline.shapes.some(shape => shape.controls.length)) {
    device.shell('input', 'swipe', `${Math.round(device.width * .5)}`, `${Math.round(device.height * .48)}`, `${Math.round(device.width * .58)}`, `${Math.round(device.height * .45)}`, '650');
    await wait(400); baseline = (await state()).snapshot;
  }
  assert(baseline.shapes.some(shape => shape.controls.length), 'Нужна сохранённая непустая деформация.');
  device.write('baseline.json', baseline);
  await device.openPanel(); await device.click('Выбрать аудиофайл', { scroll: true }); await device.chooseDocument(filename);
  const playing = await until(value => value.motion.mode === 'synced' && Math.abs(value.motion.bpm - 90) <= 2, 'synthetic playback', 22);
  device.write('before-focus.json', playing);
  const sourceId = playing.motion.sourceSessionId;
  assert.notEqual(sourceId, 'none');
  const start = device.shell('am', 'startservice', '-n', component, '--ei', 'holdMs', '5000');
  device.write('probe-start.txt', start);
  assert(!start.includes('Error'), 'Debug focus probe did not start');
  const paused = await until(value => value.motion.mode === 'autonomous', 'focus loss pause', 5);
  assert.equal(paused.motion.sourceSessionId, sourceId);
  assert.deepEqual(paused.snapshot.shapes, baseline.shapes);
  if (device.isPanelOpen()) await device.closePanel();
  await device.find('Пауза после изменения аудиовыхода');
  device.write('focus-paused.json', await state()); device.screenshot('focus-paused');
  device.step('real-focus-loss', 'Debug service запросил настоящий AUDIOFOCUS_GAIN_TRANSIENT без окна; Main приостановил local playback, UUID и поле сохранены.');
  device.report.focusRelease = abandonProbe();
  await wait(1700);
  const abandoned = await state();
  const focusLog = device.shell('logcat', '-d', '-s', 'MusicOrbsFocusProbe:I', '*:S');
  device.write('probe-log.txt', focusLog);
  assert(focusLog.includes('Transient focus granted'), 'System did not grant transient focus to the probe');
  assert.equal(abandoned.motion.mode, 'autonomous');
  assert.equal(abandoned.motion.sourceSessionId, sourceId);
  assert(abandoned.motion.energy <= playing.motion.energy + .001);
  assert.deepEqual(abandoned.snapshot.shapes, baseline.shapes);
  device.write('after-abandon.json', abandoned);
  await device.openPanel(); await device.find('Продолжить', { scroll: true });
  device.step('no-focus-auto-resume', 'После abandon playback остаётся на паузе, музыка не возобновилась автоматически; native UI предлагает «Продолжить».');
  await device.click('Продолжить');
  const resumed = await until(value => value.motion.mode === 'synced' && Math.abs(value.motion.bpm - 90) <= 2 && value.motion.energy > .01, 'explicit focus resume', 14);
  assert.equal(resumed.motion.sourceSessionId, sourceId);
  assert.deepEqual(resumed.snapshot.shapes, baseline.shapes);
  device.write('explicit-resumed.json', resumed);
  device.step('explicit-focus-resume', `Явное «Продолжить» вернуло свежий PCM: BPM=${resumed.motion.bpm.toFixed(3)}, energy=${resumed.motion.energy.toFixed(4)}, тот же sourceSessionId и постоянное поле.`);
  if (process.argv.includes('--noisy')) await checkNoisy(baseline, sourceId);
  if ((await state()).motion.sourceSessionId !== 'none') {
    await device.click('Стоп', { scroll: true });
    await until(value => value.motion.sourceSessionId === 'none', 'cleanup stop', 5);
  }
  if (device.isPanelOpen()) await device.closePanel();
} catch (error) { failure = error; }
finally { try { device.shell('am', 'stopservice', '-n', component); } catch { } cdp?.close(); device.finish(failure); }
