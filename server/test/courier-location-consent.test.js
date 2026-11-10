'use strict';
/**
 * S94 (§۲۱) — «بدونِ رضایت ردیابیِ لحظه‌ایِ پیک فعال نشود؛ لغوِ رضایت اثرِ بعدی را متوقف کند و دادهٔ قبلی پاک شود.»
 * (`lib/courier-location-consent.js`، مهاجرتِ ۷۵۰، پرچمِ خاموشِ `shop.delivery_live_location.consent`)
 *
 *   ۱) پرچمِ خاموش ⇒ همان امروز: بی رضایت هم موقعیت می‌نشیند؛ `/me` می‌گوید `required: false`.
 *   ۲) پرچمِ روشن، بی رضایت ⇒ ۴۰۳ ِ `location_consent_required` و هیچ نقطه؛ نقطهٔ مانده از پیش به مشتری نمی‌رسد.
 *   ۳) «بله» ِ خودِ پیک ⇒ موقعیت می‌نشیند و مشتری می‌بیند؛ «بله» ِ دوباره بی‌اثر و بی ردیفِ سابقهٔ دوم.
 *   ۴) «نه» ⇒ همان لحظه نقطه‌های ذخیره‌شده پاک، مشتری هیچ، نقطهٔ بعدی ۴۰۳؛ سابقه با شمارِ پاک‌شده و بی مختصات.
 *   ۵) بدنهٔ نادرست ۴۰۰؛ عضوِ غیرِ پیک به این در نمی‌رسد؛ مهاجرتِ ۷۵۰ برگشت‌پذیر و دوباره‌اجراپذیر است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const delivery = require('../src/lib/delivery');
const consent = require('../src/lib/courier-location-consent');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-consent' }, body: { device_id: 'dev-consent', schema_version: 2, ops },
});

/** دکانِ روشن با ویترین، تحویل، نمای مشتری و موقعیتِ زنده. */
async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `c-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', delivery.VIEW_FLAG, delivery.LOCATION_FLAG]) await setFlag(s.shopId, k, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(s.token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function addMember(s, role = 'staff') {
  const u = await h.newUser(`عضو ${uniq()}`, 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), s.shopId, u.user.id, role, now()]);
  return u;
}
async function addCourier(s, name) {
  const u = await addMember(s);
  const r = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name, phone: '0799999999' }, { token: s.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, userId: u.user.id, courierId: r.body.courier.id };
}

/** سفارشِ ارسالی، سپرده به پیک و «در راه». */
async function onTheWay(s, courier) {
  const phone = '0791112233';
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const ck = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(ck.status, 200, JSON.stringify(ck.body));
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone },
    address: { lat: 34.523456, lng: 69.181234, landmark: 'نزدیکِ مسجد' }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ck.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token })).status, 200, to);
  }
  const a = await h.post(`/api/shop/delivery/orders/${id}/assign`,
    { mode: 'shop_courier', courierId: courier.courierId, requireCode: true, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const aid = a.body.assignment.id;
  const evs = ['assigned', 'picked_up'].map((type, i) => ({ eventId: `ev-${uniq()}-${i}`, assignmentId: aid, type, deviceTime: Date.now() }));
  const sent = await h.post('/api/courier/events', { deviceId: 'courier-phone', events: evs }, { token: courier.token });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  for (const x of sent.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
  return { aid, trackToken: r.body.order.token };
}

const location = (c, aid, body = { lat: 34.531234, lng: 69.172345 }) => h.post(`/api/courier/missions/${aid}/location`, body, { token: c.token });
const setConsent = (c, body) => h.put('/api/courier/location-consent', body, { token: c.token });
const seen = async (s, m) => (await h.get(`/api/store/${s.slug}/orders/${m.trackToken}`)).body.order.delivery.location;
const points = async (aid) => Number((await one('SELECT COUNT(*)::int AS n FROM delivery_locations WHERE assignment_id=$1', [aid])).n);
const audits = (shopId) => query(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='courier.location_consent' ORDER BY created_at`, [shopId])
  .then((r) => r.rows.map((x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail)));

test('S94 ۱) پرچمِ رضایت خاموش ⇒ همان امروز: بی رضایت هم موقعیت می‌نشیند', async () => {
  const s = await liveShop('رضایت‌خاموش');
  const c = await addCourier(s, 'کریم');
  const me = await h.get('/api/courier/me', { token: c.token });
  assert.deepEqual(me.body.locationConsent, { required: false, granted: false, grantedAt: null });
  const m = await onTheWay(s, c);
  const l = await location(c, m.aid);
  assert.equal(l.status, 200, JSON.stringify(l.body));
  assert.equal(l.body.accepted, true);
  assert.ok(await seen(s, m));
});

