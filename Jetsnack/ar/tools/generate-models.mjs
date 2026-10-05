// Generates Jetsnack's placeholder 3D snack models into app/src/main/assets/models.
// Usage: node Jetsnack/ar/tools/generate-models.mjs
//
// Every model is built at real-world scale (meters) and rests on y = 0. Node and material names
// are part of the app contract: the donut's "Icing", "Sprinkles" and "Drizzle" nodes are
// recolored / toggled at runtime by the "Build your donut" customizer.

import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  Mesh, add, clamp, cross, lathe, lerp, mul, norm, parametric, rng, roundedBox, smoothstep, sphere, sub, tube,
  writeGlb,
} from './glb.mjs';

const OUT_DIR = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'app', 'src', 'main', 'assets', 'models');
const TAU = Math.PI * 2;

/** Copies [mesh] into the frame (origin, ax, ay, az): local x/y/z map to ax/ay/az. */
function placeInFrame(mesh, origin, ax, ay, az) {
  const out = new Mesh();
  for (let i = 0; i < mesh.positions.length; i += 3) {
    const [x, y, z] = mesh.positions.slice(i, i + 3);
    const [nx, ny, nz] = mesh.normals.slice(i, i + 3);
    out.positions.push(...add(origin, add(mul(ax, x), add(mul(ay, y), mul(az, z)))));
    out.normals.push(...norm(add(mul(ax, nx), add(mul(ay, ny), mul(az, nz)))));
  }
  out.indices = [...mesh.indices];
  return out;
}

function report(name, bytes) {
  console.log(`${name.padEnd(28)} ${(bytes / 1024).toFixed(0).padStart(5)} KB`);
}

// --- Donut -----------------------------------------------------------------------------------

function donut() {
  const R = 0.034; // ring radius
  const r = 0.016; // dough tube radius
  const ICING = r * 1.07;
  const torus = (u, v, tubeRadius) => {
    const t = u * TAU;
    return [(R + tubeRadius * Math.cos(v)) * Math.cos(t), r + tubeRadius * Math.sin(v), (R + tubeRadius * Math.cos(v)) * Math.sin(t)];
  };
  const torusNormal = (u, v) => [Math.cos(v) * Math.cos(u * TAU), Math.sin(v), Math.cos(v) * Math.sin(u * TAU)];

  // Golden dough with the classic pale ring around the side where the donut floated in the fryer.
  const random = rng(7);
  const dough = parametric(96, 40, (u, v) => torus(u, v * TAU, r)).paint((p) => {
    const radial = Math.hypot(p[0], p[2]);
    const sinV = (p[1] - r) / r;
    const cosV = (radial - R) / r;
    const band = Math.exp(-((sinV / 0.3) ** 2)) * smoothstep(-0.3, 0.5, cosV);
    const speckle = 0.93 + 0.07 * random();
    const golden = [0.62, 0.32, 0.1], pale = [0.96, 0.8, 0.52];
    return golden.map((g, k) => lerp(g, pale[k], band) * speckle);
  });

  // Icing over the top with an irregular drip edge on the outer and inner side.
  const drip = (u, phase) =>
    0.06 + 0.13 * Math.sin(6 * u * TAU + phase) + 0.07 * Math.sin(13 * u * TAU + 2 * phase) + 0.05 * Math.sin(23 * u * TAU);
  const icing = parametric(144, 30, (u, v) => {
    const a0 = -drip(u, 0), a1 = Math.PI + drip(u, 1.7);
    return torus(u, lerp(a0, a1, v), ICING);
  });

  // Rounded sprinkles laid tangent to the icing.
  const sprinkleColors = [
    ['SprinkleYellow', [1.0, 0.78, 0.1]],
    ['SprinkleBlue', [0.15, 0.4, 1.0]],
    ['SprinkleWhite', [1.0, 1.0, 1.0]],
    ['SprinkleGreen', [0.2, 0.75, 0.3]],
    ['SprinkleOrange', [1.0, 0.4, 0.08]],
  ];
  const sprinkleShape = roundedBox([0.0072, 0.0017, 0.0017], 0.00085, 2);
  const sprinkles = sprinkleColors.map(() => new Mesh());
  const rand = rng(22);
  for (let i = 0; i < 150; i++) {
    const u = rand();
    const v = 0.35 + rand() * (Math.PI - 0.7);
    const n = torusNormal(u, v);
    const p = torus(u, v, ICING + 0.0007);
    const tu = [-Math.sin(u * TAU), 0, Math.cos(u * TAU)];
    const tv = cross(n, tu);
    const a = rand() * Math.PI;
    const ax = norm(add(mul(tu, Math.cos(a)), mul(tv, Math.sin(a))));
    const az = cross(ax, n);
    sprinkles[i % sprinkles.length].append(placeInFrame(sprinkleShape, p, ax, n, az));
  }

  // Zig-zag chocolate drizzle (hidden by default, toggled in the app).
  const drizzlePath = (t) => {
    const u = t;
    const v = Math.PI / 2 + 1.05 * Math.sin(u * TAU * 8);
    return torus(u, v, ICING + 0.0011);
  };
  const drizzleUp = (t) => torusNormal(t, Math.PI / 2 + 1.05 * Math.sin(t * TAU * 8));
  const drizzle = tube(6, 420, drizzlePath, () => 0.0015, drizzleUp, { capEnds: false });

  const materials = [
    { name: 'Dough', color: [1, 1, 1], roughness: 0.85 },
    { name: 'Icing', color: [0.9, 0.24, 0.5], roughness: 0.3, doubleSided: true },
    { name: 'Drizzle', color: [0.12, 0.05, 0.02], roughness: 0.25 },
    ...sprinkleColors.map(([name, color]) => ({ name, color, roughness: 0.45 })),
  ];
  return writeGlb(join(OUT_DIR, 'donut.glb'), 'Donut', materials, [
    { name: 'Dough', mesh: [{ mesh: dough, material: 'Dough' }] },
    { name: 'Icing', mesh: [{ mesh: icing, material: 'Icing' }] },
    { name: 'Sprinkles', mesh: sprinkles.map((mesh, i) => ({ mesh, material: sprinkleColors[i][0] })) },
    { name: 'Drizzle', mesh: [{ mesh: drizzle, material: 'Drizzle' }] },
  ]);
}

