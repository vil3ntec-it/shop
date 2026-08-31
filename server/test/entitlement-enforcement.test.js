'use strict';
/**
 * اشتراک را سرور اجرا می‌کند، نه گوشی.
 *
 * ── سوراخی که این تست‌ها نگهبانش هستند ────────────────────────────
 * `/api/sync` و `/api/data/*` هیچ بررسی اشتراکی نداشتند. دکانی بدون
 * اشتراک و بدون دوره‌ی آزمایشی هم بی‌محدودیت push و pull می‌کرد، و
 * «چند کاربر روی یک دکان» — که قابلیت پولی است — مجانی بود.
 *
 * قاعده‌ی حالا: خواندن هرگز بسته نمی‌شود، صاحب دکان همیشه می‌نویسد،
 * و شاگرد برای نوشتن اشتراک لازم دارد.
 * ──────────────────────────────────────────────────────────────────
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;

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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** دکانی با اشتراک فعال و یک شاگرد داخلش. */
async function shopWithStaff(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const staff = await h.newUser(`شاگرد-${label}`);
  const shop = (await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken })).body.shop;
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30 },
    { token: await adminToken() });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });
  return { owner, staff, shop };
}

/** اشتراک و دوره‌ی آزمایشی، هر دو به گذشته. */
async function expire(shop) {
  await query('UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1', [shop.id, Date.now() - 1000]);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shop.id, Date.now() - 400 * DAY]);
}

const pushOne = (token, id) => h.post('/api/sync', {
  deviceId: 'd1',
  changes: [{ collection: 'sales', id, updatedAt: Date.now(), data: { total: 100 } }],
}, { token });

test('اشتراک تمام‌شده: شاگرد دیگر نمی‌تواند بنویسد', async () => {
  const { staff, shop } = await shopWithStaff('قفل');

  // پیش از انقضا کار می‌کند
  assert.equal((await pushOne(staff.accessToken, 'ok-1')).status, 200);

  await expire(shop);

  const blocked = await pushOne(staff.accessToken, 'blocked-1');
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'subscription_required');

  // و راه دوم نوشتن هم بسته است، وگرنه بستن اولی بی‌معنی بود
  const rest = await h.post('/api/products', { data: { name: 'کالا' } }, { token: staff.accessToken });
  assert.equal(rest.status, 403, 'مسیر REST هم باید بسته باشد');
});

test('اشتراک تمام‌شده: خواندن هرگز بسته نمی‌شود', async () => {
  const { owner, staff, shop } = await shopWithStaff('خواندن');
  await pushOne(owner.accessToken, 'row-1');
  await expire(shop);

  // داده‌ی فروشنده گروگان گرفته نمی‌شود — نه برای صاحب، نه برای شاگرد
  for (const [who, token] of [['صاحب', owner.accessToken], ['شاگرد', staff.accessToken]]) {
    const pull = await h.get('/api/sync?since=0', { token });
    assert.equal(pull.status, 200, `${who} باید بتواند داده‌اش را بخواند`);
    assert.ok(pull.body.changes.some(c => c.id === 'row-1'), `${who} داده را ندید`);

    const list = await h.get('/api/products', { token });
    assert.equal(list.status, 200, `${who} باید بتواند فهرست را بخواند`);
  }
});

test('اشتراک تمام‌شده: صاحب دکان همچنان می‌نویسد', async () => {
  const { owner, shop } = await shopWithStaff('صاحب');
  await expire(shop);

  const push = await pushOne(owner.accessToken, 'owner-after-expiry');
  assert.equal(push.status, 200, 'کارِ خودِ صاحب دکان نباید از بین برود');
  assert.equal(push.body.applied, 1);
});

test('تمدید که شد، نوشته‌های شاگرد دوباره بالا می‌روند', async () => {
  const { staff, shop } = await shopWithStaff('تمدید');
  await expire(shop);
  assert.equal((await pushOne(staff.accessToken, 'waiting')).status, 403);

  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30 },
    { token: await adminToken() });

  const after = await pushOne(staff.accessToken, 'waiting');
  assert.equal(after.status, 200, 'بعد از تمدید باید بالا برود');
  assert.equal(after.body.applied, 1);
});

test('دوره‌ی آزمایشی هم اجازه‌ی نوشتن می‌دهد', async () => {
  const owner = await h.newUser('صاحب-آزمایشی');
  const staff = await h.newUser('شاگرد-آزمایشی');
  const shop = (await h.post('/api/shop', { name: 'دکان آزمایشی' }, { token: owner.accessToken })).body.shop;
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });

  assert.equal((await pushOne(staff.accessToken, 'trial-1')).status, 200,
    'در دوره‌ی آزمایشی همه‌چیز باید باز باشد');
});
