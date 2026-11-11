'use strict';
/**
 * ⛔ هیچ‌کس — صاحبِ دکان هم — بیش از آنچه روی همان قلمِ فاکتور فروخته شد برنمی‌گرداند (`lib/return-line-cap.js`،
 * سفرِ JB5). پیش از درمان: دو دستگاهِ صاحب، هر دو بی اینترنت، همان قلم را کامل برمی‌گرداندند و سرور هر دو را
 * می‌نشاند (پول و موجودی دو بار) — v1، v3 و REST.
 *
 *   ① حساب: همهٔ `license/test-vectors/return-cap-v1.json` (همان بردارِ `ReturnCapVectorsTest` ِ core)
 *   ② دو دستگاهِ صاحب، بی اینترنت، همان برگشتِ کامل: یکی می‌نشیند، دیگری با همهٔ همراه‌هایش در صندوقِ تعارض
 *      (v1، v3، هم‌زمان، و میانِ v1 و v3)
 *   ③ برگشت‌های تکه‌ای که جمعشان از فروخته بیشتر شود؛ پولِ بیش از قلم با مقدارِ درست
 *   ④ تلاشِ دوباره: همان op دو بار یک بار شمرده می‌شود؛ همان ردیف با op ِ تازه هم؛ opِ ردشده دوباره ⇒ یک صندوق
 *   ⑤ REST (`/api/returns`) همه یا هیچ ⇒ ۴۰۹ و صندوق؛ فاکتورِ لغوشده؛ برگشتِ لغوشده دیگر شمرده نمی‌شود
 *   ⑥ جدا از دکانِ دیگر؛ خاموش‌کنِ اضطراری (`shop.returns.line_cap` = false)؛ پمپ همان `guard` ِ پیشین
 *   ⑦ مهاجرتِ ۳۰۵ و rollback ِ آن
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const db = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const liveConfig = require('../src/lib/live-config');
const cap = require('../src/lib/return-line-cap');

const ROOT = path.join(__dirname, '..', '..');
const V = JSON.parse(fs.readFileSync(path.join(ROOT, 'license', 'test-vectors', 'return-cap-v1.json'), 'utf8'));

let shopId;
let owner;
let staff;
let other;            //  صاحبِ دکانِ دیگر
let otherShop;
let n = 0;
const oid = () => `01JRL${String(++n).padStart(21, '0')}`;
let tick = 0;
const H = (node) => hlc.format({ wall: Date.now() + (++tick), counter: 0, node });
let clock = Date.now();
const ts = () => (clock += 10);

const push1 = (who, device, ops) => h.api('POST', '/api/sync/v1/push', { token: who.accessToken, body: { device_id: device, schema_version: 1, ops } });
const push3 = (who, device, ops) => h.api('POST', '/api/sync/v3/push', { token: who.accessToken, body: { device_id: device, protocol: 3, ops } });
const statuses = (r) => r.body.results.map((x) => (x.status === 'rejected' ? `rejected:${x.reason}` : x.status));
const boxesOf = (shop, saleId) => db.many(
  `SELECT * FROM sync_v3_conflicts WHERE account_id=$1 AND table_name='returns' AND detail->>'reason_core'='exceeds_sold'
      AND detail->>'sale'=$2 ORDER BY at`, [shop, saleId]);
const returned = async (saleItemId) => {
  const v1 = await db.many(`SELECT row_id, data FROM sync_rows WHERE account_id=$1 AND table_name='returns' AND data->>'saleItemId'=$2 AND deleted_at IS NULL`, [shopId, saleItemId]);
  const v3 = await db.many(`SELECT row_id, data FROM sync_v3_rows WHERE account_id=$1 AND table_name='returns' AND data->>'saleItemId'=$2 AND deleted_at IS NULL`, [shopId, saleItemId]);
  return { v1: v1.length, v3: v3.length, qty: v1.reduce((s, r) => s + Number(r.data.quantity), 0) + v3.reduce((s, r) => s + Number(r.data.quantityMilli) / 1000, 0) };
};

/* ---- v1: افغانیِ اعشاری؛ opهای یک «ثبت» یک ts ---- */
const v1 = (table, rowId, fields, t, type = 'insert') => ({ op_id: oid(), table, row_id: rowId, type, ts: t, fields });
/** فاکتور با یک قلم: `qty` تا به `price` */
function sale1(saleId, itemId, qty, price, t = ts()) {
  return [
    v1('sales', saleId, { total: qty * price, discount: 0, finalTotal: qty * price, status: 'completed', createdAt: t }, t),
    v1('saleItems', itemId, { saleId, productId: 'p-oil', quantity: qty, unitPrice: price, totalPrice: qty * price, returnedQty: 0, createdAt: t }, t),
  ];
}
/** همان ثبتِ `ReturnFlow.post`: ردیفِ برگشت، حرکتِ انبار، شمارندهٔ قلم، سابقه */
function ret1(tag, saleId, itemId, qty, amount, t = ts()) {
  const r = `r-${tag}`;
  return [
    v1('returns', r, { saleId, saleItemId: itemId, productId: 'p-oil', quantity: qty, amount, reason: 'خراب', docId: `d-${tag}`, createdAt: t }, t),
    v1('stockMovements', `m-${tag}`, { productId: 'p-oil', type: 'customer_return', qty, refId: r, createdAt: t }, t),
    v1('saleItems', itemId, { returnedQty: qty }, t, 'update'),
    v1('auditLog', `a-${tag}`, { type: 'return', refId: saleId, amount, createdAt: t }, t),
  ];
}

