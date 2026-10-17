import fs from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { unzipSync } from 'fflate';
import { connectLocalCdp } from './cdp-client.mjs';
import { profileOptions, parseMemory, memorySummary, segmentComplete, screenTimeoutPlan } from './android-profile-options.mjs';

const options = profileOptions(process.argv.slice(2));
const root = fileURLToPath(new URL('../../', import.meta.url));
const packageId = 'musicorbs.app';
const presets = [
  ['orb', 'dots', 'white', ['#FFFFFF', '#FFFFFF']],
  ['ribbon', 'dots', 'pink', ['#FF7BBF', '#FFD6EC']],
  ['cloud', 'soft', 'lilac', ['#9875FF', '#E1C7FF']],
  ['pixel-cloud', 'pixels', 'spectrum', ['#FF306E', '#FFBE32', '#33E8CA', '#6585FF', '#D855FF']],
  ['orb', 'dots', 'white', ['#FFFFFF', '#FFFFFF']],
];
if (options.help || (!options.run && !options.restoreBaseline)) {
  console.log('node scripts/android-profile.mjs --run [--durations 600,600,600,600,1800] [--quality medium] [--fps 60]');
  console.log('MUSIC_ORBS_CDP_URL=http://127.0.0.1:9223; требуется уже открытая debug-сцена и локальный adb forward.');
  console.log('--budget-status proposal|accepted|unconfigured --p95-ms 20 --slow-ms 25 --slow-fraction 0.05 --pss-growth-mib 32');
  console.log('--run --durations 2,2,2,2,3 = только software smoke; --run --screenshots-only = четыре native PNG без профилирования.');
  console.log('--restore-baseline <baseline.json> восстанавливает settings после прерванного теста, поля не перезаписывает.');
  console.log(JSON.stringify({ mode: 'plan-only', ...options, plannedSeconds: options.durations.reduce((a, b) => a + b) }, null, 2));
  process.exit(0);
}
const serial = options.serial || 'emulator-5554';
if (!/^emulator-\d+$/.test(serial)) throw new Error('Этот runner предназначен для Android Emulator; phone acceptance не выполняется.');
const sdk = process.env.ANDROID_HOME || process.env.ANDROID_SDK_ROOT || (process.env.LOCALAPPDATA && path.join(process.env.LOCALAPPDATA, 'Android', 'Sdk'));
const adbPath = process.env.ADB || (sdk && path.join(sdk, 'platform-tools', process.platform === 'win32' ? 'adb.exe' : 'adb'));
if (!adbPath || !existsSync(adbPath)) throw new Error('Не найден adb: задайте ANDROID_HOME или ADB.');
const execute = promisify(execFile);
const adb = async (args, binary = false) => (await execute(adbPath, ['-s', serial, ...args], { encoding: binary ? 'buffer' : 'utf8', windowsHide: true, timeout: 20000, maxBuffer: 64 * 1024 * 1024 })).stdout;
const shell = (...args) => adb(['shell', ...args]);
const wait = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const sha = bytes => createHash('sha256').update(bytes).digest('hex');
const cleanError = error => {
  let message = error instanceof Error ? error.message : String(error);
  for (const [value, replacement] of [[root, '<project>'], [sdk, '<android-sdk>'], [process.env.USERPROFILE, '<user>']]) if (value) message = message.replaceAll(value, replacement);
  return message.slice(0, 1500);
};
const resultsRoot = path.join(root, 'tests', 'device', 'results');
const output = path.resolve(root, options.output || path.join('tests', 'device', 'results', `profile-${new Date().toISOString().replace(/[:.]/g, '-')}`));
if (!output.startsWith(resultsRoot + path.sep) || existsSync(path.join(output, 'report.json'))) throw new Error('Выберите новый каталог tests/device/results/<run>.');
await fs.mkdir(output, { recursive: true });
const write = (name, data) => {
  const value = typeof data === 'string' && name.endsWith('.txt')
    ? data.replace(/[\t ]+$/gm, '').trimEnd() + '\n' : data;
  return fs.writeFile(path.join(output, name), typeof value === 'string' || Buffer.isBuffer(value) ? value : JSON.stringify(value, null, 2));
};
const report = {
  schemaVersion: 1, startedAt: new Date().toISOString(), scope: options.scope, budgets: options.budgets,
  serial, result: 'running', phoneAcceptance: 'not-assessed',
  definitions: { submissionFps: 'JS RendererPort.present submissions / active seconds; не GPU/display FPS', presentCpuDuration: 'performance.now adapter CPU duration, не GPU time', percentiles: 'bounded histogram upper bucket bounds; overflow uses observed maximum', androidWindowFrames: 'dumpsys gfxinfo native window, отдельно от WebGL/GPU', memory: 'Android dumpsys meminfo musicorbs.app TOTAL PSS/RSS, KiB; growth = peak minus initial; app process only, isolated WebView renderer/GPU memory may be excluded; not total WebGL leak coverage', memoryBudget: 'Only 5-orb-memory, at least 1800 active seconds: segment peak minus segment initial PSS. Global 70-minute growth is informational.' },
  thermal: { status: 'not-measured', reason: 'Эмулятор не измеряет физический нагрев телефона.' },
  battery: { status: 'not-measured', reason: 'Виртуальная батарея эмулятора не подтверждает энергопотребление телефона.' },
  nativeUiDuringRun: 'Тестовые settings передаются через локальный CDP. Нативная панель во время теста может показывать baseline; после теста baseline settings восстанавливаются.',
  segments: [], memory: [], errors: [], droppedErrors: 0,
};
let cdp, baseline, profiling = false, interrupted = false, screenTimeoutChanged = false;
process.on('SIGINT', () => { interrupted = true; });
process.on('SIGTERM', () => { interrupted = true; });
const stateExpression = `(async()=>{const canvas=document.querySelector('canvas'),rect=canvas.getBoundingClientRect(),gl=canvas.getContext('webgl2'),debug=gl?.getExtension('WEBGL_debug_renderer_info');return {visible:!document.hidden,viewport:[innerWidth,innerHeight,devicePixelRatio],canvasCss:[rect.width,rect.height],canvasBacking:[canvas.width,canvas.height],snapshot:await window.musicOrbs.snapshot(),motion:window.musicOrbs.visualState(),profilingAvailable:typeof window.musicOrbs.profiling?.start==='function',assets:[...document.querySelectorAll('script[src],link[rel=stylesheet]')].map(e=>e.src||e.href),glVersion:gl?.getParameter(gl.VERSION),glRenderer:debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):null,glVendor:debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):null};})()`;
const state = () => cdp.evaluate(stateExpression);
const checkpoint = () => write('report.json', report);
async function memorySample(segment) {
  const raw = await shell('dumpsys', 'meminfo', packageId);
  const sample = { utc: new Date().toISOString(), segment, ...parseMemory(raw) };
  report.memory.push(sample); await write(`memory-${String(report.memory.length).padStart(3, '0')}.txt`, raw); return sample;
}
async function androidFrames(name) { await write(`${name}-android-window-framestats.txt`, await shell('dumpsys', 'gfxinfo', packageId, 'framestats')); }
async function configure(settings) { await cdp.evaluate(`window.musicOrbs.command(${JSON.stringify({ type: 'configure', settings })})`); }
async function stopProfile() {
  if (!profiling) return null;
  const value = await cdp.evaluate('window.musicOrbs.profiling.stop()'); profiling = false; return value;
}

