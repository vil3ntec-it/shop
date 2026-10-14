'use strict';
/**
 * F0-15 — تأییدِ دومرحله‌ایِ «اختیاری» برای مدیرِ سرور (TOTP، RFC 6238).
 *
 *   • پیاده‌سازیِ خودمان با بردارهای آزمونِ خودِ RFC 6238 / RFC 4226
 *   • تا مدیر روشنش نکند ورود همان است که بود (اجباری نیست)
 *   • روشن ⇒ بی کد توکنی داده نمی‌شود؛ کدِ نادرست شکستِ ورود حساب می‌شود
 *   • یک کد دو بار پذیرفته نمی‌شود؛ کدِ بازیابی یک‌بارمصرف است
 *   • راهِ نجاتِ ADMIN_BOOTSTRAP_* هنوز کار می‌کند
 */
//  این پرونده بیش از ده بار وارد می‌شود — سقفِ ورود برای خودِ آزمون باز است
process.env.RATE_AUTH_MAX = process.env.RATE_AUTH_MAX || '1000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const totp = require('../src/lib/totp');

/* ------------------------------------------------ بردارهای RFC (خالص) */

test('RFC 4226 پیوستِ D: HOTPِ شش‌رقمی', () => {
  const key = Buffer.from('12345678901234567890');
  const want = ['755224', '287082', '359152', '969429', '338314', '254676', '287922', '162583', '399871', '520489'];
  want.forEach((w, i) => assert.equal(totp.hotp(key, i), w));
});

test('RFC 6238 پیوستِ B (SHA1، هشت رقم) و base32', () => {
  const key = Buffer.from('12345678901234567890');
  const cases = [[59, '94287082'], [1111111109, '07081804'], [1111111111, '14050471'],
    [1234567890, '89005924'], [2000000000, '69279037'], [20000000000, '65353130']];
  for (const [t, w] of cases) assert.equal(totp.hotp(key, Math.floor(t / 30), 8), w);
  assert.equal(totp.base32Encode(key), 'GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ');
  assert.deepEqual(totp.base32Decode('gezd gnbv gy3t qojq gezd gnbv gy3t qojq'), key);
  assert.equal(totp.generate('GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ', 59_000), '287082');
});

test('verify: یک گام پس و پیش؛ بیرونش نه؛ گامِ مصرف‌شده نه', () => {
  const s = totp.newSecret();
  const t = 1_700_000_000_000;
  const code = totp.generate(s, t);
  assert.equal(totp.verify(s, code, { ms: t }), totp.stepAt(t));
  assert.equal(totp.verify(s, code, { ms: t + 30_000 }), totp.stepAt(t));
  assert.equal(totp.verify(s, code, { ms: t + 95_000 }), null);
  assert.equal(totp.verify(s, code, { ms: t, afterStep: totp.stepAt(t) }), null);
  assert.equal(totp.verify(s, 'abcdef', { ms: t }), null);
  assert.equal(totp.verify(s, '', { ms: t }), null);
});

/* ------------------------------------------------ سرور */

const PASS = 'Root!12345';
let adminId;
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  adminId = newId('adm');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [adminId, await pw.hashPassword(PASS), now()]
  );
});
test.after(async () => { await h.stop(); });

const login = (body) => h.post('/api/admin/login', { username: 'root', password: PASS, ...body });
const nextStepCode = async (secret) => {
  //  گامِ بعدی تا «کدِ تکراری» نشود — همان کاری که ۳۰ ثانیه صبر می‌کرد
  const row = await one('SELECT totp_last_step FROM admins WHERE id=$1', [adminId]);
  const step = Math.max(totp.stepAt(), Number(row.totp_last_step) + 1);
  return totp.hotp(totp.base32Decode(secret), step);
};

let secret; let recovery; let token;

test('خاموش (پیش‌فرض): ورود مثلِ همیشه، بی کد', async () => {
  const r = await login({});
  assert.equal(r.status, 200);
  token = r.body.token;
  const st = await h.get('/api/admin/2fa', { token });
  assert.equal(st.status, 200);
  assert.deepEqual({ e: st.body.enabled, f: st.body.enforced }, { e: false, f: false });
});

