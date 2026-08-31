'use strict';
/**
 * مجوز اشتراک، از چشمِ برنامه.
 *
 * بقیه‌ی تست‌ها مجوز را از دید سرور می‌سنجیدند و کلاینت‌ها مجوزِ ساختگیِ
 * خودشان را. نتیجه: یک ناهماهنگی نامِ فیلد ماه‌ها زنده ماند و
 * **اشتراک پولی روی هیچ دستگاهی فعال نمی‌شد** — سرور `dev`/`acc`
 * می‌نوشت و هر دو کلاینت `duid`/`sub` می‌خواندند.
 *
 * این فایل همان چیزی را می‌سنجد که کلاینت می‌سنجد، روی مجوزِ واقعی.
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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** همان کاری که کلاینت می‌کند: بخش میانی را باز کن. */
function payloadOf(licenseToken) {
  return JSON.parse(Buffer.from(licenseToken.split('.')[1], 'base64url').toString('utf8'));
}

/** یک صاحب دکان با اشتراک فعال. */
async function ownerWithSubscription(name, days = 30) {
  const owner = await h.newUser(name);
  const shop = (await h.post('/api/shop', { name: `دکان ${name}` }, { token: owner.accessToken })).body.shop;
  await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days },
    { token: await adminToken() });
  return { owner, shop };
}

test('مجوز همان نام فیلدهایی را دارد که برنامه می‌خواند', async () => {
  const { owner } = await ownerWithSubscription('نام‌فیلد');
  const r = await h.post('/api/license/sync',
    { device: { uid: 'dev-format', name: 'گوشی' } }, { token: owner.accessToken });

  assert.equal(r.status, 200);
  assert.ok(r.body.license, 'برای اشتراک فعال، مجوز صادر نشد');

  const p = payloadOf(r.body.license);

  // این چهار خط دقیقاً چیزی است که `License.verify` در اندروید می‌سنجد
  assert.equal(p.iss, 'tohid-license-server');
  assert.equal(p.aud, 'tohid-shop-app');
  assert.equal(p.duid, 'dev-format', 'کلاینت duid را می‌خواند');
  assert.equal(p.sub, owner.user.id, 'کلاینت sub را می‌خواند');

  // و این‌ها را برای وضعیت و قابلیت‌ها
  assert.ok(Number(p.exp) > Date.now(), 'exp باید در آینده باشد');
  assert.ok(Number(p.nbf) <= Date.now(), 'nbf باید گذشته باشد');
  assert.ok(Array.isArray(p.feat) && p.feat.length, 'فهرست قابلیت‌ها خالی است');
  assert.ok(Array.isArray(p.core), 'core باید فهرست باشد');
});

/*
 * سناریوی خودِ صاحب مخزن: یک گوشی، دو حساب.
 *
 * اگر احمد اشتراک خریده باشد، محمود که روی همان گوشی وارد می‌شود نباید
 * اشتراک داشته باشد — باید خودش بخرد.
 */
test('روی یک گوشی، اشتراک حساب اول به حساب دوم نمی‌رسد', async () => {
  const DEVICE = 'dev-shared-phone';

  const { owner: ahmad } = await ownerWithSubscription('احمد');
  const ahmadLicense = (await h.post('/api/license/sync',
    { device: { uid: DEVICE, name: 'گوشی مشترک' } }, { token: ahmad.accessToken })).body.license;
  assert.ok(ahmadLicense, 'احمد که اشتراک دارد، باید مجوز بگیرد');

  // محمود، همان گوشی، دکان خودش، بدون اشتراک
  const mahmood = await h.newUser('محمود');
  await h.post('/api/shop', { name: 'دکان محمود' }, { token: mahmood.accessToken });
  await query('UPDATE shops SET created_at = $2 WHERE owner_user_id = $1',
    [mahmood.user.id, Date.now() - 400 * 24 * 3600 * 1000]);   // دوره‌ی آزمایشی هم تمام

  const forMahmood = await h.post('/api/license/sync',
    { device: { uid: DEVICE, name: 'گوشی مشترک' } }, { token: mahmood.accessToken });

  assert.equal(forMahmood.status, 200);
  assert.equal(forMahmood.body.license, null, 'محمود اشتراک ندارد و نباید مجوز بگیرد');
  assert.equal(forMahmood.body.reason, 'no_subscription');

  /*
   * و مهم‌تر: مجوزِ احمد روی همان گوشی مانده. تنها چیزی که جلوی
   * استفاده‌ی محمود از آن را می‌گیرد، `sub` است — چون `duid` هر دو
   * یکی است. کلاینت همین را می‌سنجد.
   */
  const p = payloadOf(ahmadLicense);
  assert.equal(p.duid, DEVICE, 'دستگاه یکی است — پس تنها سدّ، حساب است');
  assert.notEqual(p.sub, mahmood.user.id, 'مجوز احمد نباید شناسه‌ی محمود را داشته باشد');
  assert.equal(p.sub, ahmad.user.id);
});

/*
 * قفل شدن به‌موقع.
 *
 * مجوز هرگز نباید از خودِ اشتراک دیرتر تمام شود؛ وگرنه کسی که اشتراکش
 * تمام شده چند روز مجانی کار می‌کند.
 */
test('مجوز از اشتراک دیرتر تمام نمی‌شود', async () => {
  const { owner } = await ownerWithSubscription('کوتاه', 2);   // اشتراک دو روزه
  const r = await h.post('/api/license/sync',
    { device: { uid: 'dev-short', name: 'گوشی' } }, { token: owner.accessToken });

  const p = payloadOf(r.body.license);
  assert.ok(Number(p.exp) <= Number(p.sub_ends),
    `مجوز تا ${new Date(Number(p.exp)).toISOString()} کار می‌کند ولی اشتراک ` +
    `${new Date(Number(p.sub_ends)).toISOString()} تمام می‌شود`);
});

test('اشتراک که تمام شود، مجوز تازه صادر نمی‌شود', async () => {
  const { owner, shop } = await ownerWithSubscription('تمام‌شده');

  // اشتراک و دوره‌ی آزمایشی، هر دو به گذشته
  await query('UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1', [shop.id, Date.now() - 1000]);
  await query('UPDATE shops SET created_at=$2 WHERE id=$1',
    [shop.id, Date.now() - 400 * 24 * 3600 * 1000]);

  const r = await h.post('/api/license/sync',
    { device: { uid: 'dev-expired', name: 'گوشی' } }, { token: owner.accessToken });

  assert.equal(r.body.license, null, 'اشتراک تمام شده ولی مجوز صادر شد');
  assert.equal(r.body.reason, 'no_subscription');
});
