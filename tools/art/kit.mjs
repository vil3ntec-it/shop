/**
 *  Drawing kit for the flat scenes: geometry helpers, the environment (ground line, stones, bushes, pebbles) and
 *  one parametric character, so every scene shares the same soft, rounded language (batch 65, e-rows-65a).
 *  Units: the scene is 400 × 200, ground line at y = GROUND; a standing character is ~100 tall.
 */
import { fmt as f } from './engine.mjs';

export const W = 400;
export const H = 200;
export const GROUND = 176;

/* ---------------------------------------------------------------- tracks */

/** keyframes: K([t, v, ease?], ...) */
export const K = (...k) => ({ k });
/** add a seamless sine (integer `n` cycles per loop) to a track */
export const Wv = (amp, n, phase = 0, tr = {}) => ({ ...tr, w: [amp, n, phase] });

/* ---------------------------------------------------------------- geometry → path strings */

const KAPPA = 0.5523;

export function ellipse(cx, cy, rx, ry = rx) {
  const kx = rx * KAPPA, ky = ry * KAPPA;
  return `M${f(cx - rx)} ${f(cy)}C${f(cx - rx)} ${f(cy - ky)} ${f(cx - kx)} ${f(cy - ry)} ${f(cx)} ${f(cy - ry)}`
    + `C${f(cx + kx)} ${f(cy - ry)} ${f(cx + rx)} ${f(cy - ky)} ${f(cx + rx)} ${f(cy)}`
    + `C${f(cx + rx)} ${f(cy + ky)} ${f(cx + kx)} ${f(cy + ry)} ${f(cx)} ${f(cy + ry)}`
    + `C${f(cx - kx)} ${f(cy + ry)} ${f(cx - rx)} ${f(cy + ky)} ${f(cx - rx)} ${f(cy)}Z`;
}
export const circle = (cx, cy, r) => ellipse(cx, cy, r, r);

export function rrect(x, y, w, h, r) {
  const [a, b, c, d] = Array.isArray(r) ? r : [r, r, r, r];
  const k = KAPPA;
  return `M${f(x + a)} ${f(y)}L${f(x + w - b)} ${f(y)}C${f(x + w - b + b * k)} ${f(y)} ${f(x + w)} ${f(y + b - b * k)} ${f(x + w)} ${f(y + b)}`
    + `L${f(x + w)} ${f(y + h - c)}C${f(x + w)} ${f(y + h - c + c * k)} ${f(x + w - c + c * k)} ${f(y + h)} ${f(x + w - c)} ${f(y + h)}`
    + `L${f(x + d)} ${f(y + h)}C${f(x + d - d * k)} ${f(y + h)} ${f(x)} ${f(y + h - d + d * k)} ${f(x)} ${f(y + h - d)}`
    + `L${f(x)} ${f(y + a)}C${f(x)} ${f(y + a - a * k)} ${f(x + a - a * k)} ${f(y)} ${f(x + a)} ${f(y)}Z`;
}

/** tapered capsule from A (radius ra) to B (radius rb) — limbs */
export function capsule(ax, ay, bx, by, ra, rb = ra) {
  const dx = bx - ax, dy = by - ay;
  const len = Math.hypot(dx, dy) || 1;
  const ux = dx / len, uy = dy / len;
  const nx = -uy, ny = ux;
  const p = (x, y) => `${f(x)} ${f(y)}`;
  const k = KAPPA;
  // A side +n → B side +n → cap around B → B side −n → A side −n → cap around A
  return `M${p(ax + nx * ra, ay + ny * ra)}L${p(bx + nx * rb, by + ny * rb)}`
    + `C${p(bx + nx * rb + ux * rb * k, by + ny * rb + uy * rb * k)} ${p(bx + ux * rb + nx * rb * k, by + uy * rb + ny * rb * k)} ${p(bx + ux * rb, by + uy * rb)}`
    + `C${p(bx + ux * rb - nx * rb * k, by + uy * rb - ny * rb * k)} ${p(bx - nx * rb + ux * rb * k, by - ny * rb + uy * rb * k)} ${p(bx - nx * rb, by - ny * rb)}`
    + `L${p(ax - nx * ra, ay - ny * ra)}`
    + `C${p(ax - nx * ra - ux * ra * k, ay - ny * ra - uy * ra * k)} ${p(ax - ux * ra - nx * ra * k, ay - uy * ra - ny * ra * k)} ${p(ax - ux * ra, ay - uy * ra)}`
    + `C${p(ax - ux * ra + nx * ra * k, ay - uy * ra + ny * ra * k)} ${p(ax + nx * ra - ux * ra * k, ay + ny * ra - uy * ra * k)} ${p(ax + nx * ra, ay + ny * ra)}Z`;
}

/**
 *  smooth closed (or open) curve through points (Catmull-Rom → cubic). A point `[x, y, 1]` is a sharp corner;
 *  `t` = tension (1 = classic, lower = tighter).
 */
