'use strict';
/**
 * «سرور تشخیص می‌دهد برنامه کدام است و قبولش می‌کند».
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «لاگین‌ها را ببین، کدها را بزنم سرور اجازه می‌دهد و سرور تشخیص
 *  می‌دهد که برنامه کدام است و قبول کند… ببین برنامه‌ها و سرورهای خیلی
 *  حرفه‌ای چه‌جوری بدون مشکل کار می‌کنند.»
 *
 * سرور از روزِ اول نشست را به بخشش مهر می‌زد، ولی **فقط از یک جا**
 * می‌پرسید کدام بخش است: `app` در بدنه. و اپِ کارمندانِ پمپ که با گوگل
 * وارد می‌شود بدنه‌اش `app` ندارد — فقط هدرِ `X-App-Id` را دارد. یعنی
 * نشستش به بخشِ **دکان** مهر می‌خورد و همان لحظه `‎/api/pump/me‎`
 * می‌گفت «چنین نشستی نیست». کاربر «ورود با گوگل» می‌زد و هیچ اتفاقی
 * نمی‌افتاد.
 *
 * این پرونده همان قرار را قفل می‌کند: سه جای گفتن، یک جای تصمیم
 * (`lib/tenancy.appOfRequest`)، و مرزی که با هیچ‌کدامشان جابه‌جا
 * نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one } = require('../src/db');
const tenancy = require('../src/lib/tenancy');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/** بخشی که توکنِ این نشست به آن مهر خورده — از خودِ دیتابیس. */
async function appOfToken(accessToken) {
  const { hashToken } = require('../src/lib/tokens');
  const row = await one('SELECT app FROM tokens WHERE token_hash=$1', [hashToken(accessToken)]);
  return row ? (row.app || 'shop') : null;
}

/* ══════════════════════════════════════════════════════════════════
   ۱) خودِ قاعده — یک تابع، نه چهار کپی
   ══════════════════════════════════════════════════════════════════ */

test('نامِ بخش از هر شکلی که برنامه‌ها می‌گویند شناخته می‌شود', () => {
  for (const name of ['shop', 'SHOP', ' shop ', 'tohid-shop-app', 'shop-app']) {
    assert.equal(tenancy.sectionOf(name), 'shop', name);
  }
  for (const name of ['pump', 'PUMP', 'tohid-pump-app', 'pump-app', 'station']) {
    assert.equal(tenancy.sectionOf(name), 'pump', name);
  }
});

test('نامِ ناشناخته بخشی نمی‌سازد و خطا هم نمی‌دهد', () => {
  //  ⚠️ این `byApp` نیست: ورودی از درخواست می‌آید و یک نامِ عجیب نباید
  //  ورودِ کسی را بشکند. نامِ جدول همچنان از درِ بستهٔ `byApp` می‌آید.
  for (const name of ['', null, undefined, 'وب', 'kar', '../shops']) {
    assert.equal(tenancy.sectionOf(name), '');
  }
  assert.throws(() => tenancy.byApp('kar'), /ناشناخته/);
});

test('بدنه از هدر جلو می‌زند، و هدر از نشانی', () => {
  const req = (body, headers, q) => ({ body, headers, query: q });
  assert.equal(tenancy.appOfRequest(req({ app: 'pump' }, { 'x-app-id': 'shop' }, {})), 'pump');
  assert.equal(tenancy.appOfRequest(req({}, { 'x-app-id': 'tohid-pump-app' }, { app: 'shop' })), 'pump');
  assert.equal(tenancy.appOfRequest(req({}, {}, { app: 'pump' })), 'pump');
  //  نگفتن یعنی دکان — هر برنامه‌ای که امروز دستِ کاربران است چیزی
  //  نمی‌گوید و نباید بیرون بیفتد
  assert.equal(tenancy.appOfRequest(req({}, {}, {})), 'shop');
  assert.equal(tenancy.appOfRequest({}), 'shop');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ورود — همان راهی که هر برنامه می‌رود
   ══════════════════════════════════════════════════════════════════ */

test('ورود با app در بدنه، نشست را به همان بخش مهر می‌زند', async () => {
  const u = await h.newUser('بدنه‌گو', 'pump');
  assert.equal(await appOfToken(u.accessToken), 'pump');

  const again = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-body' }, app: 'pump',
  });
  assert.equal(again.status, 200);
  assert.equal(await appOfToken(again.body.accessToken), 'pump');
});

