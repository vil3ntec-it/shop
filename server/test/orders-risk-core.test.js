'use strict';
/**
 * هستهٔ خالصِ `license/orders-risk.js` (RSK-03، کارتِ «سابقهٔ این مشتری» ِ وب) — بی مرورگر و بی پایگاه‌داده.
 * همان قاعدهٔ برنامهٔ نسخهٔ ۳ (`core/orders/OrderRisk.visible/shown`): پرچمِ خاموش یا چیزی برای گفتن نیست ⇒ بی کارت،
 * هشدارها اول، شمارِ «لغو یا رد» = لغوِ مشتری + لغوِ دکان + ردِ دکان؛ پاسخِ ناجور هرگز «بی‌خطر» ِ ساختگی نمی‌سازد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const risk = require('../../license/orders-risk.js');

const history = (over = {}) => ({
  orders: 5, completed: 2, open: 1, rejectedByShop: 1, cancelledByCustomer: 1, cancelledByShop: 0, noShow: 0,
  deliveryFailed: 0, returnedToShop: 0, returnsOrRefunds: 0, ...over,
});

test('RSK-03 وب: پرچمِ خاموش، پاسخِ تهی یا بی‌چیز ⇒ بی کارت', () => {
  for (const r of [null, undefined, {}, { enabled: false }, { enabled: 'true' }, { enabled: true, known: false, flags: [] },
    { enabled: true, known: false, flags: [{ severity: 'warn' }, null, { code: '' }] }]) {
    assert.equal(risk.view(r), null, JSON.stringify(r));
  }
});

test('RSK-03 وب: شمار و جمله‌ها همان برنامه؛ هشدارها اول، به ترتیبِ سرور در هر گروه', () => {
  const v = risk.view({
    enabled: true, known: true, history: history(),
    flags: [
      { code: 'new_customer', severity: 'info', count: 0 },
      { code: 'delivery_failed_before', severity: 'warn', count: 2 },
      { code: 'returns_before', severity: 'info', count: 1 },
      { code: 'possible_duplicate', severity: 'warn', count: 1, orderNo: 41 },
    ],
  });
  assert.equal(v.title, 'سابقهٔ این مشتری در دکانِ شما');
  assert.equal(v.summary, '5 سفارشِ پیشین · 2 تحویل · 2 لغو یا رد · 1 باز');
  assert.deepEqual(v.items, [
    { warn: true, text: '2 بار تحویل ناموفق بوده' },
    { warn: true, text: 'شاید تکراریِ سفارشِ 41' },
    { warn: false, text: 'مشتریِ تازه — هنوز هیچ سفارشی از شما نگرفته' },
    { warn: false, text: '1 برگشت یا بازپرداخت داشته' },
  ]);
  assert.equal(v.footer, 'فقط سفارش‌های همین دکان؛ شماره دیده نمی‌شود.');
});

test('RSK-03 وب: نشانهٔ ناشناخته و تکراریِ بی شماره؛ بی سابقه ⇒ بی خطِ شمار؛ عددِ ناجور ⇒ صفر', () => {
  const v = risk.view({
    enabled: true, known: false, history: null,
    flags: [{ code: 'blocklisted', severity: 'warn' }, { code: 'future_sig', severity: 'info' }, { code: 'possible_duplicate', severity: 'warn' }],
  });
  assert.equal(v.summary, null);
  assert.deepEqual(v.items.map((x) => x.text), ['این شماره یا جا در فهرستِ بستهٔ دکان است', 'شاید سفارشِ تکراری', 'نشانهٔ دیگر: future_sig']);
  const w = risk.view({ enabled: true, known: true, history: history({ orders: '3', completed: 'x', open: null }), flags: [] });
  assert.equal(w.summary, '3 سفارشِ پیشین · 0 تحویل · 2 لغو یا رد · 0 باز');
  assert.deepEqual(w.items, []);
  //  سابقهٔ صفر سفارش (فقط نشانه) ⇒ بی خطِ شمار
  assert.equal(risk.view({ enabled: true, known: false, history: history({ orders: 0 }), flags: [{ code: 'new_customer', severity: 'info' }] }).summary, null);
});

test('RSK-03 وب: متنِ نشانه فقط متن است — درج در HTML با `esc` ِ صفحه (کدِ ناشناخته همان‌گونه برمی‌گردد)', () => {
  assert.equal(risk.flagText({ code: '<img src=x onerror=1>' }), 'نشانهٔ دیگر: <img src=x onerror=1>');
  assert.equal(risk.flagText({ code: 'many_open', count: 3.9 }), '3 سفارشِ بازِ دیگر دارد');
});