export function smooth(pts, { closed = true, t = 1 } = {}) {
  const n = pts.length;
  const P = (i) => (closed ? pts[(i + n) % n] : pts[Math.max(0, Math.min(n - 1, i))]);
  let d = `M${f(pts[0][0])} ${f(pts[0][1])}`;
  const segs = closed ? n : n - 1;
  for (let i = 0; i < segs; i++) {
    const p0 = P(i - 1), p1 = P(i), p2 = P(i + 1), p3 = P(i + 2);
    const s1 = p1[2] ? 0 : t / 6, s2 = p2[2] ? 0 : t / 6;
    const c1 = [p1[0] + (p2[0] - p0[0]) * s1, p1[1] + (p2[1] - p0[1]) * s1];
    const c2 = [p2[0] - (p3[0] - p1[0]) * s2, p2[1] - (p3[1] - p1[1]) * s2];
    d += `C${f(c1[0])} ${f(c1[1])} ${f(c2[0])} ${f(c2[1])} ${f(p2[0])} ${f(p2[1])}`;
  }
  return closed ? `${d}Z` : d;
}

export const poly = (pts) => `M${pts.map((p) => `${f(p[0])} ${f(p[1])}`).join('L')}Z`;
export const line = (pts) => `M${pts.map((p) => `${f(p[0])} ${f(p[1])}`).join('L')}`;
/** move every coordinate of point list */
export const shift = (pts, dx, dy) => pts.map((p) => [p[0] + dx, p[1] + dy, p[2]]);

/* ---------------------------------------------------------------- nodes */

/** group: g({ name, px, py, r, x, y, sx, sy, a, clip }, ...kids) */
export const g = (o, ...kids) => ({ ...o, kids: kids.flat(Infinity).filter(Boolean) });
/** filled path */
export const fill = (role, d) => ({ role, d });
/** stroked path (round caps) */
export const stroke = (role, w, d) => ({ role, w, d });
/** path morph between variants, driven by track `m` (0 … n−1) */
export const morph = (role, m, ...ds) => ({ role, m, morph: ds });
export const morphStroke = (role, w, m, ...ds) => ({ role, w, m, morph: ds });
/** rope (cable) between two named groups, tangents in scene units */
export const rope = (role, w, a, b) => ({ role, w, rope: [...a, ...b] });

/* ---------------------------------------------------------------- environment */

export const ground = (x0 = 22, x1 = 378, y = GROUND) => stroke('ground', 1.6, line([[x0, y + 0.8], [x1, y + 0.8]]));

/** tall rounded stone (monolith) standing on the ground */
export function monolith(cx, w, h, { role = 'rock', lean = 0 } = {}) {
  const b = GROUND + 0.8;
  return fill(role, smooth([
    [cx - w / 2, b, 1], [cx - w * 0.47, b - h * 0.45], [cx - w * 0.3 + lean, b - h * 0.9], [cx + lean * 1.2, b - h],
    [cx + w * 0.32 + lean, b - h * 0.88], [cx + w * 0.48, b - h * 0.42], [cx + w / 2, b, 1],
  ]));
}

/** low dome stone */
export function dome(cx, w, h, role = 'rock') {
  const b = GROUND + 0.8;
  return fill(role, smooth([[cx - w / 2, b, 1], [cx - w * 0.42, b - h * 0.6], [cx, b - h], [cx + w * 0.42, b - h * 0.62], [cx + w / 2, b, 1]]));
}

/** flat rounded stone lying at y (centre), for cairns */
export function slab(cx, cy, w, h, role = 'rock2', tilt = 0) {
  return fill(role, smooth([
    [cx - w / 2, cy + tilt], [cx - w * 0.38, cy - h / 2 + tilt * 0.6], [cx + w * 0.1, cy - h / 2 - 0.4], [cx + w * 0.46, cy - h * 0.38 - tilt * 0.6],
    [cx + w / 2, cy + h * 0.05 - tilt], [cx + w * 0.4, cy + h / 2 - tilt * 0.5], [cx - w * 0.2, cy + h / 2 + 0.3], [cx - w * 0.46, cy + h * 0.36 + tilt * 0.6],
  ]));
}

/** small pebble half-sunk in the ground line */
export const pebble = (cx, r, role = 'rock2') => fill(role, smooth([[cx - r, GROUND + 0.8, 1], [cx - r * 0.75, GROUND + 0.8 - r * 0.8], [cx + r * 0.1, GROUND + 0.8 - r], [cx + r * 0.85, GROUND + 0.8 - r * 0.7], [cx + r, GROUND + 0.8, 1]]));

/**
 *  leafy bush: a dome with round clumps along its edge (one silhouette, no outlines) and small pointed leaf tips
 *  between them, cut flat on the ground line; a lighter, lower clump on one side gives depth (the reference's two greens).
 */
