'use strict';
/**
 *  کاربرِ عادی نباید بتواند اشتراک را برای خودش باز کند.
 *
 *  ── فرضِ کار ───────────────────────────────────────────────────────
 *  فرض این است که کاربر APK را باز کرده، هر مقداری را که برنامه
 *  می‌فرستد عوض کرده، و مستقیم با سرور حرف می‌زند. پس هر چیزی که از
 *  سمتِ برنامه می‌آید — `vip=true`، پلن، تاریخ انقضا، وضعیت — باید
 *  بی‌اثر باشد. تصمیم فقط با سرور است.
 *  ──────────────────────────────────────────────────────────────────
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

/** کاربری با دکان، و دوره‌ی آزمایشی‌اش تمام‌شده */
async function expiredShopOwner(name) {
  const u = await h.newUser(name);
  const shop = await h.post('/api/shop', { name: `دکان ${name}` }, { token: u.accessToken });
  //  تاریخِ ساختِ دکان را عقب می‌بریم تا آزمایشی تمام شده باشد
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shop.body.shop.id, now() - 400 * DAY]);
  return { ...u, shopId: shop.body.shop.id };
}

test('کاربر با فرستادن vip=true چیزی به دست نمی‌آورد', async () => {
  const u = await expiredShopOwner('دستکار');

  for (const body of [
    { vip: true },
    { plan: 'vip', status: 'active' },
    { subscription: { plan: 'vip', endsAt: now() + 3650 * DAY } },
    { expiresAt: now() + 3650 * DAY },
  ]) {
    //  هر مسیری که به فکرِ کاربر می‌رسد
    for (const path of ['/api/me', '/api/me/subscription', '/api/shop']) {
      for (const method of ['post', 'put', 'patch']) {
        const r = await h[method](path, body, { token: u.accessToken });
        assert.ok(r.status !== 201 || path === '/api/shop', `${method} ${path} نباید اشتراک بسازد`);
      }
    }
  }

  const after = await h.get('/api/me/subscription', { token: u.accessToken });
  assert.equal(after.body.source, 'free', 'اشتراک نباید باز شده باشد');
  assert.equal(after.body.active, false);
  assert.equal(after.body.status, 'none');
});

test('مسیرهای مدیریت با توکنِ کاربرِ عادی بسته‌اند', async () => {
  const u = await expiredShopOwner('کنجکاو');

  const grant = await h.post('/api/admin/subscriptions', {
    shopId: u.shopId, plan: 'vip', days: 365,
  }, { token: u.accessToken });
  assert.ok(grant.status === 401 || grant.status === 403, `انتظار ۴۰۱/۴۰۳ بود، ${grant.status} آمد`);

  const list = await h.get('/api/admin/subscriptions', { token: u.accessToken });
  assert.ok(list.status === 401 || list.status === 403);

  //  و بدونِ هیچ توکنی هم همان
  assert.ok((await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'vip', days: 365 })).status >= 400);
});

test('درخواستِ خرید فقط یک درخواست است، نه اشتراک', async () => {
  const u = await expiredShopOwner('خریدار');

  const asked = await h.post('/api/me/purchase-request', { plan: 'vip', note: 'می‌خواهم' }, { token: u.accessToken });
  assert.ok(asked.status < 300, JSON.stringify(asked.body));

  const after = await h.get('/api/me/subscription', { token: u.accessToken });
  assert.equal(after.body.source, 'free', 'درخواستِ خرید نباید خودش اشتراک بسازد');

  const row = await one('SELECT COUNT(*)::int n FROM subscriptions WHERE shop_id=$1', [u.shopId]);
  assert.equal(row.n, 0, 'هیچ ردیفِ اشتراکی ساخته نشده');
});

test('مجوزِ آفلاین برای دستگاهِ خودش صادر می‌شود و بی‌اشتراک، مجوزی نیست', async () => {
  const u = await expiredShopOwner('آفلاین');

  const before = await h.post('/api/license/sync', {
    deviceUid: 'dev-offline-1', deviceName: 'گوشی',
  }, { token: u.accessToken });
  assert.ok(before.status < 300, JSON.stringify(before.body));
  assert.ok(!before.body.license, 'بی‌اشتراک، مجوزی صادر نمی‌شود');

  //  حالا مدیر اشتراک می‌دهد — تنها راهِ باز شدن
  const admin = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  const granted = await h.post('/api/admin/subscriptions', {
    shopId: u.shopId, plan: 'vip', days: 30,
  }, { token: admin.body.token });
  assert.equal(granted.status, 201, JSON.stringify(granted.body));

  const after = await h.post('/api/license/sync', {
    deviceUid: 'dev-offline-1', deviceName: 'گوشی',
  }, { token: u.accessToken });
  assert.ok(after.body.license, 'با اشتراک، مجوز صادر می‌شود');

  //  و مجوز به همان دستگاه بسته است
  const payload = JSON.parse(Buffer.from(after.body.license.split('.')[1], 'base64url').toString());
  assert.equal(payload.duid, 'dev-offline-1');
  assert.ok(payload.exp > now(), 'مهلتِ مجوز باید در آینده باشد');
  assert.ok(payload.exp - now() <= 11 * DAY, 'مجوزِ آفلاین نباید عمرِ بلند داشته باشد');
});

test('ساعتِ گوشی هیچ نقشی ندارد — پایان اشتراک را سرور می‌گوید', async () => {
  const u = await expiredShopOwner('ساعت‌باز');
  const admin = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'vip', days: 5 }, { token: admin.body.token });

  //  کاربر ادعا می‌کند «الان» خیلی جلوتر است
  const r = await h.get('/api/me/subscription', {
    token: u.accessToken,
    headers: { 'X-Client-Time': String(now() + 3650 * DAY) },
  });
  assert.equal(r.body.source, 'subscription');
  //  سرور زمانِ خودش را می‌گوید، نه زمانِ کاربر را
  assert.ok(Math.abs(r.body.serverTime - now()) < 60_000, 'ساعتِ پاسخ، ساعتِ سرور است');
  assert.ok(r.body.endsAt - now() <= 6 * DAY);
});
