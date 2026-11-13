'use strict';
/**
 * STF-03 — سقفِ فروشِ قرضیِ کارمند در سرور (`lib/credit-cap.js`، مجوزِ `sales.credit`، پرچمِ خاموشِ `shop.credit_cap`).
 *
 * سنجه‌ها: پرچمِ خاموش همان امروز (و سقف ذخیره نمی‌شود)؛ با پرچمِ روشن سرور تنها داور است در Sync v3 (درج و دلتای
 * `debtGivenMinor`)، Sync v1 (درج با همراه‌ها و `update` ِ بالابرنده — بی دست زدن به `lib/sync-v1.js`) و REST/راهِ قدیم؛
 * «نیازمند تأیید» با «درخواستِ تأیید» ِ STF-02 می‌نشیند، یک‌بارمصرف و بی‌خطرِ تکرار؛ «ممنوع» همان کدِ رد؛ صاحب هرگز؛
 * دکانِ دیگر نه پرچم را می‌بیند نه درخواست را.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let shopId;
let shopB;
let owner;
let manager;
let staff;
let ownerB;
let staffB;
let n = 0;
const oid = () => `01JCC${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now();

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const setRole = (role, perms, who = owner) => h.roleLayer(who.accessToken, role, perms);
//  فاکتورِ کامل در یک دستهٔ راهِ قدیم (`/api/sync`؛ جای `/api/sales/full` ِ برداشته)
const legacyFull = (who, sale, items, deviceId = 'web-full') => h.legacyPush(who.accessToken,
  [h.change('sales', sale), ...items.map((it) => h.change('saleItems', { ...it, saleId: sale.id }))], { deviceId });
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const row3 = (table, id, shop = shopId) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shop, table, id]
);
const row1 = (table, id) => one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const boxOf = (opId) => one(`SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2`, [shopId, opId]);

/** فاکتورِ v3 با «به حساب» */
const sale3 = (id, debt, extra = {}) => ({
  op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'insert',
  fields: { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 1000000 - debt, remainingMinor: debt,
    debtGivenMinor: debt, debtorId: 'd-1', paymentMethod: 'credit', status: 'completed' },
  ...extra,
});
const delta3 = (id, debt) => ({ op_id: oid(), hlc: H('pos'), table: 'sales', row_id: id, kind: 'delta', fields: { debtGivenMinor: debt } });

/** فاکتورِ نسیهٔ v1 (افغانی) با همراه‌ها — همان ثبتِ برنامه */
function sale1(id, debt) {
  const at = (clock += 1000);
  const op = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: at, fields });
  return [
    op('sales', id, 'insert', { total: 10000, discount: 0, finalTotal: 10000, paymentMethod: 'credit', debtorId: 'd-1',
      paidAmount: 10000 - debt, remaining: debt, debtGiven: debt, debtSettled: 0, status: 'completed', createdAt: at }),
    op('saleItems', `${id}-i1`, 'insert', { saleId: id, productId: 'p-cc', quantity: 1, unitPrice: 10000, totalPrice: 10000, returnedQty: 0 }),
    op('transactions', `${id}-give`, 'insert', { debtorId: 'd-1', type: 'give', amount: debt, refId: id, kind: 'sale_credit', date: '2026-11-07', createdAt: at }),
  ];
}

/** درخواستِ تأییدِ STF-02 برای قرضِ یک فاکتور، تأییدشده به دستِ صاحب */
async function approvedCredit(doc, amount, who = staff) {
  resetAll();
  const r = await h.post('/api/shop/approval-requests', { key: `cc-${++n}-${Date.now().toString(36)}`, perm: 'sales.credit', doc, amount, reason: 'مشتریِ همیشگی' },
    { token: who.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  resetAll();
  const a = await h.post(`/api/shop/approval-requests/${r.body.request.id}/approve`, {}, { token: owner.accessToken });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  return r.body.request.id;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'cc-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'cc-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label, withManager) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    let m = null;
    if (withManager) {
      const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: o.accessToken });
      m = await h.newUser(`مدیرِ ${label}`, 'shop');
      assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: m.accessToken })).status));
    }
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop'), manager: m ? await h.signIn(m, 'shop') : null };
  }
  const a = await shopWithStaff('قرض', true);
  ({ id: shopId, owner, staff, manager } = a);
  const b = await shopWithStaff('قرضِ ب', false);
  ({ id: shopB, owner: ownerB, staff: staffB } = b);
});
test.after(async () => { await h.stop(); });

