/**
 *  کالا (PRODUCTS): a young shopkeeper lifts the flap of a big cardboard box and peers in — nothing but a moth, which
 *  flutters out past his nose. He turns to you with an open palm: "empty!", then looks back in.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, pebble, bush, person, reach, blinks, smooth, rrect, line, ellipse, repeat, poly } from '../kit.mjs';

export function products() {
  const L = 6;
  const b = GROUND + 0.8;
  // the open box: back wall (dark inside), front face, side flaps, a front flap hanging open
  const X0 = 232, X1 = 282, top = 138;
  const flapLift = K([0, 0], [0.5, 0], [0.9, 24, 'b'], [3.0, 24], [3.35, 0, 'o']);
  const box = g({},
    // inside: back wall and the dark bottom seen through the opening
    fill('kraft2', poly([[X0 + 4, top - 8], [X1 - 4, top - 8], [X1, top], [X0, top]])),
    fill('shoe', poly([[X0 + 6, top - 3.6], [X1 - 6, top - 3.6], [X1 - 3.4, top], [X0 + 3.4, top]])),
    // back flap leaning away, left flap flared out
    fill('kraft', poly([[X0 + 4, top - 8], [X1 - 4, top - 8], [X1 - 7, top - 18], [X0 + 8, top - 17]])),
    fill('kraft', poly([[X1, top], [X1 - 4, top - 8], [X1 + 7, top - 15], [X1 + 12, top - 6]])),
    // front face with tape and a printed label
    fill('kraft2', rrect(X0, top, X1 - X0, b - top, [0.6, 0.6, 1.4, 1.4])),
    fill('kraft', rrect(X0 + 0.8, top + 0.8, X1 - X0 - 1.6, b - top - 1.6, [0.6, 0.6, 1.2, 1.2])),
    fill('kraft2', rrect((X0 + X1) / 2 - 3, top + 0.8, 6, 12, [0, 0, 1.4, 1.4])),
    fill('cream', rrect(X0 + 7, top + 18, 15, 10, 1.4)),
    stroke('kraft2', 1, line([[X0 + 10, top + 21.6], [X0 + 19, top + 21.6]])),
    stroke('kraft2', 1, line([[X0 + 10, top + 24.6], [X0 + 16, top + 24.6]])),
    // near (left) flap: he lifts it to peer in
    g({ name: 'flap', px: X0, py: top, r: flapLift },
      fill('kraft', poly([[X0, top], [X0 + 4, top - 8], [X0 - 7, top - 15], [X0 - 12, top - 6]])),
    ),
  );
  // the moth escapes: path up and away, wings flapping
  const moth = g({ px: 258, py: 132, a: K([0, 0], [1.25, 0], [1.4, 1], [3.8, 1], [4.2, 0]),
    x: K([0, 0, 's'], [1.25, 0, 'l'], [2.2, -12, 'l'], [3.2, 6, 'l'], [4.2, 24]), y: K([0, 0, 's'], [1.25, 0, 'o'], [2.2, -34, 'l'], [3.2, -52, 'l'], [4.2, -74]) },
    g({ px: 258, py: 132, sy: repeat(L, 0.2, 0, [[0, 1, 'io'], [0.1, 0.25, 'io']]) },
      fill('grey', ellipse(255.6, 131, 3, 2.2)), fill('grey', ellipse(260.4, 131, 3, 2.2)),
    ),
    fill('ink', ellipse(258, 132.4, 0.8, 1.8)),
  );
  const [lf1, lf2] = reach(-7.5, -60, -17, -48, 'down'); // hand on the near flap
  const [pp1, pp2] = reach(-7.5, -60, -19, -48, 'down'); // open palm toward the box
  const shop = person({
    name: 's', x: 204, facing: 'right', shirt: 'cream', shirt2: 'red', pattern: 'stripes', sleeveB: 'cream2', apron: 'orange', apron2: 'orange2',
    pants: 'navy', hair: 'hair', hairStyle: 'curly', sleeve: 12,
    anim: {
      body: { sy: Wv(0.012, 3) },
      torso: { r: K([0, -4], [0.5, -4], [0.9, -13], [3.0, -13], [3.4, 0], [4.6, 0], [5.2, -4]) },
      head: { r: K([0, -4], [0.5, -4], [0.9, -14], [1.4, -14], [1.6, 2, 'b'], [2.6, -8], [3.0, -12], [3.45, 4], [4.6, 4], [5.2, -4]) },
      look: { x: K([0, -1.2], [1.4, -1.2], [1.6, -0.4], [2.6, -1.2], [3.3, -1.2], [3.5, 0.5], [4.6, 0.5], [5.0, -1.2]), y: K([0, 0.8], [1.4, 0.8], [1.6, -1.4], [2.4, -1.4], [2.7, 0.8], [3.3, 0.8], [3.5, 0], [4.6, 0], [5.0, 0.8]) },
      eyes: { sy: blinks(2.75, 4.9) },
      browL: { r: K([0, 2], [1.4, 2], [1.55, -18, 'b'], [2.6, -6], [3.4, -12], [4.6, -12], [5.0, 2]), y: K([0, 0], [1.4, 0], [1.55, -2, 'b'], [2.6, 0], [3.4, -1.2], [4.6, -1.2], [5.0, 0]) },
      browR: { r: K([0, -2], [1.4, -2], [1.55, 18, 'b'], [2.6, 6], [3.4, 12], [4.6, 12], [5.0, -2]), y: K([0, 0], [1.4, 0], [1.55, -2, 'b'], [2.6, 0], [3.4, -1.2], [4.6, -1.2], [5.0, 0]) },
      mouth: { m: K([0, 1], [1.4, 1], [1.5, 3], [2.4, 3], [2.7, 1], [3.4, 1.6], [3.6, 0], [4.6, 0], [5.0, 1]) },
      armB: { r: K([0, 6], [0.5, 6], [0.9, lf1], [3.0, lf1], [3.4, pp1, 'o'], [4.6, pp1], [5.2, 6]) },
      foreB: { r: K([0, 6], [0.5, 6], [0.9, lf2], [3.0, lf2], [3.4, pp2 + 20, 'b'], [4.6, pp2 + 20], [5.2, 6]) },
      handB: { r: K([0, 0], [3.0, 0], [3.4, 70], [4.6, 70], [5.2, 0]) },
      armF: { r: K([0, -6], [3.3, -6], [3.6, -10], [4.6, -10], [5.2, -6]) },
      foreF: { r: K([0, 10], [3.3, 10], [3.6, 30], [4.6, 30], [5.2, 10]) },
    },
  });

  const root = g({},
    ground(40, 360),
    // left: a stack of flat boxes and a bush
    bush(70, 28, 17, { lobes: 4, seed: 21 }),
    fill('kraft2', rrect(90, 154, 40, 23, 1.2)), fill('kraft', rrect(90, 154, 40, 4, 1)), stroke('kraft2', 1, line([[100, 164], [112, 164]])),
    fill('kraft2', rrect(96, 140, 30, 15, 1.2)), fill('kraft', rrect(96, 140, 30, 3.6, 1)),
    pebble(140, 2.8), pebble(56, 3.4),
    // right: tall stone and bushes
    monolith(326, 50, 98, { lean: 2 }),
    bush(302, 22, 13, { lobes: 3, seed: 23 }),
    bush(352, 18, 11, { lobes: 3, seed: 25, front: null }),
    pebble(290, 3),
    box,
    shop,
    moth,
  );
  return { name: 'products', loop: L, rest: 3.9, badge: [176, 66, 112], root };
}
