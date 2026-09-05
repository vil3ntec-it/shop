'use strict';
/**
 * بازدیدکننده‌ها، تخفیف، برنامه‌های دیگر و تنظیمات ایمیل.
 *
 * چیزی که باید ثابت شود: مهمانِ بی‌حساب هم شمرده می‌شود و لوکیشنش
 * دیده می‌شود؛ تخفیف روی قیمت می‌نشیند بی‌آنکه قیمت اصلی گم شود؛
 * برنامه‌های دیگر از همین‌جا اداره می‌شوند؛ و رمزِ ایمیل هرگز بیرون
 * نمی‌رود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

/* ---------------------------- بازدیدکننده‌ها ---------------------------- */

test('مهمانِ بی‌حساب شمرده می‌شود و مدیر می‌بیندش', async () => {
  const ping = await h.post('/api/visit', {
    deviceUid: 'visitor-1', platform: 'web', language: 'fa',
    location: { lat: 34.5553, lng: 69.2075, accuracy: 30, source: 'gps' },
  });
  assert.equal(ping.status, 200);

  const t = await adminToken();
  const seen = await h.get('/api/admin/visitors?guests=1', { token: t });
  const row = seen.body.visitors.find(v => v.deviceUid === 'visitor-1');
  assert.ok(row, 'مهمان در فهرست نیست');
  assert.equal(row.guest, true);
  assert.equal(row.platform, 'web');
  assert.ok(row.location, 'لوکیشن ثبت نشد');
  assert.equal(Math.round(row.location.lat * 100), 3456);
  assert.ok(seen.body.summary.guests >= 1);
});

test('همان دستگاه دو بار، دو ردیف نمی‌سازد', async () => {
  await h.post('/api/visit', { deviceUid: 'visitor-2', platform: 'android' });
  await h.post('/api/visit', { deviceUid: 'visitor-2', platform: 'android' });

  const t = await adminToken();
  const seen = await h.get('/api/admin/visitors', { token: t });
  const rows = seen.body.visitors.filter(v => v.deviceUid === 'visitor-2');
  assert.equal(rows.length, 1);
  //  دو تپشِ پشت‌سرهم یک بازدید است، نه دو تا
  assert.equal(rows[0].visits, 1);
});

test('مهمانی که حساب می‌سازد، دیگر مهمان شمرده نمی‌شود', async () => {
  await h.post('/api/visit', { deviceUid: 'visitor-3', platform: 'web' });
  const u = await h.newUser('ثبت‌نامی');
  await h.post('/api/visit', { deviceUid: 'visitor-3', platform: 'web' }, { token: u.accessToken });

  const t = await adminToken();
  const seen = await h.get('/api/admin/visitors', { token: t });
  const row = seen.body.visitors.find(v => v.deviceUid === 'visitor-3');
  assert.equal(row.guest, false);
  assert.equal(row.userId, u.user.id);
  assert.equal(row.accountName, 'ثبت‌نامی');
});

test('لوکیشنِ حساب، در صفحه‌ی همان کاربر در پنل دیده می‌شود', async () => {
  const u = await h.newUser('لوکیشن‌دار');
  await h.post('/api/visit', {
    deviceUid: 'located-device', platform: 'android',
    location: { lat: 31.6289, lng: 65.7372, accuracy: 12, source: 'gps' },
  }, { token: u.accessToken });

  const t = await adminToken();
  const page = await h.get(`/api/admin/users/${u.user.id}`, { token: t });
  assert.equal(page.status, 200);
  assert.ok(page.body.locations.length >= 1, 'لوکیشنی برای این حساب نیست');
  assert.equal(Math.round(page.body.locations[0].lat * 100), 3163);
});

test('تپش بدون شناسه‌ی دستگاه چیزی نمی‌شکند', async () => {
  const out = await h.post('/api/visit', { platform: 'web' });
  assert.equal(out.status, 200);
  assert.equal(out.body.ok, true);
});