test('روشن کردن: رمز لازم است؛ کدِ نادرست روشن نمی‌کند؛ کدِ درست ده کدِ بازیابی می‌دهد', async () => {
  const bad = await h.post('/api/admin/2fa/setup', { password: 'wrong-pass' }, { token });
  assert.equal(bad.status, 403);
  const s = await h.post('/api/admin/2fa/setup', { password: PASS }, { token });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  secret = s.body.secret;
  assert.match(secret, /^[A-Z2-7]{32}$/);
  assert.match(s.body.otpauth_url, /^otpauth:\/\/totp\/.+secret=[A-Z2-7]+.*issuer=/);
  //  هنوز روشن نیست: ورود بی کد کار می‌کند
  assert.equal((await login({})).status, 200);

  const wrong = await h.post('/api/admin/2fa/enable', { code: '000000' === totp.generate(secret) ? '111111' : '000000' }, { token });
  assert.equal(wrong.status, 400);
  const ok = await h.post('/api/admin/2fa/enable', { code: totp.generate(secret) }, { token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.enabled, true);
  assert.equal(ok.body.recovery_codes.length, 10);
  assert.equal(ok.body.recovery_left, 10);
  recovery = ok.body.recovery_codes;
  const row = await one('SELECT totp_recovery FROM admins WHERE id=$1', [adminId]);
  assert.ok(!row.totp_recovery.includes(recovery[0]), 'کدِ بازیابی خام در دیتابیس نیست');
});

test('روشن: بی کد توکن نیست، کدِ نادرست شکست است، کدِ درست وارد می‌کند و دوباره پذیرفته نمی‌شود', async () => {
  const none = await login({});
  assert.equal(none.status, 401);
  assert.equal(none.body.error.code, 'totp_required');
  assert.equal(none.body.token, undefined);

  const before = await one(`SELECT COUNT(*)::int n FROM login_attempts WHERE scope='admin' AND identifier='root' AND ok=false`);
  const bad = await login({ totp: '000000' === totp.generate(secret) ? '111111' : '000000' });
  assert.equal(bad.status, 401);
  assert.equal(bad.body.error.code, 'totp_invalid');
  const after = await one(`SELECT COUNT(*)::int n FROM login_attempts WHERE scope='admin' AND identifier='root' AND ok=false`);
  assert.equal(after.n, before.n + 1, 'کدِ نادرست در شمارِ قفل حساب می‌شود');

  const code = await nextStepCode(secret);
  const good = await login({ totp: code });
  assert.equal(good.status, 200, JSON.stringify(good.body));
  assert.ok(good.body.token);
  const replay = await login({ totp: code });
  assert.equal(replay.status, 401, 'همان کد دو بار نه');

  //  رمزِ نادرست با کدِ درست هم نه
  const wrongPass = await h.post('/api/admin/login', { username: 'root', password: 'nope-nope', totp: await nextStepCode(secret) });
  assert.equal(wrongPass.status, 401);
  assert.equal(wrongPass.body.error.code, 'bad_credentials');
});

test('کدِ بازیابی یک بار وارد می‌کند و بارِ دوم نه', async () => {
  const one1 = await login({ recovery_code: recovery[0].toUpperCase() });
  assert.equal(one1.status, 200, JSON.stringify(one1.body));
  const again = await login({ recovery_code: recovery[0] });
  assert.equal(again.status, 401);
  const st = await h.get('/api/admin/2fa', { token: one1.body.token });
  assert.equal(st.body.recovery_left, 9);
});

test('راهِ نجات: ADMIN_BOOTSTRAP_PASSWORDِ تازه ⇒ رمز به محیط و تأییدِ دومرحله‌ای خاموش', async () => {
  const { ensureAdmin } = require('../src/lib/admin-bootstrap');
  //  همان رمز ⇒ هیچ چیز عوض نمی‌شود، تأیید روشن می‌ماند
  await ensureAdmin({ username: 'root', password: PASS });
  assert.equal((await login({})).status, 401);
  //  رمزِ تازه در محیط ⇒ نجات
  const out = await ensureAdmin({ username: 'root', password: 'Rescue!98765' });
  assert.equal(out.updated, true);
  const r = await h.post('/api/admin/login', { username: 'root', password: 'Rescue!98765' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const st = await h.get('/api/admin/2fa', { token: r.body.token });
  assert.equal(st.body.enabled, false);
  const audit = await one(`SELECT COUNT(*)::int n FROM audit_logs WHERE action='admin.2fa_reset_by_bootstrap'`);
  assert.equal(audit.n, 1);
});

test('خاموش کردن رمز و کد می‌خواهد', async () => {
  const r = await h.post('/api/admin/login', { username: 'root', password: 'Rescue!98765' });
  const t = r.body.token;
  const s = await h.post('/api/admin/2fa/setup', { password: 'Rescue!98765' }, { token: t });
  const en = await h.post('/api/admin/2fa/enable', { code: totp.generate(s.body.secret) }, { token: t });
  assert.equal(en.status, 200);
  const noCode = await h.post('/api/admin/2fa/disable', { password: 'Rescue!98765' }, { token: t });
  assert.equal(noCode.status, 403);
  const off = await h.post('/api/admin/2fa/disable', { password: 'Rescue!98765', recovery_code: en.body.recovery_codes[3] }, { token: t });
  assert.equal(off.status, 200, JSON.stringify(off.body));
  assert.equal(off.body.enabled, false);
  assert.equal((await h.post('/api/admin/login', { username: 'root', password: 'Rescue!98765' })).status, 200);
});

test('پنلِ وب: کادرِ کدِ ورود و بخشِ تأییدِ دومرحله‌ای', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const html = fs.readFileSync(path.join(__dirname, '..', 'public', 'admin', 'index.html'), 'utf8');
  const js = fs.readFileSync(path.join(__dirname, '..', 'public', 'admin', 'app.js'), 'utf8');
  assert.match(html, /id="in-totp"/);
  assert.match(html, /id="twofa-panel"/);
  assert.match(js, /totp_required/);
  assert.match(js, /\/admin\/2fa\/enable/);
});
