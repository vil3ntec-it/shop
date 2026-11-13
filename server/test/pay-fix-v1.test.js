'use strict';
/**
 * POS-40 در Sync v1 — «اصلاحِ روشِ پرداخت» (`auditLog` با `type = pay_fix`) از راهِ v1 هم سنجیده می‌شود.
 *
 * برنامه امروز پیش‌فرض با v1 همگام می‌شود (`SyncChoice` ِ برنامه: v1، هیچ دکمه‌ای v3 را روشن نمی‌کند)؛ پس داوریِ
 * `till-guard.js` که فقط Sync v3/v2 را می‌دید روی دستگاهِ واقعی هیچ اصلاحی را نمی‌سنجید. با همان پرچمِ خاموشِ
 * `shop.sync.pay_fix`، از سرِ نگهبانِ فقط‌دکانِ `staff-approval.v1Guard` (پیش از `sync-v1.js` ِ مشترک با پمپ، که دست نمی‌خورد):
 *   • همان شکل، فاکتورِ لغوشده/اینترنتی/برگشت‌خورده، روزِ بسته، ماندهٔ روش (هر دو دفتر + opهای پیشینِ همین دسته) و
 *     مجوزِ `sales.pay_fix` با برگهٔ امضاشده — صاحب هم برای شکل، روزِ بسته و مانده؛
 *   • سندِ اصلاحِ ثبت‌شده فقط‌افزودنی است (همان `log` ِ v3): ویرایشِ فیلدِ سند، حذف، یا ردیفِ دیگرِ سابقه که «اصلاح» شود
 *     ⇒ `pay_fix_invalid`؛ مُهر/یادداشت و درجِ دوبارهٔ همان محتوا آزاد؛
 *   • لایهٔ نقش و کاربرِ `sales.pay_fix` فقط با پرچمِ روشن ذخیره می‌شود (خاموش ⇒ `not_enforceable_yet`).
 * خاموش ⇒ همان امروز.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const live = require('../src/lib/live-config');
const slipCore = require('../src/lib/approval-core');
const guard = require('../src/lib/till-guard');

let shopId = '';
const T = {};
let mk = null;
let tomb = null;
let seq = 0;
const oid = () => `01JPF${String(++seq).padStart(21, '0')}`;
let tick = 0;
const now = () => Date.now() + (++tick);
const D = '2026-11-08';

const push1 = (who, ops, device = `pf-${who}`) => h.api('POST', '/api/sync/v1/push', {
  token: T[who].accessToken, body: { device_id: device, schema_version: 1, ops },
});
const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `pf3-${who}`, protocol: 3, ops },
});
const why = (r) => r.body.results.map((x) => (x.status === 'applied' || x.status === 'duplicate' ? x.status : x.reason || x.status));
async function expect1(who, ops, want) {
  const r = await push1(who, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(why(r), want, `${who}: ${JSON.stringify(r.body.results)}`);
  return r;
}
async function expect3(who, ops, want) {
  const r = await push3(who, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(why(r), want, `${who}: ${JSON.stringify(r.body.results)}`);
  return r;
}
const op1 = (table, rowId, fields, type = 'insert', extra = {}) => ({ op_id: oid(), table, row_id: rowId, type, ts: now(), fields, ...extra });
const row1 = async (table, rowId) => (await one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3 AND deleted_at IS NULL`, [shopId, table, rowId]
))?.data;
const boxOf = async (opId) => (await one(
  'SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2', [shopId, opId]
)) || null;
const setRole = (role, perms) => h.roleLayer(T.owner.accessToken, role, perms);

/** فاکتورِ v1: نقد ۱۰۰۰ + حساب‌پی ۵۰۰ افغانی (`payments` به پول، همان `SalePayment.amount`) */
const sale1 = (id, extra = {}) => op1('sales', id, {
  invoiceNumber: 7000 + seq, total: 1500, discount: 0, finalTotal: 1500, paidAmount: 1500, remaining: 0, debtGiven: 0,
  paymentMethod: 'mixed', status: 'completed', date: D, createdAt: now(),
  payments: [
    { method: 'cash', kind: 'cash', amount: 100000, status: 'confirmed' },
    { method: 'hesabpay', kind: 'wallet', amount: 50000, status: 'confirmed', ref: 'HP-1' },
  ],
  ...extra,
});
/** ردیفِ `PayFixFlow.row` ِ برنامه همان‌طور که v1 می‌فرستد: `amount` به افغانی (`AuditEntry.amount`) */
const fix1 = (id, saleId, afn, from = 'cash', to = 'hesabpay', extra = {}) => op1('auditLog', id, {
  id, type: 'pay_fix', date: D, refId: saleId, amount: afn, createdAt: now(), by: 'صندوق', approvedBy: 'صندوق',
  reason: 'اشتباهِ صندوقدار', fromMethod: from, toMethod: to, ...(to === 'cash' ? {} : { payRef: 'HP-9' }),
  notes: 'اصلاحِ روشِ پرداخت', ...extra,
});
/** همان ردیف در v3 (پول) */
const fix3 = (id, saleId, amountMinor, from = 'cash', to = 'hesabpay') => ({
  op_id: oid(), hlc: hlc.format({ wall: now(), counter: 0, node: 'pf' }), table: 'auditLog', row_id: id, kind: 'insert',
  fields: { type: 'pay_fix', date: D, refId: saleId, amountMinor, createdAt: now(), by: 'مدیر', approvedBy: 'مدیر',
    reason: 'اشتباهِ صندوقدار', fromMethod: from, toMethod: to, notes: 'اصلاح' },
});

