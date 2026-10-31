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
 *
 *  ⛔ SEC-ART-05 / F0-28 — جای کلیدِ خصوصی (`config.license`):
 *    • `LICENSE_KEY_MODE=env` (حالتِ امن): کلید **فقط** از `LICENSE_PRIVATE_KEY_FILE` (فایلِ ‎0600‎) یا
 *      `LICENSE_PRIVATE_KEY`. نه از دیتابیس خوانده می‌شود، نه ساخته؛ نبودنش یعنی سرور بالا نمی‌آید
 *      (`preflight`) — هرگز برگشت به کلیدِ ذخیره در `app_config` (که در هر پشتیبان هست).
 *    • پیش‌فرض (`legacy`، برای سرورهای نصب‌شده): محیط/فایل ⇒ `app_config` ⇒ ساختن. ولی اگر
 *      `license_public_key` هست و خصوصی نیست (یعنی کلیدی بوده و پاک شده)، **کلیدِ تازه ساخته
 *      نمی‌شود**: کلیدِ تازه یعنی مجوزِ همهٔ نصب‌ها بی‌صدا رد شود. خطا، نه ساختن.
 *    • `LICENSE_REVOKED_KIDS`: `kid`های لورفته. سرور با کلیدِ باطل‌شده امضا نمی‌کند و بالا نمی‌آید —
 *      حتی اگر پشتیبانِ قدیم یا `.env` ِ قدیم آن را برگرداند.
 *  ابزارِ جابه‌جایی و چرخش: `scripts/license-key.js`. مقدارِ کلید هرگز در لاگ یا خطا نمی‌آید.
 */

const crypto = require('crypto');
const fs = require('fs');
const config = require('../config');
const plans = require('./plans');

const ISSUER = 'tohid-license-server';
const AUDIENCE = 'tohid-shop-app';
/*
 *  شنوندهٔ مجوزِ بخشِ پمپ جداست.
 *
 *  هر دو بخش یک کلید خصوصی دارند — کلید مالِ سرور است نه مالِ برنامه.
 *  ولی اگر `aud` هم یکی بود، مجوزی که برای دکانی صادر شده روی برنامهٔ
 *  پمپ هم می‌نشست: امضا درست، دستگاه درست، و برنامه هیچ دلیلی برای رد
 *  کردنش نداشت. یک اشتراکِ دکان، یک اشتراکِ پمپ را هم مجانی می‌کرد.
 */
const AUDIENCE_PUMP = 'tohid-pump-app';

/** عمر خود مجوز (نه اشتراک): ده روز */
const TOKEN_TTL_MS = 10 * 24 * 60 * 60 * 1000;

const KEY_PRIVATE = 'license_private_key';
const KEY_PUBLIC = 'license_public_key';

let cached = null;

/** خطای کلید — فقط کد و پیامِ بی‌راز؛ مقدارِ کلید هرگز در آن نیست. */
class LicenseKeyError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
    this.status = 500;
    this.expose = false;
  }
}

/** کلیدِ PEM از محیط یا فایل؛ ‎{ pem, source }‎ یا ‎null‎. */
function externalKey() {
  const lic = config.license;
  if (lic.file) {
    let st;
    try { st = fs.statSync(lic.file); } catch {
      throw new LicenseKeyError('license_key_file_missing', 'LICENSE_PRIVATE_KEY_FILE پیدا نشد');
    }
    //  ‎0600‎: کلیدِ امضای همهٔ مجوزها را هر کاربرِ دیگرِ همان کامپیوتر نخواند (ویندوز ACL دارد، نه mode)
    if (process.platform !== 'win32' && (st.mode & 0o077) && lic.mode === 'env') {
      throw new LicenseKeyError('license_key_file_mode', 'LICENSE_PRIVATE_KEY_FILE را فقط صاحبش بخواند (chmod 600)');
    }
    return { pem: fs.readFileSync(lic.file, 'utf8'), source: 'file' };
  }
  const fromEnv = process.env.LICENSE_PRIVATE_KEY;
  if (fromEnv && fromEnv.trim()) return { pem: fromEnv.trim().replace(/\\n/g, '\n'), source: 'env' };
  return null;
}

function parsePrivate(pem, source) {
  try {
    const privateKey = crypto.createPrivateKey(pem);
    if (privateKey.asymmetricKeyType !== 'ec') throw new Error('not ec');
    return privateKey;
  } catch {
    throw new LicenseKeyError('license_key_invalid', `کلیدِ مجوز (${source}) خوانا نیست یا EC نیست`);
  }
}

function kidOf(publicSpki) {
  return crypto.createHash('sha256').update(Buffer.from(publicSpki, 'base64')).digest('hex').slice(0, 16);
}

