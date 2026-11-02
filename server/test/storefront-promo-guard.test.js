'use strict';
/**
 * MKT-02 (تخفیفِ اولین خرید، یک بار برای هر شماره و هر دستگاه) و RSK-09 (سدِ سوءاستفاده از تخفیفِ ویترین)،
 * پشتِ پرچمِ خاموشِ `shop.storefront.promo_guard` (همراهِ `shop.storefront.promos`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) پرچمِ خاموش ⇒ ثبت، پیش‌نمایش و فهرستِ عمومی همان پیشین؛ هیچ ردیفی در دفتر؛ درِ صاحب ۴۰۳.
 *   ۲) اولین خرید فقط یک بار برای هر شماره؛ همان دستگاه با شمارهٔ تازه نه؛ پیش‌نمایش = ثبت.
 *   ۳) دو ثبتِ هم‌زمان (همان شماره یا همان دستگاه) ⇒ دقیقاً یکی؛ قیدِ یکتای پایگاه خودش هم می‌بندد.
 *   ۴) حدس زدنِ کد ⇒ ۴۲۹ `promo_attempts` با Retry-After، حتی برای کدِ درست؛ ثبتِ بی کد آزاد.
 *   ۵) سقفِ تخفیفِ هر سفارش، سقفِ هر کد برای هر شماره/دستگاه، و قاعدهٔ «انحصاری» روی هم نمی‌نشیند.
 *   ۶) دفترِ مصرف و فهرستِ «مشکوک» برای صاحب؛ شاگرد ۴۰۳؛ تنظیمِ نادرست ۴۰۰.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const guard = require('../src/lib/storefront-promo-guard');

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
test.beforeEach(() => { resetAll(); guard.resetAttempts(); });

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-guard' },
  body: { device_id: 'dev-guard', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveShop(label, { guardOn = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `g-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.promo_uses', 'shop.storefront.promos']) await setFlag(s.shopId, k, true);
  if (guardOn) await setFlag(s.shopId, guard.FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی', purchasePrice: 100 }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ])).status, 200);
  for (const id of ['rice', 'soap']) assert.equal((await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: s.token })).status, 200);
  return s;
}

async function rule(s, id, data) {
  const r = await h.put(`/api/shop/promos/rules/${id}`, { rule: { name: id, stack: 'best', active: true, ...data } }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function settings(s, body) {
  const r = await h.put('/api/shop/storefront/promo-guard', body, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body;
}
const FIRST10 = { firstOrder: { enabled: true, kind: 'percent', bp: 1000 } };

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

const LINES = [{ productId: 'rice', qtyMilli: 2000 }, { productId: 'soap', qtyMilli: 1000 }];   //  ۲۴۰ + ۴۰ = ۲۸۰ افغانی
async function checkout(s, { phone = '0791112233', key = `ck-${uniq()}-key`, ...extra } = {}) {
  return h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'delivery', customer: { name: 'مشتری', phone }, address: { landmark: 'پشتِ مسجد' }, lines: LINES, ...extra,
  }, { headers: { 'Idempotency-Key': key, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
}
async function quote(s, body = {}, phone = null) {
  const headers = phone ? { 'X-Store-Ticket': await ticketFor(s.slug, phone) } : {};
  return h.post(`/api/store/${s.slug}/promos/quote`, { lines: LINES, ...body }, { headers });
}
const discountOf = async (s, r) => {
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return Number((await one('SELECT discount_minor FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).discount_minor);
};
const code = (r) => r.body?.error?.code;
const DEV_A = 'device-aaaa-0001';
const DEV_B = 'device-bbbb-0002';

/* ══════════════════════════════════════════════════════════════════ */