// --- Cupcake ---------------------------------------------------------------------------------

function cupcake() {
  const wrapperTop = 0.034;
  // Pleated paper cup.
  const wrapper = lathe(
    168, 30,
    (v) => {
      if (v < 0.15) return [lerp(0, 0.024, v / 0.15), 0];
      const t = (v - 0.15) / 0.85;
      return [lerp(0.024, 0.031, t), wrapperTop * t];
    },
    (theta, v) => 1 + 0.035 * Math.cos(28 * theta) * smoothstep(0.1, 0.16, v),
  );
  // Cake dome peeking out of the cup.
  const cake = lathe(64, 24, (v) => {
    const a = v * Math.PI / 2;
    return [0.0335 * Math.cos(a) + 0.001, wrapperTop - 0.003 + 0.013 * Math.sin(a)];
  });
  // Piped frosting swirl.
  const turns = 2.6;
  const swirlPath = (t) => {
    const theta = t * turns * TAU;
    const ring = 0.022 * (1 - 0.95 * t);
    return [ring * Math.cos(theta), 0.044 + 0.03 * t, ring * Math.sin(theta)];
  };
  const frosting = tube(18, 200, swirlPath, (t, phi) => 0.0105 * (1 - 0.55 * t) * (1 + 0.07 * Math.cos(7 * phi)));
  const cherry = sphere(0.0068, 10).transform({ translate: [0, 0.0805, 0] });
  const stem = tube(6, 20, (t) => [0.004 * t, 0.086 + 0.012 * t, -0.003 * t * t], () => 0.0007);
  const materials = [
    { name: 'Wrapper', color: [0.35, 0.6, 0.95], roughness: 0.7, doubleSided: true },
    { name: 'Cake', color: [0.3, 0.14, 0.06], roughness: 0.85 },
    { name: 'Frosting', color: [0.97, 0.62, 0.76], roughness: 0.45 },
    { name: 'Cherry', color: [0.7, 0.02, 0.04], roughness: 0.15 },
    { name: 'Stem', color: [0.3, 0.45, 0.12], roughness: 0.6 },
  ];
  return writeGlb(join(OUT_DIR, 'cupcake.glb'), 'Cupcake', materials, [
    { name: 'Wrapper', mesh: [{ mesh: wrapper, material: 'Wrapper' }] },
    { name: 'Cake', mesh: [{ mesh: cake, material: 'Cake' }] },
    { name: 'Frosting', mesh: [{ mesh: frosting, material: 'Frosting' }] },
    { name: 'Cherry', mesh: [{ mesh: cherry, material: 'Cherry' }, { mesh: stem, material: 'Stem' }] },
  ]);
}

