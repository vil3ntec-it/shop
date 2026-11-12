'use strict';
/**
 * CHK-08 (§۴.۴) — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ سفارش (`lib/order-address-media.js`، مهاجرتِ ۸۴۷).
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ پرچمِ `shop.storefront.address_media` خاموش ⇒ هر سه در ۴۰۴ و هیچ ردیفی.
 *   ⛔ بارگذاری فقط با توکنِ پیگیریِ همان سفارش (نادرست ⇒ ۴۰۴)؛ نوع از روی بایت (HTML ِ «image/jpeg» ⇒ ۴۱۵،
 *      صدا به‌جای عکس ⇒ ۴۱۵)؛ بزرگ‌تر از سقف ⇒ ۴۱۳؛ EXIF/GPS ِ عکس پیش از ذخیره پاک؛ دوباره ⇒ جایگزین؛ پس از سپردن به
 *      پیک ⇒ ۴۰۹؛ سقفِ نرخِ هر سفارش ⇒ ۴۲۹.
 *   ⛔ خواندن: کارمندِ `orders.view` (صندوق‌دار بله، انباردار ۴۰۳)؛ دکانِ دیگر ۴۰۴؛ پیکِ همان مأموریت بله، پیکِ دیگر ۴۰۴؛
 *      پیک از درِ کارمند نه (`courier_scope`).
 */
process.env.RATE_ADDRESS_MEDIA_MAX = '6';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const M = require('../src/lib/order-address-media');
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
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-addr' },
  body: { device_id: 'dev-addr', schema_version: 2, ops },
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

