'use strict';
/**
 * «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): «برنامه را فقط با ایمیل قبول می‌کنم؛ شماره و OTP ِ پیامکی اصلاً» و
 * «شماره توی برنامه بشه ثبت کرد ولی برای لاگین نه اصلن».
 *
 *   • ورود با رمز و ورود/ثبت‌نام با کد فقط با ایمیل؛ شماره ⇒ ۴۰۰ ِ `email_required`، بی ردیفِ کد و بی هیچ ارسالی.
 *   • `lib/otp` کد را فقط به ایمیل می‌فرستد (`email_only`) — هیچ راهِ پیامکی برای کد نمانده.
 *   • تأییدِ دوباره (`/reauth/code`) فقط به ایمیلِ حساب.
 *   • شمارهٔ حساب فیلدِ داده است: افزودن/تغییر/پاک بی کد و بی یکتایی؛ درِ کدِ شماره (AUTH-08 ِ دیروز) رفت.
 *   • AUTH-08 به ایمیل رفت: حسابِ بی‌ایمیل ایمیل را فقط با کدِ همان ایمیل و نشستِ تازه می‌افزاید.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, many, query, newId, now } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const codes = (dest) => many('SELECT * FROM otp_codes WHERE destination = $1', [dest]);

/** حسابِ کهنهٔ فقط‌شماره (پیش از این تصمیم با کدِ پیامکی ساخته می‌شد) با یک نشستِ باز */
async function legacyPhoneUser(phone, { password = null } = {}) {
  const id = newId('usr');
  const pw = password ? await require('../src/lib/password').hashPassword(password) : null;
  await query(`INSERT INTO users (id, name, phone, password_hash, status, created_at, updated_at)
               VALUES ($1,'قدیمی',$2,$3,'active',$4,$4)`, [id, phone, pw, now()]);
  const tokens = require('../src/lib/tokens');
  const access = await tokens.issue({ kind: 'access', subjectId: id, ttlMs: 3600_000, app: 'shop' });
  return { id, accessToken: access.token };
}

test('ورود با کد: شماره ⇒ ۴۰۰ email_required، بی ردیفِ کد؛ ایمیل همان راهِ همیشگی', async () => {
  for (const body of [{ phone: '0792236008' }, { destination: '+93792236008' }]) {
    const r = await h.post('/api/auth/otp/request', body);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'email_required');
  }
  assert.equal((await codes('+93792236008')).length, 0, 'هیچ کدی برای شماره ساخته نشد');
  const v = await h.post('/api/auth/otp/verify', { phone: '0792236008', code: '123456' });
  assert.equal(v.status, 400, JSON.stringify(v.body));
  assert.equal(v.body.error.code, 'email_required');

  const asked = await h.post('/api/auth/otp/request', { email: 'only-mail@test.local' });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  const inn = await h.post('/api/auth/otp/verify', { email: 'only-mail@test.local', code: asked.body.devCode, name: 'ایمیلی' });
  assert.equal(inn.status, 201, JSON.stringify(inn.body));
  assert.equal(inn.body.user.phone, null);
});

test('ورود با رمز: شماره (در identifier یا phone) ⇒ ۴۰۰ email_required؛ ایمیل وارد می‌شود', async () => {
  const u = await h.newUser('رمزدار');
  await query('UPDATE users SET phone=$2 WHERE id=$1', [u.user.id, '+93791112233']);
  for (const body of [{ identifier: '0791112233', password: u.password }, { phone: '0791112233', password: u.password }]) {
    const r = await h.post('/api/auth/login', body);
    assert.equal(r.status, 400, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'email_required');
  }
  const ok = await h.post('/api/auth/login', { identifier: u.email, password: u.password });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
});