export function bush(cx, w, h, { lobes = 4, role = 'leaf', front = 'leaf2', seed = 0, side = -1 } = {}) {
  const b = GROUND + 0.8;
  const rnd = (i) => 0.5 + 0.5 * Math.sin((i + 1) * 12.9898 + seed * 78.233);
  const mass = (x, rx, ry, n, col, s0) => {
    const out = [fill(col, ellipse(x, b, rx * 0.92, ry * 0.86))];
    for (let i = 0; i < n; i++) {
      const u = n === 1 ? 0.5 : i / (n - 1);
      const a = Math.PI * (0.94 - 0.88 * u);
      const r = Math.min(rx, ry) * (0.42 + 0.14 * rnd(i + s0));
      const ex = x + Math.cos(a) * (rx - r * 0.55), ey = b - Math.sin(a) * (ry - r * 0.55);
      out.push(fill(col, circle(ex, ey, r)));
      if (i < n - 1) {
        // pointed leaf tip in the notch to the next clump
        const a2 = a - (Math.PI * 0.88) / (n - 1) / 2;
        const tx = x + Math.cos(a2) * ry * 0.0 + Math.cos(a2) * (rx - r * 0.2), ty = b - Math.sin(a2) * (ry + r * 0.05);
        const len = r * 0.9, ang = -a2 + Math.PI / 2;
        const pts = [[0, -len, 1], [len * 0.3, -len * 0.25], [0, len * 0.35, 1], [-len * 0.3, -len * 0.25]]
          .map(([px, py, c]) => [tx + px * Math.cos(ang) - py * Math.sin(ang), ty + px * Math.sin(ang) + py * Math.cos(ang), c]);
        out.push(fill(col, smooth(pts, { t: 0.8 })));
      }
    }
    return out;
  };
  const clip = rrect(cx - w, b - h * 2.2, w * 2, h * 2.2, 0);
  const fx = cx + side * w * 0.24;
  return g({ clip },
    ...mass(cx, w / 2, h, lobes, role, seed),
    ...(front ? mass(fx, w * 0.3, h * 0.58, Math.max(2, lobes - 1), front, seed + 5) : []),
  );
}

/** single leaf (for falling leaves): pointed ellipse at origin pointing up */
export const leafShape = (len = 6, wid = 2.6) => smooth([[0, -len / 2, 1], [wid / 2, 0], [0, len / 2, 1], [-wid / 2, 0]]);

/* ---------------------------------------------------------------- character */

/**
 *  One parametric person (faces −x by default; `facing: 'right'` mirrors). Joints (names prefixed `${name}.`):
 *  body (breath), legB/legF → shinB/shinF, torso (lean, pivot hip) → armB/foreB, head (→ face: eyes, browL/browR,
 *  mouth, look), armF/foreF; hands are groups `${name}.handF` / `.handB` so props and ropes attach to them.
 *  `anim` maps those names (without prefix) to track sets; `hold` adds props to the hands.
 */
