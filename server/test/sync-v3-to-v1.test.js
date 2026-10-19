'use strict';
/**
 * پلِ v3 ⇒ v1 — دکانِ «دستگاه‌های قاطی» (بعضی v1، بعضی v3)، از راهِ HTTPِ واقعی.
 *
 *   • پرچمِ خاموش (پیش‌فرض) ⇒ صفر نوشتن در `oplog`/`sync_rows`/جدول‌های پل
 *   • روشن ⇒ هر opِ پذیرفته‌شدهٔ v3 در pullِ v1 هست، به همان شکلِ opِ بومیِ v1
 *   • همگرایی در هر دو جهت: دفترِ v1، دفترِ v3، گوشیِ v1 و گوشیِ v3 یک چیز می‌بینند
 *   • تکرار بی‌اثر (opِ تکراری، pullِ دوباره، cursorِ گم‌شده)
 *   • بی حلقه: ردیفِ پل به v3 برنمی‌گردد؛ ردیفِ v1 به v1 برنمی‌گردد؛ دلتا یک بار
 *   • داوریِ یکسان: v1 با ترتیبِ رسیدن، v3 با HLC ⇒ برندهٔ هر فیلد یکی
 *   • آنچه v1 نمی‌تواند بگوید ⇒ صندوقِ تعارض (`v1_unbridged`)، نه دور ریختن
 *   • پول: Minor/100 و برگشت با همان fromLegacy ⇒ بی تغییر
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const fs = require('node:fs');
const h = require('./helpers');
const { one, query } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const liveConfig = require('../src/lib/live-config');
const toV1 = require('../src/lib/sync-v3-to-v1');
const schema = require('../src/lib/sync-v3-schema');
const convert = require('../src/lib/sync-v3-convert');

let owner;
let shopId;
let other;
let otherShopId;
let n = 0;
const oid = () => `01JB3${String(++n).padStart(21, '0')}`;
let tick = 0;
/** HLCِ «اکنون» (+ جابه‌جایی)، همیشه بزرگ‌تر از قبلی روی همان دستگاه */
const H = (node, offset = 0) => hlc.format({ wall: Date.now() + offset, counter: ++tick, node });
const OLD = (node) => hlc.format({ wall: Date.now() - 3_600_000, counter: ++tick, node });

const v3push = (token, device, ops, extra = {}) => h.api('POST', '/api/sync/v3/push', {
  token, body: { device_id: device, protocol: 3, ops, ...extra },
});
const v1push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop' }, body: { device_id: device, schema_version: 3, ops },
});
const v1op = (o) => ({ op_id: oid(), ts: Date.now(), ...o });

