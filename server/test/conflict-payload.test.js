'use strict';
/**
 * DATA-SAFETY (`docs/DATA-SAFETY-fa.md`، `lib/conflict-payload.js`) — فاکتورِ ردشده با همهٔ قلم‌هایش روی سرور.
 *
 * پیش‌تر صندوقِ تعارض فقط خلاصه داشت (گونه، مبلغ، سقف، قرض‌دار، شناسه‌ها)؛ قلم‌ها فقط روی همان دستگاه
 * می‌ماندند و با پاک شدن یا از نو ساخته شدنش پیش از بستنِ تعارض می‌رفتند. سنجه‌ها: v1 (همراه‌ها در همان دسته
 * و در دستهٔ بعد)، v3، دیدن از دستگاهِ دیگرِ صاحب، و جدا بودن از دکانِ دیگر. ⛔ `lib/sync-v1.js` دست نمی‌خورد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

let admin = '';
let shopId;
let owner;
let staff;
let ownerB;
let n = 0;
const oid = () => `01JDS${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now();

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const setRole = (role, perms) => h.roleLayer(owner.accessToken, role, perms);
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const boxOf = (opId) => one(`SELECT kind, detail, loser_value FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, opId]);
const ids = (box) => (box.loser_value?.ops || []).map((o) => o.op_id);

/** فاکتورِ نسیهٔ v1 با دو قلم و تراکنشِ قرض — همان ثبتِ برنامه */
function sale1(id, debt) {
  const at = (clock += 1000);
  const op = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: at, fields });
  return [
    op('sales', id, 'insert', { total: 10000, discount: 0, finalTotal: 10000, paymentMethod: 'credit', debtorId: 'd-1',
      paidAmount: 10000 - debt, remaining: debt, debtGiven: debt, debtSettled: 0, status: 'completed', createdAt: at }),
    op('saleItems', `${id}-i1`, 'insert', { saleId: id, productId: 'p-rice', quantity: 2, unitPrice: 3000, totalPrice: 6000, returnedQty: 0 }),
    op('saleItems', `${id}-i2`, 'insert', { saleId: id, productId: 'p-oil', quantity: 1, unitPrice: 4000, totalPrice: 4000, returnedQty: 0 }),
    op('transactions', `${id}-give`, 'insert', { debtorId: 'd-1', type: 'give', amount: debt, refId: id, kind: 'sale_credit', date: '2026-11-08', createdAt: at }),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ds-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'ds-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop') };
  }
  ({ id: shopId, owner, staff } = await shopWithStaff('ایمنی'));
  ({ owner: ownerB } = await shopWithStaff('ایمنیِ ب'));
  await liveFlag('shop.credit_cap', true, shopId);
  assert.equal((await setRole('staff', { 'sales.credit': { caps: { maxAmount: 500000 } } })).status, 200);
});
test.after(async () => { await h.stop(); });

test('v1: فاکتورِ ردشده با همهٔ قلم‌ها و تراکنشش کامل در صندوقِ تعارض می‌نشیند', async () => {
  const ops = sale1('ds-1a', 5000.01);
  assert.deepEqual(why(await push1(staff, 'ds-pos', ops)), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  const box = await boxOf(ops[0].op_id);
  //  پیش‌تر `loser_value` تهی بود و قلم‌ها فقط روی همان دستگاه
  assert.ok(box.loser_value, 'سندِ کامل در صندوق نیست');
  assert.equal(box.loser_value.path, 'v1');
  assert.deepEqual(ids(box), ops.map((o) => o.op_id));
  const items = box.loser_value.ops.filter((o) => o.table === 'saleItems').map((o) => [o.row_id, o.fields.productId, o.fields.quantity, o.fields.unitPrice]);
  assert.deepEqual(items, [['ds-1a-i1', 'p-rice', 2, 3000], ['ds-1a-i2', 'p-oil', 1, 4000]]);
  assert.equal(box.loser_value.ops[0].fields.debtGiven, 5000.01);
});

test('v1: همراه‌هایی که در دستهٔ بعدِ همان دستگاه رسیدند به همان ردیفِ صندوق افزوده می‌شوند', async () => {
  const ops = sale1('ds-1b', 6000);
  assert.deepEqual(why(await push1(staff, 'ds-pos', [ops[0]])), ['approval_required']);
  assert.deepEqual(why(await push1(staff, 'ds-pos', ops.slice(1))), ['tx_rejected', 'tx_rejected', 'tx_rejected']);
  const box = await boxOf(ops[0].op_id);
  assert.deepEqual(ids(box), ops.map((o) => o.op_id));
  //  همان دسته دوباره (پاسخ گم شد) تکرار نمی‌سازد
  await push1(staff, 'ds-pos', ops.slice(1));
  assert.deepEqual(ids(await boxOf(ops[0].op_id)), ops.map((o) => o.op_id));
});

test('v3: فاکتورِ ردشده با همهٔ فیلدهایش در صندوق', async () => {
  const sale = {
    op_id: oid(), hlc: H('pos'), table: 'sales', row_id: 'ds-3a', kind: 'insert',
    fields: { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 399999, remainingMinor: 600001,
      debtGivenMinor: 600001, debtorId: 'd-1', paymentMethod: 'credit', status: 'completed' },
  };
  assert.deepEqual(why(await push3(staff, 'ds-pos3', [sale])), ['approval_required']);
  const box = await boxOf(sale.op_id);
  assert.equal(box.loser_value?.path, 'v3');
  assert.deepEqual(box.loser_value.ops.map((o) => [o.op_id, o.kind, o.fields.debtGivenMinor, o.fields.totalMinor]),
    [[sale.op_id, 'insert', 600001, 1000000]]);
});

test('صاحب از دستگاهِ دیگر سندِ کامل را می‌بیند؛ دکانِ دیگر هیچ', async () => {
  const ops = sale1('ds-1c', 7000);
  await push1(staff, 'ds-pos', ops);
  const mine = await h.get('/api/sync/v3/conflicts?limit=50', { token: owner.accessToken });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  const cf = mine.body.conflicts.find((c) => c.loser_op_id === ops[0].op_id);
  assert.ok(cf, 'صندوقِ صاحب این فاکتور را ندارد');
  assert.deepEqual(cf.loser_value.ops.map((o) => o.op_id), ops.map((o) => o.op_id));

  const other = await h.get('/api/sync/v3/conflicts?limit=500&status=all', { token: ownerB.accessToken });
  assert.equal(other.status, 200, JSON.stringify(other.body));
  const leak = JSON.stringify(other.body.conflicts);
  for (const o of ops) assert.ok(!leak.includes(o.op_id), 'سندِ دکانِ دیگر دیده شد');
  assert.ok(!leak.includes('p-rice'));
});
