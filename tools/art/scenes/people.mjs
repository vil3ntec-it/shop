/**
 *  قرض‌داران / مشتریان (PEOPLE): an old shopkeeper with glasses sits on a stone with his ledger open — the pages are
 *  blank. He turns a page (still blank), peers over his glasses at you with a kind, amused smile, and reads on.
 */
import { GROUND, K, Wv, g, fill, stroke, ground, monolith, dome, pebble, bush, person, reach, blinks, rrect, line, smooth } from '../kit.mjs';

export function people() {
  const L = 6;
  // ledger held in both hands in front of the chest (in the torso frame of the seated person)
  const bx = -21, by = -45;
  const [lb1, lb2] = reach(-7.5, -60, bx - 6, by + 4, 'down');
  const [lf1, lf2] = reach(7.5, -60, bx + 4, by + 5, 'down');
  const page = (side, role = 'white') => fill(role, side < 0
    ? smooth([[bx, by - 1, 1], [bx - 13, by - 2.6], [bx - 13.6, by + 9, 1], [bx, by + 10.6, 1]], { t: 0.3 })
    : smooth([[bx, by - 1, 1], [bx + 13, by - 2.6], [bx + 13.6, by + 9, 1], [bx, by + 10.6, 1]], { t: 0.3 }));
  const lines = (side) => [0, 1, 2, 3].map((i) => stroke('rock2', 0.6, line([[bx + side * 2.4, by + 2 + i * 2.2], [bx + side * 11.4, by + 1.4 + i * 2.2]])));
  const flip = K([0, 1, 's'], [1.4, 1, 'io'], [2.1, -1, 's'], [2.2, 1, 's']);
  const ledger = g({ name: 'ledger', px: bx, py: by + 8, r: K([0, -10], [2.4, -10], [2.8, -4], [4.6, -4], [5.2, -10]), sx: { k: [[0, 1.3, 's']] }, sy: { k: [[0, 1.3, 's']] } },
    fill('red', smooth([[bx, by + 0.4, 1], [bx - 14.8, by - 1.8], [bx - 15.4, by + 11.2, 1], [bx, by + 12.6, 1], [bx + 15.4, by + 11.2, 1], [bx + 14.8, by - 1.8]], { t: 0.3 })),
    page(-1), page(1), ...lines(-1), ...lines(1),
    // the turning page: a right page folding over the spine
    g({ px: bx, py: by, sx: flip, a: K([0, 0, 's'], [1.39, 0, 's'], [1.4, 1, 's'], [2.1, 1, 's'], [2.11, 0, 's']) }, page(1, 'cream'), ...lines(1)),
    stroke('red2', 0.8, line([[bx, by - 0.6], [bx, by + 10.6]])),
  );
  const elder = person({
    name: 'e', x: 214, sit: true, seat: null, shirt: 'cream', shirt2: 'cream2', vest: 'navy', pants: 'navy', hairStyle: 'bald', hair: 'grey',
    beard: true, beardCol: 'grey', glasses: true, browCol: 'grey', pencil: true, sleeve: 31, armBFront: true,
    anim: {
      body: { sy: Wv(0.01, 2) },
      torso: { r: K([0, -4], [2.4, -4], [2.8, 1], [4.6, 1], [5.2, -4]) },
      head: { r: K([0, -10], [1.2, -10], [1.5, -13], [2.4, -10], [2.8, -2, 'b'], [4.6, -2], [5.2, -10]) },
      look: { x: K([0, -1.3], [2.5, -1.3], [2.8, 0.3], [4.6, 0.3], [5.0, -1.3]), y: K([0, 1.4], [2.5, 1.4], [2.8, -0.8], [4.6, -0.8], [5.0, 1.4]) },
      eyes: { sy: blinks(1.0, 3.7) },
      browL: { r: K([0, 4], [2.5, 4], [2.75, -14, 'b'], [4.4, -14], [5.0, 4]), y: K([0, 0], [2.5, 0], [2.75, -1.6, 'b'], [4.4, -1.6], [5.0, 0]) },
      browR: { r: K([0, -4], [2.5, -4], [2.75, 14, 'b'], [4.4, 14], [5.0, -4]), y: K([0, 0], [2.5, 0], [2.75, -1.6, 'b'], [4.4, -1.6], [5.0, 0]) },
      mouth: { m: K([0, 1], [2.6, 1], [2.85, 0], [4.6, 0], [5.0, 1]) },
      armB: { r: { k: [[0, lb1, 's']] } }, foreB: { r: { k: [[0, lb2, 's']] } }, handB: { r: { k: [[0, 140, 's']] } },
      armF: { r: K([0, lf1], [1.2, lf1], [1.5, lf1 + 8], [2.1, lf1 + 6], [2.3, lf1]) }, foreF: { r: { k: [[0, lf2, 's']] } }, handF: { r: { k: [[0, -150, 's']] } },
    },
  });
  // place the ledger inside the torso, in front of the far arm and behind the near hand
  const torso = findGroup(elder, 'e.torso');
  const armFIdx = torso.kids.findIndex((k) => k.name === 'e.armF');
  torso.kids.splice(armFIdx, 0, ledger);

  const root = g({},
    ground(40, 360),
    monolith(84, 44, 86, { lean: -2 }),
    bush(112, 30, 18, { lobes: 4, seed: 41, side: 1 }),
    pebble(62, 3.2),
    // the stone seat
    dome(212, 30, 21, 'rock2'),
    monolith(318, 34, 60, { lean: 2, role: 'rock2' }),
    bush(296, 28, 17, { lobes: 4, seed: 43 }),
    bush(348, 20, 12, { lobes: 3, seed: 45, front: null }),
    pebble(270, 3), pebble(338, 2.6),
    elder,
  );
  return { name: 'people', loop: L, rest: 3.4, badge: [158, 70, 104], root };
}

function findGroup(n, name) {
  if (n.name === name) return n;
  for (const k of n.kids || []) { const r = findGroup(k, name); if (r) return r; }
  return null;
}