async function v1pullAll(token, device, since = 0) {
  const ops = [];
  let cursor = since;
  for (let i = 0; i < 50; i++) {
    const r = await h.api('GET', `/api/sync/v1/pull?device_id=${device}&since=${cursor}`, { token, headers: { 'X-App': 'shop' } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    ops.push(...r.body.ops);
    cursor = r.body.cursor;
    if (!r.body.has_more) break;
  }
  return { ops, cursor };
}
async function v3pullAll(token, device, since = 0) {
  const ops = [];
  let cursor = since;
  for (let i = 0; i < 50; i++) {
    const r = await h.api('GET', `/api/sync/v3/pull?device_id=${device}&since=${cursor}`, { token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    ops.push(...r.body.ops);
    cursor = r.body.cursor;
    if (!r.body.has_more) break;
  }
  return { ops, cursor };
}

const counts = async (id = shopId) => one(
  `SELECT (SELECT COUNT(*)::int FROM oplog WHERE account_id=$1) AS oplog,
          (SELECT COUNT(*)::int FROM sync_rows WHERE account_id=$1) AS rows,
          (SELECT COUNT(*)::int FROM sync_v3_v1_out WHERE account_id=$1) AS out,
          (SELECT COUNT(*)::int FROM sync_v3_v1_state WHERE account_id=$1) AS state,
          (SELECT COUNT(*)::int FROM sync_v3_ops WHERE account_id=$1) AS v3ops,
          (SELECT COUNT(*)::int FROM sync_v3_conflicts WHERE account_id=$1 AND kind='v1_unbridged') AS parked`, [id]
);
const v1Row = (table, id) => one(
  `SELECT data, deleted_at FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const v3Row = (table, id) => one(
  `SELECT data, deleted_at FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);

/* ---------- مدلِ گوشی: همان قاعدهٔ اعمالِ برنامه‌ها ---------- */

/** گوشیِ v1: همان `applyRemote`ِ `license/sync-core.js` (حذف ⇒ بیرون؛ `$inc` جمع؛ بقیه جایگزین). */
function v1Apply(state, ops) {
  for (const op of ops) {
    const t = (state[op.table] ||= {});
    if (op.type === 'delete') { delete t[op.row_id]; continue; }
    const row = { ...(t[op.row_id] || {}) };
    for (const [f, v] of Object.entries(op.fields || {})) {
      if (v && typeof v === 'object' && !Array.isArray(v) && Number.isFinite(v.$inc)) row[f] = (Number(row[f]) || 0) + v.$inc;
      else row[f] = v;
    }
    t[op.row_id] = row;
  }
  return state;
}
/** گوشیِ v3: همان `effectToLegacy`ِ برنامهٔ v3 روی دفترِ امروز. */
function v3Apply(state, ops) {
  for (const it of ops) {
    const spec = schema.specOf(it.table);
    if (!spec || spec.v3only) continue;
    const t = (state[it.table] ||= {});
    const e = it.effect || {};
    if (e.deleted) { delete t[it.row_id]; continue; }
    const row = { ...(t[it.row_id] || {}) };
    Object.assign(row, toV1.toLegacy(spec, e.set || {}));
    for (const [k, v] of Object.entries(toV1.toLegacy(spec, e.inc || {}))) row[k] = (Number(row[k]) || 0) + v;
    t[it.row_id] = row;
  }
  return state;
}
const pickF = (o, keys) => Object.fromEntries(keys.filter(k => o && k in o).map(k => [k, o[k]]));

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ پل', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ پل' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
  other = await h.newUser('دکانِ دیگر', 'shop');
  const s2 = await h.api('POST', '/api/shop', { token: other.accessToken, body: { name: 'دکانِ دیگر' } });
  otherShopId = s2.body?.shop?.id || s2.body?.id;
  other = await h.signIn(other, 'shop');
});
test.after(async () => { await h.stop(); });

/* ------------------------------------------------------------ پرچم و پول */

test('پول و مقدار: Minor/100 و Milli/1000؛ برگشت با fromLegacy همان عددِ صحیح (بردارهای مرجع)', () => {
  const vec = JSON.parse(fs.readFileSync(path.join(__dirname, '../../license/test-vectors/money-v1.json'), 'utf8'));
  const spec = schema.specOf('products');
  for (const [, minor] of vec.fromLegacy.money) {
    const legacy = toV1.toLegacy(spec, { salePriceMinor: minor }).salePrice;
    assert.equal(convert.toMinor(legacy), minor, `minor ${minor}`);
  }
  for (const [, milli] of vec.fromLegacy.qty) {
    const legacy = toV1.toLegacy(spec, { minStockMilli: milli }).minStock;
    assert.equal(convert.toMilli(legacy), milli, `milli ${milli}`);
  }
  //  بازهٔ بزرگ: هر عددِ صحیح تا ۱e12 رفت و برگشت را تاب می‌آورد
  for (const m of [1, 7, 19, 101, 2575, 99999999, 123456789012, -1995, -1]) {
    assert.equal(convert.toMinor(m / 100), m);
  }
  assert.deepEqual(toV1.incToLegacy(schema.specOf('sales'), { paidAmountMinor: 5000 }), { paidAmount: { $inc: 50 } });
  assert.deepEqual(toV1.toLegacy(spec, { name: 'x', salePriceMinor: null, wholesalePriceMinor: 1 }),
    { name: 'x', salePrice: null, wholesalePrice: 0.01 });
});

test('پرچمِ خاموش (پیش‌فرض) ⇒ هیچ نوشتنی در v1 و جدول‌های پل؛ گوشیِ v1 چیزی از v3 نمی‌گیرد', async () => {
  assert.equal(await toV1.enabled({ app: 'shop', accountKind: 'shop', accountId: shopId }), false);
  const before = await counts();
  const D = 'v3-off';
  const r = await v3push(owner.accessToken, D, [
    { op_id: oid(), hlc: H(D), table: 'products', row_id: 'p-off', kind: 'insert', fields: { name: 'خاموش', salePriceMinor: 100 } },
    { op_id: oid(), hlc: H(D), table: 'sales', row_id: 's-off', kind: 'insert', fields: { totalMinor: 100, paidAmountMinor: 0 } },
    { op_id: oid(), hlc: H(D), table: 'sales', row_id: 's-off', kind: 'delta', fields: { paidAmountMinor: 100 } },
    { op_id: oid(), hlc: H(D), table: 'stockCounts', row_id: 'c-off', kind: 'insert', fields: { productId: 'p-off', countedQtyMilli: 5000 } },
  ]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await v3pullAll(owner.accessToken, 'v3-off-2');
  await h.api('GET', '/api/sync/v3/status?device_id=v3-off-2', { token: owner.accessToken });
  const after = await counts();
  assert.equal(after.oplog, before.oplog, 'oplog');
  assert.equal(after.rows, before.rows, 'sync_rows');
  assert.equal(after.out, 0);
  assert.equal(after.state, 0);
  assert.equal(after.parked, 0);
  assert.ok(after.v3ops > before.v3ops);
  const { ops } = await v1pullAll(owner.accessToken, 'v1-off');
  assert.equal(ops.filter(o => ['p-off', 's-off'].includes(o.row_id)).length, 0);
  const st = await h.api('GET', '/api/sync/v3/status?device_id=v3-off-2', { token: owner.accessToken });
  assert.deepEqual(st.body.bridge.to_v1, { enabled: false });
});

test('پرچمِ دکان بر `all` جلو می‌افتد؛ فقط true روشن است', async () => {
  const ctx = { app: 'shop', accountKind: 'shop', accountId: otherShopId };
  await liveConfig.set('all', toV1.FLAG_KEY, true);
  assert.equal(await toV1.enabled(ctx), true);
  await liveConfig.set(otherShopId, toV1.FLAG_KEY, false);
  assert.equal(await toV1.enabled(ctx), false);
  await liveConfig.set(otherShopId, toV1.FLAG_KEY, 'true');
  assert.equal(await toV1.enabled(ctx), false, 'رشته روشن نیست');
  await liveConfig.remove('all', toV1.FLAG_KEY);
  await liveConfig.remove(otherShopId, toV1.FLAG_KEY);
  assert.equal(await toV1.enabled(ctx), false);
  assert.equal(await toV1.enabled({ app: 'pump', accountKind: 'station', accountId: otherShopId }), false);
});

/* ------------------------------------------------------------ روشن */

test('روشن کردن: تاریخچهٔ v3ِ پیش از پرچم هم به v1 می‌رسد (صندوقِ پایدار)، و شمارش ⇒ صندوقِ تعارض', async () => {
  await liveConfig.set(shopId, toV1.FLAG_KEY, true);
  await h.api('GET', '/api/sync/v3/status?device_id=v3-off-2', { token: owner.accessToken });
  assert.equal((await v1Row('products', 'p-off')).data.salePrice, 1);
  assert.equal((await v1Row('sales', 's-off')).data.paidAmount, 1, 'درج + دلتا، یک بار');
  const parked = await one(
    `SELECT * FROM sync_v3_conflicts WHERE account_id=$1 AND kind='v1_unbridged' AND row_id='c-off'`, [shopId]
  );
  assert.ok(parked, 'شمارشِ انبار (فقط v3) دور ریخته نشد');
  assert.equal(parked.detail.reason, 'v3_only_table');
  assert.equal(parked.loser_value.set.countedQtyMilli, 5000);
  const list = await h.api('GET', '/api/sync/v3/conflicts', { token: owner.accessToken });
  assert.ok(list.body.conflicts.some(c => c.kind === 'v1_unbridged' && c.row_id === 'c-off'));
});

test('opِ v3 در pullِ v1، به همان شکلِ opِ بومیِ v1 (pull و snapshot)', async () => {
  const D = 'v3-shape';
  await v1push(owner.accessToken, 'v1-shape', [
    v1op({ table: 'products', row_id: 'p-native', type: 'insert', fields: { name: 'بومی', salePrice: 12.5 } }),
  ]);
  const r = await v3push(owner.accessToken, D, [
    { op_id: oid(), hlc: H(D), table: 'products', row_id: 'p-shape', kind: 'insert', fields: { name: 'از v3', salePriceMinor: 2575, minStockMilli: 1500 } },
  ]);
  assert.equal(r.body.results[0].status, 'applied');
  const { ops } = await v1pullAll(owner.accessToken, 'v1-reader');
  const native = ops.find(o => o.row_id === 'p-native');
  const bridged = ops.find(o => o.row_id === 'p-shape');
  assert.ok(native && bridged);
  const shape = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, v === null ? 'null' : typeof v]));
  assert.deepEqual(shape(bridged), shape(native));
  assert.deepEqual(Object.keys(bridged).sort(), Object.keys(native).sort());
  assert.equal(bridged.type, 'insert');
  assert.equal(bridged.device_id, D);
  assert.deepEqual(bridged.fields, { name: 'از v3', salePrice: 25.75, minStock: 1.5 });
  assert.match(bridged.op_id, /^[A-Za-z0-9_-]{1,64}$/);
  assert.equal(bridged.schema_version, native.schema_version);

  const snap = await h.api('GET', '/api/sync/v1/snapshot', { token: owner.accessToken, headers: { 'X-App': 'shop' } });
  const sn = snap.body.tables.products.find(p => p.id === 'p-native');
  const sb = snap.body.tables.products.find(p => p.id === 'p-shape');
  assert.deepEqual(Object.keys(sb).sort(), Object.keys(sn).sort());
  assert.deepEqual(sb.data, { name: 'از v3', salePrice: 25.75, minStock: 1.5 });
  //  همان دستگاه opِ خودش را از v1 پس نمی‌گیرد (device_id)
  const self = await v1pullAll(owner.accessToken, D);
  assert.equal(self.ops.filter(o => o.row_id === 'p-shape').length, 0);
});

