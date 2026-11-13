'use strict';
/**
 * §۲۳.۴ «تغییرِ شمارهٔ تلفن» ِ حسابِ مشتری از مسیرِ قابل‌اعتماد — `POST /api/store/:slug/account/phone`
 * (`lib/store-customers.changePhone`؛ صفحه: `store/phone.js` ِ دیربار از `store/acct.js`، مرورگر: `web-store-phone.mjs`).
 *
 * جریان: نشستِ همین حساب (شمارهٔ پیشین) + کدِ یک‌بارمصرف به شمارهٔ **تازه** از همان درهای `verify/start|check`
 * (همان `lib/otp`، همان سقف‌ها) ⇒ بلیتِ تازه (≤ `accountTicketMaxMs`) ⇒ همین در، با همان شماره در بدنه.
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ `shop.storefront.web_account` خاموش ⇒ دقیقاً پاسخِ درِ ناشناخته، پیش از سقف و نشست؛ چیزی عوض نمی‌شود.
 *   ۲) بی نشست ۴۰۱ `session_required`؛ بی بلیت، بلیتِ کهنه، یا بلیتِ شمارهٔ دیگری جز بدنه ⇒ ۴۰۱ `verification_required`.
 *   ۳) شمارهٔ حسابِ دیگر: فرستادنِ کد همان پاسخِ شمارهٔ آزاد (وجودِ حساب پیش از کد گفته نمی‌شود)؛ پس از کد ۴۰۹
 *      `phone_taken` (دارندهٔ همان شماره) — شکلِ دیگرِ همان شماره (‎+930…‎) هم؛ هیچ حسابی عوض نمی‌شود.
 *   ۴) موفق: شماره عوض، همین نشست می‌ماند، نشستِ دستگاهِ دیگر و بلیت‌های شمارهٔ پیشین باطل، سابقهٔ
 *      `store_customer.phone_change` بی هیچ شماره‌ای؛ کدِ پیشین پس از کدِ تازه باطل.
 *   ۵) تکرار و هم‌زمانی: یک تغییر، یک سابقه؛ تکرار ۲۰۰ ِ `unchanged` (بی اثرِ دوم).
 *   ۶) سقفِ نرخِ همان سطلِ `storeCheckoutMax`.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '40';
process.env.OTP_RESEND_SECONDS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
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

async function liveStore(label, { web = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  await setFlag(shopId, 'shop.storefront.web_account', web);
  const slug = `ph-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}` }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { shopId, slug };
}

const start = (slug, phone) => h.post(`/api/store/${slug}/verify/start`, { phone });
async function ticketOf(slug, phone) {
  const s = await start(slug, phone);
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body;
}
async function signIn(slug, phone) {
  const r = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': (await ticketOf(slug, phone)).ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
}
const change = (slug, { session = '', ticket = '', phone } = {}) => h.post(`/api/store/${slug}/account/phone`, { phone },
  { headers: { ...(session ? { 'X-Store-Session': session } : {}), ...(ticket ? { 'X-Store-Ticket': ticket } : {}) } });
const me = (slug, session) => h.get(`/api/store/${slug}/account`, { headers: { 'X-Store-Session': session } });
const phoneOf = async (id) => (await one('SELECT phone FROM store_customers WHERE id = $1', [id])).phone;
const logs = (id) => many(`SELECT detail FROM audit_logs WHERE action = 'store_customer.phone_change' AND target_id = $1`, [id]);

let n = 0;
/** شماره‌های یکتا برای هر آزمون (حساب سراسری است) */
const phones = () => {
  n += 1;
  const base = 791000000 + (Date.now() % 100000) * 10 + n;
  return [`0${base}`, `0${base + 1000000}`, `0${base + 2000000}`, `0${base + 3000000}`];
};
const intl = (local) => `+93${local.slice(1)}`;

