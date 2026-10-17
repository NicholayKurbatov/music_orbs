import { validateMotionSignal, type MotionSignal } from '../../motion/domain/MotionSignal';
import type { BaseGeometry, DeformationSnapshot, SceneSettings, SceneSnapshot, ShapeId, Stroke, Vec3 } from '../domain/Models';
import { LocalDeformationField } from '../domain/LocalDeformationField';
import type { ShapeFactory } from '../domain/Policies';
import { DEFAULT_SETTINGS, ProfileLimits, validateSceneSnapshot, validateSettings } from '../domain/ProfileLimits';
import type { SceneApplication, SceneCommand, SceneEvent } from './SceneApplication';
import type { RendererPort } from './ports/RendererPort';

const shapes: ShapeId[] = ['orb', 'ribbon', 'cloud', 'pixel-cloud'];
const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value)) as T;
export class SculptureScene implements SceneApplication {
  private settings: SceneSettings = clone(DEFAULT_SETTINGS);
  private fields = new Map<ShapeId, LocalDeformationField>();
  private bases = new Map<string, BaseGeometry>();
  private paused = false;
  private elapsed = 0;
  private pointer: number | null = null;
  private lastMotion: MotionSignal | null = null;
  constructor(private readonly factory: ShapeFactory, private readonly renderer: RendererPort) {
    for (const [i, shapeId] of shapes.entries()) this.fields.set(shapeId, new LocalDeformationField({ shapeId, seed: 157 + i * 97, generatorVersion: ProfileLimits.generatorVersion, revision: 0, controls: [] }));
  }
  async handle(command: SceneCommand): Promise<SceneEvent> {
    switch (command.type) {
      case 'configure': {
        validateSettings(command.settings);
        this.finishGesture();
        this.settings = clone(command.settings);
        break;
      }
      case 'reset': this.fields.get(this.settings.shapeId)!.reset(); this.pointer = null; break;
      case 'restore': {
        validateSceneSnapshot(command.snapshot);
        const fields = new Map<ShapeId, LocalDeformationField>();
        for (const snapshot of command.snapshot.shapes) fields.set(snapshot.shapeId, new LocalDeformationField(snapshot));
        // Отсутствовавшие неактивные формы получают чистую базу с фиксированным seed.
        for (const [i, shapeId] of shapes.entries()) if (!fields.has(shapeId)) fields.set(shapeId, new LocalDeformationField({ shapeId, seed: 157 + i * 97, generatorVersion: 1, revision: 0, controls: [] }));
        this.fields = fields;
        this.settings = clone(command.snapshot.settings);
        this.bases.clear(); this.pointer = null;
        break;
      }
      case 'snapshot': return { type: 'snapshot', snapshot: this.snapshot() };
      case 'pause': this.finishGesture(); this.paused = true; break;
      case 'resume': this.paused = false; break;
      default: throw new Error('INVALID_INPUT: scene command');
    }
    return { type: 'ack' };
  }
  gesture(stroke: Stroke): void {
    if (this.paused || (this.pointer !== null && stroke.pointerId !== this.pointer)) return;
    if (stroke.phase !== 'begin' && this.pointer === null) return;
    const scaled = stroke.delta.map(x => x * this.settings.touchSensitivity);
    const length = Math.hypot(...scaled);
    const scale = length > ProfileLimits.maxStrokeDelta ? ProfileLimits.maxStrokeDelta / length : 1;
    this.fields.get(this.settings.shapeId)!.apply({ ...stroke, delta: scaled.map(x => x * scale) as unknown as Vec3 });
    this.pointer = stroke.phase === 'end' || stroke.phase === 'cancel' ? null : stroke.pointerId;
  }
  frame(deltaSeconds: number, signal: MotionSignal): void {
    if (!Number.isFinite(deltaSeconds) || deltaSeconds < 0) throw new Error('INVALID_INPUT: frame delta');
    validateMotionSignal(signal);
    if (this.paused) return;
    // Авторитетная смена sourceSessionId выполняется bridge; старые значения времени одной сессии игнорируются.
    if (this.lastMotion?.sourceSessionId === signal.sourceSessionId && this.lastMotion.timestampSeconds > signal.timestampSeconds) signal = this.lastMotion;
    this.lastMotion = signal;
    this.elapsed += Math.min(deltaSeconds, ProfileLimits.maxDeltaSeconds) * this.settings.speed;
    const deformation = this.fields.get(this.settings.shapeId)!.snapshot();
    const key = `${deformation.shapeId}:${deformation.seed}:${this.settings.quality}`;
    let base = this.bases.get(key);
    if (!base) { base = this.factory.generate(deformation.shapeId, deformation.seed, ProfileLimits.particleCounts[this.settings.quality]); this.bases.set(key, base); }
    this.renderer.present({ base, deformation, settings: this.settings, motion: signal, elapsedSeconds: this.elapsed });
  }
  private snapshot(): SceneSnapshot { return { settings: clone(this.settings), shapes: [...this.fields.values()].map(f => f.snapshot()) }; }
  private finishGesture(): void {
    if (this.pointer === null) return;
    this.fields.get(this.settings.shapeId)!.apply({ pointerId: this.pointer, phase: 'cancel', center: [0, 0, 0], delta: [0, 0, 0], radius: 0.4 });
    this.pointer = null;
  }
}