test('کاربر عادی به فهرست بازدیدکننده‌ها نمی‌رسد', async () => {
  const u = await h.newUser('کنجکاو');
  assert.equal((await h.get('/api/admin/visitors', { token: u.accessToken })).status, 401);
});

/* ------------------------------- تخفیف ------------------------------- */

test('تخفیف درصدی روی قیمت می‌نشیند و قیمت اصلی گم نمی‌شود', async () => {
  const t = await adminToken();
  const before = await h.get('/api/me/plans', { token: (await h.newUser('خریدار')).accessToken });
  const m1 = before.body.plans.find(p => p.code === 'm1');
  const fullPrice = m1.price;

  const set = await h.put('/api/admin/plans/m1/discount',
    { percent: 20, label: 'جشنواره' }, { token: t });
  assert.equal(set.status, 200);

  const u = await h.newUser('خریدار۲');
  const after = await h.get('/api/me/plans', { token: u.accessToken });
  const p = after.body.plans.find(x => x.code === 'm1');
  assert.equal(p.fullPrice, fullPrice);
  assert.equal(p.price, Math.round(fullPrice * 0.8));
  assert.equal(p.discount.percent, 20);
  assert.equal(p.discount.label, 'جشنواره');
  assert.equal(p.discount.savings, fullPrice - p.price);
});

test('تخفیف با قیمتِ ثابت هم می‌شود، و برداشتنش قیمت را برمی‌گرداند', async () => {
  const t = await adminToken();
  const u = await h.newUser('خریدار۳');
  const base = (await h.get('/api/me/plans', { token: u.accessToken })).body.plans.find(p => p.code === 'y1');

  await h.put('/api/admin/plans/y1/discount', { price: 1500 }, { token: t });
  const withDiscount = (await h.get('/api/me/plans', { token: u.accessToken })).body.plans.find(p => p.code === 'y1');
  assert.equal(withDiscount.price, 1500);
  assert.equal(withDiscount.fullPrice, base.fullPrice);

  await h.del('/api/admin/plans/y1/discount', { token: t });
  const cleared = (await h.get('/api/me/plans', { token: u.accessToken })).body.plans.find(p => p.code === 'y1');
  assert.equal(cleared.price, base.fullPrice);
  assert.equal(cleared.discount, null);
});

test('تخفیفی که مهلتش گذشته، اثر ندارد', async () => {
  const t = await adminToken();
  //  مستقیم در دیتابیس، چون مسیر مدیریت عمداً تاریخ گذشته را رد می‌کند
  await query(
    `UPDATE plans SET discount_percent=50, discount_until=$1 WHERE code='m6'`,
    [now() - 1000]
  );
  const u = await h.newUser('خریدار۴');
  const p = (await h.get('/api/me/plans', { token: u.accessToken })).body.plans.find(x => x.code === 'm6');
  assert.equal(p.discount, null);
  assert.equal(p.price, p.fullPrice);
});

test('تخفیفِ بی‌معنی پذیرفته نمی‌شود', async () => {
  const t = await adminToken();
  const plan = (await h.get('/api/admin/plans', { token: t })).body.plans.find(p => p.code === 'm1');
  const tooHigh = await h.put('/api/admin/plans/m1/discount',
    { price: plan.fullPrice + 100 }, { token: t });
  assert.equal(tooHigh.status, 400);

  const past = await h.put('/api/admin/plans/m1/discount',
    { percent: 10, until: now() - 5000 }, { token: t });
  assert.equal(past.status, 400);
});

/* --------------------------- برنامه‌های دیگر --------------------------- */

test('مدیر برنامه‌ی تازه اضافه می‌کند و می‌بیندش', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/apps',
    { slug: 'my-site', title: 'سایت شخصی', kind: 'site', url: 'https://example.com' },
    { token: t });
  assert.equal(made.status, 201);
  assert.equal(made.body.app.slug, 'my-site');

  const list = await h.get('/api/admin/apps', { token: t });
  //  فروشگاه و پنل از همان مهاجرت آنجا هستند
  assert.ok(list.body.apps.some(a => a.slug === 'shop'));
  assert.ok(list.body.apps.some(a => a.slug === 'my-site'));
});

