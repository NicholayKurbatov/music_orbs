import { test } from 'node:test';
import assert from 'node:assert/strict';
import { SculptureScene } from './SculptureScene';
import type { RenderFrame, RendererPort } from './ports/RendererPort';
import { ProceduralShapeFactory } from '../infrastructure/upstream/ProceduralShapeFactory';
import { DEFAULT_SETTINGS } from '../domain/ProfileLimits';
import { SyntheticMotion } from '../../bootstrap/SyntheticMotion';
class FakeRenderer implements RendererPort { frames: RenderFrame[] = []; present(f: RenderFrame) { this.frames.push(f); } resize() {} dispose() {} }
const create = () => { const renderer = new FakeRenderer(); return { renderer, app: new SculptureScene(new ProceduralShapeFactory(), renderer) }; };
const gesture = (app: SculptureScene) => { app.gesture({ pointerId: 1, phase: 'begin', center: [0, 0, 1], delta: [0.05, 0, 0], radius: 0.5 }); app.gesture({ pointerId: 1, phase: 'end', center: [0, 0, 1], delta: [0, 0, 0], radius: 0.5 }); };
test('FT-07/10/22: отдельные поля, seed, смена quality, reset только активной', async () => {
  const { app, renderer } = create(); gesture(app);
  const original = (await app.handle({ type: 'snapshot' })).snapshot!;
  await app.handle({ type: 'configure', settings: { ...DEFAULT_SETTINGS, shapeId: 'ribbon', quality: 'low' } }); gesture(app);
  await app.handle({ type: 'reset' });
  const reset = (await app.handle({ type: 'snapshot' })).snapshot!;
  assert.deepEqual(reset.shapes.find(s => s.shapeId === 'orb'), original.shapes.find(s => s.shapeId === 'orb'));
  assert.deepEqual(reset.shapes.find(s => s.shapeId === 'ribbon')!.controls, []);
  await app.handle({ type: 'configure', settings: { ...DEFAULT_SETTINGS, quality: 'high' } });
  app.frame(0.016, new SyntheticMotion().advance(0.016));
  assert.equal(renderer.frames[0].base.positions.length, 60000);
  assert.deepEqual(renderer.frames[0].deformation, original.shapes[0]);
});
test('FT-09: невалидный restore не применяет settings частично', async () => {
  const { app } = create(); gesture(app);
  const before = (await app.handle({ type: 'snapshot' })).snapshot!;
  const bad = { ...before, settings: { ...before.settings, shapeId: 'cloud' as const }, shapes: before.shapes.map(s => ({ ...s, generatorVersion: 99 })) };
  await assert.rejects(app.handle({ type: 'restore', snapshot: bad }), /SNAPSHOT_INVALID/);
  assert.deepEqual((await app.handle({ type: 'snapshot' })).snapshot, before);
});
test('FT-05: постоянное поле не меняется при нескольких циклах анимации и pause/resume', async () => {
  const { app, renderer } = create(); gesture(app);
  const before = (await app.handle({ type: 'snapshot' })).snapshot!;
  const motion = new SyntheticMotion();
  for (let i = 0; i < 240; i++) app.frame(0.1, motion.advance(0.1));
  assert.deepEqual((await app.handle({ type: 'snapshot' })).snapshot, before);
  const elapsed = renderer.frames.at(-1)!.elapsedSeconds;
  await app.handle({ type: 'pause' }); app.frame(999, motion.advance(0.1)); assert.equal(renderer.frames.at(-1)!.elapsedSeconds, elapsed);
  await app.handle({ type: 'resume' }); app.frame(999, motion.advance(0.1)); assert.ok(renderer.frames.at(-1)!.elapsedSeconds - elapsed < 0.051);
});
test('Чувствительность применяется один раз, snapshot восстановим независимо от частиц', async () => {
  const { app } = create(); await app.handle({ type: 'configure', settings: { ...DEFAULT_SETTINGS, touchSensitivity: 2 } }); gesture(app);
  const saved = (await app.handle({ type: 'snapshot' })).snapshot!;
  assert.equal(saved.shapes[0].controls[0].delta[0], 0.1);
  const another = create(); await another.app.handle({ type: 'restore', snapshot: saved });
  assert.deepEqual((await another.app.handle({ type: 'snapshot' })).snapshot, saved);
});
test('CT-03: прямой frame проверяет тот же MotionSignal, что wire, до present и мутации', async () => {
  const { app, renderer } = create(); gesture(app);
  const before = (await app.handle({ type: 'snapshot' })).snapshot;
  const valid = new SyntheticMotion().advance(0.016);
  for (const signal of [{ ...valid, sourceSessionId: 'bad id' }, { ...valid, slowCycleSeconds: 121 }, { ...valid, sourceSessionId: 'none', bpm: 90 }]) {
    assert.throws(() => app.frame(0.016, signal), /INVALID_INPUT/);
    assert.equal(renderer.frames.length, 0);
    assert.deepEqual((await app.handle({ type: 'snapshot' })).snapshot, before);
  }
});
