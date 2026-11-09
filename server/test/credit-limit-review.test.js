'use strict';
/**
 * CRM-03 — سقفِ اعتبارِ مشتری در سرور، راهِ «بررسی» (`lib/credit-limit-review.js`، پرچمِ خاموشِ `shop.credit_limit_review`).
 *
 * سنجه‌ها: قاعده همان `CreditGate.check` ِ هسته (مرز، بی سقف، عددِ ناامن ⇒ بیش از سقف)؛ پرچمِ خاموش ⇒ هیچ سابقه؛ با پرچم
 * فروشِ نسیهٔ بیش از سقفِ شاگرد بی تأییدِ دستگاه در v1، v3 و راهِ قدیم/REST **پذیرفته** و دست‌نخورده می‌نشیند و یک
 * سابقهٔ `sale.credit_over_limit` (یک بار، حتی با تکرارِ push) می‌گیرد؛ زیرِ سقف، تأییدِ دستگاه (نام + دلیل)، صاحب و
 * ویرایشِ فاکتورِ پیشین ⇒ هیچ؛ دستهٔ آفلاینِ چندفاکتوره فقط همان فاکتوری را نشان می‌زند که از سقف گذشت؛ سابقه بی نام و
 * شمارهٔ مشتری، فقط برای `audit.view`، و دکانِ دیگر نه پرچم را می‌بیند نه سابقه را.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { many, query, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const review = require('../src/lib/credit-limit-review');

let admin = '';
let shopId;
let shopB;
let owner;
let staff;
let ownerB;
let staffB;
let n = 0;
const oid = () => `01JCL${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now() - 3_600_000;

const liveFlag = (key, value, scope) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const legacy = (who, device, changes) => h.api('POST', '/api/sync/push', {
  token: who.accessToken, headers: { 'X-App': 'shop' }, body: { deviceId: device, changes },
});
const statuses = (r) => r.body.results.map((x) => x.status);
const flags = async (shop = shopId) => (await many(
  `SELECT target_id, detail FROM audit_logs WHERE shop_id=$1 AND action='sale.credit_over_limit' ORDER BY created_at, id`, [shop]
)).map((r) => ({ sale: r.target_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));

const op1 = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: (clock += 1000), fields });
/** مشتری با سقف و ماندهٔ پیشین (v1، افغانی) */
const debtor1 = (id, limit, prior) => [
  op1('debtors', id, 'insert', { name: 'مشتریِ سقف‌دار', phone: '0700000001', creditLimit: limit, createdAt: clock }),
  ...(prior ? [op1('transactions', `${id}-old`, 'insert', { debtorId: id, type: 'give', amount: prior, date: '2026-11-01', createdAt: clock })] : []),
];
/** فاکتورِ نسیهٔ v1 با همراه‌ها — همان ثبتِ برنامه (`SalesEngine.record`) */
function sale1(id, debtorId, debt, { approval = null } = {}) {
  const at = (clock += 1000);
  const op = (table, rowId, type, fields) => ({ op_id: oid(), table, row_id: rowId, type, ts: at, fields });
  return [
    op('sales', id, 'insert', { total: 10000, discount: 0, finalTotal: 10000, paymentMethod: 'credit', debtorId, invoiceNumber: 7,
      paidAmount: 10000 - debt, remaining: debt, debtGiven: debt, debtSettled: 0, status: 'completed', createdAt: at }),
    op('transactions', `${id}-give`, 'insert', { debtorId, type: 'give', amount: debt, refId: id, kind: 'sale_credit', date: '2026-11-07', createdAt: at }),
    ...(approval ? [op('auditLog', `${id}-ok`, 'insert', { type: 'credit_over_limit', refId: id, approvedBy: approval.by, reason: approval.reason, createdAt: at })] : []),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'cl-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()],
  );
  admin = (await h.post('/api/admin/login', { username: 'cl-admin', password: 'Admin!12345' })).body.token;
  async function shopWithStaff(label) {
    const o = await h.newUser(`صاحبِ ${label}`, 'shop');
    const shop = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
    const id = shop.body?.shop?.id || shop.body?.id;
    const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
    const s = await h.newUser(`شاگردِ ${label}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
    return { id, owner: await h.signIn(o, 'shop'), staff: await h.signIn(s, 'shop') };
  }
  ({ id: shopId, owner, staff } = await shopWithStaff('سقفِ اعتبار'));
  ({ id: shopB, owner: ownerB, staff: staffB } = await shopWithStaff('سقفِ اعتبارِ ب'));
});
test.after(async () => { await h.stop(); });

test('قاعده همان CreditGate.check ِ هسته: بی سقف، مرز، بیش از سقف، عددِ ناامن و سقفِ منفی', () => {
  assert.equal(review.check(null, 400000, 200000), null, 'بی سقف');
  assert.equal(review.check(500000, 400000, 0), null, 'بی بردِ تازه');
  assert.equal(review.check(500000, 400000, -5), null);
  assert.equal(review.check(500000, 300000, 200000), null, 'برابرِ سقف آزاد است');
  assert.deepEqual(review.check(500000, 400000, 200000),
    { limitMinor: 500000, balanceMinor: 400000, addMinor: 200000, afterMinor: 600000, excessMinor: 100000 });
  assert.deepEqual(review.check(500000, -200000, 600000), null, 'طلبِ مشتری از سقف کم می‌کند');
  assert.equal(review.check(0, 0, 1).excessMinor, 1, 'سقفِ صفر');
  //  بسته‌به‌خطا: بیرون از ±(2^53−1)، سقفِ منفی و بردِ نادرست ⇒ «بیش از سقف»، نه «آزاد»
  assert.equal(review.check(500000, 2 ** 53, 1).afterMinor, null);
  assert.ok(review.check(-1, 0, 1));
  assert.ok(review.check(500000, 0, NaN));
  assert.ok(review.check(500000, NaN, 1));
  assert.equal(review.minorOf('12.5', true), 1250);
  assert.ok(Number.isNaN(review.minorOf('x', true)));
  assert.equal(review.limitOf({ creditLimit: null }, 'v1'), null);
  assert.equal(review.limitOf({ creditLimitMinor: 500000 }, 'v3'), 500000);
});

test('پرچمِ خاموش (پیش‌فرض): فروشِ نسیهٔ بیش از سقف همان امروز می‌نشیند و هیچ سابقه‌ای نمی‌گیرد', async () => {
  assert.deepEqual(statuses(await push1(staff, 'off-1', debtor1('d-off', 5000, 4000))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push1(staff, 'off-1', sale1('s-off', 'd-off', 2000))), ['applied', 'applied']);
  assert.equal((await flags()).length, 0);
});

test('پرچمِ روشن، v1: بیش از سقف بی تأیید ⇒ پذیرفته و دست‌نخورده، یک سابقهٔ بررسی (تکرار همان یکی)؛ زیرِ سقف، تأییدِ دستگاه و صاحب نه', async () => {
  await liveFlag('shop.credit_limit_review', true, shopId);
  assert.deepEqual(statuses(await push1(staff, 'pos-1', debtor1('d-1', 5000, 4000))), ['applied', 'applied']);
  const ops = sale1('s-over', 'd-1', 2000);
  assert.deepEqual(statuses(await push1(staff, 'pos-1', ops)), ['applied', 'applied'], 'رد نمی‌شود');
  const row = (await query(`SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='sales' AND row_id='s-over'`, [shopId])).rows[0];
  assert.equal(row.data.debtGiven, 2000, 'دفتر دست نمی‌خورد');
  let f = await flags();
  assert.equal(f.length, 1, JSON.stringify(f));
  assert.equal(f[0].sale, 's-over');
  assert.deepEqual([f[0].path, f[0].role, f[0].debtorId, f[0].deviceId], ['v1', 'staff', 'd-1', 'pos-1']);
  assert.deepEqual([f[0].limitMinor, f[0].balanceMinor, f[0].addMinor, f[0].afterMinor, f[0].excessMinor], [500000, 400000, 200000, 600000, 100000]);
  //  ⛔ بی دادهٔ شخصی: نه نام، نه شماره
  assert.ok(!JSON.stringify(f[0]).includes('0700000001') && !JSON.stringify(f[0]).includes('سقف‌دار'));
  //  تکرارِ همان دسته (پاسخ گم شد) ⇒ «تکراری»، سابقهٔ دوم نه؛ ویرایشِ همان فاکتور هم نه
  assert.deepEqual(statuses(await push1(staff, 'pos-1', ops)), ['duplicate', 'duplicate']);
  assert.equal((await push1(staff, 'pos-1', [op1('sales', 's-over', 'update', { seller: 'دیگر' })])).status, 200);
  assert.equal((await flags()).length, 1);

  //  زیرِ سقف (مشتریِ تازه با سقفِ بزرگ) و بی سقف ⇒ هیچ
  await push1(staff, 'pos-1', [...debtor1('d-2', 100000, 0), ...debtor1('d-3', null, 0)]);
  assert.deepEqual(statuses(await push1(staff, 'pos-1', sale1('s-under', 'd-2', 2000))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push1(staff, 'pos-1', sale1('s-nolimit', 'd-3', 9000))), ['applied', 'applied']);
  //  تأییدِ دستگاه (ردِ `credit_over_limit` با نام و دلیل) ⇒ هیچ؛ بی دلیل ⇒ سابقه
  assert.deepEqual(statuses(await push1(staff, 'pos-1', sale1('s-ok', 'd-1', 500, { approval: { by: 'مدیر', reason: 'مشتریِ همیشگی' } }))),
    ['applied', 'applied', 'applied']);
  await push1(staff, 'pos-1', sale1('s-noreason', 'd-1', 500, { approval: { by: 'مدیر', reason: ' ' } }));
  //  ویرایشِ فاکتورِ پیشینِ زیرِ سقف پس از بالا رفتنِ مانده ⇒ هیچ (فقط درجِ تازه)
  await push1(staff, 'pos-1', [op1('sales', 's-under', 'update', { seller: 'x' })]);
  //  صاحب خودش بررسی‌کننده است ⇒ هیچ
  assert.deepEqual(statuses(await push1(owner, 'own-1', sale1('s-owner', 'd-1', 3000))), ['applied', 'applied']);
  f = await flags();
  assert.deepEqual(f.map((x) => x.sale), ['s-over', 's-noreason']);
});

test('پرچمِ روشن، v1: دستهٔ آفلاینِ چندفاکتوره فقط فاکتوری را نشان می‌زند که از سقف گذشت', async () => {
  await push1(staff, 'pos-2', debtor1('d-batch', 5000, 0));
  const before = (await flags()).length;
  const r = await push1(staff, 'pos-2', [...sale1('b-1', 'd-batch', 3000), ...sale1('b-2', 'd-batch', 1500), ...sale1('b-3', 'd-batch', 1000)]);
  assert.ok(statuses(r).every((s) => s === 'applied'));
  assert.deepEqual((await flags()).slice(before).map((x) => [x.sale, x.balanceMinor, x.excessMinor]), [['b-3', 450000, 50000]]);
});

test('پرچمِ روشن، v3: همان قاعده روی دفترِ v3 (`creditLimitMinor`، `amountMinor`)؛ پذیرفته و یک سابقه', async () => {
  const before = (await flags()).length;
  const ins = (table, id, fields) => ({ op_id: oid(), hlc: H('pos3'), table, row_id: id, kind: 'insert', fields });
  const at = Date.now();
  const r0 = await push3(staff, 'pos-3', [
    ins('debtors', 'd3-1', { name: 'مشتریِ v3', phone: '0700000003', creditLimitMinor: 500000 }),
    ins('transactions', 'd3-1-old', { debtorId: 'd3-1', type: 'give', amountMinor: 400000, createdAt: at - 10000 }),
  ]);
  assert.deepEqual(statuses(r0), ['applied', 'applied'], JSON.stringify(r0.body));
  const sale = (id, debt) => [
    ins('sales', id, { totalMinor: 1000000, finalTotalMinor: 1000000, paidAmountMinor: 1000000 - debt, remainingMinor: debt,
      debtGivenMinor: debt, debtorId: 'd3-1', paymentMethod: 'credit', status: 'completed', createdAt: at }),
    ins('transactions', `${id}-give`, { debtorId: 'd3-1', type: 'give', amountMinor: debt, refId: id, kind: 'sale_credit', createdAt: at }),
  ];
  const r = await push3(staff, 'pos-3', sale('s3-over', 200000));
  assert.deepEqual(statuses(r), ['applied', 'applied'], JSON.stringify(r.body));
  const f = (await flags()).slice(before);
  assert.deepEqual(f.map((x) => [x.sale, x.path, x.balanceMinor, x.addMinor, x.excessMinor]), [['s3-over', 'v3', 400000, 200000, 100000]]);
  assert.deepEqual(statuses(await push3(staff, 'pos-3', sale('s3-over', 200000).map((o) => ({ ...o, op_id: oid() })))).length, 2);
  assert.equal((await flags()).slice(before).length, 1, 'درجِ دوبارهٔ همان سند سابقهٔ دوم نمی‌سازد');
});

test('پرچمِ روشن، راهِ قدیم و REST: پذیرفته و سابقه با `path`؛ ویرایشِ همان فاکتور سابقهٔ دوم نه', async () => {
  const before = (await flags()).length;
  const t = Date.now();
  const r0 = await legacy(staff, 'web-1', [
    { collection: 'debtors', id: 'dl-1', updatedAt: t, data: { name: 'مشتریِ قدیم', phone: '0700000009', creditLimit: 5000, createdAt: t - 5000 } },
    { collection: 'transactions', id: 'dl-1-old', updatedAt: t, data: { debtorId: 'dl-1', type: 'give', amount: 4000, createdAt: t - 5000 } },
  ]);
  assert.equal(r0.status, 200, JSON.stringify(r0.body));
  const sale = { total: 10000, finalTotal: 10000, paymentMethod: 'credit', debtorId: 'dl-1', paidAmount: 8000, remaining: 2000,
    debtGiven: 2000, status: 'completed', createdAt: t };
  const r = await legacy(staff, 'web-1', [{ collection: 'sales', id: 'sl-over', updatedAt: t, data: sale }]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.applied, 1);
  assert.equal((await legacy(staff, 'web-1', [{ collection: 'sales', id: 'sl-over', updatedAt: t + 10, data: { ...sale, seller: 'x' } }])).status, 200);
  const rest = await h.post('/api/sales/full', {
    sale: { id: 'sr-over', total: 10000, finalTotal: 10000, debtGiven: 1500, debtorId: 'dl-1', status: 'completed', createdAt: t + 20 },
    items: [{ id: 'sr-over-i', productId: 'p-cl', quantity: 1 }],
  }, { token: staff.accessToken });
  assert.ok([200, 201].includes(rest.status), JSON.stringify(rest.body));
  const f = (await flags()).slice(before);
  //  فاکتورِ راهِ قدیم بی ردیفِ «give» آمد ⇒ بردش از `debtGiven`؛ ماندهٔ دفتر همان ۴٬۰۰۰ برای هر دو
  assert.deepEqual(f.map((x) => [x.sale, x.balanceMinor, x.excessMinor]), [['sl-over', 400000, 100000], ['sr-over', 400000, 50000]]);
  assert.equal(f[0].path, 'legacy');
  assert.ok(['api', 'legacy'].includes(f[1].path), f[1].path);
});

test('جداییِ دکان و دیدن: دکانِ دیگر با همان شناسهٔ مشتری پرچم و سابقه را نمی‌بیند؛ سابقه فقط برای audit.view', async () => {
  //  دکانِ ب پرچم ندارد: همان شکلِ فروش ⇒ هیچ سابقه
  await push1(staffB, 'b-1', debtor1('d-1', 5000, 4000));
  assert.deepEqual(statuses(await push1(staffB, 'b-1', sale1('sb-over', 'd-1', 2000))), ['applied', 'applied']);
  assert.equal((await flags(shopB)).length, 0);
  //  دفترِ دکانِ ب (مانده‌ای بزرگ برای «d-1») در سنجشِ دکانِ الف نیست
  await liveFlag('shop.credit_limit_review', true, shopB);
  const mine = (await flags()).length;
  await push1(staff, 'pos-1', [...debtor1('d-iso', 5000, 0), ...sale1('s-iso', 'd-iso', 4000)]);
  assert.equal((await flags()).length, mine, 'زیرِ سقف در دکانِ خودش');
  const ownerView = await h.get('/api/shop/audit?limit=500', { token: owner.accessToken });
  assert.equal(ownerView.status, 200);
  assert.ok(ownerView.body.entries.some((e) => e.action === 'sale.credit_over_limit'));
  const otherView = await h.get('/api/shop/audit?limit=500', { token: ownerB.accessToken });
  assert.ok(!otherView.body.entries.some((e) => e.action === 'sale.credit_over_limit'));
  assert.equal((await h.get('/api/shop/audit', { token: staff.accessToken })).status, 403, 'شاگرد سابقه را نمی‌بیند');
});
