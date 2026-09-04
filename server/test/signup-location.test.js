'use strict';
/**
 * ثبت‌نام سه‌مرحله‌ای با ایمیل، و لوکیشن.
 *
 * قرار صاحب مخزن: شماره‌ی موبایل از ثبت‌نام برداشته شد؛ سه پله است —
 * نام و ایمیل و رمز، بعد کد شش‌رقمیِ همان ایمیل، بعد لوکیشن و پذیرش
 * شرایط و ضوابط. و لوکیشن باید حتی پیش از ثبت‌نام هم به سرور برسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const PASS = 'Passw0rd!test';

/** پله‌ی یک و دو، تا رسیدن به بلیت. */
async function ticketFor(email, device) {
  const start = await h.post('/api/auth/register/start', { name: 'صاحب دکان', email, password: PASS, passwordConfirm: PASS });
  assert.equal(start.status, 201, JSON.stringify(start.body));
  assert.ok(start.body.devCode, 'در حالت آزمایش کد در پاسخ می‌آید');

  const verify = await h.post('/api/auth/register/verify', { email, code: start.body.devCode, device });
  assert.equal(verify.status, 200, JSON.stringify(verify.body));
  assert.ok(verify.body.ticket);
  return verify.body.ticket;
}

test('سه پله تا ساخته شدن حساب — و لوکیشن و شرایط با آن ثبت می‌شود', async () => {
  const email = 'signup1@example.com';
  const device = { uid: 'dev-signup-1', name: 'تست', platform: 'test' };
  const ticket = await ticketFor(email, device);

  const done = await h.post('/api/auth/register/complete', {
    ticket, name: 'صاحب دکان', password: PASS, passwordConfirm: PASS,
    terms: { accepted: true, version: '1' },
    location: { lat: 34.5553, lng: 69.2075, accuracy: 12, source: 'gps' },
    device,
  });
  assert.equal(done.status, 201, JSON.stringify(done.body));
  assert.ok(done.body.accessToken, 'همان‌جا وارد می‌شود');
  assert.equal(done.body.user.email, email);
  assert.equal(done.body.user.phone, null, 'شماره‌ای در کار نیست');
  assert.ok(done.body.location, 'لوکیشن ثبت شد');

  //  رمزی که در پله‌ی یک زده شده بود، همان است که کار می‌کند
  const login = await h.post('/api/auth/login', { identifier: email, password: PASS });
  assert.equal(login.status, 200);

  //  و لوکیشن روی همان حساب دیده می‌شود
  const mine = await h.get('/api/location/mine', { token: done.body.accessToken });
  assert.equal(mine.status, 200);
  assert.equal(mine.body.locations.length, 1);
  assert.equal(Math.round(mine.body.locations[0].lat * 1e4), 345553);
});

test('بدون پذیرش شرایط، حسابی ساخته نمی‌شود', async () => {
  const email = 'signup2@example.com';
  const ticket = await ticketFor(email, { uid: 'dev-signup-2' });

  const done = await h.post('/api/auth/register/complete', {
    ticket, name: 'کاربر', password: PASS, terms: { accepted: false },
  });
  assert.equal(done.status, 400);
  assert.equal(done.body.error.code, 'terms_required');

  //  هیچ حسابی نمانده باشد — ایمیل باید هنوز آزاد باشد
  const again = await h.post('/api/auth/register/start', { name: 'کاربر', email, password: PASS });
  assert.equal(again.status, 201);
});

test('کد اشتباه بلیت نمی‌دهد و بلیت یک‌بار مصرف است', async () => {
  const email = 'signup3@example.com';
  const start = await h.post('/api/auth/register/start', { name: 'کاربر', email, password: PASS });
  assert.equal(start.status, 201);

  const wrong = await h.post('/api/auth/register/verify', { email, code: '000000' });
  assert.equal(wrong.status, 403);

  const ticket = await ticketFor('signup3b@example.com', { uid: 'dev-signup-3' });
  const body = {
    ticket, name: 'کاربر', password: PASS, terms: { accepted: true },
    device: { uid: 'dev-signup-3' },
  };
  assert.equal((await h.post('/api/auth/register/complete', body)).status, 201);
  //  همان بلیت، بار دوم
  const twice = await h.post('/api/auth/register/complete', body);
  assert.equal(twice.status, 401);
  assert.equal(twice.body.error.code, 'register_ticket_invalid');
});

