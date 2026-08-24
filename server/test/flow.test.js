'use strict';
/* تست جریان کامل: ثبت‌نام → اشتراک → فعال‌سازی → License → قفل قابلیت‌ها */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const os = require('os');
const path = require('path');

// دیتابیس و کلید موقت، جدا از داده‌های واقعی
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'tohid-lic-test-'));
process.env.DATA_DIR = TMP;
process.env.KEYS_DIR = path.join(TMP, 'keys');
process.env.DB_PATH = path.join(TMP, 'test.db');
process.env.CORS_ORIGINS = 'https://example.test';
process.env.RATE_AUTH_MAX = '500';
process.env.RATE_GENERAL_MAX = '100000';
process.env.LOGIN_LOCKOUT_TRIES = '500';

const config = require('../src/config');
const ck = require('../src/lib/crypto-keys');
const { getDb, newId, now } = require('../src/db');
const pw = require('../src/lib/password');
const licenseLib = require('../src/lib/license');
const subsLib = require('../src/lib/subscriptions');
const timeLib = require('../src/lib/time');
const { resetAll } = require('../src/middleware/ratelimit');

let server, base, app;

async function boot() {
  const kp = await ck.generateKeyPair();
  ck.writeKeyPair(config.keysDir, kp);
  const { createApp } = require('../src/app');
  app = await createApp();
  await new Promise(r => { server = app.listen(0, '127.0.0.1', r); });
  base = `http://127.0.0.1:${server.address().port}`;
}

async function req(pathname, { method = 'GET', body, token: tk } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (tk) headers.Authorization = 'Bearer ' + tk;
  const res = await fetch(base + pathname, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch {}
  return { status: res.status, data };
}

test.before(async () => { await boot(); });
test.after(() => { if (server) server.close(); fs.rmSync(TMP, { recursive: true, force: true }); });

// ============ ۱) ثبت‌نام و ورود ============
let userToken, userId, refreshToken;

test('ثبت‌نام کاربر جدید', async () => {
  const r = await req('/api/v1/auth/register', {
    method: 'POST', body: { email: 'shopkeeper@test.af', password: 'strong-pass-123', name: 'دکاندار' },
  });
  assert.equal(r.status, 201);
  assert.ok(r.data.user.id);
  userId = r.data.user.id;
});

test('ثبت‌نام تکراری رد می‌شود', async () => {
  const r = await req('/api/v1/auth/register', {
    method: 'POST', body: { email: 'shopkeeper@test.af', password: 'strong-pass-123' },
  });
  assert.equal(r.status, 409);
});

test('رمز ضعیف رد می‌شود', async () => {
  const r = await req('/api/v1/auth/register', {
    method: 'POST', body: { email: 'weak@test.af', password: '12345678' },
  });
  assert.equal(r.status, 400);
  assert.equal(r.data.error.code, 'weak_password');
});

test('ورود با رمز اشتباه رد می‌شود', async () => {
  const r = await req('/api/v1/auth/login', {
    method: 'POST', body: { identifier: 'shopkeeper@test.af', password: 'wrong-password' },
  });
  assert.equal(r.status, 401);
  assert.equal(r.data.error.code, 'invalid_credentials');
});

test('ورود موفق توکن می‌دهد', async () => {
  const r = await req('/api/v1/auth/login', {
    method: 'POST', body: { identifier: 'shopkeeper@test.af', password: 'strong-pass-123' },
  });
  assert.equal(r.status, 200);
  assert.ok(r.data.accessToken);
  userToken = r.data.accessToken;
  refreshToken = r.data.refreshToken;
});

// ============ ۲) بدون اشتراک ============
test('فعال‌سازی بدون اشتراک رد می‌شود و License نمی‌دهد', async () => {
  const r = await req('/api/v1/license/activate', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-aaaaaaaa', name: 'تست' } },
  });
  assert.equal(r.status, 403);
  assert.equal(r.data.error.code, 'no_subscription');
  assert.ok(!r.data.license, 'نباید License صادر شود');
});

// ============ ۳) مدیر اشتراک می‌سازد ============
let adminToken, subId;

