'use strict';
/**
 * بخشِ پمپ‌بنزین — از ساختِ پمپ تا مجوزِ امضاشده.
 *
 * چیزی که باید ثابت شود:
 *   ۱) هر حساب پمپِ خودش را می‌گیرد و دفترش از دکان‌ها جداست
 *   ۲) کدِ شش‌رقمیِ پمپ اشتراکِ پمپ را باز می‌کند — و کدِ دکان نه
 *   ۳) پوشهٔ ابری کار می‌کند و کارمند نمی‌تواند رویش بنویسد
 *   ۴) مجوزِ پمپ شنونده‌ی خودش را دارد، پس مجوزِ دکان روی پمپ نمی‌نشیند
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

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

/** یک کاربر که پمپِ خودش را دارد. */
async function userWithStation(name = 'پمپ‌دار', code = '') {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, code: made.body.station.code };
}

/** اشتراکِ پمپ را مستقیم صادر می‌کند — برای آزمونی که کد وسط راه نیست. */
async function grantPump(stationId, days = 30) {
  return require('../src/lib/subscriptions').pump.grant(stationId, { plan: 'custom', days });
}

// ── ۱) پمپ و حساب ────────────────────────────────────────────────

test('حساب تازه پمپ ندارد و سرور این را خطا نمی‌داند', async () => {
  const u = await h.newUser('بی‌پمپ', 'pump');
  const me = await h.get('/api/pump/me', { token: u.accessToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.station, null);
});

test('هر حساب یک پمپ می‌سازد و کدش یکتاست', async () => {
  const a = await userWithStation('الف', 'pump-alef');
  assert.equal(a.code, 'pump-alef');

  //  همان حساب، پمپِ دوم نمی‌سازد
  const again = await h.post('/api/pump', { name: 'دومی' }, { token: a.accessToken });
  assert.equal(again.status, 409);

  //  و حسابِ دیگری همان کد را نمی‌گیرد
  const b = await h.newUser('ب', 'pump');
  const clash = await h.post('/api/pump', { code: 'pump-alef' }, { token: b.accessToken });
  assert.equal(clash.status, 409);
  assert.equal(clash.body.error.code, 'code_taken');
});

test('کدِ پمپ پاک‌سازی می‌شود و حرفِ بی‌جا نمی‌پذیرد', async () => {
  const u = await h.newUser('کددار', 'pump');
  const made = await h.post('/api/pump', { code: '  Pump 2 ی  ' }, { token: u.accessToken });
  assert.equal(made.status, 201);
  //  فاصله و حرفِ غیرانگلیسی به خط تیره، و همه کوچک
  assert.match(made.body.station.code, /^[a-z0-9-]+$/);
});

// ── ۲) نشانیِ سرورِ خانگی ────────────────────────────────────────

test('برنامه نشانیِ خانگی را ثبت می‌کند و کارمند همان را می‌خواند', async () => {
  const owner = await userWithStation('خانگی', 'pump-home');

  const put = await h.post('/api/pump/home',
    { homeUrl: 'https://pump-home.example.ir' }, { token: owner.accessToken });
  assert.equal(put.status, 200);
  assert.equal(put.body.station.homeUrl, 'https://pump-home.example.ir');

  //  و از راهِ «پمپِ من» هم دیده می‌شود — همان چیزی که اپِ کارمند
  //  صدا می‌زند تا دیگر از کسی نشانی نپرسد
  const me = await h.get('/api/pump/me', { token: owner.accessToken });
  assert.equal(me.body.station.homeUrl, 'https://pump-home.example.ir');
  assert.ok(me.body.station.homeSeenAt > 0);
});

// ── ۳) اشتراک و کدِ شش‌رقمی ──────────────────────────────────────

test('کدِ شش‌رقمیِ پمپ اشتراکِ پمپ را باز می‌کند', async () => {
  const t = await adminToken();
  const owner = await userWithStation('کددار', 'pump-vip');

  const made = await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 45, note: 'هدیه' }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  assert.match(String(made.body.code), /^\d{6}$/);

  const before = await h.get('/api/pump/subscription', { token: owner.accessToken });
  assert.notEqual(before.body.entitlement.source, 'subscription');

  const used = await h.post('/api/pump/vip/redeem',
    { code: made.body.code }, { token: owner.accessToken });
  assert.equal(used.status, 201, JSON.stringify(used.body));
  assert.equal(used.body.entitlement.source, 'subscription');
  assert.equal(used.body.entitlement.app, 'pump');

  //  یک بار مصرف
  const twice = await h.post('/api/pump/vip/redeem',
    { code: made.body.code }, { token: owner.accessToken });
  assert.equal(twice.status, 403);
});

