'use strict';
/**
 * سبدِ ماندگارِ حسابِ مشتری روی سرور (CHK-03، ۲۳ S140) — `lib/store-cart.js`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `shop.storefront.cart` ⇒ همان پاسخِ مسیرِ ناشناختهٔ main (۴۰۱ `unauthorized`)،
 *      حتی با نشست؛ هیچ ردیف.
 *   ۲) سبد روی سرور می‌ماند و دستگاهِ دومِ همان حساب همان را می‌بیند؛ قیمت همیشه امروزی؛
 *      کالای بیرون‌رفته پاک نمی‌شود و «نیست» است؛ ورودیِ بد ۴۰۰.
 *   ۳) نسخه (CAS): نوشتنِ کهنه ⇒ ۴۰۹ `cart_changed` با سبدِ تازه؛ دو نوشتنِ هم‌زمان ⇒ فقط یکی.
 *   ۴) فقط مالِ همان مشتری و همان ویترین؛ پاک کردنِ حساب سبد را هم پاک می‌کند.
 *   ۵) S140: ادغامِ سبدِ مهمان بیشینه است نه جمع؛ تکرارش هیچ اثر و نسخهٔ تازه‌ای ندارد؛
 *      بیش از سقف ⇒ ۴۰۹ و هیچ تغییر.
 *   ۶) سبد ⇒ ثبت با همان جمع ⇒ خالی کردن.
 *   ۷) بردارِ مشترکِ `core/test-vectors/account-cart-v1.json` (همان را `ShopperCartVectorsTest.kt` ِ برنامهٔ خرید روی
 *      سرورِ جعلیِ Kotlin می‌دواند، فقط CI): شکلِ پاسخ، نسخه، ۴۰۹، ادغامِ بیشینه، قیمتِ امروز و کدِ پرچمِ خاموش.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const path = require('node:path');
const V = require(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'account-cart-v1.json'));
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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

const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

const THREE = [
  { id: 'rice', name: 'برنج', unit: 'کیلو', salePrice: 120.5 },
  { id: 'oil', name: 'روغن', unit: 'بوتل', salePrice: 300 },
  { id: 'salt', name: 'نمک', unit: 'بسته', salePrice: 20 },
];

/** دکانِ روشن با ویترین و سه کالای منتشرشده؛ `cart` = پرچمِ سبدِ سروری. */
async function liveStore(label, { cart = true, products = THREE } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (cart) await setFlag(o.shopId, 'shop.storefront.cart', true);
  const slug = `c-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-cart' },
    body: { device_id: 'dev-cart', schema_version: 2, ops: products.map((x) => op(x.id, { name: x.name, unit: x.unit, salePrice: x.salePrice })) },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of products.map((x) => x.id)) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  return { ...o, slug };
}

/** نشستِ تازهٔ حسابِ این شماره — هر بار یک «دستگاهِ» دیگر. */
async function session(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session } };
}

const url = (slug) => `/api/store/${slug}/account/cart`;
const rows = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM store_customer_carts WHERE shop_id=$1', [shopId])).n;
const brief = (cart) => cart.lines.map((l) => [l.productId, l.qtyMilli, l.available]);

test('CHK-03 ⛔: پرچمِ خاموش ⇒ همان پاسخِ مسیرِ ناشناختهٔ main، حتی با نشست؛ هیچ ردیفی ساخته نمی‌شود', async () => {
  const s = await liveStore('سبدِ خاموش', { cart: false });
  const A = await session(s.slug, '0791000001');
  //  main برای درِ ناشناختهٔ `/api` همان ۴۰۱ `unauthorized` ِ روترِ پسین را می‌دهد
  const unknown = await h.get(`/api/store/${s.slug}/account/no-such-door`, A);
  assert.equal(unknown.status, 401);
  assert.equal(unknown.body.error.code, 'unauthorized');
  const calls = [
    await h.get(url(s.slug), A),
    await h.get(url(s.slug)),
    await h.put(url(s.slug), { version: 0, items: [{ productId: 'rice', qtyMilli: 1000 }] }, A),
    await h.post(`${url(s.slug)}/merge`, { items: [{ productId: 'rice', qtyMilli: 1000 }] }, A),
  ];
  for (const r of calls) {
    assert.equal(r.status, unknown.status);
    assert.deepEqual(r.body, unknown.body, 'همان پاسخِ درِ ناشناخته');
  }
  assert.equal(await rows(s.shopId), 0);
  //  روشن ⇒ همان درها کار می‌کنند
  await setFlag(s.shopId, 'shop.storefront.cart', true);
  assert.equal((await h.get(url(s.slug), A)).status, 200);
  const noSession = await h.get(url(s.slug));
  assert.equal(noSession.status, 401);
  assert.equal(noSession.body.error.code, 'session_required', 'روشن و بی نشست ⇒ ۴۰۱ ِ حساب');
  //  ویترینِ بسته ⇒ همان ۴۰۴ `store_not_found` ِ درهای دیگرِ ویترین (SUB-10، #684)، پیش از نشست
  await setFlag(s.shopId, 'shop.storefront', false);
  const off = await h.get(url(s.slug), A);
  assert.equal(off.status, 404);
  assert.equal(off.body.error.code, 'store_not_found');
});

test('CHK-03: سبد روی سرور می‌ماند و دستگاهِ دوم همان را می‌بیند؛ قیمتِ امروز؛ کالای بیرون‌رفته «نیست»؛ ورودیِ بد ۴۰۰', async () => {
  const s = await liveStore('سبدِ دو دستگاه');
  const phone = '0791000002';
  const D1 = await session(s.slug, phone);
  const D2 = await session(s.slug, phone);
  const empty = await h.get(url(s.slug), D1);
  assert.equal(empty.status, 200, JSON.stringify(empty.body));
  assert.deepEqual(empty.body.cart, { version: 0, updatedAt: null, maxLines: 100, lines: [], subtotalMinor: 0 });
  assert.equal(empty.headers.get('cache-control'), 'no-store');

  const w = await h.put(url(s.slug), {
    version: 0, items: [{ productId: 'rice', qtyMilli: 2000, note: 'دانه‌بلند' }, { productId: 'oil', qtyMilli: 1000 }],
  }, D1);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.cart.version, 1);
  assert.ok(w.body.cart.updatedAt > 0);
  //  برنج ۲ × ۱۲۰٫۵ + روغن ۳۰۰ = ۵۴۱ افغانی
  assert.equal(w.body.cart.subtotalMinor, 54100);

  const seen = await h.get(url(s.slug), D2);
  assert.equal(seen.status, 200);
  assert.deepEqual(seen.body.cart, w.body.cart, 'دستگاهِ دوم همان سبد');
  const rice = seen.body.cart.lines[0];
  assert.deepEqual([rice.name, rice.unit, rice.priceMinor, rice.lineTotalMinor, rice.note], ['برنج', 'کیلو', 12050, 24100, 'دانه‌بلند']);
  assert.equal(rice.stock.status, 'unverified', 'بی کاتالوگِ روشن هرگز «موجود» ِ ساختگی');

  //  قیمت ذخیره نمی‌شود: دکان قیمت را عوض کرد ⇒ خواندنِ بعدی قیمتِ امروز
  assert.equal((await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 35000 }, { token: s.token })).status, 200);
  //  کالا از ویترین بیرون رفت ⇒ قلم می‌ماند، «نیست» و بیرون از جمع
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: false }, { token: s.token })).status, 200);
  const now2 = (await h.get(url(s.slug), D2)).body.cart;
  assert.deepEqual(brief(now2), [['rice', 2000, false], ['oil', 1000, true]]);
  assert.equal(now2.lines[0].reason, 'not_in_store');
  assert.equal(now2.lines[0].priceMinor, null);
  assert.equal(now2.lines[1].priceMinor, 35000);
  assert.equal(now2.subtotalMinor, 35000);
  assert.equal(now2.version, 1, 'خواندن چیزی نمی‌نویسد');
  //  بازگشتِ کالا ⇒ همان قلم دوباره در دسترس
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  assert.equal((await h.get(url(s.slug), D1)).body.cart.lines[0].available, true);

  const bad = async (b, code) => {
    const r = await h.put(url(s.slug), b, D1);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, code);
  };
  await bad({ items: [] }, 'bad_version');
  await bad({ version: '1', items: [] }, 'bad_version');
  await bad({ version: 1 }, 'bad_lines');
  await bad({ version: 1, items: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'rice', qtyMilli: 2000 }] }, 'bad_lines');
  await bad({ version: 1, items: [{ productId: 'bad id!', qtyMilli: 1000 }] }, 'bad_lines');
  await bad({ version: 1, items: [{ productId: 'rice', qtyMilli: 0 }] }, 'bad_number');
  await bad({ version: 1, items: [{ productId: 'rice', qtyMilli: 1.5 }] }, 'bad_number');
  await bad({ version: 1, items: Array.from({ length: 101 }, (_, i) => ({ productId: `p${i}`, qtyMilli: 1000 })) }, 'bad_lines');
  assert.equal((await h.get(url(s.slug), D1)).body.cart.version, 1, 'ورودیِ بد چیزی ننوشت');
});

test('CHK-03: نسخهٔ کهنه ⇒ ۴۰۹ با سبدِ تازه و هیچ نوشتن؛ دو نوشتنِ هم‌زمان ⇒ فقط یکی؛ خالی کردن نسخه را بالا می‌برد', async () => {
  const s = await liveStore('سبدِ نسخه');
  const phone = '0791000003';
  const D1 = await session(s.slug, phone);
  const D2 = await session(s.slug, phone);
  //  دو دستگاه هر دو سبدِ خالیِ نسخهٔ ۰ را دیده‌اند و هم‌زمان می‌نویسند
  const [a, b] = await Promise.all([
    h.put(url(s.slug), { version: 0, items: [{ productId: 'rice', qtyMilli: 1000 }] }, D1),
    h.put(url(s.slug), { version: 0, items: [{ productId: 'salt', qtyMilli: 3000 }] }, D2),
  ]);
  assert.deepEqual([a.status, b.status].sort(), [200, 409], `${a.status} ${b.status}`);
  const lost = a.status === 409 ? a : b;
  const won = a.status === 409 ? b : a;
  assert.equal(lost.body.error.code, 'cart_changed');
  assert.deepEqual(lost.body.error.cart, won.body.cart, 'بازنده سبدِ برنده را می‌بیند');
  assert.equal(await rows(s.shopId), 1);

  //  نوشتنِ کهنه چیزی را عوض نمی‌کند
  const stale = await h.put(url(s.slug), { version: 0, items: [] }, D1);
  assert.equal(stale.status, 409);
  assert.deepEqual((await h.get(url(s.slug), D2)).body.cart, won.body.cart);

  //  خالی کردن = نوشتنِ فهرستِ خالی؛ ردیف و نسخه می‌مانند (بی ABA)
  const cleared = await h.put(url(s.slug), { version: 1, items: [] }, D2);
  assert.equal(cleared.status, 200, JSON.stringify(cleared.body));
  assert.equal(cleared.body.cart.version, 2);
  assert.deepEqual(cleared.body.cart.lines, []);
  assert.equal(await rows(s.shopId), 1);
  //  دستگاهی که پیش از خالی شدن نسخهٔ ۱ را دیده بود دیگر نمی‌تواند سبدِ کهنه را برگرداند
  assert.equal((await h.put(url(s.slug), { version: 1, items: [{ productId: 'oil', qtyMilli: 1000 }] }, D1)).status, 409);
});

test('CHK-03: فقط مالِ همان مشتری و همان ویترین؛ پاک کردنِ حساب سبد را هم پاک می‌کند', async () => {
  const s = await liveStore('سبدِ جدا');
  const s2 = await liveStore('سبدِ ویترینِ دوم');
  const A = await session(s.slug, '0791000004');
  const B = await session(s.slug, '0791000005');
  const w = await h.put(url(s.slug), { version: 0, items: [{ productId: 'rice', qtyMilli: 1000, note: 'رازِ-سبدِ-الف' }] }, A);
  assert.equal(w.status, 200, JSON.stringify(w.body));

  //  مشتریِ دیگر: سبدِ خودش (خالی)؛ نوشتنش سبدِ الف را عوض نمی‌کند
  const seenB = await h.get(url(s.slug), B);
  assert.deepEqual(seenB.body.cart.lines, []);
  assert.ok(!JSON.stringify(seenB.body).includes('رازِ-سبدِ-الف'));
  assert.equal((await h.put(url(s.slug), { version: 0, items: [{ productId: 'oil', qtyMilli: 1000 }] }, B)).status, 200);
  assert.deepEqual(brief((await h.get(url(s.slug), A)).body.cart), [['rice', 1000, true]]);

  //  همان حساب در ویترینِ دیگر: سبدِ جدا
  const A2 = await session(s2.slug, '0791000004');
  assert.deepEqual((await h.get(url(s2.slug), A2)).body.cart.lines, []);
  //  نشستِ ویترینِ دوم سبدِ ویترینِ اول را از راهِ slug ِ اول می‌بیند — همان حساب است، نه نشتی
  assert.deepEqual(brief((await h.get(url(s.slug), A2)).body.cart), [['rice', 1000, true]]);

  //  پاک کردنِ حساب ⇒ سبدِ هر دو ویترین هم؛ سبدِ ب دست نمی‌خورد
  assert.equal((await h.del(`/api/store/${s.slug}/account`, A)).status, 200);
  assert.equal((await one(
    `SELECT COUNT(*)::int AS n FROM store_customer_carts k JOIN store_customers c ON c.id = k.customer_id WHERE c.email = $1`,
    [mailOf('0791000004')]
  )).n, 0);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_carts WHERE shop_id = ANY($1)', [[s.shopId, s2.shopId]])).n,
    1, 'فقط سبدِ ب');
  assert.equal((await h.get(url(s.slug), A)).status, 401);
  assert.deepEqual(brief((await h.get(url(s.slug), B)).body.cart), [['oil', 1000, true]]);
});

test('S140: ادغامِ سبدِ مهمان بیشینه است نه جمع؛ تکرارش بی اثر و بی نسخهٔ تازه؛ بیش از سقف ⇒ ۴۰۹ و هیچ تغییر', async () => {
  const s = await liveStore('سبدِ ادغام');
  const A = await session(s.slug, '0791000006');
  const merge = (items) => h.post(`${url(s.slug)}/merge`, { items }, A);
  //  سبدِ حساب: برنج ۱ کیلو با یادداشت، روغن ۲
  assert.equal((await h.put(url(s.slug), { version: 0, items: [
    { productId: 'rice', qtyMilli: 1000, note: 'یادداشتِ حساب' }, { productId: 'oil', qtyMilli: 2000 },
  ] }, A)).status, 200);
  //  سبدِ مهمانِ مرورگر: برنج ۳ با یادداشتِ دیگر، روغن ۱، نمک ۱
  const guest = [
    { productId: 'rice', qtyMilli: 3000, note: 'یادداشتِ مهمان' }, { productId: 'oil', qtyMilli: 1000 },
    { productId: 'salt', qtyMilli: 1000, note: 'ریز' },
  ];
  const m1 = await merge(guest);
  assert.equal(m1.status, 200, JSON.stringify(m1.body));
  assert.equal(m1.body.cart.version, 2);
  assert.deepEqual(m1.body.cart.lines.map((l) => [l.productId, l.qtyMilli, l.note]), [
    ['rice', 3000, 'یادداشتِ حساب'], ['oil', 2000, ''], ['salt', 1000, 'ریز'],
  ]);
  //  تکرارِ همان ادغام (دو زدن، تلاشِ دوباره پس از قطع): همان سبد، همان نسخه
  const m2 = await merge(guest);
  assert.equal(m2.status, 200);
  assert.deepEqual(m2.body.cart, m1.body.cart);
  //  مهمانِ خالی هم چیزی عوض نمی‌کند
  assert.deepEqual((await merge([])).body.cart, m1.body.cart);
  //  ورودیِ بد ⇒ ۴۰۰
  assert.equal((await merge([{ productId: 'rice', qtyMilli: -1 }])).status, 400);
  assert.equal((await h.post(`${url(s.slug)}/merge`, {}, A)).body.error.code, 'bad_lines');

  //  سقف: ۹۸ قلمِ تازه + ۳ قلمِ موجود = ۱۰۱ ⇒ ۴۰۹ و هیچ تغییر
  const many = Array.from({ length: 98 }, (_, i) => ({ productId: `x${i}`, qtyMilli: 1000 }));
  const full = await merge(many);
  assert.equal(full.status, 409, JSON.stringify(full.body));
  assert.equal(full.body.error.code, 'cart_full');
  assert.deepEqual(full.body.error.cart, m1.body.cart);
  assert.deepEqual((await h.get(url(s.slug), A)).body.cart, m1.body.cart);
  //  ۹۷ قلم درست سقف را پر می‌کند؛ کالای ناشناخته «نیست» است، نه خطا
  const fit = await merge(many.slice(0, 97));
  assert.equal(fit.status, 200, JSON.stringify(fit.body));
  assert.equal(fit.body.cart.lines.length, 100);
  assert.equal(fit.body.cart.lines.filter((l) => !l.available).length, 97);
  assert.equal(fit.body.cart.subtotalMinor, m1.body.cart.subtotalMinor);
});

test('CHK-03: سبد ⇒ ثبتِ سفارش با همان جمع ⇒ خالی کردن؛ سبدِ ثبت‌شده با نسخهٔ تازه', async () => {
  const s = await liveStore('سبد تا ثبت');
  const A = await session(s.slug, '0791000007');
  const c = (await h.put(url(s.slug), { version: 0, items: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'salt', qtyMilli: 1000 }] }, A)).body.cart;
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتریِ سبد', phone: '0791000007' },
    lines: c.lines.map((l) => ({ productId: l.productId, qtyMilli: l.qtyMilli })), expectedTotalMinor: c.subtotalMinor,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, ...A.headers } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  assert.equal(ck.body.order.subtotalMinor, c.subtotalMinor, 'جمعِ سبد همان جمعِ ثبت');
  //  ثبت سبد را خودبه‌خود عوض نمی‌کند؛ برنامه با نسخهٔ دیده‌شده خالی‌اش می‌کند
  assert.equal((await h.get(url(s.slug), A)).body.cart.version, 1);
  const cleared = await h.put(url(s.slug), { version: c.version, items: [] }, A);
  assert.equal(cleared.status, 200);
  assert.deepEqual([cleared.body.cart.version, cleared.body.cart.lines.length], [2, 0]);
});

test('CHK-03 بردارِ account-cart-v1: همان پاسخ‌هایی که سرورِ جعلیِ برنامهٔ خرید می‌دهد', async () => {
  assert.equal(V.version, 1);
  assert.equal(V.flag, 'shop.storefront.cart');
  assert.equal(require('../src/lib/store-cart').FLAG_KEY, V.flag);
  assert.equal(require('../src/lib/store-cart').MAX_LINES, V.maxLines);
  assert.deepEqual(V.products, THREE, 'همان سه کالای آزمون‌های بالا');
  const s = await liveStore('سبدِ بردار', { products: V.products });
  const A = await session(s.slug, '0791000008');
  assert.ok(A.headers[V.sessionHeader]);

  //  پرچمِ ویترین: `features.accountCart` فقط با «حسابِ وب» (`shop.storefront.web_account`) هم
  await setFlag(s.shopId, 'shop.storefront.web_account', true);
  const f = (await h.get(`/api/store/${s.slug}`)).body.features;
  assert.equal(f && f[V.feature], true, JSON.stringify(f));

  const call = (st) => {
    if (st.do === 'get') return h.get(url(s.slug), A);
    if (st.do === 'put') return h.put(url(s.slug), st.body, A);
    return h.post(`${url(s.slug)}/merge`, st.body, A);
  };
  const shape = (c) => [c.version, c.subtotalMinor, c.lines.map((l) => [l.productId, l.qtyMilli, l.note, l.available, l.priceMinor])];
  for (const [i, st] of V.steps.entries()) {
    const at = `گامِ ${i + 1} (${st.note})`;
    if (st.do === 'unpublish' || st.do === 'price') {
      const body = st.do === 'unpublish' ? { published: false } : { published: true, onlinePriceMinor: st.onlinePriceMinor };
      assert.equal((await h.put(`/api/shop/storefront/products/${st.productId}`, body, { token: s.token })).status, 200, at);
      continue;
    }
    const r = await call(st);
    assert.equal(r.status, st.status, `${at}: ${JSON.stringify(r.body)}`);
    if (st.code) assert.equal(r.body.error.code, st.code, at);
    const cart = r.status === 200 ? r.body.cart : r.body.error && r.body.error.cart;
    if (!st.cart) { assert.equal(cart, undefined, at); continue; }
    assert.deepEqual(Object.keys(cart).sort(), V.cartKeys, at);
    for (const l of cart.lines) assert.deepEqual(Object.keys(l).sort(), V.lineKeys, at);
    assert.equal(cart.maxLines, V.maxLines, at);
    assert.deepEqual(shape(cart), [st.cart.version, st.cart.subtotalMinor, st.cart.lines], at);
    for (const [id, reason] of Object.entries(st.reasons || {})) assert.equal(cart.lines.find((l) => l.productId === id).reason, reason, at);
  }

  //  بی نشست و پرچمِ خاموش: دو ۴۰۱ ِ جدا — فقط اولی «دوباره وارد شوید» است
  const none = await h.get(url(s.slug));
  assert.deepEqual([none.status, none.body.error.code], [V.noSession.status, V.noSession.code]);
  await setFlag(s.shopId, V.flag, false);
  const off = await h.get(url(s.slug), A);
  assert.deepEqual([off.status, off.body.error.code], [V.flagOff.status, V.flagOff.code]);
  assert.notEqual(V.flagOff.code, V.noSession.code);
});