test('پرچمِ خاموش (پیش‌فرض): سقف ذخیره نمی‌شود، کاتالوگ «none»، و فروشِ قرضیِ بزرگ در v3، v1 و راهِ قدیم همان امروز', async () => {
  const set = await setRole('staff', { 'sales.credit': { caps: { maxAmount: 500000 } } });
  assert.equal(set.status, 400, JSON.stringify(set.body));
  assert.equal(set.body.error.code, 'not_enforceable_yet');
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  const entry = page.body.shop.catalog.find((c) => c.key === 'sales.credit');
  assert.deepEqual([entry.edit, entry.caps], ['none', []]);
  assert.ok(!page.body.policy.editable.includes('sales.credit'));
  //  لایه‌ای که پیش از خاموش شدن ذخیره شد هم اثر ندارد
  await query(`INSERT INTO shop_role_perms (shop_id, role, perms, updated_by, updated_at) VALUES ($1,'staff',$2,'',$3)`,
    [shopId, JSON.stringify({ 'sales.credit': { mode: 'deny' } }), now()]);
  assert.deepEqual(why(await push3(staff, 'off-3', [sale3('cc-off-3', 900000)])), ['applied']);
  assert.deepEqual(why(await push1(staff, 'off-1', sale1('cc-off-1', 9000))), ['applied', 'applied', 'applied']);
  const rest = await legacyFull(staff, { id: 'cc-off-r', total: 10000, finalTotal: 10000, debtGiven: 9000, debtorId: 'd-1', status: 'completed' },
    [{ id: 'cc-off-r-i', productId: 'p-cc', quantity: 1 }]);
  assert.equal(rest.status, 200, JSON.stringify(rest.body));
  assert.equal(rest.body.applied, 2, JSON.stringify(rest.body));
  assert.equal((await many(`SELECT 1 FROM sync_v3_conflicts WHERE account_id=$1`, [shopId])).length, 0);
  await query(`DELETE FROM shop_role_perms WHERE shop_id=$1`, [shopId]);
});

test('پرچمِ روشن، v3: بالای سقفِ شاگرد ⇒ approval_required و صندوق، زیرِ سقف و برابرِ سقف می‌نشیند؛ دلتا جمع را می‌سنجد', async () => {
  await liveFlag('shop.credit_cap', true, shopId);
  assert.equal((await setRole('staff', { 'sales.credit': { caps: { maxAmount: 500000 } } })).status, 200);
  const page = await h.get('/api/shop/permissions', { token: owner.accessToken });
  assert.equal(page.body.shop.catalog.find((c) => c.key === 'sales.credit').edit, 'restrict');

  assert.deepEqual(why(await push3(staff, 's3', [sale3('cc-3a', 500000)])), ['applied']);
  const over = sale3('cc-3b', 500001);
  assert.deepEqual(why(await push3(staff, 's3', [over])), ['approval_required']);
  assert.equal(await row3('sales', 'cc-3b'), null);
  const box = await boxOf(over.op_id);
  assert.equal(box.kind, 'approval_required');
  assert.deepEqual([box.detail.perm, box.detail.amount, box.detail.cap, box.detail.limit], ['sales.credit', 500001, 'maxAmount', 500000]);

  //  دلتای مثبت روی فاکتورِ ثبت‌شده: ۵۰۰٬۰۰۰ + ۱ ⇒ بالای سقف؛ دلتای منفی آزاد
  const up = delta3('cc-3a', 1);
  assert.deepEqual(why(await push3(staff, 's3', [up])), ['approval_required']);
  assert.equal((await row3('sales', 'cc-3a')).data.debtGivenMinor, 500000);
  assert.deepEqual(why(await push3(staff, 's3', [delta3('cc-3a', -100000)])), ['applied']);
  assert.deepEqual(why(await push3(staff, 's3', [delta3('cc-3a', 100000)])), ['applied']);

  //  همان op ِ ردشده با همان op_id دوباره ⇒ دوباره داوری و همان رد (هرگز «نشسته» نمی‌شود)
  assert.deepEqual(why(await push3(staff, 's3', [over])), ['approval_required']);
  //  صاحبِ دکان هرگز سنجیده نمی‌شود؛ مدیر بی لایه همان امروز
  assert.deepEqual(why(await push3(owner, 'o3', [sale3('cc-3o', 900000)])), ['applied']);
  assert.deepEqual(why(await push3(manager, 'm3', [sale3('cc-3m', 900000)])), ['applied']);
});