test('MKT-02/RSK-09 خاموش: ثبت، پیش‌نمایش و فهرستِ عمومی همان پیشین؛ هیچ ردیفی در دفتر؛ درِ صاحب ۴۰۳', async () => {
  const s = await liveShop('سد-خاموش', { guardOn: false });
  //  تنظیمِ «اولین خرید» حتی اگر در پایگاه باشد، بی پرچم اثری ندارد
  await query('INSERT INTO storefront_promo_guard (shop_id, data, updated_at) VALUES ($1,$2,$3)', [s.shopId, JSON.stringify(FIRST10), now()]);
  const list = await h.get(`/api/store/${s.slug}/promos`);
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.deepEqual(Object.keys(list.body).sort(), ['codes', 'promos', 'serverTime'], 'بی `guard` و `firstOrder`');
  const q = await quote(s, { deviceId: DEV_A }, '0791110001');
  assert.deepEqual(Object.keys(q.body).sort(), ['applied', 'code', 'discountMinor', 'lines', 'subtotalMinor', 'totalMinor', 'unavailable']);
  assert.equal(q.body.discountMinor, 0);
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110001', deviceId: DEV_A })), 0, 'اولین خرید بی پرچم نه');
  assert.equal(Number((await one('SELECT count(*)::int AS n FROM storefront_promo_claims WHERE shop_id=$1', [s.shopId])).n), 0);
  for (const r of [await h.get('/api/shop/storefront/promo-guard', { token: s.token }), await h.put('/api/shop/storefront/promo-guard', FIRST10, { token: s.token })]) {
    assert.equal(r.status, 403, JSON.stringify(r.body));
    assert.equal(code(r), 'promo_guard_disabled');
  }
  //  بی هیچ برخوردِ حدس: کدِ نادرستِ پیاپی فقط همان ۴۰۹/۲۰۰ ِ پیشین (سقفِ IP ِ مسیر جداست)
  for (let i = 0; i < 10; i++) assert.equal((await quote(s, { promoCode: `WRONG${i}` })).status, 200);
});

test('MKT-02: اولین خرید فقط یک بار برای هر شماره؛ همان دستگاه با شمارهٔ تازه نه؛ پیش‌نمایش = ثبت', async () => {
  const s = await liveShop('اولین-خرید');
  await settings(s, FIRST10);
  const pub = await h.get(`/api/store/${s.slug}/promos`);
  assert.deepEqual([pub.body.guard, pub.body.firstOrder], [true, { kind: 'percent', bp: 1000, maxMinor: 0, minSubtotalMinor: 0 }]);

  let q = await quote(s, { deviceId: DEV_A });
  assert.deepEqual([q.body.firstOrder, q.body.discountMinor], ['verify', 0], 'بی شمارهٔ تأییدشده هنوز نامعلوم');
  q = await quote(s, { deviceId: DEV_A }, '0791110002');
  assert.deepEqual([q.body.firstOrder, q.body.firstOrderMinor, q.body.discountMinor, q.body.totalMinor], ['applied', 2800, 2800, 25200]);

  const fee = Number((await one('SELECT delivery_fee_minor FROM storefronts WHERE shop_id=$1', [s.shopId])).delivery_fee_minor);
  const first = await checkout(s, { phone: '0791110002', deviceId: DEV_A, expectedTotalMinor: 28000 - 2800 + fee });
  assert.equal(await discountOf(s, first), 2800, 'اولین خرید: ۱۰٪');
  const again = await checkout(s, { phone: '0791110002', deviceId: DEV_B });
  assert.equal(await discountOf(s, again), 0, 'خریدِ دوم با همان شماره نه، حتی از دستگاهِ دیگر');
  q = await quote(s, {}, '0791110002');
  assert.deepEqual([q.body.firstOrder, q.body.discountMinor], ['used', 0]);

  //  همان دستگاه با شمارهٔ تازه
  q = await quote(s, { deviceId: DEV_A }, '0791110003');
  assert.equal(q.body.firstOrder, 'used');
  const sameDevice = await checkout(s, { phone: '0791110003', deviceId: DEV_A, expectedTotalMinor: 28000 - 2800 + fee });
  assert.equal(sameDevice.status, 409, 'جمعِ با تخفیفِ دیده‌شده دیگر درست نیست ⇒ بی‌صدا ثبت نمی‌شود');
  assert.equal(code(sameDevice), 'price_changed');
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110003', deviceId: DEV_A })), 0, 'همان دستگاه، شمارهٔ تازه ⇒ نه');
  //  شمارهٔ دیگری که در این دکان سفارش داشته (همین سفارشِ بی تخفیف) دیگر «اولین» نیست، حتی از دستگاهِ تازه
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110003', deviceId: 'device-cccc-0003' })), 0);
  //  شماره و دستگاهِ تازه ⇒ می‌گیرد
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110004', deviceId: 'device-dddd-0004' })), 2800);

  const claims = await many(`SELECT phone_key, device_key, discount_minor FROM storefront_promo_claims WHERE shop_id=$1 AND kind='first_order' ORDER BY created_at`, [s.shopId]);
  assert.deepEqual(claims.map((c) => [c.phone_key, Number(c.discount_minor)]), [['+93791110002', 2800], ['+93791110004', 2800]]);
  assert.ok(claims.every((c) => /^[0-9a-f]{32}$/.test(c.device_key)), 'فقط چکیدهٔ دستگاه، نه خودِ شناسه');
  assert.ok(!JSON.stringify(await many('SELECT * FROM storefront_promo_claims WHERE shop_id=$1', [s.shopId])).includes(DEV_A));

  //  تکرارِ همان ثبت: همان سفارش، بی ردیفِ تازه
  const replay = await checkout(s, { phone: '0791110004', deviceId: 'device-dddd-0004', key: 'ck-replay-0001' });
  const replay2 = await checkout(s, { phone: '0791110004', deviceId: 'device-dddd-0004', key: 'ck-replay-0001' });
  assert.equal(replay2.body.order.orderNo, replay.body.order.orderNo);
});

