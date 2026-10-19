'use strict';
/**
 * وصل کردنِ گوگل به حسابِ موجود — بندِ ۲۹.۲ قاعدهٔ ۵ و آزمونِ AUTH-04 (و AUTH-03).
 *
 * «برابر بودنِ ایمیل به‌تنهایی مجوزِ ادغامِ خودکار نیست.» توکنِ گوگل با کلیدِ
 * RSAِ خودِ آزمون امضا می‌شود و کلیدِ عمومی از یک سرورِ HTTPِ محلی (همان
 * قالبِ JWKSِ گوگل) خوانده می‌شود؛ پس `lib/google.verifyIdToken`ِ واقعی —
 * امضا، `iss`، `aud`، `exp` — بی هیچ میان‌بری اجرا می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { generateKeyPairSync, createSign } = require('node:crypto');
const h = require('./helpers');
const config = require('../src/config');
const { many, one } = require('../src/db');

const CLIENT_ID = 'test-client.apps.googleusercontent.com';
const KID = 'kid-test-1';
const keys = generateKeyPairSync('rsa', { modulusLength: 2048 });
const stranger = generateKeyPairSync('rsa', { modulusLength: 2048 });
let jwks = null;

test.before(async () => {
  await h.start();
  const jwk = { ...keys.publicKey.export({ format: 'jwk' }), kid: KID, alg: 'RS256', use: 'sig' };
  jwks = http.createServer((req, res) => {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'max-age=3600' });
    res.end(JSON.stringify({ keys: [jwk] }));
  });
  await new Promise((r) => jwks.listen(0, '127.0.0.1', r));
  config.google.clientIds = [CLIENT_ID];
  config.google.certsUrl = `http://127.0.0.1:${jwks.address().port}/certs`;
});
test.after(async () => {
  config.google.legacyEmailLink = false;
  jwks?.close();
  await h.stop();
});

const b64u = (x) => Buffer.from(typeof x === 'string' ? x : JSON.stringify(x)).toString('base64url');
function googleToken({ sub, email, verified = true, aud = CLIENT_ID, key = keys.privateKey, expIn = 600 }) {
  const t = Math.floor(Date.now() / 1000);
  const head = b64u({ alg: 'RS256', kid: KID, typ: 'JWT' });
  const body = b64u({
    iss: 'https://accounts.google.com', aud, sub, email, email_verified: verified,
    name: 'کاربرِ گوگل', iat: t, exp: t + expIn,
  });
  const s = createSign('RSA-SHA256');
  s.update(`${head}.${body}`);
  return `${head}.${body}.${s.sign(key).toString('base64url')}`;
}

const identities = (userId) => many(
  `SELECT * FROM user_identities WHERE user_id=$1 AND provider='google'`, [userId]
);
const linkAudits = (userId) => many(
  `SELECT * FROM audit_logs WHERE action='auth.identity_linked' AND user_id=$1`, [userId]
);
const detailOf = (row) => (typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail);

test('AUTH-04: ایمیلِ برابر بی اثبات ⇒ ۴۰۹ google_link_required، نه ورود و نه وصل', async () => {
  const u = await h.newUser('صاحبِ رمز');
  const idToken = googleToken({ sub: 'g-sub-1', email: u.email });

  const r = await h.post('/api/auth/google', { idToken });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'google_link_required');
  assert.match(r.body.error.message, /رمز/);
  assert.deepEqual(r.body.error.link.methods, ['password', 'email_code']);
  assert.equal(r.body.accessToken, undefined, 'هیچ نشستی صادر نشد');
  assert.equal((await identities(u.user.id)).length, 0, 'گوگل وصل نشد');

  //  رمزِ غلط هم وصل نمی‌کند
  const wrong = await h.post('/api/auth/google', { idToken, password: 'Wrong!pass1' });
  assert.equal(wrong.status, 401);
  assert.equal(wrong.body.error.code, 'bad_credentials');
  assert.equal((await identities(u.user.id)).length, 0);
  assert.equal((await linkAudits(u.user.id)).length, 0);
});

test('رمزِ درستِ همان حساب ⇒ وصل، با سابقهٔ auth.identity_linked؛ از آن به بعد گوگلِ تنها کافی است', async () => {
  const u = await h.newUser('وصل با رمز');
  const idToken = googleToken({ sub: 'g-sub-2', email: u.email });

  const ok = await h.post('/api/auth/google', { idToken, password: u.password });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.created, false);
  assert.equal(ok.body.user.id, u.user.id, 'همان حساب، نه حسابِ تازه');
  assert.ok(ok.body.accessToken);
  assert.equal((await identities(u.user.id)).length, 1);

  const logs = await linkAudits(u.user.id);
  assert.equal(logs.length, 1);
  assert.deepEqual(detailOf(logs[0]), { provider: 'google', via: 'password' });
  assert.ok(!JSON.stringify(logs[0]).includes(u.password), 'رمز در سابقه نیست (AUTH-10)');

  //  کاربرِ وصل‌شده: بی رمز و بی کد، مثلِ همیشه
  const again = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-sub-2', email: u.email }) });
  assert.equal(again.status, 200, JSON.stringify(again.body));
  assert.equal(again.body.user.id, u.user.id);
  assert.equal((await linkAudits(u.user.id)).length, 1, 'ورودِ عادی سابقهٔ «وصل» نمی‌سازد');
});

test('کدِ ایمیل (purpose=link) هم اثبات است؛ کدِ ورود به کارش نمی‌آید', async () => {
  const u = await h.newUser('وصل با کد');
  const idToken = googleToken({ sub: 'g-sub-3', email: u.email });

  //  کدی که برای «ورود» صادر شده، برای «وصل» پذیرفته نمی‌شود
  const loginCode = await h.post('/api/auth/otp/request', { email: u.email });
  const misuse = await h.post('/api/auth/google', { idToken, code: loginCode.body.devCode });
  assert.notEqual(misuse.status, 200);
  assert.equal((await identities(u.user.id)).length, 0);

  const asked = await h.post('/api/auth/google/link-code', { idToken });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  assert.equal(asked.body.sent, true);
  assert.ok(asked.body.devCode);

  const ok = await h.post('/api/auth/google', { idToken, code: asked.body.devCode });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.user.id, u.user.id);
  assert.deepEqual(detailOf((await linkAudits(u.user.id))[0]), { provider: 'google', via: 'email_code' });

  //  کدِ یک‌بارمصرف: دوباره به کار نمی‌آید
  const reuse = await h.post('/api/auth/google', {
    idToken: googleToken({ sub: 'g-sub-3b', email: u.email }), code: asked.body.devCode,
  });
  assert.notEqual(reuse.status, 200);
});

test('حسابِ بی‌رمز (ساخته با کد) فقط روشِ «کدِ ایمیل» را می‌بیند', async () => {
  const email = 'otp-only@test.local';
  const c = await h.post('/api/auth/otp/request', { email });
  const made = await h.post('/api/auth/otp/verify', { email, code: c.body.devCode, name: 'بی‌رمز' });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-sub-4', email }) });
  assert.equal(r.status, 409);
  assert.deepEqual(r.body.error.link.methods, ['email_code']);
});

test('حسابی که پیش از این تغییر گوگلش وصل شده بود، بی پرسش وارد می‌شود', async () => {
  const u = await h.newUser('وصلِ قدیمی');
  const { newId, now } = require('../src/db');
  await h.query(
    `INSERT INTO user_identities (id, user_id, provider, subject, email, created_at)
     VALUES ($1,$2,'google','g-sub-old',$3,$4)`,
    [newId('idn'), u.user.id, u.email, now()]
  );
  const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-sub-old', email: u.email }) });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.user.id, u.user.id);
});

test('ایمیلِ تازه ⇒ ثبت‌نامِ عادی با گوگل، مثلِ قبل', async () => {
  const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-sub-new', email: 'fresh-google@test.local' }) });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.created, true);
  const id = await one(`SELECT user_id FROM user_identities WHERE provider='google' AND subject='g-sub-new'`);
  assert.equal(id.user_id, r.body.user.id);
});

test('AUTH-03: توکنِ جعلی، audienceِ دیگر یا منقضی رد می‌شود', async () => {
  const forged = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'x', email: 'x@test.local', key: stranger.privateKey }) });
  assert.equal(forged.status, 403);
  assert.equal(forged.body.error.code, 'bad_google_token');
  const aud = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'x', email: 'x@test.local', aud: 'other-app' }) });
  assert.equal(aud.body.error.code, 'bad_audience');
  const old = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'x', email: 'x@test.local', expIn: -3600 }) });
  assert.equal(old.body.error.code, 'google_token_expired');
  const code = await h.post('/api/auth/google/link-code', { idToken: googleToken({ sub: 'x', email: 'x@test.local', key: stranger.privateKey }) });
  assert.equal(code.status, 403, 'کدِ وصل هم بی توکنِ معتبر خواسته نمی‌شود');
});

test('کلیدِ بازگشت (GOOGLE_EMAIL_AUTOLINK) رفتارِ قبلی را برمی‌گرداند — و باز سابقه دارد', async () => {
  const u = await h.newUser('بازگشت');
  config.google.legacyEmailLink = true;
  try {
    const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-sub-legacy', email: u.email }) });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.user.id, u.user.id);
    assert.deepEqual(detailOf((await linkAudits(u.user.id))[0]), { provider: 'google', via: 'email_auto' });
  } finally {
    config.google.legacyEmailLink = false;
  }
});
