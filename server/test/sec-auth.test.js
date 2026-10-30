'use strict';
/**
 * SEC-AUTH (§۷ و §۱۳ِ برنامهٔ امنیت) — سخت‌کردنِ ورود، رمز و نشست.
 * شرحِ هر یافته: `docs/security/auth-fa.md`.
 *
 *   SEC-AUTH-01  هزینهٔ scrypt به کفِ OWASP (N=2^14, r=8, p=5)؛ هشِ کهنه پس از ورودِ درست بازنویسی می‌شود
 *   SEC-AUTH-03  قفلِ ورودِ مدیر «نام + IP» است، نه سراسری: حدس‌زننده صاحبِ سامانه را بیرون نمی‌کند
 *   SEC-AUTH-04  نامِ مدیرِ ناموجود همان scrypt را می‌سوزاند (شمارش‌پذیریِ نام با زمان)
 *   SEC-AUTH-05  `X-Forwarded-For` فقط از پروکسیِ مطمئن؛ سرآیندِ ساختگی سقفِ نرخ را دور نمی‌زند
 *   SEC-AUTH-06  کدِ یک‌بارمصرف فقط با `NODE_ENV`ِ صریحِ development/test در پاسخ می‌آید
 *   SEC-AUTH-07  توکنِ گوگلِ بی `exp`/`iat` پذیرفته نمی‌شود (سنجاقِ RS256، iss، aud سرِ جایش)
 *   SEC-AUTH-09  عوض کردنِ نقش/وضعیتِ عضوِ پمپ ردیفِ سابقه دارد
 */
//  این پرونده هزینهٔ **واقعیِ** production را می‌سنجد (helpers پیش‌فرضِ تندِ آزمون را ۱ می‌گذارد)
process.env.PASSWORD_SCRYPT_P = '5';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { execFileSync } = require('node:child_process');
const { generateKeyPairSync, createSign } = require('node:crypto');
const h = require('./helpers');
const config = require('../src/config');
const pw = require('../src/lib/password');
const { query, one, many, newId, now } = require('../src/db');
const { clientIp, rateLimit, resetAll } = require('../src/middleware/ratelimit');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

/** هشی با p ِ دلخواه — همان قالبِ ذخیره، برای ساختنِ «هشِ کهنه». */
async function hashWithP(plain, p) {
  const keep = process.env.PASSWORD_SCRYPT_P;
  process.env.PASSWORD_SCRYPT_P = String(p);
  try { return await pw.hashPassword(plain); } finally { process.env.PASSWORD_SCRYPT_P = keep; }
}
const pOf = (stored) => Number(String(stored).split('$')[3]);

/* ── SEC-AUTH-01 ─────────────────────────────────────────────────── */

test('SEC-AUTH-01: هشِ تازه N=16384، r=8، p=5؛ در production محیط آن را پایین نمی‌آورد', async () => {
  const stored = await pw.hashPassword('Str0ng!pass');
  assert.match(stored, /^scrypt\$16384\$8\$5\$/);
  assert.equal(await pw.verifyPassword('Str0ng!pass', stored), true);
  assert.equal(await pw.verifyPassword('Str0ng!pasS', stored), false);
  assert.equal(pw.needsRehash(stored), false);
  assert.equal(pw.needsRehash(await hashWithP('Str0ng!pass', 1)), true);
  assert.equal(pw.needsRehash('bcrypt$whatever'), false, 'قالبِ ناشناخته دست نمی‌خورد');
  //  production (و بی NODE_ENV): کمتر از پیش‌فرض پذیرفته نمی‌شود
  const out = execFileSync(process.execPath, ['-e',
    "process.env.PASSWORD_SCRYPT_P='1'; console.log(require('./src/lib/password').currentP())"],
  { cwd: require('node:path').join(__dirname, '..'), env: { PATH: process.env.PATH, NODE_ENV: 'production' } }).toString().trim();
  assert.equal(out, '5');
  const bare = execFileSync(process.execPath, ['-e',
    "process.env.PASSWORD_SCRYPT_P='1'; console.log(require('./src/lib/password').currentP())"],
  { cwd: require('node:path').join(__dirname, '..'), env: { PATH: process.env.PATH } }).toString().trim();
  assert.equal(bare, '5', 'بی NODE_ENV هم production حساب می‌شود');
});

