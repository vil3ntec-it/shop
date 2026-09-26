'use strict';
/**
 * ══ نصبی که با کدِ شش‌رقمی فعال شده بود، به حسابِ صاحبش می‌رسد ══════════
 *
 * گزارشِ صاحبِ سامانه (۱۴۰۵/۰۷/۱۴): «اشتراک برای حسابِ کاربر دادم، براش
 * نیومد — نه آزمایشی، نه اشتراکی که دادم.» ریشه (بازسازی‌شده با پشتهٔ
 * واقعی و خودِ برنامه): نصب روی پمپِ بی‌صاحبِ کد مانده بود و اشتراک روی
 * پمپِ حساب نشسته بود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

const DAY = 86400000;
const dev = (uid) => ({ uid, name: 'کامپیوترِ پمپ', platform: 'windows' });

async function admin() {
  return (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
}

/** نصبی که روزی با کدِ شش‌رقمی فعال شد ⇒ پمپِ بی‌صاحب. */
async function codeInstall(uid, days = 20) {
  const t = await admin();
  const c = await h.post('/api/admin/pump/vip-codes', { plan: 'std', days }, { token: t });
  assert.equal(c.status, 201, JSON.stringify(c.body));
  const r = await h.post('/api/pump/device/activate', { code: c.body.code, device: dev(uid), station: {} });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: r.body.deviceToken, stationId: r.body.station.id };
}

async function grantVip(stationId) {
  const t = await admin();
  const g = await h.post('/api/admin/pump/subscriptions', { stationId, plan: 'vip', days: 365 }, { token: t });
  assert.equal(g.status, 201, JSON.stringify(g.body));
  return g.body.subscription;
}

const liveOf = (stationId) => one(
  `SELECT * FROM station_subscriptions WHERE station_id=$1 AND status IN ('active','suspended','pending')`, [stationId]);

test('حسابِ بی‌پمپ ⇒ پمپِ کد مالِ حساب می‌شود، با همان اشتراکش', async () => {
  const pc = await codeInstall('pc-adopt-claim');
  const u = await h.newUser('صاحبِ کد', 'pump');

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-adopt-claim'), adopt: true, deviceToken: pc.token }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.adopt, 'claimed');
  assert.equal(r.body.station.id, pc.stationId, 'همان پمپ، نه پمپِ تازه');
  assert.equal(r.body.entitlement.source, 'subscription', 'اشتراکِ کد سرِ جایش است');

  const me = await h.get('/api/pump/me', { token: u.accessToken });
  assert.equal(me.body.station.id, pc.stationId, 'حساب حالا صاحبِ همان پمپ است');
  const st = await one('SELECT owner_user_id FROM stations WHERE id=$1', [pc.stationId]);
  assert.equal(String(st.owner_user_id), String(u.user.id));
});

test('⛔ حسابِ پمپ‌دار ⇒ دستگاه به پمپِ حساب می‌رود و VIPِ مدیر همان لحظه می‌رسد', async () => {
  const pc = await codeInstall('pc-adopt-move', 20);
  const u = await h.newUser('صاحبِ حساب', 'pump');
  const mine = await h.post('/api/pump', { name: 'پمپِ حساب' }, { token: u.accessToken });
  const acct = mine.body.station.id;
  const vip = await grantVip(acct);

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-adopt-move'), adopt: true, deviceToken: pc.token }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.adopt, 'moved');
  assert.equal(r.body.station.id, acct);
  assert.equal(r.body.entitlement.subscription.plan, 'vip', JSON.stringify(r.body.entitlement));
  assert.ok(r.body.license, 'مجوزِ پمپِ حساب همان لحظه');

  //  ⛔ روزهای ماندهٔ کد نسوخت: روی VIPِ حساب نشست
  const after = await liveOf(acct);
  assert.ok(Number(after.ends_at) - Number(vip.ends_at) > 19 * DAY, 'روزهای کد روی اشتراکِ حساب آمد');
  //  و توکنِ کهنه دیگر پمپِ کد را باز نمی‌کند
  const old = await h.get('/api/pump/device/me', { token: pc.token });
  assert.equal(old.status, 401);
});

test('حسابِ پمپ‌دارِ بی‌اشتراک ⇒ اشتراکِ کد با دستگاه به پمپِ حساب می‌رود', async () => {
  const pc = await codeInstall('pc-adopt-carry', 40);
  const u = await h.newUser('صاحبِ حساب ۲', 'pump');
  const mine = await h.post('/api/pump', { name: 'پمپِ حساب ۲' }, { token: u.accessToken });
  const acct = mine.body.station.id;

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-adopt-carry'), adopt: true, deviceToken: pc.token }, { token: u.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.adopt, 'moved');
  const moved = await liveOf(acct);
  assert.equal(moved.plan, 'std');
  assert.equal(await liveOf(pc.stationId), null, 'پمپِ کد اشتراکِ زنده‌ای نگه نداشت');
});

test('⛔ پمپِ کدی که صاحب دارد دست نمی‌خورد', async () => {
  const pc = await codeInstall('pc-adopt-owned');
  const owner = await h.newUser('صاحبِ اصلی', 'pump');
  await query(`UPDATE stations SET owner_user_id=$2 WHERE id=$1`, [pc.stationId, owner.user.id]);
  await query(
    `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'owner','active',$4,$4)`, [newId('mem'), pc.stationId, owner.user.id, now()]);
  const stranger = await h.newUser('غریبه', 'pump');
  await h.post('/api/pump', { name: 'پمپِ غریبه' }, { token: stranger.accessToken });

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-adopt-owned'), adopt: true, deviceToken: pc.token }, { token: stranger.accessToken });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'station_mismatch');
  const me = await h.get('/api/pump/device/me', { token: pc.token });
  assert.equal(me.status, 200, 'دستگاه روی پمپِ صاحبش ماند');
});

test('توکنِ دستگاهِ دیگر مدرک نیست', async () => {
  const pc = await codeInstall('pc-adopt-real');
  const u = await h.newUser('دزد', 'pump');
  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-adopt-fake'), adopt: true, deviceToken: pc.token }, { token: u.accessToken });
  assert.equal(r.status, 404, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'no_station');
  const st = await one('SELECT owner_user_id FROM stations WHERE id=$1', [pc.stationId]);
  assert.equal(st.owner_user_id, null);
});