/* ---- v3: به پول و هزارم؛ یک ثبت یک tx ---- */
const v3 = (table, rowId, fields, tx, kind = 'insert') => ({ op_id: oid(), hlc: H('d'), table, row_id: rowId, kind, fields, ...(tx ? { tx } : {}) });
function sale3(saleId, itemId, qtyMilli, totalMinor, extra = {}) {
  return [
    v3('sales', saleId, { totalMinor, discountMinor: 0, finalTotalMinor: totalMinor, status: 'completed', ...extra }, `tx-${saleId}`),
    v3('saleItems', itemId, { saleId, productId: 'p-oil', quantityMilli: qtyMilli, totalPriceMinor: totalMinor }, `tx-${saleId}`),
  ];
}
function ret3(tag, saleId, itemId, qtyMilli, amountMinor) {
  const tx = `tx-${tag}`;
  return [
    v3('returns', `r-${tag}`, { saleId, saleItemId: itemId, productId: 'p-oil', quantityMilli: qtyMilli, amountMinor, docId: `d-${tag}` }, tx),
    v3('stockMovements', `m-${tag}`, { productId: 'p-oil', type: 'customer_return', qtyMilli, refId: `r-${tag}` }, tx),
    v3('saleItems', itemId, { returnedQtyMilli: qtyMilli }, tx, 'delta'),
  ];
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const o = await h.newUser('صاحبِ دکانِ سقفِ قلم', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ سقفِ قلم' }, { token: o.accessToken });
  shopId = shop.body?.shop?.id || shop.body?.id;
  const code = (await h.get('/api/shop/staff-code', { token: o.accessToken })).body.code;
  const s = await h.newUser('شاگردِ سقفِ قلم', 'shop');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: s.accessToken })).status));
  owner = await h.signIn(o, 'shop');
  staff = await h.signIn(s, 'shop');
  const x = await h.newUser('صاحبِ دکانِ دیگر', 'shop');
  otherShop = (await h.post('/api/shop', { name: 'دکانِ دیگر' }, { token: x.accessToken })).body.shop.id;
  other = await h.signIn(x, 'shop');
});
test.after(async () => { await h.stop(); });

/* ═══════════════════════════════ ① ═══════════════════════════════ */

test('حساب: همهٔ بردارهای return-cap-v1 (هم‌زادِ ReturnCap ِ core)', () => {
  assert.ok(V.cases.length >= 12 && V.caps.length >= 5);
  const pairs = (a) => { const out = []; for (let i = 0; i < a.length; i += 2) out.push({ qtyMilli: a[i], amountMinor: a[i + 1] }); return out; };
  for (const c of V.cases) {
    const prior = pairs(c.prior);
    const s = cap.state(c.soldMilli, c.capMinor, prior, c.counterMilli);
    assert.deepEqual([s.returnedMilli, s.returnedMinor, s.leftMilli, s.leftMinor], [c.returnedMilli, c.returnedMinor, c.leftMilli, c.leftMinor], c.name);
    const req = { qtyMilli: c.req[0], amountMinor: c.req[1] };
    assert.equal(cap.check(c.soldMilli, c.capMinor, prior, req, { tolMinorPerRow: c.tolMinorPerRow, counterMilli: c.counterMilli, extraMinor: c.extraMinor }), c.verdict, c.name);
  }
  for (const c of V.caps) assert.equal(cap.capMinor(c.lineTotal, c.paidRatio), c.capMinor, c.capName);
});

/* ═══════════════════════════════ ② ═══════════════════════════════ */

