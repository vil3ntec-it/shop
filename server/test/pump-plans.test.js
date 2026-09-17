'use strict';
/**
 * پلن‌های پمپ، قفلِ قابلیت‌ها، و بند شدن از راهِ **حساب**.
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * نوشتهٔ خودش در ریپوی پمپ (`native/docs/PLANS-fa.md`):
 *
 *   «اشتراک رو من به حسابِ یارو از سرور می‌دم اینترنتی و تو برنامه تو
 *    حسابِ همون ثبت می‌شن… من یادم نمیاد که برای اشتراک کدی گفته باشم.»
 *   «توی استاندارد کدِ کیو‌آر نباشه، اپِ کارمندان و آیفون براش فعال
 *    نشه… و مفاد و ضرر براش نشون داده نشه… تاریخچه‌ها هم بسته بشه… و
 *    داشبورد هم قفل باشه.»
 *   «اطلاعاتشون باشن ولی دیده نتونن، و اگه یارو بار دیگه وی‌آی‌پی یا
 *    دائمی رو خرید، قفلِ اون‌ها باز بشه.»
 *
 * چهار چیزی که این پرونده قفل می‌کند:
 *   ۱) سه پلنِ واقعی با قیمتِ دالری — و «رایگان» که هیچ‌جا دیده نمی‌شود.
 *   ۲) مرزِ هر پلن: استاندارد فقط پشتیبانِ ابری، وی‌آی‌پی همه‌چیز.
 *   ۳) دفترِ خودِ کاربر هیچ‌وقت قفل نمی‌شود — حتی بی اشتراک.
 *   ۴) `POST /api/pump/device/bind` — نصبِ تازه با **حساب**، بی هیچ کدی.
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

/** بارِ مجوز — بی سنجشِ امضا، فقط برای دیدنِ ادعاها. */
function claims(token) {
  return JSON.parse(Buffer.from(String(token).split('.')[1], 'base64url').toString('utf8'));
}

/** صاحبِ یک پمپِ تازه، با نشستِ بخشِ پمپ. */
async function pumpOwner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id, stationCode: made.body.station.code };
}

const dev = (uid) => ({ uid, name: 'کامپیوترِ پمپ', platform: 'windows' });

/* ══════════════════════════════════════════════════════════════════
   ۱) جدولِ پلن‌ها — همان سه تا، با همان قیمت
   ══════════════════════════════════════════════════════════════════ */

test('سه پلنِ پمپ با قیمتِ دالریِ خودِ صاحب مخزن', async () => {
  const r = await h.get('/api/pump/plans');
  assert.equal(r.status, 200);

  const by = Object.fromEntries(r.body.plans.map(p => [p.code, p]));
  assert.ok(by.std, 'پلنِ استاندارد باید باشد');
  assert.ok(by.vip, 'پلنِ وی‌آی‌پی باید باشد');
  assert.ok(by.perm, 'پلنِ دائمی باید باشد');

  assert.equal(by.std.price, 120);
  assert.equal(by.vip.price, 150);
  assert.equal(by.perm.price, 600);

  //  ⚠️ دالر، نه افغانی. با واحدِ مشترک، «۱۲۰ افغانی» نوشته می‌شد —
  //  دو رقم کم‌تر از حقیقت، و مستقیم روی پول.
  assert.equal(r.body.currency, 'دالر');

  //  دائمی یعنی «یک‌بار برای همیشه» — پس دوره‌اش باید خیلی دور باشد
  assert.ok(by.perm.days > 365 * 10, 'دائمی باید بسیار دور تمام شود');
});

test('«رایگان» هیچ‌جا دیده نمی‌شود', async () => {
  const r = await h.get('/api/pump/plans');
  for (const p of r.body.plans) {
    assert.doesNotMatch(p.code, /^free$/i, 'پلنِ رایگان نباید ردیف داشته باشد');
    assert.doesNotMatch(p.title, /رایگان/, 'نامِ پلن نباید «رایگان» باشد');
  }
});

