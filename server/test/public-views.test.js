'use strict';
/**
 * قراردادِ نمای عمومی (S2 در `docs/MARKETPLACE-CONTRACT-fa.md`؛ `lib/public-views.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) snapshot: فهرستِ سفیدِ فیلدها همان `core/test-vectors/market-public-v<نسخه>.json`
 *      است؛ افزودن/برداشتنِ هر فیلد بی بالا بردنِ `SCHEMA_VERSION` (و پروندهٔ تازه) سرخ.
 *      همان پرونده را آزمونِ `core` (`MarketContractTest`) هم می‌خواند.
 *   ۲) سازنده‌های امروز (`profile`، `presenceOf`، `productView`، `publicOrder`) از ورودیِ
 *      ثابتِ همان پرونده دقیقاً نمونهٔ آن را می‌سازند (کلید، مقدار و ترتیب).
 *   ۳) فیلدِ بیرون از فهرست هرگز به پاسخ نمی‌رسد، حتی اگر سازنده بگذاردش.
 *   ۴) پاسخ‌های واقعیِ `/api/store/*` و `/api/market/*` با همین شکل‌ها جورند و هیچ
 *      شناسهٔ داخلی، شمارهٔ مالک/مشتری، ایمیل یا مختصاتِ دقیقی در آن‌ها نیست.
 */
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const V = require('../src/lib/public-views');
const sf = require('../src/lib/storefront');

const VECTORS = path.join(__dirname, '..', '..', 'core', 'test-vectors');
const FILE = path.join(VECTORS, `market-public-v${V.SCHEMA_VERSION}.json`);
const C = JSON.parse(fs.readFileSync(FILE, 'utf8'));

/** شیء با شکل جور است: هیچ کلیدِ بیرون از فهرست، همهٔ کلیدهای همیشگی؛ تودرتو هم. */
function conforms(shape, obj, where = shape) {
  if (obj === null) return;
  assert.equal(typeof obj, 'object', `${where}: شیء نیست`);
  const { required, all } = V.keysOf(shape);
  for (const k of Object.keys(obj)) assert.ok(all.includes(k), `${where}: فیلدِ بیرون از قرارداد «${k}»`);
  for (const k of required) assert.ok(k in obj, `${where}: فیلدِ همیشگیِ «${k}» نیست`);
  for (const [k, sub] of Object.entries(V.NESTED[shape] || {})) {
    if (!(k in obj)) continue;
    const v = obj[k];
    if (Array.isArray(v)) v.forEach((x, i) => conforms(sub, x, `${where}.${k}[${i}]`));
    else conforms(sub, v, `${where}.${k}`);
  }
}

/* ══════════════════════════════════════════════════════════════════
   ۱) snapshot و نسخه
   ══════════════════════════════════════════════════════════════════ */

test('snapshot: فهرستِ فیلدها همان پروندهٔ همین نسخه است', () => {
  assert.equal(C.schemaVersion, V.SCHEMA_VERSION, 'نسخهٔ درونِ پرونده با نامش یکی است');
  //  ⛔ اگر این سرخ شد: فیلدی بی تغییرِ نسخه عوض شده. SCHEMA_VERSION را بالا ببر، پروندهٔ
  //  market-public-v<نسخهٔ تازه>.json بساز و مدل‌های core را هم‌گام کن — پروندهٔ کهنه دست نمی‌خورد.
  assert.deepEqual(V.FIELDS, C.fields);
  assert.deepEqual(Object.keys(V.NESTED).filter((s) => !V.FIELDS[s]), [], 'هر شکلِ تودرتو در فهرست هست');
  for (const subs of Object.values(V.NESTED)) {
    for (const s of Object.values(subs)) assert.ok(V.FIELDS[s], `شکلِ ${s}`);
  }
  //  نسخه‌های پیشین (اگر هستند) پاک نمی‌شوند و نسخهٔ آینده‌ای بی کد نیست
  const versions = fs.readdirSync(VECTORS).map((f) => /^market-public-v(\d+)\.json$/.exec(f)).filter(Boolean).map((m) => Number(m[1]));
  assert.equal(Math.max(...versions), V.SCHEMA_VERSION, 'پرونده‌ای با نسخهٔ بالاتر از کد نیست');
  for (let v = 1; v <= V.SCHEMA_VERSION; v++) assert.ok(versions.includes(v), `پروندهٔ نسخهٔ ${v}`);
});

