/**
 *  Flat-scene engine for the empty/error illustrations (batch 65, e-rows-65a).
 *
 *  A scene is a tree of groups (pivot + animated rotate/translate/scale/alpha) and leaves (filled or stroked
 *  path, a morph between path variants, or a rope whose ends follow two groups). The same tree is
 *    - rendered to SVG here (preview while drawing: tools/art/preview.mjs), and
 *    - serialised to the line format that `ui/v3/art/ArtScene.kt` parses (tools/art/gen.mjs).
 *  Both sides evaluate tracks with the same formulas (keep `EASE`/`evalTrack` and `ArtTrack.at` in step).
 */

export const EASE = {
  l: (u) => u,
  io: (u) => (u < 0.5 ? 4 * u * u * u : 1 - Math.pow(-2 * u + 2, 3) / 2),
  i: (u) => u * u * u,
  o: (u) => 1 - Math.pow(1 - u, 3),
  s: () => 0,
  b: (u) => { const c1 = 1.70158; const c3 = c1 + 1; return 1 + c3 * Math.pow(u - 1, 3) + c1 * Math.pow(u - 1, 2); },
};

/**
 *  Track = { k: [[t, v, e], ...], w: [amp, cycles, phase] } — keys loop over the scene's `loop` seconds (the last key
 *  eases back into the first); the wave (integer cycles per loop) is added on top. Missing ⇒ `def`.
 */
export function evalTrack(tr, t, loop, def) {
  if (tr == null) return def;
  let v = def;
  const k = tr.k;
  if (k && k.length) {
    if (k.length === 1) v = k[0][1];
    else {
      let i = k.length - 1;
      for (let j = 0; j < k.length; j++) if (k[j][0] <= t) i = j;
      const a = k[i];
      const b = i + 1 < k.length ? k[i + 1] : k[0];
      let ta = a[0];
      let tb = i + 1 < k.length ? b[0] : b[0] + loop;
      let tt = t;
      if (tt < ta) tt += loop;
      const span = tb - ta;
      const u = span <= 0 ? 0 : Math.min(1, Math.max(0, (tt - ta) / span));
      v = a[1] + (b[1] - a[1]) * EASE[a[2] || 'io'](u);
    }
  }
  if (tr.w) v += tr.w[0] * Math.sin((2 * Math.PI * tr.w[1] * t) / loop + (tr.w[2] || 0));
  return v;
}

const f = (n) => {
  const r = Math.round(n * 100) / 100;
  return Object.is(r, -0) ? '0' : String(r);
};
export const fmt = f;

/* ---------------------------------------------------------------- path normalisation (absolute M/L/C/Z only) */

/** parse an SVG path (M L H V C S Q T Z, abs/rel; no arcs) into absolute M/L/C/Z ops */
export function normPath(d) {
  const toks = d.match(/[MLHVCSQTZmlhvcsqtz]|-?\d*\.?\d+(?:e-?\d+)?/g) || [];
  const out = [];
  let i = 0, cmd = '', x = 0, y = 0, sx = 0, sy = 0, lcx = 0, lcy = 0, lqx = 0, lqy = 0, prev = '';
  const num = () => parseFloat(toks[i++]);
  while (i < toks.length) {
    if (/[A-Za-z]/.test(toks[i])) cmd = toks[i++];
    const rel = cmd === cmd.toLowerCase();
    const C = cmd.toUpperCase();
    if (C === 'Z') { out.push(['Z']); x = sx; y = sy; prev = 'Z'; continue; }
    if (C === 'M') {
      let nx = num(), ny = num(); if (rel) { nx += x; ny += y; }
      out.push(['M', nx, ny]); x = sx = nx; y = sy = ny; cmd = rel ? 'l' : 'L'; prev = 'M'; continue;
    }
    if (C === 'L' || C === 'H' || C === 'V') {
      let nx = x, ny = y;
      if (C === 'L') { nx = num(); ny = num(); if (rel) { nx += x; ny += y; } }
      if (C === 'H') { nx = num(); if (rel) nx += x; }
      if (C === 'V') { ny = num(); if (rel) ny += y; }
      out.push(['L', nx, ny]); x = nx; y = ny; prev = 'L'; continue;
    }
    if (C === 'C' || C === 'S') {
      let x1, y1;
      if (C === 'C') { x1 = num(); y1 = num(); if (rel) { x1 += x; y1 += y; } } else if (prev === 'C') { x1 = 2 * x - lcx; y1 = 2 * y - lcy; } else { x1 = x; y1 = y; }
      let x2 = num(), y2 = num(), nx = num(), ny = num();
      if (rel) { x2 += x; y2 += y; nx += x; ny += y; }
      out.push(['C', x1, y1, x2, y2, nx, ny]); lcx = x2; lcy = y2; x = nx; y = ny; prev = 'C'; continue;
    }
    if (C === 'Q' || C === 'T') {
      let qx, qy;
      if (C === 'Q') { qx = num(); qy = num(); if (rel) { qx += x; qy += y; } } else if (prev === 'Q') { qx = 2 * x - lqx; qy = 2 * y - lqy; } else { qx = x; qy = y; }
      let nx = num(), ny = num(); if (rel) { nx += x; ny += y; }
      out.push(['C', x + (2 / 3) * (qx - x), y + (2 / 3) * (qy - y), nx + (2 / 3) * (qx - nx), ny + (2 / 3) * (qy - ny), nx, ny]);
      lqx = qx; lqy = qy; x = nx; y = ny; prev = 'Q'; continue;
    }
    throw new Error(`path command ${cmd} not supported in ${d.slice(0, 40)}`);
  }
  return out;
}

