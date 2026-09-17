'use strict';
/**
 * پشتیبانِ هر حساب، جدا — «هر حساب بک‌اپ‌های خودش را ببیند».
 *
 * خواستهٔ صریحِ صاحب مخزن: «ببین بک‌اپ‌ها را از برنامه می‌شود گرفت برای
 * هر حساب جدا و هر حساب بک‌اپ‌های او را نشان بدهد.»
 *
 * تا پیش از این چنین چیزی نبود: سرور فقط `pg_dump`ِ کلِ دیتابیس داشت،
 * برنامهٔ دکان پشتیبانش را روی خودِ گوشی نگه می‌داشت، و برنامهٔ پمپ فقط
 * به سرورِ خانگی می‌فرستاد. پس این فایل از صفر می‌سنجد.
 *
 * ⚠️ هیچ‌کدام از این آزمون‌ها به فایلِ روی دیسک دست نمی‌زند تا «کار
 * می‌کند» را ثابت کند؛ همه از درِ خودِ HTTP می‌روند — همان دری که
 * برنامه می‌رود.
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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** یک دکان‌دار با دکانِ خودش. */
async function shopOwner(name = 'دکان‌دار') {
  const u = await h.newUser(name);
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

/** یک پمپ‌دار با پمپِ خودش. */
async function pumpOwner(name = 'پمپ‌دار', code = '') {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, code: made.body.station.code };
}

/** برنامهٔ کامپیوترِ پمپ — بی حساب، فقط با کدِ شش‌رقمی. */
async function pumpDevice(uid, days = 90) {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: uid, name: 'پمپِ دستگاه' },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  return { token: on.body.deviceToken, stationId: on.body.station.id, code: on.body.station.code };
}

/*
 *  ⚠️ دورهٔ آزمایشی پیش‌فرض ۱۴ روز است، پس **هر حسابِ تازه‌ای**
 *  `source: 'trial'` است و پلهٔ پولی می‌گیرد. برای سنجیدنِ پلهٔ رایگان
 *  باید همان لحظه بسته شود — و بعد برگردد، وگرنه آزمون‌های بعدی
 *  ناخواسته روی پلهٔ رایگان می‌دوند.
 *
 *  ⚠️ و جدول `app_config` است نه `config`؛ یک بار با نامِ غلط نوشته
 *  شد و خطایش «relation does not exist» بود، نه چیزی گمراه‌کننده.
 */
const plans = require('../src/lib/plans');
const noTrial = () => plans.setConfig('trial_days', '0');
const withTrial = () => plans.setConfig('trial_days', '14');

/** بافرِ تکرارشونده به اندازهٔ دلخواه — تا اندازه‌ها واقعاً سنجیده شوند. */
const blob = (n, fill = 0x41) => Buffer.alloc(n, fill);

// ══════════════════════════════════════════════════════════════════
//  ۱) دکان — رفت و برگشتِ کامل
// ══════════════════════════════════════════════════════════════════