test('بازدیدِ یک برنامه‌ی دیگر، زیر همان برنامه شمرده می‌شود', async () => {
  const t = await adminToken();
  await h.post('/api/admin/apps', { slug: 'other-app', title: 'برنامه‌ی دوم' }, { token: t });
  await h.post('/api/visit', { app: 'other-app', deviceUid: 'other-1', platform: 'ios' });

  const list = await h.get('/api/admin/apps', { token: t });
  const app = list.body.apps.find(a => a.slug === 'other-app');
  assert.equal(app.visitors, 1);
  assert.equal(app.guests, 1);

  //  و در فهرست بازدیدکننده‌های فروشگاه نیست
  const shopOnly = await h.get('/api/admin/visitors?app=shop', { token: t });
  assert.ok(!shopOnly.body.visitors.some(v => v.deviceUid === 'other-1'));
});

test('نام کوتاهِ تکراری و نامِ خراب رد می‌شوند', async () => {
  const t = await adminToken();
  await h.post('/api/admin/apps', { slug: 'dup-app', title: 'یکی' }, { token: t });
  const again = await h.post('/api/admin/apps', { slug: 'dup-app', title: 'دوباره' }, { token: t });
  assert.equal(again.status, 409);
  assert.equal((await h.post('/api/admin/apps', { slug: 'x' }, { token: t })).status, 400);
});

test('کلید برنامه فقط یک بار دیده می‌شود', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/apps', { slug: 'keyed-app', title: 'کلیددار' }, { token: t });
  const keyed = await h.post(`/api/admin/apps/${made.body.app.id}/key`, {}, { token: t });
  assert.equal(keyed.status, 200);
  assert.ok(keyed.body.key.startsWith('ak_'));

  const list = await h.get('/api/admin/apps', { token: t });
  const app = list.body.apps.find(a => a.slug === 'keyed-app');
  assert.equal(app.keySet, true);
  assert.ok(!JSON.stringify(app).includes(keyed.body.key));
});

test('برنامه پاک نمی‌شود، بایگانی می‌شود', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/apps', { slug: 'gone-app', title: 'رفتنی' }, { token: t });
  await h.del(`/api/admin/apps/${made.body.app.id}`, { token: t });

  const normal = await h.get('/api/admin/apps', { token: t });
  assert.ok(!normal.body.apps.some(a => a.slug === 'gone-app'));
  const all = await h.get('/api/admin/apps?archived=1', { token: t });
  assert.ok(all.body.apps.some(a => a.slug === 'gone-app'));
});

/* --------------------------- تنظیمات ایمیل --------------------------- */

test('مدیر ایمیل را تنظیم می‌کند و رمز هرگز برنمی‌گردد', async () => {
  const t = await adminToken();
  const saved = await h.put('/api/admin/email', {
    provider: 'smtp', host: 'smtp.example.com', port: 587, secure: 'starttls',
    user: 'me@example.com', pass: 'super-secret', from: 'me@example.com', fromName: 'توحید',
  }, { token: t });
  assert.equal(saved.status, 200);
  assert.equal(saved.body.email.host, 'smtp.example.com');
  assert.equal(saved.body.email.passSet, true);
  assert.ok(!JSON.stringify(saved.body).includes('super-secret'));
  assert.equal(saved.body.email.ready, true);

  //  ذخیره‌ی بعدی بدون رمز، رمز قبلی را پاک نمی‌کند
  await h.put('/api/admin/email', { fromName: 'فروشگاه توحید' }, { token: t });
  const after = await h.get('/api/admin/email', { token: t });
  assert.equal(after.body.email.passSet, true);
  assert.equal(after.body.email.fromName, 'فروشگاه توحید');
});