test('فروشِ v3 با tx: فاکتور، قلم، حرکت و پرداخت در v1؛ دلتا `$inc` و لغوِ فاکتور status', async () => {
  const D = 'v3-pos';
  const tx = 'tx-sale-1';
  const r = await v3push(owner.accessToken, D, [
    { op_id: oid(), hlc: H(D), tx, table: 'sales', row_id: 's-1', kind: 'insert', fields: { totalMinor: 5050, paidAmountMinor: 0, status: 'done' } },
    { op_id: oid(), hlc: H(D), tx, table: 'saleItems', row_id: 'si-1', kind: 'insert', fields: { saleId: 's-1', productId: 'p-shape', quantityMilli: 2000, unitPriceMinor: 2525 } },
    { op_id: oid(), hlc: H(D), tx, table: 'stockMovements', row_id: 'm-1', kind: 'insert', fields: { productId: 'p-shape', qtyMilli: -2000, type: 'sale', refId: 's-1' } },
    { op_id: oid(), hlc: H(D), tx, table: 'sales', row_id: 's-1', kind: 'delta', fields: { paidAmountMinor: 5050 } },
  ]);
  assert.ok(r.body.results.every(x => x.status === 'applied'), JSON.stringify(r.body));
  assert.deepEqual((await v1Row('sales', 's-1')).data, { total: 50.5, paidAmount: 50.5, status: 'done' });
  assert.deepEqual((await v1Row('saleItems', 'si-1')).data, { saleId: 's-1', productId: 'p-shape', quantity: 2, unitPrice: 25.25 });
  assert.equal((await v1Row('stockMovements', 'm-1')).data.qty, -2);

  const { ops } = await v1pullAll(owner.accessToken, 'v1-pos');
  const inc = ops.find(o => o.row_id === 's-1' && o.type === 'update');
  assert.deepEqual(inc.fields, { paidAmount: { $inc: 50.5 } });

  const c = await v3push(owner.accessToken, D, [
    { op_id: oid(), hlc: H(D), table: 'sales', row_id: 's-1', kind: 'cancel', reason: 'اشتباه' },
    { op_id: oid(), hlc: H(D), table: 'transactions', row_id: 't-1', kind: 'insert', fields: { debtorId: 'd-1', amountMinor: 700, type: 'give' } },
  ]);
  assert.equal(c.body.results[0].status, 'applied');
  const sale = (await v1Row('sales', 's-1')).data;
  assert.equal(sale.status, 'cancelled');
  assert.equal(sale.cancelReason, 'اشتباه');
  await v3push(owner.accessToken, D, [
    { op_id: oid(), hlc: H(D), table: 'transactions', row_id: 't-1', kind: 'cancel', reason: 'اشتباه' },
  ]);
  //  «لغو»ِ سندی جز فاکتور در v1 همان حذفِ نرم است (برعکسِ مبدلِ v2)
  assert.ok((await v1Row('transactions', 't-1')).deleted_at !== null);
});

