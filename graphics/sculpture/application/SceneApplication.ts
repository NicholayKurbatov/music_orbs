import type { MotionSignal } from '../../motion/domain/MotionSignal';
import type { SceneSettings, SceneSnapshot, Stroke } from '../domain/Models';
export type SceneCommand =
  | { readonly type: 'configure'; readonly settings: SceneSettings }
  | { readonly type: 'reset' }
  | { readonly type: 'restore'; readonly snapshot: SceneSnapshot }
  | { readonly type: 'snapshot' }
  | { readonly type: 'pause' }
  | { readonly type: 'resume' };
export interface SceneEvent { readonly type: 'ack' | 'snapshot'; readonly snapshot?: SceneSnapshot; }
export interface SceneApplication {
  handle(command: SceneCommand): Promise<SceneEvent>;
  gesture(stroke: Stroke): void;
  frame(deltaSeconds: number, signal: MotionSignal): void;
}
/** Intentional placeholder: no business behavior is implemented. */
export class SceneApplicationStub implements SceneApplication {
  handle(_command: SceneCommand): Promise<SceneEvent> { throw new Error('NOT_IMPLEMENTED: SceneApplication.handle'); }
  gesture(_stroke: Stroke): void { throw new Error('NOT_IMPLEMENTED: SceneApplication.gesture'); }
  frame(_deltaSeconds: number, _signal: MotionSignal): void { throw new Error('NOT_IMPLEMENTED: SceneApplication.frame'); }
}
