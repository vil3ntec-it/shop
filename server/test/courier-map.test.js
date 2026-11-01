'use strict';
/**
 * DLV-05 و DLV-16 (§۳.۱۱، §۳.۲) — نقشهٔ آفلاینِ پیک، پینِ مقصد به مسیریاب، و
 * «موقعیت فقط در زمانِ مأموریت» در کارتِ پیک.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.delivery_map` درهای نقشه ۴۰۴ و کارتِ مأموریت همان
 *      کلیدهای پیشین را دارد (نه `navigation`، نه `tracking`).
 *   ۲) پینِ مقصد ⇒ سه پیوندِ `geo:` / Google Maps / OsmAnd، فقط با مختصات (نه نشانه،
 *      نه نام، نه شماره)؛ بی پین یا پس از پایانِ مأموریت ⇒ `null`.
 *   ۳) فهرستِ نقشه فقط فایل‌های `<شناسه>.pmtiles` با سرآیندِ PMTiles v3 (نه فایلِ
 *      جعلی، نه نامِ ناجور، نه پیوندِ نمادین)، با اندازه و sha256 ِ درست؛ دانلودِ کامل
 *      همان بایت‌ها، `Range` ⇒ ۲۰۶؛ رفتن به بیرونِ پوشه ⇒ ۴۰۴.
 *   ۴) DLV-16: با هر دو پرچمِ موقعیت، `tracking.location` فقط «در راه» راست است.
 *   ۵) صاحبِ دکان (نه پیک) و پیکِ دکانِ دیگر از درِ نقشه چیزی نمی‌گیرند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const crypto = require('crypto');
const h = require('./helpers');
const config = require('../src/config');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const delivery = require('../src/lib/delivery');
const courierMap = require('../src/lib/courier-map');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const mapDir = fs.mkdtempSync(path.join(os.tmpdir(), 'courier-map-'));
const outside = fs.mkdtempSync(path.join(os.tmpdir(), 'courier-map-out-'));

/** یک PMTilesِ کوچک: سرآیندِ v3 و بایت‌های پیدا (برای سنجشِ Range). */
const pmtiles = (n) => Buffer.concat([Buffer.from('PMTiles', 'ascii'), Buffer.from([3]), crypto.randomBytes(n)]);
const KABUL = pmtiles(4096);

test.before(async () => {
  config.courierMap.dir = mapDir;
  fs.writeFileSync(path.join(mapDir, 'kabul.pmtiles'), KABUL);
  fs.writeFileSync(path.join(mapDir, 'herat.pmtiles'), pmtiles(64));
  fs.writeFileSync(path.join(mapDir, 'fake.pmtiles'), Buffer.from('not a map at all'));               // سرآیندِ نادرست
  fs.writeFileSync(path.join(mapDir, 'old.pmtiles'), Buffer.concat([Buffer.from('PMTiles'), Buffer.from([2])])); // نسخهٔ ۲
  fs.writeFileSync(path.join(mapDir, 'Bad_Name.pmtiles'), pmtiles(8));                                // نامِ ناجور
  fs.writeFileSync(path.join(mapDir, 'notes.txt'), 'x');
  fs.writeFileSync(path.join(outside, 'secret.pmtiles'), pmtiles(8));
  fs.symlinkSync(path.join(outside, 'secret.pmtiles'), path.join(mapDir, 'link.pmtiles'));            // پیوندِ نمادین

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
test.after(async () => {
  await h.stop();
  fs.rmSync(mapDir, { recursive: true, force: true });
  fs.rmSync(outside, { recursive: true, force: true });
});
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

const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveShop(label) {
  const o = await shopOwner(label);
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await setFlag(o.shopId, k, true);
  const slug = `m-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(o.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}

async function addCourier(owner, name) {
  const u = await h.newUser(`پیک ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), owner.shopId, u.user.id, now()]
  );
  const r = await h.post('/api/shop/delivery/couriers', { userId: u.user.id, name, phone: '0799999999' }, { token: owner.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: u.accessToken, courierId: r.body.courier.id };
}

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

/** سفارشِ ارسالیِ بسته‌بندی‌شده؛ `address` ِ دلخواه (با یا بی پین). */
async function packedOrder(s, address, phone = '0791112233') {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتریِ نقشه', phone }, address,
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  return id;
}

