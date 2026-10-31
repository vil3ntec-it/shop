'use strict';
/**
 * هستهٔ خالصِ `license/orders-desk.js` (ORD-03، ORD-07) — بی مرورگر و بی پایگاه‌داده.
 *
 *   - «عقب انداختن» فقط با `delay` ِ سرور، فقط در وضعیت‌هایی که سرور گفت، فقط با `orders.manage`.
 *   - بدنهٔ درخواست: دقیقهٔ صحیح، «دیگر» با توضیح، زمانِ دیده‌شده (`null` هم) و کلیدِ یکتایی.
 *   - هر `badge` ِ ویترین یک نشانگرِ روشن با متن (نه فقط رنگ)؛ ویترینِ بسته و نبودِ ویترین.
 *   - هشدارِ قطع فقط وقتی سرور نرسید (شبکه یا ۵xx)، نه ۴xx.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const desk = require('../../license/orders-desk.js');

const CFG = {
  delay: { statuses: ['accepted', 'picking', 'packed'], reasons: ['busy', 'other'], maxMinutes: 1440 },
  permissions: { manage: true },
};

test('canDelay: فقط با پرچم، وضعیتِ مجازِ سرور و orders.manage', () => {
  for (const s of ['accepted', 'picking', 'packed']) assert.equal(desk.canDelay({ status: s }, CFG), true, s);
  for (const s of ['new', 'ready_for_pickup', 'out_for_delivery', 'cancelled']) assert.equal(desk.canDelay({ status: s }, CFG), false, s);
  assert.equal(desk.canDelay({ status: 'accepted' }, { ...CFG, delay: null }), false, 'پرچم خاموش');
  assert.equal(desk.canDelay({ status: 'accepted' }, { ...CFG, permissions: { manage: false } }), false);
  assert.equal(desk.canDelay({ status: 'accepted' }, { ...CFG, delay: { statuses: ['picking'] } }), false, 'فهرست از سرور');
  assert.equal(desk.canDelay(null, CFG), false);
});

test('buildDelayBody: دقیقهٔ صحیح، «دیگر» با توضیح، زمانِ دیده‌شده و کلید', () => {
  assert.deepEqual(desk.buildDelayBody({ minutes: '20', reason: 'busy', note: '  شلوغ  ' }, 1700000000000, 'web-k'),
    { ok: true, body: { minutes: 20, expectedReadyAt: 1700000000000, clientKey: 'web-k', reason: 'busy', note: 'شلوغ' } });
  assert.deepEqual(desk.buildDelayBody({ minutes: 15 }, null, 'web-k'),
    { ok: true, body: { minutes: 15, expectedReadyAt: null, clientKey: 'web-k' } });
  for (const m of ['', '0', '1441', '2.5', 'x', -3]) assert.equal(desk.buildDelayBody({ minutes: m }, null, 'k').ok, false, String(m));
  assert.equal(desk.buildDelayBody({ minutes: 10, reason: 'other', note: ' ' }, null, 'k').ok, false);
  assert.equal(desk.buildDelayBody({ minutes: 10, reason: 'other', note: 'برق' }, null, 'k').ok, true);
  assert.equal(desk.buildDelayBody({ minutes: 10, note: 'x'.repeat(400) }, null, 'k').body.note.length, 300);
});

test('presenceView: هر وضعیتِ ویترین متنِ خودش را دارد (نه فقط رنگ)', () => {
  const fmt = (t) => `@${t}`;
  const v = (p) => desk.presenceView(p, fmt);
  assert.equal(v(null), null, 'بی ویترین ⇒ بی نشانگر');
  assert.deepEqual(v({ published: true, badge: 'open_online' }), { tone: 'open', title: 'باز', sub: 'سفارش می‌گیرد' });
  assert.equal(v({ published: true, badge: 'declared_until', declaredUntil: 5 }).title, 'باز تا @5');
  assert.equal(v({ published: true, badge: 'open_unconfirmed' }).tone, 'warn');
  assert.match(v({ published: true, badge: 'closed', mode: 'closed' }).sub, /اعلامِ «بسته»/);
  assert.match(v({ published: true, badge: 'closed', mode: 'auto', holiday: true }).sub, /تعطیل/);
  assert.match(v({ published: true, badge: 'closed', mode: 'auto' }).sub, /ساعتِ کاری/);
  assert.match(v({ published: true, badge: 'offline', mode: 'auto' }).sub, /هیچ دستگاهی/);
  assert.match(v({ published: true, badge: 'offline', mode: 'offline' }).sub, /اعلامِ «آفلاین»/);
  assert.deepEqual([v({ published: false, badge: 'open_online' }).tone, v({ published: false }).title], ['off', 'ویترین بسته است']);
  for (const b of ['open_online', 'declared_until', 'open_unconfirmed', 'closed', 'offline', 'x']) {
    const out = v({ published: true, badge: b });
    assert.ok(out.title.length > 0, b);
  }
});

test('netWarning و isNetDown: فقط وقتی سرور نرسید', () => {
  assert.equal(desk.netWarning({ down: false, lastOkAt: 1 }, 2), null);
  const w = desk.netWarning({ down: true, lastOkAt: 1_000_000 }, 1_000_000 + 5 * 60_000);
  assert.match(w.title, /اتصال قطع است/);
  assert.match(w.sub, /۵|5 دقیقه پیش/);
  assert.doesNotMatch(desk.netWarning({ down: true, lastOkAt: 0 }, 5).sub, /آخرین/);
  assert.equal(desk.isNetDown({ code: 'network' }), true);
  assert.equal(desk.isNetDown({ status: 503 }), true);
  assert.equal(desk.isNetDown({ status: 403, code: 'orders_disabled' }), false);
  assert.equal(desk.isNetDown({ status: 409 }), false);
  assert.equal(desk.isNetDown(null), false);
});
