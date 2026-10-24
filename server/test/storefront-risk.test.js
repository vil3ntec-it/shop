'use strict';
/**
 * ریسکِ ویترین — RSK-07 و PRES-10.
 *
 *   RSK-07) سقفِ سفارش به‌ازای شمارهٔ تأییدشده، روی سقفِ IP:
 *     - همان دکان: سفارشِ ششم در یک ساعت ⇒ ۴۲۹ `phone_order_limit` با
 *       Retry-After و بی ردیف؛ تکرارِ کلیدِ پیشین همچنان «replayed»؛ شمارهٔ
 *       دیگر و دکانِ دیگر آزاد.
 *     - همهٔ ویترین‌ها: چهل‌ویکمین سفارشِ ۲۴ ساعت ⇒ ۴۲۹ حتی در دکانِ تازه؛
 *       ‎+930…‎ و ‎+93…‎ یک شماره‌اند؛ Retry-After بیشترینِ دو قاعده.
 *     - هم‌زمان: ۱۵ ثبتِ هم‌زمان با کلیدهای گوناگون ⇒ دقیقاً ۱۰ سفارش (سنجش در تراکنش).
 *   PRES-10) اعلامِ حضورِ دکانِ دیگر یا دست‌کاری‌شده ⇒ رد و ردیف در audit_logs،
 *     برای هر (کاربر، دلیل) در ۱۰ دقیقه یکی؛ بی‌توکن یا بی دکان ⇒ بی ردیف؛
 *     اعلامِ سالم ⇒ بی ردیفِ رد.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '200';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const orderRate = require('../src/lib/order-rate');
const run = (t, p) => query(t, p);

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

const push = (token, ops, device = 'dev-risk') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});

/** دکانِ روشن با ویترین و یک کالای منتشرشده. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `r-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' },
    { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [{
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
  }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const r = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...o, slug };
}

async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

const cart = (phone) => ({
  fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
});
const checkout = (slug, phone, ticket, key = `rk-${uniq()}-key`) => h.post(`/api/store/${slug}/checkout`, cart(phone), {
  headers: { 'Idempotency-Key': key, 'X-Store-Ticket': ticket },
});
const count = async (shopId) => (await one('SELECT COUNT(*)::int AS n FROM online_orders WHERE shop_id=$1', [shopId])).n;

/* ── RSK-07 ─────────────────────────────────────────────────────────── */

test('RSK-07: سفارشِ یازدهمِ یک شماره در یک ساعت ⇒ ۴۲۹؛ تکرارِ کلید، شمارهٔ دیگر و دکانِ دیگر آزاد', async () => {
  assert.deepEqual([orderRate.PER_SHOP.max, orderRate.ALL_SHOPS.max], [10, 40]);
  const s = await liveStore('سقفِ شماره');
  const phone = '0791230001';
  const ticket = await ticketFor(s.slug, phone);
  let lastKey = '';
  for (let i = 0; i < orderRate.PER_SHOP.max; i++) {
    lastKey = `rk-${uniq()}-key`;
    const r = await checkout(s.slug, phone, ticket, lastKey);
    assert.equal(r.status, 201, JSON.stringify(r.body));
  }
  const over = await checkout(s.slug, phone, ticket);
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'phone_order_limit');
  assert.equal(over.body.error.scope, 'shop');
  const ra = Number(over.headers.get('retry-after'));
  assert.ok(ra > 0 && ra <= 3600, `Retry-After=${ra}`);
  assert.equal(await count(s.shopId), orderRate.PER_SHOP.max, 'ردیفِ یازدهمی ساخته نشد');

  //  ⛔ تکرارِ همان کلید (شبکه پس از ثبت قطع شد) پاسخِ replayed می‌گیرد، نه ۴۲۹
  const again = await checkout(s.slug, phone, ticket, lastKey);
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.replayed, true);

  //  شمارهٔ دیگر روی همان IP و همان دکان
  const other = '0791230002';
  assert.equal((await checkout(s.slug, other, await ticketFor(s.slug, other))).status, 201);
  //  همان شماره در دکانِ دیگر (سقفِ «همهٔ ویترین‌ها» ۴۰ است)
  const s2 = await liveStore('دکانِ دوم');
  assert.equal((await checkout(s2.slug, phone, await ticketFor(s2.slug, phone))).status, 201);

  //  بی شمارهٔ تأییدشده همان ۴۰۱ ِ پیشین
  const anon = await h.post(`/api/store/${s.slug}/checkout`, cart(phone), { headers: { 'Idempotency-Key': `rk-${uniq()}-key` } });
  assert.equal(anon.status, 401);
});