export function person(o) {
  const n = o.name;
  const A = (k) => (o.anim && o.anim[k]) || {};
  const id = (k) => `${n}.${k}`;
  const skin = o.skin || 'skin', shade = o.shade || 'skin2';
  const top = o.shirt || 'teal', top2 = o.shirt2 || `${top}2`;
  const pants = o.pants || 'navy', shoes = o.shoes || 'shoe';
  const sit = !!o.sit;

  // ---- rest geometry (local: feet on y = 0, facing −x)
  const hipY = sit ? -17.5 : -31;
  const shY = hipY - 32;
  const neckY = shY - 2;
  const headY = neckY - 14; // head centre
  const hipF = [4.2, hipY], hipB = [-4.4, hipY];
  const shF = [7.5, shY + 3], shB = [-7.5, shY + 3];

  // ---- legs (thigh + shin + foot); sitting ⇒ thighs forward (−x), shins down
  const leg = (key, hip, col, colShoe) => {
    const thighLen = 15.5, shinLen = 13.5;
    const shin = g({ name: id(`shin${key}`), px: 0, py: thighLen, ...(sit ? { r: { k: [[0, -84, 's']] } } : {}), ...A(`shin${key}`) },
      fill(col, capsule(0, thighLen, 0, thighLen + shinLen, 4.1, 3.5)),
      fill(colShoe, smooth([[-6.8, thighLen + shinLen + 3.2], [-6.4, thighLen + shinLen - 0.6], [-1.5, thighLen + shinLen - 2.4], [3.4, thighLen + shinLen - 1.2], [3.6, thighLen + shinLen + 3.2, 1]])),
    );
    const seatedLeg = sit ? { r: { k: [[0, 84, 's']] } } : {};
    return g({ name: id(`leg${key}`), px: hip[0], py: hip[1], ...seatedLeg, ...A(`leg${key}`) },
      g({ x: { k: [[0, hip[0], 's']] }, y: { k: [[0, hip[1], 's']] } },
        fill(col, capsule(0, 0, 0, thighLen, 4.6, 4.2)),
        shin,
      ),
    );
  };

  // ---- arms (upper + fore + hand); hand group for props
  const arm = (key, sh, sleeve, skinCol, sleeveLen) => {
    const up = 16, fore = 15;
    //  props sit behind the fist (the hand grips them); `hand${key}Top` for things drawn over the fingers
    const hand = g({ name: id(`hand${key}`), px: 0, py: up + fore, ...A(`hand${key}`) },
      ...(o.hold && o.hold[`hand${key}`] ? [o.hold[`hand${key}`]] : []),
      fill(skinCol, ellipse(0, up + fore + 1.1, 2.9, 3.15)),
      ...(o.hold && o.hold[`hand${key}Top`] ? [o.hold[`hand${key}Top`]] : []),
    );
    const foreArm = g({ name: id(`fore${key}`), px: 0, py: up, ...A(`fore${key}`) },
      fill(sleeveLen > up ? sleeve : skinCol, capsule(0, up, 0, up + fore, 2.45, 2.0)),
      sleeveLen > up ? fill(skinCol, capsule(0, up + (sleeveLen - up), 0, up + fore, 2.1, 2.0)) : null,
      o.band && key === 'F' ? fill(o.band, rrect(-2.6, up + fore - 4.2, 5.2, 1.5, 0.7)) : null,
      hand,
    );
    return g({ name: id(`arm${key}`), px: sh[0], py: sh[1], ...A(`arm${key}`) },
      g({ x: { k: [[0, sh[0], 's']] }, y: { k: [[0, sh[1], 's']] } },
        fill(skinCol, capsule(0, 0, 0, up, 2.85, 2.5)),
        fill(sleeve, capsule(0, -0.5, 0, Math.min(up, sleeveLen), 3.4, 3.1)),
        foreArm,
      ),
    );
  };

  // ---- torso (shirt + optional apron / vest / pattern)
  const torsoPts = [
    [-9.6, hipY + 1.5, 1], [-10.2, hipY - 9], [-11.4, shY + 8], [-10, shY + 1.6], [-4.4, shY - 1.2], [4.4, shY - 1.2],
    [10, shY + 1.6], [11.4, shY + 8], [10.2, hipY - 9], [9.4, hipY + 1.5, 1],
  ];
  const torsoD = smooth(torsoPts, { t: 0.9 });
  const pattern = [];
  if (o.pattern === 'stripes') for (let y = shY + 3; y < hipY; y += 4.6) pattern.push(fill(top2, rrect(-14, y, 28, 1.7, 0.8)));
  if (o.pattern === 'spots') [[-6, shY + 6, 1.6], [3, shY + 9, 2.1], [-2, shY + 16, 1.4], [6, shY + 20, 1.7], [-7, shY + 23, 2], [1, shY + 27, 1.5]].forEach(([x, y, r]) => pattern.push(fill(top2, circle(x, y, r))));
  const tunicD = o.tunic ? smooth([
    [-12.6, hipY + (sit ? 5 : 16), 1], [-11, hipY - 6], [-11.4, shY + 8], [-10, shY + 1.6], [-4.4, shY - 1.2], [4.4, shY - 1.2],
    [10, shY + 1.6], [11.4, shY + 8], [11, hipY - 6], [12.4, hipY + (sit ? 5 : 16), 1], [0, hipY + (sit ? 6 : 17.4)],
  ], { t: 0.9 }) : null;
  const torsoParts = [
    fill(o.tunic || top, tunicD || torsoD),
    pattern.length ? g({ clip: tunicD || torsoD }, ...pattern) : null,
    // collar / neck shadow
    fill(shade, smooth([[-4.2, shY - 1.6], [0, shY + 2.6], [4.2, shY - 1.6], [0, shY - 3.2]])),
  ];
  if (o.vest) {
    torsoParts.push(fill(o.vest, smooth([[-10.6, hipY + 1.5, 1], [-11.2, shY + 7], [-9.4, shY + 1], [-4.6, shY - 0.8, 1], [-1.6, hipY - 6], [-2.4, hipY + 1.5, 1]], { t: 0.8 })));
    torsoParts.push(fill(o.vest, smooth([[10.4, hipY + 1.5, 1], [11.2, shY + 7], [9.4, shY + 1], [4.6, shY - 0.8, 1], [1.8, hipY - 6], [2.6, hipY + 1.5, 1]], { t: 0.8 })));
  }
  if (o.apron) {
    const ap = o.apron, ap2 = o.apron2 || `${o.apron}2`;
    const bot = sit ? hipY + 4 : hipY + 13;
    torsoParts.push(stroke(ap2, 1.4, line([[-5.2, shY + 9], [-3.2, shY - 1.5]])));
    torsoParts.push(stroke(ap2, 1.4, line([[5.2, shY + 9], [3.2, shY - 1.5]])));
    torsoParts.push(fill(ap, smooth([
      [-5.4, shY + 7.5, 1], [5.4, shY + 7.5, 1], [6.2, shY + 13], [9.4, hipY - 6.5, 1], [10.3, hipY + 2], [10.9, bot - 2.4], [9.4, bot],
      [0, bot + 0.6], [-9.4, bot], [-10.9, bot - 2.4], [-10.3, hipY + 2], [-9.4, hipY - 6.5, 1], [-6.2, shY + 13],
    ], { t: 0.8 })));
    torsoParts.push(fill(ap2, rrect(-4.8, hipY - 2.6, 9.6, 6.2, [0.6, 0.6, 2, 2])));
    torsoParts.push(fill(ap2, rrect(-9.9, hipY - 7.4, 19.8, 1.5, 0.75)));
    // the bow of the apron strings at the back
    torsoParts.push(fill(ap2, smooth([[9.6, hipY - 6.6], [13.4, hipY - 9.4], [14.2, hipY - 6.4], [11.4, hipY - 5.8]], { t: 0.9 })));
    torsoParts.push(fill(ap2, smooth([[9.6, hipY - 6.2], [12.6, hipY - 2.6], [10.8, hipY - 1.6], [9.4, hipY - 4.6]], { t: 0.9 })));
  }
  if (o.scarfDrape) {
    // headscarf falls over the shoulders
    torsoParts.push(fill(o.scarfDrape, smooth([[-11.6, shY + 9], [-10.2, shY - 1], [-4, shY - 3.5], [4, shY - 3.5], [10.2, shY - 1], [11.6, shY + 9], [6, shY + 5.5], [0, shY + 7.2], [-6, shY + 5.5]], { t: 0.85 })));
  }

  const head = headNode(o, id, A, headY, neckY, skin, shade);

  const armB = arm('B', shB, o.sleeveB || top2, shade, o.sleeve ?? 13);
  const torso = g({ name: id('torso'), px: 0, py: hipY, ...A('torso') },
    o.armBFront ? null : armB,
    ...torsoParts,
    head,
    o.armBFront ? armB : null,
    arm('F', shF, top, skin, o.sleeve ?? 13),
  );

  const legs = [leg('B', hipB, `${pants}2`, shoes), leg('F', hipF, pants, shoes)];
  const seated = sit ? [o.seat || null] : [];
  const body = g({ name: id('body'), px: 0, py: 0, ...A('body') }, ...seated, ...legs, torso);
  const sc = o.scale || 1.1;
  const flip = { sx: { k: [[0, o.facing === 'right' ? -sc : sc, 's']] }, sy: { k: [[0, sc, 's']] } };
  return g({ name: n, px: 0, py: 0, x: { k: [[0, o.x, 's']] }, y: { k: [[0, o.y ?? GROUND, 's']] }, ...flip, ...A('root') },
    o.shadow === false ? null : fill('shadow', ellipse(0, 0.8, 17, 1.6)),
    body,
  );
}