test('پلنِ شاپ‌شکلِ کهنه روی پمپ خاموش شده، ولی پاک نشده', async () => {
  const t = await adminToken();
  const all = await h.get('/api/admin/plans?app=pump', { token: t });
  assert.equal(all.status, 200);
  const old = all.body.plans.filter(p => ['m1', 'm6', 'y1'].includes(p.code));
  for (const p of old) {
    assert.equal(p.active, false, `پلنِ ${p.code} باید خاموش باشد`);
  }
  //  و پلنِ خاموش در فهرستِ عمومی نمی‌آید
  const pub = await h.get('/api/pump/plans');
  for (const p of pub.body.plans) {
    assert.ok(!['m1', 'm6', 'y1'].includes(p.code), 'پلنِ خاموش نباید عمومی دیده شود');
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۲) کاتالوگِ قابلیت‌ها — سه کارِ سرور که مانده بود
   ══════════════════════════════════════════════════════════════════ */

test('کاتالوگِ پمپ: profit و history آمدند، dashboard دیگر هسته نیست', async () => {
  const r = await h.get('/api/pump/features');
  assert.equal(r.status, 200);
  const keys = r.body.features.map(f => f.key);

  assert.ok(keys.includes('profit'), 'مفاد/ضرر باید در کاتالوگ باشد');
  assert.ok(keys.includes('history'), 'تاریخچه‌ها باید در کاتالوگ باشد');
  assert.ok(keys.includes('dashboard'));

  //  ⛔ بی این، پلنِ استاندارد اصلاً قابلِ ساختن نبود: هسته هرگز قفل نمی‌شود
  assert.ok(!r.body.core.includes('dashboard'), 'داشبورد نباید هسته باشد');
  //  ولی دفترِ خودِ کاربر هسته می‌ماند
  assert.ok(r.body.core.includes('debtors'));
  assert.ok(r.body.core.includes('settings'));
});

test('پشتیبانِ ابری کلیدِ جدا دارد — وگرنه استاندارد کیو‌آر را هم باز می‌کرد', async () => {
  const { PUMP_ALL_KEYS } = require('../src/lib/features');
  assert.ok(PUMP_ALL_KEYS.includes('cloud'));
  assert.ok(PUMP_ALL_KEYS.includes('cloudbackup'));
  assert.notEqual('cloud', 'cloudbackup');
});

test('کاتالوگِ دکان دست نخورده — داشبوردِ شاپ هنوز هسته است', async () => {
  const { CORE_KEYS, ALL_KEYS } = require('../src/lib/features');
  assert.ok(CORE_KEYS.includes('dashboard'), 'داشبوردِ دکان باید هسته بماند');
  assert.ok(!ALL_KEYS.includes('profit'), 'کلیدِ پمپ نباید به کاتالوگِ دکان نشت کند');
  assert.ok(!ALL_KEYS.includes('history'));
});

/* ══════════════════════════════════════════════════════════════════
   ۳) مرزِ پلن‌ها — روی entitlement و روی خودِ مجوز
   ══════════════════════════════════════════════════════════════════ */

async function grant(t, stationId, plan) {
  const r = await h.post('/api/admin/pump/subscriptions',
    { stationId, plan, days: 365 }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
}

test('استاندارد: فقط پشتیبانِ ابری — کیو‌آر و اپِ کارمندان و مفاد و تاریخچه و داشبورد بسته', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ استاندارد', 'plan-std');
  await grant(t, o.stationId, 'std');

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  assert.equal(me.status, 200);
  const f = me.body.entitlement.features;

  assert.equal(me.body.entitlement.source, 'subscription');
  assert.ok(f.includes('cloudbackup'), 'پشتیبانِ ابری باید باز باشد');

  for (const locked of ['cloud', 'kar_app', 'bot', 'profit', 'history', 'dashboard']) {
    assert.ok(!f.includes(locked), `${locked} نباید در استاندارد باز باشد`);
  }
});

test('وی‌آی‌پی: هر شش دروازه باز', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ وی‌آی‌پی', 'plan-vip');
  await grant(t, o.stationId, 'vip');

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  const f = me.body.entitlement.features;
  for (const open of ['cloud', 'cloudbackup', 'kar_app', 'bot', 'profit', 'history', 'dashboard']) {
    assert.ok(f.includes(open), `${open} باید در وی‌آی‌پی باز باشد`);
  }
});

test('دائمی همان وی‌آی‌پی است با پایانِ بسیار دور', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ دائمی', 'plan-perm');
  const r = await h.post('/api/admin/pump/subscriptions',
    { stationId: o.stationId, plan: 'perm' }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  const f = me.body.entitlement.features;
  for (const open of ['cloud', 'cloudbackup', 'kar_app', 'bot', 'profit', 'history', 'dashboard']) {
    assert.ok(f.includes(open), `${open} باید در دائمی باز باشد`);
  }
  //  ۵۰ سال — یعنی «یک‌بار برای همیشه»
  assert.ok(me.body.entitlement.subscription.daysLeft > 365 * 10);
});

test('استاندارد ⇒ وی‌آی‌پی: قفل‌ها باز می‌شوند و هیچ داده‌ای پاک نمی‌شود', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ ارتقا', 'plan-upgrade');

  await grant(t, o.stationId, 'std');
  const before = await h.get('/api/pump/me', { token: o.accessToken });
  assert.ok(!before.body.entitlement.features.includes('profit'));

  //  «اگه یارو بار دیگه وی‌آی‌پی رو خرید، قفلِ اون‌ها باز بشه»
  await grant(t, o.stationId, 'vip');
  const after = await h.get('/api/pump/me', { token: o.accessToken });
  assert.ok(after.body.entitlement.features.includes('profit'));
  assert.ok(after.body.entitlement.features.includes('history'));
  assert.ok(after.body.entitlement.features.includes('dashboard'));

  //  و خودِ پمپ همان پمپ مانده
  assert.equal(after.body.station.code, before.body.station.code);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) دفترِ خودِ کاربر گروگان گرفته نمی‌شود
   ══════════════════════════════════════════════════════════════════ */

test('پمپِ بی‌اشتراک هم کلِ دفترِ خودش را دارد', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await pumpOwner('پمپِ بی‌اشتراک', 'plan-free');

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  assert.equal(me.body.entitlement.source, 'free');
  const f = me.body.entitlement.features;

  //  جدولِ خودِ صاحب مخزن: «دفترِ کامل» برای هر چهار پلن ✅
  for (const ledger of ['debtors', 'safe', 'sarrafi', 'expense', 'invoice',
    'storage', 'staff', 'companies', 'amanat', 'chakana', 'extraincome', 'reports']) {
    assert.ok(f.includes(ledger), `${ledger} نباید با نبودِ اشتراک بسته شود`);
  }
  //  ولی کارهای پولی بسته‌اند
  for (const paid of ['cloud', 'cloudbackup', 'kar_app', 'profit', 'history', 'dashboard']) {
    assert.ok(!f.includes(paid), `${paid} بی اشتراک نباید باز باشد`);
  }
  await plans.setConfig('pump_trial_days', '14');
});

