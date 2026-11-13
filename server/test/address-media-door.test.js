'use strict';
/**
 * رسانهٔ نشانی — **یک در و یک نما برای دکان و پیک** (`lib/address-media.js`، یکی‌سازیِ CRM-09 و CHK-08):
 *
 *   ۱) برای هر نوع: رسانهٔ خودِ سفارش (۸۴۷، توکنِ پیگیری) اگر هست، وگرنه رسانهٔ نشانیِ ذخیره‌شدهٔ همان سفارش (۸۳۶، `savedId`)
 *      — `source`: order | address؛ بایت همان انتخاب را دارد. چهار حالت: فقط نشانی، هر دو، فقط سفارش (مهمان)، هیچ.
 *      برداشتنِ رسانهٔ سفارش ⇒ دوباره رسانهٔ نشانی. پیگیریِ مشتری `saved` را می‌بیند (فقط بولی).
 *   ۲) ⛔ نقش: `orders.contact` + «پنهان کردنِ شمارهٔ مشتری» ِ صاحب — صاحب و مدیر بله؛ صندوق‌دار (`orders.view`) و شاگرد ۴۰۳؛
 *      مدیری که صاحب شماره را از او بسته ۴۰۳. دکانِ دیگر ۴۰۴. هر دیدنِ بایت در سابقه با `source`.
 *   ۳) پیکِ همان مأموریت (پس از «قبول») همان نما و همان بایتِ دکان را می‌بیند؛ پیکِ دیگر ۴۰۴.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const { cleanImage } = require('../src/lib/chat-media');
const { FLAG } = require('../src/lib/address-media');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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
  const o = { token: u.accessToken, shopId: made.body.shop.id, slug: `amd-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.delivery', FLAG]) await setFlag(o.shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 0, minOrderMinor: 0,
  }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-amd' },
    body: { device_id: 'dev-amd', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products',
      row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return o;
}

async function ticketOf(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

/** مشتریِ حساب‌دار (نشست) — یا مهمان (`account: false`، فقط بلیتِ شماره) */
async function shopper(slug, phone, { account = true } = {}) {
  const ticket = await ticketOf(slug, phone);
  if (!account) return { phone, ticket, headers: {} };
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': ticket } });
  assert.ok(a.body.session, JSON.stringify(a.body));
  return { phone, ticket, headers: { 'X-Store-Session': a.body.session } };
}

async function savedAddress(slug, who) {
  const r = await h.post(`/api/store/${slug}/account/addresses`,
    { label: 'خانه', text: 'کارتهٔ ۴', landmark: 'روبه‌روی نانوایی', lat: 34.5553, lng: 69.2075 }, { headers: who.headers });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.address.id;
}

