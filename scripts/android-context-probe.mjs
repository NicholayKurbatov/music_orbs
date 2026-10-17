import assert from 'node:assert/strict';
import { AndroidDevice, appId, wait } from './android-device.mjs';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

if (process.argv.includes('--help')) {
  console.log('node scripts/android-context-probe.mjs [--serial emulator-5554]\nДиагностика настоящей потери/восстановления WebGL: сохраняет GL errors и console warnings; успешный сбор данных не означает успешную приёмку восстановления.');
  process.exit(0);
}
const device = new AndroidDevice('context-probe');
device.report.scope = 'context-error-diagnostic-not-recovery-acceptance';
let cdp, baseline, failure;
const messages = [];
try {
  const installedApk = device.shell('pm', 'path', appId).trim().split(/\r?\n/).find(value => value.endsWith('/base.apk'))?.slice(8);
  assert(installedApk && /^\/data\/app\/[A-Za-z0-9_+~\/=.-]+\/base\.apk$/.test(installedApk), 'Installed APK path required');
  device.report.installedApkSha256 = device.shell('sha256sum', installedApk).trim().split(/\s+/)[0];
  device.report.webView = device.shell('dumpsys', 'webviewupdate').split(/\r?\n/).find(value => value.includes('Current WebView package'))?.trim();
  await device.start(); const forwarding = await device.cdp(); forwarding.close();
  cdp = await connectLocalCdp('http://127.0.0.1:9223');
  cdp.onEvent(value => { if (messages.length < 64 && ['Log.entryAdded', 'Runtime.exceptionThrown', 'Runtime.consoleAPICalled'].includes(value.method)) messages.push(value); });
  await cdp.call('Runtime.enable'); await cdp.call('Log.enable');
  baseline = await cdp.evaluate('window.musicOrbs.snapshot()');
  await cdp.evaluate(`(()=>{const canvas=document.querySelector('canvas'),gl=canvas.getContext('webgl2');
    const errors=()=>{const values=[];for(let i=0;i<8;i++){const error=gl.getError();if(!error)break;values.push(error);}return values;};
    const p={events:[],stages:[],gl,errors};window.__contextProbe=p;
    p.read=stage=>p.stages.push({stage,now:performance.now(),lost:gl.isContextLost(),errors:errors()});
    p.lost=e=>{p.events.push({type:e.type,now:performance.now(),prevented:e.defaultPrevented});p.read('lost-event');};
    p.restored=e=>{p.events.push({type:e.type,now:performance.now()});p.read('restored-event');};
    canvas.addEventListener('webglcontextlost',p.lost);canvas.addEventListener('webglcontextrestored',p.restored);
    p.read('before');p.ext=gl.getExtension('WEBGL_lose_context');if(!p.ext)throw Error('No context extension');p.ext.loseContext();return true;})()`);
  await wait(700); await cdp.evaluate('window.__contextProbe.read("before-restore-call");window.__contextProbe.ext.restoreContext();window.__contextProbe.read("after-restore-call");true');
  await wait(700); await cdp.evaluate('window.__contextProbe.read("after-700ms");true');
  await wait(1500);
  const result = await cdp.evaluate('(()=>{const p=window.__contextProbe;p.read("after-2200ms");return {events:p.events,stages:p.stages,notice:document.getElementById("notice").textContent,noticeError:document.getElementById("notice").dataset.error||null};})()');
  result.messages = messages; result.snapshot = await cdp.evaluate('window.musicOrbs.snapshot()');
  device.report.contextRecovery = result.stages.filter(stage => stage.stage.startsWith('after-') && !stage.lost).some(stage => stage.errors.length) ? 'errors-observed' : 'no-errors-observed';
  device.write('context.json', result); assert.deepEqual(result.snapshot, baseline);
  device.screenshot('after-context'); device.step('context-diagnostic', JSON.stringify(result.stages));
} catch (error) { failure = error; }
finally {
  try {
    if (cdp) await cdp.evaluate('(()=>{const p=window.__contextProbe;if(p){const c=document.querySelector("canvas");c.removeEventListener("webglcontextlost",p.lost);c.removeEventListener("webglcontextrestored",p.restored);delete window.__contextProbe;}return true;})()');
    device.shell('am', 'force-stop', appId); await device.start();
    cdp?.close(); cdp = await device.cdp();
    if (baseline) assert.deepEqual(await cdp.evaluate('window.musicOrbs.snapshot()'), baseline);
  } catch (error) { device.report.cleanupFailure = error.message; failure ??= error; }
  cdp?.close(); device.finish(failure);
  if (!failure) { device.report.result = 'diagnostic-complete'; device.write('report.json', device.report); }
}