export const opsToD = (ops) => ops.map((o) => o[0] + o.slice(1).map(f).join(' ')).join('');

/** every segment as a cubic (morph needs the same op list in every variant) */
function cubicOps(ops) {
  let x = 0, y = 0, sx = 0, sy = 0;
  return ops.map((o) => {
    if (o[0] === 'M') { x = sx = o[1]; y = sy = o[2]; return o; }
    if (o[0] === 'L') { const r = ['C', x + (o[1] - x) / 3, y + (o[2] - y) / 3, x + (2 * (o[1] - x)) / 3, y + (2 * (o[2] - y)) / 3, o[1], o[2]]; x = o[1]; y = o[2]; return r; }
    if (o[0] === 'C') { x = o[5]; y = o[6]; return o; }
    x = sx; y = sy; return o;
  });
}

function lerpOps(a, b, u) {
  return a.map((o, i) => [o[0], ...o.slice(1).map((v, j) => v + (b[i][j + 1] - v) * u)]);
}

/* ---------------------------------------------------------------- transforms */

const ID = [1, 0, 0, 1, 0, 0];
const mul = (m, n) => [m[0] * n[0] + m[2] * n[1], m[1] * n[0] + m[3] * n[1], m[0] * n[2] + m[2] * n[3], m[1] * n[2] + m[3] * n[3], m[0] * n[4] + m[2] * n[5] + m[4], m[1] * n[4] + m[3] * n[5] + m[5]];
const apply = (m, x, y) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]];

function local(n, t, loop) {
  const px = n.px || 0, py = n.py || 0;
  const r = (evalTrack(n.r, t, loop, 0) * Math.PI) / 180;
  const x = evalTrack(n.x, t, loop, 0), y = evalTrack(n.y, t, loop, 0);
  const sx = evalTrack(n.sx, t, loop, 1), sy = evalTrack(n.sy, t, loop, 1);
  const c = Math.cos(r), s = Math.sin(r);
  // T(x+px, y+py) · R · S · T(-px, -py)
  let m = [1, 0, 0, 1, x + px, y + py];
  m = mul(m, [c, s, -s, c, 0, 0]);
  m = mul(m, [sx, 0, 0, sy, 0, 0]);
  return mul(m, [1, 0, 0, 1, -px, -py]);
}

/** index groups by name (and parent links) once per scene */
export function indexScene(scene) {
  const byName = new Map();
  const walk = (n, parent) => {
    n.parent = parent;
    if (n.name) {
      if (byName.has(n.name)) throw new Error(`duplicate group name ${n.name}`);
      byName.set(n.name, n);
    }
    (n.kids || []).forEach((k) => walk(k, n));
  };
  walk(scene.root, null);
  scene.byName = byName;
  return scene;
}

function worldOf(scene, name, x, y, t) {
  if (name === '@') return [x, y];
  let n = scene.byName.get(name);
  if (!n) throw new Error(`rope anchor ${name} missing`);
  let m = ID;
  const chain = [];
  for (; n; n = n.parent) chain.push(n);
  for (let i = chain.length - 1; i >= 0; i--) m = mul(m, local(chain[i], t, scene.loop));
  return apply(m, x, y);
}

/* ---------------------------------------------------------------- SVG at time t */

export const VIEW = [30, 22, 340, 170];
let svgSeq = 0;