// --- Gingerbread man -------------------------------------------------------------------------

function gingerbread() {
  const H = 0.009; // cookie thickness
  const RIM = H / 2;
  // 2D silhouette (X right, Y towards the head) as a union of simple shapes.
  const capsule = (a, b, radius) => (p) => {
    const ab = [b[0] - a[0], b[1] - a[1]];
    const t = clamp(((p[0] - a[0]) * ab[0] + (p[1] - a[1]) * ab[1]) / (ab[0] ** 2 + ab[1] ** 2), 0, 1);
    return Math.hypot(p[0] - a[0] - ab[0] * t, p[1] - a[1] - ab[1] * t) <= radius;
  };
  const shapes = [
    (p) => (p[0] / 0.026) ** 2 + ((p[1] + 0.004) / 0.032) ** 2 <= 1, // body
    (p) => Math.hypot(p[0], p[1] - 0.043) <= 0.02, // head
    capsule([0, 0.012], [0.05, 0.022], 0.0115), // arms
    capsule([0, 0.012], [-0.05, 0.022], 0.0115),
    capsule([0, -0.015], [0.028, -0.062], 0.0128), // legs
    capsule([0, -0.015], [-0.028, -0.062], 0.0128),
  ];
  const inside = (p) => shapes.some((s) => s(p));
  const SAMPLES = 360;
  let radii = [];
  for (let i = 0; i < SAMPLES; i++) {
    const theta = (i / SAMPLES) * TAU;
    const dir = [Math.cos(theta), -Math.sin(theta)]; // 3D (x, z) = (X, -Y)
    let last = 0;
    for (let t = 0; t < 0.12; t += 0.0002) if (inside([dir[0] * t, dir[1] * t])) last = t;
    radii.push(last);
  }
  for (let pass = 0; pass < 3; pass++) {
    radii = radii.map((_, i) => (radii[(i + SAMPLES - 1) % SAMPLES] + 2 * radii[i] + radii[(i + 1) % SAMPLES]) / 4);
  }
  const rho = (theta) => {
    const f = ((theta / TAU) % 1) * SAMPLES;
    const i = Math.floor(f);
    return lerp(radii[i % SAMPLES], radii[(i + 1) % SAMPLES], f - i);
  };
  const cookie = parametric(240, 28, (u, v) => {
    const theta = u * TAU;
    const edge = rho(theta);
    let radial, y;
    if (v < 0.3) {
      radial = (edge - RIM) * (v / 0.3);
      y = 0;
    } else if (v < 0.7) {
      const a = -Math.PI / 2 + ((v - 0.3) / 0.4) * Math.PI;
      radial = edge - RIM + RIM * Math.cos(a);
      y = RIM + RIM * Math.sin(a);
    } else {
      radial = (edge - RIM) * (1 - (v - 0.7) / 0.3);
      y = H + 0.0012 * (1 - (radial / Math.max(edge, 1e-6)) ** 2);
    }
    return [radial * Math.cos(theta), y, radial * Math.sin(theta)];
  });
  const top = H + 0.0012;
  const at = (X, Y, lift = 0) => [X, top + lift, -Y];

  // Royal icing details.
  const icing = new Mesh();
  const dot = (X, Y, radius) => sphere(radius, 8).transform({ scale: [1, 0.45, 1], translate: at(X, Y) });
  icing.append(dot(-0.0068, 0.047, 0.0027)).append(dot(0.0068, 0.047, 0.0027));
  icing.append(tube(6, 40, (t) => {
    const a = lerp(-2.6, -0.54, t);
    return at(0.0095 * Math.cos(a), 0.0425 + 0.0095 * Math.sin(a), 0.0004);
  }, () => 0.0011));
  const squiggle = (a, b, width) => {
    const axis = norm([b[0] - a[0], 0, -(b[1] - a[1])]);
    const across = [-axis[2], 0, axis[0]];
    const c = at(lerp(a[0], b[0], 0.8), lerp(a[1], b[1], 0.8), 0.0004);
    return tube(6, 80, (t) => add(c, add(mul(across, (t - 0.5) * width), mul(axis, 0.0028 * Math.sin(t * TAU * 3)))), () => 0.001);
  };
  icing.append(squiggle([0, 0.012], [0.05, 0.022], 0.02)).append(squiggle([0, 0.012], [-0.05, 0.022], 0.02));
  icing.append(squiggle([0, -0.015], [0.028, -0.062], 0.022)).append(squiggle([0, -0.015], [-0.028, -0.062], 0.022));
  const buttons = new Mesh();
  for (const Y of [0.012, -0.001, -0.014]) buttons.append(dot(0, Y, 0.0037));

  const materials = [
    { name: 'Cookie', color: [0.56, 0.28, 0.1], roughness: 0.85 },
    { name: 'RoyalIcing', color: [0.98, 0.97, 0.95], roughness: 0.4 },
    { name: 'Candy', color: [0.85, 0.05, 0.08], roughness: 0.2 },
  ];
  return writeGlb(join(OUT_DIR, 'gingerbread.glb'), 'Gingerbread', materials, [
    { name: 'Cookie', mesh: [{ mesh: cookie, material: 'Cookie' }] },
    { name: 'Icing', mesh: [{ mesh: icing, material: 'RoyalIcing' }] },
    { name: 'Buttons', mesh: [{ mesh: buttons, material: 'Candy' }] },
  ]);
}