function slipFor(rowId, amount, { by = mk } = {}) {
  const iat = Date.now() - 1000;
  const s = { v: 1, shop: shopId, doc: `auditLog/${rowId}`, perm: 'sales.pay_fix', pct_bp: null, amount, by: by.userId, key: by.keyId,
    iat, exp: iat + 10 * 60_000, nonce: crypto.randomBytes(12).toString('base64url'), reason: 'دیدم، درست است' };
  s.sig = crypto.sign('sha256', Buffer.from(slipCore.payload(s), 'utf8'), { key: by.priv, dsaEncoding: 'der' }).toString('base64');
  return s;
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ اصلاحِ v1', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ اصلاحِ روشِ پرداخت' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  for (const role of ['manager', 'staff', 'cashier', 'picker']) {
    const inv = await h.post('/api/shop/invite', { role }, { token: T.owner.accessToken });
    assert.equal(inv.status, 201, `${role}: ${JSON.stringify(inv.body)}`);
    const u = await h.newUser(`کارمندِ ${role}`, 'shop');
    const joined = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
    assert.ok([200, 201].includes(joined.status), `${role}: ${JSON.stringify(joined.body)}`);
    T[role] = await h.signIn(u, 'shop');
  }
  const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const spki = publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  const k = await h.post('/api/shop/approver-keys', { public_key: spki, label: 'گوشیِ مدیر', device_id: 'pf-manager' }, { token: T.manager.accessToken });
  assert.equal(k.status, 201, JSON.stringify(k.body));
  mk = { priv: privateKey, keyId: k.body.key.id, userId: T.manager.user.id };
  await expect1('owner', ['v-a', 'v-b', 'v-c', 'v-d', 'v-e', 'v-f', 'v-g', 'v-r'].map((id) => sale1(id)).concat([
    sale1('v-x', { status: 'cancelled' }), sale1('v-w', { channel: 'online' }),
    op1('returns', 'vr-1', { saleId: 'v-r', productId: 'p', quantity: 1, amount: 100, date: D, createdAt: now() }),
  ]), Array(11).fill('applied'));
});
test.after(async () => { await h.stop(); });

test('پرچم خاموش (پیش‌فرض) ⇒ همان امروز در v1: جمع‌آور، بیش از مانده، فاکتورِ لغوشده و ویرایشِ سندِ اصلاح می‌نشینند؛ لایهٔ نقش ذخیره نمی‌شود', async () => {
  await expect1('picker', [fix1('off-1', 'v-a', 10)], ['applied']);
  await expect1('cashier', [fix1('off-2', 'v-a', 99_999)], ['applied']);
  await expect1('staff', [fix1('off-3', 'v-x', 10)], ['applied']);
  await expect1('owner', [op1('auditLog', 'off-1', { amount: 900 }, 'update')], ['applied']);
  assert.equal((await row1('auditLog', 'off-1')).amount, 900);
  tomb = fix1('tomb-1', 'v-a', 10);
  await expect1('owner', [tomb, op1('auditLog', 'tomb-1', null, 'delete')], ['applied', 'applied']);
  const set = await setRole('cashier', { 'sales.pay_fix': { mode: 'deny' } });
  assert.equal(set.status, 400, JSON.stringify(set.body));
  assert.equal(set.body.error.code, 'not_enforceable_yet');
  const page = await h.get('/api/shop/permissions', { token: T.owner.accessToken });
  assert.equal(page.body.shop.catalog.find((c) => c.key === 'sales.pay_fix').edit, 'none');
  assert.ok(!page.body.policy.editable.includes('sales.pay_fix'));
});

