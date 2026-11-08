'use strict';
/**
 * INV-11 — هستهٔ خالصِ `license/stock-transfers.js` («انتقالِ موجودی» ِ نسخهٔ وب) و سه واژه‌نامه‌اش، بی مرورگر و بی پایگاه.
 *
 *   ⛔ مقدار: رقمِ فارسی/عربی و «٫»، حداکثر سه رقمِ اعشار، صحیحِ هزارم؛ صفر فقط برای دریافت.
 *   ⛔ پیش‌نویس: مبدأ ≠ مقصد، دست‌کم یک کالا، هر ردیف مقدارِ درست — همان سنجشِ سرور (`parseSend`).
 *   ⛔ دریافت: هر ردیفِ در مسیر فرستاده می‌شود (حتی صفر) تا «بستن» هرگز «بقیه رسید» ِ ناخواسته نشود؛ بیش از ماندهٔ در
 *      مسیر نه؛ گامِ ناقصِ تهی نه؛ کسریِ بی دلیل نه. رد بی دلیل نه.
 *   ⛔ fa/en/ps همان کلیدها و همان جای‌نگهدارها؛ en بی حرفِ فارسی؛ هر کلیدی که JS به کار می‌برد هست؛
 *      در کدِ JS (جز یادداشت) هیچ متنِ فارسیِ ثابت و هیچ رویداد/اسکریپتِ درون‌خطی نیست (CSP).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const X = require('../../license/stock-transfers.js');
const ROOT = path.join(__dirname, '..', '..', 'license');
const src = fs.readFileSync(path.join(ROOT, 'stock-transfers.js'), 'utf8');
const dict = Object.fromEntries(X.LANGS.map((l) => [l, JSON.parse(fs.readFileSync(path.join(ROOT, 'i18n', `transfers.${l}.json`), 'utf8'))]));
const ARABIC = /[؀-ۿ]/;

test('مقدار: رقمِ فارسی و «٫»، سه رقمِ اعشار، هزارمِ صحیح؛ صفر فقط برای دریافت', () => {
  assert.equal(X.toMilli('۱٫۵'), 1500);
  assert.equal(X.toMilli('2.25'), 2250);
  assert.equal(X.toMilli(' 3 '), 3000);
  assert.equal(X.toMilli('0'), null);
  assert.equal(X.toMilli('0', true), 0);
  assert.equal(X.toMilli('1.2345'), null);
  assert.equal(X.toMilli('-1'), null);
  assert.equal(X.toMilli('1e3'), null);
  assert.equal(X.toMilli('abc'), null);
  assert.equal(X.toMilli('1000001'), null, 'بیش از سقفِ سرور (یک میلیون واحد)');
  assert.deepEqual([X.qtyText(1500), X.qtyText(2000), X.qtyText(250), X.qtyText(1001)], ['1.5', '2', '0.25', '1.001']);
});

test('پیش‌نویس ⇒ همان بدنه‌ای که سرور می‌پذیرد؛ خطا با کلید و شمارهٔ ردیف', () => {
  const ok = X.draftBody({ from: 'shop_floor', to: 'warehouse', note: '  به انبار ', lines: [{ productId: 'p1', qty: '2' }, { productId: 'p2', qty: '۰٫۵' }] });
  assert.deepEqual(ok, { body: { from: 'shop_floor', to: 'warehouse', note: 'به انبار', lines: [{ productId: 'p1', qtyMilli: 2000 }, { productId: 'p2', qtyMilli: 500 }] } });
  assert.deepEqual(X.draftBody({ from: 'warehouse', to: 'warehouse', lines: [{ productId: 'p1', qty: '1' }] }), { error: 'draft.same' });
  assert.deepEqual(X.draftBody({ from: 'shop_floor', to: 'damaged', lines: [{ productId: 'p1', qty: '1' }] }), { error: 'draft.same' });
  assert.deepEqual(X.draftBody({ from: 'shop_floor', to: 'warehouse', lines: [] }), { error: 'draft.empty' });
  assert.deepEqual(X.draftBody({ from: 'shop_floor', to: 'warehouse', lines: [{ productId: 'p1', qty: '1' }, { productId: 'p2', qty: '0' }] }), { error: 'draft.badQty', n: 2 });
  const many = Array.from({ length: X.MAX_LINES + 1 }, (_, i) => ({ productId: `p${i}`, qty: '1' }));
  assert.deepEqual(X.draftBody({ from: 'shop_floor', to: 'warehouse', lines: many }), { error: 'draft.tooMany' });
});

test('دریافت: همهٔ ردیف‌های در مسیر (حتی صفر)؛ بیش از مانده، گامِ تهی و کسریِ بی دلیل نه', () => {
  const t = {
    id: 'xfr_1', status: 'partial', lines: [
      { productId: 'a', sentMilli: 4000, receivedMilli: 1000, pendingMilli: 3000 },
      { productId: 'b', sentMilli: 2000, receivedMilli: 2000, pendingMilli: 0 },
      { productId: 'c', sentMilli: 1000, receivedMilli: null, pendingMilli: 1000 },
    ],
  };
  assert.deepEqual(X.receiveBody(t, { a: '2' }, false, ''), { body: { final: false, lines: [{ productId: 'a', receivedMilli: 2000 }, { productId: 'c', receivedMilli: 0 }] } });
  assert.deepEqual(X.receiveBody(t, { a: '3', c: '1' }, true, ''), { body: { final: true, lines: [{ productId: 'a', receivedMilli: 3000 }, { productId: 'c', receivedMilli: 1000 }] } });
  assert.deepEqual(X.receiveBody(t, { a: '2', c: '1' }, true, ''), { error: 'recv.needReason' });
  assert.deepEqual(X.receiveBody(t, { a: '2', c: '1' }, true, ' یکی شکست '),
    { body: { final: true, lines: [{ productId: 'a', receivedMilli: 2000 }, { productId: 'c', receivedMilli: 1000 }], reason: 'یکی شکست' } });
  assert.deepEqual(X.receiveBody(t, { a: '4' }, false, ''), { error: 'recv.over', n: 1 });
  assert.deepEqual(X.receiveBody(t, { a: 'x' }, false, ''), { error: 'recv.badQty', n: 1 });
  assert.deepEqual(X.receiveBody(t, {}, false, ''), { error: 'recv.nothing' });
  assert.deepEqual(X.rejectBody('  '), { error: 'rej.needReason' });
  assert.deepEqual(X.rejectBody(' نیامد '), { body: { reason: 'نیامد' } });
  assert.equal(X.isOpen(t), true);
  assert.equal(X.isOpen({ status: 'received' }), false);
});

test('کلیدِ یکتایی همان الگوی سرور؛ زبانِ ناشناخته ⇒ fa', () => {
  const k = X.newKey();
  assert.match(k, /^[A-Za-z0-9_.:-]{8,128}$/);
  assert.notEqual(k, X.newKey());
  assert.deepEqual(['fa', 'en-US', 'ps', 'prs', ''].map(X.langOf), ['fa', 'en', 'ps', 'fa', 'fa']);
  assert.equal(X.fill('{a} و {b}', { a: 1 }), '1 و {b}');
});

test('سه واژه‌نامه: همان کلیدها و جای‌نگهدارها؛ en بی حرفِ فارسی؛ هیچ متنِ خالی', () => {
  const keys = Object.keys(dict.fa).sort();
  const holes = (s) => [...String(s).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(',');
  for (const l of X.LANGS) {
    assert.deepEqual(Object.keys(dict[l]).sort(), keys, l);
    for (const k of keys) {
      assert.ok(String(dict[l][k]).trim(), `${l}.${k} خالی`);
      assert.equal(holes(dict[l][k]), holes(dict.fa[k]), `${l}.${k}: جای‌نگهدار`);
    }
  }
  for (const [k, v] of Object.entries(dict.en)) assert.ok(!ARABIC.test(v), `en.${k}`);
});

test('هر کلیدی که JS به کار می‌برد در واژه‌نامه هست (و خانواده‌های پویا)', () => {
  const used = new Set([...src.matchAll(/\bt\(\s*'([a-z][\w.]*)'/g)].map((m) => m[1]));
  for (const m of src.matchAll(/t\(\s*[^'()]*\?\s*'([a-z][\w.]*)'\s*:\s*'([a-z][\w.]*)'/g)) { used.add(m[1]); used.add(m[2]); }
  for (const s of ['in_transit', 'partial', 'received', 'rejected']) used.add(`st.${s}`);
  for (const l of X.LOCS) used.add(`loc.${l}`);
  for (const k of ['open', 'all']) used.add(`tab.${k}`);
  //  کلیدهایی که هسته به‌جای خطا برمی‌گرداند
  for (const m of src.matchAll(/error: '([a-z][\w.]*)'/g)) used.add(m[1]);
  assert.ok(used.size > 30, `کلیدها خوانده نشدند (${used.size})`);
  for (const k of used) assert.ok(Object.prototype.hasOwnProperty.call(dict.fa, k), `کلیدِ ${k} در واژه‌نامه نیست`);
  //  هر کدِ خطای سرورِ انتقال که ممکن است برسد ترجمه دارد
  const codes = ['insufficient_stock', 'product_not_found', 'transfer_not_found', 'transfer_closed', 'over_received',
    'product_not_in_transfer', 'nothing_received', 'reason_required', 'location_out_of_scope', 'idempotency_key_reused'];
  for (const c of codes) assert.ok(dict.fa[`err.${c}`], `err.${c}`);
});

test('در کدِ JS هیچ متنِ فارسیِ ثابت، رویدادِ درون‌خطی یا اسکریپتِ درون‌خطی نیست', () => {
  const hits = src.split('\n')
    .filter((l) => !/^\s*(\/\/|\*|\/\*)/.test(l))
    .map((l) => l.replace(/\s\/\/.*$/, ''))
    .filter((l) => ARABIC.test(l));
  assert.deepEqual(hits, []);
  assert.doesNotMatch(src, /\son[a-z]+=["'`]/i, 'رویدادِ درون‌خطی');
  assert.doesNotMatch(src, /<script/i, 'اسکریپتِ درون‌خطی');
  assert.doesNotMatch(src, /\beval\(|new Function\(/);
  //  index.html ِ یخ‌زده دست نمی‌خورد: بارکننده shop-sync.js است
  assert.match(fs.readFileSync(path.join(ROOT, 'shop-sync.js'), 'utf8'), /license\/stock-transfers\.js\?v=1/);
  assert.doesNotMatch(fs.readFileSync(path.join(ROOT, '..', 'index.html'), 'utf8'), /stock-transfers/);
});
