'use strict';
/**
 * زمانِ تقریبیِ تحویل، زنده و بر اساسِ شلوغی — WEB-04 (lib/storefront-eta.js، مهاجرتِ ۱۹۳).
 *
 *   - حسابِ خالص: همیشه بازه (دو عددِ جدا، گرد به ۵ دقیقه)، میانه، صف و پیک.
 *   - پرچمِ `shop.storefront.eta` خاموش ⇒ ویترین، پیگیری و `GET /:slug/eta` همان پاسخِ امروز؛ تنظیمِ مالک ۴۰۳.
 *   - بی سابقه ⇒ پیش‌فرض‌ها (۱۵ آماده‌سازی + ۲۰ راه)؛ میانهٔ «قبول ⇒ بسته‌بندی» از دادهٔ واقعی (آزمایشی،
 *     کهنه‌تر از ۱۴ روز و کمتر از ۳ نمونه کنار).
 *   - صف: سفارش‌های باز و بسته‌بندی‌نشده (آزمایشی و زمان‌بندی‌شدهٔ آینده نه)؛ «شلوغم»؛ ظرفیتِ پیک‌ها.
 *   - دقیقهٔ راهِ ناحیه از تنظیمِ مالک؛ ناحیهٔ بیگانه ۴۰۰.
 *   - پیگیری: زمانِ تقریبیِ همان سفارش با هر مرحله از نو؛ پایان ⇒ null.
 *   - پاسخِ عمومی هیچ دادهٔ مشتری، شمارِ صف یا پیک ندارد؛ دکانِ بی اتصالِ تأییدشده ⇒ بی عدد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.RATE_STORE_CODE_MAX = '500';
process.env.STORE_ETA_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { ulid } = require('../src/lib/ulid');
const { resetAll } = require('../src/middleware/ratelimit');
const eta = require('../src/lib/storefront-eta');
const config = require('../src/config');

const MIN = 60_000;
const DAY = 24 * 60 * MIN;
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
test.after(async () => { require('../src/lib/order-desk').stop(); await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ روشن با ویترین، یک کالا و دستگاهی که همین حالا همگام شد ⇒ «همین لحظه می‌پذیرد». */
async function liveStore(label, { on = true, flags = [] } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id };
  for (const key of ['shop.orders', 'shop.storefront', ...flags]) await setFlag(s.shopId, key, true);
  if (on) await setFlag(s.shopId, eta.FLAG, true);
  s.slug = `eta-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true },
    { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const dev = `dev-${uniq()}`;
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function ticketFor(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

const PHONE = '0791113555';
async function checkout(s, over = {}) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتریِ محرمانه', phone: PHONE },
    address: { text: 'کوچهٔ محرمانهٔ هفتم', landmark: 'پشتِ مسجدِ سبز' }, lines: [{ productId: 'rice', qtyMilli: 1000 }], ...over,
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, PHONE) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: r.body.order.token, id: (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1', [s.shopId])).id };
}
const track = async (s, token) => {
  const r = await h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(token)}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const move = async (s, id, body) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, body, { token: s.token });
  assert.equal(r.status, 200, `${body.to}: ${JSON.stringify(r.body)}`);
};
const publicEta = async (s) => {
  const r = await h.get(`/api/store/${s.slug}/eta`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.eta;
};
const owner = (s) => h.get('/api/shop/storefront/eta', { token: s.token });
const save = (s, body) => h.put('/api/shop/storefront/eta', body, { token: s.token });
const span = (e) => e && { minMinutes: e.minMinutes, maxMinutes: e.maxMinutes };

//  ── دادهٔ نمونه (fixture) مستقیم در جدول‌ها: سفارش، رویدادِ وضعیت، پیک و مأموریت ──
let orderNo = 900000;
async function fixtureOrder(shopId, { status = 'new', fulfillment = 'pickup', createdAt = now(), isTest = false, scheduledFor = null } = {}) {
  const id = ulid();
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, is_test, status, fulfillment, scheduled_for, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'fx','phone',$5,$6,$7,$8,$9,$9)`,
    [id, shopId, ++orderNo, `fx-${id}`, isTest, status, fulfillment, scheduledFor, createdAt]
  );
  return id;
}
async function fixtureEvent(shopId, orderId, seqNo, to, at) {
  await query(
    `INSERT INTO online_order_events (id, order_id, shop_id, seq, kind, from_status, to_status, actor_type, created_at)
     VALUES ($1,$2,$3,$4,'status','',$5,'user',$6)`,
    [ulid(), orderId, shopId, seqNo, to, at]
  );
}
/** سفارشِ بسته‌بندی‌شده‌ای که `prepMin` دقیقه پس از قبول بسته شد، `ago` پیش. */
async function packedAfter(shopId, prepMin, { ago = 60 * MIN, isTest = false } = {}) {
  const packedAt = now() - ago;
  const id = await fixtureOrder(shopId, { status: 'packed', createdAt: packedAt - prepMin * MIN - MIN, isTest });
  await fixtureEvent(shopId, id, 1, 'accepted', packedAt - prepMin * MIN);
  await fixtureEvent(shopId, id, 2, 'packed', packedAt);
  return id;
}

