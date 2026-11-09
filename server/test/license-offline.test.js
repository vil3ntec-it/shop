'use strict';
/**
 * SYN-01 / D-05 — مجوزِ اشتراکِ دکان و «۳۰ روز بی اینترنت».
 *
 * دستگاهِ بی اینترنت مجوزش را تازه نمی‌کند و برنامه (`License.status` ⇐ `LicenseGuard` ⇐ `VipGate`) پس از `exp` ِ مجوز
 * «فروش» و «قرض‌داران» را می‌بندد. مجوز ده روز عمر داشت ⇒ روزِ یازدهمِ قطعی دکانِ پول‌داده نمی‌فروخت
 * (`desktop/…/offline/Offline30DaysDesktopTest`). با پرچمِ `shop.license_offline_30d` (پیش‌فرض خاموش) ۳۵ روز —
 * و هرگز دیرتر از پایانِ اشتراک و مهلتش.
 *
 *   ۱) پرچمِ خاموش ⇒ همان ده روز (رفتارِ امروز، بایت‌به‌بایت همان قاعده)
 *   ۲) پرچمِ روشن ⇒ ۳۵ روز؛ روزهای ۲۹، ۳۰ و ۳۱ ِ پس از صدور هنوز معتبر
 *   ۳) پرچمِ روشن، اشتراکِ کوتاه ⇒ سقف همان پایانِ مهلتِ اشتراک (هیچ روزِ مجانی)
 *   ۴) پرچمِ روشن، دورهٔ آزمایشی ⇒ سقف همان پایانِ آزمایشی
 *   ۵) `license.issue` بی `ttlMs` (پمپ و هر صدازنندهٔ دیگر) ⇒ همان ده روز
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');

const DAY = 24 * 3600 * 1000;
const FLAG = 'shop.license_offline_30d';

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const payloadOf = (token) => JSON.parse(Buffer.from(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/'), 'base64').toString('utf8'));

async function shop(name) {
  const u = await h.newUser(name);
  const r = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { u, shopId: r.body.shop.id };
}

async function paid(shopId, days, graceDays = 3) {
  const t = Date.now();
  await h.query(
    `INSERT INTO subscriptions (id, shop_id, plan, status, starts_at, ends_at, features, max_devices, grace_days, note, created_at, updated_at)
     VALUES ($1,$2,'pro','active',$3,$4,'[]'::jsonb,10,$5,'',$3,$3)`,
    [`sub_off30_${shopId}`, shopId, t - DAY, t + days * DAY, graceDays]
  );
}

const flag = (shopId, on) => h.query(
  `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,1)
   ON CONFLICT (scope, key) DO UPDATE SET value = excluded.value`,
  [String(shopId), FLAG, on ? 'true' : 'false']
);

async function licenseOf(token, uid) {
  const r = await h.post('/api/license/sync', { device: { uid, name: 'کامپیوترِ صندوق' } }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.license, `مجوز صادر نشد: ${JSON.stringify(r.body)}`);
  return payloadOf(r.body.license);
}

test('پرچمِ خاموش: مجوزِ دکانِ پول‌داده همان ده روز — روزِ ۱۱ ِ بی اینترنت منقضی', async () => {
  const s = await shop('ده‌روزه');
  await paid(s.shopId, 120);
  const p = await licenseOf(s.u.accessToken, 'OFF30-A');
  assert.equal(p.exp - p.iat, 10 * DAY);
  assert.ok(p.sub_ends > p.iat + 100 * DAY, 'اشتراک ماه‌ها مانده است');
  //  همان سنجهٔ برنامه (`License.status`: now > exp ⇒ EXPIRED)
  for (const day of [29, 30, 31]) assert.ok(p.iat + day * DAY > p.exp, `روزِ ${day}: مجوز منقضی (رفتارِ امروز)`);
});

test('پرچمِ روشن: ۳۵ روز — روزهای ۲۹، ۳۰ و ۳۱ معتبر؛ دیرتر از اشتراک هرگز', async () => {
  const s = await shop('سی‌روزه');
  await paid(s.shopId, 120);
  await flag(s.shopId, true);
  const p = await licenseOf(s.u.accessToken, 'OFF30-B');
  assert.equal(p.exp - p.iat, 35 * DAY);
  for (const day of [29, 30, 31]) assert.ok(p.iat + day * DAY < p.exp, `روزِ ${day}: مجوز هنوز معتبر`);
  assert.ok(p.exp <= p.sub_ends, 'مجوز از خودِ اشتراک دیرتر تمام نمی‌شود');

  //  خاموش کردن ⇒ مجوزِ بعدی همان ده روز
  await flag(s.shopId, false);
  const q = await licenseOf(s.u.accessToken, 'OFF30-B');
  assert.equal(q.exp - q.iat, 10 * DAY);
});

test('پرچمِ روشن، اشتراکِ ۲۰ روزه با ۳ روز مهلت ⇒ سقف همان پایانِ مهلت، نه ۳۵ روز', async () => {
  const s = await shop('کوتاه');
  await paid(s.shopId, 20, 3);
  await flag(s.shopId, true);
  const p = await licenseOf(s.u.accessToken, 'OFF30-C');
  const graceEnd = p.sub_ends + 3 * DAY;
  assert.ok(Math.abs(p.exp - graceEnd) <= 1000, `exp=${p.exp} graceEnd=${graceEnd}`);
  assert.ok(p.exp - p.iat < 35 * DAY);
});

test('پرچمِ روشن، دورهٔ آزمایشی ⇒ سقف همان پایانِ آزمایشی', async () => {
  const s = await shop('آزمایشی');
  await flag(s.shopId, true);
  const p = await licenseOf(s.u.accessToken, 'OFF30-D');
  assert.ok(p.sub_ends > p.iat, 'آزمایشی پایان دارد');
  assert.equal(p.exp, p.sub_ends, 'هیچ روزِ مجانی پس از آزمایشی');
});

test('license.issue بی ttlMs (پمپ و بقیه) ⇒ همان ده روز', async () => {
  const license = require('../src/lib/license');
  const at = Date.now();
  const r = await license.issue({ deviceUid: 'PUMP-1', accountId: 'acc', subscriptionEndsAt: at + 300 * DAY, activeUntil: at + 300 * DAY, at });
  assert.equal(r.expiresAt - at, 10 * DAY);
  assert.equal(license.TOKEN_TTL_MS, 10 * DAY);
  assert.equal(license.SHOP_OFFLINE_TTL_MS, 35 * DAY);
});