async function liveShop(label, { on = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `am-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery']) await setFlag(s.shopId, k, true);
  if (on) await setFlag(s.shopId, M.FLAG, true);
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

/** سفارشِ ارسالیِ ویترین ⇒ `{orderId, token}` */
async function order(s) {
  const phone = '0791112233';
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتریِ دروازه', phone }, address: { landmark: 'پشتِ مسجد' },
    lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const orderId = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { orderId, token: r.body.order.token };
}

// ── بایت‌ها (همان شکلِ shop-chat.test.js) ─────────────────────────────
function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}
/** JPEG با Exif ِ GPS و XMP — ساختار درست، تصویر ساختگی */
function jpegWithGps() {
  const jfif = Buffer.from([0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0]);
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xe0, jfif),
    seg(0xe1, Buffer.concat([Buffer.from('Exif\0\0MM\0*\0\0\0\x08', 'latin1'), Buffer.from('GATE-GPS-SECRET', 'latin1')])),
    seg(0xe1, Buffer.from('http://ns.adobe.com/xap/1.0/\0<x:xmpmeta>GATE-XMP-SECRET</x:xmpmeta>', 'latin1')),
    seg(0xdb, Buffer.alloc(65, 1)),
    seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])),
    Buffer.alloc(200, 0x55),
    Buffer.from([0xff, 0xd9]),
  ]);
}
const ogg = (n = 300, fill = 3) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(n, fill)]);

const base = (s, t) => `/api/store/${s.slug}/orders/${t}/address-media`;
const upload = (s, t, kind, buf) => h.raw('PUT', `${base(s, t)}/${kind}`, buf);
const staffList = (token, id) => h.get(`/api/shop/orders/${id}/address-media`, { token });
const code = (r) => r.body?.error?.code;
async function staffBytes(token, id, mid) {
  const r = await h.download(`/api/shop/orders/${id}/address-media/${mid}`, { token });
  return { status: r.status, headers: r.headers, buf: r.buffer };
}

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: درِ مشتری، کارمند و پیک هر سه ۴۰۴ و هیچ ردیفی', async () => {
  const s = await liveShop('دروازه-خاموش', { on: false });
  const o = await order(s);
  const up = await upload(s, o.token, 'gate_photo', jpegWithGps());
  assert.equal(up.status, 404, JSON.stringify(up.body));
  assert.equal((await h.get(base(s, o.token))).status, 404);
  assert.equal((await staffList(s.token, o.orderId)).status, 404);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE shop_id=$1', [s.shopId])).n), 0);
  const { KNOWN } = require('../src/routes/admin-flags');
  const k = KNOWN.find((x) => x.key === M.FLAG);
  assert.deepEqual([k.scope, k.default], ['shop', false]);
});

test('CHK-08: بارگذاری با توکن — نوع از بایت، سقفِ اندازه، EXIF پاک، جایگزینی، برداشتن؛ کارمند می‌خواند', async () => {
  const s = await liveShop('دروازه-روشن');
  const o = await order(s);

  //  توکنِ نادرست ⇒ ۴۰۴؛ نوعِ ناشناس ⇒ ۴۰۴
  assert.equal((await upload(s, 'not-a-token', 'gate_photo', jpegWithGps())).status, 404);
  assert.equal((await upload(s, o.token, 'selfie', jpegWithGps())).status, 404);
  //  HTML که خود را عکس می‌نامد ⇒ ۴۱۵ (پسوند/سرآیند ملاک نیست)
  const html = Buffer.from('<html><script>alert(1)</script></html>'.padEnd(200, ' '));
  let r = await h.raw('PUT', `${base(s, o.token)}/gate_photo`, html, { headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(r.status, 415, JSON.stringify(r.body));
  //  صدا به‌جای عکس و عکس به‌جای صدا ⇒ ۴۱۵
  assert.equal((await upload(s, o.token, 'gate_photo', ogg())).status, 415);
  assert.equal((await upload(s, o.token, 'voice_note', jpegWithGps())).status, 415);
  //  بزرگ‌تر از سقف ⇒ ۴۱۳ (پیش از هر نوشتن)
  r = await upload(s, o.token, 'voice_note', ogg(800 * 1024));
  assert.equal(r.status, 413, JSON.stringify(r.body));
  assert.equal(code(r), 'too_large');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE shop_id=$1', [s.shopId])).n), 0);

  resetAll();   //  ردهای بالا هم از سقفِ هر سفارش می‌خورند (همان سقف جدا در آزمونِ آخر)
  const photo = await upload(s, o.token, 'gate_photo', jpegWithGps());
  assert.equal(photo.status, 201, JSON.stringify(photo.body));
  assert.equal(photo.body.mime, 'image/jpeg');
  const voice = await upload(s, o.token, 'voice_note', ogg());
  assert.equal(voice.status, 201, JSON.stringify(voice.body));
  assert.equal(voice.body.mime, 'audio/ogg');
  //  همان بایت دوباره ⇒ همان ردیف؛ بایتِ دیگر ⇒ جایگزین (هر سفارش از هر نوع یکی)
  assert.equal((await upload(s, o.token, 'voice_note', ogg())).body.unchanged, true);
  const again = await upload(s, o.token, 'voice_note', ogg(400, 7));
  assert.equal(again.status, 200);
  assert.equal(again.body.replaced, true);
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM order_address_media WHERE order_id=$1 AND kind='voice_note'`, [o.orderId])).n), 1);

  //  مشتری فهرستِ خودش را می‌بیند (بی بایت)
  const mine = await h.get(base(s, o.token));
  assert.equal(mine.status, 200);
  assert.deepEqual(mine.body.items.map((x) => x.kind), ['gate_photo', 'voice_note']);

  //  کارمند: فهرست و بایت با سرآیندهای امن؛ EXIF/XMP پاک
  const list = await staffList(s.token, o.orderId);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  const g = list.body.items.find((x) => x.kind === 'gate_photo');
  const got = await staffBytes(s.token, o.orderId, g.id);
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/jpeg');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.match(got.headers.get('content-security-policy'), /sandbox/);
  assert.match(got.headers.get('cache-control'), /no-store/);
  const txt = got.buf.toString('latin1');
  assert.ok(!txt.includes('GATE-GPS-SECRET') && !txt.includes('GATE-XMP-SECRET'), 'EXIF ِ مکان و XMP پیش از ذخیره پاک');
  assert.equal(got.buf[0], 0xff);
  assert.equal((await staffBytes(s.token, o.orderId, '01ARZ3NDEKTSV4RRFFQ69G5FAV')).status, 404);

  //  برداشتن ⇒ رفت
  const del = await h.api('DELETE', `${base(s, o.token)}/voice_note`);
  assert.equal(del.status, 200);
  assert.equal(del.body.removed, true);
  assert.deepEqual((await staffList(s.token, o.orderId)).body.items.map((x) => x.kind), ['gate_photo']);
});