test('همگرایی در هر دو جهت: دو گوشیِ v1 و دو گوشیِ v3، ویرایش، حذف، برگشت، دلتا و ساعتِ جلو', async () => {
  const [A1, A2, B1, B2] = ['v1-mix-a', 'v1-mix-b', 'v3-mix-a', 'v3-mix-b'];
  const tok = owner.accessToken;
  const ok = (r) => { assert.equal(r.status, 200, JSON.stringify(r.body)); return r; };

  //  ۱) کالا از v1، مشتری از v3، فاکتور از v1
  ok(await v1push(tok, A1, [
    v1op({ table: 'products', row_id: 'p-mix', type: 'insert', fields: { name: 'برنج', salePrice: 100, purchasePrice: 80 } }),
    v1op({ table: 'sales', row_id: 's-mix', type: 'insert', fields: { total: 300, paidAmount: 100, status: 'done' } }),
  ]));
  ok(await v3push(tok, B1, [
    { op_id: oid(), hlc: H(B1), table: 'debtors', row_id: 'd-mix', kind: 'insert', fields: { name: 'احمد', phone: '0700111222' } },
  ]));
  //  ۲) هر دو طرف پرداخت روی همان فاکتور (دلتا از هر دو، جمع)
  ok(await v1push(tok, A2, [v1op({ table: 'sales', row_id: 's-mix', type: 'update', fields: { paidAmount: { $inc: 50 } } })]));
  ok(await v3push(tok, B2, [{ op_id: oid(), hlc: H(B2), table: 'sales', row_id: 's-mix', kind: 'delta', fields: { paidAmountMinor: 2525 } }]));
  //  ۳) ویرایشِ هم‌زمانِ یک فیلد: v3 با HLCِ کهنه (آفلاین) می‌بازد؛ v1 دیرتر رسیده
  ok(await v1push(tok, A1, [v1op({ table: 'products', row_id: 'p-mix', type: 'update', fields: { salePrice: 110 } })]));
  ok(await v3push(tok, B1, [{ op_id: oid(), hlc: OLD(B1), table: 'products', row_id: 'p-mix', kind: 'update', fields: { salePriceMinor: 9900 } }]));
  //  ۴) فیلدِ دیگرِ همان ردیف از v3 (تازه) می‌برد
  ok(await v3push(tok, B2, [{ op_id: oid(), hlc: H(B2), table: 'products', row_id: 'p-mix', kind: 'update', fields: { purchasePriceMinor: 8550, name: 'برنجِ سفید' } }]));
  //  ۵) ساعتِ v3 دو دقیقه جلو (کج نیست)؛ بعد v1 همان فیلد را عوض می‌کند ⇒ v1 دیرتر رسیده و در هر دو می‌برد
  ok(await v3push(tok, B1, [{ op_id: oid(), hlc: H(B1, 120_000), table: 'debtors', row_id: 'd-mix', kind: 'update', fields: { name: 'احمدِ جلو' } }]));
  ok(await v1push(tok, A2, [v1op({ table: 'debtors', row_id: 'd-mix', type: 'update', fields: { name: 'احمد خان' } })]));
  //  ۶) حذف از v3 و برگشت از v1
  ok(await v3push(tok, B2, [
    { op_id: oid(), hlc: H(B2), table: 'products', row_id: 'p-del', kind: 'insert', fields: { name: 'موقت', salePriceMinor: 500 } },
  ]));
  ok(await v3push(tok, B2, [{ op_id: oid(), hlc: H(B2), table: 'products', row_id: 'p-del', kind: 'delete' }]));
  ok(await v1push(tok, A1, [v1op({ table: 'products', row_id: 'p-del2', type: 'insert', fields: { name: 'موقتِ ۲' } })]));
  ok(await v1push(tok, A1, [v1op({ table: 'products', row_id: 'p-del2', type: 'delete' })]));

  //  همه همگام می‌شوند (دو دور، به ترتیبِ دلخواه)
  const v1s = { [A1]: {}, [A2]: {} };
  const v3s = { [B1]: {}, [B2]: {} };
  const cur = {};
  for (let round = 0; round < 2; round++) {
    for (const d of [B2, A1, B1, A2]) {
      if (d.startsWith('v1')) {
        const { ops, cursor } = await v1pullAll(tok, d, cur[d] || 0);
        v1Apply(v1s[d], ops); cur[d] = cursor;
      } else {
        const { ops, cursor } = await v3pullAll(tok, d, cur[d] || 0);
        v3Apply(v3s[d], ops); cur[d] = cursor;
      }
    }
  }
  //  گوشی‌ها opهای خودشان را از سرور نمی‌گیرند ⇒ نوشتهٔ خودشان را دستی می‌گذاریم: مقایسه روی دفترِ سرور
  const snap1 = (await h.api('GET', '/api/sync/v1/snapshot', { token: tok, headers: { 'X-App': 'shop' } })).body.tables;
  const snap3 = (await h.api('GET', '/api/sync/v3/snapshot', { token: tok })).body.tables;
  const v1Of = (t, id) => (snap1[t] || []).find(r => r.id === id);
  const v3Of = (t, id) => {
    const r = (snap3[t] || []).find(x => x.id === id);
    return r && { id: r.id, data: toV1.toLegacy(schema.specOf(t), r.data) };
  };
  const want = {
    'products/p-mix': ['name', 'salePrice', 'purchasePrice'],
    'sales/s-mix': ['total', 'paidAmount', 'status'],
    'debtors/d-mix': ['name', 'phone'],
  };
  for (const [key, fields] of Object.entries(want)) {
    const [t, id] = key.split('/');
    const a = pickF(v1Of(t, id).data, fields);
    const b = pickF(v3Of(t, id).data, fields);
    assert.deepEqual(a, b, `${key}: v1 ${JSON.stringify(a)} ≠ v3 ${JSON.stringify(b)}`);
  }
  assert.deepEqual(pickF(v1Of('products', 'p-mix').data, ['name', 'salePrice', 'purchasePrice']),
    { name: 'برنجِ سفید', salePrice: 110, purchasePrice: 85.5 });
  assert.equal(v1Of('sales', 's-mix').data.paidAmount, 100 + 50 + 25.25, 'دلتای هر دو طرف، هر کدام یک بار');
  assert.equal(v1Of('debtors', 'd-mix').data.name, 'احمد خان', 'v1ِ دیرتر رسیده در هر دو می‌برد');
  assert.equal(v1Of('products', 'p-del'), undefined);
  assert.equal(v3Of('products', 'p-del'), undefined);
  assert.equal(v1Of('products', 'p-del2'), undefined);
  assert.equal(v3Of('products', 'p-del2'), undefined);

  //  گوشیِ تازه (از صفر): v1 همهٔ opهای `oplog` را به ترتیب، v3 همهٔ اثرها را — هر دو همان دفترِ سرور
  const fresh1 = v1Apply({}, (await v1pullAll(tok, 'v1-fresh')).ops);
  const fresh3 = v3Apply({}, (await v3pullAll(tok, 'v3-fresh')).ops);
  for (const [key, fields] of Object.entries(want)) {
    const [t, id] = key.split('/');
    const truth = pickF(v1Of(t, id).data, fields);
    assert.deepEqual(pickF(fresh1[t][id], fields), truth, `گوشیِ v1ِ تازه ${key}`);
    assert.deepEqual(pickF(fresh3[t][id], fields), truth, `گوشیِ v3ِ تازه ${key}`);
  }
  for (const st of [fresh1, fresh3, ...Object.values(v1s), ...Object.values(v3s)]) {
    assert.ok(!(st.products && st.products['p-del']), 'p-del پاک است');
  }
  //  گوشیِ v1 فروشِ v3 را می‌بیند و گوشیِ v3 فروشِ v1 را
  assert.ok(v1s[A1].sales && v1s[A1].sales['s-1']);
  assert.ok(v3s[B1].sales && v3s[B1].sales['s-mix']);
  assert.equal(v3s[B1].sales['s-mix'].paidAmount, 175.25, 'گوشیِ v3: درج + دلتای v1 + دلتای v3ِ دیگر');
});