test('v1: دو دستگاهِ صاحب، بی اینترنت، همان برگشتِ کامل ⇒ یکی می‌نشیند، دیگری با همراه‌هایش در صندوقِ تعارض', async () => {
  assert.ok((await push1(owner, 'own-a', sale1('s-two', 'si-two', 2, 500))).body.results.every((x) => x.status === 'applied'));
  //  هر دو دستگاه پیش از هم‌رسیدن، با `returnedQty` ِ صفرِ خودشان
  const a = ret1('two-a', 's-two', 'si-two', 2, 1000);
  const b = ret1('two-b', 's-two', 'si-two', 2, 1000);
  assert.deepEqual(statuses(await push1(owner, 'own-a', a)), ['applied', 'applied', 'applied', 'applied']);
  const rb = await push1(owner, 'own-b', b);
  assert.equal(rb.status, 200, JSON.stringify(rb.body));
  assert.deepEqual(statuses(rb), ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  assert.equal(await db.one('SELECT 1 FROM oplog WHERE op_id = ANY($1::text[])', [b.map((o) => o.op_id)]), null, 'هیچ چیزِ B ننشست');
  assert.equal((await db.one(`SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='saleItems' AND row_id='si-two'`, [shopId])).data.returnedQty, 2);
  assert.deepEqual(await returned('si-two'), { v1: 1, v3: 0, qty: 2 });

  //  صاحب از هر دستگاهی همان را در صندوقِ تعارض می‌بیند — با سند و همهٔ همراه‌هایش
  const [box] = await boxesOf(shopId, 's-two');
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.loser_device, 'own-b');
  assert.equal(box.loser_op_id, b[0].op_id);
  assert.equal(box.detail.path, 'v1');
  assert.equal(box.detail.role, 'owner');
  assert.deepEqual(box.detail.lines.map((l) => [l.item, l.sold_milli, l.returned_milli, l.left_milli, l.qty_milli, l.verdict]), [['si-two', 2000, 2000, 0, 2000, 'qty']]);
  assert.deepEqual(box.loser_value.ops.map((o) => o.op_id), b.map((o) => o.op_id), 'بستهٔ کامل');
  assert.equal(box.loser_value.ops[0].fields.amount, 1000);
  const list = await h.api('GET', '/api/sync/v3/conflicts', { token: owner.accessToken });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const seen = list.body.conflicts.find((x) => x.id === box.id);
  assert.ok(seen && seen.task, 'وظیفهٔ بررسی در صندوقِ صاحب');
  assert.equal(seen.loser_value.ops.length, 4);
});

test('v1: دو push ِ هم‌زمان از دو دستگاهِ صاحب ⇒ دقیقاً یکی', async () => {
  await push1(owner, 'own-a', sale1('s-race', 'si-race', 3, 100));
  const a = ret1('race-a', 's-race', 'si-race', 3, 300);
  const b = ret1('race-b', 's-race', 'si-race', 3, 300);
  const [ra, rb] = await Promise.all([push1(owner, 'own-a', a), push1(owner, 'own-b', b)]);
  const heads = [ra.body.results[0], rb.body.results[0]].map((x) => x.status === 'rejected' ? x.reason : x.status).sort();
  assert.deepEqual(heads, ['applied', 'return_exceeds_sold']);
  assert.deepEqual(await returned('si-race'), { v1: 1, v3: 0, qty: 3 });
  assert.equal((await boxesOf(shopId, 's-race')).length, 1);
});

