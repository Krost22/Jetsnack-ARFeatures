// Tiny geometry + GLB writer used to generate Jetsnack's placeholder snack models.
// Units are meters, +Y is up, and every model rests on y = 0 so it sits on the detected table.

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

// --- Vector helpers --------------------------------------------------------------------------

export const sub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
export const add = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
export const mul = (a, s) => [a[0] * s, a[1] * s, a[2] * s];
export const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
export const len = (a) => Math.hypot(a[0], a[1], a[2]);
export const norm = (a) => {
  const l = len(a);
  return l > 1e-12 ? mul(a, 1 / l) : [0, 1, 0];
};
export const lerp = (a, b, t) => a + (b - a) * t;
export const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x));
export const smoothstep = (e0, e1, x) => {
  const t = clamp((x - e0) / (e1 - e0), 0, 1);
  return t * t * (3 - 2 * t);
};

// Deterministic PRNG so the generated models are reproducible.
export function rng(seed) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

// --- Meshes ----------------------------------------------------------------------------------

export class Mesh {
  constructor() {
    this.positions = [];
    this.normals = [];
    this.colors = null; // optional RGB per vertex (multiplied with the material base color)
    this.indices = [];
  }

  get vertexCount() {
    return this.positions.length / 3;
  }

  append(other) {
    const base = this.vertexCount;
    this.positions.push(...other.positions);
    this.normals.push(...other.normals);
    if (other.colors || this.colors) {
      this.colors ??= new Array(base * 3).fill(1);
      this.colors.push(...(other.colors ?? new Array(other.vertexCount * 3).fill(1)));
    }
    for (const i of other.indices) this.indices.push(i + base);
    return this;
  }

  /** Applies scale -> rotation (X, then Y, then Z, radians) -> translation. */
  transform({ scale = [1, 1, 1], rot = [0, 0, 0], translate = [0, 0, 0] } = {}) {
    const rotate = (v) => {
      let [x, y, z] = v;
      let c = Math.cos(rot[0]), s = Math.sin(rot[0]);
      [y, z] = [y * c - z * s, y * s + z * c];
      c = Math.cos(rot[1]); s = Math.sin(rot[1]);
      [x, z] = [x * c + z * s, -x * s + z * c];
      c = Math.cos(rot[2]); s = Math.sin(rot[2]);
      [x, y] = [x * c - y * s, x * s + y * c];
      return [x, y, z];
    };
    for (let i = 0; i < this.positions.length; i += 3) {
      const p = rotate([this.positions[i] * scale[0], this.positions[i + 1] * scale[1], this.positions[i + 2] * scale[2]]);
      const n = norm(rotate([this.normals[i] / scale[0], this.normals[i + 1] / scale[1], this.normals[i + 2] / scale[2]]));
      for (let k = 0; k < 3; k++) {
        this.positions[i + k] = p[k] + translate[k];
        this.normals[i + k] = n[k];
      }
    }
    return this;
  }

  paint(colorFn) {
    this.colors = [];
    for (let i = 0; i < this.positions.length; i += 3) {
      this.colors.push(...colorFn([this.positions[i], this.positions[i + 1], this.positions[i + 2]], [this.normals[i], this.normals[i + 1], this.normals[i + 2]]));
    }
    return this;
  }
}

/**
 * Parametric surface over u, v in [0, 1]. Normals come from finite differences of [fn] and point
 * along dP/dv x dP/du (the same side the triangles face); pass flip = true to turn the surface inside out.
 */
export function parametric(uSeg, vSeg, fn, { flip = false } = {}) {
  const m = new Mesh();
  const e = 1e-4;
  const rows = vSeg + 1;
  for (let i = 0; i <= uSeg; i++) {
    for (let j = 0; j <= vSeg; j++) {
      const u = i / uSeg, v = j / vSeg;
      const p = fn(u, v);
      let n = [0, 0, 0];
      // Nudge towards the interior at degenerate points (poles) to get a usable normal.
      for (const dv of [0, 0.002, -0.002, 0.01, -0.01]) {
        const vv = clamp(v + dv, 0, 1);
        const pu = sub(fn(Math.min(u + e, 1), vv), fn(Math.max(u - e, 0), vv));
        const pv = sub(fn(u, Math.min(vv + e, 1)), fn(u, Math.max(vv - e, 0)));
        const c = cross(pv, pu);
        if (len(c) > 1e-14) {
          n = norm(c);
          break;
        }
      }
      if (flip) n = mul(n, -1);
      m.positions.push(...p);
      m.normals.push(...n);
    }
  }
  for (let i = 0; i < uSeg; i++) {
    for (let j = 0; j < vSeg; j++) {
      const a = i * rows + j, b = (i + 1) * rows + j;
      if (flip) m.indices.push(a, b, a + 1, b, b + 1, a + 1);
      else m.indices.push(a, a + 1, b, b, a + 1, b + 1);
    }
  }
  return m;
}

