/**
 *  انبار (STOCK): a stock keeper with a clipboard checks the shelves — empty, apart from one lonely little box. He taps
 *  the clipboard with his pen, tilts his head, and a tiny spider lowers itself on a thread from the top shelf.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, pebble, bush, person, reach, blinks, rrect, line, circle, ellipse } from '../kit.mjs';

export function stock() {
  const L = 6;
  const b = GROUND + 0.8;
  // shelving unit: two posts, three shelves
  const SX = 220, SW = 70;
  const shelves = g({},
    fill('rock2', rrect(SX, 84, 4, b - 84, 1.4)), fill('rock2', rrect(SX + SW - 4, 84, 4, b - 84, 1.4)),
    ...[96, 124, 152].map((y) => fill('rock', rrect(SX - 3, y, SW + 6, 4.4, 2.2))),
    fill('rock2', rrect(SX - 4, 82, SW + 8, 5, 2.5)),
    // one lonely box on the middle shelf
    fill('kraft2', rrect(SX + 44, 113, 14, 11, 1)), fill('kraft', rrect(SX + 44, 113, 14, 3, 1)),
  );
  // the spider lowers itself and climbs back
  const drop = K([0, 0], [1.8, 0], [2.6, 18, 'o'], [3.4, 15], [4.0, 17], [5.0, 0, 'io']);
  const spider = g({},
    g({ sy: drop, px: 0, py: 87 }, stroke('ground', 0.6, line([[SX + 22, 87], [SX + 22, 88]]))),
    g({ y: drop },
      fill('line', ellipse(SX + 22, 90, 2.2, 2.4)),
      ...[-1, 1].flatMap((sd) => [0, 1, 2].map((i) => stroke('line', 0.55, line([[SX + 22 + sd * 1.6, 89.2 + i * 1.1], [SX + 22 + sd * 3.6, 87.8 + i * 1.9]])))),
    ),
  );
  // the thread is a line whose length follows the drop: draw it as a 1-unit segment scaled by `drop + 1`
  spider.kids[0].sy = { k: drop.k.map(([t, v, e]) => [t, v + 1, e]) };

  const [cb1, cb2] = reach(-7.5, -60, -14, -54, 'down'); // clipboard at the chest
  const [pf1, pf2] = reach(7.5, -60, -6, -50, 'down'); // pen hand over the clipboard
  const [sf1, sf2] = reach(7.5, -60, 12, -88, 'back'); // scratch head with the pen
  const clipboard = g({}, fill('kraft2', rrect(-6, 28, 12, 15, 1.4)), fill('white', rrect(-4.6, 30.6, 9.2, 11.2, 0.8)), fill('grey', rrect(-2.4, 27.2, 4.8, 2.6, 1)),
    stroke('rock2', 0.7, line([[-3, 33.4], [3, 33.4]])), stroke('rock2', 0.7, line([[-3, 35.8], [2, 35.8]])), stroke('rock2', 0.7, line([[-3, 38.2], [3, 38.2]])));
  const pen = g({}, fill('navy2', rrect(-0.7, 25, 1.4, 11, 0.7)), fill('mustard', rrect(-0.7, 25, 1.4, 2.4, 0.7)));
  const keeper = person({
    name: 'k', x: 186, facing: 'right', shirt: 'navy', shirt2: 'navy2', pants: 'kraft', cap: 'mustard', cap2: 'mustard2', hair: 'hair', beard: true,
    sleeve: 13, armBFront: true,
    hold: { handB: clipboard, handF: pen },
    anim: {
      body: { sy: Wv(0.012, 3) },
      torso: { r: K([0, 0], [2.6, 0], [2.9, -3], [4.4, -3], [4.8, 0]) },
      head: { r: K([0, -6], [1.6, -6], [1.9, -12], [2.6, -12], [2.9, 6, 'b'], [4.4, 6], [4.8, -6]) },
      look: { x: K([0, -1.4], [2.6, -1.4], [2.85, 0.4], [4.4, 0.4], [4.7, -1.4]), y: K([0, -0.8], [1.6, -0.8], [1.9, -1.4], [2.6, -1.4], [2.85, 0], [4.7, -0.8]) },
      eyes: { sy: blinks(1.2, 3.6, 5.5) },
      browL: { r: K([0, 6], [2.6, 6], [2.85, -12], [4.4, -12], [4.8, 6]) },
      browR: { r: K([0, -14], [2.6, -14], [2.85, 16], [4.4, 16], [4.8, -14]), y: K([0, 0], [2.6, 0], [2.85, -1.4], [4.4, -1.4], [4.8, 0]) },
      mouth: { m: K([0, 1], [2.6, 1], [2.85, 2], [4.4, 2], [4.8, 1]) },
      armB: { r: { k: [[0, cb1, 's']] } }, foreB: { r: { k: [[0, cb2, 's']] } }, handB: { r: { k: [[0, 160, 's']] } },
      armF: { r: K([0, pf1], [2.7, pf1], [3.05, sf1, 'o'], [4.3, sf1], [4.7, pf1]) },
      foreF: { r: K([0, pf2], [2.7, pf2], [3.05, sf2, 'o'], [4.3, sf2], [4.7, pf2]) },
      handF: { r: K([0, 150], [0.3, 150], [0.4, 135], [0.5, 150], [0.6, 135], [0.7, 150], [1.2, 150], [1.3, 135], [1.4, 150], [2.7, 150], [3.05, 200], [3.3, 180], [3.5, 205], [3.7, 182], [3.9, 205], [4.3, 200], [4.7, 150]) },
    },
  });

  const root = g({},
    ground(40, 360),
    monolith(84, 46, 88, { lean: -2 }),
    bush(112, 28, 17, { lobes: 4, seed: 61, side: 1 }),
    pebble(60, 3.4), pebble(136, 2.6),
    shelves,
    spider,
    bush(318, 30, 19, { lobes: 4, seed: 63, side: 1 }),
    monolith(346, 22, 40, { role: 'rock2' }),
    pebble(300, 3),
    keeper,
  );
  return { name: 'stock', loop: L, rest: 3.6, badge: [150, 64, 104], root };
}
