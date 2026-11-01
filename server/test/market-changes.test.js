'use strict';
/**
 * جریانِ تغییرِ بازار — S6 (`lib/market-changes.js`، جدولِ `market_changes`، پرچمِ `market.changes`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی پرچم هیچ ردیفی نوشته نمی‌شود و کشِ بازار همان کشِ زمانیِ امروز است
 *      (قیمتِ تازه تا پایانِ `feedCacheMs` دیده نمی‌شود).
 *   ۲) تغییرِ قیمت ⇒ دقیقاً یک ردیف ⇒ کشِ قفسه و برشِ بازار همان دم تازه (با `seq`، نه زمان).
 *   ۳) ⛔ تکرارِ همان درخواست (قیمت، تنظیم، حضور با eventId ِ تازه، فهرست) ردیفِ دوم نمی‌سازد؛
 *      برگشت به حالتِ پیشین (A ⇒ B ⇒ A) ردیفِ تازه دارد.
 *   ۴) انتشارِ کالای تازه ⇒ نمایهٔ جست‌وجوی بازار همان دم پیدایش می‌کند.
 *   ۵) پروفایل، حضور، فهرست و جریمه (گذاشتن/برداشتن) هر کدام ردیف دارند؛ ردیف فقط با
 *      تراکنشِ کامل (خطا ⇒ بی ردیف).
 *   ۶) نگهداری: `prune` کهنه‌ها را پاک می‌کند ولی آخرین ردیف را نگه می‌دارد.
 */
