'use strict';
/**
 * RSK-07، نیمهٔ «درخواستِ کدِ ورود»: سقفِ فرستادنِ کدِ تأییدِ شمارهٔ ویترین، هم به‌ازای IP و هم به‌ازای شماره.
 *
 *   IP ──── درِ `/api/store/:slug/verify/start` سطلِ `store-verify` (`storeCheckoutMax`) دارد:
 *           درخواستِ (max+1)-ام از همان IP ⇒ ۴۲۹ با Retry-After، حتی برای شمارهٔ تازه و ویترینِ
 *           دیگر، و بی هیچ ردیفِ کد؛ IP ِ دیگر همان دم آزاد است.
 *   شماره ─ هر نشانی در ۲۴ ساعت حداکثر `OTP_DAILY_MAX` کد (`lib/otp`) — از هر IP و از هر ویترین:
 *           (max+1)-امی ⇒ ۴۲۹ `otp_daily_limit` و بی ردیفِ تازه؛ شمارهٔ دیگر از همان IP آزاد.
 *           ‎+930…‎ و ‎+93…‎ یک شماره‌اند (validate.phone همه را یک شکل می‌کند).
 *
 * نیمهٔ «ثبتِ سفارش» جای دیگر قفل است: IP در `storefront.test.js` («سقفِ ثبتِ سفارش به‌ازای IP»)
 * و شماره در `storefront-risk.test.js` (RSK-07، سه آزمون).
 */
process.env.RATE_STORE_CHECKOUT_MAX = '4';
process.env.OTP_DAILY_MAX = '3';
//  IP از سرآیندِ پراکسی (آخرین بخش) — تا «IP ِ دیگر» در آزمون شدنی باشد
process.env.TRUST_PROXY = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
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

/** دکانِ روشن با ویترینِ روشن (کالا لازم نیست؛ فقط درِ کد). */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `c-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { shopId, slug };
}

const start = (slug, phone, ip) => h.post(`/api/store/${slug}/verify/start`, { phone },
  { headers: { 'X-Forwarded-For': ip } });
const codes = async (dest) => (await one(
  'SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination = $1', [dest]
)).n;

test('RSK-07: درخواستِ پنجمِ کد از یک IP ⇒ ۴۲۹ با Retry-After و بی ردیف، حتی شمارهٔ تازه در ویترینِ دیگر؛ IP ِ دیگر آزاد', async () => {
  const a = await liveStore('کدِ IP الف');
  const b = await liveStore('کدِ IP ب');
  resetAll();
  const max = require('../src/config').rateLimit.storeCheckoutMax;
  assert.equal(max, 4);
  const ip = '198.51.100.7';
  for (let i = 0; i < max; i++) {
    const r = await start(i % 2 ? b.slug : a.slug, `07911100${10 + i}`, ip);
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const fresh = '0791110099';
  const over = await start(b.slug, fresh, ip);
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.ok(Number(over.headers.get('retry-after')) > 0, 'Retry-After');
  assert.equal(await codes('+93791110099'), 0, 'کدی ساخته نشد');

  const other = await start(b.slug, fresh, '203.0.113.9');
  assert.equal(other.status, 200, JSON.stringify(other.body));
  assert.equal(await codes('+93791110099'), 1);
});

test('RSK-07: کدِ چهارمِ یک شماره در ۲۴ ساعت ⇒ ۴۲۹ otp_daily_limit از هر IP و هر ویترین؛ شمارهٔ دیگر آزاد', async () => {
  const a = await liveStore('کدِ شماره الف');
  const b = await liveStore('کدِ شماره ب');
  const daily = require('../src/config').otp.dailyMax;
  assert.equal(daily, 3);
  const phone = '0792220011';
  const dest = '+93792220011';
  for (let i = 0; i < daily; i++) {
    resetAll();
    const r = await start(i % 2 ? b.slug : a.slug, phone, `198.51.100.${20 + i}`);
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  assert.equal(await codes(dest), daily);

  resetAll();
  //  IP ِ تازه، ویترینِ دیگر، و شکلِ ‎+930…‎ ِ همان شماره — باز هم همان سقف
  const over = await start(b.slug, '+930792220011', '198.51.100.99');
  assert.equal(over.status, 429, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'otp_daily_limit');
  assert.equal(await codes(dest), daily, 'ردیفِ تازه‌ای ساخته نشد');

  const other = await start(b.slug, '0792220012', '198.51.100.99');
  assert.equal(other.status, 200, JSON.stringify(other.body));
});

test('RSK-07: فاصلهٔ «کدِ تازه» هم همهٔ شکل‌های شماره را می‌شناسد — ‎+930…‎ پیش از مهلت ⇒ ۴۲۹ otp_resend_wait', async (t) => {
  const a = await liveStore('کدِ فاصله');
  const cfg = require('../src/config').otp;
  const was = cfg.resendMs;
  cfg.resendMs = 120_000;
  t.after(() => { cfg.resendMs = was; });
  const first = await start(a.slug, '0793330011', '198.51.100.40');
  assert.equal(first.status, 200, JSON.stringify(first.body));
  const alias = await start(a.slug, '+930793330011', '198.51.100.41');
  assert.equal(alias.status, 429, JSON.stringify(alias.body));
  assert.equal(alias.body.error.code, 'otp_resend_wait');
  assert.equal(await codes('+930793330011'), 0);
  //  شمارهٔ دیگر منتظرِ این یکی نمی‌ماند
  const other = await start(a.slug, '0793330012', '198.51.100.41');
  assert.equal(other.status, 200, JSON.stringify(other.body));
});
