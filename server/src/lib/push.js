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
const { query, one, many, newId, now } = require('../db');
const plans = require('./plans');
const apns = require('./apns');

const PREFIX = 'push_';
const SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

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
  const out = {
    enabled: rows[PREFIX + 'enabled'] === '1',
    serviceAccount: rows[PREFIX + 'service_account'] || process.env.FCM_SERVICE_ACCOUNT || '',
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
  userId = '', adminId = '', shopId = '', stationId = '', deviceUid = '', platform = '',
}) {
  const clean = String(token || '').trim();
  if (!clean || clean.length > 500) return null;
  const t = now();
  const existing = await one('SELECT * FROM push_tokens WHERE app=$1 AND token=$2', [app, clean]);
  if (existing) {
    await query(
      `UPDATE push_tokens SET user_id=$2, admin_id=$3, device_uid=$4, platform=$5,
              shop_id=$6, station_id=$7, status='active', updated_at=$8 WHERE id=$1`,
      [existing.id, userId, adminId, deviceUid, platform, shopId, stationId, t]
    );
    return existing.id;
  }
  const id = newId('psh');
  await query(
    `INSERT INTO push_tokens (id, app, token, provider, user_id, admin_id, shop_id, station_id,
                              device_uid, platform, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,'active',$11,$11)`,
    [id, app, clean, provider, userId, adminId, shopId, stationId, deviceUid, platform, t]
  );
  return id;
}

async function unregister(token) {
  await query(`UPDATE push_tokens SET status='stale' WHERE token=$1`, [String(token || '')]);
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
  const fcmOn = cfg.enabled && !!cfg.serviceAccount;
  if (!fcmOn && !apnsSender) return { sent: 0, skipped: 'push_off' };

  let sa = null;
  if (fcmOn) {
    try { sa = JSON.parse(cfg.serviceAccount); } catch { if (!apnsSender) return { sent: 0, skipped: 'bad_service_account' }; }
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

async function sendFcm(sa, fcmRows, message) {
  let bearer;
  try { bearer = await googleToken(sa); } catch (err) { return { sent: 0, error: err.message }; }

  let sent = 0;
  for (const row of fcmRows) {
    try {
      const res = await fetch(
        `https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`,
        {
          method: 'POST',
          headers: { Authorization: `Bearer ${bearer}`, 'Content-Type': 'application/json' },
          body: JSON.stringify({
            message: {
              token: row.token,
              notification: { title: message.title, body: message.body },
              data: Object.fromEntries(
                Object.entries(message.data || {}).map(([k, v]) => [k, String(v)])
              ),
              android: { priority: 'high', notification: { channel_id: message.channel || 'support' } },
            },
          }),
          signal: AbortSignal.timeout(15000),
        }
      );
      if (res.ok) {
        sent++;
        await query('UPDATE push_tokens SET last_ok_at=$2, last_error=$3 WHERE id=$1', [row.id, now(), '']);
      } else {
        const body = await res.text().catch(() => '');
        //  ۴۰۴ یا UNREGISTERED یعنی این توکن مرده است
        const dead = res.status === 404 || /UNREGISTERED|INVALID_ARGUMENT/.test(body);
        await query(
          `UPDATE push_tokens SET last_error=$2, status=$3 WHERE id=$1`,
          [row.id, body.slice(0, 300), dead ? 'stale' : row.status]
        );
      }
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

module.exports = { current, save, masked, invalidate, register, unregister, sendTo, targetsFor, setDeliver };
