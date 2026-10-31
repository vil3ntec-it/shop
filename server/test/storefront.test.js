'use strict';
/**
 * ویترینِ اینترنتی و ثبتِ سفارشِ مشتری — فاز ۴ (WEB، CHK؛ بخش‌های ۲۳ و ۳۱).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی هر دو پرچم و بی «روشن»ِ خودِ دکان ⇒ ۴۰۴ و هیچ ردیف.
 *   ۲) فقط کالای «در ویترین»، زنده و قیمت‌دار دیده و خریده می‌شود.
 *   ۳) ⛔ قیمت را سرور می‌گوید؛ عددِ مشتری خوانده نمی‌شود؛ جمعِ عوض‌شده ⇒ ۴۰۹
 *      با برگهٔ تازه و بی سفارش (CHK-17، S135).
 *   ۴) ⛔ کلیدِ یکتایی (CHK-19): تکرار، حتی هم‌زمان، سفارشِ دوم نمی‌سازد.
 *   ۵) ⛔ ثبت فقط با شمارهٔ تأییدشده (۲۳.۲، S133)؛ پیگیری فقط با توکنِ
 *      حدس‌نزدنی (S137)؛ «سفارش‌های من» فقط همان شماره (S138).
 *   ۶) سقفِ درخواستِ ثبت (۴۲۹).
 *   ۷) وضعیتِ دکان برای مشتری (۳۱.۱): سه چیزِ جدا و بی زمانِ ساختگی.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '12';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const sf = require('../src/lib/storefront');

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

const push = (token, ops, device = 'dev-store') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields, type = 'insert') => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
});

/** دکانِ روشن با ویترین و سه کالا (دو منتشرشده). */
async function liveStore(label, settings = {}) {
  const o = await shopOwner(label);
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `s-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', ...settings,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  if (tickets.has(phone)) return tickets.get(phone);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.equal(s.body.sent, true);
  assert.match(String(s.body.devCode), /^\d+$/, 'بیرون از تولید و با راهِ log، کدِ آزمایشی برمی‌گردد');
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(phone, c.body.ticket);
  return c.body.ticket;
}

const PHONE = '0791112233';
function cart(over = {}) {
  return {
    fulfillment: 'pickup',
    customer: { name: 'مشتریِ آزمایشی', phone: PHONE },
    lines: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000, note: 'کوچک' }],
    ...over,
  };
}
const checkout = (slug, body, { key = `ck-${uniq()}-key`, ticket } = {}) => h.post(`/api/store/${slug}/checkout`, body, {
  headers: { ...(key ? { 'Idempotency-Key': key } : {}), ...(ticket ? { 'X-Store-Ticket': ticket } : {}) },
});
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی پرچم: تنظیم ۴۰۳، نشانیِ عمومی ۴۰۴، و هیچ ردیفی', async () => {
  const o = await shopOwner('خاموش');
  const cfg = await h.get('/api/shop/storefront/config', { token: o.token });
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  assert.equal(cfg.body.settings, null);

  const put = await h.put('/api/shop/storefront', { slug: 'khamosh-shop', enabled: true, title: 'x' }, { token: o.token });
  assert.equal(put.status, 403);
  assert.equal(put.body.error.code, 'storefront_disabled');
  //  فقط یکی از دو پرچم هم بس نیست
  await setFlag(o.shopId, 'shop.orders', true);
  assert.equal((await h.put('/api/shop/storefront', { slug: 'khamosh-shop' }, { token: o.token })).status, 403);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefronts WHERE shop_id=$1', [o.shopId])).n, 0);

  for (const r of [
    await h.get('/api/store/khamosh-shop'),
    await h.get('/api/store/khamosh-shop/products'),
    await checkout('khamosh-shop', cart()),
    await h.post('/api/store/khamosh-shop/verify/start', { phone: PHONE }),
  ]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'store_not_found');
  }
  assert.equal(await count(o.shopId), 0);
});

test('پرچمِ خاموش پس از کار ⇒ ویترین، پیگیری و صفحه همه ۴۰۴', async () => {
  const s = await liveStore('خاموش‌شونده');
  const t = await ticketFor(s.slug, PHONE);
  const made = await checkout(s.slug, cart(), { ticket: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  await setFlag(s.shopId, 'shop.storefront', false);
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 404);
  assert.equal((await h.get(`/api/store/${s.slug}/orders/${made.body.order.token}`)).status, 404);
  const page = await fetch(`${h.base()}/store/${s.slug}`);
  assert.equal(page.status, 404);
  await setFlag(s.shopId, 'shop.storefront', true);
  const back = await fetch(`${h.base()}/store/${s.slug}`);
  assert.equal(back.status, 200, await back.text());
});

test('ویترینِ «روشن‌نشده» ِ خودِ دکان ⇒ ۴۰۴ حتی با پرچم', async () => {
  const s = await liveStore('خاموشِ دستی');
  await h.put('/api/shop/storefront', { enabled: false }, { token: s.token });
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 404);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) تنظیم و کاتالوگ
   ══════════════════════════════════════════════════════════════════ */

test('تنظیم: نشانیِ رزرو/بدشکل/گرفته‌شده رد، شاگرد دسترسی ندارد، روشن کردن نام می‌خواهد', async () => {
  const a = await liveStore('تنظیم');
  const o = await shopOwner('دیگری');
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const tryPut = (body) => h.put('/api/shop/storefront', body, { token: o.token });
  assert.equal((await tryPut({ slug: 'api' })).body.error.code, 'slug_reserved');
  assert.equal((await tryPut({ slug: 'Bad_Slug' })).body.error.code, 'bad_slug');
  assert.equal((await tryPut({ slug: a.slug })).body.error.code, 'slug_taken');
  assert.equal((await tryPut({ slug: `ok-${uniq()}`, enabled: true })).body.error.code, 'title_required');
  assert.equal((await tryPut({ slug: `ok-${uniq()}`, acceptDelivery: false, acceptPickup: false })).body.error.code, 'bad_fulfillment');
  assert.equal((await tryPut({ slug: `ok-${uniq()}`, weeklyHours: { 1: [[600, 500]] } })).body.error.code, 'bad_hours');
  assert.equal((await tryPut({ slug: `ok-${uniq()}`, timezone: 'Mars/Base' })).body.error.code, 'bad_timezone');

  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, staff.user.id, now()]
  );
  const r = await h.put('/api/shop/storefront', { title: 'دزدی' }, { token: staff.accessToken });
  assert.equal(r.status, 403);
  const r2 = await h.put('/api/shop/storefront/products/soap', { published: true }, { token: staff.accessToken });
  assert.equal(r2.status, 403);
});

test('کاتالوگ: فقط منتشرشده، زنده و قیمت‌دار؛ قیمتِ آنلاین بر قیمتِ دفتر جلو؛ موجودی «در حال بررسی»', async () => {
  const s = await liveStore('کاتالوگ');
  let list = (await h.get(`/api/store/${s.slug}/products`)).body.items;
  assert.deepEqual(list.map((p) => p.id).sort(), ['oil', 'rice']);
  const rice = list.find((p) => p.id === 'rice');
  assert.equal(rice.priceMinor, 12050);
  assert.equal(rice.unit, 'کیلو');
  assert.deepEqual(rice.stock, { status: 'unverified', checkedAt: null }, 'موجودیِ نامعلوم «موجود» نیست (۳۲.۱)');
  assert.ok(!list.some((p) => p.id === 'soap'), 'منتشرنشده دیده نمی‌شود');
  assert.ok(!('notes' in rice) && !('purchasePrice' in rice), 'قیمتِ خرید یا یادداشتِ داخلی بیرون نمی‌رود');

  await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 29000 }, { token: s.token });
  list = (await h.get(`/api/store/${s.slug}/products`)).body.items;
  assert.equal(list.find((p) => p.id === 'oil').priceMinor, 29000);

  //  نرمال‌سازیِ جست‌وجو: «ي» ِ عربی همان «ی» است
  await push(s.token, [op('tea', { name: 'چاي سبز', unit: 'بسته', salePrice: 90 })]);
  await h.put('/api/shop/storefront/products/tea', { published: true }, { token: s.token });
  const found = (await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent('چای')}`)).body.items;
  assert.deepEqual(found.map((p) => p.id), ['tea']);
  const cat = (await h.get(`/api/store/${s.slug}/products?category=${encodeURIComponent('خوراکی')}`)).body.items;
  assert.deepEqual(cat.map((p) => p.id).sort(), ['oil', 'rice']);
  const prof = (await h.get(`/api/store/${s.slug}`)).body.store;
  assert.ok(prof.categories.includes('خوراکی') && !prof.categories.includes('بهداشتی'), 'دستهٔ کالای منتشرنشده نه');

  //  صفحه‌بندیِ کلیدی
  const p1 = (await h.get(`/api/store/${s.slug}/products?limit=2`)).body;
  assert.equal(p1.items.length, 2);
  assert.ok(p1.nextCursor);
  const p2 = (await h.get(`/api/store/${s.slug}/products?limit=2&cursor=${p1.nextCursor}`)).body;
  assert.equal(p2.items.length, 1);
  assert.equal(p2.nextCursor, null);

  //  غیرفعال یا حذف‌شده ⇒ بیرون
  await push(s.token, [op('tea', { active: false }, 'update'), op('oil', {}, 'delete')]);
  list = (await h.get(`/api/store/${s.slug}/products`)).body.items;
  assert.deepEqual(list.map((p) => p.id), ['rice']);
  const miss = await h.put('/api/shop/storefront/products/nope', { published: true }, { token: s.token });
  assert.equal(miss.status, 404);
  assert.equal(miss.body.error.code, 'product_not_found');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) تأییدِ شماره و ثبت
   ══════════════════════════════════════════════════════════════════ */

