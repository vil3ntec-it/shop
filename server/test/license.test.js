'use strict';
/**
 *  مجوز اشتراک — امضا، بستن به دستگاه، و اینکه بدون اشتراک صادر نشود.
 *
 *  سنجش امضا اینجا با همان قاعده‌ای انجام می‌شود که برنامه‌ی اندروید
 *  دارد: کلید عمومی SPKI، امضای خام ۶۴ بایتی، کدگذاری base64url. اگر
 *  روزی یکی از این سه عوض شود، این آزمون می‌شکند — نه اینکه کاربر سرِ
 *  خرید بفهمد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const h = require('./helpers');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

function decodeUrl(text) {
  return Buffer.from(text.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
}

/** همان کاری که `License.kt` می‌کند */
function verify(token, publicKeySpki) {
  const parts = token.split('.');
  assert.equal(parts.length, 3, 'مجوز باید سه بخش داشته باشد');

  const header = JSON.parse(decodeUrl(parts[0]).toString('utf8'));
  const payload = JSON.parse(decodeUrl(parts[1]).toString('utf8'));
  assert.equal(header.alg, 'ES256');
  assert.equal(header.typ, 'TLIC');

  const signature = decodeUrl(parts[2]);
  assert.equal(signature.length, 64, 'امضا باید خام و ۶۴ بایتی باشد');

  const key = crypto.createPublicKey({
    key: Buffer.from(publicKeySpki, 'base64'),
    format: 'der',
    type: 'spki',
  });
  const ok = crypto.verify(
    null,
    Buffer.from(`${parts[0]}.${parts[1]}`, 'utf8'),
    { key, dsaEncoding: 'ieee-p1363' },
    signature
  );
  return { ok, payload };
}

test('کلید عمومی بدون ورود هم داده می‌شود', async () => {
  const r = await h.get('/api/license/public-key');
  assert.equal(r.status, 200);
  assert.ok(r.body.publicKey && r.body.publicKey.length > 80);
});

test('دکان تازه مجوز امضاشده می‌گیرد و امضا معتبر است', async () => {
  const u = await h.newUser('صاحب مجوز');
  await h.post('/api/shop', { name: 'دکان مجوز' }, { token: u.accessToken });

  const r = await h.post('/api/license/sync', { device: { uid: 'DEVICE-A', name: 'گوشی آزمایشی' } }, { token: u.accessToken });
  assert.equal(r.status, 200);
  assert.ok(r.body.license, 'دکان در دوره‌ی آزمایشی باید مجوز بگیرد');

  const { ok, payload } = verify(r.body.license, r.body.publicKey);
  assert.equal(ok, true, 'امضا باید با کلید عمومی همین سرور بخواند');
  assert.equal(payload.dev, 'DEVICE-A', 'مجوز باید به همین دستگاه بسته باشد');
  assert.equal(payload.iss, 'tohid-license-server');
  assert.equal(payload.aud, 'tohid-shop-app');
  assert.ok(payload.feat.includes('sales'), 'قابلیت‌های اشتراک باید داخل مجوز باشند');
  assert.ok(payload.exp > Date.now(), 'مجوز نباید منقضی صادر شود');
  assert.ok(
    payload.exp <= payload.sub_ends || payload.sub_ends === 0,
    'مجوز نباید از خود اشتراک دیرتر تمام شود'
  );
});

test('مجوز دستگاه دیگر، همان مجوز نیست', async () => {
  const u = await h.newUser('دو دستگاه');
  await h.post('/api/shop', { name: 'دکان دو دستگاه' }, { token: u.accessToken });

  const a = await h.post('/api/license/sync', { device: { uid: 'DEV-1' } }, { token: u.accessToken });
  const b = await h.post('/api/license/sync', { device: { uid: 'DEV-2' } }, { token: u.accessToken });

  assert.equal(verify(a.body.license, a.body.publicKey).payload.dev, 'DEV-1');
  assert.equal(verify(b.body.license, b.body.publicKey).payload.dev, 'DEV-2');
  assert.notEqual(a.body.license, b.body.license);
});

test('دست‌کاری در متن مجوز، امضا را باطل می‌کند', async () => {
  const u = await h.newUser('دست‌کار');
  await h.post('/api/shop', { name: 'دکان دست‌کار' }, { token: u.accessToken });
  const r = await h.post('/api/license/sync', { device: { uid: 'DEV-X' } }, { token: u.accessToken });

  const parts = r.body.license.split('.');
  const payload = JSON.parse(decodeUrl(parts[1]).toString('utf8'));
  payload.sub_ends = Date.now() + 100 * 365 * 24 * 60 * 60 * 1000;   // اشتراک صد ساله!
  const forged = Buffer.from(JSON.stringify(payload), 'utf8').toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

  const { ok } = verify(`${parts[0]}.${forged}.${parts[2]}`, r.body.publicKey);
  assert.equal(ok, false, 'تغییر تاریخ اشتراک باید امضا را بشکند');
});

test('بدون دکان مجوزی صادر نمی‌شود', async () => {
  const u = await h.newUser('بی‌دکان');
  const r = await h.post('/api/license/sync', { device: { uid: 'DEV-N' } }, { token: u.accessToken });
  assert.equal(r.status, 200);
  assert.equal(r.body.license, null);
  assert.equal(r.body.reason, 'no_shop');
});

test('شناسه‌ی دستگاه اجباری است', async () => {
  const u = await h.newUser('بی‌دستگاه');
  await h.post('/api/shop', { name: 'دکان بی‌دستگاه' }, { token: u.accessToken });
  const r = await h.post('/api/license/sync', {}, { token: u.accessToken });
  assert.equal(r.status, 400);
});
