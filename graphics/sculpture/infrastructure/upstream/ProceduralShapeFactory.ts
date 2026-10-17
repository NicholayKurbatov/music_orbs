import type { BaseGeometry, ShapeId } from '../../domain/Models';
import type { ShapeFactory } from '../../domain/Policies';
import { ProfileLimits } from '../../domain/ProfileLimits';

// fibDir adapted from thinking-orbs core.ts, de85557ca220332586d070d8788c0e1d6e877a0d.
// Copyright (c) 2026 Jakub Antalik, MIT; notice in third_party/thinking-orbs-LICENSE.
function fibDir(i: number, n: number): [number, number, number] {
  const golden = Math.PI * (3 - Math.sqrt(5));
  const y = 1 - 2 * (i + 0.5) / n;
  const r = Math.sqrt(1 - y * y);
  return [r * Math.cos(i * golden), y, r * Math.sin(i * golden)];
}
const fract = (x: number) => x - Math.floor(x);

/** Генератор версии 1; material и постоянное поле не участвуют в базе. */
export class ProceduralShapeFactory implements ShapeFactory {
  generate(shapeId: ShapeId, seed: number, count: number): BaseGeometry {
    if (!['orb', 'ribbon', 'cloud', 'pixel-cloud'].includes(shapeId) || !Number.isInteger(seed) || seed < -2147483648 || seed > 2147483647
      || !Number.isInteger(count) || count < 1 || count > ProfileLimits.maxParticles) throw new Error('INVALID_INPUT: geometry');
    const positions = new Float32Array(count * 3);
    const offset = fract(seed * 0.61803398875) * Math.PI * 2;
    for (let i = 0; i < count; i++) {
      const u = (i + 0.5) / count;
      const v = fract(i * 0.61803398875 + seed * 0.000173);
      let x: number, y: number, z: number;
      if (shapeId === 'orb') {
        const d = fibDir(i, count);
        const a = Math.atan2(d[2], d[0]) + offset;
        const radius = 0.96 + 0.065 * Math.sin(a * 3 + d[1] * 2) + 0.04 * Math.cos(d[1] * 6 - a * 2);
        x = Math.cos(a) * Math.hypot(d[0], d[2]) * radius; y = d[1] * radius; z = Math.sin(a) * Math.hypot(d[0], d[2]) * radius;
      } else if (shapeId === 'ribbon') {
        // Три складчатых полупрозрачных слоя, открытая поверхность вместо кольца.
        const layer = i % 3;
        const along = u * Math.PI * 2.4 - Math.PI * 1.2;
        const width = (v - 0.5) * (0.7 + 0.16 * Math.sin(along * 2));
        const twist = along * 0.85 + offset * 0.15;
        x = along * 0.34 + width * Math.sin(twist) * 0.2;
        y = Math.sin(along * 1.2) * 0.35 + width * Math.cos(twist);
        z = Math.cos(along * 1.1) * 0.34 + width * Math.sin(twist) + layer * 0.105 + 0.05 * Math.sin(v * 36 + along * 3);
        const horizontal = x; x = y; y = horizontal;
      } else if (shapeId === 'cloud') {
        const d = fibDir(i, count);
        const r = Math.cbrt(fract(i * 0.754877666 + seed * 0.000311)) * (0.85 + 0.2 * Math.sin(d[1] * 4 + offset));
        x = d[0] * r * 1.15; y = d[1] * r * 0.8; z = d[2] * r;
      } else {
        // Вложенные квадратные рамки с решётками, одинаковая структура на всех quality.
        const layer = i % 12;
        const frame = 0.35 + layer * 0.065;
        const t = fract(Math.floor(i / 12) * 0.61803398875) * 4;
        const side = Math.floor(t); const p = (t - side) * 2 - 1;
        const grid = Math.floor(i / 12) % 5 === 0;
        x = side === 0 ? -frame : side === 1 ? p * frame : side === 2 ? frame : -p * frame;
        y = side === 0 ? p * frame : side === 1 ? frame : side === 2 ? -p * frame : -frame;
        if (grid) { x = (Math.round(v * 16) / 8 - 1) * frame; y = (Math.round(fract(i * 0.41421356) * 16) / 8 - 1) * frame; }
        z = (layer - 5.5) * 0.12;
        const angle = layer * 0.07 + offset * 0.05;
        const rotatedX = x * Math.cos(angle) - y * Math.sin(angle);
        y = x * Math.sin(angle) + y * Math.cos(angle); x = rotatedX;
      }
      positions.set([x, y, z], i * 3);
    }
    return { positions };
  }
}
