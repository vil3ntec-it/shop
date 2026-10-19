'use strict';
/**
 * تأییدِ دوبارهٔ هویت برای کارهای حساس — بندِ ۲۱.۱ («تأیید دوباره برای
 * عملیات حساس») و AUTH-08.
 *
 * ── مسئله ──────────────────────────────────────────────────────────
 * توکنِ دسترسی یک ساعت و توکنِ تازه‌سازی نود روز زنده است. کسی که گوشیِ
 * بازِ صاحبِ دکان را یک دقیقه دست بگیرد، تا امروز می‌توانست همهٔ دستگاه‌های
 * او را بیرون کند، برای حسابِ بی‌رمز رمز بگذارد (و حساب را از آنِ خودش
 * کند)، شماره‌ای به حساب بیفزاید یا مجوزِ کارمندان را عوض کند.
 *
 * ── قاعده ──────────────────────────────────────────────────────────
 * این کارها فقط از نشستی پذیرفته می‌شوند که در چند دقیقهٔ گذشته
 * (`config.tokens.reauthWindowMs`، پیش‌فرض ده) **احراز شده** باشد:
 *   • همین حالا وارد شده (هر ورودی که از `issueSession` می‌گذرد)، یا
 *   • با `POST /api/auth/reauth` رمز یا کدِ ایمیل/پیامک را دوباره زده.
 * تازه‌سازیِ توکن احراز نیست — نشستی که فقط تازه شده باید دوباره تأیید کند.
 *
 * ── کجا نگه داشته می‌شود ───────────────────────────────────────────
 * ردیفی در `login_attempts` با `scope='session_auth'` و شناسهٔ **هشِ توکنِ
 * دسترسی** (خودِ توکن هرگز). همان جدولِ رویدادهای ورود، با همان پاک‌سازیِ
 * هفت‌روزه (`db.js`) و همان نمایه — بی جدول یا مهاجرتِ تازه. به توکنِ
 * دسترسیِ همین نشست بسته است، پس در دستگاهِ دیگر یا با توکنِ دیگر کار نمی‌کند.
 *
 * ⚠️ بازگشت: `AUTH_REAUTH=off` همه را مثلِ پیش از این تغییر باز می‌کند.
 */
const config = require('../config');
const { query, one, now } = require('../db');
const { hashToken } = require('./tokens');
const { forbidden, unauthorized } = require('../middleware/errors');

const SCOPE = 'session_auth';
const MESSAGE = 'برای امنیتِ حساب، این کار فقط چند دقیقه پس از ورود ممکن است. رمز (یا کدِ ایمیل) را دوباره بزنید، یا یک بار خارج و دوباره وارد شوید.';

function enforced() { return config.tokens.reauthEnforced !== false; }
function windowMs() { return Math.max(60_000, Number(config.tokens.reauthWindowMs) || 600_000); }

/** این نشست همین حالا احراز شد (ورود یا تأییدِ دوباره). */
async function markFresh({ accessToken = '', tokenHash = '', ip = '' }) {
  const id = tokenHash || (accessToken ? hashToken(accessToken) : '');
  if (!id) return;
  await query(
    'INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ($1,$2,$3,true,$4)',
    [SCOPE, id, String(ip || '').slice(0, 64), now()]
  );
}

/** تا کِی این نشست «تازه» است — صفر یعنی نیست. */
async function freshUntil(tokenHash) {
  if (!tokenHash) return 0;
  const row = await one(
    `SELECT MAX(created_at) AS at FROM login_attempts
      WHERE scope=$1 AND identifier=$2 AND ok=true AND created_at > $3`,
    [SCOPE, tokenHash, now() - windowMs()]
  );
  return row && row.at ? Number(row.at) + windowMs() : 0;
}

/** خطای روشن با راهِ حل — برنامه از روی `code` پنجرهٔ رمز را باز می‌کند. */
function required() {
  return forbidden(MESSAGE, 'reauth_required');
}

/** همان بررسی، برای جاهایی که فقط بخشی از درخواست حساس است. */
async function assertFresh(req) {
  if (!enforced()) return;
  if (!req.tokenRow) throw unauthorized();
  if (await freshUntil(req.tokenRow.token_hash) > now()) return;
  throw required();
}

/** میان‌افزار — پس از `requireUser`/`requireAnyUser`. */
function requireRecentAuth(req, res, next) {
  assertFresh(req).then(() => next(), next);
}

module.exports = { markFresh, freshUntil, assertFresh, requireRecentAuth, enforced, windowMs, SCOPE, MESSAGE };
