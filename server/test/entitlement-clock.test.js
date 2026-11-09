'use strict';
/**
 * SUB-03 ساعت — جهشِ ساعتِ **سرور** هیچ دکانِ پول‌داده‌ای را به‌اشتباه نمی‌بندد (فقط دکان؛ پمپ بیت‌به‌بیت همان).
 *
 * دو باگِ واقعی، هر دو با ساعتِ ساختگیِ کنترل‌شده (`Date.now` + جابه‌جایی) — نه با `at` ِ دستی:
 *  ۱) ساعت جلو می‌پرد (RTC ِ خراب پس از راه‌اندازی، NTP ِ نادرست): خانه‌تکانی (`expireDue`) ستون را برای
 *     همیشه `expired` می‌نوشت ⇒ پس از درست شدنِ ساعت هم دکانِ پول‌داده بسته می‌ماند، شاگرد ۴۰۳ می‌گرفت و تمدیدِ
 *     بعدی ردیفِ تازه از «اکنون» می‌ساخت (روزهای مانده می‌سوخت).
 *  ۲) ساعت درست پس از اعطا یک قدم عقب می‌رود: `at < starts_at` ⇒ «شروع‌نشده» ⇒ دکانِ بی آزمایشی `free` و نوشتنِ
 *     شاگرد ۴۰۳، تا ساعت دوباره به لحظهٔ اعطا برسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const subs = require('../src/lib/subscriptions');
const { entitlementOf } = require('../src/lib/entitlement');

const DAY = subs.DAY;
const MIN = 60_000;

/* ── ساعتِ ساختگیِ کنترل‌شده: ساعتِ واقعی + جابه‌جایی (زمان‌سنج‌ها و توکن‌ها همچنان جلو می‌روند) ── */
const realNow = Date.now;
let skew = 0;
const setSkew = (ms) => { skew = ms; };
Date.now = () => realNow.call(Date) + skew;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { Date.now = realNow; await h.stop(); });
test.afterEach(() => setSkew(0));

/** دکانِ بی دورهٔ آزمایشی (ساخته‌شده ۴۰۰ روز پیش) با یک شاگرد — تنها راهِ نوشتنِ شاگرد اشتراک است */
async function shopWithStaff(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const staff = await h.newUser(`شاگرد-${label}`);
  const shop = (await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken })).body.shop;
  const code = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  await h.post('/api/shop/staff/join', { code }, { token: staff.accessToken });
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shop.id, now() - 400 * DAY]);
  return { owner, staff, shop };
}

const pushOne = (token, id) => h.post('/api/sync', {
  deviceId: 'd1',
  changes: [{ collection: 'sales', id, updatedAt: Date.now(), data: { total: 100 } }],
}, { token });

const stored = async (id) => (await query('SELECT * FROM subscriptions WHERE id=$1', [id])).rows[0];

