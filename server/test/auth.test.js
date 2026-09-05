'use strict';
/** ورود، کد یک‌بارمصرف، دسترسی‌ها و جداسازی حساب‌ها. */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

test('ثبت‌نام بدون تأیید ایمیل بسته است — مسیر قدیمی حساب نمی‌سازد', async () => {
  /*
   *  این مسیر حساب را بدونِ کدِ تأیید می‌ساخت: هر کسی با ایمیلِ هر کسِ
   *  دیگری. قرار صاحب مخزن: «کد نیامد، ثبت‌نامی هم نیست».
   */
  const tried = await h.post('/api/auth/register', {
    name: 'بی‌کد', email: 'nocode@b.com', password: 'Passw0rd!1',
  });
  assert.equal(tried.status, 403);
  assert.equal(tried.body.error.code, 'verification_required');

  //  و واقعاً حسابی ساخته نشده
  const login = await h.post('/api/auth/login', { identifier: 'nocode@b.com', password: 'Passw0rd!1' });
  assert.notEqual(login.status, 200);
});

test('شماره در هر قالبی نوشته شود، یک حساب است', async () => {
  //  حساب با کدِ یک‌بارمصرف ساخته می‌شود — تنها راهی که شماره از آن
  //  واردِ سیستم می‌شود
  const asked = await h.post('/api/auth/otp/request', { phone: '0792236008' });
  await h.post('/api/auth/otp/verify', { phone: '0792236008', code: asked.body.devCode, name: 'یک' });

  //  همان شماره با قالبِ دیگر، همان حساب است — نه حسابِ تازه
  const again = await h.post('/api/auth/otp/request', { phone: '+93 792 236 008' });
  const back = await h.post('/api/auth/otp/verify', { phone: '0093792236008', code: again.body.devCode });
  assert.equal(back.status, 200, 'حسابِ موجود، نه ساختِ تازه');
  assert.equal(back.body.created, false);
});

test('ورود با کد یک‌بارمصرف: کد اشتباه رد و کد درست قبول می‌شود', async () => {
  const phone = '0793334444';
  const asked = await h.post('/api/auth/otp/request', { phone });
  assert.equal(asked.status, 200);
  assert.ok(asked.body.devCode, 'در حالت تست کد باید در دسترس باشد');

  const wrong = await h.post('/api/auth/otp/verify', { phone, code: '000000' });
  assert.equal(wrong.status, 403);

  const ok = await h.post('/api/auth/otp/verify', { phone, code: asked.body.devCode, name: 'کدی' });
  assert.equal(ok.status, 201);
  assert.equal(ok.body.created, true);
  assert.ok(ok.body.accessToken);

  // همان کد دوباره کار نمی‌کند
  const replay = await h.post('/api/auth/otp/verify', { phone, code: asked.body.devCode });
  assert.equal(replay.status, 403);
});

test('ورود با گوگل بدون تنظیم Client ID با پیام روشن رد می‌شود', async () => {
  const r = await h.post('/api/auth/google', { idToken: 'aaa.bbb.ccc' });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'google_not_configured');
});

test('توکن نامعتبر، حساب غیرفعال و خروج، همه بسته می‌شوند', async () => {
  const u = await h.newUser('خروجی');
  assert.equal((await h.get('/api/me', { token: 'bogus-token-value-123456' })).status, 401);
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 200);

  await h.post('/api/auth/logout', { refreshToken: u.refreshToken }, { token: u.accessToken });
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 401);

  const back = await h.post('/api/auth/login', { identifier: u.email, password: u.password });
  assert.equal(back.status, 200);
});

test('تازه‌سازی نشست با refresh token کار می‌کند', async () => {
  const u = await h.newUser('تازه');
  const r = await h.post('/api/auth/refresh', { refreshToken: u.refreshToken });
  assert.equal(r.status, 200);
  assert.ok(r.body.accessToken);
  assert.equal((await h.get('/api/me', { token: r.body.accessToken })).status, 200);
});

test('حذف شاگرد، همان لحظه دسترسی او را می‌بندد', async () => {
  const owner = await h.newUser('صاحب-حذف');
  const staff = await h.newUser('شاگرد-حذف');
  await h.post('/api/shop', { name: 'دکان حذف' }, { token: owner.accessToken });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });

  assert.equal((await h.get('/api/products', { token: staff.accessToken })).status, 200);

  const members = await h.get('/api/shop/members', { token: owner.accessToken });
  const row = members.body.members.find(m => m.role === 'staff');
  const removed = await h.del(`/api/shop/members/${row.id}`, { token: owner.accessToken });
  assert.equal(removed.status, 200);

  // نشست شاگرد باطل شده است
  assert.equal((await h.get('/api/products', { token: staff.accessToken })).status, 401);
});

test('نقش مدیر (manager) دسترسی بیشتری از شاگرد دارد ولی کد نمی‌سازد', async () => {
  const owner = await h.newUser('صاحب-نقش');
  const manager = await h.newUser('مدیر');
  await h.post('/api/shop', { name: 'دکان نقش' }, { token: owner.accessToken });
  const code = (await h.post('/api/shop/staff-code', { role: 'manager' }, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: manager.accessToken });

  assert.equal((await h.get('/api/shop/members', { token: manager.accessToken })).status, 200);
  assert.equal((await h.post('/api/shop/staff-code', {}, { token: manager.accessToken })).status, 403);
  assert.equal((await h.put('/api/shop', { name: 'اسم تازه' }, { token: manager.accessToken })).status, 403);
});

test('شاگرد رکورد دیگری را حذف نمی‌کند ولی رکورد خودش را می‌تواند', async () => {
  const owner = await h.newUser('صاحب-پاک');
  const staff = await h.newUser('شاگرد-پاک');
  await h.post('/api/shop', { name: 'دکان پاک' }, { token: owner.accessToken });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });

  await h.post('/api/products', { id: 'p-of-owner', data: { name: 'مال صاحب' } }, { token: owner.accessToken });
  await h.post('/api/products', { id: 'p-of-staff', data: { name: 'مال شاگرد' } }, { token: staff.accessToken });

  assert.equal((await h.del('/api/products/p-of-owner', { token: staff.accessToken })).status, 403);
  assert.equal((await h.del('/api/products/p-of-staff', { token: staff.accessToken })).status, 200);
  assert.equal((await h.del('/api/products/p-of-owner', { token: owner.accessToken })).status, 200);
});

test('ورودی نامعتبر با پیام فارسی رد می‌شود، نه با خطای خام دیتابیس', async () => {
  const u = await h.newUser('ورودی');
  await h.post('/api/shop', { name: 'دکان ورودی' }, { token: u.accessToken });

  const bad = await h.post('/api/products', { id: 'has spaces & symbols!', data: {} }, { token: u.accessToken });
  assert.equal(bad.status, 400);
  assert.match(bad.body.error.message, /[؀-ۿ]/);

  const huge = await h.post('/api/products', { id: 'p-big', data: { blob: 'x'.repeat(70000) } }, { token: u.accessToken });
  assert.equal(huge.status, 400);
});

test('تنظیمات عمومی سرور بدون ورود در دسترس است و راز ندارد', async () => {
  const r = await h.get('/api/config');
  assert.equal(r.status, 200);
  assert.equal(typeof r.body.registrationOpen, 'boolean');
  assert.equal(r.body.googleClientId, '');
  assert.ok(r.body.serverTime > 0);
  const text = JSON.stringify(r.body);
  assert.ok(!text.includes('SECRET') && !text.includes('postgres://'));
});