try {
  cdp = await connectLocalCdp(process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223');
  if (options.restoreBaseline) {
    const value = JSON.parse((await fs.readFile(options.restoreBaseline, 'utf8')).replace(/^\uFEFF/, ''));
    await configure(value.settings); report.result = 'restored-baseline'; report.cleanup = 'settings restored; fields retained';
  } else {
    const initial = await state();
    if (!initial.visible || initial.motion.sourceSessionId !== 'none') throw new Error('Нужна foreground-сцена с явно остановленным аудио.');
    if (!options.screenshotsOnly && !initial.profilingAvailable) throw new Error('Установленный APK не содержит profiling hook. Пересоберите graphics и APK.');
    baseline = initial.snapshot; await write('baseline.json', baseline);
    report.screenAwake = { ...screenTimeoutPlan(await shell('settings', 'get', 'system', 'screen_off_timeout'), options.durations), restored: false };
    await checkpoint(); screenTimeoutChanged = true;
    await shell('settings', 'put', 'system', 'screen_off_timeout', String(report.screenAwake.temporarySystemTimeoutMs));
    if (Number((await shell('settings', 'get', 'system', 'screen_off_timeout')).trim()) !== report.screenAwake.temporarySystemTimeoutMs) throw new Error('Test AVD did not confirm screen timeout.');
    const props = await shell('getprop');
    const prop = key => new RegExp(`^\\[${key.replaceAll('.', '\\.')}\\]: \\[(.*)\\]$`, 'm').exec(props)?.[1] ?? null;
    report.environment = { deviceClass: 'emulator', model: prop('ro.product.model'), manufacturer: prop('ro.product.manufacturer'), android: prop('ro.build.version.release'), sdk: prop('ro.build.version.sdk'), fingerprint: prop('ro.build.fingerprint'), webView: (await shell('dumpsys', 'webviewupdate')).split(/\r?\n/).find(line => line.includes('Current WebView package'))?.trim(), viewport: initial.viewport, canvasCss: initial.canvasCss, canvasBacking: initial.canvasBacking, glVersion: initial.glVersion, glRenderer: initial.glRenderer, glVendor: initial.glVendor };
    const packagePath = (await shell('pm', 'path', packageId)).split(/\r?\n/).find(line => line.endsWith('/base.apk'))?.slice('package:'.length);
    if (!packagePath || !/^\/data\/app\/[A-Za-z0-9_~./+=-]+\/base\.apk$/.test(packagePath)) throw new Error('Не найден безопасный путь установленного APK.');
    const apk = await adb(['exec-out', 'cat', packagePath], true);
    const assets = unzipSync(apk, { filter: entry => /^assets\/(?:index\.html|assets\/[^/]+\.(?:js|css)|THIRD_PARTY_NOTICES\.txt)$/.test(entry.name) });
    report.build = { installedApkSha256: sha(apk), assets: Object.entries(assets).map(([name, bytes]) => ({ name, sha256: sha(bytes), bytes: bytes.length })), loadedAssets: initial.assets.map(value => new URL(value).pathname) };
    await cdp.call('Runtime.enable'); await cdp.call('Log.enable').catch(() => {});
    cdp.onEvent(event => {
      const error = event.method === 'Runtime.exceptionThrown' ? event.params.exceptionDetails.text
        : event.method === 'Log.entryAdded' && event.params.entry.level === 'error' ? event.params.entry.text
        : event.method === 'Runtime.consoleAPICalled' && event.params.type === 'error' ? event.params.args.map(arg => arg.value ?? arg.description ?? '').join(' ') : null;
      if (error) { if (report.errors.length < 100) report.errors.push({ utc: new Date().toISOString(), text: cleanError(error).slice(0, 500) }); else report.droppedErrors++; }
    });
    await cdp.evaluate('window.__musicOrbsDiagnosticsEnabled=true');
    for (let index = 0; index < presets.length; index++) {
      if (interrupted) throw new Error('Прогон прерван оператором.');
      if (options.screenshotsOnly && index === 4) break;
      const seconds = options.screenshotsOnly ? 0 : options.durations[index];
      if (!options.screenshotsOnly && seconds === 0) continue;
      const [shapeId, materialId, paletteId, colors] = presets[index];
      const name = `${index + 1}-${index === 4 ? 'orb-memory' : shapeId}`;
      const settings = { ...baseline.settings, shapeId, materialId, paletteId, paletteStops: colors.map((colorHex, i) => ({ offset: i / (colors.length - 1), colorHex })), quality: options.quality, targetFps: options.fps, speed: 0.5, amplitude: 0.3, musicSync: false };
      await configure(settings); await wait(1200);
      const current = await state();
      if (!current.visible || current.snapshot.settings.shapeId !== shapeId) throw new Error('Сцена не подтвердила тестовую конфигурацию.');
      await write(`${name}.png`, await adb(['exec-out', 'screencap', '-p'], true));
      const segment = { name, requestedSeconds: seconds, settings: current.snapshot.settings, controls: current.snapshot.shapes.map(s => ({ shapeId: s.shapeId, controls: s.controls.length, seed: s.seed })), startedAt: new Date().toISOString(), metrics: null, result: options.screenshotsOnly ? 'screenshot-only' : 'running' };
      report.segments.push(segment); await checkpoint();
      if (options.screenshotsOnly) continue;
      await androidFrames(`${name}-before`); await memorySample(name);
      await cdp.evaluate(`window.musicOrbs.profiling.start(${JSON.stringify({ intervalThresholdMs: options.budgets.slowIntervalMs })})`); profiling = true;
      const started = performance.now(); let nextSample = Math.min(5000, seconds * 1000), minute = 0;
      while (performance.now() - started < seconds * 1000) {
        if (interrupted) throw new Error('Прогон прерван оператором.');
        await wait(Math.min(1000, Math.max(1, seconds * 1000 - (performance.now() - started))));
        const elapsed = performance.now() - started;
        if (elapsed >= nextSample) {
          const value = await cdp.evaluate('({visible:!document.hidden,metrics:window.musicOrbs.profiling.read()})');
          if (!value.visible || value.metrics.paused || value.metrics.contextLost || value.metrics.submissions === 0) throw new Error('Сцена приостановлена или не отправляет кадры; прогон неполон.');
          if (elapsed >= (minute + 1) * 60000) { minute++; await memorySample(name); }
          segment.metrics = value.metrics; await checkpoint();
          console.log(JSON.stringify({ segment: name, elapsedSeconds: Math.round(elapsed / 1000), requestedSeconds: seconds, submissionFps: value.metrics.submissionFps, p95IntervalMs: value.metrics.submissionIntervals.p95Ms }));
          nextSample = Math.min(seconds * 1000, (Math.floor(elapsed / 60000) + 1) * 60000);
        }
      }
      segment.metrics = await stopProfile(); segment.finishedAt = new Date().toISOString();
      segment.result = segmentComplete(segment.metrics, seconds) ? 'measured' : 'incomplete';
      await memorySample(name); await androidFrames(`${name}-after`); await write(`${name}-metrics.json`, segment.metrics); await checkpoint();
      if (segment.result === 'incomplete') throw new Error('Incomplete frame measurement: pause/context loss/error or insufficient active duration.');
    }
    const measured = report.segments.filter(segment => segment.metrics);
    report.memorySummary = memorySummary(report.memory);
    report.memoryBySegment = Object.fromEntries(measured.map(segment => [segment.name, memorySummary(report.memory, segment.name)]));
    const memorySegment = measured.find(segment => segment.name === '5-orb-memory');
    const orbMemory = report.memoryBySegment['5-orb-memory'];
    const memoryEligible = !!memorySegment && memorySegment.requestedSeconds >= 1800
      && memorySegment.metrics.activeSeconds >= 1799.9 && orbMemory.complete;
    const evaluateBudget = !options.smoke && !options.screenshotsOnly && options.budgets.status !== 'unconfigured';
    report.budgetComparison = { status: options.screenshotsOnly ? 'not-evaluated-screenshots' : options.smoke ? 'not-evaluated-short-smoke' : options.budgets.status === 'unconfigured' ? 'not-evaluated-unconfigured' : options.budgets.status === 'accepted' ? 'accepted-emulator-budget-only' : 'proposal-comparison-only', phoneAcceptance: false,
      intervals: measured.map(segment => ({ segment: segment.name, withinP95Budget: !evaluateBudget || segment.metrics.submissionIntervals.p95Ms === null ? null : segment.metrics.submissionIntervals.p95Ms <= options.budgets.p95IntervalMs, withinSlowFractionBudget: !evaluateBudget || segment.metrics.slowFraction === null ? null : segment.metrics.slowFraction < options.budgets.maxSlowFraction })),
      memorySegment: '5-orb-memory', memoryRequiredSeconds: 1800,
      memoryPeakGrowthMiB: orbMemory?.peakGrowthMiB ?? null,
      memoryWithinBudget: !evaluateBudget || !memoryEligible ? null : orbMemory.peakGrowthMiB <= options.budgets.maxPssGrowthMiB,
    };
    if (!options.screenshotsOnly && !report.memorySummary.complete) throw new Error('Incomplete Android PSS measurement.');
    report.result = options.screenshotsOnly ? 'screenshots-collected' : options.smoke ? 'software-smoke-complete' : 'measurement-complete';
  }
} catch (error) {
  report.result = 'incomplete'; report.failure = cleanError(error); process.exitCode = 1;
} finally {
  if (cdp) {
    try {
      if (profiling) { const partial = await stopProfile(); if (report.segments.length) report.segments.at(-1).metrics = partial; }
      if (baseline) { await configure(baseline.settings); report.cleanup = 'baseline settings restored; user fields retained'; }
      await cdp.evaluate('window.__musicOrbsDiagnosticsEnabled=false');
    } catch (error) { report.cleanupFailure = cleanError(error); report.result = 'incomplete'; process.exitCode = 1; }
    cdp.close();
  }
  if (screenTimeoutChanged) {
    try {
      const original = report.screenAwake.originalSystemTimeoutMs;
      if (original === null) await shell('settings', 'delete', 'system', 'screen_off_timeout');
      else await shell('settings', 'put', 'system', 'screen_off_timeout', String(original));
      const restored = (await shell('settings', 'get', 'system', 'screen_off_timeout')).trim();
      if (restored !== String(original)) throw new Error('Screen timeout restore not confirmed.');
      report.screenAwake.restored = true;
    } catch (error) { report.screenAwake.restoreFailure = cleanError(error); report.result = 'incomplete'; process.exitCode = 1; }
  }
  report.finishedAt = new Date().toISOString(); await checkpoint();
  console.log(JSON.stringify({ result: report.result, output: path.relative(root, output).split(path.sep).join('/'), phoneAcceptance: 'not-assessed' }));
}