test('بی حلقه: ردیفِ پل به v3 برنمی‌گردد و ردیفِ v1 به v1؛ دلتا با هر تعداد pull یک بار', async () => {
  const tok = owner.accessToken;
  const before = await counts();
  const bridgedBack = await one(
    `SELECT COUNT(*)::int AS n FROM sync_v3_ops v JOIN sync_v3_v1_out x
        ON x.account_id = v.account_id AND (v.op_id = x.v1_op_id OR v.op_id LIKE x.v1_op_id || '.%')
      WHERE v.account_id=$1 AND v.origin='v1'`, [shopId]
  );
  assert.equal(bridgedBack.n, 0, 'هیچ ردیفِ پل از راهِ پلِ v1 ⇒ v3 برنگشت');
  const echoed = await one(
    `SELECT COUNT(*)::int AS n FROM sync_v3_v1_out x JOIN sync_v3_ops v
        ON v.account_id = x.account_id AND v.op_id = x.v3_op_id
      WHERE x.account_id=$1 AND v.origin='v1'`, [shopId]
  );
  assert.equal(echoed.n, 0, 'هیچ opِ v1 به v1 برنگشت');
  for (let i = 0; i < 3; i++) {
    await v3pullAll(tok, 'v3-loop', 0);
    await v1pullAll(tok, 'v1-loop', 0);
    await h.api('GET', '/api/sync/v3/status?device_id=v3-loop', { token: tok });
    await h.api('GET', '/api/sync/v3/snapshot', { token: tok });
  }
  const after = await counts();
  assert.equal(after.oplog, before.oplog);
  assert.equal(after.v3ops, before.v3ops);
  assert.equal(after.out, before.out);
  assert.equal((await v3Row('sales', 's-mix')).data.paidAmountMinor, 17525);
  assert.equal((await v1Row('sales', 's-mix')).data.paidAmount, 175.25);
  const st = await h.api('GET', '/api/sync/v3/status?device_id=v3-loop', { token: tok });
  assert.equal(st.body.bridge.caught_up, true);
  assert.deepEqual(st.body.bridge.to_v1, { enabled: true, caught_up: true });
});