test('lib/otp: کد فقط به ایمیل (email_only) — شماره نه ساخته می‌شود نه فرستاده', async () => {
  const otp = require('../src/lib/otp');
  const sent = [];
  const keep = { ...otp.senders };
  for (const k of Object.keys(keep)) otp.senders[k] = async (...a) => { sent.push([k, ...a]); };
  try {
    await assert.rejects(otp.request('+93790001234', { purpose: 'login' }), (e) => e.code === 'email_only');
    await assert.rejects(otp.request('0790001234', { purpose: 'store_order' }), (e) => e.code === 'email_only');
  } finally { Object.assign(otp.senders, keep); }
  assert.deepEqual(sent, []);
  assert.equal((await codes('+93790001234')).length, 0);

  //  میزِ مدیر: کدِ کهنهٔ شماره‌ای (پیش از این تصمیم) دوباره فرستاده نمی‌شود
  const id = newId('otp');
  await query(`INSERT INTO otp_codes (id, purpose, destination, code_hash, app, attempts, max_attempts, expires_at, created_at, ip, via)
               VALUES ($1,'login','+93790009999','x','shop',0,5,$2,$3,'','sms')`, [id, now() + 600_000, now()]);
  for (const k of Object.keys(keep)) otp.senders[k] = async (...a) => { sent.push([k, ...a]); };
  try {
    const out = await otp.resend(id);
    assert.equal(out.ok, false);
    assert.equal(out.error, 'email_only');
  } finally { Object.assign(otp.senders, keep); }
  assert.deepEqual(sent, []);
});

test('تأییدِ دوباره: کد فقط به ایمیلِ حساب؛ حسابِ فقط‌شماره ⇒ ۴۰۰ no_email بی کد', async () => {
  const old = await legacyPhoneUser('+93795550001');
  const r = await h.post('/api/auth/reauth/code', {}, { token: old.accessToken });
  assert.equal(r.status, 400, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'no_email');
  assert.equal((await codes('+93795550001')).length, 0);

  const u = await h.newUser('بازتأیید');
  const c = await h.post('/api/auth/reauth/code', {}, { token: u.accessToken });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  assert.equal((await codes(u.email)).filter((x) => x.purpose === 'reauth').length, 1);
});

test('شمارهٔ حساب فیلدِ داده: افزودن، تغییر و پاک بی کد؛ دو حساب یک شماره؛ درِ کدِ شماره رفت', async () => {
  const a = await h.newUser('الف');
  const b = await h.newUser('ب');
  //  درِ کدِ شماره (AUTH-08 ِ پیامکی) نیست: همان پاسخِ مسیرِ ناشناخته
  const gone = await h.post('/api/me/phone/code', { phone: '0794440001' }, { token: a.accessToken });
  const unknown = await h.post('/api/me/no-such-door', { phone: '0794440001' }, { token: a.accessToken });
  assert.equal(gone.status, unknown.status, JSON.stringify(gone.body));
  assert.deepEqual(gone.body, unknown.body);

  const add = await h.put('/api/me', { name: 'الف', phone: '0794440001' }, { token: a.accessToken });
  assert.equal(add.status, 200, JSON.stringify(add.body));
  assert.equal(add.body.user.phone, '+93794440001');
  const same = await h.put('/api/me', { name: 'ب', phone: '+93794440001' }, { token: b.accessToken });
  assert.equal(same.status, 200, 'شماره یکتا نیست — راهِ ورود نیست، پس «گرفته شده» هم نیست');
  const change = await h.put('/api/me', { name: 'الف', phone: '0794440002' }, { token: a.accessToken });
  assert.equal(change.status, 200, JSON.stringify(change.body));
  assert.equal(change.body.user.phone, '+93794440002');
  const keep = await h.put('/api/me', { name: 'الفِ تازه' }, { token: a.accessToken });
  assert.equal(keep.body.user.phone, '+93794440002', 'نیامدنِ phone یعنی دست نزن');
  const clear = await h.put('/api/me', { name: 'الف', phone: '' }, { token: a.accessToken });
  assert.equal(clear.status, 200, JSON.stringify(clear.body));
  assert.equal(clear.body.user.phone, null);
  const bad = await h.put('/api/me', { name: 'الف', phone: '12' }, { token: a.accessToken });
  assert.equal(bad.status, 400, 'فقط شکلِ شماره سنجیده می‌شود');
  assert.equal((await many("SELECT 1 FROM otp_codes WHERE purpose LIKE 'phone_add:%'")).length, 0, 'هیچ کدی برای شماره');
});