test('MKT-02: دو ثبتِ هم‌زمان (همان شماره، یا همان دستگاه با دو شماره) ⇒ دقیقاً یکی تخفیف می‌گیرد؛ قیدِ یکتا خودش هم می‌بندد', async () => {
  const s = await liveShop('هم‌زمان');
  await settings(s, FIRST10);
  await ticketFor(s.slug, '0791110005');
  await ticketFor(s.slug, '0791110006');
  await ticketFor(s.slug, '0791110007');
  const samePhone = await Promise.all([1, 2, 3].map((i) => checkout(s, { phone: '0791110005', deviceId: `device-pp-000${i}` })));
  const d1 = await Promise.all(samePhone.map((r) => discountOf(s, r)));
  assert.deepEqual(d1.sort((a, b) => a - b), [0, 0, 2800], 'همان شماره: یک بار');
  const sameDevice = await Promise.all(['0791110006', '0791110007'].map((phone) => checkout(s, { phone, deviceId: 'device-shared-01' })));
  const d2 = await Promise.all(sameDevice.map((r) => discountOf(s, r)));
  assert.deepEqual(d2.sort((a, b) => a - b), [0, 2800], 'همان دستگاه: یک بار');

  //  قیدِ پایگاه، بی قفلِ برنامه
  const row = await one(`SELECT * FROM storefront_promo_claims WHERE shop_id=$1 AND kind='first_order' AND device_key <> '' LIMIT 1`, [s.shopId]);
  const ins = (phone, device) => query(
    `INSERT INTO storefront_promo_claims (shop_id, client_key, kind, promo_id, code, phone_key, device_key, discount_minor, created_at)
     VALUES ($1,$2,'first_order','','',$3,$4,100,$5)`, [s.shopId, `raw-${uniq()}`, phone, device, now()]);
  await assert.rejects(ins(row.phone_key, ''), /unique|duplicate/i, 'همان شماره');
  await assert.rejects(ins('+93799999999', row.device_key), /unique|duplicate/i, 'همان دستگاه');
  await ins('+93799999998', '');   //  بی دستگاه، شمارهٔ تازه: آزاد
});

test('RSK-09: حدس زدنِ کد ⇒ ۴۲۹ promo_attempts با Retry-After (حتی کدِ درست)؛ ثبتِ بی کد آزاد', async () => {
  const s = await liveShop('حدسِ-کد');
  await rule(s, 'real', { kind: 'percent', bp: 1000, channels: ['online'], code: 'REAL10' });
  assert.equal((await quote(s, { promoCode: 'REAL10' })).body.code.status, 'applied');
  for (let i = 0; i < guard.ATTEMPTS.perActor; i++) {
    const r = await quote(s, { promoCode: `GUESS${i}` });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.code.status, 'unknown');
  }
  const blocked = await quote(s, { promoCode: 'REAL10' });
  assert.equal(blocked.status, 429, JSON.stringify(blocked.body));
  assert.equal(code(blocked), 'promo_attempts');
  assert.ok(Number(blocked.headers.get('retry-after')) > 0, 'Retry-After');
  const ck = await checkout(s, { phone: '0791110008', promoCode: 'REAL10' });
  assert.equal(ck.status, 429, JSON.stringify(ck.body));
  assert.equal(code(ck), 'promo_attempts');
  assert.ok(Number(ck.headers.get('retry-after')) > 0);
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110008' })), 0, 'بی کد همان ثبتِ عادی');
  assert.equal((await quote(s, {})).status, 200, 'پیش‌نمایشِ بی کد آزاد');
  guard.resetAttempts();
  assert.equal((await quote(s, { promoCode: 'REAL10' })).body.code.status, 'applied', 'پس از پنجره دوباره');
});