process.env.MARKET_GATE_CACHE_MS = '600000';
process.env.MARKET_ELIGIBLE_CACHE_MS = '600000';
process.env.MARKET_FEED_CACHE_MS = '600000';
process.env.MARKET_CHANGES_POLL_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now, tx } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین و فهرستِ بازارچه؛ `products` = [شناسه, نام, قیمت, منتشر؟]. */
async function liveStore(label, { products, changes = false } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (changes) await setFlag(o.shopId, 'market.changes', true);
  const slug = `c-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const ops = products.map(([id, name, price]) => ({
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: price, category: 'خوراکی' },
  }));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const [id, , , pub] of products) {
    if (pub === false) continue;
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const r = await h.put('/api/shop/market', { listed: true, category: 'grocery' }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug, device };
}

const rowsOf = (shopId) => many('SELECT seq, entity, entity_id, kind FROM market_changes WHERE shop_id = $1 ORDER BY seq', [shopId]);
const feedIds = async (slug) => {
  const r = await h.get('/api/market/products');
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.filter((p) => p.shop.slug === slug).map((p) => p.id).sort();
};
const setProduct = (s, id, body) => h.put(`/api/shop/storefront/products/${id}`, body, { token: s.token });

test('⛔ پرچم خاموش: هیچ ردیفی نیست و قفسهٔ بازار تا پایانِ کشِ زمانی کهنه می‌ماند', async () => {
  await setFlag('all', 'market.directory', true);
  //  «مفت» قیمت ندارد ⇒ در قفسه نیست
  const s = await liveStore('خاموش', { products: [['k1', 'آرد', 100], ['k2', 'مفت', 0]] });
  assert.deepEqual(await feedIds(s.slug), ['k1']);
  const r = await setProduct(s, 'k2', { published: true, onlinePriceMinor: 5000 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(await rowsOf(s.shopId), [], 'بی پرچم ردیفی نوشته نمی‌شود');
  assert.deepEqual(await feedIds(s.slug), ['k1'], 'رفتارِ امروز: قفسه تا feedCacheMs کهنه است');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('تغییرِ قیمت ⇒ یک ردیف ⇒ کشِ بازار همان دم تازه؛ تکرارِ درخواست بی ردیفِ دوم', async () => {
  await setFlag('all', 'market.directory', true);
  const s = await liveStore('روشن', { products: [['p1', 'برنج', 120], ['p2', 'نمک', 0]], changes: true });
  assert.deepEqual(await feedIds(s.slug), ['p1'], 'کالای بی‌قیمت در قفسه نیست');
  const market = require('../src/lib/market');
  const before = (await rowsOf(s.shopId)).length;
  const built = market.stats();

  const r = await setProduct(s, 'p2', { published: true, onlinePriceMinor: 2500 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const after = await rowsOf(s.shopId);
  assert.equal(after.length, before + 1, 'دقیقاً یک ردیف');
  const last = after[after.length - 1];
  assert.deepEqual({ entity: last.entity, entity_id: last.entity_id, kind: last.kind }, { entity: 'product', entity_id: 'p2', kind: 'price' });

  const feed = await h.get('/api/market/products');
  assert.deepEqual(feed.body.items.filter((p) => p.shop.slug === s.slug).map((p) => p.id).sort(), ['p1', 'p2'], 'کشِ قفسه با seq تازه شد');
  assert.equal(feed.body.items.find((p) => p.id === 'p2' && p.shop.slug === s.slug).priceMinor, 2500);
  const st = market.stats();
  assert.ok(st.changes > built.changes, 'ردیف خوانده شد');
  assert.ok(st.builds > built.builds, 'برش از نو ساخته شد');

  //  ⛔ تکرارِ همان درخواست
  for (let i = 0; i < 3; i++) assert.equal((await setProduct(s, 'p2', { published: true, onlinePriceMinor: 2500 })).status, 200);
  assert.equal((await rowsOf(s.shopId)).length, before + 1, 'تکرار ردیفِ دوم نمی‌سازد');
  const quiet = market.stats();
  await h.get('/api/market/products');
  assert.equal(market.stats().builds, quiet.builds, 'بی ردیفِ تازه برش از نو ساخته نمی‌شود');

  //  A ⇒ B ⇒ A هر بار ردیف
  await setProduct(s, 'p2', { published: true, onlinePriceMinor: 3000 });
  await setProduct(s, 'p2', { published: true, onlinePriceMinor: 2500 });
  const kinds = (await rowsOf(s.shopId)).slice(before).map((x) => x.kind);
  assert.deepEqual(kinds, ['price', 'price', 'price']);
  //  برداشتن
  await setProduct(s, 'p2', { published: false, onlinePriceMinor: 2500 });
  assert.equal((await rowsOf(s.shopId)).pop().kind, 'unpublish');
  assert.deepEqual(await feedIds(s.slug), ['p1']);
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('انتشارِ کالای تازه ⇒ نمایهٔ جست‌وجوی بازار همان دم (برشِ ۱۰ دقیقه‌ای منتظر نمی‌ماند)', async () => {
  await setFlag('all', 'market.directory', true);
  const s = await liveStore('جست‌وجو', { products: [['q1', 'زعفران', 900], ['q2', 'هیلِ سبز', 400, false]], changes: true });
  const find = async (q) => (await h.get(`/api/market/search?q=${encodeURIComponent(q)}`)).body.products
    .filter((p) => p.shop.slug === s.slug).map((p) => p.id);
  assert.deepEqual(await find('زعفران'), ['q1']);
  assert.deepEqual(await find('هیل'), [], 'منتشرنشده');
  assert.equal((await setProduct(s, 'q2', { published: true })).status, 200);
  assert.equal((await rowsOf(s.shopId)).pop().kind, 'publish');
  assert.deepEqual(await find('هیل'), ['q2'], 'نمایه با seq تازه شد');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('پروفایل، حضور و فهرست هر کدام ردیف دارند؛ تکرار بی ردیف', async () => {
  const s = await liveStore('پروفایل', { products: [['r1', 'چای', 50]], changes: true });
  const count = async (entity) => (await rowsOf(s.shopId)).filter((x) => x.entity === entity).length;
  const shop0 = await count('shop');
  assert.equal((await h.put('/api/shop/storefront', { title: 'نامِ تازه' }, { token: s.token })).status, 200);
  assert.equal((await h.put('/api/shop/storefront', { title: 'نامِ تازه' }, { token: s.token })).status, 200);
  assert.equal(await count('shop'), shop0 + 1);

  const pres = (eventId) => h.post('/api/shop/storefront/presence', { eventId, mode: 'offline' }, { token: s.token });
  assert.equal((await pres(`ev-${uniq()}-a`)).status, 200);
  assert.equal((await pres(`ev-${uniq()}-b`)).status, 200, 'رویدادِ تازه با همان حالت');
  assert.equal(await count('presence'), 1);
  assert.equal((await h.post('/api/shop/storefront/presence', { eventId: `ev-${uniq()}-c`, mode: 'auto' }, { token: s.token })).status, 200);
  assert.equal(await count('presence'), 2);

  const l0 = await count('listing');
  assert.equal((await h.put('/api/shop/market', { area: 'کارته ۴' }, { token: s.token })).status, 200);
  assert.equal((await h.put('/api/shop/market', { area: 'کارته ۴' }, { token: s.token })).status, 200);
  assert.equal(await count('listing'), l0 + 1);
  const txt = JSON.stringify(await many('SELECT * FROM market_changes WHERE shop_id = $1', [s.shopId]));
  assert.ok(!txt.includes('0700000001') && !txt.includes('کارته'), 'جدول دادهٔ خام ندارد، فقط امضا');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('جریمه: گذاشتن و برداشتن هر کدام یک ردیف؛ خطای تراکنش ⇒ بی ردیف', async () => {
  const s = await liveStore('جریمه', { products: [['t1', 'روغن', 300]], changes: true });
  const trust = require('../src/lib/market-trust');
  const caseId = `tc_${'a'.repeat(23)}${seq % 10}`;
  await query(
    `INSERT INTO trust_cases (id, shop_id, kind, subject, product_id, category, severity, reporter_key, created_at, updated_at)
     VALUES ($1,$2,'report','product','t1','fake','medium','rk',$3,$3)`,
    [caseId, s.shopId, now()]
  );
  const out = await trust.decide('adm_x', caseId, { outcome: 'upheld', sanction: { kind: 'hide_listing', productId: 't1' } }, 'آزمون');
  let rows = (await rowsOf(s.shopId)).filter((x) => x.entity === 'sanction');
  assert.deepEqual(rows.map((x) => [x.entity_id, x.kind]), [[out.sanction.id, 'impose']]);
  await trust.liftSanction('adm_x', out.sanction.id, 'آزمون');
  await assert.rejects(trust.liftSanction('adm_x', out.sanction.id, 'دوباره'), (e) => e.code === 'already_lifted');
  rows = (await rowsOf(s.shopId)).filter((x) => x.entity === 'sanction');
  assert.deepEqual(rows.map((x) => x.kind), ['impose', 'lift']);

  //  ردیف فقط با تراکنشِ کامل
  const mc = require('../src/lib/market-changes');
  const n = (await rowsOf(s.shopId)).length;
  await assert.rejects(tx(async (c) => {
    assert.ok(await mc.record(c, s.shopId, { entity: 'shop', kind: 'update', state: { x: uniq() } }));
    throw new Error('rollback');
  }), /rollback/);
  assert.equal((await rowsOf(s.shopId)).length, n, 'برگشتِ تراکنش ردیف را هم برمی‌گرداند');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('همگام‌سازیِ کاتالوگ: قیمتِ تازهٔ دفتر یک ردیف، همان داده دوباره بی ردیف', async () => {
  const s = await liveStore('کاتالوگ', { products: [['g1', 'شکر', 80]], changes: true });
  const catalog = require('../src/lib/catalog');
  const productRows = async () => (await rowsOf(s.shopId)).filter((x) => x.entity === 'product' && x.entity_id === 'g1');
  await catalog.refresh(s.shopId, { throttle: false, full: true });
  const n0 = (await productRows()).length;
  await catalog.refresh(s.shopId, { throttle: false, full: true });
  assert.equal((await productRows()).length, n0, 'همان داده ⇒ بی ردیف');
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device },
    body: { device_id: s.device, schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'g1', type: 'update', fields: { salePrice: 95 } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  await catalog.refresh(s.shopId, { throttle: false });
  assert.equal((await productRows()).length, n0 + 1, 'قیمتِ تازه ⇒ یک ردیف');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
});

test('نگهداری: prune کهنه‌ها را پاک می‌کند و آخرین ردیف را نگه می‌دارد', async () => {
  const mc = require('../src/lib/market-changes');
  const s = await liveStore('نگهداری', { products: [['n1', 'نخود', 70]], changes: true });
  await query('UPDATE market_changes SET at = 1');
  const head = await mc.head();
  assert.ok(head > 0);
  const out = await mc.prune({ keepMs: 1000 });
  assert.ok(out.removed > 0);
  const left = await many('SELECT seq FROM market_changes');
  assert.deepEqual(left.map((r) => Number(r.seq)), [head], 'فقط آخرین ردیف');
  assert.equal(await mc.head(), head, 'seq پس از پاک‌سازی برنمی‌گردد');
  const fresh = await tx((c) => mc.record(c, s.shopId, { entity: 'shop', kind: 'update', state: { y: uniq() } }));
  assert.ok(fresh > head);
  assert.equal((await mc.prune({ keepMs: 60_000 })).removed, 1, 'کهنهٔ نگه‌داشته حالا که آخرین نیست می‌رود');
  assert.deepEqual((await many('SELECT seq FROM market_changes')).map((r) => Number(r.seq)), [fresh], 'تازه‌ها می‌مانند');
  assert.equal((await mc.prune({ keepMs: 60_000 })).removed, 0);
});