test('AUTH-08 ⇐ ایمیل: حسابِ بی‌ایمیل ایمیل را فقط با نشستِ تازه و کدِ همان ایمیل می‌افزاید؛ «گرفته شده» فقط پس از کد', async () => {
  const pass = 'Passw0rd!legacy';
  const old = await legacyPhoneUser('+93796660001', { password: pass });
  const taken = await h.newUser('دارندهٔ ایمیل');

  //  بی تأییدِ دوباره ⇒ ۴۰۳ ِ همان `reauth_required`
  const cold = await h.post('/api/me/email/code', { email: 'new-owner@test.local' }, { token: old.accessToken });
  assert.equal(cold.status, 403, JSON.stringify(cold.body));
  const fresh = await h.post('/api/auth/reauth', { password: pass }, { token: old.accessToken });
  assert.equal(fresh.status, 200, JSON.stringify(fresh.body));

  const sent = await h.post('/api/me/email/code', { email: 'New-Owner@test.local' }, { token: old.accessToken });
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.match(String(sent.body.devCode), /^\d{6}$/);
  const none = await h.put('/api/me', { name: 'قدیمی', email: 'new-owner@test.local' }, { token: old.accessToken });
  assert.equal(none.status, 400, JSON.stringify(none.body));
  assert.equal(none.body.error.code, 'email_code_required');
  const wrong = await h.put('/api/me', { name: 'قدیمی', email: 'new-owner@test.local', emailCode: '000000' }, { token: old.accessToken });
  assert.equal(wrong.status, 403, JSON.stringify(wrong.body));

  //  ایمیلِ حسابِ دیگر: کد می‌رود (بی شمارش‌پذیری)، ۴۰۹ فقط پس از کدِ درست
  const tc = await h.post('/api/me/email/code', { email: taken.email }, { token: old.accessToken });
  assert.equal(tc.status, 200, JSON.stringify(tc.body));
  const clash = await h.put('/api/me', { name: 'قدیمی', email: taken.email, emailCode: tc.body.devCode }, { token: old.accessToken });
  assert.equal(clash.status, 409, JSON.stringify(clash.body));
  assert.equal(clash.body.error.code, 'email_taken');

  const again = await h.post('/api/me/email/code', { email: 'new-owner@test.local' }, { token: old.accessToken });
  const ok = await h.put('/api/me', { name: 'قدیمی', email: 'new-owner@test.local', emailCode: again.body.devCode || sent.body.devCode }, { token: old.accessToken });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.user.email, 'new-owner@test.local');
  assert.equal(ok.body.user.phone, '+93796660001', 'شماره همان فیلدِ داده می‌ماند');
  //  حالا ورود با همان ایمیل و رمز
  const login = await h.post('/api/auth/login', { identifier: 'new-owner@test.local', password: pass });
  assert.equal(login.status, 200, JSON.stringify(login.body));

  //  ایمیلِ ثبت‌شده از این‌جا عوض نمی‌شود
  const lock = await h.post('/api/me/email/code', { email: 'other@test.local' }, { token: login.body.accessToken });
  assert.equal(lock.status, 409, JSON.stringify(lock.body));
  assert.equal(lock.body.error.code, 'email_locked');
  const audit = await one("SELECT * FROM audit_logs WHERE action='account.email_added' AND user_id=$1", [old.id]);
  assert.ok(audit, 'سابقهٔ افزودنِ ایمیل');
});

test('کدِ VIP ِ دکان دیگر پیامک نمی‌شود (حسابِ پیامک لازم نیست)؛ پیامکِ VIP فقط برای پمپ مانده', async () => {
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'vip-admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  const t = (await h.post('/api/admin/login', { username: 'vip-admin', password: 'Admin!12345' })).body.token;
  const otp = require('../src/lib/otp');
  const keep = otp.senders.sms;
  const sent = [];
  otp.senders.sms = async (...a) => { sent.push(a); };
  try {
    const r = await h.post('/api/admin/vip-codes', { plan: 'custom', days: 30, phone: '0790000001' }, { token: t });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal(r.body.smsStatus, 'none');
    assert.deepEqual(sent, [], 'هیچ پیامکی');
    assert.equal((await one('SELECT phone FROM vip_codes WHERE id=$1', [r.body.vipCode.id])).phone, '', 'شماره هم نگه داشته نشد');
  } finally { otp.senders.sms = keep; }
});