test('تکرار بی‌اثر: همان opهای v3 دوباره، و حتی cursorِ گم‌شده ⇒ هیچ ردیفِ تازه‌ای در v1', async () => {
  const tok = owner.accessToken;
  const D = 'v3-replay';
  const ops = [
    { op_id: oid(), hlc: H(D), table: 'sales', row_id: 's-rep', kind: 'insert', fields: { totalMinor: 1000, paidAmountMinor: 0 } },
    { op_id: oid(), hlc: H(D), table: 'sales', row_id: 's-rep', kind: 'delta', fields: { paidAmountMinor: 400 } },
    { op_id: oid(), hlc: H(D), table: 'products', row_id: 'p-rep', kind: 'insert', fields: { name: 'تکرار' } },
  ];
  await v3push(tok, D, ops);
  const once = await counts();
  const again = await v3push(tok, D, ops);
  assert.ok(again.body.results.every(r => r.status === 'duplicate'));
  assert.deepEqual(await counts(), once);
  //  cursorِ پل گم شد (بدترین حالت): صندوق از صفر بازخوانی می‌شود ولی چیزی دو بار نمی‌نشیند
  await query(`DELETE FROM sync_v3_v1_state WHERE account_id=$1`, [shopId]);
  await h.api('GET', '/api/sync/v3/status?device_id=v3-replay', { token: tok });
  const replay = await counts();
  assert.equal(replay.oplog, once.oplog);
  assert.equal(replay.out, once.out);
  assert.equal(replay.parked, once.parked);
  assert.equal((await v1Row('sales', 's-rep')).data.paidAmount, 4);
  //  همان opِ پل اگر دستی دوباره به v1 برسد: op_id یکتاست ⇒ «تکراری»
  const out = await one(`SELECT v1_op_id, v3_op_id FROM sync_v3_v1_out WHERE account_id=$1 AND v3_op_id=$2`, [shopId, ops[1].op_id]);
  const dup = await v1push(tok, 'v1-replay', [{ op_id: out.v1_op_id, table: 'sales', row_id: 's-rep', type: 'update', fields: { paidAmount: { $inc: 4 } } }]);
  assert.equal(dup.body.results[0].status, 'duplicate');
  assert.equal((await v1Row('sales', 's-rep')).data.paidAmount, 4);
});

