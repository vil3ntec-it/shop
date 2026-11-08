'use strict';
/**
 * INV-10 — فروشِ آفلاینِ **پیش از** شمردن که **پس از** ثبتِ شمارش به سرور می‌رسد دوباره کم نمی‌شود
 * (`lib/count-absorb-ledger.js`، همان قاعدهٔ `license/count-absorb-core.js` ِ برنامه).
 *
 *   A آفلاین ۱۰:۰۰ سه می‌فروشد ⇐ B ساعتِ ۱۰:۳۰ هفده می‌شمارد (ردیفِ انبارِ −۳ با `countedAt`/`seenMilli`) ⇐ B ثبت
 *   می‌شود ⇐ A ساعتِ ۱۱:۰۰ فروشش را می‌فرستد ⇒ موجودیِ سرور ۱۷ (نه ۱۴)؛ فروش در دفتر می‌ماند و جذب با یادداشتِ
 *   «پیش از شمارش ثبت شده بود — در شمارش آمده است» دیده می‌شود.
 *   ⛔ پرچمِ `shop.count_absorb` خاموش ⇒ همان ۱۴ ِ پیشین؛ هر دو راهِ همگام‌سازی (v1 و v3)؛ ساعتِ کج جذب نمی‌شود.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const hlc = require('../src/lib/hlc');

const LABEL = 'پیش از شمارش ثبت شده بود — در شمارش آمده است';
let admin = '';

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

// ── کمکی‌ها ──────────────────────────────────────────────────────────

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = await h.signIn(u, 'shop');
  return { ...s, token: s.accessToken, shopId: made.body.shop.id };
}
async function flag(s, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope: s.shopId, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

let opN = 0;
const opId = () => `01JCA${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
async function push(s, device, ops) {
  const dev = `dev-${device}-${s.shopId}`.slice(0, 60);
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: dev, schema_version: 2, ops },
    headers: { 'X-App': 'shop', 'X-Device': dev },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
  return ops;
}
const op = (table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type: 'insert', fields });
const move = (id, pid, type, qty, at, refId) => op('stockMovements', id, { productId: pid, type, qty, date: '', notes: '', refId, createdAt: at });
const product = (id) => op('products', id, {
  name: `کالای ${id}`, unit: 'دانه', salePrice: 50, purchasePrice: 40, active: true, barcodes: [], createdAt: Date.now(),
});
/** ورودِ ۲۰ دانه یک ساعت پیش (ردیفِ انبار + حرکتِ دستگاه، همان `WarehouseEngine.addEntry`) */
const receipt = (pid, at) => [
  op('warehouseEntries', `${pid}-in`, { productId: pid, units: 20, cartons: 0, perCarton: 0, unit: 'دانه', price: 40, createdAt: at }),
  move(`${pid}-in-m`, pid, 'purchase_in', 20, at, `${pid}-in`),
];
/** ردیفِ «انبارگردانی» ِ B (`WarehouseEngine.stockCount` با لحظه) + حرکتِ `count_adjust` ِ آن */
const countLine = (id, pid, units, countedAt, seenMilli, createdAt) => [
  op('warehouseEntries', id, {
    productId: pid, units, cartons: 0, perCarton: 0, unit: 'دانه', price: 0, notes: 'انبارگردانی', isAdjustment: true,
    adjKind: 'count', createdAt, countedAt, seenMilli,
  }),
  move(`${id}-m`, pid, 'count_adjust', units, createdAt, id),
];
/** فروشِ دستگاه در لحظهٔ [at] (فاکتور + قلم + حرکتِ `sale` ِ `SalesEngine`) */
const sale = (id, pid, q, at) => [
  op('sales', id, { status: 'completed', total: 0, createdAt: at }),
  op('saleItems', `${id}-i`, { saleId: id, productId: pid, quantity: q, returnedQty: 0, unitPrice: 50 }),
  move(`${id}-m`, pid, 'sale', -q, at, id),
];

const cat = (s, p) => h.get(`/api/shop/catalog${p}`, { token: s.token });
async function floor(s, pid) {
  const r = await cat(s, `/products/${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.product.stock.levels.shop_floor;
}
async function moves(s, pid) {
  const r = await cat(s, `/moves?productId=${pid}&limit=100`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items;
}
async function reconciled(s, pid) {
  const r = await cat(s, `/report?productId=${pid}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.ok, true, JSON.stringify(r.body.mismatches));
}
const H = 3_600_000;

/** صحنهٔ اصلی: ورودِ ۲۰، شمارشِ ۱۷ ِ B (فروشِ A را ندیده)، بعد فروشِ آفلاینِ A */
async function scene(name, { on = true } = {}) {
  const s = await shop(name);
  await flag(s, 'shop.catalog', true);
  if (on) await flag(s, 'shop.count_absorb', true);
  const t = Date.now() - 3 * H;            //  «۱۰:۰۰»
  const tc = t + H / 2;                    //  «۱۰:۳۰» لحظهٔ شمردن
  await push(s, 'B', [product('c1'), ...receipt('c1', t - H)]);
  await push(s, 'B', countLine('cnt1', 'c1', -3, tc, 20000, tc + 60_000));
  assert.equal(await floor(s, 'c1'), 17_000);
  return { s, t, tc };
}

