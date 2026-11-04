'use strict';
/**
 * نگهبانِ پولِ صندوق در Sync v3 — `src/lib/till-guard.js` (از سرِ `doc-roles.judgeV3`).
 *  ① `shop.sync.pay_fix` (POS-40): اصلاحِ روشِ پرداخت — شکل، فاکتورِ لغوشده/برگشت‌خورده، بیش از ماندهٔ روش (با اصلاح‌های
 *     پیشینِ سرور)، و مجوزِ `sales.pay_fix` با «برگهٔ امضاشده» (شاگرد/صندوق‌دار «نیازمند تأیید»، جمع‌آور ممنوع).
 *  ② `shop.sync.refund_cap` (POS-26/27): بازپرداختِ سفارشِ ویژه/امانی بیش از پولِ نگه‌داشتهٔ همان سند رد — لغوِ
 *     هم‌زمانِ دو دستگاه دو بار نقد از دخل نمی‌برد.
 *  هر دو خاموش ⇒ همان امروز.
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
let seq = 0;
const oid = () => `01JTG${String(++seq).padStart(21, '0')}`;
let tick = 0;
const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'pos' });

const push3 = (who, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[who].accessToken, body: { device_id: `tg-${who}`, protocol: 3, ops },
});
const op3 = (table, rowId, fields, extra = {}) => ({ op_id: oid(), hlc: H(), table, row_id: rowId, kind: 'insert', fields, ...extra });
const results = (r) => r.body.results.map((x) => x.reason || x.status);
const inV3 = async (table, rowId) => (await one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
))?.data;
const boxOf = async (opId) => (await one(
  'SELECT kind, detail FROM sync_v3_conflicts WHERE account_id=$1 AND loser_op_id=$2', [shopId, opId]
)) || null;
async function expect(who, ops, want) {
  const r = await push3(who, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(results(r), want, `${who}: ${JSON.stringify(r.body.results)}`);
  return r;
}

/** فاکتورِ نقد ۱۰۰۰ + حساب‌پی ۵۰۰ افغانی (به پول، همان `SalePayment.amount`) */
const sale = (id, extra = {}) => op3('sales', id, {
  invoiceNumber: 9000 + seq, totalMinor: 150000, finalTotalMinor: 150000, paidAmountMinor: 150000, status: 'completed',
  paymentMethod: 'mixed', date: '2026-11-04', createdAt: Date.now(),
  payments: [
    { method: 'cash', kind: 'cash', amount: 100000, status: 'confirmed' },
    { method: 'hesabpay', kind: 'wallet', amount: 50000, status: 'confirmed', ref: 'HP-1' },
  ],
  ...extra,
});
/** ردیفِ `PayFixFlow.row` ِ برنامه */
const fix = (id, saleId, amountMinor, from = 'cash', to = 'hesabpay', extra = {}) => op3('auditLog', id, {
  type: 'pay_fix', date: '2026-11-04', refId: saleId, amountMinor, createdAt: Date.now() + (++tick), by: 'صندوق', approvedBy: 'صندوق',
  reason: 'اشتباهِ صندوقدار', fromMethod: from, toMethod: to, payRef: to === 'cash' ? undefined : 'HP-9',
  notes: 'اصلاحِ روشِ پرداخت', ...extra,
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
  const o = await h.newUser('صاحبِ دکانِ صندوق', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ نگهبانِ صندوق' }, { token: o.accessToken });
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
  const k = await h.post('/api/shop/approver-keys', { public_key: spki, label: 'گوشیِ مدیر', device_id: 'tg-manager' }, { token: T.manager.accessToken });
  assert.equal(k.status, 201, JSON.stringify(k.body));
  mk = { priv: privateKey, keyId: k.body.key.id, userId: T.manager.user.id };
  await expect('owner', [sale('s-a'), sale('s-b'), sale('s-c'), sale('s-x', { status: 'cancelled' }), sale('s-r')],
    ['applied', 'applied', 'applied', 'applied', 'applied']);
  await expect('owner', [op3('returns', 'r-1', { saleId: 's-r', productId: 'p', quantityMilli: 1000, amountMinor: 10000, date: '2026-11-04' })], ['applied']);
});
test.after(async () => { await h.stop(); });

// ---------------------------------------------------------------- بی پایگاه

test('شکلِ اصلاح: دو روشِ جدای شمردنی، مبلغِ صحیحِ مثبت، فاکتور و دلیلِ یک‌خطیِ ۳ تا ۱۲۰', () => {
  const ok = fix('x', 's', 100).fields;
  assert.equal(guard.payFixShape(ok), null);
  for (const bad of [{ toMethod: 'account' }, { fromMethod: 'account' }, { toMethod: 'gift_card' }, { fromMethod: 'store_credit' },
    { toMethod: 'cash' }, { amountMinor: 0 }, { amountMinor: -5 }, { amountMinor: 1.5 }, { amountMinor: '100' }, { refId: ' ' },
    { reason: 'ab' }, { reason: 'x'.repeat(121) }, { reason: 'دو\nخط' }, { fromMethod: '' }]) {
    assert.equal(guard.payFixShape({ ...ok, ...bad }), 'pay_fix_invalid', JSON.stringify(bad));
  }
});

test('ماندهٔ روش: ردیف‌های ثبت‌شده (امانتِ پیک نه)، فاکتورِ قدیمیِ بی `payments` (نقد = جمع − بدهی) و اصلاح‌ها به ترتیبِ زمان', () => {
  const rows = guard.postedRows({ v1: false, data: { payments: [
    { method: 'cash', amount: 7000 }, { method: 'hesabpay', amount: 3000 }, { method: 'cash', amount: 500, custody: 'courier' },
  ] } });
  assert.deepEqual(rows, [{ method: 'cash', amount: 7000 }, { method: 'hesabpay', amount: 3000 }]);
  assert.deepEqual(guard.postedRows({ v1: false, data: { finalTotalMinor: 30000, debtGivenMinor: 10000 } }),
    [{ method: 'cash', amount: 20000 }, { method: 'account', amount: 10000 }]);
  assert.deepEqual(guard.postedRows({ v1: true, data: { finalTotal: 300.5, debtGiven: 0 } }), [{ method: 'cash', amount: 30050 }]);
  //  اصلاحِ بیش از مانده و اصلاحِ «به حساب» کنار می‌روند؛ ترتیب (زمان، شناسه)
  const sums = guard.applyFixes(rows, [
    { id: 'b', at: 2, from: 'cash', to: 'hesabpay', amount: 5000 },
    { id: 'a', at: 2, from: 'cash', to: 'card', amount: 1000 },
    { id: 'c', at: 3, from: 'cash', to: 'card', amount: 9999 },
    { id: 'd', at: 4, from: 'hesabpay', to: 'account', amount: 100 },
  ]);
  assert.equal(sums.get('cash'), 1000);
  assert.equal(sums.get('card'), 1000);
  assert.equal(sums.get('hesabpay'), 8000);
});

// ---------------------------------------------------------------- ① اصلاحِ روشِ پرداخت

test('پرچم خاموش (پیش‌فرض) ⇒ همان امروز: اصلاحِ بی برگه، از جمع‌آور و بیش از مانده هم می‌نشیند', async () => {
  await expect('staff', [fix('off-1', 's-a', 1000)], ['applied']);
  await expect('picker', [fix('off-2', 's-a', 1000)], ['applied']);
  await expect('cashier', [fix('off-3', 's-a', 999_999)], ['applied']);
  await expect('owner', [fix('off-4', 's-x', 1000)], ['applied']);
});

test('روشن: صاحب و مدیر خودشان؛ جمع‌آور ممنوع (صندوقِ تعارض)', async () => {
  await live.set(shopId, guard.PAY_FIX_FLAG, true);
  await expect('owner', [fix('own-1', 's-b', 10000)], ['applied']);
  await expect('manager', [fix('mgr-1', 's-b', 10000)], ['applied']);
  const r = await expect('picker', [fix('pk-1', 's-b', 10000)], ['pay_fix_not_allowed']);
  assert.equal((await boxOf(r.body.results[0].op_id)).kind, 'perm_denied');
  assert.equal(await inV3('auditLog', 'pk-1'), undefined);
});

test('روشن: شاگرد و صندوق‌دار «نیازمند تأیید» — بی برگه رد؛ با برگهٔ مدیر برای همین ردیف و مبلغ می‌نشیند؛ برگهٔ کم یا ردیفِ دیگر نه', async () => {
  for (const who of ['staff', 'cashier']) {
    const r = await expect(who, [fix(`${who}-no`, 's-b', 5000)], ['approval_required']);
    assert.equal((await boxOf(r.body.results[0].op_id)).kind, 'approval_required');
  }
  await expect('staff', [fix('st-ok', 's-b', 5000, 'cash', 'hesabpay', { approval: slipFor('st-ok', 5000) })], ['applied']);
  assert.equal((await inV3('auditLog', 'st-ok')).approval.perm, 'sales.pay_fix');
  await expect('cashier', [fix('ca-small', 's-b', 5000, 'cash', 'hesabpay', { approval: slipFor('ca-small', 4999) })], ['approval_too_small']);
  await expect('cashier', [fix('ca-other', 's-b', 5000, 'cash', 'hesabpay', { approval: slipFor('ca-else', 5000) })], ['approval_mismatch']);
  //  برگهٔ صاحبِ نقشِ بی «مجاز» (خودِ صندوق‌دار) پذیرفته نیست: کلیدِ ثبت‌شده ندارد
  await expect('cashier', [fix('ca-self', 's-b', 5000, 'cash', 'hesabpay', { approval: { ...slipFor('ca-self', 5000), by: T.cashier.user.id } })],
    ['approval_self']);
});

test('روشن: بیش از ماندهٔ روش رد، با اصلاح‌های پیشینِ سرور (دو دستگاه یک پول را دو بار جابه‌جا نمی‌کنند)', async () => {
  await expect('owner', [fix('ex-1', 's-c', 100_001)], ['pay_fix_exceeds']);
  await expect('owner', [fix('ex-2', 's-c', 60_000)], ['applied']);
  const r = await expect('manager', [fix('ex-3', 's-c', 60_000)], ['pay_fix_exceeds']);
  const box = await boxOf(r.body.results[0].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.available, 40_000);
  await expect('manager', [fix('ex-4', 's-c', 40_000)], ['applied']);
  //  حساب‌پی حالا ۵۰۰ + ۱۰۰۰ = ۱۵۰۰ افغانی؛ برگشت به نقد تا همان
  await expect('owner', [fix('ex-5', 's-c', 150_001, 'hesabpay', 'cash')], ['pay_fix_exceeds']);
  await expect('owner', [fix('ex-6', 's-c', 150_000, 'hesabpay', 'cash')], ['applied']);
  //  روشی که فاکتور ندارد
  await expect('owner', [fix('ex-7', 's-c', 1, 'afpay_card', 'cash')], ['pay_fix_exceeds']);
});

test('روشن: شکلِ بد، فاکتورِ لغوشده و برگشت‌خورده برای همه رد؛ فاکتوری که سرور ندارد فقط شکل و مجوز', async () => {
  await expect('owner', [fix('bad-1', 's-b', 100, 'cash', 'account'), fix('bad-2', 's-b', 0), fix('bad-3', 's-b', 100, 'cash', 'hesabpay', { reason: 'نه' })],
    ['pay_fix_invalid', 'pay_fix_invalid', 'pay_fix_invalid']);
  await expect('owner', [fix('bad-x', 's-x', 100)], ['pay_fix_invalid']);
  await expect('owner', [fix('bad-r', 's-r', 100)], ['pay_fix_invalid']);
  await expect('owner', [fix('unk-o', 's-none', 999_999)], ['applied']);
  await expect('staff', [fix('unk-s', 's-none', 100)], ['approval_required']);
  await live.remove(shopId, guard.PAY_FIX_FLAG);
});

// ---------------------------------------------------------------- ② سقفِ بازپرداخت

const tx = (id, kind, type, refId, amountMinor, extra = {}) => op3('transactions', id, {
  debtorId: 'd-1', type, amountMinor, refId, kind, date: '2026-11-04', createdAt: Date.now(), notes: kind, ...extra,
});

test('بازپرداخت: خاموش ⇒ همان امروز (بازپرداختِ دوباره هم می‌نشیند)', async () => {
  await expect('cashier', [tx('o-d', 'special_order_deposit', 'receive', 'so-off', 10_000)], ['applied']);
  await expect('owner', [tx('o-r1', 'special_order_refund', 'give', 'so-off', 10_000), tx('o-r2', 'special_order_refund', 'give', 'so-off', 10_000)],
    ['applied', 'applied']);
});

test('بازپرداخت روشن: سفارشِ ویژه تا پیش‌پرداختِ نگه‌داشته؛ لغوِ دوم از دستگاهِ دیگر رد و همهٔ واحدِ tx برمی‌گردد', async () => {
  await live.set('all', guard.REFUND_FLAG, true);
  await expect('cashier', [tx('so-d', 'special_order_deposit', 'receive', 'so-1', 100_000)], ['applied']);
  await expect('owner', [tx('so-r1', 'special_order_refund', 'give', 'so-1', 100_000)], ['applied']);
  const status = (id) => op3('auditLog', id, { type: 'special_order_status', refId: 'so-1', amountMinor: 100_000, reason: 'تأمین نشد',
    specialOrder: { to: 'cancelled' } }, { tx: `tx.${id}` });
  const r = await expect('manager', [status('so-st2'), { ...tx('so-r2', 'special_order_refund', 'give', 'so-1', 100_000), tx: 'tx.so-st2' }],
    ['tx_rejected', 'refund_exceeds_held']);
  const box = await boxOf(r.body.results[1].op_id);
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.held, 0);
  assert.equal(await inV3('transactions', 'so-r2'), undefined);
  assert.equal(await inV3('auditLog', 'so-st2'), undefined);
  //  بی هیچ پیش‌پرداخت
  await expect('owner', [tx('so-r9', 'special_order_refund', 'give', 'so-none', 1)], ['refund_exceeds_held']);
});