test('تنظیمات ناقص، «آماده» شمرده نمی‌شود و می‌گوید چه کم است', async () => {
  const t = await adminToken();
  await h.put('/api/admin/email', { clearPass: true }, { token: t });
  const out = await h.get('/api/admin/email', { token: t });
  assert.equal(out.body.email.ready, false);
  assert.ok(out.body.email.missing.includes('رمز'));
});

test('ایمیلِ خراب، ثبت‌نام را با «خطای داخلی» نمی‌شکند', async () => {
  const t = await adminToken();
  //  تنظیماتی که به جایی نمی‌رسد — همان حالتی که تا امروز ۵۰۰ می‌داد
  await h.put('/api/admin/email', {
    provider: 'smtp', host: '127.0.0.1', port: 1, secure: 'none',
    user: 'x@example.com', pass: 'x', from: 'x@example.com',
  }, { token: t });

  const out = await h.post('/api/auth/register/start',
    { name: 'ناکام', email: 'nowhere@test.local', password: 'Passw0rd!test' });
  assert.equal(out.status, 502);
  assert.equal(out.body.error.code, 'delivery_failed');
  assert.ok(out.body.error.message.includes('ایمیل'));

  //  کدِ ناموفق پاک شده، پس همان لحظه می‌شود دوباره تلاش کرد
  const left = await one(
    `SELECT COUNT(*)::int n FROM otp_codes WHERE destination='nowhere@test.local'`);
  assert.equal(left.n, 0);

  //  و راهِ برگشت: تنظیمات را به `log` برگردان، ثبت‌نام دوباره کار کند
  await h.put('/api/admin/email', { provider: 'log' }, { token: t });
  const again = await h.post('/api/auth/register/start',
    { name: 'موفق', email: 'nowhere2@test.local', password: 'Passw0rd!test' });
  assert.equal(again.status, 201);
});

test('کاربر عادی به تنظیمات ایمیل نمی‌رسد', async () => {
  const u = await h.newUser('کنجکاو۲');
  assert.equal((await h.get('/api/admin/email', { token: u.accessToken })).status, 401);
  assert.equal((await h.put('/api/admin/email', { host: 'evil' }, { token: u.accessToken })).status, 401);
});

/* ------------------------------ خلاصه ------------------------------ */

test('خلاصه‌ی خانه، همه‌چیز را در یک درخواست می‌دهد', async () => {
  const t = await adminToken();
  const out = await h.get('/api/admin/overview', { token: t });
  assert.equal(out.status, 200);
  for (const key of ['expiring', 'supportUnread', 'visitors', 'apps', 'email', 'push', 'vipCodesActive']) {
    assert.ok(key in out.body, `«${key}» در خلاصه نیست`);
  }
  assert.ok(Array.isArray(out.body.apps));
});

test('قیمت‌ها بی‌نیاز به ورود دیده می‌شوند — با تخفیف و لینک واتساپ', async () => {
  const t = await adminToken();
  await h.put('/api/admin/plans/m1/discount', { percent: 25, label: 'عید' }, { token: t });

  //  هیچ توکنی — همان کاری که سایت می‌کند
  const out = await h.get('/api/plans');
  assert.equal(out.status, 200);
  const p = out.body.plans.find(x => x.code === 'm1');
  assert.ok(p, 'پلنی برنگشت');
  assert.equal(p.discount.percent, 25);
  assert.equal(p.price, Math.round(p.fullPrice * 0.75));
  assert.ok(p.pricePerDay > 0, 'قیمت روزانه حساب نشد');
  assert.ok(p.whatsappUrl.startsWith('https://wa.me/'), 'لینک واتساپ ساخته نشد');
  assert.ok(out.body.whatsapp.url.startsWith('https://wa.me/'));
  assert.equal(typeof out.body.currency, 'string');

  await h.del('/api/admin/plans/m1/discount', { token: t });
});

test('سرور در /config می‌گوید کدام قابلیت‌ها را دارد', async () => {
  const cfg = await h.get('/api/config');
  assert.equal(cfg.body.support, true);
  assert.equal(cfg.body.vipCodes, true);
  assert.equal(cfg.body.visitPing, true);
});
