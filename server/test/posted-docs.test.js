'use strict';
/**
 * S89 / SEC-03 (§۶.۱۳، §۱۹.۱۲) — سندهای مالیِ دیگر و «زنده کردنِ» سندِ حذف‌شده (`lib/finalized-sales.js`، پرچمِ `shop.doc_chain`).
 *
 *   ۱) برگشت، قرض/رسید، خرید و حرکتِ انبار: ویرایشِ مالی و حذفِ سرِ جای سندِ ثبت‌شده برای نشست/شناسهٔ زنجیره‌دار رد
 *      (`doc_posted_edit_forbidden`؛ v1 «rejected»، راهِ قدیم و API ۴۰۹ و هیچ نوشتنی)؛ فیلدِ آزاد، دلتای شمارنده
 *      (`paidAmount`/`debt` ِ خرید) و ادغامِ مشتری (CRM-10) آزاد؛ درجِ دوبارهٔ همان سند بی بازنویسی.
 *      بی زنجیره ⇒ همان امروز + رویداد؛ پرچمِ خاموش ⇒ هیچ.
 *   ۲) زنده کردن: نوشتن روی فاکتور/سندِ ثبت‌شده‌ای که حذف شده بود پیش‌تر «ثبتِ تازه» دیده می‌شد و با محتوای تازه
 *      زنده می‌شد. حالا همان ویرایشِ سندِ ثبت‌شده است (`*revive*`): زنجیره‌دار رد و ردیف حذف‌شده می‌ماند.
 *   ۳) بازگرداندنِ تعارضِ مدیرِ ارشد: سندِ مالیِ دیگر و فاکتورِ حذف‌شده هم ۴۰۹.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const DOC = 'doc_posted_edit_forbidden';
const SALE = 'sale_finalized_edit_forbidden';
const OLD = 'dev-doc-old';

let owner;
let shopId;
let bound;       //  دستگاهِ نشستِ صاحب (زنجیره‌دار)
let clerk;       //  نشستِ دوم: دستگاهِ بی زنجیره
let seq = 0;

const opId = () => `01JDOC${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (table, rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table, row_id: rowId, type, fields });
const push = (dev, ops, token = owner.accessToken) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const v1Row = async (table, id) => {
  const r = await one(
    "SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3",
    [shopId, table, id]
  );
  return r ? { ...r.data, _deleted: r.deleted_at != null } : null;
};
const LEGACY = { sales: 'sales', returns: 'sale_returns', transactions: 'payments', purchases: 'purchases', stockMovements: 'stock_movements' };
const restRow = async (collection, id) => {
  const r = await one(`SELECT data, deleted FROM ${LEGACY[collection]} WHERE shop_id=$1 AND id=$2`, [shopId, id]);
  return r ? { ...r.data, _deleted: r.deleted === true } : null;
};
const flag = (on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.doc_chain',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId, on ? 'true' : 'false']
);
const events = async (id) => (await query(
  "SELECT target_type, detail FROM audit_logs WHERE action='security.doc_chain' AND shop_id=$1 AND target_id=$2 ORDER BY created_at, id",
  [shopId, id]
)).rows.map((r) => ({ type: r.target_type, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }))
  .filter((e) => e.problem === 'finalized_edit');
const devicesOf = async (userId) => (await query('SELECT device_uid FROM devices WHERE user_id=$1', [userId])).rows.map((r) => r.device_uid);
const legacy = (token, deviceId, changes) => h.api('POST', '/api/sync/push', {
  token, headers: { 'X-App': 'shop' },
  //  زمانِ گذشته ولی رو به جلو: نوشتنِ بعدیِ API (`now()`) «کهنه» (`stale`) نمی‌شود و به نگهبان می‌رسد
  body: { deviceId, changes: changes.map((c) => ({ updatedAt: 1_000_000 + (++seq), ...c })) },
});

/** هر سندِ مالی با فیلدِ مالی ([fix]) و فیلدِ آزاد ([free]) برای ویرایش */
const DOCS = {
  returns: {
    row: (id) => ({ id, saleId: 's-x', productId: 'p1', quantity: 1, amount: 100, reason: 'خراب', date: '2026-11-01', createdAt: 5 }),
    fix: { amount: 1 }, free: { reason: 'شکسته' },
  },
  transactions: {
    row: (id) => ({ id, debtorId: 'deb-a', type: 'give', amount: 500, date: '2026-11-01', notes: '', createdAt: 5 }),
    fix: { amount: 1 }, free: { dueDate: '2026-12-01', notes: 'یادداشت' },
  },
  purchases: {
    row: (id) => ({
      id, productId: 'p1', supplierId: 'sup-1', quantity: 10, unit: 'دانه', purchasePrice: 20, totalAmount: 200,
      date: '2026-11-01', notes: '', paidAmount: 200, debt: 0, createdAt: 5,
    }),
    fix: { totalAmount: 1 }, free: { invoiceNo: 4, landedCost: 3 },
  },
  stockMovements: {
    row: (id) => ({ id, productId: 'p1', type: 'adjustment', qty: 4, date: '2026-11-01', notes: '', refId: 'r-1', createdAt: 5 }),
    fix: { qty: 400 }, free: { notes: 'شمارش', reason: 'شمارش' },
  },
};
function sale(id, extra = {}) {
  return {
    id, invoiceNumber: 31, series: 'D1', total: 1000, discountType: 'amount', discountValue: 0, discount: 0, finalTotal: 1000,
    paymentMethod: 'cash', paidAmount: 1000, remaining: 0, status: 'completed', debtGiven: 0, debtSettled: 0,
    createdAt: 5, date: '2026-11-01', cancelReason: '', seller: 'صاحب', channel: 'pos', ...extra,
  };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ سندهای ثبت‌شده');
  shopId = (await h.post('/api/shop', { name: 'دکانِ سندها' }, { token: owner.accessToken })).body.shop.id;
  bound = (await devicesOf(owner.user.id))[0];
  clerk = await h.signIn(owner);
  assert.equal(new Set(await devicesOf(owner.user.id)).size, 2, 'نشستِ دوم دستگاهِ جدا دارد');
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'D1',1,$2,1,1)`,
    [shopId, bound]
  );
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'doc-arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: ویرایش و حذفِ سندهای مالی همان امروز (هیچ رویدادی)', async () => {
  for (const [table, d] of Object.entries(DOCS)) {
    const id = `off-${table}`;
    let r = await push(bound, [op(table, id, 'insert', d.row(id))]);
    assert.deepEqual(statuses(r), ['applied'], JSON.stringify(r.body));
    r = await push(bound, [op(table, id, 'update', d.fix), op(table, id, 'delete', null)]);
    assert.deepEqual(statuses(r), ['applied', 'applied'], table);
    assert.deepEqual(await events(id), []);
  }
});

test('v1، نشستِ زنجیره‌دار: ویرایشِ مالی و حذفِ سندِ ثبت‌شده رد؛ فیلدِ آزاد و درجِ تازه آزاد', async () => {
  await flag(true);
  for (const [table, d] of Object.entries(DOCS)) {
    const id = `v1-${table}`;
    let r = await push(bound, [op(table, id, 'insert', d.row(id))]);
    assert.deepEqual(statuses(r), ['applied'], `${table}: ثبتِ تازه آزاد است`);
    //  شناسهٔ ساختگی در بدنه، همان نشستِ زنجیره‌دار
    r = await push('dev-doc-spoof', [op(table, id, 'update', d.fix)]);
    assert.deepEqual(statuses(r), [`rejected:${DOC}`], `${table}: ویرایشِ مالی`);
    r = await push(bound, [op(table, id, 'delete', null)]);
    assert.deepEqual(statuses(r), [`rejected:${DOC}`], `${table}: حذف`);
    r = await push(bound, [op(table, id, 'update', d.free)]);
    assert.deepEqual(statuses(r), ['applied'], `${table}: فیلدِ آزاد`);
    const row = await v1Row(table, id);
    assert.equal(row._deleted, false, `${table}: حذف ننشست`);
    for (const [k, v] of Object.entries(d.row(id))) assert.deepEqual(row[k] ?? null, d.free[k] ?? v ?? null, `${table}.${k}`);
    //  درجِ دوبارهٔ همان سند (بازپخش) بی بازنویسی؛ نسخهٔ دیگرِ مالی رد
    r = await push(bound, [op(table, id, 'insert', d.row(id)), op(table, id, 'insert', { ...d.row(id), ...d.fix })]);
    assert.deepEqual(statuses(r), ['duplicate', `rejected:${DOC}`], table);
    assert.deepEqual((await v1Row(table, id))[Object.keys(d.free)[0]], Object.values(d.free)[0], `${table}: بازپخش فیلدِ آزاد را برنگرداند`);
    const ev = await events(id);
    assert.deepEqual(ev.map((e) => [e.type, e.path, e.enforced]), Array(3).fill([table, 'v1', true]), table);
    assert.match(ev[1].edits, /delete: \*delete\*/);
  }
  //  شمارندهٔ خرید (دلتا، همان `counters` ِ v3) آزاد؛ نوشتنِ مقدارِ تازه نه
  let r = await push(bound, [op('purchases', 'v1-purchases', 'update', { paidAmount: { $inc: -50 }, debt: { $inc: 50 } })]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op('purchases', 'v1-purchases', 'update', { debt: 0 })]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`]);
  assert.equal((await v1Row('purchases', 'v1-purchases')).debt, 50);
  //  دلتا روی فیلدِ مالیِ غیرشمارنده جمع می‌شود و همان ویرایش است
  r = await push(bound, [op('transactions', 'v1-transactions', 'update', { amount: { $inc: 1 } })]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`]);
  assert.equal((await v1Row('transactions', 'v1-transactions')).amount, 500);
});

test('v1: ادغامِ مشتری (CRM-10) قرض را جابه‌جا می‌کند؛ مشتریِ دیگر بی ادغام نه', async () => {
  let r = await push(bound, [op('transactions', 'tx-m', 'insert', DOCS.transactions.row('tx-m'))]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op('transactions', 'tx-m', 'update', { debtorId: 'deb-z' })]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`]);
  r = await push(bound, [
    op('debtors', 'deb-a', 'update', { name: 'الف', mergedInto: 'deb-b' }),
    op('transactions', 'tx-m', 'update', { debtorId: 'deb-b' }),
  ]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.equal((await v1Row('transactions', 'tx-m')).debtorId, 'deb-b');
});

