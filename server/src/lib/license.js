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
 *  کلید از کجا (به همین ترتیب): `LICENSE_PRIVATE_KEY` (PEM، یک‌خطیِ `\n` ِ .env هم)، `LICENSE_PRIVATE_KEY_FILE`
 *  (نشانیِ فایلِ PEM)، ردیفِ `app_config` (رفتارِ پیشین، سازگاری). ⛔ کلیدِ تازه فقط در `NODE_ENV=development|test`
 *  ساخته می‌شود (O-18): در production (و بی NODE_ENV) کلیدِ تازه یعنی مجوزِ همهٔ نصب‌ها و هر کدِ آفلاین بی‌صدا
 *  باطل؛ پس ۵۰۳ ِ `license_key_missing` و یک خطِ خطا در راه‌اندازی. کلیدِ پیکربندی‌شدهٔ خراب هرگز به پایگاه‌داده یا
 *  کلیدِ تازه برنمی‌گردد (`license_key_invalid`). بیرون آوردنِ کلیدِ امروز: `scripts/license-key.js`.
 */

const crypto = require('crypto');
const fs = require('fs');
const plans = require('./plans');
const { ApiError } = require('../middleware/errors');

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

/**
 *  SYN-01 / D-05 — عمرِ مجوزِ **دکان** با پرچمِ `shop.license_offline_30d` (پیش‌فرض خاموش، `routes/license.js`): ۳۵ روز.
 *  دستگاهِ بی اینترنت مجوزش را تازه نمی‌کند؛ با ده روز، روزِ یازدهمِ قطعی «فروش» و «قرض‌داران» (`VipGate`) قفل می‌شد
 *  حتی برای دکانی که اشتراکش ماه‌ها مانده بود (`Offline30DaysDesktopTest`). سقفِ همیشگی سرِ جایش است: هرگز دیرتر از
 *  پایانِ اشتراک و مهلتش (`activeUntil`)، و لغو یا «اشتراک نیست» ِ سرور همان دم مجوزِ دستگاهِ آنلاین را برمی‌دارد
 *  (`Syncer.refreshLicense`). پمپ و هر صدازننده‌ی دیگر همان ده روز.
 */
const SHOP_OFFLINE_TTL_MS = 35 * 24 * 60 * 60 * 1000;

const KEY_PRIVATE = 'license_private_key';
const KEY_PUBLIC = 'license_public_key';

let cached = null;
let loading = null;

const SOURCE_FA = { env: 'LICENSE_PRIVATE_KEY', file: 'LICENSE_PRIVATE_KEY_FILE', db: 'پایگاه‌داده (app_config)', generated: 'تازه‌ساخته (development/test)' };
//  ⛔ پیام‌ها هیچ چیزی از خودِ کلید یا خطای خامِ رمز ندارند
const missing = () => new ApiError(503, 'license_key_missing',
  'کلیدِ امضای مجوز روی سرور تنظیم نیست؛ مدیرِ سرور باید همان کلیدِ پیشین را در LICENSE_PRIVATE_KEY بگذارد');
/** F0-28: کلید با پشتیبانِ مهرشده برگشته و هنوز مهر است — همان کدِ ۵۰۳، با آنچه صاحبِ سامانه باید بگذارد */
const sealedKey = () => new ApiError(503, 'license_key_missing',
  'کلیدِ امضای مجوز از پشتیبانِ مهرشده برگشته و هنوز مهر است؛ مدیرِ سرور همان BACKUP_SECRETS_KEY ِ هنگامِ پشتیبان را بگذارد و سرور را دوباره روشن کند (یا همان کلیدِ پیشین را در LICENSE_PRIVATE_KEY). کلیدِ تازه ساخته نمی‌شود');
const SEALED_PRIVATE = `sealed:${KEY_PRIVATE}`;
const invalid = (source) => new ApiError(503, 'license_key_invalid',
  `کلیدِ امضای مجوز (${SOURCE_FA[source]}) خوانده نشد یا ES256 (P-256) نیست`);