/** Surface of revolution around +Y. profile(v) returns [radius, y]; scaleFn(theta) widens it per angle. */
export function lathe(uSeg, vSeg, profile, scaleFn = () => 1) {
  return parametric(uSeg, vSeg, (u, v) => {
    const t = u * Math.PI * 2;
    const [r, y] = profile(v);
    const s = r * scaleFn(t, v);
    return [s * Math.cos(t), y, s * Math.sin(t)];
  });
}

/** Box with rounded edges (a capsule when radius equals the smallest half extent). */
export function roundedBox([sx, sy, sz], radius, seg = 6) {
  const half = [sx / 2, sy / 2, sz / 2];
  const inner = half.map((h) => Math.max(h - radius, 0));
  const m = new Mesh();
  // Each face: axis index, sign and its two tangent axes. Winding is fixed up below.
  const faces = [
    [0, 1, 2, 1], [0, -1, 1, 2], [1, 1, 0, 2], [1, -1, 2, 0], [2, 1, 1, 0], [2, -1, 0, 1],
  ];
  for (const [axis, sign, ua, va] of faces) {
    const face = parametric(seg, seg, (u, v) => {
      const q = [0, 0, 0];
      q[axis] = sign * half[axis];
      q[ua] = (u * 2 - 1) * half[ua];
      q[va] = (v * 2 - 1) * half[va];
      const c = q.map((x, k) => clamp(x, -inner[k], inner[k]));
      const d = norm(sub(q, c));
      return add(c, mul(d, radius));
    });
    m.append(face);
  }
  // Recompute normals analytically: they are exact for a rounded box and avoid seams at face edges.
  for (let i = 0; i < m.positions.length; i += 3) {
    const p = [m.positions[i], m.positions[i + 1], m.positions[i + 2]];
    const c = p.map((x, k) => clamp(x, -inner[k], inner[k]));
    const d = sub(p, c);
    const n = len(d) > 1e-9 ? norm(d) : [0, 0, 0];
    m.normals[i] = n[0];
    m.normals[i + 1] = n[1];
    m.normals[i + 2] = n[2];
  }
  // Make sure every triangle winds outward regardless of the face's tangent ordering.
  for (let t = 0; t < m.indices.length; t += 3) {
    const [a, b, c] = [m.indices[t], m.indices[t + 1], m.indices[t + 2]];
    const pa = m.positions.slice(a * 3, a * 3 + 3);
    const pb = m.positions.slice(b * 3, b * 3 + 3);
    const pc = m.positions.slice(c * 3, c * 3 + 3);
    const fn = cross(sub(pb, pa), sub(pc, pa));
    const na = m.normals.slice(a * 3, a * 3 + 3);
    if (fn[0] * na[0] + fn[1] * na[1] + fn[2] * na[2] < 0) {
      m.indices[t + 1] = c;
      m.indices[t + 2] = b;
    }
  }
  return m;
}

export function sphere(radius, seg = 16) {
  return lathe(seg * 2, seg, (v) => {
    const a = -Math.PI / 2 + v * Math.PI;
    return [Math.cos(a) * radius, Math.sin(a) * radius];
  });
}

/** Tube swept along path(t) -> point, with radius(t, phi). up(t) is a hint for the frame. */
export function tube(uSeg, vSeg, path, radius, up = () => [0, 1, 0], { capEnds = true } = {}) {
  const frame = (t) => {
    const e = 1e-3;
    const tan = norm(sub(path(Math.min(t + e, 1)), path(Math.max(t - e, 0))));
    const side = norm(cross(tan, up(t)));
    const top = cross(side, tan);
    return { side, top };
  };
  // u runs around the tube, v along the path.
  const body = parametric(uSeg, vSeg, (u, v) => {
    const phi = u * Math.PI * 2;
    const { side, top } = frame(v);
    let r = radius(v, phi);
    if (capEnds) r *= Math.sqrt(Math.max(0, 1 - Math.pow(Math.abs(v * 2 - 1), 24)));
    const c = path(v);
    return add(c, add(mul(side, Math.cos(phi) * r), mul(top, Math.sin(phi) * r)));
  });
  return body;
}