test('کدِ دکان روی پمپ کار نمی‌کند و برعکس', async () => {
  const t = await adminToken();
  const owner = await userWithStation('مرزی', 'pump-cross');

  //  کدی که برای بخشِ شاپ ساخته شده
  const shopCode = await h.post('/api/admin/vip-codes',
    { plan: 'custom', days: 30 }, { token: t });
  assert.equal(shopCode.status, 201);

  const tried = await h.post('/api/pump/vip/redeem',
    { code: shopCode.body.code }, { token: owner.accessToken });
  //  دفترِ کدها جداست، پس این کد اینجا اصلاً وجود ندارد
  assert.equal(tried.status, 404);
  assert.equal(tried.body.error.code, 'bad_code');
});

test('قابلیتِ دکان در اشتراکِ پمپ نمی‌نشیند', async () => {
  const owner = await userWithStation('قابلیت', 'pump-feat');
  await require('../src/lib/subscriptions').pump.grant(owner.stationId, {
    plan: 'custom', days: 30,
    //  «barcode» مالِ کاتالوگِ دکان است، «safe» مالِ پمپ
    features: ['barcode', 'safe'],
  });
  const st = await h.get('/api/pump/subscription', { token: owner.accessToken });
  assert.ok(st.body.entitlement.features.includes('safe'));
  assert.ok(!st.body.entitlement.features.includes('barcode'));
});

test('دورهٔ آزمایشیِ پمپ جدا از دکان تنظیم می‌شود', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const owner = await userWithStation('بی‌آزمایش', 'pump-notrial');
  const st = await h.get('/api/pump/subscription', { token: owner.accessToken });
  assert.equal(st.body.entitlement.source, 'free');
  assert.equal(st.body.entitlement.trial.enabled, false);
  await plans.setConfig('pump_trial_days', '14');
});

// ── ۴) پوشهٔ ابری ────────────────────────────────────────────────

test('پوشهٔ پمپ نوشته و خوانده می‌شود و rev بالا می‌رود', async () => {
  const owner = await userWithStation('پوشه', 'pump-files');
  const tok = owner.accessToken;

  const w1 = await h.put('/api/pump/files/live.json',
    { data: { safe: 120, at: '۱۴۰۵/۰۶/۲۳' } }, { token: tok });
  assert.equal(w1.status, 200, JSON.stringify(w1.body));

  const w2 = await h.put('/api/pump/files/station.json',
    { data: { name: 'پمپ پوشه' } }, { token: tok });
  assert.ok(w2.body.rev > w1.body.rev, 'rev باید بالا برود');

  const read = await h.get('/api/pump/files/live.json', { token: tok });
  assert.equal(read.status, 200);
  assert.equal(read.body.data.safe, 120);

  const list = await h.get('/api/pump/files', { token: tok });
  assert.equal(list.body.files.length, 2);
  assert.equal(list.body.rev, w2.body.rev);
});

test('نامِ فایلِ بدقواره پذیرفته نمی‌شود', async () => {
  const owner = await userWithStation('بدنام', 'pump-badpath');
  for (const bad of ['..', '.hidden', 'a/b']) {
    const r = await h.put(`/api/pump/files/${encodeURIComponent(bad)}`,
      { data: {} }, { token: owner.accessToken });
    assert.ok(r.status >= 400, `«${bad}» نباید پذیرفته می‌شد`);
  }
});

