'use strict';
/**
 * PRD-07 — شکلِ `products.purchaseUnits` در سرور (`src/lib/purchase-units.js`)، همان کدهای
 * `core/product/PurchaseUnits.validate`؛ و سه راهِ همگام‌سازیِ دکان: v3 (`sync-v3-merge.validate`)، v1
 * (میان‌افزارِ `newer-fields.v1NewerFields`) و راهِ قدیم (`legacyReason`). کالای بی واحدِ خرید و `null` همان پیشین.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const PU = require('../src/lib/purchase-units');
const merge = require('../src/lib/sync-v3-merge');
const N = require('../src/lib/newer-fields');
const { hashOf } = require('../src/lib/sync-v1');
const hlc = require('../src/lib/hlc');

const tea = [
  { name: 'کارتن ۱۲', factorMilli: 12000, supplierId: 'supA', barcode: '444' },
  { name: 'کارتن ۲۴', factorMilli: 24000, supplierId: 'supB', barcode: '555' },
];

test('شکلِ درست: دو کارتن از دو تأمین‌کننده، کیسهٔ اعشاری، null و نبودنِ کلید', () => {
  assert.equal(PU.check(tea), null);
  assert.equal(PU.check([{ name: 'کیسه', factorMilli: 2500 }]), null, 'اعشار برای واحدِ وزنی (سرور واحد را نمی‌شناسد)');
  assert.equal(PU.check(null), null);
  assert.equal(PU.check(undefined), null);
  assert.equal(PU.check([]), null);
  assert.equal(PU.reasonOf({ salePrice: 10 }), '', 'ویرایشِ بی این کلید دست نمی‌خورد');
  assert.equal(PU.reasonOf({ purchaseUnits: null }), '');
});

test('شکلِ نادرست: هر کد همان کدِ هسته', () => {
  const at = (v) => PU.check(v)?.code;
  assert.equal(at('کارتن'), 'pu_not_list');
  assert.equal(at({ name: 'x' }), 'pu_not_list');
  assert.equal(at(Array.from({ length: 11 }, (_, i) => ({ name: `u${i}`, factorMilli: 1000 }))), 'pu_too_many');
  assert.equal(at([{ factorMilli: 1000 }]), 'pu_name_required');
  assert.equal(at([{ name: '   ', factorMilli: 1000 }]), 'pu_name_required');
  assert.equal(at(['کارتن']), 'pu_name_required');
  assert.equal(at([{ name: 'ک'.repeat(31), factorMilli: 1000 }]), 'pu_name_too_long');
  assert.equal(at([{ name: 'كارتن', factorMilli: 12000 }, { name: ' کارتن ', factorMilli: 24000 }]), 'pu_duplicate_name', 'ي/ك و فاصله یکی‌اند');
  assert.equal(at([{ name: 'کارتن', factorMilli: 0 }]), 'pu_bad_factor');
  assert.equal(at([{ name: 'کارتن', factorMilli: -24000 }]), 'pu_bad_factor');
  assert.equal(at([{ name: 'کارتن', factorMilli: 24.5 }]), 'pu_bad_factor', 'هزارمِ صحیح');
  assert.equal(at([{ name: 'کارتن', factorMilli: '24000' }]), 'pu_bad_factor');
  assert.equal(at([{ name: 'کارتن', factorMilli: 1000000001 }]), 'pu_factor_too_big');
  assert.equal(at([{ name: 'کارتن', factorMilli: 1000, supplierId: ' ' }]), 'pu_bad_supplier');
  assert.equal(at([{ name: 'کارتن', factorMilli: 1000, supplierId: 7 }]), 'pu_bad_supplier');
  assert.equal(at([{ name: 'کارتن', factorMilli: 1000, barcode: 'a b' }]), 'pu_bad_barcode');
  assert.equal(at([{ name: 'کارتن', factorMilli: 1000, barcode: '' }]), 'pu_bad_barcode');
  assert.equal(at([{ name: 'A', factorMilli: 1000, barcode: '9' }, { name: 'B', factorMilli: 2000, barcode: '9' }]), 'pu_duplicate_barcode');
  assert.deepEqual(PU.check([{ name: 'A', factorMilli: 1000 }, { name: 'a', factorMilli: 2000 }]), { code: 'pu_duplicate_name', index: 1 });
});

test('v3: opِ کالا با واحدِ خریدِ نادرست رد می‌شود؛ درست، null و جدولِ دیگر می‌گذرند', () => {
  const op = (fields, table = 'products', kind = 'update') => ({ op_id: 'op-1', table, row_id: 'p1', kind, hlc: hlc.format({ wall: 1700000000000, counter: 0, node: 'dev1' }), fields });
  const ok = merge.validate(op({ purchaseUnits: tea }));
  assert.equal(ok.ok, true, JSON.stringify(ok));
  assert.deepEqual(merge.validate(op({ purchaseUnits: [{ name: 'کارتن', factorMilli: 0 }] })), { ok: false, reason: 'bad_purchase_units', op_id: 'op-1' });
  assert.equal(merge.validate(op({ name: 'چای', purchaseUnits: 'x' }, 'products', 'insert')).reason, 'bad_purchase_units');
  assert.equal(merge.validate(op({ purchaseUnits: null })).ok, true, 'پاک کردن');
  assert.equal(merge.validate(op({ salePriceMinor: 1000 })).ok, true, 'ویرایشِ بی این کلید همان پیشین');
  //  v2 ِ مبدل (relaxed) هم
  assert.equal(merge.validate(op({ purchaseUnits: [{ name: '', factorMilli: 1 }] }), 'v2').reason, 'bad_purchase_units');
  //  جدولِ دیگر با همین نام نمی‌سنجد
  assert.equal(merge.validate(op({ purchaseUnits: 'x' }, 'suppliers')).ok, true);
});

/** میان‌افزارِ v1 با درخواست و پاسخِ ساختگی (همان `newer-fields.test.js`)؛ v1 هر op ِ رسیده را applied می‌کند */
function runV1(ops, { app = 'shop', kind = 'shop' } = {}) {
  const req = { sync: { app, accountKind: kind, accountId: 'shop_1' }, body: { ops, caps: ['a00'] }, headers: { 'x-app-version': '3.2.900' } };
  let sent = null;
  const res = { json: (b) => { sent = b; return b; } };
  let passed = null;
  N.v1NewerFields(req, res, (err) => { if (err) throw err; passed = req.body.ops; });
  res.json({ ok: true, results: passed.map((o) => ({ op_id: o.op_id, status: 'applied' })) });
  return { passed, results: sent.results };
}

