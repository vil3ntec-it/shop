'use strict';
/**
 * صندوقِ سندهای ردشده (`lib/conflict-inbox.js`، `/api/shop/conflict-inbox`، مهاجرتِ ۳۱۵).
 *
 * سنجه‌ها: فهرست با دلیل، کارمند، دستگاه، مبلغ، قلم‌ها و زمان؛ فاکتورِ کامل؛ «دوباره ثبت با تأییدِ من» یک بار و فقط
 * یک بار (تکرار، دو درخواستِ هم‌زمان)؛ دستگاهِ سازنده opهای دوباره‌ثبت‌شده را pull نمی‌کند (دو بار کم نمی‌شود)؛ مدیرِ
 * بی اجازه نمی‌نشاند و ردیفِ تازه‌ای نمی‌سازد؛ «کنار بگذار» با دلیلِ اجباری و «اصلاح» با شناسهٔ فاکتورِ تازه؛ سابقه و
 * برگهٔ تأیید؛ شاگرد فقط ردیف‌های خودش؛ دکانِ دیگر هیچ (TEN-01)؛ راهِ v3. ⛔ `lib/sync-v1.js` دست نمی‌خورد.
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
let A;
let B;
let n = 0;
const oid = () => `01JCI${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now();

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
const inbox = (who, q = '') => h.get(`/api/shop/conflict-inbox${q}`, { token: who.accessToken });
const resubmit = (who, id, body = {}) => h.post(`/api/shop/conflict-inbox/${id}/resubmit`, body, { token: who.accessToken });
const dismiss = (who, id, body) => h.post(`/api/shop/conflict-inbox/${id}/dismiss`, body, { token: who.accessToken });
const boxOf = (shopId, opId) => one('SELECT * FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2', [shopId, opId]);
const rowOf = (shopId, table, id) => one('SELECT data FROM sync_rows WHERE account_id=$1 AND table_name=$2 AND row_id=$3', [shopId, table, id]);
const rsOps = (shopId, cfId) => many(`SELECT op_id, device_id FROM oplog WHERE account_id=$1 AND op_id LIKE $2 ORDER BY op_id`,
  [shopId, `rs_${cfId.replace(/[^A-Za-z0-9]/g, '').slice(-24)}_%`]);

/** فاکتورِ نسیهٔ v1 با دو قلم و تراکنشِ قرض — همان ثبتِ برنامه */
function sale1(id, debt) {
  const at = (clock += 1000);
  const op = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: at, fields });
  return [
    op('sales', id, 'insert', { invoiceNumber: 41, total: 10000, discount: 0, finalTotal: 10000, paymentMethod: 'credit', debtorId: 'd-1',
      paidAmount: 10000 - debt, remaining: debt, debtGiven: debt, debtSettled: 0, status: 'completed', createdAt: at, seller: 'علی' }),
    op('saleItems', `${id}-i1`, 'insert', { saleId: id, productId: 'p-rice', quantity: 2, unitPrice: 3000, totalPrice: 6000, returnedQty: 0 }),
    op('saleItems', `${id}-i2`, 'insert', { saleId: id, productId: 'p-oil', name: 'روغنِ دست‌نویس', quantity: 1, unitPrice: 4000, totalPrice: 4000, returnedQty: 0 }),
    op('transactions', `${id}-give`, 'insert', { debtorId: 'd-1', type: 'give', amount: debt, refId: id, kind: 'sale_credit', date: '2026-11-08', createdAt: at }),
  ];
}

async function shopWith(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  const id = shop.body?.shop?.id || shop.body?.id;
  const join = async (role, name) => {
    const code = (await h.post('/api/shop/staff-code', { role }, { token: o.accessToken })).body.code;
    const u = await h.newUser(name, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: u.accessToken })).status));
    return { ...(await h.signIn(u, 'shop')), name, userId: u.id || u.user?.id };
  };
  const owner = await h.signIn(o, 'shop');
  return { id, owner, staff: await join('staff', `علیِ ${label}`), manager: await join('manager', `مدیرِ ${label}`) };
}

