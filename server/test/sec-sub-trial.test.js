'use strict';
/**
 * SEC-SUB-02 — ساعت، نصبِ دوباره، پاک کردنِ داده و بازیابیِ پشتیبانِ محلی دورهٔ آزمایشیِ دکان را از نو
 * نمی‌کنند و دراز نمی‌کنند. (SEC-SUB-03 هم: فایل یا پاسخِ دست‌کاری‌شدهٔ برنامه مجوزی در سرور نمی‌سازد.)
 *
 * دفترِ آزمایشی همان ستونِ سرور است: آغاز = `shops.created_at` (دکان) که هیچ مسیری از بیرون نمی‌نویسد،
 * و «هر حساب یک دکانِ خودش» (`createShop` ⇒ ۴۰۹ `already_member`) — پس دفتر عملاً به حساب بسته است.
 * نصبِ دوباره/پاک کردنِ داده در برنامه = ورودِ دوباره با شناسهٔ دستگاهِ تازه؛ بازیابیِ پشتیبانِ محلی =
 * بالا رفتنِ همان دفتر با Sync یا پروندهٔ پشتیبان. هیچ‌کدام نباید پایانِ آزمایشی را جابه‌جا کنند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const trialOf = async (token, headers = {}) => {
  const r = await h.get('/api/shop', { token, headers });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { source: r.body.entitlement?.source || '', ...(r.body.entitlement?.trial || {}) };
};
const relogin = (u, uid) => h.post('/api/auth/login', {
  identifier: u.email, password: u.password, app: 'shop', device: { deviceId: uid, name: 'نصبِ تازه', platform: 'test' },
});
const FORGED = {
  trial: { active: true, endsAt: now() + 3650 * DAY, startsAt: now() },
  trial_started_at: now(), created_at: now(), createdAt: now(),
  subscription: { plan: 'vip', status: 'active', endsAt: now() + 3650 * DAY },
  entitlement: { source: 'subscription', features: ['multi_device', 'online_orders'] },
  vip: true,
};

test('SEC-SUB-02: نصبِ دوباره، ساعتِ جلو/عقب، دکانِ دوم و بدنهٔ ساختگی پایانِ آزمایشی را جابه‌جا نمی‌کنند', async () => {
  const u = await h.newUser('آزمایشی-یک-بار');
  const shop = (await h.post('/api/shop', { name: 'دکان', ...FORGED }, { token: u.accessToken })).body.shop;
  const row = await one('SELECT created_at FROM shops WHERE id=$1', [shop.id]);
  assert.ok(Math.abs(Number(row.created_at) - now()) < 60_000, 'آغاز از ساعتِ سرور، نه از بدنه');
  const t0 = await trialOf(u.accessToken);
  assert.equal(t0.source, 'trial');
  assert.equal(t0.endsAt, Number(row.created_at) + 7 * DAY);

  //  ساعتِ ادعاییِ برنامه (جلو و عقب) بی‌اثر
  for (const off of [3650 * DAY, -3650 * DAY]) {
    const t = await trialOf(u.accessToken, { 'X-Client-Time': String(now() + off), Date: new Date(now() + off).toUTCString() });
    assert.equal(t.endsAt, t0.endsAt, `ساعتِ ${off > 0 ? 'جلو' : 'عقب'}`);
  }
  //  نصبِ دوباره / پاک کردنِ داده: دستگاهِ تازه، نشستِ تازه — همان پایان
  const again = await relogin(u, 'reinstall-1');
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal((await trialOf(again.body.accessToken)).endsAt, t0.endsAt);
  const lic = await h.post('/api/license/sync', { device: { uid: 'reinstall-1', name: 'نصبِ تازه' } }, { token: again.body.accessToken });
  const p = JSON.parse(Buffer.from(lic.body.license.split('.')[1], 'base64url').toString());
  assert.ok(p.exp <= t0.endsAt, 'مجوزِ آزمایشی از پایانِ آزمایشی دیرتر نیست');

  //  دکانِ دوم برای همین حساب ساخته نمی‌شود ⇒ آزمایشیِ تازه هم نه
  const second = await h.post('/api/shop/create', { name: 'دکانِ دوم', ...FORGED }, { token: u.accessToken });
  assert.equal(second.status, 409, JSON.stringify(second.body));
  assert.equal(second.body.error?.code, 'already_member');
  //  ویرایشِ دکان با فیلدهای ساختگی
  await h.put('/api/shop', { name: 'دکان', ...FORGED }, { token: u.accessToken });
  assert.equal(Number((await one('SELECT created_at FROM shops WHERE id=$1', [shop.id])).created_at), Number(row.created_at));
  assert.equal((await trialOf(u.accessToken)).endsAt, t0.endsAt);
});

test('SEC-SUB-02: پس از پایانِ آزمایشی — نصبِ دوباره، بازیابیِ پشتیبانِ محلی و Syncِ دست‌کاری‌شده چیزی باز نمی‌کنند', async () => {
  const u = await h.newUser('آزمایشی-تمام');
  const shopId = (await h.post('/api/shop', { name: 'دکان' }, { token: u.accessToken })).body.shop.id;
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shopId, now() - 8 * DAY]);
  const ended = await trialOf(u.accessToken);
  assert.equal(ended.source, 'free');
  assert.equal(ended.active, false);

  //  «پاک کردنِ داده» = دستگاهِ تازه
  const fresh = await relogin(u, 'wiped-1');
  assert.equal(fresh.status, 200);
  const tok = fresh.body.accessToken;
  //  «بازیابیِ پشتیبانِ محلی»: پروندهٔ پشتیبان با وضعیتِ ساختگیِ آزمایشی/اشتراک بالا می‌رود
  const blob = Buffer.from(JSON.stringify({ app: 'shop', ...FORGED, license: 'x.y.z' }), 'utf8');
  const up = await h.raw('POST', '/api/me/backups?kind=manual&ext=json', blob, { token: tok });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  //  و همان دفتر با تنظیماتِ دست‌کاری‌شده با Sync
  const push = await h.post('/api/shop/sync/push', {
    deviceId: 'wiped-1',
    changes: [{ collection: 'settings', id: 'subscription', updatedAt: now(), data: FORGED }],
    settings: FORGED,
  }, { token: tok });
  assert.ok(push.status < 300, JSON.stringify(push.body));

  const after = await trialOf(tok);
  assert.equal(after.source, 'free', 'هیچ‌کدام نباید آزمایشی یا اشتراک را باز کند');
  assert.equal(after.endsAt, ended.endsAt);
  const lic = await h.post('/api/license/sync', { device: { uid: 'wiped-1', name: 'نصبِ تازه' } }, { token: tok });
  assert.equal(Boolean(lic.body.license), false);
  assert.equal(lic.body.reason, 'no_subscription');
  //  پولی‌ها هم با همین نشست بسته
  const paid = await h.post('/api/shop/staff-code', {}, { token: tok });
  assert.equal(paid.status, 403, JSON.stringify(paid.body));
  assert.match(paid.body.error?.code || '', /^subscription_/);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM subscriptions WHERE shop_id=$1', [shopId])).n, 0);
});