test('دستگاهی که op_idِ پل را پیش‌دستانه بگیرد پل را نمی‌بندد (شناسهٔ جایگزین، بی صندوق)', async () => {
  const tok = owner.accessToken;
  const D = 'v3-pre';
  const future = oid();
  const ctx = { app: 'shop', accountKind: 'shop', accountId: shopId };
  const squat = await v1push(tok, 'v1-squatter', [
    { op_id: toV1.v1OpId(ctx, future, 0), table: 'expenses', row_id: 'e-squat', type: 'insert', fields: { title: 'x', amount: 1 } },
  ]);
  assert.equal(squat.body.results[0].status, 'applied');
  const before = await counts();
  await v3push(tok, D, [{ op_id: future, hlc: H(D), table: 'products', row_id: 'p-pre', kind: 'insert', fields: { name: 'پیش‌دستی' } }]);
  assert.equal((await v1Row('products', 'p-pre')).data.name, 'پیش‌دستی');
  const after = await counts();
  assert.equal(after.parked, before.parked);
  const out = await one(`SELECT v1_op_id FROM sync_v3_v1_out WHERE account_id=$1 AND v3_op_id=$2`, [shopId, future]);
  assert.match(out.v1_op_id, /^v3b-[0-9a-f]{40}-[0-9a-f]{16}$/);
});