/** شاگرد فاکتورِ نسیهٔ بالای سقف می‌زند ⇒ ردیفِ صندوق */
async function rejectedSale(S, id, debt = 6000) {
  const ops = sale1(id, debt);
  assert.deepEqual(why(await push1(S.staff, 'ci-pos', ops)), ['approval_required', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
  const box = await boxOf(S.id, ops[0].op_id);
  assert.ok(box && box.status === 'open', 'صندوق ساخته نشد');
  return { ops, box };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ci-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'ci-admin', password: 'Admin!12345' })).body.token;
  A = await shopWith('الف');
  B = await shopWith('ب');
  for (const S of [A, B]) {
    await liveFlag('shop.credit_cap', true, S.id);
    const r = await h.roleLayer(S.owner.accessToken, 'staff', { 'sales.credit': { caps: { maxAmount: 500000 } } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  //  نامِ کالا و قرض‌دار از دفترِ سرور
  const at = Date.now();
  const seed = [
    { op_id: oid(), table: 'products', row_id: 'p-rice', type: 'insert', ts: at, fields: { name: 'برنج', salePrice: 3000, stock: 50 } },
    { op_id: oid(), table: 'products', row_id: 'p-oil', type: 'insert', ts: at, fields: { name: 'روغن', salePrice: 4000, stock: 50 } },
    { op_id: oid(), table: 'debtors', row_id: 'd-1', type: 'insert', ts: at, fields: { name: 'احمد', phone: '0793330001', balance: 0 } },
  ];
  assert.deepEqual(why(await push1(A.owner, 'ci-owner-pc', seed)), ['applied', 'applied', 'applied']);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('فهرستِ صاحب: دلیلِ روشن، کارمند، دستگاه، مبلغ، قلم‌ها، زمان؛ فاکتورِ کامل در جزئیات', async () => {
  const { ops, box } = await rejectedSale(A, 'ci-1');
  const r = await inbox(A.owner);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const cf = r.body.conflicts.find((c) => c.id === box.id);
  assert.ok(cf, 'صندوقِ صاحب این فاکتور را ندارد');
  assert.ok(r.body.open >= 1);
  assert.equal(cf.reason, 'approval_required');
  assert.equal(cf.perm, 'sales.credit');
  assert.equal(cf.staff.name, A.staff.name);
  assert.equal(cf.staff.role, 'staff');
  assert.equal(cf.device.id, 'ci-pos');
  assert.equal(cf.amountMinor, 1000000);
  assert.equal(cf.number, 41);
  assert.equal(cf.itemCount, 2);
  assert.deepEqual(cf.items.map((x) => [x.name, x.qtyMilli]), [['برنج', 2000], ['روغنِ دست‌نویس', 1000]]);
  assert.ok(cf.at > 0 && cf.resubmittable && cf.fixable && !cf.busy);
  assert.equal(cf.status, 'open');
  //  جزئیات: همهٔ فاکتور و همهٔ opها
  const d = await h.get(`/api/shop/conflict-inbox/${box.id}`, { token: A.owner.accessToken });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  const inv = d.body.conflict.invoice;
  assert.deepEqual([inv.finalMinor, inv.paidMinor, inv.debtMinor, inv.paymentMethod], [1000000, 400000, 600000, 'credit']);
  assert.deepEqual(inv.debtor, { id: 'd-1', name: 'احمد' });
  assert.deepEqual(inv.items.map((x) => [x.productId, x.qtyMilli, x.unitPriceMinor, x.totalMinor]),
    [['p-rice', 2000, 300000, 600000], ['p-oil', 1000, 400000, 400000]]);
  assert.deepEqual(d.body.conflict.ops.map((o) => o.op_id), ops.map((o) => o.op_id));
  assert.equal(d.body.conflict.limits.amount, 600000);
  //  مدیر هم می‌بیند
  assert.equal((await inbox(A.manager)).status, 200);
});

test('شاگرد صندوقِ صاحب را نمی‌بیند و نمی‌بندد؛ فقط ردیفِ خودش را با دلیل', async () => {
  const { ops, box } = await rejectedSale(A, 'ci-2');
  assert.equal((await inbox(A.staff)).status, 403);
  assert.equal((await h.get(`/api/shop/conflict-inbox/${box.id}`, { token: A.staff.accessToken })).status, 403);
  assert.equal((await resubmit(A.staff, box.id)).status, 403);
  assert.equal((await dismiss(A.staff, box.id, { reason: 'من نه' })).status, 403);
  const mine = await inbox(A.staff, '/mine');
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  const m = mine.body.conflicts.find((c) => c.id === box.id);
  assert.deepEqual([m.opId, m.table, m.rowId, m.reason, m.status, m.device], [ops[0].op_id, 'sales', 'ci-2', 'approval_required', 'open', 'ci-pos']);
  //  صاحب ردیفی از خودش ندارد
  assert.ok(!(await inbox(A.owner, '/mine')).body.conflicts.some((c) => c.id === box.id));
  assert.equal((await one('SELECT status FROM sync_v3_conflicts WHERE id=$1', [box.id])).status, 'open');
});

test('«دوباره ثبت با تأییدِ من»: یک بار می‌نشیند، با برگهٔ تأیید و سابقه؛ تکرار همان پاسخ؛ دستگاهِ سازنده دو بار نمی‌گیرد', async () => {
  const { box } = await rejectedSale(A, 'ci-3');
  assert.equal(await rowOf(A.id, 'sales', 'ci-3'), null, 'پیش از دوباره ثبت نباید باشد');
  const r = await resubmit(A.owner, box.id, { note: 'مشتریِ همیشگی' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.duplicate, false);
  assert.equal(r.body.applied, 4);
  assert.equal(r.body.conflict.status, 'resubmitted');
  assert.equal(r.body.conflict.note, 'مشتریِ همیشگی');
  const sale = await rowOf(A.id, 'sales', 'ci-3');
  assert.equal(sale.data.debtGiven, 6000);
  assert.ok(await rowOf(A.id, 'saleItems', 'ci-3-i1'));
  assert.ok(await rowOf(A.id, 'transactions', 'ci-3-give'));
  //  به نامِ همان دستگاهِ سازنده، شناسه‌ها بسته به ردیفِ صندوق
  const placed = await rsOps(A.id, box.id);
  assert.equal(placed.length, 4);
  assert.ok(placed.every((o) => o.device_id === 'ci-pos'));
  //  برگهٔ تأیید (RPT-09) و سابقه
  const slip = await one('SELECT * FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [A.id, `cf:${box.id}`]);
  assert.ok(slip, 'برگهٔ تأیید نیست');
  assert.deepEqual([slip.perm, slip.doc, slip.key_id, slip.path, slip.approver_id, slip.reason],
    ['sales.credit', 'sales/ci-3', 'inbox', 'v1', A.owner.user.id, 'مشتریِ همیشگی']);
  const aud = await many(`SELECT action, user_id FROM audit_logs WHERE shop_id=$1 AND target_id=$2`, [A.id, box.id]);
  assert.deepEqual(aud.map((a) => a.action), ['conflict_inbox.resubmit']);

  //  تکرار (پاسخ گم شد) ⇒ همان، هیچ چیزی دوباره نمی‌نشیند
  const again = await resubmit(A.owner, box.id);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.duplicate, true);
  assert.equal((await rsOps(A.id, box.id)).length, 4);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE shop_id=$1 AND target_id=$2`, [A.id, box.id])).length, 1);
  assert.equal((await many(`SELECT 1 FROM shop_approvals WHERE shop_id=$1 AND nonce=$2`, [A.id, `cf:${box.id}`])).length, 1);

  //  دستگاهِ سازنده opهای خودش را pull نمی‌کند (فروش و قرض روی دفترش هست)؛ دستگاهِ صاحب می‌گیرد
  const staffPull = await h.get('/api/sync/v1/pull?device_id=ci-pos&since=0', { token: A.staff.accessToken });
  assert.equal(staffPull.status, 200, JSON.stringify(staffPull.body));
  assert.ok(!staffPull.body.ops.some((o) => String(o.op_id).startsWith('rs_')), 'دستگاهِ سازنده فروش را دو بار می‌گرفت');
  const ownerPull = await h.get('/api/sync/v1/pull?device_id=ci-owner-pc&since=0', { token: A.owner.accessToken });
  assert.ok(ownerPull.body.ops.some((o) => o.table === 'sales' && o.row_id === 'ci-3'));
  //  دستگاهِ سازنده می‌بیند که صاحب دوباره ثبت کرد
  const m = (await inbox(A.staff, '/mine')).body.conflicts.find((c) => c.id === box.id);
  assert.equal(m.status, 'resubmitted');
  //  بستن پس از دوباره ثبت ⇒ ۴۰۹
  const late = await dismiss(A.owner, box.id, { reason: 'دیر' });
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'already_resolved');
});

test('دو «دوباره ثبت» ِ هم‌زمان ⇒ فروش دقیقاً یک بار؛ پس از آن مدیر هم همان پاسخ', async () => {
  const { box } = await rejectedSale(A, 'ci-4');
  const [x, y] = await Promise.all([resubmit(A.owner, box.id), resubmit(A.owner, box.id)]);
  const codes = [x, y].map((r) => (r.status === 200 ? (r.body.duplicate ? 'dup' : 'ok') : r.body.error.code)).sort();
  assert.ok(codes.includes('ok'), JSON.stringify([x.body, y.body]));
  assert.ok(['dup', 'resubmit_in_progress'].includes(codes.find((c) => c !== 'ok') || 'dup'), JSON.stringify(codes));
  assert.equal((await rsOps(A.id, box.id)).length, 4, 'opها دو بار نشستند');
  const rows = await many(`SELECT 1 FROM oplog WHERE account_id=$1 AND table_name='sales' AND row_id='ci-4'`, [A.id]);
  assert.equal(rows.length, 1);
  assert.equal((await one('SELECT status FROM sync_v3_conflicts WHERE id=$1', [box.id])).status, 'resubmitted');
  //  و پس از آن هر بار همان
  const z = await resubmit(A.manager, box.id);
  assert.equal(z.status, 200);
  assert.equal(z.body.duplicate, true);
});

test('مدیرِ بی اجازهٔ همان مبلغ نمی‌نشاند؛ ردیفِ تازه‌ای نمی‌ماند؛ صاحب بعد می‌نشاند', async () => {
  const { box } = await rejectedSale(A, 'ci-5');
  const before = (await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1`, [A.id])).n;
  assert.equal((await h.roleLayer(A.owner.accessToken, 'manager', { 'sales.credit': { caps: { maxAmount: 100 } } })).status, 200);
  try {
    const r = await resubmit(A.manager, box.id);
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'resubmit_rejected');
    assert.equal(r.body.error.reason, 'approval_required');
    assert.equal(await rowOf(A.id, 'sales', 'ci-5'), null);
    assert.equal((await rsOps(A.id, box.id)).length, 0);
    assert.equal((await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE account_id=$1`, [A.id])).n, before, 'ردیفِ تازه در صندوق ماند');
    const cur = await one('SELECT * FROM sync_v3_conflicts WHERE id=$1', [box.id]);
    assert.equal(cur.status, 'open');
    assert.deepEqual(cur.loser_value, box.loser_value, 'بستهٔ همان ردیف عوض شد');
    assert.equal(cur.detail.resubmit_error, 'approval_required');
    assert.equal(cur.detail.resubmitting, undefined);
    const listed = (await inbox(A.owner)).body.conflicts.find((c) => c.id === box.id);
    assert.equal(listed.lastError.reason, 'approval_required');
    const aud = await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND target_id=$2`, [A.id, box.id]);
    assert.deepEqual(aud.map((a) => a.action), ['conflict_inbox.resubmit_rejected']);
  } finally {
    assert.equal((await h.roleLayer(A.owner.accessToken, 'manager', {})).status, 200);
  }
  const ok = await resubmit(A.owner, box.id);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await rsOps(A.id, box.id)).length, 4);
  assert.equal(ok.body.conflict.lastError, null);
});

