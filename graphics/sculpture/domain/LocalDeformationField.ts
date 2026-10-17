import type { ControlPoint, DeformationSnapshot, Stroke, Vec3 } from './Models';
import type { DeformationField } from './Policies';
import { ProfileLimits, validateShapeSnapshot } from './ProfileLimits';

const copyControl = (c: ControlPoint): ControlPoint => ({ center: [...c.center] as Vec3, delta: [...c.delta] as Vec3, radius: c.radius });

/** Ограниченное постоянное поле; координаты и смещения не зависят от частиц. */
export class LocalDeformationField implements DeformationField {
  private value: DeformationSnapshot;
  private pointer: number | null = null;
  constructor(snapshot: DeformationSnapshot) {
    validateShapeSnapshot(snapshot);
    this.value = { ...snapshot, controls: snapshot.controls.map(copyControl) };
  }
  apply(stroke: Stroke): void {
    const { pointerId, phase, center, delta, radius } = stroke;
    if (!Number.isSafeInteger(pointerId) || pointerId < 0 || !['begin', 'move', 'end', 'cancel'].includes(phase)
      || center.length !== 3 || delta.length !== 3 || !center.every(x => Number.isFinite(x) && Math.abs(x) <= ProfileLimits.maxCoordinate)
      || !delta.every(Number.isFinite) || Math.hypot(...delta) > ProfileLimits.maxStrokeDelta + 1e-9
      || !Number.isFinite(radius) || radius < ProfileLimits.minRadius || radius > ProfileLimits.maxRadius) throw new Error('INVALID_INPUT: stroke');
    if (this.pointer !== null && pointerId !== this.pointer) return;
    if (phase !== 'begin' && this.pointer === null) throw new Error('INVALID_INPUT: begin required');
    if (phase === 'cancel' || phase === 'end') { this.pointer = null; return; }
    const controls = this.value.controls.map(copyControl);
    if (Math.hypot(...delta) > 0) {
      // Близкие мазки объединяются только с одинаковой опорой: функция поля не меняется.
      const same = controls.findIndex(c => c.radius === radius && c.center.every((v, i) => v === center[i]));
      if (same >= 0) {
        const old = controls[same];
        const combined = old.delta.map((v, i) => v + delta[i]) as unknown as Vec3;
        if (Math.hypot(...combined) > ProfileLimits.maxControlDelta) throw new Error('INVALID_INPUT: deformation limit');
        controls[same] = { ...old, delta: combined };
      } else {
        if (controls.length >= ProfileLimits.maxControls) throw new Error('INVALID_INPUT: control limit; reset the active shape');
        controls.push({ center: [...center] as Vec3, delta: [...delta] as Vec3, radius });
      }
      if (this.value.revision >= Number.MAX_SAFE_INTEGER) throw new Error('INVALID_INPUT: revision limit');
      this.value = { ...this.value, controls, revision: this.value.revision + 1 };
    }
    this.pointer = pointerId;
  }
  reset(): void {
    if (this.value.revision >= Number.MAX_SAFE_INTEGER) throw new Error('INVALID_INPUT: revision limit');
    this.value = { ...this.value, controls: [], revision: this.value.revision + 1 };
    this.pointer = null;
  }
  snapshot(): DeformationSnapshot { return { ...this.value, controls: this.value.controls.map(copyControl) }; }
}

/** Компактный C2 falloff: вне radius вклад строго нулевой. */
export function deformationAt(position: Vec3, controls: readonly ControlPoint[]): Vec3 {
  const delta = [0, 0, 0];
  for (const c of controls) {
    const q = Math.hypot(position[0] - c.center[0], position[1] - c.center[1], position[2] - c.center[2]) / c.radius;
    if (q >= 1) continue;
    const weight = (1 - q) ** 4 * (1 + 4 * q);
    for (let axis = 0; axis < 3; axis++) delta[axis] += c.delta[axis] * weight;
  }
  const length = Math.hypot(...delta);
  const scale = length > ProfileLimits.maxControlDelta ? ProfileLimits.maxControlDelta / length : 1;
  return [delta[0] * scale, delta[1] * scale, delta[2] * scale];
}
