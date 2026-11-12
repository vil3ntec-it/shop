'use strict';
/**
 * بازبینیِ امنیتیِ دستهٔ ۴۰ (docs/SECURITY-AUDIT-fa.md، «بازبینیِ ۴۰»). هر آزمون پیش از رفع سرخ بود؛ روی PGlite و PostgreSQL.
 *
 *   SR40-1  PRD-07 (`lib/product-unit-rules.js`): واحدِ پایهٔ **نامتنی** (عدد، آرایه، بولی، `$inc` ِ v1) نه «op ِ واحددار» شمرده
 *           می‌شد نه «تغییرِ واحد» ⇒ واحدِ کالای گردش‌دار در یک op عوض می‌شد (۴۸ «دانه» ⇒ ۴۸ «7»)، در v1 و v3، صاحب هم.
 *   SR40-2  X2 (`app.js`): استثنای پرسشِ `RAW_SYNC` همهٔ `/api/sync/*` را می‌گرفت، v3 ِ فقط‌دکان هم ⇒ `GET /sync/v3/status?device_id=%00`
 *           ⇒ ۵۰۰ و خطای سرور (همان ۲۲۰۲۱ ِ PostgreSQL)؛ با `V3` ِ بزرگ هم (مسیریابی به بزرگی و کوچکی حساس نیست).
 *   SR40-3  RPT-14 (`routes/storefront-funnel-store.js`): هر سایتِ دیگری با `navigator.sendBeacon` از مرورگرِ بازدیدکنندهٔ **خودش**
 *           (IP و مرورگرِ واقعی و گوناگون، بیرون از سقفِ IP) قیفِ هر ویترین را باد می‌کرد تا سقفِ روزانه.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one } = require('../src/db');
const hlc = require('../src/lib/hlc');
const syncV1 = require('../src/lib/sync-v1');
const live = require('../src/lib/live-config');
const R = require('../src/lib/product-unit-rules');
const { resetAll } = require('../src/middleware/ratelimit');

let shopId = '';
let owner = null;
let seq = 0;
const oid = () => `01JSR${String(++seq).padStart(21, '0')}`;
let tick = 0;
const H = () => hlc.format({ wall: Date.now() + (++tick), counter: 0, node: 'pos' });
const uniq = () => `${Date.now().toString(36)}${++seq}`;

const push3 = (ops) => h.api('POST', '/api/sync/v3/push', { token: owner.accessToken, body: { device_id: 'sr40-3', protocol: 3, ops } });
const push1 = (ops) => h.api('POST', '/api/sync/v1/push', { token: owner.accessToken, body: { device_id: 'sr40-1', schema_version: 1, ops } });
const v3 = (table, rowId, fields, kind = 'insert') => ({ op_id: oid(), hlc: H(), table, row_id: rowId, kind, fields });
const v1 = (table, rowId, fields, type = 'insert') => ({ op_id: oid(), table, row_id: rowId, type, ts: Date.now(), fields });
const outcome = (r) => {
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.results.map((x) => x.reason || x.status);
};
const unitOf = async (table, rowId) => (await one(
  `SELECT data->'unit' AS u FROM ${table} WHERE app='shop' AND account_id=$1 AND table_name='products' AND row_id=$2`, [shopId, rowId]
))?.u;
const tea3 = { name: 'چای', unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24, salePriceMinor: 5000 };
const tea1 = { name: 'چای', unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24, salePrice: 50 };

test.before(async () => {
  await h.start();
  await syncV1.syncSchemaTable();
  const u = await h.newUser('صاحبِ بازبینیِ ۴۰', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ بازبینیِ ۴۰' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  owner = await h.signIn(u, 'shop');
  owner.raw = u;
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

// ---------------------------------------------------------------- SR40-1

test('SR40-1: واحدِ نامتنی تغییرِ واحد است (برنامه هرگز نمی‌فرستدش)؛ null و خالی همان قاعدهٔ همتای برنامه (از ۲-۱۶: قفل روی کالای گردش‌دار)', async () => {
  const moved = async () => true;
  const tea = { unit: 'دانه', saleUnit: 'کارتن', unitFactor: 24 };
  for (const odd of [7, 0, true, false, ['کیلو'], { $inc: 1 }, { a: 1 }]) {
    assert.equal(R.touches({ unit: odd }), true, `touches ${JSON.stringify(odd)}`);
    assert.equal(await R.reasonOf({ unit: odd }, tea, moved), R.LOCKED, JSON.stringify(odd));
    assert.equal(await R.reasonOf({ unit: odd }, tea, async () => false), '', `بی گردش ${JSON.stringify(odd)}`);
    assert.equal(await R.reasonOf({ unit: odd }, null, moved), '', `کالای تازه ${JSON.stringify(odd)}`);
  }
  //  همان رفتارِ همتای برنامه (`ProductDraftRules.unitLocked`) برای متن و نبود — ۲-۱۶ (R2 الف): خالی/null روی کالای گردش‌دار
  //  هم تغییرِ واحد است (گامِ اولِ «خالی، سپس واحدِ تازه»)؛ بی گردش و کالای تازه همان آزاد
  assert.equal(R.touches({ unit: null }), true);
  assert.equal(await R.reasonOf({ unit: null }, tea, moved), R.LOCKED);
  assert.equal(await R.reasonOf({ unit: '' }, tea, moved), R.LOCKED);
  assert.equal(await R.reasonOf({ unit: null }, tea, async () => false), '');
  assert.equal(await R.reasonOf({ unit: '' }, null, moved), '');
});

test('SR40-1: v3، پرچمِ روشن ⇒ واحدِ نامتنی روی کالای گردش‌دار رد (صاحب هم)؛ ردیف دست‌نخورده', async () => {
  await live.set(shopId, R.FLAG, true);
  for (const [tag, odd] of [['n', 7], ['a', ['کیلو']], ['b', true], ['o', { kg: 1 }]]) {
    const p = `sr40-3${tag}`;
    assert.deepEqual(outcome(await push3([
      v3('products', p, tea3),
      v3('warehouseEntries', `${p}-in`, { productId: p, unitsMilli: 48_000, priceMinor: 100 }),
    ])), ['applied', 'applied']);
    assert.deepEqual(outcome(await push3([v3('products', p, { unit: odd }, 'update')])), [R.LOCKED], tag);
    assert.equal(await unitOf('sync_v3_rows', p), 'دانه', tag);
  }
});

test('SR40-1: v1، پرچمِ روشن ⇒ عدد، آرایه و `$inc` روی واحدِ کالای گردش‌دار رد؛ بقیهٔ دسته سرِ جایش', async () => {
  await live.set(shopId, R.FLAG, true);
  for (const [tag, odd] of [['n', 7], ['i', { $inc: 1 }], ['a', ['کیلو']]]) {
    const p = `sr40-1${tag}`;
    assert.deepEqual(outcome(await push1([
      v1('products', p, tea1),
      v1('warehouseEntries', `${p}-in`, { productId: p, units: 48, price: 1 }),
    ])), ['applied', 'applied']);
    assert.deepEqual(outcome(await push1([
      v1('products', p, { unit: odd }, 'update'),
      v1('products', p, { salePrice: 60 }, 'update'),
    ])), [R.LOCKED, 'applied'], tag);
    assert.equal(await unitOf('sync_rows', p), 'دانه', tag);
  }
  //  کالای بی گردش همان آزادیِ پیشین
  assert.deepEqual(outcome(await push1([v1('products', 'sr40-1free', tea1), v1('products', 'sr40-1free', { unit: 7 }, 'update')])),
    ['applied', 'applied']);
  await live.remove(shopId, R.FLAG);
});

// ---------------------------------------------------------------- SR40-2

test('SR40-2: NUL در پرسشِ همگام‌سازیِ v3 (فقط‌دکان) ⇒ ۴۰۰ bad_query، نه ۵۰۰؛ v1 و راهِ قدیم (مشترک با پمپ) همان پیشین', async () => {
  const sync = { token: owner.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'sr40-dev' } };
  for (const p of ['/api/sync/v3/status?device_id=%00', '/api/sync/V3/status?device_id=a%00b', '/api/v1/sync/v3/status?deviceId=%00',
    '/api/sync/v3/status/?device_id=%00', '/api/sync/v3/pull?device_id=sr40&since=%00']) {
    const r = await h.api('GET', p, sync);
    assert.deepEqual([r.status, r.body?.error?.code], [400, 'bad_query'], `${p} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 160)}`);
  }
  //  `%2500` متنِ «%00» است، نه NUL
  assert.equal((await h.api('GET', '/api/sync/v3/status?device_id=a%2500', sync)).status, 200);
  for (const p of ['/api/sync/v1/status?x=%00', '/api/sync/status?x=%00']) {
    const r = await h.api('GET', p, sync);
    assert.equal(r.status, 200, `${p} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 160)}`);
  }
});

// ---------------------------------------------------------------- SR40-3

async function liveStore() {
  const u = await h.newUser(`ویترینِ ${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ قیف' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `sr40-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.orders.online_report']) await live.set(s.shopId, k, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: 'ویترینِ قیف' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return s;
}
const beat = (s, step, ua, site) => h.post(`/api/store/${s.slug}/funnel/${step}`, undefined, {
  headers: { 'User-Agent': ua, ...(site ? { 'Sec-Fetch-Site': site } : {}) },
});
const visits = async (s) => (await one('SELECT COALESCE(SUM(visits),0)::int AS n FROM storefront_funnel_days WHERE shop_id=$1', [s.shopId])).n;
const seen = async (s) => (await one('SELECT COUNT(*)::int AS n FROM storefront_funnel_seen WHERE shop_id=$1', [s.shopId])).n;

test('SR40-3: تپشِ میان‌سایتی (`Sec-Fetch-Site: cross-site`) همان ۲۰۴ ولی هیچ شمارش و هیچ ردیف؛ ویترینِ خودش و ابزارِ بی سرآیند همان', async () => {
  const s = await liveStore();
  for (let i = 0; i < 5; i++) {
    const r = await beat(s, i % 2 ? 'cart' : 'visit', `Mozilla/5.0 victim-${i}`, 'cross-site');
    assert.equal(r.status, 204, JSON.stringify(r.body));
  }
  assert.equal(await visits(s), 0, 'بازدیدِ ساختگی از سایتِ دیگر');
  assert.equal(await seen(s), 0);
  //  ویترینِ خودش (هم‌مبدأ) و زیرِدامنهٔ همان سایت شمرده می‌شوند؛ نبودِ سرآیند (مرورگرِ کهنه) هم
  assert.equal((await beat(s, 'visit', 'Mozilla/5.0 same-origin', 'same-origin')).status, 204);
  assert.equal((await beat(s, 'visit', 'Mozilla/5.0 same-site', 'same-site')).status, 204);
  assert.equal((await beat(s, 'visit', 'Mozilla/5.0 old-browser')).status, 204);
  assert.equal(await visits(s), 3);
});
