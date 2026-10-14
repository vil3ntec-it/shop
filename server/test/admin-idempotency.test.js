'use strict';
/**
 * F0-14 — اعطای اشتراک از پنلِ مدیر با کلیدِ یکتایی (Idempotency-Key).
 *
 * مشکل: دو بار کلیک روی «ثبت اشتراک» (یا تکرارِ درخواست پس از قطعِ شبکه)
 * دو دوره اشتراک می‌ساخت — ۳۰ روز می‌شد ۶۰ روز.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) همان کلید دو بار ⇒ یک دوره، و پاسخِ دوم همان پاسخِ اول است.
 *   ۲) دو درخواستِ هم‌زمان با همان کلید ⇒ باز هم یک دوره.
 *   ۳) همان کلید با بدنهٔ دیگر ⇒ ۴۰۹، و هیچ چیزی عوض نمی‌شود.
 *   ۴) کدِ تخفیف در تکرار دوباره خرج نمی‌شود.
 *   ۵) بخشِ پمپ هم همین‌طور.
 *   ۶) قاعدهٔ سازگاری: درخواستِ بی‌کلید (پنلِ قدیمیِ باز مانده) همان رفتارِ قبلی را دارد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');

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

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.token;
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function pumpOwner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

function grant(t, body, key) {
  return h.api('POST', '/api/admin/subscriptions', {
    body, token: t, headers: key ? { 'Idempotency-Key': key } : {},
  });
}

async function liveSub(shopId) {
  return one(`SELECT * FROM subscriptions WHERE shop_id=$1 AND status IN ('active','suspended')
               ORDER BY updated_at DESC LIMIT 1`, [shopId]);
}

/** طولِ دورهٔ زنده از همین حالا، به روز (گرد). */
function daysLeft(sub) {
  return Math.round((Number(sub.ends_at) - Date.now()) / DAY);
}

test('F0-14 ۱) همان کلید دو بار ⇒ یک دوره، پاسخِ دوم همان پاسخِ اول', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۱');
  const before = await liveSub(o.shopId);
  const baseDays = before ? daysLeft(before) : 0;
  const key = `k-${newId('t')}`;
  const body = { shopId: o.shopId, plan: 'custom', days: 30 };

  const a = await grant(t, body, key);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await grant(t, body, key);
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal(b.body.subscription.id, a.body.subscription.id);
  assert.equal(String(b.body.subscription.ends_at), String(a.body.subscription.ends_at));
  assert.equal(b.headers.get('idempotent-replayed'), 'true');

  const live = await liveSub(o.shopId);
  assert.equal(daysLeft(live), baseDays + 30, 'دو کلیک نباید دو دوره بسازد');
  const changes = await many(`SELECT * FROM subscription_history WHERE shop_id=$1 AND action IN ('grant','renew')`, [o.shopId]);
  assert.equal(changes.length - (before ? 1 : 0), 1, 'فقط یک ردِ تغییر برای این کلید');
});

test('F0-14 ۲) دو درخواستِ هم‌زمان با همان کلید ⇒ یک دوره', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۲');
  const before = await liveSub(o.shopId);
  const baseDays = before ? daysLeft(before) : 0;
  const key = `k-${newId('t')}`;
  const body = { shopId: o.shopId, plan: 'custom', days: 30 };

  const [a, b] = await Promise.all([grant(t, body, key), grant(t, body, key)]);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal(a.body.subscription.id, b.body.subscription.id);
  assert.equal(daysLeft(await liveSub(o.shopId)), baseDays + 30);
});

test('F0-14 ۳) همان کلید با بدنهٔ دیگر ⇒ ۴۰۹ و بی تغییر', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۳');
  const key = `k-${newId('t')}`;
  const a = await grant(t, { shopId: o.shopId, plan: 'custom', days: 30 }, key);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const endsAfterFirst = String((await liveSub(o.shopId)).ends_at);

  const b = await grant(t, { shopId: o.shopId, plan: 'custom', days: 90 }, key);
  assert.equal(b.status, 409, JSON.stringify(b.body));
  assert.equal(b.body.error.code, 'idempotency_key_reused');
  assert.equal(String((await liveSub(o.shopId)).ends_at), endsAfterFirst);
});

test('F0-14 ۴) کدِ تخفیف در تکرار دوباره خرج نمی‌شود', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۴');
  const made = await h.post('/api/admin/discount-codes', {
    code: 'ONCE10', app: 'shop', kind: 'percent', value: 10, maxUses: 5,
  }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const key = `k-${newId('t')}`;
  const body = { shopId: o.shopId, plan: 'y1', days: 365, price: 1000, discountCode: 'ONCE10' };
  const a = await grant(t, body, key);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await grant(t, body, key);
  assert.equal(b.status, 201, JSON.stringify(b.body));
  const uses = await one(`SELECT COUNT(*)::int AS n FROM discount_uses WHERE code_id=$1`, [made.body.code.id]);
  assert.equal(uses.n, 1, 'تکرار نباید کد را دوباره خرج کند');
});

test('F0-14 ۵) بخشِ پمپ: همان کلید دو بار ⇒ یک دوره', async () => {
  const t = await adminToken();
  const p = await pumpOwner('یکتا-پمپ', 'IDEM01');
  const live = () => one(`SELECT * FROM station_subscriptions WHERE station_id=$1 AND status IN ('active','suspended')
                           ORDER BY updated_at DESC LIMIT 1`, [p.stationId]);
  const before = await live();
  const baseDays = before ? daysLeft(before) : 0;
  const key = `k-${newId('t')}`;
  const body = { stationId: p.stationId, plan: 'custom', days: 30 };
  const send = () => h.api('POST', '/api/admin/pump/subscriptions', { body, token: t, headers: { 'Idempotency-Key': key } });
  const a = await send();
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await send();
  assert.equal(b.status, 201, JSON.stringify(b.body));
  assert.equal(b.body.subscription.id, a.body.subscription.id);
  assert.equal(daysLeft(await live()), baseDays + 30);
});

test('F0-14 ۶) سازگاری: بی‌کلید همان رفتارِ قبلی (هر درخواست یک تمدید)', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۶');
  const before = await liveSub(o.shopId);
  const baseDays = before ? daysLeft(before) : 0;
  const body = { shopId: o.shopId, plan: 'custom', days: 30 };
  assert.equal((await grant(t, body)).status, 201);
  assert.equal((await grant(t, body)).status, 201);
  assert.equal(daysLeft(await liveSub(o.shopId)), baseDays + 60);
});

test('F0-14 ۷) کلیدِ نامعتبر ⇒ ۴۰۰', async () => {
  const t = await adminToken();
  const o = await shopOwner('یکتا-۷');
  const r = await grant(t, { shopId: o.shopId, plan: 'custom', days: 30 }, 'x'.repeat(300));
  assert.equal(r.status, 400, JSON.stringify(r.body));
});
