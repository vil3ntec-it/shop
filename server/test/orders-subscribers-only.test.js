'use strict';
/**
 * تصمیمِ صاحب محصول (۱۴۰۵/۰۸/۰۸): سفارشِ آنلاینِ تازهٔ مشتری **فقط** برای دکانِ با اشتراکِ فعال —
 * پشتِ پرچمِ `shop.orders.subscribers_only` (پیش‌فرض خاموش). `orders.intakeOf`، درِ `orders.create`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پرچم خاموش ⇒ همان رفتارِ main (دورهٔ آزمایشی سفارش می‌گیرد، پاسخِ ویترین بی `ordering`).
 *   ۲) پرچم روشن: اشتراکِ فعال ⇒ سفارش ۲۰۱ و در بازارچه. دکانی که ویترینش طبقِ SUB-10 باز است ولی
 *      اشتراکِ پولیِ فعال ندارد (دورهٔ آزمایشی) ⇒ ۴۰۳ `store_not_accepting_orders` با پیامِ فارسی،
 *      **هیچ ردیف**، ویترین فقط‌خواندنی با `ordering.accepting: false`، و بیرون از فهرست/جست‌وجوی بازارچه.
 *      ⛔ SUB-10 (#621) دست نمی‌خورد: بی اشتراک، منقضی (زمان و وضعیت)، معلق، لغوشده و بی کلیدِ ویترین
 *      ⇒ همان ۴۰۴ ِ `store_not_found` پیش از هر سنجشِ نشست و شماره، با پرچم یا بی پرچم.
 *   ۳) سفارشِ پیشینِ دکانِ آزمایشی لغو نمی‌شود: پیگیری، تکرارِ همان کلید (۲۰۰ همان سفارش، نه ۴۰۳)،
 *      لغوِ مشتری، و گذارِ کارمند تا «تحویل شد». اشتراکِ تمام‌شده با پرچمِ روشن همان SUB-10 است.
 *   ۴) سفارشِ دستیِ کارمند به این دروازه بسته نیست (همان قاعدهٔ اشتراکِ پیشین)؛ برنامهٔ دکان
 *      `subscribersOnly`/`subscribed` را از `/api/shop/storefront/config` می‌گیرد.
 *   ۵) تصمیم فقط با ساعتِ سرور؛ هیچ سرآیند یا بدنه‌ای از مشتری آن را عوض نمی‌کند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

const DAY = 24 * 60 * 60 * 1000;
const GATE = 'shop.orders.subscribers_only';
const MSG = 'این فروشگاه فعلاً سفارشِ آنلاین نمی‌پذیرد';
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

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن (دورهٔ آزمایشی) با ویترین، دو کالای منتشرشده و ردیفِ بازارچه. */
async function liveStore(label) {
  //  نامِ حالت (مثلاً «… اشتراک …») به نمای عمومی نمی‌رود تا آزمونِ «بی حرفی از اشتراک» راست بماند
  const u = await h.newUser(`صاحب ${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(o.shopId, k, true);
  const slug = `g-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${slug}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
  ], `dev-${uniq()}`);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  }
  assert.equal((await h.put('/api/shop/market', { listed: true, category: 'grocery' }, { token: o.token })).status, 200);
  return { ...o, slug };
}

/** اشتراکِ واقعی از درِ مدیر؛ دورهٔ آزمایشی پیش‌تر تمام شده. */
async function subscribe(s, features) {
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30, ...(features ? { features } : {}) }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  s.subId = g.body.subscription.id;
}
const setStatus = (s, status) => h.post(`/api/admin/subscriptions/${s.subId}/status`, { status }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

/** ویترین طبقِ SUB-10 باز، ولی بی اشتراکِ پولیِ فعال ⇒ فقط‌خواندنی (روی دکانی که `liveStore` ساخته). */
const OPEN_UNPAID = {
  'دورهٔ آزمایشی (هرگز اشتراک نداشته)': async () => {},
};
/** ویترینی که SUB-10 می‌بندد (۴۰۴) — پرچمِ این دروازه آن را باز نمی‌کند. */
const CLOSED = {
  'بی اشتراک، آزمایشی تمام‌شده': (s) => query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]),
  'منقضی با گذشتِ زمان': async (s) => { await subscribe(s); await query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [s.subId, Date.now() - 1000]); },
  'منقضی (وضعیتِ expired)': async (s) => { await subscribe(s); await setStatus(s, 'expired'); },
  'معلق (suspended)': async (s) => { await subscribe(s); await setStatus(s, 'suspended'); },
  'لغوشده (cancelled)': async (s) => { await subscribe(s); await setStatus(s, 'cancelled'); },
  //  اشتراکِ فعال، ولی پلن ویترین را ندارد: `orders.create` به‌تنهایی رد نمی‌کرد
  'اشتراکِ فعال بی کلیدِ ویترین': (s) => subscribe(s, ['online_orders']),
};

