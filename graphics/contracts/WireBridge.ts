import type { MotionSignal } from '../motion/domain/MotionSignal';
import type { SceneApplication } from '../sculpture/application/SceneApplication';
import { decodeEnvelope, encodeEnvelope, type Envelope, NO_SOURCE } from './Wire';
import { ProfileLimits } from '../sculpture/domain/ProfileLimits';

declare global {
  interface Window {
    MusicOrbsNative?: { postMessage(json: string): void };
    MusicOrbsReceive?: (json: string) => void;
  }
}
/** Адаптер доверенного локального WebView; в браузере без native возвращает null. */
export function attachNativeBridge(app: SceneApplication, onMotion: (signal: MotionSignal) => void, onSourceChange?: (sourceSessionId: string) => void): { close(): void } | null {
  const native = window.MusicOrbsNative;
  if (!native) return null;
  let bridgeId: string | undefined;
  let incomingSequence = -1, outgoingSequence = 0;
  let sourceId = NO_SOURCE, motionTimestamp = -1;
  let sourceInitialized = false;
  let closed = false;
  let pending = 0;
  let commandChain: Promise<void> = Promise.resolve();
  const send = (type: Envelope['type'], payload: unknown, requestId?: string) => {
    if (!closed && bridgeId) {
      try { native.postMessage(encodeEnvelope({ protocolVersion: 1, bridgeSessionId: bridgeId, sequence: outgoingSequence++, type, payload, ...(requestId ? { requestId } : {}) })); }
      catch { /* Сбой транспорта завершается deadline на native; не создаём unhandled rejection. */ }
    }
  };
  const receive = (raw: string) => {
    if (closed) return;
    let envelope: Envelope;
    try { envelope = decodeEnvelope(raw); } catch { return; }
    if (envelope.type === 'ready' && !bridgeId) bridgeId = envelope.bridgeSessionId;
    if (!bridgeId || envelope.bridgeSessionId !== bridgeId || envelope.sequence <= incomingSequence) return;
    incomingSequence = envelope.sequence;
    if (envelope.type === 'ready') {
      const ready = envelope.payload as { runtimeTimestampSeconds: number; sourceSessionId?: string };
      const nextSource = ready.sourceSessionId ?? sourceId;
      if (!sourceInitialized || sourceId !== nextSource) {
        sourceInitialized = true; sourceId = nextSource; motionTimestamp = -1; onSourceChange?.(sourceId);
      }
      send('ready', { runtimeTimestampSeconds: ready.runtimeTimestampSeconds, browserTimestampSeconds: performance.now() / 1000, sourceSessionId: sourceId });
    } else if (envelope.type === 'motion') {
      const signal = envelope.payload as MotionSignal;
      if (signal.sourceSessionId === sourceId && signal.timestampSeconds >= motionTimestamp) { motionTimestamp = signal.timestampSeconds; onMotion(signal); }
    } else if (envelope.type === 'command') {
      if (pending >= ProfileLimits.maxPendingRequests) { send('error', { code: 'INVALID_INPUT', message: 'Очередь команд заполнена' }, envelope.requestId); return; }
      pending++;
      const operation = commandChain.then(() => closed ? null : app.handle(envelope.payload as Parameters<SceneApplication['handle']>[0]));
      commandChain = operation.then(() => {}, () => {});
      void operation.then(
        reply => { if (reply) send(reply.type, reply.snapshot ?? {}, envelope.requestId); },
        error => {
          const requestedCode = error instanceof Error ? error.message.split(':')[0] : '';
          const code = ['INVALID_INPUT', 'SNAPSHOT_INVALID', 'RENDERER_UNAVAILABLE', 'STORAGE_FAILURE'].includes(requestedCode) ? requestedCode : 'INVALID_INPUT';
          send('error', { code, message: 'Команда не применена' }, envelope.requestId);
        },
      ).finally(() => { pending--; });
    }
  };
  window.MusicOrbsReceive = receive;
  const dirty = () => { if (!closed) { try { native.postMessage('scene-dirty'); } catch { /* Native save повторится при следующем изменении. */ } } };
  window.addEventListener('music-orbs-dirty', dirty);
  // Событие не передаёт ID: native владеет bridgeSessionId и начинает handshake.
  native.postMessage('graphics-ready');
  return { close() { if (closed) return; closed = true; window.removeEventListener('music-orbs-dirty', dirty); if (window.MusicOrbsReceive === receive) delete window.MusicOrbsReceive; } };
}
