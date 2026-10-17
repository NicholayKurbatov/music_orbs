import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
if (args.includes('--help')) {
  console.log('node scripts/android-smoke.mjs [--serial emulator-5554] [--out tests/device/results/g2-<stamp>] [--corruption]');
  console.log('Проверяет установленный debug APK: UI → деформация → quality → save/restart → Reset. Не очищает данные приложения.');
  console.log('--corruption отдельно проверяет испорченный snapshot и затем восстанавливает сохранённый файл.');
  process.exit(0);
}
function argument(name, fallback) {
  const index = args.indexOf(name);
  if (index < 0) return fallback;
  assert(args[index + 1] && !args[index + 1].startsWith('--'), `Missing value for ${name}`);
  return args[index + 1];
}
for (const value of args.filter(value => value.startsWith('--'))) assert(['--serial', '--out', '--corruption'].includes(value), `Unknown option ${value}`);
const serial = argument('--serial', 'emulator-5554');
assert(/^emulator-\d+$/.test(serial), 'Этот сценарий предназначен только для Android Emulator.');
const stamp = new Date().toISOString().replace(/[:.]/g, '-');
const output = path.resolve(root, argument('--out', `tests/device/results/g2-${stamp}`));
assert(output.startsWith(path.join(root, 'tests', 'device', 'results') + path.sep), 'Артефакты должны находиться в tests/device/results/<run>.');
assert(!fs.existsSync(path.join(output, 'report.json')), 'Выберите новую папку: существующий report.json сохраняется.');
fs.mkdirSync(output, { recursive: true });

