'use strict';
/**
 * CRM-09 (§۴.۴ «یادداشتِ صوتی و عکسِ دروازه، اختیاری») — `lib/store-address-media.js`، مهاجرتِ ۸۳۶؛ طراحیِ مشترک با
 * رسانهٔ نشانیِ یک سفارش (CHK-08) در `lib/address-media.js` (درِ یکتای دکان/پیک: `address-media-door.test.js`).
 *
 *   ۱) ⛔ پرچمِ خاموشِ `shop.storefront.address_media`: نه `features.addressMedia`، درهای مشتری ۴۰۴، نمای دکان
 *      `{enabled:false}` و هیچ پیوندِ سفارشی.
 *   ۲) نوع از بایت: عکس فقط JPEG/PNG/WebP، صدا فقط Ogg/WebM/MP4/AMR؛ جابه‌جا ⇒ ۴۱۵، بزرگ‌تر از `config.addressMedia` ⇒ ۴۱۳؛
 *      EXIF/مکان و توضیحِ عکس پیش از ذخیره پاک؛ هر نشانی یکی از هر نوع (تازه ۲۰۱، جایگزین ۲۰۰).
 *   ۳) فقط مالِ همان حساب: حسابِ دیگر نه می‌خواند، نه می‌نویسد، نه پاک می‌کند (۴۰۴، بی ردیف).
 *   ۴) ثبت با `address.savedId` ِ نشانیِ خودِ همان حساب ⇒ صاحب در سفارش می‌بیند (با سابقه)؛ نشانیِ حسابِ دیگر ⇒ هیچ؛
 *      شاگرد ۴۰۳، دکانِ دیگر ۴۰۴؛ پاک کردنِ مشتری ⇒ دکان هم دیگر نمی‌بیند؛ پاک شدنِ نشانی ⇒ آبشاری.
 *   ۵) پیکِ همان مأموریت (پس از «قبول» و تا «تحویل شد») می‌بیند؛ پیکِ دیگر و صاحب از درِ پیک نه.
 *   ۶) rollback ِ ۸۳۶ هر دو جدول را برمی‌دارد و نشانی‌ها می‌مانند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const M = require('../src/lib/store-address-media');
const config = require('../src/config');
const F = require('./image-fixtures');
const { cleanImage } = require('../src/lib/chat-media');

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

async function liveStore(label, { media = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `am-${uniq()}` };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (media) await setFlag(o.shopId, M.FLAG, true);
  assert.equal((await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', deliveryFeeMinor: 0, minOrderMinor: 0,
  }, { token: o.token })).status, 200);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-am' },
    body: { device_id: 'dev-am', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products',
      row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120.5, category: 'خوراکی' } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  return o;
}

/** مشتری: بلیتِ شماره + نشستِ حساب */
async function shopper(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.ok(a.body.session, JSON.stringify(a.body));
  return { phone, ticket: c.body.ticket, headers: { 'X-Store-Session': a.body.session } };
}

async function addAddress(slug, who, label = 'خانه') {
  const r = await h.post(`/api/store/${slug}/account/addresses`,
    { label, text: 'کارتهٔ ۴، سرکِ دوم', landmark: 'روبه‌روی نانوایی', lat: 34.5553, lng: 69.2075 }, { headers: who.headers });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.address.id;
}

/** JPEG با EXIF ِ GPS و توضیحِ «shot at» (هر [n]/[b] یک عکسِ دیگر)؛ آن‌چه سرور نگه می‌دارد = `clean(...)` */
const JPEG = (n = 400, b = 7) => F.jpeg(64, 48, { seed: n * 31 + b, gps: true });
const clean = (buf) => cleanImage(buf, 'image/jpeg');
const OGG = (n = 300) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(n, 3)]);
const url = (slug, id, kind) => `/api/store/${slug}/account/addresses/${id}/media/${kind}`;
const put = (slug, who, id, kind, buf) => h.raw('PUT', url(slug, id, kind), buf, { headers: who.headers });
async function fetchBytes(p, headers) {
  const res = await fetch(`${h.base()}${p}`, { headers });
  return { status: res.status, buffer: Buffer.from(await res.arrayBuffer()), headers: res.headers };
}
const rows = async (addressId) => (await one('SELECT COUNT(*)::int AS n FROM store_address_media WHERE address_id=$1', [addressId])).n;

