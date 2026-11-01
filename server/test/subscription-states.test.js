'use strict';
/**
 * SUB-01 — رفتارِ جدای هر وضعیتِ اشتراک، مطابقِ جدولِ docs/SUB-STATES-fa.md.
 * یک آزمون برای هر ردیف: پرداخت‌درانتظار، فعال، مهلت (grace)، منقضی، لغوشده
 * (+ معلق و شروع‌نشده). همه با ساعتِ سرور (`at`)، نه ساعتِ دستگاه.
 *
 * هر ردیف این‌ها را قفل می‌کند: `phase`، `active`، منبعِ دسترسی (`source`)،
 * بودن/نبودنِ قابلیتِ پولی، مصرف شدنِ دورهٔ آزمایشی، و گذارِ بعدی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const subs = require('../src/lib/subscriptions');
const { entitlementOf } = require('../src/lib/entitlement');
const { PAID_KEYS } = require('../src/lib/features');

const DAY = subs.DAY;
const PAID = PAID_KEYS[0];
let admin = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function shop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id };
}

/** دورهٔ آزمایشیِ همین دکان کِی تمام می‌شود (از خودِ سرور). */
async function trialEnd(shopId) {
  return (await entitlementOf(shopId)).trial.endsAt;
}

const paid = (ent) => ent.features.includes(PAID);

test('پرداخت‌درانتظار: درخواستِ خرید دسترسی نمی‌دهد و دورهٔ آزمایشی را نمی‌سوزاند؛ تأییدِ مدیر ⇒ فعال', async () => {
  assert.ok(PAID, 'دست‌کم یک قابلیتِ پولی هست');
  const s = await shop('درانتظار');
  const r = await h.post('/api/me/purchase-request', { plan: 'm1' }, { token: s.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.request.status, 'pending');

  let ent = await entitlementOf(s.shopId);
  assert.equal(ent.source, 'trial', 'در دورهٔ آزمایشی همان دسترسیِ آزمایشی');
  assert.equal(ent.trial.active, true, 'درخواستِ خرید دوره را مصرف نمی‌کند');
  assert.equal(ent.subscription.phase, 'none');
  ent = await entitlementOf(s.shopId, (await trialEnd(s.shopId)) + DAY);
  assert.equal(ent.source, 'free', 'پس از آزمایشی، درخواستِ تأییدنشده دسترسیِ پولی نمی‌دهد');
  assert.equal(paid(ent), false);

  //  ردیفِ اشتراک با وضعیتِ pending هم همین رفتار را دارد
  const t = now();
  const rowId = newId('sub');
  await query(
    `INSERT INTO subscriptions (id, shop_id, plan, status, starts_at, ends_at, features, max_devices, grace_days, note, created_at, updated_at)
     VALUES ($1,$2,'m1','pending',$3,$4,'[]'::jsonb,10,0,'',$3,$3)`,
    [rowId, s.shopId, t, t + 30 * DAY]
  );
  ent = await entitlementOf(s.shopId);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['pending', 'pending', false]);
  assert.equal(ent.source, 'trial');
  assert.equal(ent.trial.active, true, '⛔ pending دوره را مصرف نمی‌کند (entitlement.js)');
  await query('DELETE FROM subscriptions WHERE id=$1', [rowId]);

  //  تأییدِ مدیر ⇒ فعال
  const ok = await h.post(`/api/admin/purchase-requests/${r.body.request.id}/approve`, {}, { token: admin });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.state.phase, 'active');
  ent = await entitlementOf(s.shopId);
  assert.equal(ent.source, 'subscription');
  assert.equal(ent.trial.active, false, 'اشتراکِ نشسته دوره را مصرف‌شده می‌کند');
});

test('فعال: دسترسیِ پولی، phase=active و daysLeft تا پایانِ مهلت', async () => {
  const s = await shop('فعال');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30, graceDays: 3 });
  const at = now();
  const ent = await entitlementOf(s.shopId, at);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['active', 'active', true]);
  assert.equal(ent.source, 'subscription');
  assert.ok(paid(ent));
  assert.equal(ent.trial.active, false);
  assert.equal(ent.subscription.graceEndsAt, Number(row.ends_at) + 3 * DAY);
  assert.equal(ent.subscription.daysLeft, Math.ceil((Number(row.ends_at) + 3 * DAY - at) / DAY));
});