test('ورود با هدرِ X-App-Id — همان راهی که اپِ کارمندان می‌رود', async () => {
  const u = await h.newUser('هدرگو', 'pump');
  //  ⚠️ بدنه `app` ندارد، دقیقاً مثلِ `kar/cloud.js`
  const r = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-kar' },
  }, { headers: { 'X-App-Id': 'tohid-pump-app' } });
  assert.equal(r.status, 200);
  assert.equal(await appOfToken(r.body.accessToken), 'pump');

  //  و همان توکن واقعاً مسیرهای پمپ را باز می‌کند
  await h.post('/api/pump', { name: 'پمپِ هدرگو' }, { token: r.body.accessToken });
  const me = await h.get('/api/pump/me', { token: r.body.accessToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.station.name, 'پمپِ هدرگو');
});

test('ثبت‌نامِ سه‌پله هم هدر را می‌پذیرد', async () => {
  const email = `hdr${Date.now()}@test.local`;
  const started = await h.post('/api/auth/register/start',
    { name: 'تازه', email, password: 'Passw0rd!test' });
  assert.equal(started.status, 201, JSON.stringify(started.body));
  const verified = await h.post('/api/auth/register/verify', { email, code: started.body.devCode });
  const done = await h.post('/api/auth/register/complete', {
    ticket: verified.body.ticket, name: 'تازه', password: 'Passw0rd!test',
    device: { deviceId: 'd-new' }, terms: { accepted: true },
  }, { headers: { 'X-App-Id': 'tohid-pump-app' } });
  assert.equal(done.status, 201, JSON.stringify(done.body));
  assert.equal(await appOfToken(done.body.accessToken), 'pump');
});

test('نگفتن همان دکان است — نسخه‌های امروزِ دستِ کاربر بیرون نمی‌افتند', async () => {
  const u = await h.newUser('ساکت');
  const r = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-quiet' },
  });
  assert.equal(await appOfToken(r.body.accessToken), 'shop');
  assert.equal((await h.get('/api/me', { token: r.body.accessToken })).status, 200);
});

test('هدرِ بی‌معنی چیزی را عوض نمی‌کند', async () => {
  const u = await h.newUser('هدرِ خراب');
  const r = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-junk' },
  }, { headers: { 'X-App-Id': '../../etc/passwd' } });
  assert.equal(r.status, 200);
  assert.equal(await appOfToken(r.body.accessToken), 'shop');
});

test('هدر دری باز نمی‌کند — پمپ‌نداشتن با هیچ ادعایی پمپ نمی‌شود', async () => {
  const u = await h.newUser('مدعی');
  //  همین آدم با هدرِ پمپ وارد می‌شود، ولی هیچ پمپی ندارد
  const r = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'd-claim' },
  }, { headers: { 'X-App-Id': 'pump' } });
  const me = await h.get('/api/pump/me', { token: r.body.accessToken });
  //  نشست هست، ولی دفتری نیست: دسترسی از عضویت می‌آید نه از هدر
  assert.equal(me.status, 200);
  assert.equal(me.body.station, null);
});