test('بی شمارهٔ تأییدشده سفارشی ثبت نمی‌شود (S133)؛ بلیتِ دست‌خورده یا شمارهٔ دیگر هم نه', async () => {
  const s = await liveStore('تأیید');
  const none = await checkout(s.slug, cart());
  assert.equal(none.status, 401);
  assert.equal(none.body.error.code, 'verification_required');

  const bad = await h.post(`/api/store/${s.slug}/verify/check`, { phone: '0790000009', code: '000000' });
  assert.ok(bad.status >= 400 && bad.status < 500);

  const t = await ticketFor(s.slug, PHONE);
  const forged = t.slice(0, -2) + (t.endsWith('AA') ? 'BB' : 'AA');
  assert.equal((await checkout(s.slug, cart(), { ticket: forged })).status, 401);
  const other = await checkout(s.slug, cart({ customer: { name: 'دیگری', phone: '0790000008' } }), { ticket: t });
  assert.equal(other.status, 403);
  assert.equal(other.body.error.code, 'phone_not_verified');
  assert.equal(await count(s.shopId), 0);
});

test('ثبت: قیمت از سرور، عددِ مشتری بی‌اثر، رویدادِ «مشتری»، و همان در صفِ کارمند', async () => {
  const s = await liveStore('ثبت');
  const t = await ticketFor(s.slug, PHONE);
  const body = cart({
    lines: [{ productId: 'rice', qtyMilli: 2000, unitPriceMinor: 1, name: 'ارزان' }, { productId: 'oil', qtyMilli: 1000 }],
    totalMinor: 1, discountMinor: 99999, deliveryFeeMinor: 0, source: 'manual', test: true,
  });
  const r = await checkout(s.slug, body, { ticket: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const o = r.body.order;
  //  ۱۲۰٫۵ × ۲ = ۲۴۱ و ۳۰۰ ⇒ ۵۴۱ افغانی
  assert.equal(o.subtotalMinor, 54100);
  assert.equal(o.discountMinor, 0);
  assert.equal(o.totalMinor, 54100);
  assert.deepEqual(o.lines.map((l) => [l.name, l.unitPriceMinor]), [['برنج', 12050], ['روغن', 30000]]);
  assert.equal(o.status, 'new');
  assert.equal(o.canCancel, true);
  assert.match(o.token, /^[0-9A-Z]{26}\.[A-Za-z0-9_-]{32}$/);
  assert.ok(!('customer' in o) && !('id' in o), 'نمای مشتری شناسهٔ داخلی و شماره ندارد');

  const row = await one('SELECT * FROM online_orders WHERE shop_id=$1', [s.shopId]);
  assert.equal(row.source, 'storefront');
  assert.equal(row.is_test, false, '`test` از مشتری پذیرفته نیست');
  assert.equal(row.customer_phone, '+93791112233');
  assert.equal(Number(row.total_minor), 54100);
  const ev = await one('SELECT actor_type, kind FROM online_order_events WHERE order_id=$1', [row.id]);
  assert.deepEqual({ ...ev }, { actor_type: 'customer', kind: 'created' });

  const q = await h.get('/api/shop/orders?tab=new', { token: s.token });
  assert.equal(q.status, 200);
  assert.equal(q.body.items[0].source, 'storefront');
  assert.equal(q.body.items[0].totalMinor, 54100);
});

test('جمعِ عوض‌شده ⇒ ۴۰۹ با برگهٔ تازه و بی سفارش؛ با جمعِ درست ثبت (CHK-17، S135)', async () => {
  const s = await liveStore('قیمتِ تازه');
  const t = await ticketFor(s.slug, PHONE);
  const seen = cart({ expectedTotalMinor: 54100 });
  assert.equal((await checkout(s.slug, { ...seen, expectedTotalMinor: 1 }, { ticket: t })).body.error.code, 'price_changed');

  await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 31000 }, { token: s.token });
  const r = await checkout(s.slug, seen, { ticket: t });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'price_changed');
  assert.equal(r.body.error.quote.totalMinor, 55100);
  assert.equal(r.body.error.quote.lines.find((l) => l.productId === 'oil').unitPriceMinor, 31000);
  assert.equal(await count(s.shopId), 0, 'مبلغِ قدیمی بی‌صدا پذیرفته نمی‌شود');

  const ok = await checkout(s.slug, { ...seen, expectedTotalMinor: 55100 }, { ticket: t });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.order.totalMinor, 55100);
});