test('v3: دو دستگاهِ صاحب ⇒ دومی رد با همهٔ tx و صندوقِ کامل', async () => {
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-two', 'si3-two', 2000, 100000))), ['applied', 'applied']);
  const a = ret3('3two-a', 's3-two', 'si3-two', 2000, 100000);
  const b = ret3('3two-b', 's3-two', 'si3-two', 2000, 100000);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', a)), ['applied', 'applied', 'applied']);
  const rb = await push3(owner, 'own3-b', b);
  assert.deepEqual(statuses(rb), ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  assert.deepEqual(await returned('si3-two'), { v1: 0, v3: 1, qty: 2 });
  const row = await db.one(`SELECT data FROM sync_v3_rows WHERE account_id=$1 AND table_name='saleItems' AND row_id='si3-two'`, [shopId]);
  assert.equal(row.data.returnedQtyMilli, 2000, 'شمارندهٔ قلم یک بار');
  const [box] = await boxesOf(shopId, 's3-two');
  assert.equal(box.kind, 'perm_denied');
  assert.equal(box.detail.path, 'v3');
  assert.equal(box.detail.tx, 'tx-3two-b');
  assert.deepEqual(box.loser_value.ops.map((o) => o.op_id), b.map((o) => o.op_id));
});

test('میانِ v1 و v3: برگشتِ v1 ِ قلمِ v3 و برعکس همان سقف را می‌بیند', async () => {
  await push3(owner, 'own3-a', sale3('s-mix', 'si-mix', 1000, 50000));
  assert.deepEqual(statuses(await push1(owner, 'own-a', ret1('mix-1', 's-mix', 'si-mix', 1, 500))), ['applied', 'applied', 'applied', 'applied']);
  assert.equal((await push3(owner, 'own3-b', ret3('mix-3', 's-mix', 'si-mix', 1000, 50000))).body.results[0].reason, 'return_exceeds_sold');
  await push1(owner, 'own-a', sale1('s-mix1', 'si-mix1', 1, 500));
  assert.equal((await push3(owner, 'own3-a', ret3('mix1-3', 's-mix1', 'si-mix1', 1000, 50000))).body.results[0].status, 'applied');
  assert.equal((await push1(owner, 'own-b', ret1('mix1-1', 's-mix1', 'si-mix1', 1, 500))).body.results[0].reason, 'return_exceeds_sold');
});

test('v1 و v3 ِ هم‌زمان با پلِ v3 ⇒ v1 روشن: دقیقاً یکی، بی بن‌بستِ قفل (قفلِ برگشت پس از قفلِ v1)', async () => {
  await liveConfig.set(shopId, 'shop.sync_v3_to_v1', true, 'test');
  try {
    for (let k = 0; k < 4; k++) {
      await push1(owner, 'own-a', sale1(`s-lock${k}`, `si-lock${k}`, 1, 100));
      const [r1, r3] = await Promise.all([
        push1(owner, 'own-b', ret1(`lock${k}-1`, `s-lock${k}`, `si-lock${k}`, 1, 100)),
        push3(owner, 'own3-b', ret3(`lock${k}-3`, `s-lock${k}`, `si-lock${k}`, 1000, 10000)),
      ]);
      assert.deepEqual([r1.status, r3.status], [200, 200], JSON.stringify([r1.body, r3.body]).slice(0, 400));
      const heads = [r1.body.results[0], r3.body.results[0]].map((x) => (x.status === 'rejected' ? x.reason : x.status)).sort();
      assert.deepEqual(heads, ['applied', 'return_exceeds_sold'], `دور ${k}`);
    }
  } finally { await liveConfig.set(shopId, 'shop.sync_v3_to_v1', false, 'test'); }
});

/* ═══════════════════════════════ ③ ═══════════════════════════════ */

test('تکه‌ها: ۱ + ۱ از ۲ می‌نشیند؛ تکهٔ سوم (و تکهٔ بزرگ‌تر از مانده) نه — v1 و v3، شاگرد هم', async () => {
  await push1(owner, 'own-a', sale1('s-part', 'si-part', 2, 500));
  assert.equal((await push1(owner, 'own-a', ret1('part-1', 's-part', 'si-part', 1, 500))).body.results[0].status, 'applied');
  assert.equal((await push1(owner, 'own-b', ret1('part-big', 's-part', 'si-part', 1.5, 750))).body.results[0].reason, 'return_exceeds_sold');
  assert.equal((await push1(staff, 'stf-1', ret1('part-2', 's-part', 'si-part', 1, 500))).body.results[0].status, 'applied');
  assert.equal((await push1(staff, 'stf-1', ret1('part-3', 's-part', 'si-part', 1, 500))).body.results[0].reason, 'return_exceeds_sold');
  assert.deepEqual(await returned('si-part'), { v1: 2, v3: 0, qty: 2 });

  await push3(owner, 'own3-a', sale3('s3-part', 'si3-part', 1250, 37500));   //  وزنی: ۱٫۲۵ کیلو
  assert.equal((await push3(owner, 'own3-a', ret3('3part-1', 's3-part', 'si3-part', 500, 15000))).body.results[0].status, 'applied');
  assert.equal((await push3(owner, 'own3-b', ret3('3part-2', 's3-part', 'si3-part', 751, 22500))).body.results[0].reason, 'return_exceeds_sold');
  assert.equal((await push3(owner, 'own3-b', ret3('3part-3', 's3-part', 'si3-part', 750, 22500))).body.results[0].status, 'applied');
});

test('تکه‌ها در یک دسته: دو سندِ همان قلم با هم بیش از فروخته ⇒ دومی رد، اولی می‌نشیند', async () => {
  await push1(owner, 'own-a', sale1('s-pair', 'si-pair', 2, 100));
  const r = await push1(owner, 'own-a', [...ret1('pair-1', 's-pair', 'si-pair', 2, 200), ...ret1('pair-2', 's-pair', 'si-pair', 1, 100)]);
  assert.deepEqual(statuses(r).map((s) => s.split(':').pop()), ['applied', 'applied', 'applied', 'applied', 'return_exceeds_sold', 'tx_rejected', 'tx_rejected', 'tx_rejected']);
});

test('پول: مقدارِ درست با پولِ بیش از قلم رد؛ گردِ قدیم (نیم افغانی برای هر ردیف) می‌گذرد', async () => {
  await push1(owner, 'own-a', sale1('s-amt', 'si-amt', 2, 2.5));    //  جمعِ قلم ۵
  assert.equal((await push1(owner, 'own-a', ret1('amt-big', 's-amt', 'si-amt', 1, 50))).body.results[0].reason, 'return_exceeds_sold');
  //  برنامهٔ پیش از #586: ۳ + ۳ از قلمِ ۵ (هر برگشت جدا گرد)
  assert.equal((await push1(owner, 'own-a', ret1('amt-1', 's-amt', 'si-amt', 1, 3))).body.results[0].status, 'applied');
  assert.equal((await push1(owner, 'own-b', ret1('amt-2', 's-amt', 'si-amt', 1, 3))).body.results[0].status, 'applied');
  //  قلمِ بی جمع (`totalPrice` نیامده): فقط مقدار سنجیده می‌شود، نه پول
  const t = ts();
  await push1(owner, 'own-a', [v1('sales', 's-nototal', { total: 100, discount: 0, finalTotal: 100, createdAt: t }, t),
    v1('saleItems', 'si-nototal', { saleId: 's-nototal', productId: 'p-oil', quantity: 1, unitPrice: 100 }, t)]);
  assert.equal((await push1(owner, 'own-a', ret1('nototal-a', 's-nototal', 'si-nototal', 1, 900))).body.results[0].status, 'applied');
  assert.equal((await push1(owner, 'own-b', ret1('nototal-b', 's-nototal', 'si-nototal', 1, 0))).body.results[0].reason, 'return_exceeds_sold');
});

test('POS-37: برگشتِ کاملِ قلمِ فاکتورِ مالیات‌دار سهمِ مالیاتِ همان قلم را هم پس می‌دهد — بیشتر نه (v3 و v1)', async () => {
  //  جمعِ ۱۰۰۰ افغانی + ۱۰٪ مالیات = ۱۱۰۰؛ برگشتِ کامل ۱۱۰۰ می‌نشیند (پیش از رفع: return_exceeds_sold)، ۱۱۰۲ نه
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-tax', 'si3-tax', 2000, 100000, { taxMinor: 10000, finalTotalMinor: 110000 }))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3tax-over', 's3-tax', 'si3-tax', 2000, 110200))),
    ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3tax-full', 's3-tax', 'si3-tax', 2000, 110000))), ['applied', 'applied', 'applied']);
  //  v1: مبلغ به افغانی، `taxMinor` به پول (مثلِ گرد)
  const t = ts();
  await push1(owner, 'own-a', [v1('sales', 's-tax1', { total: 500, discount: 0, finalTotal: 550, taxMinor: 5000, createdAt: t }, t),
    v1('saleItems', 'si-tax1', { saleId: 's-tax1', productId: 'p-oil', quantity: 1, unitPrice: 500, totalPrice: 500 }, t)]);
  assert.equal((await push1(owner, 'own-a', ret1('tax1-full', 's-tax1', 'si-tax1', 1, 550))).body.results[0].status, 'applied');
  //  فاکتورِ بی مالیات همان سقفِ پیشین
  assert.equal(cap.paidRatio(1000, 100), cap.paidRatio(1000, 100, 0));
  assert.equal(cap.paidRatio(1000, 0, 100), 1.1);
});

