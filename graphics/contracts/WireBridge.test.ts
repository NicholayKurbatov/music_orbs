import { test } from 'node:test';
import assert from 'node:assert/strict';
import { attachNativeBridge } from './WireBridge';
import { decodeEnvelope, type Envelope } from './Wire';
import type { SceneApplication, SceneCommand, SceneEvent } from '../sculpture/application/SceneApplication';
import type { MotionSignal } from '../motion/domain/MotionSignal';
import { ProfileLimits, DEFAULT_SETTINGS } from '../sculpture/domain/ProfileLimits';
import { SculptureScene } from '../sculpture/application/SculptureScene';
import { ProceduralShapeFactory } from '../sculpture/infrastructure/upstream/ProceduralShapeFactory';

class FakeWindow extends EventTarget {
  messages: string[] = [];
  MusicOrbsNative: { postMessage: (json: string) => void } | undefined = { postMessage: json => this.messages.push(json) };
  MusicOrbsReceive: ((json: string) => void) | undefined;
  replies(): Envelope[] { return this.messages.filter(s => s.startsWith('{')).map(decodeEnvelope); }
  deliver(sequence: number, type: string, payload: unknown, requestId?: string, bridgeSessionId = 'bridge-one') {
    this.MusicOrbsReceive?.(JSON.stringify({ protocolVersion: 1, bridgeSessionId, sequence, type, payload, ...(requestId ? { requestId } : {}) }));
  }
  ready(sequence = 0, sourceSessionId = 'none') { this.deliver(sequence, 'ready', { runtimeTimestampSeconds: 0, sourceSessionId }); }
}
const tick = () => new Promise<void>(resolve => setImmediate(resolve));
const install = () => { const value = new FakeWindow(); Object.assign(globalThis, { window: value }); return value; };
const fakeApp = (handle: SceneApplication['handle'] = async () => ({ type: 'ack' })): SceneApplication => ({ handle, frame() {}, gesture() {} });
const motion = (sourceSessionId: string, timestampSeconds = 1): MotionSignal => ({ sourceSessionId, timestampSeconds, bpm: 90, slowCycleSeconds: 21.3333333, phase: 0.37, energy: 0.6, intensity: 0.4, confidence: 0.9, mode: 'synced' });

test('bridge отсутствует без native; initial graphics-ready ожидает авторитетный ready', async () => {
  const w = install(); w.MusicOrbsNative = undefined;
  assert.equal(attachNativeBridge(fakeApp(), () => {}), null);
  assert.equal(w.MusicOrbsReceive, undefined);
  w.MusicOrbsNative = { postMessage: raw => w.messages.push(raw) };
  const calls: SceneCommand[] = [];
  const bridge = attachNativeBridge(fakeApp(async command => { calls.push(command); return { type: 'ack' }; }), () => {})!;
  assert.deepEqual(w.messages, ['graphics-ready']);
  w.deliver(0, 'command', { type: 'pause' }, 'early'); await tick(); assert.deepEqual(calls, []);
  w.ready();
  const ready = w.replies()[0]; assert.equal(ready.type, 'ready'); assert.equal(ready.bridgeSessionId, 'bridge-one');
  assert.ok(Number.isFinite((ready.payload as { browserTimestampSeconds: number }).browserTimestampSeconds));
  bridge.close();
});

test('sequence/bridge/source фильтры и authoritative ready не принимают старый motion', () => {
  const w = install(), received: MotionSignal[] = [], sources: string[] = [];
  const bridge = attachNativeBridge(fakeApp(), signal => received.push(signal), id => sources.push(id))!;
  w.ready(0, 'source-one'); w.deliver(1, 'motion', motion('source-one'));
  w.deliver(1, 'motion', motion('source-one', 2)); // sequence duplicate
  w.deliver(20, 'motion', motion('source-one', 2), undefined, 'old-bridge');
  w.deliver(2, 'motion', motion('old-source', 2));
  w.deliver(3, 'motion', motion('source-one', 0.5));
  assert.equal(received.length, 1);
  w.ready(4, 'source-two'); w.deliver(5, 'motion', motion('source-one', 9));
  w.deliver(6, 'motion', motion('source-two', 0));
  w.ready(7, 'source-two'); w.ready(8, 'none');
  assert.deepEqual(sources, ['source-one', 'source-two', 'none']);
  assert.deepEqual(received.map(s => s.sourceSessionId), ['source-one', 'source-two']);
  assert.equal(received[1].phase, received[0].phase);
  bridge.close();
});

