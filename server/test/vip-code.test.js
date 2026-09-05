'use strict';
/**
 * کد وی‌آی‌پی — از ساختنش در پنل تا فعال شدن اشتراک در برنامه.
 *
 * چیزی که باید ثابت شود: مدیر کد می‌سازد، سرور ایمیلش را می‌فرستد،
 * کاربر خرجش می‌کند و اشتراکش واقعاً فعال می‌شود؛ و همان کد دوباره کار
 * نمی‌کند.
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

/** یک کاربر با دکان — چون کد وی‌آی‌پی روی دکان می‌نشیند، نه روی حساب. */
async function userWithShop(name = 'دکان‌دار') {
  const u = await h.newUser(name);
  const shop = await h.post('/api/shop', { name: `دکان ${name}` }, { token: u.accessToken });
  return { ...u, shopId: shop.body.shop.id };
}

test('مدیر کد می‌سازد و کد خام فقط همان یک بار برمی‌گردد', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30, note: 'هدیه' }, { token: t });
  assert.equal(made.status, 201);
  assert.match(String(made.body.code), /^\d{6}$/);

  //  در فهرست، فقط نشانه‌اش هست نه خودش
  const list = await h.get('/api/admin/vip-codes', { token: t });
  const row = list.body.codes.find(c => c.id === made.body.vipCode.id);
  assert.ok(row);
  assert.equal(row.status, 'active');
  assert.ok(!JSON.stringify(row).includes(made.body.code));
});

test('کاربر کد را می‌زند و اشتراک دکانش فعال می‌شود', async () => {
  const t = await adminToken();
  const u = await userWithShop('علی');

  const before = await h.get('/api/me/subscription', { token: u.accessToken });
  assert.notEqual(before.body.status, 'active');

  const made = await h.post('/api/admin/vip-codes', { plan: 'm6', days: 180 }, { token: t });
  const used = await h.post('/api/vip/redeem', { code: made.body.code }, { token: u.accessToken });
  assert.equal(used.status, 201);
  assert.equal(used.body.subscription.active, true);
  assert.ok(used.body.subscription.daysLeft >= 179);

  const after = await h.get('/api/me/subscription', { token: u.accessToken });
  assert.equal(after.body.active, true);
});

test('همان کد بار دوم کار نمی‌کند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: t });
  const a = await userWithShop('اول');
  const b = await userWithShop('دوم');

  assert.equal((await h.post('/api/vip/redeem', { code: made.body.code }, { token: a.accessToken })).status, 201);
  const second = await h.post('/api/vip/redeem', { code: made.body.code }, { token: b.accessToken });
  assert.equal(second.status, 403);
  assert.equal(second.body.error.code, 'code_used');
});

test('کد باطل‌شده کار نمی‌کند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: t });
  await h.post(`/api/admin/vip-codes/${made.body.vipCode.id}/revoke`, {}, { token: t });

  const u = await userWithShop('سوم');
  const out = await h.post('/api/vip/redeem', { code: made.body.code }, { token: u.accessToken });
  assert.equal(out.status, 403);
  assert.equal(out.body.error.code, 'code_inactive');
});

test('کدِ نادرست پذیرفته نمی‌شود و چیزی لو نمی‌دهد', async () => {
  const u = await userWithShop('چهارم');
  const bad = await h.post('/api/vip/redeem', { code: '000000' }, { token: u.accessToken });
  assert.equal(bad.status, 404);
  const short = await h.post('/api/vip/redeem', { code: '123' }, { token: u.accessToken });
  assert.equal(short.status, 400);
});

test('کدی که برای یک دکان صادر شده، به دکان دیگر نمی‌رود', async () => {
  const t = await adminToken();
  const mine = await userWithShop('صاحب');
  const other = await userWithShop('غریبه');

  const made = await h.post('/api/admin/vip-codes',
    { plan: 'm1', days: 30, shopId: mine.shopId }, { token: t });

  const wrong = await h.post('/api/vip/redeem', { code: made.body.code }, { token: other.accessToken });
  assert.equal(wrong.status, 403);
  assert.equal(wrong.body.error.code, 'code_other_shop');

  const right = await h.post('/api/vip/redeem', { code: made.body.code }, { token: mine.accessToken });
  assert.equal(right.status, 201);
});

test('کد بدون دکان خرج نمی‌شود و هدر هم نمی‌رود', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: t });
  const u = await h.newUser('بی‌دکان');

  const out = await h.post('/api/vip/redeem', { code: made.body.code }, { token: u.accessToken });
  assert.equal(out.status, 403);   // requireShop جلویش را می‌گیرد

  //  کد باید هنوز زنده باشد
  const row = await one('SELECT status FROM vip_codes WHERE id=$1', [made.body.vipCode.id]);
  assert.equal(row.status, 'active');
});

test('ایمیل کد، وقتی ایمیل داده شود، ثبت می‌شود', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/vip-codes',
    { plan: 'm1', days: 30, email: 'someone@example.com' }, { token: t });
  assert.equal(made.status, 201);
  //  در تست، راه ارسال `log` است؛ پس «فرستاده شد» درست است
  assert.equal(made.body.emailStatus, 'sent');
  assert.equal(made.body.vipCode.email, 'someone@example.com');
});

test('کاربر عادی نمی‌تواند کد بسازد', async () => {
  const u = await h.newUser('کنجکاو');
  const out = await h.post('/api/admin/vip-codes', { plan: 'm1', days: 30 }, { token: u.accessToken });
  assert.equal(out.status, 401);
  assert.equal((await h.get('/api/admin/vip-codes', { token: u.accessToken })).status, 401);
});