// ════════════════════════════════════════════════════════════════════

test('حسابِ خالص: همیشه بازه و دو عددِ جدا؛ میانه؛ صف؛ ظرفیتِ پیک', () => {
  assert.deepEqual(eta.range(35), { minMinutes: 30, maxMinutes: 45 });
  assert.deepEqual(eta.range(15), { minMinutes: 10, maxMinutes: 20 });
  assert.deepEqual(eta.range(2), { minMinutes: 1, maxMinutes: 3 });
  for (const e of [0, 1, 3, 9, 14, 15, 44, 61, 200, 5000]) {
    const r = eta.range(e);
    assert.ok(r.minMinutes >= 1 && r.minMinutes < r.maxMinutes && r.maxMinutes <= eta.MAX_MINUTES, `${e}: ${JSON.stringify(r)}`);
    if (Math.round(e) >= 15) assert.ok(r.minMinutes % 5 === 0 && r.maxMinutes % 5 === 0, `${e}: از ۱۵ دقیقه گرد به ۵`);
  }
  assert.equal(eta.median([50, 10, 30]), 30);
  assert.equal(eta.median([10, 20, 30, 40]), 25);
  assert.equal(eta.queueWait(0, 2, 15), 0);
  assert.equal(eta.queueWait(1, 2, 15), 0);
  assert.equal(eta.queueWait(4, 2, 15), 30);
  assert.equal(eta.courierWait({ cap: 0 }, 20, 5), 0, 'دکانِ بی پیکِ خودش ⇒ بی انتظارِ پیک');
  assert.equal(eta.courierWait({ cap: 2 }, 20, 1), 0);
  assert.equal(eta.courierWait({ cap: 2 }, 20, 2), 20);
  assert.equal(eta.courierWait({ cap: 2 }, 20, 4), 40);
});

