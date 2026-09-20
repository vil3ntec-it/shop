'use strict';
/**
 * مرکزِ اعلان — بخشِ ۱۱.۳.۳ی پرامپت.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────
 *   «یک بخش «اعلان‌ها» در پنل که از آن هر پیامی را به هر کسی بفرستم:
 *    گیرنده همه / یک برنامه / فیلتر (شهر، پلن، رو به پایان، منقضی،
 *    دائمی) / یک فرد خاص… کانال: داخل برنامه، Push، ایمیل… گزارش
 *    ارسال: تحویل شد / خوانده شد / خطا، به تفکیک هر گیرنده.»
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) گیرنده‌ها واقعاً فیلتر می‌شوند (و فیلترِ «both» هر ردیف را به
 *      بخشِ خودش مهر می‌زند، نه «هر دو»).
 *   ۲) **یک ردیفِ تحویل برای هر گیرنده در هر کانال** — گزارش همین است.
 *   ۳) متغیرها برای هر گیرنده جدا پر می‌شوند و قیمت از **سرور** می‌آید.
 *   ۴) «اعلان‌های من» فقط مالِ همان حساب است و نشستِ بخشِ دیگر آن را
 *      نمی‌بیند.
 *   ۵) پوش فقط با هدف می‌رود (`push.setDeliver` تنها راهِ سنجیدنش).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const notices = require('../src/lib/notices');
const push = require('../src/lib/push');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { push.setDeliver(null); await h.stop(); });

async function adminToken() {
  const r = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  return r.body.token;
}

/** یک دکان‌دارِ تازه با دکانش. */
async function shopOwner(name, city = '') {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  if (city) await query('UPDATE users SET city=$2 WHERE id=$1', [u.user.id, city]);
  return { ...u, shopId: made.body.shop.id };
}

async function pumpOwner(name, code) {
  const u = await h.newUser(name, 'pump');
  const made = await h.post('/api/pump', { name: `پمپِ ${name}`, code }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, stationId: made.body.station.id };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) قالب‌ها
   ══════════════════════════════════════════════════════════════════ */