test('روشن، v1: صاحب و مدیر خودشان؛ جمع‌آور ممنوع (صندوقِ تعارض)؛ شاگرد و صندوق‌دار فقط با برگهٔ مدیر برای همین ردیف و مبلغ', async () => {
  await live.set(shopId, guard.PAY_FIX_FLAG, true);
  await expect1('owner', [fix1('own-1', 'v-b', 100)], ['applied']);
  await expect1('manager', [fix1('mgr-1', 'v-b', 100)], ['applied']);
  const r = await expect1('picker', [fix1('pk-1', 'v-b', 100)], ['pay_fix_not_allowed']);
  const box = await boxOf(r.body.results[0].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.path, 'v1');
  assert.equal(await row1('auditLog', 'pk-1'), undefined);
  for (const who of ['staff', 'cashier']) {
    const n = await expect1(who, [fix1(`${who}-no`, 'v-b', 50)], ['approval_required']);
    assert.equal((await boxOf(n.body.results[0].op_id)).kind, 'approval_required');
  }
  await expect1('staff', [fix1('st-ok', 'v-b', 50, 'cash', 'hesabpay', { approval: slipFor('st-ok', 5000) })], ['applied']);
  assert.equal((await row1('auditLog', 'st-ok')).approval.perm, 'sales.pay_fix');
  await expect1('cashier', [fix1('ca-small', 'v-b', 50, 'cash', 'hesabpay', { approval: slipFor('ca-small', 4999) })], ['approval_too_small']);
  await expect1('cashier', [fix1('ca-other', 'v-b', 50, 'cash', 'hesabpay', { approval: slipFor('ca-else', 5000) })], ['approval_mismatch']);
  //  op ِ دیگرِ همان دسته دست نمی‌خورد و پاسخ‌ها سرِ جای خودشان‌اند
  await expect1('picker', [op1('auditLog', 'pk-note', { type: 'note', date: D, notes: 'یادداشت', createdAt: now() }), fix1('pk-2', 'v-b', 1)],
    ['applied', 'pay_fix_not_allowed']);
});

test('روشن، v1: شکلِ بد، فاکتورِ لغوشده، اینترنتی و برگشت‌خورده برای همه رد، صاحب هم؛ فاکتوری که سرور ندارد فقط شکل و مجوز', async () => {
  await expect1('owner', [
    fix1('bad-1', 'v-c', 10, 'cash', 'account'), fix1('bad-2', 'v-c', 0), fix1('bad-3', 'v-c', 10, 'cash', 'hesabpay', { reason: 'نه' }),
    fix1('bad-4', 'v-c', '10'), fix1('bad-5', 'v-c', 10, 'cash', 'cash'),
  ], Array(5).fill('pay_fix_invalid'));
  await expect1('owner', [fix1('bad-x', 'v-x', 10)], ['pay_fix_invalid']);
  await expect1('owner', [fix1('bad-w', 'v-w', 10)], ['pay_fix_invalid']);
  await expect1('owner', [fix1('bad-r', 'v-r', 10)], ['pay_fix_invalid']);
  await expect1('owner', [fix1('unk-o', 'v-none', 9_999)], ['applied']);
  await expect1('staff', [fix1('unk-s', 'v-none', 1)], ['approval_required']);
});