test('اشتراکِ تمام‌شده هم دفتر را نمی‌بندد', async () => {
  const t = await adminToken();
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await pumpOwner('پمپِ منقضی', 'plan-expired');

  await grant(t, o.stationId, 'vip');
  //  ساعت را جلو نمی‌بریم؛ همان ردیف را منقضی می‌کنیم
  await query(
    `UPDATE station_subscriptions SET ends_at=$2, status='expired' WHERE station_id=$1`,
    [o.stationId, now() - 1000]
  );

  const me = await h.get('/api/pump/me', { token: o.accessToken });
  const f = me.body.entitlement.features;
  assert.notEqual(me.body.entitlement.source, 'subscription');
  for (const ledger of ['debtors', 'safe', 'sarrafi', 'invoice', 'companies', 'amanat']) {
    assert.ok(f.includes(ledger), `${ledger} با انقضا نباید بسته شود`);
  }
  await plans.setConfig('pump_trial_days', '14');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) بند شدن با حساب — بی هیچ کدی
   ══════════════════════════════════════════════════════════════════ */

test('حساب ⇒ توکنِ دستگاه و مجوز، بی هیچ کدِ شش‌رقمی', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ بند', 'bind-1');
  await grant(t, o.stationId, 'vip');

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-1') }, { token: o.accessToken });

  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.ok(r.body.deviceToken?.startsWith('pd_'), 'توکنِ دستگاه باید برگردد');
  assert.equal(r.body.station.code, 'bind-1');
  assert.equal(r.body.entitlement.source, 'subscription');
  assert.ok(r.body.license, 'مجوزِ امضاشده باید همان لحظه بیاید');
  assert.ok(r.body.publicKey, 'کلیدِ عمومی هم باید بیاید تا برنامه بسنجد');

  //  و همان توکن واقعاً کار می‌کند
  const me = await h.get('/api/pump/device/me', { token: r.body.deviceToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.station.id, o.stationId);
});

test('مجوزِ bind همان قیدهای activate را دارد', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ قیدها', 'bind-claims');
  await grant(t, o.stationId, 'vip');

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-claims') }, { token: o.accessToken });
  const c = claims(r.body.license);

  //  ⛔ مجوزِ بخشِ دکان روی برنامهٔ پمپ نمی‌نشیند
  assert.equal(c.aud, 'tohid-pump-app');
  //  ⛔ کپیِ پوشه روی کامپیوترِ دیگر اشتراک را با خودش نمی‌برد
  assert.equal(c.duid, 'pc-bind-claims');
  //  ⛔ مجوزِ پمپِ دیگری روی این پمپ نمی‌نشیند
  assert.equal(c.stn || c.tid || c.sub, o.stationId);
  //  فهرستِ قابلیت‌ها **آمده** است — «نیامده» یعنی نسلِ اول و پلنِ کامل
  assert.ok(Array.isArray(c.feat), 'مجوز باید فهرستِ feat را صریح بیاورد');
  assert.ok(c.feat.includes('profit'));
});

