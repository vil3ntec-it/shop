/**
 *  پیش‌فرضِ خالی (TREE): a girl in a headscarf reads under a big round tree, leaning on the trunk. A leaf lets go and
 *  drifts down in front of her; she looks up from the book, smiles, and turns the page.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, dome, monolith, pebble, bush, person, reach, blinks, smooth, circle, rrect, line, leafShape, poly } from '../kit.mjs';

export function tree() {
  const L = 6;
  const b = GROUND + 0.8;
  // tree: trunk with two branches, a big leafy crown of overlapping clumps (no outlines), lighter clumps in front
  const crown = (cx, cy, r, role) => fill(role, circle(cx, cy, r));
  const tip = (cx, cy, r, a, role) => {
    const tx = cx + Math.cos(a) * r * 0.96, ty = cy + Math.sin(a) * r * 0.96, len = 6.5, ang = a + Math.PI / 2;
    const pts = [[0, -len, 1], [len * 0.32, -len * 0.2], [0, len * 0.3, 1], [-len * 0.32, -len * 0.2]].map(([px, py, c]) => [tx + px * Math.cos(ang) - py * Math.sin(ang), ty + px * Math.sin(ang) + py * Math.cos(ang), c]);
    return fill(role, smooth(pts, { t: 0.8 }));
  };
  const T = 168;
  const treeG = g({ px: T, py: b, r: Wv(0.6, 2) },
    fill('kraft2', smooth([[T - 9, b, 1], [T - 6, b - 30], [T - 6, b - 58], [T - 15, b - 76], [T - 10, b - 78], [T - 1, b - 64], [T + 8, b - 82], [T + 12.6, b - 79], [T + 5.4, b - 58], [T + 6, b - 28], [T + 10, b, 1]], { t: 0.8 })),
    crown(T - 36, 88, 20, 'leaf'), crown(T - 14, 66, 26, 'leaf'), crown(T + 16, 62, 25, 'leaf'), crown(T + 40, 82, 20, 'leaf'), crown(T, 88, 24, 'leaf'),
    crown(T - 22, 100, 15, 'leaf'), crown(T + 24, 100, 15, 'leaf'),
    tip(T - 14, 66, 26, -2.2, 'leaf'), tip(T - 36, 88, 20, -2.7, 'leaf'), tip(T + 16, 62, 25, -1.2, 'leaf'), tip(T + 40, 82, 20, -0.5, 'leaf'),
    // lighter clumps along the lower front edge
    crown(T - 30, 100, 11, 'leaf2'), crown(T - 12, 107, 12, 'leaf2'), crown(T + 6, 108, 10, 'leaf2'),
  );
  // a leaf lets go, sways down in front of her and settles
  const fall = g({ a: K([0, 0, 's'], [0.4, 1, 'l'], [4.6, 1, 'l'], [5.4, 0, 's']),
    x: K([0, 0, 's'], [0.4, 0, 'io'], [1.4, 12], [2.4, 2], [3.4, 14], [4.4, 8, 'o'], [5.4, 8, 's']),
    y: K([0, 0, 's'], [0.4, 0, 'l'], [4.4, 84, 'o'], [5.4, 84, 's']),
    r: K([0, 0], [0.4, 0], [1.4, 50], [2.4, -30], [3.4, 60], [4.4, 90, 'o'], [5.4, 90]) },
    g({ x: { k: [[0, 204, 's']] }, y: { k: [[0, 90, 's']] } }, fill('leaf2', leafShape(7, 3.2))),
  );

  const bx = -18, by = -45;
  const [rb1, rb2] = reach(-7.5, -60, bx - 2, by + 6, 'down');
  const [rf1, rf2] = reach(7.5, -60, bx + 4, by + 7, 'down');
  const page = (side, role = 'white') => fill(role, smooth([[bx, by, 1], [bx + side * 9.6, by - 1.6], [bx + side * 10, by + 7.6, 1], [bx, by + 8.8, 1]], { t: 0.3 }));
  const flip = K([0, 1, 's'], [3.6, 1, 'io'], [4.2, -1, 's'], [4.3, 1, 's']);
  const book = g({ px: bx, py: by + 8, r: K([0, -14], [1.6, -14], [2.0, -6], [3.4, -6], [3.8, -14]) },
    fill('teal2', smooth([[bx, by + 0.6, 1], [bx - 11, by - 0.8], [bx - 11.4, by + 9.4, 1], [bx, by + 10.2, 1], [bx + 11.4, by + 9.4, 1], [bx + 11, by - 0.8]], { t: 0.3 })),
    page(-1), page(1),
    g({ px: bx, py: by, sx: flip, a: K([0, 0, 's'], [3.59, 0, 's'], [3.6, 1, 's'], [4.2, 1, 's'], [4.21, 0, 's']) }, page(1, 'cream')),
    stroke('rock2', 0.6, line([[bx - 7.6, by + 2.6], [bx - 2.4, by + 2.6]])), stroke('rock2', 0.6, line([[bx - 7.6, by + 4.8], [bx - 3.4, by + 4.8]])),
    stroke('rock2', 0.6, line([[bx + 2.4, by + 2.6], [bx + 7.6, by + 2.6]])), stroke('rock2', 0.6, line([[bx + 2.4, by + 4.8], [bx + 6.4, by + 4.8]])),
  );
  const reader = person({
    name: 'm', x: 192, facing: 'right', sit: true, scarf: 'teal', scarfDrape: 'teal', tunic: 'mustard', shirt: 'mustard', shirt2: 'mustard2', pattern: 'spots',
    pants: 'navy', sleeve: 31, armBFront: true, shadow: false,
    anim: {
      body: { sy: Wv(0.01, 2) },
      torso: { r: K([0, 6], [1.6, 6], [2.0, 10], [3.4, 10], [3.8, 6]) },
      head: { r: K([0, -12], [1.6, -12], [2.0, 6, 'b'], [3.3, 6], [3.7, -12]) },
      look: { x: K([0, -1.4], [1.7, -1.4], [1.95, -0.6], [3.3, -0.6], [3.6, -1.4]), y: K([0, 1.6], [1.7, 1.6], [1.95, -1.6], [3.3, -1.6], [3.6, 1.6]) },
      eyes: { sy: blinks(0.8, 2.9, 5.2) },
      browL: { r: K([0, 2], [1.7, 2], [1.95, -12], [3.3, -12], [3.7, 2]) },
      browR: { r: K([0, -2], [1.7, -2], [1.95, 12], [3.3, 12], [3.7, -2]) },
      mouth: { m: K([0, 1], [1.8, 1], [2.05, 0], [3.4, 0], [3.8, 0.6], [5, 1]) },
      armB: { r: { k: [[0, rb1, 's']] } }, foreB: { r: { k: [[0, rb2, 's']] } }, handB: { r: { k: [[0, 150, 's']] } },
      armF: { r: K([0, rf1], [3.5, rf1], [3.7, rf1 + 10], [4.2, rf1 + 8], [4.4, rf1]) }, foreF: { r: { k: [[0, rf2, 's']] } }, handF: { r: { k: [[0, -160, 's']] } },
    },
  });
  const torso = (function find(n) { if (n.name === 'm.torso') return n; for (const k of n.kids || []) { const r = find(k); if (r) return r; } return null; })(reader);
  torso.kids.splice(torso.kids.findIndex((k) => k.name === 'm.armF'), 0, book);

  const root = g({},
    ground(40, 360),
    bush(80, 28, 17, { lobes: 4, seed: 81 }),
    dome(104, 30, 22, 'rock2'),
    pebble(60, 3),
    treeG,
    dome(197, 26, 17, 'rock2'),
    pebble(226, 2.6),
    monolith(312, 52, 98, { lean: 3 }),
    bush(284, 26, 16, { lobes: 4, seed: 83 }),
    bush(344, 22, 13, { lobes: 3, seed: 85, side: 1, front: null }),
    pebble(354, 3.2), pebble(262, 2.8),
    reader,
    fall,
  );
  return { name: 'tree', loop: L, rest: 2.6, badge: [126, 62, 100], root };
}
