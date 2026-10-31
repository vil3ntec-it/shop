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
/** خطای «کلیدِ محیط با کلیدِ ثبت‌شده نمی‌خواند» از `checkKeyContinuity` — تا روشن شدنِ دوباره */
let blocked = null;
let loading = null;

/** چرخاندنِ عمدیِ کلید (SECURITY-AUDIT-fa.md بخشِ I) — فقط با این متغیر */
const allowNewKey = () => /^(1|true|yes|on)$/i.test(String(process.env.LICENSE_ALLOW_NEW_KEY || '').trim());

/**
 *  کلیدِ `LICENSE_PRIVATE_KEY`، یا '' — PEM ِ یک‌خطی با `\n` ِ نوشتاری (OWNER-ACTIONS-fa.md بخشِ ۳، گامِ ۴)
 *  هم پذیرفته است؛ PEM هرگز خودش «\» ندارد.
 */
function envPem() {
  const raw = String(process.env.LICENSE_PRIVATE_KEY || '').trim();
  return raw.includes('\\n') ? raw.replace(/\\n/g, '\n') : raw;
}

function keyError(code, message) {
  const err = new Error(message);
  err.code = code;
  err.status = 503;
  err.expose = true;
  return err;
}

/**
 *  جفت‌کلید سرور. یک بار ساخته و ذخیره می‌شود.
 *
 *  کلید عمومی هم ذخیره می‌شود تا برای هر درخواست از روی خصوصی حساب نشود.
 *
 *  ⛔ SUB-04 / F0-28: کلیدِ **تازه** فقط روی نصبی ساخته می‌شود که هیچ کلیدِ عمومیِ ثبت‌شده‌ای ندارد.
 *  برنامه‌ها کلیدِ عمومی را یک بار می‌گیرند و نگه می‌دارند؛ تا ۲.۱۴.۴ پاک شدنِ ردیفِ خصوصی (گامِ ۶ ِ
 *  OWNER-ACTIONS-fa.md بخشِ ۳) با گم شدنِ `LICENSE_PRIVATE_KEY` بی‌صدا کلیدِ تازه می‌ساخت و مجوزِ همهٔ نصب‌ها
 *  رد می‌شد. حالا صدور با `license_key_missing` بسته می‌ماند (نصب‌ها تا پایانِ مجوزِ ده‌روزه کار می‌کنند)
 *  مگر `LICENSE_ALLOW_NEW_KEY=1`.
 */
async function keys() {
  if (blocked) throw blocked;
  if (cached) return cached;
  //  چند درخواستِ هم‌زمان روی نصبِ تازه یک بار می‌سازند، نه هر کدام کلیدِ خودش (آخرین روی بقیه می‌نشست)
  if (!loading) loading = loadKeys().finally(() => { loading = null; });
  return loading;
}

async function loadKeys() {
  const fromEnv = envPem();
  if (fromEnv) {
    const privateKey = crypto.createPrivateKey(fromEnv);
    cached = { privateKey, publicSpki: spkiOf(privateKey) };
    return cached;
  }

  const useStored = async (pem) => {
    const privateKey = crypto.createPrivateKey(pem);
    const publicSpki = (await plans.getConfig(KEY_PUBLIC, '')) || spkiOf(privateKey);
    cached = { privateKey, publicSpki };
    return cached;
  };
  const stored = await plans.getConfig(KEY_PRIVATE, '');
  if (stored) return useStored(stored);

  if (!allowNewKey() && await plans.getConfig(KEY_PUBLIC, '')) {
    //  ساختِ هم‌زمان روی نصبِ تازه: درخواستِ دیگری همین حالا ساخته است (خصوصی پیش از عمومی نوشته می‌شود)
    const raced = await plans.getConfig(KEY_PRIVATE, '');
    if (raced) return useStored(raced);
    console.error('[license] ⛔ کلیدِ خصوصیِ مجوز نیست (نه LICENSE_PRIVATE_KEY، نه app_config) ولی کلیدِ عمومی ثبت شده است؛ '
      + 'کلیدِ تازه ساخته نمی‌شود چون مجوزِ همهٔ نصب‌ها را رد می‌کند. کلید را در LICENSE_PRIVATE_KEY بگذارید '
      + '(چرخاندنِ عمدی: LICENSE_ALLOW_NEW_KEY=1).');
    throw keyError('license_key_missing', 'کلیدِ امضای مجوز روی سرور پیدا نشد؛ صدورِ مجوز تا رسیدگیِ مدیر بسته است');
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

/**
 *  سنجشِ روشن شدنِ سرور (SUB-04 / F0-28): کلیدِ `LICENSE_PRIVATE_KEY` همان کلیدِ عمومیِ ثبت‌شده است؟
 *
 *  • متغیر نیست ⇒ کاری نیست (`keys()` خودش جلوی کلیدِ تازه را می‌گیرد).
 *  • کلیدِ عمومی ثبت نشده ⇒ ثبت می‌شود (فقط عمومی؛ خصوصیِ محیط هرگز در `app_config` نمی‌رود)، تا اگر روزی
 *    متغیر گم شد، `keys()` کلیدِ تازه نسازد.
 *  • ناهمخوان ⇒ صدورِ مجوز تا روشن شدنِ دوباره بسته (`license_key_mismatch`)؛ با `LICENSE_ALLOW_NEW_KEY=1`
 *    کلیدِ عمومیِ ثبت‌شده با کلیدِ تازه جایگزین می‌شود (چرخاندنِ عمدی).
 *  فقط `createApp` (با دیتابیس) آن را صدا می‌زند؛ مسیرِ محیطِ `keys()` همچنان بی دیتابیس است (`license-runtime`).
 *  @returns {Promise<{ok:true}|{ok:false, code:string}>}
 */
async function checkKeyContinuity() {
  blocked = null;
  const fromEnv = envPem();
  if (!fromEnv) return { ok: true };
  const envSpki = spkiOf(crypto.createPrivateKey(fromEnv));
  const stored = await plans.getConfig(KEY_PUBLIC, '');
  if (!stored) {
    await plans.setConfig(KEY_PUBLIC, envSpki);
    return { ok: true };
  }
  if (stored === envSpki) return { ok: true };
  if (allowNewKey()) {
    console.warn('[license] ⚠️ LICENSE_ALLOW_NEW_KEY: کلیدِ عمومیِ ثبت‌شده با کلیدِ LICENSE_PRIVATE_KEY جایگزین شد؛ مجوزِ نصب‌های پیشین دیگر پذیرفته نمی‌شود');
    await plans.setConfig(KEY_PUBLIC, envSpki);
    cached = null;
    return { ok: true };
  }
  console.error('[license] ⛔ LICENSE_PRIVATE_KEY با کلیدِ عمومیِ ثبت‌شده نمی‌خواند؛ صدورِ مجوز بسته شد. '
    + 'همان کلیدِ پیشین را بگذارید یا متغیر را بردارید (چرخاندنِ عمدی: LICENSE_ALLOW_NEW_KEY=1).');
  blocked = keyError('license_key_mismatch', 'کلیدِ امضای مجوزِ سرور با کلیدِ ثبت‌شده نمی‌خواند؛ صدورِ مجوز تا رسیدگیِ مدیر بسته است');
  cached = null;
  return { ok: false, code: blocked.code };
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
  const spki = await publicKey();
  return crypto.createHash('sha256').update(Buffer.from(spki, 'base64')).digest('hex').slice(0, 16);
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
  issue, publicKey, keyId, signBytes, verifyBytes, checkKeyContinuity,
  ISSUER, AUDIENCE, AUDIENCE_PUMP, TOKEN_TTL_MS,
};
