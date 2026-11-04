'use strict';
/**
 * پوش — پیامی که به گوشیِ بسته هم می‌رسد.
 *
 * ── چرا لازم است ───────────────────────────────────────────────────
 * قرار صاحب مخزن: «حتی برنامه‌اش که بسته بود، پیام پشتیبانی برای طرف
 * برود». برنامه‌ی بسته هیچ درخواستی نمی‌زند، پس هیچ‌جوری خودش خبردار
 * نمی‌شود. تنها راه این است که سرور پیام را به سرویسِ پوشِ گوگل بسپارد
 * و او به گوشی برساند.
 *
 * ── چرا FCM HTTP v1 و نه راه قدیمی ─────────────────────────────────
 * راهِ قدیمی (کلیدِ سرور) از سال ۲۰۲۴ بسته شده. راهِ امروز یک
 * «حساب سرویس» است: با کلید خصوصی‌اش یک JWT امضا می‌کنیم، آن را با یک
 * توکنِ دسترسیِ کوتاه‌عمر عوض می‌کنیم، و با آن پیام می‌فرستیم. همه‌اش
 * با `crypto` خودِ Node انجام می‌شود — بی هیچ وابستگی تازه.
 *
 * ── اگر تنظیم نشده باشد ────────────────────────────────────────────
 * هیچ‌چیز نمی‌شکند. پیام در چت می‌نشیند و دفعه‌ی بعد که برنامه باز شد
 * دیده می‌شود؛ فقط زنگش را نمی‌زند. پس نصبِ بدونِ FCM هم کار می‌کند.
 */
const { createSign } = require('crypto');
const fs = require('fs');
const { query, one, many, newId, now } = require('../db');
const plans = require('./plans');
const apns = require('./apns');

const PREFIX = 'push_';
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';
const FCM_ORIGIN = 'https://fcm.googleapis.com';
/** سقفِ توکنِ فعالِ هر کس در هر برنامه — بیشتر ⇒ کهنه‌ترین‌ها کهنه (ثبتِ انبوهِ توکنِ ساختگی پوش را کند نمی‌کند) */
const MAX_TOKENS_PER_USER = 10;

/*
 *  ⛔ «بی کلید، بی‌صدا ولی نه پنهان» (O-01، docs/FIREBASE-SETUP-fa.md): بی حسابِ سرویس هیچ پیامی نمی‌رود و هیچ
 *  خطایی بیرون نمی‌آید؛ فقط **یک بار** در هر اجرای سرور در لاگ گفته می‌شود چرا — نه با هر سفارش.
 *  ⛔ متنِ هشدار هیچ کلید، توکن یا محتوای فایل ندارد؛ فقط نامِ متغیر و دلیل.
 */
const warned = new Set();
function warnOnce(key, text) {
  if (warned.has(key)) return false;
  warned.add(key);
  console.warn(`[push] ${text}`);
  return true;
}

/**
 * حسابِ سرویس از محیطِ سرور: `FCM_SERVICE_ACCOUNT` (خودِ JSON) یا `FCM_SERVICE_ACCOUNT_FILE` (مسیرِ فایلِ JSON،
 * مثلاً یک secret ِ Docker). ⛔ هرگز در مخزن نیست. فایلِ ناخوانا ⇒ همان «تنظیم نشده» و یک هشدار.
 */
function envServiceAccount() {
  const inline = String(process.env.FCM_SERVICE_ACCOUNT || '').trim();
  if (inline) return { text: inline, source: 'env' };
  const file = String(process.env.FCM_SERVICE_ACCOUNT_FILE || '').trim();
  if (!file) return { text: '', source: '' };
  try {
    return { text: fs.readFileSync(file, 'utf8').trim(), source: 'file' };
  } catch (err) {
    warnOnce('fcm_file', `FCM_SERVICE_ACCOUNT_FILE خوانده نشد (${(err && err.code) || 'error'}) — پوشِ اندروید خاموش می‌ماند`);
    return { text: '', source: '' };
  }
}

