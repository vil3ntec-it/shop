'use strict';
/**
 * ماشینِ وضعیتِ سفارشِ آنلاین — بخشِ ۳.۹ بندِ ۲ی مشخصات (فاز ۴).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) **فقط** گذارهای نمودارِ ۳.۹ مجازند — نه یکی بیشتر، نه یکی کمتر.
 *      جدولِ انتظار این‌جا جدا از خودِ کد نوشته شده تا تغییرِ بی‌صدای
 *      جدولِ کد، آزمون را سرخ کند.
 *   ۲) قیدهای کنارِ نمودار: «ارسال یا حضوری» راه را انتخاب می‌کند،
 *      `cancelled` از «آمادهٔ تحویل» فقط با «نیامد» (no_show)، رد و لغو
 *      دلیل می‌خواهند، `auto_rejected` فقط کارِ سیستم است.
 *   ۳) وضعیتِ پرداخت و وضعیتِ نقدِ هنگامِ تحویل دو ماشینِ جدا هستند.
 *   ۴) برگه‌های ORD-12 و مبلغِ ردیف (قاعدهٔ گردکردنِ بخشِ ۳.۴).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const S = require('../src/lib/order-states');

//  نمودارِ ۳.۹، حرف به حرف — مستقل از جدولِ کد
const SPEC = {
  new: ['accepted', 'rejected', 'auto_rejected', 'cancelled_by_customer'],
  accepted: ['picking', 'cancelled'],
  picking: ['packed', 'cancelled'],
  packed: ['ready_for_pickup', 'out_for_delivery', 'cancelled'],
  ready_for_pickup: ['picked_up', 'cancelled'],
  out_for_delivery: ['delivered', 'delivery_failed'],
  delivery_failed: ['retry_scheduled'],
  retry_scheduled: ['out_for_delivery', 'returned_to_store'],
  returned_to_store: ['restocked'],
  delivered: ['return_requested'],
  picked_up: ['return_requested'],
  return_requested: ['refunded_partial', 'refunded_full'],
  rejected: [], auto_rejected: [], cancelled_by_customer: [], cancelled: [],
  restocked: [], refunded_partial: [], refunded_full: [],
};

test('فهرستِ وضعیت‌ها همان نمودارِ ۳.۹ است', () => {
  assert.deepEqual([...S.ORDER_STATUSES].sort(), Object.keys(SPEC).sort());
});

test('هر جفت (از، به): فقط گذارهای نمودار مجازند', () => {
  let allowed = 0;
  let refused = 0;
  for (const from of S.ORDER_STATUSES) {
    for (const to of S.ORDER_STATUSES) {
      const want = SPEC[from].includes(to);
      assert.equal(S.canTransition(from, to), want, `${from} → ${to}`);
      if (want) allowed++; else refused++;
    }
  }
  assert.equal(allowed, 23);
  assert.equal(refused, 19 * 19 - 23);
  //  وضعیتِ ناشناخته هیچ راهی ندارد
  assert.equal(S.canTransition('nope', 'accepted'), false);
  assert.equal(S.canTransition('new', 'nope'), false);
});

test('پایانی‌ها هیچ گذاری ندارند؛ تحویل‌شده فقط به «درخواستِ برگشت»', () => {
  for (const s of ['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'restocked', 'refunded_partial', 'refunded_full']) {
    assert.ok(S.isTerminal(s), s);
    assert.deepEqual(S.nextOf(s), []);
  }
  assert.ok(!S.isTerminal('delivered'));
  assert.deepEqual(S.nextOf('delivered'), ['return_requested']);
});

const pickup = { status: 'packed', fulfillment: 'pickup' };
const delivery = { status: 'packed', fulfillment: 'delivery' };
const user = { type: 'user', role: 'staff' };

test('بسته‌شده: حضوری فقط «آمادهٔ تحویل»، ارسالی فقط «در مسیر»', () => {
  assert.equal(S.check(pickup, 'ready_for_pickup', { actor: user }).ok, true);
  assert.equal(S.check(pickup, 'out_for_delivery', { actor: user }).code, 'fulfillment_mismatch');
  assert.equal(S.check(delivery, 'out_for_delivery', { actor: user }).ok, true);
  assert.equal(S.check(delivery, 'ready_for_pickup', { actor: user }).code, 'fulfillment_mismatch');
});

test('رد و لغو دلیل می‌خواهند (ORD-06)؛ دلیلِ ناشناخته پذیرفته نمی‌شود', () => {
  const o = { status: 'new', fulfillment: 'delivery' };
  assert.equal(S.check(o, 'rejected', { actor: user }).code, 'reason_required');
  assert.equal(S.check(o, 'rejected', { actor: user, reason: 'whatever' }).code, 'bad_reason');
  for (const r of ['out_of_stock', 'closed', 'busy', 'out_of_area']) {
    assert.equal(S.check(o, 'rejected', { actor: user, reason: r }).ok, true, r);
  }
  //  «دیگر» بی توضیح معنا ندارد
  assert.equal(S.check(o, 'rejected', { actor: user, reason: 'other' }).code, 'note_required');
  assert.equal(S.check(o, 'rejected', { actor: user, reason: 'other', note: 'برق رفت' }).ok, true);

  const acc = { status: 'accepted', fulfillment: 'delivery' };
  assert.equal(S.check(acc, 'cancelled', { actor: user }).code, 'reason_required');
  assert.equal(S.check(acc, 'cancelled', { actor: user, reason: 'customer_request' }).ok, true);
});

test('لغو از «آمادهٔ تحویل» فقط با «نیامد» (no_show)', () => {
  const r = { status: 'ready_for_pickup', fulfillment: 'pickup' };
  assert.equal(S.check(r, 'cancelled', { actor: user, reason: 'customer_request' }).code, 'bad_reason');
  assert.equal(S.check(r, 'cancelled', { actor: user, reason: 'no_show' }).ok, true);
});

test('ارسالِ ناموفق دلیل می‌خواهد', () => {
  const o = { status: 'out_for_delivery', fulfillment: 'delivery' };
  assert.equal(S.check(o, 'delivery_failed', { actor: user }).code, 'reason_required');
  assert.equal(S.check(o, 'delivery_failed', { actor: user, reason: 'no_answer' }).ok, true);
});

test('«ردِ خودکار» فقط کارِ سیستم است؛ کاربر نمی‌تواند', () => {
  const o = { status: 'new', fulfillment: 'pickup' };
  assert.equal(S.check(o, 'auto_rejected', { actor: user }).code, 'system_only');
  assert.equal(S.check(o, 'auto_rejected', { actor: { type: 'system' }, reason: 'timeout' }).ok, true);
});

test('بازپرداخت فقط صاحب یا مدیر (پول)', () => {
  const o = { status: 'return_requested', fulfillment: 'pickup' };
  assert.equal(S.check(o, 'refunded_full', { actor: user }).code, 'permission_denied');
  assert.equal(S.check(o, 'refunded_full', { actor: { type: 'user', role: 'manager' } }).ok, true);
  assert.equal(S.check(o, 'refunded_partial', { actor: { type: 'user', role: 'owner' } }).ok, true);
});

test('گذارِ نامجاز کدِ invalid_transition دارد', () => {
  const o = { status: 'new', fulfillment: 'delivery' };
  const r = S.check(o, 'delivered', { actor: user });
  assert.equal(r.ok, false);
  assert.equal(r.code, 'invalid_transition');
});

test('nextFor: دکمه‌های مجازِ هر کارت برای همین کاربر', () => {
  assert.deepEqual(S.nextFor({ status: 'new', fulfillment: 'pickup' }, user),
    ['accepted', 'rejected', 'cancelled_by_customer']);
  assert.deepEqual(S.nextFor(pickup, user), ['ready_for_pickup', 'cancelled']);
  assert.deepEqual(S.nextFor(delivery, user), ['out_for_delivery', 'cancelled']);
  assert.deepEqual(S.nextFor({ status: 'return_requested', fulfillment: 'pickup' }, user), []);
});

test('وضعیتِ پرداخت ماشینِ جداست (۳.۹)', () => {
  const P = S.PAYMENT_TRANSITIONS;
  assert.deepEqual(Object.keys(P).sort(), ['failed', 'paid', 'partially_refunded', 'pending', 'refunded', 'unpaid']);
  assert.ok(S.canPayment('unpaid', 'pending'));
  assert.ok(S.canPayment('pending', 'paid'));
  assert.ok(S.canPayment('pending', 'failed'));
  assert.ok(S.canPayment('paid', 'partially_refunded'));
  assert.ok(S.canPayment('paid', 'refunded'));
  assert.ok(S.canPayment('partially_refunded', 'refunded'));
  assert.ok(!S.canPayment('unpaid', 'paid'), 'پرداخت‌شده فقط از «در انتظار» (رویدادِ تأییدشده)');
  assert.ok(!S.canPayment('refunded', 'paid'));
  assert.ok(!S.canPayment('failed', 'paid'));
});

test('نقدِ هنگامِ تحویل ماشینِ جداست (۳.۹)', () => {
  assert.ok(S.canCod('to_collect', 'collected_by_courier'));
  assert.ok(S.canCod('to_collect', 'collected_in_store'));
  assert.ok(S.canCod('collected_by_courier', 'settled_to_shop'));
  assert.ok(S.canCod('collected_in_store', 'settled_to_shop'));
  assert.ok(!S.canCod('to_collect', 'settled_to_shop'));
  assert.ok(!S.canCod('settled_to_shop', 'to_collect'));
});

test('برگه‌های ORD-12', () => {
  const t = 1_000_000;
  const tab = (status, scheduledFor = null) => S.tabOf({ status, scheduled_for: scheduledFor }, t);
  assert.equal(tab('new'), 'new');
  assert.equal(tab('new', t + 3600e3), 'new', 'سفارشِ زمان‌بندی‌شدهٔ تازه هم باید قبول شود');
  assert.equal(tab('accepted', t + 3600e3), 'scheduled');
  assert.equal(tab('accepted', t - 1), 'preparing');
  assert.equal(tab('accepted'), 'preparing');
  assert.equal(tab('picking'), 'preparing');
  assert.equal(tab('packed'), 'preparing');
  assert.equal(tab('ready_for_pickup'), 'ready');
  for (const s of ['out_for_delivery', 'delivery_failed', 'retry_scheduled']) assert.equal(tab(s), 'on_the_way', s);
  for (const s of ['delivered', 'picked_up', 'rejected', 'auto_rejected', 'cancelled', 'cancelled_by_customer',
    'returned_to_store', 'restocked', 'return_requested', 'refunded_partial', 'refunded_full']) {
    assert.equal(tab(s), 'done', s);
  }
  assert.deepEqual(S.TABS, ['new', 'preparing', 'ready', 'on_the_way', 'scheduled', 'done']);
});

test('مبلغِ ردیف: floorDiv(qty.milli × price.minor + 50000, 100000) × 100 (۳.۴)', () => {
  //  ۲ عدد × ۱۲.۵۰ افغانی = ۲۵ افغانی
  assert.equal(S.lineTotalMinor(2000, 1250), 2500);
  //  ۱.۵ کیلو × ۳۳.۳۳ = ۴۹.۹۹۵ ⇒ ۵۰ افغانی (گرد به یک افغانی، نیمه به بالا)
  assert.equal(S.lineTotalMinor(1500, 3333), 5000);
  //  ۰.۲۵ × ۱۰ = ۲.۵ ⇒ ۳ (نیمه به سمتِ +∞)
  assert.equal(S.lineTotalMinor(250, 1000), 300);
  //  ۰.۲۴۹ × ۱۰ = ۲.۴۹ ⇒ ۲
  assert.equal(S.lineTotalMinor(249, 1000), 200);
  //  منفی: −۰.۲۵ × ۱۰ = −۲.۵ ⇒ −۲ (Math.round، نه برش به سمتِ صفر)
  assert.equal(S.lineTotalMinor(-250, 1000), -200);
  assert.equal(S.lineTotalMinor(-251, 1000), -300);
  assert.equal(S.lineTotalMinor(0, 999999), 0);
  //  عددِ بزرگ بی خطای ممیزِ شناور
  assert.equal(S.lineTotalMinor(9_999_999, 1_000_000_000), 9_999_999_000_000);
});