test('پرچمِ روشن، v3: «درخواستِ تأیید» ِ STF-02 قرضِ بالای سقف را می‌نشاند — یک‌بارمصرف، تکرار بی‌خطر، فقط همان سند و مبلغ', async () => {
  await liveFlag('shop.approval_requests', true, shopId);
  const reqId = await approvedCredit('sales/cc-3r', 800000);
  //  مبلغِ بزرگ‌تر از تأیید ⇒ رد؛ سندِ دیگر ⇒ رد
  assert.deepEqual(why(await push3(staff, 's3r', [sale3('cc-3r', 800001, { creditApproval: { v: 1, request: reqId } })])), ['approval_too_small']);
  assert.deepEqual(why(await push3(staff, 's3r', [sale3('cc-3x', 700000, { creditApproval: { v: 1, request: reqId } })])), ['approval_mismatch']);
  const ok = sale3('cc-3r', 800000, { approval: { v: 1, request: reqId } });
  assert.deepEqual(why(await push3(staff, 's3r', [ok])), ['applied']);
  assert.equal((await row3('sales', 'cc-3r')).data.debtGivenMinor, 800000);
  //  همان op دوباره (پاسخ گم شد) ⇒ تکراری؛ opِ دیگر با همان درخواست ⇒ بازپخش
  assert.deepEqual(why(await push3(staff, 's3r', [ok])), ['duplicate']);
  assert.deepEqual(why(await push3(staff, 's3r', [sale3('cc-3y', 600000, { creditApproval: { v: 1, request: reqId } })])), ['approval_replayed']);
  const used = await one(`SELECT perm, doc, amount, path FROM shop_approvals WHERE shop_id=$1 AND nonce=$2`, [shopId, `req_${reqId}`]);
  assert.deepEqual({ ...used, amount: Number(used.amount) }, { perm: 'sales.credit', doc: 'sales/cc-3r', amount: 800000, path: 'v3' });
});

test('پرچمِ روشن، v3: «ممنوع» ⇒ credit_not_allowed؛ تخفیفِ پذیرفته و قرضِ بالای سقف ⇒ قرض داوری می‌شود', async () => {
  assert.equal((await setRole('manager', { 'sales.credit': { mode: 'deny' } })).status, 200);
  const denied = sale3('cc-3d', 1000);
  assert.deepEqual(why(await push3(manager, 'm3d', [denied])), ['credit_not_allowed']);
  assert.equal((await boxOf(denied.op_id)).kind, 'perm_denied');
  //  فاکتورِ بی قرض برای همان مدیر آزاد است
  assert.deepEqual(why(await push3(manager, 'm3d', [sale3('cc-3n', 0)])), ['applied']);
  assert.equal((await setRole('manager', { 'sales.discount': { caps: { maxPct: 50 } }, 'sales.credit': { caps: { maxAmount: 1000 } } })).status, 200);
  const both = sale3('cc-3t', 5000);
  both.fields.discountMinor = 10000;
  both.fields.finalTotalMinor = 990000;
  const r = await push3(manager, 'm3t', [both]);
  assert.deepEqual(why(r), ['approval_required']);
  assert.equal((await boxOf(both.op_id)).detail.perm, 'sales.credit');
  assert.equal((await setRole('manager', {})).status, 200);
});

test('پرچمِ روشن، v1: درجِ بالای سقف ⇒ approval_required و همراه‌ها tx_rejected؛ `$inc` ِ بالابرنده رد، ردیف دست‌نخورده', async () => {
  assert.deepEqual(why(await push1(staff, 's1', sale1('cc-1a', 5000))), ['applied', 'applied', 'applied']);
  const r = await push1(staff, 's1', sale1('cc-1b', 5000.01));
  assert.deepEqual(why(r), ['approval_required', 'tx_rejected', 'tx_rejected']);
  assert.equal(await row1('sales', 'cc-1b'), null);
  assert.equal(await row1('transactions', 'cc-1b-give'), null);
  const inc = { op_id: oid(), table: 'sales', row_id: 'cc-1a', type: 'update', ts: (clock += 1000), fields: { debtGiven: { $inc: 1 } } };
  assert.deepEqual(why(await push1(staff, 's1', [inc])), ['approval_required']);
  assert.equal((await row1('sales', 'cc-1a')).data.debtGiven, 5000);
  assert.equal((await boxOf(inc.op_id)).detail.edit, true);
  //  کاهش آزاد
  const down = { op_id: oid(), table: 'sales', row_id: 'cc-1a', type: 'update', ts: (clock += 1000), fields: { debtGiven: 4000 } };
  assert.deepEqual(why(await push1(staff, 's1', [down])), ['applied']);
  //  با «درخواستِ تأیید» همان ثبت می‌نشیند (همان قاعدهٔ v3)
  const reqId = await approvedCredit('sales/cc-1c', 700000);
  const ops = sale1('cc-1c', 7000);
  ops[0].fields.creditApproval = { v: 1, request: reqId };
  assert.deepEqual(why(await push1(staff, 's1', ops)), ['applied', 'applied', 'applied']);
  assert.equal((await row1('sales', 'cc-1c')).data.debtGiven, 7000);
});