test('ساعت جلو پرید و برگشت: دکانِ پول‌داده پس از درست شدنِ ساعت باز است، ستون خودش درست می‌شود و تمدید روزها را نمی‌سوزاند', async () => {
  const { staff, shop } = await shopWithStaff('جهش');
  const row = await subs.grant(shop.id, { plan: 'custom', days: 30 });
  assert.equal((await pushOne(staff.accessToken, 'j-0')).status, 200);

  //  ساعتِ سرور ۴۰۰ روز جلو می‌پرد و خانه‌تکانی همان لحظه می‌دود
  setSkew(400 * DAY);
  assert.ok(await subs.expireDue() >= 1, 'با ساعتِ جلورفته، کارِ دوره‌ای ردیف را می‌بندد (همان پیشین)');
  assert.equal((await entitlementOf(shop.id)).source, 'free', 'در خودِ پنجرهٔ نادرست بسته است — ساعتِ دیگری نداریم');
  const jumped = await stored(row.id);
  assert.equal(jumped.status, 'expired');
  assert.ok(Number(jumped.auto_expired_at) >= realNow.call(Date) + 399 * DAY, 'شاهدِ ساعت: لحظهٔ بستنِ خودکار (با ساعتِ همان دم) ثبت می‌شود');

  //  ساعت درست شد
  setSkew(0);
  const ent = await entitlementOf(shop.id);
  assert.deepEqual([ent.source, ent.subscription.phase, ent.subscription.status], ['subscription', 'active', 'active'],
    '⛔ پس از درست شدنِ ساعت، همان دم دوباره «اشتراک» — نه «تمام شد»');
  const w = await pushOne(staff.accessToken, 'j-1');
  assert.equal(w.status, 200, `⛔ شاگردِ دکانِ پول‌داده دوباره می‌نویسد: ${JSON.stringify(w.body)}`);

  //  نخستین خانه‌تکانی با ساعتِ درست ستون را هم برمی‌گرداند (تا grant و فهرست‌های SQL همان را ببینند)
  assert.equal(await subs.expireDue(), 0, 'هیچ ردیفِ تازه‌ای بسته نمی‌شود');
  const healed = await stored(row.id);
  assert.deepEqual([healed.status, healed.auto_expired_at], ['active', null]);
  const log = (await query(
    `SELECT action, prev_status, new_status, actor FROM subscription_history WHERE subscription_id=$1 ORDER BY created_at DESC LIMIT 1`,
    [row.id])).rows[0];
  assert.deepEqual(log, { action: 'status', prev_status: 'expired', new_status: 'active', actor: 'system' }, 'برگشت در سابقه می‌ماند');

  //  تمدیدِ بعدی همان ردیف را از پایانِ خودش جلو می‌برد — روزهای مانده نمی‌سوزد
  const renewed = await subs.grant(shop.id, { plan: 'custom', days: 30 });
  assert.equal(renewed.id, row.id);
  assert.equal(Number(renewed.ends_at), Number(row.ends_at) + 30 * DAY);
});

test('ساعت جلو پرید: اشتراکی که واقعاً تمام شده بسته می‌ماند و پایانِ دستیِ مدیر هرگز برنمی‌گردد', async () => {
  //  الف) پایانِ واقعی: مهلت در ساعتِ درست هم گذشته
  const a = await shopWithStaff('واقعی');
  const ra = await subs.grant(a.shop.id, { plan: 'custom', endsAt: now() + 5 * DAY });
  setSkew(400 * DAY);
  await subs.expireDue();
  setSkew(10 * DAY);   //  «درست شد» به لحظه‌ای که پایانِ واقعی هم گذشته
  assert.equal((await entitlementOf(a.shop.id)).source, 'free');
  await subs.expireDue();
  assert.equal((await stored(ra.id)).status, 'expired', 'ردیفِ واقعاً تمام‌شده برنمی‌گردد');

  //  ب) پایانِ دستیِ مدیر پیش از موعد — حتی با ساعتِ عقب‌تر از آن لحظه
  setSkew(0);
  const b = await shopWithStaff('دستی');
  const rb = await subs.grant(b.shop.id, { plan: 'custom', days: 30 });
  await subs.setStatus(rb.id, 'expired', 'admin-x');
  setSkew(-5 * MIN);
  assert.equal((await entitlementOf(b.shop.id)).source, 'free');
  await subs.expireDue();
  const sb = await stored(rb.id);
  assert.deepEqual([sb.status, sb.auto_expired_at], ['expired', null], 'تصمیمِ مدیر پایانی است');

  //  ج) جهش، سپس اعطای تازه در همان پنجرهٔ نادرست (ردیفِ دوم)، سپس درست شدن: ایندکسِ «یک زنده» نمی‌شکند
  const c = await shopWithStaff('دوردیف');
  const rc = await subs.grant(c.shop.id, { plan: 'custom', days: 30 });
  setSkew(400 * DAY);
  await subs.expireDue();
  const second = await subs.grant(c.shop.id, { plan: 'custom', days: 30 });
  assert.notEqual(second.id, rc.id);
  setSkew(0);
  assert.equal((await entitlementOf(c.shop.id)).source, 'subscription', 'اعطای پنجرهٔ نادرست هم «شروع‌نشده» خوانده نمی‌شود');
  await subs.expireDue();
  assert.equal((await stored(rc.id)).status, 'expired', 'با ردیفِ زندهٔ دیگر، ردیفِ کهنه زنده نمی‌شود');
  assert.equal((await pushOne(c.staff.accessToken, 'c-1')).status, 200);
});