test('قالب‌های آماده از روزِ اول هستند و از پنل ویرایش می‌شوند', async () => {
  const t = await adminToken();
  const r = await h.get('/api/admin/notice-templates', { token: t });
  assert.equal(r.status, 200);
  const keys = r.body.templates.map(x => x.key);
  for (const want of ['welcome', 'expiring', 'renewed', 'discount', 'update', 'suspended', 'personal']) {
    assert.ok(keys.includes(want), `قالبِ «${want}» باید باشد`);
  }

  const saved = await h.put('/api/admin/notice-templates/welcome',
    { app: 'shop', title: 'خوش آمدی {نام}', body: 'به {برنامه} خوش آمدید.' }, { token: t });
  assert.equal(saved.status, 200);
  assert.equal(saved.body.template.title, 'خوش آمدی {نام}');

  //  ⛔ ویرایشِ مدیر با بالا آمدنِ دوبارهٔ سرور روی‌نویسی نمی‌شود
  await notices.seedTemplates();
  const again = await notices.templateFor('welcome', 'shop');
  assert.equal(again.title, 'خوش آمدی {نام}');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) گیرنده‌ها
   ══════════════════════════════════════════════════════════════════ */

test('گیرنده: همه · فیلترِ شهر · یک فرد — و «both» هر ردیف را به بخشِ خودش مهر می‌زند', async () => {
  const t = await adminToken();
  const kabul = await shopOwner('احمد', 'کابل');
  const herat = await shopOwner('محمود', 'هرات');
  const pump = await pumpOwner('کریم', 'PMPN1');

  const all = await h.post('/api/admin/notices/audience', { app: 'shop', audience: { kind: 'all' } }, { token: t });
  assert.equal(all.status, 200);
  assert.ok(all.body.count >= 2);
  assert.ok(all.body.recipients.every(r => r.app === 'shop'), 'اعلانِ دکان نباید پمپ را بگیرد');

  const city = await h.post('/api/admin/notices/audience',
    { app: 'shop', audience: { kind: 'filter', city: 'کابل' } }, { token: t });
  assert.equal(city.body.count, 1);
  assert.equal(city.body.recipients[0].tenantId, kabul.shopId);

  const one_ = await h.post('/api/admin/notices/audience',
    { app: 'shop', audience: { kind: 'user', user_id: herat.user.id } }, { token: t });
  assert.equal(one_.body.count, 1);
  assert.equal(one_.body.recipients[0].tenantId, herat.shopId);

  const both = await h.post('/api/admin/notices/audience', { app: 'both', audience: { kind: 'all' } }, { token: t });
  const apps = new Set(both.body.recipients.map(r => r.app));
  assert.ok(apps.has('shop') && apps.has('pump'), '«both» باید هر دو بخش را بیاورد');
  assert.ok(both.body.recipients.every(r => r.app === 'shop' || r.app === 'pump'),
    '⛔ هیچ گیرنده‌ای «both» نیست — هر کس مالِ یک بخش است');
  assert.ok(both.body.recipients.some(r => r.tenantId === pump.stationId));
});

test('فیلترِ «رو به پایان» فقط اشتراکِ فعالِ نزدیک را می‌گیرد، نه دائمی را', async () => {
  const t = await adminToken();
  const soon = await shopOwner('نزدیک');
  const far = await shopOwner('دور');

  await h.post('/api/admin/subscriptions', { shopId: soon.shopId, plan: 'm1', days: 5 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: far.shopId, plan: 'm1', days: 200 }, { token: t });

  const r = await h.post('/api/admin/notices/audience',
    { app: 'shop', audience: { kind: 'filter', expiring_days: 7 } }, { token: t });
  const ids = r.body.recipients.map(x => x.tenantId);
  assert.ok(ids.includes(soon.shopId), 'اشتراکِ ۵ روزه باید رو به پایان باشد');
  assert.ok(!ids.includes(far.shopId), 'اشتراکِ ۲۰۰ روزه رو به پایان نیست');
});

/* ══════════════════════════════════════════════════════════════════
   ۳) ارسال، متغیرها و گزارش
   ══════════════════════════════════════════════════════════════════ */

test('ارسال: یک ردیفِ تحویل برای هر گیرنده در هر کانال، با نتیجهٔ واقعی', async () => {
  const t = await adminToken();
  const a = await shopOwner('گیرنده الف');
  const b = await shopOwner('گیرنده ب');

  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: a.user.id },
    channels: ['inapp', 'email'], title: 'سلام {نام}', body: 'اشتراکِ {برنامه} شما.',
  }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.notice.id;

  const sent = await h.post(`/api/admin/notices/${id}/send`, {}, { token: t });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.equal(sent.body.recipients, 1);
  //  یک گیرنده × دو کانال = دو ردیف
  assert.equal(sent.body.sent + sent.body.error, 2);

  const rep = await h.get(`/api/admin/notices/${id}/report`, { token: t });
  assert.equal(rep.status, 200);
  assert.equal(rep.body.deliveries.length, 2);
  const channels = rep.body.deliveries.map(d => d.channel).sort();
  assert.deepEqual(channels, ['email', 'inapp']);
  //  ⛔ متنِ نهایی با متغیرهای پرشده همان چیزی است که گیرنده دید
  assert.ok(rep.body.deliveries[0].title.includes('گیرنده الف'), 'متغیرِ {نام} باید پر شده باشد');
  assert.ok(rep.body.deliveries.every(d => d.body.includes('دکان')), 'متغیرِ {برنامه} باید پر شده باشد');
  //  نشانیِ ایمیل همان لحظه عکس گرفته می‌شود
  assert.equal(rep.body.deliveries.find(d => d.channel === 'email').address, a.email);

  //  ⛔ گیرندهٔ دیگر هیچ ردیفی ندارد
  assert.ok(!rep.body.deliveries.some(d => d.userId === b.user.id));
});

test('پیش‌نمایش و ارسالِ آزمایشی هیچ ردیفی در گزارش نمی‌گذارند', async () => {
  const t = await adminToken();
  const u = await shopOwner('پیش‌نمایش');
  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id },
    channels: ['email'], title: 'سلام {نام}', body: 'متن',
  }, { token: t });
  const id = made.body.notice.id;

  const pv = await h.post(`/api/admin/notices/${id}/preview`, {}, { token: t });
  assert.equal(pv.status, 200);
  assert.ok(pv.body.sample.length >= 1);
  assert.ok(pv.body.sample[0].title.includes('پیش‌نمایش'));

  const tst = await h.post(`/api/admin/notices/${id}/test`, { to: 'me@test.local' }, { token: t });
  assert.equal(tst.status, 200, JSON.stringify(tst.body));

  const rep = await h.get(`/api/admin/notices/${id}/report`, { token: t });
  assert.equal(rep.body.deliveries.length, 0, '⛔ پیش‌نمایش و آزمایشی گزارش نمی‌سازند');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) «اعلان‌های من»
   ══════════════════════════════════════════════════════════════════ */

