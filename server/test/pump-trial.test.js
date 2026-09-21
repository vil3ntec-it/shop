'use strict';
/**
 * ⏳ **یک ماه رایگان برای حسابِ تازه**، و **یافتنِ حساب با ایمیل**.
 *
 * ── خواستهٔ صریحِ صاحب سامانه (۱۴۰۵/۰۷/۰۸) ──────────────────────────
 *   «به حسابِ مورد نظر یا **ایمیلِ مد نظر** اشتراک بدم و ثبت هم بشه…
 *    و یک کاری هم بکن برای کسایی که **تازه حساب افتتاح می‌کنن** هم
 *    **یک ماه رایگان** داده بشه.»
 *
 * ── دو چیزی که این پرونده قفل می‌کند ───────────────────────────────
 *   ۱) پمپِ تازه، بی هیچ اشتراکی، یک ماه همه‌چیز دارد — و پس از آن
 *      خودش می‌بندد. عددِ پیش‌فرض **یک جا** است (`plans.trialConfig`)
 *      و هر سه خواننده‌اش همان را می‌گویند.
 *   ۲) فهرستِ مدیر با **ایمیل** هم پیدا می‌شود — بی آن، تنها راهِ یافتنِ
 *      یک پمپ نامش یا نامِ صاحبش بود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const plans = require('../src/lib/plans');

const DAY = 24 * 3600 * 1000;

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

/* ══════════════════════════════════════════════════════════════════
   ۱) یک ماه رایگان
   ══════════════════════════════════════════════════════════════════ */

test('پیش‌فرضِ دورهٔ آزمایشیِ پمپ یک ماه است، و دکان دست نخورده', async () => {
  assert.equal(plans.TRIAL_DEFAULT.pump, '30');
  //  ⛔ عوض کردنِ پیش‌فرضِ **مشترک** دکان را هم عوض می‌کرد و دربارهٔ آن
  //  چیزی گفته نشده بود.
  assert.equal(plans.TRIAL_DEFAULT.shop, '14');

  assert.deepEqual(plans.trialConfig('pump'), { key: 'pump_trial_days', def: '30' });
  assert.deepEqual(plans.trialConfig('shop'), { key: 'trial_days', def: '14' });
});