test('ساعت درست پس از اعطا عقب رفت: اشتراکِ تازه «شروع‌نشده» خوانده نمی‌شود و شاگرد ۴۰۳ نمی‌گیرد', async () => {
  const { staff, shop } = await shopWithStaff('عقب');
  const adm = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  const g = await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30 }, { token: adm });
  assert.equal(g.status, 201, JSON.stringify(g.body));

  setSkew(-2 * MIN);   //  NTP ساعت را دو دقیقه عقب کشید
  const ent = await entitlementOf(shop.id);
  assert.deepEqual([ent.source, ent.subscription.phase], ['subscription', 'active'], '⛔ اعطای همین لحظه «شروع‌نشده» نیست');
  const w = await pushOne(staff.accessToken, 'b-1');
  assert.equal(w.status, 200, `⛔ شاگرد پس از عقب رفتنِ ساعت هم می‌نویسد: ${JSON.stringify(w.body)}`);
  const me = await h.get('/api/me/subscription', { token: staff.accessToken });
  assert.equal(me.body.phase, 'active');

  //  تمدیدِ اشتراکی که در مهلت بود (شروعِ تازه = همین لحظه) همان قاعده را دارد
  setSkew(0);
  const s2 = await shopWithStaff('عقب-مهلت');
  await subs.grant(s2.shop.id, { plan: 'custom', endsAt: now() + 1000, graceDays: 3 });
  setSkew(2 * DAY);
  await subs.grant(s2.shop.id, { plan: 'custom', days: 30 });
  setSkew(2 * DAY - 10 * MIN);
  assert.equal((await entitlementOf(s2.shop.id)).subscription.phase, 'active');
});

test('شروعِ آیندهٔ واقعی (startsAt ِ مدیر) همچنان «شروع‌نشده» است — قاعدهٔ تازه فقط اعطای «همین لحظه» را می‌گیرد', async () => {
  const { shop } = await shopWithStaff('آینده');
  const start = now() + 3 * DAY;
  const row = await subs.grant(shop.id, { plan: 'custom', startsAt: start, endsAt: start + 30 * DAY });
  assert.equal((await entitlementOf(shop.id)).subscription.phase, 'scheduled');
  assert.equal((await entitlementOf(shop.id, start - 1)).subscription.phase, 'scheduled');
  assert.equal((await entitlementOf(shop.id, start)).subscription.phase, 'active');
  //  ویرایشِ دستیِ مدیر که شروع را جلو می‌برد هم «شروع‌نشده» است
  await query('UPDATE subscriptions SET starts_at=$2, updated_at=$3 WHERE id=$1', [row.id, start + DAY, now()]);
  assert.equal((await entitlementOf(shop.id, start + 1)).subscription.phase, 'scheduled');
});

test('پمپ بیت‌به‌بیت همان: ردیفِ پمپ (بی ستونِ شاهدِ ساعت) همان «شروع‌نشده» و «منقضی» ِ پیشین را دارد', async () => {
  const t = now();
  const pumpRow = {
    id: 'ps1', station_id: 'st1', plan: 'custom', status: 'active', starts_at: t, ends_at: t + 30 * DAY,
    grace_days: 0, features: [], max_devices: 1, note: '', created_at: t, updated_at: t,
  };
  assert.equal(subs.pump.stateOf(pumpRow, t - MIN).phase, 'scheduled', 'پمپ: همان پیشین');
  assert.equal(subs.stateOf({ ...pumpRow, status: 'expired' }, t + MIN).status, 'expired');
  const cols = (await query(
    `SELECT column_name FROM information_schema.columns WHERE table_name='station_subscriptions' AND column_name='auto_expired_at'`
  )).rows;
  assert.equal(cols.length, 0, 'جدولِ پمپ ستونِ تازه نگرفته');
});