test('دکان: پشتیبان می‌رود، در فهرست می‌نشیند، و همان بایت‌ها برمی‌گردند', async () => {
  const o = await shopOwner('یک');

  const empty = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(empty.status, 200);
  assert.deepEqual(empty.body.backups, [], 'حسابِ تازه هیچ پشتیبانی ندارد');
  assert.equal(empty.body.stats.count, 0);
  assert.equal(empty.body.stats.usedBytes, 0);

  const data = Buffer.from(JSON.stringify({ products: [{ name: 'برنج' }] }), 'utf8');
  const up = await h.raw('POST', '/api/me/backups?ext=json&kind=manual&label=دستی', data,
    { token: o.accessToken, headers: { 'X-App-Version': '2.4.0' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.backup.bytes, data.length);
  assert.equal(up.body.backup.kind, 'manual');
  assert.equal(up.body.backup.label, 'دستی');
  assert.equal(up.body.backup.appVersion, '2.4.0');
  assert.match(up.body.backup.name, /^shop-\d{8}-\d{6}-[0-9a-f]{8}\.json$/);

  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.body.backups.length, 1);
  assert.equal(list.body.stats.count, 1);
  assert.equal(list.body.stats.usedBytes, data.length);

  //  ⚠️ همان بایت‌ها، نه «چیزی شبیهِ آن». پشتیبانی که یک بایتش عوض
  //  شود، پشتیبان نیست.
  const back = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(back.status, 200);
  assert.ok(back.buffer.equals(data), 'بایت‌های برگشتی باید مو‌به‌مو همان باشند');
  assert.equal(back.headers.get('x-backup-sha256'), up.body.backup.sha256);
  assert.match(back.headers.get('content-disposition') || '', /attachment; filename="shop-/);
});

test('دکان: پشتیبانِ خالی رد می‌شود و چیزی نمی‌سازد', async () => {
  const o = await shopOwner('دو');
  const up = await h.raw('POST', '/api/me/backups', Buffer.alloc(0), { token: o.accessToken });
  assert.equal(up.status, 400);
  assert.equal(up.body.error.code, 'empty_backup');
  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.body.backups.length, 0);
});

test('دکان: حسابِ بی‌دکان می‌فهمد چرا نمی‌شود — نه خطای گنگ', async () => {
  const u = await h.newUser('بی‌دکان');
  const list = await h.get('/api/me/backups', { token: u.accessToken });
  assert.equal(list.status, 403);
  assert.equal(list.body.error.code, 'no_shop');

  const up = await h.raw('POST', '/api/me/backups', blob(10), { token: u.accessToken });
  assert.equal(up.status, 403);
  assert.equal(up.body.error.code, 'no_shop');
});

test('دکان: بی توکن هیچ دری باز نمی‌شود', async () => {
  const o = await shopOwner('سه');
  const up = await h.raw('POST', '/api/me/backups', blob(10), { token: o.accessToken });
  assert.equal(up.status, 201);

  for (const r of [
    await h.get('/api/me/backups'),
    await h.raw('POST', '/api/me/backups', blob(10)),
    await h.download(`/api/me/backups/${up.body.backup.id}`),
    await h.del(`/api/me/backups/${up.body.backup.id}`),
  ]) {
    assert.equal(r.status, 401, 'بی توکن باید ۴۰۱ باشد');
  }
});

// ══════════════════════════════════════════════════════════════════
//  ۲) جدا بودنِ حساب‌ها — مهم‌ترین بند
// ══════════════════════════════════════════════════════════════════

test('هر دکان فقط پشتیبانِ خودش را می‌بیند', async () => {
  const a = await shopOwner('الف');
  const b = await shopOwner('ب');

  const mine = await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"a":1}'), { token: a.accessToken });
  assert.equal(mine.status, 201);
  await h.raw('POST', '/api/me/backups?ext=json', Buffer.from('{"b":2}'), { token: b.accessToken });

  const listB = await h.get('/api/me/backups', { token: b.accessToken });
  assert.equal(listB.body.backups.length, 1);
  assert.ok(!listB.body.backups.some(x => x.id === mine.body.backup.id));

  //  حتی با شناسه‌ی درستِ دیگری — چون شناسه‌ی دکان از توکن می‌آید،
  //  نه از مسیر
  const steal = await h.download(`/api/me/backups/${mine.body.backup.id}`, { token: b.accessToken });
  assert.equal(steal.status, 404);

  const kill = await h.del(`/api/me/backups/${mine.body.backup.id}`, { token: b.accessToken });
  assert.equal(kill.status, 404);

  //  و مالِ خودش سرِ جایش ماند
  const still = await h.download(`/api/me/backups/${mine.body.backup.id}`, { token: a.accessToken });
  assert.equal(still.status, 200);
});

