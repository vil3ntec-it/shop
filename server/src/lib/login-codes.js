'use strict';
/**
 * ورود با کدِ شش‌رقمیِ ایمیلی، مخصوصِ هر برنامه — هستهٔ منطق.
 *
 * ── قرارداد (بخشِ ۲ و ۶ی پرامپتِ ورود) ────────────────────────────
 *   کد ۶ رقم، CSPRNG، ۳۰۰ ثانیه اعتبار، یک‌بارمصرف، **فقط آخرین کد**
 *   ۵ کدِ غلط ⇒ ۹۰۰ ثانیه قفل     ۶۰ ثانیه فاصلهٔ ارسالِ دوباره
 *   ۲۰ درخواست / IP / ۱۰ دقیقه     ۵ کد / ایمیل / ساعت
 *   ثبت‌نام و ورود یکی است؛ ایمیلِ ناموجود هم ۲۰۰ می‌گیرد (ضدِ شمارش)
 *
 * ── بی Redis ────────────────────────────────────────────────────────
 * هر «کلید با TTL» یک ردیف با ستونِ زمان است، و هر شمارنده یک COUNT در
 * پنجرهٔ زمانی روی همان ردیف‌ها. معنا همان است، دفتر همان دفتر.
 *
 * ── کد کجاست ────────────────────────────────────────────────────────
 *   `code_hash`   HMAC-SHA256(pepper, "app\nemail\ncode") — برای سنجش
 *   `code_sealed` AES-256-GCM با کلیدِ مشتق از همان راز — فقط برای دو
 *                 کار: خودِ ارسال (که در صف است و بعداً می‌رود) و
 *                 «نمایشِ کد به مدیرِ کل» وقتی ایمیلِ مشتری خراب است.
 *                 با انقضا یا مصرف پاک می‌شود. **هیچ‌وقت در لاگ نمی‌آید.**
 *
 * ⚠️ این فایل هیچ HTTPی نمی‌داند: نه `req` نه `res`. پاسخ‌ها به شکلِ
 * `{ ok:false, status, error, message, retry_after }` برمی‌گردند و
 * `routes/app-auth.js` همان را می‌فرستد.
 */
const { randomInt, randomBytes, createHmac, createHash, createCipheriv, createDecipheriv, timingSafeEqual } = require('crypto');
const { notifyPanel } = require('./panel-live');
const { query, one, many, now } = require('../db');
const config = require('../config');
const { sectionOf } = require('./tenancy');

// ---------- ثابت‌ها (ثانیه، همان عددهای پرامپت) ----------
const CODE_TTL_S      = 300;
const RESEND_S        = 60;
const LOCK_S          = 900;
const MAX_WRONG       = 5;
/*
 *  سقفِ IP قابلِ تنظیم است و باید باشد: پشتِ تونل، تا وقتی
 *  `TRUST_PROXY=true` نباشد همهٔ کاربران **یک** IP دیده می‌شوند و سقفِ
 *  بیستِ ثابت یعنی نفرِ بیست‌ویکم قفل می‌شود بی آن‌که کاری کرده باشد.
 *  پیش‌فرض همان عددِ پرامپت است.
 */
const envNum = (key, dflt) => {
  const v = Number(process.env[key]);
  return Number.isFinite(v) && v > 0 ? v : dflt;
};
const IP_MAX          = envNum('LOGIN_IP_MAX', 20);
const IP_WINDOW_S     = envNum('LOGIN_IP_WINDOW_S', 600);
const EMAIL_MAX       = envNum('LOGIN_EMAIL_MAX', 5);
const EMAIL_WINDOW_S  = 3600;
//  ردیف‌ها بعد از این مدت از دفتر می‌روند (برای پنلِ «ورودها» می‌مانند)
const KEEP_DAYS       = 7;

/** نامِ برنامه‌ها — همان که در عنوانِ ایمیل و `From` می‌نشیند. */
const APPS = Object.freeze({
  shop: { id: 'shop', displayName: 'برنامهٔ دکان' },
  pump: { id: 'pump', displayName: 'برنامهٔ پمپ' },
});

