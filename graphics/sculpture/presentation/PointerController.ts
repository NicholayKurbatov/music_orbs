import type { SceneApplication } from '../application/SceneApplication';
import type { Vec3 } from '../domain/Models';

interface PickingSurface { pick(x: number, y: number, anchor?: Vec3): Vec3 | null; }
/** Pointer capture гарантирует завершение drag вне view; второй палец игнорируется. */
export class PointerController {
  private active: { id: number; anchor: Vec3; previous: Vec3 } | null = null;
  private readonly down = (event: PointerEvent) => {
    if (this.active || event.button !== 0) return;
    const anchor = this.surface.pick(event.clientX, event.clientY);
    if (!anchor) return;
    event.preventDefault(); this.canvas.setPointerCapture(event.pointerId);
    this.active = { id: event.pointerId, anchor, previous: anchor };
    this.send('begin', [0, 0, 0.025]);
  };
  private readonly move = (event: PointerEvent) => {
    if (!this.active || this.active.id !== event.pointerId) return;
    event.preventDefault();
    const next = this.surface.pick(event.clientX, event.clientY, this.active.anchor);
    if (!next) return;
    const delta = next.map((x, i) => x - this.active!.previous[i]) as unknown as Vec3;
    this.active.previous = next; this.send('move', delta);
  };
  private readonly up = (event: PointerEvent) => this.finish(event, 'end');
  private readonly cancel = (event: PointerEvent) => this.finish(event, 'cancel');
  constructor(private readonly canvas: HTMLCanvasElement, private readonly surface: PickingSurface, private readonly application: SceneApplication, private readonly onChange: () => void, private readonly onError: (error: unknown) => void) {
    canvas.addEventListener('pointerdown', this.down); canvas.addEventListener('pointermove', this.move);
    canvas.addEventListener('pointerup', this.up); canvas.addEventListener('pointercancel', this.cancel); canvas.addEventListener('lostpointercapture', this.cancel);
  }
  dispose(): void {
    if (this.active) this.send('cancel', [0, 0, 0]);
    this.active = null;
    this.canvas.removeEventListener('pointerdown', this.down); this.canvas.removeEventListener('pointermove', this.move);
    this.canvas.removeEventListener('pointerup', this.up); this.canvas.removeEventListener('pointercancel', this.cancel); this.canvas.removeEventListener('lostpointercapture', this.cancel);
  }
  private finish(event: PointerEvent, phase: 'end' | 'cancel'): void {
    if (!this.active || this.active.id !== event.pointerId) return;
    this.send(phase, [0, 0, 0]); this.active = null;
    if (this.canvas.hasPointerCapture(event.pointerId)) this.canvas.releasePointerCapture(event.pointerId);
    this.onChange();
  }
  private send(phase: 'begin' | 'move' | 'end' | 'cancel', delta: Vec3): void {
    if (!this.active) return;
    try { this.application.gesture({ pointerId: this.active.id, phase, center: this.active.anchor, delta, radius: 0.5 }); }
    catch (error) { this.onError(error); }
  }
}
