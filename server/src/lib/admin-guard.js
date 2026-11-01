'use strict';
/**
 * دو قفلِ اختیاریِ نشستِ مدیر — هر دو **پیش‌فرض خاموش**، روشن کردن فقط با صاحبِ سامانه (F0-15).
 *
 * ── SEC-AUTH-12: تأییدِ دومرحله‌ایِ اجباری (`ADMIN_2FA_REQUIRED=1`) ──────────────
 * روشن ⇒ مدیری که هنوز TOTP ندارد با رمزِ درست وارد می‌شود، ولی نشستش **فقط** به درهای
 * «روشن کردنِ همان» می‌رسد (`ENROLL`): دیدنِ حال، ساختنِ کلید، روشن کردن، «من» و خروج. هر درِ
 * دیگر ⇒ `403 admin_2fa_enrollment_required`. این را در **هر درخواست** از ردیفِ زندهٔ مدیر
 * می‌سنجیم، نه از توکن: نشستی که پیش از روشن شدنِ پرچم باز بوده هم همان دم بسته می‌شود.
 *
 * ⛔ قفل شدنِ صاحبِ سامانه ممکن نیست: رمزِ درست همیشه به همان درهای روشن کردن می‌رسد، و راهِ
 * نجات (`ADMIN_BOOTSTRAP_PASSWORD`ِ تازه یا `scripts/create-admin.js`) رمز را برمی‌گرداند و
 * TOTPِ همان مدیر را خاموش می‌کند ⇒ ورود با رمز ⇒ روشن کردنِ دوباره.
 *
 * ── SEC-AUTH-13: تأییدِ دوباره برای کارِ حساسِ مدیر (`ADMIN_REAUTH=1`) ─────────────
 * روشن ⇒ هر **نوشتنِ** پشتِ `requireSuperAdmin` (مدیرها، تنظیم و رازها، پلن و قیمت، پیامک،
 * ایمیل، کلیدِ برنامه، پشتیبان، حذفِ کاربر…) و دانلودِ پشتیبانِ یک حساب فقط از نشستی پذیرفته
 * می‌شود که در `REAUTH_WINDOW_MIN` دقیقهٔ گذشته وارد شده یا با `POST /api/admin/reauth` رمز
 * (و کدِ TOTP اگر روشن است) را دوباره زده. نشانه همان ردیفِ `login_attempts`ِ `lib/reauth`
 * است، بسته به هشِ همین توکنِ مدیر. توکنِ ۱۲ساعتهٔ دزدیده‌شده بی رمز به این کارها نمی‌رسد.
 */
const config = require('../config');
const reauth = require('./reauth');
const { now } = require('../db');
const { forbidden } = require('../middleware/errors');

const ENROLL = new Set([
  'GET /admin/me', 'POST /admin/logout',
  'GET /admin/2fa', 'POST /admin/2fa/setup', 'POST /admin/2fa/enable',
]);
const ENROLL_MESSAGE = 'ورودِ دومرحله‌ای برای مدیرانِ این سرور اجباری است. اول از «ورودِ دومرحله‌ای» آن را روشن کنید.';
const REAUTH_MESSAGE = 'برای این کار، رمزِ مدیر (و کدِ برنامهٔ تأیید اگر روشن است) را دوباره بزنید.';

function twoFactorRequired() { return config.admin?.twoFactorRequired === true; }
function reauthRequired() { return config.admin?.reauthRequired === true; }

/** «/api/v1/admin/2fa?x» ⇒ «/admin/2fa» */
function pathOf(req) {
  const p = String(req.originalUrl || req.url || '').split('?')[0].replace(/\/+$/, '');
  return p.replace(/^\/api(?:\/v1)?(?=\/)/, '');
}

/** خطای ۴۰۳ اگر این مدیر هنوز TOTP ندارد و این در بیرون از درهای روشن کردن است؛ وگرنه null. */
function enrollmentBlock(req, admin) {
  if (!twoFactorRequired() || !admin || admin.totp_enabled_at) return null;
  if (ENROLL.has(`${req.method} ${pathOf(req)}`)) return null;
  return forbidden(ENROLL_MESSAGE, 'admin_2fa_enrollment_required');
}

/** نشستِ مدیر همین چند دقیقه احراز شده؟ پرچم خاموش ⇒ همیشه بله. */
async function assertFresh(req) {
  if (!reauthRequired()) return;
  const hash = req.tokenRow && req.tokenRow.token_hash;
  if (hash && await reauth.freshUntil(hash) > now()) return;
  throw forbidden(REAUTH_MESSAGE, 'admin_reauth_required');
}

module.exports = { ENROLL, twoFactorRequired, reauthRequired, enrollmentBlock, assertFresh, pathOf };
