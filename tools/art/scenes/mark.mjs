/**
 *  خطای دیگر (ERROR_MARK): a big "!" made of two warm stones stands on the ground; its dot hops. A woman in a headscarf
 *  jumps back startled, looks up at it, then turns to you with an "oops" shrug.
 */
import { GROUND, K, Wv, g, fill, ground, monolith, dome, slab, pebble, bush, person, blinks, smooth, ellipse, leafShape } from '../kit.mjs';

export function mark() {
  const L = 4;
  const b = GROUND + 0.8;
  const bar = g({ px: 252, py: 150, r: Wv(1.2, 2, 0.4) },
    fill('orange', smooth([[244, 93], [252, 86.5], [261, 93], [258.4, 146], [252, 151.4], [246, 146]], { t: 0.9 })),
    fill('orange2', smooth([[255.4, 92], [261, 93], [258.4, 146], [252, 151.4], [255.6, 146]], { t: 0.9 })),
  );
  // the dot hops: squash, leap, stretch, land, squash
  const hop = (t0) => [[t0, 0], [t0 + 0.12, 0, 'o'], [t0 + 0.38, -9, 'i'], [t0 + 0.64, 0, 'o'], [t0 + 0.74, 0]];
  const dot = g({ px: 252, py: b, y: K([0, 0], ...hop(0.15), ...hop(2.9)),
    sy: K([0, 1], [0.15, 1], [0.27, 0.78, 'o'], [0.4, 1.12], [0.62, 1.05], [0.79, 0.8, 'o'], [0.95, 1, 'b'], [2.9, 1], [3.02, 0.82, 'o'], [3.15, 1.1], [3.37, 1.04], [3.54, 0.84, 'o'], [3.7, 1, 'b']),
    sx: K([0, 1], [0.15, 1], [0.27, 1.16, 'o'], [0.4, 0.92], [0.62, 0.96], [0.79, 1.18, 'o'], [0.95, 1, 'b'], [2.9, 1], [3.02, 1.14, 'o'], [3.15, 0.93], [3.37, 0.97], [3.54, 1.15, 'o'], [3.7, 1, 'b']) },
    fill('orange', ellipse(252, b - 8.2, 8.4, 8.2)),
    fill('orange2', smooth([[255.5, b - 15.6], [260.4, b - 8.2], [255.5, b - 0.8], [258.2, b - 8.2]], { t: 0.9 })),
  );
  // a leaf drifts down from the bush behind the stones
  const leaf = g({ px: 300, py: 120, a: K([0, 0], [0.9, 0], [1.1, 1], [3.3, 1], [3.6, 0]), y: K([0, 0, 's'], [0.9, 0, 'l'], [3.6, 52, 'l']), x: K([0, 0, 's'], [0.9, 0, 'l'], [3.6, -14]), r: Wv(40, 3) },
    g({ x: { k: [[0, 300, 's']] }, y: { k: [[0, 120, 's']] } }, fill('leaf2', leafShape(6, 2.8))),
  );

  const woman = person({
    name: 'w', x: 186, facing: 'right', scarf: 'plum', scarfDrape: 'plum', tunic: 'teal', shirt: 'teal', pattern: 'spots', pants: 'navy', sleeve: 31,
    anim: {
      body: { sy: Wv(0.012, 2), x: K([0, 0], [0.32, 0], [0.48, 2.4, 'b'], [1.7, 2.4], [2.6, 0]) },
      torso: { r: K([0, 0], [0.32, 0], [0.46, 7, 'b'], [1.6, 5], [1.95, -2], [2.7, -2], [3.1, 0]), y: K([0, 0], [1.9, 0], [2.05, -1.4, 'o'], [2.6, -1.4], [2.8, 0]) },
      head: { r: K([0, 0], [0.32, 0], [0.48, 10, 'b'], [1.6, 8], [1.95, -4], [2.7, -4], [3.1, 0]) },
      look: { x: K([0, -1], [0.32, -1], [0.45, -0.6], [1.7, -0.6], [1.95, 0.5], [2.7, 0.5], [3.0, -1]), y: K([0, 0], [0.32, 0], [0.45, -1.3], [1.7, -1.3], [1.95, 0], [3.1, 0]) },
      eyes: { sy: blinks(1.35, 3.4) },
      browL: { r: K([0, 6], [0.32, 6], [0.42, -16, 'b'], [1.6, -12], [1.95, -16], [2.7, -16], [3.1, 6]), y: K([0, 0], [0.32, 0], [0.42, -2, 'b'], [1.6, -1.4], [3.1, 0]) },
      browR: { r: K([0, -6], [0.32, -6], [0.42, 16, 'b'], [1.6, 12], [1.95, 16], [2.7, 16], [3.1, -6]), y: K([0, 0], [0.32, 0], [0.42, -2, 'b'], [1.6, -1.4], [3.1, 0]) },
      mouth: { m: K([0, 1], [0.32, 1], [0.42, 3], [1.6, 3], [1.9, 2], [2.1, 0.6], [2.7, 0.6], [3.1, 1]) },
      // startle: both hands come up; then an "oops" shrug with open palms
      armB: { r: K([0, 6], [0.32, 6], [0.46, 52, 'b'], [1.6, 40], [1.95, 24], [2.7, 24], [3.1, 6]) },
      foreB: { r: K([0, 6], [0.32, 6], [0.46, 96, 'b'], [1.6, 84], [1.95, 104], [2.7, 104], [3.1, 6]) },
      armF: { r: K([0, -6], [0.32, -6], [0.46, 8, 'b'], [1.6, 4], [1.95, -26], [2.7, -26], [3.1, -6]) },
      foreF: { r: K([0, -4], [0.32, -4], [0.46, -118, 'b'], [1.6, -110], [1.95, -100], [2.7, -100], [3.1, -4]) },
    },
  });

  const root = g({},
    ground(40, 360),
    bush(78, 30, 18, { lobes: 4, seed: 11 }),
    dome(104, 30, 30),
    slab(103, 141.5, 36, 10, 'rock2', 0.6),
    slab(101.5, 131.6, 12, 10, 'rock', -0.8),
    pebble(58, 3.4), pebble(128, 2.6),
    bush(302, 34, 22, { lobes: 5, seed: 14, side: 1 }),
    monolith(338, 30, 56, { lean: 2, role: 'rock2' }),
    pebble(276, 3), pebble(356, 3.2),
    leaf,
    bar, dot,
    woman,
  );
  return { name: 'error_mark', loop: L, rest: 2.3, badge: [158, 64, 104], root };
}