test('RSK-09: سقفِ تخفیفِ هر سفارش؛ قاعدهٔ «انحصاری» با اولین خرید روی هم نمی‌نشیند؛ اولین خریدِ «انحصاری» بزرگ‌تر را می‌گیرد', async () => {
  const s = await liveShop('سقفِ-سفارش');
  await rule(s, 'all10', { kind: 'percent', bp: 1000, channels: ['online'] });
  await settings(s, { ...FIRST10, limits: { maxDiscountMinor: 4000 } });
  let q = await quote(s, {}, '0791110009');
  assert.deepEqual([q.body.discountMinor, q.body.firstOrderMinor], [4000, 1200], 'قاعده ۲۸۰۰ + اولین خرید ۲۵۰۰ ⇒ سقفِ ۴۰ افغانی');
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110009' })), 4000);
  assert.equal(Number((await one(`SELECT discount_minor FROM storefront_promo_claims WHERE shop_id=$1 AND kind='first_order'`, [s.shopId])).discount_minor), 1200);

  //  قاعدهٔ انحصاری ⇒ اولین خرید روی آن نمی‌نشیند و مصرف هم نمی‌شود
  const t = await liveShop('انحصاری');
  await rule(t, 'ex5', { kind: 'percent', bp: 500, channels: ['online'], stack: 'exclusive' });
  await settings(t, FIRST10);
  assert.equal(await discountOf(t, await checkout(t, { phone: '0791110010' })), 1400, 'فقط همان ۵٪ ِ انحصاری');
  assert.equal(Number((await one(`SELECT count(*)::int AS n FROM storefront_promo_claims WHERE shop_id=$1 AND kind='first_order'`, [t.shopId])).n), 0);

  //  اولین خریدِ «انحصاری» در برابرِ خودکارِ کوچک‌تر ⇒ فقط اولین خرید
  const u = await liveShop('اولین-انحصاری');
  await rule(u, 'small5', { kind: 'percent', bp: 500, channels: ['online'] });
  await settings(u, { firstOrder: { ...FIRST10.firstOrder, stack: 'exclusive' } });
  q = await quote(u, {}, '0791110011');
  assert.deepEqual([q.body.discountMinor, q.body.firstOrderMinor, q.body.applied], [2800, 2800, []]);
  assert.equal(await discountOf(u, await checkout(u, { phone: '0791110011' })), 2800);
  assert.deepEqual((await many('SELECT kind FROM storefront_promo_claims WHERE shop_id=$1', [u.shopId])).map((r) => r.kind), ['first_order']);
});

test('RSK-09: هر کد یک بار برای هر شماره و هر دستگاه ⇒ ۴۰۹ limit_reached؛ سقفِ PRM-02 همان پیشین', async () => {
  const s = await liveShop('سقفِ-کد');
  await rule(s, 'vip', { kind: 'percent', bp: 1000, channels: ['online'], code: 'VIP10' });
  await settings(s, { limits: { codePerPhone: 1, codePerDevice: 1 } });
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110012', deviceId: DEV_A, promoCode: 'VIP10' })), 2800);
  let r = await checkout(s, { phone: '0791110012', deviceId: DEV_B, promoCode: 'VIP10' });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(code(r), 'promo_code_rejected');
  assert.deepEqual(r.body.error.promo, { code: 'VIP10', status: 'limit_reached' }, 'همان شماره');
  r = await checkout(s, { phone: '0791110013', deviceId: DEV_A, promoCode: 'VIP10' });
  assert.equal(r.status, 409, 'همان دستگاه، شمارهٔ دیگر');
  assert.equal(r.body.error.promo.status, 'limit_reached');
  const q = await quote(s, { promoCode: 'VIP10', deviceId: DEV_A });
  assert.deepEqual([q.body.code, q.body.discountMinor], [{ code: 'VIP10', status: 'limit_reached' }, 0], 'پیش‌نمایش همان را می‌گوید');
  assert.equal(await discountOf(s, await checkout(s, { phone: '0791110013', deviceId: DEV_B, promoCode: 'VIP10' })), 2800, 'شماره و دستگاهِ تازه');
  const uses = await many(`SELECT sale_id FROM shop_promo_redemptions WHERE shop_id=$1 AND promo_id='vip' AND released_at IS NULL`, [s.shopId]);
  assert.equal(uses.length, 2, 'دفترِ PRM-02 فقط دو مصرفِ نشسته');
});

