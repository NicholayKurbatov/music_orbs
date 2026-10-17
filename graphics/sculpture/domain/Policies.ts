import type { BaseGeometry, DeformationSnapshot, ShapeId, Stroke } from './Models';
/** Pure policies. No renderer, browser or platform imports. See docs/spec.md. */
export interface ShapeFactory { generate(shapeId: ShapeId, seed: number, count: number): BaseGeometry; }
export interface DeformationField {
  apply(stroke: Stroke): void;
  reset(): void;
  snapshot(): DeformationSnapshot;
}
