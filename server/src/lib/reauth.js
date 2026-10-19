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
 *   • همین حالا وارد شده (هر ورودی که از `issueSession` می‌گذرد، و ورود با
 *     کدِ ایمیل در `routes/app-auth.js`)، یا
 *   • با `POST /api/auth/reauth` رمز یا کدِ ایمیل/پیامک را دوباره زده.
 * تازه‌سازیِ توکن احراز نیست — نشستی که فقط تازه شده باید دوباره تأیید کند.
 *
 * ── کجا نگه داشته می‌شود ───────────────────────────────────────────
 * ردیفی در `login_attempts` با `scope='session_auth'` و شناسهٔ **هشِ توکنِ
 * دسترسی** (خودِ توکن هرگز). همان جدولِ رویدادهای ورود، با همان پاک‌سازیِ
 * هفت‌روزه (`db.js`) و همان نمایه — بی جدول یا مهاجرتِ تازه. به توکنِ
 * دسترسیِ همین نشست بسته است، پس در دستگاهِ دیگر یا با توکنِ دیگر کار نمی‌کند.
 *
 * ── فقط نشستِ برنامهٔ دکان ─────────────────────────────────────────
 * توکن بخشِ خودش را دارد (`tokens.app`، و `req.appSection` از همان ردیف).
 * پنجرهٔ «رمز را دوباره بزنید» فقط در برنامه‌های دکان هست (`ReauthHost`).
 * پمپ برنامه‌ای جداست و چنین پنجره‌ای ندارد؛ پس روی مسیرهای مشترکِ
 * `/api/auth/*` (`requireAnyUser`: خروج از همه) نشستِ پمپ یا هر بخشِ دیگر
 * **همان رفتارِ پیش از این تغییر** را دارد — جز «گذاشتنِ نخستین رمز» که در
 * هر بخش تازگی می‌خواهد (`assertFresh(req, { allSections: true })`). `/api/me` و
 * `/api/shop/*` پشتِ `requireUser`اند و از اصل فقط توکنِ دکان را می‌پذیرند.
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

/** بخش‌هایی که پنجرهٔ تأییدِ دوباره دارند — بخشِ ناشناخته/خالی یعنی دکان (پیش‌فرضِ توکن) */
const APPS = new Set(['shop']);
function appliesTo(req) { return APPS.has(req.appSection || 'shop'); }
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

/**
 *  همان بررسی، برای جاهایی که فقط بخشی از درخواست حساس است.
 *
 *  `allSections`: برای کارهایی که **خودِ حساب** را (نه داده‌ی یک بخش را)
 *  می‌گیرند و در هر بخشی باید تازه باشند — امروز فقط «گذاشتنِ نخستین رمز».
 *  ⛔ بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷: توکنِ کهنهٔ پمپ روی حسابِ مشترکِ بی‌رمز
 *  نخستین رمز را می‌گذاشت و همان رمز برنامهٔ دکان را باز می‌کرد. نشستِ پمپ
 *  که همین حالا وارد شده «تازه» است (`issueSession` و `app-auth` هر ورودی را
 *  `markFresh` می‌کنند)، و کدِ ایمیل از `/api/auth/reauth` در هر بخش کار
 *  می‌کند؛ پس جریانِ عادیِ پمپ دست نمی‌خورد.
 */
async function assertFresh(req, { allSections = false } = {}) {
  if (!enforced() || (!allSections && !appliesTo(req))) return;
  if (!req.tokenRow) throw unauthorized();
  if (await freshUntil(req.tokenRow.token_hash) > now()) return;
  throw required();
}

/** میان‌افزار — پس از `requireUser`/`requireAnyUser`. */
function requireRecentAuth(req, res, next) {
  assertFresh(req).then(() => next(), next);
}

module.exports = { markFresh, freshUntil, assertFresh, requireRecentAuth, enforced, appliesTo, windowMs, SCOPE, MESSAGE };
