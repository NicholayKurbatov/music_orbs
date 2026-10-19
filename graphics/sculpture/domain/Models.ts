export type ShapeId = 'orb' | 'ribbon' | 'cloud' | 'pixel-cloud';
export type MaterialId = 'dots' | 'pixels' | 'soft';
export type Vec3 = readonly [number, number, number];
export interface ControlPoint { readonly center: Vec3; readonly delta: Vec3; readonly radius: number; }
export interface DeformationSnapshot {
  readonly shapeId: ShapeId;
  readonly seed: number;
  readonly generatorVersion: number;
  readonly revision: number;
  readonly controls: readonly ControlPoint[];
}
export interface BaseGeometry { readonly positions: Float32Array; }
export interface Stroke { readonly pointerId: number; readonly phase: 'begin' | 'move' | 'end' | 'cancel'; readonly center: Vec3; readonly delta: Vec3; readonly radius: number; }
export type PaletteId = 'white' | 'pink' | 'lilac' | 'spectrum' | 'custom';
export interface PaletteStop { readonly offset: number; readonly colorHex: string; }
export interface SceneSettings {
  readonly shapeId: ShapeId;
  readonly materialId: MaterialId;
  readonly colorHex: string;
  readonly paletteId: PaletteId;
  readonly paletteStops: readonly PaletteStop[];
  readonly speed: number;
  readonly amplitude: number;
  readonly touchSensitivity: number;
  readonly musicSync: boolean;
  readonly fullSync?: boolean;
  readonly quality: 'low' | 'medium' | 'high';
  readonly targetFps: 30 | 60;
}
export interface SceneSnapshot { readonly settings: SceneSettings; readonly shapes: readonly DeformationSnapshot[]; }
