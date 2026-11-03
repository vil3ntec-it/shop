'use strict';
/**
 * فاکتورِ نسیهٔ بی مشتری از برنامهٔ کهنه (`lib/credit-debtor.js`، صاحب‌کار ۱۴۰۵/۰۸/۱۳):
 *   ۱) push ِ v1: پذیرفته (فروشِ واقعی — ردِ op یعنی گم شدنش در دستگاهِ کهنه)، دادهٔ نشسته دست‌نخورده، و یک سابقهٔ
 *      `sale.credit_no_debtor` برای هر فاکتور (تکرار ⇒ همان یکی). نسیهٔ با مشتری، نقد، لغوشده و «به حساب» ِ ترکیبیِ
 *      با مشتری ⇒ هیچ سابقه. ویرایشی که مشتری را از فاکتورِ نسیه برمی‌دارد ⇒ سابقه (شکلِ نهایی از `sync_rows`).
 *   ۲) راهِ قدیمِ `/api/sync`: همان (`path: legacy`).
 *   ۳) `verify-data`: بررسیِ فقط‌خواندنیِ `orphan.credit_no_debtor` (warn) دفترِ موجود را می‌یابد.
 *   ۴) جداییِ دکان‌ها: سابقه فقط در دکانِ خودش.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const cd = require('../src/lib/credit-debtor');
const vd = require('../src/lib/verify-data');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

let owner;
let shopId;
let other;
let otherShop;
let seq = 0;

const opId = () => `01JCRD${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (ops, token = owner.accessToken, dev = 'dev-old') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const flags = async (shop = shopId) => (await query(
  'SELECT target_id, detail FROM audit_logs WHERE action=$1 AND shop_id=$2 ORDER BY created_at, id', [cd.ACTION, shop]
)).rows.map((r) => ({ sale: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));

function sale(id, extra = {}) {
  return {
    id, invoiceNumber: 3, total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
    paymentMethod: 'credit', debtorId: null, paidAmount: 400, remaining: 600, status: 'completed',
    debtGiven: 600, debtSettled: 0, createdAt: 1, date: '2026-11-03', syncStatus: 'pending', seller: 'کهنه', ...extra,
  };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ نسیه');
  shopId = (await h.post('/api/shop', { name: 'دکانِ نسیه' }, { token: owner.accessToken })).body.shop.id;
  other = await h.newUser('صاحبِ دیگر');
  otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگرِ نسیه' }, { token: other.accessToken })).body.shop.id;
});

test.after(() => h.stop());

test('قاعدهٔ خالص: سهمِ «به حساب» بی مشتری', () => {
  assert.equal(cd.orphanCredit(sale('a')), true);
  assert.equal(cd.orphanCredit(sale('a', { debtGiven: 0, remaining: 600 })), true, 'نسیه با مانده و بی debtGiven ِ برنامهٔ کهنه');
  assert.equal(cd.orphanCredit(sale('a', { debtorId: 'd1' })), false);
  assert.equal(cd.orphanCredit(sale('a', { debtorId: '  ' })), true);
  assert.equal(cd.orphanCredit(sale('a', { status: 'cancelled' })), false);
  assert.equal(cd.orphanCredit(sale('a', { paymentMethod: 'cash', paidAmount: 1000, remaining: 0, debtGiven: 0 })), false);
  assert.equal(cd.orphanCredit(sale('a', { paymentMethod: 'credit', paidAmount: 1000, remaining: 0, debtGiven: 0 })), false, 'نسیهٔ کامل‌پرداخت');
  const mixed = { paymentMethod: 'mixed', debtGiven: 0, payments: [{ kind: 'cash', amount: 40000 }, { kind: 'account', amount: 60000 }] };
  assert.equal(cd.orphanCredit(sale('a', mixed)), true, 'ردیفِ account ِ پرداختِ ترکیبی');
  assert.equal(cd.orphanCredit(sale('a', { ...mixed, remaining: 0 })), false);
  assert.equal(cd.orphanCredit(null), false);
  assert.equal(cd.orphanCredit(sale('a', { debtGiven: '600', remaining: 'x', paymentMethod: 'cash' })), false, 'رشته عدد نیست');
});

test('push ِ v1: نسیهٔ بی مشتری پذیرفته و دست‌نخورده می‌نشیند و یک سابقه می‌گیرد؛ تکرار همان یکی', async () => {
  const r = await push([op('sales', 'c1', 'insert', sale('c1'))]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => x.status), ['applied']);
  const row = (await query("SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='sales' AND row_id='c1'", [shopId])).rows[0];
  assert.equal(row.data.debtGiven, 600);
  assert.equal(row.data.debtorId, null);
  let f = await flags();
  assert.deepEqual(f.map((x) => [x.sale, x.path, x.problem]), [['c1', 'v1', 'credit_no_debtor']]);
  assert.equal(f[0].debtGiven, 600);
  //  ویرایشِ همان فاکتور (هنوز بی مشتری) ⇒ سابقهٔ دوم نه
  assert.equal((await push([op('sales', 'c1', 'update', { seller: 'دیگر' })])).status, 200);
  f = await flags();
  assert.equal(f.length, 1);
});

test('push ِ v1: نسیه با مشتری، نقد، لغو و «به حساب» با مشتری ⇒ هیچ سابقه؛ برداشتنِ مشتری ⇒ سابقه', async () => {
  const before = (await flags()).length;
  const r = await push([
    op('sales', 'ok1', 'insert', sale('ok1', { debtorId: 'd1' })),
    op('sales', 'ok2', 'insert', sale('ok2', { paymentMethod: 'cash', paidAmount: 1000, remaining: 0, debtGiven: 0 })),
    op('sales', 'ok3', 'insert', sale('ok3', { status: 'cancelled' })),
    op('sales', 'ok4', 'insert', sale('ok4', { paymentMethod: 'credit', debtorId: 'd1', payments: [{ kind: 'account', amount: 60000 }] })),
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await flags()).length, before);
  //  ویرایشی که مشتریِ فاکتورِ نسیه را خالی می‌کند — شکلِ نهایی از `sync_rows`
  assert.equal((await push([op('sales', 'ok1', 'update', { debtorId: null })])).status, 200);
  assert.deepEqual((await flags()).slice(before).map((x) => x.sale), ['ok1']);
});

test('راهِ قدیمِ `/api/sync`: پذیرفته و سابقه با `path: legacy`', async () => {
  const r = await h.api('POST', '/api/sync/push', {
    token: owner.accessToken, headers: { 'X-App': 'shop' },
    body: { deviceId: 'old-web', changes: [{ collection: 'sales', id: 'L9', updatedAt: Date.now(), data: sale('L9') }] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 1);
  const f = (await flags()).filter((x) => x.sale === 'L9');
  assert.deepEqual(f.map((x) => x.path), ['legacy']);
});

test('verify-data: `orphan.credit_no_debtor` دفترِ موجود را می‌یابد (warn، بی نوشتن)', async () => {
  const rep = await vd.verify({ shopId, only: ['orphan.credit_no_debtor'] });
  const c = rep.checks.find((x) => x.id === 'orphan.credit_no_debtor');
  assert.ok(c, JSON.stringify(rep.checks.map((x) => x.id)));
  assert.equal(c.severity, 'warn');
  //  c1 و ok1 (مشتری برداشته شد)؛ ok2..ok4 نه
  assert.deepEqual(c.samples.map((s) => s.ref).sort(), ['sales/c1', 'sales/ok1']);
  assert.equal(rep.errors, 0);
});

test('جداییِ دکان‌ها: سابقه فقط در دکانِ خودش', async () => {
  const r = await push([op('sales', 'x1', 'insert', sale('x1'))], other.accessToken, 'dev-other');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual((await flags(otherShop)).map((x) => x.sale), ['x1']);
  assert.equal((await flags()).filter((x) => x.sale === 'x1').length, 0);
});