/** `dukan` و `pomp`ِ پرامپت هم به همان دو بخش می‌رسند. */
function appOf(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (s === 'dukan' || s === 'dokan') return 'shop';
  if (s === 'pomp' || s === 'pomp-app') return 'pump';
  return sectionOf(s) || '';
}

// ---------- نرمال‌سازی ----------

const FA = '۰۱۲۳۴۵۶۷۸۹', AR = '٠١٢٣٤٥٦٧٨٩';
function faToEn(s) {
  return String(s || '')
    .replace(/[۰-۹]/g, d => String(FA.indexOf(d)))
    .replace(/[٠-٩]/g, d => String(AR.indexOf(d)));
}
/** فقط رقم‌ها، با ارقامِ فارسی/عربی به انگلیسی. */
function toEnDigits(s) { return faToEn(s).replace(/\D/g, ''); }
/** Trim، حروفِ کوچک، ارقامِ فارسی به انگلیسی. */
function normEmail(e) { return faToEn(e).trim().toLowerCase(); }
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
function isEmail(e) { return EMAIL_RE.test(e) && e.length <= 254; }

/** `ahmad@gmail.com` ⇒ `ah***@gmail.com` */
function mask(email) {
  const at = email.indexOf('@');
  if (at < 0) return '***';
  const name = email.slice(0, at), host = email.slice(at + 1);
  return `${name.slice(0, name.length > 2 ? 2 : 1)}***@${host}`;
}

// ---------- کد: ساخت، هش، مهروموم ----------

function pepper() { return config.secrets.otp || config.secrets.api || 'shop-otp-pepper'; }
function genCode() { return String(randomInt(0, 1_000_000)).padStart(6, '0'); }
function hashCode(app, email, code) {
  return createHmac('sha256', pepper()).update(`${app}\n${email}\n${code}`).digest('hex');
}
function safeEq(a, b) {
  const ba = Buffer.from(String(a)), bb = Buffer.from(String(b));
  return ba.length === bb.length && timingSafeEqual(ba, bb);
}
function sealKey() { return createHash('sha256').update(`${pepper()}:login-code-seal`).digest(); }
function seal(code) {
  const iv = randomBytes(12);
  const c = createCipheriv('aes-256-gcm', sealKey(), iv);
  const ct = Buffer.concat([c.update(String(code), 'utf8'), c.final()]);
  return `${iv.toString('base64url')}.${c.getAuthTag().toString('base64url')}.${ct.toString('base64url')}`;
}
function unseal(sealed) {
  if (!sealed) return '';
  const [iv, tag, ct] = String(sealed).split('.');
  try {
    const d = createDecipheriv('aes-256-gcm', sealKey(), Buffer.from(iv, 'base64url'));
    d.setAuthTag(Buffer.from(tag, 'base64url'));
    return Buffer.concat([d.update(Buffer.from(ct, 'base64url')), d.final()]).toString('utf8');
  } catch { return ''; }
}

/** `req_` + زمان (base36) + ۱۲ بایت تصادفی — یکتا و تقریباً مرتب. */
function newRequestId() {
  return `req_${Date.now().toString(36)}${randomBytes(12).toString('base64url').replace(/[^A-Za-z0-9]/g, 'x')}`;
}

// ---------- پاسخ‌های قرارداد ----------

function fail(status, error, message, retryAfter = 0, extra = {}) {
  return { ok: false, status, error, message, retry_after: retryAfter, ...extra };
}

// ---------- قفل و سقف‌ها ----------

async function lockOf(app, email) {
  const row = await one('SELECT locked_until FROM login_locks WHERE app=$1 AND email=$2', [app, email]);
  if (!row) return 0;
  const left = Math.ceil((Number(row.locked_until) - now()) / 1000);
  if (left <= 0) {
    await query('DELETE FROM login_locks WHERE app=$1 AND email=$2', [app, email]).catch(() => {});
    return 0;
  }
  return left;
}