test('SEC-AUTH-01: کاربر و مدیرِ هشِ کهنه پس از ورودِ درست به هزینهٔ امروز می‌رسند؛ رمزِ غلط هش را عوض نمی‌کند', async () => {
  const u = await h.newUser('هشِ کهنه');
  const old = await hashWithP(u.password, 1);
  await query('UPDATE users SET password_hash=$2 WHERE id=$1', [u.user.id, old]);
  const bad = await h.post('/api/auth/login', { identifier: u.email, password: 'Wrong!pass1' });
  assert.equal(bad.status, 401);
  assert.equal((await one('SELECT password_hash FROM users WHERE id=$1', [u.user.id])).password_hash, old);
  const ok = await h.post('/api/auth/login', { identifier: u.email, password: u.password });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  const now1 = (await one('SELECT password_hash FROM users WHERE id=$1', [u.user.id])).password_hash;
  assert.equal(pOf(now1), 5);
  assert.equal(await pw.verifyPassword(u.password, now1), true);
  assert.equal((await h.post('/api/auth/login', { identifier: u.email, password: u.password })).status, 200);

  const adm = newId('adm');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'kohne','کهنه',$2,'admin','active',$3)`, [adm, await hashWithP('Admin!12345', 1), now()]);
  assert.equal((await h.post('/api/admin/login', { username: 'kohne', password: 'Admin!12345' })).status, 200);
  assert.equal(pOf((await one('SELECT password_hash FROM admins WHERE id=$1', [adm])).password_hash), 5);
});

/* ── SEC-AUTH-03 / 04 ────────────────────────────────────────────── */

async function adminRow(username, password = 'Owner!12345') {
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,$2,'صاحب',$3,'superadmin','active',$4)`, [newId('adm'), username, await pw.hashPassword(password), now()]);
}
const adminLogin = (username, password, ip) => h.post('/api/admin/login', { username, password }, { headers: { 'X-Forwarded-For': ip } });

test('SEC-AUTH-03: رمزهای غلطِ یک IP فقط همان IP را می‌بندد؛ صاحب از جای دیگر وارد می‌شود؛ حملهٔ پخش‌شده سقفِ سراسری دارد', async (t) => {
  const keep = { trust: config.trustProxy, tries: config.rateLimit.lockoutTries, all: config.rateLimit.lockoutGlobalTries };
  t.after(() => { config.trustProxy = keep.trust; config.rateLimit.lockoutTries = keep.tries; config.rateLimit.lockoutGlobalTries = keep.all; });
  config.trustProxy = true;
  config.rateLimit.lockoutTries = 3;
  config.rateLimit.lockoutGlobalTries = 6;
  await adminRow('sahib');
  for (let i = 0; i < 3; i++) assert.equal((await adminLogin('sahib', 'guess-' + i, '203.0.113.7')).status, 401);
  const locked = await adminLogin('sahib', 'guess-x', '203.0.113.7');
  assert.equal(locked.status, 429);
  assert.equal(locked.body.error.code, 'locked_out');
  //  ⛔ پیش از اصلاح: ۴۲۹ — صاحبِ سامانه با رمزِ درست بیرون می‌ماند
  const owner = await adminLogin('sahib', 'Owner!12345', '198.51.100.4');
  assert.equal(owner.status, 200, JSON.stringify(owner.body));
  //  پخش‌شده روی چند IP: پس از سقفِ سراسری همه بسته‌اند (پشتوانه، نه ابزارِ آزار از یک IP)
  for (const ip of ['192.0.2.1', '192.0.2.2', '192.0.2.3']) assert.equal((await adminLogin('sahib', 'nope', ip)).status, 401);
  assert.equal((await adminLogin('sahib', 'Owner!12345', '198.51.100.4')).status, 429);
});