test('پرچم خاموش: ویترین، پیگیری و GET /eta همان پاسخِ امروز؛ تنظیمِ مالک ۴۰۳ و بی ردیف', async () => {
  const s = await liveStore('خاموش', { on: false });
  const o = await checkout(s);
  const store = await h.get(`/api/store/${s.slug}`);
  assert.equal(store.status, 200);
  assert.ok(!(store.body.features && 'eta' in store.body.features), JSON.stringify(store.body.features));
  const order = await track(s, o.token);
  assert.ok(!('eta' in order), 'پیگیری بی کلیدِ eta');
  const [route, unknown] = [await h.get(`/api/store/${s.slug}/eta`), await h.get(`/api/store/${s.slug}/no-such-door`)];
  assert.equal(route.status, unknown.status);
  assert.deepEqual(route.body, unknown.body, 'همان پاسخِ مسیرِ ناشناخته');
  assert.equal(route.headers.get('cache-control'), unknown.headers.get('cache-control'));
  assert.deepEqual((await owner(s)).body, { enabled: false });
  const put = await save(s, { prepMinutes: 30 });
  assert.equal(put.status, 403);
  assert.equal(put.body.error.code, 'storefront_eta_disabled');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_eta WHERE shop_id=$1', [s.shopId])).n, 0);
  //  روشن و دوباره خاموش ⇒ همان بایت‌ها
  const strip = (b) => { const x = structuredClone(b); delete x.store.status.localTime; return JSON.stringify(x); };
  const before = strip(store.body);
  await setFlag(s.shopId, eta.FLAG, true);
  assert.equal((await h.get(`/api/store/${s.slug}`)).body.features.eta, true);
  assert.ok('eta' in (await track(s, o.token)));
  await setFlag(s.shopId, eta.FLAG, false);
  assert.equal(strip((await h.get(`/api/store/${s.slug}`)).body), before);
  assert.equal(JSON.stringify(await track(s, o.token)), JSON.stringify(order));
});

