'use strict';
/**
 * تأییدِ دوباره برای کارهای حساس — بندِ ۲۱.۱ و AUTH-06/AUTH-08 (`lib/reauth.js`).
 *
 * برای **هر** مسیرِ حساس سه چیز سنجیده می‌شود:
 *   ۱) نشستی که فقط «تازه‌سازی» شده (نه ورود) ⇒ ۴۰۳ `reauth_required` و هیچ اثری
 *   ۲) پس از `POST /api/auth/reauth` با رمز ⇒ همان درخواست انجام می‌شود
 *   ۳) نشستی که همین حالا وارد شده ⇒ بی پرسشِ دوباره (رفتارِ امروزِ برنامه‌ها)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { one, query } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');

test.before(async () => { await h.start(); });
test.after(async () => { config.tokens.reauthEnforced = true; await h.stop(); });

/** «یک ساعت بعد»: برنامه توکن را تازه کرده — تازه‌سازی احراز نیست */
async function staleSession(u) {
  const r = await h.post('/api/auth/refresh', { refreshToken: u.refreshToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { ...u, accessToken: r.body.accessToken, refreshToken: r.body.refreshToken };
}
const reauthWithPassword = (u) => h.post('/api/auth/reauth', { password: u.password }, { token: u.accessToken });

function assertNeedsReauth(r, label) {
  assert.equal(r.status, 403, `${label} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  assert.equal(r.body.error.code, 'reauth_required', label);
  assert.match(r.body.error.message, /دوباره/);
}

async function shopWithStaff(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const members = (await h.get('/api/shop/members', { token: owner.accessToken })).body.members;
  const mid = members.find((m) => m.userId === staff.user.id || m.user_id === staff.user.id)?.id;
  assert.ok(mid, JSON.stringify(members));
  return { owner, staff, mid };
}

test('خروج از همهٔ دستگاه‌ها (AUTH-06): نشستِ کهنه ۴۰۳، پس از تأییدِ دوباره انجام', async () => {
  const fresh = await h.newUser('همه‌جا');
  const stale = await staleSession(fresh);
  assertNeedsReauth(await h.post('/api/auth/logout-all', {}, { token: stale.accessToken }), 'logout-all');
  //  هیچ نشستی بسته نشد
  assert.equal((await h.get('/api/me', { token: stale.accessToken })).status, 200);

  const wrong = await h.post('/api/auth/reauth', { password: 'Wrong!pass1' }, { token: stale.accessToken });
  assert.equal(wrong.status, 401);
  assertNeedsReauth(await h.post('/api/auth/logout-all', {}, { token: stale.accessToken }), 'پس از رمزِ غلط');

  const ok = await reauthWithPassword(stale);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.freshUntil > Date.now());
  const out = await h.post('/api/auth/logout-all', {}, { token: stale.accessToken });
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.ok(out.body.sessions >= 1);

  const log = await one(`SELECT detail FROM audit_logs WHERE action='auth.reauth' AND user_id=$1`, [fresh.user.id]);
  const detail = typeof log.detail === 'string' ? JSON.parse(log.detail) : log.detail;
  assert.deepEqual(detail, { method: 'password' });
});

test('ورودِ تازه خودش احراز است: همان رفتارِ امروزِ برنامه‌ها', async () => {
  const u = await h.newUser('تازه‌وارد');
  assert.equal((await h.post('/api/auth/logout-all', {}, { token: u.accessToken })).status, 200);
  const again = await h.signIn(u);
  assert.equal((await h.post('/api/auth/logout-all', {}, { token: again.accessToken })).status, 200);
});

test('گذاشتنِ رمز برای حسابِ بی‌رمز: نشستِ کهنه ۴۰۳؛ با کدِ ایمیل (purpose=reauth) انجام', async () => {
  const email = 'reauth-otp@test.local';
  const c = await h.post('/api/auth/otp/request', { email });
  const made = await h.post('/api/auth/otp/verify', { email, code: c.body.devCode, name: 'بی‌رمز' });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const stale = await staleSession(made.body);

  assertNeedsReauth(await h.post('/api/auth/password', { newPassword: 'Taze!pass12' }, { token: stale.accessToken }), 'password');
  assert.equal((await one('SELECT password_hash FROM users WHERE email=$1', [email])).password_hash, null, 'رمزی گذاشته نشد');

  //  کدِ «ورود» برای تأییدِ دوباره پذیرفته نیست
  const loginCode = await h.post('/api/auth/otp/request', { email });
  const misuse = await h.post('/api/auth/reauth', { code: loginCode.body.devCode }, { token: stale.accessToken });
  assert.notEqual(misuse.status, 200);

  const asked = await h.post('/api/auth/reauth/code', {}, { token: stale.accessToken });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  const ok = await h.post('/api/auth/reauth', { code: asked.body.devCode }, { token: stale.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  const set = await h.post('/api/auth/password', { newPassword: 'Taze!pass12' }, { token: stale.accessToken });
  assert.equal(set.status, 200, JSON.stringify(set.body));
});

test('عوض کردنِ رمزِ حسابِ رمزدار: «رمزِ فعلی» خودش اثبات است — مثلِ قبل', async () => {
  const u = await staleSession(await h.newUser('رمزدار'));
  const r = await h.post('/api/auth/password', { currentPassword: u.password, newPassword: 'Digar!pass12' }, { token: u.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const bad = await h.post('/api/auth/password', { currentPassword: 'nope', newPassword: 'Digar!pass13' }, { token: u.accessToken });
  assert.equal(bad.status, 401);
});

test('افزودنِ شماره (راهِ ورود) به حساب: نشستِ کهنه ۴۰۳؛ عوض کردنِ نام آزاد می‌ماند', async () => {
  const u = await staleSession(await h.newUser('بی‌شماره'));
  assert.equal((await h.put('/api/me', { name: 'نامِ تازه' }, { token: u.accessToken })).status, 200, 'نام حساس نیست');
  assertNeedsReauth(await h.put('/api/me', { name: 'نامِ تازه', phone: '0791112233' }, { token: u.accessToken }), 'PUT /me phone');
  assert.equal((await one('SELECT phone FROM users WHERE id=$1', [u.user.id])).phone, null);
  assert.equal((await reauthWithPassword(u)).status, 200);
  const ok = await h.put('/api/me', { name: 'نامِ تازه', phone: '0791112233' }, { token: u.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
});

test('مجوز و نقشِ کارمندان: هر سه مسیر با نشستِ کهنه ۴۰۳ و پس از تأیید انجام', async () => {
  const t = await shopWithStaff('مجوز');
  const owner = await staleSession(t.owner);
  const calls = [
    ['PUT /shop/permissions/roles/manager', () => h.put('/api/shop/permissions/roles/manager', { perms: {} }, { token: owner.accessToken })],
    ['PUT /shop/permissions/members/:id', () => h.put(`/api/shop/permissions/members/${t.mid}`, { perms: {} }, { token: owner.accessToken })],
    ['PATCH /shop/members/:id', () => h.patch(`/api/shop/members/${t.mid}`, { role: 'manager' }, { token: owner.accessToken })],
  ];
  for (const [label, call] of calls) assertNeedsReauth(await call(), label);
  const staffRow = await one('SELECT role FROM shop_members WHERE id=$1', [t.mid]);
  assert.equal(staffRow.role, 'staff', 'نقش دست نخورد');

  assert.equal((await reauthWithPassword(owner)).status, 200);
  for (const [label, call] of calls) {
    const r = await call();
    assert.equal(r.status, 200, `${label} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
});

test('نشستِ کهنهٔ پمپ: نخستین رمز را بی تأییدِ دوباره نمی‌گذارد (امنیت ۶)؛ خروج از همه مثلِ پیش از این تغییر', async () => {
  //  گذاشتنِ رمز برای حسابِ بی‌رمز — یک حساب، دو نشستِ کهنه: دکان و پمپ
  const email = 'reauth-pump@test.local';
  const shopCode = await h.post('/api/auth/otp/request', { email });
  const made = await h.post('/api/auth/otp/verify', { email, code: shopCode.body.devCode, name: 'دوبخشی' });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const pumpCode = await h.post('/api/auth/otp/request', { email, app: 'pump' });
  const pumpIn = await h.post('/api/auth/otp/verify', { email, code: pumpCode.body.devCode, app: 'pump' });
  assert.ok([200, 201].includes(pumpIn.status), JSON.stringify(pumpIn.body));
  const shopStale = await staleSession(made.body);
  const pumpStale = await staleSession(pumpIn.body);

  assertNeedsReauth(await h.post('/api/auth/password', { newPassword: 'Taze!pass12' }, { token: shopStale.accessToken }), 'password/shop');
  /*
   *  ⛔ بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷ (یافتهٔ ۶): تا امروز این‌جا ۲۰۰ بود — توکنِ
   *  کهنهٔ پمپ روی حسابِ مشترکِ بی‌رمز نخستین رمز را می‌گذاشت و همان رمز
   *  برنامهٔ دکان را باز می‌کرد. حالا در هر بخش تازگی یا کدِ ایمیل لازم است.
   *  ورودِ تازهٔ پمپ «تازه» است (آزمونِ بعدی)، پس جریانِ عادیِ پمپ نمی‌شکند.
   */
  assertNeedsReauth(await h.post('/api/auth/password', { newPassword: 'Taze!pass12' }, { token: pumpStale.accessToken }), 'password/pump');
  assert.equal((await one('SELECT password_hash FROM users WHERE email=$1', [email])).password_hash, null, 'رمزی گذاشته نشد');
  const asked = await h.post('/api/auth/reauth/code', {}, { token: pumpStale.accessToken });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  assert.equal((await h.post('/api/auth/reauth', { code: asked.body.devCode }, { token: pumpStale.accessToken })).status, 200);
  const pumpSet = await h.post('/api/auth/password', { newPassword: 'Taze!pass12' }, { token: pumpStale.accessToken });
  assert.equal(pumpSet.status, 200, `پس از کدِ ایمیل انجام: ${JSON.stringify(pumpSet.body)}`);

  //  خروج از همهٔ دستگاه‌ها
  const u = await h.newUser('دوبخشیِ رمزدار');
  const pump = await h.signIn(u, 'pump');
  assertNeedsReauth(await h.post('/api/auth/logout-all', {}, { token: (await staleSession(u)).accessToken }), 'logout-all/shop');
  const out = await h.post('/api/auth/logout-all', {}, { token: (await staleSession(pump)).accessToken });
  assert.equal(out.status, 200, `پمپ نباید reauth_required بگیرد: ${JSON.stringify(out.body)}`);
  assert.equal(out.body.app, 'pump');

  //  `/api/me` اصلاً توکنِ پمپ را نمی‌پذیرد (`requireUser`) — پس این‌جا چیزی برای پمپ عوض نشده
  const pumpAgain = await h.signIn(u, 'pump');
  assert.equal((await h.get('/api/pump/me', { token: pumpAgain.accessToken })).status, 200, 'نشستِ پمپ سالم است');
  assert.equal((await h.put('/api/me', { phone: '09121112233' }, { token: pumpAgain.accessToken })).status, 401);
});

test('تازگی به همان نشست بسته است، و پس از پنجره تمام می‌شود', async () => {
  const u = await h.newUser('پنجره');
  const a = await staleSession(u);
  const b = await staleSession(await h.signIn(u));
  assert.equal((await reauthWithPassword(a)).status, 200);
  assertNeedsReauth(await h.post('/api/auth/logout-all', {}, { token: b.accessToken }), 'نشستِ دیگرِ همان آدم');

  //  ده دقیقه گذشت
  await query(
    `UPDATE login_attempts SET created_at = created_at - $2 WHERE scope='session_auth' AND identifier=$1`,
    [hashToken(a.accessToken), config.tokens.reauthWindowMs + 1000]
  );
  assertNeedsReauth(await h.post('/api/auth/logout-all', {}, { token: a.accessToken }), 'پس از پنجره');
  const row = await one(`SELECT identifier FROM login_attempts WHERE scope='session_auth' AND identifier=$1 LIMIT 1`, [hashToken(a.accessToken)]);
  assert.notEqual(row.identifier, a.accessToken, 'خودِ توکن هرگز ذخیره نمی‌شود');
});

test('بی توکن ۴۰۱؛ و کلیدِ بازگشت (AUTH_REAUTH=off) رفتارِ قبلی را برمی‌گرداند', async () => {
  assert.equal((await h.post('/api/auth/reauth', { password: 'x' })).status, 401);
  assert.equal((await h.post('/api/auth/reauth/code', {})).status, 401);
  const u = await staleSession(await h.newUser('بازگشت'));
  config.tokens.reauthEnforced = false;
  try {
    assert.equal((await h.post('/api/auth/logout-all', {}, { token: u.accessToken })).status, 200);
  } finally {
    config.tokens.reauthEnforced = true;
  }
});

/* ================================================================
 *  یافته‌های بازبینیِ امنیتی (۱۴۰۵/۰۷/۲۷) — هر آزمون پیش از رفع سرخ بود.
 * ================================================================ */

const deviceOf = async (accessToken) =>
  (await one('SELECT device_id FROM tokens WHERE token_hash=$1', [hashToken(accessToken)])).device_id;

test('امنیت ۳: دعوتِ مدیر و چرخاندنِ کدِ ثابت با نشستِ کهنه ۴۰۳؛ دعوتِ شاگرد مثلِ پیش', async () => {
  const t = await shopWithStaff('دعوتِ مدیر');
  const owner = await staleSession(t.owner);
  const shopId = (await one('SELECT shop_id FROM shop_members WHERE id=$1', [t.mid])).shop_id;
  const managerCodes = async () =>
    (await one(`SELECT COUNT(*)::int AS n FROM staff_codes WHERE shop_id=$1 AND role='manager'`, [shopId])).n;
  const generation = async () =>
    (await one('SELECT MAX(generation) AS g FROM staff_codes WHERE shop_id=$1 AND standing', [shopId])).g;
  const genBefore = await generation();

  assertNeedsReauth(await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken }), 'invite/manager');
  assertNeedsReauth(await h.post('/api/shop/staff-code', { role: 'manager', maxUses: 50 }, { token: owner.accessToken }), 'staff-code/manager');
  assertNeedsReauth(await h.post('/api/shop/staff-code/rotate', {}, { token: owner.accessToken }), 'staff-code/rotate');
  assert.equal(await managerCodes(), 0, 'هیچ کدِ مدیری ساخته نشد');
  assert.equal(String(await generation()), String(genBefore), 'کدِ ثابت نچرخید');

  //  شاگرد (و نه بالاتر) همان رفتارِ پیش را دارد
  const staff = await h.post('/api/shop/invite', { role: 'staff' }, { token: owner.accessToken });
  assert.equal(staff.status, 201, JSON.stringify(staff.body));

  assert.equal((await reauthWithPassword(owner)).status, 200);
  const mgr = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  assert.equal(mgr.status, 201, JSON.stringify(mgr.body));
  const rot = await h.post('/api/shop/staff-code/rotate', {}, { token: owner.accessToken });
  assert.equal(rot.status, 200, JSON.stringify(rot.body));
});

