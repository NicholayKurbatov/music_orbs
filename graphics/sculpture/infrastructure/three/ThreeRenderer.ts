import * as THREE from 'three';
import type { RenderFrame, RendererPort } from '../../application/ports/RendererPort';
import type { Vec3 } from '../../domain/Models';
import { deformationAt } from '../../domain/LocalDeformationField';

const vertexShader = `
uniform float time; uniform float amplitude; uniform float phase; uniform float energy;
uniform float pointSize; uniform float pixelRatio; uniform float impulse;
attribute vec3 stablePosition;
varying float shade; varying float palettePosition;
void main() {
  vec3 p = position;
  float wave = sin(stablePosition.y*3.5+phase*6.2831853)+0.5*sin(stablePosition.x*4.0-time*0.3);
  p += normalize(stablePosition+vec3(0.001))*wave*(0.035*amplitude+0.008*energy+impulse);
  vec4 mv = modelViewMatrix * vec4(p,1.0);
  gl_Position = projectionMatrix * mv;
  gl_PointSize = clamp(pointSize*pixelRatio*3.8/-mv.z,1.0,18.0);
  shade = clamp(0.52+p.z*0.3,0.20,1.0);
  palettePosition = clamp(0.5+stablePosition.y*0.30+stablePosition.x*0.15+stablePosition.z*0.1,0.0,1.0);
}`;
const fragmentShader = `
uniform sampler2D palette; uniform float style; uniform float opacity;
varying float shade; varying float palettePosition;
void main() {
  vec2 p=gl_PointCoord-0.5;
  float alpha=1.0;
  if (style<1.5) { float r=length(p)*2.0; if(r>1.0) discard; alpha=style>0.5?exp(-r*r*4.0):1.0-smoothstep(0.6,1.0,r); }
  else if(max(abs(p.x),abs(p.y))>0.44) discard;
  vec3 color=texture2D(palette,vec2(palettePosition,0.5)).rgb;
  gl_FragColor=vec4(color*(0.65+shade*0.35),alpha*opacity*(0.5+shade*0.5));
}`;

