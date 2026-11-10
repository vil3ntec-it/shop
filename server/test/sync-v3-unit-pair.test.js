'use strict';
/**
 * R9 ِ بازبینیِ دومِ دستهٔ ۴۳ (SECURITY-AUDIT B9): نامِ قدیم و نامِ واحددارِ **یک** فیلد در یک op ِ v1
 * (`amount` و `amountMinor`، `quantity` و `quantityMilli`، …). مبدل (`fromV2` ِ سرور، همزادِ وب `fromLegacyOp`)
 * پیش‌تر به ترتیبِ کلیدها یکی را روی دیگری می‌نشاند ⇒ `{amount:100, amountMinor:999999}` در v1 مبلغِ ۱۰۰ و در v3 ‏9999.99.
 * حالا ترتیب بی‌اثر است: هر دو پس از **همان** تبدیلِ مبدل یکی ⇒ همان یک مقدار؛ ناجور ⇒ کلِ op با `unit_conflict` رد
 * (همان راهِ op ِ نادرست: push ِ v2 ⇒ `rejected`، پلِ v1 ⇒ به v3 نمی‌رسد، کلاینت ⇒ v2 تا داوریِ سرور).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_SYNC_MAX = process.env.RATE_SYNC_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const h = require('./helpers');
const { one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');
const convert = require('../src/lib/sync-v3-convert');

const V3 = require(path.join(__dirname, '..', '..', 'license', 'sync-v3-engine.js'));
const HLC = '001760000000000:000000:dev-x';
const TS = 1_760_000_000_000;

let n = 0;
const oid = () => `01JR9PAIR${String(++n).padStart(17, '0')}`;
const rev = (f) => Object.fromEntries(Object.entries(f).reverse());
const op = (table, type, fields) => ({ op_id: oid(), ts: TS, table, row_id: `${table}-${n}`, type, fields });
const server = (o) => convert.fromV2(o, { schemaVersion: 3, deviceId: 'dev-x', hlc: HLC });
const strip = (ops) => ops.map(({ legacy, ...rest }) => rest);   // eslint-disable-line no-unused-vars

/** ناجور: هر دو ترتیب ⇒ رد */
const CLASH = [
  ['expenses', 'insert', { title: 'کرایه', amount: 100, amountMinor: 999999 }],          //  نمونهٔ بازبین
  ['expenses', 'insert', { title: 'کرایه', amount: 1.005, amountMinor: 101 }],           //  همان Math.round ِ مبدل ⇒ ۱۰۰
  ['saleItems', 'insert', { saleId: 's', productId: 'p', quantity: 2, quantityMilli: 3000 }],
  ['expenses', 'update', { amount: null, amountMinor: 5 }],
  ['expenses', 'insert', { title: 'x', amount: 'نامعلوم', amountMinor: 5 }],             //  نامِ قدیمِ نامفهوم
  ['expenses', 'insert', { title: 'x', amount: 100, amountMinor: '10000' }],             //  رشته ≠ عدد (سخت‌گیر)
  ['sales', 'update', { paidAmount: { $inc: 50 }, paidAmountMinor: { $inc: 4000 } }],
  ['sales', 'update', { paidAmount: { $inc: 50 }, paidAmountMinor: 5000 }],              //  دلتا در برابرِ مقدار
  ['products', 'update', { minStock: 2.5, minStockMilli: 2000 }],
];
/** یکی: هر دو ترتیب ⇒ همان یک مقدار، با نامِ تازه */
const AGREE = [
  ['expenses', 'insert', { title: 'کرایه', amount: 100, amountMinor: 10000 }, { amountMinor: 10000 }],
  ['expenses', 'insert', { title: 'کرایه', amount: 1.005, amountMinor: 100 }, { amountMinor: 100 }],
  ['saleItems', 'insert', { saleId: 's', productId: 'p', quantity: 2.5, quantityMilli: 2500 }, { quantityMilli: 2500 }],
  ['expenses', 'update', { amount: null, amountMinor: null }, { amountMinor: null }],
  ['sales', 'update', { paidAmount: { $inc: 50.25 }, paidAmountMinor: { $inc: 5025 } }, { paidAmountMinor: 5025 }],
];

