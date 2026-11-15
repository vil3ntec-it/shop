'use strict';
/**
 * تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۴ («همهٔ این‌ها رو فعال‌شده بذار»؛ `docs/FLAGS-ON-fa.md` §۴۸): همهٔ قابلیت‌های سفارش برای هر دکان
 * پیش‌فرض روشن‌اند — `shop.orders`، `shop.storefront`، `shop.order_notify`، `shop.device_push` (`lib/flag-defaults.js`).
 *
 * آن‌چه قفل می‌شود:
 *   ۱) دکانِ تازه بی هیچ ردیفِ پرچم: بخشِ «سفارش‌ها» روشن، «چرا سفارش نمی‌رسد؟» فقط کارِ خودِ دکان (ساختنِ ویترین) را
 *      می‌گوید — هیچ دروازهٔ «از پشتیبانی بخواهید» (`orders_off`، `storefront_off`) و هیچ هشدارِ `alerts_off`.
 *   ۲) ویترینِ ساخته‌وروشنِ خودِ دکان بی ردیفِ پرچم برای مشتری باز است.
 *   ۳) اعلانِ سفارش: `/pending` باز و سفارشِ مشتری ردیفِ صفِ اعلان می‌سازد.
 *   ۴) پوشِ دستگاه: ثبتِ توکن بی ردیف؛ بی حسابِ Firebase ِ سرور هیچ پوشی نمی‌رود و هیچ خطایی بیرون نمی‌آید.
 *   ۵) `false` ِ صریحِ دکان یا `all` هر کدام را خاموش می‌کند (همان کدهای پیشین)، `true` ِ دکان بر `false` ِ `all` جلو است
 *      و «غیرفعال کردنِ سرویس» ِ دکان هنوز می‌بُرد؛ `shop.stock_reserve` (بیرون از فهرست) همان پیش‌فرضِ خاموش.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.CATALOG_REFRESH_THROTTLE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const push = require('../src/lib/push');
const N = require('../src/lib/order-notify');
const orders = require('../src/lib/orders');
const { resetAll } = require('../src/middleware/ratelimit');

const KEYS = ['shop.orders', 'shop.storefront', 'shop.order_notify', 'shop.device_push'];
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;
let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const tok = (p) => `${p}${'x'.repeat(40)}:APA91b${crypto.randomBytes(12).toString('hex')}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'defaults-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'defaults-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const flag = (scope, key, value) => h.put('/api/admin/flags', { scope: String(scope), key, value, reason: 'آزمونِ پیش‌فرض‌ها' }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const unflag = (scope, key) => h.api('DELETE', '/api/admin/flags', { token: admin, body: { scope: String(scope), key, reason: 'برگشت به پیش‌فرض' } })
  .then((r) => assert.ok(r.status < 300, JSON.stringify(r.body)));
const codes = (list) => list.map((b) => b.code);
const config = async (s) => (await h.get('/api/shop/orders/config', { token: s.token })).body;

async function newShop(label) {
  const u = await h.newUser(`صاحب ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}

const product = (id, name, price) => ({
  op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: id, type: 'insert',
  fields: { name, unit: 'کیلو', salePrice: price, category: 'خوراکی', active: true },
});

/** ویترینِ ساخته و روشن با یک کالای منتشرشده — همان کارِ خودِ دکان در برنامه؛ هیچ پرچمی نوشته نمی‌شود */
async function storefront(s) {
  const slug = `dfo-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, body: { device_id: device, schema_version: 3, ops: [product('rice', 'برنج', 120)] }, headers: { 'X-App': 'shop', 'X-Device': device },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return slug;
}

let phoneSeq = 0;
async function customerOrder(slug) {
  const phone = `07955${String(++phoneSeq).padStart(5, '0')}`;
  const v = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: v.body.devCode });
  const ck = await h.post(`/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  return ck;
}