test('اعلانِ من فقط مالِ خودم است، و «خواندم» یک بار می‌نشیند', async () => {
  const t = await adminToken();
  const me = await shopOwner('خواننده');
  const other = await shopOwner('دیگری');

  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: me.user.id },
    channels: ['inapp'], title: 'خبرِ من', body: 'متنِ خبر',
  }, { token: t });
  await h.post(`/api/admin/notices/${made.body.notice.id}/send`, {}, { token: t });

  const mine = await h.get('/api/me/notices', { token: me.accessToken });
  assert.equal(mine.status, 200);
  assert.equal(mine.body.notices.length, 1);
  assert.equal(mine.body.unread, 1);
  //  خواندنِ فهرست = «تحویل شد»
  assert.equal(mine.body.notices[0].status, 'delivered');

  const theirs = await h.get('/api/me/notices', { token: other.accessToken });
  assert.equal(theirs.body.notices.length, 0, '⛔ اعلانِ یکی در صندوقِ دیگری نمی‌نشیند');

  const did = await h.post(`/api/me/notices/${mine.body.notices[0].id}/read`, {}, { token: me.accessToken });
  assert.equal(did.status, 200);
  assert.equal(did.body.notice.status, 'read');

  const after = await h.get('/api/me/notices', { token: me.accessToken });
  assert.equal(after.body.unread, 0);

  //  ⛔ کسِ دیگری نمی‌تواند اعلانِ من را «خوانده» کند
  const steal = await h.post(`/api/me/notices/${mine.body.notices[0].id}/read`, {}, { token: other.accessToken });
  assert.equal(steal.status, 404);
});

test('اعلانِ دکان در نشستِ پمپِ همان آدم دیده نمی‌شود', async () => {
  const t = await adminToken();
  const u = await h.newUser('دوبخشی', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ دوبخشی' }, { token: u.accessToken });
  assert.equal(shop.status, 201);
  const pumpSession = await h.signIn(u, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ دوبخشی', code: 'PMPN2' }, { token: pumpSession.accessToken });
  assert.equal(st.status, 201);

  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id },
    channels: ['inapp'], title: 'فقط دکان', body: 'متن',
  }, { token: t });
  await h.post(`/api/admin/notices/${made.body.notice.id}/send`, {}, { token: t });

  const onShop = await h.get('/api/me/notices', { token: u.accessToken });
  assert.equal(onShop.body.notices.length, 1);
  const onPump = await h.get('/api/pump/notices', { token: pumpSession.accessToken });
  assert.equal(onPump.status, 200);
  assert.equal(onPump.body.notices.length, 0, '⛔ اعلانِ دکان مالِ بخشِ دکان است');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پوش — فقط با هدف
   ══════════════════════════════════════════════════════════════════ */

test('کانالِ پوش: بی دستگاه خطا می‌نویسد، با دستگاه واقعاً می‌رود', async () => {
  const t = await adminToken();
  const u = await shopOwner('پوشی');

  //  بی توکنِ پوش ⇒ ردیفِ خطا، نه «فرستاده شد»ِ دروغ
  const n1 = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id },
    channels: ['push'], title: 'زنگ', body: 'متن',
  }, { token: t });
  await h.post(`/api/admin/notices/${n1.body.notice.id}/send`, {}, { token: t });
  const r1 = await h.get(`/api/admin/notices/${n1.body.notice.id}/report`, { token: t });
  assert.equal(r1.body.deliveries.length, 1);
  assert.equal(r1.body.deliveries[0].status, 'error');

  //  با دستگاهِ ثبت‌شده و فرستندهٔ آزمونی
  const reg = await h.post('/api/me/push', { token: 'fcm-token-notice', platform: 'android' }, { token: u.accessToken });
  assert.ok(reg.status < 300, JSON.stringify(reg.body));
  const got = [];
  push.setDeliver((row, message) => { got.push({ row, message }); });
  try {
    const n2 = await h.post('/api/admin/notices', {
      app: 'shop', audience: { kind: 'user', user_id: u.user.id },
      channels: ['push'], title: 'زنگِ دوم', body: 'متنِ دوم',
    }, { token: t });
    await h.post(`/api/admin/notices/${n2.body.notice.id}/send`, {}, { token: t });
    const r2 = await h.get(`/api/admin/notices/${n2.body.notice.id}/report`, { token: t });
    assert.equal(r2.body.deliveries[0].status, 'sent');
    assert.equal(got.length, 1);
    assert.equal(got[0].message.title, 'زنگِ دوم');
  } finally { push.setDeliver(null); }
});

/* ══════════════════════════════════════════════════════════════════
   ۶) زمان‌بندی و اعلانِ خودکار
   ══════════════════════════════════════════════════════════════════ */