test('SEC-AUTH-04: نامِ مدیرِ ناموجود و مدیرِ غیرفعال همان scrypt را می‌سوزانند', async (t) => {
  const orig = pw.burnTime;
  let burned = 0;
  pw.burnTime = async (...a) => { burned += 1; return orig(...a); };
  t.after(() => { pw.burnTime = orig; });
  assert.equal((await h.post('/api/admin/login', { username: 'no-such-admin', password: 'Whatever!123' })).status, 401);
  assert.equal(burned, 1, 'نامِ ناموجود');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'khamosh','خاموش',$2,'admin','disabled',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  assert.equal((await h.post('/api/admin/login', { username: 'khamosh', password: 'Admin!12345' })).status, 401);
  assert.equal(burned, 2, 'مدیرِ غیرفعال');
});

/* ── SEC-AUTH-05 ─────────────────────────────────────────────────── */

const fakeReq = (peer, xff) => ({ socket: { remoteAddress: peer }, headers: xff ? { 'x-forwarded-for': xff } : {} });

test('SEC-AUTH-05: X-Forwarded-For فقط از همسایهٔ مطمئن، و فقط راست‌ترین بخش', (t) => {
  const keep = { trust: config.trustProxy, from: config.trustProxyFrom };
  t.after(() => { config.trustProxy = keep.trust; config.trustProxyFrom = keep.from; });
  config.trustProxy = false;
  assert.equal(clientIp(fakeReq('127.0.0.1', '9.9.9.9')), '127.0.0.1', 'بی TRUST_PROXY سرآیند خوانده نمی‌شود');
  config.trustProxy = true;
  config.trustProxyFrom = [];
  //  ⛔ پیش از اصلاح: '1.2.3.4' — هر کسی که مستقیم به سرور برسد IPِ دلخواه می‌شد
  assert.equal(clientIp(fakeReq('203.0.113.50', '1.2.3.4')), '203.0.113.50');
  assert.equal(clientIp(fakeReq('::ffff:203.0.113.50', '1.2.3.4')), '::ffff:203.0.113.50');
  assert.equal(clientIp(fakeReq('2001:db8::5', '1.2.3.4')), '2001:db8::5');
  //  پروکسیِ مطمئن (پنلِ خانگی روی loopback، Caddy در شبکهٔ docker) ⇒ راست‌ترین، نه چپ‌ترینِ ساختگی
  assert.equal(clientIp(fakeReq('127.0.0.1', '6.6.6.6, 198.51.100.7')), '198.51.100.7');
  assert.equal(clientIp(fakeReq('::1', '198.51.100.8')), '198.51.100.8');
  assert.equal(clientIp(fakeReq('172.18.0.3', '198.51.100.9')), '198.51.100.9');
  //  `TRUST_PROXY_FROM` فهرست را تنگ می‌کند: فقط همان‌ها
  config.trustProxyFrom = ['10.9.0.0/16'];
  assert.equal(clientIp(fakeReq('127.0.0.1', '198.51.100.7')), '127.0.0.1');
  assert.equal(clientIp(fakeReq('10.9.4.4', '198.51.100.7')), '198.51.100.7');
});

test('SEC-AUTH-05: سرآیندِ ساختگیِ تازه در هر درخواست سقفِ نرخ را دور نمی‌زند', (t) => {
  const keep = { trust: config.trustProxy, from: config.trustProxyFrom };
  t.after(() => { config.trustProxy = keep.trust; config.trustProxyFrom = keep.from; });
  config.trustProxy = true;
  config.trustProxyFrom = [];
  const limit = rateLimit({ max: 3, windowMs: 60_000, keyPrefix: 'sec-auth-05' });
  const hit = (req) => {
    let err = null;
    limit(req, { set() {} }, (e) => { err = e || null; });
    return err ? err.status : 200;
  };
  //  مستقیم از اینترنت، هر بار یک XFFِ تازه
  const direct = [1, 2, 3, 4, 5].map((i) => hit(fakeReq('203.0.113.66', `10.0.0.${i}`)));
  assert.deepEqual(direct, [200, 200, 200, 429, 429]);
  //  پشتِ پروکسیِ مطمئن، چپ‌ترینِ ساختگیِ تازه + IPِ واقعیِ افزوده‌شده
  const proxied = [1, 2, 3, 4].map((i) => hit(fakeReq('127.0.0.1', `66.6.6.${i}, 198.51.100.77`)));
  assert.deepEqual(proxied, [200, 200, 200, 429]);
});

