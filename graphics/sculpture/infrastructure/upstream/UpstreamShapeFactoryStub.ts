import type { BaseGeometry, ShapeId } from '../../domain/Models';
import type { ShapeFactory } from '../../domain/Policies';
/** Boundary for adapted upstream generators. No upstream code is copied yet. */
export class UpstreamShapeFactoryStub implements ShapeFactory {
  generate(_shapeId: ShapeId, _seed: number, _count: number): BaseGeometry {
    throw new Error('NOT_IMPLEMENTED: UpstreamShapeFactory.generate');
  }
}