test('بی سابقه ⇒ پیش‌فرض‌ها (۱۵ + ۲۰)؛ کمتر از ۳ نمونه هم پیش‌فرض؛ پاسخِ عمومی کشِ کوتاه دارد', async () => {
  const s = await liveStore('پیش‌فرض');
  const r = await h.get(`/api/store/${s.slug}/eta`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store', 'کشِ سرورِ آزمون خاموش (۰) ⇒ no-store');
  assert.equal(r.body.eta.available, true);
  assert.equal(r.body.eta.approximate, true);
  assert.deepEqual(span(r.body.eta.delivery), { minMinutes: 30, maxMinutes: 45 });
  assert.deepEqual(span(r.body.eta.pickup), { minMinutes: 10, maxMinutes: 20 });
  assert.deepEqual(r.body.eta.zones, []);
  await packedAfter(s.shopId, 60);
  await packedAfter(s.shopId, 60);
  const o = (await owner(s)).body;
  assert.equal(o.enabled, true);
  assert.deepEqual(o.signals.samples, 2);
  assert.equal(o.signals.prepFromHistory, false);
  assert.equal(o.signals.prepMinutes, 15, 'دو نمونه کم است ⇒ پیش‌فرضِ مالک');
  assert.deepEqual(span((await publicEta(s)).pickup), { minMinutes: 10, maxMinutes: 20 });
  //  کشِ همین نمونهٔ سرور: پاسخِ دوم همان، تا پایانِ مهلت؛ پرچمِ خاموش هر بار سنجیده می‌شود
  const keep = config.storefront.etaCacheMs;
  config.storefront.etaCacheMs = 60_000;
  try {
    const sf = await one('SELECT * FROM storefronts WHERE shop_id=$1', [s.shopId]);
    const first = await eta.cachedPublic(sf);
    for (let i = 0; i < 6; i++) await fixtureOrder(s.shopId);
    assert.deepEqual(await eta.cachedPublic(sf), first, 'درونِ مهلت همان پاسخ');
    const pub = await h.get(`/api/store/${s.slug}/eta`);
    assert.equal(pub.headers.get('cache-control'), 'public, max-age=60');
    eta._forget();
    assert.notDeepEqual(span((await eta.cachedPublic(sf)).pickup), span(first.pickup), 'پس از مهلت از نو');
    await setFlag(s.shopId, eta.FLAG, false);
    assert.equal(await eta.cachedPublic(sf), undefined, 'خاموش ⇒ بی کش');
  } finally {
    config.storefront.etaCacheMs = keep;
    eta._forget();
  }
});

test('میانهٔ «قبول ⇒ بسته‌بندی» از دادهٔ واقعی؛ آزمایشی و کهنه‌تر از ۱۴ روز کنار', async () => {
  const s = await liveStore('میانه');
  for (const m of [10, 30, 50]) await packedAfter(s.shopId, m);
  await packedAfter(s.shopId, 2, { isTest: true });
  await packedAfter(s.shopId, 1, { ago: 15 * DAY });
  const sample = await eta.prepSample(s.shopId, now());
  assert.deepEqual(sample, { samples: 3, median: 30 });
  const o = (await owner(s)).body;
  assert.equal(o.signals.prepMinutes, 30);
  assert.equal(o.signals.prepFromHistory, true);
  const e = await publicEta(s);
  assert.deepEqual(span(e.pickup), eta.range(30));
  assert.deepEqual(span(e.pickup), { minMinutes: 25, maxMinutes: 40 });
  assert.deepEqual(span(e.delivery), eta.range(50));
});

test('صف: سفارش‌های باز و بسته‌بندی‌نشده زمان را بالا می‌برند؛ آزمایشی، آینده و بسته‌بندی‌شده نه', async () => {
  const s = await liveStore('صف');
  for (const status of ['new', 'new', 'accepted', 'picking']) await fixtureOrder(s.shopId, { status });
  await fixtureOrder(s.shopId, { status: 'packed' });
  await fixtureOrder(s.shopId, { status: 'delivered' });
  await fixtureOrder(s.shopId, { isTest: true });
  await fixtureOrder(s.shopId, { scheduledFor: now() + DAY });
  assert.equal(await eta.queueCount(s.shopId, now()), 4);
  let e = await publicEta(s);
  //  ۴ سفارشِ جلوتر، ۲ هم‌زمان ⇒ ۲ دورِ ۱۵ دقیقه‌ای
  assert.deepEqual(span(e.pickup), eta.range(15 + 30));
  assert.deepEqual(span(e.delivery), eta.range(15 + 30 + 20));
  assert.deepEqual(span(e.delivery), { minMinutes: 55, maxMinutes: 85 });
  const put = await save(s, { parallel: 4 });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  e = await publicEta(s);
  assert.deepEqual(span(e.delivery), eta.range(15 + 15 + 20), 'چهار هم‌زمان ⇒ یک دور');
});

test('«شلوغم» و ظرفیتِ پیک‌ها: دقیقه‌های افزوده و انتظارِ پیک', async () => {
  const s = await liveStore('شلوغ', { flags: ['shop.orders.desk'] });
  const busy = await h.post('/api/shop/orders/desk/busy', { eventId: `ev-${uniq()}-busy`, minutes: 30, extraMinutes: 20 }, { token: s.token });
  assert.equal(busy.status, 200, JSON.stringify(busy.body));
  let e = await publicEta(s);
  assert.deepEqual(span(e.pickup), eta.range(15 + 20));
  //  یک پیکِ فعال (ظرفیت ۱) با یک مأموریتِ زنده ⇒ یک «راه» انتظار
  const courierId = ulid();
  await query(`INSERT INTO shop_couriers (id, shop_id, user_id, name, created_at, updated_at) VALUES ($1,$2,'u-courier','پیک',$3,$3)`,
    [courierId, s.shopId, now()]);
  const busyOrder = await fixtureOrder(s.shopId, { status: 'out_for_delivery', fulfillment: 'delivery' });
  await query(
    `INSERT INTO delivery_assignments (id, shop_id, order_id, attempt_no, mode, courier_id, status, created_at, updated_at)
     VALUES ($1,$2,$3,1,'shop_courier',$4,'assigned',$5,$5)`, [ulid(), s.shopId, busyOrder, courierId, now()]);
  assert.deepEqual(await eta.courierLoad(s.shopId), { couriers: 1, cap: 1, live: 1, waiting: 0 });
  e = await publicEta(s);
  assert.deepEqual(span(e.delivery), eta.range(15 + 20 + 20 + 20));
  assert.deepEqual(span(e.pickup), eta.range(15 + 20), 'دریافت از دکان پیک نمی‌خواهد');
});

test('ناحیه: دقیقهٔ راهِ هر ناحیه از تنظیمِ مالک؛ ناحیهٔ بیگانه و عددِ بیرون از بازه ۴۰۰', async () => {
  const s = await liveStore('ناحیه', { flags: ['shop.storefront.zones'] });
  const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
  const z = await h.put('/api/shop/storefront/delivery', { zones: [{ name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0 }] },
    { token: s.token });
  assert.equal(z.status, 200, JSON.stringify(z.body));
  const zoneId = (await h.get(`/api/store/${s.slug}`)).body.store.delivery.zones[0].id;
  let e = await publicEta(s);
  assert.deepEqual(e.zones, [{ id: zoneId, ...eta.range(35) }], 'بی تنظیم ⇒ راهِ پیش‌فرض');
  const put = await save(s, { travelMinutes: 25, zoneTravel: { [zoneId]: 40 } });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.deepEqual(put.body.settings.zoneTravel, { [zoneId]: 40 });
  e = await publicEta(s);
  assert.deepEqual(e.zones, [{ id: zoneId, ...eta.range(15 + 40) }]);
  assert.deepEqual(span(e.delivery), eta.range(15 + 25));
  const alien = await save(s, { zoneTravel: { '01ZZZZZZZZZZZZZZZZZZZZZZZZ': 10 } });
  assert.equal(alien.status, 400);
  assert.equal(alien.body.error.code, 'bad_zone');
  assert.equal((await save(s, { prepMinutes: 0 })).status, 400);
  assert.equal((await save(s, { show: 'yes' })).status, 400);
  const stale = await save(s, { prepMinutes: 20, expectedVersion: 0 });
  assert.equal(stale.status, 409);
  assert.equal(stale.body.error.code, 'version_conflict');
  const ok = await save(s, { prepMinutes: 20, expectedVersion: put.body.settings.version });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.settings.zoneTravel, { [zoneId]: 40 }, 'فیلدِ نیامده همان می‌ماند');
});