test('آشکارساز: افزودن یا برداشتنِ یک فیلد بی تغییرِ نسخه با snapshot نمی‌خواند', () => {
  const added = JSON.parse(JSON.stringify(V.FIELDS));
  added.product.push('costPrice');
  assert.notDeepEqual(added, C.fields);
  const removed = JSON.parse(JSON.stringify(V.FIELDS));
  removed.order = removed.order.filter((k) => k !== 'zoneName');
  assert.notDeepEqual(removed, C.fields);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) سازنده‌های امروز ⇒ همان نمونه (بی تغییرِ پاسخ)
   ══════════════════════════════════════════════════════════════════ */

test('سازنده‌ها از ورودیِ ثابت دقیقاً نمونهٔ قرارداد را می‌سازند (کلید، مقدار، ترتیب)', () => {
  const I = C.inputs;
  const same = (got, want, label) => {
    assert.deepEqual(got, want, label);
    assert.equal(JSON.stringify(got), JSON.stringify(want), `${label}: ترتیبِ کلیدها`);
  };
  const status = sf.presenceOf(I.storefront, I.at, I.lastSeenAt);
  same(status, C.samples.presence, 'حضور');
  same(sf.profile(I.storefront, I.categories, status), C.samples.shop, 'دکان');
  same(sf.productView(I.productRow), C.samples.product, 'کالا');
  same(sf.publicOrder(I.order, I.orderToken), C.samples.order, 'سفارش');
  same(V.delivery({ id: 'dlv_internal', status: 'picked_up', mode: 'shop_courier', courier_id: 'c1', code_hash: 'x' }),
    C.samples.delivery, 'پیک');
});

test('هر نمونه با شکلِ خودش جور است و view چیزی از آن کم یا زیاد نمی‌کند', () => {
  const shapeOf = { productDetail: 'product' };
  for (const [name, sample] of Object.entries(C.samples)) {
    const shape = shapeOf[name] || name;
    for (const s of Array.isArray(sample) ? sample : [sample]) {
      conforms(shape, s, name);
      assert.deepEqual(V.view(shape, s), s, name);
    }
  }
});

test('فیلدِ بیرون از فهرست حتی اگر سازنده بگذارد، به پاسخ نمی‌رسد', () => {
  const p = V.view('product', { ...C.samples.product, costPrice: 90, shopId: 'shp_x', stock: { status: 'in_stock', costMinor: 1 } });
  assert.equal('costPrice' in p, false);
  assert.equal('shopId' in p, false);
  assert.deepEqual(p.stock, { status: 'in_stock' });
  const o = V.view('order', { ...C.samples.order, customerPhone: '0791112233', lines: [{ ...C.samples.order.lines[0], costMinor: 1 }] });
  assert.equal('customerPhone' in o, false);
  assert.equal('costMinor' in o.lines[0], false);
  assert.throws(() => V.view('nope', {}), /ناشناخته/);
  //  ورودی‌های نمونه عمداً فیلدِ داخلی دارند؛ هیچ‌کدام در نمای عمومی نیست
  const out = JSON.stringify(C.samples);
  for (const secret of ['shp_internal_do_not_publish', '0799999999', '0791112233', 'usr_internal', 'یادداشتِ کارمند',
    'یادداشتِ داخلی', 'نشانیِ کامل', '34.5312', '69.1661', 'dlv_internal', 'code_hash', 'costPrice', 'costMinor']) {
    assert.equal(out.includes(secret), false, `«${secret}» در نمای عمومی`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۳) پاسخ‌های واقعی
   ══════════════════════════════════════════════════════════════════ */

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

const FORBIDDEN_KEYS = new Set(['shop_id', 'shopId', 'account_id', 'accountId', 'owner_id', 'ownerId', 'user_id', 'userId',
  'actorId', 'actor_id', 'device_id', 'deviceId', 'customerPhone', 'customer_phone', 'phone', 'email', 'lat', 'lng',
  'geo_lat', 'geo_lng', 'location', 'costPrice', 'costMinor', 'staffNote', 'orderId', 'courierId', 'code_hash']);

function noLeak(body, secrets, where) {
  const walk = (v, p) => {
    if (Array.isArray(v)) return v.forEach((x, i) => walk(x, `${p}[${i}]`));
    if (v && typeof v === 'object') {
      for (const [k, x] of Object.entries(v)) {
        assert.equal(FORBIDDEN_KEYS.has(k), false, `${where}: کلیدِ داخلیِ «${k}» در ${p}`);
        walk(x, `${p}.${k}`);
      }
    }
  };
  walk(body, '$');
  const text = JSON.stringify(body);
  for (const s of secrets) assert.equal(text.includes(s), false, `${where}: «${s}» در پاسخ`);
}

test('پاسخ‌های /store و /market: شکلِ قرارداد، بی شناسهٔ داخلی، شماره یا مختصاتِ دقیق', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', V.FLAG, true);
  const u = await h.newUser('قرارداد', 'shop');
  const made = await h.post('/api/shop', { name: 'دکانِ قرارداد' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const token = u.accessToken;
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `pv-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', {
    slug, enabled: true, title: 'ویترینِ قرارداد', tagline: 'کالای روزانه', contactPhone: '0700000001', deliveryFeeMinor: 5000,
  }, { token })).status, 200);
  const device = `dev-${uniq()}`;
  const ops = [['rice', 'برنج', 120], ['oil', 'روغن', 300]].map(([id, name, price]) => ({
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
    fields: { name, unit: 'دانه', salePrice: price, costPrice: 77, category: 'خوراکی' },
  }));
  const pushed = await h.api('POST', '/api/sync/v1/push', {
    token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
  });
  assert.equal(pushed.status, 200, JSON.stringify(pushed.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true, description: 'برنجِ خوب' }, { token })).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 25000 }, { token })).status, 200);
  assert.equal((await h.put('/api/shop/market', {
    listed: true, category: 'grocery', area: 'شهرِ نو', showDeals: true, location: { lat: 34.5312, lng: 69.1661 },
  }, { token })).status, 200);

  const phone = '0791112233';
  const vs = await h.post('/api/market/verify/start', { phone });
  const vc = await h.post('/api/market/verify/check', { phone, code: vs.body.devCode });
  assert.equal(vc.status, 200, JSON.stringify(vc.body));
  const ticket = vc.body.ticket;
  const placed = await h.post(`/api/store/${slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `pv-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(placed.status, 201, JSON.stringify(placed.body));
  const orderToken = placed.body.order.token;
  const orderId = orderToken.split('.')[0];
  const T = { headers: { 'X-Store-Ticket': ticket } };

  const secrets = [shopId, u.user.id, u.email, phone, device, '34.5312', '69.1661'].filter(Boolean);
  const seen = new Map();
  const check = async (url, opts, shapes) => {
    const r = await h.get(url, opts);
    seen.set(url, { opts, body: r.body });
    assert.equal(r.status, 200, `${url}: ${JSON.stringify(r.body)}`);
    noLeak(r.body, secrets, url);
    for (const [pick, shape] of shapes) {
      const got = pick(r.body);
      for (const x of Array.isArray(got) ? got : [got]) conforms(shape, x, url);
    }
    return r.body;
  };

  conforms('order', placed.body.order, 'checkout');
  noLeak(placed.body, secrets, 'checkout');
  const page = await check(`/api/store/${slug}`, {}, [[(b) => b.store, 'shop']]);
  assert.ok(page.store.contactPhone, 'شمارهٔ تماسِ دکان عمداً عمومی است (§۱.۱)');
  const items = await check(`/api/store/${slug}/products`, {}, [[(b) => b.items, 'product']]);
  assert.deepEqual(items.items.map((p) => p.id).sort(), ['oil', 'rice']);
  await check(`/api/store/${slug}/products?ids=rice,oil`, {}, [[(b) => b.items, 'product']]);
  const detail = await check(`/api/store/${slug}/products/rice`, {}, [
    [(b) => b.product, 'product'], [(b) => b.similar, 'product'], [(b) => b.complementary, 'product'],
  ]);
  assert.equal(detail.product.description, 'برنجِ خوب');
  //  `deals` (WEB-02 #626): همان کالا + `regularPriceMinor`
  const hl = await check(`/api/store/${slug}/highlights`, {}, [
    [(b) => b.bestsellers, 'product'], [(b) => b.newArrivals, 'product'], [(b) => b.deals, 'product'],
  ]);
  assert.ok(hl.deals.some((p) => p.id === 'oil' && p.regularPriceMinor > p.priceMinor), 'روغن ارزان‌تر از قیمتِ دکان');
  const tracked = await check(`/api/store/${slug}/orders/${orderToken}`, {}, [[(b) => b.order, 'order']]);
  //  PAY-21 (#529): پیگیری دکمهٔ پرداخت را با همین شکل دارد
  assert.deepEqual(Object.keys(tracked.order.onlinePay), ['available']);
  assert.equal(tracked.order.orderNo, placed.body.order.orderNo);
  const re = await check(`/api/store/${slug}/orders/${orderToken}/reorder`, {}, [[(b) => b.lines, 'reorderLine']]);
  assert.equal(re.lines.length, 1);
  await check(`/api/store/${slug}/orders`, T, [[(b) => b.orders, 'orderSummary']]);
  const mine = await check('/api/market/orders', T, [[(b) => b.orders, 'orderSummary']]);
  assert.ok(mine.orders.length >= 1);

  const shops = await check('/api/market/shops?lat=34.53&lng=69.17', {}, [[(b) => b.shops, 'shopCard']]);
  const card = shops.shops.find((s) => s.slug === slug);
  assert.ok(card && card.distanceKm > 0, 'فقط فاصلهٔ گردشده');
  await check('/api/market/products', {}, [
    [(b) => b.items, 'marketProduct'], [(b) => b.popular, 'marketProduct'], [(b) => b.deals, 'marketProduct'],
    [(b) => b.shops, 'shopCard'],
  ]);
  const found = await check('/api/market/search?q=برنج', {}, [[(b) => b.shops, 'shopCard'], [(b) => b.products, 'searchProduct']]);
  assert.ok(found.products.some((p) => p.id === 'rice'));
  //  شناسهٔ سفارش فقط درونِ توکنِ HMAC دار؛ جای دیگر هرگز
  const tr = JSON.stringify(tracked).split(orderToken).join('');
  assert.equal(tr.includes(orderId), false, 'شناسهٔ خامِ سفارش بیرون از توکن');

  //  پرچمِ خاموش (پیش‌فرض) ⇒ پاسخِ امروز همان است که با بُرش (سازنده‌های امروز فیلدِ اضافه ندارند)
  await setFlag('all', V.FLAG, false);
  for (const url of [`/api/store/${slug}/products`, `/api/store/${slug}/products/rice`, `/api/store/${slug}/orders/${orderToken}`]) {
    const { opts, body } = seen.get(url);
    const r = await h.get(url, opts);
    assert.equal(r.status, 200, url);
    assert.deepEqual(r.body, body, `${url}: پرچمِ خاموش همان پاسخ`);
  }
  //  همهٔ نشانی‌های بالا، به شکل (نه مقدار): فیلدی که main همیشه می‌فرستد و در فهرست نیست، با پرچمِ
  //  روشن بی‌صدا بریده می‌شود ⇒ این‌جا سرخ (مثلِ `store.media` ِ M3 #428)
  const paths = (v, p = '$', out = new Set()) => {
    if (Array.isArray(v)) v.forEach((x) => paths(x, `${p}[]`, out));
    else if (v && typeof v === 'object') for (const [k, x] of Object.entries(v)) { out.add(`${p}.${k}`); paths(x, `${p}.${k}`, out); }
    return out;
  };
  for (const [url, { opts, body }] of seen) {
    const r = await h.get(url, opts);
    assert.equal(r.status, 200, url);
    const cut = paths(body);
    assert.deepEqual([...paths(r.body)].filter((x) => !cut.has(x)), [], `${url}: فیلدِ عمومیِ بیرون از فهرست`);
  }
  await setFlag('all', 'market.directory', false);
});

test('پرچمِ market.public_views: خاموش (پیش‌فرض) ⇒ فیلدِ تازهٔ سازنده بی‌صدا بریده نمی‌شود؛ روشن ⇒ فهرستِ سفید', async () => {
  const p = { ...C.samples.product, brand: 'برندِ تازه (M4)', publicId: 'pub_x (S3)' };
  await setFlag('all', V.FLAG, false);
  assert.equal(await V.sync(), false);
  assert.equal(V.enforced(), false);
  assert.equal(V.pub('product', p), p, 'خاموش ⇒ همان شیء');
  assert.deepEqual(V.pubList('product', [p]), [p]);
  await setFlag('all', V.FLAG, true);
  assert.equal(await V.sync(), true);
  const cut = V.pub('product', p);
  assert.equal('brand' in cut, false);
  assert.equal('publicId' in cut, false);
  assert.deepEqual(cut, C.samples.product);
  //  view/list (آزمون و پیش‌نمایش) همیشه می‌بُرند، پرچم هر چه باشد
  await setFlag('all', V.FLAG, false);
  assert.equal(await V.sync(), false);
  assert.deepEqual(V.view('product', p), C.samples.product);
});
