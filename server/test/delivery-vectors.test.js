'use strict';
/**
 * بردارِ مشترکِ ماشینِ وضعیتِ مأموریتِ تحویل (`core/test-vectors/delivery-states-v1.json`).
 *
 *   ۱) فایل همان خروجیِ امروزِ `delivery-states.js` است — ماشین بی بردار عوض نمی‌شود
 *      (آینه‌های کاتلین، `DeliveryStatesVectorsTest.kt`، همین فایل را می‌خوانند).
 *   ۲) فهرست‌های دستیِ همان وضعیت‌ها در SQLِ `delivery.js` و مهاجرتِ ۰۷۷ با بردار یکی‌اند.
 *   ۳) هر «ناهم‌خوانیِ شناخته» ادعای درستی دربارهٔ سرور است: سرور آن گذار را
 *      می‌پذیرد، و «unreachable» واقعاً از وضعیتِ آغازِ همان روش دست‌نیافتنی است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const gen = require('../scripts/delivery-vectors');
const D = require('../src/lib/delivery-states');

const vec = JSON.parse(fs.readFileSync(gen.FIXTURE, 'utf8'));
const read = (rel) => fs.readFileSync(path.join(__dirname, '..', rel), 'utf8');
const sortedSet = (list) => [...new Set(list)].sort();
/** `IN ('a','b',…)` ⇒ ['a','b',…] (مرتب) برای هر فهرستِ ثابت در متن */
const sqlLists = (src) => [...src.matchAll(/IN \(\s*((?:'[a-z_]+',?\s*)+)\)/g)].map((m) => sortedSet(m[1].match(/[a-z_]+/g)));
/** فقط فهرست‌های وضعیتِ مأموریت */
const statusLists = (src) => sqlLists(src).filter((list) => list.includes('requested'));

test('۱) بردار با delivery-states.js امروز یکی است', () => {
  assert.equal(fs.readFileSync(gen.FIXTURE, 'utf8'), gen.expected(),
    'بردارِ تحویل کهنه است: cd server && node scripts/delivery-vectors.js --write');
  const pairs = Object.values(D.TRANSITIONS).reduce((n, list) => n + list.length, 0);
  assert.equal(vec.allowed.length, pairs * D.MODES.length * Object.keys(vec.actors).length);
});

test('۱) پایانی = بی گذار؛ زنده = همهٔ غیرِپایانی؛ رفته ⊂ زنده', () => {
  assert.deepEqual(vec.terminal, ['delivered', 'rejected', 'withdrawn', 'returned']);
  assert.deepEqual(sortedSet([...vec.live, ...vec.terminal]), sortedSet(vec.statuses));
  for (const s of vec.live) assert.ok(!vec.terminal.includes(s), s);
  for (const s of vec.departed) assert.ok(vec.live.includes(s), s);
  //  از وضعیتِ پایانی هیچ‌کس به هیچ‌جا نمی‌رود
  for (const s of vec.terminal) assert.deepEqual(vec.transitions[s], []);
});

test('۱) چه کسی: کارمندِ بی دسترسی و پیکِ دیگر هیچ؛ پیک فقط در پیکِ دکان', () => {
  for (const [mode, from, to, actor, out] of vec.allowed) {
    const row = `${mode} ${from}→${to} ${actor}`;
    if (actor === 'staff_none' || actor === 'courier_other') assert.notEqual(out, 'ok', row);
    if (actor.startsWith('courier') && mode !== 'shop_courier') assert.notEqual(out, 'ok', row);
    if (out === 'ok' && actor.startsWith('courier')) assert.ok(vec.courierTargets.includes(to), row);
  }
  //  پیکِ غیرفعال کارِ تازه نمی‌پذیرد ولی کارِ گرفته را پیش می‌برد (§۲۸.۶)
  const v = (actor, from, to) => vec.allowed.find((r) => r[0] === 'shop_courier' && r[1] === from && r[2] === to && r[3] === actor)[4];
  assert.equal(v('courier_inactive', 'requested', 'assigned'), 'courier_inactive');
  assert.equal(v('courier_inactive', 'arrived', 'delivered'), 'ok');
  assert.equal(v('courier', 'assigned', 'delivered'), 'invalid_transition');
});

test('۲) فهرست‌های دستیِ وضعیت در delivery.js و مهاجرتِ ۰۷۷ همان بردارند', () => {
  const live = statusLists(read('src/lib/delivery.js'));
  assert.ok(live.length >= 5, 'فهرستِ SQLِ وضعیت‌های زنده در delivery.js پیدا نشد');
  for (const list of live) assert.deepEqual(list, sortedSet(vec.live));

  const mig = read('migrations/077_delivery.sql');
  assert.deepEqual(statusLists(mig), [sortedSet(vec.statuses), sortedSet(vec.live)], 'CHECKِ وضعیت و ایندکسِ زنده');
  assert.ok(sqlLists(mig).some((list) => list.join() === sortedSet(vec.modes).join()), 'CHECKِ روش');
});

test('۳) هر ناهم‌خوانیِ شناخته گذاری است که سرور واقعاً می‌پذیرد', () => {
  const ok = new Set(vec.allowed.filter((r) => r[4] === 'ok').map((r) => r.slice(0, 3).join(' ')));
  const kinds = new Set(['latent-bug', 'ui-choice', 'unreachable']);
  assert.ok(vec.knownDivergences.length > 0);
  for (const d of vec.knownDivergences) {
    assert.ok(kinds.has(d.kind), `${d.id}: ${d.kind}`);
    assert.ok(d.note && d.pairs.length, d.id);
    for (const p of d.pairs) {
      const allowedHere = ok.has(p.join(' '));
      //  تنها «باگ» عکسِ این است: کاتلین چیزی را می‌پذیرد که سرور رد می‌کند
      assert.equal(allowedHere, d.kind !== 'latent-bug', `${d.id}: ${p.join(' ')}`);
    }
  }
});

test('۳) «unreachable» از وضعیتِ آغازِ همان روش دست‌نیافتنی است', () => {
  const src = read('src/lib/delivery.js');
  //  وضعیتِ آغاز و ردِ platform همان است که فهرستِ ناهم‌خوانی‌ها فرض کرده
  assert.match(src, /const status = mode === 'shop_courier' \? 'requested' : 'assigned';/);
  assert.match(src, /if \(mode === 'platform'\) \{\s*throw conflict\([^)]*'platform_unavailable'\)/);
  const start = { shop_courier: 'requested', external: 'assigned', pickup: 'assigned' };

  const reachable = (mode) => {
    const seen = new Set([start[mode]]);
    for (let grew = true; grew;) {
      grew = false;
      for (const [m, from, to, , out] of vec.allowed) {
        if (m === mode && out === 'ok' && seen.has(from) && !seen.has(to)) { seen.add(to); grew = true; }
      }
    }
    return seen;
  };
  for (const d of vec.knownDivergences.filter((x) => x.kind === 'unreachable')) {
    for (const [mode, from] of d.pairs) {
      if (mode === 'platform') continue; //  ساختش رد می‌شود (بالا)
      assert.ok(!reachable(mode).has(from), `${d.id}: ${mode}/${from} دست‌یافتنی است`);
    }
  }
  //  و خودِ BFS چیزی را پنهان نمی‌کند: پیکِ دکان به همهٔ وضعیت‌ها می‌رسد
  assert.deepEqual(sortedSet([...reachable('shop_courier')]), sortedSet(vec.statuses));
});
