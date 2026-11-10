'use strict';
/**
 * CHK-10 — «داخلِ محدوده» بی ژئوکدر، و سرور تنها داور (`lib/storefront-delivery.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) بردارِ مشترکِ `core/test-vectors/delivery-zone-v1.json` (همان که `StorefrontDeliveryVectorsTest` ِ برنامهٔ دکان و
 *      `AddressRoundVectorsTest` ِ core می‌خوانند): درون/بیرونِ چندضلعی با لبه و رأس «درون» (مثلِ `<=` ِ دایره)، فاصله،
 *      گردِ شش‌رقمیِ پین (نیمه رو به بالا، همان `orders.coord`)، خانهٔ شبکه و لبه‌های به‌هم‌بُریده.
 *   ۲) ⛔ «کجا؟» (`GET /:slug/delivery`)، «خبرم کن» و ثبت یک خانه را می‌سنجند: پینی با رقمِ هفتم کنارِ خطِ شبکه پیش‌تر پیش
 *      از ثبت «بیرون» و در ثبت «درون» بود.
 *   ۳) چندضلعیِ تازه یا تغییرکرده با لبه‌های به‌هم‌بُریده (دو نقطهٔ جابه‌جا) ⇒ ۴۰۰ `bad_zone` با شمارهٔ نقطه‌ها؛ همان
 *      چندضلعیِ ذخیره‌شدهٔ پیشین دست‌نخورده می‌گذرد تا ذخیرهٔ هزینه یا بازه نیفتد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const { ulid } = require('../src/lib/ulid');
const sfd = require('../src/lib/storefront-delivery');

const vec = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'delivery-zone-v1.json'), 'utf8'));

/* ══════════════════════════════════════════════════════════════════
   ۱) بردارِ مشترک (توابعِ خالص)
   ══════════════════════════════════════════════════════════════════ */

test('بردار: درون/بیرونِ چندضلعی و دایره — لبه و رأس درون‌اند (مثلِ `<=` ِ دایره)، یک سانتی‌متر بیرونِ لبه بیرون؛ فاصله', () => {
  assert.equal(vec.version, 1);
  for (const k of ['inPolygon', 'radius', 'distance', 'round6', 'cell', 'crossing']) assert.ok(vec[k].length >= 4, k);
  for (const c of vec.inPolygon) {
    assert.equal(sfd.inPolygon(c.point[0], c.point[1], c.polygon), c.want, c.name);
    assert.equal(sfd.contains({ kind: 'polygon', polygon: c.polygon }, c.point[0], c.point[1]), c.want, c.name);
  }
  for (const c of vec.radius) {
    const z = { kind: 'radius', centerLat: c.center[0], centerLng: c.center[1], radiusM: c.radiusM };
    assert.equal(sfd.contains(z, c.point[0], c.point[1]), c.want, c.name);
  }
  for (const c of vec.distance) {
    const d = sfd.distanceM(c.a[0], c.a[1], c.b[0], c.b[1]);
    assert.ok(Math.abs(d - c.wantM) <= c.tolM, `${c.name}: ${d} ≠ ${c.wantM}`);
  }
});

test('بردار: گردِ شش‌رقمی (نیمه رو به بالا) و خانهٔ پین پس از گرد', () => {
  for (const c of vec.round6) assert.equal(sfd.round6(c.raw), c.want, String(c.raw));
  const orders = require('../src/lib/orders');
  for (const c of vec.round6) {
    //  همان گردِ پینِ ثبت
    assert.equal(orders.parseAddress({ lat: c.raw, lng: 69.1 }).lat, c.want, `parseAddress ${c.raw}`);
  }
  for (const c of vec.cell) {
    const got = sfd.pinCell(c.raw[0], c.raw[1]);
    assert.ok(Math.abs(got.lat - c.want[0]) < 1e-12 && Math.abs(got.lng - c.want[1]) < 1e-12,
      `${c.raw}: ${JSON.stringify(got)} ≠ ${c.want}`);
  }
  //  پینی که گردش از خطِ شبکه می‌گذرد: خامش خانهٔ دیگری بود
  const edge = vec.cell.find((c) => sfd.snapPin(c.raw[0], c.raw[1]).lat !== sfd.pinCell(c.raw[0], c.raw[1]).lat);
  assert.ok(edge, 'بردار پینِ کنارِ خطِ شبکه دارد');
});

