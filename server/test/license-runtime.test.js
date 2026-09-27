'use strict';
/**
 * ⛔ مجوز زیرِ **همان Nodeی** امضا شود که سرور واقعاً رویش می‌دود.
 *
 * مرکز فرمان این سرور را با Nodeِ خودِ Electron بالا می‌آورد
 * (`ELECTRON_RUN_AS_NODE`) و آن‌جا کتابخانهٔ رمز BoringSSL است، نه OpenSSL.
 * BoringSSL برای کلیدِ EC «درهمسازِ پیش‌فرض» ندارد، پس `crypto.sign(null, …)`
 * با `ERR_OSSL_EVP_NO_DEFAULT_DIGEST` می‌افتاد و **هر** مجوزی ۵۰۰ می‌داد —
 * آزمایشی، VIP، دائمی، و مجوزِ دکان — در حالی که همهٔ آزمون‌ها روی Nodeِ
 * معمولی سبز بودند (۱۴۰۵/۰۷/۱۵، کدِ پیگیریِ صاحبِ سامانه: efkkbwbrxrq).
 *
 * این فایل بی دیتابیس می‌دود (کلید از `LICENSE_PRIVATE_KEY`)، پس زیرِ
 * Electron هم ارزان است؛ کارِ `electron`ِ CI کلِ آزمون‌ها را همان‌جا می‌دواند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const pair = crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1',
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' },
});
process.env.LICENSE_PRIVATE_KEY = pair.privateKey;
const license = require('../src/lib/license');

test('هیچ امضا یا سنجشی با درهمسازِ «پیش‌فرض» (null) نیست', () => {
  const src = path.join(__dirname, '..', 'src');
  const files = [];
  const walk = (d) => fs.readdirSync(d, { withFileTypes: true }).forEach((e) => {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p); else if (p.endsWith('.js')) files.push(p);
  });
  walk(src);
  for (const f of files) {
    const code = fs.readFileSync(f, 'utf8').split('\n')
      .filter((l) => !l.trim().startsWith('//') && !l.trim().startsWith('*')).join('\n');
    assert.doesNotMatch(code, /crypto\.(sign|verify)\(\s*null/, `${path.relative(src, f)}: sign/verify(null, …) زیرِ BoringSSL می‌افتد`);
  }
});

test(`مجوزِ پمپ روی همین Node امضا می‌شود و با ES256 سنجیده می‌شود (${process.versions.electron ? 'Electron ' + process.versions.electron : 'Node ' + process.versions.node})`, async () => {
  const issued = await license.issue({
    deviceUid: 'dev-1', accountId: 'stn_1', features: ['kar'], core: ['ledger'],
    subscriptionEndsAt: Date.now() + 365 * 86400000, activeUntil: Date.now() + 365 * 86400000,
    plan: 'vip', planTitle: 'vip', audience: license.AUDIENCE_PUMP, tenantId: 'stn_1',
  });
  assert.ok(issued && issued.token, 'مجوز صادر نشد');
  const [h, p, s] = issued.token.split('.');
  const pub = crypto.createPublicKey({ key: Buffer.from(await license.publicKey(), 'base64'), format: 'der', type: 'spki' });
  const ok = crypto.verify('sha256', Buffer.from(`${h}.${p}`, 'utf8'),
    { key: pub, dsaEncoding: 'ieee-p1363' }, Buffer.from(s, 'base64url'));
  assert.equal(ok, true, 'امضا با ES256 (SHA-256) نمی‌خواند');
  const payload = JSON.parse(Buffer.from(p, 'base64url').toString('utf8'));
  assert.equal(payload.aud, license.AUDIENCE_PUMP);
  assert.equal(payload.duid, 'dev-1');
});
