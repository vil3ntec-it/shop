'use strict';
/**
 * برنامهٔ کامپیوترِ پمپ — «نشانی در خودِ برنامه، اشتراک از سرور».
 *
 * خواستهٔ صاحب مخزن: «نشانی توی خودِ برنامه باشد و دیده نشود، و اشتراک
 * هم از سرور برایش برود، و کسی نتواند کرک کند یا دور بزند.»
 *
 * این‌جا ثابت می‌شود که کلِ راه بی هیچ حسابِ گوگلی کار می‌کند: شش رقم
 * می‌رود، پمپ ساخته می‌شود، اشتراک فعال می‌شود، و مجوزِ امضاشده
 * برمی‌گردد — و هیچ‌کدامِ این‌ها در برنامه تصمیم گرفته نمی‌شود.
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

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

async function pumpCode(t, body = {}) {
  const r = await h.post('/api/admin/pump/vip-codes',
    { plan: 'custom', days: 30, ...body }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.code;
}

const dev = (uid) => ({ uid, name: 'کامپیوترِ پمپ', platform: 'windows' });

// ── ۱) فعال‌سازی ────────────────────────────────────────────────

test('شش رقم ⇒ پمپ، اشتراک، توکنِ دستگاه و مجوز — بی هیچ حساب', async () => {
  const t = await adminToken();
  const code = await pumpCode(t, { days: 90 });

  const r = await h.post('/api/pump/device/activate', {
    code,
    device: dev('pc-activate-1'),
    station: { code: 'dev-first', name: 'پمپِ اول' },
  });

  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.ok(r.body.deviceToken?.startsWith('pd_'), 'توکنِ دستگاه باید برگردد');
  assert.equal(r.body.station.code, 'dev-first');
  assert.equal(r.body.createdStation, true);
  assert.equal(r.body.entitlement.source, 'subscription');
  assert.ok(r.body.license, 'مجوز باید همان لحظه صادر شود');
  assert.ok(r.body.publicKey, 'کلید عمومی هم باید بیاید تا برنامه بسنجد');

  //  مجوز به همین پمپ و همین دستگاه بسته است
  const payload = JSON.parse(
    Buffer.from(r.body.license.split('.')[1], 'base64').toString('utf8')
  );
  assert.equal(payload.aud, 'tohid-pump-app');
  assert.equal(payload.duid, 'pc-activate-1');
  assert.equal(payload.stn, r.body.station.id);
  assert.ok(payload.feat.includes('cloud'));

  //  و پمپ هنوز صاحبِ گوگلی ندارد — که درست است
  const row = await one('SELECT owner_user_id FROM stations WHERE id=$1', [r.body.station.id]);
  assert.equal(row.owner_user_id, null);
});

test('کدِ غلط هیچ پمپی نمی‌سازد', async () => {
  const before = await one('SELECT COUNT(*)::int n FROM stations');
  const r = await h.post('/api/pump/device/activate', {
    code: '000000', device: dev('pc-bad-1'),
  });
  assert.ok(r.status >= 400);
  const after = await one('SELECT COUNT(*)::int n FROM stations');
  assert.equal(after.n, before.n, 'کدِ بد نباید پمپِ یتیم بسازد');
});

test('همان کامپیوتر با کدِ تازه، همان پمپ را تمدید می‌کند — نه پمپِ دوم', async () => {
  const t = await adminToken();
  const first = await h.post('/api/pump/device/activate', {
    code: await pumpCode(t, { days: 30 }),
    device: dev('pc-renew'), station: { code: 'dev-renew' },
  });
  assert.equal(first.status, 201);

  const again = await h.post('/api/pump/device/activate', {
    code: await pumpCode(t, { days: 30 }),
    device: dev('pc-renew'), station: { code: 'dev-renew-other' },
  });
  assert.equal(again.status, 201, JSON.stringify(again.body));
  assert.equal(again.body.station.id, first.body.station.id, 'باید همان پمپ بماند');
  assert.equal(again.body.createdStation, false);

  //  و روزها روی هم می‌آیند
  assert.ok(again.body.subscription.endsAt > first.body.subscription.endsAt);
});

test('کدی که به نامِ پمپِ مشخصی صادر شده، روی همان می‌نشیند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/pump/device/activate', {
    code: await pumpCode(t, { days: 30 }),
    device: dev('pc-bound-a'), station: { code: 'dev-bound' },
  });
  const stationId = made.body.station.id;

  //  کدِ تازه، این‌بار به نامِ همان پمپ
  const bound = await pumpCode(t, { days: 30, stationId });

  //  کامپیوترِ دیگری همان کد را می‌زند
  const second = await h.post('/api/pump/device/activate', {
    code: bound, device: dev('pc-bound-b'),
  });
  assert.equal(second.status, 201, JSON.stringify(second.body));
  assert.equal(second.body.station.id, stationId, 'باید به همان پمپ بچسبد');
  assert.equal(second.body.createdStation, false);
});

// ── ۲) توکنِ دستگاه ─────────────────────────────────────────────

async function activated(uid, code) {
  const t = await adminToken();
  const r = await h.post('/api/pump/device/activate', {
    code: code || await pumpCode(t, { days: 60 }),
    device: dev(uid), station: { code: `st-${uid}` },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return { token: r.body.deviceToken, stationId: r.body.station.id };
}

test('توکنِ دستگاه پمپِ خودش را می‌دهد و نه چیز دیگری', async () => {
  const a = await activated('pc-iso-a');
  const b = await activated('pc-iso-b');

  await h.put('/api/pump/device/files/live.json',
    { data: { safe: 500 } }, { token: a.token });

  const mine = await h.get('/api/pump/device/files/live.json', { token: a.token });
  assert.equal(mine.body.data.safe, 500);

  //  b همان مسیر را می‌زند و پوشهٔ خودش خالی است
  const theirs = await h.get('/api/pump/device/files/live.json', { token: b.token });
  assert.equal(theirs.status, 404);

  const meB = await h.get('/api/pump/device/me', { token: b.token });
  assert.equal(meB.body.station.id, b.stationId);
  assert.notEqual(meB.body.station.id, a.stationId);
});

test('توکنِ دستگاه روی مسیرهای کاربر و دکان کار نمی‌کند', async () => {
  const a = await activated('pc-scope');
  for (const path of ['/api/pump/me', '/api/me', '/api/me/subscription']) {
    const r = await h.get(path, { token: a.token });
    assert.equal(r.status, 401, `${path} نباید توکنِ دستگاه را بپذیرد`);
  }
});

test('توکنِ کاربر روی مسیرهای دستگاه کار نمی‌کند', async () => {
  const u = await h.newUser('کاربرِ کنجکاو', 'pump');
  const r = await h.get('/api/pump/device/me', { token: u.accessToken });
  assert.equal(r.status, 401);
});

test('برنامه نشانی و رمزِ خانگی را می‌سپارد', async () => {
  const a = await activated('pc-home');
  const r = await h.post('/api/pump/device/home',
    { homeUrl: 'https://home-dev.example.ir', readKey: 'rk-dev' }, { token: a.token });
  assert.equal(r.status, 200);
  assert.equal(r.body.station.homeUrl, 'https://home-dev.example.ir');
  assert.equal(r.body.station.hasReadKey, true);
});

test('مجوزِ تازه هر بار از سرور می‌آید', async () => {
  const a = await activated('pc-lic');
  const r = await h.post('/api/pump/device/license', {}, { token: a.token });
  assert.equal(r.status, 200);
  assert.ok(r.body.license);
  const payload = JSON.parse(Buffer.from(r.body.license.split('.')[1], 'base64').toString('utf8'));
  assert.equal(payload.stn, a.stationId);
  assert.ok(payload.exp <= payload.sub_ends, 'مجوز نباید دیرتر از اشتراک تمام شود');
});

// ── ۳) دور زدن ──────────────────────────────────────────────────

test('اشتراکِ تمام‌شده: نه مجوزی، نه نوشتنی', async () => {
  const a = await activated('pc-expired');

  //  اشتراک را دستی تمام می‌کنیم — مثلِ روزی که واقعاً تمام شود
  await query(
    `UPDATE station_subscriptions SET status='expired', ends_at=$2 WHERE station_id=$1`,
    [a.stationId, now() - 1000]
  );
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');

  const lic = await h.post('/api/pump/device/license', {}, { token: a.token });
  assert.equal(lic.status, 200);
  assert.equal(lic.body.license, null, 'مجوزی نباید صادر شود');
  assert.equal(lic.body.reason, 'no_subscription');

  const write = await h.put('/api/pump/device/files/live.json',
    { data: { safe: 1 } }, { token: a.token });
  assert.equal(write.status, 403);
  assert.equal(write.body.error.code, 'subscription_required');

  //  ولی خواندن باز است — دادهٔ پمپ مالِ خودش است
  const read = await h.get('/api/pump/device/me', { token: a.token });
  assert.equal(read.status, 200);

  await plans.setConfig('pump_trial_days', '14');
});

test('توکنِ ساختگی هیچ دری را باز نمی‌کند', async () => {
  for (const fake of ['pd_notreal', 'pd_' + 'a'.repeat(43), 'Bearer', '']) {
    const r = await h.get('/api/pump/device/me', { token: fake });
    assert.equal(r.status, 401, `«${fake}» نباید پذیرفته شود`);
  }
});

test('پمپِ خاموش‌شده، دستگاه‌هایش هم خاموش‌اند', async () => {
  const t = await adminToken();
  const a = await activated('pc-off');
  const off = await h.post(`/api/admin/pump/stations/${a.stationId}/status`,
    { status: 'disabled' }, { token: t });
  assert.equal(off.status, 200);

  const r = await h.get('/api/pump/device/me', { token: a.token });
  assert.equal(r.status, 401, 'دستگاهِ پمپِ خاموش نباید کار کند');
});

// ── ۴) گوشیِ کارمند به پمپی که با کد فعال شده ───────────────────

test('کدِ پیوستن: کارمند با گوگل می‌آید و اولین نفر صاحب می‌شود', async () => {
  const a = await activated('pc-join');

  const minted = await h.post('/api/pump/device/join-code',
    { role: 'staff', hours: 2 }, { token: a.token });
  assert.equal(minted.status, 201, JSON.stringify(minted.body));
  assert.match(String(minted.body.code), /^\d{6}$/);

  //  اولین نفر ⇒ صاحب
  const boss = await h.newUser('کارفرما', 'pump');
  const joined = await h.post('/api/pump/claim',
    { code: minted.body.code }, { token: boss.accessToken });
  assert.equal(joined.status, 201, JSON.stringify(joined.body));
  assert.equal(joined.body.role, 'owner');
  assert.equal(joined.body.station.id, a.stationId);

  //  و حالا «پمپِ من» برایش کار می‌کند
  const me = await h.get('/api/pump/me', { token: boss.accessToken });
  assert.equal(me.body.station.id, a.stationId);

  //  نفرِ دوم ⇒ همان نقشی که کد می‌گوید
  const staff = await h.newUser('کارمند', 'pump');
  const second = await h.post('/api/pump/claim',
    { code: minted.body.code }, { token: staff.accessToken });
  assert.equal(second.status, 201);
  assert.equal(second.body.role, 'staff');
});

test('کدِ پیوستنِ باطل‌شده و کدِ غلط کار نمی‌کنند', async () => {
  const a = await activated('pc-join-2');
  const first = await h.post('/api/pump/device/join-code', {}, { token: a.token });
  //  کدِ تازه، قبلی را باطل می‌کند
  const second = await h.post('/api/pump/device/join-code', {}, { token: a.token });
  assert.notEqual(first.body.code, second.body.code);

  const u = await h.newUser('دیرآمده', 'pump');
  const old = await h.post('/api/pump/claim', { code: first.body.code }, { token: u.accessToken });
  assert.equal(old.status, 403, 'کدِ قبلی باید باطل شده باشد');

  const bad = await h.post('/api/pump/claim', { code: '000000' }, { token: u.accessToken });
  assert.equal(bad.status, 404);
});

test('کسی که عضوِ پمپِ دیگری است، به پمپِ دوم نمی‌پیوندد', async () => {
  const a = await activated('pc-join-3');
  const b = await activated('pc-join-4');

  const u = await h.newUser('دوپمپه', 'pump');
  const codeA = (await h.post('/api/pump/device/join-code', {}, { token: a.token })).body.code;
  const codeB = (await h.post('/api/pump/device/join-code', {}, { token: b.token })).body.code;

  assert.equal((await h.post('/api/pump/claim', { code: codeA }, { token: u.accessToken })).status, 201);
  const twice = await h.post('/api/pump/claim', { code: codeB }, { token: u.accessToken });
  assert.equal(twice.status, 409);
});

/* ==========================================================
   پمپِ بی‌صاحب هم باید در «رو به پایان» دیده شود

   پمپی که با کدِ شش‌رقمی فعال شده `owner_user_id` خالی دارد. تا
   دیروز `expiringSoon` با یک JOINِ ساده به `users` می‌رفت، پس چنین
   پمپی از فهرست می‌افتاد و اشتراکش بی‌صدا تمام می‌شد.
   ========================================================== */
