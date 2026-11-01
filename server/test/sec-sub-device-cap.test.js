'use strict';
/**
 * SEC-SUB-03 / SEC-SUB-04 — سقفِ دستگاهِ اشتراکِ دکان در سرور (`POST /api/license/sync`).
 *
 * ریشه (SEC-CLI-01): شناسهٔ دستگاهی که در بدنه می‌آمد فقط **شمرده** می‌شد، هرگز **نشانده** نمی‌شد.
 * پس با یک توکن (یا توکنِ کپی‌شده روی گوشیِ دیگر) و شناسه‌های تازهٔ پشتِ‌سرِهم، هر بار «n < سقف» بود و
 * بی‌شمار گوشی مجوزِ امضاشده می‌گرفتند؛ شناسهٔ دستگاهی هم که صاحب از «دستگاه‌های من» باطل کرده بود
 * (`revoked`) دیگر شمرده نمی‌شد و دوباره مجوز می‌گرفت. حالا:
 *   • شناسهٔ تازه فقط با جای خالی، زیرِ قفلِ همان دکان، **ثبت** می‌شود و از آن پس شمرده می‌شود؛
 *   • شناسهٔ باطل‌شدهٔ همین حساب ⇒ `device_revoked`، بی مجوز؛
 *   • درخواست‌های هم‌زمان از سقف رد نمی‌شوند (قفلِ `shop-devices:<دکان>`)؛
 *   • با پرچمِ `shop.device_cap` (پیش‌فرض خاموش): دستگاهِ ثبت‌شده‌ای که بیرون از «n ِ اول» است هم رد می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

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

/** صاحبِ دکان با اشتراکِ فعال و سقفِ `max` دستگاه؛ دستگاهِ ثبت‌نام (`dev-N`) خودش یکی است. */
async function shopWithCap(label, max) {
  const owner = await h.newUser(label);
  const shop = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken })).body.shop;
  const g = await h.post('/api/admin/subscriptions', { shopId: shop.id, plan: 'm1', days: 30, maxDevices: max }, { token: admin });
  assert.equal(g.status, 201, JSON.stringify(g.body));
  const row = await one('SELECT max_devices FROM subscriptions WHERE shop_id=$1', [shop.id]);
  assert.equal(Number(row.max_devices), max);
  return { owner, shop };
}

const lic = (token, uid) => h.post('/api/license/sync', { device: { uid, name: 'گوشی' } }, { token });
const login = (owner, uid) => h.post('/api/auth/login', {
  identifier: owner.email, password: owner.password, app: 'shop',
  device: { deviceId: uid, name: uid, platform: 'test' },
});

test('SEC-SUB-03: یک توکن و شناسه‌های ساختگیِ پیاپی بیش از سقف مجوز نمی‌گیرند', async () => {
  const { owner } = await shopWithCap('توکن-کپی', 2);
  //  دستگاهِ ثبت‌نام یکی؛ یک جای خالی مانده
  const first = await lic(owner.accessToken, 'forged-1');
  assert.ok(first.body.license, `جای دوم خالی بود: ${JSON.stringify(first.body)}`);
  const bad = [];
  for (const uid of ['forged-2', 'forged-3', 'forged-4']) {
    const r = await lic(owner.accessToken, uid);
    if (r.body.license || r.body.reason !== 'device_limit') bad.push(`${uid} ⇒ ${r.body.reason || 'مجوز'}`);
  }
  assert.deepEqual(bad, [], `سقفِ ۲ دستگاه دور خورد: ${bad.join('، ')}`);
  //  شناسهٔ تازه ثبت شد و دیده می‌شود (صاحب می‌تواند باطلش کند)
  const mine = (await h.get('/api/me/devices', { token: owner.accessToken })).body.devices.map((d) => d.device_uid);
  assert.ok(mine.includes('forged-1'), JSON.stringify(mine));
  //  دستگاهی که جا دارد همیشه دوباره مجوز می‌گیرد (نصبِ دوباره بیرون نمی‌ماند)
  assert.ok((await lic(owner.accessToken, 'forged-1')).body.license);
});