/* ── SEC-AUTH-06 ─────────────────────────────────────────────────── */

test('SEC-AUTH-06: سرورِ بی NODE_ENV کد را نه در پاسخ می‌دهد نه در لاگ؛ فقط development/test', async (t) => {
  const bare = execFileSync(process.execPath, ['-e',
    "const c=require('./src/config'); console.log(JSON.stringify({env:c.env,dev:c.devCodes}))"],
  { cwd: require('node:path').join(__dirname, '..'), env: { PATH: process.env.PATH } }).toString().trim();
  assert.deepEqual(JSON.parse(bare.split('\n').pop()), { env: 'development', dev: false });

  //  همان سرور در آزمون (NODE_ENV=test) کد را پس می‌دهد — پس آزمون بی‌اثر نیست
  const shown = await h.post('/api/auth/otp/request', { phone: '0790000601' });
  assert.equal(shown.status, 200, JSON.stringify(shown.body));
  assert.match(String(shown.body.devCode || ''), /^\d{6}$/);

  const keep = config.devCodes;
  const logs = [];
  const orig = console.log;
  console.log = (...a) => { logs.push(a.join(' ')); };
  t.after(() => { config.devCodes = keep; console.log = orig; });
  config.devCodes = false;   //  = NODE_ENV نیامده
  const hidden = await h.post('/api/auth/otp/request', { phone: '0790000602' });
  console.log = orig;
  assert.equal(hidden.status, 200, JSON.stringify(hidden.body));
  //  ⛔ پیش از اصلاح: devCode در پاسخ — ورود با شمارهٔ هر کسی
  assert.equal(hidden.body.devCode, undefined);
  assert.ok(!logs.some((l) => /\d{6}/.test(l) && l.includes('0790000602')), logs.join('\n'));
});

/* ── SEC-AUTH-07 ─────────────────────────────────────────────────── */

test('SEC-AUTH-07: توکنِ گوگل بی exp یا iat، با الگوریتمِ دیگر، صادرکنندهٔ دیگر یا مخاطبِ دیگر رد می‌شود', async (t) => {
  const google = require('../src/lib/google');
  const keys = generateKeyPairSync('rsa', { modulusLength: 2048 });
  const jwk = { ...keys.publicKey.export({ format: 'jwk' }), kid: 'k-sec-auth', alg: 'RS256', use: 'sig' };
  const srv = http.createServer((req, res) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify({ keys: [jwk] })); });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  const keep = { ids: config.google.clientIds, url: config.google.certsUrl };
  t.after(() => { config.google.clientIds = keep.ids; config.google.certsUrl = keep.url; srv.close(); });
  config.google.clientIds = ['sec-auth.apps.googleusercontent.com'];
  config.google.certsUrl = `http://127.0.0.1:${srv.address().port}/certs`;
  const sec = Math.floor(Date.now() / 1000);
  const base = { iss: 'https://accounts.google.com', aud: 'sec-auth.apps.googleusercontent.com', sub: 'g-1', email: 'g1@test.local', email_verified: true, iat: sec, exp: sec + 600 };
  const b64 = (o) => Buffer.from(JSON.stringify(o)).toString('base64url');
  const make = (payload, head = { alg: 'RS256', kid: 'k-sec-auth', typ: 'JWT' }) => {
    const s = createSign('RSA-SHA256');
    s.update(`${b64(head)}.${b64(payload)}`);
    return `${b64(head)}.${b64(payload)}.${s.sign(keys.privateKey).toString('base64url')}`;
  };
  const code = async (tok) => { try { await google.verifyIdToken(tok); return 'ok'; } catch (e) { return e.code || e.message; } };

  assert.equal(await code(make(base)), 'ok');
  const { exp, ...noExp } = base;
  const { iat, ...noIat } = base;
  assert.ok(exp && iat);
  //  ⛔ پیش از اصلاح: 'ok' — NaN از «منقضی؟» و «آینده؟» هر دو رد می‌شد
  assert.equal(await code(make(noExp)), 'bad_google_token');
  assert.equal(await code(make(noIat)), 'bad_google_token');
  assert.equal(await code(make({ ...base, exp: 'فردا' })), 'bad_google_token');
  assert.equal(await code(make({ ...base, exp: sec - 3600, iat: sec - 7200 })), 'google_token_expired');
  assert.equal(await code(make({ ...base, iss: 'https://evil.example' })), 'bad_google_token');
  assert.equal(await code(make({ ...base, aud: 'other.apps.googleusercontent.com' })), 'bad_audience');
  assert.equal(await code(make(base, { alg: 'HS256', kid: 'k-sec-auth' })), 'bad_google_token');
  assert.equal(await code(`${b64({ alg: 'none', kid: 'k-sec-auth' })}.${b64(base)}.`), 'bad_google_token');
});

