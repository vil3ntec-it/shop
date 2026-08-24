'use strict';
/**
 * هش رمز عبور با scrypt (داخل Node، بدون وابستگی بیرونی).
 * قالب ذخیره: scrypt$N$r$p$saltB64$hashB64
 */
const { randomBytes, scrypt, timingSafeEqual } = require('crypto');
const { promisify } = require('util');
const scryptAsync = promisify(scrypt);

const N = 16384, R = 8, P = 1, KEYLEN = 32, SALTLEN = 16;
const MAX_MEM = 64 * 1024 * 1024; // scrypt پیش‌فرض ۳۲ مگ است و برای N=16384 کم می‌آید

async function hashPassword(plain) {
  if (typeof plain !== 'string' || plain.length < 8) {
    throw new Error('رمز عبور باید حداقل ۸ کاراکتر باشد');
  }
  const salt = randomBytes(SALTLEN);
  const hash = await scryptAsync(plain, salt, KEYLEN, { N, r: R, p: P, maxmem: MAX_MEM });
  return `scrypt$${N}$${R}$${P}$${salt.toString('base64')}$${Buffer.from(hash).toString('base64')}`;
}

async function verifyPassword(plain, stored) {
  try {
    if (typeof plain !== 'string' || typeof stored !== 'string') return false;
    const parts = stored.split('$');
    if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
    const [, n, r, p, saltB64, hashB64] = parts;
    const salt = Buffer.from(saltB64, 'base64');
    const expected = Buffer.from(hashB64, 'base64');
    const actual = Buffer.from(
      await scryptAsync(plain, salt, expected.length, { N: +n, r: +r, p: +p, maxmem: MAX_MEM })
    );
    if (actual.length !== expected.length) return false;
    return timingSafeEqual(actual, expected);
  } catch {
    return false;
  }
}

/** بررسی حداقل قدرت رمز — جلوی رمزهای بدیهی را می‌گیرد. */
function checkStrength(plain) {
  if (typeof plain !== 'string' || plain.length < 8) return 'رمز عبور باید حداقل ۸ کاراکتر باشد';
  if (/^\d+$/.test(plain)) return 'رمز عبور نباید فقط عدد باشد';
  const weak = ['password', '12345678', 'qwertyui', 'admin123', '11111111'];
  if (weak.includes(plain.toLowerCase())) return 'این رمز عبور بسیار ساده است';
  return null;
}

module.exports = { hashPassword, verifyPassword, checkStrength };
