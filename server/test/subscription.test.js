'use strict';
/** اشتراک دکانی، دوره‌ی آزمایشی، ساعت سرور و پنل مدیریت. */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const { newId, now } = require('../src/db');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  assert.equal(r.status, 200);
  return r.body.token;
}

test('دکان تازه در دوره‌ی آزمایشی است و همه‌ی قابلیت‌ها را دارد', async () => {
  const u = await h.newUser('آزمایشی');
  await h.post('/api/shop', { name: 'دکان آزمایشی' }, { token: u.accessToken });
  const s = await h.get('/api/me/subscription', { token: u.accessToken });
  assert.equal(s.body.source, 'trial');
  assert.equal(s.body.trial.active, true);
  assert.ok(s.body.features.includes('sales'));
  assert.ok(s.body.serverTime > 0);
});

test('با تمام شدن دوره‌ی آزمایشی، افزودن شاگرد بسته می‌شود و با اشتراک باز', async () => {
  const owner = await h.newUser('صاحب-اشتراک');
  const staff = await h.newUser('شاگرد-اشتراک');
  const shop = (await h.post('/api/shop', { name: 'دکان اشتراک' }, { token: owner.accessToken })).body.shop;

  // دکان را به گذشته می‌بریم تا دوره‌ی آزمایشی تمام شده باشد
  await query('UPDATE shops SET created_at = $2 WHERE id = $1', [shop.id, Date.now() - 400 * DAY]);

  const denied = await h.post('/api/shop/staff-code', {}, { token: owner.accessToken });
  assert.equal(denied.status, 403);
  assert.ok(['subscription_required', 'subscription_expired'].includes(denied.body.error.code),
    `کد خطا: ${denied.body.error.code}`);

  // مدیر اشتراک می‌دهد
  const token = await adminToken();
  const granted = await h.post('/api/admin/subscriptions',
    { shopId: shop.id, plan: 'm1', days: 30, note: 'پرداخت نقدی' }, { token });
  assert.equal(granted.status, 201);
  assert.equal(granted.body.state.active, true);

  const code = await h.post('/api/shop/staff-code', {}, { token: owner.accessToken });
  assert.equal(code.status, 201);

  // شاگرد وارد می‌شود و همان اشتراک دکان را می‌بیند — بدون خرید جدا
  const joined = await h.post('/api/shop/staff/join', { code: code.body.code }, { token: staff.accessToken });
  assert.equal(joined.status, 201);
  const staffSub = await h.get('/api/me/subscription', { token: staff.accessToken });
  assert.equal(staffSub.body.source, 'subscription');
  assert.equal(staffSub.body.active, true);
  assert.equal(staffSub.body.shop.id, shop.id);
});

test('اشتراک با ساعت سرور سنجیده می‌شود، نه با تاریخِ فرستاده‌شده', async () => {
  const owner = await h.newUser('صاحب-ساعت');
  const shop = (await h.post('/api/shop', { name: 'دکان ساعت' }, { token: owner.accessToken })).body.shop;
  const token = await adminToken();
  await h.post('/api/admin/subscriptions', { shopId: shop.id, days: 10 }, { token });

  // گوشی ادعا می‌کند سال دیگر است — هیچ اثری ندارد
  const s = await h.get('/api/me/subscription', {
    token: owner.accessToken,
    headers: { 'X-Client-Time': String(Date.now() + 365 * DAY) },
  });
  assert.ok(s.body.daysLeft <= 11 && s.body.daysLeft >= 9, `daysLeft=${s.body.daysLeft}`);

  // و اشتراکی که تاریخش گذشته، خودکار expired می‌شود
  await query(`UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1`, [shop.id, Date.now() - DAY]);
  const after = await h.get('/api/me/subscription', { token: owner.accessToken });
  assert.equal(after.body.active, false);
  assert.equal(after.body.status, 'expired');
});

test('تمدید، روزهای باقی‌مانده را از بین نمی‌برد', async () => {
  const owner = await h.newUser('صاحب-تمدید');
  const shop = (await h.post('/api/shop', { name: 'دکان تمدید' }, { token: owner.accessToken })).body.shop;
  const token = await adminToken();
  const first = await h.post('/api/admin/subscriptions', { shopId: shop.id, days: 30 }, { token });
  const second = await h.post('/api/admin/subscriptions', { shopId: shop.id, days: 30 }, { token });
  const gap = second.body.subscription.ends_at - first.body.subscription.ends_at;
  assert.ok(Math.abs(gap - 30 * DAY) < 60_000, `تمدید ${gap} میلی‌ثانیه اضافه کرد`);
});

test('مدیر می‌تواند اشتراک را معلق و دوباره فعال کند', async () => {
  const owner = await h.newUser('صاحب-تعلیق');
  const shop = (await h.post('/api/shop', { name: 'دکان تعلیق' }, { token: owner.accessToken })).body.shop;
  const token = await adminToken();
  const sub = (await h.post('/api/admin/subscriptions', { shopId: shop.id, days: 30 }, { token })).body.subscription;

  await h.post(`/api/admin/subscriptions/${sub.id}/status`, { status: 'suspended' }, { token });
  let s = await h.get('/api/me/subscription', { token: owner.accessToken });
  assert.equal(s.body.active, false);
  assert.equal(s.body.status, 'suspended');

  await h.post(`/api/admin/subscriptions/${sub.id}/status`, { status: 'active' }, { token });
  s = await h.get('/api/me/subscription', { token: owner.accessToken });
  assert.equal(s.body.active, true);
});

test('پنل مدیریت کاربران، دکان‌ها و اعضا را می‌بیند', async () => {
  const token = await adminToken();
  const users = await h.get('/api/admin/users?limit=5', { token });
  assert.equal(users.status, 200);
  assert.ok(users.body.total > 0);

  const shops = await h.get('/api/admin/shops', { token });
  assert.ok(shops.body.shops.length > 0);
  const one = await h.get(`/api/admin/shops/${shops.body.shops[0].id}`, { token });
  assert.equal(one.status, 200);
  assert.ok(Array.isArray(one.body.members));
  assert.ok(one.body.entitlement);
});

test('کاربر عادی به مسیرهای مدیریت نمی‌رسد و برعکس', async () => {
  const u = await h.newUser('کنجکاو');
  assert.equal((await h.get('/api/admin/users', { token: u.accessToken })).status, 401);

  const token = await adminToken();
  assert.equal((await h.get('/api/me', { token })).status, 401);
});

test('غیرفعال کردن کاربر توسط مدیر، نشست او را می‌بندد', async () => {
  const u = await h.newUser('مسدود');
  const token = await adminToken();
  const r = await h.post(`/api/admin/users/${u.user.id}/status`, { status: 'disabled' }, { token });
  assert.equal(r.status, 200);
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 401);
});
