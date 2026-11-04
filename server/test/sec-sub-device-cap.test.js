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

const flagOn = async (shop) => {
  const on = await h.put('/api/admin/pump/live-config', { scope: shop.id, key: 'shop.device_cap', value: true }, { token: admin });
  assert.equal(on.status, 200, JSON.stringify(on.body));
};

/** کارمندِ (`staff`) همین دکان؛ دستگاهِ ثبت‌نامِ خودش (`dev-N`) هم از همین لحظه شمرده می‌شود */
async function staffOf(shop, label) {
  const u = await h.newUser(label);
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`, [newId('mem'), shop.id, u.user.id, now()]);
  return u;
}

test('SEC-SUB-03: پرچمِ shop.device_cap — دستگاهِ واردشده‌ای که بیرون از سقف است مجوز نمی‌گیرد؛ خاموش ⇒ رفتارِ پیشین', async () => {
  const { owner, shop } = await shopWithCap('سخت‌گیر', 2);
  for (const uid of ['phone-2', 'phone-3']) {
    const r = await login(owner, uid);
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  //  پیش‌فرض (پرچم خاموش): همان رفتارِ نسخه‌های نصب‌شده — دستگاهِ واردشده مجوز می‌گیرد، بی هشدار
  const off = await lic(owner.accessToken, 'phone-3');
  assert.ok(off.body.license, 'پرچمِ خاموش نباید رفتار را عوض کند');
  assert.equal(off.body.warning, undefined, 'پرچمِ خاموش: پاسخ همان پاسخِ پیشین');

  await flagOn(shop);
  //  دو دستگاهِ اول (ثبت‌نام و phone-2) جا دارند
  assert.ok((await lic(owner.accessToken, `dev-${owner.email.match(/\d+/)[0]}`)).body.license, 'دستگاهِ ثبت‌نام');
  assert.ok((await lic(owner.accessToken, 'phone-2')).body.license, 'دستگاهِ دوم');
  //  O-09: سومی هم مالِ صاحبِ دکان است ⇒ هرگز بسته نمی‌شود؛ مجوز + هشدار (تا ۲.۱۴.۵ این‌جا `device_limit` بود
  //  و صاحب از دستگاهِ خودش بیرون می‌ماند). ردِ دستگاهِ کارمند: آزمونِ «کارمندِ بیرون از سقف» در پایین.
  const third = await lic(owner.accessToken, 'phone-3');
  assert.ok(third.body.license, `دستگاهِ سومِ صاحب بسته شد: ${JSON.stringify(third.body)}`);
  assert.equal(third.body.warning?.code, 'device_over_cap');

  //  صاحب یکی را باطل می‌کند ⇒ سقف دوباره سرِ جایش و هشدار می‌رود
  const devs = (await h.get('/api/me/devices', { token: owner.accessToken })).body.devices;
  const p2 = devs.find((d) => d.device_uid === 'phone-2');
  assert.equal((await h.del(`/api/me/devices/${p2.id}`, { token: owner.accessToken })).status, 200);
  const after = await lic(owner.accessToken, 'phone-3');
  assert.ok(after.body.license, 'پس از باطل کردنِ یکی، جا باز می‌شود');
  assert.equal(after.body.warning, undefined, 'زیرِ سقف هشداری نمی‌ماند');
});

test('O-09: دستگاهِ سومِ صاحبِ دکان با سقفِ ۲ ⇒ مجوز با هشدارِ روشن و راهِ برداشتنِ دستگاهِ کهنه', async () => {
  const { owner, shop } = await shopWithCap('صاحب-بیرون', 2);
  const p2 = await login(owner, 'own-2');
  const p3 = await login(owner, 'own-3');
  assert.equal(p3.status, 200, JSON.stringify(p3.body));
  await flagOn(shop);

  //  ورودِ صاحب روی دستگاهِ سوم و مجوزِ همان دستگاه با توکنِ خودش (راهِ واقعیِ برنامه)
  const r = await lic(p3.body.accessToken, 'own-3');
  assert.equal(r.status, 200);
  assert.ok(r.body.license, `صاحب از دستگاهِ خودش بیرون ماند: ${JSON.stringify(r.body)}`);
  assert.equal(r.body.reason, undefined);
  const w = r.body.warning;
  assert.ok(w, 'بیرون از سقف ⇒ هشدار برای صاحب');
  assert.equal(w.code, 'device_over_cap');
  assert.equal(w.maxDevices, 2);
  assert.equal(w.devices, 3);
  assert.equal(w.manage, 'devices', 'راهِ برداشتن: صفحهٔ دستگاه‌ها');
  assert.match(w.message, /۳ دستگاه/);
  assert.match(w.message, /برای ۲ دستگاه/);
  assert.match(w.message, /بردارید/);

  //  راهِ برداشتن کار می‌کند: صاحب دستگاهِ کهنه را از «دستگاه‌های دکان» از کار می‌اندازد ⇒ هشدار می‌رود
  const list = (await h.get('/api/shop/devices', { token: p3.body.accessToken })).body.devices;
  const old = list.find((d) => d.id === p2.body.device?.id) || list.find((d) => d.name === 'own-2');
  assert.ok(old, JSON.stringify(list));
  const off = await h.post(`/api/shop/devices/${old.id}/disable`, {}, { token: p3.body.accessToken });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  const again = await lic(p3.body.accessToken, 'own-3');
  assert.ok(again.body.license);
  assert.equal(again.body.warning, undefined, 'پس از برداشتنِ یکی دیگر بیرون از سقف نیست');
});

test('O-09: کارمندِ بیرون از سقف رد می‌شود؛ دستگاهِ صاحب نه — و صاحب با برداشتنِ دستگاهِ کهنه جا باز می‌کند', async () => {
  const { owner, shop } = await shopWithCap('کارمند-بیرون', 2);
  const staff = await staffOf(shop, 'کارمند-سقف');          // جای دوم: دستگاهِ ثبت‌نامِ کارمند
  const s2 = await login(staff, 'staff-2');                 // جای سوم ⇒ بیرون از سقف
  const o2 = await login(owner, 'owner-2');                 // جای چهارم ⇒ بیرون از سقف، ولی صاحب
  assert.equal(s2.status, 200, JSON.stringify(s2.body));
  assert.equal(o2.status, 200, JSON.stringify(o2.body));
  await flagOn(shop);

  const rej = await lic(s2.body.accessToken, 'staff-2');
  assert.equal(rej.status, 200);
  assert.equal(Boolean(rej.body.license), false, 'دستگاهِ کارمندِ بیرون از سقف مجوز گرفت');
  assert.equal(rej.body.reason, 'device_limit');
  assert.equal(rej.body.warning, undefined);
  assert.match(rej.body.message, /صاحبِ دکان/, 'کارمند باید بداند چه کسی جا باز می‌کند');

  //  همان سقف، دستگاهِ صاحب ⇒ مجوز با هشدار
  const own = await lic(o2.body.accessToken, 'owner-2');
  assert.ok(own.body.license, `دستگاهِ صاحب بسته شد: ${JSON.stringify(own.body)}`);
  assert.equal(own.body.warning?.code, 'device_over_cap');
  assert.equal(own.body.warning?.devices, 4);
  //  دستگاهِ کارمند که جای خودش را دارد مثلِ پیش، بی هشدار
  const staffFirst = await lic(staff.accessToken, `dev-${staff.email.match(/\d+/)[0]}`);
  assert.ok(staffFirst.body.license);
  assert.equal(staffFirst.body.warning, undefined);
  //  شناسهٔ ساختگی با توکنِ صاحب هم از سقف رد نمی‌شود (SEC-CLI-01 سرِ جایش)
  const forged = await lic(o2.body.accessToken, 'owner-forged');
  assert.equal(Boolean(forged.body.license), false, 'شناسهٔ ثبت‌نشده با توکنِ صاحب سقف را دور زد');
  assert.equal(forged.body.reason, 'device_limit');

  //  صاحب دستگاهِ کهنهٔ کارمند را برمی‌دارد ⇒ دستگاهِ دومِ کارمند جا می‌گیرد
  const list = (await h.get('/api/shop/devices', { token: owner.accessToken })).body.devices;
  const staffOld = (await one(`SELECT id FROM devices WHERE user_id=$1 AND device_uid=$2`,
    [staff.user.id, `dev-${staff.email.match(/\d+/)[0]}`])).id;
  assert.ok(list.some((d) => d.id === staffOld), JSON.stringify(list));
  assert.equal((await h.post(`/api/shop/devices/${staffOld}/disable`, {}, { token: owner.accessToken })).status, 200);
  assert.ok((await lic(s2.body.accessToken, 'staff-2')).body.license, 'پس از برداشتنِ دستگاهِ کهنه، کارمند جا می‌گیرد');
});

test('O-09: دستگاهِ ثبت‌شدهٔ صاحب که ۹۰ روز دیده نشده، وقتی جاها پر است (پرچم خاموش) ⇒ مجوز با هشدار، نه قفل', async () => {
  const { owner, shop } = await shopWithCap('صاحب-کهنه', 2);
  await staffOf(shop, 'کارمند-جا');                        // جاها پر: ثبت‌نامِ صاحب + ثبت‌نامِ کارمند
  const old = await login(owner, 'owner-tablet');
  assert.equal(old.status, 200, JSON.stringify(old.body));
  const long = Date.now() - 120 * 24 * 3600 * 1000;
  await query(`UPDATE devices SET created_at=$2, last_seen_at=$2 WHERE user_id=$1 AND device_uid='owner-tablet'`,
    [owner.user.id, long]);

  //  توکنِ دستگاهِ دیگر (مثلِ نسخهٔ وب که دستگاه را در ورود نمی‌گوید) — ردیفِ دستگاه مالِ خودِ صاحب است
  const r = await lic(owner.accessToken, 'owner-tablet');
  assert.ok(r.body.license, `دستگاهِ خودِ صاحب قفل شد: ${JSON.stringify(r.body)}`);
  assert.equal(r.body.warning?.code, 'device_over_cap');
  assert.equal(r.body.warning?.devices, 3);
  const row = await one(`SELECT last_seen_at FROM devices WHERE user_id=$1 AND device_uid='owner-tablet'`, [owner.user.id]);
  assert.ok(Number(row.last_seen_at) > long, 'دستگاهِ برگشته دوباره شمرده می‌شود');
  //  شناسهٔ تازه با همان توکن ⇒ همچنان رد (فقط دستگاهِ ثبت‌شدهٔ خودِ صاحب)
  assert.equal((await lic(owner.accessToken, 'owner-new')).body.reason, 'device_limit');
});