test('بردار: لبه‌های به‌هم‌بُریده (پاپیون، رأسِ تکراری، رأس روی لبه) و نه برای مقعر یا نقطهٔ پایانیِ تکراری', () => {
  for (const c of vec.crossing) assert.deepEqual(sfd.crossing(c.polygon), c.want, c.name);
});

/* ══════════════════════════════════════════════════════════════════
   ۲ و ۳) از راهِ درها
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

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.zones', 'shop.storefront.notify_area']) await setFlag(o.shopId, k, true);
  const slug = `z-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-zone' },
    body: { device_id: 'dev-zone', schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 100 },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return { ...o, slug };
}
const zonesTo = (s, zones) => h.put('/api/shop/storefront/delivery', { zones }, { token: s.token });

async function ticketFor(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
const checkout = (slug, address, ticket, phone) => h.post(`/api/store/${slug}/checkout`, {
  fulfillment: 'delivery', customer: { name: 'مشتری', phone }, address,
  lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });

test('⛔ «کجا؟»، «خبرم کن» و ثبت یک خانه را می‌سنجند: پینِ هفت‌رقمیِ کنارِ خطِ شبکه', async () => {
  //  (بی تابعِ تازه، تا همین آزمون روی کدِ پیشین هم همان پرسش را بپرسد)
  const r6 = (v) => Math.round(v * 1e6) / 1e6;
  const raw = vec.cell.find((c) => sfd.snapPin(c.raw[0], c.raw[1]).lat !== sfd.snapPin(r6(c.raw[0]), r6(c.raw[1])).lat).raw;
  const cellRaw = sfd.snapPin(raw[0], raw[1]);
  const cell = sfd.snapPin(r6(raw[0]), r6(raw[1]));
  //  ناحیه‌ای که خانهٔ پینِ گرد را دارد و خانهٔ پینِ خام را نه
  const lo = (cellRaw.lat + cell.lat) / 2;
  const zone = {
    name: 'لبه', kind: 'polygon', feeMinor: 1000, minOrderMinor: 0,
    polygon: [[lo, raw[1] - 0.02], [lo, raw[1] + 0.02], [lo + 0.02, raw[1] + 0.02], [lo + 0.02, raw[1] - 0.02]],
  };
  assert.equal(sfd.inPolygon(cell.lat, cell.lng, zone.polygon.map((p) => p.map(r6))), true);
  assert.equal(sfd.inPolygon(cellRaw.lat, cellRaw.lng, zone.polygon.map((p) => p.map(r6))), false);
  const s = await liveStore('خطِ شبکه');
  assert.equal((await zonesTo(s, [zone])).status, 200);

  const where = await h.get(`/api/store/${s.slug}/delivery?lat=${raw[0]}&lng=${raw[1]}`);
  assert.equal(where.status, 200, JSON.stringify(where.body));
  const phone = '0791230001';
  const t = await ticketFor(s.slug, phone);
  const placed = await checkout(s.slug, { text: 'کنارِ خط', lat: raw[0], lng: raw[1] }, t, phone);
  assert.equal(placed.status, 201, JSON.stringify(placed.body));
  assert.equal(where.body.inZone, true, '«کجا؟» همان پاسخِ ثبت را می‌دهد (پیش‌تر: بیرون، و ثبت: درون)');
  assert.equal(where.body.zone.name, 'لبه');
  //  «خبرم کن» برای همین پین: درون است، پس ردیفی ساخته نمی‌شود
  const ask = await h.post(`/api/store/${s.slug}/area-request`, { lat: raw[0], lng: raw[1] }, { headers: { 'X-Store-Ticket': t } });
  assert.equal(ask.status, 409, JSON.stringify(ask.body));
  assert.equal(ask.body.error.code, 'in_zone');
  assert.equal((await many('SELECT 1 FROM storefront_area_requests WHERE shop_id = $1', [s.shopId])).length, 0);
});

test('چندضلعیِ پاپیونی ⇒ ۴۰۰ `bad_zone` با شمارهٔ نقطه‌ها؛ همان چندضلعیِ ذخیره‌شده می‌گذرد، تغییرش نه', async () => {
  const s = await liveStore('پاپیون');
  const square = [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]];
  //  دو نقطهٔ میانی جابه‌جا؛ قرینه مساحتِ صفر هم دارد (پیش‌تر پیامِ «مساحت ندارد»)، ناقرینه پیش‌تر بی‌صدا ذخیره می‌شد
  const sym = [[34.50, 69.12], [34.56, 69.22], [34.50, 69.22], [34.56, 69.12]];
  const bow = [[34.50, 69.12], [34.56, 69.22], [34.50, 69.22], [34.57, 69.12]];
  const zone = (polygon, feeMinor = 3000) => ({ name: 'کارته‌سه', kind: 'polygon', polygon, feeMinor, minOrderMinor: 0 });

  for (const [poly, why] of [[bow, 'ناقرینه (پیش‌تر بی‌صدا ذخیره می‌شد)'], [sym, 'قرینه']]) {
    const bad = await zonesTo(s, [zone(square), { ...zone(poly), name: 'دوم' }]);
    assert.equal(bad.status, 400, `${why}: ${JSON.stringify(bad.body)}`);
    assert.equal(bad.body.error.code, 'bad_zone');
    assert.match(bad.body.error.message, /^ناحیهٔ 2: لبهٔ نقطه‌های 1 و 2 لبهٔ نقطه‌های 3 و 4 را می‌بُرد؛ نقطه‌ها را به ترتیبِ دورِ ناحیه بنویسید$/, why);
  }
  assert.equal((await many('SELECT 1 FROM storefront_zones WHERE shop_id = $1', [s.shopId])).length, 0, 'هیچ ناحیه‌ای نوشته نشد');

  //  نقطهٔ پایانیِ برابر با نخستین (رونوشت از ابزارِ نقشه) پاپیون نیست
  assert.equal((await zonesTo(s, [zone([...square, square[0]])])).status, 200);

  //  پاپیونی که پیش از این سنجش ذخیره شده بود
  const at = now();
  await query('DELETE FROM storefront_zones WHERE shop_id = $1', [s.shopId]);
  await query(
    `INSERT INTO storefront_zones (id, shop_id, name, kind, polygon, fee_minor, min_order_minor, active, sort, updated_by, created_at, updated_at)
     VALUES ($1,$2,'کارته‌سه','polygon',$3::jsonb,3000,0,true,0,'',$4,$4)`,
    [ulid(at), s.shopId, JSON.stringify(bow), at]
  );
  const kept = await zonesTo(s, [zone(bow, 4500)]);
  assert.equal(kept.status, 200, `ذخیرهٔ هزینه به‌خاطرِ چندضلعیِ پیشین نیفتاد: ${JSON.stringify(kept.body)}`);
  //  چرا: پینِ نیمهٔ شمالیِ همان مربع با پاپیون بیرون است
  const north = `/api/store/${s.slug}/delivery?lat=34.555&lng=69.17`;
  assert.equal((await h.get(north)).body.inZone, false);
  const moved = bow.map((p, i) => (i === 3 ? [34.58, 69.12] : p));
  const edited = await zonesTo(s, [zone(moved, 4500)]);
  assert.equal(edited.status, 400, 'چندضلعیِ تغییرکرده دوباره سنجیده می‌شود');
  assert.equal(edited.body.error.code, 'bad_zone');
  //  همان نقطه‌ها به ترتیبِ دور ⇒ پذیرفته، و پینِ نیمهٔ جاافتادهٔ پاپیون حالا درون است
  const fixed = await zonesTo(s, [zone(square, 4500)]);
  assert.equal(fixed.status, 200, JSON.stringify(fixed.body));
  const now2 = await h.get(north);
  assert.equal(now2.body.inZone, true, JSON.stringify(now2.body));
});