async function checkout(o, who, savedId) {
  const body = {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone: who.phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
    address: { lat: 34.5553, lng: 69.2075, text: 'کارتهٔ ۴', ...(savedId ? { savedId } : {}) },
  };
  const r = await h.post(`/api/store/${o.slug}/checkout`, body,
    { headers: { 'Idempotency-Key': `am-${uniq()}-key`, 'X-Store-Ticket': who.ticket, ...who.headers } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1', [o.shopId])).id;
}

test('⛔ پرچمِ خاموش: نه ویژگی، نه در، نه پیوندِ سفارش', async () => {
  const o = await liveStore('خاموش', { media: false });
  assert.equal((await h.get(`/api/store/${o.slug}`)).body.features?.addressMedia, undefined);
  const who = await shopper(o.slug, '0791230001');
  const id = await addAddress(o.slug, who);
  const r = await put(o.slug, who, id, 'gate-photo', JPEG());
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'address_media_disabled');
  assert.equal(await rows(id), 0);
  const list = await h.get(`/api/store/${o.slug}/account/addresses`, { headers: who.headers });
  assert.equal(list.body.addresses[0].media, undefined, 'پاسخِ نشانی‌ها همان پیشین');
  const orderId = await checkout(o, who, id);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM online_order_address_refs WHERE order_id=$1', [orderId])).n, 0);
  assert.deepEqual((await h.get(`/api/shop/orders/${orderId}/address-media`, { token: o.token })).body, { enabled: false });
  assert.equal((await h.download(`/api/shop/orders/${orderId}/address-media/gate-photo`, { token: o.token })).status, 404);
});

test('عکس و صدا: نوع از بایت، سقف، یکی از هر نوع، و فقط مالِ همان حساب', async () => {
  const o = await liveStore('رسانه');
  assert.equal((await h.get(`/api/store/${o.slug}`)).body.features.addressMedia, true);
  const who = await shopper(o.slug, '0791230002');
  const id = await addAddress(o.slug, who);

  const a = await put(o.slug, who, id, 'gate-photo', JPEG());
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.deepEqual({ ...a.body.media, createdAt: 0 }, { kind: 'gatePhoto', mime: 'image/jpeg', size: clean(JPEG()).length, createdAt: 0 });
  assert.equal(a.body.replaced, false);
  const v = await put(o.slug, who, id, 'voice-note', OGG());
  assert.equal(v.status, 201, JSON.stringify(v.body));
  assert.equal(v.body.media.mime, 'audio/ogg');

  //  جابه‌جا، بزرگ، ناشناخته
  assert.equal((await put(o.slug, who, id, 'gate-photo', OGG())).status, 415);
  assert.equal((await put(o.slug, who, id, 'voice-note', JPEG())).status, 415);
  assert.equal((await put(o.slug, who, id, 'gate-photo', Buffer.from('<svg onload=alert(1)></svg>'.padEnd(64, ' ')))).status, 415);
  const big = await put(o.slug, who, id, 'gate-photo', Buffer.concat([JPEG(), Buffer.alloc(config.addressMedia.photoMax)]));
  assert.deepEqual([big.status, big.body.error.code], [413, 'too_large']);
  const long = await put(o.slug, who, id, 'voice-note', OGG(config.addressMedia.voiceMax));
  assert.deepEqual([long.status, long.body.error.code], [413, 'too_large']);
  assert.equal((await put(o.slug, who, id, 'voice-note', JPEG())).body.error.code, 'unsupported_media');
  assert.equal((await put(o.slug, who, id, 'selfie', JPEG())).status, 404);
  //  ⛔ بدنهٔ JSON (شیء، آرایه، رشته) به‌جای بایت ⇒ ۴۰۰ `bad_media`، نه ۵۰۰ (CodeQL: type confusion)؛ پرسشِ تکراری بی‌اثر
  for (const body of [{ a: 1 }, [1, 2, 3], 'x'.repeat(40)]) {
    const j = await h.put(url(o.slug, id, 'gate-photo'), body, { headers: who.headers });
    //  شیء و آرایه به درِ رسانه می‌رسند ⇒ `bad_media`؛ رشتهٔ تنها را خودِ parser ِ JSON ِ برنامه (strict) رد می‌کند ⇒ `bad_json`
    assert.deepEqual([j.status, j.body.error.code], [400, typeof body === 'string' ? 'bad_json' : 'bad_media'], JSON.stringify(j.body));
  }
  assert.equal((await h.get(`${url(o.slug, id, 'gate-photo')}?kind=a&kind=b`, { headers: who.headers })).status, 200);

  //  جایگزینی: هنوز دو ردیف (یکی از هر نوع) و بایتِ تازه
  const again = await put(o.slug, who, id, 'gate-photo', JPEG(500, 9));
  assert.deepEqual([again.status, again.body.replaced], [200, true]);
  assert.equal(await rows(id), 2);
  const got = await fetchBytes(url(o.slug, id, 'gate-photo'), who.headers);
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/jpeg');
  assert.equal(got.headers.get('cache-control'), 'private, no-store');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.ok(got.buffer.equals(clean(JPEG(500, 9))));
  //  EXIF ِ GPS و توضیحِ عکس (مکانِ گرفتن) پیش از ذخیره رفتند — همان پاک‌سازیِ نشانیِ سفارش
  assert.ok(!got.buffer.includes(Buffer.from('shot at', 'latin1')) && !got.buffer.includes(Buffer.from('Exif\0\0', 'latin1')));
  const list = await h.get(`/api/store/${o.slug}/account/addresses`, { headers: who.headers });
  assert.deepEqual(list.body.addresses[0].media, { gatePhoto: true, voiceNote: true });

  //  حسابِ دیگر: نه می‌خواند، نه می‌نویسد، نه پاک می‌کند
  const other = await shopper(o.slug, '0791230003');
  assert.equal((await fetchBytes(url(o.slug, id, 'gate-photo'), other.headers)).status, 404);
  const w = await put(o.slug, other, id, 'gate-photo', JPEG(100, 1));
  assert.equal(w.status, 404);
  assert.equal(w.body.error.code, 'address_not_found');
  assert.equal((await h.del(url(o.slug, id, 'voice-note'), { headers: other.headers })).body.deleted, false);
  assert.equal(await rows(id), 2);
  //  بی نشست ⇒ ۴۰۱
  assert.equal((await h.raw('PUT', url(o.slug, id, 'gate-photo'), JPEG())).status, 401);

  //  پاک کردنِ صدا، سپس خودِ نشانی ⇒ آبشاری
  assert.equal((await h.del(url(o.slug, id, 'voice-note'), { headers: who.headers })).body.deleted, true);
  assert.equal(await rows(id), 1);
  assert.equal((await h.del(`/api/store/${o.slug}/account/addresses/${id}`, { headers: who.headers })).status, 200);
  assert.equal(await rows(id), 0);
});

test('سفارش با نشانیِ نام‌دار ⇒ صاحب عکس و صدا را می‌بیند؛ نشانیِ دیگری، شاگرد و دکانِ دیگر نه', async () => {
  const o = await liveStore('سفارش');
  const who = await shopper(o.slug, '0791230004');
  const id = await addAddress(o.slug, who);
  assert.equal((await put(o.slug, who, id, 'gate-photo', JPEG(300, 5))).status, 201);
  assert.equal((await put(o.slug, who, id, 'voice-note', OGG(200))).status, 201);

  const orderId = await checkout(o, who, id);
  const seen = await h.get(`/api/shop/orders/${orderId}/address-media`, { token: o.token });
  assert.equal(seen.status, 200, JSON.stringify(seen.body));
  assert.equal(seen.body.enabled, true);
  assert.equal(seen.body.gatePhoto.mime, 'image/jpeg');
  assert.equal(seen.body.voiceNote.mime, 'audio/ogg');
  const photo = await h.download(`/api/shop/orders/${orderId}/address-media/gate-photo`, { token: o.token });
  assert.equal(photo.status, 200);
  assert.ok(photo.buffer.equals(clean(JPEG(300, 5))));
  assert.equal(photo.headers.get('cache-control'), 'private, no-store');
  const voice = await h.download(`/api/shop/orders/${orderId}/address-media/voice-note`, { token: o.token });
  assert.ok(voice.buffer.equals(OGG(200)));
  const logged = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='order.address_media.view' AND target_id=$2`,
    [o.shopId, orderId]);
  assert.equal(logged.n, 2);

  //  نشانیِ حسابِ دیگر در بدنه ⇒ هیچ پیوندی
  const other = await shopper(o.slug, '0791230005');
  const otherOrder = await checkout(o, other, id);
  assert.deepEqual((await h.get(`/api/shop/orders/${otherOrder}/address-media`, { token: o.token })).body,
    { enabled: true, gatePhoto: null, voiceNote: null });
  assert.equal((await h.download(`/api/shop/orders/${otherOrder}/address-media/gate-photo`, { token: o.token })).status, 404);

  //  شاگرد (بی `orders.contact`) ۴۰۳؛ دکانِ دیگر ۴۰۴
  const staff = await h.newUser('شاگرد', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), o.shopId, staff.user.id, now()]
  );
  assert.equal((await h.get(`/api/shop/orders/${orderId}/address-media`, { token: staff.accessToken })).status, 403);
  assert.equal((await h.download(`/api/shop/orders/${orderId}/address-media/gate-photo`, { token: staff.accessToken })).status, 403);
  const b = await liveStore('دکانِ دیگر');
  assert.equal((await h.get(`/api/shop/orders/${orderId}/address-media`, { token: b.token })).status, 404);
  assert.equal((await h.download(`/api/shop/orders/${orderId}/address-media/gate-photo`, { token: b.token })).status, 404);

  //  مشتری عکس را پاک کرد ⇒ دکان هم دیگر نمی‌بیند (نسخه‌ای نزدِ دکان نیست)
  assert.equal((await h.del(url(o.slug, id, 'gate-photo'), { headers: who.headers })).body.deleted, true);
  assert.equal((await h.download(`/api/shop/orders/${orderId}/address-media/gate-photo`, { token: o.token })).status, 404);
  assert.equal((await h.get(`/api/shop/orders/${orderId}/address-media`, { token: o.token })).body.gatePhoto, null);
});

async function courierOf(o, name) {
  const inv = await h.post('/api/shop/invite', { role: 'courier' }, { token: o.token });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const uid = `am-courier-${uniq()}`;
  const r = await h.post('/api/auth/staff-login', { code: inv.body.code, name, device: { uid, name: 'گوشیِ پیک', platform: 'android' } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.accessToken;
  return { token, uid, id: (await h.get('/api/courier/me', { token })).body.courier.id };
}

test('پیکِ همان مأموریت عکس و صدا را تا مأموریت زنده است می‌بیند؛ پیکِ دیگر و پس از تحویل نه', async () => {
  const o = await liveStore('پیک');
  await setFlag(o.shopId, 'shop.delivery', true);
  const who = await shopper(o.slug, '0791230007');
  const id = await addAddress(o.slug, who);
  assert.equal((await put(o.slug, who, id, 'gate-photo', JPEG(250, 4))).status, 201);
  const orderId = await checkout(o, who, id);
  for (const to of ['accepted', 'picking', 'packed']) {
    const t = await h.post(`/api/shop/orders/${orderId}/transition`, { to }, { token: o.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
  const courier = await courierOf(o, 'پیکِ یک');
  const other = await courierOf(o, 'پیکِ دو');
  const asg = await h.post(`/api/shop/delivery/orders/${orderId}/assign`, { mode: 'shop_courier', courierId: courier.id, overrideCashCap: true },
    { token: o.token });
  assert.equal(asg.status, 201, JSON.stringify(asg.body));
  const aid = asg.body.assignment.id;
  const base = `/api/courier/missions/${aid}/address-media`;

  //  پیش از «قبول» ⇒ هنوز نه (همان قاعدهٔ «تماس با مشتری»)
  assert.equal((await h.download(`${base}/gate-photo`, { token: courier.token })).status, 404);
  const sent = await h.post('/api/courier/events', { deviceId: courier.uid, events: [
    { eventId: `ev-${uniq()}`, assignmentId: aid, type: 'assigned', deviceTime: Date.now(), deviceSeq: 1 },
  ] }, { token: courier.token });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  const view = await h.get(base, { token: courier.token });
  assert.equal(view.status, 200, JSON.stringify(view.body));
  assert.equal(view.body.gatePhoto.mime, 'image/jpeg');
  assert.equal(view.body.voiceNote, null);
  const bytes = await h.download(`${base}/gate-photo`, { token: courier.token });
  assert.equal(bytes.status, 200);
  assert.ok(bytes.buffer.equals(clean(JPEG(250, 4))));
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='delivery.courier.address_media'`, [o.shopId])).n, 1);

  //  پیکِ دیگرِ همان دکان ⇒ «پیدا نشد»؛ صاحب با توکنِ خودش از درِ پیک ⇒ رد
  assert.equal((await h.get(base, { token: other.token })).status, 404);
  assert.equal((await h.download(`${base}/gate-photo`, { token: other.token })).status, 404);
  assert.ok([403, 404].includes((await h.get(base, { token: o.token })).status));

  //  پس از «تحویل شد» ⇒ دیگر نه
  const total = Number((await one('SELECT total_minor FROM online_orders WHERE id=$1', [orderId])).total_minor);
  const done = await h.post('/api/courier/events', { deviceId: courier.uid, events: [
    { eventId: `ev-${uniq()}`, assignmentId: aid, type: 'picked_up', deviceTime: Date.now(), deviceSeq: 2 },
    { eventId: `ev-${uniq()}`, assignmentId: aid, type: 'delivered', deviceTime: Date.now(), deviceSeq: 3, cashCollectedMinor: total, code: asg.body.code },
  ] }, { token: courier.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.deepEqual(done.body.results.map((x) => x.status), ['applied', 'applied'], JSON.stringify(done.body));
  assert.equal((await h.download(`${base}/gate-photo`, { token: courier.token })).status, 404);
});

