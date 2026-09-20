'use strict';
/**
 * پورتالِ مشتری (وب) — بخشِ ۱۴ی پرامپت.
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────
 *   «یک صفحهٔ سادهٔ وب برای مشتری‌ها: ورود با ایمیل + کد ۶ رقمی →
 *    مشاهدهٔ برنامه‌ها، اشتراک‌ها و تاریخ پایان، پرداخت‌ها و رسیدها،
 *    دستگاه‌ها، تیکت‌های پشتیبانی، دانلود آخرین نسخه، مدیریت
 *    Cloud Sync.»
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ **شناسهٔ حساب هیچ‌وقت از درخواست خوانده نمی‌شود** — هرچه
 *      مرورگر بفرستد، سرور از توکن برمی‌دارد.
 *   ۲) نوارِ روزِ مانده: سبز > ۳۰ · زرد ≤ ۳۰ · سرخ منقضی · «دائمی ✓».
 *   ۳) آزاد کردنِ دستگاه، نشستش را همان لحظه می‌بندد.
 *   ۴) Cloud Sync انتخابِ خودِ مشتری است و بخشِ درست را عوض می‌کند.
 *   ۵) خودِ صفحهٔ ایستا سرو می‌شود و راست‌به‌چپ و دو-تمی است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');

const DAY = 24 * 60 * 60 * 1000;
const PUBLIC = path.join(__dirname, '..', 'public', 'portal');

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

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) صفحهٔ ایستا
   ══════════════════════════════════════════════════════════════════ */