test('پمپ از حسابِ توکن پیدا می‌شود، نه از بدنهٔ درخواست', async () => {
  const t = await adminToken();
  const mine = await pumpOwner('پمپِ من', 'bind-mine');
  const other = await pumpOwner('پمپِ دیگری', 'bind-other');
  await grant(t, mine.stationId, 'vip');

  //  شناسهٔ پمپِ دیگری را صریح می‌فرستیم
  const r = await h.post('/api/pump/device/bind', {
    device: dev('pc-bind-evil'),
    stationId: other.stationId,
    station: { id: other.stationId, code: 'bind-other' },
  }, { token: mine.accessToken });

  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.station.id, mine.stationId, 'باید پمپِ خودِ حساب باشد');
  assert.notEqual(r.body.station.id, other.stationId);
});

test('حسابِ بی‌پمپ ۴۰۴ می‌گیرد و می‌گوید چرا', async () => {
  const u = await h.newUser('بی‌پمپ', 'pump');
  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-nostation') }, { token: u.accessToken });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'no_station');
});

test('bind بی توکن و با توکنِ دکان رد می‌شود', async () => {
  const anon = await h.post('/api/pump/device/bind', { device: dev('pc-bind-anon') });
  assert.equal(anon.status, 401);

  //  ⛔ نشستِ بخشِ دکان روی مسیرهای پمپ **پیدا نمی‌شود**
  const shopUser = await h.newUser('دکان‌دار', 'shop');
  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-shop') }, { token: shopUser.accessToken });
  assert.equal(r.status, 401);
});