test('مبدلِ سرور، ترتیب بی‌اثر: ناجور ⇒ کلِ op رد با unit_conflict؛ یکی پس از همان تبدیل ⇒ همان یک مقدار', () => {
  for (const [table, type, fields] of CLASH) {
    for (const f of [fields, rev(fields)]) {
      const o = op(table, type, f);
      assert.deepEqual(server(o), { ok: false, reason: 'unit_conflict', op_id: o.op_id }, `${table} ${JSON.stringify(f)}`);
    }
  }
  for (const [table, type, fields, want] of AGREE) {
    const outs = [fields, rev(fields)].map((f) => server(op(table, type, f)));
    for (const r of outs) {
      assert.equal(r.ok, true, `${table} ${JSON.stringify(fields)}: ${r.reason}`);
      const got = Object.assign({}, ...r.ops.map((x) => x.fields || {}));
      for (const [k, v] of Object.entries(want)) assert.equal(got[k], v, `${table}.${k}`);
      for (const k of Object.keys(want)) assert.equal(k.replace(/(Minor|Milli)$/, '') in got, false, `${table}: نامِ قدیم`);
    }
    assert.deepEqual(outs[0].ops.map((x) => x.fields), outs[1].ops.map((x) => x.fields));
  }
});

test('همزادِ وب (fromLegacyOp) همان داوری: ناجور ⇒ v2 برای سرور با همان دلیل؛ یکی ⇒ همان opهای سرور', () => {
  for (const [table, type, fields] of CLASH) {
    for (const f of [fields, rev(fields)]) {
      const r = V3.fromLegacyOp(op(table, type, f), HLC);
      assert.deepEqual(r, { ok: false, ops: [], dropped: [], native: false, reason: 'unit_conflict' }, `${table} ${JSON.stringify(f)}`);
    }
  }
  for (const [table, type, fields] of AGREE) {
    for (const f of [fields, rev(fields)]) {
      const o = op(table, type, f);
      assert.deepEqual(V3.fromLegacyOp(o, HLC).ops, strip(server(o).ops), `${table} ${JSON.stringify(f)}`);
    }
  }
});

/* ---------------------------------------------------- با سرورِ واقعی */

let owner;
let shopId;
const rowOf = (table, id) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const v1RowOf = (table, id) => one(
  `SELECT data FROM sync_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ دکانِ R9', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ R9' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { await h.stop(); });

test('پلِ v1 ⇒ v3: سندِ تازه با amount و amountMinor ِ ناجور به v3 نمی‌رسد (هر ترتیبی)؛ یکی ⇒ همان مبلغ', async () => {
  const at = Date.now() - 5000;
  const v1 = (row, fields) => ({ op_id: oid(), ts: at, table: 'expenses', row_id: row, type: 'insert', fields });
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: owner.accessToken, headers: { 'X-App': 'shop' },
    body: { device_id: 'r9-old-phone', schema_version: 3, ops: [
      v1('e-r9-a', { title: 'کرایه', amount: 100, amountMinor: 999999 }),
      v1('e-r9-b', { title: 'کرایه', amountMinor: 999999, amount: 100 }),
      v1('e-r9-ok', { title: 'کرایه', amount: 100, amountMinor: 10000 }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const pulled = await h.api('GET', '/api/sync/v3/pull?device_id=r9-v3-dev&since=0', { token: owner.accessToken });
  assert.equal(pulled.status, 200, JSON.stringify(pulled.body));

  for (const id of ['e-r9-a', 'e-r9-b']) {
    assert.equal(await rowOf('expenses', id), null, `${id}: v3 مبلغی را نشان نمی‌دهد که v1 ندارد`);
    assert.equal((await v1RowOf('expenses', id)).data.amount, 100, `${id}: v1 دست نخورد`);
  }
  assert.equal((await rowOf('expenses', 'e-r9-ok')).data.amountMinor, 10000);
  assert.equal('amount' in (await rowOf('expenses', 'e-r9-ok')).data, false);
});

test('صفِ v2 از راهِ v3: ناجور ⇒ «rejected» با unit_conflict (هر ترتیبی)، چیزی نمی‌نشیند؛ یکی ⇒ applied', async () => {
  const at = Date.now() - 4000;
  const v2 = (row, fields) => ({ op_id: oid(), ts: at, table: 'expenses', row_id: row, type: 'insert', fields });
  const r = await h.api('POST', '/api/sync/v3/push', {
    token: owner.accessToken,
    body: { device_id: 'r9-v2-dev', protocol: 3, format: 'v2', schema_version: 3, ops: [
      v2('e-r9-c', { title: 'کرایه', amount: 100, amountMinor: 999999 }),
      v2('e-r9-d', { title: 'کرایه', amountMinor: 999999, amount: 100 }),
      v2('e-r9-ok2', { title: 'کرایه', amountMinor: 10000, amount: 100 }),
    ] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.status, x.reason || '']),
    [['rejected', 'unit_conflict'], ['rejected', 'unit_conflict'], ['applied', '']]);
  assert.equal(await rowOf('expenses', 'e-r9-c'), null);
  assert.equal(await rowOf('expenses', 'e-r9-d'), null);
  assert.equal((await rowOf('expenses', 'e-r9-ok2')).data.amountMinor, 10000);
});