/**
 *  Head (local origin at the neck pivot; head centre `headY`): back hair, ear, face, front hair / scarf / cap.
 *  Face: eyes blink with `eyes.sy`, look with `look.x`; brows (`browL`/`browR`) angle; mouth morph `mouth.m`
 *  (0 = smile, 1 = flat, 2 = worried, 3 = "o").
 */
function headNode(o, id, A, headY, neckY, skin, shade) {
  const c = headY;
  const hairCol = o.hair || 'hair';
  const parts = [];
  // neck
  parts.push(fill(shade, rrect(-3.6, c + 8, 7.2, neckY - c - 5, 2)));
  // back hair / scarf back
  if (o.scarf) parts.push(fill(o.scarf, smooth([[-13.2, c + 2], [-12.4, c - 11], [-3, c - 17.6], [8, c - 15.4], [14.6, c - 6], [15.4, c + 6], [12.6, c + 15.5], [4, c + 17.8], [-7, c + 15]])));
  else if (o.hairStyle === 'long') parts.push(fill(hairCol, smooth([[-11, c - 6], [-4, c - 15.5], [7, c - 14.5], [14, c - 5], [14.8, c + 9], [11.4, c + 15], [9, c + 6], [2, c - 4]])));
  // ear
  if (!o.scarf) {
    parts.push(fill(shade, ellipse(11.4, c + 1.2, 3.1, 3.9)));
    parts.push(fill('blush', ellipse(11.6, c + 1.3, 1.4, 2.1)));
  }
  const S0 = o.turn ?? 2.0;
  const X0 = (x) => x - S0;
  // face skin — a bearded head has a fuller jaw: the beard is drawn inside this silhouette, never past it
  const chin = o.beard ? [[5.8, c + 13.4], [-1.4, c + 18.6], [-8.4, c + 15.2]] : [[5.6, c + 12], [-1.4, c + 13.6], [-7.8, c + 11.6]];
  const faceD = smooth([[-1, c - 13.2], [-9.4, c - 10.6], [-12.4, c - 2.4], [-11.6, c + 6.4], ...chin.reverse(), [10.8, c + 6.4], [12.2, c - 2.6], [10, c - 10.4], [4.6, c - 13.2]]);
  //  bearded: the beard is the lower part of the head itself — skin above a soft wave under the nose, beard below, both
  //  inside the same silhouette (the skin stops 1.4 under the wave so no light rim shows at the beard's edge)
  const wave = [[X0(-7.6), c + 5.2], [X0(-3.6), c + 4.8], [X0(-0.8), c + 6.6], [X0(2.6), c + 4.8], [X0(7.4), c + 3]];
  if (o.beard) {
    const above = smooth([[-20, c - 30, 1], [20, c - 30, 1], [20, c + 2, 1], ...wave.slice().reverse().map(([x, y]) => [x, y + 1.4]), [-20, c + 2.6, 1]], { t: 0.9 });
    parts.push(g({ name: id('skin') }, g({ clip: above }, fill(skin, faceD))));
    parts.push(g({ name: id('beard'), clip: faceD },
      fill(o.beardCol || hairCol, smooth([[-16, c + 1.2], ...wave, [16, c + 0.6], [16, c + 24, 1], [-16, c + 24, 1]], { t: 0.9 })),
    ));
  } else parts.push(g({ name: id('skin') }, fill(skin, faceD)));

  /*
   *  Three-quarter face (owner 1405/08/25: the nose and mouth must never stick out of the face). Features are laid out
   *  frontally around x = 0, then the whole face slides toward the side the head faces (−x) by the turn S: S0 at rest,
   *  0 … TURN_MAX while acting (`face.turn` 0 … 1 in a scene). TURN_MAX is chosen so that, at full turn and with the
   *  eyes looking aside, every feature's outer edge stays ≥ 6% of the head width inside the skin — tools/art/check.mjs
   *  proves it for every frame, and gen.mjs refuses to write data that fails.
   */
  const X = X0;
  const face = [];
  // cheek (the nose comes after the facial hair, so it sits on the mustache)
  face.push(g({ name: id('blush') }, fill('blush', ellipse(X(4.6), c + 4.8, 2.8, 1.9))));
  // eyes (blink + look)
  const eye = (x, r) => fill('ink', ellipse(X(x), c - 0.6, r, r * 1.08));
  face.push(g({ name: id('look'), px: 0, py: c, ...A('look') },
    g({ name: id('eyes'), px: 0, py: c - 0.6, ...A('eyes') }, eye(-4.4, 1.35), eye(4.2, 1.45)),
  ));
  // brows: thick rounded bars, each with its own pivot
  const brow = (key, fx, y, base) => {
    const x = X(fx);
    return g({ name: id(key), px: x, py: y, r: { k: [[0, base, 's']] }, ...A(key) },
      fill(o.browCol || 'brow', smooth([[x - 2.7, y + 0.4], [x - 1.9, y - 1.3], [x + 2.1, y - 1.1], [x + 2.8, y + 0.5], [x + 1.8, y + 1.4], [x - 1.9, y + 1.3]], { t: 0.9 })),
    );
  };
  face.push(brow('browL', -3.9, c - 5.4, 8));
  face.push(brow('browR', 4.4, c - 6.2, -8));
  // glasses: lenses round the eyes, the arm runs back toward the ear but stops inside the face
  if (o.glasses) {
    face.push(g({ name: id('glasses') },
      stroke('ink', 0.9, ellipse(X(-4.4), c - 0.4, 3.0, 2.8)),
      stroke('ink', 0.9, ellipse(X(4.2), c - 0.4, 3.1, 2.9)),
      stroke('ink', 0.9, line([[X(-1.4), c - 0.8], [X(1.1), c - 0.8]])),
      stroke('ink', 0.9, line([[X(7.3), c - 0.8], [X(10.2), c - 1.4]]))));
  }
  // mustache under the nose, tips turned up, well inside the cheeks
  const my = o.mustache ? c + 8.6 : c + 7.4;
  if (o.mustache) {
    face.push(g({ name: id('mustache') }, fill(o.beardCol || hairCol, smooth([
      [X(-6.0), c + 5.4], [X(-4.8), c + 3.6], [X(-2.6), c + 3.1], [X(-0.8), c + 3.8], [X(1.0), c + 3.1], [X(3.2), c + 3.6], [X(4.4), c + 5.4],
      [X(2.8), c + 6.2], [X(0.6), c + 7.2], [X(-0.8), c + 6.6], [X(-2.2), c + 7.2], [X(-4.4), c + 6.2],
    ], { t: 0.9 }))));
  }
  // nose between the eyes, a touch toward the turned side
  face.push(g({ name: id('nose') }, fill('nose', ellipse(X(-0.8), c + 2.2, 2.5, 2.3))));
  //  every variant = M + two cubics (smile, flat, worried, "o") so they morph point to point
  const mo = (x0, y0, c1x, c1y, c2x, c2y, xm, ym, c3x, c3y, c4x, c4y, x1, y1) =>
    `M${X(x0)} ${y0}C${X(c1x)} ${c1y} ${X(c2x)} ${c2y} ${X(xm)} ${ym}C${X(c3x)} ${c3y} ${X(c4x)} ${c4y} ${X(x1)} ${y1}`;
  face.push(g({ name: id('mouth') }, morphStroke(o.beard ? 'nose' : 'ink', o.beard ? 1.5 : 1.15, A('mouth').m || { k: [[0, 0, 's']] },
    mo(-3.2, my - 0.4, -2.6, my + 0.8, -1.6, my + 1.3, -0.6, my + 1.3, 0.6, my + 1.3, 1.5, my + 0.6, 2.0, my - 0.3),
    mo(-3.0, my + 0.5, -2.2, my + 0.4, -1.4, my + 0.3, -0.6, my + 0.3, 0.2, my + 0.3, 1.0, my + 0.4, 1.8, my + 0.5),
    mo(-2.6, my + 1.4, -2.1, my + 0.3, -1.5, my - 0.2, -0.7, my - 0.2, 0.2, my - 0.2, 0.8, my + 0.3, 1.2, my + 1.3),
    mo(0, my - 0.9, -1.6, my - 0.9, -1.6, my + 1.8, 0, my + 1.8, 1.6, my + 1.8, 1.6, my - 0.9, 0.1, my - 0.9),
  )));
  // the face turns: `face.turn` 0 … 1 slides every feature (TURN_MAX at full turn), from the rest turn S0
  //  no explicit turn ⇒ the head follows the eyes: looking toward the prop turns the face, looking at you opens it
  const lookX = A('look').x;
  const turnTrack = A('face').turn || (lookX && lookX.k ? { k: lookX.k.map(([t, v, e]) => [t, Math.max(0, Math.min(1, 0.62 - 0.35 * v)), e]) } : null);
  const faceX = turnTrack ? {
    x: {
      k: (turnTrack.k || []).map(([t, v, e]) => [t, S0 - v * TURN_MAX, e]),
      ...(turnTrack.w ? { w: [-turnTrack.w[0] * TURN_MAX, turnTrack.w[1], turnTrack.w[2]] } : {}),
    },
  } : {};
  parts.push(g({ name: id('face'), px: 0, py: c, ...faceX }, ...face));

  // front hair / scarf / cap
  if (o.scarf) {
    parts.push(fill(o.scarf, smooth([[-12.8, c + 4], [-12.6, c - 8], [-5, c - 16.8], [6, c - 16], [13.6, c - 8], [14.6, c + 4], [12.4, c - 3.6], [6, c - 11.2], [-3, c - 11.6], [-9.6, c - 7.6]], { t: 0.9 })));
    parts.push(fill(o.scarf2 || `${o.scarf}2`, smooth([[-12.6, c + 3.4], [-11.4, c - 6.4], [-9.6, c - 7.6], [-11, c - 1]], { t: 0.9 })));
  } else if (o.hairStyle !== 'bald') {
    const style = o.hairStyle || 'short';
    if (style === 'short' || style === 'long') {
      parts.push(fill(hairCol, smooth([
        [-12.4, c - 3], [-12.8, c - 9.4], [-8.4, c - 15.4], [-1.4, c - 17.6], [6.4, c - 16.8], [12.2, c - 12.4], [13.6, c - 5.2], [12.6, c + 0.8],
        [10.6, c - 4.4, 1], [8.6, c - 8.4], [5.4, c - 10.2], [3.4, c - 8.6, 1], [0.6, c - 10.6], [-3.4, c - 11.2], [-5.4, c - 9.6, 1], [-8.4, c - 10.8], [-10.8, c - 7.8],
      ], { t: 0.85 })));
    } else if (style === 'curly') {
      const bumps = [];
      for (let i = 0; i <= 10; i++) {
        const a = Math.PI * (1.05 + (i / 10) * 0.95);
        const r = i % 2 ? 15.4 : 13.8;
        bumps.push([Math.cos(a) * r * 0.98 + 0.6, c - 2.4 + Math.sin(a) * r * 0.95, i % 2 ? 0 : 1]);
      }
      parts.push(fill(hairCol, smooth([...bumps, [13.4, c + 1], [10.4, c - 5.6], [4, c - 9.8], [-4.4, c - 10], [-10.6, c - 6.6], [-12.6, c - 1]], { t: 0.9 })));
    }
    if (o.grey) parts.push(fill(o.grey, smooth([[-12.4, c - 3.4], [-12.8, c - 8], [-11.6, c - 6.8], [-11.2, c - 2.2]])));
  } else {
    // bald: side hair fringe
    parts.push(fill(hairCol, smooth([[6.6, c - 10.4], [11.6, c - 9], [14.2, c - 3], [13.8, c + 4.6], [11.2, c + 6.4], [9.4, c + 1], [9.6, c - 5.2]], { t: 0.9 })));
  }
  if (o.cap) {
    const cp = o.cap, cp2 = o.cap2 || `${o.cap}2`;
    parts.push(fill(cp, smooth([[-12.8, c - 8.4, 1], [-11.6, c - 14.2], [-4, c - 19.8], [5, c - 19.6], [11.8, c - 15], [13.6, c - 8.4, 1]], { t: 0.9 })));
    parts.push(fill(cp2, smooth([[-20.6, c - 8.6], [-19.4, c - 10.6], [-8, c - 10.6], [13.6, c - 9.8, 1], [13.6, c - 7.6, 1], [-8, c - 7.4]], { t: 0.6 })));
    parts.push(fill(cp2, circle(-1.4, c - 19.4, 1.5)));
  }
  if (o.helmet) {
    const hm = o.helmet, hm2 = o.helmet2 || `${o.helmet}2`;
    parts.push(fill(hm, smooth([[-14.4, c - 2.6, 1], [-14.2, c - 12], [-6, c - 20.6], [5, c - 20.4], [13.6, c - 13.6], [15.4, c - 3.4, 1], [12, c - 3.8], [0, c - 6.6], [-10, c - 5.2]], { t: 0.9 })));
    parts.push(fill(hm2, smooth([[-15.2, c - 3.4], [-15, c - 6.4, 1], [-9.6, c - 8.6], [-1, c - 9], [-1.4, c - 6], [-9.4, c - 4.6]], { t: 0.8 })));
    parts.push(fill(hm2, rrect(-4, c - 20.6, 8, 3, 1.5)));
  }
  if (o.pencil) parts.push(g({}, fill('mustard', capsule(6.4, c - 2.6, 16.4, c - 7.6, 1.1)), fill('cream2', capsule(5.6, c - 2.2, 7.2, c - 3, 1.1)), fill('ink', capsule(16.2, c - 7.5, 17.8, c - 8.3, 0.55, 0.2))));
  return g({ name: id('head'), px: 0, py: neckY, ...A('head') }, ...parts);
}

