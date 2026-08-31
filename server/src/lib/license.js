'use strict';

/**
 *  صدور مجوز اشتراک — همان توکنی که برنامه امضایش را می‌سنجد.
 *
 *  چرا اصلاً مجوز؟ برنامه باید آفلاین هم بداند اشتراک باز است یا نه.
 *  اگر جواب «باز است» فقط یک پرچم در حافظه‌ی گوشی باشد، هر کسی می‌تواند
 *  عوضش کند. پس سرور یک برگه‌ی امضاشده می‌دهد و برنامه فقط امضا را
 *  می‌سنجد: بدون کلید خصوصی سرور، ساختن چنین برگه‌ای شدنی نیست.
 *
 *  توکن سه بخش دارد — `header.payload.signature` — با ES256 روی منحنی
 *  P-256، کدگذاری base64url. دقیقاً همان چیزی که `License.kt` می‌خواند.
 *
 *  دو قید که مجوز دزدیده‌شده را بی‌ارزش می‌کنند:
 *    • به **دستگاه** بسته است (`dev`)؛ کپی کردنش روی گوشی دیگر کار نمی‌کند.
 *    • عمر کوتاهی دارد (`exp`)، کوتاه‌تر از خود اشتراک. پس برنامه هر چند
 *      روز باید دوباره از سرور بگیرد و لغو اشتراک زود اثر می‌کند. تا آن
 *      وقت هم آفلاین کار می‌کند.
 *
 *  کلید یک بار ساخته می‌شود و در `app_config` می‌ماند. اگر
 *  `LICENSE_PRIVATE_KEY` در محیط باشد همان استفاده می‌شود — برای وقتی که
 *  چند سرور باید یک کلید داشته باشند.
 */

const crypto = require('crypto');
const plans = require('./plans');

const ISSUER = 'tohid-license-server';
const AUDIENCE = 'tohid-shop-app';

/** عمر خود مجوز (نه اشتراک): ده روز */
const TOKEN_TTL_MS = 10 * 24 * 60 * 60 * 1000;

const KEY_PRIVATE = 'license_private_key';
const KEY_PUBLIC = 'license_public_key';

let cached = null;

/**
 *  جفت‌کلید سرور. یک بار ساخته و ذخیره می‌شود.
 *
 *  کلید عمومی هم ذخیره می‌شود تا برای هر درخواست از روی خصوصی حساب نشود.
 */
async function keys() {
  if (cached) return cached;

  const fromEnv = process.env.LICENSE_PRIVATE_KEY;
  if (fromEnv && fromEnv.trim()) {
    const privateKey = crypto.createPrivateKey(fromEnv.trim());
    cached = { privateKey, publicSpki: spkiOf(privateKey) };
    return cached;
  }

  const stored = await plans.getConfig(KEY_PRIVATE, '');
  if (stored) {
    const privateKey = crypto.createPrivateKey(stored);
    const publicSpki = (await plans.getConfig(KEY_PUBLIC, '')) || spkiOf(privateKey);
    cached = { privateKey, publicSpki };
    return cached;
  }

  const pair = crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  });
  const privateKey = crypto.createPrivateKey(pair.privateKey);
  const publicSpki = spkiOf(privateKey);

  await plans.setConfig(KEY_PRIVATE, pair.privateKey);
  await plans.setConfig(KEY_PUBLIC, publicSpki);

  cached = { privateKey, publicSpki };
  return cached;
}

/** کلید عمومی به شکل base64 از SPKI DER — همان چیزی که برنامه می‌خواهد */
function spkiOf(privateKey) {
  return crypto
    .createPublicKey(privateKey)
    .export({ type: 'spki', format: 'der' })
    .toString('base64');
}

async function publicKey() {
  return (await keys()).publicSpki;
}

function b64url(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 *  یک مجوز امضاشده.
 *
 *  @param {object} p
 *  @param {string} p.deviceUid   شناسه‌ی دستگاه — مجوز فقط روی همین کار می‌کند
 *  @param {string} p.accountId   شناسه‌ی حساب
 *  @param {string[]} p.features  قابلیت‌های باز
 *  @param {string[]} p.core      قابلیت‌هایی که همیشه بازند
 *  @param {number} p.subscriptionEndsAt پایان اشتراک (میلی‌ثانیه)
 */
async function issue({
  deviceUid,
  deviceName = '',
  accountId,
  features = [],
  core = [],
  subscriptionEndsAt = 0,
  plan = '',
  planTitle = '',
  at = Date.now(),
}) {
  const { privateKey } = await keys();

  const header = { alg: 'ES256', typ: 'TLIC' };

  // مجوز از خود اشتراک زودتر تمام می‌شود تا لغو اشتراک زود اثر کند —
  // ولی نه دیرتر از پایان اشتراک، وگرنه چند روز مجانی می‌دهد
  const expiresAt = Math.min(at + TOKEN_TTL_MS, subscriptionEndsAt || at + TOKEN_TTL_MS);

  /*
   * نام فیلدها همان چیزی است که برنامه می‌خواند — نه چیز دیگری.
   *
   * تا امروز اینجا `dev` و `acc` نوشته می‌شد، ولی هر دو کلاینت
   * (اندروید و وب) `duid` و `sub` را می‌خوانند. یعنی `payload.duid`
   * همیشه undefined بود و مجوز با `device_mismatch` رد می‌شد:
   * **اشتراک پولی روی هیچ دستگاهی فعال نمی‌شد.**
   *
   * تست‌های کلاینت این را نمی‌گرفتند چون خودشان مجوز می‌ساختند و
   * همان نام‌های درست را می‌گذاشتند؛ هیچ تستی مجوزِ واقعیِ سرور را با
   * چشمِ کلاینت نگاه نمی‌کرد. حالا می‌کند (`license-format.test.js`).
   *
   * `dev` و `acc` هم کنارشان می‌مانند تا اگر ابزاری بیرون از این مخزن
   * آن‌ها را می‌خواند، نشکند.
   */
  const payload = {
    iss: ISSUER,
    aud: AUDIENCE,
    duid: deviceUid,
    sub: accountId,
    dev: deviceUid,
    dev_name: deviceName,
    acc: accountId,
    iat: at,
    nbf: at - 60_000,            // یک دقیقه ارفاق برای ساعت گوشی
    exp: expiresAt,
    sub_ends: subscriptionEndsAt,
    feat: features,
    core,
    plan,
    plan_title: planTitle,
  };

  const signing =
    `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;

  // امضای خام (r||s) نه DER: برنامه همین را می‌خواند
  const signature = crypto.sign(null, Buffer.from(signing, 'utf8'), {
    key: privateKey,
    dsaEncoding: 'ieee-p1363',
  });

  return {
    token: `${signing}.${b64url(signature)}`,
    expiresAt,
    subscriptionEndsAt,
  };
}

module.exports = { issue, publicKey, ISSUER, AUDIENCE, TOKEN_TTL_MS };
