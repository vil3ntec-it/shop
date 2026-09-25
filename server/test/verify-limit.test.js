'use strict';
/**
 * زدنِ کد سطلِ خودش را دارد — جدا از فرستادنِ ایمیل (۱۴۰۵/۰۷/۱۳).
 *
 * سنجهٔ `linkstates`ِ برنامهٔ پمپ روی سرورِ واقعی: کاربری که ثبت‌نام کرده
 * بود و همان ربع ساعت رمزش را بازیابی کرد، سرِ **زدنِ** کدِ بازیابی «تلاشِ
 * زیاد» گرفت — چون تأییدِ کد همان پنج‌تای «ایمیلِ فرستاده» را می‌خورد.
 *
 * ⚠️ این‌جا سقفِ فرستادن همان پیش‌فرضِ واقعی (۵) است، نه ده‌هزارِ آزمون‌ها.
 */
process.env.RATE_OTP_MAX = '5';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('⛔ ثبت‌نام + بازیابیِ رمز در یک ربع ساعت — زدنِ کد «تلاشِ زیاد» نمی‌گیرد', async () => {
  const email = 'verify-limit@example.com';
  const password = 'Passw0rd!test';
  //  ثبت‌نام: فرستادن (۱) + تأیید
  const s = await h.post('/api/auth/register/start', { name: 'الف', email, password });
  assert.ok(s.status < 300, JSON.stringify(s.body));
  const v = await h.post('/api/auth/register/verify', { email, code: s.body.devCode });
  assert.ok(v.status < 300, JSON.stringify(v.body));
  const done = await h.post('/api/auth/register/complete', {
    ticket: v.body.ticket, name: 'الف', password, terms: { accepted: true },
    device: { deviceId: 'dev-vl', name: 'pc', platform: 'test' },
  });
  assert.ok(done.status < 300, JSON.stringify(done.body));

  //  دو بار «کد را دوباره بفرست» (۲ و ۳) و یک کدِ اشتباه — بعد کدِ درست
  let code = '';
  for (let i = 0; i < 2; i++) {
    const f = await h.post('/api/auth/password/forgot', { email });
    assert.equal(f.status, 200, JSON.stringify(f.body));
    code = f.body.devCode;
  }
  const wrong = await h.post('/api/auth/password/reset', { email, code: '000000', password: 'TazeRamz!9876' });
  assert.notEqual(wrong.status, 429, 'کدِ اشتباه نباید سقفِ نرخ را بخورد');
  const ok = await h.post('/api/auth/password/reset', { email, code, password: 'TazeRamz!9876' });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.accessToken, 'پس از رمزِ تازه، وارد شد');
});

test('⛔ ولی فرستادنِ ایمیل همچنان سقفِ پنج‌تایی دارد', async () => {
  const email = 'verify-limit-2@example.com';
  const codes = [];
  for (let i = 0; i < 6; i++) codes.push((await h.post('/api/auth/password/forgot', { email })).status);
  assert.deepEqual(codes.slice(0, 5).every((c) => c === 200), true, JSON.stringify(codes));
  assert.equal(codes[5], 429, 'ششمین ایمیل در ربع ساعت رد می‌شود');
});

test('⛔ و حدس زدنِ کد همچنان بسته است — هر کد سقفِ تلاشِ خودش را دارد', async () => {
  const email = 'verify-limit-3@example.com';
  const password = 'Passw0rd!test';
  const s = await h.post('/api/auth/register/start', { name: 'ب', email, password });
  const statuses = [];
  for (let i = 0; i < 7; i++) {
    statuses.push((await h.post('/api/auth/register/verify', { email, code: String(100000 + i) })).status);
  }
  const right = await h.post('/api/auth/register/verify', { email, code: s.body.devCode });
  assert.notEqual(right.status, 200, 'پس از پنج حدسِ غلط، همان کد هم دیگر پذیرفته نمی‌شود: ' + JSON.stringify(statuses));
});
