import assert from 'node:assert/strict';
import { AndroidDevice, appId, inspect, wait } from './android-device.mjs';

const device = new AndroidDevice('p8-permissions');
const permissionName = 'android.permission.RECORD_AUDIO';
const original = device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json'], { binary: true });
const beforePermission = /android.permission.RECORD_AUDIO: granted=(true|false), flags=\[([^\]]*)\]/.exec(device.shell('dumpsys', 'package', appId));
let cdp, failure;
const state = () => cdp.evaluate(inspect);
async function until(predicate, label) {
  const end = Date.now() + 12000;
  while (Date.now() < end) { const value = await state(); if (predicate(value)) return value; await wait(200); }
  throw Error(label);
}
async function permission(allow) {
  for (let i = 0; i < 8; i++) {
    const button = device.nodes().find(n => new RegExp(allow ? '/permission_allow_foreground_only_button$|/permission_allow_button$' : '/permission_deny_button$').test(n['resource-id']));
    if (button) { device.tap(button); await wait(400); return; }
    await wait(200);
  }
  throw Error('Системный RECORD_AUDIO dialog отсутствует');
}
async function closePanel() { if (device.isPanelOpen()) await device.closePanel(); }
function noCaptureOrMic() {
  assert(!device.shell('dumpsys', 'activity', 'services', appId).includes('CaptureProjectionService'));
  assert(!device.nodes().some(n => n['content-desc'] === 'Выбран микрофон'));
}
try {
  device.shell('pm', 'revoke', appId, permissionName);
  device.shell('pm', 'clear-permission-flags', appId, permissionName, 'user-set', 'user-fixed');
  await device.start(); cdp = await device.cdp();
  const baseline = (await state()).snapshot;
  await device.openPanel(); await device.click('Visualizer GLOBAL · общий микс', { scroll: true });
  await permission(false); await closePanel();
  await device.find('Доступ к аудио не разрешён. Сцена продолжает двигаться.');
  const denied = await until(v => v.motion.sourceSessionId === 'none', 'Denied Visualizer started audio');
  assert.deepEqual(denied.snapshot.shapes, baseline.shapes); noCaptureOrMic();
  device.step('visualizer-permission-denied', 'Системный deny: нет аудиоисточника, MIC/MediaProjection/fallback; форма сохранена.');
  await device.openPanel(); await device.click('Visualizer GLOBAL · общий микс', { scroll: true });
  await permission(true); await closePanel();
  const active = await until(v => v.motion.sourceSessionId !== 'none', 'Granted GLOBAL did not open');
  noCaptureOrMic(); assert.deepEqual(active.snapshot.shapes, baseline.shapes);
  device.step('visualizer-explicit-grant', 'Системный grant открыл GLOBAL с UUID без MIC/MediaProjection. Получение handle не считается получением музыки.');
  device.shell('pm', 'revoke', appId, permissionName);
  await wait(500); cdp.close(); cdp = null;
  await device.start(); cdp = await device.cdp();
  const restarted = await until(v => v.motion.sourceSessionId === 'none' && v.motion.mode === 'autonomous', 'Visualizer restarted after permission revoke');
  assert.deepEqual(restarted.snapshot.shapes, baseline.shapes); noCaptureOrMic();
  device.step('visualizer-revoke-restart', 'Активный Visualizer прерван отзывом RECORD_AUDIO; после нового запуска сцена восстановлена, источник не перезапущен.');
  await device.openPanel(); await device.click('Visualizer PLAYER · выбрать плеер', { scroll: true }); await closePanel();
  await device.find('Ожидание сессии плеера.', { prefix: true });
  assert.equal((await state()).motion.sourceSessionId, 'none');
  device.step('player-missed-open', 'Без объявления PLAYER показывает ожидание; Android IDs не подбираются и аудио не запускается.');
  device.shell('input', 'keyevent', 'KEYCODE_HOME'); await wait(600);
  const broadcast = action => device.shell('am', 'broadcast', '-a', action, '-n', `${appId}/musicorbs.platform.audio.PlayerSessionReceiver`,
    '--ei', 'android.media.extra.AUDIO_SESSION', '123456', '--es', 'android.media.extra.PACKAGE_NAME', 'com.example.p8test');
  broadcast('android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION');
  await wait(600); noCaptureOrMic();
  await device.start(); cdp.close(); cdp = await device.cdp();
  assert.equal((await state()).motion.sourceSessionId, 'none');
  broadcast('android.media.action.CLOSE_AUDIO_EFFECT_CONTROL_SESSION');
  assert.deepEqual((await state()).snapshot.shapes, baseline.shapes);
  device.step('passive-background-announcement', 'OPEN в фоне обновляет только RAM registry; возврат не включает Visualizer/MIC/capture.');
} catch (error) { failure = error; }
finally {
  cdp?.close(); device.shell('am', 'force-stop', appId);
  device.command(['shell', 'run-as', appId, 'sh', '-c', "'cat > files/session.json'"], { input: original });
  device.shell('pm', beforePermission?.[1] === 'true' ? 'grant' : 'revoke', appId, permissionName);
  device.shell('pm', 'clear-permission-flags', appId, permissionName, 'user-set', 'user-fixed');
  const flags = [...(beforePermission?.[2] || '').matchAll(/\bUSER_(SET|FIXED)\b/g)].map(m => `user-${m[1].toLowerCase()}`);
  if (flags.length) device.shell('pm', 'set-permission-flags', appId, permissionName, ...flags);
  assert(original.equals(device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json'], { binary: true })));
  device.report.originalSessionRestored = true; device.finish(failure);
}