test('مدیر با اجازهٔ همان مبلغ از همان نگهبان‌های شاگرد می‌گذرد و می‌نشاند (یک بار)', async () => {
  const { box } = await rejectedSale(A, 'ci-5m');
  const r = await resubmit(A.manager, box.id, { note: 'تأییدِ مدیر' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.duplicate, r.body.applied, r.body.conflict.status], [false, 4, 'resubmitted']);
  assert.equal(r.body.conflict.resolvedBy.id, A.manager.user.id);
  assert.equal((await rsOps(A.id, box.id)).length, 4);
  assert.ok(await rowOf(A.id, 'transactions', 'ci-5m-give'), 'همراهِ فاکتور هم نشست (نگهبانِ «همراهِ سندِ نگه‌داشته» نگرفتش)');
  const slip = await one('SELECT approver_id, requester_id FROM shop_approvals WHERE shop_id=$1 AND nonce=$2', [A.id, `cf:${box.id}`]);
  assert.deepEqual([slip.approver_id, slip.requester_id], [A.manager.user.id, A.staff.user.id]);
});

test('«کنار بگذار» دلیل می‌خواهد و می‌ماند؛ «اصلاح و ثبت» با شناسهٔ فاکتورِ تازه؛ هیچ‌کدام چیزی نمی‌نشاند', async () => {
  const { box } = await rejectedSale(A, 'ci-6');
  const empty = await dismiss(A.owner, box.id, { reason: ' ' });
  assert.equal(empty.status, 400);
  assert.equal(empty.body.error.code, 'note_required');
  assert.equal((await dismiss(A.owner, box.id, { reason: 'خطِ اول\nخطِ دوم' })).status, 400);
  const r = await dismiss(A.owner, box.id, { reason: 'مشتری پس گرفت' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.conflict.status, r.body.conflict.note, r.body.conflict.resolvedBy.id], ['dismissed', 'مشتری پس گرفت', A.owner.user.id]);
  const again = await dismiss(A.owner, box.id, { reason: 'مشتری پس گرفت' });
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal((await resubmit(A.owner, box.id)).body.error.code, 'already_resolved');
  assert.equal(await rowOf(A.id, 'sales', 'ci-6'), null);
  assert.deepEqual((await many(`SELECT action FROM audit_logs WHERE shop_id=$1 AND target_id=$2`, [A.id, box.id])).map((a) => a.action), ['conflict_inbox.dismiss']);
  const m = (await inbox(A.staff, '/mine')).body.conflicts.find((c) => c.id === box.id);
  assert.deepEqual([m.status, m.note], ['dismissed', 'مشتری پس گرفت']);

  const f = await rejectedSale(A, 'ci-7');
  assert.equal((await dismiss(A.owner, f.box.id, { reason: 'اصلاح شد', fixedDoc: 'bad id!' })).status, 400);
  const fx = await dismiss(A.owner, f.box.id, { reason: 'اصلاح و دوباره ثبت شد', fixedDoc: 'sales/ci-7-fixed' });
  assert.equal(fx.status, 200, JSON.stringify(fx.body));
  assert.deepEqual([fx.body.conflict.status, fx.body.conflict.fixedDoc], ['fixed', 'ci-7-fixed']);
  assert.equal((await inbox(A.staff, '/mine')).body.conflicts.find((c) => c.id === f.box.id).fixedDoc, 'ci-7-fixed');
  //  بسته‌ها از فهرستِ باز بیرون‌اند، در «همه» هستند
  const open = (await inbox(A.owner)).body.conflicts.map((c) => c.id);
  assert.ok(!open.includes(box.id) && !open.includes(f.box.id));
  const all = (await inbox(A.owner, '?status=all')).body.conflicts.map((c) => c.id);
  assert.ok(all.includes(box.id) && all.includes(f.box.id));
});