test('امنیت ۴: بستنِ دستگاهِ **دیگر** با نشستِ کهنه ۴۰۳ (هر دو در)؛ دستگاهِ خودِ نشست آزاد', async () => {
  const u = await h.newUser('دستگاهِ دیگر');
  const other = await h.post('/api/auth/login', {
    identifier: u.email, password: u.password, device: { deviceId: 'gooshi-digar-reauth', name: 'دیگر', platform: 'test' },
  });
  assert.equal(other.status, 200, JSON.stringify(other.body));
  const stale = await staleSession(u);
  const otherId = await deviceOf(other.body.accessToken);
  assert.ok(otherId);
  assert.notEqual(otherId, await deviceOf(stale.accessToken));

  assertNeedsReauth(await h.del(`/api/me/devices/${otherId}`, { token: stale.accessToken }), 'DELETE /me/devices/:other');
  assertNeedsReauth(await h.del(`/api/portal/devices/${otherId}`, { token: stale.accessToken }), 'DELETE /portal/devices/:other');
  assert.equal((await h.get('/api/me', { token: other.body.accessToken })).status, 200, 'دستگاهِ دیگر هنوز وارد است');

  assert.equal((await reauthWithPassword(stale)).status, 200);
  const gone = await h.del(`/api/me/devices/${otherId}`, { token: stale.accessToken });
  assert.equal(gone.status, 200, JSON.stringify(gone.body));
  assert.equal((await h.get('/api/me', { token: other.body.accessToken })).status, 401);

  //  بستنِ دستگاهِ خودِ همین نشست (خروج) تأیید نمی‌خواهد
  const v = await staleSession(await h.newUser('دستگاهِ خود'));
  const mine = await h.del(`/api/me/devices/${await deviceOf(v.accessToken)}`, { token: v.accessToken });
  assert.equal(mine.status, 200, JSON.stringify(mine.body));
});