test('پیگیری: زمانِ تقریبیِ همان سفارش با هر مرحله از نو؛ پایان ⇒ null', async () => {
  const s = await liveStore('پیگیری');
  const o = await checkout(s);
  let e = (await track(s, o.token)).eta;
  assert.equal(e.stage, 'waiting');
  assert.equal(e.approximate, true);
  assert.deepEqual(span(e), eta.range(15 + 20, 5), 'در انتظارِ قبول: سرِ بالا بازتر');
  await move(s, o.id, { to: 'accepted', readyInMinutes: 25 });
  e = (await track(s, o.token)).eta;
  assert.equal(e.stage, 'preparing');
  assert.deepEqual(span(e), eta.range(25 + 20), 'زمانِ آمادهٔ خودِ دکان + راه');
  await move(s, o.id, { to: 'picking' });
  assert.equal((await track(s, o.token)).eta.stage, 'preparing');
  await move(s, o.id, { to: 'packed' });
  e = (await track(s, o.token)).eta;
  assert.equal(e.stage, 'courier');
  assert.deepEqual(span(e), eta.range(20));
  await move(s, o.id, { to: 'out_for_delivery' });
  e = (await track(s, o.token)).eta;
  assert.equal(e.stage, 'on_the_way');
  assert.deepEqual(span(e), eta.range(20));
  await move(s, o.id, { to: 'delivered' });
  assert.equal((await track(s, o.token)).eta, null);

  //  بی «زمانِ آماده»: باقیِ میانه + صفِ جلوتر؛ دریافت از دکان ⇒ «آماده» پس از بسته‌بندی
  const first = await checkout(s, { fulfillment: 'pickup', address: undefined });
  const second = await checkout(s, { fulfillment: 'pickup', address: undefined });
  await move(s, first.id, { to: 'accepted' });
  await move(s, second.id, { to: 'accepted' });
  assert.deepEqual(span((await track(s, second.token)).eta), eta.range(15), 'یک سفارشِ جلوتر با دو هم‌زمان ⇒ بی دورِ افزوده');
  await save(s, { parallel: 1 });
  assert.deepEqual(span((await track(s, second.token)).eta), eta.range(15 + 15), 'یک هم‌زمان ⇒ یک دورِ انتظار');
  await move(s, first.id, { to: 'picking' });
  await move(s, first.id, { to: 'packed' });
  e = (await track(s, first.token)).eta;
  assert.equal(e.stage, 'ready');
  assert.deepEqual(span(e), { minMinutes: 1, maxMinutes: 3 });

  //  مالک نمایش را خاموش کرد ⇒ پیگیری `eta: null` و ویترین بی عدد
  await save(s, { show: false });
  assert.equal((await track(s, second.token)).eta, null);
  assert.deepEqual(await publicEta(s), { available: false, reason: 'hidden' });
});