/** JSON ِ حسابِ سرویس ⇐ شیء، یا null با یک هشدار (بی محتوای فایل در لاگ) */
function parseServiceAccount(text) {
  if (!text) return null;
  try {
    const sa = JSON.parse(text);
    if (sa && sa.client_email && sa.private_key && sa.project_id) return sa;
  } catch { /* پایین */ }
  warnOnce('fcm_bad_sa', 'حسابِ سرویسِ Firebase خراب یا ناقص است (client_email، private_key و project_id لازم است) — پوشِ اندروید خاموش می‌ماند');
  return null;
}

let cache = { at: 0, value: null };
const TTL_MS = 10 * 1000;

function invalidate() { cache = { at: 0, value: null }; }

/**
 * تنظیمات.
 *
 * `serviceAccount` همان فایل JSON است که کنسول Firebase می‌دهد — کامل،
 * همان‌طور که هست. جای دیگری نمی‌رود و به بیرون هم برنمی‌گردد.
 */
async function current() {
  const t = Date.now();
  if (cache.value && t - cache.at < TTL_MS) return cache.value;
  const rows = await plans.allConfig();
  const stored = rows[PREFIX + 'service_account'] || '';
  const env = stored ? { text: '', source: '' } : envServiceAccount();
  const out = {
    enabled: rows[PREFIX + 'enabled'] === '1',
    serviceAccount: stored || env.text,
    //  از کجا: پنل (ذخیره در پایگاه) · env · file — برای «چرا خاموش است» ِ پنل، نه خودِ کلید
    source: stored ? 'panel' : env.source,
  };
  cache = { at: t, value: out };
  return out;
}

async function save(patch = {}) {
  if (patch.enabled !== undefined) await plans.setConfig(PREFIX + 'enabled', patch.enabled ? '1' : '0');
  if (typeof patch.serviceAccount === 'string' && patch.serviceAccount.trim()) {
    //  پیش از ذخیره سنجیده می‌شود، وگرنه خرابی‌اش وقتی معلوم می‌شد که
    //  کسی منتظر پیام مانده بود
    const parsed = JSON.parse(patch.serviceAccount);
    if (!parsed.client_email || !parsed.private_key || !parsed.project_id) {
      throw new Error('فایل حساب سرویس ناقص است: client_email، private_key و project_id لازم است');
    }
    await plans.setConfig(PREFIX + 'service_account', patch.serviceAccount.trim());
  }
  if (patch.clearServiceAccount === true) await plans.setConfig(PREFIX + 'service_account', '');
  invalidate();
  accessToken = { token: '', expiresAt: 0 };
  return masked();
}

async function masked() {
  const s = await current();
  let project = '';
  let account = '';
  try {
    const parsed = JSON.parse(s.serviceAccount || '{}');
    project = parsed.project_id || '';
    account = parsed.client_email || '';
  } catch { /* ذخیره‌شده‌ی خراب؛ همان خالی نشان داده می‌شود */ }
  const counts = await one(
    `SELECT COUNT(*)::int n FROM push_tokens WHERE status='active'`
  );
  return {
    enabled: s.enabled,
    configured: !!s.serviceAccount,
    source: s.source || '',
    project,
    account,
    devices: counts.n,
    //  آیفون: فقط «روشن؟/کدام محیط/چرا خاموش» — کلید فقط در محیطِ سرور است (`lib/apns.js`)
    apns: apns.status(),
  };
}

/* ---------------------------- توکن دسترسی ---------------------------- */

let accessToken = { token: '', expiresAt: 0 };

