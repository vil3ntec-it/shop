'use strict';
/**
 * مسیرِ درخواست برای لاگ — بی توکن (بازبینیِ حریم).
 *
 * توکنِ پیگیریِ سفارش (`/api/store/:slug/orders/<ULID>.<HMAC>`)، توکنِ پرونده،
 * لینکِ دعوت و هر چیزِ مشابه در **مسیر** است، نه در querystring؛ لاگِ ۴xx،
 * کند و ۵xx آن را کامل می‌نوشت و هر کس به لاگ دسترسی داشت سفارش یا پرونده
 * را باز می‌کرد. قاعده عمومی است، نه فهرستِ مسیر: هر بخشی که شبیهِ توکن،
 * شناسهٔ بلند یا شمارهٔ تلفن است `:tok` می‌شود؛ نامِ مسیر، نشانیِ کوتاهِ
 * ویترین و شناسهٔ کوتاه می‌مانند تا لاگ هنوز به کار بیاید. querystring هرگز.
 */

const MAX = 120;

function decode(seg) {
  try { return decodeURIComponent(seg); } catch { return seg; }
}

/** آیا این بخشِ مسیر توکن‌مانند است؟ */
function tokenLike(raw) {
  const s = decode(raw);
  if (!s) return false;
  //  شمارهٔ تلفن یا عددِ بلند
  if (/\d{9,}/.test(s.replace(/[\s+\-()]/g, ''))) return true;
  if (s.length < 16) return false;
  //  base64url / هگز / ULID / JWT: حرف و رقم با هم
  if (/[A-Za-z]/.test(s) && /\d/.test(s)) return true;
  //  base64 ِ بلندِ بی رقم: حروفِ کوچک و بزرگ با هم (نامِ مسیرها کوچک‌اند)
  return s.length >= 24 && /[a-z]/.test(s) && /[A-Z]/.test(s);
}

/** مسیرِ بی querystring، با بخش‌های توکن‌مانندِ `:tok`، حداکثر ۱۲۰ نویسه. */
function redactPath(url) {
  const path = String(url || '').split('?')[0].split('#')[0];
  return path.split('/').map((seg) => (tokenLike(seg) ? ':tok' : seg)).join('/').slice(0, MAX);
}

module.exports = { redactPath, tokenLike };