test('bind بی شناسهٔ دستگاه رد می‌شود', async () => {
  const o = await pumpOwner('پمپِ بی‌شناسه', 'bind-nouid');
  const r = await h.post('/api/pump/device/bind', { device: { name: 'بی شناسه' } },
    { token: o.accessToken });
  assert.equal(r.status, 400);
});

test('bind اشتراک نمی‌سازد — بی اشتراک، دستگاه بند می‌شود ولی مجوزی نیست', async () => {
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await pumpOwner('پمپِ بی‌پول', 'bind-free');

  const r = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-free') }, { token: o.accessToken });

  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.ok(r.body.deviceToken, 'دستگاه باید بند شود');
  assert.equal(r.body.entitlement.source, 'free');
  assert.equal(r.body.license, null, 'بی اشتراک مجوزی صادر نمی‌شود');
  assert.equal(r.body.reason, 'no_subscription');
  assert.match(r.body.message, /فعال نیست/);

  //  ⛔ و دفترِ خودش باز مانده
  assert.ok(r.body.entitlement.features.includes('debtors'));
  await plans.setConfig('pump_trial_days', '14');
});

test('bind دوباره روی همان دستگاه، پمپِ دوم نمی‌سازد', async () => {
  const t = await adminToken();
  const o = await pumpOwner('پمپِ دوباره', 'bind-twice');
  await grant(t, o.stationId, 'vip');

  const a = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-twice') }, { token: o.accessToken });
  const b = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-twice') }, { token: o.accessToken });

  assert.equal(b.status, 201);
  assert.equal(b.body.station.id, a.body.station.id);

  //  توکنِ تازه جای کهنه را می‌گیرد — و هر دو به همان پمپ می‌رسند
  const me = await h.get('/api/pump/device/me', { token: b.body.deviceToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.station.id, o.stationId);

  const { one } = require('../src/db');
  const { n } = await one(
    'SELECT COUNT(*)::int AS n FROM station_devices WHERE device_uid=$1', ['pc-bind-twice']);
  assert.equal(n, 1, 'یک دستگاه، یک ردیف');
});

test('اشتراکِ بعد از bind همان لحظه در برنامه دیده می‌شود', async () => {
  const t = await adminToken();
  const plans = require('../src/lib/plans');
  await plans.setConfig('pump_trial_days', '0');
  const o = await pumpOwner('پمپِ بعداً', 'bind-later');

  const bound = await h.post('/api/pump/device/bind',
    { device: dev('pc-bind-later') }, { token: o.accessToken });
  assert.equal(bound.body.license, null);

  //  مدیر از پنل اشتراک می‌دهد — بی هیچ کدی، روی همان حساب
  await grant(t, o.stationId, 'vip');

  //  برنامه فقط مجوزِ تازه می‌خواهد
  const lic = await h.post('/api/pump/device/license', {}, { token: bound.body.deviceToken });
  assert.equal(lic.status, 200, JSON.stringify(lic.body));
  assert.equal(lic.body.source, 'subscription');
  assert.ok(lic.body.license, 'حالا باید مجوز بیاید');
  assert.ok(claims(lic.body.license).feat.includes('kar_app'));
  await plans.setConfig('pump_trial_days', '14');
});

/* ══════════════════════════════════════════════════════════════════
   ۶) راهِ کد هم سرِ جایش است — پلنِ تازه آن را نشکسته
   ══════════════════════════════════════════════════════════════════ */

test('کدِ شش‌رقمیِ پلنِ استاندارد همان مرزِ استاندارد را می‌آورد', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes',
    { plan: 'std', days: 365 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));

  const r = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: dev('pc-code-std'),
    station: { code: 'code-std', name: 'پمپِ کددار' },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.entitlement.source, 'subscription');

  const f = r.body.entitlement.features;
  assert.ok(f.includes('cloudbackup'));
  assert.ok(!f.includes('profit'), 'کدِ استاندارد نباید مفاد را باز کند');
  assert.ok(!f.includes('kar_app'));
  assert.ok(f.includes('debtors'), 'دفتر همیشه باز است');
});

