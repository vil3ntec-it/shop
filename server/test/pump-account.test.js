'use strict';
/**
 * قرارِ حسابِ برنامهٔ پمپ با سرور — «با ایمیل خودم حساب بسازم».
 *
 * گزارشِ صاحب مخزن (۱۴۰۵/۰۶/۲۹): «می‌خواهم با ایمیل خودم حسابی بسازم،
 * نمی‌شود و می‌گوید بعداً، فعلاً بی حساب ادامه دهید.»
 *
 * این‌جا **همان چیزی که برنامهٔ پمپ می‌فرستد** زده می‌شود و آن‌چه سرور
 * واقعاً پس می‌دهد قفل می‌شود. پس اگر فردا کسی نامِ فیلدی را عوض کند یا
 * دری را ببندد، همین‌جا قرمز می‌شود — نه روی کامپیوترِ کاربر.
 *
 * ── دو چیزی که همین آزمون روشن کرد ────────────────────────────────────
 *   ۱) درِ یک‌مرحله‌ایِ `/api/auth/register` **عمداً بسته است**
 *      (`verification_required`) — حساب بی تأییدِ ایمیل ساخته نمی‌شود.
 *      پس برنامه باید راهِ سه‌پله را برود.
 *   ۲) نشستِ سرور در فیلدِ `accessToken` برمی‌گردد، **نه** `token`.
 *      برنامه تا امروز `token` را می‌خواند و «سرور نشست نداد» می‌گفت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const PASS = 'Passw0rd!1';

test('درِ یک‌مرحله‌ایِ ثبت‌نام بسته است و دلیلش را می‌گوید', async () => {
  const r = await h.post('/api/auth/register', {
    name: 'هارون', email: 'one-shot@pump.com', password: PASS, app: 'pump',
  });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'verification_required');
  //  ⚠️ پیام باید به کاربر بگوید چه کند، نه فقط «نه»
  assert.match(r.body.error.message, /تأییدِ ایمیل|به‌روز/);
});

test('راهِ سه‌پله: نام و ایمیل و رمز ⇒ کدِ ایمیل ⇒ حساب و نشست', async () => {
  const email = 'pump-owner@pump.com';

  const start = await h.post('/api/auth/register/start', {
    name: 'هارون یعقوبی', email, password: PASS, passwordConfirm: PASS, app: 'pump',
  });
  assert.equal(start.status, 201);
  assert.equal(start.body.step, 'verify');
  assert.equal(start.body.email, email);
  assert.ok(start.body.devCode, 'در محیطِ آزمون کد برگردانده می‌شود');

  //  کدِ غلط حساب نمی‌سازد
  const wrong = await h.post('/api/auth/register/verify', { email, code: '000000' });
  assert.equal(wrong.status >= 400, true);

  const ver = await h.post('/api/auth/register/verify', { email, code: start.body.devCode });
  assert.equal(ver.status, 200);
  assert.equal(ver.body.step, 'location');
  assert.ok(ver.body.ticket, 'بلیتِ ثبت‌نام');
  assert.ok(ver.body.terms && ver.body.terms.version, 'متنِ شرایط همراهِ بلیت می‌آید');

  //  ⚠️ بی پذیرشِ شرایط حسابی ساخته نمی‌شود
  const noTerms = await h.post('/api/auth/register/complete', {
    ticket: ver.body.ticket, name: 'هارون یعقوبی', password: PASS, app: 'pump',
  });
  assert.equal(noTerms.status, 400);
  assert.equal(noTerms.body.error.code, 'terms_required');

  const done = await h.post('/api/auth/register/complete', {
    ticket: ver.body.ticket, name: 'هارون یعقوبی', password: PASS,
    terms: { accepted: true, version: ver.body.terms.version },
    device: { uid: 'pc-pump-test', name: 'PC', platform: 'windows' },
    app: 'pump',
  });
  assert.equal(done.status, 201);
  assert.equal(done.body.created, true);
  //  ⚠️ همان فیلدهایی که برنامهٔ پمپ می‌خواند
  assert.ok(done.body.accessToken, 'نشست در accessToken است');
  assert.ok(done.body.refreshToken);
  assert.equal(done.body.user.email, email);
  assert.equal(done.body.user.name, 'هارون یعقوبی');
  assert.equal(done.body.token, undefined, 'فیلدِ token وجود ندارد — برنامه نباید دنبالش باشد');
});

test('ورود با همان ایمیل و رمز — و پیامِ روشن برای رمزِ غلط', async () => {
  const email = 'pump-login@pump.com';
  const start = await h.post('/api/auth/register/start', {
    name: 'کریم', email, password: PASS, passwordConfirm: PASS, app: 'pump',
  });
  const ver = await h.post('/api/auth/register/verify', { email, code: start.body.devCode });
  await h.post('/api/auth/register/complete', {
    ticket: ver.body.ticket, name: 'کریم', password: PASS,
    terms: { accepted: true, version: ver.body.terms.version }, app: 'pump',
  });

  const ok = await h.post('/api/auth/login', { email, password: PASS, app: 'pump' });
  assert.equal(ok.status, 200);
  assert.ok(ok.body.accessToken);
  assert.equal(ok.body.user.email, email);

  const bad = await h.post('/api/auth/login', { email, password: 'na-drost-ast' , app: 'pump' });
  assert.equal(bad.status, 401);
  assert.equal(bad.body.error.code, 'bad_credentials');
});

test('ایمیلِ تکراری حسابِ دوم نمی‌سازد', async () => {
  const email = 'twice@pump.com';
  const first = await h.post('/api/auth/register/start', {
    name: 'یک', email, password: PASS, passwordConfirm: PASS, app: 'pump',
  });
  const ver = await h.post('/api/auth/register/verify', { email, code: first.body.devCode });
  await h.post('/api/auth/register/complete', {
    ticket: ver.body.ticket, name: 'یک', password: PASS,
    terms: { accepted: true, version: ver.body.terms.version }, app: 'pump',
  });

  const again = await h.post('/api/auth/register/start', {
    name: 'دو', email, password: PASS, passwordConfirm: PASS, app: 'pump',
  });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'already_registered');
});

test('رمزِ ضعیف و تکرارِ ناهمسان همان‌جا رد می‌شوند', async () => {
  const weak = await h.post('/api/auth/register/start', {
    name: 'ضعیف', email: 'weak@pump.com', password: '123', passwordConfirm: '123', app: 'pump',
  });
  assert.equal(weak.status, 400);
  assert.equal(weak.body.error.code, 'weak_password');

  const mismatch = await h.post('/api/auth/register/start', {
    name: 'ناهمسان', email: 'mismatch@pump.com', password: PASS, passwordConfirm: PASS + 'x', app: 'pump',
  });
  assert.equal(mismatch.status, 400);
  assert.equal(mismatch.body.error.code, 'password_mismatch');
});