test('روشن: بیش از ماندهٔ روش رد — اصلاح‌های هر دو دفتر و opهای پیشینِ همین دستهٔ v1 شمرده می‌شوند (صاحب هم)', async () => {
  await expect1('owner', [fix1('ex-1', 'v-d', 1000.01)], ['pay_fix_exceeds']);
  await expect1('owner', [fix1('ex-2', 'v-d', 600)], ['applied']);
  //  ردیفِ v1 در داوریِ v3 هم هست، و برعکس
  const r = await expect3('manager', [fix3('ex-3', 'v-d', 60_000)], ['pay_fix_exceeds']);
  assert.equal(r.body.results[0].reason, 'pay_fix_exceeds');
  await expect3('manager', [fix3('ex-4', 'v-d', 30_000)], ['applied']);
  const b = await expect1('manager', [fix1('ex-5', 'v-d', 100.01)], ['pay_fix_exceeds']);
  const box = await boxOf(b.body.results[0].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.available, 10_000);
  await expect1('manager', [fix1('ex-6', 'v-d', 100)], ['applied']);
  //  دو اصلاح در یک دسته: دومی از ماندهٔ پس از اولی
  await expect1('owner', [fix1('ex-7', 'v-e', 600), fix1('ex-8', 'v-e', 600), fix1('ex-9', 'v-e', 400)],
    ['applied', 'pay_fix_exceeds', 'applied']);
  //  فاکتور و اصلاح در یک دسته (دستگاهِ بی‌اینترنت): مانده از همان فاکتورِ دسته؛ لغوِ همان دسته ⇒ invalid
  await expect1('owner', [sale1('v-new'), fix1('ex-10', 'v-new', 1000.01), fix1('ex-11', 'v-new', 1000)],
    ['applied', 'pay_fix_exceeds', 'applied']);
  await expect1('owner', [op1('sales', 'v-f', { status: 'cancelled', cancelReason: 'اشتباه' }, 'update'), fix1('ex-12', 'v-f', 10)],
    ['applied', 'pay_fix_invalid']);
});

test('روشن، v1: روزِ بسته (Z) برای همه رد، صاحب هم — صندوق‌های هر دو دفتر و Z ِ پیشینِ همین دسته', async () => {
  const Z = '2026-10-29';
  const audit = (id, f) => op1('auditLog', id, { id, date: Z, notes: f.type, ...f });
  await expect1('owner', [
    sale1('z-in', { date: Z, createdAt: 1_500_000 }),
    sale1('z-post', { date: Z, createdAt: 2_500_000 }),
    sale1('z-late', { date: Z, createdAt: 3_500_000 }),
    audit('zs-1', { type: 'shift_open', createdAt: 1_000_000, amount: 0 }),
    audit('zs-1z', { type: 'shift_close', refId: 'zs-1', createdAt: 2_000_000, amount: 0 }),
    audit('zs-2', { type: 'shift_open', createdAt: 3_000_000, amount: 0 }),
  ], Array(6).fill('applied'));
  const r = await expect1('owner', [fix1('cl-in', 'z-in', 10)], ['pay_fix_closed']);
  const box = await boxOf(r.body.results[0].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.reason_core, 'day_closed');
  await expect1('manager', [fix1('cl-pre', 'z-in', 10)], ['pay_fix_closed']);
  await expect1('owner', [fix1('cl-post', 'z-post', 10)], ['applied']);
  await expect1('owner', [
    fix1('cl-late-1', 'z-late', 10),
    audit('zs-2z', { type: 'shift_close', refId: 'zs-2', createdAt: 4_000_000, amount: 0 }),
    fix1('cl-late-2', 'z-late', 10),
  ], ['applied', 'applied', 'pay_fix_closed']);
});