/** how far (scene units) the features may slide toward the turned side — see `headNode` and tools/art/check.mjs */
export const TURN_MAX = 3.0;

/* ---------------------------------------------------------------- posing helpers */

/** limb lengths used by `person` (upper arm, forearm; thigh, shin) */
export const ARM = [16, 15];
export const LEG = [15.5, 13.5];
export const SHOULDER = { F: [7.5, -60], B: [-7.5, -60] };

/**
 *  two-link IK in the person's local frame (rotation 0 = limb hanging down, + = swings toward −x / forward):
 *  returns [upper, lower] angles in degrees so the end reaches (hx, hy) from joint (sx, sy). `bend` = ±1 elbow side.
 */
export function ik(sx, sy, hx, hy, bend = 1, [l1, l2] = ARM) {
  const vx = hx - sx, vy = hy - sy;
  const d = Math.min(l1 + l2 - 0.01, Math.max(Math.abs(l1 - l2) + 0.01, Math.hypot(vx, vy)));
  const base = Math.atan2(-vx, vy);
  const alpha = Math.acos((l1 * l1 + d * d - l2 * l2) / (2 * l1 * d));
  const inner = Math.acos((l1 * l1 + l2 * l2 - d * d) / (2 * l1 * l2));
  const a1 = base - bend * alpha;
  const a2 = bend * (Math.PI - inner);
  return [(a1 * 180) / Math.PI, (a2 * 180) / Math.PI];
}