async function lock(app, email, seconds = LOCK_S) {
  const t = now();
  await query(
    `INSERT INTO login_locks (app, email, locked_until, reason, created_at) VALUES ($1,$2,$3,'too_many_wrong_codes',$4)
     ON CONFLICT (app, email) DO UPDATE SET locked_until=excluded.locked_until, created_at=excluded.created_at`,
    [app, email, t + seconds * 1000, t]
  );
}

async function unlock(app, email) {
  const r = await query('DELETE FROM login_locks WHERE app=$1 AND email=$2', [app, email]);
  //  و شمارندهٔ کدهای غلطِ درخواستِ زنده هم صفر می‌شود
  await query(
    `UPDATE login_requests SET attempts=0 WHERE app=$1 AND email=$2 AND consumed_at IS NULL AND superseded_at IS NULL`,
    [app, email]
  );
  return r.rowCount > 0;
}

/** چند درخواست در پنجره، و اولینِ آن‌ها کِی بود — برای `retry_after`ِ دقیق. */
async function windowCount(where, args, windowS) {
  const since = now() - windowS * 1000;
  const r = await one(
    `SELECT COUNT(*)::int AS n, MIN(created_at) AS first FROM login_requests WHERE ${where} AND created_at > $${args.length + 1}`,
    [...args, since]
  );
  const retry = r.first ? Math.max(1, Math.ceil((Number(r.first) + windowS * 1000 - now()) / 1000)) : windowS;
  return { n: r.n, retry };
}

// ---------- درخواستِ کد ----------

/**
 * @returns {{ok:true, request_id, expires_in, resend_after, masked_email, code}}  (`code` فقط برای صف — بیرون نمی‌رود)
 *          | {ok:false, status, error, message, retry_after}
 */
async function requestCode({ app, email, ip = '', deviceId = '', deviceName = '', appVersion = '', clientRequestId = '' }) {
  if (!APPS[app]) return fail(400, 'APP_UNKNOWN', 'برنامه شناخته نشد.');
  email = normEmail(email);
  if (!isEmail(email)) return fail(400, 'INVALID_EMAIL', 'ایمیل معتبر نیست.');

  const ipHits = ip ? await windowCount('ip=$1', [ip], IP_WINDOW_S) : { n: 0 };
  if (ipHits.n >= IP_MAX) return fail(429, 'RATE_LIMITED', 'درخواست زیاد است.', ipHits.retry);
  const emailHits = await windowCount('app=$1 AND email=$2', [app, email], EMAIL_WINDOW_S);
  if (emailHits.n >= EMAIL_MAX) return fail(429, 'RATE_LIMITED', 'برای این ایمیل زیاد کد گرفته‌اید.', emailHits.retry);

  const locked = await lockOf(app, email);
  if (locked > 0) return fail(423, 'LOCKED', 'حساب موقتاً قفل است.', locked);

  const last = await one(
    'SELECT created_at FROM login_requests WHERE app=$1 AND email=$2 ORDER BY created_at DESC LIMIT 1', [app, email]
  );
  if (last) {
    const wait = Math.ceil((Number(last.created_at) + RESEND_S * 1000 - now()) / 1000);
    if (wait > 0) return fail(429, 'RATE_LIMITED', `لطفاً ${wait} ثانیه صبر کنید.`, wait);
  }

  const t = now();
  //  کدِ قبلی باطل می‌شود — همیشه فقط یک کدِ فعال
  await query(
    `UPDATE login_requests SET superseded_at=$3, code_sealed=''
      WHERE app=$1 AND email=$2 AND consumed_at IS NULL AND superseded_at IS NULL`,
    [app, email, t]
  );

  const code = genCode();
  const request_id = newRequestId();
  await query(
    `INSERT INTO login_requests (request_id, app, email, code_hash, code_sealed, attempts, max_attempts, created_at, expires_at,
                                 device_id, device_name, app_version, ip, client_request_id)
     VALUES ($1,$2,$3,$4,$5,0,$6,$7,$8,$9,$10,$11,$12,$13)`,
    [request_id, app, email, hashCode(app, email, code), seal(code), MAX_WRONG, t, t + CODE_TTL_S * 1000,
      String(deviceId || '').slice(0, 64), String(deviceName || '').slice(0, 80), String(appVersion || '').slice(0, 32),
      String(ip || '').slice(0, 64), String(clientRequestId || '').slice(0, 64)]
  );
  //  میزِ «ورودها» و «کدهای زنده»ی پنل همان لحظه می‌بینندش
  notifyPanel('logins');
  notifyPanel('codes');
  return {
    ok: true, request_id, email, expires_in: CODE_TTL_S, resend_after: RESEND_S, masked_email: mask(email), code,
  };
}