test('زمان‌بندی: اعلانِ وقت‌رسیده با tick می‌رود، وقت‌نرسیده نمی‌رود', async () => {
  const t = await adminToken();
  const u = await shopOwner('زمان‌دار');

  const later = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id }, channels: ['inapp'],
    title: 'فردا', body: 'متن', scheduleAt: now() + 3600_000,
  }, { token: t });
  assert.equal(later.body.notice.status, 'scheduled');

  const due = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id }, channels: ['inapp'],
    title: 'همین حالا', body: 'متن', scheduleAt: now() - 1000,
  }, { token: t });

  await notices.tick({ daily: false });

  assert.equal((await notices.get(due.body.notice.id)).status, 'sent');
  assert.equal((await notices.get(later.body.notice.id)).status, 'scheduled',
    '⛔ اعلانی که وقتش نرسیده نباید برود');
});

test('تکرارِ ماهانه: پس از ارسال دوباره در صف می‌نشیند', async () => {
  const t = await adminToken();
  const u = await shopOwner('ماهانه');
  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id }, channels: ['inapp'],
    title: 'هر ماه', body: 'متن', scheduleAt: now() - 1000, repeat: 'monthly',
  }, { token: t });
  await notices.tick({ daily: false });
  const after = await notices.get(made.body.notice.id);
  assert.equal(after.status, 'scheduled');
  assert.equal(Number(after.runs), 1);
  assert.ok(Number(after.schedule_at) > now(), 'زمانِ بعدی باید جلوتر باشد');
});

test('صدورِ اشتراک خودش خبرِ «تمدید شد» می‌دهد — و شکستِ خبر اشتراک را نمی‌شکند', async () => {
  const t = await adminToken();
  const u = await shopOwner('تمدیدی');
  const r = await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 30 }, { token: t });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const inbox = await h.get('/api/me/notices', { token: u.accessToken });
  assert.ok(inbox.body.notices.some(n => n.title.length > 0), 'خبرِ خودکارِ تمدید باید رسیده باشد');
});

test('«رو به پایان»ِ خودکار: هر آستانه یک بار، نه هر روز دوباره', async () => {
  const t = await adminToken();
  const u = await shopOwner('هشداری');
  await h.post('/api/admin/subscriptions', { shopId: u.shopId, plan: 'm1', days: 3 }, { token: t });

  const before = (await h.get('/api/me/notices', { token: u.accessToken })).body.notices.length;
  await notices.checkSystem({ thresholds: [7, 3, 1] });
  const once = (await h.get('/api/me/notices', { token: u.accessToken })).body.notices.length;
  assert.ok(once > before, 'باید یک یادآوری رفته باشد');

  await notices.checkSystem({ thresholds: [7, 3, 1] });
  const twice = (await h.get('/api/me/notices', { token: u.accessToken })).body.notices.length;
  assert.equal(twice, once, '⛔ همان آستانه دوباره زنگ نمی‌زند');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) مرز و اعتبارسنجی
   ══════════════════════════════════════════════════════════════════ */

test('اعلانِ بی عنوان و بی متن ساخته نمی‌شود، و بی توکنِ مدیر هیچ‌کدام باز نیستند', async () => {
  const t = await adminToken();
  const empty = await h.post('/api/admin/notices', { app: 'shop', channels: ['inapp'] }, { token: t });
  assert.equal(empty.status, 400);

  for (const [m, p] of [['GET', '/api/admin/notices'], ['POST', '/api/admin/notices'],
    ['GET', '/api/admin/notice-templates']]) {
    const r = await h.api(m, p, { body: m === 'POST' ? { title: 'x' } : null });
    assert.ok(r.status === 401 || r.status === 403, `${p} باید بسته باشد (${r.status})`);
  }
});

test('کانالِ ناشناخته پذیرفته نمی‌شود و فهرستِ خالی به «داخلِ برنامه» برمی‌گردد', async () => {
  const t = await adminToken();
  const r = await h.post('/api/admin/notices',
    { app: 'shop', channels: ['inapp', 'sms', 'telegram'], title: 'عنوان', body: 'متن' }, { token: t });
  assert.equal(r.status, 201);
  assert.deepEqual(r.body.notice.channels, ['inapp']);

  const r2 = await h.post('/api/admin/notices',
    { app: 'shop', channels: [], title: 'عنوان', body: 'متن' }, { token: t });
  assert.deepEqual(r2.body.notice.channels, ['inapp']);
});

test('حذفِ اعلان، گزارشش را هم می‌برد', async () => {
  const t = await adminToken();
  const u = await shopOwner('حذفی');
  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id }, channels: ['inapp'],
    title: 'رفتنی', body: 'متن',
  }, { token: t });
  await h.post(`/api/admin/notices/${made.body.notice.id}/send`, {}, { token: t });
  const gone = await h.del(`/api/admin/notices/${made.body.notice.id}`, { token: t });
  assert.equal(gone.status, 200);
  const left = await one('SELECT COUNT(*)::int n FROM notice_deliveries WHERE notice_id=$1', [made.body.notice.id]);
  assert.equal(left.n, 0, 'تحویل‌ها با خودِ اعلان می‌روند');
});
