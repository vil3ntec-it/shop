'use strict';
/**
 * TOTP — RFC 6238 (روی HOTPِ RFC 4226)، بی هیچ وابستگیِ تازه.
 *
 * فقط همان چیزی که برنامه‌های تأییدِ دومرحله‌ای (Google Authenticator،
 * Microsoft Authenticator، Aegis، 2FAS…) می‌خواهند: HMAC-SHA1، شش رقم،
 * گامِ ۳۰ ثانیه، رازِ base32. با بردارهای آزمونِ خودِ RFC سنجیده شده
 * (‎test/admin-totp.test.js‎).
 *
 * ⛔ کد هرگز لاگ نمی‌شود. ⛔ هر گام فقط یک بار پذیرفته می‌شود (بازپخش) —
 * آن را صدا زننده با `step`ِ برگشتی نگه می‌دارد.
 */
const crypto = require('node:crypto');

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';
const STEP_SEC = 30;
const DIGITS = 6;

function base32Encode(buf) {
  let bits = 0; let value = 0; let out = '';
  for (const byte of buf) {
    value = (value << 8) | byte; bits += 8;
    while (bits >= 5) { out += ALPHABET[(value >>> (bits - 5)) & 31]; bits -= 5; }
  }
  if (bits > 0) out += ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

function base32Decode(str) {
  const clean = String(str || '').toUpperCase().replace(/[\s=-]/g, '');
  let bits = 0; let value = 0; const out = [];
  for (const ch of clean) {
    const i = ALPHABET.indexOf(ch);
    if (i < 0) throw new Error('رازِ base32 نامعتبر است');
    value = (value << 5) | i; bits += 5;
    if (bits >= 8) { out.push((value >>> (bits - 8)) & 255); bits -= 8; }
  }
  return Buffer.from(out);
}

/** رازِ تازه: ۲۰ بایتِ تصادفی (۱۶۰ بیت، همان اندازهٔ پیشنهادیِ RFC 4226) */
function newSecret() { return base32Encode(crypto.randomBytes(20)); }

/** HOTP (RFC 4226) — `key` بافرِ خام، `counter` عددِ صحیح */
function hotp(key, counter, digits = DIGITS, algo = 'sha1') {
  const msg = Buffer.alloc(8);
  msg.writeBigUInt64BE(BigInt(counter));
  const mac = crypto.createHmac(algo, key).update(msg).digest();
  const off = mac[mac.length - 1] & 0x0f;
  const bin = ((mac[off] & 0x7f) << 24) | (mac[off + 1] << 16) | (mac[off + 2] << 8) | mac[off + 3];
  return String(bin % 10 ** digits).padStart(digits, '0');
}

function stepAt(ms = Date.now()) { return Math.floor(ms / 1000 / STEP_SEC); }

/** کدِ این لحظه (برای آزمون و برای نمایش در هیچ‌جا) */
function generate(secret, ms = Date.now()) { return hotp(base32Decode(secret), stepAt(ms)); }

/**
 * سنجیدنِ کد با یک گامِ پس و پیش (ساعتِ گوشی کمی جلو/عقب).
 * @returns {number|null} گامی که پذیرفته شد، یا null
 *   `afterStep`: گام‌های تا این‌جا (و خودش) دیگر پذیرفته نمی‌شوند — ضدِ بازپخش.
 */
function verify(secret, code, { ms = Date.now(), window = 1, afterStep = -1 } = {}) {
  const c = String(code || '').replace(/\s/g, '');
  if (!/^\d{6}$/.test(c) || !secret) return null;
  let key;
  try { key = base32Decode(secret); } catch { return null; }
  const now = stepAt(ms);
  let hit = null;
  for (let s = now - window; s <= now + window; s++) {
    if (s <= afterStep) continue;
    const want = Buffer.from(hotp(key, s));
    //  مقایسهٔ هم‌زمان؛ حلقه هم زود نمی‌شکند تا زمان چیزی نگوید
    if (crypto.timingSafeEqual(want, Buffer.from(c)) && hit === null) hit = s;
  }
  return hit;
}

/** نشانیِ otpauth:// برای برنامهٔ تأیید (دستی یا با لمس روی گوشی) */
function otpauthUrl(secret, account, issuer = 'VILL3N Admin') {
  const label = encodeURIComponent(`${issuer}:${account}`);
  return `otpauth://totp/${label}?secret=${secret}&issuer=${encodeURIComponent(issuer)}&algorithm=SHA1&digits=${DIGITS}&period=${STEP_SEC}`;
}

/* ---------------------------------------------------------- کدهای بازیابی */

/** ده کدِ یک‌بارمصرف به شکلِ xxxxx-xxxxx (۵۰ بیت هر کدام) */
function newRecoveryCodes(n = 10) {
  const codes = [];
  for (let i = 0; i < n; i++) {
    const s = base32Encode(crypto.randomBytes(7)).slice(0, 10).toLowerCase();
    codes.push(`${s.slice(0, 5)}-${s.slice(5)}`);
  }
  return codes;
}

function normalizeRecovery(code) { return String(code || '').toLowerCase().replace(/[^a-z2-7]/g, ''); }

/** درهم‌سازیِ کدِ بازیابی؛ کد آن‌قدر تصادفی است که SHA-256 کافی است */
function hashRecovery(code) {
  return crypto.createHash('sha256').update('admin-recovery:' + normalizeRecovery(code)).digest('hex');
}

module.exports = {
  base32Encode, base32Decode, newSecret, hotp, generate, verify, stepAt, otpauthUrl,
  newRecoveryCodes, hashRecovery, normalizeRecovery, STEP_SEC, DIGITS,
};
