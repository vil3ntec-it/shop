'use strict';
/**
 * «فقط ایمیل» برای مشتریِ ویترین و برنامهٔ خرید (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳؛ §۲۳ ۲–۴، S133–S138، RSK-01، CHK-02).
 * جای `storefront-account-phone.test.js` ِ دیروز (تغییرِ شماره با کدِ پیامکی ⇐ به‌جایش ویرایشِ سادهٔ شمارهٔ تماس
 * و تغییرِ ایمیل با کد به ایمیلِ تازه).
 *
 *   ۱) تأیید فقط با ایمیل: `verify/start|check` با شماره ⇒ ۴۰۰ `email_required` بی ردیفِ کد؛ کد به ایمیل؛ تک‌مصرف؛
 *      کدِ کهنه پس از کدِ درست باطل؛ کدِ غلط رد؛ بی افشای وجودِ حساب (پاسخِ ایمیلِ دارای حساب = ایمیلِ تازه).
 *   ۲) ثبت (S133): بی بلیت ۴۰۱؛ با بلیتِ ایمیل، **هر** شمارهٔ تماسی (بی کد، فقط شکل) پذیرفته؛ سفارش ایمیل و شمارهٔ
 *      تماس را جدا دارد؛ «سفارش‌های من» با ایمیل، نه شماره (ایمیلِ دیگر با همان شماره چیزی نمی‌بیند).
 *   ۳) حساب: ساختن با بلیتِ تازه؛ سفارش‌های مهمانِ همان ایمیل در «سفارش‌های من» ِ حساب؛ شمارهٔ تماس بی کد عوض/پاک.
 *   ۴) تغییرِ ایمیل: نشست + بلیتِ تازهٔ ایمیلِ نو؛ «گرفته شده» فقط پس از کد؛ پس از تغییر سفارش‌های پیشین در حساب
 *      می‌مانند و دارندهٔ بلیتِ ایمیلِ پیشین آن‌ها را نمی‌بیند؛ نشستِ دیگر و بلیت‌های ایمیلِ پیشین باطل.
 *   ۵) حسابِ کهنهٔ فقط‌شماره با نشستِ زنده: «تأییدشده» نیست تا ایمیل را با کد وصل کند؛ وصل ⇒ سفارش‌های همان شمارهٔ
 *      تأییدشدهٔ پیشین به حساب می‌آیند.
 *   ۶) ⛔ پرچمِ `shop.storefront.web_account` خاموش ⇒ درهای `account/phone|email` همان درِ ناشناخته.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '60';
process.env.OTP_RESEND_SECONDS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function liveStore(label, { web = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  await setFlag(shopId, 'shop.storefront.web_account', web);
  const slug = `em-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0, acceptPickup: true },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-em' },
    body: { device_id: 'dev-em', schema_version: 2, ops: [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120 })] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: u.accessToken })).status, 200);
  return { shopId, slug };
}

const start = (slug, email) => h.post(`/api/store/${slug}/verify/start`, { email });
async function ticketOf(slug, email) {
  const s = await start(slug, email);
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
async function signIn(slug, email) {
  const r = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(slug, email) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
}
const checkout = (slug, phone, headers) => h.post(`/api/store/${slug}/checkout`, {
  fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
}, { headers: { 'Idempotency-Key': `em-${uniq()}`, ...headers } });
const mine = (slug, headers) => h.get(`/api/store/${slug}/orders`, { headers });
const acctOrders = (slug, session) => h.get(`/api/store/${slug}/account/orders`, { headers: { 'X-Store-Session': session } });
const sess = (s) => ({ 'X-Store-Session': s });
const tick = (t) => ({ 'X-Store-Ticket': t });

test('تأیید فقط با ایمیل: شماره ۴۰۰ بی کد؛ کد به ایمیل، تک‌مصرف، کدِ کهنه باطل؛ بی افشای حساب', async () => {
  const s = await liveStore('تأیید');
  for (const body of [{ phone: '0790001111' }, { phone: '+93790001111', code: '123456' }]) {
    const r = await h.post(`/api/store/${s.slug}/verify/${body.code ? 'check' : 'start'}`, body);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'email_required');
  }
  assert.deepEqual(await many("SELECT 1 FROM otp_codes WHERE destination LIKE '+93%'"), [], 'هیچ کدی به شماره');

  const a = await start(s.slug, 'Buyer.One@Example.com');
  assert.equal(a.status, 200, JSON.stringify(a.body));
  assert.equal(a.body.email, 'buyer.one@example.com');
  const b = await start(s.slug, 'buyer.one@example.com');
  const wrong = await h.post(`/api/store/${s.slug}/verify/check`, { email: 'buyer.one@example.com', code: '000000' });
  assert.equal(wrong.status, 403);
  const ok = await h.post(`/api/store/${s.slug}/verify/check`, { email: 'buyer.one@example.com', code: b.body.devCode });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.match(ok.body.ticket, /^v3\./);
  const replay = await h.post(`/api/store/${s.slug}/verify/check`, { email: 'buyer.one@example.com', code: b.body.devCode });
  assert.equal(replay.status, 403, 'تک‌مصرف');
  const stale = await h.post(`/api/store/${s.slug}/verify/check`, { email: 'buyer.one@example.com', code: a.body.devCode });
  assert.equal(stale.status, 403, 'کدِ کهنه پس از کدِ درست باطل');

  //  بی افشا: ایمیلِ دارای حساب همان پاسخِ ایمیلِ تازه
  await signIn(s.slug, 'has-acct@example.com');
  const known = await start(s.slug, 'has-acct@example.com');
  const fresh = await start(s.slug, 'no-acct@example.com');
  assert.deepEqual(Object.keys(known.body).sort(), Object.keys(fresh.body).sort());
  assert.equal(known.status, fresh.status);
});

test('ثبت: ایمیلِ تأییدشده لازم؛ شمارهٔ تماس هر چه باشد (بی کد)؛ «سفارش‌های من» با ایمیل، نه شماره', async () => {
  const s = await liveStore('ثبت');
  const none = await checkout(s.slug, '0790002222', {});
  assert.equal(none.status, 401);
  assert.equal(none.body.error.code, 'verification_required');
  const bad = await checkout(s.slug, '12', tick(await ticketOf(s.slug, 'x@example.com')));
  assert.equal(bad.status, 400, 'شمارهٔ تماس فقط شکل');

  const t = await ticketOf(s.slug, 'ali@example.com');
  const o = await checkout(s.slug, '0790002222', tick(t));
  assert.equal(o.status, 201, JSON.stringify(o.body));
  const row = await one('SELECT customer_email, customer_phone, store_customer_id FROM online_orders WHERE shop_id=$1', [s.shopId]);
  assert.deepEqual(row, { customer_email: 'ali@example.com', customer_phone: '+93790002222', store_customer_id: null });

  const m = await mine(s.slug, tick(t));
  assert.equal(m.status, 200);
  assert.equal(m.body.orders.length, 1);
  //  ایمیلِ دیگر با همان شمارهٔ تماس: هیچ
  const other = await ticketOf(s.slug, 'stranger@example.com');
  assert.equal((await checkout(s.slug, '0790002222', tick(other))).status, 201);
  assert.equal((await mine(s.slug, tick(other))).body.orders.length, 1, 'فقط سفارشِ خودش');
  assert.equal((await mine(s.slug, tick(t))).body.orders.length, 1);
});

test('حساب: سفارش‌های مهمانِ همان ایمیل می‌آیند؛ شمارهٔ تماس بی کد افزوده/عوض/پاک', async () => {
  const s = await liveStore('حساب');
  const t = await ticketOf(s.slug, 'guest@example.com');
  assert.equal((await checkout(s.slug, '0790003333', tick(t))).status, 201);
  const a = await signIn(s.slug, 'guest@example.com');
  assert.equal(a.customer.email, 'guest@example.com');
  assert.equal(a.customer.phone, null);
  assert.equal((await acctOrders(s.slug, a.session)).body.orders.length, 1, 'سفارشِ مهمان در حساب');
  assert.ok((await one('SELECT store_customer_id FROM online_orders WHERE shop_id=$1', [s.shopId])).store_customer_id);

  const set = await h.post(`/api/store/${s.slug}/account/phone`, { phone: '0790003334' }, { headers: sess(a.session) });
  assert.equal(set.status, 200, JSON.stringify(set.body));
  assert.equal(set.body.customer.phone, '+93790003334');
  const again = await h.post(`/api/store/${s.slug}/account/phone`, { phone: '+93790003334' }, { headers: sess(a.session) });
  assert.equal(again.body.unchanged, true);
  //  دو حساب یک شمارهٔ تماس (نه یکتا، نه «گرفته شده»)
  const b = await signIn(s.slug, 'sibling@example.com');
  assert.equal((await h.post(`/api/store/${s.slug}/account/phone`, { phone: '0790003334' }, { headers: sess(b.session) })).status, 200);
  const cleared = await h.post(`/api/store/${s.slug}/account/phone`, { phone: '' }, { headers: sess(a.session) });
  assert.equal(cleared.status, 200);
  assert.equal(cleared.body.customer.phone, null);
  const badPhone = await h.post(`/api/store/${s.slug}/account/phone`, { phone: '12' }, { headers: sess(a.session) });
  assert.equal(badPhone.status, 400);
  assert.equal((await many("SELECT 1 FROM otp_codes WHERE destination LIKE '+93%'")).length, 0, 'بی هیچ کدی');
  assert.equal((await acctOrders(s.slug, a.session)).body.orders.length, 1, 'سفارش پس از تغییرِ شماره می‌ماند');
});

test('تغییرِ ایمیل: نشست + کدِ ایمیلِ نو؛ «گرفته شده» پس از کد؛ سفارش‌های پیشین در حساب می‌مانند', async () => {
  const s = await liveStore('ایمیل');
  const a = await signIn(s.slug, 'old@example.com');
  const second = await signIn(s.slug, 'old@example.com');   //  دستگاهِ دوم
  assert.equal((await checkout(s.slug, '0790004444', sess(a.session))).status, 201);
  const oldTicket = await ticketOf(s.slug, 'old@example.com');
  const taken = await signIn(s.slug, 'taken@example.com');

  const noTicket = await h.post(`/api/store/${s.slug}/account/email`, { email: 'new@example.com' }, { headers: sess(a.session) });
  assert.equal(noTicket.status, 401);
  const wrongTicket = await h.post(`/api/store/${s.slug}/account/email`, { email: 'new@example.com' },
    { headers: { ...sess(a.session), ...tick(await ticketOf(s.slug, 'other@example.com')) } });
  assert.equal(wrongTicket.status, 401, 'بلیتِ ایمیلِ دیگری جز بدنه');
  const clash = await h.post(`/api/store/${s.slug}/account/email`, { email: 'taken@example.com' },
    { headers: { ...sess(a.session), ...tick(await ticketOf(s.slug, 'taken@example.com')) } });
  assert.equal(clash.status, 409, JSON.stringify(clash.body));
  assert.equal(clash.body.error.code, 'email_taken');
  assert.ok(taken.customer.id);

  const ok = await h.post(`/api/store/${s.slug}/account/email`, { email: 'new@example.com' },
    { headers: { ...sess(a.session), ...tick(await ticketOf(s.slug, 'new@example.com')) } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.customer.email, 'new@example.com');
  assert.equal(ok.body.sessionsRevoked, 1);
  assert.equal((await acctOrders(s.slug, a.session)).body.orders.length, 1, 'سفارشِ پیشین در حساب');
  assert.equal((await acctOrders(s.slug, second.session)).status, 401, 'نشستِ دستگاهِ دیگر باطل');
  assert.equal((await mine(s.slug, tick(oldTicket))).status, 401, 'بلیتِ ایمیلِ پیشین باطل');
  //  دارندهٔ تازهٔ ایمیلِ پیشین سفارشِ حساب را نمی‌بیند
  assert.equal((await mine(s.slug, tick(await ticketOf(s.slug, 'old@example.com')))).body.orders.length, 0);
  const audit = await one("SELECT detail FROM audit_logs WHERE action='store_customer.email_change' AND target_id=$1", [a.customer.id]);
  assert.ok(audit);
  assert.ok(!JSON.stringify(audit.detail).includes('@'), 'سابقه بی ایمیل');
});

test('حسابِ کهنهٔ فقط‌شماره: نشستِ زنده «تأییدشده» نیست؛ وصلِ ایمیل با کد ⇒ سفارش‌های همان شماره به حساب', async () => {
  const s = await liveStore('کهنه');
  //  حساب و سفارشِ پیش از ۸۸۱ (شمارهٔ تأییدشده) + نشستِ زنده
  const cid = '01JLEGACY0000000000000000A';
  await query(`INSERT INTO store_customers (id, phone, name, created_at, updated_at) VALUES ($1,'+93795550001','قدیمی',$2,$2)`, [cid, now()]);
  const a = await signIn(s.slug, 'temp@example.com');
  await query('UPDATE store_customer_sessions SET customer_id=$1 WHERE customer_id=$2', [cid, a.customer.id]);
  assert.equal((await checkout(s.slug, '0795550001', tick(await ticketOf(s.slug, 'temp2@example.com')))).status, 201);
  await query("UPDATE online_orders SET customer_email='' , store_customer_id=NULL WHERE shop_id=$1", [s.shopId]);

  const before = await checkout(s.slug, '0795550001', sess(a.session));
  assert.equal(before.status, 401, 'حسابِ بی ایمیل تأییدشده نیست');
  assert.equal(before.body.error.code, 'verification_required');
  assert.equal((await acctOrders(s.slug, a.session)).body.orders.length, 0);

  const link = await h.post(`/api/store/${s.slug}/account/email`, { email: 'legacy@example.com' },
    { headers: { ...sess(a.session), ...tick(await ticketOf(s.slug, 'legacy@example.com')) } });
  assert.equal(link.status, 200, JSON.stringify(link.body));
  assert.equal(link.body.linked, true);
  assert.equal(link.body.customer.email, 'legacy@example.com');
  assert.equal(link.body.customer.phone, '+93795550001', 'شماره همان شمارهٔ تماس می‌ماند');
  assert.equal((await acctOrders(s.slug, a.session)).body.orders.length, 1, 'سفارشِ شمارهٔ پیشین به حساب آمد');
  assert.equal((await checkout(s.slug, '0795550001', sess(a.session))).status, 201);
});

test('⛔ پرچمِ خاموش ⇒ درهای حساب همان درِ ناشناخته', async () => {
  const s = await liveStore('خاموش', { web: false });
  const a = await signIn(s.slug, 'off@example.com');
  for (const path of ['phone', 'email']) {
    const r = await h.post(`/api/store/${s.slug}/account/${path}`, { phone: '0790006666', email: 'x@example.com' }, { headers: sess(a.session) });
    const unknown = await h.post(`/api/store/${s.slug}/account/no-such-door`, {}, { headers: sess(a.session) });
    assert.equal(r.status, unknown.status, path);
    assert.deepEqual(r.body, unknown.body, path);
  }
  assert.equal((await one('SELECT phone FROM store_customers WHERE id=$1', [a.customer.id])).phone, null);
});
