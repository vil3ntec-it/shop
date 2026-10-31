'use strict';
/**
 * AUTH-08 — «تغییر ایمیل/روش ورود بدون اثبات مالکیت انجام نشود» (§۲۹.۸).
 *
 * شماره راهِ ورود است (`/api/auth/login` با شماره و رمز، `/api/auth/otp/*` با کدِ پیامکی).
 * با پرچمِ `auth.phone_add_code` (scope `security`، پیش‌فرض خاموش) افزودنِ شماره به حسابِ
 * بی‌شماره، کدِ یک‌بارمصرفِ **همان شماره** و **همان حساب** را هم می‌خواهد (`routes/me.js`).
 * پرچمِ خاموش ⇒ رفتارِ امروز (فقط تأییدِ دوبارهٔ حساب، `reauth.test.js`).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const { one, many, query, newId, now } = require('../src/db');

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'phone-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'phone-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function flag(on) {
  const r = await h.put('/api/admin/pump/live-config', { scope: 'security', key: 'auth.phone_add_code', value: on }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const phoneOf = async (u) => (await one('SELECT phone FROM users WHERE id=$1', [u.user.id])).phone;
const codeFor = (u, phone) => h.post('/api/me/phone/code', { phone }, { token: u.accessToken });
const claim = (u, phone, phoneCode) => h.put('/api/me', { name: 'نام', phone, ...(phoneCode !== undefined ? { phoneCode } : {}) }, { token: u.accessToken });
const rowsOf = (u) => many('SELECT * FROM otp_codes WHERE purpose=$1', [`phone_add:${u.user.id}`]);
let n = 0;
const freshPhone = () => `07${String(70000000 + (++n)).slice(-8)}`;

test('آزمونِ مرجع، پرچمِ خاموش: همان رفتارِ امروز و درِ کد بسته، بی هیچ کدی', async () => {
  await flag(false);
  const u = await h.newUser('بی‌پرچم');
  const phone = freshPhone();
  const door = await codeFor(u, phone);
  assert.equal(door.status, 404, JSON.stringify(door.body));
  assert.equal(door.body.error.code, 'phone_code_disabled');
  assert.equal((await rowsOf(u)).length, 0, 'کدی ساخته نشد');
  const ok = await claim(u, phone);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.ok(ok.body.user.phone);
});

test('پرچمِ روشن: بی کد، با کدِ غلط، کدِ حسابِ دیگر و کدِ منقضی شماره نمی‌نشیند؛ با کدِ درست یک بار', async () => {
  await flag(true);
  const u = await h.newUser('با کد');
  const other = await h.newUser('حسابِ دیگر');
  const phone = freshPhone();

  const none = await claim(u, phone);
  assert.equal(none.status, 400, JSON.stringify(none.body));
  assert.equal(none.body.error.code, 'phone_code_required');
  assert.equal(await phoneOf(u), null);

  const sent = await codeFor(u, phone);
  assert.equal(sent.status, 200, JSON.stringify(sent.body));
  assert.equal(sent.body.sent, true);
  const code = sent.body.devCode;
  assert.match(code, /^\d{6}$/);
  //  در پایگاه فقط هش، بسته به همین حساب
  const [row] = await rowsOf(u);
  assert.ok(row && row.destination === sent.body.phone, JSON.stringify(row));
  assert.ok(!JSON.stringify(row).includes(`"${code}"`), 'کدِ خام در ردیف نیست');

  const wrong = await claim(u, phone, code === '111111' ? '222222' : '111111');
  assert.equal(wrong.status, 403, JSON.stringify(wrong.body));
  assert.equal(wrong.body.error.code, 'otp_wrong');
  assert.equal(await phoneOf(u), null);

  //  همان کد برای حسابِ دیگر (هدف بسته به حساب) کار نمی‌کند — و کدِ «الف» را نمی‌سوزاند
  const stolen = await claim(other, phone, code);
  assert.equal(stolen.status, 403, JSON.stringify(stolen.body));
  assert.equal(stolen.body.error.code, 'otp_not_found');
  assert.equal(await phoneOf(other), null);

  const ok = await claim(u, phone, code);
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.user.phone, sent.body.phone);
  const log = await one(`SELECT detail FROM audit_logs WHERE action='account.phone_added' AND user_id=$1`, [u.user.id]);
  assert.equal((typeof log.detail === 'string' ? JSON.parse(log.detail) : log.detail).method, 'code');
  assert.ok((await one('SELECT consumed_at FROM otp_codes WHERE id=$1', [row.id])).consumed_at, 'کد مصرف شد');

  //  شمارهٔ نشسته قفل است — نه کدِ تازه، نه عوض کردن
  assert.equal((await codeFor(u, freshPhone())).status, 409);
  assert.equal((await claim(u, freshPhone(), code)).body.error.code, 'phone_locked');

  //  کدِ منقضی
  const late = await h.newUser('دیرکرده');
  const p2 = freshPhone();
  const s2 = await codeFor(late, p2);
  assert.equal(s2.status, 200);
  await query('UPDATE otp_codes SET expires_at=$2 WHERE purpose=$1', [`phone_add:${late.user.id}`, now() - 1]);
  const expired = await claim(late, p2, s2.body.devCode);
  assert.equal(expired.status, 403, JSON.stringify(expired.body));
  assert.equal(expired.body.error.code, 'otp_expired');
  assert.equal(await phoneOf(late), null);
});

test('پرچمِ روشن: سقفِ تلاش — پس از پنج کدِ غلط، کدِ درست هم رد', async () => {
  await flag(true);
  const u = await h.newUser('حدس‌زن');
  const phone = freshPhone();
  const sent = await codeFor(u, phone);
  const code = sent.body.devCode;
  const bad = code === '111111' ? '222222' : '111111';
  for (let i = 0; i < config.otp.maxAttempts; i++) assert.equal((await claim(u, phone, bad)).status, 403);
  const late = await claim(u, phone, code);
  assert.equal(late.status, 403, JSON.stringify(late.body));
  assert.equal(late.body.error.code, 'otp_locked');
  assert.equal(await phoneOf(u), null);
});

test('پرچمِ روشن: نشستِ کهنه کد نمی‌فرستد و شماره نمی‌نشاند', async () => {
  await flag(true);
  const u = await h.newUser('کهنه');
  const r = await h.post('/api/auth/refresh', { refreshToken: u.refreshToken });
  const stale = { ...u, accessToken: r.body.accessToken };
  const door = await codeFor(stale, freshPhone());
  assert.equal(door.status, 403, JSON.stringify(door.body));
  assert.equal(door.body.error.code, 'reauth_required');
  assert.equal((await rowsOf(u)).length, 0, 'پیامکی برای نشستِ کهنه نرفت');
  assert.equal((await claim(stale, freshPhone(), '123456')).body.error.code, 'reauth_required');
});

test('پرچمِ روشن: «شماره گرفته شده» فقط پس از کدِ درست — بی شمارش‌پذیری', async () => {
  await flag(true);
  const holder = await h.newUser('دارندهٔ شماره');
  const phone = freshPhone();
  const c1 = await codeFor(holder, phone);
  assert.equal((await claim(holder, phone, c1.body.devCode)).status, 200);

  const probe = await h.newUser('کاوشگر');
  const sent = await codeFor(probe, phone);
  assert.equal(sent.status, 200, 'درخواستِ کد برای شمارهٔ گرفته همان پاسخِ شمارهٔ آزاد را دارد');
  assert.deepEqual(Object.keys(sent.body).sort(), Object.keys(c1.body).sort());
  const guess = await claim(probe, phone, sent.body.devCode === '111111' ? '222222' : '111111');
  assert.equal(guess.body.error.code, 'otp_wrong', 'بی کدِ درست، چیزی دربارهٔ دارندهٔ شماره گفته نمی‌شود');
  const proven = await claim(probe, phone, sent.body.devCode);
  assert.equal(proven.status, 409, JSON.stringify(proven.body));
  assert.equal(proven.body.error.code, 'phone_taken');
  assert.equal(await phoneOf(probe), null);
});

test('پرچمِ روشن: دو حسابِ هم‌زمان با کدِ درستِ یک شماره ⇒ یکی می‌نشیند، دیگری ۴۰۹ — نه ۵۰۰', async () => {
  await flag(true);
  const a = await h.newUser('هم‌زمانِ یک');
  const b = await h.newUser('هم‌زمانِ دو');
  const phone = freshPhone();
  const ca = (await codeFor(a, phone)).body.devCode;
  const cb = (await codeFor(b, phone)).body.devCode;
  const [ra, rb] = await Promise.all([claim(a, phone, ca), claim(b, phone, cb)]);
  assert.deepEqual([ra.status, rb.status].sort(), [200, 409], `${JSON.stringify(ra.body)} ${JSON.stringify(rb.body)}`);
  const holders = await many('SELECT id FROM users WHERE phone=$1', [ra.status === 200 ? ra.body.user.phone : rb.body.user.phone]);
  assert.equal(holders.length, 1);
});

test('پرچمِ روشن، production بی سرویسِ پیامک: ۵۰۳ ِ صریح و هیچ کدی ساخته نمی‌شود', async () => {
  await flag(true);
  const u = await h.newUser('بی‌پیامک');
  const real = config.env;
  let r;
  try {
    config.env = 'production';
    r = await codeFor(u, freshPhone());
  } finally { config.env = real; }
  assert.equal(r.status, 503, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'verification_unavailable');
  assert.equal((await rowsOf(u)).length, 0);
  await flag(false);
});
