'use strict';
/**
 * RPT-04 — Z ِ بسته در سرور ثابت است (`lib/finalized-sales.js`، همان پرچمِ خاموشِ `shop.doc_chain` ِ S89).
 *
 *   ردیف‌های صندوق در سابقه (`auditLog`): `shift_open` (اولِ صندوق)، `shift_close` (Z ِ ذخیره‌شده در `z` و نقدِ شمرده در
 *   `amount`)، `cash_in`/`cash_out`/`no_sale` (رویدادهای X/Z)، `shift_review` و `shift_resume`. برنامه این‌ها را فقط
 *   می‌افزاید (`PayFlow`، `CashShift`، `ShiftReview`)؛ Sync v3 هم `auditLog` را فقط‌افزودنی می‌داند (`log`). ولی v1، راهِ قدیم و
 *   بازگرداندنِ تعارض Z ِ ذخیره‌شده را سرِ جا عوض یا پاک می‌کردند. حالا مثلِ سندهای مالیِ دیگر: نشست/شناسهٔ زنجیره‌دار ⇒
 *   `doc_posted_edit_forbidden` (v1 «rejected»، راهِ قدیم ۴۰۹)؛ بی زنجیره ⇒ همان امروز + رویداد؛ پرچمِ خاموش ⇒ هیچ.
 *   ردیف‌های دیگرِ سابقه (تغییرِ قیمت، حذفِ کالا…) دست نمی‌خورند؛ اصلاحِ Z فقط با سندِ تازه (بررسیِ مدیر، `shift_review`).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const SCHEMA = require('../src/lib/sync-v1-migrations').currentVersion('shop');

const DOC = 'doc_posted_edit_forbidden';
const OLD = 'dev-z-old';

let owner;
let shopId;
let bound;       //  دستگاهِ نشستِ صاحب (زنجیره‌دار)
let clerk;       //  نشستِ دوم: دستگاهِ بی زنجیره
let seq = 0;

const opId = () => `01JZFX${String(Date.now()).slice(-8)}${String(++seq).padStart(5, '0')}`;
const op = (rowId, type, fields, ts = Date.now()) => ({ op_id: opId(), ts, table: 'auditLog', row_id: rowId, type, fields });
const push = (dev, ops, token = owner.accessToken) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: SCHEMA, caps: ['a00'], ops },
});
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const v1Row = async (id) => {
  const r = await one(
    "SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name='auditLog' AND row_id=$2",
    [shopId, id]
  );
  return r ? { ...r.data, _deleted: r.deleted_at != null } : null;
};
const restRow = async (id) => {
  const r = await one('SELECT data, deleted FROM shop_audit_log WHERE shop_id=$1 AND id=$2', [shopId, id]);
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
  body: { deviceId, changes: changes.map((c) => ({ updatedAt: 1_000_000 + (++seq), ...c })) },
});

/** همان شکلِ `PayFlow.closeRow` + `reportJson`: Z ِ کامل در `z`، نقدِ شمرده (افغانی) در `amount` */
function zRow(id, open = 'sh-open') {
  return {
    id, type: 'shift_close', date: '2026-11-10', refId: open, amount: 16790, createdAt: 1_762_760_000_000, by: 'احمد',
    notes: 'بستنِ صندوق: مورد انتظار 16,800، شمرده 16,790، کسری 10 — احمد',
    z: {
      kind: 'Z', shiftId: open, currency: 'AFN', at: 1_762_760_000_000, by: 'احمد', openedAt: 1_762_730_000_000, openedBy: 'احمد',
      float: 200000, methods: { cash: { sales: 1530000, refunds: 0, receipts: 0, net: 1530000 } }, moves: { in: 0, out: 50000 },
      expected: { cash: 1680000 }, events: { sales: 3, refunds: 0, receipts: 0, cashIn: 0, cashOut: 1, noSale: 0 },
      counted: { cash: 1679000 }, denominations: { 100000: 10, 50000: 12, 10000: 7, 5000: 1, 2000: 2 },
      variance: { cash: -1000 }, reason: null, needsReview: true, blind: true,
    },
  };
}
/** هر ردیفِ صندوق با ویرایشِ مالی ([fix]) */
const SHIFT = {
  shift_open: { row: (id) => ({ id, type: 'shift_open', date: '2026-11-10', amount: 2000, createdAt: 5, notes: 'باز کردنِ صندوق با 2,000 افغانی' }), fix: { amount: 20 } },
  shift_close: { row: (id) => zRow(id), fix: { z: { ...zRow('x').z, counted: { cash: 1680000 }, variance: { cash: 0 }, needsReview: false } } },
  cash_out: { row: (id) => ({ id, type: 'cash_out', refId: 'sh-open', amount: 500, createdAt: 6, reason: 'petty_expense', notes: 'خرید شیر' }), fix: { amount: 5 } },
  no_sale: { row: (id) => ({ id, type: 'no_sale', refId: 'sh-open', createdAt: 7, reason: 'پول خرد', approvedBy: 'مدیر' }), fix: { approvedBy: 'خودم' } },
  shift_review: { row: (id) => ({ id, type: 'shift_review', refId: 'z-1', createdAt: 8, by: 'صاحب', note: 'شمارش دوباره' }), fix: { note: 'هیچ' } },
};

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ Z ِ ثابت');
  shopId = (await h.post('/api/shop', { name: 'دکانِ Z' }, { token: owner.accessToken })).body.shop.id;
  bound = (await devicesOf(owner.user.id))[0];
  clerk = await h.signIn(owner);
  assert.equal(new Set(await devicesOf(owner.user.id)).size, 2, 'نشستِ دوم دستگاهِ جدا دارد');
  await query(
    `INSERT INTO device_chain_heads (shop_id, series, epoch, device_id, reported_at, created_at) VALUES ($1,'Z1',1,$2,1,1)`,
    [shopId, bound]
  );
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'z-arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش: Z و ردیف‌های صندوق همان امروز ویرایش و حذف می‌شوند (هیچ رویدادی)', async () => {
  const id = 'off-z';
  let r = await push(bound, [op(id, 'insert', zRow(id))]);
  assert.deepEqual(statuses(r), ['applied'], JSON.stringify(r.body));
  r = await push(bound, [op(id, 'update', { amount: 16800 }), op(id, 'delete', null)]);
  assert.deepEqual(statuses(r), ['applied', 'applied']);
  assert.deepEqual(await events(id), []);
});

