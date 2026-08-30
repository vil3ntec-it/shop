'use strict';
/**
 * کد شاگردِ ثابت، تاریخچه‌ی اشتراک، و کد ورود با ایمیل.
 *
 * سه چیزی که تازه‌اند و باید همان‌طور بمانند: کد دکان با هر بار پرسیدن
 * عوض نشود، تمدید ردّ خودش را بگذارد، و کد ورود برای ایمیل هم برود.
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

test('کد ثابت دکان با هر بار پرسیدن همان می‌ماند', async () => {
  const u = await h.newUser('صاحب دکان');
  await h.post('/api/shop', { name: 'دکان کد' }, { token: u.accessToken });

  const first = await h.get('/api/shop/staff-code', { token: u.accessToken });
  assert.equal(first.status, 200);
  assert.match(first.body.code, /^SHG(-[A-Z0-9]{4}){3}$/);

  const second = await h.get('/api/shop/staff-code', { token: u.accessToken });
  assert.equal(second.body.code, first.body.code, 'کد نباید عوض شود');
});

test('کد ثابت بی‌شمار شاگرد می‌گیرد، نه فقط یکی', async () => {
  const owner = await h.newUser('صاحب');
  await h.post('/api/shop', { name: 'دکان دو نفره' }, { token: owner.accessToken });
  const { body } = await h.get('/api/shop/staff-code', { token: owner.accessToken });

  for (const name of ['شاگرد یک', 'شاگرد دو']) {
    const staff = await h.newUser(name);
    const join = await h.post('/api/shop/join', { code: body.code }, { token: staff.accessToken });
    assert.ok([200, 201].includes(join.status), `${name} باید بتواند با همان کد بپیوندد`);
  }
});

test('عوض کردن کد، کد قبلی را می‌کشد ولی شاگردها را بیرون نمی‌کند', async () => {
  const owner = await h.newUser('صاحب سوم');
  await h.post('/api/shop', { name: 'دکان سوم' }, { token: owner.accessToken });
  const old = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;

  const staff = await h.newUser('شاگرد قدیمی');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: old }, { token: staff.accessToken })).status));

  const rotated = await h.post('/api/shop/staff-code/rotate', {}, { token: owner.accessToken });
  assert.equal(rotated.status, 200);
  assert.notEqual(rotated.body.code, old, 'کد تازه باید فرق کند');

  const late = await h.newUser('شاگرد دیرآمده');
  const withOld = await h.post('/api/shop/join', { code: old }, { token: late.accessToken });
  assert.ok(withOld.status >= 400, 'کد قدیمی نباید دیگر کار کند');

  // شاگرد قبلی سر جایش است
  const members = await h.get('/api/shop/members', { token: owner.accessToken });
  assert.ok(members.body.members.some(m => m.name === 'شاگرد قدیمی'));
});

test('صاحب دکان می‌تواند شاگرد را بیرون کند', async () => {
  const owner = await h.newUser('صاحب چهارم');
  await h.post('/api/shop', { name: 'دکان چهارم' }, { token: owner.accessToken });
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;

  const staff = await h.newUser('شاگرد اخراجی');
  await h.post('/api/shop/join', { code }, { token: staff.accessToken });

  const before = await h.get('/api/shop/members', { token: owner.accessToken });
  const row = before.body.members.find(m => m.name === 'شاگرد اخراجی');
  assert.ok(row, 'شاگرد باید در فهرست باشد');

  const out = await h.del(`/api/shop/members/${row.id}`, { token: owner.accessToken });
  assert.equal(out.status, 200);

  const after = await h.get('/api/shop/members', { token: owner.accessToken });
  assert.ok(!after.body.members.some(m => m.name === 'شاگرد اخراجی'), 'باید بیرون رفته باشد');
});

test('تمدید در تاریخچه ثبت می‌شود، با تاریخ پیش و پس', async () => {
  const u = await h.newUser('صاحب تاریخچه');
  const shop = await h.post('/api/shop', { name: 'دکان تاریخچه' }, { token: u.accessToken });
  const shopId = shop.body.shop.id;
  const token = await adminToken();

  await h.post('/api/admin/subscriptions', { shopId, plan: 'custom', days: 30 }, { token });
  await h.post('/api/admin/subscriptions', { shopId, plan: 'custom', days: 30 }, { token });

  const { rows } = await h.query(
    'SELECT * FROM subscription_history WHERE shop_id=$1 ORDER BY created_at', [shopId]
  );
  assert.ok(rows.length >= 2, 'باید دو سطر تاریخچه باشد');
  const renew = rows[rows.length - 1];
  assert.equal(renew.action, 'renew');
  assert.ok(Number(renew.new_ends_at) > Number(renew.prev_ends_at), 'تمدید باید تاریخ پایان را جلو ببرد');
});

test('کد ورود برای ایمیل هم صادر می‌شود', async () => {
  const r = await h.post('/api/auth/otp/request', { email: 'someone@example.com' });
  assert.equal(r.status, 200);
  assert.equal(r.body.sent, true);
  assert.ok(r.body.devCode, 'در حالت آزمایش کد برگردانده می‌شود');

  const verify = await h.post('/api/auth/otp/verify', {
    email: 'someone@example.com', code: r.body.devCode, name: 'کاربر ایمیلی',
  });
  assert.equal(verify.status, 201, 'حساب تازه با ایمیل ساخته می‌شود');
  assert.equal(verify.body.user.email, 'someone@example.com');
});
