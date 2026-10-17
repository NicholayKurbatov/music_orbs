import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'tests/device/results', `raf-probe-${new Date().toISOString().replace(/[:.]/g, '-')}`);
await fs.mkdir(output, { recursive: true });
const report = { schemaVersion: 1, scope: 'short-RAF-diagnostic-not-performance-acceptance', startedAt: new Date().toISOString() };
const cdp = await connectLocalCdp(process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223');
const sample = `new Promise(resolve => {
  const started = performance.now(), timestamps = [], callbackTimes = [];
  let previousTimestamp = null, previousCallback = null;
  function tick(timestamp) {
    const now = performance.now();
    if (previousTimestamp !== null) { timestamps.push(timestamp - previousTimestamp); callbackTimes.push(now - previousCallback); }
    previousTimestamp = timestamp; previousCallback = now;
    if (now - started < 3000 && timestamps.length < 512) requestAnimationFrame(tick);
    else resolve({durationMs:now-started,rafTimestampIntervalsMs:timestamps,callbackIntervalsMs:callbackTimes});
  }
  requestAnimationFrame(tick);
})`;
const summarize = values => {
  const ordered = [...values].sort((a, b) => a - b);
  return { count: values.length, meanMs: values.reduce((a, b) => a + b, 0) / values.length,
    p50Ms: ordered[Math.ceil(ordered.length * 0.5) - 1], p95Ms: ordered[Math.ceil(ordered.length * 0.95) - 1] };
};
try {
  report.initial = await cdp.evaluate(`(async()=>{const gl=document.querySelector('canvas').getContext('webgl2'), debug=gl.getExtension('WEBGL_debug_renderer_info');return {visible:!document.hidden,settings:(await window.musicOrbs.snapshot()).settings,motion:window.musicOrbs.visualState(),glVersion:gl.getParameter(gl.VERSION),glRenderer:debug?gl.getParameter(debug.UNMASKED_RENDERER_WEBGL):null,glVendor:debug?gl.getParameter(debug.UNMASKED_VENDOR_WEBGL):null};})()`);
  if (!report.initial.visible || report.initial.motion.sourceSessionId !== 'none') throw new Error('Foreground scene without audio required.');
  await cdp.evaluate('window.musicOrbs.command({type:"pause"})');
  report.paused = await cdp.evaluate(sample);
  await cdp.evaluate('window.musicOrbs.command({type:"resume"})');
  await new Promise(resolve => setTimeout(resolve, 1000));
  await cdp.evaluate('window.__musicOrbsDiagnosticsEnabled=true;window.musicOrbs.profiling.start()');
  report.resumed = await cdp.evaluate(sample);
  report.submissions = await cdp.evaluate('window.musicOrbs.profiling.stop()');
  for (const state of ['paused', 'resumed']) {
    report[state].rafTimestampSummary = summarize(report[state].rafTimestampIntervalsMs);
    report[state].callbackSummary = summarize(report[state].callbackIntervalsMs);
  }
  report.result = 'diagnostic-complete';
} catch (error) { report.result = 'incomplete'; report.failure = error.message; process.exitCode = 1; }
finally {
  try { await cdp.evaluate('window.musicOrbs.profiling.stop();window.__musicOrbsDiagnosticsEnabled=false;window.musicOrbs.command({type:"resume"})'); }
  catch (error) { report.cleanupFailure = error.message; report.result = 'incomplete'; process.exitCode = 1; }
  cdp.close(); report.finishedAt = new Date().toISOString();
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({output:path.relative(root,output).replaceAll('\\','/'),result:report.result,initial:report.initial,
    paused:report.paused?.rafTimestampSummary,resumed:report.resumed?.rafTimestampSummary,submissionFps:report.submissions?.submissionFps,cpuP95:report.submissions?.presentCpuDuration.p95Ms},null,2));
}