test('صفِ قدیمِ برنامه (format v2): opی که v1 هم دارد دوباره نمی‌رود؛ opی که بعداً از v1 برسد «تکراری» است', async () => {
  const tok = owner.accessToken;
  //  الف) اول v1، بعد همان op از راهِ v3 (پاسخِ v1 گم شده بود)
  const shared = v1op({ table: 'sales', row_id: 's-v2', type: 'insert', fields: { total: 20, paidAmount: 5 } });
  await v1push(tok, 'v1-upgrading', [shared]);
  const r = await v3push(tok, 'v1-upgrading', [shared], { format: 'v2', schema_version: 3 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await h.api('GET', '/api/sync/v3/status?device_id=x', { token: tok });
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM oplog WHERE account_id=$1 AND row_id='s-v2'`, [shopId])).n, 1);

  //  ب) اول از راهِ v3 (صفِ v2)، بعد (برگشت به نسخهٔ قدیم) همان op از v1
  const later = v1op({ table: 'sales', row_id: 's-v2b', type: 'update', fields: { paidAmount: { $inc: 3 } } });
  await v3push(tok, 'v1-upgrading', [v1op({ table: 'sales', row_id: 's-v2b', type: 'insert', fields: { total: 9, paidAmount: 0 } }), later],
    { format: 'v2', schema_version: 3 });
  assert.equal((await v1Row('sales', 's-v2b')).data.paidAmount, 3);
  const back = await v1push(tok, 'v1-upgrading', [later]);
  assert.equal(back.body.results[0].status, 'duplicate', 'همان op_idِ v1 ⇒ دلتا دو بار جمع نمی‌شود');
  await v3pullAll(tok, 'v3-v2b');
  assert.equal((await v1Row('sales', 's-v2b')).data.paidAmount, 3);
  assert.equal((await v3Row('sales', 's-v2b')).data.paidAmountMinor, 300);
});

test('برگرداندن از صندوقِ تعارضِ v3 به v1 هم می‌رسد', async () => {
  const tok = owner.accessToken;
  const [X, Y] = ['v3-cf-x', 'v3-cf-y'];
  await v3push(tok, X, [{ op_id: oid(), hlc: H(X), table: 'products', row_id: 'p-cf', kind: 'insert', fields: { name: 'اول' } }]);
  await v3push(tok, Y, [{ op_id: oid(), hlc: H(Y), table: 'products', row_id: 'p-cf', kind: 'update', fields: { name: 'دوم' } }]);
  //  X بی آن‌که «دوم» را دیده باشد، با HLCِ کهنه ⇒ می‌بازد و به صندوق می‌رود
  const lost = await v3push(tok, X, [{ op_id: oid(), hlc: OLD(X), table: 'products', row_id: 'p-cf', kind: 'update', fields: { name: 'سوم' } }]);
  assert.equal(lost.body.results[0].status, 'conflict');
  assert.equal((await v1Row('products', 'p-cf')).data.name, 'دوم', 'بازنده به v1 نرفت');
  const list = await h.api('GET', '/api/sync/v3/conflicts', { token: tok });
  const cf = list.body.conflicts.find(c => c.row_id === 'p-cf' && c.kind === 'lww');
  const res = await h.api('POST', `/api/sync/v3/conflicts/${cf.id}/resolve`, { token: tok, body: { action: 'revert' } });
  assert.equal(res.status, 200, JSON.stringify(res.body));
  assert.equal((await v3Row('products', 'p-cf')).data.name, 'سوم');
  assert.equal((await v1Row('products', 'p-cf')).data.name, 'سوم');
});

test('پمپ و دکانِ دیگر دست‌نخورده: دکانِ بی پرچم هیچ ردیفِ پل ندارد', async () => {
  const c2 = await counts(otherShopId);
  assert.equal(c2.out, 0);
  assert.equal(c2.state, 0);
  assert.ok(syncV1.PUMP_TABLES.includes('SafeEntry'));
  //  پل فقط دکان است
  assert.equal(await toV1.enabled({ app: 'pump', accountKind: 'station', accountId: shopId }), false);
});
