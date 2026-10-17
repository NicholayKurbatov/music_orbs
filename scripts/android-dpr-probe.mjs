import fs from 'node:fs/promises';
import path from 'node:path';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { connectLocalCdp } from '../graphics/scripts/cdp-client.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const output = path.join(root, 'tests/device/results', `dpr-probe-${new Date().toISOString().replace(/[:.]/g, '-')}`);
await fs.mkdir(output, { recursive: true });
const report = { schemaVersion: 1, scope: 'temporary-backing-DPR1-diagnostic-not-acceptance', startedAt: new Date().toISOString(), segments: [] };
const cdp = await connectLocalCdp(process.env.MUSIC_ORBS_CDP_URL || 'http://127.0.0.1:9223');
try {
  for (const shape of ['orb', 'cloud']) {
    const result = await cdp.evaluate(`(async()=>{
      const api=window.musicOrbs, canvas=document.querySelector('canvas'), gl=canvas.getContext('webgl2');
      const descriptor=Object.getOwnPropertyDescriptor(window,'devicePixelRatio');
      const originalFlag=window.__musicOrbsDiagnosticsEnabled;
      const state=async()=>({visible:!document.hidden,dpr:devicePixelRatio,css:[canvas.getBoundingClientRect().width,canvas.getBoundingClientRect().height],backing:[canvas.width,canvas.height],snapshot:await api.snapshot(),glError:gl.getError()});
      const original=await state(), result={shape:${JSON.stringify(shape)},original};
      if(!original.visible||api.visualState().sourceSessionId!=='none')throw Error('Foreground scene without audio required.');
      try {
        Object.defineProperty(window,'devicePixelRatio',{value:1,configurable:true});dispatchEvent(new Event('resize'));
        const cloud=${shape === 'cloud'};
        const colors=cloud?['#9875FF','#E1C7FF']:['#FFFFFF','#FFFFFF'];
        await api.command({type:'configure',settings:{...original.snapshot.settings,shapeId:${JSON.stringify(shape)},materialId:cloud?'soft':'dots',paletteId:cloud?'lilac':'white',paletteStops:colors.map((colorHex,i)=>({offset:i,colorHex})),quality:'medium',targetFps:60,speed:0.5,amplitude:0.3,musicSync:false}});
        await new Promise(resolve=>setTimeout(resolve,1000));
        result.temporary=await state();window.__musicOrbsDiagnosticsEnabled=true;api.profiling.start();
        await new Promise(resolve=>setTimeout(resolve,3000));
        result.metrics=api.profiling.stop();result.afterMeasurement=await state();
      } finally {
        api.profiling.stop();window.__musicOrbsDiagnosticsEnabled=originalFlag;
        if(descriptor)Object.defineProperty(window,'devicePixelRatio',descriptor);else delete window.devicePixelRatio;
        dispatchEvent(new Event('resize'));await api.command({type:'configure',settings:original.snapshot.settings});
        result.restored=await state();
      }
      return result;
    })()`);
    assert.deepEqual(result.restored, result.original, 'DPR/settings/fields restoration');
    assert.equal(result.temporary.dpr, 1); assert.equal(result.temporary.visible, true);
    assert.equal(result.afterMeasurement.glError, 0); assert.deepEqual(result.afterMeasurement.snapshot.shapes, result.original.snapshot.shapes);
    report.segments.push(result);
    console.log(JSON.stringify({shape,beforeBacking:result.original.backing,temporaryBacking:result.temporary.backing,fps:result.metrics.submissionFps,p95:result.metrics.submissionIntervals.p95Ms,cpuP95:result.metrics.presentCpuDuration.p95Ms,restored:true}));
  }
  report.result = 'diagnostic-complete';
} catch (error) { report.result = 'incomplete'; report.failure = error.message; process.exitCode = 1; }
finally {
  cdp.close(); report.finishedAt = new Date().toISOString();
  await fs.writeFile(path.join(output, 'report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify({result:report.result,output:path.relative(root,output).replaceAll('\\','/')}));
}