test('تازه‌سازیِ نشست بخشِ توکن را عوض نمی‌کند، حتی با هدرِ دروغ', async () => {
  const u = await h.newUser('تازه‌شو', 'pump');
  const r = await h.post('/api/auth/refresh', { refreshToken: u.refreshToken },
    { headers: { 'X-App-Id': 'shop' } });
  assert.equal(r.status, 200);
  assert.equal(await appOfToken(r.body.accessToken), 'pump',
    'بخش از ردیفِ خودِ توکن می‌آید، نه از ادعای درخواست');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) خروج — هر بخش، دفترِ خودش
   ══════════════════════════════════════════════════════════════════ */

test('خروج از همهٔ دستگاه‌ها در دکان، نشستِ پمپ را نمی‌کشد', async () => {
  const u = await h.newUser('دوکاره');
  const pump = await h.signIn(u, 'pump');

  const out = await h.post('/api/auth/logout-all', {}, { token: u.accessToken });
  assert.equal(out.status, 200);
  assert.equal(out.body.app, 'shop');

  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 401,
    'نشستِ خودِ دکان می‌رود');
  assert.equal((await h.get('/api/pump/me', { token: pump.accessToken })).status, 200,
    'و نشستِ پمپ سرِ جایش می‌ماند');
});

test('خروج از همهٔ دستگاه‌ها در پمپ، نشستِ دکان را نمی‌کشد', async () => {
  const u = await h.newUser('دوکارهٔ دوم');
  const pump = await h.signIn(u, 'pump');

  const out = await h.post('/api/auth/logout-all', {}, { token: pump.accessToken });
  assert.equal(out.body.app, 'pump');
  assert.equal((await h.get('/api/pump/me', { token: pump.accessToken })).status, 401);
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 200);
});

test('خروج از همهٔ دستگاه‌ها هر دو نوعِ توکن را می‌برد', async () => {
  const u = await h.newUser('خروجی');
  await h.post('/api/auth/logout-all', {}, { token: u.accessToken });
  const again = await h.post('/api/auth/refresh', { refreshToken: u.refreshToken });
  assert.equal(again.status, 401, 'توکنِ تازه‌سازی هم باطل شده');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) عوض کردنِ رمز — نشستِ لو‌رفته زنده نمی‌ماند
   ══════════════════════════════════════════════════════════════════ */

test('عوض کردنِ رمز، نشستِ گوشی‌های دیگر را می‌بندد', async () => {
  const u = await h.newUser('رمزگردان');
  const other = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'gooshi-digar' },
  });
  assert.equal(other.status, 200);

  const r = await h.post('/api/auth/password',
    { currentPassword: u.password, newPassword: 'Tazeh!Passw0rd' }, { token: u.accessToken });
  assert.equal(r.status, 200);
  assert.ok(r.body.closedSessions >= 1, 'باید بگوید چند نشست بسته شد');

  assert.equal((await h.get('/api/me', { token: other.body.accessToken })).status, 401);
});

test('نشستِ خودِ همان دستگاه با عوض شدنِ رمز نمی‌رود', async () => {
  const u = await h.newUser('همان‌جا');
  await h.post('/api/auth/password',
    { currentPassword: u.password, newPassword: 'Tazeh!Passw0rd2' }, { token: u.accessToken });
  assert.equal((await h.get('/api/me', { token: u.accessToken })).status, 200,
    'وگرنه کاربر با عوض کردنِ رمز از برنامهٔ خودش هم بیرون می‌افتاد');
});