// ── آزمون‌ها ──────────────────────────────────────────────────────────

test('v1: فروشِ آفلاینِ ۱۰:۰۰ پس از ثبتِ شمارشِ ۱۷ ِ ۱۰:۳۰ می‌رسد ⇒ ۱۷، فروش در دفتر و جذب با یادداشت', async () => {
  const { s, t } = await scene('دیررس');
  await push(s, 'A', sale('sa', 'c1', 3, t));
  assert.equal(await floor(s, 'c1'), 17_000);
  const items = await moves(s, 'c1');
  const sold = items.filter((m) => m.type === 'sale');
  assert.deepEqual(sold.map((m) => m.qtyMilli), [3000], 'فروش همان‌طور در دفتر');
  const abs = items.filter((m) => m.reason === 'count_absorb');
  assert.deepEqual(abs.map((m) => [m.type, m.deltaOnHandMilli, m.note, m.refId]), [['adjustment', 3000, LABEL, 'cnt1']]);
  await reconciled(s, 'c1');
  //  تکرارِ همان opها (صفِ دوباره‌فرستاده) ⇒ همان، بی حرکتِ دوم
  await push(s, 'A', sale('sa', 'c1', 3, t).map((o, i) => ({ ...o, op_id: `01JCADUP${String(i).padStart(18, '0')}` })));
  assert.equal(await floor(s, 'c1'), 17_000);
  assert.equal((await moves(s, 'c1')).filter((m) => m.reason === 'count_absorb').length, 1);
});

test('پرچم خاموش ⇒ همان ۱۴ ِ پیشین؛ روشن ⇒ ۱۷؛ دوباره خاموش ⇒ حرکتِ برعکس و ۱۴ (دفتر = مانده‌ها)', async () => {
  const { s, t } = await scene('پرچم', { on: false });
  await push(s, 'A', sale('sb', 'c1', 3, t));
  assert.equal(await floor(s, 'c1'), 14_000);
  assert.equal((await moves(s, 'c1')).filter((m) => m.reason === 'count_absorb').length, 0);
  await flag(s, 'shop.count_absorb', true);
  assert.equal(await floor(s, 'c1'), 17_000);
  await flag(s, 'shop.count_absorb', false);
  assert.equal(await floor(s, 'c1'), 14_000);
  assert.deepEqual((await moves(s, 'c1')).filter((m) => m.reason === 'count_absorb').map((m) => m.deltaOnHandMilli).sort(), [-3000, 3000]);
  await reconciled(s, 'c1');
});

test('فروشِ پس از شمارش عادی کم می‌شود؛ فروشی که شمارنده داشت دوباره برنمی‌گردد؛ برگشتِ پیش از شمارش جذب', async () => {
  const { s, t, tc } = await scene('پس‌از');
  await push(s, 'A', sale('sc', 'c1', 2, tc + H));
  assert.equal(await floor(s, 'c1'), 15_000);

  //  B فروش را داشت: seen = ۲۰ − ۳ = ۱۷، ۱۶ شمرده ⇒ −۱ ⇒ ۱۶
  const k = await shop('داشت');
  await flag(k, 'shop.catalog', true);
  await flag(k, 'shop.count_absorb', true);
  await push(k, 'A', [product('c2'), ...receipt('c2', t - H), ...sale('sd', 'c2', 3, t)]);
  await push(k, 'B', countLine('cnt2', 'c2', -1, tc, 17000, tc + 60_000));
  assert.equal(await floor(k, 'c2'), 16_000);
  assert.equal((await moves(k, 'c2')).filter((m) => m.reason === 'count_absorb').length, 0);

  //  برگشتِ آفلاینِ پیش از شمارش (+۲) که دیر می‌رسد: در عددِ شمرده هست
  await push(k, 'A', [move('ret-m', 'c2', 'customer_return', 2, t + 60_000, 'ret')]);
  await push(k, 'A', [op('warehouseEntries', 'ret-e', { productId: 'c2', units: 2, isAdjustment: true, adjKind: 'customer_return', createdAt: t + 60_000 })]);
  assert.equal(await floor(k, 'c2'), 16_000);
  await reconciled(k, 'c2');
});

test('مسابقهٔ چند دستگاه: A و D هر دو آفلاین پیش از شمارش، به هر ترتیب ⇒ ۱۵', async () => {
  const { s, t } = await scene('مسابقه');
  //  B ِ این صحنه ۱۷ شمرد؛ این‌جا شمارشِ تازهٔ ۱۵ (پنج کم) با seen ِ ۲۰
  await push(s, 'B', countLine('cnt3', 'c1', -2, t + H, 17000, t + H + 60_000));
  assert.equal(await floor(s, 'c1'), 15_000);
  await push(s, 'D', sale('sd2', 'c1', 2, t + 10 * 60_000));
  assert.equal(await floor(s, 'c1'), 15_000);
  await push(s, 'A', sale('sa2', 'c1', 3, t));
  assert.equal(await floor(s, 'c1'), 15_000);
  await reconciled(s, 'c1');
});

