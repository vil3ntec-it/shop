'use strict';
/**
 * خبرهای پمپ روی ابر — «برنامه بسته هم باشد، خبر برسد».
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «ببین برنامه‌ها جوری استن که بسته هم باشن هر اتفاقی که تو برنامه
 *  بوفته کم‌بودی یا هر چی به سرور ارسال میشه و سرور وقتی که برنامه‌ها
 *  بسته هم باشن براشون میده پیام‌ها رو اگه کاربر نت داشت.»
 *
 * ⛔ **این تا امروز فقط برای دکان بود.** `‎/api/events‎` از روزِ اول
 * `requireShop` داشت، پس بخشِ پمپ هیچ دفترِ خبری روی ابر نداشت:
 * «اضافه برد» و «کم مانده» فقط روی سرورِ **خانگی** می‌نشستند و گوشیِ
 * کارمند هر پانزده دقیقه از همان شبکه می‌پرسید. صاحبِ پمپی که بیرون
 * بود — یا مودمش خاموش بود — هیچ‌وقت خبر نمی‌گرفت.
 *
 * چیزهایی که این پرونده قفل می‌کند:
 *   ۱) دو در، یک دفتر: کامپیوترِ پمپ با توکنِ دستگاه، صاحبش با حساب
 *   ۲) پوش فقط برای سه نوعِ ارزشمند، یک زنگ برای هر دسته
 *   ۳) دفترِ هر پمپ مالِ خودش — نه پمپِ همسایه، نه دکان
 *   ۴) قفلِ اشتراک این‌جا نیست: خبر پیام است، نه دادهٔ فروشی
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const push = require('../src/lib/push');

/** هر پوشی که فرستاده شد، این‌جا می‌نشیند. FCM از این ماشین در دسترس نیست. */
let outbox = [];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  push.setDeliver((row, message) => { outbox.push({ row, message }); });
});
test.after(async () => {
  push.setDeliver(null);
  await h.stop();
});

const clear = () => { outbox = []; };
const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

/** صاحبِ یک پمپِ تازه، با نشستِ بخشِ پمپ. */
async function pumpOwner(name) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, stationCode: made.body.station.code };
}