test('مهلت (grace): پس از ends_at تا graceEndsAt همان دسترسی، ولی phase=grace؛ یک لحظه پس از آن منقضی', async () => {
  const s = await shop('مهلت');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30, graceDays: 3 });
  const endsAt = Number(row.ends_at);
  const graceEnd = endsAt + 3 * DAY;

  let ent = await entitlementOf(s.shopId, endsAt);
  assert.equal(ent.subscription.phase, 'active', 'خودِ لحظهٔ پایان هنوز فعال است');
  ent = await entitlementOf(s.shopId, endsAt + 1);
  assert.deepEqual([ent.subscription.phase, ent.subscription.active], ['grace', true], 'یک میلی‌ثانیه پس از پایان ⇒ مهلت');
  ent = await entitlementOf(s.shopId, endsAt + DAY);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['active', 'grace', true]);
  assert.equal(ent.source, 'subscription', 'در مهلت دسترسی بسته نمی‌شود');
  assert.ok(paid(ent));
  assert.equal(ent.subscription.daysLeft, 2, 'روزهای مانده تا پایانِ مهلت');
  ent = await entitlementOf(s.shopId, graceEnd);
  assert.equal(ent.subscription.phase, 'grace', 'آخرین لحظهٔ مهلت');
  ent = await entitlementOf(s.shopId, graceEnd + 1);
  assert.deepEqual([ent.subscription.phase, ent.subscription.active, ent.source], ['expired', false, 'free']);

  //  بی مهلت (grace_days = 0) هیچ‌وقت phase=grace ندارد
  const s2 = await shop('بی‌مهلت');
  const r2 = await subs.grant(s2.shopId, { plan: 'custom', days: 30 });
  assert.equal((await entitlementOf(s2.shopId, Number(r2.ends_at) + 1)).subscription.phase, 'expired');
});

test('منقضی: بی دسترسیِ پولی، دورهٔ آزمایشی از نو شروع نمی‌شود، دفتر می‌ماند؛ تمدید از همین حالا', async () => {
  const s = await shop('منقضی');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 10 });
  const after = Number(row.ends_at) + DAY;
  assert.ok(await subs.expireDue(after) >= 1, 'کارِ دوره‌ای ردیف را expired می‌کند');
  const stored = await one('SELECT status FROM subscriptions WHERE id=$1', [row.id]);
  assert.equal(stored.status, 'expired');

  const ent = await entitlementOf(s.shopId, after);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['expired', 'expired', false]);
  assert.equal(ent.source, 'free');
  assert.equal(paid(ent), false);
  assert.equal(ent.trial.active, false, '⛔ پایانِ اشتراک دورهٔ آزمایشی را زنده نمی‌کند');
  assert.ok(ent.subscription.endsAt > 0, 'کاربر «اشتراک تمام شد» می‌بیند، نه «اشتراکی نیست»');
  //  منقضی پایانی است: برگشت فقط با grant ِ تازه
  await assert.rejects(subs.setStatus(row.id, 'active'), (e) => e.code === 'bad_transition');
  const fresh = await subs.grant(s.shopId, { plan: 'custom', days: 30 });
  assert.notEqual(fresh.id, row.id, 'دورهٔ تازه ردیفِ تازه است');
  assert.equal((await entitlementOf(s.shopId)).subscription.phase, 'active');
});

test('لغوشده: دسترسیِ پولی همان دم بسته، پایانی، و گرفتنِ دورهٔ آزمایشی دوباره ممکن نیست', async () => {
  const s = await shop('لغو');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30, graceDays: 3 });
  await subs.setStatus(row.id, 'cancelled', 'test');
  const ent = await entitlementOf(s.shopId);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['cancelled', 'cancelled', false]);
  assert.equal(ent.source, 'free', 'حتی در ماهِ اولِ حساب، لغو دورهٔ آزمایشی را باز نمی‌کند');
  assert.equal(ent.trial.active, false);
  assert.equal(paid(ent), false);
  for (const to of ['active', 'suspended']) {
    await assert.rejects(subs.setStatus(row.id, to), (e) => e.code === 'bad_transition');
  }
  const fresh = await subs.grant(s.shopId, { plan: 'custom', days: 30 });
  assert.notEqual(fresh.id, row.id);
});

test('معلق و شروع‌نشده: معلق بی دسترسی و کد بازش نمی‌کند؛ شروعِ آینده phase=scheduled', async () => {
  const s = await shop('معلق');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30 });
  await subs.setStatus(row.id, 'suspended', 'test');
  let ent = await entitlementOf(s.shopId);
  assert.deepEqual([ent.subscription.phase, ent.subscription.active, ent.source], ['suspended', false, 'free']);
  await assert.rejects(subs.grant(s.shopId, { plan: 'custom', days: 30 }), (e) => e.code === 'subscription_suspended');
  await subs.setStatus(row.id, 'active', 'test');
  assert.equal((await entitlementOf(s.shopId)).subscription.phase, 'active');

  const s2 = await shop('آینده');
  const start = now() + 5 * DAY;
  await subs.grant(s2.shopId, { plan: 'custom', startsAt: start, endsAt: start + 30 * DAY });
  ent = await entitlementOf(s2.shopId);
  assert.deepEqual([ent.subscription.status, ent.subscription.phase, ent.subscription.active], ['active', 'scheduled', false]);
  assert.equal((await entitlementOf(s2.shopId, start + DAY)).subscription.phase, 'active');
  assert.equal(subs.stateOf(null).phase, 'none');
});

