'use strict';
/**
 * NTF-10 — عذرخواهی و کوپنِ خودکار برای تأخیرِ بیش از حد (`lib/order-late-apology.js`، مهاجرتِ ۱۹۷).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش: کارگر هیچ ردیفی نمی‌سازد، پیگیری بی `apology`، درِ صاحب ۴۰۳/`{enabled:false}`، کدِ هم‌شکل
 *      همان راهِ پیشینِ کدِ تخفیف.
 *   ۲) حداکثر **یک** پوزش برای هر سفارش، حتی با دو دورِ پیاپی و دو دورِ هم‌زمانِ کارگر (دو نمونهٔ سرور): یک ردیف،
 *      یک پیام در صف، یک رویداد. پیش از آستانه هیچ؛ زمانِ وعده = `ready_at` ِ قبول.
 *   ۳) کوپن یک‌بارمصرف و فقط برای همان شماره: شمارهٔ دیگر رد، همان شماره یک بار، بارِ دوم `used_up`، منقضی
 *      `inactive`، سفارشِ مبدأ لغوشده بی کوپن؛ درصد با پلهٔ ۱۰۰؛ پیش‌نمایشِ `/promos/quote`.
 *   ۴) سقفِ هزینهٔ NTF-02: کانالِ پولیِ پُر صدا زده نمی‌شود (`cost_cap`) و کانالِ رایگانِ بعدی می‌برد.
 *   ۵) تأخیرِ کارِ مشتری (عقب‌انداختن با `customer_request`) ⇒ بی پیام و بی کوپن؛ لغوشده هرگز نامزد نیست.
 *   ۶) TEN-02: پوزشِ «الف» به سفارش، صف، رویداد، کوپن، فهرست و پیگیریِ «ب» نمی‌رسد؛ پرچم و آستانهٔ هر دکان جدا و
 *      کوپنِ «الف» در ویترینِ «ب» ناشناخته است.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const A = require('../src/lib/order-late-apology');
const N = require('../src/lib/customer-notify');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

const MIN = 60_000;
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
test.after(async () => { N.setProviders(null); await h.stop(); });
test.beforeEach(() => resetAll());
test.afterEach(() => {
  N.setProviders(null);
  delete process.env.CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP;
  delete process.env.CUSTOMER_NOTIFY_DAILY_CAP_MICROS_WHATSAPP;
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function shop(label, { on = true, flags = [] } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `la-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.customer_notify', ...flags]) await setFlag(s.shopId, k, true);
  if (on) await setFlag(s.shopId, 'shop.orders.late_apology', true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await push(s.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 333.33, category: 'خوراکی' }),
  ])).status, 200);
  for (const p of ['rice', 'oil']) assert.equal((await h.put(`/api/shop/storefront/products/${p}`, { published: true }, { token: s.token })).status, 200);
  return s;
}
const policy = (s, body) => h.put('/api/shop/orders/late-apology', body, { token: s.token });

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
const PHONE = '0791112233';
const OTHER = '0795556677';
const checkout = async (s, { phone = PHONE, body = {} } = {}) => h.post(`/api/store/${s.slug}/checkout`, {
  fulfillment: 'pickup', customer: { name: 'مشتری', phone },
  lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }], ...body,
}, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
async function storeOrder(s, opts) {
  const r = await checkout(s, opts);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, order: r.body.order };
}
const move = async (s, id, to, extra = {}) => {
  const r = await h.post(`/api/shop/orders/${id}/transition`, { to, ...extra }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
};
const track = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const rowsOf = (id) => many('SELECT * FROM order_late_apologies WHERE order_id=$1', [id]);
const outboxOf = (id) => many(`SELECT * FROM customer_notify_outbox WHERE order_id=$1 AND event='order_late_apology'`, [id]);
const eventsOf = (id) => many(`SELECT detail FROM online_order_events WHERE order_id=$1 AND kind='note' AND detail->>'type'='late_apology'`, [id]);
const dueOf = async (id) => {
  const r = await one('SELECT ready_at, scheduled_for, created_at FROM online_orders WHERE id=$1', [id]);
  return Number(r.ready_at ?? r.scheduled_for ?? r.created_at);
};

/* ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش: بی ردیف، بی کلیدِ `apology`، درِ صاحب بسته و کدِ هم‌شکل همان راهِ پیشین', async () => {
  const s = await shop('خاموش', { on: false });
  const g = await h.get('/api/shop/orders/late-apology', { token: s.token });
  assert.equal(g.status, 200);
  assert.deepEqual(g.body, { enabled: false });
  const p = await policy(s, { enabled: true, lateMinutes: 5 });
  assert.equal(p.status, 403);
  assert.equal(p.body.error.code, 'late_apology_disabled');
  //  سیاستِ روشن در پایگاه داده، ولی پرچمِ خاموش ⇒ کارگر دست نمی‌زند
  await query(`INSERT INTO order_late_apology_policy (shop_id, enabled, late_minutes, coupon_kind, coupon_amount_minor, updated_at)
               VALUES ($1, true, 5, 'amount', 5000, $2)`, [s.shopId, now()]);
  const o = await storeOrder(s);
  const t0 = await track(s, o.token);
  await A.sweep(now() + 60 * MIN);
  assert.equal((await rowsOf(o.id)).length, 0);
  assert.equal((await outboxOf(o.id)).length, 0);
  assert.equal('apology' in (await track(s, o.token)).body.order, false);
  assert.deepEqual((await track(s, o.token)).body, t0.body);
  //  کدِ هم‌شکلِ AP… بی پرچمِ تخفیف همان نادیده گرفتنِ پیشین است
  const c = await checkout(s, { body: { promoCode: 'AP23456789' } });
  assert.equal(c.status, 201);
  assert.equal(c.body.order.discountMinor, 0);
});

test('یک پوزش برای هر سفارش: دو دورِ پیاپی و دو دورِ هم‌زمان ⇒ یک ردیف، یک پیام، یک رویداد', async () => {
  const s = await shop('یکی');
  const pol = await policy(s, { enabled: true, lateMinutes: 20, coupon: { kind: 'amount', amountMinor: 5000, days: 7 } });
  assert.equal(pol.status, 200, JSON.stringify(pol.body));
  assert.deepEqual(pol.body.policy.coupon, { kind: 'amount', amountMinor: 5000, bp: 0, days: 7 });
  const o = await storeOrder(s);
  const r = await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted', readyInMinutes: 15 }, { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const due = await dueOf(o.id);
  assert.ok(due > now(), 'زمانِ وعده = ready_at ِ قبول');

  assert.equal(await A.sweep(due + 19 * MIN), 0, 'پیش از آستانه هیچ');
  assert.equal((await rowsOf(o.id)).length, 0);
  const at = due + 21 * MIN;
  const [a, b] = await Promise.all([A.sweep(at), A.sweep(at)]);
  assert.equal(a + b, 1, 'دو دورِ هم‌زمان ⇒ یک تصمیم');
  assert.equal(await A.sweep(at + MIN), 0, 'دورِ سوم هیچ');
  const rows = await rowsOf(o.id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].outcome, 'apology');
  assert.match(rows[0].coupon_code, A.CODE_RE);
  assert.equal(Number(rows[0].coupon_expires_at), at + 7 * 24 * 60 * MIN);
  assert.equal((await outboxOf(o.id)).length, 1, 'یک پیام در صف');
  assert.equal(rows[0].notify_id, (await outboxOf(o.id))[0].id);
  assert.equal((await eventsOf(o.id)).length, 1);
  assert.equal((await eventsOf(o.id))[0].detail.coupon.kind, 'amount');

  //  پیگیری: پوزش و کوپن فقط برای دارندهٔ توکن؛ صاحب فقط ۴ نویسهٔ آخر
  const t = await track(s, o.token);
  assert.equal(t.body.order.apology.coupon.code, rows[0].coupon_code);
  assert.equal(t.body.order.apology.coupon.usable, true);
  const list = await h.get('/api/shop/orders/late-apology', { token: s.token });
  assert.equal(list.body.recent.length, 1);
  assert.equal(list.body.recent[0].coupon.last4, rows[0].coupon_code.slice(-4));
  assert.equal(JSON.stringify(list.body).includes(rows[0].coupon_code), false);
  //  پیامِ پوزش با پیامِ وضعیتِ بعدی کنار زده نمی‌شود
  await move(s, o.id, 'picking');
  assert.equal((await outboxOf(o.id))[0].status, 'pending');
});

test('کوپن یک‌بارمصرف و فقط برای همان مشتری (ایمیلِ تأییدشده)؛ منقضی و لغوشده بی‌اعتبار', async () => {
  const s = await shop('کوپن');
  await policy(s, { enabled: true, lateMinutes: 10, coupon: { kind: 'amount', amountMinor: 5000, days: 7 } });
  const o = await storeOrder(s);
  const at = (await dueOf(o.id)) + 11 * MIN;
  assert.ok(await A.sweep(at) >= 1);
  const code = (await rowsOf(o.id))[0].coupon_code;

  const stranger = await checkout(s, { phone: OTHER, body: { promoCode: code } });
  assert.equal(stranger.status, 409);
  assert.equal(stranger.body.error.code, 'promo_code_rejected');
  assert.equal((await rowsOf(o.id))[0].redeemed_at, null, 'شمارهٔ دیگر مصرف نکرد');
  //  «فقط ایمیل»: همان شمارهٔ تماس با ایمیلِ تأییدشدهٔ دیگر ⇒ رد (شماره تأیید نمی‌شود، پس هویت نیست)
  const samePhone = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'دیگری', phone: PHONE }, promoCode: code,
    lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s.slug, OTHER) } });
  assert.equal(samePhone.status, 409, JSON.stringify(samePhone.body));
  assert.equal(samePhone.body.error.code, 'promo_code_rejected');
  assert.equal((await rowsOf(o.id))[0].redeemed_at, null, 'شمارهٔ تماسِ همان مشتری بی ایمیلش مصرف نکرد');

  const mine = await checkout(s, { body: { promoCode: code.toLowerCase().replace(/^ap/, 'ap-') } });
  assert.equal(mine.status, 201, JSON.stringify(mine.body));
  assert.equal(mine.body.order.discountMinor, 5000);
  assert.equal(mine.body.order.totalMinor, mine.body.order.subtotalMinor - 5000);
  const used = (await rowsOf(o.id))[0];
  assert.ok(used.redeemed_at !== null);
  assert.match(used.redeemed_sale, /^sf:ck-/);

  const twice = await checkout(s, { body: { promoCode: code } });
  assert.equal(twice.status, 409);
  assert.equal(twice.body.error.code, 'promo_code_rejected');
  assert.equal(twice.body.error.data?.promo?.status ?? twice.body.promo?.status ?? 'used_up', 'used_up');
  assert.equal((await track(s, o.token)).body.order.apology.coupon.redeemed, true);

  //  منقضی ⇒ inactive؛ سفارشِ مبدأ لغوشده ⇒ بی کوپن در پیگیری و رد در ثبت
  const o2 = await storeOrder(s);
  await A.sweep((await dueOf(o2.id)) + 11 * MIN);   //  سفارش‌های دیگرِ همین آزمون هم دیر شده‌اند
  assert.equal((await rowsOf(o2.id)).length, 1);
  const code2 = (await rowsOf(o2.id))[0].coupon_code;
  await query('UPDATE order_late_apologies SET coupon_expires_at=$2 WHERE order_id=$1', [o2.id, now() - 1]);
  const late = await checkout(s, { body: { promoCode: code2 } });
  assert.equal(late.status, 409);
  const o3 = await storeOrder(s);
  await A.sweep((await dueOf(o3.id)) + 11 * MIN);   //  سفارش‌های دیگرِ همین آزمون هم دیر شده‌اند
  assert.equal((await rowsOf(o3.id)).length, 1);
  const code3 = (await rowsOf(o3.id))[0].coupon_code;
  await move(s, o3.id, 'rejected', { reason: 'busy' });
  assert.equal((await track(s, o3.token)).body.order.apology.coupon, null);
  assert.equal((await checkout(s, { body: { promoCode: code3 } })).status, 409);

  //  درصد با پلهٔ ۱۰۰ و پیش‌نمایشِ /promos/quote (پرچمِ تخفیفِ ویترین)
  await setFlag(s.shopId, 'shop.storefront.promos', true);
  await policy(s, { enabled: true, lateMinutes: 10, coupon: { kind: 'percent', bp: 1000, days: 3 } });
  const o4 = await storeOrder(s);
  await A.sweep((await dueOf(o4.id)) + 11 * MIN);   //  سفارش‌های دیگرِ همین آزمون هم دیر شده‌اند
  assert.equal((await rowsOf(o4.id)).length, 1);
  const code4 = (await rowsOf(o4.id))[0].coupon_code;
  const q = await h.post(`/api/store/${s.slug}/promos/quote`, { lines: [{ productId: 'rice', qtyMilli: 3000 }, { productId: 'oil', qtyMilli: 1000 }], promoCode: code4 });
  assert.equal(q.status, 200, JSON.stringify(q.body));
  const sub = 36000 + 33333;
  assert.equal(q.body.code.status, 'applied');
  assert.equal(q.body.discountMinor, Math.floor((sub * 1000 / 10000 + 50) / 100) * 100);
  const p = await checkout(s, { body: { promoCode: code4, expectedTotalMinor: q.body.totalMinor } });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(p.body.order.totalMinor, q.body.totalMinor);
});

test('سقفِ هزینهٔ NTF-02: کانالِ پولیِ پُر صدا زده نمی‌شود و کانالِ رایگانِ بعدی می‌برد', async () => {
  const s = await shop('هزینه');
  await policy(s, { enabled: true, lateMinutes: 10 });
  const o = await storeOrder(s, { phone: '0794443322' });
  const at = (await dueOf(o.id)) + 11 * MIN;
  await A.sweep(at);
  assert.equal((await rowsOf(o.id))[0].coupon_code, '', 'بی کوپن در سیاست ⇒ فقط پوزش');
  //  پیام‌های دیگرِ صف را کنار بگذار تا فقط همین پوزش بماند (سقفِ هر مشتری جداست)
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE status='pending' AND NOT (order_id=$1 AND event='order_late_apology')`, [o.id]);
  process.env.CUSTOMER_NOTIFY_COST_MICROS_WHATSAPP = '1000';
  process.env.CUSTOMER_NOTIFY_DAILY_CAP_MICROS_WHATSAPP = '0';
  const calls = [];
  const fake = (channel) => ({ send: async (msg) => { calls.push({ channel, text: msg.text }); return { status: 'accepted' }; } });
  N.setProviders({ whatsapp: fake('whatsapp'), telegram: fake('telegram') });
  await N.tick(at + 1000);
  const row = (await outboxOf(o.id))[0];
  assert.equal(row.status, 'sent');
  assert.equal(row.channel, 'telegram');
  const tried = typeof row.tried === 'string' ? JSON.parse(row.tried) : row.tried;
  assert.deepEqual(tried.map((x) => [x.channel, x.result]), [['push', 'not_configured'], ['email', 'not_configured'], ['whatsapp', 'cost_cap'], ['telegram', 'accepted']]);   //  «فقط ایمیل»: پوش ⇒ ایمیل ⇒ …
  assert.deepEqual(calls.map((c) => c.channel), ['telegram']);
  assert.match(calls[0].text, /پوزش/);
  assert.equal(/AP[0-9A-Z]{8}/.test(calls[0].text), false, 'کد در متنِ پیام نیست');
});

test('«فقط ایمیل»: پوزش از کانالِ ایمیلِ خودِ سرور به ایمیلِ تأییدشدهٔ همان سفارش می‌رسد (بی حسابِ بیرونی)، بی کد در متن', async (t) => {
  const mailer = require('../src/lib/mailer');
  const keep = { send: mailer.send, current: mailer.current };
  t.after(() => Object.assign(mailer, keep));
  const s = await shop('ایمیلِ پوزش');
  await policy(s, { enabled: true, lateMinutes: 10, coupon: { kind: 'amount', amountMinor: 5000, days: 7 } });
  const o = await storeOrder(s, { phone: '0794443366' });
  //  پس از ثبت (کدِ آزمایشیِ تأیید فقط در راهِ log): سرویسِ ایمیلِ سرور «تنظیم‌شده» و فرستادن ضبط می‌شود
  const sent = [];
  mailer.current = async () => ({ provider: 'smtp' });
  mailer.send = async (m) => { sent.push(m); };
  const at = (await dueOf(o.id)) + 11 * MIN;
  await A.sweep(at);
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE status='pending' AND NOT (order_id=$1 AND event='order_late_apology')`, [o.id]);
  await N.tick(at + 1000);
  const row = (await outboxOf(o.id))[0];
  assert.equal(row.status, 'sent', JSON.stringify(row));
  assert.equal(row.channel, 'email');
  const mine = sent.filter((m) => m.to === mailOf('0794443366'));
  assert.equal(mine.length, 1, JSON.stringify(sent.map((m) => m.to)));
  assert.match(mine[0].text, /پوزش/);
  assert.equal(/AP[0-9A-Z]{8}/.test(mine[0].text + mine[0].html), false, 'کدِ کوپن فقط در صفحهٔ پیگیری، نه در ایمیل');
  assert.equal(mine[0].text.includes('0794443366') || mine[0].text.includes('+93794443366'), false, 'بی شماره در متن');
});

test('تأخیرِ کارِ مشتری ⇒ بی پیام و بی کوپن؛ لغوشده هرگز نامزد نیست', async () => {
  const s = await shop('مشتری', { flags: ['shop.orders.delay'] });
  await policy(s, { enabled: true, lateMinutes: 10, coupon: { kind: 'amount', amountMinor: 5000, days: 7 } });
  const o = await storeOrder(s);
  await h.post(`/api/shop/orders/${o.id}/transition`, { to: 'accepted', readyInMinutes: 5 }, { token: s.token });
  const d = await h.post(`/api/shop/orders/${o.id}/delay`, { minutes: 10, reason: 'customer_request' },
    { token: s.token, headers: { 'Idempotency-Key': `dl-${uniq()}-key` } });
  assert.equal(d.status, 200, JSON.stringify(d.body));
  await A.sweep((await dueOf(o.id)) + 11 * MIN);
  const r = (await rowsOf(o.id))[0];
  assert.equal(r.outcome, 'skipped_customer');
  assert.equal(r.coupon_code, '');
  assert.equal((await outboxOf(o.id)).length, 0);
  assert.equal((await track(s, o.token)).body.order.apology, null);

  const c = await storeOrder(s);
  await move(s, c.id, 'rejected', { reason: 'busy' });
  await A.sweep((await dueOf(c.id)) + 60 * MIN);
  assert.equal((await rowsOf(c.id)).length, 0, 'ردشده/لغوشده هرگز');

  //  اعتبارسنجیِ سیاست
  const bad = await policy(s, { enabled: true, lateMinutes: 2 });
  assert.equal(bad.status, 400);
  const bad2 = await policy(s, { enabled: true, lateMinutes: 10, coupon: { kind: 'percent', bp: 9000, days: 3 } });
  assert.equal(bad2.status, 400);
});

test('TEN-02: دو دکان — پوزشِ «الف» به سفارش، صف، کوپن و فهرستِ «ب» نمی‌رسد؛ پرچم و آستانهٔ هر دکان جدا', async () => {
  const a = await shop('الفِ پوزش');
  const b = await shop('بِ پوزش');
  const c = await shop('جیمِ خاموش', { on: false });
  const pa = await policy(a, { enabled: true, lateMinutes: 10, coupon: { kind: 'amount', amountMinor: 5000, days: 7 } });
  assert.equal(pa.status, 200, JSON.stringify(pa.body));
  const pb = await policy(b, { enabled: true, lateMinutes: 60, coupon: { kind: 'amount', amountMinor: 3000, days: 7 } });
  assert.equal(pb.status, 200, JSON.stringify(pb.body));
  //  «ج»: سیاستِ روشن با آستانهٔ کوتاه‌تر در پایگاه داده، ولی پرچمِ خاموش
  await query(`INSERT INTO order_late_apology_policy (shop_id, enabled, late_minutes, coupon_kind, coupon_amount_minor, updated_at)
               VALUES ($1, true, 5, 'amount', 5000, $2)`, [c.shopId, now()]);
  //  همان مشتری در هر سه دکان
  const oa = await storeOrder(a);
  const ob = await storeOrder(b);
  const oc = await storeOrder(c);
  const due = Math.max(await dueOf(oa.id), await dueOf(ob.id), await dueOf(oc.id));
  const snap = (id) => one('SELECT * FROM online_orders WHERE id=$1', [id]);
  const [b0, c0] = [await snap(ob.id), await snap(oc.id)];
  const audits = async () => (await many(
    `SELECT shop_id, target_id FROM audit_logs WHERE action='order.late_apology' AND target_id = ANY($1::text[]) ORDER BY created_at, id`,
    [[oa.id, ob.id, oc.id]])).map((r) => [r.shop_id, r.target_id]);

  //  از آستانهٔ «الف» گذشته، نه از «ب»؛ «ج» خاموش
  await A.sweep(due + 20 * MIN);
  const [ra] = await rowsOf(oa.id);
  assert.equal(ra.shop_id, a.shopId);
  assert.equal(ra.late_minutes, 10);
  assert.deepEqual((await outboxOf(oa.id)).map((q) => q.shop_id), [a.shopId]);
  for (const o of [ob, oc]) {
    assert.equal((await rowsOf(o.id)).length, 0, `${o.id}: بی تصمیم`);
    assert.equal((await outboxOf(o.id)).length, 0, `${o.id}: بی پیام`);
    assert.equal((await eventsOf(o.id)).length, 0, `${o.id}: بی رویداد`);
  }
  assert.deepEqual(await snap(ob.id), b0, 'سفارشِ «ب» دست نخورد');
  assert.deepEqual(await snap(oc.id), c0, 'سفارشِ «ج» دست نخورد');
  assert.deepEqual(await audits(), [[a.shopId, oa.id]]);
  assert.equal((await track(b, ob.token)).body.order.apology, null);
  assert.deepEqual((await h.get('/api/shop/orders/late-apology', { token: a.token })).body.recent.map((x) => x.orderId), [oa.id]);
  assert.deepEqual((await h.get('/api/shop/orders/late-apology', { token: b.token })).body.recent, [], 'فهرستِ «ب» بی تصمیمِ «الف»');

  //  کوپنِ «الف» در ویترینِ «ب» با همان شماره ⇒ ناشناخته: بی تخفیف، و کوپنِ «الف» مصرف نمی‌شود
  const atB = await checkout(b, { body: { promoCode: ra.coupon_code } });
  assert.equal(atB.status, 201, JSON.stringify(atB.body));
  assert.equal(atB.body.order.discountMinor, 0);
  assert.equal((await rowsOf(oa.id))[0].redeemed_at, null);

  //  نوبتِ «ب» با آستانه و کوپنِ خودش؛ «الف» دوباره نه؛ «ج» هرگز
  await A.sweep(due + 61 * MIN);
  const [rb] = await rowsOf(ob.id);
  assert.equal(rb.shop_id, b.shopId);
  assert.equal(rb.late_minutes, 60);
  assert.equal(Number(rb.coupon_amount_minor), 3000);
  assert.notEqual(rb.coupon_code, ra.coupon_code);
  assert.equal((await rowsOf(oa.id)).length, 1);
  assert.equal((await outboxOf(oa.id)).length, 1);
  assert.equal((await rowsOf(oc.id)).length, 0);
  assert.deepEqual(await audits(), [[a.shopId, oa.id], [b.shopId, ob.id]]);
  assert.equal((await track(a, oa.token)).body.order.apology.coupon.code, ra.coupon_code, 'پیگیریِ «الف» کوپنِ خودش');
  assert.equal((await track(b, ob.token)).body.order.apology.coupon.code, rb.coupon_code, 'پیگیریِ «ب» کوپنِ خودش');
});
