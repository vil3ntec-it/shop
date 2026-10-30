'use strict';
/**
 * S10 (docs/MARKETPLACE-CONTRACT-fa.md §۳) — جای فیلدهای آیندهٔ بازار روی سفارش، مهاجرتِ ۱۳۰.
 *
 *   ۱) ستون‌ها با پیش‌فرضِ بی‌اثر: سبد NULL، کارمزد و کمیسیون ۰، `channel` ''.
 *   ۲) سفارشِ تازهٔ ویترین ⇒ `channel = 'storefront'`؛ سفارشِ کارمند ⇒ تهی؛ بدنهٔ درخواست
 *      نمی‌تواند هیچ‌کدام را بگذارد (کلاینت قابلِ اعتماد نیست).
 *   ۳) شکلِ پاسخ‌های امروز (کارت، جزئیات، پیگیریِ مشتری) دست نخورده: هیچ فیلدِ تازه‌ای بیرون نمی‌رود.
 *   ۴) قیدها: `channel` فقط storefront | market_app | api:<appId> | ''؛ مبلغِ منفی رد.
 *   ۵) گزارش‌ها و جمع‌های امروز (شمارشِ برگه‌ها، تطبیقِ دریافت) با پرکردنِ این ستون‌ها عوض نمی‌شوند.
 *   ۶) برگشت: همان خط‌های `rollback:` ِ خودِ فایلِ مهاجرت اجرا می‌شوند؛ داده و جمع‌ها دست نمی‌خورند؛
 *      اجرای دوباره ستون‌ها را با پیش‌فرض روی ردیف‌های پیشین برمی‌گرداند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const migrate = require('../src/migrate');

const MIGRATION = '130_order_market_fields.sql';
const NEW_COLS = ['market_order_group_id', 'platform_fee_minor', 'commission_minor', 'channel'];
const NEW_KEYS = ['channel', 'marketOrderGroupId', 'platformFeeMinor', 'commissionMinor',
  'market_order_group_id', 'platform_fee_minor', 'commission_minor'];

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکان با ویترینِ روشن و یک کالای منتشرشده. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `t-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-s10' },
    body: { device_id: 'dev-s10', schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const r = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

/** سفارشِ مشتری از درِ ویترین — بدنه عمداً فیلدهای بازار را هم می‌فرستد. */
async function storefrontOrder(s, phone) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
    channel: 'api:evil', platformFeeMinor: 999, commissionMinor: 999, marketOrderGroupId: 'g-1',
  }, { headers: { 'Idempotency-Key': `s10-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

/** سفارشِ دستیِ کارمند — همان بدنهٔ اضافه. */
async function staffOrder(s) {
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
    channel: 'market_app', platform_fee_minor: 5, commission_minor: 5, market_order_group_id: 'g-2',
  }, { token: s.token, headers: { 'Idempotency-Key': `s10-staff-${uniq()}` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

const marketCols = (id) => one(
  'SELECT market_order_group_id, platform_fee_minor, commission_minor, channel FROM online_orders WHERE id=$1', [id]);
const inert = (channel) => ({ market_order_group_id: null, platform_fee_minor: 0, commission_minor: 0, channel });
const norm = (r) => ({ ...r, platform_fee_minor: Number(r.platform_fee_minor), commission_minor: Number(r.commission_minor) });

function noNewKeys(obj, where) {
  for (const k of NEW_KEYS) assert.ok(!(k in obj), `${where}: «${k}» نباید بیرون برود`);
}

/** «گزارش‌های امروز» برای یک دکان: شمارشِ برگه‌ها و تطبیقِ دریافت. */
const WIN = { from: Date.now() - 3_600_000, to: Date.now() + 3_600_000 };
async function reports(s) {
  const sum = await h.get('/api/shop/orders/summary', { token: s.token });
  assert.equal(sum.status, 200, JSON.stringify(sum.body));
  const rec = await h.get(`/api/shop/orders/reconciliation?from=${WIN.from}&to=${WIN.to}`, { token: s.token });
  assert.equal(rec.status, 200, JSON.stringify(rec.body));
  const { serverTime, ...counts } = sum.body;
  const { serverTime: _t, ...recon } = rec.body;
  return { counts, recon };
}

/** دادهٔ کاملِ ستون‌های امروزِ سفارش‌های یک دکان (بی ستون‌های تازه). */
async function snapshot(shopId) {
  const rows = await many('SELECT * FROM online_orders WHERE shop_id=$1 ORDER BY id', [shopId]);
  return rows.map((r) => { const c = { ...r }; for (const k of NEW_COLS) delete c[k]; return c; });
}

async function deliverPickup(s, id) {
  for (const [to, extra] of [['accepted', { from: 'new', readyInMinutes: 5 }], ['picking'], ['packed'], ['ready_for_pickup'], ['picked_up']]) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...(extra || {}) }, { token: s.token });
    assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
  }
}

test('ستون‌ها با پیش‌فرضِ بی‌اثر ساخته شده‌اند', async () => {
  const cols = await many(
    `SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns
      WHERE table_name = 'online_orders' AND column_name = ANY($1) ORDER BY column_name`, [NEW_COLS]);
  const by = Object.fromEntries(cols.map((c) => [c.column_name, c]));
  assert.deepEqual(Object.keys(by).sort(), [...NEW_COLS].sort());
  assert.equal(by.market_order_group_id.is_nullable, 'YES');
  assert.equal(by.market_order_group_id.column_default, null);
  for (const k of ['platform_fee_minor', 'commission_minor']) {
    assert.equal(by[k].data_type, 'bigint');
    assert.equal(by[k].is_nullable, 'NO');
    assert.equal(String(by[k].column_default), '0');
  }
  assert.equal(by.channel.is_nullable, 'NO');
  assert.match(String(by.channel.column_default), /^''/);
});

test('سفارشِ ویترین ⇒ channel = storefront؛ کارمند ⇒ تهی؛ بدنه هیچ‌کدام را نمی‌گذارد؛ پاسخ‌ها همان شکل', async () => {
  const s = await liveStore('کانال');
  const sf = await storefrontOrder(s, '0791110001');
  const st = await staffOrder(s);
  const sfRow = await one('SELECT id FROM online_orders WHERE shop_id=$1 AND source=$2', [s.shopId, 'storefront']);
  assert.deepEqual(norm(await marketCols(sfRow.id)), inert('storefront'));
  assert.deepEqual(norm(await marketCols(st.id)), inert(''));

  //  شکلِ پاسخ‌ها: جزئیاتِ کارمند، فهرست، پیگیریِ مشتری
  noNewKeys(sf, 'پاسخِ ثبتِ ویترین');
  noNewKeys(st, 'پاسخِ ثبتِ کارمند');
  const d = await h.get(`/api/shop/orders/${sfRow.id}`, { token: s.token });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  noNewKeys(d.body.order, 'جزئیات');
  const list = await h.get('/api/shop/orders?tab=new', { token: s.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.ok(list.body.items.length >= 2);
  for (const c of list.body.items) noNewKeys(c, 'کارت');
  const track = await h.get(`/api/store/${s.slug}/orders/${sf.token}`);
  assert.equal(track.status, 200, JSON.stringify(track.body));
  noNewKeys(track.body.order, 'پیگیری');

  //  تکرارِ همان کلید چیزی را عوض نمی‌کند
  const before = await marketCols(st.id);
  const again = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'نوشابه', qtyMilli: 2000, unitPriceMinor: 1250 }],
    channel: 'market_app', platform_fee_minor: 5, commission_minor: 5, market_order_group_id: 'g-2',
  }, { token: s.token, headers: { 'Idempotency-Key': (await one('SELECT client_key FROM online_orders WHERE id=$1', [st.id])).client_key } });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.deepEqual(await marketCols(st.id), before);
});

test('قیدها: فقط درهای شناخته‌شده و مبلغِ نامنفی', async () => {
  const s = await liveStore('قید');
  const o = await staffOrder(s);
  for (const ok of ['storefront', 'market_app', 'api:app_1', 'api:a.b-C9', '']) {
    await query('UPDATE online_orders SET channel=$2 WHERE id=$1', [o.id, ok]);
  }
  for (const bad of ['web', 'api:', 'api:با فاصله', 'API:x', 'storefront ', `api:${'x'.repeat(65)}`]) {
    await assert.rejects(query('UPDATE online_orders SET channel=$2 WHERE id=$1', [o.id, bad]), /check|ck_online_orders_channel/i, bad);
  }
  await assert.rejects(query('UPDATE online_orders SET platform_fee_minor=-1 WHERE id=$1', [o.id]), /check/i);
  await assert.rejects(query('UPDATE online_orders SET commission_minor=-1 WHERE id=$1', [o.id]), /check/i);
  await assert.rejects(query('UPDATE online_orders SET channel=NULL WHERE id=$1', [o.id]), /null/i);
});

test('گزارش‌ها و جمع‌های امروز با پرشدنِ این ستون‌ها عوض نمی‌شوند', async () => {
  const s = await liveStore('گزارش');
  await storefrontOrder(s, '0791110002');
  const a = await staffOrder(s);
  const b = await staffOrder(s);
  await deliverPickup(s, a.id);
  const base = await reports(s);
  assert.deepEqual(base.recon.collected, { count: 1, totalMinor: 2500 }, 'تطبیق چیزی برای سنجیدن دارد');
  const totals = await many('SELECT id, subtotal_minor, discount_minor, delivery_fee_minor, total_minor FROM online_orders WHERE shop_id=$1 ORDER BY id', [s.shopId]);

  //  آینده: کارمزد، کمیسیون، سبد و درِ دیگر روی همین سفارش‌ها
  await query(`UPDATE online_orders SET platform_fee_minor=700, commission_minor=300, market_order_group_id='grp-1',
                 channel='market_app' WHERE shop_id=$1`, [s.shopId]);
  assert.deepEqual(await reports(s), base);
  assert.deepEqual(await many('SELECT id, subtotal_minor, discount_minor, delivery_fee_minor, total_minor FROM online_orders WHERE shop_id=$1 ORDER BY id', [s.shopId]), totals);
  const d = (await h.get(`/api/shop/orders/${b.id}`, { token: s.token })).body.order;
  assert.equal(d.totalMinor, b.totalMinor);
  assert.equal(d.subtotalMinor, b.subtotalMinor);
});

//  ⛔ آخرین آزمونِ این پرونده: طرح را برمی‌گرداند و دوباره می‌سازد.
test('برگشت: خط‌های rollback ِ فایل اجرا می‌شوند، داده و گزارش دست نمی‌خورد؛ اجرای دوباره پیش‌فرض را برمی‌گرداند', async () => {
  const s = await liveStore('برگشت');
  await storefrontOrder(s, '0791110003');
  const a = await staffOrder(s);
  await deliverPickup(s, a.id);
  const base = await reports(s);
  const snap = await snapshot(s.shopId);

  const sql = fs.readFileSync(path.join(__dirname, '..', 'migrations', MIGRATION), 'utf8');
  const lines = sql.split('\n').map((l) => l.match(/^--\s+rollback:\s+(.+)$/)).filter(Boolean).map((m) => m[1]);
  assert.equal(lines.length, 6, 'هر ستون، قید و ردیفِ schema_migrations برگشت دارد');
  for (const l of lines) await query(l);

  const left = await many(
    `SELECT column_name FROM information_schema.columns WHERE table_name='online_orders' AND column_name = ANY($1)`, [NEW_COLS]);
  assert.deepEqual(left, [], 'ستون‌ها رفتند');
  assert.equal(await one('SELECT version FROM schema_migrations WHERE version=$1', [MIGRATION]), null);
  assert.deepEqual(await snapshot(s.shopId), snap, 'دادهٔ امروز دست نخورد');
  assert.deepEqual(await reports(s), base, 'گزارش‌ها همان');

  //  ردیفی که پیش از مهاجرت ساخته شده باشد (همان INSERT ِ بی ستون‌های تازه)
  const old = await one('SELECT * FROM online_orders WHERE id=$1', [a.id]);
  const oldId = `OLD${uniq()}`.toUpperCase().slice(0, 26);
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, fulfillment, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'h','storefront','pickup',$5,$5)`,
    [oldId, s.shopId, Number(old.order_no) + 100, `old-${uniq()}`, now()]);

  //  اجرای دوباره (همان راهِ بالا آمدنِ سرور)
  const done = await migrate.run();
  assert.deepEqual(done, [MIGRATION]);
  assert.deepEqual(norm(await marketCols(oldId)), inert(''), 'ردیفِ پیشین ⇒ پیش‌فرضِ بی‌اثر، حتی ویترینی');
  assert.deepEqual(norm(await marketCols(a.id)), inert(''));
  await query('DELETE FROM online_orders WHERE id=$1', [oldId]);
  assert.deepEqual(await snapshot(s.shopId), snap);
  assert.deepEqual(await reports(s), base);
  //  و سفارشِ تازه دوباره کار می‌کند
  await storefrontOrder(s, '0791110004');
  assert.equal(await migrate.run().then((x) => x.length), 0, 'اجرای سوم هیچ');
});
