import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'tests/device/results', `palette-probe-${new Date().toISOString().replace(/[:.]/g, '-')}`);
await fs.mkdir(output, { recursive: true });
const report = { schemaVersion: 1, scope: 'stable-white-1KiB-upload-A-B-A-diagnostic-not-acceptance', startedAt: new Date().toISOString(), segments: [] };
const cdp = await connectLocalCdp(process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223');
try {
  for (const mode of ['normal-before', 'skip-palette-upload', 'normal-after']) {
    const result = await cdp.evaluate(`(async()=>{
      const api=window.musicOrbs,gl=document.querySelector('canvas').getContext('webgl2');
      const before=await api.snapshot(),originalFlag=window.__musicOrbsDiagnosticsEnabled;
      if(document.hidden||api.visualState().sourceSessionId!=='none'||before.settings.paletteId!=='white'||before.settings.quality!=='medium'||before.settings.targetFps!==60)throw Error('Foreground medium/60 stable white scene without audio required.');
      const saved=['texImage2D','texSubImage2D'].map(name=>({name,descriptor:Object.getOwnPropertyDescriptor(gl,name),fn:gl[name]}));
      const counts={observed:0,skipped:0,byMethod:{},sampleBytes:null};
      const mode=${JSON.stringify(mode)},result={mode,before};
      try {
        for(const entry of saved)Object.defineProperty(gl,entry.name,{configurable:true,writable:true,value:function(...args){
          const data=args.find(arg=>ArrayBuffer.isView(arg)&&arg.byteLength===1024);
          if(data){counts.observed++;counts.byMethod[entry.name]=(counts.byMethod[entry.name]||0)+1;if(counts.sampleBytes===null)counts.sampleBytes=Array.from(new Uint8Array(data.buffer,data.byteOffset,16));
            if(mode==='skip-palette-upload'){counts.skipped++;return;}}
          return Reflect.apply(entry.fn,this,args);
        }});
        window.__musicOrbsDiagnosticsEnabled=true;api.profiling.start();
        await new Promise(resolve=>setTimeout(resolve,3000));
        result.metrics=api.profiling.stop();result.counts=counts;result.glError=gl.getError();
      } finally {
        api.profiling.stop();window.__musicOrbsDiagnosticsEnabled=originalFlag;
        for(const entry of saved){if(entry.descriptor)Object.defineProperty(gl,entry.name,entry.descriptor);else delete gl[entry.name];}
        result.methodsRestored=saved.every(entry=>gl[entry.name]===entry.fn);result.flagRestored=window.__musicOrbsDiagnosticsEnabled===originalFlag;
        result.after=await api.snapshot();
      }
      return result;
    })()`);
    assert.deepEqual(result.after, result.before); assert.equal(result.methodsRestored, true); assert.equal(result.flagRestored, true);
    assert.equal(result.glError, 0); assert(result.counts.observed > 0, 'No matching uploads observed');
    assert.equal(result.counts.skipped, mode === 'skip-palette-upload' ? result.counts.observed : 0);
    assert(result.counts.sampleBytes.every(value => value === 255), 'Palette is not stable white');
    report.segments.push(result);
    console.log(JSON.stringify({mode,fps:result.metrics.submissionFps,p95:result.metrics.submissionIntervals.p95Ms,cpu:result.metrics.presentCpuDuration.p95Ms,counts:result.counts,restored:true}));
  }
  report.result = 'diagnostic-complete';
} catch (error) { report.result = 'incomplete'; report.failure = error.message; process.exitCode = 1; }
finally {
  cdp.close(); report.finishedAt = new Date().toISOString();
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({result:report.result,output:path.relative(root,output).replaceAll('\\','/')}));
}
