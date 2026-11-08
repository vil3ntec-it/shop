'use strict';
/**
 * توکنِ سرآیندِ `Authorization: Bearer <token>` — پارسرِ خطی با سقفِ طول، بی regex ِ عقب‌گرد.
 *
 * ⛔ پیش‌تر `/^Bearer\s+(.+)$/i`: `\s+` و `.+` هر دو فاصله را می‌گیرند، پس «Bearer» + هزاران فاصله + یک
 *    پایانِ خط زمانِ درجهٔ دو می‌سوزاند (CodeQL `js/polynomial-redos`؛ ۱۶هزار فاصله ≈ ۰٫۲ ثانیه برای هر
 *    درخواست). Node پایانِ خطِ خام را در سرآیند نمی‌پذیرد، ولی این قاعده به آن تکیه نمی‌کند.
 *    خروجی برای هر سرآیندِ درست همان پیشین است (آزمونِ تفاضلیِ `codeql-hardening`): «bearer» با هر
 *    بزرگی + دست‌کم یک فاصله + بقیهٔ trim‌شده که پایانِ خط ندارد (همان `.` ِ regex)؛ وگرنه null.
 */

/** سرآیندِ بلندتر از این توکن نیست (توکنِ نشست چند ده نویسه است؛ کلِ سرآیندهای Node ۱۶KB). */
const MAX_HEADER = 8192;
const LINE_END = /[\n\r\u2028\u2029]/;

/** @returns {string|null} */
function bearerToken(header) {
  if (typeof header !== 'string' || header.length > MAX_HEADER) return null;
  const s = header.trim();
  //  «bearer» + یک فاصله + دست‌کم یک نویسه؛ `trim` و `\s` یک مجموعه‌اند
  if (s.length < 8 || s.slice(0, 6).toLowerCase() !== 'bearer' || s[6].trim() !== '') return null;
  const token = s.slice(7).trim();
  return token && !LINE_END.test(token) ? token : null;
}

module.exports = { bearerToken, MAX_HEADER };
