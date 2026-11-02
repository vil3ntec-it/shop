'use strict';
/**
 * ورود با اپل (آیفون گامِ ۶) — همان قاعدهٔ گوگل (AUTH-04، بندِ ۲۹.۲) + nonce ِ یک‌بارمصرفِ سرور.
 *
 * توکنِ اپل با کلیدِ RSAِ خودِ آزمون امضا می‌شود و کلیدِ عمومی از یک سرورِ HTTPِ محلی (همان
 * قالبِ JWKSِ اپل) خوانده می‌شود؛ پس `lib/apple.verifyIdToken`ِ واقعی — امضا، `iss`، `aud`،
 * `exp`، `nonce` — بی هیچ میان‌بری اجرا می‌شود. بی شبکه.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { generateKeyPairSync, createSign, createHash } = require('node:crypto');
const h = require('./helpers');
const config = require('../src/config');
const apple = require('../src/lib/apple');
const { many } = require('../src/db');

const BUNDLE = 'ir.vil3ntec.tohid';
const KID = 'apple-kid-1';
const keys = generateKeyPairSync('rsa', { modulusLength: 2048 });
const stranger = generateKeyPairSync('rsa', { modulusLength: 2048 });
let jwks = null;
let jwksHits = 0;

test.before(async () => {
  await h.start();
  const jwk = { ...keys.publicKey.export({ format: 'jwk' }), kid: KID, alg: 'RS256', use: 'sig' };
  jwks = http.createServer((req, res) => {
    jwksHits += 1;
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ keys: [jwk] }));
  });
  await new Promise((r) => jwks.listen(0, '127.0.0.1', r));
  config.apple.keysUrl = `http://127.0.0.1:${jwks.address().port}/auth/keys`;
});
test.after(async () => {
  config.apple.clientIds = [];
  jwks?.close();
  await h.stop();
});

const b64u = (x) => Buffer.from(typeof x === 'string' ? x : JSON.stringify(x)).toString('base64url');
const sha = (s) => createHash('sha256').update(s).digest('hex');

function appleToken({ sub, email, nonce, verified = 'true', aud = BUNDLE, key = keys.privateKey, expIn = 600, iss = 'https://appleid.apple.com', alg = 'RS256', kid = KID, extra = {} }) {
  const t = Math.floor(Date.now() / 1000);
  const head = b64u({ alg, kid });
  const body = b64u({
    iss, aud, sub, iat: t, exp: t + expIn, nonce: nonce === undefined ? undefined : sha(nonce), nonce_supported: true,
    ...(email ? { email, email_verified: verified } : {}), ...extra,
  });
  const s = createSign('RSA-SHA256');
  s.update(`${head}.${body}`);
  return `${head}.${body}.${s.sign(key).toString('base64url')}`;
}

async function freshNonce() {
  const r = await h.post('/api/auth/apple/nonce', {});
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.expiresIn, 600);
  return r.body.nonce;
}

const identities = (userId) => many(`SELECT * FROM user_identities WHERE user_id=$1 AND provider='apple'`, [userId]);

test('خاموش بی APPLE_CLIENT_IDS: /config ⇐ appleLogin=false، nonce و ورود ⇐ ۴۰۳ apple_not_configured', async () => {
  config.apple.clientIds = [];
  const cfg = await h.get('/api/config');
  assert.equal(cfg.body.appleLogin, false);
  const n = await h.post('/api/auth/apple/nonce', {});
  assert.equal(n.status, 403);
  assert.equal(n.body.error.code, 'apple_not_configured');
  const r = await h.post('/api/auth/apple', { idToken: 'a.b.c', nonce: 'x' });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'apple_not_configured');
  config.apple.clientIds = [BUNDLE];
  assert.equal((await h.get('/api/config')).body.appleLogin, true);
});

test('حسابِ تازه با اپل (نام از بدنه) و nonce ِ یک‌بارمصرف: همان توکن + nonce بارِ دوم ⇐ ۴۰۱ apple_nonce_used', async () => {
  config.apple.clientIds = [BUNDLE];
  const nonce = await freshNonce();
  const idToken = appleToken({ sub: 'a-sub-1', email: 'Relay1@PrivateRelay.AppleID.com', nonce, extra: { is_private_email: 'true' } });
  const r = await h.post('/api/auth/apple', { idToken, nonce, name: '  کاربرِ اپل\u0007 ' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.created, true);
  assert.ok(r.body.accessToken);
  assert.equal(r.body.user.email, 'relay1@privaterelay.appleid.com');
  assert.equal(r.body.user.name, 'کاربرِ اپل');
  const ids = await identities(r.body.user.id);
  assert.equal(ids.length, 1);
  assert.equal(ids[0].subject, 'a-sub-1');

  const again = await h.post('/api/auth/apple', { idToken, nonce });
  assert.equal(again.status, 401, JSON.stringify(again.body));
  assert.equal(again.body.error.code, 'apple_nonce_used');
  assert.equal(again.body.accessToken, undefined);

  //  با nonce ِ تازه همان شناسه همان حساب است (نه حسابِ دوم)
  const n2 = await freshNonce();
  const back = await h.post('/api/auth/apple', { idToken: appleToken({ sub: 'a-sub-1', email: 'relay1@privaterelay.appleid.com', nonce: n2 }), nonce: n2 });
  assert.equal(back.status, 200, JSON.stringify(back.body));
  assert.equal(back.body.user.id, r.body.user.id);
});

test('AUTH-04 برای اپل: ایمیلِ دارای حساب ⇒ ۴۰۹ apple_link_required (nonce مصرف نمی‌شود)؛ وصل فقط با کدِ ایمیلِ حساب', async () => {
  config.apple.clientIds = [BUNDLE];
  const u = await h.newUser('صاحبِ رمز');
  const nonce = await freshNonce();
  const idToken = appleToken({ sub: 'a-sub-2', email: u.email, nonce });

  const r = await h.post('/api/auth/apple', { idToken, nonce });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'apple_link_required');
  assert.match(r.body.error.message, /اپل/);
  assert.deepEqual(r.body.error.link.methods, ['password', 'email_code']);
  assert.equal(r.body.accessToken, undefined);
  assert.equal((await identities(u.user.id)).length, 0);

  const wrong = await h.post('/api/auth/apple', { idToken, nonce, password: 'Wrong!pass1' });
  assert.equal(wrong.status, 401);
  assert.equal(wrong.body.error.code, 'bad_credentials');

  const asked = await h.post('/api/auth/apple/link-code', { idToken, nonce });
  assert.equal(asked.status, 200, JSON.stringify(asked.body));
  assert.equal(asked.body.sent, true);
  assert.ok(asked.body.devCode);

  const ok = await h.post('/api/auth/apple', { idToken, nonce, code: asked.body.devCode });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.user.id, u.user.id);
  assert.equal((await identities(u.user.id)).length, 1);
  const audits = await many(`SELECT * FROM audit_logs WHERE action='auth.identity_linked' AND user_id=$1`, [u.user.id]);
  assert.equal(audits.length, 1);
  const detail = typeof audits[0].detail === 'string' ? JSON.parse(audits[0].detail) : audits[0].detail;
  assert.deepEqual(detail, { provider: 'apple', via: 'email_code' });

  const replay = await h.post('/api/auth/apple', { idToken, nonce, code: asked.body.devCode });
  assert.equal(replay.status, 401);
  assert.equal(replay.body.error.code, 'apple_nonce_used');
});

test('ایمیلِ تأییدنشدهٔ اپل نه حساب می‌سازد نه کدِ وصل می‌گیرد', async () => {
  config.apple.clientIds = [BUNDLE];
  const u = await h.newUser('قربانی');
  const nonce = await freshNonce();
  const r = await h.post('/api/auth/apple', { idToken: appleToken({ sub: 'a-sub-3', email: u.email, nonce, verified: 'false' }), nonce });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'apple_email_unverified');
  const fresh = await h.post('/api/auth/apple', { idToken: appleToken({ sub: 'a-sub-3b', email: 'nobody-apple@test.local', nonce, verified: false }), nonce });
  assert.equal(fresh.status, 403);
  const code = await h.post('/api/auth/apple/link-code', { idToken: appleToken({ sub: 'a-sub-3', email: u.email, nonce, verified: 'false' }), nonce });
  assert.equal(code.status, 200);
  assert.equal(code.body.sent, false);
  assert.equal((await many(`SELECT 1 FROM users WHERE email='nobody-apple@test.local'`)).length, 0);
});

test('توکن/nonce ِ نادرست: nonce ِ دیگر، nonce ِ ساختگی، کهنه، امضای بیگانه، aud/iss/alg، منقضی', async () => {
  config.apple.clientIds = [BUNDLE];
  const nonce = await freshNonce();
  const other = await freshNonce();
  const cases = [
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce: other }), nonce }, 403, 'bad_apple_nonce'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce }) }, 400, 'bad_apple_nonce'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce: `${nonce.slice(0, -2)}AA` }), nonce: `${nonce.slice(0, -2)}AA` }, 403, 'bad_apple_nonce'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce: 'v1.9999999999.abc.def' }), nonce: 'v1.9999999999.abc.def' }, 403, 'bad_apple_nonce'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce, key: stranger.privateKey }), nonce }, 403, 'bad_apple_token'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce, aud: 'com.other.app' }), nonce }, 403, 'bad_audience'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce, iss: 'https://accounts.google.com' }), nonce }, 403, 'bad_apple_token'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce, alg: 'none' }), nonce }, 400, 'bad_apple_token'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce, expIn: -3600 }), nonce }, 403, 'apple_token_expired'],
    [{ idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce: undefined }), nonce }, 403, 'bad_apple_nonce'],
    [{ idToken: 'not-a-jwt', nonce }, 400, 'bad_apple_token'],
  ];
  for (const [body, status, code] of cases) {
    const r = await h.post('/api/auth/apple', body);
    assert.equal(r.status, status, `${code}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, code);
    assert.equal(r.body.accessToken, undefined);
  }
  //  nonce ِ کهنه (مهلتِ ده دقیقه گذشته) — امضای درستِ همین سرور
  const stale = apple.issueNonce(Date.now() - 11 * 60 * 1000).nonce;
  const old = await h.post('/api/auth/apple', { idToken: appleToken({ sub: 's', email: 'e1@test.local', nonce: stale }), nonce: stale });
  assert.equal(old.status, 403);
  assert.equal(old.body.error.code, 'apple_nonce_expired');
  assert.equal((await many(`SELECT 1 FROM users WHERE email='e1@test.local'`)).length, 0, 'هیچ حسابی ساخته نشد');
});

test('کلیدِ تازهٔ اپل (kid ِ ناشناخته) کشِ JWKS را یک بار تازه می‌کند', async () => {
  config.apple.clientIds = [BUNDLE];
  await apple.keys();
  const before = jwksHits;
  const nonce = await freshNonce();
  const r = await h.post('/api/auth/apple', { idToken: appleToken({ sub: 's2', email: 'e2@test.local', nonce, kid: 'rotated' }), nonce });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'bad_apple_token');
  assert.equal(jwksHits, before + 1, 'یک بار تازه شد، نه بیشتر');
});