function b64url(buf) {
  return Buffer.from(buf).toString('base64')
    .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * توکنِ دسترسیِ گوگل، با امضای خودِ ما.
 *
 * یک ساعت اعتبار دارد و تا وقتی زنده است دوباره گرفته نمی‌شود — وگرنه
 * هر پیام یک رفت‌وبرگشتِ اضافه به گوگل بود.
 */
async function googleToken(sa) {
  const t = Date.now();
  if (accessToken.token && accessToken.expiresAt - 60_000 > t) return accessToken.token;

  const iat = Math.floor(t / 1000);
  const header = b64url(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claim = b64url(JSON.stringify({
    iss: sa.client_email,
    scope: SCOPE,
    aud: sa.token_uri || 'https://oauth2.googleapis.com/token',
    iat,
    exp: iat + 3600,
  }));
  const signer = createSign('RSA-SHA256');
  signer.update(`${header}.${claim}`);
  const signature = b64url(signer.sign(sa.private_key));
  const jwt = `${header}.${claim}.${signature}`;

  const res = await fetch(sa.token_uri || 'https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }).toString(),
    signal: AbortSignal.timeout(15000),
  });
  const body = await res.json().catch(() => ({}));
  if (!res.ok || !body.access_token) {
    throw new Error(`گوگل توکن نداد (${res.status}): ${JSON.stringify(body).slice(0, 200)}`);
  }
  accessToken = { token: body.access_token, expiresAt: t + (body.expires_in || 3600) * 1000 };
  return accessToken.token;
}

/* ------------------------------ ثبت توکن ------------------------------ */

/**
 * ثبت یا به‌روزرسانی توکنِ یک دستگاه.
 *
 * یک توکن ممکن است از دستگاهی به دستگاه دیگر برود (بازنشانی برنامه)،
 * پس صاحبش هم به‌روز می‌شود — وگرنه پیامِ یک نفر به گوشی نفرِ قبلی
 * می‌رفت.
 */
async function register({
  app = 'shop', token, provider = 'fcm',
  userId = '', adminId = '', shopId = '', stationId = '', deviceUid = '', platform = '', exclusive = false,
}) {
  const clean = String(token || '').trim();
  if (!clean || clean.length > 500) return null;
  const t = now();
  const existing = await one('SELECT * FROM push_tokens WHERE app=$1 AND token=$2', [app, clean]);
  let id;
  if (existing) {
    await query(
      `UPDATE push_tokens SET user_id=$2, admin_id=$3, device_uid=$4, platform=$5,
              shop_id=$6, station_id=$7, status='active', stale_reason='', stale_at=NULL, updated_at=$8 WHERE id=$1`,
      [existing.id, userId, adminId, deviceUid, platform, shopId, stationId, t]
    );
    id = existing.id;
  } else {
    id = newId('psh');
    await query(
      `INSERT INTO push_tokens (id, app, token, provider, user_id, admin_id, shop_id, station_id,
                                device_uid, platform, status, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'active',$11,$11)`,
      [id, app, clean, provider, userId, adminId, shopId, stationId, deviceUid, platform, t]
    );
  }
  //  `exclusive` (درِ `/api/shop/push`): یک دستگاه، یک توکنِ زنده برای هر کس — FCM توکن را گاهی عوض می‌کند و
  //  توکنِ پیشینِ همان دستگاه دیگر هیچ‌جا نمی‌رسد؛ بی این، هر پیام یک تلاشِ بی‌حاصلِ دیگر به گوگل بود تا
  //  UNREGISTERED برگردد. درهای کهنه (`/me/push`، `/support/push`) همان رفتارِ پیشین را دارند.
  if (exclusive && deviceUid && userId) {
    await query(
      `UPDATE push_tokens SET status='stale', stale_reason='replaced', stale_at=$5
        WHERE app=$1 AND user_id=$2 AND device_uid=$3 AND provider=$6 AND id<>$4 AND status='active'`,
      [app, userId, deviceUid, id, t, provider]
    );
  }
  if (userId) {
    await query(
      `UPDATE push_tokens SET status='stale', stale_reason='over_cap', stale_at=$3
        WHERE id IN (SELECT id FROM push_tokens WHERE app=$1 AND user_id=$2 AND status='active'
                      ORDER BY updated_at DESC, id DESC OFFSET ${MAX_TOKENS_PER_USER})`,
      [app, userId, t]
    );
  }
  return id;
}

async function unregister(token) {
  await query(`UPDATE push_tokens SET status='stale', stale_reason='unregistered', stale_at=$2 WHERE token=$1`,
    [String(token || ''), now()]);
}

/* ---------------------------- کنار گذاشتن ---------------------------- */
/*
 *  ⛔ توکن هرگز پاک نمی‌شود، فقط «کهنه» با دلیل (`stale_reason`، مهاجرتِ ۲۱۳) — تا «چرا دیگر زنگ نمی‌زند»
 *  جواب داشته باشد. ثبتِ دوبارهٔ همان توکن (برنامه پس از ورودِ دوباره) همان ردیف را زنده می‌کند.
 */
async function stale(where, args, reason) {
  const a = [...args, String(reason || '').slice(0, 40), now()];
  const r = await query(
    `UPDATE push_tokens SET status='stale', stale_reason=$${a.length - 1}, stale_at=$${a.length}
      WHERE status='active' AND ${where}`, a
  );
  return r.rowCount || 0;
}

/** خروجِ خودِ این کس از همین دستگاه: فقط توکن‌های خودش (و اگر داده شد، همین توکن اگر مالِ خودش است) */
async function unregisterOwn({ app = 'shop', userId, deviceUid = '', token = '' }) {
  if (!userId) return 0;
  const clean = String(token || '').trim().slice(0, 500);
  const parts = [];
  const args = [app, String(userId)];
  if (deviceUid) { args.push(String(deviceUid)); parts.push(`device_uid=$${args.length}`); }
  if (clean) { args.push(clean); parts.push(`token=$${args.length}`); }
  if (!parts.length) return 0;
  return stale(`app=$1 AND user_id=$2 AND (${parts.join(' OR ')})`, args, 'unregistered');
}

/** عضوی که از دکان برداشته یا معلق شد: هیچ خبرِ دیگری از آن دکان به گوشی‌اش نمی‌رسد */
async function revokeMember(shopId, userId, reason = 'member_removed') {
  if (!shopId || !userId) return 0;
  return stale(`app='shop' AND shop_id=$1 AND user_id=$2`, [String(shopId), String(userId)], reason);
}

/**
 * خروج از یک دستگاه یا از کار انداختنش. `deviceId` شناسهٔ ردیفِ `devices` است؛ توکن با `device_uid` ِ برنامه یا
 * همان شناسه ثبت شده (`routes/me.js`) ⇒ هر دو. `userId` داده شود ⇒ فقط توکن‌های همان کس روی آن دستگاه.
 */
async function revokeDevice({ deviceId, userId = '', app = '', reason = 'logout' }) {
  if (!deviceId) return 0;
  const dev = await one('SELECT id, device_uid FROM devices WHERE id=$1', [String(deviceId)]);
  const keys = [...new Set([String(deviceId), dev && dev.device_uid ? String(dev.device_uid) : ''].filter(Boolean))];
  const args = [keys];
  let where = 'device_uid = ANY($1::text[])';
  if (userId) { args.push(String(userId)); where += ` AND user_id=$${args.length}`; }
  if (app) { args.push(String(app)); where += ` AND app=$${args.length}`; }
  return stale(where, args, reason);
}

/** «خروج از همه» ِ یک بخش ⇒ هیچ گوشیِ این کس در این بخش دیگر خبر نمی‌گیرد تا دوباره وارد شود */
async function revokeUser(userId, app = 'shop', reason = 'logout_all') {
  if (!userId) return 0;
  return stale('user_id=$1 AND app=$2', [String(userId), String(app || 'shop')], reason);
}

/** این دستگاهِ این کس توکنِ زنده دارد؟ (برای «پوش روشن است؟» ِ برنامه) */
async function deviceRegistered({ app = 'shop', userId, shopId = '', deviceUid = '' }) {
  if (!userId) return false;
  const args = [app, String(userId)];
  let sql = `SELECT 1 AS x FROM push_tokens WHERE status='active' AND app=$1 AND user_id=$2`;
  if (shopId) { args.push(String(shopId)); sql += ` AND shop_id=$${args.length}`; }
  if (deviceUid) { args.push(String(deviceUid)); sql += ` AND device_uid=$${args.length}`; }
  return Boolean(await one(`${sql} LIMIT 1`, args));
}

/* ------------------------------ فرستادن ------------------------------ */

/**
 * فرستادن به همه‌ی دستگاه‌های یک نفر (یا همه‌ی مدیرها).
 *
 * توکنی که گوگل «دیگر وجود ندارد» بخواند، همان‌جا کنار گذاشته می‌شود —
 * وگرنه فهرست پر می‌شد از گوشی‌هایی که برنامه از رویشان پاک شده و هر
 * پیام چند خطای بی‌فایده می‌داد.
 */
/**
 * گیرنده‌های یک پیام — جدا از فرستادن، تا بشود سنجیدش.
 *
 * ⚠️ `stationId` و `shopId` از امروز هستند و بی آن‌ها نیمی از
 * گیرنده‌ها دیده نمی‌شدند: برنامهٔ کامپیوترِ پمپ حساب ندارد، و
 * شاگردِ یک دکان `user_id`ِ دیگری دارد ولی باید خبرِ همان دکان را
 * بگیرد.
 */
async function targetsFor({ userId = '', adminId = '', shopId = '', stationId = '', deviceUid = '', app = '', allAdmins = false, exceptUserId = '', onlyUserIds = null }) {
  const where = [];
  const args = [];
  if (userId)    { args.push(userId);    where.push(`user_id = $${args.length}`); }
  if (adminId)   { args.push(adminId);   where.push(`admin_id = $${args.length}`); }
  if (shopId)    { args.push(shopId);    where.push(`shop_id = $${args.length}`); }
  if (stationId) { args.push(stationId); where.push(`station_id = $${args.length}`); }
  if (deviceUid) { args.push(deviceUid); where.push(`device_uid = $${args.length}`); }
  if (allAdmins) where.push(`admin_id <> ''`);
  if (!where.length) return [];
  let sql = `SELECT * FROM push_tokens WHERE status='active' AND (${where.join(' OR ')})`;
  if (app) { args.push(app); sql += ` AND app = $${args.length}`; }
  //  ⚠️ کسی که خودش کاری کرده، نباید خبرِ کارِ خودش را بگیرد. بی این،
  //  فروشنده با هر فروش یک زنگ روی گوشیِ خودش می‌شنید و همان روزِ اول
  //  اعلان‌ها را خاموش می‌کرد.
  if (exceptUserId) { args.push(exceptUserId); sql += ` AND user_id <> $${args.length}`; }
  //  فقط همین کاربرها (مثلاً اعلانِ سفارش فقط برای دارندگانِ `orders.view`)؛ فهرستِ خالی ⇒ هیچ‌کس
  if (Array.isArray(onlyUserIds)) {
    if (!onlyUserIds.length) return [];
    args.push(onlyUserIds.map(String)); sql += ` AND user_id = ANY($${args.length}::text[])`;
  }
  return many(sql, args);
}

/**
 * درِ فرستادن — تا آزمون بتواند جایش بنشیند.
 *
 * ⚠️ بی این، هیچ سنجه‌ای نمی‌توانست ثابت کند پوش به **چه کسی** می‌رود:
 * FCM از ماشینِ آزمون در دسترس نیست و هر سنجه‌ای یا باید شبکه بزند یا
 * سبزِ دروغ بدهد. با این، خودِ تصمیم سنجیده می‌شود و شبکه بیرون می‌ماند.
 *
 * ⛔ در کارِ عادی هیچ‌وقت مقدار نمی‌گیرد: فقط `test/*.test.js` آن را
 * می‌نشاند و همان‌جا هم برش می‌دارد.
 */
let deliver = null;
function setDeliver(fn) { deliver = typeof fn === 'function' ? fn : null; }

/**
 * نشانیِ FCM — فقط آزمون عوضش می‌کند (سرورِ HTTP ِ ساختگی روی همین ماشین، `test/push-fcm.test.js`)، تا فرستادنِ
 * واقعی، توکنِ گوگل و کنار گذاشتنِ توکنِ مرده بی شبکه سنجیده شود. ⛔ در کارِ عادی هیچ‌وقت صدا زده نمی‌شود.
 */
let fcmOrigin = FCM_ORIGIN;
function useFcmOrigin(origin) {
  fcmOrigin = origin ? String(origin).replace(/\/+$/, '') : FCM_ORIGIN;
  accessToken = { token: '', expiresAt: 0 };
  warned.clear();
  invalidate();
}

async function sendTo(target, message) {
  //  راهِ آزمون: تنظیماتِ ابر و گوگل اصلاً لمس نمی‌شوند
  if (deliver) {
    const rows = await targetsFor(target);
    if (!rows.length) return { sent: 0, skipped: 'no_devices' };
    for (const row of rows) await deliver(row, message);
    return { sent: rows.length, total: rows.length };
  }

  //  کلیدِ «پوش روشن» ِ پنل کلیدِ اصلیِ هر دو راه است؛ FCM حسابِ سرویس و APNs کلیدِ محیط را هم می‌خواهد
  const cfg = await current();
  const apnsSender = cfg.enabled ? apns.sender() : null;
  const sa = cfg.enabled ? parseServiceAccount(cfg.serviceAccount) : null;
  if (!cfg.enabled) warnOnce('push_off', 'کلیدِ «پوش روشن» ِ پنل خاموش است — هیچ پوشی فرستاده نمی‌شود (docs/FIREBASE-SETUP-fa.md)');
  else if (!cfg.serviceAccount) warnOnce('fcm_none', 'حسابِ سرویسِ Firebase تنظیم نشده (FCM_SERVICE_ACCOUNT_FILE یا FCM_SERVICE_ACCOUNT) — پوشِ اندروید فرستاده نمی‌شود');
  if (!sa && !apnsSender) {
    return { sent: 0, skipped: !cfg.enabled ? 'push_off' : cfg.serviceAccount ? 'bad_service_account' : 'push_off' };
  }

  const rows = await targetsFor(target);
  if (!rows.length) return { sent: 0, skipped: target.userId || target.adminId || target.shopId
    || target.stationId || target.deviceUid || target.allAdmins ? 'no_devices' : 'no_target' };

  //  توکنِ APNs ِ برنامهٔ آیفون از درِ FCM نمی‌رود: گوگل آن را «نامعتبر» می‌خواند و ردیف «کهنه» می‌شد.
  //  فرستنده‌اش `lib/apns.js` است با کلیدِ p8 ِ حسابِ اپلِ صاحب‌کار (docs/IOS-PLAN-fa.md §۰.۶ و §۰.۱۰)؛ بی آن
  //  این توکن‌ها فقط ثبت و نگه داشته می‌شوند — نه فرستاده، نه کهنه.
  const fcmRows = rows.filter(r => r.provider !== 'apns');
  const apnsRows = rows.filter(r => r.provider === 'apns');
  const out = { sent: 0, total: rows.length };

  if (apnsRows.length && apnsSender) {
    const r = await sendApns(apnsSender, apnsRows, message);
    out.sent += r.sent;
    if (r.error) out.apnsError = r.error;
  }

  if (fcmRows.length && sa) {
    const r = await sendFcm(sa, fcmRows, message);
    out.sent += r.sent;
    if (r.error) out.error = r.error;
  }

  if (!fcmRows.length && !apnsSender) out.skipped = 'apns_only';
  else if (!apnsRows.length && !sa) out.skipped = 'fcm_off';
  //  هیچ نرسید ⇒ خطای APNs هم دیده شود (`order-notify` فقط `error` را می‌خواند)
  if (!out.sent && !out.error && out.apnsError) out.error = out.apnsError;
  return out;
}

/** پیامِ FCM HTTP v1 — `tag` اعلانِ پیشینِ همان سفارش را جایگزین می‌کند؛ `ttlSeconds` خبرِ کهنه را دیر نمی‌رساند */
function fcmBody(token, message) {
  const notification = { channel_id: message.channel || 'support' };
  if (message.tag) notification.tag = String(message.tag).slice(0, 100);
  const android = { priority: 'high', notification };
  if (Number.isInteger(message.ttlSeconds) && message.ttlSeconds > 0) android.ttl = `${message.ttlSeconds}s`;
  return {
    message: {
      token,
      notification: { title: message.title, body: message.body },
      data: Object.fromEntries(Object.entries(message.data || {}).map(([k, v]) => [k, String(v)])),
      android,
    },
  };
}

/**
 * پاسخِ خطای FCM ⇐ `{ code, dead, fatal }`. «مرده» فقط وقتی خطا خودِ توکن است: ۴۰۴/`UNREGISTERED` (برنامه پاک
 * شد)، `SENDER_ID_MISMATCH` (توکنِ پروژهٔ Firebase ِ دیگر) و `INVALID_ARGUMENT` ای که دربارهٔ توکن است. ⛔
 * `INVALID_ARGUMENT` ِ خودِ پیام (دادهٔ نادرست) توکن را کهنه نمی‌کند — وگرنه یک پیامِ خراب همهٔ گوشی‌ها را می‌برید.
 * «کشنده» (۴۰۱، ۴۰۳ ِ دیگر، ۴۲۹) ⇒ بقیهٔ دسته فرستاده نمی‌شود: کلید خراب است یا سهمیه پر.
 */
function fcmError(status, text) {
  let err = {};
  try { err = (JSON.parse(text) || {}).error || {}; } catch { /* متنِ خام */ }
  const details = Array.isArray(err.details) ? err.details : [];
  const code = String((details.find((d) => d && d.errorCode) || {}).errorCode || err.status || '');
  const aboutToken = /registration token|message\.token/i.test(`${err.message || ''} ${JSON.stringify(details)}`);
  const dead = status === 404 || code === 'UNREGISTERED' || code === 'SENDER_ID_MISMATCH'
    || (code === 'INVALID_ARGUMENT' && aboutToken);
  const fatal = !dead && (status === 401 || status === 403 || status === 429);
  const reason = code === 'UNREGISTERED' || status === 404 ? 'fcm_unregistered'
    : code === 'SENDER_ID_MISMATCH' ? 'fcm_sender_mismatch' : 'fcm_invalid';
  const note = `fcm ${status} ${code} ${String(err.message || '').slice(0, 160)}`.replace(/\s+/g, ' ').trim();
  return { code, dead, fatal, reason, note };
}

async function sendFcm(sa, fcmRows, message) {
  let bearer;
  try { bearer = await googleToken(sa); } catch (err) { return { sent: 0, error: err.message }; }

  let sent = 0;
  for (const row of fcmRows) {
    try {
      const res = await fetch(
        `${fcmOrigin}/v1/projects/${encodeURIComponent(sa.project_id)}/messages:send`,
        {
          method: 'POST',
          headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
          body: JSON.stringify(fcmBody(row.token, message)),
          signal: AbortSignal.timeout(15000),
        }
      );
      if (res.ok) {
        sent++;
        await query('UPDATE push_tokens SET last_ok_at=$2, last_error=$3 WHERE id=$1', [row.id, now(), '']);
        continue;
      }
      const e = fcmError(res.status, await res.text().catch(() => ''));
      await query(
        `UPDATE push_tokens SET last_error=$2, status=$3, stale_reason=$4, stale_at=$5 WHERE id=$1`,
        [row.id, e.note.slice(0, 300), e.dead ? 'stale' : row.status, e.dead ? e.reason : row.stale_reason || '',
          e.dead ? now() : row.stale_at || null]
      );
      if (e.fatal) return { sent, error: e.note };
    } catch (err) {
      await query('UPDATE push_tokens SET last_error=$2 WHERE id=$1', [row.id, String(err.message).slice(0, 300)]);
    }
  }
  return { sent };
}

/**
 * APNs: هر ردیف یک درخواست روی همان نشستِ HTTP/2. «مرده» (۴۱۰، BadDeviceToken) ⇒ کهنه؛
 * خطای کلید/تیم (۴۰۳ ِ دیگر) ⇒ بقیهٔ دسته فرستاده نمی‌شود. `last_error` فقط «apns کد دلیل» است — بی توکن.
 */
async function sendApns(sender, apnsRows, message) {
  let sent = 0;
  const collapseId = message.collapseId || (message.data && message.data.notifId) || '';
  for (const row of apnsRows) {
    let r;
    try {
      r = await sender.send(row.token, { ...message, collapseId });
    } catch {
      r = { ok: false, status: 0, reason: 'send_failed' };
    }
    if (r.ok) {
      sent++;
      await query('UPDATE push_tokens SET last_ok_at=$2, last_error=$3 WHERE id=$1', [row.id, now(), '']);
      continue;
    }
    const note = `apns ${r.status || ''} ${r.reason || ''}`.replace(/\s+/g, ' ').trim().slice(0, 120);
    await query(
      `UPDATE push_tokens SET last_error=$2, status=$3 WHERE id=$1`,
      [row.id, note, r.dead ? 'stale' : row.status]
    );
    if (r.fatal) return { sent, error: note };
  }
  return { sent };
}

module.exports = {
  current, save, masked, invalidate, register, unregister, sendTo, targetsFor, setDeliver,
  unregisterOwn, revokeMember, revokeDevice, revokeUser, deviceRegistered, useFcmOrigin, fcmError, MAX_TOKENS_PER_USER,
};