test('۱) پرچمِ خاموش: همان پاسخِ درِ ناشناخته، پیش از سقف و نشست؛ چیزی عوض نمی‌شود', async () => {
  const s = await liveStore('خاموش', { web: false });
  const [P, Q] = phones();
  const a = await signIn(s.slug, P);
  const tk = (await ticketOf(s.slug, Q)).ticket;
  const unknown = await h.post(`/api/store/${s.slug}/account/no-such-door`, { phone: Q },
    { headers: { 'X-Store-Session': a.session, 'X-Store-Ticket': tk } });
  //  ۴۱ بار > سقفِ ۴۰ ِ سطلِ همین در: خاموش هرگز به سقف نمی‌رسد
  for (let i = 0; i < 41; i++) {
    const r = await change(s.slug, { session: a.session, ticket: tk, phone: Q });
    assert.equal(r.status, unknown.status, JSON.stringify(r.body));
    assert.deepEqual(r.body, unknown.body);
  }
  assert.ok([401, 404].includes(unknown.status), JSON.stringify(unknown.body));   //  دروازهٔ پایانیِ `/api` بی توکن ۴۰۱ است
  assert.equal(await phoneOf(a.customer.id), intl(P));
  assert.equal((await logs(a.customer.id)).length, 0);
});

test('۲) بی نشست، بی بلیت، بلیتِ کهنه یا بلیتِ شمارهٔ دیگر ⇒ ۴۰۱ و شماره همان', async () => {
  const s = await liveStore('سد');
  const [P, Q, R] = phones();
  const a = await signIn(s.slug, P);
  const tq = (await ticketOf(s.slug, Q)).ticket;
  let r = await change(s.slug, { ticket: tq, phone: Q });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'session_required');
  r = await change(s.slug, { session: a.session, phone: Q });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'verification_required');
  //  بلیتِ درست ولی کهنه‌تر از مهلتِ حساب (همان قاعدهٔ ساختن/ورود)
  const sf = require('../src/lib/storefront');
  const gen = await require('../src/lib/store-customers').ticketGen(intl(Q));
  const old = sf.ticketOf(intl(Q), now() - config.storefront.accountTicketMaxMs - 60 * 1000, gen).ticket;
  r = await change(s.slug, { session: a.session, ticket: old, phone: Q });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'verification_required');
  //  بلیتِ Q، بدنهٔ R: شماره‌ای که کاربر خواست ثابت نشده
  r = await change(s.slug, { session: a.session, ticket: tq, phone: R });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'verification_required');
  r = await change(s.slug, { session: a.session, ticket: tq, phone: 'abc' });
  assert.equal(r.status, 400);
  assert.equal(await phoneOf(a.customer.id), intl(P));
  assert.equal((await logs(a.customer.id)).length, 0);
});