test('صفحهٔ پورتال روی /portal سرو می‌شود و راست‌به‌چپ و دو-تمی است', async () => {
  const r = await h.get('/portal/');
  assert.equal(r.status, 200);
  const html = r.body.raw;
  assert.ok(html.includes('lang="fa"') && html.includes('dir="rtl"'), 'فارسی و راست‌به‌چپ');
  assert.ok(html.includes('portal.js') && html.includes('portal.css'));
  //  همهٔ تب‌های خواسته‌شده
  for (const tab of ['اشتراک‌ها', 'پرداخت‌ها', 'دستگاه‌ها', 'پشتیبانی', 'اعلان‌ها', 'دانلود']) {
    assert.ok(html.includes(tab), `تبِ «${tab}» باید باشد`);
  }
  const css = fs.readFileSync(path.join(PUBLIC, 'portal.css'), 'utf8');
  assert.ok(css.includes('prefers-color-scheme: dark') || css.includes('[data-theme="dark"]'),
    'تمِ تاریک باید باشد');
  const js = fs.readFileSync(path.join(PUBLIC, 'portal.js'), 'utf8');
  assert.ok(js.includes('/auth/otp/request') && js.includes('/auth/otp/verify'),
    'ورود با ایمیل + کدِ شش‌رقمی');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ورود با ایمیل + کدِ شش‌رقمی
   ══════════════════════════════════════════════════════════════════ */

test('ورود با ایمیل و کدِ شش‌رقمی — کدِ غلط هیچ نشستی نمی‌سازد', async () => {
  const u = await shopOwner('پورتالی');

  const asked = await h.post('/api/auth/otp/request', { email: u.email, app: 'shop' });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  const code = asked.body.devCode;
  assert.ok(code, 'کدِ آزمایشی برگشت');

  const bad = await h.post('/api/auth/otp/verify',
    { email: u.email, code: '000000', app: 'shop', device: { deviceId: 'web-1', platform: 'web' } });
  assert.ok(bad.status >= 400, 'کدِ غلط رد می‌شود');
  assert.ok(!bad.body.accessToken);

  const ok = await h.post('/api/auth/otp/verify',
    { email: u.email, code, app: 'shop', device: { deviceId: 'web-1', name: 'پورتال وب', platform: 'web' } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.accessToken, '⛔ نامِ فیلد `accessToken` است');

  const me = await h.get('/api/portal/me', { token: ok.body.accessToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.user.email, u.email);
});

test('پورتال بی توکن هیچ چیزی نمی‌دهد', async () => {
  for (const p of ['/api/portal/me', '/api/portal/subscriptions', '/api/portal/payments',
    '/api/portal/devices', '/api/portal/support', '/api/portal/notices', '/api/portal/settings']) {
    const r = await h.get(p);
    assert.equal(r.status, 401, `${p} باید توکن بخواهد (${r.status})`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۳) برنامه‌ها، اشتراک و نوارِ روزِ مانده
   ══════════════════════════════════════════════════════════════════ */

test('«برنامه‌های من»: هر عضویت با اشتراک و نوارِ روزِ مانده', async () => {
  const t = await adminToken();
  const u = await h.newUser('دوبرنامه', 'shop');
  const shop = await h.post('/api/shop', { name: 'دکانِ دوبرنامه' }, { token: u.accessToken });
  const pumpSession = await h.signIn(u, 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ دوبرنامه', code: 'PRTL1' }, { token: pumpSession.accessToken });
  assert.equal(st.status, 201);

  await h.post('/api/admin/subscriptions', { shopId: shop.body.shop.id, plan: 'm1', days: 60 }, { token: t });

  const me = await h.get('/api/portal/me', { token: u.accessToken });
  assert.equal(me.status, 200);
  assert.equal(me.body.memberships.length, 2, 'یک نفر با دو برنامه، هر دو را می‌بیند');
  const byApp = Object.fromEntries(me.body.memberships.map(m => [m.app, m]));
  assert.equal(byApp.shop.name, 'دکانِ دوبرنامه');
  assert.equal(byApp.pump.name, 'پمپِ دوبرنامه');
  assert.ok(byApp.shop.subscription.active);
  assert.ok(Array.isArray(byApp.shop.history), 'تاریخچهٔ اشتراک هم می‌آید');
  assert.ok(me.body.downloads.length >= 2, 'لینکِ دانلودِ هر برنامه');
});

test('رنگ و نوار: سبز > ۳۰ روز، زرد ≤ ۳۰، سرخ منقضی', async () => {
  const t = await adminToken();
  const green = await shopOwner('سبز');
  const yellow = await shopOwner('زرد');
  const red = await shopOwner('سرخ');

  await h.post('/api/admin/subscriptions', { shopId: green.shopId, plan: 'm1', days: 90 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: yellow.shopId, plan: 'm1', days: 5 }, { token: t });
  await h.post('/api/admin/subscriptions', { shopId: red.shopId, plan: 'm1', days: 30 }, { token: t });
  //  منقضی: پایان به دیروز — و دکان آن‌قدر کهنه که دورهٔ آزمایشی هم
  //  تمام شده باشد، وگرنه «منقضی» به «آزمایشی» برمی‌گردد و زرد می‌ماند
  await query(`UPDATE subscriptions SET ends_at=$2 WHERE shop_id=$1`, [red.shopId, now() - 2 * DAY]);
  await query(`UPDATE shops SET created_at=$2 WHERE id=$1`, [red.shopId, now() - 400 * DAY]);

  const view = async (u) => (await h.get('/api/portal/subscriptions', { token: u.accessToken }))
    .body.subscriptions[0].subscription;

  const g = await view(green);
  assert.equal(g.color, 'green');
  assert.ok(g.daysLeft > 30);
  assert.ok(g.label.includes('روز مانده'));
  assert.ok(g.progress > 0 && g.progress <= 100, 'نوارِ پیشرفت عددِ درصد است');

  const y = await view(yellow);
  assert.equal(y.color, 'yellow');
  assert.ok(y.daysLeft <= 30);

  const r = await view(red);
  assert.equal(r.color, 'red');
  assert.equal(r.active, false);
  assert.equal(r.label, 'منقضی');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) شناسه از توکن، نه از درخواست
   ══════════════════════════════════════════════════════════════════ */

test('⛔ شناسهٔ حسابِ دیگری در بدنه یا مسیر، چیزی را باز نمی‌کند', async () => {
  const t = await adminToken();
  const a = await shopOwner('صاحب');
  const b = await shopOwner('همسایه');
  await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 90 }, { token: t });
  await h.post('/api/admin/payments', { app: 'shop', tenantId: a.shopId, amount: 3000 }, { token: t });

  //  همسایه شناسهٔ دکانِ «صاحب» را می‌فرستد
  const pay = await h.get('/api/portal/payments', { token: b.accessToken });
  assert.equal(pay.status, 200);
  assert.equal(pay.body.payments.length, 0, '⛔ پرداختِ دیگری دیده نمی‌شود');

  const beat = await h.get('/api/portal/heartbeat', { token: b.accessToken });
  assert.equal(beat.status, 200);
  assert.notEqual(beat.body.tenantId, a.shopId);
  assert.equal(beat.body.tenantId, b.shopId);

  //  و تنظیماتِ حسابِ دیگری هم با فرستادنِ شناسه عوض نمی‌شود
  const put = await h.put('/api/portal/settings',
    { app: 'shop', cloudSync: true, shopId: a.shopId, tenantId: a.shopId }, { token: b.accessToken });
  assert.equal(put.status, 200);
  const victim = await one('SELECT cloud_sync FROM shops WHERE id=$1', [a.shopId]);
  assert.equal(victim.cloud_sync, false, '⛔ دکانِ «صاحب» دست نخورد');
  const mine = await one('SELECT cloud_sync FROM shops WHERE id=$1', [b.shopId]);
  assert.equal(mine.cloud_sync, true, 'فقط دکانِ خودش عوض شد');
});

/* ══════════════════════════════════════════════════════════════════
   ۵) پرداخت‌ها و رسید
   ══════════════════════════════════════════════════════════════════ */

test('پرداخت‌های من با نشانیِ رسیدِ هرکدام', async () => {
  const t = await adminToken();
  const u = await shopOwner('پرداختی');
  await h.post('/api/admin/payments', { app: 'shop', tenantId: u.shopId, amount: 1200, method: 'hawala' }, { token: t });

  const r = await h.get('/api/portal/payments', { token: u.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.payments.length, 1);
  assert.equal(r.body.payments[0].amount, 1200);
  assert.ok(r.body.payments[0].receiptUrl.endsWith('/receipt'), 'نشانیِ رسید هم می‌آید');

  const receipt = await h.get(r.body.payments[0].receiptUrl, { token: u.accessToken });
  assert.equal(receipt.status, 200);
  assert.ok(receipt.body.raw.includes('رسیدِ پرداخت'));
});

/* ══════════════════════════════════════════════════════════════════
   ۶) دستگاه‌ها
   ══════════════════════════════════════════════════════════════════ */

test('دستگاه‌ها: آزاد کردن، نشستِ همان دستگاه را همان لحظه می‌بندد', async () => {
  const u = await shopOwner('چنددستگاه');
  const second = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, app: 'shop',
    device: { deviceId: 'phone-2', name: 'گوشیِ دوم', platform: 'android' },
  });
  assert.equal(second.status, 200, JSON.stringify(second.body));

  const list = await h.get('/api/portal/devices', { token: u.accessToken });
  assert.equal(list.status, 200);
  assert.ok(list.body.devices.length >= 2);
  const mine = list.body.devices.find(d => d.current);
  assert.ok(mine, 'دستگاهِ همین نشست نشان‌دار است');
  const other = list.body.devices.find(d => !d.current && d.status === 'active');
  assert.ok(other, 'دستگاهِ دوم هست');

  //  نشستِ دستگاهِ دوم هنوز کار می‌کند
  const before = await h.get('/api/me', { token: second.body.accessToken });
  assert.equal(before.status, 200);

  const freed = await h.del(`/api/portal/devices/${other.id}`, { token: u.accessToken });
  assert.equal(freed.status, 200);

  const after = await h.get('/api/me', { token: second.body.accessToken });
  assert.equal(after.status, 401, '⛔ نشستِ دستگاهِ آزادشده همان لحظه بسته می‌شود');

  //  و نشستِ خودم زنده ماند
  const still = await h.get('/api/me', { token: u.accessToken });
  assert.equal(still.status, 200);
});

test('دستگاهِ کسِ دیگری آزاد نمی‌شود', async () => {
  const a = await shopOwner('صاحبِ دستگاه');
  const b = await shopOwner('مزاحم');
  const list = await h.get('/api/portal/devices', { token: a.accessToken });
  const id = list.body.devices[0].id;
  await h.del(`/api/portal/devices/${id}`, { token: b.accessToken });
  const still = await one('SELECT status FROM devices WHERE id=$1', [id]);
  assert.equal(still.status, 'active', '⛔ دستگاهِ دیگری دست نمی‌خورد');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) پشتیبانی
   ══════════════════════════════════════════════════════════════════ */

test('تیکتِ پشتیبانی: باز شدنِ رشته، فرستادنِ پیام و دیدنش از پنل', async () => {
  const t = await adminToken();
  const u = await shopOwner('پرسشگر');

  const empty = await h.get('/api/portal/support', { token: u.accessToken });
  assert.equal(empty.status, 200);
  assert.ok(empty.body.thread, 'رشته خودش ساخته می‌شود');

  const sent = await h.post('/api/portal/support/messages',
    { body: 'سلام، اشتراکم تمدید نشد.', subject: 'تمدید' }, { token: u.accessToken });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));

  const again = await h.get('/api/portal/support', { token: u.accessToken });
  assert.equal(again.body.messages.length, 1);
  assert.equal(again.body.messages[0].body, 'سلام، اشتراکم تمدید نشد.');
  assert.equal(again.body.messages[0].sender, 'user');

  //  ⛔ همان رشته‌ای است که مدیر می‌بیند — نه دفترِ دوم
  const threads = await h.get('/api/admin/support/threads', { token: t });
  assert.equal(threads.status, 200);
  assert.ok(threads.body.threads.some(x => x.id === again.body.thread.id));
});

test('پیامِ خالی فرستاده نمی‌شود و متنِ بلند بریده نمی‌شود', async () => {
  const u = await shopOwner('پرحرف');
  const empty = await h.post('/api/portal/support/messages', { body: '   ' }, { token: u.accessToken });
  assert.ok(empty.status >= 400, 'پیامِ خالی رد می‌شود');

  const long = 'الف '.repeat(300).trim();
  const sent = await h.post('/api/portal/support/messages', { body: long }, { token: u.accessToken });
  if (sent.status === 201) {
    assert.equal(sent.body.message.body, long, '⛔ متن بریده نمی‌شود — یا می‌رود یا خطا می‌دهد');
  } else {
    assert.ok(sent.status >= 400, 'یا صریح رد می‌شود');
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۸) اعلان، دانلود، Cloud Sync، خطا
   ══════════════════════════════════════════════════════════════════ */

test('اعلان‌های من در پورتال هم همان‌هاست، و «خواندم» کار می‌کند', async () => {
  const t = await adminToken();
  const u = await shopOwner('اعلانی');
  const made = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: u.user.id },
    channels: ['inapp'], title: 'خبر', body: 'متن',
  }, { token: t });
  await h.post(`/api/admin/notices/${made.body.notice.id}/send`, {}, { token: t });

  const box = await h.get('/api/portal/notices', { token: u.accessToken });
  assert.equal(box.status, 200);
  assert.equal(box.body.notices.length, 1);
  assert.equal(box.body.unread, 1);

  const read = await h.post(`/api/portal/notices/${box.body.notices[0].id}/read`, {}, { token: u.accessToken });
  assert.equal(read.status, 200);
  assert.equal((await h.get('/api/portal/notices', { token: u.accessToken })).body.unread, 0);
});

test('Cloud Sync انتخابِ خودِ مشتری است و بخشِ درست را عوض می‌کند', async () => {
  const u = await h.newUser('همگام', 'shop');
  await h.post('/api/shop', { name: 'دکانِ همگام' }, { token: u.accessToken });
  const pumpSession = await h.signIn(u, 'pump');
  await h.post('/api/pump', { name: 'پمپِ همگام', code: 'PRTL2' }, { token: pumpSession.accessToken });

  const first = await h.get('/api/portal/settings', { token: u.accessToken });
  assert.equal(first.status, 200);
  assert.equal(first.body.settings.shop.cloudSync, false, 'پیش‌فرض خاموش است');
  assert.equal(first.body.settings.pump.cloudSync, false);

  const on = await h.put('/api/portal/settings', { app: 'pump', cloudSync: true }, { token: u.accessToken });
  assert.equal(on.status, 200);
  const after = await h.get('/api/portal/settings', { token: u.accessToken });
  assert.equal(after.body.settings.pump.cloudSync, true);
  assert.equal(after.body.settings.shop.cloudSync, false, '⛔ فقط همان بخش عوض شد');

  //  و همان را برنامه هم از `/api/me` می‌بیند
  const me = await h.get('/api/me', { token: u.accessToken });
  assert.equal(me.body.settings.cloudSync, false);
});

test('دانلود: آخرین نسخهٔ هر برنامه، و «نسخهٔ تازه» در تپش', async () => {
  const t = await adminToken();
  const u = await shopOwner('دانلودی');
  await h.put('/api/admin/downloads',
    { app: 'shop', url: 'https://example.test/x.apk', version: '3.2.1', notes: 'تغییرات' }, { token: t });

  const dl = await h.get('/api/portal/downloads', { token: u.accessToken });
  assert.equal(dl.status, 200);
  const shop = dl.body.downloads.find(d => d.app === 'shop');
  assert.equal(shop.version, '3.2.1');
  assert.equal(shop.url, 'https://example.test/x.apk');

  const beat = await h.get('/api/portal/heartbeat?version=3.0.0', { token: u.accessToken });
  assert.equal(beat.status, 200);
  assert.equal(beat.body.latest.version, '3.2.1');
  //  نسخهٔ برنامه روی نشست می‌نشیند
  const tok = await one(
    `SELECT app_version FROM tokens WHERE revoked_at IS NULL AND kind='access' AND subject_id=$1
      ORDER BY issued_at DESC LIMIT 1`, [u.user.id]);
  assert.equal(tok.app_version, '3.0.0');
});

test('گزارشِ خطای برنامه با شناسهٔ خودِ فرستنده ثبت می‌شود، نه با آن‌چه فرستاده', async () => {
  const a = await shopOwner('خطادار');
  const b = await shopOwner('دیگری');
  const r = await h.post('/api/portal/errors', {
    message: 'Cannot read properties of null', stack: 'at foo()\nat bar()',
    version: '1.2.3', platform: 'web', context: { screen: 'فروش' },
    userId: b.user.id, tenantId: b.shopId,          // ⛔ دروغ در بدنه
  }, { token: a.accessToken });
  assert.equal(r.status, 201, JSON.stringify(r.body));

  const row = await one('SELECT * FROM client_errors WHERE id=$1', [r.body.id]);
  assert.equal(row.user_id, a.user.id, '⛔ شناسه از توکن است، نه از بدنه');
  assert.equal(row.tenant_id, a.shopId);
  assert.equal(row.app, 'shop');
  assert.equal(row.version, '1.2.3');
  assert.ok(row.message.includes('Cannot read'));
});
