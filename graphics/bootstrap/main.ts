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

const element = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const notice = element('notice');
const report = (error: unknown) => { notice.textContent = error instanceof Error ? error.message : String(error); };
const canvas = element<HTMLCanvasElement>('scene');
const native = !!window.MusicOrbsNative || location.hostname === 'appassets.androidplatform.net';
try {
  const renderer = new ThreeRenderer(canvas);
  const app = new SculptureScene(new ProceduralShapeFactory(), renderer);
  const synthetic = new SyntheticMotion();
  let settings: SceneSettings = JSON.parse(JSON.stringify(DEFAULT_SETTINGS));
  let paused = false;
  let external: MotionSignal | null = null;
  let currentPhase = 0;
  let saveTimer: ReturnType<typeof setTimeout> | undefined;
  const storeKey = 'music-orbs-browser-v2';
  const snapshot = async () => (await app.handle({ type: 'snapshot' })).snapshot!;
  const reflect = () => {
    element<HTMLSelectElement>('shape').value = settings.shapeId; element<HTMLSelectElement>('material').value = settings.materialId;
    element<HTMLSelectElement>('palette').value = settings.paletteId; element<HTMLInputElement>('color').value = settings.colorHex;
    element<HTMLInputElement>('speed').value = String(settings.speed); element<HTMLInputElement>('amplitude').value = String(settings.amplitude);
    element<HTMLInputElement>('touch').value = String(settings.touchSensitivity); element<HTMLSelectElement>('quality').value = settings.quality;
    element<HTMLSelectElement>('fps').value = String(settings.targetFps); element<HTMLInputElement>('sync').checked = settings.musicSync;
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
  const resize = () => { if (innerWidth > 0 && innerHeight > 0) renderer.resize(innerWidth, innerHeight, devicePixelRatio); };
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
    let signal = synthetic.advance(frameDelta);
    if (external) signal = external;
    currentPhase = (currentPhase + frameDelta / (settings.musicSync ? signal.slowCycleSeconds : 24)) % 1;
    signal = { ...signal, phase: currentPhase };
    if (!settings.musicSync) signal = { ...signal, energy: 0, mode: 'autonomous' };
    element('mode').textContent = signal.mode === 'synced' ? 'Движение под музыку' : 'Автономное движение';
    try { app.frame(frameDelta, signal); } catch (error) { report(error); }
  };
  raf = requestAnimationFrame(draw);
  const visibility = () => { lastDraw = performance.now(); if (document.hidden) { void app.handle({ type: 'pause' }); } else if (!paused) { void app.handle({ type: 'resume' }); } };
  document.addEventListener('visibilitychange', visibility);
  const bridge = attachNativeBridge(
    { handle: command, gesture: stroke => app.gesture(stroke), frame: (delta, signal) => app.frame(delta, signal) },
    signal => { external = signal; currentPhase = signal.phase; },
    sourceSessionId => { external = { sourceSessionId, timestampSeconds: 0, bpm: null, slowCycleSeconds: 24, phase: currentPhase, energy: 0, intensity: 0, confidence: 0, mode: 'autonomous' }; },
  );
  const api = {
    command, snapshot,
    motion(signal: MotionSignal) { external = signal; currentPhase = signal.phase; },
    dispose() { cancelAnimationFrame(raf); clearTimeout(saveTimer); bridge?.close(); pointer.dispose(); renderer.dispose(); removeEventListener('resize', resize); document.removeEventListener('visibilitychange', visibility); },
    synthetic,
  };
  Object.assign(window, { musicOrbs: api });
  window.dispatchEvent(new Event('music-orbs-ready'));
} catch (error) { report(error); }