test('امنیت ۴: درِ پورتال نشستِ دستگاهِ کسِ دیگری را نمی‌بندد', async () => {
  const a = await h.newUser('پورتالِ الف');
  const b = await h.newUser('پورتالِ ب');
  const bDevice = await deviceOf(b.accessToken);
  const r = await h.del(`/api/portal/devices/${bDevice}`, { token: a.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await h.get('/api/me', { token: b.accessToken })).status, 200, 'نشستِ ب سالم ماند');
});

test('امنیت ۵: حدسِ «رمزِ فعلی» در /auth/password پشتِ همان قفلِ /auth/reauth است', async () => {
  const u = await staleSession(await h.newUser('حدسِ رمز'));
  const before = (await one('SELECT password_hash FROM users WHERE id=$1', [u.user.id])).password_hash;
  const saved = config.rateLimit.lockoutTries;
  config.rateLimit.lockoutTries = 3;
  try {
    for (let i = 0; i < 3; i++) {
      const bad = await h.post('/api/auth/password', { currentPassword: `Wrong!pass${i}`, newPassword: 'Gozar!pass12' }, { token: u.accessToken });
      assert.equal(bad.status, 401, JSON.stringify(bad.body));
    }
    const right = await h.post('/api/auth/password', { currentPassword: u.password, newPassword: 'Gozar!pass12' }, { token: u.accessToken });
    assert.equal(right.status, 429, `قفل ⇒ ۴۲۹: ${JSON.stringify(right.body)}`);
    assert.equal(right.body.error.code, 'locked_out');
    assert.equal((await one('SELECT password_hash FROM users WHERE id=$1', [u.user.id])).password_hash, before, 'رمز عوض نشد');
    //  همان قفل: /auth/reauth هم بسته است
    assert.equal((await reauthWithPassword(u)).status, 429);
  } finally {
    config.rateLimit.lockoutTries = saved;
  }
});

