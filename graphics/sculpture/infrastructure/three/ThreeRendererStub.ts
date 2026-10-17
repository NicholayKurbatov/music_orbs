import type { RenderFrame, RendererPort } from '../../application/ports/RendererPort';
/** Future Three.js adapter. No third-party dependency is installed yet. */
export class ThreeRendererStub implements RendererPort {
  present(_frame: RenderFrame): void { throw new Error('NOT_IMPLEMENTED: ThreeRenderer.present'); }
  resize(_width: number, _height: number, _dpr: number): void { throw new Error('NOT_IMPLEMENTED: ThreeRenderer.resize'); }
  dispose(): void { throw new Error('NOT_IMPLEMENTED: ThreeRenderer.dispose'); }
}
