/**
 *  فروش (SALES): no sales yet — the cashier waits behind the counter, chin in her hand, drumming her fingers next to the
 *  register; her eyes go to one side and the other, a slow blink, a little sigh; the receipt paper curls in the breeze.
 */
import { GROUND, K, Wv, g, fill, ground, monolith, pebble, bush, person, reach, blinks, smooth, rrect, line, circle, ellipse, repeat, poly } from '../kit.mjs';

export function sales() {
  const L = 4;
  const b = GROUND + 0.8;
  const T = 133; // counter top
  const top = (T - 176) / 1.1;
  const [cb1, cb2] = reach(-7.5, -60, -19, top - 2.5, 'fwd'); // one hand on the counter, drumming
  const [tf1, tf2] = [-4, 8]; // the other hangs behind the counter

  const cashier = person({
    name: 'c', x: 194, scarf: 'mustard', scarfDrape: 'mustard', tunic: 'red', shirt: 'cream', shirt2: 'red2', sleeveB: 'cream2', pattern: 'spots', pants: 'navy', sleeve: 31,
    armBFront: true,
    anim: {
      body: { y: K([0, 0], [2.9, 0], [3.2, 1.2, 'o'], [3.6, 0]), sy: Wv(0.01, 2) },
      torso: { r: K([0, -3], [0.9, -3], [1.2, 2], [2.0, 2], [2.3, -5], [3.2, -5], [3.6, -3]) },
      head: { r: K([0, -9], [0.9, -9], [1.2, 0, 'b'], [2.0, 0], [2.3, -13], [3.2, -13], [3.6, -9]) },
      look: { x: K([0, -1.3], [0.8, -1.3], [1.05, 1.0, 'o'], [1.9, 1.0], [2.15, -0.2], [2.9, -0.2], [3.2, -1.3]), y: K([0, 0.3], [4, 0.3]) },
      eyes: { sy: { k: [[0, 1, 'l'], [2.25, 1, 'l'], [2.45, 0.08, 'l'], [2.7, 0.08, 'l'], [2.9, 1, 'l']] } },
      browL: { r: K([0, -4], [2.2, -4], [2.4, -10], [3.0, -10], [3.4, -4]) },
      browR: { r: K([0, 4], [2.2, 4], [2.4, 10], [3.0, 10], [3.4, 4]) },
      mouth: { m: K([0, 1], [2.9, 1], [3.05, 3], [3.4, 3], [3.7, 1]) },
      armB: { r: { k: [[0, cb1, 's']] } },
      foreB: { r: { k: [[0, cb2, 's']] } },
      handB: { r: repeat(L, 0.5, 0.25, [[0, 0, 'o'], [0.12, 22, 'i'], [0.24, 0, 's']]) },
      armF: { r: { k: [[0, tf1, 's']] } },
      foreF: { r: { k: [[0, tf2, 's']] } },
      handF: { r: K([0, 0]) },
    },
  });

  const register = g({},
    fill('navy', smooth([[226, T, 1], [229, T - 16, 1], [256, T - 16, 1], [259, T, 1]], { t: 0 })),
    fill('navy2', rrect(231, T - 13, 22, 3.2, 1.2)),
    ...[0, 1, 2].flatMap((r) => [0, 1, 2, 3].map((c) => fill('grey', rrect(232.4 + c * 5.2, T - 8.4 + r * 2.8, 3.4, 1.7, 0.7)))),
    fill('navy2', rrect(236, T - 23, 4, 8, 1)),
    fill('navy', rrect(229, T - 29, 14, 8.4, 2)),
    fill('mustard', rrect(230.6, T - 27.4, 10.8, 5.2, 1.2)),
    // the display waits for the first sale: a blinking cursor
    g({ a: repeat(L, 1, 0, [[0, 1, 's'], [0.5, 0, 's']]) }, fill('navy', rrect(232.4, T - 25.8, 3.4, 2.2, 0.6))),
  );
  const counter = g({},
    fill('rock', rrect(148, T + 2, 116, b - T - 2, 0)),
    fill('rock2', rrect(144, T - 3, 124, 6, 3)),
    fill('rock2', rrect(158, T + 14, 96, 2.4, 1.2)),
    // a small plant in a pot at the end of the counter
    fill('orange2', poly([[155, T - 3], [167, T - 3], [165.4, T - 12], [156.6, T - 12]])),
    fill('orange', rrect(155, T - 14, 12, 3, 1.2)),
    fill('leaf', smooth([[161, T - 14, 1], [155, T - 21], [156.6, T - 27], [160.6, T - 22]], { t: 0.9 })),
    fill('leaf2', smooth([[161, T - 14, 1], [163.4, T - 25], [168, T - 29], [166.8, T - 19]], { t: 0.9 })),
    fill('leaf', smooth([[161, T - 14, 1], [158, T - 30], [161.6, T - 34], [163, T - 25]], { t: 0.9 })),
  );

  // a fly buzzes a lazy figure-eight beside her — nothing else is happening
  const fly = g({ x: Wv(13, 2, 0, { k: [[0, 222, 's']] }), y: Wv(5, 4, 0, { k: [[0, 74, 's']] }) },
    g({ sy: repeat(L, 0.1, 0, [[0, 1, 'l'], [0.05, 0.3, 'l']]), px: 0, py: 0 },
      fill('grey', ellipse(-1.4, -1.6, 1.6, 1.1)), fill('grey', ellipse(1.4, -1.6, 1.6, 1.1)),
    ),
    fill('line', ellipse(0, 0, 1.1, 1.5)),
  );

  const root = g({},
    ground(40, 360),
    bush(78, 32, 20, { lobes: 4, seed: 31 }),
    monolith(108, 34, 66, { lean: -1, role: 'rock2' }),
    pebble(56, 3.2), pebble(130, 2.6),
    monolith(318, 52, 104, { lean: 3 }),
    bush(292, 24, 14, { lobes: 4, seed: 33 }),
    bush(350, 20, 12, { lobes: 3, seed: 35, front: null }),
    pebble(280, 3),
    g({ clip: `M0 0L400 0L400 ${T + 2}L0 ${T + 2}Z` }, cashier),
    counter,
    register,
    fly,
  );
  return { name: 'sales', loop: L, rest: 0.4, badge: [160, 64, 104], root };
}