test('پمپِ تازه بی هیچ اشتراکی یک ماه همه‌چیز دارد', async () => {
  const u = await h.newUser('trial-owner', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ آزمایشی' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));

  const me = await h.get('/api/pump/me', { token: u.accessToken });
  assert.equal(me.status, 200);
  const ent = me.body.entitlement;

  //  ⛔ «آزمایشی»، نه «رایگان» — و کلیدش `source` است
  assert.equal(ent.source, 'trial');
  assert.equal(ent.trial.enabled, true);
  assert.equal(ent.trial.active, true);

  //  یک ماه، با یک روز رفت‌وآمد برای مرزِ گردکردن
  assert.ok(ent.trial.daysLeft >= 29 && ent.trial.daysLeft <= 30,
    `باید نزدیکِ ۳۰ روز باشد، شد ${ent.trial.daysLeft}`);

  /*
   *  ⛔ و در این یک ماه **همهٔ** کارهای پولی بازند — «یک ماه رایگان»
   *  یعنی همین، وگرنه مشتری نمی‌فهمد چه خریده.
   *
   *  ⚠️ فهرست از خودِ **کاتالوگ** می‌آید، نه از حافظهٔ من: بارِ اول
   *  `qrlive` نوشتم و سنجه گرفتش — آن نامِ **برنامه** است، نه نامِ
   *  سرور (کلیدِ سرور `cloud` است). همان تلهٔ «نامِ قابلیت در برنامه و
   *  روی سرور یکی نیست».
   */
  const paid = require('../src/lib/features').catalogOf('pump').PAID_KEYS;
  assert.ok(paid.length > 0);
  for (const key of paid) {
    assert.ok(ent.features.includes(key), `${key} باید در دورهٔ آزمایشی باز باشد`);
  }
});

test('و پس از یک ماه خودش می‌بندد — بی هیچ کارِ دستی', async () => {
  const u = await h.newUser('trial-over', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ کهنه' }, { token: u.accessToken });
  const id = made.body.station.id;

  //  ⚠️ ساعتِ سرور عقب برده نمی‌شود؛ **تاریخِ ساختِ پمپ** به گذشته
  //  می‌رود. همان کاری که گذرِ زمان می‌کند، بی دست‌کاریِ زمان.
  await query('UPDATE stations SET created_at=$1 WHERE id=$2', [now() - 31 * DAY, id]);

  const me = await h.get('/api/pump/me', { token: u.accessToken });
  const ent = me.body.entitlement;
  assert.equal(ent.source, 'free');
  assert.equal(ent.trial.active, false);
  assert.equal(ent.trial.used, true);
  for (const key of require('../src/lib/features').catalogOf('pump').PAID_KEYS) {
    assert.ok(!ent.features.includes(key), `${key} باید پس از دوره بسته باشد`);
  }
});

test('تنظیمِ نوشته‌شده جلوتر از پیش‌فرض است', async () => {
  const token = await adminToken();
  const saved = await h.patch('/api/admin/config', { pump_trial_days: '3' }, { token });
  assert.equal(saved.status, 200);
  try {
    assert.equal(await plans.trialDaysOf('pump'), 3);
    //  و `/api/pump/plans` همان عدد را می‌گوید، نه عددِ دیگری
    const r = await h.get('/api/pump/plans');
    assert.equal(r.body.trialDays, 3);
  } finally {
    await plans.setConfig('pump_trial_days', '30');
  }
});

test('«روزِ آزمایشیِ صفر» یعنی دوره‌ای نیست — نه یعنی بی‌نهایت', async () => {
  await plans.setConfig('pump_trial_days', '0');
  try {
    const u = await h.newUser('trial-off', 'pump');
    await h.post('/api/pump', { name: 'پمپِ بی‌دوره' }, { token: u.accessToken });
    const me = await h.get('/api/pump/me', { token: u.accessToken });
    assert.equal(me.body.entitlement.source, 'free');
    assert.equal(me.body.entitlement.trial.enabled, false);
  } finally {
    await plans.setConfig('pump_trial_days', '30');
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۲) «یا ایمیلِ مد نظر» — فهرستِ مدیر با ایمیل هم پیدا می‌شود
   ══════════════════════════════════════════════════════════════════ */

test('فهرستِ پمپ‌ها با ایمیلِ صاحبش پیدا می‌شود', async () => {
  const token = await adminToken();
  const u = await h.newUser('by-email', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ ایمیلی' }, { token: u.accessToken });
  assert.equal(made.status, 201);

  //  ⚠️ با **بخشی** از ایمیل هم — صاحبِ سامانه معمولاً کلِ ایمیل را
  //  تایپ نمی‌کند.
  const part = u.email.split('@')[0];
  const found = await h.get(`/api/admin/pump/stations?q=${encodeURIComponent(part)}`, { token });
  assert.equal(found.status, 200);
  const ids = found.body.stations.map((s) => s.id);
  assert.ok(ids.includes(made.body.station.id), 'پمپ باید با ایمیلِ صاحبش پیدا شود');

  //  و کلِ ایمیل هم
  const whole = await h.get(`/api/admin/pump/stations?q=${encodeURIComponent(u.email)}`, { token });
  assert.ok(whole.body.stations.map((s) => s.id).includes(made.body.station.id));
});

test('فهرستِ دکان‌ها هم با ایمیل پیدا می‌شود', async () => {
  const token = await adminToken();
  const u = await h.newUser('shop-by-email', 'shop');
  //  ⚠️ `/api/shop`، مفرد — `/api/shops` پشتِ `requireShop` است و
  //  «برای این حساب دکانی ثبت نشده» می‌دهد (خودِ همین سنجه گرفتش).
  const made = await h.post('/api/shop', { name: 'دکانِ ایمیلی' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));

  const found = await h.get(`/api/admin/shops?q=${encodeURIComponent(u.email)}`, { token });
  assert.equal(found.status, 200);
  assert.ok(found.body.shops.map((s) => s.id).includes(made.body.shop.id));
});

test('⛔ جست‌وجوی خالی همه را می‌دهد، و جست‌وجوی بی‌جواب هیچ‌کس را', async () => {
  const token = await adminToken();
  const all = await h.get('/api/admin/pump/stations', { token });
  assert.ok(all.body.stations.length > 0);

  const none = await h.get('/api/admin/pump/stations?q=hich-chizi-inja-nist', { token });
  assert.equal(none.body.stations.length, 0);
});