test('SEC-SUB-03: دستگاهِ باطل‌شده از «دستگاه‌های من» با توکنِ دیگر هم مجوز نمی‌گیرد', async () => {
  const { owner } = await shopWithCap('باطل', 5);
  const second = await login(owner, 'phone-revoked');
  assert.equal(second.status, 200, JSON.stringify(second.body));
  const devs = (await h.get('/api/me/devices', { token: owner.accessToken })).body.devices;
  const victim = devs.find((d) => d.device_uid === 'phone-revoked');
  assert.ok(victim, JSON.stringify(devs));
  assert.equal((await h.del(`/api/me/devices/${victim.id}`, { token: owner.accessToken })).status, 200);

  const r = await lic(owner.accessToken, 'phone-revoked');
  assert.equal(r.status, 200);
  assert.equal(Boolean(r.body.license), false, 'دستگاهِ باطل‌شده دوباره مجوز گرفت');
  assert.equal(r.body.reason, 'device_revoked');
  const row = await one(`SELECT status FROM devices WHERE user_id=$1 AND device_uid='phone-revoked'`, [owner.user.id]);
  assert.equal(row.status, 'revoked', 'باطل‌شده باید باطل بماند');
});

test('SEC-SUB-04: درخواست‌های هم‌زمان با شناسه‌های تازه از سقف رد نمی‌شوند', async () => {
  const { owner } = await shopWithCap('هم‌زمان', 3);
  const uids = Array.from({ length: 8 }, (_, i) => `race-${i}`);
  const out = await Promise.all(uids.map((uid) => lic(owner.accessToken, uid)));
  const granted = out.filter((r) => r.body.license).length;
  assert.equal(granted, 2, `سقفِ ۳ (یکی دستگاهِ ثبت‌نام) ولی ${granted} مجوزِ تازه: ${out.map((r) => r.body.reason || 'ok').join(',')}`);
  assert.ok(out.every((r) => r.status === 200));
  const n = await one(
    `SELECT COUNT(*)::int AS n FROM devices WHERE user_id=$1 AND status='active'`, [owner.user.id]);
  assert.equal(n.n, 3, 'دقیقاً به اندازهٔ سقف دستگاهِ فعال');
});

test('SEC-SUB-03: پرچمِ shop.device_cap — دستگاهِ واردشده‌ای که بیرون از سقف است مجوز نمی‌گیرد؛ خاموش ⇒ رفتارِ پیشین', async () => {
  const { owner, shop } = await shopWithCap('سخت‌گیر', 2);
  for (const uid of ['phone-2', 'phone-3']) {
    const r = await login(owner, uid);
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  //  پیش‌فرض (پرچم خاموش): همان رفتارِ نسخه‌های نصب‌شده — دستگاهِ واردشده مجوز می‌گیرد
  assert.ok((await lic(owner.accessToken, 'phone-3')).body.license, 'پرچمِ خاموش نباید رفتار را عوض کند');

  const on = await h.put('/api/admin/pump/live-config', { scope: shop.id, key: 'shop.device_cap', value: true }, { token: admin });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  //  دو دستگاهِ اول (ثبت‌نام و phone-2) جا دارند، سومی نه
  assert.ok((await lic(owner.accessToken, `dev-${owner.email.match(/\d+/)[0]}`)).body.license, 'دستگاهِ ثبت‌نام');
  assert.ok((await lic(owner.accessToken, 'phone-2')).body.license, 'دستگاهِ دوم');
  const third = await lic(owner.accessToken, 'phone-3');
  assert.equal(Boolean(third.body.license), false, 'دستگاهِ سوم با سقفِ ۲ مجوز گرفت');
  assert.equal(third.body.reason, 'device_limit');

  //  صاحب یکی را باطل می‌کند ⇒ جا برای سومی باز می‌شود
  const devs = (await h.get('/api/me/devices', { token: owner.accessToken })).body.devices;
  const p2 = devs.find((d) => d.device_uid === 'phone-2');
  assert.equal((await h.del(`/api/me/devices/${p2.id}`, { token: owner.accessToken })).status, 200);
  assert.ok((await lic(owner.accessToken, 'phone-3')).body.license, 'پس از باطل کردنِ یکی، جا باز می‌شود');
});
