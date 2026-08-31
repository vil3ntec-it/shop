'use strict';
/**
 * خبرهای دکان: آنچه شاگرد در برنامه‌اش می‌بیند، صاحب دکان هم ببیند.
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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username:'admin', password:'Admin!12345' })).body.token;

/** دکانی با اشتراک و یک شاگرد که با کد وارد شده. */
let seq = 0;
async function shopWithStaff(label) {
  //  شناسه‌ی دستگاه باید ASCII باشد — `v.id` حروف فارسی را نمی‌پذیرد
  const uid = `phone-${++seq}`;
  const owner = await h.newUser(`صاحب-${label}`);
  const shop = (await h.post('/api/shop', { name:`دکان ${label}` }, { token: owner.accessToken })).body.shop;
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan:'m1', days:30 },
    { token: await adminToken() });
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  const login = await h.post('/api/auth/staff', { code, name:'کریم', device:{ uid } });
  assert.equal(login.status, 201, `ورود شاگرد نشد: ${JSON.stringify(login.body)}`);
  return { owner, staff: login.body, shop };
}

test('فروشِ شاگرد، در خبرهای صاحب دکان دیده می‌شود', async () => {
  const { owner, staff } = await shopWithStaff('خبر');

  const sent = await h.post('/api/events', {
    events: [
      { kind:'sale', title:'فروش تازه', body:'۳ قلم — ۱٬۲۰۰ افغانی',
        data:{ total:1200 }, clientId:'evt-1' },
      { kind:'stock_out', title:'برنج تمام شد', body:'موجودی صفر است', clientId:'evt-2' },
    ],
  }, { token: staff.accessToken });
  assert.equal(sent.status, 201);
  assert.equal(sent.body.saved, 2);

  const seen = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(seen.status, 200);
  const kinds = seen.body.events.map(e => e.kind);
  assert.ok(kinds.includes('sale'), 'صاحب دکان فروشِ شاگرد را ندید');
  assert.ok(kinds.includes('stock_out'), 'خبرِ تمام شدن کالا نرسید');
  assert.equal(seen.body.events[0].userName, 'کریم', 'نامِ فرستنده نیامد');
  assert.equal(seen.body.unread, 2, 'هر دو باید نخوانده باشند');
});

test('همان خبر دو بار فرستاده شود، دو بار دیده نمی‌شود', async () => {
  const { owner, staff } = await shopWithStaff('تکراری');
  const body = { events: [{ kind:'sale', title:'فروش', clientId:'same-1' }] };

  await h.post('/api/events', body, { token: staff.accessToken });
  const again = await h.post('/api/events', body, { token: staff.accessToken });
  assert.equal(again.body.saved, 0, 'خبر تکراری دوباره ثبت شد');

  const seen = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(seen.body.events.filter(e => e.title === 'فروش').length, 1);
});

test('«تا اینجا خواندم» شمارِ نخوانده را صفر می‌کند', async () => {
  const { owner, staff } = await shopWithStaff('خوانده');
  await h.post('/api/events', { events:[{ kind:'sale', title:'ف', clientId:'a' }] },
    { token: staff.accessToken });

  const before = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(before.body.unread, 1);

  await h.post('/api/events/seen', { at: before.body.serverTime }, { token: owner.accessToken });
  const after = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(after.body.unread, 0);
  assert.ok(after.body.events.length > 0, 'خبرها نباید پاک شوند، فقط خوانده');
});

test('خبرهای دکان دیگر دیده نمی‌شوند', async () => {
  const a = await shopWithStaff('الف');
  const b = await shopWithStaff('ب');
  await h.post('/api/events', { events:[{ kind:'sale', title:'رازِ دکان الف', clientId:'x' }] },
    { token: a.staff.accessToken });

  const seen = await h.get('/api/events', { token: b.owner.accessToken });
  assert.equal(seen.body.events.length, 0, 'خبر به دکان دیگر نشت کرد');
});

test('اشتراک تمام‌شده هم جلوی دیدنِ خبرها را نمی‌گیرد', async () => {
  const { owner, staff, shop } = await shopWithStaff('منقضی');
  await h.post('/api/events', { events:[{ kind:'sale', title:'ف', clientId:'q' }] },
    { token: staff.accessToken });
  await query('UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1', [shop.id, Date.now() - 1000]);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shop.id, Date.now() - 400*24*3600*1000]);

  const seen = await h.get('/api/events', { token: owner.accessToken });
  assert.equal(seen.status, 200, 'صاحب دکان باید بداند در نبودش چه گذشت');
  assert.ok(seen.body.events.length > 0);
});
