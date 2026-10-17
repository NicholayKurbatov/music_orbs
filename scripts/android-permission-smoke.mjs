import assert from 'node:assert/strict';
import { AndroidDevice, appId, activity, inspect, wait } from './android-device.mjs';

if (process.argv.includes('--help')) { console.log('node scripts/android-permission-smoke.mjs [--serial emulator-5554]\nMain UI: RECORD_AUDIO denial → explicit microphone grant/indicator → runtime revoke/restart → no hidden restart.'); process.exit(0); }
const device = new AndroidDevice('permissions');
const captureOnly = process.argv.includes('--capture-cancel-only');
let cdp;
let failure;
async function state() { return cdp.evaluate(inspect); }
async function until(predicate, label, seconds = 8) {
  const until = Date.now() + seconds * 1000;
  let latest;
  while (Date.now() < until) { latest = await state(); if (predicate(latest)) return latest; await wait(250); }
  device.write('last-state.json', latest); throw new Error(`Condition timed out: ${label}`);
}
async function permission(allow) {
  for (let attempt = 0; attempt < 7; attempt++) {
    const button = device.nodes().find(node => new RegExp(allow ? '/permission_allow_foreground_only_button$|/permission_allow_button$' : '/permission_deny_button$|/permission_deny_and_dont_ask_again_button$').test(node['resource-id']));
    if (button) { device.tap(button); await wait(600); return; }
    await wait(200);
  }
  throw new Error(`System RECORD_AUDIO ${allow ? 'allow' : 'deny'} button missing`);
}
async function closePanelIfNeeded() { if (device.isPanelOpen()) await device.closePanel(); }
async function panelAction(label) { await device.openPanel(); await device.click(label, { scroll: true }); }
function noService() { assert(!device.shell('dumpsys', 'activity', 'services', appId).includes('CaptureProjectionService'), 'Capture FGS remained active'); }
async function captureCancel(baseline) {
  await panelAction('Захват внешнего воспроизведения');
  let cancel;
  for (let attempt = 0; attempt < 6 && !cancel; attempt++) {
    cancel = device.nodes().find(node => node['resource-id'] === 'android:id/button2' || /^(Cancel|Отмена)$/i.test(node.text));
    if (!cancel) await wait(200);
  }
  assert(cancel, 'System MediaProjection Cancel button missing');
  device.tap(cancel); await wait(500); await closePanelIfNeeded();
  await device.find('Захват не разрешён. Можно выбрать локальный файл.');
  const denied = await until(value => value.motion.sourceSessionId === 'none', 'Main capture consent canceled');
  assert.deepEqual(denied.snapshot.shapes, baseline.shapes);
  assert(!device.nodes().some(node => node['content-desc'] === 'Выбран микрофон'));
  noService();
  device.write('main-capture-canceled.json', denied); device.screenshot('main-capture-canceled');
  device.step('main-capture-consent-canceled', 'Main native Capture → настоящий MediaProjection Cancel: нет источника, mic fallback, индикатора и capture FGS; постоянная форма сохранена.');
}

try {
  // Только выданный тесту AVD: revoke может завершить процесс приложения.
  if (!captureOnly) {
    device.shell('pm', 'revoke', appId, 'android.permission.RECORD_AUDIO');
    device.shell('pm', 'clear-permission-flags', appId, 'android.permission.RECORD_AUDIO', 'user-set', 'user-fixed');
  }
  await device.start(); cdp = await device.cdp();
  const baseline = (await state()).snapshot;
  assert(baseline.shapes.some(shape => shape.controls.length), 'Permission smoke requires a persisted deformed baseline');
  device.write('baseline.json', baseline);

  if (captureOnly) {
    // Отдельный повтор Main routing после полного permission smoke: grant — только подготовка AVD.
    device.shell('pm', 'grant', appId, 'android.permission.RECORD_AUDIO');
    device.report.precondition = 'RECORD_AUDIO granted by adb for isolated MediaProjection cancellation; no microphone started.';
    await captureCancel(baseline);
  } else {

  await panelAction('Захват внешнего воспроизведения');
  await permission(false);
  await closePanelIfNeeded();
  await device.find('Доступ к аудио не разрешён. Сцена продолжает двигаться.');
  const denied = await until(value => value.motion.sourceSessionId === 'none', 'capture RECORD_AUDIO denied');
  assert.deepEqual(denied.snapshot.shapes, baseline.shapes);
  assert(!device.nodes().some(node => node['content-desc'] === 'Выбран микрофон'));
  noService();
  device.write('record-denied.json', denied); device.screenshot('record-denied');
  device.step('capture-record-permission-denied', 'Настоящий системный RECORD_AUDIO dialog отклонён через Main; нет источника, mic fallback или capture FGS; форма сохранена.');

  await panelAction('Выбрать микрофон');
  await permission(true);
  await closePanelIfNeeded();
  await device.find('Выбран микрофон', { attempts: 7 });
  const microphone = await until(value => value.motion.sourceSessionId !== 'none', 'explicit microphone becomes active');
  assert.deepEqual(microphone.snapshot.shapes, baseline.shapes);
  device.write('microphone-active.json', microphone); device.screenshot('microphone-active');
  device.step('explicit-microphone-indicator', 'Явный выбор и системный grant включили microphone UUID и видимый индикатор. Амплитуда физического микрофона не утверждается.');

  const beforeRevokePid = device.shell('pidof', appId).trim();
  assert.notEqual((await state()).motion.sourceSessionId, 'none', 'Revoke must interrupt an active microphone');
  device.shell('pm', 'revoke', appId, 'android.permission.RECORD_AUDIO');
  await wait(500);
  let afterRevokePid = '';
  try { afterRevokePid = device.shell('pidof', appId).trim(); } catch { }
  device.report.revocation = { permission: 'android.permission.RECORD_AUDIO', beforeRevokePid, afterRevokePid, processTerminated: !afterRevokePid };
  cdp.close(); cdp = null;
  await device.start(); cdp = await device.cdp();
  const restarted = await until(value => value.motion.sourceSessionId === 'none' && value.motion.mode === 'autonomous', 'restart without microphone');
  assert.deepEqual(restarted.snapshot.shapes, baseline.shapes);
  assert(!device.nodes().some(node => node['content-desc'] === 'Выбран микрофон'));
  noService();
  device.write('after-revoke-restart.json', restarted); device.screenshot('after-revoke-restart');
  device.step('runtime-revoke-restart', `RECORD_AUDIO отозван; processTerminated=${!afterRevokePid}; после явного запуска нет скрытого mic/capture, форма восстановлена.`);

  await panelAction('Выбрать микрофон');
  await permission(false);
  await closePanelIfNeeded();
  await device.find('Доступ к аудио не разрешён. Сцена продолжает двигаться.');
  const micDenied = await until(value => value.motion.sourceSessionId === 'none', 'explicit microphone denied');
  assert.deepEqual(micDenied.snapshot.shapes, baseline.shapes);
  device.write('microphone-denied.json', micDenied); device.screenshot('microphone-denied');
  device.step('microphone-permission-denied', 'Отдельный явный выбор микрофона с deny не создал источник и не изменил сохранённую форму.');
  device.shell('pm', 'grant', appId, 'android.permission.RECORD_AUDIO');
  device.report.capturePrecondition = 'После active microphone revoke/restart и второго UI deny RECORD_AUDIO выдан adb только для отдельной проверки MediaProjection Cancel.';
  await captureCancel(baseline);
  }
} catch (error) { failure = error; }
finally { cdp?.close(); device.finish(failure); }