test('ساعتِ کج (v1): شمارندهٔ ۲ ساعت جلو جذب نمی‌کند — فروشِ درستِ پس از شمارش کم می‌شود', async () => {
  const s = await shop('ساعت‌جلو');
  await flag(s, 'shop.catalog', true);
  await flag(s, 'shop.count_absorb', true);
  const real = Date.now();
  await push(s, 'B', [product('c4'), ...receipt('c4', real - 2 * H)]);
  //  لحظه و ساختِ سند با ساعتِ جلوی B؛ سرور سند را همین حالا می‌سازد (server_at)
  await push(s, 'B', countLine('cnt4', 'c4', -1, real + 2 * H, 20000, real + 2 * H + 60_000));
  await push(s, 'A', sale('se', 'c4', 3, real + 60_000));
  assert.equal(await floor(s, 'c4'), 16_000);
  assert.equal((await moves(s, 'c4')).filter((m) => m.reason === 'count_absorb').length, 0);
});

test('Sync v3: همان قاعده؛ فروشِ عقب‌کشیده با HLC ِ پس از شمارش (correctedAt ِ سرور) جذب نمی‌شود', async () => {
  const s = await shop('نسخهٔ سه');
  await flag(s, 'shop.catalog', true);
  await flag(s, 'shop.count_absorb', true);
  assert.equal((await h.put('/api/shop/catalog/settings', { syncSource: 'v3' }, { token: s.token })).status, 200);
  let tick = 0;
  const oid = () => `01JV3CA${String(++opN).padStart(19, '0')}`;
  const at = (wall) => hlc.format({ wall, counter: ++tick, node: 'v3dev' });
  const v3 = async (ops) => {
    const r = await h.api('POST', '/api/sync/v3/push', { token: s.token, body: { device_id: 'v3-ca-dev', protocol: 3, ops } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  };
  const ins = (table, rowId, wall, fields) => ({ op_id: oid(), table, row_id: rowId, kind: 'insert', hlc: at(wall), fields });
  const t = Date.now() - 3 * H;
  const tc = t + H / 2;
  await v3([
    ins('products', 'v1p', t - H, { name: 'چای', unit: 'بسته', salePriceMinor: 9900 }),
    ins('warehouseEntries', 'v1p-in', t - H, { productId: 'v1p', unitsMilli: 20000, createdAt: t - H }),
    ins('stockMovements', 'v1p-in-m', t - H, { productId: 'v1p', qtyMilli: 20000, type: 'purchase_in', createdAt: t - H, refId: 'v1p-in' }),
    ins('warehouseEntries', 'vc1', tc + 60_000, { productId: 'v1p', unitsMilli: -3000, isAdjustment: true, adjKind: 'count', notes: 'انبارگردانی', createdAt: tc + 60_000, countedAt: tc, seenMilli: 20000 }),
    ins('stockMovements', 'vc1-m', tc + 60_000, { productId: 'v1p', qtyMilli: -3000, type: 'count_adjust', createdAt: tc + 60_000, refId: 'vc1' }),
  ]);
  assert.equal(await floor(s, 'v1p'), 17_000);
  //  فروشِ درستِ ۱۰:۰۰ (HLC همان ساعت) ⇒ جذب
  await v3([
    ins('sales', 'vs1', t, { status: 'completed', totalMinor: 0, createdAt: t }),
    ins('saleItems', 'vs1-i', t, { saleId: 'vs1', productId: 'v1p', quantityMilli: 3000, returnedQtyMilli: 0 }),
    ins('stockMovements', 'vs1-m', t, { productId: 'v1p', qtyMilli: -3000, type: 'sale', createdAt: t, refId: 'vs1' }),
  ]);
  assert.equal(await floor(s, 'v1p'), 17_000);
  //  فروشِ پس از دیدنِ شمارش با ساعتِ عقب‌کشیده: createdAt «۱۰:۰۵»، HLC ِ دستگاه همین حالا ⇒ correctedAt ⇒ کم می‌شود
  const wall = Date.now();
  await v3([
    ins('sales', 'vs2', wall, { status: 'completed', totalMinor: 0, createdAt: t + 300_000 }),
    ins('saleItems', 'vs2-i', wall, { saleId: 'vs2', productId: 'v1p', quantityMilli: 2000, returnedQtyMilli: 0 }),
    ins('stockMovements', 'vs2-m', wall, { productId: 'v1p', qtyMilli: -2000, type: 'sale', createdAt: t + 300_000, refId: 'vs2' }),
  ]);
  assert.equal(await floor(s, 'v1p'), 15_000);
  const ev = (await query("SELECT corrected_at FROM stock_device_events WHERE shop_id=$1 AND row_id='vs2-m'", [s.shopId])).rows[0];
  assert.ok(Number(ev.corrected_at) >= wall - 1000, 'سرورِ v3 زمانِ تصحیح‌شده گذاشت');
  await reconciled(s, 'v1p');
});
