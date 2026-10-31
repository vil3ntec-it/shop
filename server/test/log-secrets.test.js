'use strict';
/**
 * AUTH-10 — «log فاقد رمز، OTP، token و secret باشد» (§۲۹.۸؛ §۲۹.۷ بندِ ۳).
 *
 * آزمونِ سراسری، نه یک خطِ خاص: سرورِ درون‌فرآیندی در حالتِ `production` (همان
 * شاخه‌های لاگِ سرورِ واقعی) همهٔ درهای ورود و نشست را با رمز، کد و توکنِ واقعی
 * می‌گذراند — درست و غلط، تازه و کهنه — و هر چه سرور با `console.*` چاپ کرد گرفته
 * می‌شود. هیچ‌کدام از این رازها (و رازهای محیط) نباید در آن باشد؛ نه در لاگ و نه در
 * `audit_logs` (سابقهٔ عملیات هم لاگ است).
 *
 * کدها از خودِ سرور گرفته می‌شوند (`otp.reveal`، `login-codes.unseal`) — همان راهِ
 * «نمایشِ کد» ِ میزِ مدیر — چون در production هیچ‌جا جز صندوقِ گیرنده نمی‌روند.
 * راهِ ارسالِ ایمیل `log` است (پیش‌فرضِ سرورِ تازه): بدترین حالت برای این آزمون.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const util = require('node:util');
const h = require('./helpers');
const config = require('../src/config');
const otp = require('../src/lib/otp');
const codes = require('../src/lib/login-codes');
const outbox = require('../src/lib/login-outbox');
const { one, many, query, newId, now } = require('../src/db');

const METHODS = ['log', 'info', 'warn', 'error', 'debug', 'trace'];
const lines = [];
const real = {};
const secrets = new Map();   // راز ⇒ برچسب

function remember(label, value) {
  const s = String(value == null ? '' : value);
  if (s.length >= 6) secrets.set(s, label);
}
function capture() {
  for (const m of METHODS) {
    real[m] = console[m];
    console[m] = (...a) => { lines.push(util.format(...a)); };
  }
}
function release() {
  for (const m of METHODS) if (real[m]) console[m] = real[m];
}

/** کدِ زندهٔ تازه‌ترین درخواستِ همین نشانی/هدف — همان «نمایشِ کد» ِ میزِ مدیر */
async function liveOtp(destination, purpose) {
  const row = await one(
    'SELECT id FROM otp_codes WHERE destination=$1 AND purpose=$2 ORDER BY created_at DESC LIMIT 1',
    [destination, purpose]
  );
  assert.ok(row, `کدی برای ${purpose} صادر نشد`);
  const out = await otp.reveal(row.id);
  assert.ok(out && out.code, `کدِ ${purpose} زنده نیست`);
  remember(`کدِ ${purpose}`, out.code);
  return out.code;
}

function keepSession(label, body) {
  for (const k of ['accessToken', 'refreshToken', 'token', 'ticket', 'access_token', 'refresh_token']) {
    if (body && typeof body[k] === 'string') remember(`${label}.${k}`, body[k]);
  }
}

let realEnv;
test.before(async () => {
  await h.start();
  outbox.stop();
  realEnv = config.env;
  for (const [k, v] of Object.entries(config.secrets)) remember(`secrets.${k}`, v);
  remember('OTP_SECRET', process.env.OTP_SECRET);
  remember('API_SECRET', process.env.API_SECRET);
});
test.after(async () => {
  release();
  config.env = realEnv;
  await h.stop();
});