/** سفارشِ ارسالی ⇒ `{id, token}`؛ با [savedId] ⇒ پیوند به نشانیِ ذخیره‌شدهٔ همان حساب */
async function checkout(o, who, savedId = '') {
  const r = await h.post(`/api/store/${o.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone: who.phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
    address: { lat: 34.5553, lng: 69.2075, text: 'کارتهٔ ۴', ...(savedId ? { savedId } : {}) },
  }, { headers: { 'Idempotency-Key': `amd-${uniq()}-key`, 'X-Store-Ticket': who.ticket, ...who.headers } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [o.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}

async function member(shopId, role) {
  const u = await h.newUser(`عضو ${uniq()}`, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return u;
}

async function courierOf(o, name) {
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: o.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const uid = `amd-courier-${uniq()}`;
  const r = await h.post('/api/auth/staff', { code: inv.body.code, name, device: { uid, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.accessToken;
  return { token, uid, id: (await h.get('/api/courier/me', { token })).body.courier.id };
}

/** JPEG با EXIF ِ GPS و توضیحِ مکان؛ آن‌چه ذخیره می‌شود `clean(...)` */
const JPEG = (seed) => F.jpeg(64, 48, { seed, gps: true });
const clean = (buf) => cleanImage(buf, 'image/jpeg');
const OGG = (n, fill) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(n, fill)]);

const accountPut = (o, who, addr, kind, buf) => h.raw('PUT', `/api/store/${o.slug}/account/addresses/${addr}/media/${kind}`, buf, { headers: who.headers });
const orderPut = (o, token, kind, buf) => h.raw('PUT', `/api/store/${o.slug}/orders/${token}/address-media/${kind}`, buf);
const view = (token, orderId) => h.get(`/api/shop/orders/${orderId}/address-media`, { token });
const bytes = (token, orderId, kind) => h.download(`/api/shop/orders/${orderId}/address-media/${kind}`, { token });
const sources = (b) => [b.gatePhoto && b.gatePhoto.source, b.voiceNote && b.voiceNote.source];
const tracking = async (o, token) => (await h.get(`/api/store/${o.slug}/orders/${token}`)).body.order.addressMedia;

/* ══════════════════════════════════════════════════════════════════ */

test('یک در، یک نما: رسانهٔ سفارش اگر هست، وگرنه نشانیِ ذخیره‌شده — فقط نشانی، هر دو، فقط سفارش، هیچ', async () => {
  const o = await liveStore('یک-در');
  const who = await shopper(o.slug, '0791240001');
  const addr = await savedAddress(o.slug, who);
  assert.equal((await accountPut(o, who, addr, 'gate-photo', JPEG(11))).status, 201);
  assert.equal((await accountPut(o, who, addr, 'voice-note', OGG(200, 3))).status, 201);

  //  (۱) فقط نشانیِ ذخیره‌شده
  const A = await checkout(o, who, addr);
  let v = await view(o.token, A.id);
  assert.equal(v.status, 200, JSON.stringify(v.body));
  assert.equal(v.body.enabled, true);
  assert.deepEqual(sources(v.body), ['address', 'address']);
  assert.ok((await bytes(o.token, A.id, 'gate-photo')).buffer.equals(clean(JPEG(11))));
  assert.ok((await bytes(o.token, A.id, 'voice-note')).buffer.equals(OGG(200, 3)));
  let tr = await tracking(o, A.token);
  assert.deepEqual([tr.gatePhoto, tr.voiceNote, tr.saved], [null, null, { gatePhoto: true, voiceNote: true }], 'پیگیری: «از نشانیِ ذخیره‌شده»');

  //  (۲) هر دو: صدای همین سفارش بر صدای نشانی مقدم است؛ عکس هنوز از نشانی
  assert.equal((await orderPut(o, A.token, 'voice-note', OGG(300, 9))).status, 201);
  v = await view(o.token, A.id);
  assert.deepEqual(sources(v.body), ['address', 'order']);
  assert.equal(v.body.voiceNote.size, 304);
  assert.ok((await bytes(o.token, A.id, 'voice-note')).buffer.equals(OGG(300, 9)));
  assert.ok((await bytes(o.token, A.id, 'gate-photo')).buffer.equals(clean(JPEG(11))));
  tr = await tracking(o, A.token);
  assert.deepEqual([tr.gatePhoto, tr.voiceNote.kind, tr.saved], [null, 'voiceNote', { gatePhoto: true, voiceNote: true }]);
  //  برداشتنِ صدای سفارش ⇒ دوباره صدای نشانی
  assert.equal((await h.api('DELETE', `/api/store/${o.slug}/orders/${A.token}/address-media/voice-note`)).body.deleted, true);
  assert.deepEqual(sources((await view(o.token, A.id)).body), ['address', 'address']);
  assert.ok((await bytes(o.token, A.id, 'voice-note')).buffer.equals(OGG(200, 3)));

  //  (۳) فقط رسانهٔ سفارش: مهمان، بی حساب و بی نشانیِ ذخیره‌شده
  const guest = await shopper(o.slug, '0791240002', { account: false });
  const G = await checkout(o, guest);
  assert.equal((await orderPut(o, G.token, 'gate-photo', JPEG(22))).status, 201);
  v = await view(o.token, G.id);
  assert.deepEqual(sources(v.body), ['order', null]);
  const got = await bytes(o.token, G.id, 'gate-photo');
  assert.ok(got.buffer.equals(clean(JPEG(22))));
  assert.ok(!got.buffer.includes(Buffer.from('shot at', 'latin1')), 'EXIF/توضیحِ مکان پیش از ذخیره پاک');
  assert.equal((await bytes(o.token, G.id, 'voice-note')).status, 404);
  assert.deepEqual((await tracking(o, G.token)).saved, { gatePhoto: false, voiceNote: false });

  //  (۴) هیچ
  const N = await checkout(o, guest);
  assert.deepEqual((await view(o.token, N.id)).body, { enabled: true, gatePhoto: null, voiceNote: null });
  assert.equal((await bytes(o.token, N.id, 'gate-photo')).status, 404);
  assert.equal((await bytes(o.token, N.id, 'voice-note')).status, 404);

  //  سابقه: هر دیدنِ بایت با منبعش
  const logs = await many(`SELECT detail FROM audit_logs WHERE shop_id=$1 AND action='order.address_media.view' AND target_id=$2`, [o.shopId, A.id]);
  assert.deepEqual([...new Set(logs.map((x) => x.detail.source))].sort(), ['address', 'order']);
});

test('⛔ نقش و مالکیت: صاحب و مدیر بله، صندوق‌دار و شاگرد ۴۰۳، مدیرِ بسته‌شده ۴۰۳؛ دکانِ دیگر ۴۰۴؛ پیکِ همان مأموریت همان نما، پیکِ دیگر ۴۰۴', async () => {
  const o = await liveStore('یک-در-نقش');
  const B = await liveStore('یک-در-دیگر');
  const who = await shopper(o.slug, '0791240003');
  const addr = await savedAddress(o.slug, who);
  assert.equal((await accountPut(o, who, addr, 'gate-photo', JPEG(33))).status, 201);
  const X = await checkout(o, who, addr);
  assert.equal((await orderPut(o, X.token, 'voice-note', OGG(250, 5))).status, 201);

  const owner = await view(o.token, X.id);
  assert.equal(owner.status, 200, JSON.stringify(owner.body));
  assert.deepEqual(sources(owner.body), ['address', 'order']);
  assert.equal(owner.headers.get('cache-control'), 'private, no-store');
  const ob = await bytes(o.token, X.id, 'gate-photo');
  assert.equal(ob.status, 200);
  assert.match(ob.headers.get('content-security-policy'), /sandbox/);

  const manager = await member(o.shopId, 'manager');
  assert.equal((await view(manager.accessToken, X.id)).status, 200);
  assert.equal((await bytes(manager.accessToken, X.id, 'voice-note')).status, 200);
  //  `orders.view` بس نیست (صندوق‌دار)؛ شاگرد هم نه
  for (const role of ['cashier', 'staff']) {
    const m = await member(o.shopId, role);
    const r = await view(m.accessToken, X.id);
    assert.equal(r.status, 403, `${role}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'permission_denied');
    assert.equal((await bytes(m.accessToken, X.id, 'gate-photo')).status, 403, role);
  }
  //  صاحب دیدنِ شمارهٔ مشتری را از مدیر بست ⇒ عکس و صدای نشانی هم نه (`requireOrdersContact`)
  const close = await h.put(`/api/shop/permissions/members/${manager.user.id}`, { perms: { 'customers.contact': { mode: 'deny' } } }, { token: o.token });
  assert.equal(close.status, 200, JSON.stringify(close.body));
  assert.equal((await view(manager.accessToken, X.id)).status, 403);
  assert.equal((await bytes(manager.accessToken, X.id, 'gate-photo')).status, 403);
  //  دکانِ دیگر با شناسهٔ درست ⇒ ۴۰۴
  assert.equal((await view(B.token, X.id)).status, 404);
  assert.equal((await bytes(B.token, X.id, 'gate-photo')).status, 404);

  //  پیک: همان نما و همان بایت، فقط مأموریتِ خودش و پس از «قبول»
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${X.id}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  const courier = await courierOf(o, 'پیکِ یک');
  const other = await courierOf(o, 'پیکِ دو');
  const asg = await h.post(`/api/shop/delivery/orders/${X.id}/assign`, { mode: 'shop_courier', courierId: courier.id, overrideCashCap: true },
    { token: o.token });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  const base = `/api/courier/missions/${asg.body.assignment.id}/address-media`;
  assert.equal((await h.download(`${base}/gate-photo`, { token: courier.token })).status, 404, 'پیش از «قبول» نه');
  const sent = await h.post('/api/courier/events', { deviceId: courier.uid, events: [
    { eventId: `ev-${uniq()}`, assignmentId: asg.body.assignment.id, type: 'assigned', deviceTime: Date.now(), deviceSeq: 1 },
  ] }, { token: courier.token });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  const cv = await h.get(base, { token: courier.token });
  assert.equal(cv.status, 200, JSON.stringify(cv.body));
  assert.deepEqual(cv.body, owner.body, 'پیک همان نمای دکان را می‌بیند');
  assert.ok((await h.download(`${base}/gate-photo`, { token: courier.token })).buffer.equals(clean(JPEG(33))));
  assert.ok((await h.download(`${base}/voice-note`, { token: courier.token })).buffer.equals(OGG(250, 5)));
  assert.equal((await h.get(base, { token: other.token })).status, 404);
  assert.equal((await h.download(`${base}/voice-note`, { token: other.token })).status, 404);
  //  پیک از درِ دکان نه (دامنهٔ توکنِ پیک)
  assert.ok([403, 404].includes((await view(courier.token, X.id)).status));
});