test('ثبت‌نام بدون لوکیشن هم تمام می‌شود', async () => {
  const email = 'signup4@example.com';
  const ticket = await ticketFor(email, { uid: 'dev-signup-4' });
  const done = await h.post('/api/auth/register/complete', {
    ticket, name: 'کاربر', password: PASS, terms: { accepted: true },
    device: { uid: 'dev-signup-4' },
  });
  assert.equal(done.status, 201, JSON.stringify(done.body));
  assert.equal(done.body.location, null);
});

test('ایمیلی که از قبل حساب دارد، در پله‌ی اول رد می‌شود', async () => {
  const email = 'signup5@example.com';
  const ticket = await ticketFor(email, { uid: 'dev-signup-5' });
  await h.post('/api/auth/register/complete', {
    ticket, name: 'کاربر', password: PASS, terms: { accepted: true }, device: { uid: 'dev-signup-5' },
  });

  const again = await h.post('/api/auth/register/start', { name: 'کاربر', email, password: PASS });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'already_registered');
});

test('رمز و تکرارش که یکی نباشد، همان پله‌ی اول رد می‌شود', async () => {
  const r = await h.post('/api/auth/register/start', {
    name: 'کاربر', email: 'signup6@example.com', password: PASS, passwordConfirm: 'DigarChiz!123',
  });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'password_mismatch');
});

test('لوکیشن پیش از ثبت‌نام هم ثبت می‌شود و بعد به حساب می‌چسبد', async () => {
  const device = { uid: 'dev-anon-7', name: 'تست', platform: 'test' };

  //  هنوز هیچ حسابی در کار نیست
  const anon = await h.post('/api/location', {
    device, location: { lat: 34.51, lng: 69.18, accuracy: 30, source: 'startup' },
  });
  assert.equal(anon.status, 201, JSON.stringify(anon.body));
  assert.equal(anon.body.linked, false, 'بی‌حساب ثبت شد');

  const email = 'signup7@example.com';
  const ticket = await ticketFor(email, device);
  const done = await h.post('/api/auth/register/complete', {
    ticket, name: 'کاربر', password: PASS, terms: { accepted: true },
    location: { lat: 34.52, lng: 69.19, source: 'signup' }, device,
  });
  assert.equal(done.status, 201);

  //  ردیفِ بی‌نامِ قبلی هم مال همین حساب شده
  const mine = await h.get('/api/location/mine', { token: done.body.accessToken });
  assert.equal(mine.body.locations.length, 2);
});

test('لوکیشن بی‌معنی رد می‌شود', async () => {
  const r = await h.post('/api/location', {
    device: { uid: 'dev-bad-8' }, location: { lat: 999, lng: 0 },
  });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'bad_location');
});

test('متن شرایط و ضوابط از سرور خوانده می‌شود', async () => {
  const r = await h.get('/api/terms');
  assert.equal(r.status, 200);
  assert.ok(r.body.version);
  assert.ok(r.body.sections.length >= 9, 'همه‌ی بندها هست');
  assert.ok(r.body.sections.some(s => s.title.includes('پشتیبانی')));
});

/*
 *  هر دو نشانی — `/api/…` و `/api/v1/…` — باید کار کنند.
 *
 *  برنامه‌ی وب و اندروید همه‌ی درخواست‌هایشان را به `/api/v1` می‌زنند، ولی
 *  `/api` اول سوار شده بود و `/api/v1/*` را هم می‌قاپید؛ داخلش مسیری به
 *  نام `/v1/…` نبود، پس به لایه‌ی داده می‌رسید و ۴۰۱ می‌گرفت. یعنی هیچ
 *  برنامه‌ای نمی‌توانست وارد شود.
 */
test('نشانی /api/v1 هم مثل /api کار می‌کند', async () => {
  assert.equal((await h.get('/api/v1/health')).status, 200);
  assert.equal((await h.get('/api/v1/config')).status, 200);
  assert.equal((await h.get('/api/v1/terms')).status, 200);

  const r = await h.post('/api/v1/auth/register/start', {
    name: 'کاربر', email: 'v1@example.com', password: 'Passw0rd!test',
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
});