test('برگشتِ بیش از فروخته با هیچ تأییدی نمی‌نشیند (فقط اصلاح یا کنار گذاشتن)', async () => {
  const id = newId('cf');
  await query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, loser_op_id, loser_device, detail, at, loser_value)
     VALUES ($1,'shop','shop',$2,'perm_denied','returns','ci-ret-1','op-ci-ret-1','ci-pos',$3::jsonb,$4,$5::jsonb)`,
    [id, A.id, JSON.stringify({ perm: 'returns.create', decision: 'deny', reason_core: 'exceeds_sold', requester: A.staff.user.id, role: 'staff' }), now(),
      JSON.stringify({ v: 1, path: 'v1', ops: [{ op_id: 'op-ci-ret-1', table: 'returns', row_id: 'ci-ret-1', type: 'insert', ts: 1, fields: { saleId: 'x', productId: 'p-rice', quantity: 1, amount: 3000 } }] })]);
  const cf = (await inbox(A.owner)).body.conflicts.find((c) => c.id === id);
  assert.deepEqual([cf.reason, cf.resubmittable, cf.fixable, cf.amountMinor, cf.items[0].name], ['return_exceeds_sold', false, false, 300000, 'برنج']);
  const r = await resubmit(A.owner, id);
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'not_resubmittable');
  assert.equal((await dismiss(A.owner, id, { reason: 'برگشتِ اشتباه' })).status, 200);
});

test('دکانِ دیگر هیچ: نه در فهرست، نه جزئیات، نه دوباره ثبت، نه کنار گذاشتن (TEN-01)', async () => {
  const { ops, box } = await rejectedSale(A, 'ci-8');
  const list = await inbox(B.owner, '?status=all&limit=200');
  assert.equal(list.status, 200);
  const leak = JSON.stringify(list.body);
  assert.ok(!leak.includes(box.id) && !leak.includes('ci-8') && !leak.includes(ops[0].op_id), 'صندوقِ دکانِ دیگر دیده شد');
  assert.equal((await h.get(`/api/shop/conflict-inbox/${box.id}`, { token: B.owner.accessToken })).status, 404);
  assert.equal((await resubmit(B.owner, box.id)).status, 404);
  assert.equal((await dismiss(B.owner, box.id, { reason: 'دکانِ دیگر' })).status, 404);
  assert.ok(!(await inbox(B.staff, '/mine')).body.conflicts.some((c) => c.id === box.id));
  const cur = await one('SELECT status, resolution_note FROM sync_v3_conflicts WHERE id=$1', [box.id]);
  assert.deepEqual([cur.status, cur.resolution_note], ['open', '']);
  assert.equal(await rowOf(A.id, 'sales', 'ci-8'), null);
  assert.equal((await many(`SELECT 1 FROM oplog WHERE account_id=$1 AND op_id LIKE 'rs\\_%'`, [B.id])).length, 0);
});

test('راهِ v3: فاکتورِ ردشده یک بار با همان دستگاه و یک tx ِ تازه می‌نشیند', async () => {
  const sale = {
    op_id: oid(), hlc: H('pos3'), table: 'sales', row_id: 'ci-v3a', kind: 'insert',
    fields: { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 399999, remainingMinor: 600001,
      debtGivenMinor: 600001, debtorId: 'd-1', paymentMethod: 'credit', status: 'completed' },
  };
  const item = {
    op_id: oid(), hlc: H('pos3'), table: 'saleItems', row_id: 'ci-v3a-i1', kind: 'insert',
    fields: { saleId: 'ci-v3a', productId: 'p-rice', quantityMilli: 2000, unitPriceMinor: 500000, totalPriceMinor: 1000000 },
  };
  const out = await push3(A.staff, 'ci-pos3', [sale, item]);
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal(out.body.results[0].status, 'rejected');
  const box = await boxOf(A.id, sale.op_id);
  assert.equal(box.loser_value.path, 'v3');
  const cf = (await inbox(A.owner)).body.conflicts.find((c) => c.id === box.id);
  assert.deepEqual([cf.path, cf.amountMinor, cf.device.id, cf.resubmittable], ['v3', 1000000, 'ci-pos3', true]);
  const r = await resubmit(A.owner, box.id);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const key = box.id.replace(/[^A-Za-z0-9]/g, '').slice(-24);
  const placed = await many(`SELECT op_id, device_id FROM sync_v3_ops WHERE account_id=$1 AND op_id LIKE $2`, [A.id, `rs_${key}_%`]);
  assert.equal(placed.length, box.loser_value.ops.length);
  assert.ok(placed.every((o) => o.device_id === 'ci-pos3'));
  const row = await one(`SELECT data FROM sync_v3_rows WHERE account_id=$1 AND table_name='sales' AND row_id='ci-v3a'`, [A.id]);
  assert.equal(row.data.debtGivenMinor, 600001);
  const again = await resubmit(A.owner, box.id);
  assert.equal(again.body.duplicate, true);
  assert.equal((await many(`SELECT 1 FROM sync_v3_ops WHERE account_id=$1 AND op_id LIKE $2`, [A.id, `rs_${key}_%`])).length, placed.length);
});

test('هم‌سو شدنِ دستگاهِ سازنده: «کنار بگذار»/«اصلاح» ⇒ `/mine` بستهٔ کامل و مقدارِ امروزِ سرور؛ باز و دوباره‌ثبت‌شده بی آن', async () => {
  //  ۱) فروشِ ردشده (همه درج) ⇒ کنار گذاشته: همهٔ opها، هیچ مقدارِ سروری (هیچ‌کدام روی سرور نیست)
  const d = await rejectedSale(A, 'ci-st-1');
  const open = (await inbox(A.staff, '/mine')).body.conflicts.find((c) => c.id === d.box.id);
  assert.equal(open.settle, undefined, 'ردیفِ باز چیزی برای برگرداندن نمی‌دهد');
  assert.equal((await dismiss(A.owner, d.box.id, { reason: 'مشتری نخواست' })).status, 200);
  const m = (await inbox(A.staff, '/mine')).body.conflicts.find((c) => c.id === d.box.id);
  assert.equal(m.settle.path, 'v1');
  assert.deepEqual(m.settle.ops.map((o) => [o.id, o.table, o.rowId, o.type]), d.ops.map((o) => [o.op_id, o.table, o.row_id, 'insert']));
  assert.deepEqual(m.settle.base, {}, 'ردیفِ درجِ تنها روی سرور نیست');
  assert.equal(m.settle.truncated, undefined);

  //  ۲) لغوِ ردشده (ویرایشِ فاکتورِ روی سرور) ⇒ مقدارِ امروزِ همان فاکتور برای برگرداندنِ وضعیت
  const at = (clock += 1000);
  const sid = 'ci-st-2';
  const placed = [
    { op_id: oid(), table: 'sales', row_id: sid, type: 'insert', ts: at, fields: { invoiceNumber: 77, total: 3000, finalTotal: 3000, paymentMethod: 'cash', paidAmount: 3000, status: 'completed', cancelReason: '', createdAt: at } },
    { op_id: oid(), table: 'saleItems', row_id: `${sid}-i1`, type: 'insert', ts: at, fields: { saleId: sid, productId: 'p-rice', quantity: 1, unitPrice: 3000, totalPrice: 3000, returnedQty: 0 } },
  ];
  assert.deepEqual(why(await push1(A.owner, 'ci-owner-pc', placed)), ['applied', 'applied']);
  const t2 = (clock += 1000);
  const cancel = [
    { op_id: oid(), table: 'sales', row_id: sid, type: 'update', ts: t2, fields: { status: 'cancelled', cancelReason: 'اشتباه' } },
    { op_id: oid(), table: 'stockMovements', row_id: `${sid}-mv`, type: 'insert', ts: t2, fields: { productId: 'p-rice', type: 'sale_cancel', qty: 1, refId: sid, createdAt: t2 } },
  ];
  const res = why(await push1(A.staff, 'ci-pos', cancel));
  assert.equal(res[0], 'cancel_not_allowed', JSON.stringify(res));
  const cbox = await boxOf(A.id, cancel[0].op_id);
  assert.ok(cbox, 'لغوِ ردشده صندوق نگرفت');
  assert.equal((await dismiss(A.owner, cbox.id, { reason: 'فاکتور درست بود' })).status, 200);
  const c = (await inbox(A.staff, '/mine')).body.conflicts.find((x) => x.id === cbox.id);
  assert.ok(c.settle.ops.some((o) => o.id === cancel[0].op_id && o.type === 'update'));
  assert.equal(c.settle.base[`sales/${sid}`].status, 'completed', JSON.stringify(c.settle.base));
  assert.equal(c.settle.base[`sales/${sid}`].cancelReason, '');
  assert.ok(!(`stockMovements/${sid}-mv` in c.settle.base), 'درجِ تنها مقدارِ سرور ندارد');

  //  ۳) «اصلاح» همان `settle` را دارد؛ «دوباره ثبت» ندارد (ردیف‌ها روی سرور نشستند)
  const f = await rejectedSale(A, 'ci-st-3');
  assert.equal((await dismiss(A.owner, f.box.id, { reason: 'اصلاح و دوباره ثبت شد', fixedDoc: 'sales/ci-st-3-new' })).status, 200);
  const fm = (await inbox(A.staff, '/mine')).body.conflicts.find((x) => x.id === f.box.id);
  assert.deepEqual([fm.status, fm.fixedDoc, fm.settle.ops.length], ['fixed', 'ci-st-3-new', 4]);
  const r = await rejectedSale(A, 'ci-st-4');
  assert.equal((await resubmit(A.owner, r.box.id)).status, 200);
  const rm = (await inbox(A.staff, '/mine')).body.conflicts.find((x) => x.id === r.box.id);
  assert.deepEqual([rm.status, rm.settle], ['resubmitted', undefined]);

  //  ⛔ فقط ردیف‌های خودِ کارمند: صاحب و دکانِ دیگر هیچ
  assert.ok(!(await inbox(A.owner, '/mine')).body.conflicts.some((x) => x.id === d.box.id));
  assert.ok(!JSON.stringify((await inbox(B.staff, '/mine')).body).includes('ci-st-'));
});