/** اگر صف نپذیرفت، همان ردیف می‌رود تا کاربر ۶۰ ثانیه پشتِ چیزی که نرفته نماند. */
async function discard(requestId) {
  await query('DELETE FROM login_requests WHERE request_id=$1', [requestId]).catch(() => {});
}

// ---------- سنجشِ کد ----------

/**
 * @returns {{ok:true, email, request}} | {ok:false, status, error, message, retry_after, attempts_left?}
 */
async function verifyCode({ app, requestId, code }) {
  if (!APPS[app]) return fail(400, 'APP_UNKNOWN', 'برنامه شناخته نشد.');
  const clean = toEnDigits(code);
  if (clean.length !== 6) return fail(400, 'CODE_WRONG', 'کد باید ۶ رقم باشد.');

  const rec = await one('SELECT * FROM login_requests WHERE request_id=$1', [String(requestId || '').slice(0, 80)]);
  if (!rec) return fail(400, 'CODE_EXPIRED', 'کد منقضی شده یا نامعتبر است. کد جدید بگیرید.');
  if (rec.app !== app) return fail(400, 'REQUEST_NOT_FOUND', 'درخواست نامعتبر است.');

  const locked = await lockOf(app, rec.email);
  if (locked > 0) return fail(423, 'LOCKED', 'حساب موقتاً قفل است.', locked);

  const t = now();
  if (rec.consumed_at || rec.superseded_at || Number(rec.expires_at) < t) {
    return fail(400, 'CODE_EXPIRED', 'کد منقضی شده است. کد جدید بگیرید.');
  }

  if (!safeEq(rec.code_hash, hashCode(app, rec.email, clean))) {
    const attempts = Number(rec.attempts) + 1;
    if (attempts >= Number(rec.max_attempts || MAX_WRONG)) {
      await query(
        `UPDATE login_requests SET attempts=$2, superseded_at=$3, code_sealed='' WHERE request_id=$1`,
        [rec.request_id, attempts, t]
      );
      await lock(app, rec.email);
      return fail(423, 'LOCKED', '۵ بار اشتباه — ۱۵ دقیقه قفل شد.', LOCK_S, { attempts_left: 0 });
    }
    await query('UPDATE login_requests SET attempts=$2 WHERE request_id=$1', [rec.request_id, attempts]);
    return fail(400, 'CODE_WRONG', 'کد اشتباه است.', 0, { attempts_left: Number(rec.max_attempts || MAX_WRONG) - attempts });
  }

  //  یک‌بارمصرف: مصرف شد و کدِ مهروموم هم پاک می‌شود
  await query(
    `UPDATE login_requests SET consumed_at=$2, code_sealed='' WHERE request_id=$1`, [rec.request_id, t]
  );
  //  «مصرف شد» هم یک تغییر است — چراغِ همان ردیف روی صفحه عوض می‌شود
  notifyPanel('logins');
  notifyPanel('codes');
  return { ok: true, email: rec.email, request: rec };
}

// ---------- برای پنل ----------

