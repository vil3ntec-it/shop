'use strict';
/**
 * میزِ «کدهای شش‌رقمی» برای دفترِ `otp_codes`.
 *
 * ── گزارشِ صاحب سامانه، با عکس ────────────────────────────────────────
 *   «کد نمیاد توی بخش کد ها هیچ کدی نمیاد… این کد که شانسی برای این حساب
 *    اومد توی این بخش کد های شش رقمی اصلن دیده نمیشه برای کدوم حساب درست
 *    شده و برای کدوم برنامه و ایمیل است.»
 *
 * ⛔ ریشه: دو دفترِ کد بود و پنل یکی‌شان را می‌دید. `login_requests` میز
 * داشت، `otp_codes` — دفترِ کدِ **ثبت‌نام** — هیچ. این پرونده همان میز را
 * قفل می‌کند و **پیش از اصلاح سرخ بود** (مسیر ۴۰۴ می‌گرفت).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

test('۱) کدِ ثبت‌نام در میزِ مدیر دیده می‌شود — با ایمیل و برنامه و هدف', async () => {
  const email = 'desk1@test.local';
  const started = await h.post(
    '/api/auth/register/start',
    { name: 'هارون', email, password: 'Passw0rd!test' },
    { headers: { 'X-App-Id': 'tohid-pump-app' } },
  );
  assert.equal(started.status, 201);

  const token = await adminToken();
  const list = await h.get('/api/admin/otp?limit=20', { token });
  assert.equal(list.status, 200);
  const row = list.body.requests.find((r) => r.destination === email);
  assert.ok(row, 'ردیفِ همین ایمیل باید در میز باشد');
  //  سه چیزی که صاحب سامانه گفت دیده نمی‌شود
  assert.equal(row.destination, email);
  assert.equal(row.app, 'pump', 'باید بگوید کدام برنامه کد را خواسته');
  assert.equal(row.purpose, 'register', 'باید بگوید برای چه کاری ساخته شده');
  assert.equal(row.active, true);
  //  ⛔ خودِ کد در فهرست نمی‌آید
  assert.equal(row.code, undefined);
  assert.ok(!Object.values(row).some((v) => typeof v === 'string' && /^\d{6}$/.test(v)),
    'هیچ خانه‌ای نباید کدِ شش‌رقمی باشد');
  //  ⚠️ «رفت» با «در لاگ چاپ شد» یکی نیست
  assert.equal(row.via, 'log');
  assert.equal(row.log_only, true);
  assert.ok(row.sent_at > 0);
});

test('۲) نمایشِ کد همان کدی را می‌دهد که واقعاً کار می‌کند', async () => {
  const email = 'desk2@test.local';
  const started = await h.post('/api/auth/register/start', { name: 'ن', email, password: 'Passw0rd!test' });
  assert.equal(started.status, 201);

  const token = await adminToken();
  const list = await h.get(`/api/admin/otp?destination=${encodeURIComponent(email)}`, { token });
  const row = list.body.requests[0];
  assert.equal(row.can_reveal, true);

  const shown = await h.post(`/api/admin/otp/${row.id}/reveal`, {}, { token });
  assert.equal(shown.status, 200);
  assert.match(String(shown.body.code), /^\d{6}$/);
  assert.ok(shown.body.expires_in > 0);

  //  ⛔ سنجهٔ واقعی: همان کد باید در خودِ جریانِ ثبت‌نام بپذیرد
  const ok = await h.post('/api/auth/register/verify', { email, code: shown.body.code });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.ticket);
});

test('۳) کدِ مصرف‌شده دیگر نشان داده نمی‌شود', async () => {
  const email = 'desk3@test.local';
  await h.post('/api/auth/register/start', { name: 'ن', email, password: 'Passw0rd!test' });
  const token = await adminToken();
  const row = (await h.get(`/api/admin/otp?destination=${encodeURIComponent(email)}`, { token })).body.requests[0];
  const first = await h.post(`/api/admin/otp/${row.id}/reveal`, {}, { token });
  assert.equal(first.status, 200);

  await h.post('/api/auth/register/verify', { email, code: first.body.code });

  const again = await h.post(`/api/admin/otp/${row.id}/reveal`, {}, { token });
  assert.equal(again.status, 404);
  assert.equal(again.body.error.code, 'code_unavailable');

  const after = (await h.get(`/api/admin/otp?destination=${encodeURIComponent(email)}`, { token }))
    .body.requests[0];
  assert.equal(after.can_reveal, false, 'مهروموم با مصرف پاک می‌شود');
  assert.ok(after.consumed_at > 0);
});

test('۴) هر نمایش در دفترِ رخدادها می‌نشیند — و بی نشانیِ کامل', async () => {
  const email = 'desk4@test.local';
  await h.post('/api/auth/register/start', { name: 'ن', email, password: 'Passw0rd!test' });
  const token = await adminToken();
  const row = (await h.get(`/api/admin/otp?destination=${encodeURIComponent(email)}`, { token })).body.requests[0];
  await h.post(`/api/admin/otp/${row.id}/reveal`, {}, { token });

  const logged = await require('../src/db').one(
    `SELECT * FROM audit_logs WHERE action='otp.code_revealed' AND target_id=$1`, [row.id]
  );
  assert.ok(logged, 'رخداد باید ثبت شده باشد');
  const text = JSON.stringify(logged);
  assert.ok(!text.includes(email), 'نشانیِ کامل در دفترِ رخدادها نمی‌نشیند');
});

test('۵) فیلترِ برنامه و هدف کار می‌کند', async () => {
  const token = await adminToken();
  const pump = await h.get('/api/admin/otp?app=pump&limit=50', { token });
  assert.equal(pump.status, 200);
  assert.ok(pump.body.requests.every((r) => r.app === 'pump' || r.app === ''));
  const resets = await h.get('/api/admin/otp?purpose=reset&limit=50', { token });
  assert.ok(resets.body.requests.every((r) => r.purpose === 'reset'));
});

test('۶) میز پشتِ ورودِ مدیر است', async () => {
  const anon = await h.get('/api/admin/otp');
  assert.ok(anon.status === 401 || anon.status === 403, `بی توکن باید بسته باشد، شد ${anon.status}`);
});

test('۷) هر ۵۰۰ کدِ پیگیری دارد — «خطای داخلی سرور» بی نشانی نمی‌ماند', async () => {
  const { errorHandler } = require('../src/middleware/errors');
  const sent = [];
  const res = {
    status(c) { this._c = c; return this; },
    json(b) { sent.push({ status: this._c, body: b }); return this; },
  };
  const quiet = console.error;
  console.error = () => {};
  try {
    errorHandler(new Error('ستونی که وجود ندارد'), { method: 'POST', path: '/api/pump' }, res, () => {});
  } finally { console.error = quiet; }

  const out = sent[0];
  assert.equal(out.status, 500);
  //  ⛔ پیامِ خام هیچ‌وقت بیرون نمی‌رود
  assert.ok(!out.body.error.message.includes('ستونی که وجود ندارد'));
  //  ⛔ ولی بی نشانی هم نمی‌ماند — وگرنه عکسِ «خطای داخلی سرور» به هیچ سطرِ لاگی وصل نمی‌شود
  assert.match(out.body.error.ref, /^e[a-z0-9]{6,}$/);
  assert.ok(out.body.error.message.includes(out.body.error.ref));

  //  و خطای ۴xx دست‌نخورده می‌ماند: پیامِ خودش، بی کدِ پیگیری
  sent.length = 0;
  errorHandler(require('../src/middleware/errors').badRequest('ایمیل لازم است'), { method: 'POST', path: '/x' }, res, () => {});
  assert.equal(sent[0].status, 400);
  assert.equal(sent[0].body.error.message, 'ایمیل لازم است');
  assert.equal(sent[0].body.error.ref, undefined);
});

test('۸) نبضِ زنده هر دو دفترِ کد را می‌بیند', async () => {
  const token = await adminToken();
  const before = (await h.get('/api/admin/stamps', { token })).body.stamps;

  //  کدی که **فقط** در دفترِ دوم می‌نشیند (ثبت‌نام)
  await h.post('/api/auth/register/start', { name: 'ن', email: 'pulse@test.local', password: 'Passw0rd!test' });

  const after = (await h.get('/api/admin/stamps', { token })).body.stamps;
  /*
   *  ⛔ این بندِ اصلی است: تا ۲.۸.۳ مهرِ `codes` فقط از `login_requests`
   *  می‌آمد، پس کدِ ثبت‌نام گذرگاهِ زنده را بیدار نمی‌کرد و صفحهٔ پنل تا
   *  تازه کردنِ دستی هیچ نمی‌دانست. میز را ساخته بودیم، زنده‌اش نکرده بودیم.
   */
  assert.ok(after.codes > before.codes,
    `مهرِ کدها باید جلو برود — پیش ${before.codes}، پس ${after.codes}`);
  //  ⚠️ و «ورودها» دفترِ خودش است و نباید با این تکان بخورد
  assert.equal(after.logins, before.logins, 'مهرِ ورودها دفترِ خودش را می‌گوید');
});