/** فقط development/test کلید می‌سازند؛ بی NODE_ENV = production (همان قاعدهٔ SEC-AUTH-06 ِ `config.devCodes`) */
const mayGenerate = () => ['development', 'test'].includes(String(process.env.NODE_ENV || '').trim());

function parsePem(pem, source) {
  let key;
  try { key = crypto.createPrivateKey(pem); } catch { throw invalid(source); }
  //  نامِ منحنی در OpenSSL و BoringSSL ِ Electron «prime256v1» است؛ «P-256» هم پذیرفته می‌شود
  if (key.asymmetricKeyType !== 'ec' || !['prime256v1', 'P-256'].includes(key.asymmetricKeyDetails?.namedCurve)) throw invalid(source);
  return key;
}

/** کلیدِ پیکربندی‌شده (محیط، سپس فایل)؛ `\n` ِ نوشتاریِ .env (بارگذارِ `config.js` آن را باز نمی‌کند) ⇒ خطِ تازه */
function configured() {
  const env = String(process.env.LICENSE_PRIVATE_KEY || '').trim();
  if (env) return { pem: env.replace(/\\n/g, '\n'), source: 'env' };
  const file = String(process.env.LICENSE_PRIVATE_KEY_FILE || '').trim();
  if (!file) return null;
  let pem = '';
  try { pem = fs.readFileSync(file, 'utf8').trim(); } catch { throw invalid('file'); }
  return { pem, source: 'file' };
}

/** کلیدِ موجود (محیط ⇐ فایل ⇐ پایگاه‌داده)؛ `generate` فقط برای development/test */
async function load({ generate }) {
  const conf = configured();
  if (conf) {
    const privateKey = parsePem(conf.pem, conf.source);
    return { privateKey, publicSpki: spkiOf(privateKey), source: conf.source };
  }

  const stored = await plans.getConfig(KEY_PRIVATE, '');
  if (stored) {
    const privateKey = parsePem(stored, 'db');
    const publicSpki = (await plans.getConfig(KEY_PUBLIC, '')) || spkiOf(privateKey);
    return { privateKey, publicSpki, source: 'db' };
  }
  //  ⛔ F0-28: کلیدِ پیشین مهرشده این‌جاست ⇒ هرگز کلیدِ تازه کنارش (حتی development/test)؛ باز شدنش همان کلید را برمی‌گرداند
  if (await plans.getConfig(SEALED_PRIVATE, '')) throw sealedKey();
  if (!generate) throw missing();

  const pem = newPem();
  const privateKey = crypto.createPrivateKey(pem);
  const publicSpki = spkiOf(privateKey);

  await plans.setConfig(KEY_PRIVATE, pem);
  await plans.setConfig(KEY_PUBLIC, publicSpki);
  return { privateKey, publicSpki, source: 'generated' };
}

/** PEM ِ یک کلیدِ تازهٔ P-256 (pkcs8) — فقط `load` ِ development/test و `scripts/license-key.js generate` */
function newPem() {
  return crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'pem' },
  }).privateKey;
}

/**
 *  جفت‌کلید سرور — یک بار خوانده (یا در development/test ساخته) و نگه داشته می‌شود.
 *  دو درخواستِ هم‌زمانِ نخست یک بارگذاری را می‌بینند (پیش‌تر هر کدام کلیدِ خودش را می‌ساخت).
 *  شکست نگه داشته نمی‌شود: کلیدی که با بازیابی به پایگاه‌داده برگشت، بی راه‌اندازیِ دوباره کار می‌کند.
 */
async function keys() {
  if (cached) return cached;
  if (!loading) {
    loading = load({ generate: mayGenerate() })
      .then((k) => { cached = k; return k; })
      .finally(() => { loading = null; });
  }
  return loading;
}

const kidOf = (spki) => crypto.createHash('sha256').update(Buffer.from(spki, 'base64')).digest('hex').slice(0, 16);