// --- Ice cream sandwich ----------------------------------------------------------------------

function iceCreamSandwich() {
  const W = 0.09, D = 0.05, COOKIE = 0.008, CREAM = 0.016;
  const bottom = roundedBox([W, COOKIE, D], 0.003).transform({ translate: [0, COOKIE / 2, 0] });
  const cream = roundedBox([W - 0.003, CREAM, D - 0.003], 0.006).transform({ translate: [0, COOKIE + CREAM / 2, 0] });
  const topCookie = roundedBox([W, COOKIE, D], 0.003).transform({ translate: [0, COOKIE * 1.5 + CREAM, 0] });
  const holes = new Mesh();
  for (let i = 0; i < 6; i++) {
    for (const z of [-0.011, 0.011]) {
      holes.append(sphere(0.0019, 6).transform({ scale: [1, 0.35, 1], translate: [-0.0325 + i * 0.013, COOKIE * 2 + CREAM, z] }));
    }
  }
  const materials = [
    { name: 'ChocolateCookie', color: [0.07, 0.035, 0.02], roughness: 0.75 },
    { name: 'VanillaCream', color: [0.96, 0.93, 0.86], roughness: 0.65 },
    { name: 'CookieHoles', color: [0.025, 0.012, 0.008], roughness: 0.9 },
  ];
  return writeGlb(join(OUT_DIR, 'ice_cream_sandwich.glb'), 'IceCreamSandwich', materials, [
    { name: 'Cookies', mesh: [{ mesh: bottom.append(topCookie), material: 'ChocolateCookie' }, { mesh: holes, material: 'CookieHoles' }] },
    { name: 'Cream', mesh: [{ mesh: cream, material: 'VanillaCream' }] },
  ]);
}

// --- KitKat ----------------------------------------------------------------------------------

