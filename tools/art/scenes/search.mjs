/**
 *  جست‌وجو (SEARCH): nothing found — a young man bends over with a big magnifying glass and sweeps it along the ground,
 *  then straightens up, looks at you through the lens (one huge eye) and shrugs; the bush beside him rustles.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, dome, slab, pebble, bush, person, reach, blinks, circle, line, capsule } from '../kit.mjs';

export function search() {
  const L = 6;
  const [m1, m2] = reach(7.5, -60, -8, -29, 'down'); // magnifier low, in front (bent over)
  const [u1, u2] = reach(7.5, -60, -13, -74, 'down'); // magnifier up at the face
  const lens = g({},
    fill('kraft2', capsule(0, 26, 0, 37.4, 1.3, 1.5)),
    fill('sky', circle(0, 44.8, 7.6)),
    fill('white', circle(-2.4, 42.4, 2.2)),
    stroke('navy', 2.2, circle(0, 44.8, 7.6)),
  );
  const seeker = person({
    name: 'q', x: 182, facing: 'right', shirt: 'plum', shirt2: 'plum2', pants: 'navy', hair: 'hair', hairStyle: 'short', sleeve: 13,
    hold: { handF: lens },
    anim: {
      body: { sy: Wv(0.012, 3) },
      torso: { r: K([0, -30], [2.6, -30], [3.1, 0, 'o'], [4.8, 0], [5.4, -30]) },
      head: { r: K([0, -14], [2.6, -14], [3.1, 4, 'b'], [4.8, 4], [5.4, -14]) },
      look: { x: K([0, -1.4], [2.7, -1.4], [3.0, 0.4], [4.7, 0.4], [5.0, -1.4]), y: K([0, 1.6], [2.7, 1.6], [3.0, 0], [4.7, 0], [5.0, 1.6]) },
      eyes: { sy: blinks(1.3, 4.1) },
      browL: { r: K([0, 10], [2.7, 10], [2.95, -14, 'b'], [4.6, -14], [5.0, 10]), y: K([0, 0], [2.7, 0], [2.95, -1.6], [4.6, -1.6], [5.0, 0]) },
      browR: { r: K([0, -10], [2.7, -10], [2.95, 14, 'b'], [4.6, 14], [5.0, -10]), y: K([0, 0], [2.7, 0], [2.95, -1.6], [4.6, -1.6], [5.0, 0]) },
      mouth: { m: K([0, 1], [2.8, 1], [3.0, 2], [4.6, 2], [5.0, 1]) },
      // sweep the glass along the ground, then lift it to the face
      armF: { r: K([0, m1 - 8], [0.7, m1 + 10], [1.4, m1 - 10], [2.1, m1 + 8], [2.6, m1], [3.1, u1, 'o'], [4.7, u1], [5.3, m1 - 8]) },
      foreF: { r: K([0, m2], [2.6, m2], [3.1, u2, 'o'], [4.7, u2], [5.3, m2]) },
      handF: { r: K([0, 40 - m1 - m2], [2.6, 40 - m1 - m2], [2.85, 110 - m1 - m2], [3.1, 168 - u1 - u2, 'o'], [4.7, 168 - u1 - u2], [4.95, 110 - m1 - m2], [5.3, 40 - m1 - m2]) },
      armB: { r: K([0, 18], [2.6, 18], [3.1, 30, 'b'], [4.7, 30], [5.3, 18]) },
      foreB: { r: K([0, 24], [2.6, 24], [3.1, 100, 'b'], [4.7, 100], [5.3, 24]) },
      legF: { r: K([0, 3], [2.6, 3], [3.1, 1], [4.8, 1], [5.4, 3]) },
      legB: { r: K([0, -3], [2.6, -3], [3.1, -1], [4.8, -1], [5.4, -3]) },
    },
  });
  // the bush rustles when the glass sweeps past
  const rustle = { px: 250, py: GROUND, r: K([0, 0], [1.2, 0], [1.3, 3], [1.4, -3], [1.5, 2.4], [1.6, -1.6], [1.75, 0]) };

  const root = g({},
    ground(40, 360),
    bush(70, 28, 17, { lobes: 4, seed: 71 }),
    dome(98, 34, 30),
    slab(97, 141.2, 30, 9, 'rock2', 0.5),
    pebble(126, 2.8),
    g(rustle, bush(250, 34, 21, { lobes: 5, seed: 73, side: 1 })),
    monolith(318, 50, 100, { lean: 3 }),
    bush(350, 20, 12, { lobes: 3, seed: 75, front: null }),
    pebble(286, 3.4), pebble(232, 2.4),
    seeker,
  );
  return { name: 'search', loop: L, rest: 3.8, badge: [150, 64, 104], root };
}