export function svgAt(scene, t, colors, { view = VIEW, px = 800 } = {}) {
  if (!scene.byName) indexScene(scene);
  const L = scene.loop;
  const col = (role) => {
    const c = colors[role];
    if (!c) throw new Error(`role ${role} missing in palette`);
    return c;
  };
  const leaf = (n, alpha) => {
    const a = alpha < 0.999 ? ` opacity="${f(alpha)}"` : '';
    const paint = n.w ? `fill="none" stroke="${col(n.role)}" stroke-width="${f(n.w)}" stroke-linecap="round" stroke-linejoin="round"` : `fill="${col(n.role)}"`;
    let d;
    if (n.d) d = opsToD(n.ops || (n.ops = normPath(n.d)));
    else if (n.morph) {
      if (!n.mops) n.mops = n.morph.map((x) => cubicOps(normPath(x)));
      const v = Math.min(n.mops.length - 1, Math.max(0, evalTrack(n.m, t, L, 0)));
      const i = Math.min(n.mops.length - 2, Math.floor(v));
      d = opsToD(lerpOps(n.mops[i], n.mops[i + 1], v - i));
    } else if (n.rope) {
      const [an, ax, ay, tax, tay, bn, bx, by, tbx, tby] = n.rope;
      const A = worldOf(scene, an, ax, ay, t), B = worldOf(scene, bn, bx, by, t);
      d = `M${f(A[0])} ${f(A[1])}C${f(A[0] + tax)} ${f(A[1] + tay)} ${f(B[0] + tbx)} ${f(B[1] + tby)} ${f(B[0])} ${f(B[1])}`;
    }
    return `<path d="${d}" ${paint}${a}/>`;
  };
  const node = (n, alpha) => {
    if (!n.kids) return leaf(n, alpha);
    const a = alpha * evalTrack(n.a, t, L, 1);
    if (a <= 0.001) return '';
    const m = local(n, t, L);
    let inner = n.kids.map((k) => node(k, a)).join('');
    if (n.clip) {
      const cid = `c${svgSeq}-${(clipSeq += 1)}`;
      inner = `<clipPath id="${cid}"><path d="${opsToD(normPath(n.clip))}"/></clipPath><g clip-path="url(#${cid})">${inner}</g>`;
    }
    const id = n.name ? ` id="${n.name}"` : '';
    return `<g${id} transform="matrix(${m.map(f).join(' ')})">${inner}</g>`;
  };
  let clipSeq = 0;
  svgSeq += 1;
  const [vx, vy, vw, vh] = view;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="${vx} ${vy} ${vw} ${vh}" width="${px}" height="${Math.round((px * vh) / vw)}">${node(scene.root, 1)}</svg>`;
}

/* ---------------------------------------------------------------- line format for Kotlin (`ArtScene.parse`) */

function trackText(tr) {
  if (tr == null) return null;
  const k = (tr.k || []).map((x) => `${f(x[0])},${f(x[1])},${x[2] || 'io'}`).join('/');
  const w = tr.w ? `~${f(tr.w[0])},${tr.w[1]},${f(tr.w[2] || 0)}` : '';
  return k + w;
}

export function sceneText(scene) {
  if (!scene.byName) indexScene(scene);
  const lines = [`S ${f(scene.loop)} ${f(scene.rest)} ${scene.badge.map(f).join(' ')}`];
  const d = (s) => opsToD(normPath(s));
  const walk = (n, depth) => {
    if (n.kids) {
      const tr = ['r', 'x', 'y', 'sx', 'sy', 'a'].map((p) => (n[p] != null ? `${p}:${trackText(n[p])}` : null)).filter(Boolean);
      lines.push(['G', n.name || '-', f(n.px || 0), f(n.py || 0), ...tr].join(' '));
      if (n.clip) lines.push(`C ${d(n.clip)}`);
      n.kids.forEach((k) => walk(k, depth + 1));
      lines.push('E');
    } else if (n.d) lines.push(`P ${n.role} ${f(n.w || 0)} ${d(n.d)}`);
    else if (n.morph) {
      const vs = n.morph.map((x) => opsToD(cubicOps(normPath(x))));
      lines.push(`M ${n.role} ${f(n.w || 0)} ${trackText(n.m)} ${vs.join(' | ')}`);
    } else if (n.rope) {
      if (depth > 1) throw new Error('rope must sit at the scene root (world coordinates)');
      lines.push(`R ${n.role} ${f(n.w || 0)} ${n.rope.map((v) => (typeof v === 'number' ? f(v) : v)).join(' ')}`);
    }
  };
  walk(scene.root, 0);
  return lines.join('\n');
}
