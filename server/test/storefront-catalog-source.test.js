'use strict';
/**
 * S1 ِ قراردادِ بازار (`docs/MARKETPLACE-CONTRACT-fa.md` بخشِ ۳) — ویترین و بازار از کاتالوگِ واحد.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `storefront.catalog_source` (فقط `all`) پیش‌فرض خاموش ⇒ همان `sync_rows` ِ
 *      همیشگی؛ دکانِ کوچ‌کرده به v3 کالایی در ویترین ندارد (همان شکافِ امروز).
 *   ۲) روشن: یک دکانِ v1 و یک دکانِ v3 همان فهرست و همان قیمت را در ویترین، فهرستِ صاحب
 *      دکان و جست‌وجوی بازار می‌دهند؛ ثبتِ سفارش با همان قاعدهٔ «زنده» (منتشرشده، حذف‌نشده،
 *      فعال، قیمت‌دار) و با قیمتِ تازهٔ دفتر.
 *   ۳) فقط بخشِ کالا: بی `shop.catalog` هیچ حرکتِ موجودی و هیچ آبراههٔ موجودی نوشته نمی‌شود.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
process.env.CATALOG_REFRESH_THROTTLE_MS = '0';
process.env.RATE_STORE_CHECKOUT_MAX = '50';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const hlc = require('../src/lib/hlc');

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

// ── دو دفترِ همگام‌سازی ─────────────────────────────────────────

const pushV1 = (o, products) => h.api('POST', '/api/sync/v1/push', {
  token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-v1' },
  body: {
    device_id: 'dev-v1', schema_version: 2,
    ops: products.map(([rowId, fields, type = 'insert']) => ({
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
    })),
  },
});

let tick = 0;
let opN = 0;
const H = () => hlc.format({ wall: Date.now() - 60_000 + (++tick) * 10, counter: 0, node: 'v3sf' });
const oid = () => `01JV3SFC${String(++opN).padStart(18, '0')}`;
const pushV3 = (o, products) => h.api('POST', '/api/sync/v3/push', {
  token: o.token,
  body: {
    device_id: 'dev-v3', protocol: 3,
    ops: products.map(([rowId, fields, kind = 'insert']) => ({ op_id: oid(), table: 'products', row_id: rowId, kind, hlc: H(), fields })),
  },
});

//  یک فهرست، در دو قالب: v1 اعشاری (`salePrice`)، v3 صحیح (`salePriceMinor`)
const LIST = [
  ['rice', 'برنج', 'کیلو', 'خوراکی', 12050, true],
  ['oil', 'روغن', 'بوتل', 'خوراکی', 30000, true],
  ['soap', 'صابون', 'دانه', 'بهداشتی', 4000, false],
];
const v1Fields = ([, name, unit, category, minor]) => ({ name, unit, category, salePrice: minor / 100 });
const v3Fields = ([, name, unit, category, minor]) => ({ name, unit, category, salePriceMinor: minor });

async function liveStore(label, fmt) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, fmt };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  o.slug = `c-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = fmt === 'v3'
    ? await pushV3(o, LIST.map((x) => [x[0], v3Fields(x)]))
    : await pushV1(o, LIST.map((x) => [x[0], v1Fields(x)]));
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const m = await h.put('/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, { token: o.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  return o;
}

const publishAll = async (o) => {
  const out = [];
  for (const [id, , , , , pub] of LIST) {
    if (pub) out.push(await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token }));
  }
  return out;
};

const shape = (items) => items.map((p) => ({ id: p.id, name: p.name, unit: p.unit, category: p.category, priceMinor: p.priceMinor }))
  .sort((a, b) => a.id.localeCompare(b.id));
const storeList = async (o) => {
  const r = await h.get(`/api/store/${o.slug}/products`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return shape(r.body.items);
};

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
const PHONE = '0791112233';
async function checkout(o, lines) {
  return h.post(`/api/store/${o.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ آزمایشی', phone: PHONE }, lines,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(o.slug, PHONE) } });
}

const EXPECTED = [
  { id: 'oil', name: 'روغن', unit: 'بوتل', category: 'خوراکی', priceMinor: 30000 },
  { id: 'rice', name: 'برنج', unit: 'کیلو', category: 'خوراکی', priceMinor: 12050 },
];

/* ══════════════════════════════════════════════════════════════════ */

