import './style.css';
import { SculptureScene } from '../sculpture/application/SculptureScene';
import type { SceneCommand } from '../sculpture/application/SceneApplication';
import { ThreeRenderer } from '../sculpture/infrastructure/three/ThreeRenderer';
import { ProceduralShapeFactory } from '../sculpture/infrastructure/upstream/ProceduralShapeFactory';
import { PointerController } from '../sculpture/presentation/PointerController';
import { DEFAULT_SETTINGS, PALETTES, ProfileLimits } from '../sculpture/domain/ProfileLimits';
import type { PaletteId, SceneSettings } from '../sculpture/domain/Models';
import type { MotionSignal } from '../motion/domain/MotionSignal';
import { SyntheticMotion } from './SyntheticMotion';
import { attachNativeBridge } from '../contracts/WireBridge';
import { VisualPhase } from '../motion/domain/VisualPhase';
import { RenderMetrics, type RenderMetricsSnapshot } from './RenderMetrics';
import type { RendererPort } from '../sculpture/application/ports/RendererPort';

const element = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const notice = element('notice');
const report = (error: unknown) => { notice.dataset.error = 'true'; notice.textContent = error instanceof Error ? error.message : String(error); };
const canvas = element<HTMLCanvasElement>('scene');
const native = !!window.MusicOrbsNative || location.hostname === 'appassets.androidplatform.net';
if (native) document.documentElement.dataset.runtime = 'native';
try {
  const renderer = new ThreeRenderer(canvas);
  let metrics: RenderMetrics | null = null;
  let lastMetrics: RenderMetricsSnapshot | null = null;
  const diagnosticRenderer: RendererPort = {
    present(frame) {
      if (!metrics) { renderer.present(frame); return; }
      const start = performance.now();
      try { renderer.present(frame); metrics.recordPresent(start, performance.now()); }
      catch (error) { metrics.renderError(performance.now()); throw error; }
    },
    resize: (width, height, dpr) => renderer.resize(width, height, dpr), dispose: () => renderer.dispose(),
  };
  const app = new SculptureScene(new ProceduralShapeFactory(), diagnosticRenderer);
  const synthetic = new SyntheticMotion();
  const visual = new VisualPhase();
  let settings: SceneSettings = JSON.parse(JSON.stringify(DEFAULT_SETTINGS));
  let paused = false;
  let external = native;
  if (!native) visual.accept(synthetic.advance(0));
  let saveTimer: ReturnType<typeof setTimeout> | undefined;
  const storeKey = 'music-orbs-browser-v2';
  const snapshot = async () => (await app.handle({ type: 'snapshot' })).snapshot!;
  const reflect = () => {
    element<HTMLSelectElement>('shape').value = settings.shapeId; element<HTMLSelectElement>('material').value = settings.materialId;
    element<HTMLSelectElement>('palette').value = settings.paletteId; element<HTMLInputElement>('color').value = settings.colorHex;
    element<HTMLInputElement>('speed').value = String(settings.speed); element<HTMLInputElement>('amplitude').value = String(settings.amplitude);
    element<HTMLInputElement>('touch').value = String(settings.touchSensitivity); element<HTMLSelectElement>('quality').value = settings.quality;
    element<HTMLSelectElement>('fps').value = String(settings.targetFps); element<HTMLInputElement>('sync').checked = settings.musicSync;
    element<HTMLInputElement>('full-sync').checked = settings.fullSync === true;
    element<HTMLInputElement>('full-sync').disabled = !settings.musicSync;
  };
  const save = () => {
    if (native) { window.dispatchEvent(new Event('music-orbs-dirty')); return; }
    clearTimeout(saveTimer);
    saveTimer = setTimeout(() => { void snapshot().then(value => { localStorage.setItem(storeKey, JSON.stringify({ schemaVersion: 2, scene: value })); }).catch(report); }, 180);
  };
  const command = async (value: SceneCommand) => {
    const reply = await app.handle(value);
    if (value.type === 'configure' || value.type === 'restore') { settings = (await snapshot()).settings; reflect(); }
    if (value.type === 'pause') { paused = true; synthetic.paused = true; }
    if (value.type === 'resume') { paused = false; synthetic.paused = false; }
    if (value.type === 'pause' || value.type === 'resume') metrics?.setPaused(paused || document.hidden, performance.now());
    if (value.type === 'configure' || value.type === 'restore' || value.type === 'reset') save();
    return reply;
  };
  if (!native) {
    try {
      const raw = localStorage.getItem(storeKey);
      if (raw) { const value = JSON.parse(raw); if (value.schemaVersion !== 2) throw new Error('SNAPSHOT_INVALID'); await app.handle({ type: 'restore', snapshot: value.scene }); settings = (await snapshot()).settings; }
    } catch { notice.textContent = 'Сохранённая сессия повреждена. Открыта исходная форма.'; }
  }
  reflect();
  const pointer = new PointerController(canvas, renderer, app, save, report);
  const resize = () => {
    if (innerWidth <= 0 || innerHeight <= 0) return;
    // WebView может возвращать 0 для dvh после измерения AndroidView; backing buffer и CSS должны совпадать.
    canvas.style.width = `${innerWidth}px`; canvas.style.height = `${innerHeight}px`;
    renderer.resize(innerWidth, innerHeight, devicePixelRatio);
  };
  resize(); addEventListener('resize', resize);
  const update = async (patch: Partial<SceneSettings>) => { try { await command({ type: 'configure', settings: { ...settings, ...patch } }); } catch (error) { report(error); reflect(); } };
  element('toggle').addEventListener('click', () => { const panel = element('panel'); panel.hidden = !panel.hidden; element('toggle').setAttribute('aria-expanded', String(!panel.hidden)); });
  element<HTMLSelectElement>('shape').addEventListener('change', event => {
    const shapeId = (event.target as HTMLSelectElement).value as SceneSettings['shapeId'];
    const materialId = shapeId === 'cloud' ? 'soft' : shapeId === 'pixel-cloud' ? 'pixels' : 'dots';
    const paletteId: PaletteId = shapeId === 'ribbon' ? 'pink' : shapeId === 'pixel-cloud' ? 'spectrum' : shapeId === 'cloud' ? 'lilac' : 'white';
    void update({ shapeId, materialId, paletteId, paletteStops: PALETTES[paletteId] });
  });
  element<HTMLSelectElement>('material').addEventListener('change', event => { void update({ materialId: (event.target as HTMLSelectElement).value as SceneSettings['materialId'] }); });
  element<HTMLSelectElement>('palette').addEventListener('change', event => { const paletteId = (event.target as HTMLSelectElement).value as PaletteId; void update({ paletteId, paletteStops: paletteId === 'custom' ? [{ offset: 0, colorHex: settings.colorHex }, { offset: 1, colorHex: settings.colorHex }] : PALETTES[paletteId] }); });
  element<HTMLInputElement>('color').addEventListener('input', event => { const colorHex = (event.target as HTMLInputElement).value; void update({ colorHex, paletteId: 'custom', paletteStops: [{ offset: 0, colorHex }, { offset: 1, colorHex }] }); });
  for (const [id, key] of [['speed', 'speed'], ['amplitude', 'amplitude'], ['touch', 'touchSensitivity']] as const) element<HTMLInputElement>(id).addEventListener('input', event => { void update({ [key]: Number((event.target as HTMLInputElement).value) }); });
  element<HTMLSelectElement>('quality').addEventListener('change', event => { void update({ quality: (event.target as HTMLSelectElement).value as SceneSettings['quality'] }); });
  element<HTMLSelectElement>('fps').addEventListener('change', event => { void update({ targetFps: Number((event.target as HTMLSelectElement).value) as 30 | 60 }); });
  element<HTMLInputElement>('sync').addEventListener('change', event => { void update({ musicSync: (event.target as HTMLInputElement).checked }); });
  element<HTMLInputElement>('full-sync').addEventListener('change', event => { void update({ fullSync: (event.target as HTMLInputElement).checked }); });
  element<HTMLInputElement>('test-motion').addEventListener('change', event => { synthetic.enabled = (event.target as HTMLInputElement).checked; });
  element<HTMLInputElement>('bpm').addEventListener('input', event => { synthetic.bpm = Number((event.target as HTMLInputElement).value); });
  element('source').addEventListener('click', () => synthetic.switchSource());
  element('reset').addEventListener('click', () => { void command({ type: 'reset' }).catch(report); });
  element('pause').addEventListener('click', () => { void command({ type: paused ? 'resume' : 'pause' }).then(() => { element('pause').textContent = paused ? 'Продолжить' : 'Пауза'; }).catch(report); });
  if (native) { element('synthetic').hidden = true; element('toggle').hidden = true; }
  let lastDraw = performance.now(), raf = 0;
  const draw = (now: number) => {
    raf = requestAnimationFrame(draw);
    if (paused || document.hidden) return;
    if (now - lastDraw < 1000 / settings.targetFps - 0.5) return;
    const frameDelta = Math.min((now - lastDraw) / 1000, ProfileLimits.maxDeltaSeconds); lastDraw = now;
    if (!external) visual.accept(synthetic.advance(frameDelta, settings.musicSync));
    let signal = visual.advance(frameDelta);
    if (!settings.musicSync) signal = { ...signal, energy: 0, mode: 'autonomous' };
    element('mode').textContent = signal.mode === 'synced' ? 'Движение под музыку' : 'Автономное движение';
    try { app.frame(frameDelta, signal); } catch (error) { report(error); }
  };
  raf = requestAnimationFrame(draw);
  const visibility = () => { lastDraw = performance.now(); metrics?.setPaused(paused || document.hidden, lastDraw); if (document.hidden) { void app.handle({ type: 'pause' }); } else if (!paused) { void app.handle({ type: 'resume' }); } };
  document.addEventListener('visibilitychange', visibility);
  const bridge = attachNativeBridge(
    { handle: command, gesture: stroke => app.gesture(stroke), frame: (delta, signal) => app.frame(delta, signal) },
    signal => { external = true; visual.accept(signal); },
    sourceSessionId => { external = true; visual.setSource(sourceSessionId); },
  );
  const profileLost = () => metrics?.contextLost(performance.now());
  const profileRestored = () => metrics?.contextRestored(performance.now());
  const stopProfiling = () => {
    if (metrics) { lastMetrics = metrics.stop(performance.now()); metrics = null; }
    canvas.removeEventListener('webglcontextlost', profileLost); canvas.removeEventListener('webglcontextrestored', profileRestored);
    return lastMetrics;
  };
  const api = {
    command, snapshot,
    motion(signal: MotionSignal) { external = true; visual.accept(signal); },
    visualState() { return visual.advance(0); },
    animationState() { return app.animationState(); },
    profiling: {
      start(options: { intervalThresholdMs?: number } = {}) {
        if (!(window as Window & { __musicOrbsDiagnosticsEnabled?: boolean }).__musicOrbsDiagnosticsEnabled) throw new Error('INVALID_INPUT: profiling disabled');
        if (metrics) throw new Error('INVALID_INPUT: profiling active');
        metrics = new RenderMetrics(performance.now(), options.intervalThresholdMs ?? 25, paused || document.hidden, canvas.getContext('webgl2')?.isContextLost() ?? false);
        lastMetrics = null;
        canvas.addEventListener('webglcontextlost', profileLost); canvas.addEventListener('webglcontextrestored', profileRestored);
        return metrics.read(performance.now());
      },
      read() { return metrics?.read(performance.now()) ?? lastMetrics; },
      stop: stopProfiling,
    },
    dispose() { stopProfiling(); cancelAnimationFrame(raf); clearTimeout(saveTimer); bridge?.close(); pointer.dispose(); renderer.dispose(); removeEventListener('resize', resize); document.removeEventListener('visibilitychange', visibility); },
    synthetic,
  };
  Object.assign(window, { musicOrbs: api });
  window.dispatchEvent(new Event('music-orbs-ready'));
} catch (error) { report(error); }