/** blink keys for an `eyes.sy` track: open (1) with quick closes at `times` */
export function blinks(...times) {
  const k = [[0, 1, 'l']];
  for (const b of times) k.push([b, 1, 'l'], [b + 0.07, 0.08, 'l'], [b + 0.16, 1, 'l']);
  return { k: k.sort((a, b) => a[0] - b[0]).filter((x, i, arr) => i === 0 || x[0] !== arr[i - 1][0]) };
}

/** `ik` choosing the elbow side: 'down' | 'up' | 'fwd' (−x) | 'back' (+x) */
export function reach(sx, sy, hx, hy, side = 'down', lens = ARM) {
  const opts = [1, -1].map((b) => {
    const a = ik(sx, sy, hx, hy, b, lens);
    const r = (a[0] * Math.PI) / 180;
    return { a, ex: sx - lens[0] * Math.sin(r), ey: sy + lens[0] * Math.cos(r) };
  });
  const score = { down: (o) => o.ey, up: (o) => -o.ey, fwd: (o) => -o.ex, back: (o) => o.ex }[side];
  return (score(opts[0]) >= score(opts[1]) ? opts[0] : opts[1]).a;
}

/**
 *  keys repeated every `period` seconds (offset `phase`) over a `loop`: rain drops, blinking lights, flutter.
 *  `keys` = [[u, v, ease]] with u inside one period.
 */
export function repeat(loop, period, phase, keys) {
  const n = Math.round(loop / period);
  const out = [];
  for (let i = 0; i < n; i++) for (const [u, v, e] of keys) out.push([(((i * period + u + phase) % loop) + loop) % loop, v, e]);
  out.sort((a, b) => a[0] - b[0]);
  return { k: out.filter((x, i, arr) => i === 0 || Math.abs(x[0] - arr[i - 1][0]) > 1e-6) };
}