test('S32: فیِ هر قلمِ دیده‌شده ⇒ «X از … به …» در changes؛ جمعِ برابر با قیمتِ عوض‌شده هم ثبت نمی‌شود (CHK-17)', async () => {
  const s = await liveStore('فیِ دیده‌شده');
  const t = await ticketFor(s.slug, PHONE);
  //  برنج ۱۲۰٫۵ × ۲ + روغن ۳۰۰ = ۵۴۱
  const seenLines = (rice, oil) => [
    { productId: 'rice', qtyMilli: 2000, expectedUnitPriceMinor: rice },
    { productId: 'oil', qtyMilli: 1000, expectedUnitPriceMinor: oil },
  ];
  //  فیِ درست ⇒ ثبت؛ فیِ نفرستاده هم همان رفتارِ پیشین
  const ok = await checkout(s.slug, cart({ lines: seenLines(12050, 30000), expectedTotalMinor: 54100 }), { ticket: t });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));

  //  روغن ۳۰۰ ⇒ ۳۱۰ و برنج ۱۲۰٫۵ ⇒ ۱۱۵٫۵: جمعِ تازه ۲۳۱ + ۳۱۰ = ۵۴۱ — همان جمعِ دیده‌شده
  await h.put('/api/shop/storefront/products/oil', { published: true, onlinePriceMinor: 31000 }, { token: s.token });
  await h.put('/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: 11550 }, { token: s.token });
  const n0 = await count(s.shopId);
  const r = await checkout(s.slug, cart({ lines: seenLines(12050, 30000), expectedTotalMinor: 54100 }), { ticket: t });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'price_changed');
  assert.equal(r.body.error.quote.totalMinor, 54100, 'جمع از قضا همان است');
  assert.deepEqual(r.body.error.changes, [
    { productId: 'rice', name: 'برنج', fromMinor: 12050, toMinor: 11550 },
    { productId: 'oil', name: 'روغن', fromMinor: 30000, toMinor: 31000 },
  ]);
  assert.equal(await count(s.shopId), n0, 'هرگز با قیمتِ تأییدنشده ثبت نمی‌شود');

  //  فقط جمع (صفحهٔ امروزِ ویترین) ⇒ همان ۴۰۹ ِ پیشین با changes ِ خالی
  const total = await checkout(s.slug, cart({ expectedTotalMinor: 1 }), { ticket: t });
  assert.equal(total.body.error.code, 'price_changed');
  assert.deepEqual(total.body.error.changes, []);

  //  مشتری تغییر را دید و تأیید کرد ⇒ ثبت با قیمتِ تازه
  const again = await checkout(s.slug, cart({ lines: seenLines(11550, 31000), expectedTotalMinor: 54100 }), { ticket: t });
  assert.equal(again.status, 201, JSON.stringify(again.body));
  assert.deepEqual(again.body.order.lines.map((l) => l.unitPriceMinor), [11550, 31000]);

  for (const bad of [-1, 1.5, '12050', 100_000_000_001]) {
    const b = await checkout(s.slug, cart({ lines: [{ productId: 'rice', qtyMilli: 1000, expectedUnitPriceMinor: bad }] }), { ticket: t });
    assert.equal(b.status, 400, JSON.stringify(bad));
    assert.equal(b.body.error.code, 'bad_lines');
  }
});