test('v1، نشستِ زنجیره‌دار: Z و ردیف‌های صندوقِ ثبت‌شده ویرایش و حذف نمی‌شوند؛ بازپخشِ همان ردیف بی بازنویسی', async () => {
  await flag(true);
  for (const [type, d] of Object.entries(SHIFT)) {
    const id = `v1-${type}`;
    let r = await push(bound, [op(id, 'insert', d.row(id))]);
    assert.deepEqual(statuses(r), ['applied'], `${type}: ثبتِ تازه آزاد است`);
    //  شناسهٔ ساختگی در بدنه، همان نشستِ زنجیره‌دار
    r = await push('dev-z-spoof', [op(id, 'update', d.fix)]);
    assert.deepEqual(statuses(r), [`rejected:${DOC}`], `${type}: ویرایش`);
    r = await push(bound, [op(id, 'delete', null)]);
    assert.deepEqual(statuses(r), [`rejected:${DOC}`], `${type}: حذف`);
    r = await push(bound, [op(id, 'update', { type: 'price_change' })]);
    assert.deepEqual(statuses(r), [`rejected:${DOC}`], `${type}: عوض کردنِ نوع`);
    //  فیلدِ بیرونِ فهرست (مُهر و حلقهٔ زنجیرهٔ سابقه، STF-09) آزاد
    r = await push(bound, [op(id, 'update', { userRole: 'owner' })]);
    assert.deepEqual(statuses(r), ['applied'], `${type}: مُهر`);
    r = await push(bound, [op(id, 'insert', d.row(id)), op(id, 'insert', { ...d.row(id), ...d.fix })]);
    assert.deepEqual(statuses(r), ['duplicate', `rejected:${DOC}`], `${type}: بازپخش`);
    const row = await v1Row(id);
    assert.equal(row._deleted, false, `${type}: حذف ننشست`);
    for (const [k, v] of Object.entries(d.row(id))) assert.deepEqual(row[k], v, `${type}.${k}`);
    assert.equal(row.userRole, 'owner', `${type}: بازپخش مُهر را برنگرداند`);
    const ev = await events(id);
    assert.deepEqual(ev.map((e) => [e.type, e.path, e.enforced]), Array(4).fill(['auditLog', 'v1', true]), type);
    assert.match(ev[1].edits, /delete: \*delete\*/);
  }
  //  Z ِ بسته: شمرده و اختلاف همان، نه «بی اختلاف» ِ بعدی
  assert.deepEqual((await v1Row('v1-shift_close')).z.variance, { cash: -1000 });
});