test('رمزِ فعلیِ غلط هیچ نشستی را نمی‌بندد', async () => {
  const u = await h.newUser('حدس‌زن');
  const other = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'gooshi-salem' },
  });
  const r = await h.post('/api/auth/password',
    { currentPassword: 'اشتباه', newPassword: 'Tazeh!Passw0rd3' }, { token: u.accessToken });
  assert.equal(r.status, 401);
  assert.equal((await h.get('/api/me', { token: other.body.accessToken })).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پلن‌های بازِ هر بخش
   ══════════════════════════════════════════════════════════════════ */

test('مسیرِ بازِ /plans با app=pump سه پلنِ پمپ را می‌دهد', async () => {
  const r = await h.get('/api/plans?app=pump');
  assert.equal(r.status, 200);
  assert.equal(r.body.app, 'pump');
  const codes = r.body.plans.map(p => p.code).sort();
  assert.deepEqual(codes, ['perm', 'std', 'vip']);
  assert.equal(r.body.currency, 'دالر', 'واحدِ پولِ پمپ دالر است، نه افغانی');
});

test('مسیرِ بازِ /plans بی app همان دکان است — سایتِ امروز دست‌نخورده', async () => {
  const r = await h.get('/api/plans');
  assert.equal(r.body.app, 'shop');
  const codes = r.body.plans.map(p => p.code);
  assert.ok(codes.includes('m1'), JSON.stringify(codes));
  assert.ok(!codes.includes('std'), 'پلنِ پمپ نباید در فهرستِ دکان بیاید');
  assert.equal(r.body.currency, 'افغانی');
});

test('پلن‌های دو بخش قیمتِ هم را عوض نمی‌کنند', async () => {
  const shop = await h.get('/api/plans');
  const pump = await h.get('/api/plans?app=pump');
  const shopCodes = new Set(shop.body.plans.map(p => p.code));
  for (const p of pump.body.plans) {
    assert.ok(!shopCodes.has(p.code), `کدِ ${p.code} در دو بخش یکی است — خطرِ قاطی شدنِ پول`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۶) حذفِ عضو — همان نشانی‌ای که نسخهٔ وب صدا می‌زند
   ══════════════════════════════════════════════════════════════════ */

async function shopWithStaff() {
  const owner = await h.newUser('صاحب');
  const made = await h.post('/api/shop', { name: 'دکانِ عضوها' }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  //  اشتراک لازم است تا کدِ شاگرد ساخته شود (`multi_device`)
  const code = await h.post('/api/shop/staff-code', {}, { token: owner.accessToken });
  assert.equal(code.status, 201, JSON.stringify(code.body));

  const staff = await h.newUser('شاگرد');
  const joined = await h.post('/api/shop/join',
    { code: code.body.code || code.body.staffCode?.code }, { token: staff.accessToken });
  assert.ok(joined.status < 300, JSON.stringify(joined.body));
  return { owner, staff, shopId: made.body.shop.id };
}

test('POST /shop/members/<شناسه>/remove همان کارِ DELETE را می‌کند', async () => {
  const { owner, staff } = await shopWithStaff();
  const members = await h.get('/api/shop/members', { token: owner.accessToken });
  const row = members.body.members.find(m => m.userId === staff.user.id);
  assert.ok(row, 'شاگرد باید در فهرست باشد');

  const r = await h.post(`/api/shop/members/${row.id}/remove`, {}, { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));

  const after = await h.get('/api/shop/members', { token: owner.accessToken });
  assert.ok(!after.body.members.some(m => m.userId === staff.user.id));
});

test('شناسه‌ی **کاربر** هم پذیرفته می‌شود — دکمهٔ حذفِ سایت همین را می‌فرستد', async () => {
  const { owner, staff } = await shopWithStaff();
  //  ⛔ تا دیروز فقط شناسه‌ی عضویت پذیرفته می‌شد و نسخهٔ وب که `userId`
  //  را می‌فرستد همیشه «این عضو در دکان شما نیست» می‌گرفت.
  const r = await h.post(`/api/shop/members/${staff.user.id}/remove`, {},
    { token: owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));

  const after = await h.get('/api/shop/members', { token: owner.accessToken });
  assert.ok(!after.body.members.some(m => m.userId === staff.user.id));
});

test('شاگرد نمی‌تواند کسی را بردارد', async () => {
  const { staff, owner } = await shopWithStaff();
  const members = await h.get('/api/shop/members', { token: owner.accessToken });
  const ownerRow = members.body.members.find(m => m.role === 'owner');
  const r = await h.post(`/api/shop/members/${ownerRow.id}/remove`, {}, { token: staff.accessToken });
  assert.equal(r.status, 403);
});

test('عضوِ دکانِ دیگری با این مسیر برداشته نمی‌شود', async () => {
  const a = await shopWithStaff();
  const b = await shopWithStaff();
  const members = await h.get('/api/shop/members', { token: b.owner.accessToken });
  const hisStaff = members.body.members.find(m => m.userId === b.staff.user.id);

  const r = await h.post(`/api/shop/members/${hisStaff.id}/remove`, {},
    { token: a.owner.accessToken });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'member_not_found');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) تپشِ بازدید — «اطلاعاتِ هر برنامه به سرور می‌رسد و ثبت می‌شود»
   ══════════════════════════════════════════════════════════════════ */

test('تپشِ برنامهٔ پمپ، پمپِ همان حساب را هم ثبت می‌کند', async () => {
  const u = await h.newUser('تپنده', 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ تپنده' }, { token: u.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));

  const r = await h.post('/api/visit',
    { deviceUid: 'pump-uid-1', app: 'pump', platform: 'desktop', version: '3.1.140' },
    { token: u.accessToken });
  assert.equal(r.status, 200);

  const row = await one(
    'SELECT app, user_id, shop_id, station_id, app_version FROM app_visitors WHERE device_uid=$1',
    ['pump-uid-1']
  );
  assert.equal(row.app, 'pump');
  assert.equal(row.user_id, u.user.id);
  assert.equal(row.station_id, st.body.station.id, 'پمپش باید ثبت شود');
  assert.equal(row.shop_id, '', 'و دکانی به آن چسبانده نشود');
  assert.equal(row.app_version, '3.1.140');
});

test('تپشِ برنامهٔ دکان همان دکان را ثبت می‌کند و پمپ خالی می‌ماند', async () => {
  const u = await h.newUser('تپندهٔ دکان');
  const sh = await h.post('/api/shop', { name: 'دکانِ تپنده' }, { token: u.accessToken });

  await h.post('/api/visit', { deviceUid: 'shop-uid-1', app: 'shop' }, { token: u.accessToken });
  const row = await one('SELECT shop_id, station_id FROM app_visitors WHERE device_uid=$1',
    ['shop-uid-1']);
  assert.equal(row.shop_id, sh.body.shop.id);
  assert.equal(row.station_id, '');
});

test('تپشِ مهمانِ بی‌حساب هم ثبت می‌شود', async () => {
  const r = await h.post('/api/visit', { deviceUid: 'guest-uid-1', app: 'pump' });
  assert.equal(r.status, 200);
  const row = await one('SELECT user_id, station_id FROM app_visitors WHERE device_uid=$1',
    ['guest-uid-1']);
  assert.equal(row.user_id, '');
  assert.equal(row.station_id, '');
});

test('مدیر در فهرستِ بازدیدکننده‌ها نامِ پمپ را می‌بیند', async () => {
  const pw = require('../src/lib/password');
  const { newId, now } = require('../src/db');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'vis-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const t = (await h.post('/api/admin/login',
    { username: 'vis-admin', password: 'Admin!12345' })).body.token;

  const u = await h.newUser('دیده‌شو', 'pump');
  await h.post('/api/pump', { name: 'پمپِ دیده‌شو', code: 'DIDEH' }, { token: u.accessToken });
  await h.post('/api/visit', { deviceUid: 'pump-uid-2', app: 'pump' }, { token: u.accessToken });

  const r = await h.get('/api/admin/visitors?app=pump&limit=200', { token: t });
  assert.equal(r.status, 200);
  const row = r.body.visitors.find(v => v.deviceUid === 'pump-uid-2');
  assert.ok(row, 'ردیف باید در فهرست باشد');
  assert.equal(row.stationName, 'پمپِ دیده‌شو');
  //  سرور کدِ پمپ را کوچک می‌نویسد
  assert.equal(row.stationCode, 'dideh');
});