test('روشن، v1: سندِ اصلاحِ ثبت‌شده فقط‌افزودنی است — ویرایشِ فیلدِ سند، حذف و «اصلاح» کردنِ ردیفِ دیگر رد، صاحب هم', async () => {
  await expect1('owner', [fix1('ap-1', 'v-g', 100)], ['applied']);
  await expect1('owner', [op1('auditLog', 'ap-1', { amount: 900 }, 'update')], ['pay_fix_invalid']);
  await expect1('manager', [op1('auditLog', 'ap-1', { toMethod: 'card' }, 'update')], ['pay_fix_invalid']);
  await expect1('owner', [op1('auditLog', 'ap-1', null, 'delete')], ['pay_fix_invalid']);
  await expect1('owner', [op1('auditLog', 'ap-1', { type: 'note' }, 'update')], ['pay_fix_invalid']);
  assert.equal((await row1('auditLog', 'ap-1')).amount, 100);
  //  مُهر و یادداشت آزاد؛ درجِ دوبارهٔ همان محتوا (بازپخش) آزاد؛ درجِ همان شناسه با مبلغِ دیگر نه
  await expect1('owner', [op1('auditLog', 'ap-1', { notes: 'یادداشتِ تازه', chainSeq: 4, hash: 'ab' }, 'update')], ['applied']);
  const again = fix1('ap-1', 'v-g', 100);
  const posted = await row1('auditLog', 'ap-1');
  again.fields = { ...again.fields, createdAt: posted.createdAt };
  await expect1('owner', [again], ['applied']);
  await expect1('owner', [{ ...again, op_id: oid(), fields: { ...again.fields, amount: 101 } }], ['pay_fix_invalid']);
  //  ردیفِ دیگرِ سابقه «اصلاح» نمی‌شود
  await expect1('owner', [op1('auditLog', 'ap-note', { type: 'note', date: D, notes: 'x', createdAt: now() })], ['applied']);
  await expect1('owner', [op1('auditLog', 'ap-note', { type: 'pay_fix', refId: 'v-g', amount: 1, fromMethod: 'cash', toMethod: 'hesabpay', reason: 'دستی' }, 'update')],
    ['pay_fix_invalid']);
  //  اصلاحی که پیش از روشن شدن حذف شد سرِ جا زنده نمی‌شود (v1 با نوشتن روی سنگِ قبر همان داده را برمی‌گرداند)
  await expect1('owner', [op1('auditLog', 'tomb-1', { notes: 'برگرد' }, 'update')], ['pay_fix_invalid']);
  assert.equal(await row1('auditLog', 'tomb-1'), undefined);
  //  ولی op ِ ثبت‌شدهٔ همان ردیف که دوباره رسید (پاسخ گم شد) «تکراری» ِ خودِ v1 است، نه رد
  await expect1('owner', [tomb], ['duplicate']);
  //  پاسخِ گم‌شده: همان op دوباره ⇒ «تکراری» ِ خودِ v1، نه داوریِ دوباره
  const first = fix1('ap-2', 'v-g', 130);
  await expect1('manager', [first], ['applied']);
  await expect1('manager', [first], ['duplicate']);
});

test('روشن: لایهٔ نقش و کاربرِ `sales.pay_fix` ذخیره و در هر دو راه اجرا می‌شود؛ بیش از نقش نه', async () => {
  const page = await h.get('/api/shop/permissions', { token: T.owner.accessToken });
  assert.equal(page.body.shop.catalog.find((c) => c.key === 'sales.pay_fix').edit, 'restrict');
  assert.ok(page.body.policy.editable.includes('sales.pay_fix'));
  assert.equal((await setRole('staff', { 'sales.pay_fix': { mode: 'allow' } })).body.error.code, 'exceeds_role');
  assert.equal((await setRole('cashier', { 'sales.pay_fix': { mode: 'deny' } })).status, 200);
  assert.equal((await setRole('manager', { 'sales.pay_fix': { mode: 'approve' } })).status, 200);
  await expect1('cashier', [fix1('ly-1', 'v-g', 10, 'cash', 'hesabpay', { approval: slipFor('ly-1', 1000) })], ['pay_fix_not_allowed']);
  await expect3('cashier', [{ ...fix3('ly-2', 'v-g', 1000), fields: { ...fix3('ly-2', 'v-g', 1000).fields, approval: slipFor('ly-2', 1000) } }],
    ['pay_fix_not_allowed']);
  await expect1('manager', [fix1('ly-3', 'v-g', 10)], ['approval_required']);
  //  برگهٔ مدیرِ «نیازمند تأیید» دیگر پذیرفته نیست: تأییدکننده خودش باید «مجاز» باشد
  await expect1('staff', [fix1('ly-4', 'v-g', 10, 'cash', 'hesabpay', { approval: slipFor('ly-4', 1000) })], ['approver_not_allowed']);
  assert.equal((await setRole('cashier', {})).status, 200);
  assert.equal((await setRole('manager', {})).status, 200);
  await live.remove(shopId, guard.PAY_FIX_FLAG);
  //  خاموش ⇒ دوباره `not_enforceable_yet`
  assert.equal((await setRole('cashier', { 'sales.pay_fix': { mode: 'deny' } })).body.error.code, 'not_enforceable_yet');
});