function shape(r) {
  const t = now();
  const active = !r.consumed_at && !r.superseded_at && Number(r.expires_at) > t;
  return {
    request_id: r.request_id, app: r.app, email: r.email, masked_email: mask(r.email),
    created_at: Number(r.created_at), expires_at: Number(r.expires_at),
    consumed_at: r.consumed_at ? Number(r.consumed_at) : null,
    superseded_at: r.superseded_at ? Number(r.superseded_at) : null,
    active, code_attempts: Number(r.attempts), max_attempts: Number(r.max_attempts),
    device_id: r.device_id, device_name: r.device_name, app_version: r.app_version, ip: r.ip,
    client_request_id: r.client_request_id,
    revealed_at: r.revealed_at ? Number(r.revealed_at) : null,
    //  از صف
    state: r.status || 'queued', reason: r.reason || '', send_attempts: Number(r.send_attempts || 0),
    sent_at: r.sent_at ? Number(r.sent_at) : null, last_error: r.last_error || '',
  };
}

async function listRequests({ email = '', app = '', limit = 50 } = {}) {
  const args = [];
  const where = [];
  if (email) { args.push(normEmail(email)); where.push(`r.email=$${args.length}`); }
  if (app) { args.push(app); where.push(`r.app=$${args.length}`); }
  args.push(Math.min(Math.max(1, Number(limit) || 50), 200));
  const rows = await many(
    `SELECT r.*, o.status, o.reason, o.attempts AS send_attempts, o.sent_at, o.last_error
       FROM login_requests r LEFT JOIN otp_outbox o ON o.id = r.request_id
      ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
      ORDER BY r.created_at DESC LIMIT $${args.length}`,
    args
  );
  const out = rows.map(shape);
  //  قفل‌ها هم کنارش، تا مدیر ببیند چرا مشتری نمی‌تواند
  const lockRows = await many(
    `SELECT app, email, locked_until FROM login_locks WHERE locked_until > $1 ${email ? 'AND email=$2' : ''}`,
    email ? [now(), normEmail(email)] : [now()]
  );
  const locks = lockRows.map(l => ({ app: l.app, email: l.email, masked_email: mask(l.email), locked_until: Number(l.locked_until) }));
  for (const r of out) r.locked = locks.some(l => l.app === r.app && l.email === r.email);
  return { requests: out, locks };
}

async function requestById(requestId) {
  return one('SELECT * FROM login_requests WHERE request_id=$1', [String(requestId || '').slice(0, 80)]);
}

/** کدِ فعلیِ یک درخواست — فقط برای مدیرِ کل و فقط تا وقتی زنده است. */
async function reveal(requestId, adminId) {
  const rec = await requestById(requestId);
  if (!rec) return null;
  const t = now();
  if (rec.consumed_at || rec.superseded_at || Number(rec.expires_at) < t) return { expired: true, request: shape(rec) };
  const code = unseal(rec.code_sealed);
  if (!code) return { expired: true, request: shape(rec) };
  await query('UPDATE login_requests SET revealed_at=$2, revealed_by=$3 WHERE request_id=$1', [rec.request_id, t, String(adminId || '')]);
  return { expired: false, code, request: shape(rec), expires_in: Math.ceil((Number(rec.expires_at) - t) / 1000) };
}

/** جاروکش: کدِ مهرومومِ منقضی پاک، ردیف‌های کهنه و قفل‌های تمام‌شده حذف. */
async function sweep() {
  const t = now();
  await query(`UPDATE login_requests SET code_sealed='' WHERE code_sealed <> '' AND (expires_at < $1 OR consumed_at IS NOT NULL OR superseded_at IS NOT NULL)`, [t]);
  await query('DELETE FROM login_locks WHERE locked_until < $1', [t]);
  await query('DELETE FROM login_requests WHERE created_at < $1', [t - KEEP_DAYS * 86400 * 1000]);
}

module.exports = {
  APPS, appOf, CODE_TTL_S, RESEND_S, LOCK_S, MAX_WRONG, IP_MAX, IP_WINDOW_S, EMAIL_MAX, EMAIL_WINDOW_S,
  toEnDigits, normEmail, isEmail, mask, genCode, hashCode, safeEq, seal, unseal,
  requestCode, discard, verifyCode, lockOf, lock, unlock,
  listRequests, requestById, reveal, shape, sweep, fail,
};