//  SR48-1: `taxMinor` ادعای خودِ دستگاه است و سرور (پرچمِ `shop.sale_tax` خاموش، یا روشن برای صاحب) آن را با نهایی نمی‌سنجید
//  ⇒ «مالیاتی» که در نهایی نیامده (مشتری نپرداخته) سهمِ پرداخت را بیش از ۱ می‌کرد و برگشت بیش از پولِ فاکتور می‌نشست.
//  SR48-2: گردِ نقدیِ مثبت (`roundingMinor`) هم بی سقف و بی نهایی به سقفِ پول افزوده می‌شد.
test('SR48-1/2: مالیات و گردی که در نهایی نیامده سقفِ برگشت را بالا نمی‌برد (v3 و v1)؛ مالیاتِ درست همان', async () => {
  //  جمع = نهایی = ۱۰۰۰ افغانی، «مالیات» ِ ادعاشده ۱۰۰۰ ⇒ پیش از درمان برگشتِ ۲۰۰۰ می‌نشست
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-fake-tax', 'si3-fake-tax', 2000, 100000, { taxMinor: 100000 }))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3fake-over', 's3-fake-tax', 'si3-fake-tax', 2000, 200000))),
    ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  //  گردِ منفیِ ساختگی راهِ گذرِ مالیاتِ ساختگی نیست
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-fake-rnd', 'si3-fake-rnd', 2000, 100000, { taxMinor: 100000, roundingMinor: -100000 }))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3fake-rnd', 's3-fake-rnd', 'si3-fake-rnd', 2000, 200000))),
    ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  //  گردِ مثبتِ ساختگی (۱۰۰۰ افغانی، در نهایی نیامده)
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-fake-up', 'si3-fake-up', 2000, 100000, { roundingMinor: 100000 }))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3fake-up', 's3-fake-up', 'si3-fake-up', 2000, 200000))),
    ['rejected:return_exceeds_sold', 'rejected:tx_rejected', 'rejected:tx_rejected']);
  //  v1: «مالیات» ِ ۵۰۰ افغانی روی فاکتورِ ۵۰۰ که نهایی‌اش ۵۰۰ است
  const t = ts();
  await push1(owner, 'own-a', [v1('sales', 's-fake1', { total: 500, discount: 0, finalTotal: 500, taxMinor: 50000, createdAt: t }, t),
    v1('saleItems', 'si-fake1', { saleId: 's-fake1', productId: 'p-oil', quantity: 1, unitPrice: 500, totalPrice: 500 }, t)]);
  assert.equal((await push1(owner, 'own-a', ret1('fake1-over', 's-fake1', 'si-fake1', 1, 1000))).body.results[0].reason, 'return_exceeds_sold');
  //  مالیاتِ درست با گردِ منفیِ واقعی (۱۰۰۰ + ۱۰۰ − ۰٫۵۰ = ۱۰۹۹٫۵۰) کامل برمی‌گردد
  assert.deepEqual(statuses(await push3(owner, 'own3-a', sale3('s3-tax-rnd', 'si3-tax-rnd', 1000, 100000, { taxMinor: 10000, roundingMinor: -50, finalTotalMinor: 109950 }))), ['applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', ret3('3tax-rnd', 's3-tax-rnd', 'si3-tax-rnd', 1000, 109950))), ['applied', 'applied', 'applied']);
});

