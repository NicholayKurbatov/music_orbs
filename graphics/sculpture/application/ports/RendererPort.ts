import type { MotionSignal } from '../../../motion/domain/MotionSignal';
import type { BaseGeometry, DeformationSnapshot, SceneSettings } from '../../domain/Models';
export interface RenderFrame {
  readonly base: BaseGeometry;
  readonly deformation: DeformationSnapshot;
  readonly settings: SceneSettings;
  readonly motion: MotionSignal;
  readonly elapsedSeconds: number;
}
export interface RendererPort {
  present(frame: RenderFrame): void;
  resize(width: number, height: number, dpr: number): void;
  dispose(): void;
}