test('کارمند فقط در صندوقِ ورودی می‌نویسد، نه در دفتر', async () => {
  const owner = await userWithStation('صاحب', 'pump-staff');
  const staff = await h.newUser('کارمند', 'pump');

  //  کارمند را عضوِ همین پمپ می‌کنیم
  await query(
    `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), owner.stationId, staff.user.id, now()]
  );

  const blocked = await h.put('/api/pump/files/live.json',
    { data: { safe: 1 } }, { token: staff.accessToken });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'read_only');

  const allowed = await h.put('/api/pump/files/inbox.json',
    { data: { messages: [{ from: 'کارمند', text: 'تیل کم است' }] } }, { token: staff.accessToken });
  assert.equal(allowed.status, 200, JSON.stringify(allowed.body));

  //  و خواندن برایش باز است
  const read = await h.get('/api/pump/files/inbox.json', { token: staff.accessToken });
  assert.equal(read.status, 200);
});

test('پمپِ دیگری پوشهٔ این پمپ را نمی‌بیند', async () => {
  const a = await userWithStation('یکی', 'pump-iso-a');
  const b = await userWithStation('دیگری', 'pump-iso-b');

  await h.put('/api/pump/files/live.json', { data: { secret: 42 } }, { token: a.accessToken });
  const peek = await h.get('/api/pump/files/live.json', { token: b.accessToken });
  //  پوشه‌ی b خالی است — شناسه از توکن می‌آید، نه از مسیر
  assert.equal(peek.status, 404);
});

// ── ۵) مجوز ──────────────────────────────────────────────────────

test('مجوزِ پمپ شنونده‌ی خودش را دارد', async () => {
  const owner = await userWithStation('مجوزدار', 'pump-lic');
  await grantPump(owner.stationId, 60);
  //  ⛔ مجوز فقط برای کامپیوترِ ثبت‌شده — اول بند می‌شود
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: 'pc-1', name: 'کامپیوترِ پمپ' } }, { token: owner.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));

  const r = await h.post('/api/pump/license',
    { device: { uid: 'pc-1', name: 'کامپیوترِ پمپ' } }, { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.ok(r.body.license, 'مجوز باید صادر شود');

  const payload = JSON.parse(
    Buffer.from(r.body.license.split('.')[1], 'base64').toString('utf8')
  );
  assert.equal(payload.aud, 'tohid-pump-app');
  assert.equal(payload.duid, 'pc-1');
  assert.ok(payload.feat.includes('safe'));
  //  و از خودِ اشتراک دیرتر تمام نمی‌شود
  assert.ok(payload.exp <= payload.sub_ends);
});

test('بی‌اشتراک و بی‌آزمایش، مجوزی صادر نمی‌شود', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const owner = await userWithStation('بی‌مجوز', 'pump-nolic');
  await h.post('/api/pump/device/bind', { device: { uid: 'pc-2' } }, { token: owner.accessToken });

  const r = await h.post('/api/pump/license',
    { device: { uid: 'pc-2' } }, { token: owner.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.license, null);
  assert.equal(r.body.reason, 'no_subscription');
  //  ولی بخش‌های همیشه‌باز سرِ جایشان‌اند
  assert.ok(r.body.features.includes('debtors'));
  await plans.setConfig('pump_trial_days', '14');
});

test('⛔ برای کامپیوترِ ثبت‌نشده مجوزی امضا نمی‌شود — سقفِ دستگاه از این در دور نمی‌خورد', async () => {
  const owner = await userWithStation('بی‌دستگاه', 'pump-nodev');
  await grantPump(owner.stationId, 60);
  const r = await h.post('/api/pump/license',
    { device: { uid: 'pc-made-up-1' } }, { token: owner.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'device_not_registered');
});

// ── ۶) مرزِ دو بخش ───────────────────────────────────────────────

test('اشتراکِ دکان، پمپِ همان آدم را باز نمی‌کند', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  await plans.setConfig('trial_days', '0');

  //  یک آدم، دو نشست — توکنِ هر بخش فقط همان بخش را باز می‌کند
  const u = await h.newUser('دوکاره', 'shop');
  const asPump = await h.signIn(u, 'pump');
  const shop = await h.post('/api/shop', { name: 'دکانِ دوکاره' }, { token: u.accessToken });
  const station = await h.post('/api/pump', { code: 'pump-both' }, { token: asPump.accessToken });

  //  فقط دکان اشتراک می‌گیرد
  await require('../src/lib/subscriptions').grant(shop.body.shop.id, { plan: 'custom', days: 30 });

  const pumpState = await h.get('/api/pump/subscription', { token: asPump.accessToken });
  assert.equal(pumpState.body.entitlement.source, 'free',
    'اشتراکِ دکان نباید پمپ را باز کند');

  //  و برعکس: اشتراکِ پمپ دکان را باز نمی‌کند
  await require('../src/lib/subscriptions').pump.grant(station.body.station.id,
    { plan: 'custom', days: 30 });
  const shopState = await h.get('/api/me/subscription', { token: u.accessToken });
  //  دکان اشتراکِ خودش را دارد و اشتراکِ پمپ چیزی به آن اضافه نکرده
  assert.equal(shopState.body.source, 'subscription');

  await plans.setConfig('pump_trial_days', '14');
  await plans.setConfig('trial_days', '14');
});

// ── ۷) رمزِ فقط‌خواندنی — «کارمند هیچ‌چیز نمی‌پرسد» ────────────────

test('برنامه نشانی و رمزِ فقط‌خواندنی را می‌سپارد و عضو هر دو را می‌گیرد', async () => {
  const owner = await userWithStation('سپرده', 'pump-key');

  const put = await h.post('/api/pump/home', {
    homeUrl: 'https://key.example.ir', readKey: 'rk_secret_123',
  }, { token: owner.accessToken });
  assert.equal(put.status, 200);

  //  صاحب که خودش سپرده، می‌گیردش
  const me = await h.get('/api/pump/me', { token: owner.accessToken });
  assert.equal(me.body.home.url, 'https://key.example.ir');
  assert.equal(me.body.home.readKey, 'rk_secret_123');
  assert.equal(me.body.home.station, 'pump-key');

  //  و کارمندِ همان پمپ هم — همین است که کیو‌آر را بی‌کار می‌کند
  const staff = await h.newUser('کارمندِ کلید', 'pump');
  await query(
    `INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), owner.stationId, staff.user.id, now()]
  );
  const his = await h.get('/api/pump/me', { token: staff.accessToken });
  assert.equal(his.body.home.readKey, 'rk_secret_123');
  assert.equal(his.body.home.url, 'https://key.example.ir');
});

