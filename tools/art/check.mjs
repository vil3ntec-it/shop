#!/usr/bin/env node
/**
 *  Face guard (owner 1405/08/25: «دماغ و دهنش از چهره‌اش بیرون زده»): every facial feature — eyes, brows, nose, mouth,
 *  mustache, cheeks, glasses — must stay inside its head's skin shape in every frame of every loop.
 *
 *    node tools/art/check.mjs [--times 48] [--report]
 *
 *  For each person head (`<p>.head`) the skin path (`<p>.skin`) is the bound; each feature group (`<p>.<feature>`) is
 *  sampled at `--times` evenly spaced moments of the loop, every path in it is taken into head coordinates (all the
 *  rig's transforms in between: turn, look, blink, brow angle; the mouth morph at that moment; stroke half-width
 *  added) and its bounding box must lie inside the skin with no tolerance. The beard is drawn clipped to the skin, so
 *  it cannot leave it. A nose may touch the contour only in a head marked `profile` (none today). Exit 1 on any miss.
 *  `--report` also prints the smallest margin per feature (head-width share), so a near miss is visible.
 */
import { pathToFileURL } from 'node:url';
import { evalTrack, normPath, indexScene, local, mul, ID } from './engine.mjs';
import { flatten, inside, bbox, boxPoints, margin } from './geom.mjs';
import { SCENES } from './scenes/index.mjs';

export const FEATURES = ['eyes', 'browL', 'browR', 'nose', 'mouth', 'mustache', 'blush', 'glasses'];
/** design target: the outermost edge of each feature stays this share of the head width inside the silhouette */
export const MARGIN = 0.06;

const morphOps = (n, t, loop) => {
  const vs = n.morph.map((d) => normPath(d));
  const v = Math.min(vs.length - 1, Math.max(0, evalTrack(n.m, t, loop, 0)));
  const i = Math.min(vs.length - 2, Math.floor(v));
  const u = v - i;
  // morph variants share their op list once every segment is a cubic; lerp on the normalised ops is close enough for bounds
  const a = flatten(vs[i]).flat(), b = flatten(vs[i + 1]).flat();
  return a.map((p, k) => [p[0] + ((b[k] || p)[0] - p[0]) * u, p[1] + ((b[k] || p)[1] - p[1]) * u]);
};

/** all points of a leaf in its own coordinates (strokes widened by half their width) */
function leafPoints(n, t, loop) {
  const pts = n.d ? flatten(n.d).flat() : n.morph ? morphOps(n, t, loop) : [];
  if (!n.w) return pts;
  const h = n.w / 2;
  return pts.flatMap(([x, y]) => [[x - h, y - h], [x + h, y - h], [x - h, y + h], [x + h, y + h]]);
}

/** transform of `node` relative to `top` (product of the groups strictly below `top`, node included) */
function relative(node, top, t, loop) {
  const chain = [];
  for (let n = node; n && n !== top; n = n.parent) chain.push(n);
  let m = ID;
  for (let i = chain.length - 1; i >= 0; i--) if (chain[i].kids) m = mul(m, local(chain[i], t, loop));
  return m;
}

const tx = (m, [x, y]) => [m[0] * x + m[2] * y + m[4], m[1] * x + m[3] * y + m[5]];

function leaves(n, out = []) {
  if (n.kids) n.kids.forEach((k) => leaves(k, out));
  else out.push(n);
  return out;
}

/** check one scene; returns { misses, worst } */
export function checkScene(scene, times = 48) {
  if (!scene.byName) indexScene(scene);
  const misses = [];
  const worst = {};
  for (const [name, head] of scene.byName) {
    if (!name.endsWith('.head')) continue;
    const p = name.slice(0, -5);
    const skin = scene.byName.get(`${p}.skin`);
    if (!skin) { misses.push(`${scene.name}: ${p} has no skin group`); continue; }
    for (let s = 0; s < times; s++) {
      const t = (s / times) * scene.loop;
      const sm = relative(skin, head, t, scene.loop);
      const poly = leaves(skin).flatMap((l) => leafPoints(l, t, scene.loop)).map((q) => tx(sm, q));
      const [x0, , x1] = bbox(poly);
      const width = x1 - x0;
      for (const f of FEATURES) {
        const g = scene.byName.get(`${p}.${f}`);
        if (!g) continue;
        for (const leaf of leaves(g)) {
          const m = relative(leaf.parent, head, t, scene.loop);
          const pts = leafPoints(leaf, t, scene.loop).map((q) => tx(m, q));
          if (!pts.length) continue;
          const box = bbox(pts);
          const out = boxPoints(box).filter(([x, y]) => !inside(poly, x, y));
          const profileNose = f === 'nose' && head.profile;
          if (out.length && !profileNose) {
            misses.push(`${scene.name}: ${p}.${f} leaves the head at t=${t.toFixed(3)}s (box ${box.map((v) => v.toFixed(2)).join(', ')})`);
          }
          const mm = Math.min(...pts.map(([x, y]) => margin(poly, x, y))) / width;
          const key = `${p}.${f}`;
          if (!(key in worst) || mm < worst[key].m) worst[key] = { m: mm, t };
        }
      }
    }
  }
  return { misses, worst };
}

if (import.meta.url === pathToFileURL(process.argv[1]).href) {
  const args = process.argv.slice(2);
  const times = Number(args[args.indexOf('--times') + 1]) || 48;
  let bad = 0;
  for (const [key, make] of Object.entries(SCENES)) {
    const { misses, worst } = checkScene(indexScene(make()), times);
    for (const m of misses.slice(0, 12)) console.error(`✗ ${m}`);
    if (misses.length > 12) console.error(`  … ${misses.length - 12} more in ${key}`);
    bad += misses.length;
    const thin = Object.entries(worst).filter(([, w]) => w.m < MARGIN);
    for (const [f, w] of thin) console.error(`✗ ${key}: ${f} only ${(w.m * 100).toFixed(1)}% of the head width inside (t=${w.t.toFixed(2)}s, needs ≥ ${MARGIN * 100}%)`);
    bad += thin.length;
    if (args.includes('--report')) console.log(key, Object.entries(worst).map(([f, w]) => `${f.split('.').pop()} ${(w.m * 100).toFixed(1)}%@${w.t.toFixed(2)}`).join('  '));
  }
  if (bad) { console.error(`face guard: ${bad} problem(s)`); process.exit(1); }
  console.log(`face guard: every feature inside its head in ${times} moments of every scene`);
}