/**
 *  یک خطِ روشن در راه‌اندازی (`index.js`) — هرگز سرور را نمی‌خواباند و هرگز کلید را چاپ نمی‌کند.
 *  نبودِ کلید در production ⇒ یک خطِ خطا (با kid ِ کلیدِ پیشین اگر ردیفِ عمومی مانده)؛ کلیدِ محیط/فایل که با
 *  کلیدِ پیشینِ پایگاه‌داده یکی نیست ⇒ یک هشدار (چرخاندنِ عمدی؟ وگرنه مجوزِ نصب‌های امروز رد می‌شود).
 */
async function startupCheck(log = console) {
  const previous = async () => { try { return await plans.getConfig(KEY_PUBLIC, ''); } catch { return ''; } };
  let k;
  try { k = await keys(); } catch (err) {
    const code = err && err.code;
    if (code !== 'license_key_missing' && code !== 'license_key_invalid') {
      log.error(`[license] ⛔ کلیدِ امضای مجوز خوانده نشد (${(err && err.message) || err})`);
      return { ok: false, code: 'license_key_unreadable' };
    }
    const prev = await previous();
    log.error(`[license] ⛔ ${code}: ${err.message}. کلیدِ تازه ساخته نمی‌شود؛ صدورِ مجوز و کدِ اشتراکِ آفلاین تا آن ۵۰۳ است`
      + `، بقیهٔ سرور کار می‌کند${prev ? `. کلیدِ پیشینِ این سرور kid=${kidOf(prev)} بود (سنجشِ مجوز و کدِ موجود با کلیدِ عمومیِ آن کار می‌کند)` : ''} (docs/RULES-fa.md «کلیدها»).`);
    return { ok: false, code };
  }
  const kid = kidOf(k.publicSpki);
  log.log(`[license] کلیدِ امضای مجوز از ${SOURCE_FA[k.source]} (kid=${kid})`);
  if (k.source === 'env' || k.source === 'file') {
    const prev = await previous();
    if (prev && prev !== k.publicSpki) {
      log.warn(`[license] ⚠️ این کلید (kid=${kid}) با کلیدِ پیشینِ پایگاه‌داده (kid=${kidOf(prev)}) یکی نیست؛ اگر چرخاندنِ عمدی`
        + ' نیست، مجوزِ نصب‌های امروز رد می‌شود: متغیر را بردارید و سرور را دوباره روشن کنید.');
    }
  }
  return { ok: true, source: k.source, kid };
}

/** کلیدِ موجود برای `scripts/license-key.js` — ⛔ هرگز نمی‌سازد، حتی در development */
async function existing() {
  const k = await load({ generate: false });
  return { ...k, kid: kidOf(k.publicSpki) };
}

/**
 * هر ردی از کلیدی که هست یا بوده (برای `scripts/license-key.js generate`): متغیرِ محیط، نشانیِ فایل (حتی نخواندنی)،
 * ردیفِ خصوصی یا عمومیِ `app_config`. kid فقط وقتی ساختنی است؛ خطای پایگاه‌داده بالا می‌رود (نبودن را ثابت نمی‌کند).
 */
async function traces() {
  const out = [];
  const kidOfPem = (pem) => { try { return kidOf(spkiOf(crypto.createPrivateKey(pem))); } catch { return ''; } };
  const env = String(process.env.LICENSE_PRIVATE_KEY || '').trim();
  if (env) out.push({ source: 'env', kid: kidOfPem(env.replace(/\\n/g, '\n')) });
  const file = String(process.env.LICENSE_PRIVATE_KEY_FILE || '').trim();
  if (file) { let pem = ''; try { pem = fs.readFileSync(file, 'utf8'); } catch { /* نخواندنی هم رد است */ } out.push({ source: 'file', kid: kidOfPem(pem) }); }
  const priv = await plans.getConfig(KEY_PRIVATE, '');
  if (priv) out.push({ source: 'db', kid: kidOfPem(priv) });
  const pub = await plans.getConfig(KEY_PUBLIC, '');
  if (pub) out.push({ source: 'db-public', kid: kidOf(pub) });
  //  F0-28: کلیدِ مهرشدهٔ پشتیبان هم ردِ کلیدِ پیشین است (kid اش فقط با باز شدن معلوم می‌شود)
  if (await plans.getConfig(SEALED_PRIVATE, '')) out.push({ source: 'db-sealed', kid: '' });
  return out;
}