test('v1: ردیف‌های دیگرِ سابقه همان امروز آزادند', async () => {
  const id = 'v1-other';
  let r = await push(bound, [op(id, 'insert', { id, type: 'delete_product', refId: 'p1', createdAt: 9, notes: 'حذف' })]);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(bound, [op(id, 'update', { notes: 'حذف — دلیل: تکراری' })]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.equal((await v1Row(id)).notes, 'حذف — دلیل: تکراری');
  r = await push(bound, [op(id, 'delete', null)]);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual(await events(id), []);
});

test('v1، بی زنجیره: همان امروز + رویداد (F0-02)؛ زنده کردنِ Z ِ حذف‌شده برای زنجیره‌دار رد', async () => {
  const id = 'old-z';
  let r = await push(OLD, [op(id, 'insert', zRow(id))], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(OLD, [op(id, 'update', { amount: 16800 })], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  r = await push(OLD, [op(id, 'delete', null)], clerk.accessToken);
  assert.deepEqual(statuses(r), ['applied']);
  assert.deepEqual((await events(id)).map((e) => [e.type, e.path, e.enforced]), [['auditLog', 'v1', false], ['auditLog', 'v1', false]]);
  r = await push(bound, [op(id, 'update', { amount: 1 })]);
  assert.deepEqual(statuses(r), [`rejected:${DOC}`], 'Z ِ حذف‌شده سرِ جا زنده نمی‌شود');
  const row = await v1Row(id);
  assert.deepEqual([row._deleted, row.amount], [true, 16800]);
  assert.ok((await events(id)).some((e) => e.enforced && /\*revive\*/.test(e.edits)));
});

test('راهِ قدیم: نشستِ زنجیره‌دار ۴۰۹ روی ویرایش و حذفِ Z؛ بی زنجیره همان امروز + رویداد', async () => {
  let r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'auditLog', id: 'L-z', data: zRow('L-z') }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'auditLog', id: 'L-z', data: { ...zRow('L-z'), amount: 16800 } }]);
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'auditLog', id: 'L-z', deleted: true }]);
  assert.equal(r.status, 409);
  r = await legacy(owner.accessToken, 'old-phone', [{ collection: 'auditLog', id: 'L-z', data: { ...zRow('L-z'), userRole: 'owner' } }]);
  assert.equal(r.status, 200, 'فیلدِ آزاد');
  const z = await restRow('L-z');
  assert.deepEqual([z.amount, z._deleted, z.z.counted], [16790, false, { cash: 1679000 }]);

  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'auditLog', id: 'L-o', data: SHIFT.shift_open.row('L-o') }]);
  assert.equal(r.status, 200);
  r = await legacy(clerk.accessToken, 'old-web', [{ collection: 'auditLog', id: 'L-o', data: { ...SHIFT.shift_open.row('L-o'), amount: 9 } }]);
  assert.equal(r.status, 200);
  assert.equal((await restRow('L-o')).amount, 9);
  assert.deepEqual((await events('L-z')).map((e) => [e.type, e.path, e.enforced]), [['auditLog', 'legacy', true], ['auditLog', 'legacy', true]]);
  assert.deepEqual((await events('L-o')).map((e) => [e.type, e.path, e.enforced]), [['auditLog', 'legacy', false]]);
});

test('بازگرداندنِ تعارض: مقدارِ بازندهٔ Z ِ بسته ۴۰۹؛ ردیفِ دیگرِ سابقه همان امروز', async () => {
  const token = (await h.post('/api/admin/login', { username: 'z-arshad', password: 'Admin!12345' })).body.token;
  const conflict = async (row, field, loser) => (await one(
    `INSERT INTO sync_conflicts (app, account_kind, account_id, table_name, row_id, field, loser_value, winner_value,
                                 loser_op_id, winner_op_id, loser_device, winner_device, at)
     VALUES ('shop','shop',$1,'auditLog',$2,$3,$4::jsonb,'null'::jsonb,'o1','o2',$5,$6,$7) RETURNING id`,
    [shopId, row, field, JSON.stringify(loser), OLD, bound, now()]
  )).id;
  const restore = (id) => h.post(`/api/admin/sync/conflicts/${id}/restore`, { reason: 'درخواستِ صاحب' }, { token });

  let r = await restore(await conflict('v1-shift_close', 'amount', 16800));
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, DOC);
  assert.equal((await v1Row('v1-shift_close')).amount, 16790);
  const other = 'v1-price';
  assert.deepEqual(statuses(await push(bound, [op(other, 'insert', { id: other, type: 'price_change', refId: 'p1', createdAt: 9 })])), ['applied']);
  r = await restore(await conflict(other, 'notes', 'کهنه'));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok((await events('v1-shift_close')).some((e) => e.path === 'admin' && e.enforced));
});
