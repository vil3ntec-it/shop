'use strict';
/**
 * امتیازِ دوطرفهٔ تحویل — DLV-18 (پشتِ پرچمِ خاموشِ `shop.delivery_ratings`، مهاجرتِ ۱۸۵).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ورودی و جمع همان بردارِ `core/test-vectors/delivery-ratings-v1.json` است که `DeliveryRating.kt` می‌خواند.
 *   ۲) پرچمِ خاموش ⇒ پیک ۴۰۴ `ratings_disabled`، مشتری ۴۰۴ «سفارش پیدا نشد»، دکان ۴۰۳ و هیچ ردیفی.
 *   ۳) فقط پس از «تحویل شد» ِ پیکِ دکان و تا ۷ روز؛ از هر سو یک بار (همان ⇒ `replayed`، دیگر ⇒ ۴۰۹)؛ برچسبِ
 *      سوی دیگر رد؛ پیک مأموریتِ پیکِ دیگر را «پیدا نشد» می‌بیند؛ توکنِ نادرست ۴۰۴.
 *   ۴) فقط صاحب و مدیر جمع را می‌بینند (شاگرد ۴۰۳)؛ بی نام و شمارهٔ مشتری؛ دکانِ دیگر چیزی نمی‌بیند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const R = require('../src/lib/delivery-ratings');
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
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-rate' },
  body: { device_id: 'dev-rate', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function member(shopId, role) {
  const u = await h.newUser(`عضو ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return u;
}

async function liveShop(label, { ratings = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `r-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await setFlag(s.shopId, k, true);
  if (ratings) await setFlag(s.shopId, R.FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [op('rice', { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  const couriers = [];
  for (const name of ['پیکِ اول', 'پیکِ دوم']) {
    const c = await member(s.shopId, 'staff');
    const r = await h.post('/api/shop/delivery/couriers', { userId: c.user.id, name, phone: '0799999999' }, { token: s.token });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    couriers.push({ token: c.accessToken, id: r.body.courier.id });
  }
  return { ...s, courier: couriers[0], other: couriers[1] };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function events(courier, list) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events: list }, { token: courier.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}

/** سفارشِ ویترین ⇒ بسته ⇒ سپرده به `courier`؛ `deliver` ⇒ تا «تحویل شد». ⇒ `{orderId, token, assignmentId}` */
async function delivery(s, courier, { deliver = true } = {}) {
  const phone = '0791112233';
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتریِ امتیاز', phone }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const orderId = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${orderId}/transition`, { to }, { token: s.token })).status, 200);
  }
  const a = await h.post(`/api/shop/delivery/orders/${orderId}/assign`,
    { mode: 'shop_courier', courierId: courier.id, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const assignmentId = a.body.assignment.id;
  await events(courier, [ev(assignmentId, 'assigned'), ev(assignmentId, 'picked_up')]);
  if (deliver) {
    const due = Number((await one('SELECT expected_collect_minor FROM delivery_assignments WHERE id=$1', [assignmentId])).expected_collect_minor);
    await events(courier, [ev(assignmentId, 'delivered', { cashCollectedMinor: due })]);
  }
  return { orderId, token: r.body.order.token, assignmentId };
}

const rateCourier = (courier, id, body) => h.post(`/api/courier/missions/${id}/rating`, body, { token: courier.token });
const customerGet = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}/rating`);
const customerPost = (s, token, body) => h.post(`/api/store/${s.slug}/orders/${token}/rating`, body);
const code = (r) => r.body?.error?.code;

/* ══════════════════════════════════════════════════════════════════ */

test('DLV-18 delivery-ratings-v1: ورودی و جمع — همان بردارِ DeliveryRating.kt', () => {
  const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'delivery-ratings-v1.json'), 'utf8'));
  assert.equal(V.version, 1);
  assert.deepEqual(V.tags, R.TAGS);
  assert.equal(V.maxTags, R.MAX_TAGS);
  assert.ok(V.parse.length >= 12 && V.summarize.length >= 4);
  for (const c of V.parse) assert.deepEqual(R.parse(c.body, c.rater), c.want, c.name);
  for (const c of V.summarize) assert.deepEqual(R.summarize(c.list), c.want, c.name);
});

test('DLV-18 خاموش: پیک ۴۰۴، مشتری «سفارش پیدا نشد»، دکان ۴۰۳؛ هیچ ردیفی', async () => {
  const s = await liveShop('امتیاز-خاموش', { ratings: false });
  const d = await delivery(s, s.courier);
  let r = await rateCourier(s.courier, d.assignmentId, { stars: 5 });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(code(r), 'ratings_disabled');
  r = await customerGet(s, d.token);
  assert.equal(r.status, 404);
  assert.equal(code(r), 'order_not_found');
  r = await customerPost(s, d.token, { stars: 5 });
  assert.equal(r.status, 404);
  r = await h.get('/api/shop/delivery/ratings', { token: s.token });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(code(r), 'ratings_disabled');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM delivery_ratings WHERE shop_id=$1', [s.shopId])).n), 0);
  //  دکمه‌های برنامه: پیک (`/courier/me`) و دکان (`/shop/delivery/config`) پرچم را می‌دانند
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.ratingsEnabled, false);
  assert.equal((await h.get('/api/shop/delivery/config', { token: s.token })).body.ratings, false);
  await setFlag(s.shopId, R.FLAG, true);
  assert.equal((await h.get('/api/courier/me', { token: s.courier.token })).body.ratingsEnabled, true);
  assert.equal((await h.get('/api/shop/delivery/config', { token: s.token })).body.ratings, true);
});

test('DLV-18: فقط پس از «تحویل شد»، یک بار از هر سو؛ برچسبِ سوی دیگر و پیکِ دیگر رد؛ پس از ۷ روز بسته', async () => {
  const s = await liveShop('امتیاز');
  const open = await delivery(s, s.courier, { deliver: false });
  let r = await rateCourier(s.courier, open.assignmentId, { stars: 4 });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(code(r), 'not_ratable');
  r = await customerGet(s, open.token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.canRate, false);
  assert.equal(code(await customerPost(s, open.token, { stars: 5 })), 'not_ratable');

  const d = await delivery(s, s.courier);
  r = await customerGet(s, d.token);
  assert.equal(r.body.canRate, true);
  assert.deepEqual(r.body.tags, R.TAGS.customer);
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.equal(code(await customerPost(s, d.token, { stars: 0 })), 'bad_stars');
  assert.equal(code(await customerPost(s, d.token, { stars: 5, tags: ['ready'] })), 'bad_tags', 'برچسبِ سوی پیک');
  r = await customerPost(s, d.token, { stars: 5, tags: ['polite', 'on_time'] });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual([r.body.rating.stars, r.body.rating.tags, r.body.replayed], [5, ['on_time', 'polite'], false]);
  r = await customerPost(s, d.token, { stars: 5, tags: ['on_time', 'polite'] });
  assert.equal(r.status, 200);
  assert.equal(r.body.replayed, true, 'همان امتیاز دوباره');
  r = await customerPost(s, d.token, { stars: 1 });
  assert.equal(r.status, 409);
  assert.equal(code(r), 'already_rated');
  r = await customerGet(s, d.token);
  assert.deepEqual([r.body.canRate, r.body.rating.stars], [false, 5]);
  assert.equal((await customerGet(s, `${d.token}x`)).status, 404, 'توکنِ نادرست');

  r = await rateCourier(s.other, d.assignmentId, { stars: 3 });
  assert.equal(r.status, 404);
  assert.equal(code(r), 'assignment_not_found', 'پیکِ دیگر');
  assert.equal(code(await rateCourier(s.courier, d.assignmentId, { stars: 3, tags: ['rude'] })), 'bad_tags');
  r = await rateCourier(s.courier, d.assignmentId, { stars: 3, tags: ['hard_to_find'] });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal((await rateCourier(s.courier, d.assignmentId, { stars: 3, tags: ['hard_to_find'] })).status, 200);
  assert.equal(code(await rateCourier(s.courier, d.assignmentId, { stars: 4 })), 'already_rated');

  const late = await delivery(s, s.courier);
  await query('UPDATE delivery_assignments SET delivered_at=$2 WHERE id=$1', [late.assignmentId, now() - R.WINDOW_MS - 60_000]);
  assert.equal((await customerGet(s, late.token)).body.canRate, false);
  assert.equal(code(await customerPost(s, late.token, { stars: 5 })), 'rating_closed');
  assert.equal(code(await rateCourier(s.courier, late.assignmentId, { stars: 5 })), 'rating_closed');
});

test('DLV-18: جمعِ هر پیک فقط برای صاحب و مدیر؛ بی نام و شمارهٔ مشتری؛ دکانِ دیگر نمی‌بیند', async () => {
  const s = await liveShop('جمعِ امتیاز');
  const a = await delivery(s, s.courier);
  const b = await delivery(s, s.courier);
  assert.equal((await customerPost(s, a.token, { stars: 4, tags: ['on_time'] })).status, 201);
  assert.equal((await customerPost(s, b.token, { stars: 5, tags: ['on_time', 'polite'] })).status, 201);
  assert.equal((await rateCourier(s.courier, a.assignmentId, { stars: 2, tags: ['not_ready'] })).status, 201);

  const r = await h.get('/api/shop/delivery/ratings', { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.couriers.length, 1, 'پیکِ بی امتیاز در فهرست نیست');
  const c = r.body.couriers[0];
  assert.equal(c.courierId, s.courier.id);
  assert.deepEqual(c.fromCustomers, { count: 2, avgTenths: 45, tags: { on_time: 2, polite: 1 } });
  assert.deepEqual(c.byCourier, { count: 1, avgTenths: 20, tags: { not_ready: 1 } });
  assert.equal(r.body.recent.length, 3);
  const dump = JSON.stringify(r.body);
  assert.ok(!dump.includes('0791112233') && !dump.includes('مشتریِ امتیاز'), 'بی نام و شمارهٔ مشتری');

  const manager = await member(s.shopId, 'manager');
  assert.equal((await h.get('/api/shop/delivery/ratings', { token: manager.accessToken })).status, 200);
  const staff = await member(s.shopId, 'staff');
  assert.equal((await h.get('/api/shop/delivery/ratings', { token: staff.accessToken })).status, 403);
  assert.equal(code(await h.get('/api/shop/delivery/ratings?from=x', { token: s.token })), 'bad_range');

  const t = await liveShop('دکانِ دیگر');
  const other = await h.get('/api/shop/delivery/ratings', { token: t.token });
  assert.equal(other.status, 200);
  assert.deepEqual([other.body.couriers, other.body.recent], [[], []]);
  assert.equal((await rateCourier(t.courier, a.assignmentId, { stars: 5 })).status, 404, 'پیکِ دکانِ دیگر');
});

test('DLV-18 ویترین: صفحهٔ پیگیری فرمِ امتیاز را فقط پس از «تحویل شد» و با درِ همین پرونده دارد', () => {
  const pub = path.join(__dirname, '..', 'public');
  const html = fs.readFileSync(path.join(pub, 'store', 'index.html'), 'utf8');
  const js = fs.readFileSync(path.join(pub, 'store', 'order.js'), 'utf8');
  for (const id of ['o-rate', 'o-rate-form', 'o-rate-stars', 'o-rate-tags', 'o-rate-send', 'o-rate-msg']) assert.match(html, new RegExp(`id="${id}"`), id);
  assert.match(html, /id="o-rate"[^>]*hidden/, 'پیش‌فرض پنهان');
  assert.match(js, /o\.status !== 'delivered'/, 'فقط پس از «تحویل شد»');
  assert.match(js, /'\/orders\/' \+ encodeURIComponent\(token\) \+ '\/rating'/, 'همان درِ `/api/store/:slug/orders/:token/rating`');
  //  برچسب‌های صفحه همان فهرستِ ثابتِ سرور برای مشتری‌اند — نه بیشتر، نه کمتر
  const tags = [...js.matchAll(/\{ tag: '([a-z_]+)', label: 'od\.rt\.\1' \}/g)].map((m) => m[1]);
  assert.deepEqual(tags, R.TAGS.customer);
});