test('امنیت ۵: /auth/password سقفِ «به‌ازای هر نشست» دارد', async () => {
  const u = await h.newUser('سقفِ نشست');
  const stale = await staleSession(u);
  const max = config.rateLimit.passwordSessionMax || 10;
  for (let i = 0; i < max; i++) {
    const bad = await h.post('/api/auth/password', { currentPassword: `Wrong!pass${i}`, newPassword: 'Gozar!pass12' }, { token: stale.accessToken });
    assert.equal(bad.status, 401, `${i}: ${JSON.stringify(bad.body)}`);
  }
  const over = await h.post('/api/auth/password', { currentPassword: 'Wrong!passX', newPassword: 'Gozar!pass12' }, { token: stale.accessToken });
  assert.equal(over.status, 429, JSON.stringify(over.body));
  //  نشستِ دیگرِ همان آدم سطلِ خودش را دارد
  const fresh = await h.signIn(u);
  const ok = await h.post('/api/auth/password', { currentPassword: u.password, newPassword: 'Gozar!pass12' }, { token: fresh.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
});

test('امنیت ۶: نشستِ تازهٔ پمپ (همین حالا وارد شده) نخستین رمز را مثلِ پیش می‌گذارد', async () => {
  const email = 'reauth-pump-fresh@test.local';
  const c = await h.post('/api/auth/otp/request', { email, app: 'pump' });
  const pumpIn = await h.post('/api/auth/otp/verify', { email, code: c.body.devCode, name: 'پمپیِ تازه', app: 'pump' });
  assert.ok([200, 201].includes(pumpIn.status), JSON.stringify(pumpIn.body));
  const set = await h.post('/api/auth/password', { newPassword: 'Pomp!pass12' }, { token: pumpIn.body.accessToken });
  assert.equal(set.status, 200, `جریانِ عادیِ پمپ نباید بشکند: ${JSON.stringify(set.body)}`);
  assert.equal((await h.get('/api/pump/me', { token: pumpIn.body.accessToken })).status, 200);
});
