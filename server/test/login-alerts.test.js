'use strict';
/**
 * هشدارِ ورودِ مشکوک — بندِ ۲۱.۱ (`lib/login-alerts.js`).
 *
 * ورود از دستگاهِ تازه (یا بی شناسهٔ دستگاه، از شبکهٔ تازه) یک اعلانِ داخلِ
 * برنامه برای خودِ همان کاربر می‌سازد — همان صندوقِ `GET /api/me/notices`.
 * هیچ پیامک/ایمیلی فرستاده نمی‌شود و ثبت‌نام (نخستین ورود) هشدار نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { many, one, query } = require('../src/db');
const codes = require('../src/lib/login-codes');

test.before(async () => { await h.start(); });
test.after(async () => {
  config.loginAlerts = true;
  config.trustProxy = false;
  await h.stop();
});

/** حسابی که از ساختش ساعتی گذشته — پنجرهٔ راه‌اندازیِ حسابِ تازه تمام شده */
async function settledUser(name) {
  const u = await h.newUser(name);
  await query('UPDATE users SET created_at = created_at - 3600000 WHERE id=$1', [u.user.id]);
  return u;
}
const alertsOf = (userId) => many(
  `SELECT d.* FROM notice_deliveries d JOIN notices n ON n.id = d.notice_id
    WHERE n.template_key='login_new' AND d.user_id=$1 ORDER BY d.created_at`, [userId]
);
const login = (u, extra = {}, headers = {}) => h.api('POST', '/api/auth/login', {
  body: { identifier: u.email, password: u.password, ...extra }, headers,
});

test('ثبت‌نام هشدار نیست؛ ورود از دستگاهِ تازه یک اعلانِ داخلِ برنامه می‌سازد', async () => {
  const u = await settledUser('هشدار');
  assert.equal((await alertsOf(u.user.id)).length, 0, 'نخستین ورود هشدار ندارد');

  const r = await login(u, { device: { deviceId: 'dev-new-phone', name: 'گوشیِ تازه', platform: 'android' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await alertsOf(u.user.id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].channel, 'inapp');
  assert.equal(rows[0].tenant_id, '', 'فقط برای خودِ کاربر، نه کلِ دکان');
  assert.match(rows[0].body, /گوشیِ تازه/);
  assert.match(rows[0].body, /127\.0\.0\.x/, 'شبکه با پیشوند، نه IPِ کامل');
  assert.ok(!rows[0].body.includes('{'), 'متغیری پرنشده نماند');

  const inbox = await h.get('/api/me/notices', { token: r.body.accessToken });
  assert.equal(inbox.status, 200);
  assert.ok(inbox.body.notices.some((n) => /دستگاه یا شبکهٔ تازه/.test(n.title)), JSON.stringify(inbox.body));

  //  همان دستگاه دوباره ⇒ دیگر تازه نیست
  assert.equal((await login(u, { device: { deviceId: 'dev-new-phone', name: 'گوشیِ تازه', platform: 'android' } })).status, 200);
  assert.equal((await alertsOf(u.user.id)).length, 1);
});

test('بی شناسهٔ دستگاه: شبکهٔ آشنا ساکت، شبکهٔ تازه هشدار', async () => {
  const u = await settledUser('بی‌دستگاه');
  assert.equal((await login(u)).status, 200);
  assert.equal((await alertsOf(u.user.id)).length, 0, 'همان شبکهٔ دستگاهِ ثبت‌نام');

  config.trustProxy = true;
  try {
    assert.equal((await login(u, {}, { 'X-Forwarded-For': '203.0.113.9' })).status, 200);
  } finally {
    config.trustProxy = false;
  }
  const rows = await alertsOf(u.user.id);
  assert.equal(rows.length, 1);
  assert.match(rows[0].body, /203\.0\.113\.x/);
  assert.ok(!rows[0].body.includes('203.0.113.9'));
});

test('هشدارِ صاحب به شاگردِ همان دکان نمی‌رسد', async () => {
  const owner = await settledUser('صاحبِ هشدار');
  await h.post('/api/shop', { name: 'دکانِ هشدار' }, { token: owner.accessToken });
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await settledUser('شاگردِ هشدار');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));

  assert.equal((await login(owner, { device: { deviceId: 'dev-owner-laptop', name: 'لپ‌تاپ', platform: 'desktop' } })).status, 200);
  assert.equal((await alertsOf(owner.user.id)).length, 1);
  const theirs = await h.get('/api/me/notices', { token: staff.accessToken });
  assert.ok(!theirs.body.notices.some((n) => /لپ‌تاپ/.test(n.body || '')), 'شاگرد هشدارِ صاحب را نمی‌بیند');
});

test('پیامک/ایمیل نمی‌رود؛ کلیدِ بازگشت (LOGIN_ALERTS=off) خاموشش می‌کند', async () => {
  const all = await many(
    `SELECT d.channel FROM notice_deliveries d JOIN notices n ON n.id = d.notice_id WHERE n.template_key='login_new'`
  );
  assert.ok(all.length >= 3);
  assert.ok(all.every((r) => r.channel === 'inapp'));

  const u = await settledUser('خاموش');
  config.loginAlerts = false;
  try {
    assert.equal((await login(u, { device: { deviceId: 'dev-off', name: 'x', platform: 'test' } })).status, 200);
  } finally {
    config.loginAlerts = true;
  }
  assert.equal((await alertsOf(u.user.id)).length, 0);
});