test('کالای بیرون از ویترین، سقفِ تعداد، حداقلِ سفارش، روشِ تحویل و پرداخت', async () => {
  const s = await liveStore('قواعد', { minOrderMinor: 30000, deliveryFeeMinor: 5000 });
  const t = await ticketFor(s.slug, PHONE);
  const un = await checkout(s.slug, cart({ lines: [{ productId: 'soap', qtyMilli: 1000 }, { productId: 'rice', qtyMilli: 3000 }] }), { ticket: t });
  assert.equal(un.status, 409);
  assert.equal(un.body.error.code, 'product_unavailable');
  assert.deepEqual(un.body.error.unavailable, ['soap']);

  await h.put('/api/shop/storefront/products/rice', { published: true, maxQtyMilli: 5000 }, { token: s.token });
  const over = await checkout(s.slug, cart({ lines: [{ productId: 'rice', qtyMilli: 6000 }] }), { ticket: t });
  assert.equal(over.body.error.code, 'qty_over_limit');
  assert.equal(over.body.error.maxQtyMilli, 5000, 'مشتری مقدارِ قابلِ سفارش را می‌بیند (۳۲.۳)');

  const small = await checkout(s.slug, cart({ lines: [{ productId: 'rice', qtyMilli: 1000 }] }), { ticket: t });
  assert.equal(small.body.error.code, 'below_min_order');
  assert.equal(small.body.error.minOrderMinor, 30000);

  assert.equal((await checkout(s.slug, cart({ paymentMethod: 'online' }), { ticket: t })).body.error.code, 'bad_payment_method');
  assert.equal((await checkout(s.slug, cart({ fulfillment: 'delivery' }), { ticket: t })).body.error.code, 'bad_address');
  const del = await checkout(s.slug, cart({ fulfillment: 'delivery', address: { lat: 34.52, lng: 69.18, landmark: 'نزدیکِ مسجد' }, delivery: { callBeforeArrival: true } }), { ticket: t });
  assert.equal(del.status, 201, JSON.stringify(del.body));
  assert.equal(del.body.order.deliveryFeeMinor, 5000);
  assert.equal(del.body.order.totalMinor, 54100 + 5000);

  await h.put('/api/shop/storefront', { acceptPickup: false }, { token: s.token });
  assert.equal((await checkout(s.slug, cart(), { ticket: t })).body.error.code, 'fulfillment_unavailable');
  assert.equal(await count(s.shopId), 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) کلیدِ یکتایی
   ══════════════════════════════════════════════════════════════════ */

test('کلیدِ یکتایی: تکرار و هم‌زمانی یک سفارش؛ همان کلید با بدنهٔ دیگر ۴۰۹؛ بی کلید ۴۰۰', async () => {
  const s = await liveStore('یکتا');
  const t = await ticketFor(s.slug, PHONE);
  const key = `same-key-${uniq()}`;
  const a = await checkout(s.slug, cart(), { key, ticket: t });
  const b = await checkout(s.slug, cart(), { key, ticket: t });
  assert.equal(a.status, 201);
  assert.equal(b.status, 200);
  assert.equal(b.headers.get('idempotent-replayed'), 'true');
  assert.equal(b.body.order.token, a.body.order.token, 'تکرار همان توکن را برمی‌گرداند');

  //  قیمت پس از ثبت عوض شد: تکرارِ همان درخواست باز همان سفارشِ قبلی است
  await h.put('/api/shop/storefront/products/rice', { published: true, onlinePriceMinor: 1 }, { token: s.token });
  const c = await checkout(s.slug, cart(), { key, ticket: t });
  assert.equal(c.body.order.totalMinor, a.body.order.totalMinor);

  const d = await checkout(s.slug, cart({ note: 'دیگر' }), { key, ticket: t });
  assert.equal(d.status, 409);
  assert.equal(d.body.error.code, 'idempotency_key_reused');
  const none = await checkout(s.slug, cart(), { key: '', ticket: t });
  assert.equal(none.status, 400);
  assert.equal(none.body.error.code, 'idempotency_key_required');

  const k2 = `race-key-${uniq()}`;
  const all = await Promise.all([1, 2, 3, 4].map(() => checkout(s.slug, cart(), { key: k2, ticket: t })));
  assert.deepEqual(all.map((r) => r.status).sort(), [200, 200, 200, 201]);
  assert.equal(new Set(all.map((r) => r.body.order.token)).size, 1);
  assert.equal(await count(s.shopId), 2);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پیگیری، لغو و «سفارش‌های من»
   ══════════════════════════════════════════════════════════════════ */

test('پیگیری فقط با توکن (S137)؛ لغوِ رایگان تا پیش از قبول (CHK-22)', async () => {
  const s = await liveStore('پیگیری');
  const other = await liveStore('همسایه');
  const t = await ticketFor(s.slug, PHONE);
  const o = (await checkout(s.slug, cart(), { ticket: t })).body.order;

  const seen = await h.get(`/api/store/${s.slug}/orders/${o.token}`);
  assert.equal(seen.status, 200);
  assert.deepEqual(seen.body.order.timeline.map((e) => e.status), ['new']);
  const [id] = o.token.split('.');
  for (const bad of [id, `${id}.${'A'.repeat(32)}`, String(o.orderNo)]) {
    assert.equal((await h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(bad)}`)).status, 404);
  }
  assert.equal((await h.get(`/api/store/${other.slug}/orders/${o.token}`)).status, 404, 'توکنِ دکانِ دیگر');

  const c = await h.post(`/api/store/${s.slug}/orders/${o.token}/cancel`, {});
  assert.equal(c.status, 200, JSON.stringify(c.body));
  assert.equal(c.body.order.status, 'cancelled_by_customer');
  assert.equal(c.body.order.canCancel, false);

  const o2 = (await checkout(s.slug, cart({ note: 'دوم' }), { ticket: t })).body.order;
  const row = await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, o2.orderNo]);
  const acc = await h.post(`/api/shop/orders/${row.id}/transition`, { to: 'accepted' }, { token: s.token });
  assert.equal(acc.status, 200, JSON.stringify(acc.body));
  const late = await h.post(`/api/store/${s.slug}/orders/${o2.token}/cancel`, {});
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'cannot_cancel');
});

test('«سفارش‌های من»: فقط همان شمارهٔ تأییدشده، از هر دستگاه (S138)', async () => {
  const s = await liveStore('سفارش‌های من');
  const mine = await ticketFor(s.slug, PHONE);
  const theirs = await ticketFor(s.slug, '0795554433');
  await checkout(s.slug, cart(), { ticket: mine });
  await checkout(s.slug, cart({ customer: { name: 'ب', phone: '0795554433' } }), { ticket: theirs });
  const list = await h.get(`/api/store/${s.slug}/orders`, { headers: { 'X-Store-Ticket': mine } });
  assert.equal(list.status, 200);
  assert.equal(list.body.orders.length, 1);
  assert.equal((await h.get(`/api/store/${s.slug}/orders/${list.body.orders[0].token}`)).status, 200);
  assert.equal((await h.get(`/api/store/${s.slug}/orders`)).status, 401);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) سقفِ درخواست
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ ثبتِ سفارش به‌ازای IP ⇒ ۴۲۹ با Retry-After', async () => {
  const s = await liveStore('سقف');
  resetAll();
  const max = require('../src/config').rateLimit.storeCheckoutMax;
  const codes = [];
  for (let i = 0; i < max; i++) codes.push((await checkout(s.slug, cart())).status);
  assert.deepEqual([...new Set(codes)], [401], 'تا سقف، همان جوابِ «تأیید لازم است»');
  const last = await checkout(s.slug, cart());
  assert.equal(last.status, 429);
  assert.ok(Number(last.headers.get('retry-after')) > 0);
  assert.equal(await count(s.shopId), 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) وضعیتِ دکان برای مشتری (۳۱.۱)
   ══════════════════════════════════════════════════════════════════ */

test('وضعیت: ساعتِ کاری، اعلامِ مالک، اتصال و پذیرشِ سفارش جدا؛ بی زمانِ ساختگی', () => {
  //  ۲۰۲۶-۱۰-۱۹ ساعتِ ۱۰:۰۰ ِ کابل (دوشنبه) = ۰۵:۳۰ UTC
  const at = Date.UTC(2026, 9, 19, 5, 30);
  const base = {
    timezone: 'Asia/Kabul', weekly_hours: { 1: [[480, 1200]] }, closed_dates: [], presence_mode: 'auto',
    declared_until: null, notice: '',
  };
  const MIN = 60_000;
  assert.equal(sf.localParts(at, 'Asia/Kabul').time, '10:00');
  let p = sf.presenceOf(base, at, at - 2 * MIN);
  assert.deepEqual([p.badge, p.connection, p.orderAcceptance], ['open_online', 'confirmed', 'accepting']);
  assert.equal(p.lastConfirmedAt, at - 2 * MIN);
  p = sf.presenceOf(base, at, at - 30 * MIN);
  assert.deepEqual([p.badge, p.orderAcceptance], ['open_unconfirmed', 'delayed']);
  p = sf.presenceOf(base, at, 0);
  assert.deepEqual([p.badge, p.lastConfirmedAt], ['offline', null], 'هرگز وصل نشده ⇒ «همین حالا» ِ ساختگی نه');
  p = sf.presenceOf(base, at + 11 * 60 * MIN, at);   // ۲۱:۰۰
  assert.deepEqual([p.badge, p.orderAcceptance], ['closed', 'later']);
  p = sf.presenceOf({ ...base, closed_dates: ['2026-10-19'] }, at, at);
  assert.equal(p.badge, 'closed');
  assert.equal(p.holiday, true);
  p = sf.presenceOf({ ...base, presence_mode: 'online_until', declared_until: at + 60 * MIN }, at, at);
  assert.deepEqual([p.badge, p.declaredUntil], ['declared_until', at + 60 * MIN]);
  p = sf.presenceOf({ ...base, presence_mode: 'online_until', declared_until: at - MIN }, at, at);
  assert.equal(p.badge, 'open_online', 'اعلامِ گذشته خودکار منقضی می‌شود');
  assert.equal(sf.presenceOf({ ...base, presence_mode: 'closed' }, at, at).badge, 'closed');
  assert.equal(sf.presenceOf({ ...base, presence_mode: 'offline' }, at, at).badge, 'offline');
  assert.equal(sf.presenceOf({ ...base, weekly_hours: {} }, at, at).hoursSet, false);
});

test('اعلامِ مالک ثبت می‌شود، تکرارش بی‌اثر، رویدادِ کهنه وضعیتِ تازه را برنمی‌گرداند', async () => {
  const s = await liveStore('حضور');
  const declare = (body) => h.post('/api/shop/storefront/presence', body, { token: s.token });
  const until = Date.now() + 2 * 3600_000;
  const a = await declare({ eventId: `ev-${uniq()}-a`, mode: 'online_until', declaredUntil: until, clientEventAt: Date.now() });
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.applied, true);
  assert.equal(a.body.status.badge, 'declared_until');
  const again = await declare({ eventId: a.body.eventId || 'x', mode: 'closed' });
  assert.equal(again.status, 400, 'شناسهٔ رویداد کوتاه پذیرفته نیست');
  const ev = `ev-${uniq()}-b`;
  const b1 = await declare({ eventId: ev, mode: 'closed', clientEventAt: Date.now() });
  const b2 = await declare({ eventId: ev, mode: 'closed', clientEventAt: Date.now() });
  assert.equal(b1.body.status.badge, 'closed');
  assert.equal(b2.body.replayed, true);
  const stale = await declare({ eventId: `ev-${uniq()}-c`, mode: 'online', clientEventAt: Date.now() - 3600_000 });
  assert.equal(stale.body.applied, false);
  assert.equal(stale.body.status.badge, 'closed');
  const n = await one('SELECT COUNT(*)::int AS n FROM storefront_presence_events WHERE shop_id=$1 AND kind=$2', [s.shopId, 'presence']);
  assert.equal(n.n, 3);
  const pub = (await h.get(`/api/store/${s.slug}`)).body.store.status;
  assert.equal(pub.badge, 'closed');
  assert.equal(pub.orderAcceptance, 'later');
  assert.ok(pub.lastConfirmedAt > 0, 'همگام‌سازیِ واقعیِ دستگاه همان «آخرین تماس» است');
});

/* ══════════════════════════════════════════════════════════════════
   ۸) نشانی (D-06) و CSP ِ صفحه
   ══════════════════════════════════════════════════════════════════ */

function rawGet(path, host) {
  const http = require('node:http');
  const u = new URL(h.base());
  return new Promise((resolve, reject) => {
    http.get({ hostname: u.hostname, port: u.port, path, headers: { Host: host } }, (res) => {
      res.resume();
      res.on('end', () => resolve(res));
    }).on('error', reject);
  });
}

test('D-06: `<slug>.vill3n.top` در ریشه همان ویترین است؛ زیردامنهٔ رزرو نه؛ CSP ِ سخت', async () => {
  const s = await liveStore('زیردامنه');
  const page = await rawGet('/', `${s.slug}.vill3n.top`);
  assert.equal(page.statusCode, 200);
  const csp = page.headers['content-security-policy'] || '';
  assert.match(csp, /script-src 'self';/);
  assert.doesNotMatch(csp, /unsafe-inline|unsafe-eval/);
  assert.match(csp, /connect-src 'self';/);
  assert.equal((await rawGet('/', 'nope-shop.vill3n.top')).statusCode, 404, 'ویترینِ ناموجود ⇒ ۴۰۴');
  const api = await rawGet('/', 'api.vill3n.top');
  assert.equal(api.statusCode, 302, 'زیردامنهٔ رزرو همان رفتارِ پیشین (پنل)');
  assert.equal(api.headers.location, '/admin/');
  assert.equal(sf.slugFromHost('www.vill3n.top'), '');
  assert.equal(sf.slugFromHost(`${s.slug}.vill3n.top:443`), s.slug);
  assert.equal(sf.slugFromHost(`${s.slug}.evil.top`), '');
  //  هیچ اسکریپت یا style ِ درون‌خطی در پوسته
  const fs = require('node:fs');
  const html = fs.readFileSync(require('node:path').join(__dirname, '..', 'public', 'store', 'index.html'), 'utf8');
  assert.doesNotMatch(html, /<script(?![^>]*\ssrc=)[^>]*>/i);
  assert.doesNotMatch(html, /\sstyle=|\son[a-z]+=/i);
});