test('رمزِ پمپ به کسی که عضوش نیست نمی‌رسد', async () => {
  const a = await userWithStation('کلیددار', 'pump-key-a');
  await h.post('/api/pump/home',
    { homeUrl: 'https://a.example.ir', readKey: 'rk_a' }, { token: a.accessToken });

  const b = await userWithStation('غریبه', 'pump-key-b');
  const his = await h.get('/api/pump/me', { token: b.accessToken });
  //  پمپِ خودش را می‌بیند، نه پمپِ a را
  assert.equal(his.body.station.code, 'pump-key-b');
  assert.notEqual(his.body.home.readKey, 'rk_a');
  assert.equal(his.body.home.readKey, '');
});

test('رمز در دیتابیس به شکلِ خام نیست', async () => {
  const owner = await userWithStation('رمزی', 'pump-key-enc');
  await h.post('/api/pump/home',
    { homeUrl: 'https://enc.example.ir', readKey: 'rk_plain_text_key' },
    { token: owner.accessToken });

  const row = await require('../src/db').one(
    'SELECT read_key_enc FROM stations WHERE id=$1', [owner.stationId]
  );
  assert.ok(row.read_key_enc.startsWith('v1.'), 'باید رمزگذاری‌شده باشد');
  assert.ok(!row.read_key_enc.includes('rk_plain_text_key'), 'رمزِ خام نباید در ردیف باشد');
});

test('رمز در پاسخِ پنلِ مدیریت نمی‌آید — فقط «دارد یا ندارد»', async () => {
  const t = await adminToken();
  const owner = await userWithStation('پنلی', 'pump-key-panel');
  await h.post('/api/pump/home',
    { homeUrl: 'https://panel.example.ir', readKey: 'rk_never_shown' },
    { token: owner.accessToken });

  const seen = await h.get(`/api/admin/pump/stations/${owner.stationId}`, { token: t });
  assert.equal(seen.status, 200);
  assert.equal(seen.body.station.hasReadKey, true);
  assert.ok(!JSON.stringify(seen.body).includes('rk_never_shown'),
    'رمز نباید هیچ‌جای پاسخِ پنل باشد');
});

test('ثبتِ نشانی بی رمز هم کار می‌کند و رمزِ قبلی را پاک نمی‌کند', async () => {
  const owner = await userWithStation('بی‌رمز', 'pump-key-keep');
  await h.post('/api/pump/home',
    { homeUrl: 'https://one.example.ir', readKey: 'rk_keep' }, { token: owner.accessToken });

  //  سرورِ خانگیِ به‌روزنشده رمزِ جدا ندارد و فقط نشانی می‌فرستد
  const again = await h.post('/api/pump/home',
    { homeUrl: 'https://two.example.ir' }, { token: owner.accessToken });
  assert.equal(again.status, 200);

  const me = await h.get('/api/pump/me', { token: owner.accessToken });
  assert.equal(me.body.home.url, 'https://two.example.ir');
  assert.equal(me.body.home.readKey, 'rk_keep', 'رمزِ قبلی باید بماند');
});

/* ==========================================================
   افرادِ پمپ در پنلِ مدیریت

   خواستهٔ صاحب مخزن: «ببینم افراد رو، اشتراک‌هاشون و غیره؛ بخشِ
   فروشگاه خیلی تکمیل است، شبیه همون باشه.»
   ========================================================== */

