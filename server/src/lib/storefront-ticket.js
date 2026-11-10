'use strict';
/**
 * تأییدِ شمارهٔ مشتریِ ویترین (۲۳: بلیتِ امضاشده و کدِ یک‌بارمصرف) — از `lib/storefront.js` جدا شد تا زیرِ
 * سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (کلیدِ توکن و سنجندهٔ ورودی) تزریق می‌شوند.
 */
const crypto = require('crypto');
const { now } = require('../db');
const config = require('../config');
const { ApiError } = require('../middleware/errors');

module.exports = ({ tokenKey, obj }) => {
// ── ۲۳: تأییدِ شمارهٔ مشتری ─────────────────────────────────────────
//  بلیتِ امضاشده: `v2.<شماره>.<صدور>.<انقضا>.<نسل>.<HMAC>` — می‌گوید «این
//  شماره در لحظهٔ صدور با کدِ یک‌بارمصرف تأیید شد». خودِ بلیت در سرور ذخیره
//  نمی‌شود؛ فقط «نسلِ» هر شماره (HMAC ِ شماره ⇒ عدد، store_phone_revocations):
//  «خروج از همه‌جا» و پاک کردنِ حساب نسل را یکی بالا می‌برند و هر بلیتِ کهنه‌تر
//  همان دم بی‌اعتبار است. بلیتِ v1 (پیش از نسل) همان نسلِ صفر است.
//  ⛔ ساختن/ورود به حساب (نشستِ ۱۸۰ روزه) فقط با بلیتِ صادرشده در چند دقیقهٔ
//  اخیر (`accountTicketMaxMs`)؛ ثبت و «سفارش‌های من» با مهلتِ کاملِ بلیت.

const OTP_PURPOSE = 'store_order';
const TICKET_V1 = /^v1\.([A-Za-z0-9_-]{4,40})\.([0-9a-z]{1,12})\.([A-Za-z0-9_-]{32})$/;
const TICKET_V2 = /^v2\.([A-Za-z0-9_-]{4,40})\.([0-9a-z]{1,12})\.([0-9a-z]{1,12})\.([0-9a-z]{1,7})\.([A-Za-z0-9_-]{32})$/;

function ticketMacV1(phone, exp) {
  return crypto.createHmac('sha256', tokenKey()).update(`customer-phone\n${phone}\n${exp}`)
    .digest('base64url').slice(0, 32);
}

function ticketMac(phone, iat, exp, gen) {
  return crypto.createHmac('sha256', tokenKey()).update(`customer-phone-v2\n${phone}\n${iat}\n${exp}\n${gen}`)
    .digest('base64url').slice(0, 32);
}

function ticketOf(phone, at = now(), gen = 0) {
  const exp = at + config.storefront.ticketTtlMs;
  const b64 = Buffer.from(phone).toString('base64url');
  const g = Math.max(0, Math.trunc(Number(gen) || 0));
  return {
    ticket: `v2.${b64}.${at.toString(36)}.${exp.toString(36)}.${g.toString(36)}.${ticketMac(phone, at, exp, g)}`,
    expiresAt: exp,
  };
}

const sameMac = (given, want) => {
  const a = Buffer.from(given);
  const b = Buffer.from(want);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

/** بلیتِ سالم و نامنقضی ⇒ `{ phone, issuedAt, expiresAt, gen }` — یا null. نسل این‌جا سنجیده نمی‌شود. */
function parseTicket(ticket, at = now()) {
  const t = String(ticket || '');
  let m = TICKET_V2.exec(t);
  if (m) {
    const phone = Buffer.from(m[1], 'base64url').toString('utf8');
    const [iat, exp, gen] = [m[2], m[3], m[4]].map((x) => Number.parseInt(x, 36));
    if (!/^\+[1-9]\d{7,14}$/.test(phone) || !(exp > at)) return null;
    return sameMac(m[5], ticketMac(phone, iat, exp, gen)) ? { phone, issuedAt: iat, expiresAt: exp, gen } : null;
  }
  m = TICKET_V1.exec(t);
  if (!m) return null;
  const phone = Buffer.from(m[1], 'base64url').toString('utf8');
  const exp = Number.parseInt(m[2], 36);
  if (!/^\+[1-9]\d{7,14}$/.test(phone) || !(exp > at)) return null;
  return sameMac(m[3], ticketMacV1(phone, exp))
    ? { phone, issuedAt: exp - config.storefront.ticketTtlMs, expiresAt: exp, gen: 0 } : null;
}

/**
 * شمارهٔ تأییدشده از بلیت — یا null (منقضی، دست‌خورده، بی‌شکل، نسلِ لغوشده،
 * یا کهنه‌تر از `maxAgeMs`). ناهم‌گام است چون نسلِ شماره را از پایگاه می‌خواند.
 * ⛔ جای `phoneOfTicket` ِ هم‌گامِ پیشین: نامِ تازه تا فراخوانِ کهنه‌ای که
 * await ندارد، به‌جای Promise ِ «راست» با خطای آشکار بشکند.
 */
async function verifiedPhoneOfTicket(ticket, { maxAgeMs = null, at = now() } = {}) {
  const t = parseTicket(ticket, at);
  if (!t) return null;
  if (maxAgeMs !== null && !(t.issuedAt >= at - maxAgeMs)) return null;
  const gen = await require('./store-customers').ticketGen(t.phone);
  return gen === t.gen ? t.phone : null;
}

/**
 * شمارهٔ تأییدشده با اثباتش: بلیتِ تازه (`ticket`، نسل و انقضا)، وگرنه نشستِ حسابِ مشتری (`session`) — یا null.
 * تنها منبعِ `storefront.verifiedPhone` (همان ترتیب: بلیت جلوتر). اثبات برای کاری که بعداً به نامِ همین شماره انجام می‌شود
 * (پوشِ «خبرم کن»، `store-alert-push.js`) نگه داشته می‌شود تا باطل یا منقضی شدنش همان کار را هم ببندد.
 * ⛔ بی شرطِ «نشست هست؟»: `auth` خودش نشستِ تهی/بی‌شکل را بی پرس‌وجو null می‌کند؛ شرطِ بیرونی یعنی «مقدارِ کاربر
 *    تصمیم می‌گیرد احراز اجرا شود یا نه» (CodeQL: user-controlled bypass).
 */
async function verification(ticket, session, at = now()) {
  const t = parseTicket(ticket, at);
  if (t && (await require('./store-customers').ticketGen(t.phone)) === t.gen) {
    return { phone: t.phone, proof: 'ticket', gen: t.gen, expiresAt: t.expiresAt };
  }
  const a = await require('./store-customers').auth(session, at);
  return a ? { phone: a.customer.phone, proof: 'session', sessionId: a.sessionId, expiresAt: a.expiresAt } : null;
}

/**
 * آیا کد واقعاً به مشتری می‌رسد؟ در تولید، راهِ `log` یعنی کد فقط در لاگِ
 * سرور می‌نشیند ⇒ تأیید در دسترس نیست و همین را صریح می‌گوییم (۲۳.۳ بندِ ۳).
 */
async function verificationAvailable() {
  if (config.env !== 'production') return true;
  const { provider } = await require('./sms-settings').current();
  return Boolean(provider) && provider !== 'log';
}

async function startVerification(body, { ip = '', shopId = '' } = {}) {
  const phone = require('./validate').phone(obj(body).phone, { required: true });
  if (!(await verificationAvailable())) {
    throw new ApiError(503, 'verification_unavailable',
      'تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است؛ فعلاً برای سفارش با شمارهٔ دکان تماس بگیرید');
  }
  tokenKey();
  const out = await require('./otp').request(phone, { purpose: OTP_PURPOSE, ip, app: 'shop', shopId });
  return {
    sent: out.sent === true, expiresAt: out.expiresAt, resendSeconds: out.resendSeconds,
    ...(out.devCode ? { devCode: out.devCode } : {}),
  };
}

async function checkVerification(body) {
  const b = obj(body);
  const phone = require('./validate').phone(b.phone, { required: true });
  tokenKey();
  await require('./otp').verify(phone, b.code, { purpose: OTP_PURPOSE });
  const gen = await require('./store-customers').ticketGen(phone);
  return { phone, ...ticketOf(phone, now(), gen) };
}

  return { ticketOf, parseTicket, verifiedPhoneOfTicket, verification, verificationAvailable, startVerification, checkVerification };
};
