'use strict';
/**
 * CHK-08 (§۴.۴) — عکسِ دروازه و یادداشتِ صوتیِ نشانیِ یک سفارش (`lib/order-address-media.js`، مهاجرتِ ۸۴۷؛ طراحیِ مشترکِ
 * `lib/address-media.js` با نشانیِ حساب — درِ یکتای دکان/پیک و انتخابِ «سفارش، وگرنه نشانیِ ذخیره‌شده» در `address-media-door.test.js`).
 *
 * آن‌چه قفل می‌شود:
 *   ⛔ پرچمِ `shop.storefront.address_media` خاموش ⇒ درِ مشتری ۴۰۴، نمای دکان `{enabled:false}` و بایت ۴۰۴، هیچ ردیفی.
 *   ⛔ بارگذاری فقط با توکنِ پیگیریِ همان سفارش (نادرست ⇒ ۴۰۴)؛ نوع از روی بایت (HTML ِ «image/jpeg» ⇒ ۴۱۵،
 *      صدا به‌جای عکس ⇒ ۴۱۵)؛ بزرگ‌تر از سقف ⇒ ۴۱۳؛ EXIF/GPS ِ عکس پیش از ذخیره پاک؛ دوباره ⇒ جایگزین؛ پس از سپردن به
 *      پیک ⇒ ۴۰۹؛ سقفِ نرخِ هر سفارش ⇒ ۴۲۹.
 *   ⛔ خواندن: کارمندِ `orders.contact` (صاحب و مدیر بله؛ صندوق‌دار و انباردار ۴۰۳)؛ دکانِ دیگر ۴۰۴؛ پیکِ همان مأموریت پس از
 *      «قبول» بله، پیکِ دیگر ۴۰۴؛ پیک از درِ کارمند نه (`courier_scope`).
 */
process.env.RATE_ADDRESS_MEDIA_MAX = '6';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const M = require('../src/lib/order-address-media');
const config = require('../src/config');
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
async function staffBytes(token, id, kind) {
  const r = await h.download(`/api/shop/orders/${id}/address-media/${kind}`, { token });
  return { status: r.status, headers: r.headers, buf: r.buffer };
}

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: درِ مشتری ۴۰۴، نمای دکان `{enabled:false}` و بایتش ۴۰۴، هیچ ردیفی', async () => {
  const s = await liveShop('دروازه-خاموش', { on: false });
  const o = await order(s);
  const up = await upload(s, o.token, 'gate-photo', jpegWithGps());
  assert.equal(up.status, 404, JSON.stringify(up.body));
  assert.equal((await h.get(base(s, o.token))).status, 404);
  assert.deepEqual((await staffList(s.token, o.orderId)).body, { enabled: false });
  assert.equal((await staffBytes(s.token, o.orderId, 'gate-photo')).status, 404);
  assert.equal((await h.get(`/api/store/${s.slug}/orders/${o.token}`)).body.order.addressMedia, undefined, 'پیگیری بی کلیدِ addressMedia');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE shop_id=$1', [s.shopId])).n), 0);
  const { KNOWN } = require('../src/routes/admin-flags');
  const k = KNOWN.find((x) => x.key === M.FLAG);
  assert.deepEqual([k.scope, k.default], ['shop', false]);
});

