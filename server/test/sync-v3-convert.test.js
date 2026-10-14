'use strict';
/**
 * Sync v3 — مبدلِ op v2 (SYN-11، بخشِ ۳.۸.۱ بندِ ۵): مبلغِ Double ⇒ Long و
 * نام‌های قدیمی. خالص است: نه دیتابیس دارد نه شبکه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const convert = require('../src/lib/sync-v3-convert');
const schema = require('../src/lib/sync-v3-schema');
const hlc = require('../src/lib/hlc');
const { TABLES: SHOP_TABLES } = require('../src/lib/sync');
const path = require('node:path');

const TS = 1_760_000_000_000;
const v2 = (o) => ({ op_id: '01JTEST0000000000000000001', ts: TS, row_id: 'r-1', ...o });
const one = (raw, opts = {}) => {
  const out = convert.fromV2(raw, { schemaVersion: 3, deviceId: 'dev-a', index: 0, ...opts });
  assert.equal(out.ok, true, `مبدل رد کرد: ${out.reason}`);
  return out;
};

test('هر جدولِ Sync v1ِ دکان در v3 جا دارد (SYN-13: دسته‌ها، واحدها، دسته‌های مصارف هم)', () => {
  for (const t of Object.keys(SHOP_TABLES)) assert.ok(schema.specOf(t), `جدولِ ${t} در v3 نیست`);
  for (const t of ['productCategories', 'productUnits', 'expenseCategories']) {
    assert.equal(schema.specOf(t).policy, 'master');
  }
});

test('toMinor / toMilli = همان بردارهای مرجعِ fromLegacy (یک قاعده برای سرور، کاتلین و JS)', () => {
  const vectors = require(path.join(__dirname, '..', '..', 'license', 'test-vectors', 'money-v1.json')).fromLegacy;
  assert.ok(vectors.money.length > 5 && vectors.qty.length > 5);
  for (const [x, minor] of vectors.money) assert.equal(convert.toMinor(x), minor, `مبلغِ ${x}`);
  for (const [x, milli] of vectors.qty) assert.equal(convert.toMilli(x), milli, `مقدارِ ${x}`);
});

test('toMinor / toMilli: نیمه به سمتِ +∞ (Math.round)، بی −۰، رشتهٔ عددی، نامفهوم ⇒ undefined', () => {
  assert.equal(convert.toMinor(150.5), 15050);
  assert.equal(convert.toMinor(0.1 + 0.2), 30);
  assert.equal(convert.toMinor(19.99), 1999);
  assert.equal(convert.toMinor(-0.005), 0);
  assert.ok(Object.is(convert.toMinor(-0.004), 0));
  assert.equal(convert.toMinor(-2.5), -250);
  assert.equal(convert.toMinor('150'), 15000);
  assert.equal(convert.toMinor('abc'), undefined);
  assert.equal(convert.toMinor(Infinity), undefined);
  assert.equal(convert.toMilli(0.333), 333);
});

test('درجِ کالا: مبلغ و مقدار نامِ تازه و عددِ صحیح می‌گیرند؛ مانده و id نمی‌روند', () => {
  const out = one(v2({ table: 'products', type: 'insert', fields: {
    id: 'r-1', name: 'شیر', salePrice: 150.5, purchasePrice: 120, minStock: 2.5,
    barcodes: ['123'], stock: 40, discountValue: 5,
  } }));
  assert.equal(out.ops.length, 1);
  const [op] = out.ops;
  assert.equal(op.op_id, '01JTEST0000000000000000001');
  assert.equal(op.kind, 'insert');
  assert.equal(op.table, 'products');
  assert.deepEqual(op.fields, {
    name: 'شیر', salePriceMinor: 15050, purchasePriceMinor: 12000, minStockMilli: 2500,
    barcodes: ['123'], discountValue: 5,
  });
  assert.deepEqual(out.dropped, ['stock']);
  assert.equal(op.hlc, hlc.fromTs(TS, 0, 'dev-a'));
});

test('مقدارِ نامفهوم با نامِ قدیم می‌ماند — مبدل چیزی را دور نمی‌اندازد', () => {
  const out = one(v2({ table: 'expenses', type: 'update', fields: { amount: 'نامعلوم', title: 'کرایه' } }));
  assert.deepEqual(out.ops[0].fields, { amount: 'نامعلوم', title: 'کرایه' });
  assert.equal(out.ops[0].legacy, true);
});

test('$inc روی فاکتور ⇒ یک op «دلتا» با واحدِ تازه', () => {
  const out = one(v2({ table: 'sales', type: 'update', fields: {
    paidAmount: { $inc: 50 }, remaining: { $inc: -50.25 },
  } }));
  assert.equal(out.ops.length, 1);
  assert.equal(out.ops[0].kind, 'delta');
  assert.deepEqual(out.ops[0].fields, { paidAmountMinor: 5000, remainingMinor: -5025 });
});

test('لغوِ فاکتور در v2 (status=cancelled) ⇒ op «لغو» با دلیل؛ نشانِ محلی نمی‌رود', () => {
  const out = one(v2({ table: 'sales', type: 'update', fields: {
    status: 'cancelled', cancelReason: 'مشتری پس داد', syncStatus: 'synced',
  } }));
  assert.equal(out.ops.length, 1);
  assert.equal(out.ops[0].kind, 'cancel');
  assert.equal(out.ops[0].reason, 'مشتری پس داد');
  assert.deepEqual(out.dropped, ['syncStatus']);
});

test('یک op v2 با سه کار ⇒ سه op v3، یک tx، شناسه‌های پایدار', () => {
  const raw = v2({ table: 'sales', type: 'update', fields: {
    status: 'cancelled', paidAmount: { $inc: -10 }, debtorId: 'd-2',
  } });
  const a = one(raw);
  const b = one(raw);
  assert.deepEqual(a, b, 'دو بار تبدیل ⇒ همان خروجی (تکرارِ op بی‌اثر می‌ماند)');
  assert.deepEqual(a.ops.map(o => o.kind), ['cancel', 'delta', 'update']);
  assert.deepEqual(a.ops.map(o => o.op_id), [
    '01JTEST0000000000000000001.c', '01JTEST0000000000000000001.d', '01JTEST0000000000000000001.e',
  ]);
  assert.ok(a.ops.every(o => o.tx === '01JTEST0000000000000000001'));
  assert.equal(a.ops[0].reason.length > 0, true);
  assert.deepEqual(a.ops[2].fields, { debtorId: 'd-2' });
  assert.equal(a.ops[2].legacy, true);
});

test('حذفِ سندِ مالی در v2 ⇒ «لغو»، نه نابودی؛ حذفِ حرکتِ کالا ⇒ صندوقِ تعارض', () => {
  const s = one(v2({ table: 'sales', type: 'delete' }));
  assert.equal(s.ops[0].kind, 'cancel');
  const m = one(v2({ table: 'stockMovements', type: 'delete' }));
  assert.equal(m.ops[0].kind, 'delete');
  assert.equal(m.ops[0].legacy, true);
});

test('حرکتِ کالا: qty ⇒ qtyMilli', () => {
  const out = one(v2({ table: 'stockMovements', type: 'insert', fields: { productId: 'p-1', qty: -2, type: 'sale' } }));
  assert.deepEqual(out.ops[0].fields, { productId: 'p-1', qtyMilli: -2000, type: 'sale' });
});

test('op پلهٔ ۱ اول با همان مهاجرت‌های v1 بالا می‌آید (فروشِ بی‌کانال ⇒ pos)', () => {
  const out = one(v2({ table: 'sales', type: 'insert', fields: { total: 10 } }), { schemaVersion: 1 });
  assert.deepEqual(out.ops[0].fields, { totalMinor: 1000, channel: 'pos' });
});

test('op v2ِ نادرست با دلیلِ خودش رد می‌شود', () => {
  const bad = convert.fromV2(v2({ table: 'nope', type: 'insert', fields: { a: 1 } }), { schemaVersion: 3, deviceId: 'd' });
  assert.deepEqual(bad, { ok: false, reason: 'unknown_table', op_id: '01JTEST0000000000000000001' });
  const bad2 = convert.fromV2(v2({ op_id: 'bad id', table: 'sales', type: 'insert', fields: { a: 1 } }), { schemaVersion: 3, deviceId: 'd' });
  assert.equal(bad2.reason, 'bad_op_id');
});

test('HLC: از ts و جایگاهِ op در دسته؛ یا HLCِ داده‌شده (پلِ v1)', () => {
  const a = one(v2({ table: 'debtors', type: 'update', fields: { name: 'x' } }), { index: 7 });
  assert.equal(a.ops[0].hlc, hlc.fromTs(TS, 7, 'dev-a'));
  const given = hlc.format({ wall: TS + 5, counter: 0, node: 'v1.000000000000042' });
  const b = one(v2({ table: 'debtors', type: 'update', fields: { name: 'x' } }), { hlc: given });
  assert.equal(b.ops[0].hlc, given);
});

test('نام‌های قدیمیِ جدول (هر شکلِ نوشتن) به نامِ v3 می‌رسند', () => {
  const out = one(v2({ table: 'Product_Categories', type: 'insert', fields: { name: 'لبنیات' } }));
  assert.equal(out.ops[0].table, 'productCategories');
});
