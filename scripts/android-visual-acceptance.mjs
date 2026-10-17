import assert from 'node:assert/strict';
import { isDeepStrictEqual } from 'node:util';
import { AndroidDevice, appId, activity, wait } from './android-device.mjs';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

if (process.argv.includes('--help')) {
  console.log('node scripts/android-visual-acceptance.mjs [--serial emulator-5554]');
  console.log('Дополнительная приёмка установленного debug APK: trusted CDP touch в настоящем WebView, sensitivity, rotation, decay, context recovery, palette/restart и Reset/restart.');
  console.log('Это не Android MotionEvent/multitouch instrumentation и не эстетическая оценка человеком. Исходная сцена восстанавливается в finally.');
  process.exit(0);
}
const device = new AndroidDevice('visual-acceptance');
device.report.limits.push('Жесты CDP проходят через WebView input/PointerController; Android MotionEvent и физическое удобство проверяются отдельно.', 'Цвет проверяется по фактическим загрузкам palette texture; GPU/display FPS этим runner не измеряется.');
let cdp, baseline, failure;
const same = isDeepStrictEqual;
const shape = (snapshot, id) => snapshot.shapes.find(value => value.shapeId === id);
const scene = () => cdp.evaluate('window.musicOrbs.snapshot()');
const command = value => cdp.evaluate(`window.musicOrbs.command(${JSON.stringify(value)})`);
async function connect() {
  cdp?.close();
  const forwarding = await device.cdp(); forwarding.close();
  cdp = await connectLocalCdp('http://127.0.0.1:9223');
  assert(await cdp.evaluate('!!window.musicOrbs && !document.hidden'), 'Foreground WebView required');
}
async function persisted(expected) {
  for (let i = 0; i < 40; i++) {
    const value = JSON.parse(device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json']));
    if (same(value.settings, expected.settings) && same(value.shapes, expected.shapes)) return value;
    await wait(250);
  }
  throw new Error('Native store did not confirm the live scene');
}
async function restart(expected) {
  cdp?.close(); cdp = null;
  device.shell('am', 'force-stop', appId);
  device.shell('am', 'start', '-W', '-n', activity);
  await wait(1600); await connect();
  if (expected) {
    for (let i = 0; i < 40; i++) {
      if (same(await scene(), expected)) return;
      await wait(150);
    }
    assert.deepEqual(await scene(), expected, 'Native restore did not converge to the expected scene');
  }
}
async function touch(type, points) {
  await cdp.call('Input.dispatchTouchEvent', { type, touchPoints: points.map(point => ({ ...point, radiusX: 2, radiusY: 2, force: 1 })) });
  await wait(80);
}
const point = (x, y, id = 1) => ({ x, y, id });
async function tap(x, y) { await touch('touchStart', [point(x, y)]); await touch('touchEnd', []); }
async function restore(value) { await command({ type: 'restore', snapshot: value }); await wait(100); }
async function cleanScene(sensitivity = 1) {
  const value = structuredClone(baseline);
  Object.assign(value.settings, { shapeId: 'orb', materialId: 'dots', speed: 0, amplitude: 0, touchSensitivity: sensitivity, musicSync: false, quality: 'medium', targetFps: 60 });
  shape(value, 'orb').controls = []; shape(value, 'orb').revision++;
  await restore(value); return value;
}
const magnitude = snapshot => Math.hypot(...shape(snapshot, 'orb').controls.flatMap(control => control.delta));
async function glState() {
  return cdp.evaluate(`(()=>{const gl=document.querySelector('canvas').getContext('webgl2'),p=gl.getParameter(gl.CURRENT_PROGRAM);const uniform=name=>{const l=gl.getUniformLocation(p,name);const v=l?gl.getUniform(p,l):null;return v&&typeof v!=='number'?Array.from(v):v;};return {modelView:uniform('modelViewMatrix'),projection:uniform('projectionMatrix'),impulse:uniform('impulse'),phase:window.musicOrbs.visualState().phase,error:gl.getError(),now:performance.now()};})()`);
}
async function paletteTexture() {
  return cdp.evaluate(`(()=>{const gl=document.querySelector('canvas').getContext('webgl2'),program=gl.getParameter(gl.CURRENT_PROGRAM);
    const unit=gl.getUniform(program,gl.getUniformLocation(program,'palette')),active=gl.getParameter(gl.ACTIVE_TEXTURE);
    const draw=gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING),read=gl.getParameter(gl.READ_FRAMEBUFFER_BINDING),fb=gl.createFramebuffer();
    try{gl.activeTexture(gl.TEXTURE0+unit);const texture=gl.getParameter(gl.TEXTURE_BINDING_2D);if(!texture)throw Error('No actual palette texture');
      gl.bindFramebuffer(gl.FRAMEBUFFER,fb);gl.framebufferTexture2D(gl.FRAMEBUFFER,gl.COLOR_ATTACHMENT0,gl.TEXTURE_2D,texture,0);
      if(gl.checkFramebufferStatus(gl.FRAMEBUFFER)!==gl.FRAMEBUFFER_COMPLETE)throw Error('Palette texture not readable');
      const bytes=new Uint8Array(1024);gl.readPixels(0,0,256,1,gl.RGBA,gl.UNSIGNED_BYTE,bytes);
      if(gl.getError()!==0)throw Error('GPU palette read failed');
      return [...bytes.slice(0,3),...bytes.slice(512,515),...bytes.slice(1020,1023)];
    }finally{gl.bindFramebuffer(gl.DRAW_FRAMEBUFFER,draw);gl.bindFramebuffer(gl.READ_FRAMEBUFFER,read);gl.activeTexture(active);gl.deleteFramebuffer(fb);}})()`);
}
function expectedPalette(stops) {
  const linear = hex => [1, 3, 5].map(offset => {
    const value = parseInt(hex.slice(offset, offset + 2), 16) / 255;
    return (value <= .04045 ? value / 12.92 : ((value + .055) / 1.055) ** 2.4) * 255;
  });
  return [0, 128 / 255, 1].flatMap(t => {
    let index = 0; while (index < stops.length - 2 && stops[index + 1].offset < t) index++;
    const a = stops[index], b = stops[Math.min(index + 1, stops.length - 1)];
    const weight = b.offset === a.offset ? 0 : Math.max(0, Math.min(1, (t - a.offset) / (b.offset - a.offset)));
    return linear(a.colorHex).map((value, axis) => Math.round(value + (linear(b.colorHex)[axis] - value) * weight));
  });
}
const assertColors = (actual, expected, label) => assert(actual.length === expected.length && actual.every((value, index) => Math.abs(value - expected[index]) <= 2), label);
try {
  const installedApk = device.shell('pm', 'path', appId).trim().split(/\r?\n/).find(value => value.endsWith('/base.apk'))?.slice(8);
  assert(installedApk && /^\/data\/app\/[A-Za-z0-9_+~\/=.-]+\/base\.apk$/.test(installedApk), 'Installed APK path required');
  device.report.installedApkSha256 = device.shell('sha256sum', installedApk).trim().split(/\s+/)[0];
  device.report.webView = device.shell('dumpsys', 'webviewupdate').split(/\r?\n/).find(value => value.includes('Current WebView package'))?.trim();
  await device.start(); await connect();
  assert.equal(await cdp.evaluate('window.musicOrbs.visualState().sourceSessionId'), 'none', 'Stop audio before this visual scenario');
  const originalRaw = device.command(['exec-out', 'run-as', appId, 'cat', 'files/session.json']);
  assert(Buffer.byteLength(originalRaw) <= 262144, 'Original store exceeds ProfileLimits');
  const originalStore = JSON.parse(originalRaw);
  assert.equal(originalStore.schemaVersion, 2, 'This runner requires a valid v2 store; corruption testing is separate');
  const expectedOpening = { settings: originalStore.settings, shapes: originalStore.shapes };
  for (let i = 0; i < 40 && !same(await scene(), expectedOpening); i++) await wait(150);
  assert.deepEqual(await scene(), expectedOpening, 'Original valid store was not restored; no test mutations performed');
  baseline = await scene(); device.write('baseline.json', baseline); device.write('original-store.json', originalStore);
  device.report.limits.push('Required valid v2 store matched the opening scene. Cleanup preserves semantic scene; global save revision may increase.');
  await cleanScene();
  const viewport = await cdp.evaluate('({width:innerWidth,height:innerHeight})');
  let hit;
  for (const [fx, fy] of [[.5, .48], [.43, .5], [.57, .5], [.5, .4], [.43, .42], [.57, .58], [.5, .55]]) {
    await cleanScene(); const x = viewport.width * fx, y = viewport.height * fy;
    await tap(x, y);
    if (shape(await scene(), 'orb').controls.length) { hit = { x, y }; break; }
  }
  assert(hit, 'No pickable Orb point found');
  device.report.hit = hit;
  const amplitudes = [];
  for (const sensitivity of [.5, 2]) {
    await cleanScene(sensitivity); await tap(hit.x, hit.y);
    const value = await scene(); assert(magnitude(value) > 0, 'Short touch must deform');
    device.write(`tap-sensitivity-${sensitivity}.json`, value);
    amplitudes.push(magnitude(value));
  }
  assert(Math.abs(amplitudes[1] / amplitudes[0] - 4) < .05, 'Sensitivity must scale the same touch once');
  device.step('short-touch-sensitivity', `Trusted WebView tap: magnitudes ${amplitudes.join(', ')}, ratio ${amplitudes[1] / amplitudes[0]}. FT-04.`);
  const drags = [];
  for (const sensitivity of [.5, 2]) {
    await cleanScene(sensitivity);
    await touch('touchStart', [point(hit.x, hit.y)]);
    await touch('touchMove', [point(hit.x + 3, hit.y - 2)]);
    await touch('touchEnd', []);
    const value = await scene(); drags.push(magnitude(value));
    device.write(`drag-sensitivity-${sensitivity}.json`, value);
  }
  assert(Math.abs(drags[1] / drags[0] - 4) < .05, 'Sensitivity must scale the same drag once');
  device.step('drag-sensitivity', `Identical 3px/-2px WebView drag: magnitude ratio ${drags[1] / drags[0]}. FT-04.`);

  await cleanScene();
  await touch('touchStart', [point(hit.x, hit.y)]);
  const firstPointer = await scene();
  await touch('touchStart', [point(hit.x, hit.y), point(hit.x + 15, hit.y + 15, 2)]);
  await touch('touchMove', [point(hit.x, hit.y), point(hit.x + 60, hit.y + 30, 2)]);
  const secondPointer = await scene();
  const beforeControls = shape(firstPointer, 'orb').controls, afterControls = shape(secondPointer, 'orb').controls;
  assert.equal(afterControls.length, beforeControls.length, 'Second touch created a control');
  assert(afterControls.every((control, i) => same(control.center, beforeControls[i].center) && control.radius === beforeControls[i].radius), 'Second touch moved a control anchor/radius');
  assert(afterControls.every((control, i) => control.delta.every((v, axis) => Math.abs(v - beforeControls[i].delta[axis]) < 1e-8)), 'Second touch deformed the sculpture');
  await touch('touchCancel', []);
  const canceled = await scene();
  await tap(hit.x, hit.y);
  const afterCancel = await scene(); assert(magnitude(afterCancel) > magnitude(canceled), 'Gesture stuck after cancel');
  await touch('touchStart', [point(hit.x, hit.y)]);
  await touch('touchMove', [point(-20, hit.y)]);
  await touch('touchEnd', []);
  const outside = await scene();
  await tap(hit.x, hit.y);
  assert(shape(await scene(), 'orb').revision > shape(outside, 'orb').revision, 'Gesture stuck after outside release');
  device.write('pointer-cancel-outside.json', await scene());
  device.step('webview-second-pointer-cancel-outside', 'Second pointer ignored; cancel and release outside the view allow the next gesture. FT-25; CDP input, not Android MotionEvent.');

  await cleanScene();
  await touch('touchStart', [point(hit.x, hit.y)]);
  await touch('touchMove', [point(hit.x + 5, hit.y - 3)]);
  await touch('touchEnd', []);
  const deformed = await scene();
  assert(shape(deformed, 'orb').controls.length);
  const impulseStart = await glState(); await wait(1500); const impulseEnd = await glState();
  assert.equal(impulseStart.error, 0);
  assert(impulseStart.impulse > 0, 'No actual residual impulse observed');
  assert.equal(impulseEnd.error, 0); assert(impulseEnd.impulse < .0001, 'Residual impulse did not decay');
  device.write('impulse-decay.json', { before: impulseStart, after: impulseEnd });
  await command({ type: 'configure', settings: { ...deformed.settings, speed: 2, amplitude: .3 } });
  const rotating = await scene(), matricesBefore = await glState();
  let cycles = 0, lastPhase = matricesBefore.phase;
  const observations = [matricesBefore];
  for (let second = 0; second < 75; second++) {
    await wait(1000); const current = await glState();
    assert.equal(current.error, 0);
    cycles += (current.phase - lastPhase + 1) % 1; lastPhase = current.phase;
    assert.deepEqual((await scene()).shapes, rotating.shapes, 'Animation changed persistent fields');
    observations.push(current);
  }
  const a = matricesBefore.modelView, b = observations[8].modelView;
  const trace = [0, 1, 2].reduce((sum, col) => sum + [0, 1, 2].reduce((v, row) => v + a[col * 4 + row] * b[col * 4 + row], 0), 0);
  const angle = Math.acos(Math.max(-1, Math.min(1, (trace - 1) / 2)));
  assert(angle > .8, 'Object did not rotate by a significant angle');
  assert(cycles >= 2.5, 'Fewer than several autonomous cycles observed');
  device.write('rotation-cycles.json', { angleRadians: angle, cycles, observations, snapshot: rotating }); device.screenshot('rotated-deformed-orb');
  device.step('decay-rotation-persistence', `75s: ${cycles.toFixed(2)} phase cycles; rotation ${angle.toFixed(3)} rad by 8s; impulse decayed and object-space field unchanged. FT-05/06. Human motion comfort separate.`);

  await command({ type: 'configure', settings: { ...rotating.settings, speed: 0 } });
  const beforeContext = await scene();
  await cdp.evaluate(`(()=>{const c=document.querySelector('canvas');window.__acceptanceContext={lost:0,restored:0};c.addEventListener('webglcontextlost',window.__acceptanceLost=()=>window.__acceptanceContext.lost++);c.addEventListener('webglcontextrestored',window.__acceptanceRestored=()=>window.__acceptanceContext.restored++);window.__acceptanceExt=c.getContext('webgl2').getExtension('WEBGL_lose_context');if(!window.__acceptanceExt)throw Error('WEBGL_lose_context unavailable');window.__acceptanceExt.loseContext();})()`);
  await wait(500); await cdp.evaluate('window.__acceptanceExt.restoreContext()'); await wait(1600);
  const context = await cdp.evaluate('({...window.__acceptanceContext,lost:window.__acceptanceContext.lost,glLost:document.querySelector("canvas").getContext("webgl2").isContextLost(),error:document.querySelector("canvas").getContext("webgl2").getError(),notice:document.getElementById("notice").dataset.error||null})');
  device.write('native-context-recovery.json', context);
  assert.equal(context.lost, 1); assert.equal(context.restored, 1); assert.equal(context.glLost, false); assert.equal(context.error, 0); assert.equal(context.notice, null);
  assert.deepEqual(await scene(), beforeContext); device.write('native-context-recovery.json', context); device.screenshot('context-restored');
  await cdp.evaluate(`(()=>{const c=document.querySelector('canvas');c.removeEventListener('webglcontextlost',window.__acceptanceLost);c.removeEventListener('webglcontextrestored',window.__acceptanceRestored);delete window.__acceptanceExt;delete window.__acceptanceLost;delete window.__acceptanceRestored;delete window.__acceptanceContext;})()`);
  device.step('native-webgl-context-recovery', 'Real native WebView WebGL context lost/restored; scene unchanged, errors absent. FT-21/CT-07.');

  const white = [{ offset: 0, colorHex: '#FFFFFF' }, { offset: 1, colorHex: '#FFFFFF' }];
  const pink = [{ offset: 0, colorHex: '#FF7BBF' }, { offset: 1, colorHex: '#FFD6EC' }];
  await command({ type: 'configure', settings: { ...beforeContext.settings, paletteId: 'white', paletteStops: white } }); await wait(3500);
  await cdp.evaluate(`(()=>{const gl=document.querySelector('canvas').getContext('webgl2');window.__paletteProbe={gl,records:[],originals:{}};for(const name of ['texImage2D','texSubImage2D']){const original=gl[name];window.__paletteProbe.originals[name]=original;gl[name]=function(...args){const data=args.at(-1);if(ArrayBuffer.isView(data)&&data.byteLength===1024&&window.__paletteProbe.records.length<300)window.__paletteProbe.records.push({now:performance.now(),colors:[...data.slice(0,3),...data.slice(512,515),...data.slice(1020,1023)]});return original.apply(this,args);};}})()`);
  let paletteSamples;
  try {
    await wait(100);
    await command({ type: 'configure', settings: { ...beforeContext.settings, paletteId: 'pink', paletteStops: pink } }); await wait(3500);
    paletteSamples = await cdp.evaluate('window.__paletteProbe.records');
  } finally {
    await cdp.evaluate(`(()=>{const p=window.__paletteProbe;if(p){for(const [name,value]of Object.entries(p.originals))p.gl[name]=value;delete window.__paletteProbe;}})()`);
  }
  assert(paletteSamples.length >= 10, 'No actual palette texture uploads observed');
  assertColors(paletteSamples[0].colors, expectedPalette(white), 'Palette transition did not start at white');
  assertColors(paletteSamples.at(-1).colors, expectedPalette(pink), 'Palette transition did not reach pink');
  assert(new Set(paletteSamples.map(sample => sample.colors.join(','))).size >= 5, 'No intermediate palette colors');
  for (let i = 1; i < paletteSamples.length; i++) {
    const dt = Math.min(.1, (paletteSamples[i].now - paletteSamples[i - 1].now) / 1000);
    const bound = Math.ceil(255 * (1 - Math.exp(-dt * 4))) + 2;
    assert(paletteSamples[i].colors.every((v, c) => Math.abs(v - paletteSamples[i - 1].colors[c]) <= bound), 'Palette transition exceeded smoothing bound');
  }
  device.write('palette-transition.json', { samples: paletteSamples, policy: 'exponential tau=0.25s, delta capped 0.1s, 8-bit rounding tolerance2' });
  device.step('palette-smoothing', 'Actual palette texture has intermediate colors and follows bounded smoothing. FT-29; human aesthetic approval separate.');
  for (const [paletteId, stops] of [['white', white], ['pink', pink], ['lilac', [{ offset: 0, colorHex: '#9875FF' }, { offset: 1, colorHex: '#E1C7FF' }]], ['spectrum', ['#FF306E', '#FFBE32', '#33E8CA', '#6585FF', '#D855FF'].map((colorHex, i) => ({ offset: i / 4, colorHex }))], ['custom', [{ offset: 0, colorHex: '#3366CC' }, { offset: 1, colorHex: '#3366CC' }]]]) {
    const current = await scene();
    await command({ type: 'configure', settings: { ...current.settings, paletteId, paletteStops: stops, ...(paletteId === 'custom' ? { colorHex: '#3366CC' } : {}) } });
    const expected = await scene(); await persisted(expected); await restart(expected); await wait(3500);
    assert.deepEqual(await scene(), expected, `Palette ${paletteId} changed after restart`);
    const actualColors = await paletteTexture(), expectedColors = expectedPalette(stops);
    assertColors(actualColors, expectedColors, `Actual GPU palette ${paletteId} differs after restart`);
    device.write(`palette-restored-${paletteId}.json`, { snapshot: await scene(), actualColors, expectedColors });
  }
  device.step('palette-five-restarts', 'All four built-in palettes and custom preserve colors, seed and deformation after separate force-stop/restart. FT-29.');

  const beforeReset = await scene();
  const ribbon = shape(beforeReset, 'ribbon');
  if (!ribbon.controls.length) { ribbon.controls = [{ center: [.2, .1, 0], delta: [.02, .01, .015], radius: .5 }]; ribbon.revision++; }
  await restore(beforeReset); await persisted(beforeReset); await restart(beforeReset);
  await device.openPanel(); await device.click('Сбросить активную форму', { scroll: true }); await device.closePanel();
  const reset = await scene(); assert.equal(shape(reset, 'orb').controls.length, 0); assert.deepEqual(shape(reset, 'ribbon'), ribbon);
  assert.deepEqual(reset.settings, beforeReset.settings, 'Reset changed settings');
  for (const previous of beforeReset.shapes.filter(value => value.shapeId !== 'orb')) assert.deepEqual(shape(reset, previous.shapeId), previous, `Reset changed ${previous.shapeId}`);
  assert.equal(shape(reset, 'orb').seed, shape(beforeReset, 'orb').seed, 'Reset changed seed');
  await persisted(reset); await restart(reset); assert.deepEqual(await scene(), reset);
  device.write('reset-after-restart.json', await scene());
  device.step('native-reset-restart', 'Native Reset persists after force-stop/restart; other shape and settings preserved. FT-10.');
} catch (error) { failure = error; }
finally {
  try {
    if (baseline) {
      try {
        if (!cdp) await connect();
        await restore(baseline); await persisted(baseline);
      } catch (error) {
        device.report.cleanupRecovery = { reason: String(error.message).slice(0, 200), method: 'restart WebView and retry original scene restore' };
        await restart(); await restore(baseline); await persisted(baseline);
      }
      await restart(baseline);
      assert.deepEqual(await scene(), baseline); device.write('restored-baseline.json', await scene());
      device.step('cleanup-baseline', 'Original scene restored and confirmed after restart.');
    }
  } catch (error) { device.report.cleanupFailure = error.message; failure ??= error; }
  cdp?.close(); device.finish(failure);
}