test('RSK-09: دفترِ مصرف و فهرستِ «مشکوک» برای صاحب؛ شاگرد ۴۰۳؛ تنظیمِ نادرست ۴۰۰', async () => {
  const s = await liveShop('مشکوک');
  await settings(s, FIRST10);
  for (const phone of ['0791110014', '0791110015', '0791110016']) await checkout(s, { phone, deviceId: 'device-farm-0001' });
  for (const dev of ['device-x-00000001', 'device-x-00000002']) await checkout(s, { phone: '0791110017', deviceId: dev });
  await checkout(s, { phone: '0791110017', deviceId: 'device-x-00000003' });
  const v = await h.get('/api/shop/storefront/promo-guard', { token: s.token });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  assert.equal(v.headers.get('cache-control'), 'no-store');
  assert.deepEqual(v.body.settings.firstOrder, { enabled: true, kind: 'percent', bp: 1000, amountMinor: 0, maxMinor: 0, minSubtotalMinor: 0, stack: 'combine' });
  assert.equal(v.body.suspicious.devices.length, 1);
  assert.deepEqual(v.body.suspicious.devices[0].phones, ['+93791110014', '+93791110015', '+93791110016']);
  assert.equal(v.body.suspicious.devices[0].count, 3);
  assert.deepEqual(v.body.suspicious.phones.map((p) => [p.phone, p.count]), [['+93791110017', 3]]);
  const firsts = v.body.recent.filter((x) => x.kind === 'first_order');
  assert.deepEqual(firsts.map((x) => x.phone).sort(), ['+93791110014', '+93791110017'], 'دفتر: اولین خریدِ هر شماره');
  assert.ok(firsts.every((x) => Number.isInteger(x.orderNo) && x.discountMinor === 2800 && /^[0-9a-f]{8}$/.test(x.device)));

  //  دکانِ دیگر چیزی از این دفتر نمی‌بیند
  const t = await liveShop('دکانِ دیگر');
  const other = await h.get('/api/shop/storefront/promo-guard', { token: t.token });
  assert.deepEqual([other.body.recent, other.body.suspicious.devices, other.body.suspicious.phones], [[], [], []]);

  for (const bad of [{ firstOrder: { enabled: true, kind: 'percent', bp: 0 } }, { firstOrder: { kind: 'free' } },
    { firstOrder: { enabled: true, kind: 'amount', amountMinor: 150 } }, { limits: { codePerPhone: -1 } }, { limits: { maxDiscountMinor: '100' } }]) {
    const r = await h.put('/api/shop/storefront/promo-guard', bad, { token: s.token });
    assert.equal(r.status, 400, JSON.stringify(bad));
    assert.equal(code(r), 'bad_promo_guard');
  }
  const staffCode = (await h.post('/api/shop/staff-code', {}, { token: s.token })).body.code;
  const staff = await h.newUser('شاگردِ سد');
  assert.ok((await h.post('/api/shop/staff/join', { code: staffCode }, { token: staff.accessToken })).status < 300);
  assert.equal((await h.get('/api/shop/storefront/promo-guard', { token: staff.accessToken })).status, 403);
  assert.equal((await h.put('/api/shop/storefront/promo-guard', FIRST10, { token: staff.accessToken })).status, 403);
});

test('MKT-02: مبلغِ ثابت با کفِ سبد و سقف — تابعِ حساب', () => {
  const f = { enabled: true, kind: 'amount', amountMinor: 5000, maxMinor: 0, minSubtotalMinor: 0, stack: 'combine' };
  assert.equal(guard.amountOf(f, 3050), 3000, 'هرگز بیش از سبد، با پلهٔ افغانی');
  assert.equal(guard.amountOf({ ...f, kind: 'percent', bp: 1500, maxMinor: 2000 }, 28000), 2000);
  const s = guard.normalize({ firstOrder: { ...f, minSubtotalMinor: 30000 } });
  assert.equal(guard.split(s, { discountMinor: 0, exclusive: false }, 28000, true, false).firstOrderMinor, 0, 'زیرِ کفِ سبد');
  assert.equal(guard.split(s, { discountMinor: 0, exclusive: false }, 30000, true, false).firstOrderMinor, 5000);
  assert.equal(guard.deviceKeyOf('short'), '', 'شناسهٔ نادرست ⇒ نامعلوم');
});
