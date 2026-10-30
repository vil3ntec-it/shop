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
function googleToken({ sub, email, verified = true, aud = CLIENT_ID, key = keys.privateKey, expIn = 600, iss = 'https://accounts.google.com' }) {
  const t = Math.floor(Date.now() / 1000);
  const head = b64u({ alg: 'RS256', kid: KID, typ: 'JWT' });
  const body = b64u({
    iss, aud, sub, email, email_verified: verified,
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

/* ================================================================
 *  یافته‌های بازبینیِ امنیتی (۱۴۰۵/۰۷/۲۷) — گوگلِ «ایمیلِ تأییدنشده»
 *
 *  گوگل اجازه می‌دهد حسابی با نشانیِ ایمیلِ **دیگری** ساخته شود بی آن‌که
 *  آن ایمیل تأیید شود (`email_verified=false`). چنین توکنی هیچ چیزی دربارهٔ
 *  مالکیتِ ایمیل ثابت نمی‌کند.
 * ================================================================ */

test('امنیت ۱: گوگلِ تأییدنشده حسابِ تازه نمی‌سازد (پیش‌دستی روی ایمیلِ قربانی)', async () => {
  const email = 'victim-precreate@test.local';
  const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-att-1', email, verified: false }) });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'google_email_unverified');
  assert.match(r.body.error.message, /تأیید/);
  assert.equal(r.body.accessToken, undefined);
  assert.equal(await one('SELECT id FROM users WHERE email=$1', [email]), null, 'هیچ حسابی ساخته نشد');
  assert.equal(await one(`SELECT id FROM user_identities WHERE provider='google' AND subject='g-att-1'`), null);
});

test('امنیت ۱: گوگلِ تأییدنشده حتی با رمزِ درست وصل نمی‌شود و وجودِ حساب/روش‌ها را لو نمی‌دهد', async () => {
  const u = await h.newUser('قربانیِ وصل');
  const tokenOf = (email) => googleToken({ sub: `g-att-${email}`, email, verified: false });

  const bare = await h.post('/api/auth/google', { idToken: tokenOf(u.email) });
  const none = await h.post('/api/auth/google', { idToken: tokenOf('nobody-here@test.local') });
  assert.equal(bare.status, 403, JSON.stringify(bare.body));
  assert.deepEqual(bare.body, none.body, 'پاسخِ حسابِ موجود و ناموجود یکی است');
  assert.equal(bare.body.error.link, undefined, 'روش‌های ورودِ حساب گفته نمی‌شود');

  const withPassword = await h.post('/api/auth/google', { idToken: tokenOf(u.email), password: u.password });
  assert.equal(withPassword.status, 403, JSON.stringify(withPassword.body));
  assert.equal(withPassword.body.error.code, 'google_email_unverified');
  assert.equal((await identities(u.user.id)).length, 0, 'گوگل وصل نشد');
  assert.equal((await linkAudits(u.user.id)).length, 0);
});

test('امنیت ۱: «رمزِ فراموش‌شده» گوگلِ تأییدنشدهٔ قدیمی را جدا می‌کند و سابقه می‌گذارد؛ گوگلِ تأییدشده می‌ماند', async () => {
  const { newId, now } = require('../src/db');
  const email = 'victim-reset@test.local';
  //  حسابی که پیش از این رفع، مهاجم با گوگلِ تأییدنشده برای ایمیلِ قربانی ساخته بود
  const t = now();
  const acc = await one(
    `INSERT INTO users (id, name, email, status, created_at, updated_at)
     VALUES ($1,'مهاجم',$2,'active',$3,$3) RETURNING *`, [newId('usr'), email, t]
  );
  await h.query(
    `INSERT INTO user_identities (id, user_id, provider, subject, email, created_at)
     VALUES ($1,$2,'google','g-att-old',$3,$4)`, [newId('idn'), acc.id, email, t]
  );
  //  و یک گوگلِ قدیمیِ دیگر که بعداً با توکنِ تأییدشده وارد شده (همان صاحبِ ایمیل)
  await h.query(
    `INSERT INTO user_identities (id, user_id, provider, subject, email, created_at)
     VALUES ($1,$2,'google','g-own-old',$3,$4)`, [newId('idn'), acc.id, email, t]
  );
  const own = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-own-old', email }) });
  assert.equal(own.status, 200, JSON.stringify(own.body));

  //  مهاجم تا این‌جا هنوز وارد می‌شود (شناسه از قبل وصل است)
  const before = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-att-old', email, verified: false }) });
  assert.equal(before.status, 200, JSON.stringify(before.body));

  //  صاحبِ واقعیِ ایمیل رمز را بازیابی می‌کند
  const forgot = await h.post('/api/auth/password/forgot', { email });
  assert.ok(forgot.body.devCode);
  const reset = await h.post('/api/auth/password/reset', { email, code: forgot.body.devCode, password: 'N3w!Passw0rd' });
  assert.equal(reset.status, 200, JSON.stringify(reset.body));

  const left = (await identities(acc.id)).map((r) => r.subject).sort();
  assert.deepEqual(left, ['g-own-old'], 'فقط گوگلِ تأییدشده ماند');
  const after = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'g-att-old', email, verified: false }) });
  assert.equal(after.status, 403, 'مهاجم دیگر راهی ندارد');
  assert.equal(after.body.error.code, 'google_email_unverified');

  const log = await one(
    `SELECT * FROM audit_logs WHERE action='auth.identity_unlinked' AND user_id=$1`, [acc.id]
  );
  assert.ok(log, 'جدا شدن در سابقه نشست');
  assert.deepEqual(detailOf(log), { provider: 'google', reason: 'password_reset_unverified', count: 1 });
});

