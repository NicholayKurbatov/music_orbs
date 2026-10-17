import { chromium } from 'playwright-core';
import { createServer } from 'vite';
import { mkdir, writeFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import assert from 'node:assert/strict';

const executablePath = process.env.MUSIC_ORBS_BROWSER || ['C:/Program Files/Google/Chrome/Application/chrome.exe', 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'].find(existsSync);
if (!executablePath) throw new Error('Задайте MUSIC_ORBS_BROWSER: путь к Chromium/Chrome/Edge.');
const server = await createServer({ server: { port: 4173, host: '127.0.0.1' } });
await server.listen();
const browser = await chromium.launch({ executablePath, headless: true, args: ['--enable-webgl', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
const context = await browser.newContext({ viewport: { width: 720, height: 1000 } });
const page = await context.newPage();
const errors = [];
const glValidationWarnings = [];
const contextRecovery = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => {
  if (message.type() === 'error') errors.push(message.text());
  if (/INVALID_OPERATION|does not belong to this context/.test(message.text())) glValidationWarnings.push(message.text());
});
// Observer only: native create/delete calls still run, and getError is never consumed here.
await page.addInitScript(() => {
  const generations = new WeakMap(), resources = new WeakMap(), observed = new WeakSet();
  window.glLifecycle = { created: {}, violations: [], losses: 0, restores: 0 };
  const getContext = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (...args) {
    const gl = Reflect.apply(getContext, this, args);
    if (gl instanceof WebGL2RenderingContext && !observed.has(gl)) {
      observed.add(gl); generations.set(gl, 0);
      this.addEventListener('webglcontextlost', () => window.glLifecycle.losses++, true);
      this.addEventListener('webglcontextrestored', () => {
        generations.set(gl, generations.get(gl) + 1); window.glLifecycle.restores++;
      }, true);
    }
    return gl;
  };
  for (const kind of ['Buffer', 'VertexArray', 'Texture', 'Program', 'Shader', 'Framebuffer', 'Renderbuffer']) {
    const prototype = WebGL2RenderingContext.prototype;
    const create = prototype[`create${kind}`], remove = prototype[`delete${kind}`];
    prototype[`create${kind}`] = function (...args) {
      const object = Reflect.apply(create, this, args);
      if (object) {
        resources.set(object, { context: this, generation: generations.get(this) });
        window.glLifecycle.created[kind] = (window.glLifecycle.created[kind] ?? 0) + 1;
      }
      return object;
    };
    prototype[`delete${kind}`] = function (object, ...args) {
      const owner = object && resources.get(object);
      if (owner && !this.isContextLost() && (owner.context !== this || owner.generation !== generations.get(this))) {
        window.glLifecycle.violations.push({ kind, createdGeneration: owner.generation, deletedGeneration: generations.get(this) });
      }
      return Reflect.apply(remove, this, [object, ...args]);
    };
  }
});
const output = new URL('../dist/smoke/', import.meta.url);
await mkdir(output, { recursive: true });
try {
  await page.goto('http://127.0.0.1:4173');
  await page.waitForFunction(() => !!window.musicOrbs);
  assert.equal(await page.evaluate(() => window.musicOrbs.profiling.read()), null);
  assert.equal(await page.evaluate(() => { try { window.musicOrbs.profiling.start(); return false; } catch { return true; } }), true);
  await page.addStyleTag({ content: '#scene { height: 0px; }' });
  const canvasViewport = await page.$eval('#scene', canvas => ({ height: canvas.getBoundingClientRect().height, viewport: innerHeight }));
  assert.equal(canvasViewport.height, canvasViewport.viewport, 'CSS canvas должен совпадать с viewport даже при нулевом dvh');
  await page.waitForTimeout(350);
  for (const shape of ['orb', 'ribbon', 'cloud', 'pixel-cloud']) {
    await page.click('#toggle');
    await page.selectOption('#shape', shape);
    await page.click('#toggle');
    await page.waitForTimeout(650);
    await page.screenshot({ path: new URL(`${shape}.png`, output).pathname.replace(/^\/([A-Za-z]:)/, '$1') });
    const scene = await page.evaluate(() => window.musicOrbs.snapshot());
    assert.equal(scene.settings.shapeId, shape);
    const draw = await page.evaluate(() => {
      const gl = document.querySelector('canvas').getContext('webgl2');
      return { error: gl.getError(), lost: gl.isContextLost() };
    });
    assert.equal(draw.error, 0); assert.equal(draw.lost, false);
  }
  await page.evaluate(async () => { const s = await window.musicOrbs.snapshot(); await window.musicOrbs.command({ type: 'configure', settings: { ...s.settings, shapeId: 'orb', speed: 0 } }); });
  await page.waitForTimeout(100);
  await page.mouse.move(360, 500); await page.mouse.down(); await page.mouse.move(430, 470, { steps: 8 }); await page.mouse.up();
  const sculpted = await page.evaluate(() => window.musicOrbs.snapshot());
  assert.ok(sculpted.shapes.find(s => s.shapeId === 'orb').controls.length > 0, 'Реальный drag должен изменить поле');
  await page.evaluate(async () => { const s = await window.musicOrbs.snapshot(); await window.musicOrbs.command({ type: 'configure', settings: { ...s.settings, quality: 'high', targetFps: 30 } }); });
  const quality = await page.evaluate(() => window.musicOrbs.snapshot());
  assert.deepEqual(quality.shapes, sculpted.shapes);
  await page.waitForTimeout(300); await page.reload(); await page.waitForFunction(() => !!window.musicOrbs);
  assert.deepEqual((await page.evaluate(() => window.musicOrbs.snapshot())).shapes, sculpted.shapes);
  await page.waitForFunction(() => window.glLifecycle.created.Buffer >= 2 && window.glLifecycle.created.VertexArray >= 1);
  const clearColorBeforeLoss = await page.evaluate(() => { const gl = document.querySelector('canvas').getContext('webgl2'); return Array.from(gl.getParameter(gl.COLOR_CLEAR_VALUE)); });
  await page.evaluate(() => { window.__musicOrbsDiagnosticsEnabled = true; window.musicOrbs.profiling.start(); });
  for (let cycle = 0; cycle < 2; cycle++) {
    await page.evaluate(() => new Promise(resolve => {
      const canvas = document.querySelector('canvas'), gl = canvas.getContext('webgl2');
      const extension = gl.getExtension('WEBGL_lose_context');
      canvas.addEventListener('webglcontextlost', () => setTimeout(() => extension.restoreContext(), 100), { once: true });
      canvas.addEventListener('webglcontextrestored', () => resolve(), { once: true });
      extension.loseContext();
    }));
    await page.waitForTimeout(500);
    const restored = await page.evaluate(() => {
      const gl = document.querySelector('canvas').getContext('webgl2');
      return { error: gl.getError(), lost: gl.isContextLost(), clearColor: Array.from(gl.getParameter(gl.COLOR_CLEAR_VALUE)), lifecycle: window.glLifecycle };
    });
    assert.equal(restored.lost, false);
    assert.equal(restored.error, 0, `Первый gl.getError после restore ${cycle + 1}`);
    assert.deepEqual(restored.lifecycle.violations, [], 'GPU delete не должен получать ресурсы прежнего контекста');
    assert.deepEqual(glValidationWarnings, [], 'После restore нет предупреждений WebGL о чужих ресурсах');
    assert.deepEqual(restored.clearColor, clearColorBeforeLoss, 'Clear color восстановлен');
    contextRecovery.push({ cycle: cycle + 1, firstPostRestoreError: restored.error, clearColor: restored.clearColor, lifecycle: restored.lifecycle });
    const beforeSwitch = await page.evaluate(() => window.musicOrbs.snapshot());
    await page.evaluate(async () => { const s = await window.musicOrbs.snapshot(); await window.musicOrbs.command({ type: 'configure', settings: { ...s.settings, quality: s.settings.quality === 'high' ? 'low' : 'high' } }); });
    await page.waitForTimeout(150);
    assert.deepEqual((await page.evaluate(() => window.musicOrbs.snapshot())).shapes, beforeSwitch.shapes);
    assert.equal(await page.evaluate(() => document.querySelector('canvas').getContext('webgl2').getError()), 0, 'Quality switch после restore');
  }
  const recoveryMetrics = await page.evaluate(() => window.musicOrbs.profiling.stop());
  assert.equal(recoveryMetrics.counters.contextLosses, 2); assert.equal(recoveryMetrics.counters.contextRestores, 2);
  assert.equal(recoveryMetrics.counters.renderErrors, 0); assert.ok(recoveryMetrics.submissions > 0);
  assert.equal(recoveryMetrics.presentCpuDuration.count, recoveryMetrics.submissions);
  assert.deepEqual((await page.evaluate(() => window.musicOrbs.snapshot())).shapes, sculpted.shapes);
  await page.screenshot({ path: new URL('sculpted-restored.png', output).pathname.replace(/^\/([A-Za-z]:)/, '$1') });
  await page.evaluate(() => { window.musicOrbs.dispose(); window.musicOrbs.dispose(); });
  assert.deepEqual(await page.evaluate(() => window.glLifecycle.violations), [], 'Dispose после повторного restore не удаляет старые ресурсы');
  assert.deepEqual(glValidationWarnings, []);
  const nativePage = await context.newPage();
  await nativePage.addInitScript(() => { window.nativeMessages = []; window.MusicOrbsNative = { postMessage: json => window.nativeMessages.push(json) }; });
  nativePage.on('pageerror', error => errors.push(error.message));
  await nativePage.goto('http://127.0.0.1:4173'); await nativePage.waitForFunction(() => !!window.MusicOrbsReceive);
  const deliver = (sequence, type, payload, requestId) => nativePage.evaluate(message => window.MusicOrbsReceive(JSON.stringify(message)), { protocolVersion: 1, bridgeSessionId: 'browser-smoke', sequence, type, payload, ...(requestId ? { requestId } : {}) });
  await deliver(0, 'ready', { runtimeTimestampSeconds: 0, sourceSessionId: 'none' });
  const motion = { sourceSessionId: 'stale-source', timestampSeconds: 1, bpm: 90, slowCycleSeconds: 21.3333333333, phase: 0.25, energy: 0.5, intensity: 0.3, confidence: 0.9, mode: 'synced' };
  await deliver(1, 'motion', motion); await nativePage.waitForTimeout(100);
  assert.equal(await nativePage.textContent('#mode'), 'Автономное движение');
  await deliver(2, 'ready', { runtimeTimestampSeconds: 2, sourceSessionId: 'current-source' });
  await deliver(3, 'motion', { ...motion, sourceSessionId: 'current-source' }); await nativePage.waitForTimeout(100);
  assert.equal(await nativePage.textContent('#mode'), 'Движение под музыку');
  const beforeDelayed = await nativePage.evaluate(() => window.musicOrbs.visualState());
  await deliver(4, 'motion', { ...motion, sourceSessionId: 'current-source', timestampSeconds: 2, phase: 0.01 });
  const afterDelayed = await nativePage.evaluate(() => window.musicOrbs.visualState());
  assert.ok((afterDelayed.phase - beforeDelayed.phase + 1) % 1 < 0.02, 'Задержанный motion не должен устанавливать старую фазу');
  await deliver(5, 'command', { type: 'snapshot' }, 'smoke-snapshot');
  await nativePage.waitForFunction(() => window.nativeMessages.some(raw => raw.startsWith('{') && JSON.parse(raw).type === 'snapshot'));
  assert.equal(await nativePage.isVisible('#toggle'), false);
  const beforeSource = await nativePage.evaluate(() => window.musicOrbs.visualState());
  await deliver(6, 'ready', { runtimeTimestampSeconds: 3, sourceSessionId: 'none' });
  await nativePage.waitForTimeout(100);
  assert.equal(await nativePage.textContent('#mode'), 'Автономное движение');
  const afterSource = await nativePage.evaluate(() => window.musicOrbs.visualState());
  assert.equal(afterSource.slowCycleSeconds, beforeSource.slowCycleSeconds);
  assert.equal(afterSource.energy, 0);
  assert.ok((afterSource.phase - beforeSource.phase + 1) % 1 < 0.02, 'Source switch должен сохранять фазу');
  await nativePage.evaluate(() => window.musicOrbs.dispose());
  await nativePage.close();
  const phasePage = await context.newPage();
  await phasePage.addInitScript(() => {
    window.MusicOrbsNative = { postMessage() {} };
    let callback, clock = null, id = 0;
    window.requestAnimationFrame = next => { callback = next; return ++id; };
    window.cancelAnimationFrame = () => { callback = null; };
    window.stepVisual = milliseconds => { clock = (clock ?? performance.now()) + milliseconds; const next = callback; if (next) next(clock); };
  });
  phasePage.on('pageerror', error => errors.push(error.message));
  await phasePage.goto('http://127.0.0.1:4173'); await phasePage.waitForFunction(() => !!window.musicOrbs);
  await phasePage.setViewportSize({ width: 393, height: 851 });
  const nativeViewport = await phasePage.$eval('#scene', canvas => ({ height: canvas.getBoundingClientRect().height, width: canvas.getBoundingClientRect().width }));
  assert.deepEqual(nativeViewport, { width: 393, height: 851 });
  await phasePage.evaluate(async () => {
    const send = (sequence, type, payload, requestId) => window.MusicOrbsReceive(JSON.stringify({ protocolVersion: 1, bridgeSessionId: 'phase-smoke', sequence, type, payload, ...(requestId ? { requestId } : {}) }));
    send(0, 'ready', { runtimeTimestampSeconds: 0, sourceSessionId: 'current-source' });
    send(1, 'motion', { sourceSessionId: 'current-source', timestampSeconds: 1, bpm: 120, slowCycleSeconds: 16, phase: 0.4, energy: 0.5, intensity: 0.3, confidence: 0.9, mode: 'synced' });
    const snapshot = await window.musicOrbs.snapshot();
    await window.musicOrbs.command({ type: 'configure', settings: { ...snapshot.settings, musicSync: false } });
    window.stepVisual(100);
  });
  const firstTick = await phasePage.evaluate(() => window.musicOrbs.visualState());
  assert.ok(Math.abs(firstTick.phase - (0.4 + 0.1 / 16)) < 1e-10, 'Native syncOff должен использовать плавный период16, без скачка на24');
  await phasePage.evaluate(() => {
    window.MusicOrbsReceive(JSON.stringify({ protocolVersion: 1, bridgeSessionId: 'phase-smoke', sequence: 2, type: 'motion', payload: { sourceSessionId: 'current-source', timestampSeconds: 2, bpm: null, slowCycleSeconds: 17, phase: 0.001, energy: 0, intensity: 0, confidence: 0, mode: 'autonomous' } }));
    window.stepVisual(100);
  });
  const secondTick = await phasePage.evaluate(() => window.musicOrbs.visualState());
  assert.ok(Math.abs(secondTick.phase - firstTick.phase - 0.1 / 17) < 1e-10);
  const pausedMetrics = await phasePage.evaluate(async () => {
    window.__musicOrbsDiagnosticsEnabled = true; window.musicOrbs.profiling.start(); window.stepVisual(100);
    await window.musicOrbs.command({ type: 'pause' }); window.stepVisual(100); window.stepVisual(100);
    await window.musicOrbs.command({ type: 'resume' }); window.stepVisual(100);
    return window.musicOrbs.profiling.stop();
  });
  assert.equal(pausedMetrics.submissions, 2); assert.equal(pausedMetrics.submissionIntervals.count, 0);
  await phasePage.evaluate(() => window.musicOrbs.dispose()); await phasePage.close();
  assert.deepEqual(errors, []);
  const result = { browser: browser.version(), viewport: '720x1000; portrait 393x851', renderer: 'SwiftShader (headless)', checks: ['four shapes', 'GLSL compilation', 'pointer drag', 'quality/FPS field invariant', 'browser persistence/reload', 'two context loss/recovery cycles, GPU generations/GL errors/clear color/quality switch/dispose', 'idempotent dispose', 'native handshake/snapshot', 'stale source rejection', 'delayed motion/source phase continuity', 'native sync-off smoothed-period integration', 'zero-dvh canvas CSS viewport regression', 'profiling opt-in/actual submissions/CPU/context loss/pause'], errors, contextRecovery, glValidationWarnings };
  await writeFile(new URL('result.json', output), JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 2));
} finally { await browser.close(); await server.close(); }