test('پشتیبانِ دکان با توکنِ پمپ دیده نمی‌شود و برعکس', async () => {
  const s = await shopOwner('دوگانه');
  const up = await h.raw('POST', '/api/me/backups', blob(32), { token: s.accessToken });
  assert.equal(up.status, 201);

  //  همان آدم، نشستِ بخشِ پمپ
  const pumpSide = await h.signIn(s, 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ همان آدم' }, { token: pumpSide.accessToken });
  assert.equal(made.status, 201);

  const list = await h.get('/api/pump/backups', { token: pumpSide.accessToken });
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.backups, [], 'دفترِ پمپ باید خالی باشد');

  const cross = await h.download(`/api/pump/backups/${up.body.backup.id}`, { token: pumpSide.accessToken });
  assert.equal(cross.status, 404);

  //  و توکنِ دکان روی مسیرِ پمپ اصلاً پیدا نمی‌شود
  const wrongDoor = await h.get('/api/pump/backups', { token: s.accessToken });
  assert.equal(wrongDoor.status, 401);
});

// ══════════════════════════════════════════════════════════════════
//  ۳) پمپ — دو در به یک پوشه
// ══════════════════════════════════════════════════════════════════

test('پمپ: برنامهٔ کامپیوتر با توکنِ دستگاه پشتیبان می‌فرستد', async () => {
  const d = await pumpDevice('pc-bak-1');
  const data = blob(4096, 0x7a);

  const up = await h.raw('POST', '/api/pump/device/backups?ext=db&kind=auto', data, { token: d.token });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.match(up.body.backup.name, /^pump-\d{8}-\d{6}-[0-9a-f]{8}\.db$/);

  const back = await h.download(`/api/pump/device/backups/${up.body.backup.id}`, { token: d.token });
  assert.ok(back.buffer.equals(data));
});

test('پمپ: همان پشتیبان از درِ حسابِ صاحبِ پمپ هم دیده می‌شود', async () => {
  const d = await pumpDevice('pc-bak-2');
  const up = await h.raw('POST', '/api/pump/device/backups?ext=db', blob(128), { token: d.token });
  assert.equal(up.status, 201);

  //  گوشیِ صاحبِ پمپ با کدِ پیوستن می‌آید و صاحب می‌شود
  const owner = await h.newUser('صاحبِ پمپ', 'pump');
  const join = await h.post('/api/pump/device/join-code', {}, { token: d.token });
  assert.equal(join.status, 201, JSON.stringify(join.body));
  const joined = await h.post('/api/pump/claim', { code: join.body.code }, { token: owner.accessToken });
  assert.equal(joined.status, 201, JSON.stringify(joined.body));

  const list = await h.get('/api/pump/backups', { token: owner.accessToken });
  assert.equal(list.status, 200);
  assert.equal(list.body.backups.length, 1, 'یک پوشه، دو در');
  assert.equal(list.body.backups[0].id, up.body.backup.id);
});

test('پمپ: توکنِ یک دستگاه پشتیبانِ پمپِ دیگری را نمی‌بیند', async () => {
  const a = await pumpDevice('pc-bak-a');
  const b = await pumpDevice('pc-bak-b');

  const mine = await h.raw('POST', '/api/pump/device/backups', blob(64), { token: a.token });
  assert.equal(mine.status, 201);

  const listB = await h.get('/api/pump/device/backups', { token: b.token });
  assert.deepEqual(listB.body.backups, []);

  const steal = await h.download(`/api/pump/device/backups/${mine.body.backup.id}`, { token: b.token });
  assert.equal(steal.status, 404);
});

// ══════════════════════════════════════════════════════════════════
//  ۴) سهم — دو پله، و «تازه‌ترین هرگز پاک نمی‌شود»
// ══════════════════════════════════════════════════════════════════

