/**
 *  سفارش‌ها (ORDERS): a courier in a helmet waits beside his delivery scooter, phone in hand: he checks it, taps his
 *  foot, looks down the road one way and the other, and shrugs at you — no orders yet.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, pebble, bush, person, reach, blinks, rrect, line, circle, smooth, repeat } from '../kit.mjs';

export function orders() {
  const L = 4;
  const b = GROUND + 0.8;
  const [pb1, pb2] = reach(-7.5, -60, -12, -62, 'down'); // phone up at the chest
  const phone = g({}, fill('navy2', rrect(-2.6, 30.4, 5.2, 9.4, 1.4)), fill('sky', rrect(-1.8, 31.6, 3.6, 6.6, 0.8)));
  const courier = person({
    name: 'r', x: 214, shirt: 'teal', shirt2: 'teal2', vest: 'orange', pants: 'navy', helmet: 'red', helmet2: 'red2', hair: 'hairD', sleeve: 31,
    armBFront: true,
    hold: { handB: phone },
    anim: {
      body: { sy: Wv(0.012, 2) },
      torso: { r: K([0, -3], [1.6, -3], [1.9, 1], [3.0, 1], [3.3, -3]) },
      head: { r: K([0, -12], [1.5, -12], [1.8, 2], [2.4, 2], [2.6, -2], [3.2, -2], [3.5, -12]) },
      look: { x: K([0, -1.4], [1.5, -1.4], [1.75, -1.6], [2.3, -1.6], [2.5, 1.2], [2.9, 1.2], [3.1, 0.3], [3.4, -1.4]), y: K([0, 1.6], [1.5, 1.6], [1.7, 0], [3.3, 0], [3.5, 1.6]) },
      eyes: { sy: blinks(0.9, 2.95) },
      browL: { r: K([0, 6], [1.5, 6], [1.7, -2], [3.0, -10], [3.4, 6]) },
      browR: { r: K([0, -6], [1.5, -6], [1.7, 2], [3.0, 10], [3.4, -6]) },
      mouth: { m: K([0, 1], [2.9, 1], [3.05, 2], [3.3, 2], [3.5, 1]) },
      armB: { r: K([0, pb1], [2.9, pb1], [3.1, pb1 - 30, 'b'], [3.35, pb1 - 30], [3.6, pb1]) },
      foreB: { r: K([0, pb2], [2.9, pb2], [3.1, pb2 + 20, 'b'], [3.35, pb2 + 20], [3.6, pb2]) },
      handB: { r: { k: [[0, 160, 's']] } },
      armF: { r: K([0, -4], [2.9, -4], [3.1, -28, 'b'], [3.35, -28], [3.6, -4]) },
      foreF: { r: K([0, 6], [2.9, 6], [3.1, -70, 'b'], [3.35, -70], [3.6, 6]) },
      // foot taps while he waits
      legF: { r: K([0, 0]) },
      shinF: { r: K([0, 0]) },
      root: {},
    },
  });
  // tap: the front foot lifts at the toe (rotate the shin a touch)
  courier.kids[1].kids.find((k) => k.name === 'r.legF').kids[0].kids[1].r = repeat(L, 0.5, 0, [[0, 0, 'o'], [0.12, -9, 'i'], [0.25, 0, 's']]);

  // scooter (faces left), parked to the left of the courier
  const wheel = (x) => g({}, fill('navy2', circle(x, b - 7.4, 7.4)), fill('grey', circle(x, b - 7.4, 3)));
  const scooter = g({},
    wheel(130), wheel(172),
    // rear body with the seat
    fill('red', smooth([[146, b - 9, 1], [148, b - 23], [160, b - 30], [180, b - 30], [186, b - 22], [184, b - 10], [178, b - 9, 1]], { t: 0.9 })),
    fill('red2', smooth([[150, b - 13], [178, b - 13], [182, b - 18], [158, b - 20]], { t: 0.9 })),
    fill('navy2', rrect(156, b - 34.6, 24, 5.4, 2.7)),
    // footboard and front shield
    fill('red2', rrect(136, b - 12, 22, 4, 2)),
    fill('red', smooth([[128, b - 14], [132, b - 38], [140, b - 44], [143, b - 40], [140, b - 30], [142, b - 12, 1]], { t: 0.9 })),
    stroke('navy2', 2, line([[138, b - 44], [134, b - 53]])),
    stroke('navy2', 2.2, line([[128, b - 54], [140, b - 52]])),
    fill('mustard', circle(132.6, b - 44.6, 2.6)),
    // delivery box on the back
    fill('orange', rrect(160, b - 56, 26, 21, 2.4)),
    fill('orange2', rrect(160, b - 56, 26, 4.2, [2.4, 2.4, 0, 0])),
    fill('cream', rrect(166, b - 47.4, 14, 6.6, 1.6)),
    fill('orange2', circle(170, b - 44.1, 1.6)), fill('orange2', rrect(173, b - 45, 5, 1.8, 0.9)),
  );

  const root = g({},
    ground(40, 360),
    bush(70, 30, 18, { lobes: 4, seed: 51 }),
    pebble(98, 3),
    monolith(306, 58, 112, { lean: 3 }),
    bush(276, 26, 16, { lobes: 4, seed: 53 }),
    bush(346, 22, 13, { lobes: 3, seed: 55, front: null }),
    pebble(260, 3.2), pebble(334, 2.6),
    scooter,
    courier,
  );
  return { name: 'orders', loop: L, rest: 3.25, badge: [162, 66, 104], root };
}