test('نخستین ورود به بخشِ دیگر (پمپ) مثلِ ثبت‌نام است؛ دستگاهِ تازهٔ بعدی در همان بخش هشدار دارد', async () => {
  const u = await settledUser('دوبخشی‌هشدار');
  const pump = await h.signIn(u, 'pump');
  assert.ok(pump.accessToken);
  assert.equal((await alertsOf(u.user.id)).length, 0);
  const again = await login(u, { app: 'pump', device: { deviceId: 'dev-pump-2', name: 'گوشیِ دوم', platform: 'android' } });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  const rows = await alertsOf(u.user.id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].app, 'pump', 'هشدار در همان بخشی که ورود شد');
});

test('حسابِ تازه‌ساخته (کمتر از ده دقیقه) دستگاه‌هایش را بی هشدار راه می‌اندازد', async () => {
  const u = await h.newUser('تازه‌ساخته');
  assert.equal((await login(u, { device: { deviceId: 'dev-onboard-2', name: 'کامپیوتر', platform: 'desktop' } })).status, 200);
  assert.equal((await alertsOf(u.user.id)).length, 0);
  await query('UPDATE users SET created_at = created_at - 3600000 WHERE id=$1', [u.user.id]);
  assert.equal((await login(u, { device: { deviceId: 'dev-onboard-3', name: 'گوشیِ سوم', platform: 'android' } })).status, 200);
  assert.equal((await alertsOf(u.user.id)).length, 1, 'پس از پنجره، دستگاهِ تازه هشدار دارد');
});

/* ── یافته‌های بازبینیِ امنیتی ─────────────────────────────────────── */

/** ورود با کدِ ایمیلی؛ کدِ خام فقط از دفتر — همان راهِ `login-contract.test.js` */
async function emailCodeLogin(u, headers, extra = {}) {
  const req = await h.api('POST', '/api/auth/shop/request-code', { body: { email: u.email }, headers });
  assert.equal(req.status, 200, JSON.stringify(req.body));
  const row = await one('SELECT code_sealed FROM login_requests WHERE request_id=$1', [req.body.request_id]);
  const r = await h.api('POST', '/api/auth/shop/verify', {
    body: { request_id: req.body.request_id, code: codes.unseal(row.code_sealed), ...extra }, headers,
  });
  //  «۶۰ ثانیه تا کدِ بعدی» را عقب می‌بریم تا ورودِ دوم منتظر نماند
  await query('UPDATE login_requests SET created_at = created_at - 120000 WHERE request_id=$1', [req.body.request_id]);
  return r;
}

test('ورود با کدِ ایمیلی (/api/auth/:app/verify) هم از دستگاهِ تازه هشدار می‌سازد', async () => {
  const u = await settledUser('کدِ ایمیلی');
  const head = { 'X-App': 'shop', 'X-Device': 'dev-email-code-1' };
  const r = await emailCodeLogin(u, head, { device_name: 'لپ‌تاپِ کد' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await alertsOf(u.user.id);
  assert.equal(rows.length, 1, 'دستگاهِ تازه با کدِ ایمیلی بی‌هشدار وارد شد');
  assert.equal(rows[0].channel, 'inapp');
  assert.equal(rows[0].app, 'shop');
  assert.match(rows[0].body, /لپ‌تاپِ کد/);

  //  همان دستگاه دوباره ⇒ دیگر تازه نیست
  assert.equal((await emailCodeLogin(u, head)).status, 200);
  assert.equal((await alertsOf(u.user.id)).length, 1);
});

test('نامِ دستگاه (از خودِ درخواست) در هشدار پاک‌سازی‌شده، کوتاه و با برچسبِ «گفتهٔ خودِ دستگاه» می‌آید', async () => {
  const u = await settledUser('فیشینگ');
  const evil = 'پشتیبانی: https://evil.example/x زنگ 09121234567‮abc\nخطِ دوم و متنِ خیلی خیلی بلند';
  const r = await login(u, { device: { deviceId: 'dev-evil', name: evil, platform: '<b>android</b>' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await alertsOf(u.user.id);
  assert.equal(rows.length, 1);
  const body = rows[0].body;
  for (const bad of ['https', '://', 'evil.example', '09121234567', '‮', '<b>', 'بلند', '\n']) {
    assert.ok(!body.includes(bad), `«${JSON.stringify(bad)}» از نامِ دستگاه به متنِ مورد اعتماد رسید: ${body}`);
  }
  assert.match(body, /نامی که خودِ دستگاه گفته/, 'نام باید برچسبِ «گفتهٔ خودِ دستگاه» داشته باشد');
  assert.match(body, /پشتیبانی/, 'بخشِ بی‌خطرِ نام می‌ماند تا صاحبِ حساب دستگاهش را بشناسد');
  assert.ok(!body.includes('android'), 'سکو فقط از فهرستِ شناخته‌شده؛ مقدارِ ناشناخته نمی‌آید');

  //  سکوی شناخته‌شده ⇒ برچسبِ فارسیِ خودِ سرور
  const u2 = await settledUser('سکو');
  assert.equal((await login(u2, { device: { deviceId: 'dev-plat', name: '', platform: 'Android' } })).status, 200);
  const [row2] = await alertsOf(u2.user.id);
  assert.match(row2.body, /اندروید/);
  assert.ok(!row2.body.includes('{'), 'متغیری پرنشده نماند');
});