test('CHK-08: بارگذاری با توکن — نوع از بایت، سقفِ اندازه، EXIF پاک، جایگزینی، برداشتن؛ کارمند می‌خواند', async () => {
  const s = await liveShop('دروازه-روشن');
  const o = await order(s);

  //  توکنِ نادرست ⇒ ۴۰۴؛ نوعِ ناشناس ⇒ ۴۰۴
  assert.equal((await upload(s, 'not-a-token', 'gate-photo', jpegWithGps())).status, 404);
  assert.equal((await upload(s, o.token, 'selfie', jpegWithGps())).status, 404);
  //  HTML که خود را عکس می‌نامد ⇒ ۴۱۵ (پسوند/سرآیند ملاک نیست)
  const html = Buffer.from('<html><script>alert(1)</script></html>'.padEnd(200, ' '));
  let r = await h.raw('PUT', `${base(s, o.token)}/gate-photo`, html, { headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(r.status, 415, JSON.stringify(r.body));
  //  صدا به‌جای عکس و عکس به‌جای صدا ⇒ ۴۱۵
  assert.equal((await upload(s, o.token, 'gate-photo', ogg())).status, 415);
  assert.equal((await upload(s, o.token, 'voice-note', jpegWithGps())).status, 415);
  //  بزرگ‌تر از سقف ⇒ ۴۱۳ (پیش از هر نوشتن)
  r = await upload(s, o.token, 'voice-note', ogg(800 * 1024));
  assert.equal(r.status, 413, JSON.stringify(r.body));
  assert.equal(code(r), 'too_large');
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE shop_id=$1', [s.shopId])).n), 0);

  //  ⛔ بدنهٔ JSON (شیء، آرایه، رشته) به‌جای بایت ⇒ ۴۰۰ `bad_media` پیش از هر `.length`، نه ۵۰۰ (CodeQL: type confusion)
  resetAll();
  for (const body of [{ a: 1 }, [1, 2, 3], 'x'.repeat(40)]) {
    const j = await h.put(`${base(s, o.token)}/gate-photo`, body);
    //  شیء و آرایه به درِ رسانه می‌رسند ⇒ `bad_media`؛ رشتهٔ تنها را خودِ parser ِ JSON ِ برنامه (strict) رد می‌کند ⇒ `bad_json`
    assert.deepEqual([j.status, code(j)], [400, typeof body === 'string' ? 'bad_json' : 'bad_media'], JSON.stringify(j.body));
  }
  //  پارامترِ تکراری در پرسش بی‌اثر است (نوع فقط از مسیر)؛ نوعِ ناجور در مسیر ⇒ ۴۰۴ — هیچ‌کدام ۵۰۰ نیست
  const dup = await h.api('DELETE', `${base(s, o.token)}/gate-photo?kind=a&kind=b`);
  assert.deepEqual([dup.status, dup.body.deleted], [200, false], JSON.stringify(dup.body));
  assert.equal((await h.api('DELETE', `${base(s, o.token)}/gate-photo,voice-note`)).status, 404);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE shop_id=$1', [s.shopId])).n), 0);

  resetAll();   //  ردهای بالا هم از سقفِ هر سفارش می‌خورند (همان سقف جدا در آزمونِ آخر)
  const photo = await upload(s, o.token, 'gate-photo', jpegWithGps());
  assert.equal(photo.status, 201, JSON.stringify(photo.body));
  assert.deepEqual([photo.body.media.kind, photo.body.media.mime], ['gatePhoto', 'image/jpeg']);
  const voice = await upload(s, o.token, 'voice-note', ogg());
  assert.equal(voice.status, 201, JSON.stringify(voice.body));
  assert.equal(voice.body.media.mime, 'audio/ogg');
  //  همان بایت دوباره ⇒ همان ردیف؛ بایتِ دیگر ⇒ جایگزین (هر سفارش از هر نوع یکی)
  assert.equal((await upload(s, o.token, 'voice-note', ogg())).body.unchanged, true);
  const again = await upload(s, o.token, 'voice-note', ogg(400, 7));
  assert.equal(again.status, 200);
  assert.equal(again.body.replaced, true);
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM order_address_media WHERE order_id=$1 AND kind='voice_note'`, [o.orderId])).n), 1);

  //  پیگیری: کلیدِ `addressMedia` با سقف‌ها و همان نما (فرمِ `store/order-addr-media.js`)
  const tr = (await h.get(`/api/store/${s.slug}/orders/${o.token}`)).body.order.addressMedia;
  assert.deepEqual([tr.photoMax, tr.voiceMax, tr.gatePhoto.kind, tr.voiceNote.kind, tr.saved], [1024 * 1024, 768 * 1024, 'gatePhoto', 'voiceNote',
    { gatePhoto: false, voiceNote: false }]);
  //  مشتری نمای خودش را می‌بیند (بی بایت)
  const mine = await h.get(base(s, o.token));
  assert.equal(mine.status, 200);
  assert.deepEqual([mine.body.gatePhoto.mime, mine.body.voiceNote.mime], ['image/jpeg', 'audio/ogg']);

  //  کارمند: نما و بایت با سرآیندهای امن؛ EXIF/XMP پاک
  const list = await staffList(s.token, o.orderId);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual([list.body.enabled, list.body.gatePhoto.source, list.body.voiceNote.source], [true, 'order', 'order']);
  const got = await staffBytes(s.token, o.orderId, 'gate-photo');
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/jpeg');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.match(got.headers.get('content-security-policy'), /sandbox/);
  assert.match(got.headers.get('cache-control'), /no-store/);
  const txt = got.buf.toString('latin1');
  assert.ok(!txt.includes('GATE-GPS-SECRET') && !txt.includes('GATE-XMP-SECRET'), 'EXIF ِ مکان و XMP پیش از ذخیره پاک');
  assert.equal(got.buf[0], 0xff);
  assert.equal((await staffBytes(s.token, o.orderId, '01ARZ3NDEKTSV4RRFFQ69G5FAV')).status, 404);
  assert.equal((await staffBytes(s.token, o.orderId, 'gate_photo')).status, 404, 'فقط نامِ نشانی (gate-photo)');

  //  برداشتن ⇒ رفت
  const del = await h.api('DELETE', `${base(s, o.token)}/voice-note`);
  assert.equal(del.status, 200);
  assert.equal(del.body.deleted, true);
  const after = (await staffList(s.token, o.orderId)).body;
  assert.deepEqual([Boolean(after.gatePhoto), after.voiceNote], [true, null]);
  assert.equal((await staffBytes(s.token, o.orderId, 'voice-note')).status, 404);
});

test('⛔ ۴۰۳/۴۰۴: مدیر بله، صندوق‌دار و انباردار ۴۰۳؛ دکانِ دیگر ۴۰۴؛ پیکِ همان مأموریت پس از «قبول» بله، پیکِ دیگر ۴۰۴؛ پس از سپردن ۴۰۹', async () => {
  const s = await liveShop('دروازه-نقش');
  const B = await liveShop('دروازه-دیگر');
  const o = await order(s);
  assert.equal((await upload(s, o.token, 'gate-photo', jpegWithGps())).status, 201);

  const manager = await member(s.shopId, 'manager');
  assert.equal((await staffList(manager.accessToken, o.orderId)).status, 200);
  assert.equal((await staffBytes(manager.accessToken, o.orderId, 'gate-photo')).status, 200);
  //  `orders.view` بس نیست: صندوق‌دار سفارش را می‌بیند ولی صدا و عکسِ درِ خانهٔ مشتری را نه (همان شمارهٔ کامل)
  for (const role of ['cashier', 'storekeeper']) {
    const m = await member(s.shopId, role);
    const r = await staffList(m.accessToken, o.orderId);
    assert.equal(r.status, 403, `${role}: ${JSON.stringify(r.body)}`);
    assert.equal(code(r), 'permission_denied');
    assert.equal((await staffBytes(m.accessToken, o.orderId, 'gate-photo')).status, 403);
  }
  //  دکانِ دیگر (حتی با شناسهٔ درست) ⇒ ۴۰۴
  let r = await staffList(B.token, o.orderId);
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal((await staffBytes(B.token, o.orderId, 'gate-photo')).status, 404);
  //  توکنِ همین سفارش روی ویترینِ دیگر ⇒ ۴۰۴
  assert.equal((await upload(B, o.token, 'gate-photo', jpegWithGps())).status, 404);

  //  سپردن به پیکِ اول
  for (const to of ['accepted', 'picking', 'packed']) {
    assert.equal((await h.post(`/api/shop/orders/${o.orderId}/transition`, { to }, { token: s.token })).status, 200);
  }
  const a = await h.post(`/api/shop/delivery/orders/${o.orderId}/assign`,
    { mode: 'shop_courier', courierId: s.courier.id, overrideCashCap: true }, { token: s.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const aid = a.body.assignment.id;
  let seqNo = 0;
  const ev = async (type) => {
    const e = await h.post('/api/courier/events', { deviceId: 'courier-phone', events: [{ eventId: `ev-${uniq()}-${type}`, assignmentId: aid, type,
      deviceTime: Date.now(), deviceSeq: ++seqNo }] }, { token: s.courier.token });
    assert.deepEqual([e.status, e.body.results?.[0]?.status], [200, 'applied'], JSON.stringify(e.body));
    return e;
  };
  //  پیش از «قبول» ⇒ هنوز نه (همان قاعدهٔ «تماس با مشتری»)
  assert.equal((await h.download(`/api/courier/missions/${aid}/address-media/gate-photo`, { token: s.courier.token })).status, 404);
  assert.equal((await ev('assigned')).status, 200);
  const mine = await h.get(`/api/courier/missions/${aid}/address-media`, { token: s.courier.token });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
  assert.deepEqual([mine.body.gatePhoto.source, mine.body.voiceNote], ['order', null]);
  const bytes = await h.download(`/api/courier/missions/${aid}/address-media/gate-photo`, { token: s.courier.token });
  assert.equal(bytes.status, 200);
  assert.equal(bytes.headers.get('content-type'), 'image/jpeg');
  //  پیکِ دیگرِ همان دکان ⇒ ۴۰۴
  r = await h.get(`/api/courier/missions/${aid}/address-media`, { token: s.other.token });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal((await h.download(`/api/courier/missions/${aid}/address-media/gate-photo`, { token: s.other.token })).status, 404);

  //  پس از برداشتنِ بسته از دکان، مشتری دیگر نمی‌گذارد/برنمی‌دارد — پیکِ در راه هنوز می‌بیند
  assert.equal((await ev('picked_up')).status, 200);
  assert.equal((await h.download(`/api/courier/missions/${aid}/address-media/gate-photo`, { token: s.courier.token })).status, 200);
  assert.equal((await h.get(`/api/store/${s.slug}/orders/${o.token}`)).body.order.addressMedia, undefined, 'سپرده به پیک ⇒ بی کلید');
  r = await upload(s, o.token, 'voice-note', ogg());
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(code(r), 'order_closed');
  assert.equal((await h.api('DELETE', `${base(s, o.token)}/gate-photo`)).status, 409);
});

test('⛔ سقفِ نرخِ هر سفارش: هفتمین بارگذاری در پنجره ⇒ ۴۲۹', async () => {
  const s = await liveShop('دروازه-نرخ');
  const o = await order(s);
  for (let i = 0; i < 6; i++) {
    const r = await upload(s, o.token, 'voice-note', ogg(300, i + 1));
    assert.ok([200, 201].includes(r.status), `${i}: ${JSON.stringify(r.body)}`);
  }
  const r = await upload(s, o.token, 'voice-note', ogg(300, 99));
  assert.equal(r.status, 429, JSON.stringify(r.body));
});

test('⛔ توکن و نوع پیش از خواندنِ بدنه: توکنِ نادرست یا نوعِ ناشناخته با بدنهٔ بزرگ‌تر از سقف ⇒ ۴۰۴ (نه ۴۱۳)، بی هیچ ردیف', async () => {
  const s = await liveShop('ترتیبِ در');
  const o = await order(s);
  const big = Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(config.addressMedia.voiceMax + config.addressMedia.photoMax, 1)]);
  const bad = await upload(s, `${o.token}x`, 'voice-note', big);
  assert.equal(bad.status, 404, JSON.stringify(bad.body));
  const kind = await upload(s, o.token, 'selfie', big);
  assert.equal(kind.status, 404, JSON.stringify(kind.body));
  //  توکنِ درست و نوعِ درست ⇒ همان ۴۱۳ ِ سقف
  assert.equal((await upload(s, o.token, 'voice-note', big)).status, 413);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM order_address_media WHERE order_id=$1', [o.orderId])).n, 0);
});