test('⛔ ۴۰۳/۴۰۴: صندوق‌دار بله، انباردار ۴۰۳؛ دکانِ دیگر ۴۰۴؛ پیکِ همان مأموریت بله، پیکِ دیگر ۴۰۴؛ پس از سپردن ۴۰۹', async () => {
  const s = await liveShop('دروازه-نقش');
  const B = await liveShop('دروازه-دیگر');
  const o = await order(s);
  assert.equal((await upload(s, o.token, 'gate_photo', jpegWithGps())).status, 201);
  const mid = (await one('SELECT id FROM order_address_media WHERE order_id=$1', [o.orderId])).id;

  const cashier = await member(s.shopId, 'cashier');
  assert.equal((await staffList(cashier.accessToken, o.orderId)).status, 200);
  const keeper = await member(s.shopId, 'storekeeper');
  let r = await staffList(keeper.accessToken, o.orderId);
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(code(r), 'permission_denied');
  assert.equal((await staffBytes(keeper.accessToken, o.orderId, mid)).status, 403);
  //  دکانِ دیگر (حتی با شناسهٔ درست) ⇒ ۴۰۴
  r = await staffList(B.token, o.orderId);
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal((await staffBytes(B.token, o.orderId, mid)).status, 404);
  //  توکنِ همین سفارش روی ویترینِ دیگر ⇒ ۴۰۴
  assert.equal((await upload(B, o.token, 'gate_photo', jpegWithGps())).status, 404);

  //  سپردن به پیکِ اول
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${o.orderId}/transition`, { to }, { token: s.token })).status, 200);
  }
  const a = await h.post(`/api/shop/delivery/orders/${o.orderId}/assign`,
    { mode: 'shop_courier', courierId: s.courier.id, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const aid = a.body.assignment.id;
  const mine = await h.get(`/api/courier/missions/${aid}/address-media`, { token: s.courier.token });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.deepEqual(mine.body.items.map((x) => x.id), [mid]);
  const bytes = await h.download(`/api/courier/missions/${aid}/address-media/${mid}`, { token: s.courier.token });
  assert.equal(bytes.status, 200);
  assert.equal(bytes.headers.get('content-type'), 'image/jpeg');
  //  پیکِ دیگرِ همان دکان ⇒ ۴۰۴
  r = await h.get(`/api/courier/missions/${aid}/address-media`, { token: s.other.token });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal((await h.download(`/api/courier/missions/${aid}/address-media/${mid}`, { token: s.other.token })).status, 404);

  //  پس از برداشتنِ بسته از دکان، مشتری دیگر نمی‌گذارد/برنمی‌دارد
  const evs = ['assigned', 'picked_up'].map((type, i) => ({ eventId: `ev-${uniq()}-${i}`, assignmentId: aid, type, deviceTime: Date.now() }));
  const e = await h.post('/api/courier/events', { deviceId: 'courier-phone', events: evs }, { token: s.courier.token });
  assert.equal(e.status, 200, JSON.stringify(e.body));
  r = await upload(s, o.token, 'voice_note', ogg());
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(code(r), 'order_closed');
  assert.equal((await h.api('DELETE', `${base(s, o.token)}/gate_photo`)).status, 409);
});

test('⛔ سقفِ نرخِ هر سفارش: هفتمین بارگذاری در پنجره ⇒ ۴۲۹', async () => {
  const s = await liveShop('دروازه-نرخ');
  const o = await order(s);
  for (let i = 0; i < 6; i++) {
    const r = await upload(s, o.token, 'voice_note', ogg(300, i + 1));
    assert.ok([200, 201].includes(r.status), `${i}: ${JSON.stringify(r.body)}`);
  }
  const r = await upload(s, o.token, 'voice_note', ogg(300, 99));
  assert.equal(r.status, 429, JSON.stringify(r.body));
});