test('ساخت مدیر و ورود', async () => {
  const db = getDb();
  db.prepare(`INSERT INTO admins (id,username,name,password_hash,role,status,created_at)
              VALUES (?,?,?,?,?,'active',?)`)
    .run(newId('adm'), 'boss', 'مدیر', await pw.hashPassword('admin-pass-9876'), 'superadmin', now());

  const r = await req('/api/v1/admin/login', {
    method: 'POST', body: { username: 'boss', password: 'admin-pass-9876' },
  });
  assert.equal(r.status, 200);
  adminToken = r.data.token;
});

test('کاربر عادی نمی‌تواند به API مدیر دسترسی پیدا کند', async () => {
  const r = await req('/api/v1/admin/users', { token: userToken });
  assert.equal(r.status, 401);
});

test('مدیر اشتراک یک‌ساله با قابلیت‌های انتخابی می‌سازد', async () => {
  const r = await req(`/api/v1/admin/users/${userId}/subscription`, {
    method: 'POST', token: adminToken,
    body: {
      plan: 'حرفه‌ای', startDate: '2026-08-24', amount: 1, unit: 'year',
      timezone: 'Asia/Kabul', features: ['sales', 'warehouse', 'reports'],
      maxDevices: 2, graceDays: 0,
    },
  });
  assert.equal(r.status, 201);
  subId = r.data.subscription.id;

  // تاریخ‌ها دقیقاً مطابق مثال کاربر: شروع ۲۰۲۶-۰۸-۲۴ → پایان ۲۰۲۷-۰۸-۲۴
  const tz = 'Asia/Kabul';
  assert.equal(timeLib.formatInZone(r.data.subscription.starts_at, tz).slice(0, 10), '2026-08-24');
  assert.equal(timeLib.formatInZone(r.data.subscription.ends_at, tz).slice(0, 10), '2027-08-24');
  assert.deepEqual(r.data.subscription.features, ['sales', 'warehouse', 'reports']);
});

test('قابلیت ناشناخته و قابلیت پایه در اشتراک ذخیره نمی‌شوند', async () => {
  const sub = subsLib.getSubscriptionById(subId);
  const feats = subsLib.parseFeatures(sub.features);
  assert.ok(!feats.includes('dashboard'));
  const r = await req(`/api/v1/admin/subscriptions/${subId}`, {
    method: 'PATCH', token: adminToken,
    body: { features: ['sales', 'warehouse', 'reports', 'hack_everything', 'settings'] },
  });
  assert.deepEqual(r.data.subscription.features, ['sales', 'warehouse', 'reports']);
});

// ============ ۴) فعال‌سازی و License ============
let licenseToken, publicKey;

test('کلید عمومی در دسترس است و کلید خصوصی هرگز برنمی‌گردد', async () => {
  const r = await req('/api/v1/license/public-key');
  assert.equal(r.status, 200);
  assert.ok(r.data.publicKey);
  assert.equal(r.data.algorithm, 'ES256');
  assert.ok(!JSON.stringify(r.data).includes('PRIVATE'));
  publicKey = await ck.importPublicKey(ck.toPem(Buffer.from(r.data.publicKey, 'base64'), 'PUBLIC KEY'));
});

test('فعال‌سازی License امضاشده می‌دهد', async () => {
  const r = await req('/api/v1/license/activate', {
    method: 'POST', token: userToken,
    body: { device: { uid: 'device-uid-aaaaaaaa', name: 'گوشی دکان', fingerprint: 'fp-1' } },
  });
  assert.equal(r.status, 200);
  assert.ok(r.data.license);
  licenseToken = r.data.license;

  const v = await licenseLib.verifyLicense(publicKey, licenseToken);
  assert.ok(v.ok, 'امضا باید معتبر باشد');
  assert.equal(v.payload.uid, userId);
  assert.equal(v.payload.duid, 'device-uid-aaaaaaaa');
  assert.deepEqual(v.payload.feat, ['sales', 'warehouse', 'reports']);
  assert.deepEqual(v.payload.core, ['dashboard', 'products', 'settings']);
  assert.equal(v.payload.tz, 'Asia/Kabul');
  assert.equal(v.payload.iss, 'tohid-license-server');
});