const v1 = (op_id, table, type, fields) => {
  const o = { op_id, table, row_id: `${table}-1`, type, fields, ts: 1 };
  o.hash = hashOf({ table, row_id: o.row_id, type, fields });
  return o;
};

test('v1: فقط همان op ِ نادرست رد می‌شود و بقیهٔ دسته به همان ترتیب پاسخ می‌گیرد', () => {
  const ops = [
    v1('a', 'products', 'update', { purchaseUnits: tea }),
    v1('b', 'products', 'update', { purchaseUnits: [{ name: 'کارتن', factorMilli: -1 }] }),
    v1('c', 'sales', 'insert', { total: 10 }),
    v1('d', 'products', 'insert', { name: 'چای', purchaseUnits: [{ name: 'A', factorMilli: 1000 }, { name: 'a', factorMilli: 2000 }] }),
  ];
  const { passed, results } = runV1(ops);
  assert.deepEqual(passed.map((o) => o.op_id), ['a', 'c']);
  assert.deepEqual(results, [
    { op_id: 'a', status: 'applied' },
    { op_id: 'b', status: 'rejected', reason: 'bad_purchase_units' },
    { op_id: 'c', status: 'applied' },
    { op_id: 'd', status: 'rejected', reason: 'bad_purchase_units' },
  ]);
});

test('v1: دستهٔ بی واحدِ خرید، پمپ و حسابِ غیرِ دکان دست نمی‌خورند', () => {
  const ops = [v1('a', 'products', 'update', { salePrice: 12 }), v1('b', 'products', 'delete', null)];
  const same = runV1(ops);
  assert.deepEqual(same.passed, ops);
  assert.deepEqual(same.results.map((r) => r.status), ['applied', 'applied']);
  const pump = runV1([v1('p', 'products', 'update', { purchaseUnits: 'x' })], { app: 'pump', kind: 'station' });
  assert.equal(pump.results[0].status, 'applied', 'پمپ هرگز به این‌جا نمی‌رسد');
});

test('راهِ قدیم: ردیفِ کالا با شکلِ نادرست ⇒ دلیل؛ درست یا حذف ⇒ هیچ', () => {
  assert.equal(PU.legacyReason([{ collection: 'products', id: 'p1', data: { purchaseUnits: tea } }]), '');
  assert.equal(PU.legacyReason([{ collection: 'products', id: 'p1', data: { purchaseUnits: [{ name: 'x', factorMilli: 0 }] } }]), 'bad_purchase_units');
  assert.equal(PU.legacyReason([{ collection: 'products', id: 'p1', deleted: true, data: { purchaseUnits: 'x' } }]), '');
  assert.equal(PU.legacyReason([{ collection: 'debtors', id: 'd1', data: { purchaseUnits: 'x' } }]), '');
  assert.equal(PU.legacyReason(null), '');
});
