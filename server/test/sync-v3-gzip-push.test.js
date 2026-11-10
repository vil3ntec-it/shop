'use strict';
/**
 * SYN-04 — push ِ فشردهٔ Sync v3 (بخشِ ۳.۸.۱ بندِ ۴: «دسته‌های فشرده (gzip) با اندازهٔ وفقی»). تا امروز فقط v1 ِ وب
 * فشرده می‌فرستاد (`sync-gzip-push.test.js`)؛ v3 ِ وب و کاتلین همیشه ساده.
 *
 *   ۱) سرور: pull ِ v3 فقط با پرچمِ دکانِ `sync.gzip_push` (همان پرچمِ v1، پیش‌فرض خاموش) `gzip_push: true` دارد؛
 *      push ِ v3 ِ فشرده = ساده (`applied`، همان بایت‌ها دوباره ⇒ `duplicate`)، gzip ِ خراب ⇒ ۴۰۰ ِ تمیز، ناشناخته ⇒ ۴۱۵.
 *   ۲) موتورِ وب (`license/sync-v3-engine.js`): پس از `gzip_push` بدنهٔ push ِ بالای ۱KB روی خط gzip ِ همان JSON است،
 *      کوچک یا بی پرچم ساده؛ ۴۱۵ ِ فشرده ⇒ صف دست‌نخورده و از آن پس ساده (`gzipRefused`).
 * کاتلین: `HttpV3Transport` + `V3Gzip` (فقط CI؛ `V3GzipTest`).
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_SYNC_MAX = process.env.RATE_SYNC_MAX || '100000';
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const zlib = require('node:zlib');
const h = require('./helpers');
const { query, one } = require('../src/db');
const syncV1 = require('../src/lib/sync-v1');

const ROOT = path.join(__dirname, '..', '..');
const V3 = require(path.join(ROOT, 'license', 'sync-v3-engine.js'));
const core = require(path.join(ROOT, 'license', 'sync-core.js'));

let owner;
let shopId;

const flag = (on) => query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'sync.gzip_push',$2,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`, [shopId, on ? 'true' : 'false']
);
const rowOf = (table, id) => one(
  `SELECT data FROM sync_v3_rows WHERE app='shop' AND account_id=$1 AND table_name=$2 AND row_id=$3`, [shopId, table, id]
);
const auth = () => ({ Authorization: `Bearer ${owner.accessToken}`, 'Content-Type': 'application/json' });
const pull = (dev) => h.api('GET', `/api/sync/v3/pull?device_id=${dev}&since=0`, { token: owner.accessToken });
const rawPush = (bytes, encoding) => fetch(`${h.base()}/api/sync/v3/push`, {
  method: 'POST', headers: { ...auth(), ...(encoding ? { 'Content-Encoding': encoding } : {}) }, body: bytes,
}).then(async (r) => ({ status: r.status, body: JSON.parse((await r.text()) || 'null') }));
const products = (prefix, n) => Array.from({ length: n }, (_, i) => ({ id: `${prefix}-${i}`, name: `کالای فشردهٔ ${prefix} ${i}`, salePrice: 10 + i, purchasePrice: 5 }));

/** fetch ِ سراسری با چشم روی push: سرآیند و بایت‌های روی خط؛ `refuse` ⇒ ۴۱۵ ِ «پراکسی» برای بدنهٔ فشرده */
const wire = [];
let refuse = false;
const realFetch = globalThis.fetch;
globalThis.fetch = async (url, init = {}) => {
  if (String(url).includes('/api/sync/v3/push')) {
    const enc = (init.headers && init.headers['Content-Encoding']) || '';
    wire.push({ enc, body: init.body });
    if (refuse && enc) {
      return new Response(JSON.stringify({ error: { code: 'unsupported_encoding', message: 'فشرده‌سازیِ این درخواست شناخته نشد' } }), { status: 415 });
    }
  }
  return realFetch(url, init);
};

function device(name, data) {
  let state = core.cloneLedger(data);
  const ledger = { read: () => state, write: (d) => { state = core.cloneLedger(d); } };
  const engine = new V3.Engine({ transport: V3.FetchTransport(h.base(), () => owner.accessToken), store: new V3.MemoryStore(), ledger, deviceId: name, random: () => 0.5 });
  engine.keepLocalOnly = true;
  return { engine, add: (list) => { state = { ...state, products: [...(state.products || []), ...list] }; } };
}

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  owner = await h.newUser('صاحبِ push ِ فشردهٔ v3', 'shop');
  const shop = await h.api('POST', '/api/shop', { token: owner.accessToken, body: { name: 'دکانِ push ِ فشردهٔ v3' } });
  shopId = shop.body?.shop?.id || shop.body?.id;
  owner = await h.signIn(owner, 'shop');
});
test.after(async () => { globalThis.fetch = realFetch; await h.stop(); });