// --- GLB writer ------------------------------------------------------------------------------

/**
 * Writes a GLB. nodes: [{ name, mesh: [{ mesh: Mesh, material: name }], translation? }].
 * All mesh nodes become children of a root node named [rootName].
 */
export function writeGlb(file, rootName, materials, nodes) {
  const chunks = [];
  let byteLength = 0;
  const bufferViews = [], accessors = [], meshes = [];
  const materialIndex = new Map(materials.map((m, i) => [m.name, i]));

  const pushView = (typed, target) => {
    const pad = (4 - (byteLength % 4)) % 4;
    if (pad) {
      chunks.push(Buffer.alloc(pad));
      byteLength += pad;
    }
    const buf = Buffer.from(typed.buffer, typed.byteOffset, typed.byteLength);
    bufferViews.push({ buffer: 0, byteOffset: byteLength, byteLength: buf.length, target });
    chunks.push(buf);
    byteLength += buf.length;
    return bufferViews.length - 1;
  };

  const gltfNodes = [{ name: rootName, children: [] }];
  for (const node of nodes) {
    const primitives = node.mesh.map(({ mesh, material }) => {
      if (!materialIndex.has(material)) throw new Error(`Unknown material ${material}`);
      const pos = new Float32Array(mesh.positions);
      const nrm = new Float32Array(mesh.normals);
      const big = mesh.vertexCount > 65535;
      const idx = big ? new Uint32Array(mesh.indices) : new Uint16Array(mesh.indices);
      const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
      for (let i = 0; i < pos.length; i += 3) {
        for (let k = 0; k < 3; k++) {
          min[k] = Math.min(min[k], pos[i + k]);
          max[k] = Math.max(max[k], pos[i + k]);
        }
      }
      const attributes = {
        POSITION: accessors.push({ bufferView: pushView(pos, 34962), componentType: 5126, count: pos.length / 3, type: 'VEC3', min, max }) - 1,
        NORMAL: accessors.push({ bufferView: pushView(nrm, 34962), componentType: 5126, count: nrm.length / 3, type: 'VEC3' }) - 1,
      };
      if (mesh.colors) {
        const col = new Float32Array(mesh.colors);
        attributes.COLOR_0 = accessors.push({ bufferView: pushView(col, 34962), componentType: 5126, count: col.length / 3, type: 'VEC3' }) - 1;
      }
      const indices = accessors.push({ bufferView: pushView(idx, 34963), componentType: big ? 5125 : 5123, count: idx.length, type: 'SCALAR' }) - 1;
      return { attributes, indices, material: materialIndex.get(material) };
    });
    meshes.push({ name: node.name, primitives });
    gltfNodes[0].children.push(gltfNodes.length);
    gltfNodes.push({ name: node.name, mesh: meshes.length - 1, ...(node.translation ? { translation: node.translation } : {}) });
  }

  const gltf = {
    asset: { version: '2.0', generator: 'Jetsnack ar/tools/generate-models.mjs' },
    scene: 0,
    scenes: [{ nodes: [0] }],
    nodes: gltfNodes,
    meshes,
    materials: materials.map(({ name, color, roughness = 0.6, metallic = 0, doubleSided = false }) => ({
      name,
      doubleSided,
      pbrMetallicRoughness: { baseColorFactor: [...color, 1], metallicFactor: metallic, roughnessFactor: roughness },
    })),
    accessors,
    bufferViews,
    buffers: [{ byteLength }],
  };

  let json = Buffer.from(JSON.stringify(gltf), 'utf8');
  json = Buffer.concat([json, Buffer.alloc((4 - (json.length % 4)) % 4, 0x20)]);
  let bin = Buffer.concat(chunks);
  bin = Buffer.concat([bin, Buffer.alloc((4 - (bin.length % 4)) % 4)]);
  const header = Buffer.alloc(12);
  header.writeUInt32LE(0x46546c67, 0);
  header.writeUInt32LE(2, 4);
  header.writeUInt32LE(12 + 8 + json.length + 8 + bin.length, 8);
  const chunkHeader = (length, type) => {
    const b = Buffer.alloc(8);
    b.writeUInt32LE(length, 0);
    b.writeUInt32LE(type, 4);
    return b;
  };
  mkdirSync(dirname(file), { recursive: true });
  const out = Buffer.concat([header, chunkHeader(json.length, 0x4e4f534a), json, chunkHeader(bin.length, 0x004e4942), bin]);
  writeFileSync(file, out);
  return out.length;
}
