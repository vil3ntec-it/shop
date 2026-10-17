'use strict';
/**
 * بردارِ مشترکِ ماشینِ وضعیتِ سفارش (`core/test-vectors/order-states.json`).
 *
 *   ۱) فایل همان خروجیِ امروزِ `order-states.js` است — ماشینِ وضعیت بی
 *      بردار عوض نمی‌شود (آینهٔ کاتلین، `OrderStatesTest.kt`، همین فایل را می‌خواند).
 *   ۲) هستهٔ `license/orders-ui.js`ِ وب با همان بردار جور است: دلیل‌های
 *      پنجره، دکمه‌ها (فقط با `orders.manage`) و نامِ هر برگه/وضعیت/دلیل.
 *   ۳) فرمِ «سفارشِ دستی» عدد را بی ممیزِ شناور به هزارم و یک‌صدم می‌برد و
 *      همان بدنه‌ای را می‌سازد که `parseCreate`ِ سرور می‌پذیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const gen = require('../scripts/order-vectors');
const S = require('../src/lib/order-states');
const { parseCreate } = require('../src/lib/orders');
const web = require('../../license/orders-ui.js');

const vec = JSON.parse(fs.readFileSync(gen.FIXTURE, 'utf8'));

test('۱) بردار با order-states.js امروز یکی است', () => {
  assert.equal(fs.readFileSync(gen.FIXTURE, 'utf8'), gen.expected(),
    'بردارها کهنه‌اند: cd server && node scripts/order-vectors.js --write');
  //  خودِ سنجه خالی نیست
  assert.ok(vec.check.length > 500 && vec.nextFor.length === S.ORDER_STATUSES.length * 2 * 3);
});

test('۲) وب: دلیل‌های پنجره همان بردار است (آماده ⇐ لغو فقط «نیامد»)', () => {
  for (const [from, to, reasons] of vec.reasonChoices) {
    assert.deepEqual(web.reasonChoices(from, to, vec.reasons), reasons, `${from} → ${to}`);
    assert.equal(web.needsReason(to, vec.reasons), vec.reasonRequired.includes(to));
  }
  //  گذارِ بی دلیل پنجرهٔ دلیل ندارد
  for (const to of S.ORDER_STATUSES) {
    if (!vec.reasonRequired.includes(to)) assert.equal(web.needsReason(to, vec.reasons), false, to);
  }
});

test('۲) وب: دکمه‌ها همان next ِ سرور، و بی orders.manage هیچ', () => {
  for (const [status, fulfillment, role, next] of vec.nextFor) {
    const card = { status, fulfillment, next };
    const manage = vec.permissions['orders.manage'].includes(role);
    assert.deepEqual(web.actionsFor(card, { manage }), manage ? next : [], `${status}/${fulfillment}/${role}`);
    assert.deepEqual(web.actionsFor(card, { manage: false }), []);
  }
});

test('۲) وب: هر برگه، وضعیت، دلیل و منبع نامِ فارسی دارد', () => {
  assert.deepEqual(web.TABS, vec.tabs);
  for (const t of vec.tabs) assert.ok(web.TAB_LABELS[t], t);
  for (const s of vec.statuses) assert.ok(web.STATUS_LABELS[s], s);
  //  هر مقصدِ گذار دکمه‌ای با نام دارد
  for (const list of Object.values(vec.transitions)) for (const to of list) assert.ok(web.ACTION_LABELS[to], to);
  for (const list of Object.values(vec.reasons)) for (const r of list) assert.ok(web.REASON_LABELS[r], r);
  for (const s of require('../src/lib/orders').STAFF_SOURCES) assert.ok(web.SOURCE_LABELS[s], s);
});

test('۳) فرمِ دستی: عددِ فارسی، اعشار بی ممیزِ شناور، و بدنه‌ای که سرور می‌پذیرد', () => {
  assert.equal(web.scaled('۱٫۵', 3), 1500);
  assert.equal(web.scaled('0.001', 3), 1);
  assert.equal(web.scaled('1,250', 2), 125000);
  assert.equal(web.scaled('33.33', 2), 3333);
  assert.equal(web.scaled('1.234', 2), null, 'اعشارِ بیشتر از مجاز');
  assert.equal(web.scaled('-1', 2), null);
  assert.equal(web.scaled('abc', 2), null);
  assert.equal(web.money(125000), '1,250');
  assert.equal(web.money(3333), '33.33');

  const ok = web.buildCreateBody({
    source: 'phone', fulfillment: 'delivery', name: ' احمد ', phone: '0791234567',
    addressText: 'کارتهٔ سه', deliveryFee: '50', note: 'زنگ بزنید',
    lines: [{ name: 'برنج', qty: '1.5', price: '33.33' }, { name: '', qty: '', price: '' }, { name: 'نوشابه', qty: '2', price: '12.5' }],
  });
  assert.equal(ok.ok, true, ok.error);
  assert.deepEqual(ok.body.lines, [
    { name: 'برنج', qtyMilli: 1500, unitPriceMinor: 3333 },
    { name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 },
  ]);
  const parsed = parseCreate(ok.body, Date.now());
  assert.equal(parsed.source, 'phone');
  assert.equal(parsed.deliveryFeeMinor, 5000);
  assert.equal(parsed.itemsCount, 2);
  assert.equal(parsed.totalMinor, S.lineTotalMinor(1500, 3333) + S.lineTotalMinor(2000, 1250) + 5000);

  const bad = (f, re) => { const r = web.buildCreateBody(f); assert.equal(r.ok, false); assert.match(r.error, re); };
  bad({ name: '', lines: [{ name: 'x', qty: '1', price: '1' }] }, /نامِ مشتری/);
  bad({ name: 'a', fulfillment: 'delivery', lines: [{ name: 'x', qty: '1', price: '1' }] }, /شمارهٔ مشتری/);
  bad({ name: 'a', fulfillment: 'delivery', phone: '0791234567', lines: [{ name: 'x', qty: '1', price: '1' }] }, /نشانی/);
  bad({ name: 'a', lines: [] }, /دست‌کم یک قلم/);
  bad({ name: 'a', lines: [{ name: 'x', qty: '0', price: '1' }] }, /مقدارِ/);
  bad({ name: 'a', lines: [{ name: 'x', qty: '1', price: 'x' }] }, /قیمتِ/);
  //  حضوری: هزینهٔ ارسال و نشانی فرستاده نمی‌شود (سرور برای حضوری هزینه نمی‌پذیرد)
  const pickup = web.buildCreateBody({ name: 'a', deliveryFee: '50', addressText: 'x', lines: [{ name: 'x', qty: '1', price: '1' }] });
  assert.equal(pickup.body.deliveryFeeMinor, undefined);
  assert.equal(pickup.body.address, undefined);
  assert.doesNotThrow(() => parseCreate(pickup.body, Date.now()));
});
