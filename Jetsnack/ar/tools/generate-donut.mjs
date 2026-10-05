// Generates a simple placeholder donut model (donut.glb) for the AR Scene Viewer demo.
// Usage: node Jetsnack/ar/tools/generate-donut.mjs
// Units are meters; the donut sits on y = 0 so it rests on the detected floor/table plane.

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'models', 'donut.glb');

const R = 0.045; // major radius (ring)
const r = 0.022; // minor radius (dough tube)
const ICING_SCALE = 1.06;

// Deterministic PRNG so the model is reproducible.
let seed = 22;
const rand = () => {
  seed = (seed * 1664525 + 1013904223) >>> 0;
  return seed / 4294967296;
};

function torusPoint(u, v, tube) {
  const cu = Math.cos(u), su = Math.sin(u), cv = Math.cos(v), sv = Math.sin(v);
  return {
    p: [(R + tube * cv) * cu, r + tube * sv, (R + tube * cv) * su],
    n: [cv * cu, sv, cv * su],
  };
}

// Grid surface over the torus: vRange(u) returns [vStart, vEnd] for each ring column.
function torusGrid(uSeg, vSeg, tube, vRange) {
  const positions = [], normals = [], indices = [];
  const rows = vSeg + 1;
  for (let i = 0; i <= uSeg; i++) {
    const u = (i / uSeg) * Math.PI * 2;
    const [v0, v1] = vRange(u);
    for (let j = 0; j <= vSeg; j++) {
      const v = v0 + (j / vSeg) * (v1 - v0);
      const { p, n } = torusPoint(u, v, tube);
      positions.push(...p);
      normals.push(...n);
    }
  }
  for (let i = 0; i < uSeg; i++) {
    for (let j = 0; j < vSeg; j++) {
      const a = i * rows + j, b = (i + 1) * rows + j;
      indices.push(a, a + 1, b, b, a + 1, b + 1);
    }
  }
  return { positions, normals, indices };
}

// Small box (sprinkle) placed tangent to the icing surface.
function addSprinkle(target, u, v) {
  const { p, n } = torusPoint(u, v, r * ICING_SCALE + 0.0008);
  // Tangent basis on the surface.
  const tu = [-Math.sin(u), 0, Math.cos(u)];
  const tv = [n[1] * tu[2] - n[2] * tu[1], n[2] * tu[0] - n[0] * tu[2], n[0] * tu[1] - n[1] * tu[0]];
  const angle = rand() * Math.PI;
  const ca = Math.cos(angle), sa = Math.sin(angle);
  const ax = tu.map((x, k) => x * ca + tv[k] * sa); // sprinkle long axis
  const ay = n;
  const az = [ax[1] * ay[2] - ax[2] * ay[1], ax[2] * ay[0] - ax[0] * ay[2], ax[0] * ay[1] - ax[1] * ay[0]]; // right-handed basis
  const half = [0.0045, 0.0011, 0.0011];
  const axes = [ax, ay, az];
  // 6 faces, 4 verts each, flat normals.
  for (let f = 0; f < 3; f++) {
    for (const sign of [1, -1]) {
      const normal = axes[f].map((x) => x * sign);
      const a1 = axes[(f + 1) % 3], a2 = axes[(f + 2) % 3];
      const h1 = half[(f + 1) % 3], h2 = half[(f + 2) % 3];
      const base = target.positions.length / 3;
      const corners = [[-1, -1], [1, -1], [1, 1], [-1, 1]];
      for (const [s1, s2] of corners) {
        for (let k = 0; k < 3; k++) {
          target.positions.push(p[k] + normal[k] * half[f] + a1[k] * h1 * s1 + a2[k] * h2 * s2);
        }
        target.normals.push(...normal);
      }
      if (sign > 0) target.indices.push(base, base + 1, base + 2, base, base + 2, base + 3);
      else target.indices.push(base, base + 2, base + 1, base, base + 3, base + 2);
    }
  }
}

// --- Geometry --------------------------------------------------------------------------------

const dough = torusGrid(96, 48, r, () => [0, Math.PI * 2]);

// Icing covers the top of the torus with a wavy "drip" edge on both the outer and inner side.
const drip = (u, phase) =>
  0.02 + 0.13 * Math.sin(6 * u + phase) + 0.07 * Math.sin(13 * u + 2 * phase) + 0.05 * Math.sin(23 * u);
const icing = torusGrid(160, 40, r * ICING_SCALE, (u) => [-drip(u, 0.0), Math.PI + drip(u, 1.7)]);