const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT ||
  (process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
assert(sdk, 'Укажите ANDROID_HOME с установленным SDK.');
const adb = path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb');
assert(fs.existsSync(adb), 'В SDK отсутствует platform-tools/adb.');
const app = 'musicorbs.app';
const activity = `${app}/musicorbs.bootstrap.MainActivity`;
const report = {
  schemaVersion: 1, startedAt: new Date().toISOString(), serial, appId: app,
  result: 'running', steps: [], screenshots: [],
  limits: ['Android Emulator, без физического устройства.', 'Форма и настройки проверяются через сохранённый snapshot и native UI.', 'Визуальная эстетика, FPS, нагрев, звук и доступность capture этим сценарием не подтверждаются.'],
};
const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
function adbCall(values, binary = false) {
  return execFileSync(adb, ['-s', serial, ...values], {
    encoding: binary ? null : 'utf8', timeout: 30000, maxBuffer: 12 * 1024 * 1024, windowsHide: true,
    stdio: ['ignore', 'pipe', 'pipe'],
  });
}
const shell = (...values) => adbCall(['shell', ...values]);
function write(name, value) { fs.writeFileSync(path.join(output, name), typeof value === 'string' ? value : `${JSON.stringify(value, null, 2)}\n`); }
function publicError(error) {
  let message = error instanceof Error ? error.message : String(error);
  for (const [privatePath, replacement] of [[adb, 'adb'], [sdk, '<android-sdk>'], [root, '<project>'], [process.env.USERPROFILE, '<user>']]) {
    if (privatePath) message = message.replaceAll(privatePath, replacement);
  }
  return message;
}
function record(id, details, scenarios) {
  report.steps.push({ id, result: 'pass', details, scenarios });
  console.log(`✓ ${id}: ${details}`);
  write('report.json', report);
}
function screenshot(name) {
  fs.writeFileSync(path.join(output, `${name}.png`), adbCall(['exec-out', 'screencap', '-p'], true));
  report.screenshots.push(`${name}.png`);
}
function writeDeviceSnapshot(raw) {
  execFileSync(adb, ['-s', serial, 'shell', 'run-as', app, 'tee', 'files/session.json'], {
    input: raw, encoding: 'utf8', timeout: 30000, maxBuffer: 1024 * 1024, windowsHide: true,
    stdio: ['pipe', 'pipe', 'pipe'],
  });
}
function decodeXml(value) {
  return value.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&#(x[\da-f]+|\d+);/gi, (_, code) => String.fromCodePoint(code[0].toLowerCase() === 'x' ? parseInt(code.slice(1), 16) : Number(code))).replace(/&amp;/g, '&');
}
function hierarchy(name) {
  shell('uiautomator', 'dump', '--compressed', '/sdcard/music-orbs-smoke.xml');
  const xml = adbCall(['exec-out', 'cat', '/sdcard/music-orbs-smoke.xml']);
  if (name) write(`${name}.xml`, xml);
  return [...xml.matchAll(/<node\b([^>]+)>/g)].map(match => {
    const attributes = Object.fromEntries([...match[1].matchAll(/([\w-]+)="([^"]*)"/g)].map(pair => [pair[1], decodeXml(pair[2])]));
    const bounds = attributes.bounds?.match(/^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$/)?.slice(1).map(Number);
    return { ...attributes, bounds };
  }).filter(node => node.bounds && node.bounds[2] > node.bounds[0] && node.bounds[3] > node.bounds[1]);
}
function matching(nodes, value, prefix = false) {
  return nodes.filter(node => [node.text, node['content-desc']].some(text => prefix ? text?.startsWith(value) : text === value));
}
let width = 1080, height = 2400;
async function locate(value, { prefix = false, scroll = false } = {}) {
  for (let attempt = 0; attempt < (scroll ? 9 : 6); attempt++) {
    const found = matching(hierarchy(), value, prefix).filter(node => node.enabled !== 'false');
    if (found.length) return found.at(-1);
    if (scroll) shell('input', 'swipe', String(Math.round(width * 0.75)), String(Math.round(height * 0.77)), String(Math.round(width * 0.75)), String(Math.round(height * 0.35)), '400');
    await delay(250);
  }
  throw new Error(`UI element missing: ${value}`);
}
function tap(node) {
  const [left, top, right, bottom] = node.bounds;
  shell('input', 'tap', String(Math.round((left + right) / 2)), String(Math.round((top + bottom) / 2)));
}
async function openPanel() { tap(await locate('Настройки')); await delay(350); await locate('Ваша скульптура'); }
async function closePanel() { shell('input', 'keyevent', '4'); await delay(350); }
async function choose(label, option) {
  tap(await locate(`${label}: `, { prefix: true, scroll: true }));
  await delay(250);
  tap(await locate(option));
  await delay(350);
}
function readSnapshot() {
  const raw = adbCall(['exec-out', 'run-as', app, 'cat', 'files/session.json']);
  assert(Buffer.byteLength(raw) <= 262144, 'Snapshot exceeds ProfileLimits');
  const value = JSON.parse(raw);
  assert.equal(value.schemaVersion, 2);
  assert(Number.isSafeInteger(value.revision) && value.revision >= 0);
  assert(Array.isArray(value.shapes) && value.shapes.length >= 1 && value.shapes.length <= 4);
  assert(value.shapes.some(shape => shape.shapeId === value.settings.shapeId));
  return value;
}
async function saved(predicate, label, timeout = 15000) {
  const until = Date.now() + timeout;
  let last;
  while (Date.now() < until) {
    try { last = readSnapshot(); if (predicate(last)) return last; } catch (error) { last = error; }
    await delay(300);
  }
  throw new Error(`Snapshot condition timed out: ${label}; ${last instanceof Error ? last.message : 'last revision ' + last?.revision}`);
}
const shape = (snapshot, id) => snapshot.shapes.find(value => value.shapeId === id);
const geometry = snapshot => [...snapshot.shapes].sort((a, b) => a.shapeId.localeCompare(b.shapeId));
const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);
async function selectShape(id, name) {
  await openPanel();
  await choose('Форма', name);
  await closePanel();
  return saved(value => value.settings.shapeId === id, `active ${id}`);
}
async function deform(id, name) {
  const before = await selectShape(id, name);
  write(`${id}-before.json`, before);
  const old = shape(before, id);
  for (const [x, y] of [[0.5, 0.48], [0.43, 0.5], [0.57, 0.5], [0.5, 0.4], [0.43, 0.42], [0.57, 0.58]]) {
    shell('input', 'swipe', String(Math.round(width * x)), String(Math.round(height * y)), String(Math.round(width * (x + 0.09))), String(Math.round(height * (y - 0.035))), '750');
    await delay(600);
    const changed = await saved(value => value.settings.shapeId === id, `read ${id}`);
    const current = shape(changed, id);
    if (current?.controls.length && !same(current.controls, old?.controls ?? [])) {
      if (old) assert.equal(current.seed, old.seed, 'Gesture must preserve seed');
      assert(current.controls.length <= 64);
      assert(current.revision > (old?.revision ?? -1));
      write(`${id}-deformed.json`, changed);
      screenshot(`${id}-deformed`);
      record(`deform-${id}`, `${id}: ${current.controls.length} controls; native revision ${changed.revision}`, ['FT-04 (input→snapshot)', 'FT-07']);
      return changed;
    }
  }
  throw new Error(`Gesture did not change persisted controls for ${id}`);
}

try {
  assert.equal(adbCall(['get-state']).trim(), 'device');
  report.device = {
    model: shell('getprop', 'ro.product.model').trim(), android: shell('getprop', 'ro.build.version.release').trim(),
    sdk: shell('getprop', 'ro.build.version.sdk').trim(), webView: shell('dumpsys', 'webviewupdate').split(/\r?\n/).find(line => line.includes('Current WebView package'))?.trim() ?? 'unknown',
  };
  const size = shell('wm', 'size').match(/(?:Override|Physical) size:\s*(\d+)x(\d+)/g)?.at(-1)?.match(/(\d+)x(\d+)/);
  if (size) { width = Number(size[1]); height = Number(size[2]); }
  shell('input', 'keyevent', 'KEYCODE_WAKEUP');
  shell('wm', 'dismiss-keyguard');
  // Разрешение может отсутствовать в manifest shell; такой отказ revoke допустим.
  try { shell('pm', 'revoke', app, 'android.permission.RECORD_AUDIO'); } catch { /* Проверяется фактическое granted=false ниже. */ }
  const permissionState = shell('dumpsys', 'package', app);
  assert(!/android\.permission\.RECORD_AUDIO:\s*granted=true/.test(permissionState), 'RECORD_AUDIO remains granted');
  shell('am', 'force-stop', app);
  shell('am', 'start', '-W', '-n', activity);
  await delay(2000);
  await locate('Настройки');
  const opening = hierarchy('opened');
  assert(!opening.some(node => node.package === 'com.android.permissioncontroller'), 'Audio permission prompt on startup');
  screenshot('opened-without-audio-permission');
  record('open-without-audio-permission', 'Native settings доступны без RECORD_AUDIO; системный permission prompt отсутствует.', ['FT-01 (native UI, partial)']);
  write('baseline.json', await saved(() => true, 'initial saved state'));

  const orb = await deform('orb', 'Сфера из частиц');
  const ribbon = await deform('ribbon', 'Лента из частиц');
  assert.deepEqual(shape(ribbon, 'orb'), shape(orb, 'orb'), 'Switching to Ribbon changed Orb');
  const targetQuality = ribbon.settings.quality === 'low' ? 'medium' : 'low';
  await openPanel();
  await choose('Плотность', targetQuality === 'low' ? 'Низкая · 4 000' : 'Средняя · 10 000');
  await closePanel();
  let beforeRestart = await saved(value => value.settings.quality === targetQuality, 'quality changed');
  assert.deepEqual(geometry(beforeRestart), geometry(ribbon), 'Quality changed seed or deformation');
  write('before-restart.json', beforeRestart);
  screenshot('quality-changed');
  record('quality-preserves-fields', `quality=${targetQuality}; seed/controls/revision формы сохранены.`, ['FT-22 (state)', 'FT-11 (subset)']);

  const beforeBackground = beforeRestart;
  write('before-background.json', beforeBackground);
  shell('input', 'keyevent', 'KEYCODE_HOME');
  await delay(800);
  shell('am', 'start', '-W', '-n', activity);
  await delay(700);
  await openPanel();
  await choose('Форма', 'Лента из частиц');
  await closePanel();
  const afterBackground = await saved(value => value.settings.shapeId === 'ribbon' && value.revision > beforeBackground.revision, 'background resume accepts configure');
  assert.deepEqual(afterBackground.settings, beforeBackground.settings, 'Background resume changed settings');
  assert.deepEqual(geometry(afterBackground), geometry(beforeBackground), 'Background resume lost deformation');
  write('after-background.json', afterBackground);
  screenshot('background-resumed');
  record('background-resume', 'После HOME и возврата новая Configure подтверждена; settings/controls/seed сохранены.', ['FT-21 (emulator state/lifecycle)']);
  beforeRestart = afterBackground;
  write('before-restart.json', beforeRestart);

  shell('am', 'force-stop', app);
  shell('am', 'start', '-W', '-n', activity);
  await delay(2000);
  await openPanel();
  await locate('Форма: Лента из частиц', { prefix: true });
  await locate(`Плотность: ${targetQuality === 'low' ? 'Низкая · 4 000' : 'Средняя · 10 000'}`, { prefix: true, scroll: true });
  hierarchy('restored-settings');
  await closePanel();
  const afterRestart = await saved(value => value.revision >= beforeRestart.revision, 'restart snapshot');
  assert.deepEqual(afterRestart.settings, beforeRestart.settings, 'Restored settings differ');
  assert.deepEqual(geometry(afterRestart), geometry(beforeRestart), 'Restored geometry differs');
  write('after-restart.json', afterRestart);
  screenshot('restored-ribbon');
  // Новая команда заставляет читать модель WebView: старый файл сам по себе не доказывает restore.
  const beforeReset = await selectShape('orb', 'Сфера из частиц');
  assert.deepEqual(geometry(beforeReset), geometry(beforeRestart), 'Restored WebView lost previously saved controls');
  record('force-stop-restore', 'После перезапуска UI и новый snapshot сохраняют обе деформации, seed и quality; revision не уменьшается.', ['FT-07', 'FT-08 (emulator)']);

  await openPanel();
  tap(await locate('Сбросить активную форму', { scroll: true }));
  await closePanel();
  const reset = await saved(value => value.settings.shapeId === 'orb' && shape(value, 'orb')?.controls.length === 0 && value.revision > beforeReset.revision, 'active reset');
  assert.equal(shape(reset, 'orb').seed, shape(beforeReset, 'orb').seed);
  assert.deepEqual(shape(reset, 'ribbon'), shape(beforeReset, 'ribbon'), 'Reset affected Ribbon');
  assert.deepEqual(reset.settings, beforeReset.settings, 'Reset affected settings');
  write('after-reset.json', reset);
  screenshot('reset-orb');
  record('reset-active-only', 'Orb controls пусты; Ribbon, settings и seed сохранены.', ['FT-10 (emulator)']);
  if (args.includes('--corruption')) {
    const original = adbCall(['exec-out', 'run-as', app, 'cat', 'files/session.json']);
    const invalid = '{"schemaVersion":999,"revision":0}';
    write('before-optional-corruption.json', JSON.parse(original));
    try {
      shell('am', 'force-stop', app);
      writeDeviceSnapshot(invalid);
      shell('am', 'start', '-W', '-n', activity);
      await delay(2000);
      await locate('Сохранение не восстановлено. Открыта исходная форма.');
      await locate('Настройки');
      assert.equal(adbCall(['exec-out', 'run-as', app, 'cat', 'files/session.json']).trim(), invalid);
      screenshot('corruption-fallback');
      hierarchy('corruption-fallback');
      record('optional-corruption-fallback', 'Неверная schema вызывает понятный fallback; файл до новой явной записи сохранён.', ['FT-09 (emulator)']);
    } finally {
      shell('am', 'force-stop', app);
      writeDeviceSnapshot(original);
      shell('am', 'start', '-W', '-n', activity);
    }
    await saved(value => same(value, JSON.parse(original)), 'optional corruption restores original snapshot');
  }
  report.result = 'pass';
} catch (error) {
  report.result = 'fail';
  report.failure = publicError(error);
  try { screenshot('failure'); hierarchy('failure'); } catch { /* Ошибка соединения также фиксируется без артефакта экрана. */ }
  console.error(report.failure);
  process.exitCode = 1;
} finally {
  report.finishedAt = new Date().toISOString();
  write('report.json', report);
  console.log(`Артефакты: ${path.relative(root, output).split(path.sep).join('/')}`);
}