test('حسابِ بی‌اشتراک سهمِ کوچک‌تری دارد، ولی صفر نیست', async () => {
  const o = await shopOwner('بی‌اشتراک');
  //  دورهٔ آزمایشی را می‌بندیم تا `source` واقعاً `free` شود
  await noTrial();

  const st = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(st.body.stats.paid, false);
  assert.equal(st.body.stats.source, 'free');
  assert.ok(st.body.stats.quotaBytes > 0, 'سهمِ رایگان صفر نیست');

  const up = await h.raw('POST', '/api/me/backups', blob(1024), { token: o.accessToken });
  assert.equal(up.status, 201, 'حسابِ بی‌اشتراک هم باید بتواند پشتیبان بگذارد');

  await withTrial();
});

test('بالای شمارِ مجاز، کهنه‌ترین می‌رود و تازه‌ترین می‌ماند', async () => {
  const o = await shopOwner('پرتکرار');
  await noTrial();
  const keep = require('../src/config').backup.account.freeKeep;

  const ids = [];
  for (let i = 0; i < keep + 2; i++) {
    const up = await h.raw('POST', '/api/me/backups', Buffer.from(`نسخه ${i}`), { token: o.accessToken });
    assert.equal(up.status, 201, JSON.stringify(up.body));
    ids.push(up.body.backup.id);
  }

  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.body.backups.length, keep, `فقط ${keep} تا باید بماند`);

  //  تازه‌ترین هست
  assert.equal(list.body.backups[0].id, ids[ids.length - 1]);
  //  کهنه‌ترین‌ها رفته‌اند — و فایلشان هم
  const gone = await h.download(`/api/me/backups/${ids[0]}`, { token: o.accessToken });
  assert.equal(gone.status, 404);

  await withTrial();
});

test('فایلِ بزرگ‌تر از سقف رد می‌شود و پیامش می‌گوید چرا', async () => {
  const o = await shopOwner('بزرگ');
  await noTrial();
  const quota = require('../src/config').backup.account.freeBytes;

  const up = await h.raw('POST', '/api/me/backups', blob(quota + 1024), { token: o.accessToken });
  assert.equal(up.status, 403, JSON.stringify(up.body).slice(0, 200));
  assert.equal(up.body.error.code, 'backup_quota');

  const list = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(list.body.backups.length, 0, 'فایلِ ردشده نباید ردیفی بسازد');

  await withTrial();
});

test('اشتراکِ فعال سهمِ بزرگ‌تر می‌دهد', async () => {
  const o = await shopOwner('مشترک');
  await require('../src/lib/subscriptions').grant(o.shopId, { plan: 'custom', days: 30 });

  const st = await h.get('/api/me/backups', { token: o.accessToken });
  assert.equal(st.body.stats.paid, true);
  assert.equal(st.body.stats.source, 'subscription');
  const cfg = require('../src/config').backup.account;
  assert.equal(st.body.stats.quotaBytes, cfg.paidBytes);
  assert.equal(st.body.stats.keep, cfg.paidKeep);
});

test('اشتراکِ تمام‌شده دستِ کاربر را از پشتیبانش کوتاه نمی‌کند', async () => {
  const o = await shopOwner('تمام‌شده');
  await require('../src/lib/subscriptions').grant(o.shopId, { plan: 'custom', days: 30 });
  const up = await h.raw('POST', '/api/me/backups', blob(256), { token: o.accessToken });
  assert.equal(up.status, 201);

  //  اشتراک تمام شد
  await query("UPDATE subscriptions SET status='expired', ends_at=$1 WHERE shop_id=$2",
    [now() - 1000, o.shopId]);
  await noTrial();

  const back = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(back.status, 200, 'خواندن هرگز قفل نمی‌شود');
  assert.equal(back.buffer.length, 256);

  await withTrial();
});

// ══════════════════════════════════════════════════════════════════
//  ۵) پاک کردن
// ══════════════════════════════════════════════════════════════════

