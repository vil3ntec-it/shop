'use strict';
/**
 * C-01 (ممیزیِ رازهای در حالِ سکون، ۱۴۰۵/۰۸/۲۶) — رازِ TOTP ِ مدیر در دیتابیس رمزشده است، نه base32 ِ خام.
 *
 *   • پس از «روشن کردن» نه `totp_pending` و نه `totp_secret` برابرِ رازی که به مدیر نشان داده شد نیستند
 *     (ردیفِ واقعیِ دیتابیس خوانده می‌شود) و ورود با کدِ درست همچنان کار می‌کند
 *   • ردیفِ کهنهٔ خام (پیش از این نسخه) هنوز وارد می‌کند و `sealLegacy` آن را یک بار رمز می‌کند
 *   • پاکتِ یک مدیر روی ردیفِ مدیرِ دیگر باز نمی‌شود (AAD = شناسهٔ مدیر) ⇒ کدِ او پذیرفته نیست
 *   • برگشت (`unsealAll`) همان خام را برمی‌گرداند تا نسخهٔ پیشین هم بخواند
 */
process.env.RATE_AUTH_MAX = process.env.RATE_AUTH_MAX || '1000';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const totp = require('../src/lib/totp');

const PASS = 'Root!12345';
let adminId;
let otherId;
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  adminId = newId('adm');
  otherId = newId('adm');
  const hash = await pw.hashPassword(PASS);
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$3,'superadmin','active',$4), ($2,'second','دوم',$3,'superadmin','active',$4)`,
    [adminId, otherId, hash, now()]
  );
});
test.after(async () => { await h.stop(); });

const login = (username, body = {}) => h.post('/api/admin/login', { username, password: PASS, ...body });
async function codeFor(id, secret) {
  const row = await one('SELECT totp_last_step FROM admins WHERE id=$1', [id]);
  return totp.hotp(totp.base32Decode(secret), Math.max(totp.stepAt(), Number(row.totp_last_step) + 1));
}

let secret;

test('۱) راهِ واقعی: رازِ در انتظار و رازِ روشن هیچ‌کدام base32 ِ خام نیستند؛ ورود با کد کار می‌کند', async () => {
  const token = (await login('root')).body.token;
  const s = await h.post('/api/admin/2fa/setup', { password: PASS }, { token });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  secret = s.body.secret;
  assert.match(secret, /^[A-Z2-7]{32}$/);
  const pending = await one('SELECT totp_pending, totp_secret FROM admins WHERE id=$1', [adminId]);
  assert.notEqual(pending.totp_pending, secret, 'رازِ در انتظار خام در دیتابیس نیست');
  assert.ok(!pending.totp_pending.includes(secret));
  assert.match(pending.totp_pending, /^ats1\./);

  const ok = await h.post('/api/admin/2fa/enable', { code: totp.generate(secret) }, { token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  const row = await one('SELECT totp_pending, totp_secret, totp_recovery FROM admins WHERE id=$1', [adminId]);
  assert.equal(row.totp_pending, '');
  assert.notEqual(row.totp_secret, secret, 'رازِ روشن خام در دیتابیس نیست');
  assert.ok(!row.totp_secret.includes(secret));
  assert.match(row.totp_secret, /^ats1\./);
  for (const c of ok.body.recovery_codes) assert.ok(!row.totp_recovery.includes(c), 'کدِ بازیابی هم خام نیست');

  assert.equal((await login('root')).status, 401, 'بی کد نه');
  const good = await login('root', { totp: await codeFor(adminId, secret) });
  assert.equal(good.status, 200, JSON.stringify(good.body));
});

test('۲) پاکتِ مدیرِ «root» روی ردیفِ «second» باز نمی‌شود', async () => {
  const sealed = (await one('SELECT totp_secret FROM admins WHERE id=$1', [adminId])).totp_secret;
  await query(`UPDATE admins SET totp_secret=$2, totp_enabled_at=$3, totp_last_step=0, totp_recovery='[]' WHERE id=$1`,
    [otherId, sealed, now()]);
  const r = await login('second', { totp: totp.generate(secret) });
  assert.equal(r.status, 401);
  assert.equal(r.body.error.code, 'totp_invalid');
  await query(`UPDATE admins SET totp_secret='', totp_enabled_at=NULL WHERE id=$1`, [otherId]);
});

test('۳) ردیفِ کهنهٔ خام: هنوز وارد می‌کند؛ sealLegacy یک بار رمزش می‌کند و ورود همان می‌ماند', async () => {
  const seal = require('../src/lib/admin-totp-seal');
  const legacy = totp.newSecret();
  await query(`UPDATE admins SET totp_secret=$2, totp_pending=$3, totp_enabled_at=$4, totp_last_step=0, totp_recovery='[]'
               WHERE id=$1`, [otherId, legacy, totp.newSecret(), now()]);
  const before = await login('second', { totp: await codeFor(otherId, legacy) });
  assert.equal(before.status, 200, 'پنجرهٔ مهاجرت: خامِ کهنه پذیرفته');

  const n = await seal.sealLegacy();
  assert.ok(n >= 1, `شمارِ رمزشده: ${n}`);
  const row = await one('SELECT totp_secret, totp_pending FROM admins WHERE id=$1', [otherId]);
  assert.match(row.totp_secret, /^ats1\./);
  assert.match(row.totp_pending, /^ats1\./);
  assert.ok(!row.totp_secret.includes(legacy));
  assert.equal(await seal.sealLegacy(), 0, 'دوباره چیزی برای رمز کردن نیست (idempotent)');
  const left = await one(`SELECT COUNT(*)::int n FROM admins WHERE (totp_secret <> '' AND totp_secret NOT LIKE 'ats1.%')
                            OR (totp_pending <> '' AND totp_pending NOT LIKE 'ats1.%')`);
  assert.equal(left.n, 0, 'هیچ رازِ خامی در جدول نمانده');

  const after = await login('second', { totp: await codeFor(otherId, legacy) });
  assert.equal(after.status, 200, JSON.stringify(after.body));
});

test('۴) برگشت: unsealAll خام را برمی‌گرداند (برای نسخهٔ پیشین) و sealLegacy دوباره رمز می‌کند', async () => {
  const seal = require('../src/lib/admin-totp-seal');
  const n = await seal.unsealAll();
  assert.ok(n >= 2);
  const row = await one('SELECT totp_secret FROM admins WHERE id=$1', [adminId]);
  assert.equal(row.totp_secret, secret);
  assert.ok(await seal.sealLegacy() >= 2);
  assert.match((await one('SELECT totp_secret FROM admins WHERE id=$1', [adminId])).totp_secret, /^ats1\./);
});

test('۵) پاکتِ دست‌خورده ⇒ هیچ کدی پذیرفته نیست (بسته، نه باز)', async () => {
  const seal = require('../src/lib/admin-totp-seal');
  const sealed = seal.sealSecret(adminId, secret);
  const parts = sealed.split('.');
  parts[2] = Buffer.from('x'.repeat(20)).toString('base64url');
  assert.equal(seal.openSecret(adminId, parts.join('.')), null);
  assert.equal(seal.openSecret(otherId, sealed), null);
  assert.equal(seal.openSecret(adminId, sealed), secret);
  assert.equal(seal.openSecret(adminId, ''), '');
});