test('AUTH-10: در production هیچ رمز، کد، توکن یا رازی در لاگِ سرور و سابقهٔ عملیات نمی‌نشیند', async () => {
  //  حساب‌های پایه پیش از production (ثبت‌نامِ سنجه‌ها devCode می‌خواهد)
  const u = await h.newUser('لاگ‌یک');
  const owner = await h.newUser('صاحبِ لاگ');
  remember('رمزِ حساب', u.password);
  keepSession('newUser', u);
  keepSession('owner', owner);
  assert.equal((await h.post('/api/shop', { name: 'دکانِ لاگ' }, { token: owner.accessToken })).status, 201);
  const staffCode = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  remember('کدِ شاگرد', staffCode);
  const pw = require('../src/lib/password');
  const adminPassword = 'Adm!nLogSecret77';
  remember('رمزِ مدیر', adminPassword);
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'log-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword(adminPassword), now()]
  );

  capture();
  config.env = 'production';
  try {
    //  ورود با رمز: درست، غلط، و ایمیلِ ناموجود
    const wrong = 'Wr0ng!Secret-pass';
    remember('رمزِ غلط', wrong);
    assert.equal((await h.post('/api/auth/login', { identifier: u.email, password: wrong })).status, 401);
    assert.equal((await h.post('/api/auth/login', { identifier: 'nobody@test.local', password: wrong })).status, 401);
    const login = await h.post('/api/auth/login', { identifier: u.email, password: u.password, device: { deviceId: 'log-dev-1', name: 'x', platform: 'test' } });
    assert.equal(login.status, 200, JSON.stringify(login.body));
    keepSession('login', login.body);

    //  توکن: درست، دست‌خورده، در querystring، و تازه‌سازی (درست و تکراری)
    assert.equal((await h.get('/api/me', { token: login.body.accessToken })).status, 200);
    assert.equal((await h.get('/api/me', { token: `${login.body.accessToken}x` })).status, 401);
    assert.equal((await h.api('GET', `/api/me?token=${login.body.accessToken}&refresh=${login.body.refreshToken}`)).status, 401);
    const ref = await h.post('/api/auth/refresh', { refreshToken: login.body.refreshToken });
    assert.equal(ref.status, 200, JSON.stringify(ref.body));
    keepSession('refresh', ref.body);
    assert.ok([401, 403].includes((await h.post('/api/auth/refresh', { refreshToken: 'bogus-refresh-token-Value123' })).status));
    remember('توکنِ ساختگی', 'bogus-refresh-token-Value123');

    //  ورود با کدِ ایمیل: کدِ غلط، کدِ درست
    assert.equal((await h.post('/api/auth/otp/request', { email: u.email })).status, 200);
    const loginCode = await liveOtp(u.email, 'login');
    const bad = loginCode === '111111' ? '222222' : '111111';
    assert.equal((await h.post('/api/auth/otp/verify', { email: u.email, code: bad })).status, 403);
    const viaCode = await h.post('/api/auth/otp/verify', { email: u.email, code: loginCode });
    assert.equal(viaCode.status, 200, JSON.stringify(viaCode.body));
    keepSession('otp', viaCode.body);

    //  تأییدِ دوباره با کد و با رمز؛ عوضِ رمز
    assert.equal((await h.post('/api/auth/reauth/code', {}, { token: viaCode.body.accessToken })).status, 200);
    const reauthCode = await liveOtp(u.email, 'reauth');
    assert.equal((await h.post('/api/auth/reauth', { code: reauthCode }, { token: viaCode.body.accessToken })).status, 200);
    assert.equal((await h.post('/api/auth/reauth', { password: u.password }, { token: viaCode.body.accessToken })).status, 200);
    const changed = 'Ch4nged!Secret-pw';
    remember('رمزِ تازه', changed);
    const chg = await h.post('/api/auth/password', { currentPassword: u.password, newPassword: changed }, { token: viaCode.body.accessToken });
    assert.equal(chg.status, 200, JSON.stringify(chg.body));

    //  رمزِ فراموش‌شده: کد و رمزِ تازه
    assert.equal((await h.post('/api/auth/password/forgot', { email: u.email })).status, 200);
    const resetCode = await liveOtp(u.email, 'reset');
    const reset = 'R3set!Secret-pw';
    remember('رمزِ بازیابی', reset);
    const rs = await h.post('/api/auth/password/reset', { email: u.email, code: resetCode, password: reset });
    assert.equal(rs.status, 200, JSON.stringify(rs.body));
    keepSession('reset', rs.body);

    //  ثبت‌نامِ سه‌پله‌ای در production
    const email = 'log-signup@test.local';
    assert.equal((await h.post('/api/auth/register/start', { name: 'تازه', email, password: 'Sign!Up-secret9' })).status, 201);
    remember('رمزِ ثبت‌نام', 'Sign!Up-secret9');
    const regCode = await liveOtp(email, 'register');
    const ver = await h.post('/api/auth/register/verify', { email, code: regCode });
    assert.equal(ver.status, 200, JSON.stringify(ver.body));
    keepSession('register', ver.body);
    const done = await h.post('/api/auth/register/complete', {
      ticket: ver.body.ticket, name: 'تازه', password: 'Sign!Up-secret9',
      device: { deviceId: 'log-dev-2', name: 'x', platform: 'test' }, terms: { accepted: true },
    });
    assert.equal(done.status, 201, JSON.stringify(done.body));
    keepSession('register.complete', done.body);

    //  ورودِ کدِ ایمیلیِ برنامه (`/api/auth/:app`، صفِ ایمیل) — راهِ ارسال `log`
    const rq = await h.post('/api/auth/shop/request-code', { email: owner.email, device_id: 'log-dev-3' });
    assert.equal(rq.status, 200, JSON.stringify(rq.body));
    await outbox.processById(rq.body.request_id);
    const rec = await codes.requestById(rq.body.request_id);
    const appCode = codes.unseal(rec.code_sealed);
    assert.match(appCode, /^\d{6}$/);
    remember('کدِ ورودِ برنامه', appCode);
    const appIn = await h.post('/api/auth/shop/verify', { request_id: rq.body.request_id, code: appCode, device_id: 'log-dev-3' });
    assert.equal(appIn.status, 200, JSON.stringify(appIn.body));
    keepSession('app-auth', appIn.body);

    //  کدِ شاگرد
    const staff = await h.post('/api/auth/staff-login', { code: staffCode, name: 'شاگردِ لاگ', device: { uid: 'log-staff-dev', name: 'x', platform: 'android' } });
    assert.equal(staff.status, 201, JSON.stringify(staff.body));
    keepSession('staff', staff.body);

    //  مدیر: غلط و درست
    assert.equal((await h.post('/api/admin/login', { username: 'log-admin', password: wrong })).status, 401);
    const adm = await h.post('/api/admin/login', { username: 'log-admin', password: adminPassword });
    assert.equal(adm.status, 200, JSON.stringify(adm.body));
    keepSession('admin', adm.body);

    //  بدنهٔ خراب با رمز درونش، و توکنِ ساختگیِ گوگل
    const broken = await h.raw('POST', '/api/auth/login', Buffer.from(`{"identifier":"${u.email}","password":"${reset}"`), { headers: { 'Content-Type': 'application/json' } });
    assert.equal(broken.status, 400);
    const fakeGoogle = 'eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJsb2ctdGVzdCJ9.c2lnbmF0dXJlLXNlY3JldA';
    remember('توکنِ گوگل', fakeGoogle);
    assert.ok((await h.post('/api/auth/google', { idToken: fakeGoogle })).status >= 400);

    //  خروج و خروج از همه‌جا
    assert.equal((await h.post('/api/auth/logout', { refreshToken: rs.body.refreshToken }, { token: rs.body.accessToken })).status, 200);
    assert.equal((await h.post('/api/auth/logout-all', {}, { token: done.body.accessToken })).status, 200);
  } finally {
    config.env = realEnv;
    release();
  }

  //  آزمون بی‌اثر نبود: سرور واقعاً لاگ نوشت (درخواست‌های ۴xx و هشدارِ «راهِ ارسال»)
  const text = lines.join('\n');
  assert.ok(lines.length >= 5, `فقط ${lines.length} خطِ لاگ — گرفتنِ لاگ کار نکرد؟`);
  assert.match(text, /POST \/api\/auth\/login 401/);
  assert.ok(secrets.size >= 20, `فقط ${secrets.size} راز جمع شد`);

  const leaks = [];
  const audit = (await many('SELECT action, detail::text AS d FROM audit_logs')).map((r) => `${r.action} ${r.d}`).join('\n');
  for (const [s, label] of secrets) {
    const re = new RegExp(`(?<![0-9A-Za-z])${s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![0-9A-Za-z])`);
    if (re.test(text)) leaks.push(`لاگ: ${label}`);
    if (re.test(audit)) leaks.push(`audit_logs: ${label}`);
    //  تکه‌ای از توکن هم راز است
    if (s.length >= 32 && text.includes(s.slice(0, 16))) leaks.push(`لاگ (۱۶ نویسهٔ اول): ${label}`);
  }
  assert.deepEqual(leaks, [], `${leaks.join('\n')}\n--- لاگ ---\n${text.slice(0, 4000)}`);
});

test('راهِ «log» ِ ایمیل: در production فقط نشانیِ پوشیده و موضوع؛ بیرون از آن همان متنِ کامل (سرورِ توسعه)', async () => {
  const mailer = require('../src/lib/mailer');
  const mail = { to: 'reader@example.com', subject: 'کد ورود', text: 'کد شما: 583920' };
  const out = [];
  const realLog = console.log;
  console.log = (...a) => out.push(util.format(...a));
  try {
    config.env = 'production';
    assert.deepEqual(await mailer.send(mail), { delivered: true, via: 'log' });
    config.env = realEnv;
    await mailer.send(mail);
  } finally {
    config.env = realEnv;
    console.log = realLog;
  }
  assert.equal(out.length, 2, out.join('\n'));
  assert.ok(!out[0].includes('583920'), out[0]);
  assert.ok(!out[0].includes('reader@example.com'), out[0]);
  assert.match(out[0], /r\*\*\*@example\.com — کد ورود/);
  assert.ok(out[1].includes('583920') && out[1].includes('reader@example.com'), out[1]);
});