test('SUB-01: /api/me/subscription و /api/me/heartbeat مرحله را می‌گویند — «مهلت» با پایانِ مهلت، نه «فعال»', async () => {
  const s = await shop('مهلتِ-API');
  const row = await subs.grant(s.shopId, { plan: 'custom', days: 30, graceDays: 3 });
  let r = await h.get('/api/me/subscription', { token: s.token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual([r.body.status, r.body.phase, r.body.active], ['active', 'active', true]);
  assert.equal(r.body.graceEndsAt, Number(row.ends_at) + 3 * DAY);

  //  پایانِ دوره دیروز گذشته ⇒ مهلت (ستونِ status همان active می‌ماند و دسترسی باز است)
  const endsAt = now() - DAY;
  await query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [row.id, endsAt]);
  r = await h.get('/api/me/subscription', { token: s.token });
  assert.deepEqual([r.body.status, r.body.phase, r.body.active], ['active', 'grace', true]);
  assert.equal(r.body.endsAt, endsAt);
  assert.equal(r.body.graceEndsAt, endsAt + 3 * DAY);
  assert.equal(r.body.daysLeft, 2, 'روزهای ماندهٔ مهلت');

  const hb = await h.get('/api/me/heartbeat', { token: s.token });
  assert.equal(hb.status, 200, JSON.stringify(hb.body));
  assert.equal(hb.body.subscription.status, 'active', 'سازگاری: status همان');
  assert.equal(hb.body.subscription.phase, 'grace');
  assert.equal(hb.body.subscription.graceEndsAt, endsAt + 3 * DAY);

  //  پس از مهلت ⇒ منقضی در هر دو
  await query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [row.id, now() - 4 * DAY]);
  r = await h.get('/api/me/subscription', { token: s.token });
  assert.deepEqual([r.body.status, r.body.phase, r.body.active], ['expired', 'expired', false]);
  assert.equal((await h.get('/api/me/heartbeat', { token: s.token })).body.subscription.phase, 'expired');
});

/**
 * ⛔ SUB-01 فقط دکان: `phase` و `graceEndsAt` به نمای پمپ اضافه نمی‌شوند — پاسخِ `/api/pump/heartbeat`
 * (کدِ مشترکِ پمپ) کلید‌به‌کلید و به همان ترتیبِ main است، حتی در مهلت؛ نمای پورتال برای عضویتِ پمپ هم.
 */
test('SUB-01: پاسخِ تپشِ پمپ همان شکلِ main است — بی phase و graceEndsAt، حتی در مهلت', async () => {
  //  کلیدهای main (پیش از SUB-01)، به همان ترتیب
  const MAIN_HEARTBEAT = ['app', 'tenantId', 'subscription', 'entitlement', 'unreadNotices', 'latest', 'serverTime'];
  const MAIN_VIEW = ['source', 'status', 'active', 'plan', 'startsAt', 'endsAt', 'daysLeft', 'totalDays', 'progress',
    'permanent', 'color', 'label', 'features', 'addons', 'trial'];

  const u = await h.newUser('پمپِ-مهلت', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ مهلت' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const stationId = made.body.station.id;
  const row = await subs.pump.grant(stationId, { plan: 'custom', days: 30, graceDays: 3 });
  //  پایانِ دوره دیروز ⇒ مهلت (همان حالتی که دکان در آن phase='grace' می‌گیرد)
  await query('UPDATE station_subscriptions SET ends_at=$2 WHERE id=$1', [row.id, now() - DAY]);

  const hb = await h.get('/api/pump/heartbeat', { token: u.accessToken });
  assert.equal(hb.status, 200, JSON.stringify(hb.body));
  assert.deepEqual(Object.keys(hb.body), MAIN_HEARTBEAT);
  assert.deepEqual(Object.keys(hb.body.subscription), MAIN_VIEW);
  assert.equal(hb.body.subscription.status, 'active');
  assert.equal(hb.body.subscription.daysLeft, 2, 'روزهای ماندهٔ مهلت همان حسابِ main');

  //  نمای پورتال: عضویتِ پمپ همان شکل؛ تابع بی app یا با pump همان خروجی
  const me = await h.get('/api/portal/me', { token: u.accessToken });
  assert.equal(me.status, 200, JSON.stringify(me.body));
  const pumpM = me.body.memberships.find((m) => m.app === 'pump' || m.tenantId === stationId);
  assert.ok(pumpM, JSON.stringify(me.body.memberships));
  assert.deepEqual(Object.keys(pumpM.subscription), MAIN_VIEW);

  const { subscriptionView } = require('../src/routes/portal');
  const ent = await require('../src/lib/entitlement').forApp('pump').entitlementOf(stationId);
  const at = now();
  assert.equal(JSON.stringify(subscriptionView(ent, at, 'pump')), JSON.stringify(subscriptionView(ent, at)));
  assert.deepEqual(Object.keys(subscriptionView(ent, at, 'shop')).filter((k) => !MAIN_VIEW.includes(k)), ['phase', 'graceEndsAt']);
});