let evSeq = 0;
const ev = (assignmentId, type, extra = {}) => ({ eventId: `ev-${Date.now()}-${++evSeq}`, assignmentId, type, deviceTime: Date.now(), ...extra });
async function sendOk(token, events) {
  const r = await h.post('/api/courier/events', { deviceId: 'courier-phone', events }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.equal(x.status, 'applied', JSON.stringify(x));
}
async function assignTo(s, orderId, courierId) {
  const a = await h.post(`/api/shop/delivery/orders/${orderId}/assign`,
    { mode: 'shop_courier', courierId, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  return a.body.assignment.id;
}
async function card(token, assignmentId, scope = 'active') {
  const r = await h.get(`/api/courier/missions?scope=${scope}`, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.find((x) => x.id === assignmentId);
}
const PIN = { lat: 34.523456, lng: 69.181234, landmark: 'پشتِ مسجدِ جامع، دروازهٔ سبز' };

/* ══════════════════════════════════════════════════════════════════ */

test('navigation(): فقط مختصاتِ معتبر، گرد به ۶ رقم؛ بی پین یا نامعتبر ⇒ null', () => {
  const n = courierMap.navigation(34.5234564, 69.1812341);
  assert.deepEqual(n, {
    lat: 34.523456, lng: 69.181234,
    geo: 'geo:34.523456,69.181234?q=34.523456,69.181234',
    google: 'https://www.google.com/maps/dir/?api=1&destination=34.523456,69.181234',
    osmand: 'https://osmand.net/map?pin=34.523456,69.181234#17/34.523456/69.181234',
  });
  for (const [a, o] of [[undefined, 69], [34, undefined], [null, null], [91, 69], [34, 181], ['x', 69], [NaN, 1]]) {
    assert.equal(courierMap.navigation(a, o), null, `${a},${o}`);
  }
});

test('⛔ پیش‌فرض خاموش: درِ نقشه ۴۰۴ و کارتِ مأموریت بی کلیدِ تازه', async () => {
  const s = await liveShop('خاموش');
  const c = await addCourier(s, 'کریم');
  const id = await assignTo(s, await packedOrder(s, PIN), c.courierId);
  await sendOk(c.token, [ev(id, 'assigned'), ev(id, 'picked_up')]);

  for (const p of ['/api/courier/map', '/api/courier/map/kabul']) {
    const r = await h.get(p, { token: c.token });
    assert.equal(r.status, 404, p);
    assert.equal(r.body.error.code, 'delivery_map_disabled', p);
  }
  const m = await card(c.token, id);
  assert.equal('navigation' in m, false, 'کارت همان پیشین');
  assert.equal('tracking' in m, false, 'بی پرچمِ موقعیت کلیدِ tracking نیست');
  assert.deepEqual(Object.keys(m).sort(), [
    'address', 'cashCollectedMinor', 'customer', 'delivery', 'expectedCollectMinor', 'fareMinor', 'id', 'itemsCount',
    'live', 'orderId', 'orderNo', 'orderStatus', 'paymentMethod', 'photoAllowed', 'recall', 'requiresCode', 'status',
    'timeline', 'updatedAt', 'version',   //  `timeline`: خطِ زمانیِ پیک (DLV، بی پرچم؛ ‎lib/courier-summary.js‎)
  ]);
});

test('DLV-05: پینِ مقصد ⇒ پیوندهای مسیریاب فقط با مختصات؛ بی پین یا پس از پایان ⇒ null', async () => {
  const s = await liveShop('مسیر');
  await setFlag(s.shopId, courierMap.FLAG, true);
  const c = await addCourier(s, 'محمود');
  const withPin = await assignTo(s, await packedOrder(s, PIN), c.courierId);
  const noPin = await assignTo(s, await packedOrder(s, { landmark: 'کوچهٔ دوم، دکانِ نانوایی', text: 'کارتهٔ سه' }, '0791112234'), c.courierId);

  const m = await card(c.token, withPin);
  assert.deepEqual(m.navigation, courierMap.navigation(PIN.lat, PIN.lng));
  for (const url of [m.navigation.geo, m.navigation.google, m.navigation.osmand]) {
    assert.doesNotMatch(decodeURIComponent(url), /مسجد|دروازه|مشتری|0791/, `فقط مختصات: ${url}`);
  }
  assert.equal(m.address.landmark, PIN.landmark, 'پیک نشانه را در کارت می‌بیند');
  assert.equal((await card(c.token, noPin)).navigation, null, 'بی پین ⇒ با «اول نشانه»');

  //  پس از تحویل: نه نشانی و نه پیوند
  await sendOk(c.token, [ev(withPin, 'assigned'), ev(withPin, 'picked_up'), ev(withPin, 'arrived'),
    ev(withPin, 'delivered', { cashCollectedMinor: m.expectedCollectMinor })]);
  const done = await card(c.token, withPin, 'done');
  assert.equal(done.address, null);
  assert.equal(done.navigation, null);
});

test('DLV-05: فهرستِ نقشه فقط PMTilesِ v3 ِ معتبر؛ دانلودِ کامل، Range و بی راهِ بیرون', async () => {
  const s = await liveShop('نقشه');
  await setFlag(s.shopId, courierMap.FLAG, true);
  const c = await addCourier(s, 'نادر');

  const list = await h.get('/api/courier/map', { token: c.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.attribution, '© OpenStreetMap contributors');
  assert.deepEqual(list.body.packs.map((p) => p.id), ['herat', 'kabul'], 'نه جعلی، نه نسخهٔ ۲، نه نامِ ناجور، نه پیوندِ نمادین');
  const k = list.body.packs.find((p) => p.id === 'kabul');
  assert.equal(k.bytes, KABUL.length);
  assert.equal(k.sha256, crypto.createHash('sha256').update(KABUL).digest('hex'));
  assert.equal(k.url, '/api/courier/map/kabul');
  assert.equal(typeof k.updatedAt, 'number');

  const auth = { Authorization: `Bearer ${c.token}` };
  const full = await fetch(`${h.base()}${k.url}`, { headers: auth });
  assert.equal(full.status, 200);
  assert.equal(full.headers.get('accept-ranges'), 'bytes');
  assert.ok(Buffer.from(await full.arrayBuffer()).equals(KABUL), 'همان بایت‌ها');

  const part = await fetch(`${h.base()}${k.url}`, { headers: { ...auth, Range: 'bytes=100-' } });
  assert.equal(part.status, 206, 'ادامهٔ دانلودِ بریده');
  assert.ok(Buffer.from(await part.arrayBuffer()).equals(KABUL.subarray(100)));

  for (const bad of ['fake', 'old', 'Bad_Name', 'link', 'notes', 'nope', '..%2F..%2Fetc%2Fpasswd', '%2E%2E', 'kabul.pmtiles']) {
    const r = await h.get(`/api/courier/map/${bad}`, { token: c.token });
    assert.equal(r.status, 404, bad);
  }
  //  فایلِ عوض‌شده ⇒ جمعِ کنترلِ تازه
  const next = pmtiles(2048);
  fs.writeFileSync(path.join(mapDir, 'herat.pmtiles'), next);
  const later = new Date(Date.now() + 5000);
  fs.utimesSync(path.join(mapDir, 'herat.pmtiles'), later, later);
  const again = (await h.get('/api/courier/map', { token: c.token })).body.packs.find((p) => p.id === 'herat');
  assert.equal(again.sha256, crypto.createHash('sha256').update(next).digest('hex'));
});

test('DLV-05: صاحبِ دکان و پیکِ دکانِ دیگر از درِ نقشه چیزی نمی‌گیرند', async () => {
  const a = await liveShop('الفِ نقشه');
  await setFlag(a.shopId, courierMap.FLAG, true);
  const b = await liveShop('بِ نقشه');
  const bc = await addCourier(b, 'پیکِ ب');
  for (const p of ['/api/courier/map', '/api/courier/map/kabul']) {
    const owner = await h.get(p, { token: a.token });
    assert.ok(owner.status >= 400, `صاحبِ دکان: ${owner.status}`);
    const other = await h.get(p, { token: bc.token });
    assert.equal(other.status, 404, 'دکانِ «ب» پرچم ندارد');
    const none = await h.get(p);
    assert.equal(none.status, 401);
  }
});

test('DLV-16: با هر دو پرچمِ موقعیت، tracking.location فقط «در راه» راست است', async () => {
  const s = await liveShop('ردیابی');
  const c = await addCourier(s, 'رحیم');
  const id = await assignTo(s, await packedOrder(s, PIN), c.courierId);

  //  فقط پرچمِ موقعیت (بی نمای مشتری) هیچ کلیدی نمی‌سازد — همان شرطِ درِ موقعیت
  await setFlag(s.shopId, delivery.LOCATION_FLAG, true);
  assert.equal('tracking' in await card(c.token, id), false);
  await setFlag(s.shopId, delivery.VIEW_FLAG, true);

  const seen = [];
  const look = async (label, scope = 'active') => {
    const m = await card(c.token, id, scope);
    seen.push([label, m.tracking.location]);
    assert.equal(m.tracking.minIntervalMs, 5000);
  };
  await look('requested');
  await sendOk(c.token, [ev(id, 'assigned')]);
  await look('assigned');
  await sendOk(c.token, [ev(id, 'picked_up')]);
  await look('picked_up');
  await sendOk(c.token, [ev(id, 'moving')]);
  await look('moving');
  //  سرور همان را می‌پذیرد که کارت می‌گوید
  const l = await h.post(`/api/courier/missions/${id}/location`, { lat: 34.53, lng: 69.17 }, { token: c.token });
  assert.equal(l.status, 200, JSON.stringify(l.body));
  await sendOk(c.token, [ev(id, 'arrived')]);
  await look('arrived');
  const after = await h.post(`/api/courier/missions/${id}/location`, { lat: 34.53, lng: 69.17 }, { token: c.token });
  assert.equal(after.body.error.code, 'not_on_the_way');
  const expected = (await card(c.token, id)).expectedCollectMinor;
  await sendOk(c.token, [ev(id, 'delivered', { cashCollectedMinor: expected })]);
  await look('delivered', 'done');
  assert.deepEqual(seen, [
    ['requested', false], ['assigned', false], ['picked_up', true], ['moving', true], ['arrived', false], ['delivered', false],
  ]);
});