test('کدِ شش‌رقمیِ وی‌آی‌پی همه‌چیز را باز می‌کند', async () => {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes',
    { plan: 'vip', days: 365 }, { token: t });
  const r = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: dev('pc-code-vip'),
    station: { code: 'code-vip', name: 'پمپِ وی‌آی‌پی' },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const f = r.body.entitlement.features;
  for (const open of ['cloud', 'cloudbackup', 'kar_app', 'bot', 'profit', 'history', 'dashboard']) {
    assert.ok(f.includes(open), `${open} باید با کدِ وی‌آی‌پی باز شود`);
  }
});

test('کدِ غلط هیچ توکنی نمی‌دهد', async () => {
  const r = await h.post('/api/pump/device/activate', {
    code: '000000', device: dev('pc-code-bad'),
  });
  assert.ok(r.status >= 400, 'کدِ غلط نباید بپذیرد');
  assert.ok(!r.body.deviceToken, 'هیچ توکنی نباید برگردد');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) از اول تا آخر — «با تست ببین لاگین می‌شود، کد می‌رود، اشتراک»
   ══════════════════════════════════════════════════════════════════ */

test('ثبت‌نام ⇒ ورود ⇒ پمپ ⇒ bind ⇒ اشتراک ⇒ قفل‌ها باز', async () => {
  const t = await adminToken();

  //  ۱) ثبت‌نامِ سه‌پله در بخشِ پمپ
  const u = await h.newUser('هارونِ یعقوبی', 'pump');
  assert.ok(u.accessToken, 'نشست باید در accessToken بیاید');
  assert.ok(u.refreshToken, 'توکنِ تازه‌سازی هم باید بیاید');

  //  ۲) ورودِ دوباره با همان ایمیل و رمز
  const again = await h.signIn(u, 'pump');
  assert.ok(again.accessToken);

  //  ۳) تازه‌سازیِ نشست — «یک ساعت بعد هم باید کار کند»
  const fresh = await h.post('/api/auth/refresh', { refreshToken: again.refreshToken });
  assert.equal(fresh.status, 200, JSON.stringify(fresh.body));
  assert.ok(fresh.body.accessToken);

  //  ۴) پمپش را می‌سازد
  const made = await h.post('/api/pump', { name: 'پمپِ یعقوبی', code: 'e2e-pump' },
    { token: fresh.body.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));

  //  ۵) برنامهٔ کامپیوتر با همان حساب بند می‌شود — بی هیچ کدی
  const bound = await h.post('/api/pump/device/bind',
    { device: dev('pc-e2e') }, { token: fresh.body.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));

  //  ۶) مدیر از پنل وی‌آی‌پی می‌دهد
  await grant(t, made.body.station.id, 'vip');

  //  ۷) و برنامه همان لحظه می‌بیندش
  const lic = await h.post('/api/pump/device/license', {}, { token: bound.body.deviceToken });
  assert.equal(lic.body.source, 'subscription');
  const c = claims(lic.body.license);
  assert.equal(c.aud, 'tohid-pump-app');
  assert.equal(c.duid, 'pc-e2e');
  for (const open of ['cloud', 'cloudbackup', 'kar_app', 'bot', 'profit', 'history', 'dashboard']) {
    assert.ok(c.feat.includes(open), `${open} باید در مجوز باشد`);
  }

  //  ۸) و نوشتن روی پوشهٔ ابری — همان چیزی که با اشتراکِ تمام‌شده بسته بود
  const put = await h.put('/api/pump/device/files/live.json',
    { data: { ok: 1 } }, { token: bound.body.deviceToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
});