/* ═══════════════════════════════ ④ ═══════════════════════════════ */

test('تلاشِ دوباره: همان op دو بار ⇒ «تکراری»، یک بار شمرده؛ همان ردیف با op ِ تازه هم یک بار', async () => {
  await push1(owner, 'own-a', sale1('s-retry', 'si-retry', 2, 100));
  const a = ret1('retry-a', 's-retry', 'si-retry', 2, 200);
  assert.deepEqual(statuses(await push1(owner, 'own-a', a)), ['applied', 'applied', 'applied', 'applied']);
  //  پاسخ گم شد ⇒ همان دسته دوباره
  assert.deepEqual(statuses(await push1(owner, 'own-a', a)), ['duplicate', 'duplicate', 'duplicate', 'duplicate']);
  //  همان ردیفِ برگشتِ کامل با op_id ِ تازه (صفِ از نو ساخته‌شده) ⇒ همان یک ردیف، نه «برگشتِ دوم»
  const again = a.map((o) => ({ ...o, op_id: oid() }));
  assert.deepEqual(statuses(await push1(owner, 'own-a', again)), ['applied', 'applied', 'applied', 'applied']);
  assert.deepEqual(await returned('si-retry'), { v1: 1, v3: 0, qty: 2 });
  //  ولی برگشتِ تازه از همان قلم دیگر جا ندارد
  assert.equal((await push1(owner, 'own-b', ret1('retry-b', 's-retry', 'si-retry', 1, 100))).body.results[0].reason, 'return_exceeds_sold');

  //  v3: همان tx دو بار ⇒ «تکراری»؛ همان ردیفِ برگشت با op ِ تازه ⇒ رد نمی‌شود (همان سند)
  await push3(owner, 'own3-a', sale3('s3-retry', 'si3-retry', 1000, 10000));
  const t = ret3('3retry', 's3-retry', 'si3-retry', 1000, 10000);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', t)), ['applied', 'applied', 'applied']);
  assert.deepEqual(statuses(await push3(owner, 'own3-a', t)), ['duplicate', 'duplicate', 'duplicate']);
  const head = (await push3(owner, 'own3-a', [{ ...t[0], op_id: oid() }])).body.results[0];
  assert.notEqual(head.status, 'rejected', JSON.stringify(head));
  assert.deepEqual(await returned('si3-retry'), { v1: 0, v3: 1, qty: 1 });
  assert.equal((await push3(owner, 'own3-b', ret3('3retry-b', 's3-retry', 'si3-retry', 1, 1))).body.results[0].reason, 'return_exceeds_sold');
});

