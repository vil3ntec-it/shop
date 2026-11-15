/**
 *  بی اینترنت (ERROR_CABLE): a shopkeeper in an apron holds the unplugged cable's plug up to his face, frowns at it,
 *  blows into it, looks at you hopefully, then scratches his head. The cable runs from behind the big stone.
 */
import { GROUND, K, Wv, g, fill, stroke, rope, ground, monolith, dome, slab, pebble, bush, person, reach, blinks, rrect, line } from '../kit.mjs';

export function offline() {
  const L = 6;
  // poses (local frame of the person: faces −x, mirrored on screen to face the stone)
  const [hb1, hb2] = reach(-7.5, -60, -20, -65, 'down');
  const [hc1, hc2] = reach(-7.5, -60, -18, -68.5, 'down'); // up to the mouth, blowing
  const [fa1, fa2] = [-5, 7];
  const [fs1, fs2] = reach(7.5, -60, 12.5, -87, 'back'); // scratching the back of the head
  const plugUp = (a1, a2, tilt = 0) => 180 + tilt - (a1 + a2);

  const plug = g({},
    fill('line', rrect(-2.8, 32.2, 5.6, 8.6, 1.8)),
    fill('line', rrect(-2.2, 40, 4.4, 1.6, 0.6)),
    fill('grey', rrect(-1.9, 41.2, 1.1, 3.6, 0.4)),
    fill('grey', rrect(0.8, 41.2, 1.1, 3.6, 0.4)),
  );
  // puffs of air while he blows into the plug (hand frame: toward the face is −y… drawn near the prongs)
  const puff = (dx, dy, delay) => g({ a: K([0, 0, 's'], [1.7 + delay, 0, 'o'], [1.85 + delay, 1, 'l'], [2.35 + delay, 0, 'l']), y: K([0, 0, 's'], [1.7 + delay, 0, 'o'], [2.35 + delay, 4, 'l']) },
    stroke('ground', 1, line([[dx, 45 + dy], [dx, 47.6 + dy]])),
  );

  const t = { hold: 0, look: 1.15, blow: 1.65, hope: 2.55, scratch: 3.2, back: 5.0 };
  const shop = person({
    name: 'k', x: 200, facing: 'right', shirt: 'teal', apron: 'cream', pants: 'navy', mustache: true, hairStyle: 'short', hair: 'hairD',
    sleeve: 11, armBFront: true,
    hold: { handB: plug, handBTop: g({}, puff(-1.4, 0, 0), puff(1.4, 0.6, 0.12)) },
    anim: {
      body: { sy: Wv(0.012, 2) },
      torso: { r: K([0, 1.5], [t.blow, 1.5], [t.blow + 0.3, 3.2], [t.hope, 0.5, 'o'], [t.scratch + 0.4, -1.5], [t.back, -1.5], [t.back + 0.6, 1.5]) },
      head: { r: K([0, 5], [t.look, 5], [t.blow, 7, 'o'], [t.hope, -3, 'b'], [t.scratch + 0.3, 9], [t.back, 9], [t.back + 0.6, 5]) },
      look: { x: K([0, -1.3], [t.hope - 0.1, -1.3], [t.hope + 0.1, 0.4, 'o'], [t.scratch + 0.6, 0.4], [t.scratch + 0.8, -0.6], [t.back + 0.5, -1.3]) },
      eyes: { sy: blinks(1.0, 3.95) },
      browL: { r: K([0, 12], [t.hope - 0.1, 12], [t.hope + 0.15, -14, 'b'], [t.scratch + 0.4, -6], [t.back, 10]), y: K([0, 0.6], [t.hope - 0.1, 0.6], [t.hope + 0.15, -1.6, 'b'], [t.back, 0.4]) },
      browR: { r: K([0, -14], [t.hope - 0.1, -14], [t.hope + 0.15, 10, 'b'], [t.scratch + 0.4, 18], [t.back, -12]), y: K([0, 0.6], [t.hope - 0.1, 0.6], [t.hope + 0.15, -1.8, 'b'], [t.scratch + 0.4, -0.4], [t.back, 0.6]) },
      mouth: { m: K([0, 1], [t.blow, 1], [t.blow + 0.12, 3], [t.hope - 0.1, 3], [t.hope + 0.1, 0], [t.scratch + 0.3, 2], [t.back, 2], [t.back + 0.4, 1]) },
      armB: { r: K([0, hb1], [t.blow, hb1], [t.blow + 0.25, hc1], [t.hope - 0.05, hc1], [t.hope + 0.3, hb1 - 6, 'b'], [t.back, hb1 - 4], [t.back + 0.5, hb1]) },
      foreB: { r: K([0, hb2], [t.blow, hb2], [t.blow + 0.25, hc2], [t.hope - 0.05, hc2], [t.hope + 0.3, hb2 + 8, 'b'], [t.back, hb2 + 6], [t.back + 0.5, hb2]) },
      handB: { r: K([0, plugUp(hb1, hb2, -18)], [0.55, plugUp(hb1, hb2, 14)], [t.look - 0.1, plugUp(hb1, hb2, -6)], [t.blow, plugUp(hb1, hb2, -6)], [t.blow + 0.25, plugUp(hc1, hc2, -8)], [t.hope - 0.05, plugUp(hc1, hc2, -8)], [t.hope + 0.3, plugUp(hb1 - 6, hb2 + 8, 6), 'b'], [t.back, plugUp(hb1 - 4, hb2 + 6, 2)], [t.back + 0.5, plugUp(hb1, hb2, -18)]) },
      // the arm folds first (elbow leads), then the hand rises to the back of the head
      armF: { r: K([0, fa1], [t.scratch, fa1], [t.scratch + 0.18, -22], [t.scratch + 0.34, fs1 * 0.6], [t.scratch + 0.5, fs1, 'o'], [t.back - 0.1, fs1], [t.back + 0.12, fs1 * 0.6], [t.back + 0.3, -22], [t.back + 0.5, fa1, 'o']) },
      foreF: { r: K([0, fa2], [t.scratch, fa2], [t.scratch + 0.16, -120, 'o'], [t.scratch + 0.34, -125], [t.scratch + 0.5, fs2, 'o'], [t.back - 0.1, fs2], [t.back + 0.12, -125], [t.back + 0.32, -120], [t.back + 0.5, fa2, 'o']) },
      handF: { r: K([0, 0], [t.scratch + 0.45, 0], [t.scratch + 0.6, -14], [t.scratch + 0.75, 10], [t.scratch + 0.9, -14], [t.scratch + 1.05, 10], [t.scratch + 1.2, -12], [t.scratch + 1.35, 8], [t.scratch + 1.5, 0]) },
    },
  });

  const root = g({},
    ground(40, 360),
    // left: cairn with a bush and pebbles
    bush(84, 30, 19, { lobes: 4, seed: 1 }),
    dome(110, 34, 36),
    slab(109, 135.5, 48, 12.5, 'rock2', 1),
    slab(106.5, 123.5, 16, 12, 'rock', -1),
    slab(108, 114.6, 8.5, 7, 'rock2', 0.5),
    pebble(62, 4), pebble(137, 2.8),
    // cable lying on the ground, coming from behind the big stone
    stroke('line', 1.9, line([[305, GROUND + 0.1], [262, GROUND + 0.1]])),
    // right: the tall stone, bushes, pebbles
    monolith(308, 56, 110, { lean: 3 }),
    bush(279, 24, 14, { lobes: 4, seed: 4 }),
    bush(343, 16, 10, { lobes: 3, seed: 2, front: null }),
    pebble(287, 3.2), pebble(332, 4.2),
    shop,
    rope('line', 1.9, ['k.handB', 0, 29.4, 0, 22], ['@', 262, GROUND + 0.1, -28, 0]),
  );
  return { name: 'error_cable', loop: L, rest: 0.4, badge: [166, 74, 64], root };
}
