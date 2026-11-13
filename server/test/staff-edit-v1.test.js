'use strict';
/**
 * STF-03 — ویرایشِ پولیِ سندِ ثبت‌شده از راهِ Sync v1 (`lib/staff-edit-v1.js`).
 *
 * نگهبانِ v1 فقط درجِ فاکتور/برگشت را می‌سنجید؛ v1 هر ستون را با `update` یا `{"$inc": n}` عوض می‌کند و
 * `authorizeOp` ِ v1 فقط `Number(discount) > 0` را می‌بیند. پس شاگردِ بی تخفیف با `$inc` تخفیف می‌داد،
 * مدیرِ سقف‌دار تخفیف را پس از ثبت بالا می‌برد یا جمع را پایین می‌آورد، و سقفِ مبلغِ برگشت با بالا بردنِ
 * `amount` ِ برگشتِ ثبت‌شده دور زده می‌شد. این آزمون‌ها پیش از درمان سرخ بودند.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

let shopId;
let owner;
let manager;
let staff;
let n = 0;
const oid = () => `01JED${String(++n).padStart(21, '0')}`;
let ts = Date.now();
const op = (table, id, type, fields) => ({ op_id: oid(), table, row_id: id, type, ts: ++ts, fields });
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token: who.accessToken, body: { device_id: device, schema_version: 1, ops },
});
const results = async (who, device, ops) => {
  const r = await push1(who, device, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results.map((x) => (x.status === 'rejected' ? x.reason : x.status));
};
const rowOf = async (table, id) => (await one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
))?.data;
const boxOf = (opId) => one(
  `SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2 AND status='open'`, [shopId, opId]
);
const setRole = (role, perms) => h.roleLayer(owner.accessToken, role, perms);
const sale = (id, total, discount) => op('sales', id, 'insert', { total, discount, finalTotal: total - discount, status: 'completed' });

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ ویرایش', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ ویرایش' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ ویرایش', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
  const m = await h.newUser('مدیرِ ویرایش', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  manager = await h.signIn(m, 'shop');
  staff = await h.signIn(s, 'shop');
});
test.after(async () => { await h.stop(); });

test('آزمونِ مرجع: بی لایه همان امروز — مدیر و صاحب تخفیف و جمعِ فاکتورِ ثبت‌شده را عوض می‌کنند، شاگرد ویرایشِ بی‌پول', async () => {
  assert.deepEqual(await results(manager, 'm-0', [sale('e-0', 100, 0)]), ['applied']);
  assert.deepEqual(await results(manager, 'm-0', [op('sales', 'e-0', 'update', { discount: 60, finalTotal: 40 })]), ['applied']);
  assert.deepEqual(await results(owner, 'o-0', [op('sales', 'e-0', 'update', { discount: { $inc: 10 } })]), ['applied']);
  assert.equal((await rowOf('sales', 'e-0')).discount, 70);
  assert.deepEqual(await results(staff, 's-0', [sale('e-0s', 100, 0)]), ['applied']);
  assert.deepEqual(await results(staff, 's-0', [op('sales', 'e-0s', 'update', { paidAmount: 100, note: 'x' })]), ['applied']);
});

test('شاگردِ بی تخفیف: `$inc` روی تخفیفِ فاکتورِ ثبت‌شده ⇒ discount_not_allowed، ردیف دست‌نخورده و صندوقِ perm_denied', async () => {
  assert.deepEqual(await results(staff, 's-1', [sale('e-1', 100, 0)]), ['applied']);
  const inc = op('sales', 'e-1', 'update', { discount: { $inc: 70 } });
  assert.deepEqual(await results(staff, 's-1', [inc]), ['discount_not_allowed']);
  assert.equal((await rowOf('sales', 'e-1')).discount, 0);
  const box = await boxOf(inc.op_id);
  assert.equal(box?.kind, 'perm_denied');
  assert.equal(box.detail.path, 'v1');
  assert.equal(box.detail.edit, true);
  //  کاهش یا بی‌تغییر آزاد است
  assert.deepEqual(await results(staff, 's-1', [op('sales', 'e-1', 'update', { discount: 0, note: 'بی تغییر' })]), ['applied']);
});

test('مدیرِ سقف‌دار (۱۰٪): بالا بردنِ تخفیف (عدد یا $inc) یا پایین آوردنِ جمع پس از ثبت ⇒ approval_required؛ زیرِ سقف و کاهش می‌نشیند', async () => {
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 10 } } })).status, 200);
  try {
    assert.deepEqual(await results(manager, 'm-2', [sale('e-2', 100, 5)]), ['applied']);
    const up = op('sales', 'e-2', 'update', { discount: 60, finalTotal: 40 });
    const inc = op('sales', 'e-2', 'update', { discount: { $inc: 55 } });
    const down = op('sales', 'e-2', 'update', { total: 20, finalTotal: 15 });
    assert.deepEqual(await results(manager, 'm-2', [up]), ['approval_required']);
    assert.deepEqual(await results(manager, 'm-2', [inc]), ['approval_required']);
    assert.deepEqual(await results(manager, 'm-2', [down]), ['approval_required']);
    const row = await rowOf('sales', 'e-2');
    assert.equal(row.discount, 5);
    assert.equal(row.total, 100);
    assert.equal((await boxOf(up.op_id))?.kind, 'approval_required');
    //  ۵ از ۲۰ افغانی، با بخششِ گردکردنِ هسته (`discountBp`: ۵۰ پول) ⇒ ۲۲٫۵٪
    assert.equal((await boxOf(down.op_id)).detail.pct_bp, 2250);
    //  زیرِ سقف، کاهش، و ویرایشِ بی‌پول
    assert.deepEqual(await results(manager, 'm-2', [op('sales', 'e-2', 'update', { discount: 8, finalTotal: 92 })]), ['applied']);
    assert.deepEqual(await results(manager, 'm-2', [op('sales', 'e-2', 'update', { discount: 2, finalTotal: 98 })]), ['applied']);
    assert.deepEqual(await results(manager, 'm-2', [op('sales', 'e-2', 'update', { total: 110, finalTotal: 108 })]), ['applied']);
    //  درج و ویرایشِ همان فاکتور در یک دسته: درجِ زیرِ سقف می‌نشیند، ویرایشِ بیش از سقفِ پس از آن نه
    const ins = sale('e-2b', 100, 5);
    const over = op('sales', 'e-2b', 'update', { discount: 50 });
    assert.deepEqual(await results(manager, 'm-2', [ins, over]), ['applied', 'approval_required']);
    assert.equal((await rowOf('sales', 'e-2b')).discount, 5);
    //  تکرارِ opِ ویرایشِ پذیرفته پس از پایین آمدنِ سقف ⇒ «تکراری»ِ خودِ v1، نه رد
    const ok = op('sales', 'e-2b', 'update', { discount: 9 });
    assert.deepEqual(await results(manager, 'm-2', [ok]), ['applied']);
    assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 1 } } })).status, 200);
    assert.notEqual((await results(manager, 'm-2', [ok]))[0], 'approval_required');
  } finally {
    assert.equal((await setRole('manager', {})).status, 200);
  }
});

test('مدیرِ «ممنوع»: ویرایشِ تخفیف ⇒ discount_not_allowed؛ برگشتِ سقف‌دار: بالا بردنِ مبلغِ ثبت‌شده بیش از سقفِ تجمعیِ فاکتور ⇒ رد', async () => {
  assert.equal((await setRole('manager', {
    'sales.discount': { mode: 'deny' }, 'returns.create': { caps: { maxAmount: 1000 } },
  })).status, 200);
  try {
    assert.deepEqual(await results(owner, 'o-3', [sale('e-3', 100, 0)]), ['applied']);
    assert.deepEqual(await results(manager, 'm-3', [op('sales', 'e-3', 'update', { discount: 1 })]), ['discount_not_allowed']);
    //  برگشتِ ۵ افغانی (۵۰۰ پول) زیرِ سقفِ ۱۰۰۰ پول
    const ret = op('returns', 'e-3r', 'insert', { saleId: 'e-3', productId: 'p1', quantity: 1, amount: 5 });
    assert.deepEqual(await results(manager, 'm-3', [ret]), ['applied']);
    const big = op('returns', 'e-3r', 'update', { amount: 900 });
    const inc = op('returns', 'e-3r', 'update', { amount: { $inc: 6 } });
    assert.deepEqual(await results(manager, 'm-3', [big]), ['approval_required']);
    assert.deepEqual(await results(manager, 'm-3', [inc]), ['approval_required']);
    assert.equal((await rowOf('returns', 'e-3r')).amount, 5);
    assert.equal((await boxOf(big.op_id)).detail.cumulative, 90000);
    //  تا سقف: ۵ ⇒ ۱۰ افغانی (۱۰۰۰ پول) می‌نشیند؛ برگشتِ صاحب هم در جمعِ فاکتور است
    assert.deepEqual(await results(manager, 'm-3', [op('returns', 'e-3r', 'update', { amount: 10 })]), ['applied']);
    assert.deepEqual(await results(manager, 'm-3', [op('returns', 'e-3r', 'update', { amount: 4 })]), ['applied']);
    assert.deepEqual(await results(owner, 'o-3', [op('returns', 'e-3o', 'insert', { saleId: 'e-3', productId: 'p2', quantity: 1, amount: 6 })]), ['applied']);
    assert.deepEqual(await results(manager, 'm-3', [op('returns', 'e-3r', 'update', { amount: 5 })]), ['approval_required']);
  } finally {
    assert.equal((await setRole('manager', {})).status, 200);
  }
});