test('rollback ِ ۸۳۶ دو جدول را برمی‌دارد و نشانی‌ها می‌مانند', async () => {
  const o = await liveStore('برگشت');
  const who = await shopper(o.slug, '0791230006');
  const id = await addAddress(o.slug, who);
  assert.equal((await put(o.slug, who, id, 'gate-photo', JPEG())).status, 201);
  const dir = path.join(__dirname, '..', 'migrations');
  const exists = async (t) => (await one('SELECT to_regclass($1) AS r', [t])).r !== null;
  const mark = await one(`SELECT * FROM schema_migrations WHERE version='836_store_address_media.sql'`);
  assert.ok(mark, 'مهاجرت نشسته است');
  await query(fs.readFileSync(path.join(dir, 'rollback', '836_store_address_media.down.sql'), 'utf8'));
  assert.equal(await exists('store_address_media'), false);
  assert.equal(await exists('online_order_address_refs'), false);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM store_customer_addresses WHERE id=$1', [id])).n, 1);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM schema_migrations WHERE version='836_store_address_media.sql'`)).n, 0);
  //  دوباره برای آزمون‌های پس از این پرونده
  await query(fs.readFileSync(path.join(dir, '836_store_address_media.sql'), 'utf8'));
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await exists('store_address_media'), true);
});