test('⛔ نشانیِ ذخیره‌شده فقط برای سفارشِ باز: پس از بسته شدن نه نما نه بایت، حتی رسانه‌ای که بعداً از ویترینِ دکانِ دیگر آمد؛ FINISHED از ماشینِ وضعیت', async () => {
  //  FINISHED = هر وضعیتی که `out_for_delivery` از آن دست‌یافتنی نیست (وضعیتِ تازه بی طبقه‌بندی ⇒ سرخ)
  const { ORDER_TRANSITIONS } = require('../src/lib/order-states');
  const { FINISHED } = require('../src/lib/address-media');
  const { CLOSED } = require('../src/lib/order-address-media');
  const reaches = (from, seen = new Set()) => {
    if (from === 'out_for_delivery') return true;
    seen.add(from);
    return ORDER_TRANSITIONS[from].some((n) => !seen.has(n) && reaches(n, seen));
  };
  const derived = Object.keys(ORDER_TRANSITIONS).filter((s) => !reaches(s)).sort();
  assert.deepEqual([...FINISHED].sort(), derived);
  assert.deepEqual([...CLOSED].sort(), ['out_for_delivery', ...derived].sort(), 'گذاشتن/برداشتنِ مشتری پس از سپردن به پیک بسته');
  for (const s of ['return_requested', 'refunded_partial']) assert.ok(CLOSED.includes(s), s);

  const A = await liveStore('بسته-الف');
  const B = await liveStore('بسته-ب');
  const who = await shopper(A.slug, '0791240011');
  const addr = await savedAddress(A.slug, who);
  assert.equal((await accountPut(A, who, addr, 'gate-photo', JPEG(41))).status, 201);
  const X = await checkout(A, who, addr);
  assert.equal((await orderPut(A, X.token, 'voice-note', OGG(210, 4))).status, 201);
  assert.deepEqual(sources((await view(A.token, X.id)).body), ['address', 'order'], 'باز: هر دو');

  const rej = await h.post(`/api/shop/orders/${X.id}/transition`, { to: 'rejected', reason: 'busy' }, { token: A.token });
  assert.equal(rej.status, 200, JSON.stringify(rej.body));
  //  سفارشِ بازِ دیگری در همان دکان (وضعیتِ همان سفارش سنجیده می‌شود، نه «هر سفارشِ باز»)
  await checkout(A, await shopper(A.slug, '0791240012', { account: false }));
  //  مشتری پس از آن از ویترینِ دکانِ دیگر برای همان نشانی صدا می‌گذارد (حسابِ سراسری)
  assert.equal((await accountPut(B, who, addr, 'voice-note', OGG(220, 6))).status, 201);
  const v = await view(A.token, X.id);
  assert.equal(v.status, 200);
  assert.deepEqual(sources(v.body), [null, 'order'], 'بسته: رسانهٔ نشانی نه؛ رسانهٔ خودِ سفارش می‌ماند');
  assert.equal((await bytes(A.token, X.id, 'gate-photo')).status, 404);
  assert.ok((await bytes(A.token, X.id, 'voice-note')).buffer.equals(OGG(210, 4)), 'صدای خودِ سفارش، نه صدای تازهٔ نشانی');
  //  مشتری هم دیگر چیزی نمی‌گذارد
  assert.equal((await orderPut(A, X.token, 'gate-photo', JPEG(42))).status, 409);
});