/** کلید عمومی به شکل base64 از SPKI DER — همان چیزی که برنامه می‌خواهد */
function spkiOf(privateKey) {
  return crypto
    .createPublicKey(privateKey)
    .export({ type: 'spki', format: 'der' })
    .toString('base64');
}

/**
 * کلیدِ عمومی (base64 ِ SPKI). بی کلیدِ خصوصی (نبود یا خراب) از ردیفِ ماندهٔ `license_public_key`: سنجشِ مجوز و کدِ
 * آفلاین فقط کلیدِ عمومی می‌خواهد، پس سرورِ بی کلیدِ خصوصی هنوز همان کدهای پیشین را می‌شناسد. هیچ‌کدام ⇒ همان ۵۰۳.
 */
async function publicKey() {
  try { return (await keys()).publicSpki; } catch (err) {
    const stored = err instanceof ApiError ? await plans.getConfig(KEY_PUBLIC, '') : '';
    if (!stored) throw err;
    return stored;
  }
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
  /** عمرِ خودِ مجوز؛ پیش‌فرض همان ده روز ([TOKEN_TTL_MS]) — فقط مجوزِ دکان با پرچم بلندتر ([SHOP_OFFLINE_TTL_MS]) */
  ttlMs = TOKEN_TTL_MS,
  at = Date.now(),
}) {
  const { privateKey } = await keys();
  const ttl = Number(ttlMs) > 0 ? Number(ttlMs) : TOKEN_TTL_MS;

  const header = { alg: 'ES256', typ: 'TLIC', kid: await keyId() };

  // مجوز از خود اشتراک زودتر تمام می‌شود تا لغو اشتراک زود اثر کند —
  // ولی نه دیرتر از پایان اشتراک، وگرنه چند روز مجانی می‌دهد.
  //  ⚠️ `activeUntil` پایانِ **مهلتِ** اشتراک است (`graceEndsAt`): تا ۲.۹.۰
  //  سقف همان `ends_at`ِ خام بود، پس در روزهای مهلت سرور «فعال» می‌گفت و
  //  مجوزی می‌داد که همان لحظه منقضی بود.
  const cap = Number(activeUntil) || Number(subscriptionEndsAt) || at + ttl;
  const expiresAt = Math.min(at + ttl, cap);
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

/**
 * سنجشِ همان امضا با کلیدِ عمومیِ همین سرور — فقط کلیدِ عمومی (`publicKey()`؛ بی کلیدِ خصوصی از ردیفِ ماندهٔ
 * `license_public_key`). امضای بد ⇒ false. هیچ کلیدی (نه خصوصی، نه عمومی) ⇒ ۵۰۳؛ پیش‌تر همین حال کلیدِ تازه‌ای
 * می‌ساخت که هیچ کدِ موجودی با آن نمی‌خواند (همیشه false).
 */
async function verifyBytes(buf, sig) {
  const spki = await publicKey();
  try {
    const key = crypto.createPublicKey({ key: Buffer.from(spki, 'base64'), format: 'der', type: 'spki' });
    return crypto.verify('sha256', Buffer.from(buf), { key, dsaEncoding: 'ieee-p1363' }, Buffer.from(sig));
  } catch { return false; }
}

module.exports = {
  issue, publicKey, keyId, signBytes, verifyBytes, startupCheck, existing, traces, newPem, kidOf,
  ISSUER, AUDIENCE, AUDIENCE_PUMP, TOKEN_TTL_MS, SHOP_OFFLINE_TTL_MS,
  /** فقط آزمون: کشِ کلید را خالی می‌کند */
  _reset() { cached = null; loading = null; },
};