function assertNotRevoked(publicSpki) {
  const kid = kidOf(publicSpki);
  if (config.license.revokedKids.includes(kid)) {
    throw new LicenseKeyError('license_key_revoked', `کلیدِ مجوز (kid ${kid}) در LICENSE_REVOKED_KIDS است — با کلیدِ باطل‌شده امضا نمی‌شود`);
  }
}

/**
 *  جفت‌کلید سرور. یک بار ساخته و ذخیره می‌شود.
 *
 *  کلید عمومی هم ذخیره می‌شود تا برای هر درخواست از روی خصوصی حساب نشود.
 */
async function keys() {
  if (cached) return cached;

  const ext = externalKey();
  if (ext) {
    const privateKey = parsePrivate(ext.pem, ext.source);
    const publicSpki = spkiOf(privateKey);
    assertNotRevoked(publicSpki);
    cached = { privateKey, publicSpki, source: ext.source };
    return cached;
  }
  if (config.license.mode === 'env') {
    //  ⛔ هرگز برگشت به app_config یا ساختنِ کلید: حالتِ env یعنی کلید فقط بیرون از دیتابیس است
    throw new LicenseKeyError('license_key_missing', 'LICENSE_KEY_MODE=env ولی نه LICENSE_PRIVATE_KEY_FILE هست نه LICENSE_PRIVATE_KEY');
  }

  const stored = await plans.getConfig(KEY_PRIVATE, '');
  if (stored) {
    const privateKey = parsePrivate(stored, 'app_config');
    const publicSpki = (await plans.getConfig(KEY_PUBLIC, '')) || spkiOf(privateKey);
    assertNotRevoked(publicSpki);
    cached = { privateKey, publicSpki, source: 'db' };
    return cached;
  }

  //  کلیدی بوده (عمومی‌اش مانده) و خصوصی‌اش پاک شده: کلیدِ تازه مجوزِ همهٔ نصب‌ها را رد می‌کند — نه
  if (await plans.getConfig(KEY_PUBLIC, '')) {
    throw new LicenseKeyError('license_key_lost', 'کلیدِ خصوصیِ مجوز نیست ولی کلیدِ عمومی‌اش هست — کلیدِ تازه ساخته نمی‌شود؛ LICENSE_PRIVATE_KEY_FILE را بدهید');
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

  cached = { privateKey, publicSpki, source: 'db' };
  return cached;
}

/**
 *  سنجشِ پیش از listen (‎index.js‎): کلید هست، باطل نیست و حالتِ env واقعاً بیرون از دیتابیس است.
 *  خطا ⇒ سرور بالا نمی‌آید. خروجی فقط شکلِ کار است، هرگز کلید:
 *  ‎{ mode, source, kid, dbCopy, dbMatches }‎ — `dbCopy` یعنی نسخه‌ای از خصوصی هنوز در `app_config`
 *  (و پس در پشتیبان‌ها) است و باید با ‎scripts/license-key.js purge-db‎ برداشته شود.
 */
async function preflight() {
  const k = await keys();
  const kid = kidOf(k.publicSpki);
  let dbCopy = false;
  let dbMatches = null;
  if (k.source !== 'db') {
    const stored = await plans.getConfig(KEY_PRIVATE, '');
    dbCopy = !!stored;
    if (stored) {
      try { dbMatches = spkiOf(crypto.createPrivateKey(stored)) === k.publicSpki; } catch { dbMatches = false; }
    }
  } else {
    dbCopy = true;
    dbMatches = true;
  }
  return { mode: config.license.mode, source: k.source, kid, dbCopy, dbMatches };
}

/** فقط برای آزمون: کلیدِ در حافظه را فراموش کن */
function _reset() { cached = null; }

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

/**
 * شناسهٔ کلید (`kid`) — ۱۶ نویسهٔ اولِ SHA-256ِ خودِ کلیدِ عمومی.
 *
 * ⛔ تا ۲.۹.۰ مجوز هیچ `kid`ی نداشت، پس برنامه فقط یک کلید را می‌شناخت و
 * عوض کردنِ کلید (مثلاً پس از لو رفتنش) یعنی همهٔ نصب‌ها یک‌شبه قفل
 * می‌شدند. با `kid` برنامه می‌تواند چند کلید را بشناسد و کلیدِ تازه پیش از
 * جابه‌جایی در برنامه بنشیند. از خودِ کلید ساخته می‌شود، پس هیچ تنظیمی
 * نمی‌خواهد و دو سرور با یک کلید یک `kid` دارند.
 */
async function keyId() {
  return kidOf(await publicKey());
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
  servicesEndsAt = 0,
  activeUntil = 0,
  plan = '',
  planTitle = '',
  audience = AUDIENCE,
  tenantId = '',
  at = Date.now(),
}) {
  const { privateKey } = await keys();

  const header = { alg: 'ES256', typ: 'TLIC', kid: await keyId() };

  // مجوز از خود اشتراک زودتر تمام می‌شود تا لغو اشتراک زود اثر کند —
  // ولی نه دیرتر از پایان اشتراک، وگرنه چند روز مجانی می‌دهد.
  //  ⚠️ `activeUntil` پایانِ **مهلتِ** اشتراک است (`graceEndsAt`): تا ۲.۹.۰
  //  سقف همان `ends_at`ِ خام بود، پس در روزهای مهلت سرور «فعال» می‌گفت و
  //  مجوزی می‌داد که همان لحظه منقضی بود.
  const cap = Number(activeUntil) || Number(subscriptionEndsAt) || at + TOKEN_TTL_MS;
  const expiresAt = Math.min(at + TOKEN_TTL_MS, cap);
  //  مجوزی که پیش از صدور منقضی است امضا نمی‌شود
  if (expiresAt <= at) return null;

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
    aud: audience,
    duid: deviceUid,
    sub: accountId,
    dev: deviceUid,
    dev_name: deviceName,
    acc: accountId,
    iat: at,
    nbf: at - 60_000,            // یک دقیقه ارفاق برای ساعت گوشی
    exp: expiresAt,
    sub_ends: subscriptionEndsAt,
    //  دائمی: پایانِ خدماتِ سرور (۰ ⇒ محدودیتی نیست) — ‎lib/pump-services.js‎
    svc_ends: Number(servicesEndsAt) || 0,
    feat: features,
    core,
    plan,
    plan_title: planTitle,
    /*
     *  به کدام پمپ/دکان بسته است.
     *
     *  ⚠️ بی این، مجوزی که برای پمپِ الف صادر شده روی پمپِ ب هم
     *  می‌نشست اگر همان دستگاه بود: امضا درست، `duid` درست، و برنامه
     *  هیچ دلیلی برای ردش نداشت. یعنی یک اشتراک، چند پمپ را باز
     *  می‌کرد — روی یک کامپیوتر که چند پمپ را اداره می‌کند، دقیقاً
     *  همان کاری که کسی برای دور زدن می‌کند.
     */
    stn: tenantId,
  };

  const signing =
    `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;

  // امضای خام (r||s) نه DER: برنامه همین را می‌خواند
  //
  //  ⛔ **`'sha256'` صریح، نه `null`** (۱۴۰۵/۰۷/۱۵). مرکز فرمان همین سرور را
  //  با Nodeِ خودِ Electron می‌دواند (`ELECTRON_RUN_AS_NODE`) و آن‌جا
  //  کتابخانهٔ رمز BoringSSL است، نه OpenSSL. BoringSSL برای کلیدِ EC هیچ
  //  «درهمسازِ پیش‌فرضی» ندارد: `sign(null, …)` ⇒ `ERR_OSSL_EVP_NO_DEFAULT_DIGEST`
  //  ⇒ هر مجوزی (آزمایشی، VIP، دائمی، دکان) ۵۰۰ِ «خطای داخلی سرور» می‌داد و
  //  هیچ اشتراکی به هیچ برنامه‌ای نمی‌رسید — در حالی که پنل «فعال» می‌گفت.
  //  روی Nodeِ معمولی (OpenSSL) پیش‌فرض همان SHA-256 است، پس امضا مو‌به‌مو
  //  همان است و ES256 هم همین را می‌خواهد. آزمونِ `license-runtime.test.js`
  //  و کارِ `electron`ِ CI همین را زیرِ خودِ Electron می‌سنجند.
  const signature = crypto.sign('sha256', Buffer.from(signing, 'utf8'), {
    key: privateKey,
    dsaEncoding: 'ieee-p1363',
  });

  return {
    token: `${signing}.${b64url(signature)}`,
    expiresAt,
    subscriptionEndsAt,
  };
}

/**
 *  امضای خامِ چند بایت با همان کلیدِ مجوز — برای کدِ اشتراکِ آفلاین
 *  (`lib/offline-codes.js`). ⚠️ `'sha256'` صریح، به همان دلیلِ بالا (Electron).
 */
async function signBytes(buf) {
  const { privateKey } = await keys();
  return crypto.sign('sha256', Buffer.from(buf), { key: privateKey, dsaEncoding: 'ieee-p1363' });
}

/** سنجشِ همان امضا با کلیدِ عمومیِ همین سرور. هیچ‌وقت خطا نمی‌دهد. */
async function verifyBytes(buf, sig) {
  try {
    const { privateKey } = await keys();
    return crypto.verify('sha256', Buffer.from(buf), {
      key: crypto.createPublicKey(privateKey), dsaEncoding: 'ieee-p1363',
    }, Buffer.from(sig));
  } catch { return false; }
}

module.exports = {
  issue, publicKey, keyId, signBytes, verifyBytes, preflight, kidOf, spkiOf,
  ISSUER, AUDIENCE, AUDIENCE_PUMP, TOKEN_TTL_MS,
  KEY_PRIVATE, KEY_PUBLIC, LicenseKeyError, _reset,
};