test('پنل افرادِ پمپ و حالِ اشتراکشان را نشان می‌دهد', async () => {
  const t = await adminToken();
  const owner = await userWithStation('کریمِ پنل', 'pump-users-1');

  const r = await h.get('/api/admin/pump/users', { token: t });
  assert.equal(r.status, 200, JSON.stringify(r.body));

  const mine = r.body.users.find((u) => u.station_id === owner.stationId);
  assert.ok(mine, 'صاحبِ پمپ باید در فهرست باشد');
  assert.equal(mine.role, 'owner');
  assert.ok(mine.station_name, 'نامِ پمپ هم باید بیاید');
  //  ستونِ اشتراک هست حتی وقتی هنوز اشتراکی نخریده — یعنی null، نه غایب
  assert.ok('sub_status' in mine, 'ستونِ حالِ اشتراک باید باشد');
});

test('جست‌وجو هم با نامِ شخص کار می‌کند هم با نامِ پمپ', async () => {
  const t = await adminToken();
  await userWithStation('نصرالله', 'pump-users-search');

  const byPerson = await h.get('/api/admin/pump/users?q=نصرالله', { token: t });
  assert.equal(byPerson.status, 200);
  assert.ok(byPerson.body.users.length >= 1, 'با نامِ شخص پیدا شود');

  const byStation = await h.get('/api/admin/pump/users?q=pump-users-search', { token: t });
  assert.ok(byStation.body.users.length >= 1, 'با کدِ پمپ هم پیدا شود');
});

test('⚠️ کسی که فقط دکان دارد در افرادِ پمپ پیدا نمی‌شود', async () => {
  const t = await adminToken();
  //  یک کاربرِ بخشِ دکان، بی هیچ پمپی
  const shopOnly = await h.newUser('فقط‌دکان', 'shop');

  const r = await h.get('/api/admin/pump/users', { token: t });
  const found = r.body.users.find((u) => u.id === shopOnly.user.id);
  assert.equal(found, undefined, 'دو دفتر جدا هستند و باید جدا بمانند');
});

/* ==========================================================
   ⛔ حسابِ پمپی که هنوز پمپ ندارد هم در پنل دیده می‌شود (مهاجرتِ ۰۳۰)

   گزارشِ صاحب سامانه: «حسابی که در برنامهٔ پمپ ساختم در پنل دیده
   نمی‌شود.» فهرست فقط از `station_members` می‌آمد.
   ========================================================== */

test('⛔ حسابِ پمپ بی پمپ در افراد هست — با station_id خالی و no_station', async () => {
  const t = await adminToken();
  const lone = await h.newUser('بی‌پمپِ پنل', 'pump');

  const r = await h.get('/api/admin/pump/users', { token: t });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const found = r.body.users.find((u) => u.id === lone.user.id);
  assert.ok(found, 'حسابِ تازهٔ پمپ باید دیده شود حتی بی پمپ');
  assert.equal(found.station_id, null);
  assert.equal(found.no_station, true);
  assert.ok(r.body.total >= r.body.users.length, 'شمارِ کل همان قاعده را دارد');

  //  با ایمیل هم پیدا می‌شود
  const byEmail = await h.get(`/api/admin/pump/users?q=${encodeURIComponent(lone.user.email)}`, { token: t });
  assert.ok(byEmail.body.users.some((u) => u.id === lone.user.id), 'با ایمیل پیدا شود');
});

test('⛔ و وقتی پمپ ساخت، یک ردیف با پمپ می‌ماند — ردیفِ بی‌پمپ نه', async () => {
  const t = await adminToken();
  const owner = await userWithStation('پمپ‌ساز', 'pump-users-once');
  const r = await h.get('/api/admin/pump/users', { token: t });
  const rows = r.body.users.filter((u) => u.id === owner.user.id);
  assert.equal(rows.length, 1, JSON.stringify(rows));
  assert.equal(rows[0].station_id, owner.stationId);
  assert.equal(rows[0].no_station, false);
});

test('⛔ حسابِ دکان که یک بار هم از پمپ نیامده، هنوز در افرادِ پمپ نیست', async () => {
  const t = await adminToken();
  const shopOnly = await h.newUser('فقط‌دکانِ دوم', 'shop');
  const r = await h.get('/api/admin/pump/users', { token: t });
  assert.equal(r.body.users.find((u) => u.id === shopOnly.user.id), undefined);
});
