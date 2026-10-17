import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'tests/device/results', `render-probe-${new Date().toISOString().replace(/[:.]/g, '-')}`);
await fs.mkdir(output, { recursive: true });
const report = { schemaVersion: 1, scope: 'paused-clear-particle-count-diagnostic-not-acceptance', startedAt: new Date().toISOString(), segments: [] };
const cdp = await connectLocalCdp(process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223');
try {
  for (const mode of process.argv.includes('--raw-clear-only') ? ['raw-clear'] : ['paused', 'clear-only', 'medium', 'low', 'high']) {
    const result = await cdp.evaluate(`(async()=>{
      const api=window.musicOrbs,canvas=document.querySelector('canvas'),gl=canvas.getContext('webgl2'),mode=${JSON.stringify(mode)};
      const before=await api.snapshot(),originalFlag=window.__musicOrbsDiagnosticsEnabled;
      if(document.hidden||api.visualState().sourceSessionId!=='none')throw Error('Foreground without audio required.');
      const saved=['drawArrays','drawElements'].map(name=>({name,descriptor:Object.getOwnPropertyDescriptor(gl,name),fn:gl[name]}));
      const clearColor=Array.from(gl.getParameter(gl.COLOR_CLEAR_VALUE));let probeRaf=0;
      const result={mode,before,drawsSuppressed:0,backing:[canvas.width,canvas.height],dpr:devicePixelRatio};
      try {
        if(mode==='paused'||mode==='raw-clear')await api.command({type:'pause'});
        else await api.command({type:'resume'});
        if(mode==='clear-only')for(const entry of saved)Object.defineProperty(gl,entry.name,{configurable:true,writable:true,value:function(){result.drawsSuppressed++;}});
        if(mode==='low'||mode==='high')await api.command({type:'configure',settings:{...before.settings,quality:mode}});
        await new Promise(resolve=>setTimeout(resolve,800));
        window.__musicOrbsDiagnosticsEnabled=true;api.profiling.start();
        result.raf=await new Promise(resolve=>{const started=performance.now(),intervals=[];let previous=null;function tick(time){if(mode==='raw-clear'){gl.clearColor(...clearColor);gl.clear(gl.COLOR_BUFFER_BIT);}if(previous!==null)intervals.push(time-previous);previous=time;if(performance.now()-started<3000&&intervals.length<512)probeRaf=requestAnimationFrame(tick);else resolve({durationMs:performance.now()-started,intervals});}probeRaf=requestAnimationFrame(tick);});
        result.metrics=api.profiling.stop();result.glError=gl.getError();result.measuredSettings=(await api.snapshot()).settings;
      } finally {
        api.profiling.stop();window.__musicOrbsDiagnosticsEnabled=originalFlag;
        cancelAnimationFrame(probeRaf);gl.clearColor(...clearColor);
        for(const entry of saved){if(entry.descriptor)Object.defineProperty(gl,entry.name,entry.descriptor);else delete gl[entry.name];}
        await api.command({type:'resume'});await api.command({type:'configure',settings:before.settings});
        result.methodsRestored=saved.every(entry=>gl[entry.name]===entry.fn);result.after=await api.snapshot();
      }
      return result;
    })()`);
    assert.deepEqual(result.before, result.after); assert.equal(result.methodsRestored, true); assert.equal(result.glError, 0);
    if (mode === 'clear-only') assert(result.drawsSuppressed > 0);
    const ordered = [...result.raf.intervals].sort((a, b) => a - b);
    result.raf.summary = { count: ordered.length, meanMs: ordered.reduce((a,b)=>a+b,0)/ordered.length, p95Ms: ordered[Math.ceil(ordered.length*0.95)-1] };
    report.segments.push(result);
    console.log(JSON.stringify({mode,quality:result.measuredSettings.quality,fps:result.metrics.submissionFps,p95:result.metrics.submissionIntervals.p95Ms,cpu:result.metrics.presentCpuDuration.p95Ms,raf:result.raf.summary,suppressed:result.drawsSuppressed,restored:true}));
  }
  report.result = 'diagnostic-complete';
} catch (error) { report.result = 'incomplete'; report.failure = error.message; process.exitCode = 1; }
finally {
  cdp.close(); report.finishedAt = new Date().toISOString();
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({result:report.result,output:path.relative(root,output).replaceAll('\\','/')}));
}