let phoneSeq = 0;
const nextPhone = () => `07955${String(++phoneSeq).padStart(5, '0')}`;
async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
const cart = (phone) => ({
  fulfillment: 'pickup', customer: { name: 'مشتری', phone },
  lines: [{ productId: 'rice', qtyMilli: 1000 }, { productId: 'oil', qtyMilli: 1000 }],
});
/** ثبت با شمارهٔ تأییدشده؛ `key`/`phone` برای تکرارِ همان درخواست. */
async function checkout(s, { key = `ck-${uniq()}-key`, phone = nextPhone(), headers = {} } = {}) {
  const ticket = await ticketFor(s.slug, phone);
  const r = await h.post(`/api/store/${s.slug}/checkout`, cart(phone), {
    headers: { 'Idempotency-Key': key, 'X-Store-Ticket': ticket, ...headers },
  });
  return Object.assign(r, { key, phone });
}
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;
const marketSlugs = async () => (await h.get('/api/market/shops')).body.shops.map((x) => x.slug);
const searchSlugs = async () => {
  const r = await h.get(`/api/market/search?q=${encodeURIComponent('برنج')}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.products.map((x) => x.shop.slug);
};

function assertRejected(r) {
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'store_not_accepting_orders');
  assert.equal(r.body.error.message, MSG);
  //  نمای عمومی هیچ حرفی از اشتراک یا پولِ دکان نمی‌زند
  assert.ok(!/subscri|اشتراک/.test(JSON.stringify(r.body)), JSON.stringify(r.body));
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پرچمِ خاموش ⇒ رفتارِ پیشین
   ══════════════════════════════════════════════════════════════════ */

test('پرچمِ خاموش: دکانِ آزمایشی همچنان سفارش می‌گیرد و نمای عمومی «می‌پذیرد» است', async () => {
  const s = await liveStore('خاموش');
  const pub = await h.get(`/api/store/${s.slug}`);
  assert.equal(pub.status, 200);
  assert.equal('ordering' in pub.body.store, false, 'پرچمِ خاموش ⇒ پاسخِ main، بی `ordering`');
  const r = await checkout(s);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const cfg = (await h.get('/api/shop/storefront/config', { token: s.token })).body;
  assert.equal(cfg.subscribersOnly, false);
  assert.equal(cfg.accepting, true);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) پرچمِ روشن
   ══════════════════════════════════════════════════════════════════ */

test('پرچمِ روشن + اشتراکِ فعال: سفارش ۲۰۱، نمای عمومی «می‌پذیرد»، در بازارچه دیده می‌شود', async () => {
  await setFlag('all', 'market.directory', true);
  const s = await liveStore('مشترک');
  await setFlag(s.shopId, GATE, true);
  await subscribe(s);
  assert.deepEqual((await h.get(`/api/store/${s.slug}`)).body.store.ordering, { accepting: true });
  const r = await checkout(s);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(await count(s.shopId), 1);
  assert.ok((await marketSlugs()).includes(s.slug), 'دکانِ مشترک در فهرستِ بازارچه');
  assert.ok((await searchSlugs()).includes(s.slug), 'کالای دکانِ مشترک در جست‌وجو');
  const cfg = (await h.get('/api/shop/storefront/config', { token: s.token })).body;
  assert.equal(cfg.subscribersOnly, true);
  assert.equal(cfg.subscribed, true);
  assert.equal(cfg.accepting, true);
  //  مهلتِ اشتراک (grace) هم فعال است — همان تصمیمِ سرور
  await query('UPDATE subscriptions SET ends_at=$2, grace_days=3 WHERE id=$1', [s.subId, Date.now() - DAY]);
  assert.equal((await checkout(s)).status, 201);
});

for (const [name, apply] of Object.entries(OPEN_UNPAID)) {
  test(`پرچمِ روشن + ${name}: ثبت ۴۰۳ بی ردیف، ویترین فقط‌خواندنی، بیرون از بازارچه`, async () => {
    await setFlag('all', 'market.directory', true);
    const s = await liveStore(name);
    await setFlag(s.shopId, GATE, true);
    await apply(s);

    const pub = await h.get(`/api/store/${s.slug}`);
    assert.equal(pub.status, 200, 'ویترین «پیدا نشد» نمی‌شود — فقط سفارش نمی‌گیرد');
    assert.deepEqual(pub.body.store.ordering, { accepting: false, code: 'store_not_accepting_orders', message: MSG });
    assert.ok(!/subscri|اشتراک/.test(JSON.stringify(pub.body)), 'نمای عمومی حرفی از اشتراک ندارد');
    assert.equal((await h.get(`/api/store/${s.slug}/products`)).status, 200);

    assertRejected(await checkout(s));
    assert.equal(await count(s.shopId), 0, 'هیچ سفارشی ساخته نشد');

    assert.ok(!(await marketSlugs()).includes(s.slug), 'بیرون از فهرستِ بازارچه');
    assert.ok(!(await searchSlugs()).includes(s.slug), 'بیرون از جست‌وجوی بازارچه');

    const cfg = (await h.get('/api/shop/storefront/config', { token: s.token })).body;
    assert.equal(cfg.subscribersOnly, true);
    assert.equal(cfg.subscribed, false);
    assert.equal(cfg.accepting, false);
  });
}

for (const [name, apply] of Object.entries(CLOSED)) {
  test(`پرچمِ روشن + ${name}: SUB-10 دست نمی‌خورد — ویترین و ثبت ۴۰۴ ِ store_not_found، بیرون از بازارچه`, async () => {
    await setFlag('all', 'market.directory', true);
    const s = await liveStore(name);
    await setFlag(s.shopId, GATE, true);
    await apply(s);

    //  ۴۰۴ پیش از هر سنجشِ نشست و شماره: ثبت بی بلیت هم همان ۴۰۴ است، نه ۴۰۱ و نه ۴۰۳
    const phone = nextPhone();
    for (const r of [
      await h.get(`/api/store/${s.slug}`), await h.get(`/api/store/${s.slug}/products`),
      await h.post(`/api/store/${s.slug}/verify/start`, { phone }),
      await h.post(`/api/store/${s.slug}/checkout`, cart(phone), { headers: { 'Idempotency-Key': `ck-${uniq()}-key` } }),
    ]) {
      assert.equal(r.status, 404, JSON.stringify(r.body));
      assert.equal(r.body.error.code, 'store_not_found');
    }
    assert.equal(await count(s.shopId), 0, 'هیچ سفارشی ساخته نشد');
    assert.ok(!(await marketSlugs()).includes(s.slug), 'بیرون از فهرستِ بازارچه');
    assert.ok(!(await searchSlugs()).includes(s.slug), 'بیرون از جست‌وجوی بازارچه');

    const cfg = (await h.get('/api/shop/storefront/config', { token: s.token })).body;
    assert.equal(cfg.enabled, false, 'ویترین طبقِ SUB-10 بسته');
    assert.equal(cfg.subscribersOnly, true);
    assert.equal(cfg.subscribed, false);
    assert.equal(cfg.accepting, false);
  });
}

test('دکان‌به‌دکان: `false` ِ خودِ دکان بر `all` جلو می‌افتد؛ برداشتنِ پرچم همان دم سفارش را باز می‌کند', async () => {
  const s = await liveStore('همگانی');
  await setFlag('all', GATE, true);
  try {
    assertRejected(await checkout(s));
    await setFlag(s.shopId, GATE, false);
    assert.equal((await checkout(s)).status, 201);
  } finally {
    await setFlag('all', GATE, null);
  }
});

test('ساعت و دادهٔ مشتری اثری ندارد: سرآیند، بدنه و `test` ِ مشتری دروازه را باز نمی‌کنند', async () => {
  const s = await liveStore('ساعت');
  await setFlag(s.shopId, GATE, true);
  const future = String(Date.now() + 400 * DAY);
  const phone = nextPhone();
  const ticket = await ticketFor(s.slug, phone);
  const r = await h.post(`/api/store/${s.slug}/checkout`, { ...cart(phone), test: true, subscribed: true, source: 'phone' }, {
    headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket, Date: new Date(Number(future)).toUTCString(), 'X-Client-Time': future },
  });
  assertRejected(r);
  assert.equal(await count(s.shopId), 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) سفارشِ پیشین جلو می‌رود
   ══════════════════════════════════════════════════════════════════ */

test('دورهٔ آزمایشی + پرچمِ روشن: سفارشِ پیشین جلو می‌رود — پیگیری، تکرارِ همان کلید، لغوِ مشتری و گذارِ کارمند تا «تحویل شد»', async () => {
  const s = await liveStore('پیشین');
  //  پیش از روشن شدنِ دروازه، دکانِ آزمایشی سفارش می‌گیرد (همان main)
  const first = await checkout(s);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const second = await checkout(s);
  assert.equal(second.status, 201, JSON.stringify(second.body));

  await setFlag(s.shopId, GATE, true);
  assertRejected(await checkout(s));

  //  تکرارِ همان درخواست (همان کلید و بدنه) ⇒ همان سفارش، نه ۴۰۳
  const ticket = await ticketFor(s.slug, first.phone);
  const again = await h.post(`/api/store/${s.slug}/checkout`, cart(first.phone), {
    headers: { 'Idempotency-Key': first.key, 'X-Store-Ticket': ticket },
  });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.order.token, first.body.order.token);

  //  پیگیری و لغوِ مشتری
  const track = await h.get(`/api/store/${s.slug}/orders/${first.body.order.token}`);
  assert.equal(track.status, 200, JSON.stringify(track.body));
  assert.equal(track.body.order.status, 'new');
  const cancel = await h.post(`/api/store/${s.slug}/orders/${second.body.order.token}/cancel`, {});
  assert.equal(cancel.status, 200, JSON.stringify(cancel.body));
  assert.equal(cancel.body.order.status, 'cancelled_by_customer');

  //  بخشِ «سفارش‌ها» پیداست و کارمند سفارش را تا پایان می‌برد
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.entitled, true, 'دورهٔ آزمایشی کلیدِ اشتراک را دارد (SUB-10)');
  assert.equal(cfg.enabled, true);
  assert.equal(cfg.subscribersOnly, true);
  assert.equal(cfg.subscribed, false);
  assert.equal(cfg.accepting, false);
  const list = (await h.get('/api/shop/orders?tab=new', { token: s.token })).body.items;
  const id = list.find((o) => o.orderNo === first.body.order.orderNo).id;
  let from = 'new';
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const r = await h.post(`/api/shop/orders/${id}/transition`, { to, from }, { token: s.token });
    assert.equal(r.status, 200, `${from} → ${to}: ${JSON.stringify(r.body)}`);
    from = to;
  }
  assert.equal((await h.get(`/api/store/${s.slug}/orders/${first.body.order.token}`)).body.order.status, 'picked_up');
  assert.equal(await count(s.shopId), 2, 'هیچ سفارشِ تازه‌ای ساخته نشد و هیچ سفارشی پاک نشد');
});

test('پرچمِ خاموش و اشتراکِ تمام‌شده: همان قاعدهٔ پیشین (گذار ۴۰۳، ویترین ۴۰۴)', async () => {
  const s = await liveStore('پیشینِ خاموش');
  await subscribe(s);
  const first = await checkout(s);
  assert.equal(first.status, 201);
  await setStatus(s, 'expired');
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 404);
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.enabled, false);
  const id = (await h.get('/api/shop/orders?tab=new', { token: s.token })).body.items[0].id;
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted', from: 'new' }, { token: s.token });
  assert.equal(r.status, 403);
  assert.match(r.body.error.code, /^subscription_(expired|required)$/);
});

test('پرچمِ روشن و اشتراکِ تمام‌شده: همان SUB-10 (ویترین و پیگیری ۴۰۴، بخشِ «سفارش‌ها» پنهان، گذار ۴۰۳)', async () => {
  const s = await liveStore('پیشینِ روشن');
  await setFlag(s.shopId, GATE, true);
  await subscribe(s);
  const first = await checkout(s);
  assert.equal(first.status, 201, JSON.stringify(first.body));
  await query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [s.subId, Date.now() - 1000]);
  for (const url of [`/api/store/${s.slug}`, `/api/store/${s.slug}/orders/${first.body.order.token}`]) {
    const r = await h.get(url);
    assert.equal(r.status, 404, `${url}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'store_not_found');
  }
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.enabled, false);
  const id = (await h.get('/api/shop/orders?tab=new', { token: s.token })).body.items[0].id;
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to: 'accepted', from: 'new' }, { token: s.token });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.match(r.body.error.code, /^subscription_(expired|required)$/);
});

test('دکانِ بی اشتراک و بی سفارشِ پیشین: بخشِ «سفارش‌ها» همان‌طور پنهان', async () => {
  const s = await liveStore('بی‌سفارش');
  await setFlag(s.shopId, GATE, true);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [s.shopId, Date.now() - 400 * DAY]);
  const cfg = (await h.get('/api/shop/orders/config', { token: s.token })).body;
  assert.equal(cfg.entitled, false);
  assert.equal(cfg.enabled, false);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) سفارشِ دستیِ کارمند
   ══════════════════════════════════════════════════════════════════ */

test('سفارشِ دستیِ کارمند در دورهٔ آزمایشی به این دروازه بسته نیست', async () => {
  const s = await liveStore('دستی');
  await setFlag(s.shopId, GATE, true);
  const r = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد', phone: '0791234567' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 1000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `staff-${uniq()}-key` } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
});