test('oversized/невалидные bytes и версия не потребляют sequence и не мутируют app', async () => {
  const w = install(), calls: SceneCommand[] = [];
  const bridge = attachNativeBridge(fakeApp(async command => { calls.push(command); return { type: 'ack' }; }), () => {})!;
  w.ready();
  w.MusicOrbsReceive!('я'.repeat(ProfileLimits.maxPayloadBytes / 2 + 1));
  w.MusicOrbsReceive!('{broken');
  const hostile = JSON.stringify({ protocolVersion: 1, bridgeSessionId: 'bridge-one', sequence: 999, type: 'command', requestId: 'hostile', payload: { type: 'reset' } });
  w.MusicOrbsReceive!(hostile.slice(0, -1) + ',"padding":' + '['.repeat(16000) + '0' + ']'.repeat(16000) + '}');
  w.MusicOrbsReceive!(JSON.stringify({ protocolVersion: 99, bridgeSessionId: 'bridge-one', sequence: 999, type: 'command', requestId: 'future', payload: { type: 'reset' } }));
  w.deliver(1, 'command', { type: 'reset' }, 'valid'); await tick();
  assert.deepEqual(calls, [{ type: 'reset' }]); assert.equal(w.replies().at(-1)!.requestId, 'valid');
  bridge.close();
});

test('async app команды строго последовательны; ошибка сохраняет requestId/code, очередь продолжает работу', async () => {
  const w = install(), calls: string[] = [];
  let rejectFirst!: (reason: unknown) => void;
  const first = new Promise<SceneEvent>((_, reject) => { rejectFirst = reject; });
  const bridge = attachNativeBridge(fakeApp(command => { calls.push(command.type); return calls.length === 1 ? first : Promise.resolve({ type: 'ack' }); }), () => {})!;
  w.ready(); w.deliver(1, 'command', { type: 'restore', snapshot: { settings: DEFAULT_SETTINGS, shapes: [{ shapeId: 'orb', seed: 1, generatorVersion: 1, revision: 0, controls: [] }] } }, 'first');
  w.deliver(2, 'command', { type: 'resume' }, 'second'); await tick();
  assert.deepEqual(calls, ['restore']);
  rejectFirst(new Error('SNAPSHOT_INVALID: private path must not escape')); await tick();
  assert.deepEqual(calls, ['restore', 'resume']);
  const replies = w.replies();
  assert.deepEqual(replies.slice(1).map(r => [r.type, r.requestId]), [['error', 'first'], ['ack', 'second']]);
  assert.equal((replies[1].payload as { code: string }).code, 'SNAPSHOT_INVALID');
  assert.ok(!JSON.stringify(replies).includes('private path'));
  assert.deepEqual(replies.map(r => r.sequence), [0, 1, 2]); bridge.close();
});

test('pending bounded16 до mutation; close отменяет queued apply и suppress pending reply/dirty listener', async () => {
  const w = install(); let called = 0, resolveFirst!: (event: SceneEvent) => void;
  const first = new Promise<SceneEvent>(resolve => { resolveFirst = resolve; });
  const bridge = attachNativeBridge(fakeApp(() => { called++; return first; }), () => {})!;
  w.ready();
  for (let i = 1; i <= 17; i++) w.deliver(i, 'command', { type: 'pause' }, `request-${i}`);
  assert.equal(called, 0); assert.equal(w.replies().at(-1)!.type, 'error'); assert.equal(w.replies().at(-1)!.requestId, 'request-17');
  await tick(); assert.equal(called, 1);
  w.dispatchEvent(new Event('music-orbs-dirty')); assert.equal(w.messages.at(-1), 'scene-dirty');
  const count = w.messages.length; bridge.close(); bridge.close(); resolveFirst({ type: 'ack' });
  await tick(); await tick(); w.dispatchEvent(new Event('music-orbs-dirty'));
  assert.equal(w.messages.length, count); assert.equal(called, 1); assert.equal(w.MusicOrbsReceive, undefined);
});

test('реальная app: configure/reset/snapshot через bridge дают согласованный полный снимок', async () => {
  const w = install();
  const app = new SculptureScene(new ProceduralShapeFactory(), { present() {}, resize() {}, dispose() {} });
  const bridge = attachNativeBridge(app, () => {})!; w.ready();
  w.deliver(1, 'command', { type: 'configure', settings: { ...DEFAULT_SETTINGS, shapeId: 'ribbon', quality: 'low' } }, 'configure');
  w.deliver(2, 'command', { type: 'reset' }, 'reset'); w.deliver(3, 'command', { type: 'snapshot' }, 'snapshot'); await tick();
  const replies = w.replies(); assert.deepEqual(replies.map(r => r.type), ['ready', 'ack', 'ack', 'snapshot']);
  assert.equal((replies.at(-1)!.payload as { settings: typeof DEFAULT_SETTINGS }).settings.shapeId, 'ribbon');
  assert.equal((replies.at(-1)!.payload as { shapes: { shapeId: string; revision: number }[] }).shapes.find(s => s.shapeId === 'ribbon')!.revision, 1);
  bridge.close();
});