test('تلاشِ دوباره: opِ ردشده دوباره ⇒ همان رد و یک صندوق، هیچ چیزی بیشتر نمی‌نشیند', async () => {
  await push1(owner, 'own-a', sale1('s-rej', 'si-rej', 1, 100));
  await push1(owner, 'own-a', ret1('rej-a', 's-rej', 'si-rej', 1, 100));
  const b = ret1('rej-b', 's-rej', 'si-rej', 1, 100);
  for (let i = 0; i < 3; i++) assert.equal((await push1(owner, 'own-b', b)).body.results[0].reason, 'return_exceeds_sold');
  assert.equal((await boxesOf(shopId, 's-rej')).length, 1);
  assert.deepEqual(await returned('si-rej'), { v1: 1, v3: 0, qty: 1 });
});

/* ═══════════════════════════════ ⑤ ═══════════════════════════════ */

test('REST (`/api/returns`): بیش از فروخته ⇒ ۴۰۹ ِ همه یا هیچ و صندوقِ تعارض؛ تا فروخته می‌نشیند', async () => {
  const tok = { token: owner.accessToken };
  assert.equal((await h.post('/api/sales', { id: 's-rest', total: 300, discount: 0, finalTotal: 300 }, tok)).status, 201);
  assert.equal((await h.post('/api/sale-items', { id: 'si-rest', saleId: 's-rest', quantity: 3, totalPrice: 300 }, tok)).status, 201);
  assert.equal((await h.post('/api/returns', { id: 'r-rest-1', saleId: 's-rest', saleItemId: 'si-rest', quantity: 2, amount: 200 }, tok)).status, 201);
  const over = await h.post('/api/returns', { id: 'r-rest-2', saleId: 's-rest', saleItemId: 'si-rest', quantity: 2, amount: 200 }, tok);
  assert.equal(over.status, 409, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'return_exceeds_sold');
  assert.equal(over.body.error.lines[0].left_milli, 1000);
  assert.equal(await db.one(`SELECT 1 FROM sale_returns WHERE shop_id=$1 AND id='r-rest-2'`, [shopId]), null);
  const [box] = await boxesOf(shopId, 's-rest');
  assert.equal(box.detail.path, 'rest');
  assert.equal(box.loser_value.ops.find((o) => o.row_id === 'r-rest-2').fields.quantity, 2);
  assert.equal((await h.post('/api/returns', { id: 'r-rest-3', saleId: 's-rest', saleItemId: 'si-rest', quantity: 1, amount: 100 }, tok)).status, 201);
  //  ویرایشِ همان ردیف به مقدارِ بیشتر هم همان سقف
  const edit = await h.api('PUT', '/api/returns/r-rest-3', { token: owner.accessToken, body: { saleId: 's-rest', saleItemId: 'si-rest', quantity: 2, amount: 200, updatedAt: Date.now() + 5000 } });
  assert.equal(edit.status, 409, JSON.stringify(edit.body));
});

test('فاکتورِ لغوشده چیزی برای برگشت ندارد؛ برگشتِ لغوشده دیگر شمرده نمی‌شود', async () => {
  await push3(owner, 'own3-a', sale3('s3-cx', 'si3-cx', 1000, 10000));
  assert.equal((await push3(owner, 'own3-a', [{ op_id: oid(), hlc: H('d'), table: 'sales', row_id: 's3-cx', kind: 'cancel', reason: 'اشتباه' }])).body.results[0].status, 'applied');
  assert.equal((await push3(owner, 'own3-b', ret3('3cx', 's3-cx', 'si3-cx', 1000, 10000))).body.results[0].reason, 'return_exceeds_sold');

  await push3(owner, 'own3-a', sale3('s3-undo', 'si3-undo', 1000, 10000));
  assert.equal((await push3(owner, 'own3-a', ret3('3undo-a', 's3-undo', 'si3-undo', 1000, 10000))).body.results[0].status, 'applied');
  assert.equal((await push3(owner, 'own3-a', [{ op_id: oid(), hlc: H('d'), table: 'returns', row_id: 'r-3undo-a', kind: 'cancel', reason: 'اشتباه' }])).body.results[0].status, 'applied');
  assert.equal((await push3(owner, 'own3-b', ret3('3undo-b', 's3-undo', 'si3-undo', 1000, 10000))).body.results[0].status, 'applied');
});

/* ═══════════════════════════════ ⑥ ═══════════════════════════════ */