function kitKat() {
  const L = 0.09;
  const chocolate = roundedBox([0.054, 0.004, L], 0.0015).transform({ translate: [0, 0.002, 0] });
  for (let i = 0; i < 4; i++) {
    chocolate.append(roundedBox([0.0118, 0.011, L - 0.002], 0.0026).transform({ translate: [-0.0203 + i * 0.01353, 0.0085, 0] }));
  }
  const wrapper = roundedBox([0.058, 0.0158, 0.046], 0.0022).transform({ translate: [0, 0.0079, 0.023] });
  const stripe = roundedBox([0.0584, 0.0162, 0.007], 0.0023).transform({ translate: [0, 0.0079, 0.036] });
  const materials = [
    { name: 'MilkChocolate', color: [0.25, 0.12, 0.06], roughness: 0.3 },
    { name: 'Wrapper', color: [0.78, 0.02, 0.03], roughness: 0.35 },
    { name: 'WrapperStripe', color: [0.95, 0.95, 0.95], roughness: 0.35 },
  ];
  return writeGlb(join(OUT_DIR, 'kitkat.glb'), 'KitKat', materials, [
    { name: 'Chocolate', mesh: [{ mesh: chocolate, material: 'MilkChocolate' }] },
    { name: 'Wrapper', mesh: [{ mesh: wrapper, material: 'Wrapper' }, { mesh: stripe, material: 'WrapperStripe' }] },
  ]);
}

// --- Eclair ----------------------------------------------------------------------------------

function eclair() {
  const L = 0.12, HH = 0.0135, HW = 0.016;
  const profile = (v) => Math.cbrt(Math.max(0, 1 - Math.abs(2 * v - 1) ** 3));
  const pastry = (scale, phi0, phi1) => (u, v) => {
    const phi = lerp(phi0(v), phi1(v), u);
    const s = profile(v) * scale;
    return [lerp(-L / 2, L / 2, v), HH + HH * s * Math.sin(phi), HW * s * Math.cos(phi)];
  };
  const choux = parametric(36, 72, pastry(1, () => 0, () => TAU));
  const random = rng(3);
  choux.paint((p) => {
    const top = smoothstep(0, 0.03, p[1]);
    const k = 0.9 + 0.1 * random();
    return [lerp(0.7, 0.86, top) * k, lerp(0.42, 0.58, top) * k, lerp(0.16, 0.26, top) * k];
  });
  const a = (v) => 0.12 + 0.07 * Math.sin(v * 21) + 0.04 * Math.sin(v * 47);
  const glaze = parametric(30, 72, pastry(1.07, (v) => -a(v), (v) => Math.PI + a(v + 0.3)));
  const glazeTop = (x) => HH + HH * 1.07 * profile((x + L / 2) / L);
  const drizzle = tube(6, 300, (t) => {
    const x = lerp(-0.046, 0.046, t);
    return [x, glazeTop(x) + 0.0008, 0.0085 * Math.sin(t * TAU * 6)];
  }, () => 0.0009);
  const materials = [
    { name: 'Choux', color: [1, 1, 1], roughness: 0.8 },
    { name: 'ChocolateGlaze', color: [0.13, 0.05, 0.02], roughness: 0.18, doubleSided: true },
    { name: 'WhiteDrizzle', color: [0.97, 0.95, 0.9], roughness: 0.4 },
  ];
  return writeGlb(join(OUT_DIR, 'eclair.glb'), 'Eclair', materials, [
    { name: 'Choux', mesh: [{ mesh: choux, material: 'Choux' }] },
    { name: 'Glaze', mesh: [{ mesh: glaze, material: 'ChocolateGlaze' }] },
    { name: 'Drizzle', mesh: [{ mesh: drizzle, material: 'WhiteDrizzle' }] },
  ]);
}

report('donut.glb', donut());
report('cupcake.glb', cupcake());
report('gingerbread.glb', gingerbread());
report('ice_cream_sandwich.glb', iceCreamSandwich());
report('kitkat.glb', kitKat());
report('eclair.glb', eclair());
