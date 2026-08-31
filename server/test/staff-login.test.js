'use strict';
/**
 * ورود شاگرد، فقط با کد.
 *
 * قرار صاحب مخزن: کد را که به کسی می‌دهم، در صفحه‌ی ورود بزند و مستقیم
 * برود داخل حساب — نه ایمیل بخواهد، نه شماره.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
});
test.after(async () => { await h.stop(); });

test('شاگرد فقط با کد وارد می‌شود', async () => {
  const owner = await h.newUser('صاحب');
  const shop = (await h.post('/api/shop', { name: 'دکان من' }, { token: owner.accessToken })).body.shop;
  const admin = (await h.post('/api/admin/login', { username:'admin', password:'Admin!12345' })).body.token;
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan:'m1', days:30 }, { token: admin });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  console.log('  کد شاگرد:', code);

  const r = await h.post('/api/auth/staff', { code, name: 'کریم', device: { uid: 'phone-1', name: 'گوشی کریم' } });
  console.log('  ورود →', r.status, '| دکان:', JSON.stringify(r.body.shop), '| کاربر:', r.body.user?.name);
  assert.equal(r.status, 201);
  assert.ok(r.body.accessToken, 'توکن نیامد');
  assert.equal(r.body.shop.id, shop.id);
  assert.equal(r.body.shop.role, 'staff');
  assert.equal(r.body.user.email, null, 'شاگرد ایمیل ندارد');
  assert.equal(r.body.user.phone, null, 'شاگرد شماره ندارد');

  // کار می‌کند: می‌بیند و می‌فروشد
  const pull = await h.get('/api/sync?since=0', { token: r.body.accessToken });
  assert.equal(pull.status, 200);
  const push = await h.post('/api/sync', { deviceId:'phone-1',
    changes:[{ collection:'sales', id:'s-karim', updatedAt: Date.now(), data:{ total: 250 } }] },
    { token: r.body.accessToken });
  assert.equal(push.status, 200);
  console.log('  فروشِ کریم ثبت شد:', push.body.applied);

  // دفعه‌ی دوم: همان حساب، نه یک عضو تازه
  const again = await h.post('/api/auth/staff', { code, device: { uid: 'phone-1' } });
  assert.equal(again.body.user.id, r.body.user.id, 'باید همان حساب باشد');
  const members = (await h.get('/api/shop/members', { token: owner.accessToken })).body;
  console.log('  اعضای دکان بعد از دو بار ورود:', members.members.length);
  assert.equal(members.members.length, 2, 'صاحب + یک شاگرد');

  // صاحب دکان فروشِ شاگرد را می‌بیند
  const ownerPull = await h.get('/api/sync?since=0', { token: owner.accessToken });
  assert.ok(ownerPull.body.changes.some(c => c.id === 's-karim'), 'صاحب فروشِ شاگرد را ندید');
  console.log('  صاحب دکان فروشِ شاگرد را دید ✓');
});

test('کد باطل‌شده دیگر وارد نمی‌کند', async () => {
  const owner = await h.newUser('صاحب۲');
  const shop = (await h.post('/api/shop', { name:'دکان۲' }, { token: owner.accessToken })).body.shop;
  const admin = (await h.post('/api/admin/login', { username:'admin', password:'Admin!12345' })).body.token;
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan:'m1', days:30 }, { token: admin });
  const made = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body;
  await h.post('/api/shop/staff-code/rotate', {}, { token: owner.accessToken }).catch(()=>{});
  await query(`UPDATE staff_codes SET status='revoked' WHERE shop_id=$1`, [shop.id]);
  const r = await h.post('/api/auth/staff', { code: made.code, device:{ uid:'p9' } });
  console.log('  کد باطل →', r.status, r.body.error?.code);
  assert.ok(r.status >= 400);
});
