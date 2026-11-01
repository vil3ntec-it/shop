'use strict';
/**
 * بیرون از محدوده (CHK-10، S34): «دریافت از دکان» یا «وقتی به محلِ شما رسیدیم خبرم کن»
 * — `lib/storefront-area-requests.js`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `shop.storefront.notify_area` (یا ناحیهٔ خاموش) ⇒ درِ مشتری همان پاسخِ
 *      درِ ناشناختهٔ main، درِ دکان ۴۰۳، و «کجا؟» ِ ویترین همان کلیدهای پیشین.
 *   ۲) S34: پینِ بیرون ⇒ سفارشِ ارسالی ثبت نمی‌شود، «دریافت از دکان» با همان سبد ثبت می‌شود،
 *      و «خبرم کن» با شمارهٔ تأییدشده یک ردیف می‌سازد — پینِ خام نه، فقط خانهٔ شبکه؛ پینِ درون ⇒ ۴۰۹.
 *   ۳) دکان: تقاضا بی شماره؛ شماره فقط وقتی ناحیهٔ فعال آن خانه را بپوشاند؛ «خبر دادم» ردیف را
 *      پاک می‌کند و در سابقه شماره نیست؛ دکانِ دیگر با شناسهٔ واقعی هیچ نمی‌کند.
 *   ۴) پس گرفتن، نگه‌داریِ یک‌ساله و سقفِ هر دکان.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const sfd = require('../src/lib/storefront-delivery');
const areas = require('../src/lib/storefront-area-requests');

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

const INSIDE = { lat: 34.53, lng: 69.17 };
const OUTSIDE = { lat: 34.62, lng: 69.31 };
const POLY = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
const FAR_POLY = [[34.60, 69.28], [34.60, 69.34], [34.64, 69.34], [34.64, 69.28]];
const ZONE = { name: 'کارته‌سه', kind: 'polygon', polygon: POLY, feeMinor: 3000, minOrderMinor: 0 };
const FAR = { name: 'خیرخانه', kind: 'polygon', polygon: FAR_POLY, feeMinor: 6000, minOrderMinor: 0 };

/** دکانِ روشن با ویترین، یک ناحیه و دو کالا؛ `notify` = پرچمِ «خبرم کن». */
async function liveStore(label, { notify = true, zones = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(o.shopId, k, true);
  if (zones) await setFlag(o.shopId, 'shop.storefront.zones', true);
  if (notify) await setFlag(o.shopId, 'shop.storefront.notify_area', true);
  const slug = `a-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-area' },
    body: { device_id: 'dev-area', schema_version: 2, ops: ['rice', 'oil'].map((id) => ({
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert', fields: { name: id, unit: 'کیلو', salePrice: 100 },
    })) },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token })).status, 200);
  if (zones) assert.equal((await zonesTo(o, [ZONE])).status, 200);
  return { ...o, slug };
}
const zonesTo = (s, zones) => h.put('/api/shop/storefront/delivery', { zones }, { token: s.token });

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
const T = (ticket) => ({ headers: { 'X-Store-Ticket': ticket } });
const ask = (slug, pin, opts) => h.post(`/api/store/${slug}/area-request`, pin, opts);
const rowsOf = (shopId) => many('SELECT * FROM storefront_area_requests WHERE shop_id = $1 ORDER BY created_at', [shopId]);
const checkout = (slug, b, ticket) => h.post(`/api/store/${slug}/checkout`, b, {
  headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket },
});
const order = (fulfillment, phone, address) => ({
  fulfillment, customer: { name: 'مشتریِ بیرون', phone }, ...(address ? { address } : {}),
  lines: [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'oil', qtyMilli: 1000 }],
});
const orders = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id = $1', [shopId])).n;

test('CHK-10 ⛔: پرچمِ خاموش ⇒ درِ مشتری همان پاسخِ درِ ناشناخته، درِ دکان ۴۰۳، «کجا؟» همان کلیدهای پیشین', async () => {
  const s = await liveStore('خبرم کنِ خاموش', { notify: false });
  const t = await ticketFor(s.slug, '0791200001');
  const unknown = await h.post(`/api/store/${s.slug}/no-such-door`, OUTSIDE, T(t));
  assert.equal(unknown.status, 401);
  for (const r of [await ask(s.slug, OUTSIDE, T(t)), await ask(s.slug, OUTSIDE), await h.del(`/api/store/${s.slug}/area-request`, T(t))]) {
    assert.equal(r.status, unknown.status);
    assert.deepEqual(r.body, unknown.body, 'همان پاسخِ درِ ناشناخته');
  }
  const own = await h.get('/api/shop/storefront/area-requests', { token: s.token });
  assert.equal(own.status, 403);
  assert.equal(own.body.error.code, 'notify_area_disabled');
  assert.equal('notifyArea' in (await h.get('/api/shop/orders/config', { token: s.token })).body, false, 'خاموش ⇒ پاسخِ پیشینِ `/orders/config`');
  const where = await h.get(`/api/store/${s.slug}/delivery?lat=${OUTSIDE.lat}&lng=${OUTSIDE.lng}`);
  assert.deepEqual(Object.keys(where.body).sort(), ['inZone', 'pickup', 'zone', 'zoned']);
  assert.equal((await rowsOf(s.shopId)).length, 0);

  //  پرچمِ «خبرم کن» روشن ولی ناحیه خاموش ⇒ باز همان پاسخ (بی ناحیه «بیرون» معنا ندارد)
  const z = await liveStore('خبرم کنِ بی ناحیه', { zones: false });
  const tz = await ticketFor(z.slug, '0791200001');
  const offZones = await ask(z.slug, OUTSIDE, T(tz));
  assert.deepEqual([offZones.status, offZones.body], [unknown.status, unknown.body]);
  assert.equal((await h.get('/api/shop/storefront/area-requests', { token: z.token })).body.error.code, 'zones_disabled');
});

test('S34: پینِ بیرون ⇒ ارسال ثبت نمی‌شود، «دریافت از دکان» با همان سبد ثبت می‌شود، «خبرم کن» فقط خانهٔ شبکه را نگه می‌دارد', async () => {
  const s = await liveStore('بیرون از محدوده');
  const phone = '0791200002';
  const t = await ticketFor(s.slug, phone);
  //  «کجا؟» پیش از پرداخت: بیرون ⇒ دو راه؛ درون ⇒ همان پاسخِ پیشین
  const out = await h.get(`/api/store/${s.slug}/delivery?lat=${OUTSIDE.lat}&lng=${OUTSIDE.lng}`);
  assert.deepEqual(out.body, { zoned: true, inZone: false, zone: null, pickup: true, notifyArea: true });
  const inn = await h.get(`/api/store/${s.slug}/delivery?lat=${INSIDE.lat}&lng=${INSIDE.lng}`);
  assert.deepEqual(Object.keys(inn.body).sort(), ['inZone', 'pickup', 'zone', 'zoned']);

  const n0 = await orders(s.shopId);
  const far = await checkout(s.slug, order('delivery', phone, { text: 'بیرون', ...OUTSIDE }), t);
  assert.equal(far.status, 400, JSON.stringify(far.body));
  assert.deepEqual([far.body.error.code, far.body.error.pickup], ['out_of_zone', true]);
  assert.equal(await orders(s.shopId), n0, 'سفارشِ ارسالی ثبت نشد');
  //  راهِ اول: همان سبد، «دریافت از دکان»
  const pick = await checkout(s.slug, order('pickup', phone), t);
  assert.equal(pick.status, 201, JSON.stringify(pick.body));
  assert.equal(pick.body.order.deliveryFeeMinor, 0);

  //  راهِ دوم: «خبرم کن» — شمارهٔ تأییدشده لازم است
  const anon = await ask(s.slug, OUTSIDE);
  assert.equal(anon.status, 401);
  assert.equal(anon.body.error.code, 'verification_required');
  for (const bad of [{}, { lat: '34.6', lng: 69.3 }, { lat: 91, lng: 0 }, { lat: 34.6 }]) {
    const r = await ask(s.slug, bad, T(t));
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'bad_address');
  }
  const inside = await ask(s.slug, INSIDE, T(t));
  assert.equal(inside.status, 409);
  assert.equal(inside.body.error.code, 'in_zone');
  assert.equal(inside.body.error.zone.name, 'کارته‌سه');
  assert.equal((await rowsOf(s.shopId)).length, 0, 'پینِ درون هیچ ردیفی نساخت');

  const first = await ask(s.slug, OUTSIDE, T(t));
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const again = await ask(s.slug, { lat: OUTSIDE.lat + 0.002, lng: OUTSIDE.lng }, T(t));
  assert.equal(again.status, 200, 'همان شماره ⇒ همان درخواست');
  assert.equal(again.body.request.createdAt, first.body.request.createdAt);
  const rows = await rowsOf(s.shopId);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].phone, '+93791200002');
  //  ⛔ پینِ خام نه: فقط مرکزِ خانهٔ شبکهٔ آخرین پین
  const cell = sfd.snapPin(OUTSIDE.lat + 0.002, OUTSIDE.lng);
  assert.deepEqual([Number(rows[0].cell_lat), Number(rows[0].cell_lng)], [cell.lat, cell.lng]);
  assert.notEqual(Number(rows[0].cell_lat), OUTSIDE.lat + 0.002);
  assert.ok(!JSON.stringify(first.body).includes('791200002'), 'پاسخ شماره را پس نمی‌دهد');
});

test('CHK-10: دکان تقاضا را بی شماره می‌بیند؛ شماره فقط پس از پوشاندنِ خانه؛ «خبر دادم» پاک می‌کند؛ دکانِ دیگر هیچ', async () => {
  const s = await liveStore('تقاضای بیرون');
  const b = await liveStore('دکانِ همسایه');
  for (const phone of ['0791200003', '0791200004']) {
    assert.equal((await ask(s.slug, OUTSIDE, T(await ticketFor(s.slug, phone)))).status, 201);
  }
  assert.equal((await ask(s.slug, { lat: 34.70, lng: 69.40 }, T(await ticketFor(s.slug, '0791200005')))).status, 201);

  //  وب (license/area-requests.js): فهرست فقط برای صاحب و مدیر (`settings.write`) — صندوق‌دار نه کلید دارد نه فهرست
  assert.equal((await h.get('/api/shop/orders/config', { token: s.token })).body.notifyArea, true);
  {
    resetAll();
    const fresh = await h.signIn(s, 'shop');
    const inv = await h.post('/api/shop/invite', { role: 'cashier' }, { token: fresh.accessToken });
    assert.equal(inv.status, 201, JSON.stringify(inv.body));
    const u = await h.newUser(`صندوق-${uniq()}`, 'shop');
    assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken })).status));
    const c = await h.signIn(u, 'shop');
    assert.equal('notifyArea' in (await h.get('/api/shop/orders/config', { token: c.accessToken })).body, false, 'صندوق‌دار ⇒ بی کلید');
    assert.equal((await h.get('/api/shop/storefront/area-requests', { token: c.accessToken })).status, 403);
    resetAll();
  }
  const before = await h.get('/api/shop/storefront/area-requests', { token: s.token });
  assert.equal(before.status, 200, JSON.stringify(before.body));
  assert.equal(before.headers.get('cache-control'), 'no-store');
  assert.deepEqual([before.body.total, before.body.covered, before.body.contacts], [3, 0, []]);
  assert.deepEqual(before.body.cells.map((c) => [c.count, c.coveredBy]), [[2, null], [1, null]]);
  assert.ok(!JSON.stringify(before.body).includes('79120000'), 'پیش از پوشاندن هیچ شماره‌ای');
  //  هیچ «خبر دادنی» برای خانهٔ ناپوشیده
  const early = await h.post(`/api/shop/storefront/area-requests/${(await rowsOf(s.shopId))[0].id}/notified`, {}, { token: s.token });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'not_covered');

  //  دکان ناحیه را به آن‌جا رساند ⇒ دو شمارهٔ همان خانه، کهنه‌ترین اول؛ خانهٔ سوم هنوز بی شماره
  assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
  const after = (await h.get('/api/shop/storefront/area-requests', { token: s.token })).body;
  assert.deepEqual([after.total, after.covered], [3, 2]);
  assert.deepEqual(after.contacts.map((c) => [c.phone, c.zone]), [['+93791200003', 'خیرخانه'], ['+93791200004', 'خیرخانه']]);
  assert.deepEqual(after.cells.map((c) => [c.count, c.coveredBy]), [[2, 'خیرخانه'], [1, null]]);
  assert.ok(!JSON.stringify(after).includes('791200005'));
  //  مشتریِ پوشیده‌شده دیگر «خبرم کن» نمی‌گیرد: همین حالا سفارش می‌دهد
  assert.equal((await ask(s.slug, OUTSIDE, T(await ticketFor(s.slug, '0791200003')))).body.error.code, 'in_zone');

  //  دکانِ همسایه (پرچم‌ها روشن) با شناسهٔ واقعیِ «الف»: پیدا نشد؛ ردیفِ «الف» می‌ماند
  const id = after.contacts[0].id;
  const cross = await h.post(`/api/shop/storefront/area-requests/${id}/notified`, {}, { token: b.token });
  assert.equal(cross.status, 404, JSON.stringify(cross.body));
  assert.deepEqual((await h.get('/api/shop/storefront/area-requests', { token: b.token })).body.contacts, []);
  assert.equal((await rowsOf(s.shopId)).length, 3);

  const done = await h.post(`/api/shop/storefront/area-requests/${id}/notified`, {}, { token: s.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual(done.body, { notified: true, zone: 'خیرخانه' });
  assert.equal((await h.post(`/api/shop/storefront/area-requests/${id}/notified`, {}, { token: s.token })).status, 404, 'یک بار');
  assert.deepEqual((await rowsOf(s.shopId)).map((r) => r.phone), ['+93791200004', '+93791200005']);
  const log = await one(`SELECT detail::text AS d FROM audit_logs WHERE shop_id = $1 AND action = 'storefront.area_request_notified'`, [s.shopId]);
  assert.ok(log && log.d.includes('خیرخانه') && !log.d.includes('79120000'), `سابقه بی شماره: ${log && log.d}`);
});

test('CHK-10: پس گرفتن با بلیت یا نشستِ حساب؛ نگه‌داریِ یک‌ساله؛ سقفِ هر دکان', async () => {
  const s = await liveStore('پس گرفتن');
  const t = await ticketFor(s.slug, '0791200006');
  assert.equal((await ask(s.slug, OUTSIDE, T(t))).status, 201);
  //  شمارهٔ دیگر فقط درخواستِ خودش را پس می‌گیرد
  const other = await h.del(`/api/store/${s.slug}/area-request`, T(await ticketFor(s.slug, '0791200009')));
  assert.deepEqual([other.status, other.body], [200, { withdrawn: false }]);
  assert.equal((await rowsOf(s.shopId)).length, 1);
  const w1 = await h.del(`/api/store/${s.slug}/area-request`, T(t));
  assert.deepEqual([w1.status, w1.body], [200, { withdrawn: true }]);
  assert.deepEqual((await h.del(`/api/store/${s.slug}/area-request`, T(t))).body, { withdrawn: false }, 'تکرارِ امن');
  assert.equal((await rowsOf(s.shopId)).length, 0);

  //  نشستِ حساب هم شمارهٔ تأییدشده است
  const acc = await h.post(`/api/store/${s.slug}/account`, {}, T(t));
  assert.equal(acc.status, 201, JSON.stringify(acc.body));
  const S = { headers: { 'X-Store-Session': acc.body.session } };
  assert.equal((await ask(s.slug, OUTSIDE, S)).status, 201);

  //  کهنه‌تر از یک سال ⇒ در فهرست نیست و با نوشتنِ بعدی پاک می‌شود
  await query('UPDATE storefront_area_requests SET updated_at = $2 WHERE shop_id = $1', [s.shopId, now() - areas.RETAIN_MS - 1000]);
  assert.equal((await h.get('/api/shop/storefront/area-requests', { token: s.token })).body.total, 0);
  assert.equal((await ask(s.slug, OUTSIDE, T(await ticketFor(s.slug, '0791200007')))).status, 201);
  assert.deepEqual((await rowsOf(s.shopId)).map((r) => r.phone), ['+93791200007']);

  //  سقف: پر ⇒ شمارهٔ تازه ۴۰۹، شمارهٔ موجود هنوز تازه می‌کند
  await query(
    `INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at)
     SELECT 'fill' || g, $1, '+9370' || lpad(g::text, 7, '0'), 34.7, 69.4, $2, $2 FROM generate_series(1, $3::int - 1) g`,
    [s.shopId, now(), areas.MAX_PER_SHOP]
  );
  const full = await ask(s.slug, OUTSIDE, T(await ticketFor(s.slug, '0791200008')));
  assert.equal(full.status, 409, JSON.stringify(full.body));
  assert.equal(full.body.error.code, 'too_many_area_requests');
  assert.equal((await ask(s.slug, OUTSIDE, T(await ticketFor(s.slug, '0791200007')))).status, 200);
  assert.equal((await rowsOf(s.shopId)).length, areas.MAX_PER_SHOP);
});