test('بازپرداخت روشن: امانی = پیش‌پرداخت + قسط‌ها (همان دسته هم)، بیش از آن رد؛ ردیفِ دیگرِ دفتر دست نمی‌خورد', async () => {
  await expect('cashier', [
    tx('lw-d', 'layaway_deposit', 'receive', 'lw-1', 50_000),
    tx('lw-p1', 'layaway_installment', 'receive', 'lw-1', 30_000),
  ], ['applied', 'applied']);
  await expect('owner', [tx('lw-1-r', 'layaway_refund', 'give', 'lw-1', 80_001)], ['refund_exceeds_held']);
  await expect('owner', [tx('lw-p2', 'layaway_installment', 'receive', 'lw-1', 1), tx('lw-1-r', 'layaway_refund', 'give', 'lw-1', 80_001)],
    ['applied', 'applied']);
  //  پیش‌پرداختِ سفارشِ ویژهٔ همان شناسه پولِ امانی نیست
  await expect('cashier', [tx('mix-d', 'special_order_deposit', 'receive', 'lw-2', 5_000)], ['applied']);
  await expect('owner', [tx('lw-2-r', 'layaway_refund', 'give', 'lw-2', 5_000)], ['refund_exceeds_held']);
  await expect('cashier', [tx('plain-give', 'store_credit', 'give', 'lw-2', 9_000_000)], ['applied']);
  await live.remove('all', guard.REFUND_FLAG);
});