test('دکانِ تازه بی ردیفِ پرچم: سفارش روشن، فقط «ساختنِ ویترین» ِ خودِ دکان؛ هیچ «از پشتیبانی بخواهید»', async () => {
  for (const k of KEYS) await query('DELETE FROM live_config WHERE key=$1', [k]);
  const s = await newShop('پیش‌فرض');
  const cfg = await config(s);
  assert.equal(cfg.enabled, true, JSON.stringify(cfg));
  assert.deepEqual(codes(cfg.arrival.blockers), ['storefront_missing']);
  assert.deepEqual(cfg.arrival.blockers.map((b) => b.fix), ['storefront']);
  assert.deepEqual(cfg.arrival.warnings, [], 'اعلانِ سفارش روشن ⇒ هیچ هشدارِ alerts_off');
  for (const b of cfg.arrival.blockers) assert.notEqual(b.fix, 'support', b.code);

  const slug = await storefront(s);
  const after = await config(s);
  assert.deepEqual(after.arrival, { ok: true, blockers: [], warnings: [] });
  assert.equal((await h.get(`/api/store/${slug}`)).status, 200, 'ویترینِ ساخته‌وروشن بی ردیفِ پرچم باز است');

  //  اعلانِ سفارش: صفِ پایدار و `/pending` بی ردیف
  assert.equal(await N.flagOf(s.shopId), true);
  assert.equal((await h.get('/api/shop/order-notify/pending', { token: s.token })).status, 200);
  const ck = await customerOrder(slug);
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1', [s.shopId])).id;
  assert.ok(await one("SELECT id FROM order_notify_outbox WHERE order_id=$1 AND event='order_created'", [id]), 'سفارشِ مشتری ⇒ ردیفِ اعلان');
});

test('پوشِ دستگاه پیش‌فرض روشن؛ بی حسابِ Firebase ِ سرور توکن ثبت می‌شود ولی هیچ پوشی نمی‌رود و خطایی بیرون نمی‌آید', async () => {
  for (const k of KEYS) await query('DELETE FROM live_config WHERE key=$1', [k]);
  delete process.env.FCM_SERVICE_ACCOUNT_FILE;
  delete process.env.FCM_SERVICE_ACCOUNT;
  push.useFcmOrigin(null);
  await push.save({ enabled: false });
  const s = await newShop('پوش');
  const cfg = await h.get('/api/shop/push/config', { token: s.token });
  assert.equal(cfg.status, 200, JSON.stringify(cfg.body));
  assert.equal(cfg.body.enabled, true);
  assert.equal(cfg.body.server.ready, false, 'سرورِ بی کلید «آماده» نیست');
  assert.equal(cfg.body.orderAlerts, true);
  const t1 = tok('ok');
  const reg = await h.post('/api/shop/push/devices', { token: t1 }, { token: s.token });
  assert.equal(reg.status, 201, JSON.stringify(reg.body));
  assert.equal((await one('SELECT status FROM push_tokens WHERE token=$1', [t1])).status, 'active');
  const sent = await push.sendTo({ shopId: s.shopId, app: 'shop' }, { title: 't', body: 'b' });
  assert.equal(sent.sent, 0);
  assert.ok(sent.skipped, JSON.stringify(sent));
  await push.save({ enabled: true });
  const sent2 = await push.sendTo({ shopId: s.shopId, app: 'shop' }, { title: 't', body: 'b' });
  assert.equal(sent2.sent, 0, 'روشن ولی بی حسابِ سرویس ⇒ هیچ');
  assert.ok(sent2.skipped, JSON.stringify(sent2));
  await push.save({ enabled: false });
});