/* ── SEC-AUTH-09 ─────────────────────────────────────────────────── */

test('SEC-AUTH-09: نقش و وضعیتِ عضوِ پمپ و تنظیمِ پمپ ردیفِ سابقه دارند (کنشگر، زمانِ سرور، هدف)', async () => {
  const o = await h.newUser('صاحبِ پمپِ سابقه', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ سابقه' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const stationId = made.body.station.id;
  const s = await h.newUser('کارمندِ پمپ', 'pump');
  const mem = newId('mem');
  await query(`INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [mem, stationId, s.user.id, now()]);
  const before = now();
  const r = await h.patch(`/api/pump/members/${mem}`, { role: 'manager' }, { token: o.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await many(`SELECT * FROM audit_logs WHERE action='pump.member_updated' AND target_id=$1`, [mem]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].user_id, o.user.id);
  assert.ok(Number(rows[0].created_at) >= before);
  const detail = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.deepEqual({ role: detail.role, status: detail.status, stationId: detail.stationId }, { role: 'manager', status: 'active', stationId });
  assert.equal((await h.patch('/api/pump', { name: 'پمپِ تازه' }, { token: o.accessToken })).status, 200);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='pump.station_updated' AND target_id=$1`, [stationId])).length, 1);
});

/* ── SEC-AUTH-12: تأییدِ دومرحله‌ایِ اجباری پشتِ پرچم ─────────────── */