test('دستکاری License امضا را می‌شکند', async () => {
  const [h, b, s] = licenseToken.split('.');
  const body = JSON.parse(Buffer.from(b, 'base64url').toString());
  body.exp = Date.now() + 100 * 365 * 86400000;      // تمدید ۱۰۰ ساله
  body.feat = ['sales', 'warehouse', 'reports', 'purchasing', 'backup', 'csv_export'];
  const forged = [h, Buffer.from(JSON.stringify(body)).toString('base64url'), s].join('.');
  const v = await licenseLib.verifyLicense(publicKey, forged);
  assert.equal(v.ok, false);
  assert.equal(v.reason, 'signature');
});

test('امضا با کلید دیگر پذیرفته نمی‌شود', async () => {
  const other = await ck.generateKeyPair();
  const otherPub = await ck.importPublicKey(other.publicKeyPem);
  const v = await licenseLib.verifyLicense(otherPub, licenseToken);
  assert.equal(v.ok, false);
});

// ============ ۵) جداسازی کاربران ============
test('کاربر نمی‌تواند به دستگاه کاربر دیگر دست بزند', async () => {
  const r2 = await req('/api/v1/auth/register', {
    method: 'POST', body: { email: 'other@test.af', password: 'another-pass-123' },
  });
  const login2 = await req('/api/v1/auth/login', {
    method: 'POST', body: { identifier: 'other@test.af', password: 'another-pass-123' },
  });
  const otherToken = login2.data.accessToken;

  const myDevice = getDb().prepare('SELECT * FROM devices WHERE user_id=?').get(userId);
  assert.ok(myDevice);

  // کاربر دوم می‌خواهد دستگاه کاربر اول را حذف کند
  const del = await req(`/api/v1/license/devices/${myDevice.id}`, { method: 'DELETE', token: otherToken });
  assert.equal(del.status, 404, 'نباید دستگاه کاربر دیگر را ببیند');

  // و دستگاه هنوز سر جایش است
  assert.ok(getDb().prepare('SELECT * FROM devices WHERE id=?').get(myDevice.id));

  // وضعیت کاربر دوم مستقل است
  const st = await req('/api/v1/license/status', { token: otherToken });
  assert.equal(st.data.subscription.state, 'none');
});

// ============ ۶) بررسی دوباره در سمت سرور ============
test('API محافظت‌شده با قابلیت فعال کار می‌کند', async () => {
  const r = await req('/api/v1/protected/reports', { token: userToken });
  assert.equal(r.status, 200);
});

test('API محافظت‌شده با قابلیت قفل رد می‌شود', async () => {
  const r = await req('/api/v1/protected/backup', { token: userToken });
  assert.equal(r.status, 403);
  assert.equal(r.data.error.code, 'feature_not_allowed');
});

// ============ ۷) سقف دستگاه ============
test('سقف دستگاه رعایت می‌شود', async () => {
  const ok = await req('/api/v1/license/activate', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-bbbbbbbb', name: 'تبلت' } },
  });
  assert.equal(ok.status, 200, 'دستگاه دوم مجاز است (سقف ۲)');

  const over = await req('/api/v1/license/activate', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-cccccccc', name: 'سومی' } },
  });
  assert.equal(over.status, 409);
  assert.equal(over.data.error.code, 'device_limit_reached');
});

// ============ ۸) لغو دستگاه ============
test('لغو دستگاه توسط مدیر، Sync آن دستگاه را می‌بندد', async () => {
  const dev = getDb().prepare('SELECT * FROM devices WHERE user_id=? AND device_uid=?')
    .get(userId, 'device-uid-bbbbbbbb');
  const rv = await req(`/api/v1/admin/devices/${dev.id}/revoke`, { method: 'POST', token: adminToken });
  assert.equal(rv.status, 200);

  const sync = await req('/api/v1/license/sync', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-bbbbbbbb' } },
  });
  assert.equal(sync.status, 403);
  assert.equal(sync.data.error.code, 'device_revoked');

  // License آن دستگاه هم باطل شده است
  const lic = getDb().prepare("SELECT * FROM licenses WHERE device_id=? ORDER BY issued_at DESC").get(dev.id);
  assert.equal(lic.status, 'revoked');
});