test('امنیت ۲: کدِ وصل برای گوگلِ تأییدنشده چیزی نمی‌فرستد و پاسخش با «حساب نیست» یکی است', async () => {
  const u = await h.newUser('قربانیِ کد');
  const asked = await h.post('/api/auth/google/link-code', {
    idToken: googleToken({ sub: 'g-att-code', email: u.email, verified: false }),
  });
  const nobody = await h.post('/api/auth/google/link-code', {
    idToken: googleToken({ sub: 'g-att-code-2', email: 'nobody-code@test.local', verified: false }),
  });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  assert.equal(asked.body.sent, false, 'کدی نرفت');
  assert.equal(asked.body.devCode, undefined);
  const strip = ({ email, ...rest }) => rest;
  assert.deepEqual(strip(asked.body), strip(nobody.body), 'پاسخ وجودِ حساب را لو نمی‌دهد');
});

test('امنیت ۲: کدِ وصل به‌ازای «ایمیل + IP» سقف دارد', async () => {
  const u = await h.newUser('سقفِ کدِ وصل');
  const idToken = googleToken({ sub: 'g-sub-limit', email: u.email });
  const max = config.rateLimit.linkCodeMax || 3;
  for (let i = 0; i < max; i++) {
    const r = await h.post('/api/auth/google/link-code', { idToken });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.sent, true);
  }
  const over = await h.post('/api/auth/google/link-code', { idToken });
  assert.equal(over.status, 429, JSON.stringify(over.body));
  //  ایمیلِ دیگر از همان IP سطلِ خودش را دارد
  const v = await h.newUser('ایمیلِ دیگر');
  const other = await h.post('/api/auth/google/link-code', { idToken: googleToken({ sub: 'g-sub-limit-2', email: v.email }) });
  assert.equal(other.status, 200, JSON.stringify(other.body));
});

test('AUTH-03: صادرکنندهٔ دیگر (iss) با امضای درست هم رد می‌شود؛ هر دو شکلِ iss گوگل پذیرفته است', async () => {
  for (const iss of ['https://accounts.google.com.evil.test', 'https://evil.test', 'accounts.google.co', '']) {
    const r = await h.post('/api/auth/google', { idToken: googleToken({ sub: 'iss-x', email: 'iss-x@test.local', iss }) });
    assert.equal(r.status, 403, `iss=${iss}`);
    assert.equal(r.body.error.code, 'bad_google_token', `iss=${iss}`);
  }
  const rows = await many(`SELECT id FROM users WHERE email=$1`, ['iss-x@test.local']);
  assert.equal(rows.length, 0, 'توکنِ iss نادرست حسابی نمی‌سازد');
  for (const [i, iss] of ['accounts.google.com', 'https://accounts.google.com'].entries()) {
    const ok = await h.post('/api/auth/google', { idToken: googleToken({ sub: `iss-ok-${i}`, email: `iss-ok-${i}@test.local`, iss }) });
    assert.equal(ok.status, 201, `iss=${iss} ${JSON.stringify(ok.body)}`);
  }
});