test('جدا از دکانِ دیگر: همان شناسهٔ قلم در دکانِ دیگر اثری ندارد و صندوقش را نمی‌بیند', async () => {
  //  دکانِ دیگر همان شناسه‌ها را دارد و پیش‌تر همه را برگردانده
  assert.ok((await push1(other, 'oth-a', sale1('s-iso', 'si-iso', 1, 100))).body.results.every((x) => x.status === 'applied'));
  assert.equal((await push1(other, 'oth-a', ret1('iso-o', 's-iso', 'si-iso', 1, 100))).body.results[0].status, 'applied');
  assert.equal((await push1(other, 'oth-a', ret1('iso-o2', 's-iso', 'si-iso', 1, 100))).body.results[0].reason, 'return_exceeds_sold');
  //  این دکان: فاکتورِ خودش با همان شناسه — هنوز همه قابلِ برگشت است
  await push1(owner, 'own-a', sale1('s-iso', 'si-iso-mine', 1, 100));
  assert.equal((await push1(owner, 'own-a', ret1('iso-m', 's-iso', 'si-iso-mine', 1, 100))).body.results[0].status, 'applied');
  const mine = await h.api('GET', '/api/sync/v3/conflicts', { token: owner.accessToken });
  assert.ok(!mine.body.conflicts.some((x) => x.row_id === 'r-iso-o2'), 'صندوقِ دکانِ دیگر دیده نمی‌شود');
  assert.equal((await boxesOf(otherShop, 's-iso')).length, 1);
});

test('خاموش‌کنِ اضطراری: پیش‌فرض روشن؛ فقط `false` ِ صریح همان رفتارِ پیشین را برمی‌گرداند', async () => {
  assert.equal(await cap.enabled({ query: (s, p) => db.query(s, p) }, shopId), true);
  await push1(owner, 'own-a', sale1('s-kill', 'si-kill', 1, 100));
  await push1(owner, 'own-a', ret1('kill-a', 's-kill', 'si-kill', 1, 100));
  await liveConfig.set(shopId, cap.FLAG, false, 'test');
  try {
    assert.equal((await push1(owner, 'own-b', ret1('kill-b', 's-kill', 'si-kill', 1, 100))).body.results[0].status, 'applied');
  } finally { await liveConfig.set(shopId, cap.FLAG, true, 'test'); }
  assert.equal((await push1(owner, 'own-b', ret1('kill-c', 's-kill', 'si-kill', 1, 100))).body.results[0].reason, 'return_exceeds_sold');
});

test('پمپ و دستهٔ بی برگشتِ قلم‌دار همان `guard` ِ پیشین را می‌گیرند (بی هیچ پرسشی)', () => {
  const inner = { check: async () => null, finish: () => {} };
  const ops = [{ op_id: oid(), table: 'returns', row_id: 'r', type: 'insert', ts: 1, fields: { saleItemId: 'x', quantity: 1 } }];
  assert.equal(cap.v1Guard({ app: 'pump', accountKind: 'station', accountId: 'st-1' }, 'd', ops, inner), inner);
  assert.equal(cap.v1Guard({ app: 'pump', accountKind: 'station', accountId: 'st-1' }, 'd', ops, null), null);
  assert.equal(cap.v1Guard({ app: 'shop', accountKind: 'shop', accountId: shopId }, 'd', [{ ...ops[0], fields: { quantity: 1 } }], inner), inner);
  assert.notEqual(cap.v1Guard({ app: 'shop', accountKind: 'shop', accountId: shopId }, 'd', ops, inner), inner);
});

/* ═══════════════════════════════ ⑦ ═══════════════════════════════ */

test('مهاجرتِ ۳۰۵: نمایه‌ها هست؛ rollback برشان می‌دارد و ردیفِ خودش را پاک می‌کند؛ دوباره ساخته می‌شوند', async () => {
  const names = ['ix_sync_v3_rows_return_item', 'ix_sync_rows_return_item', 'ix_sale_returns_item'];
  const have = async () => (await db.many(`SELECT indexname FROM pg_indexes WHERE indexname = ANY($1::text[])`, [names])).map((r) => r.indexname).sort();
  assert.deepEqual(await have(), [...names].sort());
  const dir = path.join(__dirname, '..', 'migrations');
  await db.query(fs.readFileSync(path.join(dir, 'rollback', '305_return_line_index.down.sql'), 'utf8'));
  assert.deepEqual(await have(), []);
  assert.equal(await db.one(`SELECT 1 FROM schema_migrations WHERE version='305_return_line_index.sql'`), null);
  //  بی نمایه هم درست (فقط کندتر)
  await push1(owner, 'own-a', sale1('s-noidx', 'si-noidx', 1, 100));
  await push1(owner, 'own-a', ret1('noidx-a', 's-noidx', 'si-noidx', 1, 100));
  assert.equal((await push1(owner, 'own-b', ret1('noidx-b', 's-noidx', 'si-noidx', 1, 100))).body.results[0].reason, 'return_exceeds_sold');
  await db.query(fs.readFileSync(path.join(dir, '305_return_line_index.sql'), 'utf8'));
  assert.deepEqual(await have(), [...names].sort());
});