test('اشتراکِ پمپِ بی‌صاحب هم در فهرستِ رو به پایان می‌آید', async () => {
  const t = await adminToken();
  const code = await pumpCode(t, { days: 2 });   // دو روزه: همین حالا رو به پایان است

  const r = await h.post('/api/pump/device/activate', {
    code,
    device: dev('pc-ownerless-expiring'),
    station: { code: 'dev-ownerless', name: 'پمپِ بی‌صاحب' },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const row = await one('SELECT owner_user_id FROM stations WHERE id=$1', [r.body.station.id]);
  assert.equal(row.owner_user_id, null, 'این پمپ باید بی‌صاحب باشد');

  const soon = await h.get('/api/admin/pump/subscriptions/expiring?days=7', { token: t });
  assert.equal(soon.status, 200, JSON.stringify(soon.body));
  const mine = soon.body.subscriptions.find((s) => s.tenantId === r.body.station.id);
  assert.ok(mine, 'پمپِ بی‌صاحب نباید از فهرست بیفتد');
  assert.equal(mine.ownerUserId, null);
});

test('و خبر دادنِ خودکار روی پمپِ بی‌صاحب نمی‌شکند', async () => {
  //  کسی نیست که پیام به او برسد، پس فقط باید بی‌صدا رد شود
  const subs = require('../src/lib/subscriptions').pump;
  //  مهم این است که پرتاب نکند: پیش از نگهبانِ ownerUserId، این‌جا
  //  پیامی بی‌گیرنده ساخته می‌شد.
  const out = await subs.notifyExpiring();
  assert.ok(out !== undefined, 'باید بی‌خطا برگردد');
});

test('⚠️ پمپِ بی‌صاحب در فهرستِ پنل دیده می‌شود — نه صفحهٔ خالی', async () => {
  //  علتِ «بخشِ پمپ هیچی نداره»: فهرست با JOINِ ساده به users می‌رفت،
  //  پس هر پمپی که با کدِ شش‌رقمی فعال شده بود (و صاحب نداشت) از
  //  فهرست می‌افتاد — در حالی که `total` عددِ درست را می‌گفت.
  const t = await adminToken();
  const code = await pumpCode(t, { days: 30 });
  const made = await h.post('/api/pump/device/activate', {
    code,
    device: dev('pc-panel-visible'),
    station: { code: 'dev-panel-visible', name: 'پمپِ پنل' },
  });
  assert.equal(made.status, 201);

  const list = await h.get('/api/admin/pump/stations?limit=200', { token: t });
  assert.equal(list.status, 200);

  const mine = list.body.stations.find((s) => s.id === made.body.station.id);
  assert.ok(mine, 'پمپِ بی‌صاحب باید در فهرست باشد');
  assert.equal(mine.code, 'dev-panel-visible');

  //  و شمارشِ ردیف‌ها نباید از total کمتر بیفتد — همان ناسازگاری که
  //  صفحه را خالی نشان می‌داد.
  assert.ok(list.body.stations.length >= 1);
  assert.ok(list.body.stations.length <= list.body.total);
});