test('۳) شمارهٔ حسابِ دیگر: کد بی افشا، پس از کد ۴۰۹ `phone_taken` (شکلِ دیگرِ شماره هم)؛ هیچ حسابی عوض نمی‌شود', async () => {
  const s = await liveStore('گرفته');
  const [P, B, F] = phones();
  const a = await signIn(s.slug, P);
  const b = await signIn(s.slug, B);
  //  پاسخِ «فرستادنِ کد» برای شمارهٔ دارای حساب و شمارهٔ آزاد یک شکل دارد
  const shape = (x) => ({ status: x.status, keys: Object.keys(x.body).sort(), sent: x.body.sent });
  assert.deepEqual(shape(await start(s.slug, B)), shape(await start(s.slug, F)));
  resetAll();
  for (const form of [B, `+930${B.slice(1)}`]) {
    const t = await ticketOf(s.slug, form);
    const r = await change(s.slug, { session: a.session, ticket: t.ticket, phone: t.phone });
    assert.equal(r.status, 409, `${form}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'phone_taken');
  }
  assert.equal(await phoneOf(a.customer.id), intl(P));
  assert.equal(await phoneOf(b.customer.id), intl(B));
  assert.equal((await me(s.slug, a.session)).status, 200, 'نشستِ حساب می‌ماند');
  assert.equal((await me(s.slug, b.session)).status, 200, 'حسابِ دیگر دست نخورد');
  assert.equal((await logs(a.customer.id)).length, 0);
});

test('۴) موفق: شماره عوض، همین نشست می‌ماند، نشستِ دیگر و بلیتِ شمارهٔ پیشین باطل، سابقه بی شماره، کدِ پیشین باطل', async () => {
  const s = await liveStore('عوض');
  const [P, Q] = phones();
  const a = await signIn(s.slug, P);
  const other = await signIn(s.slug, P);    //  دستگاهِ دیگرِ همین حساب
  assert.equal(other.customer.id, a.customer.id);
  const oldTicket = (await ticketOf(s.slug, P)).ticket;
  //  دو کد برای شمارهٔ تازه؛ تازه‌ترین به کار می‌رود
  const first = await start(s.slug, Q);
  const second = await start(s.slug, Q);
  assert.notEqual(first.body.devCode, second.body.devCode);
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone: Q, code: second.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const r = await change(s.slug, { session: a.session, ticket: c.body.ticket, phone: c.body.phone });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.customer.phone, intl(Q));
  assert.equal(r.body.customer.id, a.customer.id);
  assert.equal(r.body.sessionsRevoked, 1);
  assert.equal(r.body.unchanged, undefined);
  assert.equal(await phoneOf(a.customer.id), intl(Q));
  const mine = await me(s.slug, a.session);
  assert.equal(mine.status, 200, 'همین نشست می‌ماند');
  assert.equal(mine.body.customer.phone, intl(Q));
  assert.equal((await me(s.slug, other.session)).status, 401, 'نشستِ دستگاهِ دیگر باطل');
  //  بلیتِ شمارهٔ پیشین (همین دقیقه) دیگر حساب نمی‌سازد و سفارش نمی‌خواند
  const re = await h.post(`/api/store/${s.slug}/account`, {}, { headers: { 'X-Store-Ticket': oldTicket } });
  assert.equal(re.status, 401);
  assert.equal(re.body.error.code, 'verification_required');
  //  کدِ پیشینِ شمارهٔ تازه پس از به کار رفتنِ کدِ تازه باطل است
  const late = await h.post(`/api/store/${s.slug}/verify/check`, { phone: Q, code: first.body.devCode });
  assert.equal(late.status, 403, JSON.stringify(late.body));
  //  سابقه: یک ردیف، بی هیچ شماره‌ای
  const rows = await logs(a.customer.id);
  assert.equal(rows.length, 1);
  const detail = JSON.stringify(rows[0].detail);
  for (const d of [P.slice(1), Q.slice(1)]) assert.ok(!detail.includes(d), detail);
  assert.equal(rows[0].detail.sessionsRevoked, 1);
  //  شمارهٔ پیشین حالا آزاد است: ورودِ تازه با آن حسابِ دیگری است و نشانیِ این حساب را نمی‌بیند
  await h.post(`/api/store/${s.slug}/account/addresses`, { label: 'خانه', text: 'کوچهٔ آزمون' }, { headers: { 'X-Store-Session': a.session } });
  const fresh = await signIn(s.slug, P);
  assert.notEqual(fresh.customer.id, a.customer.id);
  assert.equal((await me(s.slug, fresh.session)).body.addresses.length, 0);
});

test('۵) تکرار و هم‌زمانی: یک تغییر، یک سابقه؛ تکرار ۲۰۰ ِ `unchanged`', async () => {
  const s = await liveStore('تکرار');
  const [P, Q] = phones();
  const a = await signIn(s.slug, P);
  const t = await ticketOf(s.slug, Q);
  const both = await Promise.all([1, 2].map(() => change(s.slug, { session: a.session, ticket: t.ticket, phone: t.phone })));
  for (const r of both) assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(both.filter((r) => r.body.unchanged === true).length, 1, JSON.stringify(both.map((r) => r.body)));
  for (const r of both) assert.equal(r.body.customer.phone, intl(Q));
  const again = await change(s.slug, { session: a.session, ticket: t.ticket, phone: t.phone });
  assert.equal(again.status, 200);
  assert.equal(again.body.unchanged, true);
  assert.equal(again.body.sessionsRevoked, 0);
  assert.equal((await logs(a.customer.id)).length, 1);
  assert.equal((await me(s.slug, a.session)).status, 200);
});

test('۶) سقفِ نرخ: سطلِ `storeCheckoutMax` ِ همین IP', async () => {
  const s = await liveStore('سقف');
  const [P] = phones();
  const a = await signIn(s.slug, P);
  resetAll();
  let last = null;
  for (let i = 0; i < 41; i++) last = await change(s.slug, { session: a.session, phone: P });
  assert.equal(last.status, 429, JSON.stringify(last.body));
});
