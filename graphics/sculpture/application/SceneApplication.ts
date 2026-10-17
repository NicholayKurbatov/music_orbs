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