/** کامپیوترِ همان پمپ — توکنِ دستگاه، بی حساب. */
async function bindDevice(owner, uid) {
  const r = await h.post('/api/pump/device/bind',
    { device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' } },
    { token: owner.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.deviceToken;
}

/* ══════════════════════════════════════════════════════════════════
   ۱) دو در، یک دفتر
   ══════════════════════════════════════════════════════════════════ */

test('کامپیوترِ پمپ خبر می‌فرستد و صاحبِ پمپ روی گوشی می‌بیندش', async () => {
  const owner = await pumpOwner('یک');
  const deviceToken = await bindDevice(owner, 'pc-1');

  const sent = await h.post('/api/pump/device/events', {
    events: [
      { kind: 'debt', title: 'کریم اضافه برد', body: 'الباقی منفی شد', clientId: 'd7-out' },
      { kind: 'low_stock', title: 'پطرول کم مانده', clientId: 'tank-low' },
    ],
  }, { token: deviceToken });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.equal(sent.body.saved, 2);

  const seen = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(seen.status, 200);
  assert.equal(seen.body.events.length, 2);
  const titles = seen.body.events.map(e => e.title).sort();
  assert.deepEqual(titles, ['پطرول کم مانده', 'کریم اضافه برد'].sort());
});

test('خبری که گوشیِ صاحبِ پمپ می‌سازد، کامپیوترِ همان پمپ هم می‌بیند', async () => {
  const owner = await pumpOwner('دو');
  const deviceToken = await bindDevice(owner, 'pc-2');

  await h.post('/api/pump/events', { kind: 'note', title: 'یادداشتِ صاحب' },
    { token: owner.accessToken });

  const fromPc = await h.get('/api/pump/device/events', { token: deviceToken });
  assert.equal(fromPc.status, 200);
  assert.equal(fromPc.body.events.length, 1);
  assert.equal(fromPc.body.events[0].title, 'یادداشتِ صاحب');
});

test('نامِ فرستنده روی خبر می‌نشیند — کارِ کامپیوتر از کارِ آدم جدا می‌ماند', async () => {
  const owner = await pumpOwner('سه');
  const deviceToken = await bindDevice(owner, 'pc-3');

  await h.post('/api/pump/device/events', { kind: 'sale', title: 'فروشِ شیفت' },
    { token: deviceToken });
  await h.post('/api/pump/events', { kind: 'sale', title: 'فروشِ دستی' },
    { token: owner.accessToken });

  const all = await h.get('/api/pump/events', { token: owner.accessToken });
  const byTitle = Object.fromEntries(all.body.events.map(e => [e.title, e]));

  //  کامپیوترِ پمپ حساب ندارد، پس شناسهٔ دستگاهش می‌نشیند و کاربر خالی است
  assert.equal(byTitle['فروشِ شیفت'].userId, '');
  assert.equal(byTitle['فروشِ شیفت'].deviceUid, 'pc-3');
  //  و خبرِ خودِ صاحب، شناسهٔ حسابش را دارد
  assert.equal(byTitle['فروشِ دستی'].userId, owner.user.id);
  assert.equal(byTitle['فروشِ دستی'].deviceUid, '');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) صفِ آفلاین
   ══════════════════════════════════════════════════════════════════ */

test('صفِ آفلاین که دو بار برسد، دو ردیف نمی‌سازد', async () => {
  const owner = await pumpOwner('چهار');
  const deviceToken = await bindDevice(owner, 'pc-4');

  const batch = {
    events: [
      { kind: 'debt', title: 'اضافه برد', clientId: 'd9-out' },
      { kind: 'stock_out', title: 'دیزل تمام شد', clientId: 'tank-out' },
    ],
  };
  const first = await h.post('/api/pump/device/events', batch, { token: deviceToken });
  const again = await h.post('/api/pump/device/events', batch, { token: deviceToken });

  assert.equal(first.body.saved, 2);
  assert.equal(again.body.saved, 0, 'همان دسته دوباره ردیف نمی‌سازد');

  const seen = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(seen.body.events.length, 2);
});

test('کلیدِ خالی یعنی «هر بار یک ردیف» — خبرِ بی‌شناسه گم نمی‌شود', async () => {
  const owner = await pumpOwner('پنج');
  const deviceToken = await bindDevice(owner, 'pc-5');

  await h.post('/api/pump/device/events', { kind: 'note', title: 'بی‌کلید' }, { token: deviceToken });
  await h.post('/api/pump/device/events', { kind: 'note', title: 'بی‌کلید' }, { token: deviceToken });

  const seen = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(seen.body.events.length, 2);
});

test('نوعِ ناشناخته بی‌سروصدا رد می‌شود و بقیه ثبت می‌شوند', async () => {
  const owner = await pumpOwner('شش');
  const deviceToken = await bindDevice(owner, 'pc-6');

  const r = await h.post('/api/pump/device/events', {
    events: [{ kind: 'چیزی-که-نیست', title: 'رد' }, { kind: 'note', title: 'ماند' }],
  }, { token: deviceToken });
  assert.equal(r.status, 201);
  assert.equal(r.body.saved, 1);
  assert.equal(r.body.events[0].title, 'ماند');
});

test('دستهٔ بزرگ‌تر از پنجاه خبر رد می‌شود', async () => {
  const owner = await pumpOwner('هفت');
  const deviceToken = await bindDevice(owner, 'pc-7');

  const many = Array.from({ length: 51 }, (_, i) => ({ kind: 'note', title: `خبر ${i}` }));
  const r = await h.post('/api/pump/device/events', { events: many }, { token: deviceToken });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'batch_too_large');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) پوش — «برنامه بسته هم باشد»
   ══════════════════════════════════════════════════════════════════ */

test('خبرِ قرض گوشیِ صاحبِ پمپ را بیدار می‌کند', async () => {
  const owner = await pumpOwner('هشت');
  const deviceToken = await bindDevice(owner, 'pc-8');
  //  گوشیِ صاحبِ پمپ توکنِ پوشش را ثبت می‌کند
  const reg = await h.post('/api/pump/support/push',
    { token: 'fcm-owner-8', provider: 'fcm', platform: 'android' },
    { token: owner.accessToken });
  assert.equal(reg.status, 201, JSON.stringify(reg.body));

  clear();
  await h.post('/api/pump/device/events', { kind: 'debt', title: 'کریم اضافه برد' },
    { token: deviceToken });

  assert.equal(outbox.length, 1, 'یک زنگ، روی همان گوشی');
  assert.equal(outbox[0].row.token, 'fcm-owner-8');
  assert.equal(outbox[0].message.title, 'قرض از حد گذشت');
  assert.match(outbox[0].message.body, /کریم/);
});

test('فقط سه نوع پوش می‌شوند — فروش و یادداشت زنگ نمی‌زنند', async () => {
  const owner = await pumpOwner('نه');
  const deviceToken = await bindDevice(owner, 'pc-9');
  await h.post('/api/pump/support/push', { token: 'fcm-owner-9' }, { token: owner.accessToken });

  for (const kind of ['sale', 'expense', 'note']) {
    clear();
    await h.post('/api/pump/device/events', { kind, title: `خبرِ ${kind}` }, { token: deviceToken });
    assert.equal(outbox.length, 0, `${kind} نباید زنگ بزند`);
  }

  for (const kind of ['stock_out', 'low_stock', 'debt']) {
    clear();
    await h.post('/api/pump/device/events', { kind, title: `خبرِ ${kind}` }, { token: deviceToken });
    assert.equal(outbox.length, 1, `${kind} باید زنگ بزند`);
  }
});

test('صفِ بیست‌تایی یک زنگ می‌زند، نه بیست تا', async () => {
  const owner = await pumpOwner('ده');
  const deviceToken = await bindDevice(owner, 'pc-10');
  await h.post('/api/pump/support/push', { token: 'fcm-owner-10' }, { token: owner.accessToken });

  clear();
  const batch = Array.from({ length: 20 }, (_, i) => ({
    kind: 'low_stock', title: `مخزن ${i}`, clientId: `low-${i}`,
  }));
  await h.post('/api/pump/device/events', { events: batch }, { token: deviceToken });

  assert.equal(outbox.length, 1, 'یک پیام برای یک دسته');
  assert.match(outbox[0].message.body, /۱۹|19/, 'باید بگوید چند خبرِ دیگر هست');
});

test('کسی که خودش خبر را ساخته، زنگِ کارِ خودش را نمی‌گیرد', async () => {
  const owner = await pumpOwner('یازده');
  await h.post('/api/pump/support/push', { token: 'fcm-owner-11' }, { token: owner.accessToken });

  clear();
  //  خودِ صاحب خبر را می‌سازد ⇒ روی گوشیِ خودش زنگ نمی‌خورد
  await h.post('/api/pump/events', { kind: 'debt', title: 'دستی' }, { token: owner.accessToken });
  assert.equal(outbox.length, 0);
});

test('زنگِ یک پمپ به گوشیِ پمپِ دیگر نمی‌رود', async () => {
  const a = await pumpOwner('دوازده');
  const b = await pumpOwner('سیزده');
  const aDevice = await bindDevice(a, 'pc-12');
  await h.post('/api/pump/support/push', { token: 'fcm-a-12' }, { token: a.accessToken });
  await h.post('/api/pump/support/push', { token: 'fcm-b-13' }, { token: b.accessToken });

  clear();
  await h.post('/api/pump/device/events', { kind: 'stock_out', title: 'مالِ الف' },
    { token: aDevice });

  assert.deepEqual(outbox.map(o => o.row.token), ['fcm-a-12']);
});

test('زنگِ پمپ به گوشیِ دکانِ همان آدم نمی‌رود', async () => {
  //  یک آدم، هم دکان هم پمپ — دو نشستِ جدا، دو توکنِ پوشِ جدا
  const u = await h.newUser('هر دو کاره');
  await h.post('/api/shop', { name: 'دکانش' }, { token: u.accessToken });
  await h.post('/api/me/push', { token: 'fcm-shop-side' }, { token: u.accessToken });

  const asPump = await h.signIn(u, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپش' }, { token: asPump.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  const deviceToken = await bindDevice(asPump, 'pc-both');
  await h.post('/api/pump/support/push', { token: 'fcm-pump-side' }, { token: asPump.accessToken });

  clear();
  await h.post('/api/pump/device/events', { kind: 'debt', title: 'خبرِ پمپ' }, { token: deviceToken });

  assert.deepEqual(outbox.map(o => o.row.token), ['fcm-pump-side'],
    'خبرِ پمپ فقط به سمتِ پمپ می‌رود');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) دفترِ هر پمپ مالِ خودش
   ══════════════════════════════════════════════════════════════════ */

test('خبرهای یک پمپ در دفترِ پمپِ دیگر پیدا نمی‌شوند', async () => {
  const a = await pumpOwner('چهارده');
  const b = await pumpOwner('پانزده');
  const aDevice = await bindDevice(a, 'pc-14');

  await h.post('/api/pump/device/events', { kind: 'note', title: 'رازِ الف' }, { token: aDevice });

  const mine = await h.get('/api/pump/events', { token: a.accessToken });
  const theirs = await h.get('/api/pump/events', { token: b.accessToken });
  assert.equal(mine.body.events.length, 1);
  assert.equal(theirs.body.events.length, 0);
});

test('توکنِ دکان روی مسیرِ خبرِ پمپ انگار وجود ندارد', async () => {
  const u = await h.newUser('دکان‌دار');
  await h.post('/api/shop', { name: 'دکانی' }, { token: u.accessToken });
  const r = await h.get('/api/pump/events', { token: u.accessToken });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'invalid_token');
});

test('حسابی که پمپ ندارد، خبرِ پمپ نمی‌نویسد', async () => {
  const u = await h.newUser('بی‌پمپ', 'pump');
  const r = await h.post('/api/pump/events', { kind: 'note', title: 'هیچ' },
    { token: u.accessToken });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'no_station');
});

test('خبرِ پمپ در دفترِ خبرِ دکان نمی‌نشیند', async () => {
  const owner = await pumpOwner('شانزده');
  const deviceToken = await bindDevice(owner, 'pc-16');
  await h.post('/api/pump/device/events', { kind: 'note', title: 'مالِ پمپ' },
    { token: deviceToken });

  const shopRows = await h.query('SELECT COUNT(*)::int n FROM shop_events');
  assert.equal(shopRows.rows[0].n, 0, 'جدولِ دکان دست‌نخورده می‌ماند');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) «تا کجا خواندم»
   ══════════════════════════════════════════════════════════════════ */

test('شمارِ نخوانده‌ها با «تا این‌جا خواندم» صفر می‌شود', async () => {
  const owner = await pumpOwner('هفده');
  const deviceToken = await bindDevice(owner, 'pc-17');
  await h.post('/api/pump/device/events', {
    events: [{ kind: 'note', title: 'یک' }, { kind: 'note', title: 'دو' }],
  }, { token: deviceToken });

  const before = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(before.body.unread, 2);

  const mark = await h.post('/api/pump/events/seen', { at: before.body.serverTime },
    { token: owner.accessToken });
  assert.equal(mark.status, 200);

  const after = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(after.body.unread, 0);
  assert.equal(after.body.seenAt, mark.body.seenAt);
});

test('نقطه‌ی خوانده‌شدنِ صاحبِ پمپ و کامپیوترش جدا است', async () => {
  const owner = await pumpOwner('هجده');
  const deviceToken = await bindDevice(owner, 'pc-18');
  await h.post('/api/pump/device/events', { kind: 'note', title: 'یک' }, { token: deviceToken });

  //  صاحب می‌خواند
  const mine = await h.get('/api/pump/events', { token: owner.accessToken });
  await h.post('/api/pump/events/seen', { at: mine.body.serverTime }, { token: owner.accessToken });

  //  کامپیوتر خبری از آن ندارد و همان خبر برایش نخوانده است
  const pc = await h.get('/api/pump/device/events', { token: deviceToken });
  assert.equal(pc.body.unread, 1);
  assert.equal(pc.body.seenAt, 0);
});

test('«از این زمان به بعد» تاریخ را دوباره دانلود نمی‌کند', async () => {
  const owner = await pumpOwner('نوزده');
  const deviceToken = await bindDevice(owner, 'pc-19');
  await h.post('/api/pump/device/events', { kind: 'note', title: 'کهنه' }, { token: deviceToken });
  const mid = (await h.get('/api/pump/events', { token: owner.accessToken })).body.serverTime;
  await h.post('/api/pump/device/events', { kind: 'note', title: 'تازه' }, { token: deviceToken });

  const only = await h.get(`/api/pump/events?since=${mid}`, { token: owner.accessToken });
  assert.equal(only.body.events.length, 1);
  assert.equal(only.body.events[0].title, 'تازه');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) قفلِ اشتراک این‌جا نیست
   ══════════════════════════════════════════════════════════════════ */

test('پمپِ بی‌اشتراک هم خبر می‌فرستد و می‌خواند', async () => {
  const owner = await pumpOwner('بیست');
  const deviceToken = await bindDevice(owner, 'pc-20');

  //  اشتراکی روی این پمپ نیست و دورهٔ آزمایشی هم تمام‌شده فرض می‌شود
  await h.query('UPDATE stations SET created_at=$2 WHERE id=$1',
    [owner.stationId, Date.now() - 400 * 24 * 3600 * 1000]);

  const sent = await h.post('/api/pump/device/events', { kind: 'stock_out', title: 'تیل تمام' },
    { token: deviceToken });
  assert.equal(sent.status, 201, 'خبر پیام است، نه دادهٔ فروشی — قفل نمی‌شود');

  const seen = await h.get('/api/pump/events', { token: owner.accessToken });
  assert.equal(seen.body.events.length, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) از دیدِ مدیر
   ══════════════════════════════════════════════════════════════════ */

test('مدیر دفترِ خبرِ یک پمپ را می‌بیند', async () => {
  const owner = await pumpOwner('بیست‌ویک');
  const deviceToken = await bindDevice(owner, 'pc-21');
  await h.post('/api/pump/device/events', { kind: 'debt', title: 'برای مدیر' },
    { token: deviceToken });

  const t = await adminToken();
  const r = await h.get(`/api/admin/pump/stations/${owner.stationId}/events`, { token: t });
  assert.equal(r.status, 200);
  assert.equal(r.body.events.length, 1);
  assert.equal(r.body.events[0].title, 'برای مدیر');
});

test('کاربرِ عادی دفترِ خبرِ پمپِ دیگری را از درِ مدیر نمی‌بیند', async () => {
  const owner = await pumpOwner('بیست‌ودو');
  const r = await h.get(`/api/admin/pump/stations/${owner.stationId}/events`,
    { token: owner.accessToken });
  assert.equal(r.status, 401);
});

test('پاک شدنِ یک پمپ، دفترِ خبرش را هم می‌برد', async () => {
  const owner = await pumpOwner('بیست‌وسه');
  const deviceToken = await bindDevice(owner, 'pc-23');
  await h.post('/api/pump/device/events', { kind: 'note', title: 'موقت' }, { token: deviceToken });

  await h.query('DELETE FROM stations WHERE id=$1', [owner.stationId]);
  const left = await h.query('SELECT COUNT(*)::int n FROM station_events WHERE station_id=$1',
    [owner.stationId]);
  assert.equal(left.rows[0].n, 0);
});