test('پرچم خاموش: همان sync_rows ِ همیشگی — دکانِ v3 کالایی برای انتشار ندارد', async () => {
  await setFlag('all', 'market.directory', true);
  const a = await liveStore('یک-خاموش', 'v1');
  const b = await liveStore('سه-خاموش', 'v3');
  for (const r of await publishAll(a)) assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(await storeList(a), EXPECTED);
  //  شکافِ امروز (همان که S1 می‌بندد): کالای v3 در `sync_rows` نیست
  for (const r of await publishAll(b)) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'product_not_found');
  }
  assert.deepEqual(await storeList(b), []);
  //  پرچم خاموش ⇒ کاتالوگ برای ویترین پر نمی‌شود
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM catalog_products WHERE shop_id = ANY($1::text[])', [[a.shopId, b.shopId]])).n, 0);
  await setFlag('all', 'market.directory', false);
});

test('پرچم روشن: دکانِ v1 و دکانِ v3 همان فهرست و قیمت در ویترین، فهرستِ صاحب و جست‌وجوی بازار', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'storefront.catalog_source', true);
  const a = await liveStore('یک', 'v1');
  const b = await liveStore('سه', 'v3');
  for (const o of [a, b]) for (const r of await publishAll(o)) assert.equal(r.status, 200, `${o.fmt} ${JSON.stringify(r.body)}`);

  assert.deepEqual(await storeList(a), EXPECTED);
  assert.deepEqual(await storeList(b), EXPECTED);

  //  دسته‌ها، صفحهٔ کالا و فهرستِ صاحب دکان هم از همان کاتالوگ
  for (const o of [a, b]) {
    const store = await h.get(`/api/store/${o.slug}`);
    assert.equal(store.status, 200, JSON.stringify(store.body));
    assert.deepEqual(store.body.store.categories, ['خوراکی'], o.fmt);
    const d = await h.get(`/api/store/${o.slug}/products/rice`);
    assert.equal(d.status, 200, JSON.stringify(d.body));
    assert.equal(d.body.product.priceMinor, 12050);
    const staff = await h.get('/api/shop/storefront/products', { token: o.token });
    assert.equal(staff.status, 200, JSON.stringify(staff.body));
    assert.deepEqual(staff.body.items.map((p) => [p.id, p.published, p.priceMinor]).sort(),
      [['oil', true, 30000], ['rice', true, 12050], ['soap', false, 4000]], o.fmt);
  }

  const s = await h.get(`/api/market/search?q=${encodeURIComponent('برن')}`);
  assert.equal(s.status, 200, JSON.stringify(s.body));
  //  (دکانِ v1 ِ آزمونِ پیشین هم در بازار است؛ فقط همین دو دکان سنجیده می‌شوند)
  const mine = s.body.products.filter((p) => [a.slug, b.slug].includes(p.shop.slug));
  assert.deepEqual(mine.map((p) => p.shop.slug).sort(), [a.slug, b.slug].sort());
  assert.ok(mine.every((p) => p.id === 'rice' && p.priceMinor === 12050 && p.name === 'برنج' && p.unit === 'کیلو'), JSON.stringify(mine));
  const none = await h.get(`/api/market/search?q=${encodeURIComponent('صابون')}`);
  assert.equal(none.body.products.length, 0, 'کالای منتشرنشده هرگز');

  //  فقط بخشِ کالا: بی `shop.catalog` هیچ موجودی و هیچ آبراههٔ موجودی
  for (const o of [a, b]) {
    const src = await one('SELECT array_agg(DISTINCT source) AS s FROM catalog_products WHERE shop_id=$1', [o.shopId]);
    assert.deepEqual(src.s, [o.fmt]);
    assert.equal((await one('SELECT COUNT(*)::int AS n FROM stock_moves WHERE shop_id=$1', [o.shopId])).n, 0);
    assert.equal((await one('SELECT COUNT(*)::int AS n FROM catalog_sync_state WHERE shop_id=$1', [o.shopId])).n, 0);
  }

  //  ثبتِ سفارش: قیمتِ سرور از همان کاتالوگ، در هر دو دکان یکی
  for (const o of [a, b]) {
    const r = await checkout(o, [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }]);
    assert.equal(r.status, 201, `${o.fmt} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.order.totalMinor, 2 * 12050 + 30000, o.fmt);
  }
  await setFlag('all', 'storefront.catalog_source', false);
  await setFlag('all', 'market.directory', false);
});

test('پرچم روشن: قاعدهٔ «زنده» و قیمتِ تازه در هر دو دکان یکی — ویترین، بازار و ثبتِ سفارش', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'storefront.catalog_source', true);
  const a = await liveStore('یک-زنده', 'v1');
  const b = await liveStore('سه-زنده', 'v3');
  for (const o of [a, b]) for (const r of await publishAll(o)) assert.equal(r.status, 200, JSON.stringify(r.body));

  //  روغن غیرفعال، برنج گران‌تر — هر دکان در دفترِ خودش
  const w1 = await pushV1(a, [['oil', { active: false }, 'update'], ['rice', { salePrice: 130 }, 'update']]);
  assert.equal(w1.status, 200, JSON.stringify(w1.body));
  const w3 = await pushV3(b, [['oil', { active: false }, 'update'], ['rice', { salePriceMinor: 13000 }, 'update']]);
  assert.equal(w3.status, 200, JSON.stringify(w3.body));

  const want = [{ id: 'rice', name: 'برنج', unit: 'کیلو', category: 'خوراکی', priceMinor: 13000 }];
  assert.deepEqual(await storeList(a), want);
  assert.deepEqual(await storeList(b), want);
  const s = await h.get(`/api/market/search?q=${encodeURIComponent('روغن')}`);
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.ok(s.body.products.length > 0, 'روغنِ دکان‌های آزمونِ پیشین هنوز هست');
  assert.ok(!s.body.products.some((p) => [a.slug, b.slug].includes(p.shop.slug)), 'غیرفعال در بازار هم نیست');

  for (const o of [a, b]) {
    const off = await checkout(o, [{ productId: 'oil', qtyMilli: 1000 }]);
    assert.equal(off.status, 409, `${o.fmt} ${JSON.stringify(off.body)}`);
    assert.equal(off.body.error.code, 'product_unavailable');
    const ok = await checkout(o, [{ productId: 'rice', qtyMilli: 1000 }]);
    assert.equal(ok.status, 201, JSON.stringify(ok.body));
    assert.equal(ok.body.order.totalMinor, 13000, `${o.fmt}: قیمتِ تازهٔ دفتر، نه کهنهٔ کاتالوگ`);
  }

  //  حذف در دفتر ⇒ از ویترین و ثبت بیرون؛ برگرداندنِ پرچم ⇒ همان منبعِ v1 ِ پیشین
  assert.equal((await pushV3(b, [['rice', {}, 'delete']])).status, 200);
  assert.deepEqual(await storeList(b), []);
  assert.equal((await checkout(b, [{ productId: 'rice', qtyMilli: 1000 }])).status, 409);
  await setFlag('all', 'storefront.catalog_source', false);
  assert.deepEqual(await storeList(a), want);
  assert.deepEqual(await storeList(b), [], 'خاموش: دکانِ v3 در sync_rows کالا ندارد');
  await setFlag('all', 'market.directory', false);
});

test('پرچمِ دکان (نه all) منبع را عوض نمی‌کند — ویترین و بازار هرگز دو منبع نمی‌خوانند', async () => {
  const b = await liveStore('سه-دکان', 'v3');
  await setFlag(b.shopId, 'storefront.catalog_source', true);
  for (const r of await publishAll(b)) assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.deepEqual(await storeList(b), []);
});
