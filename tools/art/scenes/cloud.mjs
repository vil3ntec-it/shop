/**
 *  سرور (ERROR_CLOUD): a little rain cloud hangs over the shop's server cabinet; a technician in a cap holds an umbrella
 *  over it, looks up worried while the drops patter on the canopy, glances at you with a sheepish smile, and looks up
 *  again. The cabinet's red light blinks.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, dome, pebble, bush, person, reach, blinks, rrect, circle, smooth, line, repeat } from '../kit.mjs';

export function cloud() {
  const L = 4;
  const [ub1, ub2] = reach(-7.5, -60, -24, -57, 'down'); // umbrella hand, reaching out over the cabinet
  const [hh1, hh2] = reach(7.5, -60, 10.5, -33, 'back'); // hand on hip
  const umbUp = (a1, a2, tilt) => 180 + tilt - (a1 + a2);

  // umbrella in the hand frame: +y runs up the shaft from the fist
  const canopyY = 31 + 31;
  const umbrella = g({},
    stroke('grey', 1.3, line([[0, 27.5], [0, canopyY + 2]])),
    stroke('navy2', 1.6, `M0 27.5C0 24.6 -3.4 24.2 -3.8 26.4`),
    fill('red', smooth([[-24, canopyY], [-21, canopyY + 9], [-10, canopyY + 15.6], [0, canopyY + 17], [10, canopyY + 15.6], [21, canopyY + 9], [24, canopyY],
      [18, canopyY + 2.4, 1], [12, canopyY], [6, canopyY + 2.4, 1], [0, canopyY], [-6, canopyY + 2.4, 1], [-12, canopyY], [-18, canopyY + 2.4, 1]], { t: 0.8 })),
    fill('red2', smooth([[-6, canopyY + 2.4, 1], [-4, canopyY + 10], [0, canopyY + 17], [4, canopyY + 10], [6, canopyY + 2.4, 1], [0, canopyY]], { t: 0.8 })),
    fill('grey', circle(0, canopyY + 17.6, 1.3)),
  );

  const tech = person({
    name: 'p', x: 186, facing: 'right', shirt: 'mustard', vest: 'navy', pants: 'navy', hair: 'hairD', cap: 'teal', sleeve: 15,
    armBFront: true,
    hold: { handB: umbrella },
    anim: {
      body: { sy: Wv(0.012, 2) },
      torso: { r: K([0, -2], [1.9, -2], [2.3, 1], [3.2, 1], [3.6, -2]) },
      head: { r: K([0, -9], [1.9, -9], [2.25, 2, 'b'], [3.2, 2], [3.55, -9]) },
      look: { x: K([0, -0.6], [1.9, -0.6], [2.15, 0.5], [3.2, 0.5], [3.45, -0.6]), y: K([0, -1.4], [1.9, -1.4], [2.15, 0], [3.2, 0], [3.45, -1.4]) },
      eyes: { sy: blinks(1.1, 3.05) },
      browL: { r: K([0, -16], [1.9, -16], [2.2, -8], [3.2, -8], [3.5, -16]), y: K([0, -0.6], [2.2, 0], [3.2, 0], [3.5, -0.6]) },
      browR: { r: K([0, 16], [1.9, 16], [2.2, 8], [3.2, 8], [3.5, 16]), y: K([0, -0.6], [2.2, 0], [3.2, 0], [3.5, -0.6]) },
      mouth: { m: K([0, 2], [1.9, 2], [2.25, 0.35], [3.2, 0.35], [3.5, 2]) },
      armB: { r: { k: [[0, ub1, 's']], w: [1.2, 4] } },
      foreB: { r: { k: [[0, ub2, 's']] } },
      handB: { r: { k: [[0, umbUp(ub1, ub2, -30), 's']], w: [2.2, 4, 1.2] } },
      armF: { r: K([0, hh1], [2.0, hh1], [2.3, hh1 - 18, 'b'], [3.1, hh1 - 18], [3.4, hh1]) },
      foreF: { r: K([0, hh2], [2.0, hh2], [2.3, hh2 + 26, 'b'], [3.1, hh2 + 26], [3.4, hh2]) },
    },
  });

  // rain: drops from the cloud onto the canopy (left) and past it (right)
  const drop = (x, top, bottom, period, phase) => g({
    y: repeat(L, period, phase, [[0, 0, 'l'], [period * 0.72, bottom - top, 's']]),
    a: repeat(L, period, phase, [[0, 0, 'l'], [period * 0.1, 1, 's'], [period * 0.62, 1, 'l'], [period * 0.72, 0, 's']]),
  }, fill('sky2', smooth([[x, top - 2.6, 1], [x + 1.1, top], [x, top + 1.3], [x - 1.1, top]], { t: 0.9 })));
  const drops = [
    drop(228, 57, 70, 0.8, 0.0), drop(237, 58, 70, 0.8, 0.27), drop(246, 57, 74, 0.8, 0.53),
    drop(255, 58, 80, 0.8, 0.13), drop(264, 57, 86, 0.8, 0.4), drop(272, 58, 176, 0.8, 0.66),
  ];
  // splashes where drops meet the canopy
  const splash = (x, y, phase) => g({ a: repeat(L, 0.8, phase, [[0, 0, 'l'], [0.04, 1, 'l'], [0.2, 0, 's']]), sy: repeat(L, 0.8, phase, [[0, 0.4, 'l'], [0.2, 1.2, 's']]), px: x, py: y },
    stroke('sky2', 0.8, line([[x - 2.6, y - 0.6], [x - 3.8, y - 2.4]])), stroke('sky2', 0.8, line([[x + 2.6, y - 0.6], [x + 3.8, y - 2.4]])));

  const cloudG = g({ px: 250, py: 44, x: { k: [[0, -4, 's']] }, y: Wv(1.4, 1, 0, { k: [[0, -4, 's']] }), sx: Wv(0.025, 2, 1.3) },
    fill('rock2', circle(232, 50, 9.5)), fill('rock2', circle(247, 42, 13)), fill('rock2', circle(263, 47, 10)), fill('rock2', circle(274, 52, 7)),
    fill('rock2', rrect(224, 47, 56, 12.5, 6.2)),
  );

  const led = (x, y, role, period, phase) => g({ a: repeat(L, period, phase, [[0, 1, 's'], [period / 2, 0.25, 's']]) }, fill(role, circle(x, y, 1.5)));
  const X = 222, T = 124;
  const server = g({},
    fill('navy2', rrect(X + 4, 172, 5, 5, 1)), fill('navy2', rrect(X + 29, 172, 5, 5, 1)),
    fill('navy', rrect(X, T, 38, 50, 4.5)),
    ...[0, 1, 2].flatMap((i) => [
      fill('navy2', rrect(X + 4.5, T + 6 + i * 13.4, 29, 10, 2.2)),
      stroke('navy', 1, line([[X + 16, T + 9.6 + i * 13.4], [X + 29, T + 9.6 + i * 13.4]])),
      stroke('navy', 1, line([[X + 16, T + 12.6 + i * 13.4], [X + 25, T + 12.6 + i * 13.4]])),
    ]),
    led(X + 10, T + 11, 'red', 0.5, 0), fill('leaf2', circle(X + 10, T + 24.4, 1.5)), led(X + 10, T + 37.8, 'mustard', 2, 0.6),
  );

  const root = g({},
    ground(40, 360),
    monolith(80, 46, 92, { lean: -2 }),
    bush(104, 26, 15, { lobes: 4, seed: 3, side: 1 }),
    pebble(60, 3.6), pebble(122, 2.6),
    dome(318, 40, 26, 'rock2'),
    bush(298, 22, 13, { lobes: 3, seed: 6 }),
    bush(340, 26, 17, { lobes: 4, seed: 8, side: 1 }),
    pebble(286, 3), pebble(358, 3.4),
    server,
    tech,
    ...drops,
    splash(237, 70.5, 0.27 + 0.576), splash(255, 80.5, 0.13 + 0.576),
    cloudG,
  );
  return { name: 'error_cloud', loop: L, rest: 0.3, badge: [170, 34, 94], root };
}
