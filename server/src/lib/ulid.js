'use strict';
/**
 * ULID — شناسهٔ ردیف‌های تازه (بخشِ ۳.۴: «شناسهٔ جدید فقط ULID است»).
 *
 * ۲۶ نویسهٔ Crockford Base32: ۱۰ نویسهٔ زمان (۴۸ بیت میلی‌ثانیه) + ۱۶ نویسهٔ
 * تصادفی (۸۰ بیت از `crypto`). به ترتیبِ ساخت مرتب می‌شود، پس ایندکسِ
 * کلیدِ اصلی پراکنده نمی‌شود.
 */
const { randomBytes } = require('crypto');

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

function ulid(at = Date.now()) {
  let t = Math.floor(at);
  let time = '';
  for (let i = 0; i < 10; i++) {
    time = ALPHABET[t % 32] + time;
    t = Math.floor(t / 32);
  }
  const bytes = randomBytes(16);
  let rand = '';
  for (let i = 0; i < 16; i++) rand += ALPHABET[bytes[i] % 32];
  return time + rand;
}

module.exports = { ulid, ALPHABET };