test('حریم: پاسخِ عمومی فقط بازهٔ دقیقه‌هاست — نه مشتری، نه سفارش، نه شمارِ صف و پیک؛ دکانِ بی اتصال ⇒ بی عدد', async () => {
  const s = await liveStore('حریم');
  const o = await checkout(s);
  const r = await h.get(`/api/store/${s.slug}/eta`);
  const raw = JSON.stringify(r.body);
  for (const secret of ['مشتریِ محرمانه', PHONE, '791113555', 'کوچهٔ محرمانهٔ هفتم', 'پشتِ مسجدِ سبز', o.id, o.token, s.shopId]) {
    assert.ok(!raw.includes(secret), `نشت: ${secret}`);
  }
  assert.deepEqual(Object.keys(r.body), ['eta']);
  assert.deepEqual(Object.keys(r.body.eta).sort(), ['approximate', 'available', 'delivery', 'pickup', 'updatedAt', 'zones']);
  for (const k of ['delivery', 'pickup']) assert.deepEqual(Object.keys(r.body.eta[k]).sort(), ['maxMinutes', 'minMinutes']);
  assert.ok(!/queue|courier|samples|prep/i.test(raw), raw);
  //  دکانی که دستگاهش ۲۰ دقیقه است دیده نشده ⇒ «فعلاً قابل تخمین نیست»، نه عددِ ساختگی
  await query("UPDATE sync_devices SET last_seen_at = $2 WHERE account_id = $1", [s.shopId, now() - 20 * MIN]);
  const e = await publicEta(s);
  assert.deepEqual(e, { available: false, reason: 'unconfirmed' });
  assert.equal((await track(s, o.token)).eta, null, 'سفارشِ در انتظارِ قبول در دکانِ بی اتصال ⇒ بی عدد');
});

test('مالک: نمایِ تنظیم فقط برای خودِ دکان؛ شاگرد بی settings.write نمی‌نویسد', async () => {
  const s = await liveStore('مالک');
  const v = await owner(s);
  assert.equal(v.status, 200, JSON.stringify(v.body));
  assert.deepEqual(v.body.settings, { ...eta.DEFAULTS, zoneTravel: {}, version: 0, updatedAt: null });
  assert.equal(v.body.permissions.manage, true);
  assert.equal(v.body.preview.available, true);
  const code = (await h.post('/api/shop/staff-code', {}, { token: s.token })).body.code;
  const staff = await h.newUser('شاگرد');
  assert.ok((await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken })).status < 300);
  const denied = await h.put('/api/shop/storefront/eta', { prepMinutes: 30 }, { token: staff.accessToken });
  assert.equal(denied.status, 403, JSON.stringify(denied.body));
  assert.equal((await h.get('/api/shop/storefront/eta')).status, 401);
});