test('سرور: `gzip_push` فقط با پرچمِ دکان؛ push ِ v3 ِ فشرده = ساده، تکرار ⇒ تکراری، gzip ِ خراب ۴۰۰ و ناشناخته ۴۱۵ ِ تمیز', async () => {
  const off = await pull('gz-raw');
  assert.equal(off.status, 200);
  assert.equal('gzip_push' in off.body, false, 'پرچم خاموش ⇒ همان پاسخِ پیشین');
  await flag(true);
  assert.equal((await pull('gz-raw')).body.gzip_push, true);

  const t = Date.now();
  const ops = Array.from({ length: 20 }, (_, i) => ({
    op_id: `gz-raw-${i}`, table: 'products', row_id: `gz-raw-p${i}`, kind: 'insert', hlc: `${t}:${i}:gz-raw`, fields: { name: `برنجِ فشرده ${i}`, salePriceMinor: 1000 + i },
  }));
  const text = JSON.stringify({ device_id: 'gz-raw', ops });
  const packed = zlib.gzipSync(Buffer.from(text, 'utf8'));
  assert.ok(packed.length * 3 < Buffer.byteLength(text), `${packed.length} از ${Buffer.byteLength(text)}`);
  const r1 = await rawPush(packed, 'gzip');
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  assert.deepEqual([...new Set(r1.body.results.map((x) => x.status))], ['applied']);
  assert.equal((await rowOf('products', 'gz-raw-p7')).data.salePriceMinor, 1007);
  const r2 = await rawPush(packed, 'gzip');
  assert.deepEqual([...new Set(r2.body.results.map((x) => x.status))], ['duplicate'], 'بازفرستِ همان بایت‌ها');

  const bad = await rawPush(Buffer.concat([packed.subarray(0, 40), Buffer.from('خراب')]), 'gzip');
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_json');
  const unknown = await rawPush(packed, 'br-xyz');
  assert.equal(unknown.status, 415);
  assert.equal(unknown.body.error.code, 'unsupported_encoding');
  assert.equal((await pull('gz-raw')).status, 200, 'سرور زنده ماند');
  await flag(false);
});

test('موتورِ وب: پس از `gzip_push` push ِ بزرگ روی خط gzip ِ همان JSON؛ کوچک و بی پرچم ساده؛ ۴۱۵ ⇒ صف می‌ماند و از آن پس ساده', async () => {
  //  بی پرچم: همیشه ساده
  const E = device('gz-web-off', { products: products('off', 30) });
  wire.length = 0;
  assert.equal((await E.engine.sync()).ok, true);
  assert.ok(wire.length >= 1);
  assert.deepEqual([...new Set(wire.map((w) => w.enc))], [''], 'پرچم خاموش ⇒ ساده');

  await flag(true);
  const D = device('gz-web', {});
  assert.equal((await D.engine.sync()).ok, true);
  assert.equal(D.engine.meta.gzipPush, true, 'pull ِ v3 پرچم را رساند');

  D.add(products('on', 30));
  assert.equal(await D.engine.record(), 30);
  wire.length = 0;
  const p = await D.engine.pushOnce();
  assert.equal(p.ok, true, JSON.stringify(p));
  assert.equal(wire.length, 1);
  assert.equal(wire[0].enc, 'gzip');
  const plain = zlib.gunzipSync(Buffer.from(wire[0].body)).toString('utf8');
  assert.equal(JSON.parse(plain).ops.length, 30, 'زیرِ فشرده همان JSON ِ push');
  assert.ok(wire[0].body.byteLength * 3 < Buffer.byteLength(plain), `${wire[0].body.byteLength} از ${Buffer.byteLength(plain)}`);
  assert.equal((await rowOf('products', 'on-29')).data.salePriceMinor, 3900);
  assert.equal(D.engine.queued(), 0);

  //  کوچک (زیرِ ۱KB) ⇒ ساده
  D.add([{ id: 'one-small', name: 'تک', salePrice: 1 }]);
  assert.equal(await D.engine.record(), 1);
  wire.length = 0;
  assert.equal((await D.engine.pushOnce()).ok, true);
  assert.equal(wire[0].enc, '');

  //  «پراکسی» فشرده را نمی‌پذیرد ⇒ ۴۱۵، هیچ چیز از صف بیرون نمی‌رود؛ تلاشِ بعد ساده و پذیرفته
  D.add(products('refused', 30));
  assert.equal(await D.engine.record(), 30);
  refuse = true;
  wire.length = 0;
  const f = await D.engine.pushOnce();
  assert.equal(f.ok, false);
  assert.equal(f.status, 415);
  assert.equal(D.engine.queued(), 30, 'صف دست نخورد');
  assert.equal(D.engine.meta.gzipRefused, true);
  const again = await D.engine.pushOnce();
  assert.equal(again.ok, true, JSON.stringify(again));
  assert.deepEqual(wire.map((w) => w.enc), ['gzip', '']);
  assert.equal(D.engine.queued(), 0);
  assert.equal((await rowOf('products', 'refused-29')).data.name, 'کالای فشردهٔ refused 29');
  refuse = false;
  await flag(false);
});
