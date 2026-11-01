'use strict';
/**
 * هش رمز عبور با scrypt (داخل Node، بدون وابستگی بیرونی).
 * قالب ذخیره: scrypt$N$r$p$saltB64$hashB64
 *
 * ── SEC-AUTH-01 (S20ِ ممیزی): هزینه به کفِ OWASP رسید ─────────────
 * تا ۲.۱۴.۴ `N=2^14, r=8, p=1` بود — یک‌پنجمِ کفِ OWASP. کفِ امروزِ OWASP
 * برای scrypt «`N=2^17, r=8, p=1` یا هم‌ارزش» است و یکی از هم‌ارزهای رسمی
 * `N=2^14, r=8, p=5`. همین را برگزیدیم: همان ۱۶ مگابایت حافظه برای هر هش
 * (سرورِ خانگی با چند ورودِ هم‌زمان ۱۲۸ مگابایت را تاب نمی‌آورد)، پنج برابر
 * کارِ CPU (~۲۰۰ms).
 *
 * پارامترها در خودِ رشته‌اند، پس هش‌های کهنه همان‌طور سنجیده می‌شوند و
 * نسخهٔ پیشین هم هش‌های تازه را می‌خواند (برگشت بی خطر است). هشِ کهنه پس
 * از نخستین ورودِ درست بازنویسی می‌شود (`upgrade`).
 *
 * `PASSWORD_SCRYPT_P` فقط برای تندیِ آزمون‌هاست؛ در production هرگز کمتر از
 * پیش‌فرض پذیرفته نمی‌شود.
 */
const { randomBytes, scrypt, timingSafeEqual } = require('crypto');
const { promisify } = require('util');
const scryptAsync = promisify(scrypt);

const N = 16384, R = 8, KEYLEN = 32, SALTLEN = 16;
const DEFAULT_P = 5;
const MAX_MEM = 64 * 1024 * 1024; // scrypt پیش‌فرض ۳۲ مگ است و برای N=16384 کم می‌آید

/**
 *  p ِ امروز، ۱ تا ۱۶. کمتر از پیش‌فرض فقط با `NODE_ENV`ِ صریحِ test/development —
 *  سرورِ بی `NODE_ENV` هم production حساب می‌شود (همان قاعدهٔ `config.devCodes`).
 */
function currentP() {
  const raw = Number(process.env.PASSWORD_SCRYPT_P);
  const asked = Number.isInteger(raw) && raw >= 1 && raw <= 16 ? raw : DEFAULT_P;
  const dev = ['development', 'test'].includes(String(process.env.NODE_ENV || '').trim());
  return dev ? asked : Math.max(DEFAULT_P, asked);
}

async function hashPassword(plain) {
  if (typeof plain !== 'string' || plain.length < 8) {
    throw new Error('رمز عبور باید حداقل ۸ کاراکتر باشد');
  }
  const P = currentP();
  const salt = randomBytes(SALTLEN);
  const hash = await scryptAsync(plain, salt, KEYLEN, { N, r: R, p: P, maxmem: MAX_MEM });
  return `scrypt$${N}$${R}$${P}$${salt.toString('base64')}$${Buffer.from(hash).toString('base64')}`;
}

/** هشِ ذخیره‌شده از هزینهٔ امروز ارزان‌تر است؟ (قالبِ ناشناخته ⇒ نه؛ دست نمی‌خورد) */
function needsRehash(stored) {
  const parts = typeof stored === 'string' ? stored.split('$') : [];
  if (parts.length !== 6 || parts[0] !== 'scrypt') return false;
  const [, n, r, p] = parts.map(Number);
  return n < N || r < R || p < currentP();
}

/**
 *  پس از ورودِ **درست**: اگر هش ارزان‌تر از امروز است، هشِ تازه برمی‌گردد
 *  (صدا زننده آن را شرطی، فقط اگر هش در این میان عوض نشده، می‌نویسد).
 */
async function upgrade(plain, stored) {
  if (!needsRehash(stored)) return null;
  try { return await hashPassword(plain); } catch { return null; }
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

/**
 *  همان‌قدر وقت گرفتن، وقتی حسابی در کار نیست.
 *
 *  ── چه چیزی را می‌بندد ─────────────────────────────────────────────
 *  مسیرِ ورود برای ایمیلِ ناشناس و رمزِ غلط یک پیام می‌دهد، ولی یک
 *  **زمان** نمی‌داد: اگر حساب نبود، هیچ scrypt‌ای اجرا نمی‌شد و پاسخ
 *  چند میلی‌ثانیه‌ای برمی‌گشت؛ اگر حساب بود، scrypt اجرا می‌شد و پاسخ
 *  ده‌ها برابر دیرتر می‌آمد. یعنی همان چیزی که پیامِ یکسان پنهان کرده
 *  بود، ساعتِ پاسخ لو می‌داد: هر کسی با یک فهرست ایمیل می‌توانست
 *  بفهمد کدام‌یک روی این سرور حساب دارد.
 *
 *  حالا وقتی حسابی پیدا نشد، همان scrypt روی یک هشِ ساختگی اجرا
 *  می‌شود. نتیجه‌اش دور ریخته می‌شود؛ فقط زمانش به کار می‌آید.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  هشِ ساختگی یک بار ساخته می‌شود و همان **قول** نگه داشته می‌شود، نه
 *  مقدارش: اگر ده درخواست با هم برسند، هر ده تا منتظرِ همان یک ساخت
 *  می‌مانند، نه اینکه هرکدام یکی بسازد.
 */
let dummyHash = null;

async function burnTime(plain) {
  if (!dummyHash) dummyHash = hashPassword(randomBytes(24).toString('base64'));
  await verifyPassword(typeof plain === 'string' ? plain : '', await dummyHash);
}

/** بررسی حداقل قدرت رمز — جلوی رمزهای بدیهی را می‌گیرد. */
function checkStrength(plain) {
  if (typeof plain !== 'string' || plain.length < 8) return 'رمز عبور باید حداقل ۸ کاراکتر باشد';
  if (/^\d+$/.test(plain)) return 'رمز عبور نباید فقط عدد باشد';
  const weak = ['password', '12345678', 'qwertyui', 'admin123', '11111111'];
  if (weak.includes(plain.toLowerCase())) return 'این رمز عبور بسیار ساده است';
  return null;
}

module.exports = { hashPassword, verifyPassword, burnTime, checkStrength, needsRehash, upgrade, currentP, DEFAULT_P };