const totp = require('../src/lib/totp');
/** کدی که از آخرین گامِ پذیرفتهٔ مدیر جلوتر است (یک گام بعد، در پنجرهٔ ±۱). */
const codeAfter = async (adminId, secret) => {
  const last = Number((await one('SELECT totp_last_step FROM admins WHERE id=$1', [adminId])).totp_last_step || 0);
  const at = Math.max(Date.now(), (last + 1) * totp.STEP_SEC * 1000);
  return totp.generate(secret, at);
};
async function newAdmin(username, role = 'superadmin', password = 'Admin!12345') {
  const id = newId('adm');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,$2,$2,$3,$4,'active',$5)`, [id, username, await pw.hashPassword(password), role, now()]);
  return id;
}
async function enroll(token, adminId, password = 'Admin!12345') {
  const s = await h.post('/api/admin/2fa/setup', { password }, { token });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const on = await h.post('/api/admin/2fa/enable', { code: await codeAfter(adminId, s.body.secret) }, { token });
  assert.equal(on.status, 200, JSON.stringify(on.body));
  return { secret: s.body.secret, recovery: on.body.recovery_codes };
}

test('SEC-AUTH-12: پرچمِ خاموش ⇒ همان رفتارِ پیشین؛ روشن ⇒ مدیرِ بی TOTP فقط به «روشن کردن» می‌رسد، حتی با نشستِ کهنه', async (t) => {
  t.after(() => { config.admin.twoFactorRequired = false; });
  const id = await newAdmin('bitotp');
  const login = await h.post('/api/admin/login', { username: 'bitotp', password: 'Admin!12345' });
  assert.equal(login.status, 200, JSON.stringify(login.body));
  assert.deepEqual(login.body.twoFactor, { enabled: false, required: false });
  const token = login.body.token;
  assert.equal((await h.get('/api/admin/stats', { token })).status, 200, 'پیش‌فرض: اجباری نیست');

  config.admin.twoFactorRequired = true;
  //  ⛔ پیش از اصلاح: ۲۰۰ — «اجباری» راهی برای اجرا نداشت
  const blocked = await h.get('/api/admin/stats', { token });
  assert.equal(blocked.status, 403);
  assert.equal(blocked.body.error.code, 'admin_2fa_enrollment_required');
  for (const [m, p] of [['GET', '/api/admin/users'], ['POST', '/api/admin/admins'], ['PATCH', '/api/admin/config'], ['POST', '/api/admin/reauth'], ['GET', '/api/admin/notices']]) {
    const r = await h.api(m, p, { token, body: m === 'GET' ? null : {} });
    assert.equal(r.status, 403, `${m} ${p} ⇒ ${r.status}`);
  }
  assert.equal((await h.get('/api/admin/me', { token })).status, 200);
  const st = await h.get('/api/admin/2fa', { token });
  assert.equal(st.status, 200);
  assert.equal(st.body.enforced, true);
  const fresh = await h.post('/api/admin/login', { username: 'bitotp', password: 'Admin!12345' });
  assert.deepEqual(fresh.body.twoFactor, { enabled: false, required: true });

  const { secret } = await enroll(token, id);
  assert.equal((await h.get('/api/admin/stats', { token })).status, 200, 'پس از روشن کردن، همان نشست باز است');
  const noCode = await h.post('/api/admin/login', { username: 'bitotp', password: 'Admin!12345' });
  assert.equal(noCode.status, 401);
  assert.equal(noCode.body.error.code, 'totp_required');
  const ok = await h.post('/api/admin/login', { username: 'bitotp', password: 'Admin!12345', totp: await codeAfter(id, secret) });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.deepEqual(ok.body.twoFactor, { enabled: true, required: true });
});

test('SEC-AUTH-12: صاحبِ سامانه با گوشیِ گم‌شده قفل نمی‌شود — راهِ نجاتِ محیط TOTP را خاموش و «روشن کردن» را باز می‌کند', async (t) => {
  t.after(() => { config.admin.twoFactorRequired = false; });
  config.admin.twoFactorRequired = true;
  const id = await newAdmin('sahibe-gom', 'superadmin', 'Owner!12345');
  const first = await h.post('/api/admin/login', { username: 'sahibe-gom', password: 'Owner!12345' });
  await enroll(first.body.token, id, 'Owner!12345');
  //  گوشی و کدهای بازیابی گم شد: بی کد راهی نیست…
  assert.equal((await h.post('/api/admin/login', { username: 'sahibe-gom', password: 'Owner!12345' })).status, 401);
  //  …تا صاحبِ سامانه روی خودِ سرور رمزِ تازهٔ محیط را بگذارد
  const { ensureAdmin } = require('../src/lib/admin-bootstrap');
  const out = await ensureAdmin({ username: 'sahibe-gom', password: 'Rescue!98765' });
  assert.equal(out.updated, true);
  const back = await h.post('/api/admin/login', { username: 'sahibe-gom', password: 'Rescue!98765' });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.deepEqual(back.body.twoFactor, { enabled: false, required: true });
  assert.equal((await h.get('/api/admin/stats', { token: back.body.token })).status, 403);
  await enroll(back.body.token, id, 'Rescue!98765');
  assert.equal((await h.get('/api/admin/stats', { token: back.body.token })).status, 200);
});

/* ── SEC-AUTH-13: تأییدِ دوباره برای نوشتنِ مدیرِ ارشد پشتِ پرچم ─────── */

const ageSession = (token) => query(
  `UPDATE login_attempts SET created_at = created_at - 86400000 WHERE scope='session_auth' AND identifier=$1`,
  [require('../src/lib/tokens').hashToken(token)]
);

test('SEC-AUTH-13: نشستِ کهنهٔ مدیرِ ارشد بی رمزِ دوباره مدیر نمی‌سازد؛ خواندن باز است؛ /reauth ۴۰۳ می‌دهد نه ۴۰۱', async (t) => {
  t.after(() => { config.admin.reauthRequired = false; });
  await newAdmin('arshad-13');
  const token = (await h.post('/api/admin/login', { username: 'arshad-13', password: 'Admin!12345' })).body.token;
  await ageSession(token);
  //  پرچم خاموش: همان رفتارِ پیشین
  assert.equal((await h.post('/api/admin/admins', { username: 'clerk-a', password: 'Clerk!12345' }, { token })).status, 201);

  config.admin.reauthRequired = true;
  //  ⛔ پیش از اصلاح: ۲۰۱ — توکنِ ۱۲ساعتهٔ دزدیده‌شده مدیرِ ارشدِ تازه می‌ساخت
  const stale = await h.post('/api/admin/admins', { username: 'clerk-b', password: 'Clerk!12345', role: 'superadmin' }, { token });
  assert.equal(stale.status, 403);
  assert.equal(stale.body.error.code, 'admin_reauth_required');
  assert.equal((await one(`SELECT COUNT(*)::int n FROM admins WHERE username='clerk-b'`)).n, 0);
  assert.equal((await h.patch('/api/admin/config', { trial_days: 30 }, { token })).status, 403);
  assert.equal((await h.get('/api/admin/admins', { token })).status, 200, 'خواندن تأییدِ دوباره نمی‌خواهد');

  const wrong = await h.post('/api/admin/reauth', { password: 'nope-nope' }, { token });
  assert.equal(wrong.status, 403);
  assert.equal(wrong.body.error.code, 'bad_password');
  const ok = await h.post('/api/admin/reauth', { password: 'Admin!12345' }, { token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.freshUntil > Date.now());
  assert.equal((await h.post('/api/admin/admins', { username: 'clerk-b', password: 'Clerk!12345' }, { token })).status, 201);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='admin.reauth'`)).length, 1);
  //  ورودِ تازه خودش «تازه» است
  const again = (await h.post('/api/admin/login', { username: 'arshad-13', password: 'Admin!12345' })).body.token;
  assert.equal((await h.post('/api/admin/admins', { username: 'clerk-c', password: 'Clerk!12345' }, { token: again })).status, 201);
});

test('SEC-AUTH-13: مدیرِ با TOTP برای تأییدِ دوباره کد هم می‌خواهد؛ رمزِ تنها کافی نیست', async (t) => {
  t.after(() => { config.admin.reauthRequired = false; });
  const id = await newAdmin('arshad-totp');
  const first = (await h.post('/api/admin/login', { username: 'arshad-totp', password: 'Admin!12345' })).body.token;
  const { secret } = await enroll(first, id);
  config.admin.reauthRequired = true;
  await ageSession(first);
  const only = await h.post('/api/admin/reauth', { password: 'Admin!12345' }, { token: first });
  assert.equal(only.status, 403);
  assert.equal(only.body.error.code, 'totp_required');
  assert.equal((await h.post('/api/admin/admins', { username: 'clerk-d', password: 'Clerk!12345' }, { token: first })).status, 403);
  const ok = await h.post('/api/admin/reauth', { password: 'Admin!12345', totp: await codeAfter(id, secret) }, { token: first });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal((await h.post('/api/admin/admins', { username: 'clerk-d', password: 'Clerk!12345' }, { token: first })).status, 201);
});