test('S94 ۲–۴) بی رضایت هیچ؛ «بله» ⇒ می‌نشیند؛ «نه» ⇒ همان لحظه پاک و نقطهٔ بعدی ۴۰۳', async () => {
  const s = await liveShop('رضایت');
  const c = await addCourier(s, 'محمود');
  const m = await onTheWay(s, c);
  //  نقطه‌ای که پیش از روشن شدنِ پرچم رسیده بود
  assert.equal((await location(c, m.aid)).body.accepted, true);
  await setFlag(s.shopId, consent.FLAG, true);
  assert.equal(await seen(s, m), null, 'نقطهٔ مانده بی رضایت به مشتری نمی‌رسد');
  const denied = await location(c, m.aid, { lat: 34.6, lng: 69.2 });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'location_consent_required');
  assert.equal(Number((await one('SELECT lat FROM delivery_locations WHERE assignment_id=$1', [m.aid])).lat).toFixed(3), '34.531', 'هیچ نقطهٔ تازه‌ای ننشست');
  assert.deepEqual((await h.get('/api/courier/me', { token: c.token })).body.locationConsent, { required: true, granted: false, grantedAt: null });

  //  «بله» ِ خودِ پیک
  const yes = await setConsent(c, { granted: true });
  assert.equal(yes.status, 200, JSON.stringify(yes.body));
  assert.equal(yes.body.granted, true);
  assert.equal(yes.body.unchanged, false);
  const again = await setConsent(c, { granted: true });
  assert.equal(again.body.unchanged, true);
  assert.equal(again.body.grantedAt, yes.body.grantedAt);
  assert.equal((await h.get('/api/courier/me', { token: c.token })).body.locationConsent.granted, true);
  await query('UPDATE delivery_locations SET server_time=server_time - 6000 WHERE assignment_id=$1', [m.aid]);
  const ok = await location(c, m.aid, { lat: 34.6, lng: 69.2 });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.accepted, true);
  assert.equal((await seen(s, m)).lat, 34.6);

  //  «نه»: پاک، مشتری هیچ، بعدی ۴۰۳
  const no = await setConsent(c, { granted: false });
  assert.equal(no.status, 200, JSON.stringify(no.body));
  assert.deepEqual({ granted: no.body.granted, grantedAt: no.body.grantedAt, purged: no.body.purged }, { granted: false, grantedAt: null, purged: 1 });
  assert.equal(await points(m.aid), 0);
  assert.equal(await seen(s, m), null);
  assert.equal((await location(c, m.aid)).body.error.code, 'location_consent_required');
  assert.equal(await points(m.aid), 0);

  const log = await audits(s.shopId);
  assert.deepEqual(log, [{ granted: true, purged: 0 }, { granted: false, purged: 1 }], 'یک ردیف برای هر تغییر، بی مختصات');
});

test('S94 ۵) بدنهٔ نادرست ۴۰۰؛ عضوِ غیرِ پیک نه؛ پیکِ دیگر نقطه‌های این پیک را پاک نمی‌کند', async () => {
  const s = await liveShop('رضایت‌مرز');
  await setFlag(s.shopId, consent.FLAG, true);
  const a = await addCourier(s, 'جاوید');
  const b = await addCourier(s, 'نادر');
  for (const body of [{}, { granted: 'yes' }, { granted: 1 }]) {
    const r = await setConsent(a, body);
    assert.equal(r.status, 400, JSON.stringify(body));
    assert.equal(r.body.error.code, 'bad_consent');
  }
  const staff = await addMember(s);
  const r = await h.put('/api/courier/location-consent', { granted: true }, { token: staff.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'not_a_courier');
  assert.equal((await setConsent(a, { granted: true })).status, 200);
  const m = await onTheWay(s, a);
  assert.equal((await location(a, m.aid)).body.accepted, true);
  assert.equal((await setConsent(b, { granted: true })).status, 200);
  const off = await setConsent(b, { granted: false });
  assert.equal(off.body.purged, 0);
  assert.equal(await points(m.aid), 1, 'رضایتِ پیکِ دیگر به نقطه‌های این پیک دست نمی‌زند');
});

test('مهاجرتِ ۷۵۰: برگشت و اجرای دوباره', async () => {
  const dir = path.join(__dirname, '..', 'migrations');
  const col = async () => Boolean((await one(
    `SELECT 1 AS x FROM information_schema.columns WHERE table_name='shop_couriers' AND column_name='location_consent_at'`)));
  assert.equal(await col(), true);
  await query(fs.readFileSync(path.join(dir, 'rollback', '750_courier_location_consent.down.sql'), 'utf8'));
  assert.equal(await col(), false);
  await query(fs.readFileSync(path.join(dir, '750_courier_location_consent.sql'), 'utf8'));
  await query(fs.readFileSync(path.join(dir, '750_courier_location_consent.sql'), 'utf8'));
  assert.equal(await col(), true);
});

test('SR46-2: «نه» ِ هم‌زمان با فرستادنِ موقعیت — نقطه‌ای پس از لغو نمی‌نشیند (قفلِ ردیفِ پیک)', async () => {
  const { tx } = require('../src/db');
  const s = await liveShop('رضایت‌هم‌زمان');
  await setFlag(s.shopId, consent.FLAG, true);
  const c = await addCourier(s, 'هم‌زمان');
  assert.equal((await setConsent(c, { granted: true })).status, 200);
  const m = await onTheWay(s, c);
  //  همان کارِ `consent.set` («نه»): قفلِ ردیفِ پیک، لغو و پاک کردن — و تراکنش هنوز باز است که موقعیت می‌رسد
  let locked;
  let release;
  const gotLock = new Promise((r) => { locked = r; });
  const gate = new Promise((r) => { release = r; });
  const revoke = tx(async (t) => {
    await t.query('SELECT 1 FROM shop_couriers WHERE shop_id=$1 AND id=$2 FOR UPDATE', [s.shopId, c.courierId]);
    await t.query('UPDATE shop_couriers SET location_consent_at=NULL WHERE shop_id=$1 AND id=$2', [s.shopId, c.courierId]);
    await t.query('DELETE FROM delivery_locations WHERE assignment_id=$1', [m.aid]);
    locked();
    await gate;
  });
  await gotLock;
  const sent = location(c, m.aid);
  await new Promise((r) => setTimeout(r, 400));
  release();
  await revoke;
  const r = await sent;
  assert.deepEqual([r.status, r.body.error?.code], [403, 'location_consent_required'], JSON.stringify(r.body));
  assert.equal(await points(m.aid), 0, 'نقطهٔ رسیده در میانهٔ لغو نماند');
});
