'use strict';
/**
 * کدِ تحویل (PCK-11، DLV-08، DLV-15) — فقط HMAC در دیتابیس. جدا از `lib/delivery.js`، بی تغییرِ رفتار.
 */
const crypto = require('crypto');
const config = require('../config');
const { str } = require('./delivery-input');

const CODE_MAX_ATTEMPTS = 5;

function pepper() {
  return config.secrets.api || config.secrets.otp || config.secrets.jwt || 'shop-delivery-code';
}

function codeHash(assignmentId, code) {
  return crypto.createHmac('sha256', pepper()).update(`delivery:${assignmentId}:${code}`).digest('hex');
}

function newCode() {
  return String(crypto.randomInt(0, 1_000_000)).padStart(6, '0');
}

/**
 * کدِ قابلِ بازسازی (فقط با پرچمِ نمای مشتری): از دانهٔ تصادفیِ مأموریت و رمزِ
 * سرور ساخته می‌شود، تا مشتری کد را ببیند بی آن‌که خودِ کد جایی نوشته شود.
 */
function derivedCode(assignmentId, nonce) {
  const mac = crypto.createHmac('sha256', pepper()).update(`delivery-code:${assignmentId}:${nonce}`).digest();
  return String(mac.readUIntBE(0, 6) % 1_000_000).padStart(6, '0');
}

/** کد + دانه: با پرچم قابلِ بازسازی، وگرنه همان کدِ تصادفیِ یک‌بار-دیدنی. */
function codeFor(assignmentId, rebuildable) {
  if (!rebuildable) return { code: newCode(), nonce: '' };
  const nonce = crypto.randomBytes(16).toString('base64url');
  return { code: derivedCode(assignmentId, nonce), nonce };
}

function codeMatches(a, raw) {
  const code = str(raw).replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0));
  if (!/^\d{6}$/.test(code)) return false;
  const want = Buffer.from(a.code_hash, 'hex');
  const got = Buffer.from(codeHash(a.id, code), 'hex');
  return want.length === got.length && crypto.timingSafeEqual(want, got);
}

module.exports = { CODE_MAX_ATTEMPTS, codeHash, newCode, derivedCode, codeFor, codeMatches };
