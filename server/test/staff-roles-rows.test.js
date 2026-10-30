'use strict';
/**
 * A16 — قاعدهٔ ردیفِ صندوق‌دار در سه راهِ همگام‌سازی (بازبینیِ مستقلِ #336، یافته‌های ۱ تا ۴).
 * همان سناریوهای آزمونِ اثباتِ بازبین، وارونه: هر درزی که او باز نشان داد این‌جا بسته است.
 *   ۱. «insert» روی ردیفِ موجود (حتی حذف‌شده) ویرایش است؛ شناسهٔ تکراریِ دسته هم.
 *   ۲. اصلاحِ برگشت: isAdjustment، بی قیمت، کالای همان قلم، نه بیش از مقدارش، یک اصلاح برای هر
 *      قلم، و فقط برگشتِ خودی (همین دسته یا همین دستگاه/کاربر).
 *   ۳. برگشتِ شبح: برگشتی که نمی‌نشیند (op_id ِ تکراری، tx ِ دیگر، لغوشده) نشانی نیست.
 *   ۴. حرکتِ انبار: فقط فاکتور و برگشت، با نشانیِ درست.
 *   ۰. (#406) کدِ ثابتِ شاگرد نقشِ حسابدار را «شاگرد» نمی‌کند.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');

let shopId = '';
const T = {};
let n = 0;
const oid = () => `01JRW${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
const push1 = (role, ops) => h.api('POST', '/api/sync/v1/push', {
  token: T[role].accessToken, body: { device_id: `r1-${role}`, schema_version: 1, ops },
});
const push3 = (role, ops) => h.api('POST', '/api/sync/v3/push', {
  token: T[role].accessToken, body: { device_id: `r3-${role}`, protocol: 3, ops },
});
const legacy = (role, changes) => h.post('/api/sync/push', { deviceId: `lg-${role}`, changes }, { token: T[role].accessToken });
const v1op = (table, rowId, fields, type = 'insert', opId = oid()) => ({ op_id: opId, table, row_id: rowId, type, ts: Date.now(), fields });
const v3op = (node, table, rowId, fields, tx = '', opId = oid()) => ({
  op_id: opId, hlc: H(node), table, row_id: rowId, kind: 'insert', fields, ...(tx ? { tx } : {}),
});
const reasons = (r) => r.body.results.map((x) => x.reason || x.status);
const row1 = (rowId) => one(
  `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name='warehouseEntries' AND row_id=$2`,
  [shopId, rowId]
);
const v1Exists = async (table, rowId) => !!(await one(
  `SELECT 1 FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, rowId]
));
const v3Exists = async (table, rowId) => !!(await one(
  `SELECT 1 FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3 AND inserted`, [shopId, table, rowId]
));
const lgExists = async (table, id) => !!(await one(`SELECT 1 FROM ${table} WHERE shop_id=$1 AND id=$2`, [shopId, id]));
const DENY = 'table_not_allowed';
//  اصلاحِ درستِ یک قلمِ برگشت (v1 و راهِ قدیم)؛ v3 همان با Milli
const adj = (refId, extra = {}) => ({ productId: 'pR', units: -1, isAdjustment: true, adjKind: 'damage', refId, ...extra });
const adj3 = (refId, extra = {}) => ({ productId: 'pR', unitsMilli: -1000, isAdjustment: true, adjKind: 'damage', refId, ...extra });
const ret1 = (docId, extra = {}) => ({ saleId: 's-none', productId: 'pR', quantity: 1, amount: 0, docId, ...extra });
const ret3 = (docId, extra = {}) => ({ saleId: 's-none', productId: 'pR', quantityMilli: 1000, amountMinor: 0, docId, ...extra });

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'rows-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const o = await h.newUser('صاحبِ دکانِ قاعدهٔ ردیف', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ قاعدهٔ ردیف' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = await h.signIn(o, 'shop');
  for (const role of ['cashier', 'storekeeper', 'accountant']) {
    const inv = await h.post('/api/shop/invite', { role }, { token: T.owner.accessToken });
    assert.equal(inv.status, 201, `${role}: ${JSON.stringify(inv.body)}`);
    const u = await h.newUser(`کارمندِ ${role}`, 'shop');
    const joined = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
    assert.ok([200, 201].includes(joined.status), `${role}: ${JSON.stringify(joined.body)}`);
    T[role] = await h.signIn(u, 'shop');
  }
  //  برگشتِ کهنه‌ای که صاحب دکان ثبت کرده (v1، v3 و راهِ قدیم) — مالِ صندوق‌دار نیست
  assert.deepEqual(reasons(await push1('owner', [v1op('returns', 'old-r1', ret1('old-rd1'))])), ['applied']);
  const r3 = await push3('owner', [v3op('o', 'returns', 'old-r3', ret3('old-rd3'))]);
  assert.deepEqual(reasons(r3), ['applied'], JSON.stringify(r3.body));
  assert.equal((await legacy('owner', [
    { collection: 'returns', id: 'old-lg', updatedAt: Date.now(), data: ret1('old-lgd') },
  ])).status, 200);
});
test.after(async () => { await h.stop(); });

test('۱ — v1: «insert» ِ صندوق‌دار روی ردیفِ موجودِ انباردار (زنده یا حذف‌شده) ویرایش است و رد می‌شود', async () => {
  const sk = await push1('storekeeper', [
    v1op('warehouseEntries', 'w-stk-1', { productId: 'pA', units: 10, price: 50 }),
    v1op('warehouseEntries', 'w-stk-2', { productId: 'pA', units: 20, price: 50 }),
  ]);
  assert.deepEqual(reasons(sk), ['applied', 'applied']);
  assert.deepEqual(reasons(await push1('storekeeper', [v1op('warehouseEntries', 'w-stk-2', null, 'delete')])), ['applied']);
  assert.ok((await row1('w-stk-2')).deleted_at, 'حذف شد');

  assert.deepEqual(reasons(await push1('cashier', [v1op('warehouseEntries', 'w-stk-1', { units: -999 }, 'update')])), [DENY]);
  //  همان ویرایش با type=insert — حتی همراهِ برگشتِ خودیِ همین دسته که اصلاح را درست می‌کند
  const ow = await push1('cashier', [
    v1op('returns', 'r-ow', ret1('rd-ow')),
    v1op('warehouseEntries', 'w-stk-1', adj('rd-ow')),
    v1op('warehouseEntries', 'w-stk-2', adj('rd-ow')),
  ]);
  assert.deepEqual(reasons(ow), ['applied', DENY, DENY], JSON.stringify(ow.body));
  const w1 = await row1('w-stk-1');
  assert.equal(w1.data.units, 10, 'ردیفِ انباردار دست‌نخورده');
  assert.equal(w1.data.price, 50);
  assert.ok((await row1('w-stk-2')).deleted_at, 'ردیفِ حذف‌شده زنده نشد');

  //  شناسهٔ تکراری در همان دسته: دومی ویرایشِ اولی است ⇒ هر دو رد
  const dup = await push1('cashier', [
    v1op('returns', 'r-dup', ret1('rd-dup', { quantity: 2 })),
    v1op('warehouseEntries', 'w-dup', adj('rd-dup')),
    v1op('warehouseEntries', 'w-dup', adj('rd-dup', { units: -2 })),
  ]);
  assert.deepEqual(reasons(dup), ['applied', DENY, DENY], JSON.stringify(dup.body));
  assert.equal(await v1Exists('warehouseEntries', 'w-dup'), false);

  //  v3: insert روی ردیفِ موجود هم رد است (نه صندوقِ تعارض)
  const sk3 = await push3('storekeeper', [v3op('k', 'warehouseEntries', 'w3-stk', { productId: 'pA', unitsMilli: 10000, priceMinor: 5000 })]);
  assert.deepEqual(reasons(sk3), ['applied'], JSON.stringify(sk3.body));
  const ow3 = await push3('cashier', [
    v3op('c', 'returns', 'r3-ow', ret3('rd3-ow'), 'tx-ow3'),
    v3op('c', 'warehouseEntries', 'w3-stk', adj3('rd3-ow'), 'tx-ow3'),
  ]);
  assert.deepEqual(reasons(ow3), ['tx_rejected', DENY], JSON.stringify(ow3.body));
});

test('۲ — اصلاحِ برگشت محتوا را می‌بندد: رسیدِ دستی با برچسبِ برگشت، قیمت، کالا و مقدارِ بیرون از قلم، اصلاحِ دوم، برگشتِ دیگری', async () => {
  //  آزمونِ بازبین: رسیدِ دستی (بی isAdjustment، با قیمت، هر مقدار) با refId ِ برگشتِ کهنه
  const a = await push1('cashier', [
    v1op('warehouseEntries', 'w-man-1', { productId: 'pB', units: 500, price: 70, adjKind: 'customer_return', refId: 'old-rd1' }),
    v1op('warehouseEntries', 'w-man-2', adj('old-rd1')),            //  اصلاحِ درست، ولی برگشتِ صاحب دکان
  ]);
  assert.deepEqual(reasons(a), [DENY, DENY], JSON.stringify(a.body));
  const b = await push3('cashier', [
    v3op('c', 'warehouseEntries', 'w3-man-1', { productId: 'pB', unitsMilli: 500000, priceMinor: 7000, adjKind: 'damage', refId: 'old-rd3' }),
  ]);
  assert.deepEqual(reasons(b), [DENY], JSON.stringify(b.body));
  assert.equal(await v3Exists('warehouseEntries', 'w3-man-1'), false);
  const c = await legacy('cashier', [
    { collection: 'warehouseEntries', id: 'lg-man-1', updatedAt: Date.now(), data: adj('old-lgd') },
  ]);
  assert.equal(c.status, 403, 'برگشتِ کاربرِ دیگر');
  //  راهِ قدیم با برگشتِ همین دسته: رسیدِ دستی و کالای دیگر ⇒ ۴۰۳ ِ کلِ دسته
  for (const bad of [
    { productId: 'pR', units: 500, price: 70, adjKind: 'damage', refId: 'lg-rd0' },
    adj('lg-rd0', { productId: 'pC' }),
    adj('lg-rd0', { units: -900 }),
  ]) {
    const d = await legacy('cashier', [
      { collection: 'returns', id: 'lg-r0', updatedAt: Date.now(), data: ret1('lg-rd0') },
      { collection: 'warehouseEntries', id: 'lg-man-2', updatedAt: Date.now(), data: bad },
    ]);
    assert.equal(d.status, 403, JSON.stringify(bad));
    assert.equal(await lgExists('sale_returns', 'lg-r0'), false, 'همه یا هیچ');
  }
  const okLg = await legacy('cashier', [
    { collection: 'returns', id: 'lg-r0', updatedAt: Date.now(), data: ret1('lg-rd0') },
    { collection: 'warehouseEntries', id: 'lg-adj-0', updatedAt: Date.now(), data: adj('lg-rd0') },
  ]);
  assert.equal(okLg.status, 200, JSON.stringify(okLg.body));

  //  v1، هر شرط جدا، با برگشتِ خودیِ همین دسته
  const cases = [
    [{ isAdjustment: undefined }, 'بی isAdjustment'],
    [{ price: 70 }, 'با قیمت'],
    [{ cartons: 2 }, 'با کارتن'],
    [{ productId: 'pC' }, 'کالایی که در برگشت نیست'],
    [{ units: -2 }, 'بیش از مقدارِ قلم'],
    [{ units: 1 }, 'ضایعاتِ مثبت'],
    [{ adjKind: 'count' }, 'نوعِ دیگر'],
  ];
  for (const [extra, why] of cases) {
    const docId = `rd-c-${++n}`;
    const r = await push1('cashier', [v1op('returns', `r-${docId}`, ret1(docId)), v1op('warehouseEntries', `w-${docId}`, adj(docId, extra))]);
    assert.deepEqual(reasons(r), ['applied', DENY], `${why}: ${JSON.stringify(r.body)}`);
  }
  //  درست ⇒ می‌نشیند؛ اصلاحِ دوم برای همان قلم (همین دسته یا بعد) ⇒ رد
  const good = await push1('cashier', [
    v1op('returns', 'r-good', ret1('rd-good')),
    v1op('warehouseEntries', 'w-good', adj('rd-good')),
    v1op('warehouseEntries', 'w-good-2', adj('rd-good')),
  ]);
  assert.deepEqual(reasons(good), ['applied', 'applied', DENY], JSON.stringify(good.body));
  assert.deepEqual(reasons(await push1('cashier', [v1op('warehouseEntries', 'w-good-3', adj('rd-good'))])), [DENY]);
  //  برگشتِ بی‌رسیدِ کالای سالم (مثبت) برای قلمِ برگشتی که همین دستگاه پیش‌تر ثبت کرد ⇒ می‌نشیند
  assert.deepEqual(reasons(await push1('cashier', [v1op('returns', 'r-later', ret1('rd-later'))])), ['applied']);
  const later = await push1('cashier', [
    v1op('warehouseEntries', 'w-later', adj('rd-later', { units: 1, adjKind: 'customer_return' })),
  ]);
  assert.deepEqual(reasons(later), ['applied'], JSON.stringify(later.body));
  //  ولی از دستگاهِ دیگرِ همان صندوق‌دار نه (نشانیِ خودی = همین دستگاه)
  const other = await h.api('POST', '/api/sync/v1/push', {
    token: T.cashier.accessToken,
    body: { device_id: 'r1-cashier-2', schema_version: 1, ops: [v1op('returns', 'r-dev', ret1('rd-dev'))] },
  });
  assert.deepEqual(reasons(other), ['applied']);
  assert.deepEqual(reasons(await push1('cashier', [v1op('warehouseEntries', 'w-dev', adj('rd-dev'))])), [DENY]);
});

test('۳ — برگشتِ شبح: برگشتی که نمی‌نشیند (op_id ِ تکراری، tx ِ دیگر، لغوشده) نشانیِ اصلاح نیست', async () => {
  const first = v1op('auditLog', 'a-seed', { type: 'note' });
  assert.deepEqual(reasons(await push1('cashier', [first])), ['applied']);
  const r = await push1('cashier', [
    v1op('returns', 'ghost-r', ret1('ghost-rd'), 'insert', first.op_id),
    v1op('warehouseEntries', 'w-ghost', adj('ghost-rd')),
    v1op('stockMovements', 'm-ghost', { productId: 'pR', type: 'damage', qty: -1, refId: 'ghost-r' }),
  ]);
  assert.deepEqual(reasons(r), ['duplicate', DENY, DENY], JSON.stringify(r.body));
  assert.equal(await v1Exists('returns', 'ghost-r'), false);
  assert.equal(await v1Exists('warehouseEntries', 'w-ghost'), false);
  assert.equal(await v1Exists('stockMovements', 'm-ghost'), false);

  const seed3 = v3op('c', 'auditLog', 'a3-seed', { type: 'note' });
  assert.deepEqual(reasons(await push3('cashier', [seed3])), ['applied']);
  const r3 = await push3('cashier', [
    v3op('c', 'returns', 'ghost-r3', ret3('ghost-rd3'), '', seed3.op_id),
    v3op('c', 'warehouseEntries', 'w3-ghost', adj3('ghost-rd3')),
  ]);
  assert.equal(reasons(r3)[1], DENY, JSON.stringify(r3.body));
  assert.equal(await v3Exists('returns', 'ghost-r3'), false);
  assert.equal(await v3Exists('warehouseEntries', 'w3-ghost'), false);

  //  v3: فقط از همان tx — برگشت در یک tx و اصلاح در tx ِ دیگرِ همان دسته ⇒ رد
  const split = await push3('cashier', [
    v3op('c', 'returns', 'r3-split', ret3('rd3-split'), 'tx-a'),
    v3op('c', 'warehouseEntries', 'w3-split', adj3('rd3-split'), 'tx-b'),
  ]);
  assert.deepEqual(reasons(split), ['applied', DENY], JSON.stringify(split.body));
  //  و همان در یک tx ⇒ می‌نشیند
  const same = await push3('cashier', [
    v3op('c', 'returns', 'r3-same', ret3('rd3-same'), 'tx-c'),
    v3op('c', 'warehouseEntries', 'w3-same', adj3('rd3-same'), 'tx-c'),
    v3op('c', 'stockMovements', 'm3-same', { productId: 'pR', type: 'damage', qtyMilli: -1000, refId: 'r3-same' }, 'tx-c'),
  ]);
  assert.deepEqual(reasons(same), ['applied', 'applied', 'applied'], JSON.stringify(same.body));

  //  برگشتِ ثبت‌شده‌ای که لغو شد دیگر پایهٔ اصلاح نیست
  assert.deepEqual(reasons(await push3('cashier', [v3op('c', 'returns', 'r3-cx', ret3('rd3-cx'), 'tx-cx')])), ['applied']);
  const cx = await push3('owner', [{ op_id: oid(), hlc: H('o'), table: 'returns', row_id: 'r3-cx', kind: 'cancel', reason: 'اشتباه' }]);
  assert.deepEqual(reasons(cx), ['applied'], JSON.stringify(cx.body));
  assert.deepEqual(reasons(await push3('cashier', [v3op('c', 'warehouseEntries', 'w3-cx', adj3('rd3-cx'))])), [DENY]);
});

test('۴ — حرکتِ انبارِ صندوق‌دار فقط از فاکتور و برگشت، در v1، v3 و راهِ قدیم', async () => {
  const a = await push1('cashier', [
    v1op('stockMovements', 'm-free-1', { productId: 'pD', type: 'adjust', qty: 1000 }),
    v1op('stockMovements', 'm-free-2', { productId: 'pD', type: 'sale', qty: -1, refId: 's-404' }),
    v1op('stockMovements', 'm-free-3', { productId: 'pD', type: 'sale', qty: 5 }),
  ]);
  assert.deepEqual(reasons(a), [DENY, DENY, DENY], JSON.stringify(a.body));
  const b = await push3('cashier', [v3op('c', 'stockMovements', 'm3-free-1', { productId: 'pD', qtyMilli: -1000000, type: 'purchase' })]);
  assert.deepEqual(reasons(b), [DENY], JSON.stringify(b.body));
  assert.equal(await v3Exists('stockMovements', 'm3-free-1'), false);
  const c = await legacy('cashier', [
    { collection: 'stockMovements', id: 'lg-m-free', updatedAt: Date.now(), data: { productId: 'pD', type: 'adjust', qty: 777 } },
  ]);
  assert.equal(c.status, 403, JSON.stringify(c.body));
  const { can } = require('../src/lib/permissions');
  assert.equal(can('cashier', 'stock.adjust'), false);
  assert.equal(can('cashier', 'stock.move'), false);

  //  فاکتورِ همین دسته (با قلمش) و لغوِ ثبت‌شدهٔ آن ⇒ می‌نشیند — همان ردیف‌های SalesEngine
  const sale = await push1('cashier', [
    v1op('sales', 's-mv', { total: 10, discount: 0, finalTotal: 10, status: 'completed' }),
    v1op('saleItems', 'si-mv', { saleId: 's-mv', productId: 'pD', quantity: 1, unitPrice: 10 }),
    v1op('stockMovements', 'm-sale', { productId: 'pD', type: 'sale', qty: -1, refId: 's-mv' }),
  ]);
  assert.deepEqual(reasons(sale), ['applied', 'applied', 'applied'], JSON.stringify(sale.body));
  //  صندوق‌دار sales.cancel ندارد (سرور cancel_not_allowed)؛ لغو را صاحب می‌زند، حرکتش را صندوق‌دار
  const off = await push1('owner', [v1op('sales', 's-mv', { status: 'cancelled', cancelReason: 'آزمون' }, 'update')]);
  assert.deepEqual(reasons(off), ['applied'], JSON.stringify(off.body));
  const back = await push1('cashier', [v1op('stockMovements', 'm-sale-x', { productId: 'pD', type: 'sale_cancel', qty: 1, refId: 's-mv' })]);
  assert.deepEqual(reasons(back), ['applied'], JSON.stringify(back.body));
  //  راهِ قدیم: فاکتورِ کامل (`/api/sales/full` حرکت را با saleId می‌نشاند)
  const full = await h.post('/api/sales/full', {
    sale: { id: 'lg-full-mv', total: 10, finalTotal: 10 }, items: [{ productId: 'pD', quantity: 1, unitPrice: 10 }],
    stockMovements: [{ productId: 'pD', qty: -1, type: 'sale' }],
  }, { token: T.cashier.accessToken });
  assert.equal(full.status, 201, JSON.stringify(full.body));
});

test('۵ — حرکتِ سند سقف دارد: کالای همان فاکتور، لغو فقط برای فاکتورِ لغوشده، و هر قلم تا مقدارش (v1، v3، قدیم)', async () => {
  //  v1: فاکتورِ ثبت‌شده با ۲ عدد pF، لغونشده
  assert.deepEqual(reasons(await push1('owner', [
    v1op('sales', 's-cap', { total: 20, finalTotal: 20, status: 'completed' }),
    v1op('saleItems', 'si-cap', { saleId: 's-cap', productId: 'pF', quantity: 2, unitPrice: 10 }),
    v1op('returns', 'r-cap', { saleId: 's-cap', productId: 'pF', quantity: 2, amount: 0, docId: 'rd-cap' }),
  ])), ['applied', 'applied', 'applied']);
  const bad = async (fields) => {
    const r = await push1('cashier', [v1op('stockMovements', `m-cap-${++n}`, fields)]);
    assert.deepEqual(reasons(r), [DENY], `${JSON.stringify(fields)} ⇒ ${JSON.stringify(r.body)}`);
  };
  //  یافتهٔ ۱: لغوِ فروشِ لغونشده، کالای بیرون از فاکتور، بیش از قلم
  await bad({ productId: 'pF', type: 'sale_cancel', qty: 1, refId: 's-cap' });
  await bad({ productId: 'pX', type: 'sale', qty: -1, refId: 's-cap' });
  await bad({ productId: 'pF', type: 'sale', qty: -3, refId: 's-cap' });
  //  یافتهٔ ۲: برگشتِ تکراری روی همان قلم — یک بار می‌نشیند، بارِ دوم (و با docId) نه
  const once = await push1('cashier', [v1op('stockMovements', 'm-cap-ret', { productId: 'pF', type: 'customer_return', qty: 2, refId: 'r-cap' })]);
  assert.deepEqual(reasons(once), ['applied'], JSON.stringify(once.body));
  await bad({ productId: 'pF', type: 'customer_return', qty: 1, refId: 'r-cap' });
  await bad({ productId: 'pF', type: 'customer_return', qty: 1, refId: 'rd-cap' });
  const twice = await push1('cashier', [
    v1op('stockMovements', 'm-cap-d1', { productId: 'pF', type: 'damage', qty: -2, refId: 'r-cap' }),
    v1op('stockMovements', 'm-cap-d2', { productId: 'pF', type: 'damage', qty: -1, refId: 'r-cap' }),
  ]);
  assert.deepEqual(reasons(twice), ['applied', DENY], JSON.stringify(twice.body));
  assert.equal(await v1Exists('stockMovements', 'm-cap-d2'), false);
  //  پس از لغوِ فاکتور (صاحب؛ صندوق‌دار sales.cancel ندارد) ⇒ لغو تا مقدارِ قلم؛ بیش از آن نه
  assert.deepEqual(reasons(await push1('owner', [v1op('sales', 's-cap', { status: 'cancelled' }, 'update')])), ['applied']);
  const cancel = await push1('cashier', [
    v1op('stockMovements', 'm-cap-x1', { productId: 'pF', type: 'sale_cancel', qty: 2, refId: 's-cap' }),
    v1op('stockMovements', 'm-cap-x2', { productId: 'pF', type: 'sale_cancel', qty: 1, refId: 's-cap' }),
  ]);
  assert.deepEqual(reasons(cancel), ['applied', DENY], JSON.stringify(cancel.body));
  await bad({ productId: 'pF', type: 'sale_cancel', qty: 1, refId: 's-cap' });

  //  v3: لغوِ فاکتورِ لغونشده و برگشتِ تکراری در یک tx ⇒ کلِ tx برمی‌گردد
  const s3 = await push3('owner', [
    v3op('o', 'sales', 's3-cap', { totalMinor: 2000, finalTotalMinor: 2000, status: 'completed' }, 'tx-s3cap'),
    v3op('o', 'saleItems', 'si3-cap', { saleId: 's3-cap', productId: 'pF', quantityMilli: 2000, unitPriceMinor: 1000 }, 'tx-s3cap'),
  ]);
  assert.deepEqual(reasons(s3), ['applied', 'applied'], JSON.stringify(s3.body));
  const x3 = await push3('cashier', [v3op('c', 'stockMovements', 'm3-cap-x', { productId: 'pF', type: 'sale_cancel', qtyMilli: 1000, refId: 's3-cap' })]);
  assert.deepEqual(reasons(x3), [DENY], JSON.stringify(x3.body));
  const r3 = await push3('cashier', [
    v3op('c', 'returns', 'r3-cap', { saleId: 's3-cap', productId: 'pF', quantityMilli: 1000, amountMinor: 0, docId: 'rd3-cap' }, 'tx-r3cap'),
    v3op('c', 'stockMovements', 'm3-cap-r1', { productId: 'pF', type: 'customer_return', qtyMilli: 1000, refId: 'r3-cap' }, 'tx-r3cap'),
    v3op('c', 'stockMovements', 'm3-cap-r2', { productId: 'pF', type: 'customer_return', qtyMilli: 1000, refId: 'r3-cap' }, 'tx-r3cap'),
  ]);
  assert.ok(reasons(r3).includes(DENY), JSON.stringify(r3.body));
  assert.equal(await v3Exists('stockMovements', 'm3-cap-r1'), false);
  assert.equal(await v3Exists('returns', 'r3-cap'), false);
  const s3sale = await push3('cashier', [v3op('c', 'stockMovements', 'm3-cap-s', { productId: 'pF', type: 'sale', qtyMilli: -2000, refId: 's3-cap' })]);
  assert.deepEqual(reasons(s3sale), ['applied'], JSON.stringify(s3sale.body));
  const s3more = await push3('cashier', [v3op('c', 'stockMovements', 'm3-cap-s2', { productId: 'pF', type: 'sale', qtyMilli: -1000, refId: 's3-cap' })]);
  assert.deepEqual(reasons(s3more), [DENY], JSON.stringify(s3more.body));

  //  راهِ قدیم: لغوِ فاکتورِ لغونشده (همه یا هیچ)
  const lg = await h.post('/api/sales/full', {
    sale: { id: 'lg-cap', total: 10, finalTotal: 10 }, items: [{ productId: 'pF', quantity: 1, unitPrice: 10 }],
    stockMovements: [{ productId: 'pF', qty: -1, type: 'sale' }],
  }, { token: T.cashier.accessToken });
  assert.equal(lg.status, 201, JSON.stringify(lg.body));
  const lgx = await legacy('cashier', [{ collection: 'stockMovements', id: 'lg-cap-x', updatedAt: Date.now(),
    data: { productId: 'pF', qty: 1, type: 'sale_cancel', refId: 'lg-cap' } }]);
  assert.equal(lgx.status, 403, JSON.stringify(lgx.body));
  assert.equal(await lgExists('stock_movements', 'lg-cap-x'), false);
  const lgs = await legacy('cashier', [{ collection: 'stockMovements', id: 'lg-cap-s2', updatedAt: Date.now(),
    data: { productId: 'pF', qty: -1, type: 'sale', refId: 'lg-cap' } }]);
  assert.equal(lgs.status, 403, `فروشِ دوم بیش از قلم: ${JSON.stringify(lgs.body)}`);
});

test('کنترل — حسابدار: v1/v3/قدیم/عکس هیچ نمی‌نشیند؛ op_id ِ تکراری هم فقط «تکراری» است', async () => {
  const a = await push1('accountant', [
    v1op('auditLog', 'a-acc', { type: 'note' }),
    { op_id: oid(), table: null, table_name: 'WAREHOUSE_entries', row_id: 'w-acc', type: 'insert', fields: { units: 1, adjKind: 'damage', refId: 'old-rd1' } },
    v1op('returns', 'r-acc', { amount: 0 }, 'insert', 'nonexistent-but-valid-01'),
  ]);
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.deepEqual(reasons(a), [DENY, DENY, DENY]);
  const b = await push3('accountant', [v3op('a', 'auditLog', 'a3-acc', { type: 'note' })]);
  assert.deepEqual(reasons(b), [DENY]);
  assert.equal((await legacy('accountant', [{ collection: 'auditLog', id: 'lg-acc', data: { type: 'x' } }])).status, 403);
  assert.equal((await h.api('PUT', '/api/sync/v1/photos/pA', { token: T.accountant.accessToken, body: {} })).status, 403);
  assert.equal(await v1Exists('auditLog', 'a-acc'), false);
  assert.equal(await v1Exists('warehouseEntries', 'w-acc'), false);
});

test('۰ — کدِ ثابتِ شاگرد نقشِ حسابدار را بالا نمی‌برد: همان حسابدار می‌ماند و هنوز هیچ نمی‌نویسد (#406)', async () => {
  const standing = (await h.get('/api/shop/staff-code', { token: T.owner.accessToken })).body.code;
  assert.ok(standing, 'کدِ ثابت');
  const u = await h.newUser('شاگردی که حسابدار شد', 'shop');
  const j1 = await h.post('/api/shop/join', { code: standing }, { token: u.accessToken });
  assert.ok([200, 201].includes(j1.status), JSON.stringify(j1.body));
  const m = (await h.get('/api/shop/members', { token: T.owner.accessToken })).body.members.find((x) => x.userId === u.user.id);
  assert.ok(m, 'عضو');
  const owner = await h.signIn(T.owner, 'shop');
  const ch = await h.patch(`/api/shop/members/${m.id}`, { role: 'accountant' }, { token: owner.accessToken });
  assert.equal(ch.status, 200, JSON.stringify(ch.body));
  let s = await h.signIn(u, 'shop');
  assert.equal(s.shop.actualRole, 'accountant');
  const push = (ops) => h.api('POST', '/api/sync/v1/push', { token: s.accessToken, body: { device_id: 'esc-1', schema_version: 1, ops } });
  assert.deepEqual(reasons(await push([v1op('products', 'p-esc-0', { name: 'x' })])), [DENY]);
  //  خودِ کارمند همان کدِ ثابتِ همیشگی را دوباره می‌زند
  const j2 = await h.post('/api/shop/join', { code: standing }, { token: s.accessToken });
  assert.ok([200, 201].includes(j2.status), JSON.stringify(j2.body));
  assert.equal(j2.body.actualRole, 'accountant', 'نقش بی اجازهٔ صاحب دکان بالا نرفت');
  s = await h.signIn(u, 'shop');
  assert.equal(s.shop.actualRole, 'accountant');
  assert.deepEqual(reasons(await push([v1op('products', 'p-esc-1', { name: 'x', salePrice: 1 })])), [DENY]);
  assert.equal(await v1Exists('products', 'p-esc-1'), false);
});