// ============ ۹) پایان اشتراک ============
test('پس از پایان اشتراک، قابلیت‌ها قفل می‌شوند ولی حساب باقی می‌ماند', async () => {
  // اشتراک را به گذشته می‌بریم
  getDb().prepare('UPDATE subscriptions SET starts_at=?, ends_at=? WHERE id=?')
    .run(Date.now() - 60 * 86400000, Date.now() - 86400000, subId);

  const st = await req('/api/v1/license/status', { token: userToken });
  assert.equal(st.data.subscription.state, 'expired');
  assert.deepEqual(st.data.subscription.features, [], 'هیچ قابلیتی نباید مجاز بماند');

  // Sync دیگر License نمی‌دهد
  const sync = await req('/api/v1/license/sync', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-aaaaaaaa' } },
  });
  assert.equal(sync.status, 403);
  assert.ok(!sync.data.license);

  // ولی کاربر هنوز می‌تواند وارد شود و حسابش هست
  const me = await req('/api/v1/auth/me', { token: userToken });
  assert.equal(me.status, 200);
  assert.equal(me.data.user.id, userId);

  // و API محافظت‌شده رد می‌شود
  const prot = await req('/api/v1/protected/reports', { token: userToken });
  assert.equal(prot.status, 403);
  assert.equal(prot.data.error.code, 'subscription_inactive');
});

// ============ ۱۰) تمدید ============
test('تمدید اشتراک منقضی، از امروز حساب می‌شود و دوباره License می‌دهد', async () => {
  const r = await req(`/api/v1/admin/subscriptions/${subId}/renew`, {
    method: 'POST', token: adminToken, body: { amount: 1, unit: 'month' },
  });
  assert.equal(r.status, 200);
  assert.equal(r.data.evaluated.state, 'active');
  assert.ok(r.data.subscription.ends_at > Date.now(), 'پایان باید در آینده باشد');

  const sync = await req('/api/v1/license/sync', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-aaaaaaaa' } },
  });
  assert.equal(sync.status, 200);
  const v = await licenseLib.verifyLicense(publicKey, sync.data.license);
  assert.ok(v.ok);
  assert.ok(v.payload.exp > Date.now());
});

// ============ ۱۱) تعلیق و لغو ============
test('تعلیق اشتراک دسترسی را می‌بندد و فعال‌سازی دوباره بازش می‌کند', async () => {
  await req(`/api/v1/admin/subscriptions/${subId}/status`, {
    method: 'POST', token: adminToken, body: { status: 'suspended' },
  });
  let st = await req('/api/v1/license/status', { token: userToken });
  assert.equal(st.data.subscription.state, 'suspended');
  assert.deepEqual(st.data.subscription.features, []);

  await req(`/api/v1/admin/subscriptions/${subId}/status`, {
    method: 'POST', token: adminToken, body: { status: 'active' },
  });
  st = await req('/api/v1/license/status', { token: userToken });
  assert.equal(st.data.subscription.state, 'active');
  assert.deepEqual(st.data.subscription.features, ['sales', 'warehouse', 'reports']);
});

// ============ ۱۲) TTL آفلاین ============
test('اعتبار آفلاین کوتاه، پایان License را جلو می‌آورد', async () => {
  await req(`/api/v1/admin/subscriptions/${subId}`, {
    method: 'PATCH', token: adminToken, body: { licenseTtlDays: 3, amount: 1, unit: 'year' },
  });
  const sync = await req('/api/v1/license/sync', {
    method: 'POST', token: userToken, body: { device: { uid: 'device-uid-aaaaaaaa' } },
  });
  const v = await licenseLib.verifyLicense(publicKey, sync.data.license);
  const days = (v.payload.exp - Date.now()) / 86400000;
  assert.ok(days > 2.9 && days < 3.1, `اعتبار باید ~۳ روز باشد، بود: ${days}`);
  assert.ok(v.payload.sub_ends > v.payload.exp, 'پایان اشتراک باید دورتر از پایان License باشد');
});

// ============ ۱۳) حساب غیرفعال ============
test('غیرفعال کردن کاربر همه توکن‌هایش را می‌بندد', async () => {
  await req(`/api/v1/admin/users/${userId}/status`, {
    method: 'POST', token: adminToken, body: { status: 'disabled' },
  });
  const r = await req('/api/v1/license/status', { token: userToken });
  assert.ok(r.status === 401 || r.status === 403);

  const login = await req('/api/v1/auth/login', {
    method: 'POST', body: { identifier: 'shopkeeper@test.af', password: 'strong-pass-123' },
  });
  assert.equal(login.status, 403);
  assert.equal(login.data.error.code, 'account_disabled');
});
