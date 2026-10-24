'use strict';
/**
 * توکن و نشانیِ QRِ هر فاکتور (فاکتورِ نسخهٔ ۲) — همتای `core/receipt/ReceiptToken.kt`.
 * هر دو با `core/test-vectors/receipt-v1.json` سنجیده می‌شوند؛ رفتارِ یکی را بی
 * دیگری و بی فایلِ مرجع عوض نکنید.
 *
 *   QR = `<نشانیِ سرورِ برنامه>/r/<نشانِ دکان>/<توکن>`
 *
 * • توکن: ۱۲۸ بیتِ تصادفی (SecureRandom ِ دستگاه، هنگامِ ثبتِ فروش) به ۲۶ نویسهٔ
 *   Crockford base32 ِ کوچک — عددِ ۱۲۸ بیتی، بزرگ‌مرتبه اول، دو بیتِ صفر در سر؛
 *   پس نویسهٔ اول همیشه `0`..`7` است. روی `Sale.receiptToken` می‌نشیند و همگام می‌شود.
 * • نشانِ دکان: ۸ نویسهٔ اولِ همان base32 از `sha256("vill3n.receipt.shop.v1:" + shopId)`
 *   (۴۰ بیتِ اول). دستگاه بی اینترنت می‌سازدش و سرور با آن **فقط** فروشِ همان دکان
 *   را برمی‌گرداند: کسی که توکنِ رسیدِ دیگری را در فروشِ دکانِ خودش بگذارد نشانِ
 *   دکانِ خودش را دارد، نه نشانِ دکانِ صاحبِ رسید.
 */
const { createHash } = require('crypto');

const ALPHABET = '0123456789abcdefghjkmnpqrstvwxyz';
const TOKEN_LENGTH = 26;
const TOKEN_BYTES = 16;
const TAG_LENGTH = 8;
const TAG_BYTES = 5;
const TAG_SALT = 'vill3n.receipt.shop.v1:';
const TOKEN_RE = /^[0-7][0-9a-hjkmnp-tv-z]{25}$/;
const TAG_RE = /^[0-9a-hjkmnp-tv-z]{8}$/;
const PATH_PREFIX = '/r/';

/** بایت‌ها ⇒ `chars` نویسهٔ base32، بزرگ‌مرتبه اول (بیت‌های کم‌شده صفرِ سرند). */
function encode(buf, chars) {
  let n = 0n;
  for (const b of buf) n = (n << 8n) | BigInt(b);
  let out = '';
  for (let i = 0; i < chars; i++) {
    out = ALPHABET[Number(n & 31n)] + out;
    n >>= 5n;
  }
  if (n !== 0n) throw new RangeError('بیت‌ها در این تعداد نویسه جا نمی‌شوند');
  return out;
}

/** ۱۶ بایت ⇒ توکنِ ۲۶ نویسه‌ای */
function encodeToken(bytes) {
  if (!bytes || bytes.length !== TOKEN_BYTES) throw new RangeError('توکن ۱۶ بایت است');
  return encode(bytes, TOKEN_LENGTH);
}

/** متنِ اسکن‌شده ⇒ توکنِ یکدست، یا null. اسکنرِ «همه بزرگ» هم پذیرفته است. */
function normalizeToken(raw) {
  const s = String(raw == null ? '' : raw).trim().toLowerCase();
  return TOKEN_RE.test(s) ? s : null;
}

function normalizeTag(raw) {
  const s = String(raw == null ? '' : raw).trim().toLowerCase();
  return TAG_RE.test(s) ? s : null;
}

/** نشانِ ۸ نویسه‌ایِ دکان — بی راز، فقط برای جدا کردنِ دکان‌ها */
function shopTag(shopId) {
  const digest = createHash('sha256').update(TAG_SALT + String(shopId), 'utf8').digest();
  return encode(digest.subarray(0, TAG_BYTES), TAG_LENGTH);
}

/** مسیرِ صفحهٔ عمومی: `/r/<نشان>/<توکن>` */
function receiptPath(shopId, token) {
  return `${PATH_PREFIX}${shopTag(shopId)}/${token}`;
}

/** نشانیِ کاملِ QR — ریشه بی `/` ِ آخر */
function receiptUrl(base, shopId, token) {
  return String(base).replace(/\/+$/, '') + receiptPath(shopId, token);
}

module.exports = {
  ALPHABET, TOKEN_LENGTH, TAG_LENGTH, PATH_PREFIX,
  encodeToken, normalizeToken, normalizeTag, shopTag, receiptPath, receiptUrl,
};