test('RSK-07: چهل‌ویکمین سفارشِ یک شماره در ۲۴ ساعت از همهٔ ویترین‌ها ⇒ ۴۲۹ حتی در دکانِ تازه', async () => {
  const a = await liveStore('روزانهٔ الف');
  const b = await liveStore('روزانهٔ ب');
  const phone = '+93795550001';
  const at = now();
  //  ۴۰ سفارشِ دو تا بیست ساعتِ پیش در دکانِ الف (سقفِ ساعتیِ دکان دیگر نمی‌گیرد)
  const max = orderRate.ALL_SHOPS.max;
  const times = Array.from({ length: max }, (_, i) => Math.round(at - (2 + (18 * i) / max) * 3600_000));
  for (let i = 0; i < max; i++) {
    const t = times[i];
    await query(
      `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, fulfillment,
                                  customer_phone, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'h','storefront','pickup',$5,$6,$6)`,
      //  نیمی با صفرِ پس از ‎+93‎ ذخیره شده — همان شماره است
      [newId('ord'), a.shopId, 900000 + i, `seed-${uniq()}`, i % 2 ? '+930795550001' : phone, t]
    );
  }
  await assert.doesNotReject(orderRate.check(run, a.shopId, phone, { at, rules: [orderRate.PER_SHOP] }));
  const err = await orderRate.check(run, b.shopId, phone, { at }).then(() => null, (e) => e);
  assert.ok(err, 'دکانِ تازه هم باید ۴۲۹ بدهد');
  assert.equal(err.status, 429);
  assert.equal(err.code, 'phone_order_limit');
  assert.equal(err.data.scope, 'all');
  //  کهنه‌ترینِ چهل که بیرون برود یک جا باز می‌شود
  assert.equal(err.retryAfter, Math.ceil((times[max - 1] + 24 * 3600_000 - at) / 1000));
  //  شمارهٔ دیگر و شمارهٔ خالی آزاد
  await assert.doesNotReject(orderRate.check(run, b.shopId, '+93795550002', { at }));
  await assert.doesNotReject(orderRate.check(run, b.shopId, '', { at }));
  //  همان شماره با صفر هم همان سقف را می‌گیرد
  assert.equal((await orderRate.check(run, b.shopId, '+930795550001', { at }).catch((e) => e)).code, 'phone_order_limit');
  //  پنجره می‌گذرد ⇒ آزاد
  await assert.doesNotReject(orderRate.check(run, b.shopId, phone, { at: at + 6 * 3600_000 }));

  //  Retry-After: هر دو قاعده پر ⇒ بیشترینِ آن دو
  const tb = Array.from({ length: orderRate.PER_SHOP.max }, (_, i) => at - (10 + i) * 60_000);
  for (let i = 0; i < tb.length; i++) {
    await query(
      `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, fulfillment,
                                  customer_phone, created_at, updated_at)
       VALUES ($1,$2,$3,$4,'h','storefront','pickup',$5,$6,$6)`,
      [newId('ord'), b.shopId, 900000 + i, `seed-${uniq()}`, phone, tb[i]]
    );
  }
  const raShop = Math.ceil((tb[tb.length - 1] + 3600_000 - at) / 1000);
  //  ۵۰ ردیف در ۲۴ ساعت: ۱۰ تازه‌ترِ ب، سپس الف به ترتیب ⇒ چهلمین = الفِ شمارهٔ ۲۹
  const raAll = Math.ceil((times[max - 1 - tb.length] + 24 * 3600_000 - at) / 1000);
  const onlyShop = await orderRate.check(run, b.shopId, phone, { at, rules: [orderRate.PER_SHOP] }).catch((e) => e);
  assert.equal(onlyShop.retryAfter, raShop);
  const both = await orderRate.check(run, b.shopId, phone, { at }).catch((e) => e);
  assert.ok(raAll > raShop);
  assert.deepEqual([both.retryAfter, both.data.scope], [raAll, 'all']);
});

test('RSK-07: ۱۵ ثبتِ هم‌زمانِ یک شماره با کلیدهای گوناگون ⇒ دقیقاً ۱۰ سفارش، بقیه ۴۲۹', async () => {
  const s = await liveStore('هم‌زمان');
  const phone = '0791230077';
  const ticket = await ticketFor(s.slug, phone);
  const res = await Promise.all(Array.from({ length: 15 }, () => checkout(s.slug, phone, ticket)));
  const codes = res.map((r) => r.status).sort();
  assert.deepEqual(codes, [...Array(10).fill(201), ...Array(5).fill(429)], JSON.stringify(codes));
  assert.equal(await count(s.shopId), orderRate.PER_SHOP.max);
});

