'use strict';
/**
 * بازیابی رمز فراموش‌شده، با ایمیل.
 *
 * تا امروز این اصلاً نبود: دکمه‌ای در برنامه بود که فقط می‌گفت «با
 * پشتیبانی تماس بگیرید». یعنی کسی که رمزش را فراموش می‌کرد، عملاً از
 * حسابش بیرون می‌ماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

let seq = 0;
/**
 *  حساب می‌سازد — از همان سه پله‌ای که برنامه می‌رود.
 *
 *  مسیرِ یک‌مرحله‌ای بسته شد (حساب بدونِ تأییدِ ایمیل ساخته می‌شد)، پس
 *  این کمکی هم مثل خودِ برنامه کد می‌گیرد و بعد حساب می‌سازد.
 */
async function makeUser(email) {
  seq += 1;
  const password = 'Passw0rd!test';
  const started = await h.post('/api/auth/register/start', { name: 'صاحب حساب', email, password });
  assert.ok(started.status < 300, JSON.stringify(started.body));
  const verified = await h.post('/api/auth/register/verify', { email, code: started.body.devCode });
  assert.ok(verified.status < 300, JSON.stringify(verified.body));
  const done = await h.post('/api/auth/register/complete', {
    ticket: verified.body.ticket,
    name: 'صاحب حساب', password,
    terms: { accepted: true },
    device: { deviceId: `dev-reset-${seq}`, name: 'تست', platform: 'test' },
  });
  assert.ok(done.status < 300, JSON.stringify(done.body));
  return done.body;
}

test('کد بازیابی به همان ایمیل می‌رود و رمز تازه می‌نشیند', async () => {
  const email = 'reset1@example.com';
  await makeUser(email);

  const ask = await h.post('/api/auth/password/forgot', { email });
  assert.equal(ask.status, 200);
  assert.ok(ask.body.devCode, 'در حالت آزمایش کد برمی‌گردد');

  const done = await h.post('/api/auth/password/reset', {
    email, code: ask.body.devCode, password: 'TazeRamz!9876',
  });
  assert.equal(done.status, 200);
  assert.ok(done.body.accessToken, 'بعد از گذاشتن رمز، همان‌جا وارد می‌شود');

  //  رمز تازه کار می‌کند
  const inWithNew = await h.post('/api/auth/login', { identifier: email, password: 'TazeRamz!9876' });
  assert.equal(inWithNew.status, 200);

  //  و رمز قدیمی دیگر نه
  const inWithOld = await h.post('/api/auth/login', { identifier: email, password: 'Passw0rd!test' });
  assert.equal(inWithOld.status, 401);
});

test('نشست‌های باز بعد از عوض شدن رمز بسته می‌شوند', async () => {
  const email = 'reset2@example.com';
  const session = await makeUser(email);

  //  نشست فعلی کار می‌کند
  assert.equal((await h.get('/api/me', { token: session.accessToken })).status, 200);

  const ask = await h.post('/api/auth/password/forgot', { email });
  await h.post('/api/auth/password/reset', {
    email, code: ask.body.devCode, password: 'DigarRamz!5544',
  });

  //  اگر گوشی دستِ کسِ دیگری افتاده باشد، آن نشست هم باید برود
  assert.equal((await h.get('/api/me', { token: session.accessToken })).status, 401);
});

test('ایمیلِ بی‌حساب هم همان پاسخ را می‌گیرد، ولی کدی صادر نمی‌شود', async () => {
  const ask = await h.post('/api/auth/password/forgot', { email: 'nobody@example.com' });
  assert.equal(ask.status, 200);
  assert.equal(ask.body.ok, true);
  //  اگر اینجا چیزی متفاوت برمی‌گشت، می‌شد فهمید چه کسانی حساب دارند
  assert.equal(ask.body.devCode, undefined, 'برای حسابِ ناموجود کدی نباید باشد');
});

test('کد اشتباه رمز را عوض نمی‌کند', async () => {
  const email = 'reset3@example.com';
  await makeUser(email);
  await h.post('/api/auth/password/forgot', { email });

  const bad = await h.post('/api/auth/password/reset', {
    email, code: '000000', password: 'HarChizi!1234',
  });
  assert.ok(bad.status >= 400);

  //  رمز اصلی سر جایش است
  assert.equal((await h.post('/api/auth/login', { identifier: email, password: 'Passw0rd!test' })).status, 200);
});

test('کدِ ورود به درد بازیابی نمی‌خورد', async () => {
  const email = 'reset4@example.com';
  await makeUser(email);

  //  کدی که برای «ورود» صادر شده
  const login = await h.post('/api/auth/otp/request', { email });
  assert.ok(login.body.devCode);

  const misuse = await h.post('/api/auth/password/reset', {
    email, code: login.body.devCode, password: 'NaBayad!7788',
  });
  assert.ok(misuse.status >= 400, 'کد ورود نباید رمز را عوض کند');
});

test('رمز ضعیف پذیرفته نمی‌شود', async () => {
  const email = 'reset5@example.com';
  await makeUser(email);
  const ask = await h.post('/api/auth/password/forgot', { email });

  const weak = await h.post('/api/auth/password/reset', {
    email, code: ask.body.devCode, password: '123',
  });
  assert.equal(weak.status, 400);
});