test('پرچمِ روشن، راهِ قدیم (فاکتورِ کامل): بالا بردنِ `debtGiven` بیش از سقف ⇒ ۴۰۳ و هیچ ردیف؛ «ممنوع» ⇒ credit_not_allowed', async () => {
  const full = (id, debt) => legacyFull(staff, { id, total: 10000, finalTotal: 10000, debtGiven: debt, debtorId: 'd-1', status: 'completed' },
    [{ id: `${id}-i`, productId: 'p-cc', quantity: 1 }]);
  const ok = await full('cc-r1', 5000);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.applied, 2, JSON.stringify(ok.body));
  const over = await full('cc-r2', 5001);
  assert.equal(over.status, 403, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'approval_required');
  assert.equal((await many(`SELECT 1 FROM sales WHERE shop_id=$1 AND id='cc-r2'`, [shopId])).length, 0);
  assert.equal((await many(`SELECT 1 FROM sale_items WHERE shop_id=$1 AND id='cc-r2-i'`, [shopId])).length, 0);
  //  ویرایشِ بی بالا بردنِ قرض آزاد؛ بالا بردنش رد
  const put = (debt) => h.seed(staff.accessToken, 'sales', { id: 'cc-r1', total: 10000, finalTotal: 10000, debtGiven: debt, debtorId: 'd-1', status: 'completed' },
    { deviceId: 'web-full', updatedAt: Date.now() });
  const same = await put(5000);
  assert.deepEqual([same.status, same.body.applied], [200, 1], JSON.stringify(same.body));
  assert.equal((await put(6000)).body.error.code, 'approval_required');
  assert.equal((await setRole('staff', { 'sales.credit': { mode: 'deny' } })).status, 200);
  assert.equal((await full('cc-r3', 1)).body.error.code, 'credit_not_allowed');
  assert.equal((await setRole('staff', { 'sales.credit': { caps: { maxAmount: 500000 } } })).status, 200);
});

test('جدا از دکانِ دیگر: پرچمِ «الف» دکانِ «ب» را نمی‌سنجد و درخواستِ «الف» در «ب» پذیرفته نیست', async () => {
  //  «ب» پرچم ندارد: لایه هم ذخیره نمی‌شود و فروشِ قرضیِ بزرگِ شاگردش همان امروز
  assert.equal((await setRole('staff', { 'sales.credit': { mode: 'deny' } }, ownerB)).body.error.code, 'not_enforceable_yet');
  const r = await push3(staffB, 'b3', [sale3('cc-b1', 900000)]);
  assert.deepEqual(why(r), ['applied']);
  assert.ok(await row3('sales', 'cc-b1', shopB));
  assert.equal(await row3('sales', 'cc-b1', shopId), null);
  //  «ب» پرچمِ خودش را روشن می‌کند؛ ارجاع به درخواستِ «الف» در «ب» ⇒ نادرست (پرچمِ درخواستِ «ب» خاموش است)
  await liveFlag('shop.credit_cap', true, shopB);
  assert.equal((await setRole('staff', { 'sales.credit': { mode: 'approve' } }, ownerB)).status, 200);
  const reqA = await approvedCredit('sales/cc-b2', 900000);
  assert.deepEqual(why(await push3(staffB, 'b3', [sale3('cc-b2', 900000, { creditApproval: { v: 1, request: reqA } })])), ['approval_invalid']);
  await liveFlag('shop.approval_requests', true, shopB);
  assert.deepEqual(why(await push3(staffB, 'b3', [sale3('cc-b2', 900000, { creditApproval: { v: 1, request: reqA } })])), ['approval_invalid']);
  assert.equal(await row3('sales', 'cc-b2', shopB), null);
  //  درخواستِ «الف» هنوز در «الف» دست‌نخورده است
  assert.equal((await one('SELECT status FROM shop_approval_requests WHERE id=$1', [reqA])).status, 'approved');
});