test('false ِ صریحِ دکان هر کلید را با همان کدِ پیشین خاموش می‌کند؛ حذفِ ردیف ⇒ دوباره روشن', async () => {
  const s = await newShop('خاموشِ صریح');
  const other = await newShop('همسایه');
  for (const k of KEYS) await flag(s.shopId, k, false);

  const cfg = await config(s);
  assert.equal(cfg.enabled, false);
  assert.deepEqual(codes(cfg.arrival.blockers), ['orders_off', 'storefront_off', 'storefront_missing']);
  assert.deepEqual(codes(cfg.arrival.warnings), ['alerts_off']);
  const pend = await h.get('/api/shop/order-notify/pending', { token: s.token });
  assert.equal(pend.status, 403);
  assert.equal(pend.body.error.code, 'order_notify_disabled');
  const pc = await h.get('/api/shop/push/config', { token: s.token });
  assert.equal(pc.body.enabled, false);
  assert.equal(pc.body.orderAlerts, false);
  const reg = await h.post('/api/shop/push/devices', { token: tok('ok') }, { token: s.token });
  assert.equal(reg.status, 403);
  assert.equal(reg.body.error.code, 'device_push_disabled');
  const write = await h.post('/api/shop/orders', {
    source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'احمد' },
    lines: [{ name: 'برنج', qtyMilli: 1000, unitPriceMinor: 12000 }],
  }, { token: s.token, headers: { 'Idempotency-Key': `off-${uniq()}-key` } });
  assert.equal(write.status, 403);

  //  دکانِ دیگر دست نمی‌خورد
  const o = await config(other);
  assert.equal(o.enabled, true);
  assert.deepEqual(codes(o.arrival.blockers), ['storefront_missing']);

  //  حذفِ ردیف ⇒ برگشت به پیش‌فرضِ روشن
  for (const k of KEYS) await unflag(s.shopId, k);
  const back = await config(s);
  assert.equal(back.enabled, true);
  assert.deepEqual(codes(back.arrival.blockers), ['storefront_missing']);
  assert.deepEqual(back.arrival.warnings, []);
});

test('false ِ all همه را خاموش می‌کند و true ِ دکان بر آن جلو است؛ ویترینِ ساخته با false خاموش ⇒ ۴۰۴', async () => {
  const a = await newShop('همه‌خاموش');
  const b = await newShop('استثنا');
  const slug = await storefront(a);
  try {
    for (const k of KEYS) await flag('all', k, false);
    await flag(b.shopId, 'shop.orders', true);
    await flag(b.shopId, 'shop.order_notify', true);
    assert.equal((await config(a)).enabled, false);
    assert.equal((await h.get(`/api/store/${slug}`)).status, 404, 'ویترینِ خاموش برای مشتری بسته');
    assert.equal(await N.flagOf(a.shopId), false);
    assert.equal((await config(b)).enabled, true, 'true ِ دکان بر false ِ all');
    assert.equal(await N.flagOf(b.shopId), true);
  } finally {
    for (const k of KEYS) await unflag('all', k);
    await unflag(b.shopId, 'shop.orders');
    await unflag(b.shopId, 'shop.order_notify');
  }
  assert.equal((await h.get(`/api/store/${slug}`)).status, 200);
});

test('درونِ تراکنش هم همان؛ `shop.stock_reserve` (بیرون از فهرست) پیش‌فرض خاموش می‌ماند؛ نگه‌داشتِ سرویس می‌بُرد', async () => {
  const s = await newShop('تراکنش');
  const run = (sql, p) => query(sql, p);
  assert.equal(await N.flagIn(run, s.shopId), true);
  assert.equal(await N.flagIn(run, s.shopId, 'shop.stock_reserve'), false);
  await flag(s.shopId, 'shop.stock_reserve', true);
  assert.equal(await N.flagIn(run, s.shopId, 'shop.stock_reserve'), true);
  await unflag(s.shopId, 'shop.stock_reserve');
  await query(
    "INSERT INTO shop_holds (id, shop_id, kind, service, reason, placed_by, placed_at) VALUES ($1,$2,'service_disabled','shop.orders','آزمون','admin',1)",
    [newId('hold'), s.shopId],
  );
  assert.equal(await orders.flagOf(s.shopId), false, '«غیرفعال کردنِ سرویس» هنوز می‌بُرد');
  assert.equal((await config(s)).enabled, false);
});