/* ── PRES-10 ────────────────────────────────────────────────────────── */

const rejected = async (shopId) => (await query(
  `SELECT user_id, detail FROM audit_logs WHERE shop_id=$1 AND action='storefront.presence_rejected' ORDER BY created_at`,
  [shopId]
)).rows.map((r) => ({ userId: r.user_id, ...(typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail) }));

test('PRES-10: اعلامِ دکانِ دیگر یا دست‌کاری‌شده رد و در سابقه ثبت می‌شود؛ اعلامِ سالم نه', async () => {
  const s = await liveStore('حضورِ امن');
  const foreign = await liveStore('دکانِ بیگانه');
  require('../src/routes/storefront-admin').resetRejectAudit();
  const declare = (body, token = s.token) => h.post('/api/shop/storefront/presence', body, { token });
  const ev = () => `ev-${uniq()}-p`;

  //  نامِ دکانِ دیگر در بدنه ⇒ ۴۰۳ و هیچ اثری روی هیچ‌کدام
  const f = await declare({ eventId: ev(), mode: 'closed', shopId: foreign.shopId });
  assert.equal(f.status, 403, JSON.stringify(f.body));
  assert.equal(f.body.error.code, 'foreign_shop');
  //  هر کلیدِ بیگانه بس است، حتی اگر نخستین کلید شناسهٔ خودِ دکان باشد
  const f2 = await declare({ eventId: ev(), mode: 'closed', shopId: s.shopId, store_id: foreign.shopId });
  assert.equal(f2.status, 403);
  assert.equal(f2.body.error.code, 'foreign_shop');
  const events = await one('SELECT COUNT(*)::int AS n FROM storefront_presence_events WHERE shop_id = ANY($1)',
    [[s.shopId, foreign.shopId]]);
  assert.equal(events.n, 0);

  //  بدنهٔ دست‌کاری‌شده: وضعیتِ ناشناخته، زمانِ پایانِ بیرون از بازه
  assert.equal((await declare({ eventId: ev(), mode: 'hacked' })).status, 400);
  assert.equal((await declare({ eventId: ev(), mode: 'online_until', declaredUntil: Date.now() + 400 * 86400_000 })).status, 400);

  //  بی‌توکن ⇒ ۴۰۱ و بی ردیف
  assert.equal((await h.post('/api/shop/storefront/presence', { eventId: ev(), mode: 'closed' })).status, 401);

  //  اعلامِ سالم (با شناسهٔ خودِ دکان هم) ⇒ ۲۰۰ و بی ردیفِ رد
  const ok = await declare({ eventId: ev(), mode: 'closed', shopId: s.shopId });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.status.badge, 'closed');

  const rows = await rejected(s.shopId);
  //  ضدِ سیل: دو ردِ foreign_shop ِ پیاپی ⇒ یک ردیف
  assert.deepEqual(rows.map((r) => [r.status, r.reason]), [
    [403, 'foreign_shop'], [400, 'bad_mode'], [400, 'bad_number'],
  ]);
  for (let i = 0; i < 5; i++) await declare({ eventId: ev(), mode: 'hacked' });
  assert.equal((await rejected(s.shopId)).length, 3, 'تکرارِ همان دلیل در ۱۰ دقیقه ردیفِ تازه نمی‌سازد');
  //  کاربرِ بی دکان ⇒ ۴۰۳ no_shop و هیچ ردیف
  const lone = await h.newUser('بی‌دکان', 'shop');
  const nos = await h.post('/api/shop/storefront/presence', { eventId: ev(), mode: 'closed' }, { token: lone.accessToken });
  assert.equal(nos.status, 403);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='storefront.presence_rejected' AND shop_id=''`)).n, 0);
  assert.ok(rows.every((r) => !JSON.stringify(r).includes(foreign.shopId)), 'شناسهٔ دکانِ دیگر در سابقهٔ این دکان نیست');
  assert.ok(rows.every((r) => r.userId), 'کاربرِ واردشده ثبت شد');
  assert.equal((await rejected(foreign.shopId)).length, 0, 'سابقه زیرِ دکانِ خودِ درخواست‌کننده است');
  //  بدنه و شناسهٔ رویداد در سابقه نیست
  assert.ok(rows.every((r) => !('eventId' in r) && !('mode' in r)));
});