test('پاک کردن، هم ردیف را می‌برد هم فایل را', async () => {
  const o = await shopOwner('پاک‌کن');
  const up = await h.raw('POST', '/api/me/backups', blob(100), { token: o.accessToken });

  const gone = await h.del(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(gone.status, 200);
  assert.equal(gone.body.stats.count, 0);
  assert.equal(gone.body.stats.usedBytes, 0);

  const after = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(after.status, 404);

  //  دو بار پاک کردن، ۴۰۴ می‌دهد نه ۵۰۰
  const twice = await h.del(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(twice.status, 404);
  assert.equal(twice.body.error.code, 'backup_not_found');
});

test('شناسه‌ی ساختگی ۴۰۴ می‌دهد، نه خطای داخلی', async () => {
  const o = await shopOwner('کنجکاو');
  for (const id of ['bak_00000000000000000000000000000000', 'nothing', '../../etc/passwd']) {
    const r = await h.download(`/api/me/backups/${encodeURIComponent(id)}`, { token: o.accessToken });
    assert.equal(r.status, 404, `«${id}» باید ۴۰۴ بدهد`);
  }
});

// ══════════════════════════════════════════════════════════════════
//  ۶) مدیر — می‌بیند، برمی‌دارد، ولی جای کاربر پشتیبان نمی‌سازد
// ══════════════════════════════════════════════════════════════════

test('مدیر پشتیبانِ هر حساب را جدا می‌بیند و برمی‌دارد', async () => {
  const t = await adminToken();
  const o = await shopOwner('زیرِ نظر');
  const data = Buffer.from('دفترِ دکان');
  const up = await h.raw('POST', '/api/me/backups?ext=json', data, { token: o.accessToken });
  assert.equal(up.status, 201);

  const list = await h.get(`/api/admin/accounts/shop/${o.shopId}/backups`, { token: t });
  assert.equal(list.status, 200);
  assert.equal(list.body.app, 'shop');
  assert.equal(list.body.backups.length, 1);
  assert.equal(list.body.stats.usedBytes, data.length);

  const back = await h.download(`/api/admin/accounts/shop/${o.shopId}/backups/${up.body.backup.id}`, { token: t });
  assert.equal(back.status, 200);
  assert.ok(back.buffer.equals(data));
});

test('مدیر پشتیبانِ پمپ را هم می‌بیند، در دفترِ خودش', async () => {
  const t = await adminToken();
  const d = await pumpDevice('pc-bak-admin');
  await h.raw('POST', '/api/pump/device/backups?ext=db', blob(77), { token: d.token });

  const list = await h.get(`/api/admin/accounts/pump/${d.stationId}/backups`, { token: t });
  assert.equal(list.body.app, 'pump');
  assert.equal(list.body.backups.length, 1);

  //  همان شناسه در دفترِ دکان هیچ‌چیزی ندارد — `app` واقعاً شرط است
  const wrong = await h.get(`/api/admin/accounts/shop/${d.stationId}/backups`, { token: t });
  assert.deepEqual(wrong.body.backups, []);
});

test('بخشِ ناشناخته در مسیرِ مدیر رد می‌شود', async () => {
  const t = await adminToken();
  const r = await h.get('/api/admin/accounts/bank/xyz/backups', { token: t });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'bad_app');
});

test('کاربرِ عادی به مسیرِ مدیر نمی‌رسد', async () => {
  const o = await shopOwner('عادی');
  const r = await h.get(`/api/admin/accounts/shop/${o.shopId}/backups`, { token: o.accessToken });
  assert.equal(r.status, 401);
});

test('مدیرِ غیرارشد پشتیبانِ کسی را پاک نمی‌کند', async () => {
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'kamtar','کم‌تر',$2,'admin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const weak = (await h.post('/api/admin/login', { username: 'kamtar', password: 'Admin!12345' })).body.token;

  const o = await shopOwner('محافظت‌شده');
  const up = await h.raw('POST', '/api/me/backups', blob(20), { token: o.accessToken });

  const tryDelete = await h.del(
    `/api/admin/accounts/shop/${o.shopId}/backups/${up.body.backup.id}`, { token: weak });
  assert.equal(tryDelete.status, 403);

  const still = await h.download(`/api/me/backups/${up.body.backup.id}`, { token: o.accessToken });
  assert.equal(still.status, 200);
});