export class ThreeRenderer implements RendererPort {
  private readonly renderer: THREE.WebGLRenderer;
  private readonly scene = new THREE.Scene();
  private readonly camera = new THREE.PerspectiveCamera(38, 1, 0.1, 30);
  private readonly paletteData = new Uint8Array(256 * 4);
  private readonly paletteTarget = new Float32Array(256 * 3);
  private readonly paletteCurrent = new Float32Array(256 * 3);
  private readonly texture = new THREE.DataTexture(this.paletteData, 256, 1, THREE.RGBAFormat);
  private readonly material: THREE.ShaderMaterial;
  private readonly points: THREE.Points;
  private base: Float32Array | null = null;
  private key = '';
  private paletteKey = '';
  private lastPresentTime = performance.now();
  private impulse = 0;
  private disposed = false;
  private contextLost = false;
  private firstPalette = true;
  private readonly lost = (event: Event) => {
    event.preventDefault(); this.contextLost = true;
    // Three заменяет GL caches при restore. Удаляем их dispose listeners сейчас:
    // GL deletions в потерянном контексте — no-op; CPU arrays/uniforms остаются.
    this.points.geometry.dispose(); this.material.dispose(); this.texture.dispose();
  };
  private readonly restored = () => {
    this.contextLost = false; this.key = '';
    this.texture.needsUpdate = true; this.material.needsUpdate = true;
    this.renderer.setClearColor(0x05050b);
  };
  constructor(private readonly canvas: HTMLCanvasElement) {
    try { this.renderer = new THREE.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: 'high-performance' }); }
    catch { throw new Error('RENDERER_UNAVAILABLE: WebGL2'); }
    this.renderer.setClearColor(0x05050b);
    this.camera.position.set(0, 0, 4.7);
    this.texture.magFilter = this.texture.minFilter = THREE.LinearFilter;
    this.texture.generateMipmaps = false;
    this.material = new THREE.ShaderMaterial({ vertexShader, fragmentShader, transparent: true, depthWrite: false, blending: THREE.NormalBlending,
      uniforms: { time: { value: 0 }, amplitude: { value: 0.3 }, phase: { value: 0 }, energy: { value: 0 }, pointSize: { value: 2 }, pixelRatio: { value: 1 }, impulse: { value: 0 }, palette: { value: this.texture }, style: { value: 0 }, opacity: { value: 0.65 } } });
    this.points = new THREE.Points(new THREE.BufferGeometry(), this.material);
    this.points.frustumCulled = false;
    this.scene.add(this.points);
    canvas.addEventListener('webglcontextlost', this.lost);
    canvas.addEventListener('webglcontextrestored', this.restored);
  }
  present(frame: RenderFrame): void {
    if (this.disposed) throw new Error('RENDERER_UNAVAILABLE: disposed');
    if (this.contextLost) return;
    const now = performance.now();
    const dt = Math.max(0, Math.min(0.1, (now - this.lastPresentTime) / 1000));
    this.lastPresentTime = now;
    const key = `${frame.deformation.shapeId}:${frame.deformation.seed}:${frame.deformation.revision}`;
    if (this.base !== frame.base.positions || this.key !== key) {
      if (this.key && this.key !== key && this.base === frame.base.positions && frame.deformation.controls.length) this.impulse = 0.024;
      this.base = frame.base.positions; this.key = key;
      const deformed = new Float32Array(this.base.length);
      for (let i = 0; i < this.base.length; i += 3) {
        const p: Vec3 = [this.base[i], this.base[i + 1], this.base[i + 2]];
        const d = deformationAt(p, frame.deformation.controls);
        deformed.set([p[0] + d[0], p[1] + d[1], p[2] + d[2]], i);
      }
      const geometry = new THREE.BufferGeometry();
      geometry.setAttribute('position', new THREE.BufferAttribute(deformed, 3));
      geometry.setAttribute('stablePosition', new THREE.BufferAttribute(this.base, 3));
      this.points.geometry.dispose(); this.points.geometry = geometry;
    }
    this.impulse *= Math.exp(-dt * 7);
    this.updatePalette(frame, dt);
    const u = this.material.uniforms;
    u.time.value = frame.elapsedSeconds; u.amplitude.value = frame.settings.amplitude;
    u.phase.value = frame.motion.phase; u.energy.value = frame.settings.musicSync ? frame.motion.energy : 0;
    u.impulse.value = this.impulse;
    u.style.value = frame.settings.materialId === 'soft' ? 1 : frame.settings.materialId === 'pixels' ? 2 : 0;
    u.pointSize.value = frame.settings.materialId === 'pixels' ? 5.5 : frame.settings.materialId === 'soft' ? 10 : 2.8;
    u.opacity.value = frame.settings.shapeId === 'cloud' ? 0.32 : frame.settings.shapeId === 'ribbon' ? 0.72 : 0.84;
    this.points.rotation.set(Math.sin(frame.elapsedSeconds * 0.09) * 0.22, frame.elapsedSeconds * 0.12, Math.sin(frame.elapsedSeconds * 0.05) * 0.1);
    this.renderer.render(this.scene, this.camera);
  }
  resize(width: number, height: number, dpr: number): void {
    if (![width, height, dpr].every(x => Number.isFinite(x) && x > 0) || width > 16384 || height > 16384 || dpr > 8) throw new Error('INVALID_INPUT: viewport');
    this.renderer.setPixelRatio(Math.min(dpr, 2)); this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.position.z = 4.7 / Math.min(1, this.camera.aspect);
    this.camera.far = this.camera.position.z + 20;
    this.camera.updateProjectionMatrix();
    this.material.uniforms.pixelRatio.value = Math.min(dpr, 2);
  }
  /** Picking преобразуется inverse matrix в object space; DOM остаётся в адаптере. */
  pick(clientX: number, clientY: number, anchor?: Vec3): Vec3 | null {
    const rect = this.canvas.getBoundingClientRect();
    const ray = new THREE.Raycaster();
    ray.params.Points = { threshold: 0.10 };
    ray.setFromCamera(new THREE.Vector2((clientX - rect.left) / rect.width * 2 - 1, -(clientY - rect.top) / rect.height * 2 + 1), this.camera);
    this.points.updateMatrixWorld();
    if (!anchor) {
      const hit = ray.intersectObject(this.points)[0];
      if (!hit) return null;
      return this.points.worldToLocal(hit.point.clone()).toArray() as unknown as Vec3;
    }
    const world = this.points.localToWorld(new THREE.Vector3(...anchor));
    const plane = new THREE.Plane().setFromNormalAndCoplanarPoint(this.camera.getWorldDirection(new THREE.Vector3()), world);
    const hit = ray.ray.intersectPlane(plane, new THREE.Vector3());
    return hit ? this.points.worldToLocal(hit).toArray() as unknown as Vec3 : null;
  }
  dispose(): void {
    if (this.disposed) return; this.disposed = true;
    this.canvas.removeEventListener('webglcontextlost', this.lost); this.canvas.removeEventListener('webglcontextrestored', this.restored);
    this.points.geometry.dispose(); this.material.dispose(); this.texture.dispose(); this.renderer.dispose(); this.renderer.forceContextLoss();
    this.scene.clear();
  }
  private updatePalette(frame: RenderFrame, dt: number): void {
    const key = JSON.stringify(frame.settings.paletteStops);
    if (key !== this.paletteKey) {
      this.paletteKey = key;
      const stops = frame.settings.paletteStops;
      for (let i = 0; i < 256; i++) {
        const t = i / 255;
        let index = 0; while (index < stops.length - 2 && stops[index + 1].offset < t) index++;
        const a = stops[index], b = stops[Math.min(index + 1, stops.length - 1)];
        const alpha = b.offset === a.offset ? 0 : Math.max(0, Math.min(1, (t - a.offset) / (b.offset - a.offset)));
        const colorA = new THREE.Color(a.colorHex), colorB = new THREE.Color(b.colorHex);
        colorA.lerp(colorB, alpha);
        this.paletteTarget.set([colorA.r * 255, colorA.g * 255, colorA.b * 255], i * 3);
      }
    }
    const blend = this.firstPalette ? 1 : 1 - Math.exp(-dt * 4);
    for (let i = 0; i < 256; i++) {
      for (let c = 0; c < 3; c++) {
        this.paletteCurrent[i * 3 + c] += (this.paletteTarget[i * 3 + c] - this.paletteCurrent[i * 3 + c]) * blend;
        this.paletteData[i * 4 + c] = Math.round(this.paletteCurrent[i * 3 + c]);
      }
      this.paletteData[i * 4 + 3] = 255;
    }
    this.firstPalette = false; this.texture.needsUpdate = true;
  }
}
