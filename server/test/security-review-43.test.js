'use strict';
/**
 * بازبینیِ امنیتیِ دومِ دستهٔ ۴۳ (SEC-03/S89 ِ `claude/posted-docs`، `lib/finalized-sales.js`، پرچمِ `shop.doc_chain`).
 *
 *   SR43-1  ادغامِ «خیالی»: نگهبان ادغام‌های دسته را از opهای خام می‌خواند، پیش از آن‌که `push` ِ v1 یا راهِ قدیم بگوید
 *           کدام op واقعاً می‌نشیند. یک op ِ `debtors … mergedInto` که هرگز نمی‌نشیند (شناسهٔ op ِ بد، op_id ِ تکراری،
 *           ردیفِ کهنهٔ راهِ قدیم، یا ردیفِ کاملِ بعدی در همان دسته که ادغام را برمی‌دارد) قرض/رسید یا فاکتورِ نسیهٔ
 *           ثبت‌شده را بی ادغام به مشتریِ دیگری می‌برد — بی هیچ رویدادی.
 *   SR43-2  `$inc` ِ لفظی در نوشتنِ کلِ ردیف (راهِ قدیم و API ِ مستقیم): آن‌جا `$inc` دلتا نیست، خودِ مقدار است و همان‌طور
 *           در جدول می‌نشیند؛ نگهبان آن را دلتا می‌خواند (`{$inc:0}` = «همان»، شمارندهٔ خرید = «آزاد») ⇒ مبلغ و تعدادِ
 *           سندِ ثبت‌شده با شیءِ `{"$inc":…}` جایگزین می‌شد (خواننده‌ها ⇒ ۰؛ مثلاً سقفِ برگشتِ هر قلم دوباره باز).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const DOC = 'doc_posted_edit_forbidden';
const SALE = 'sale_finalized_edit_forbidden';

let owner;
let shopId;
let bound;       //  دستگاهِ نشستِ صاحب (زنجیره‌دار ⇒ `enforce`)
let clerk;       //  نشستِ دوم (API ِ مستقیم همیشه `enforce`)
let seq = 0;

const opId = () => `01JSRB${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (ops) => h.api('POST', '/api/sync/v1/push', {
  token: owner.accessToken, headers: { 'X-App': 'shop', 'X-Device': bound },
  body: { device_id: bound, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const v1Row = async (table, id) => (await one(
  "SELECT data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3",
  [shopId, table, id]
))?.data || null;
const LEGACY = { sales: 'sales', returns: 'sale_returns', transactions: 'payments', purchases: 'purchases', debtors: 'debtors' };
const restRow = async (collection, id) => (await one(`SELECT data FROM ${LEGACY[collection]} WHERE shop_id=$1 AND id=$2`, [shopId, id]))?.data || null;
const legacy = (changes) => h.api('POST', '/api/sync/push', {
  token: owner.accessToken, headers: { 'X-App': 'shop' },
  body: { deviceId: 'old-phone', changes: changes.map((c) => ({ updatedAt: 2_000_000 + (++seq), ...c })) },
});
const api = (method, path, body) => h.api(method, path, { token: clerk.accessToken, headers: { 'X-App': 'shop' }, body });
const events = async (id) => (await query(
  "SELECT detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 AND target_id=$2",
  [shopId, id]
)).rows.map((r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail)).filter((e) => e.problem === 'finalized_edit');

const txRow = (id, debtorId) => ({ id, debtorId, type: 'receive', amount: 500, date: '2026-11-01', notes: '', createdAt: 5 });
const creditSale = (id, debtorId) => ({
  id, invoiceNumber: 77, series: 'D1', total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
  paymentMethod: 'credit', paidAmount: 0, remaining: 1000, status: 'completed', debtGiven: 1000, debtSettled: 0,
  createdAt: 5, date: '2026-11-01', cancelReason: '', seller: 'صاحب', channel: 'pos', debtorId,
});

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ بازبینیِ ۴۳');
  shopId = (await h.post('/api/shop', { name: 'دکانِ بازبینیِ ۴۳' }, { token: owner.accessToken })).body.shop.id;
  bound = (await query('SELECT device_uid FROM devices WHERE user_id=$1', [owner.user.id])).rows[0].device_uid;
  clerk = await h.signIn(owner);
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'D1',1,$2,1,1)`,
    [shopId, bound]
  );
  await query(
    `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain','true',1)
     ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId]
  );
});
test.after(async () => { await h.stop(); });

test('SR43-1 v1: ادغامِ «خیالی» (op ِ ردشده یا تکراری) قرض/رسید و فاکتورِ نسیهٔ ثبت‌شده را جابه‌جا نمی‌کند', async () => {
  let r = await push([
    op('transactions', 'p-tx', 'insert', txRow('p-tx', 'p-deb-a')),
    op('sales', 'p-s', 'insert', creditSale('p-s', 'p-deb-a')),
    op('debtors', 'p-deb-c', 'insert', { name: 'ج' }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);

  //  ۱) op ِ ادغام با شناسهٔ بد: `push` ردش می‌کند و هرگز نمی‌نشیند
  r = await push([
    { ...op('debtors', 'p-deb-a', 'update', { mergedInto: 'p-deb-b' }), op_id: 'نادرست!' },
    op('transactions', 'p-tx', 'update', { debtorId: 'p-deb-b' }),
    op('sales', 'p-s', 'update', { debtorId: 'p-deb-b' }),
  ]);
  assert.deepEqual(statuses(r), ['rejected:bad_op_id', `rejected:${DOC}`, `rejected:${SALE}`]);

  //  ۲) op_id ِ تکراری (همان op_id ِ درجِ پیشینِ «ج»): «duplicate» و هرگز نمی‌نشیند
  const taken = (await one("SELECT op_id FROM oplog WHERE account_id=$1 AND table_name='debtors' AND row_id='p-deb-c'", [shopId])).op_id;
  r = await push([
    op('transactions', 'p-tx', 'update', { debtorId: 'p-deb-b' }),
    { ...op('debtors', 'p-deb-a', 'update', { mergedInto: 'p-deb-b' }), op_id: taken },
  ]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`, 'duplicate']);

  assert.equal((await v1Row('transactions', 'p-tx')).debtorId, 'p-deb-a');
  assert.equal((await v1Row('sales', 'p-s')).debtorId, 'p-deb-a');
  assert.equal(await v1Row('debtors', 'p-deb-a'), null, 'ادغامی ننشست');

  //  ادغامِ واقعی (پیش یا پس از جابه‌جایی، همان دسته) همان امروز آزاد است — CRM-10
  r = await push([
    op('transactions', 'p-tx', 'update', { debtorId: 'p-deb-b' }),
    op('debtors', 'p-deb-a', 'update', { mergedInto: 'p-deb-b' }),
    op('sales', 'p-s', 'update', { debtorId: 'p-deb-b' }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied', 'applied']);
  assert.equal((await v1Row('transactions', 'p-tx')).debtorId, 'p-deb-b');
});

test('SR43-1 راهِ قدیم: ادغامِ کهنه (`stale`) یا برداشته در همان دسته جابه‌جایی را نمی‌پوشاند؛ ادغامِ واقعی آزاد', async () => {
  let r = await legacy([
    { collection: 'debtors', id: 'L-deb-a', data: { id: 'L-deb-a', name: 'الف' } },
    { collection: 'debtors', id: 'L-deb-b', data: { id: 'L-deb-b', name: 'ب' } },
    { collection: 'transactions', id: 'L-tx', data: txRow('L-tx', 'L-deb-a') },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));

  //  ۱) ادغامِ کهنه: راهِ قدیم آن را `stale` کنار می‌گذارد (همان مشتری تازه‌تر است) ولی نگهبان آن را «ادغام» می‌شمرد
  r = await legacy([
    { collection: 'debtors', id: 'L-deb-a', updatedAt: 1, data: { id: 'L-deb-a', name: 'الف', mergedInto: 'L-deb-b' } },
    { collection: 'transactions', id: 'L-tx', data: txRow('L-tx', 'L-deb-b') },
  ]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);

  //  ۲) ردیفِ کاملِ بعدیِ همان مشتری در همان دسته ادغام را برمی‌دارد (راهِ قدیم کلِ ردیف را جایگزین می‌کند)
  r = await legacy([
    { collection: 'debtors', id: 'L-deb-a', data: { id: 'L-deb-a', name: 'الف', mergedInto: 'L-deb-b' } },
    { collection: 'transactions', id: 'L-tx', data: txRow('L-tx', 'L-deb-b') },
    { collection: 'debtors', id: 'L-deb-a', data: { id: 'L-deb-a', name: 'الف' } },
  ]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  assert.equal((await restRow('transactions', 'L-tx')).debtorId, 'L-deb-a');
  assert.equal((await restRow('debtors', 'L-deb-a')).mergedInto, undefined, 'ادغامی ننشست');
  assert.ok((await events('L-tx')).length >= 2, 'هر تلاش رویداد دارد');

  //  ادغامِ واقعی در همان دسته همان امروز آزاد
  r = await legacy([
    { collection: 'transactions', id: 'L-tx', data: txRow('L-tx', 'L-deb-b') },
    { collection: 'debtors', id: 'L-deb-a', data: { id: 'L-deb-a', name: 'الف', mergedInto: 'L-deb-b' } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await restRow('transactions', 'L-tx')).debtorId, 'L-deb-b');
});

test('SR43-2 API ِ مستقیم: `$inc` ِ لفظی روی مبلغ/تعدادِ سندِ ثبت‌شده ۴۰۹ و ردیف همان می‌ماند', async () => {
  const ret = { id: 'I-r', saleId: 's-x', productId: 'p1', quantity: 1, amount: 100, reason: 'خراب', date: '2026-11-01', createdAt: 5 };
  let r = await api('POST', '/api/returns', { id: 'I-r', data: ret });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  r = await api('PUT', '/api/returns/I-r', { data: { ...ret, quantity: { $inc: 0 }, amount: { $inc: 0 } } });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  assert.deepEqual([(await restRow('returns', 'I-r')).quantity, (await restRow('returns', 'I-r')).amount], [1, 100]);

  //  شمارندهٔ خرید فقط در v1 دلتاست؛ این‌جا `{"$inc":-300}` خودِ مقدارِ بدهی می‌شد
  const pur = {
    id: 'I-p', productId: 'p1', supplierId: 'sup-1', quantity: 10, unit: 'دانه', purchasePrice: 30, totalAmount: 300,
    date: '2026-11-01', notes: '', paidAmount: 0, debt: 300, createdAt: 5,
  };
  r = await api('POST', '/api/purchases', { id: 'I-p', data: pur });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  r = await api('PUT', '/api/purchases/I-p', { data: { ...pur, debt: { $inc: -300 }, paidAmount: { $inc: 300 } } });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  assert.equal((await restRow('purchases', 'I-p')).debt, 300);

  //  فاکتور: همان (`{$inc:0}` «همان» شمرده می‌شد)
  const s = { ...creditSale('I-s', ''), paymentMethod: 'cash', paidAmount: 1000, remaining: 0, debtGiven: 0 };
  r = await api('POST', '/api/sales', { id: 'I-s', data: s });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  r = await api('PUT', '/api/sales/I-s', { data: { ...s, finalTotal: { $inc: 0 }, paidAmount: { $inc: 0 } } });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, SALE);
  assert.equal((await restRow('sales', 'I-s')).finalTotal, 1000);

  //  فیلدِ آزاد همان امروز
  r = await api('PUT', '/api/returns/I-r', { data: { ...ret, reason: 'شکسته' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
});