test('v1، بی زنجیره: همان امروز + رویداد (F0-02)', async () => {
  const id = 'old-transactions';
  let r = await push(OLD, [op('transactions', id, 'insert', DOCS.transactions.row(id))], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(OLD, [op('transactions', id, 'update', { amount: 7 })], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('transactions', id)).amount, 7);
  r = await push(OLD, [op('transactions', id, 'delete', null)], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual((await events(id)).map((e) => [e.type, e.path, e.enforced]), [['transactions', 'v1', false], ['transactions', 'v1', false]]);
});

test('v1، زنده کردن: نوشتن روی فاکتور/سندِ ثبت‌شدهٔ حذف‌شده برای زنجیره‌دار رد و ردیف حذف‌شده می‌ماند', async () => {
  //  حذف از برنامهٔ بی زنجیره (`log`) — همان راهی که امروز تنها راهِ حذفِ فاکتورِ ثبت‌شده است
  let r = await push(OLD, [op('sales', 'rv-s', 'insert', sale('rv-s')), op('transactions', 'rv-t', 'insert', DOCS.transactions.row('rv-t'))], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  r = await push(OLD, [op('sales', 'rv-s', 'delete', null), op('transactions', 'rv-t', 'delete', null)], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied', 'applied']);

  r = await push(bound, [op('sales', 'rv-s', 'update', { total: 5, finalTotal: 5, paidAmount: 5 })]);
  assert.deepEqual(statuses(r), [`rejected:${SALE}`], 'ویرایشِ فاکتورِ حذف‌شده «ثبتِ تازه» نیست');
  r = await push(bound, [op('sales', 'rv-s', 'update', { seller: 'دیگری' })]);
  assert.deepEqual(statuses(r), [`rejected:${SALE}`], 'سندِ حذف‌شده سرِ جا زنده نمی‌شود');
  r = await push(bound, [op('transactions', 'rv-t', 'update', { amount: 1 })]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`]);
  //  درجِ دوبارهٔ همان سند (بازپخش) بی بازنویسی؛ همان محتوا با مبلغِ دیگر رد
  r = await push(bound, [op('sales', 'rv-s', 'insert', sale('rv-s')), op('sales', 'rv-s', 'insert', sale('rv-s', { total: 9, finalTotal: 9 }))]);
  assert.deepEqual(statuses(r), ['duplicate', `rejected:${SALE}`]);
  const s = await v1Row('sales', 'rv-s');
  assert.deepEqual([s._deleted, s.finalTotal, s.seller], [true, 1000, 'صاحب']);
  const t = await v1Row('transactions', 'rv-t');
  assert.deepEqual([t._deleted, t.amount], [true, 500]);
  assert.ok((await events('rv-s')).some((e) => e.enforced && /\*revive\*/.test(e.edits)), 'رویدادِ زنده کردن');

  //  سنگِ قبرِ بی محتوا (حذفِ ردیفی که سرور ندیده بود) سندِ ثبت‌شده نیست: ثبتِ تازه آزاد
  r = await push(OLD, [op('transactions', 'rv-tomb', 'delete', null)], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op('transactions', 'rv-tomb', 'insert', DOCS.transactions.row('rv-tomb'))]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('transactions', 'rv-tomb'))._deleted, false);

  //  بی زنجیره: همان امروز (زنده می‌شود) + رویداد
  r = await push(OLD, [op('transactions', 'rv-t', 'update', { notes: 'برگشت' })], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row('transactions', 'rv-t'))._deleted, false);
  assert.ok((await events('rv-t')).some((e) => !e.enforced && /\*revive\*/.test(e.edits)));
});

test('راهِ قدیم: نشستِ زنجیره‌دار ۴۰۹ روی ویرایش، حذف و زنده کردن؛ بی زنجیره همان امروز + رویداد', async () => {
  const d = DOCS.returns;
  let r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'returns', id: 'L-r', data: d.row('L-r') }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'returns', id: 'L-r', data: { ...d.row('L-r'), amount: 1 } }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'returns', id: 'L-r', deleted: true }]);
  assert.equal(r.status, 409);
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'returns', id: 'L-r', data: { ...d.row('L-r'), reason: 'شکسته' } }]);
  assert.equal(r.status, 200, 'فیلدِ آزاد');
  assert.deepEqual([(await restRow('returns', 'L-r')).amount, (await restRow('returns', 'L-r'))._deleted], [100, false]);

  //  زنده کردنِ فاکتورِ حذف‌شده از راهِ قدیم: پیش‌تر کلِ ردیفِ تازه می‌نشست
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'sales', id: 'L-s', data: sale('L-s') }]);
  assert.equal(r.status, 200);
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'sales', id: 'L-s', deleted: true }]);
  assert.equal(r.status, 200, 'بی زنجیره همان امروز');
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'sales', id: 'L-s', data: sale('L-s', { total: 2, finalTotal: 2 }) }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, SALE);
  const s = await restRow('sales', 'L-s');
  assert.deepEqual([s._deleted, s.finalTotal], [true, 1000]);

  //  بی زنجیره ⇒ همان امروز + رویداد
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'stockMovements', id: 'L-m', data: DOCS.stockMovements.row('L-m') }]);
  assert.equal(r.status, 200);
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'stockMovements', id: 'L-m', data: { ...DOCS.stockMovements.row('L-m'), qty: 9 } }]);
  assert.equal(r.status, 200);
  assert.equal((await restRow('stockMovements', 'L-m')).qty, 9);
  assert.deepEqual((await events('L-r')).map((e) => [e.type, e.path, e.enforced]), [['returns', 'legacy', true], ['returns', 'legacy', true]]);
  assert.deepEqual((await events('L-m')).map((e) => [e.type, e.path, e.enforced]), [['stockMovements', 'legacy', false]]);
});

test('راهِ قدیم با نشستِ زنجیره‌دار (جای درِ مستقیمِ برداشته): ویرایش/حذفِ قرض و خرید ۴۰۹؛ بازنویسیِ حرکتِ ثبت‌شده در دستهٔ فاکتورِ کامل و زنده کردنِ فاکتور هم', async () => {
  const api = (changes) => legacy(owner.accessToken, 'old-phone', changes);
  let r = await api([{ collection: 'transactions', id: 'A-t', data: DOCS.transactions.row('A-t') }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  r = await api([{ collection: 'transactions', id: 'A-t', data: { ...DOCS.transactions.row('A-t'), amount: 1 } }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  r = await api([{ collection: 'transactions', id: 'A-t', data: { ...DOCS.transactions.row('A-t'), notes: 'یادداشت' } }]);
  assert.equal(r.status, 200, 'فیلدِ آزاد');
  r = await api([{ collection: 'purchases', id: 'A-p', data: DOCS.purchases.row('A-p') }]);
  assert.equal(r.status, 200);
  r = await api([{ collection: 'purchases', id: 'A-p', deleted: true }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal((await restRow('purchases', 'A-p'))._deleted, false);
  assert.equal((await restRow('transactions', 'A-t')).amount, 500);

  //  فاکتورِ کامل با شناسهٔ حرکتِ موجود ⇒ بازنویسیِ حرکتِ ثبت‌شده ⇒ ۴۰۹ و هیچ بخشی (فاکتورِ تازه هم نه)
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'stockMovements', id: 'A-m', data: DOCS.stockMovements.row('A-m') }]);
  assert.equal(r.status, 200);
  r = await api([
    { collection: 'sales', id: 'A-s', data: sale('A-s') },
    { collection: 'saleItems', id: 'A-s-i', data: { saleId: 'A-s', productId: 'p1', quantity: 1, unitPrice: 1000, totalPrice: 1000 } },
    { collection: 'stockMovements', id: 'A-m', data: { saleId: 'A-s', productId: 'p1', type: 'sale', qty: -1, date: '2026-11-01' } },
  ]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal((await restRow('stockMovements', 'A-m')).qty, 4);
  assert.equal(await restRow('sales', 'A-s'), null);

  //  فاکتورِ حذف‌شده با ردیفِ کامل زنده نمی‌شود
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'sales', id: 'A-d', data: sale('A-d') }]);
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'sales', id: 'A-d', deleted: true }]);
  r = await api([{ collection: 'sales', id: 'A-d', data: sale('A-d', { total: 3, finalTotal: 3 }) }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal((await restRow('sales', 'A-d'))._deleted, true);
});

test('بازگرداندنِ تعارض: فیلدِ مالیِ سندِ دیگر و زنده کردنِ فاکتورِ حذف‌شده ۴۰۹؛ فیلدِ آزاد همان امروز', async () => {
  const token = (await h.post('/api/admin/login', { username: 'doc-arshad', password: 'Admin!12345' })).body.token;
  const conflict = async (table, row, field, loser) => (await one(
    `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value, winner_value,
                                 loser_op_id, winner_op_id, loser_device, winner_device, at)
     VALUES ('shop','shop',$1,$2,$3,$4,$5::jsonb,'null'::jsonb,'o1','o2',$6,$7,$8) RETURNING id`,
    [shopId, table, row, field, JSON.stringify(loser), OLD, bound, now()]
  )).id;
  const restore = (id) => h.post(`/api/admin/sync/conflicts/${id}/restore`, { reason: 'درخواستِ صاحب' }, { token });

  let r = await restore(await conflict('returns', 'v1-returns', 'amount', 1));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  assert.equal((await v1Row('returns', 'v1-returns')).amount, 100);
  r = await restore(await conflict('returns', 'v1-returns', 'reason', 'کهنه'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await v1Row('returns', 'v1-returns')).reason, 'کهنه');
  //  فاکتورِ حذف‌شده (`rv-s`): حتی فیلدِ آزاد آن را سرِ جا زنده می‌کرد
  r = await restore(await conflict('sales', 'rv-s', 'seller', 'مدیر'));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, SALE);
  assert.equal((await v1Row('sales', 'rv-s'))._deleted, true);
  assert.ok((await events('v1-returns')).some((e) => e.path === 'admin' && e.enforced));
});