test('یادداشتِ صوتی: فقط صدا — MP4/3GP با ترکِ تصویر، WebM با کدکِ تصویر و Ogg ِ Theora ۴۱۵؛ اتمِ مکانِ M4A (©xyz، loci) بی جابه‌جایی پاک', () => {
  const { accept } = require('../src/lib/address-media');
  const atom = (type, body) => { const b = Buffer.alloc(8); b.writeUInt32BE(8 + body.length); b.write(type, 4, 'latin1'); return Buffer.concat([b, body]); };
  const hdlr = (h) => atom('hdlr', Buffer.concat([Buffer.alloc(8), Buffer.from(h, 'latin1'), Buffer.alloc(12)]));
  const mp4 = (brand, handlers, extra = []) => Buffer.concat([atom('ftyp', Buffer.from(`${brand}\0\0\0\0${brand}`, 'latin1')),
    atom('moov', Buffer.concat([...handlers.map((x) => atom('trak', atom('mdia', hdlr(x)))), ...extra])), atom('mdat', Buffer.alloc(64, 5))]);
  const code = (fn) => { try { fn(); return 'ok'; } catch (e) { return `${e.status} ${e.code}`; } };

  //  صدا می‌ماند
  assert.equal(code(() => accept('voice_note', mp4('M4A ', ['soun']))), 'ok');
  assert.equal(code(() => accept('voice_note', Buffer.concat([Buffer.from([0x1a, 0x45, 0xdf, 0xa3]), Buffer.from('\x42\x82\x84webm\x86\x86A_OPUS', 'latin1'), Buffer.alloc(40)]))), 'ok');
  assert.equal(code(() => accept('voice_note', Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(24), Buffer.from('OpusHead', 'latin1'), Buffer.alloc(20)]))), 'ok');
  //  تصویر نه
  for (const [label, buf] of [
    ['mp4 ِ isom با ترکِ vide', mp4('isom', ['soun', 'vide'])],
    ['3gp با ترکِ vide', mp4('3gp4', ['vide'])],
    ['webm ِ VP8', Buffer.concat([Buffer.from([0x1a, 0x45, 0xdf, 0xa3]), Buffer.from('\x42\x82\x84webm\x86\x85V_VP8', 'latin1'), Buffer.alloc(40)])],
    ['webm ِ AV1', Buffer.concat([Buffer.from([0x1a, 0x45, 0xdf, 0xa3]), Buffer.from('\x86\x85V_AV1', 'latin1'), Buffer.alloc(40)])],
    ['ogg ِ theora', Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(24), Buffer.from('\x80theora', 'latin1'), Buffer.alloc(20)])],
  ]) assert.equal(code(() => accept('voice_note', buf)), '415 unsupported_media', label);

  //  مکان: ©xyz (QuickTime) و loci (3GPP) ⇒ همان اندازه، اتمِ free، بی مختصات
  const loc = Buffer.from('+34.5553+069.2075/', 'latin1');
  const withLoc = mp4('M4A ', ['soun'], [atom('udta', Buffer.concat([atom('\xa9xyz', Buffer.concat([Buffer.alloc(4), loc])), atom('loci', loc)]))]);
  const ok = accept('voice_note', withLoc);
  assert.equal(ok.mime, 'audio/mp4');
  assert.equal(ok.data.length, withLoc.length, 'هیچ offset ِ stco/co64 جابه‌جا نمی‌شود');
  assert.ok(!ok.data.includes(loc) && !ok.data.includes(Buffer.from('\xa9xyz', 'latin1')) && !ok.data.includes(Buffer.from('loci', 'latin1')));
  assert.equal(ok.data.indexOf(Buffer.from('mdat')), withLoc.indexOf(Buffer.from('mdat')));
});