const sprinkleColors = [
  [1.0, 0.85, 0.2, 1], // yellow
  [0.25, 0.55, 1.0, 1], // blue
  [1.0, 1.0, 1.0, 1], // white
  [0.35, 0.85, 0.4, 1], // green
  [1.0, 0.5, 0.15, 1], // orange
];
const sprinkles = sprinkleColors.map(() => ({ positions: [], normals: [], indices: [] }));
for (let i = 0; i < 140; i++) {
  const u = rand() * Math.PI * 2;
  const v = 0.35 + rand() * (Math.PI - 0.7);
  addSprinkle(sprinkles[i % sprinkles.length], u, v);
}

// --- glTF assembly ---------------------------------------------------------------------------

const materials = [
  { name: 'Dough', pbrMetallicRoughness: { baseColorFactor: [0.72, 0.42, 0.16, 1], metallicFactor: 0, roughnessFactor: 0.85 } },
  {
    name: 'Icing',
    doubleSided: true,
    pbrMetallicRoughness: { baseColorFactor: [0.92, 0.22, 0.52, 1], metallicFactor: 0, roughnessFactor: 0.35 },
  },
  ...sprinkleColors.map((c, i) => ({
    name: `Sprinkle${i}`,
    pbrMetallicRoughness: { baseColorFactor: c, metallicFactor: 0, roughnessFactor: 0.5 },
  })),
];

const parts = [dough, icing, ...sprinkles];
const chunks = [];
let byteLength = 0;
const bufferViews = [], accessors = [], primitives = [];

function pushView(typedArray, target) {
  const pad = (4 - (byteLength % 4)) % 4;
  if (pad) {
    chunks.push(Buffer.alloc(pad));
    byteLength += pad;
  }
  const buf = Buffer.from(typedArray.buffer, typedArray.byteOffset, typedArray.byteLength);
  bufferViews.push({ buffer: 0, byteOffset: byteLength, byteLength: buf.length, target });
  chunks.push(buf);
  byteLength += buf.length;
  return bufferViews.length - 1;
}

parts.forEach((part, m) => {
  const pos = new Float32Array(part.positions);
  const nrm = new Float32Array(part.normals);
  const idx = new Uint16Array(part.indices);
  if (pos.length / 3 > 65535) throw new Error('Too many vertices for Uint16 indices');
  const min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (let i = 0; i < pos.length; i += 3) {
    for (let k = 0; k < 3; k++) {
      min[k] = Math.min(min[k], pos[i + k]);
      max[k] = Math.max(max[k], pos[i + k]);
    }
  }
  const posAcc = accessors.push({ bufferView: pushView(pos, 34962), componentType: 5126, count: pos.length / 3, type: 'VEC3', min, max }) - 1;
  const nrmAcc = accessors.push({ bufferView: pushView(nrm, 34962), componentType: 5126, count: nrm.length / 3, type: 'VEC3' }) - 1;
  const idxAcc = accessors.push({ bufferView: pushView(idx, 34963), componentType: 5123, count: idx.length, type: 'SCALAR' }) - 1;
  primitives.push({ attributes: { POSITION: posAcc, NORMAL: nrmAcc }, indices: idxAcc, material: m });
});

const gltf = {
  asset: { version: '2.0', generator: 'Jetsnack generate-donut.mjs' },
  scene: 0,
  scenes: [{ nodes: [0] }],
  nodes: [{ name: 'Donut', mesh: 0 }],
  meshes: [{ name: 'Donut', primitives }],
  materials,
  accessors,
  bufferViews,
  buffers: [{ byteLength }],
};

let json = Buffer.from(JSON.stringify(gltf), 'utf8');
json = Buffer.concat([json, Buffer.alloc((4 - (json.length % 4)) % 4, 0x20)]);
let bin = Buffer.concat(chunks);
bin = Buffer.concat([bin, Buffer.alloc((4 - (bin.length % 4)) % 4)]);

const header = Buffer.alloc(12);
header.writeUInt32LE(0x46546c67, 0); // "glTF"
header.writeUInt32LE(2, 4);
header.writeUInt32LE(12 + 8 + json.length + 8 + bin.length, 8);
const chunkHeader = (len, type) => {
  const b = Buffer.alloc(8);
  b.writeUInt32LE(len, 0);
  b.writeUInt32LE(type, 4);
  return b;
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, Buffer.concat([header, chunkHeader(json.length, 0x4e4f534a), json, chunkHeader(bin.length, 0x004e4942), bin]));
console.log(`Wrote ${OUT} (${(12 + 16 + json.length + bin.length) / 1024 | 0} KB)`);
