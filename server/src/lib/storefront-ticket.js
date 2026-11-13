'use strict';
/**
 * تأییدِ مشتریِ ویترین و برنامهٔ خرید با **ایمیل** (۲۳: بلیتِ امضاشده و کدِ یک‌بارمصرف) — از `lib/storefront.js` جدا شد
 * تا زیرِ سقفِ اندازه بماند. وابستگی‌های همان پرونده (کلیدِ توکن و سنجندهٔ ورودی) تزریق می‌شوند.
 *
 * ⛔ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳): کدِ ۶ رقمیِ همان `lib/otp` (همان سقف‌ها، تک‌مصرف، باطل شدنِ کدِ کهنه)
 * فقط به ایمیل می‌رود؛ شماره هرگز تأیید نمی‌شود و فقط «شمارهٔ تماس برای پیک» است (فرمِ ثبت/حساب، فقط شکل).
 * بلیت‌های v1/v2 ِ شماره دیگر اثبات نیستند (همان «بلیتِ نادرست»).
 */
const crypto = require('crypto');
const { now } = require('../db');
const config = require('../config');
const { ApiError } = require('../middleware/errors');

module.exports = ({ tokenKey, obj }) => {
// ── ۲۳: تأییدِ ایمیلِ مشتری ─────────────────────────────────────────
//  بلیتِ امضاشده: `v3.<ایمیل>.<صدور>.<انقضا>.<نسل>.<HMAC>` — می‌گوید «این ایمیل در لحظهٔ صدور با کدِ یک‌بارمصرف
//  تأیید شد». خودِ بلیت ذخیره نمی‌شود؛ فقط «نسلِ» هر ایمیل (HMAC ِ ایمیل ⇒ عدد، `store_phone_revocations` با
//  دامنهٔ جدا از شماره): «خروج از همه‌جا»، تغییرِ ایمیل و پاک کردنِ حساب نسل را یکی بالا می‌برند و هر بلیتِ کهنه‌تر
//  همان دم بی‌اعتبار است.
//  ⛔ ساختن/ورود به حساب (نشستِ ۱۸۰ روزه) فقط با بلیتِ صادرشده در چند دقیقهٔ اخیر (`accountTicketMaxMs`)؛
//  ثبت و «سفارش‌های من» با مهلتِ کاملِ بلیت.

const OTP_PURPOSE = 'store_order';
const TICKET_V3 = /^v3\.([A-Za-z0-9_-]{4,260})\.([0-9a-z]{1,12})\.([0-9a-z]{1,12})\.([0-9a-z]{1,7})\.([A-Za-z0-9_-]{32})$/;
const EMAIL_RE = /^[^\s@A-Z]+@[^\s@A-Z]+$/;

function ticketMac(email, iat, exp, gen) {
  return crypto.createHmac('sha256', tokenKey()).update(`customer-email-v3\n${email}\n${iat}\n${exp}\n${gen}`)
    .digest('base64url').slice(0, 32);
}

function ticketOf(email, at = now(), gen = 0) {
  const exp = at + config.storefront.ticketTtlMs;
  const b64 = Buffer.from(email).toString('base64url');
  const g = Math.max(0, Math.trunc(Number(gen) || 0));
  return {
    ticket: `v3.${b64}.${at.toString(36)}.${exp.toString(36)}.${g.toString(36)}.${ticketMac(email, at, exp, g)}`,
    expiresAt: exp,
  };
}

const sameMac = (given, want) => {
  const a = Buffer.from(given);
  const b = Buffer.from(want);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
};

/** بلیتِ سالم و نامنقضی ⇒ `{ email, issuedAt, expiresAt, gen }` — یا null. نسل این‌جا سنجیده نمی‌شود. */
function parseTicket(ticket, at = now()) {
  const m = TICKET_V3.exec(String(ticket || ''));
  if (!m) return null;
  const email = Buffer.from(m[1], 'base64url').toString('utf8');
  const [iat, exp, gen] = [m[2], m[3], m[4]].map((x) => Number.parseInt(x, 36));
  if (!EMAIL_RE.test(email) || email.length > 190 || !(exp > at)) return null;
  return sameMac(m[5], ticketMac(email, iat, exp, gen)) ? { email, issuedAt: iat, expiresAt: exp, gen } : null;
}

const customers = () => require('./store-customers');

/**
 * ایمیلِ تأییدشده از بلیت — یا null (منقضی، دست‌خورده، بی‌شکل، نسلِ لغوشده، یا کهنه‌تر از `maxAgeMs`).
 * ناهم‌گام است چون نسلِ ایمیل را از پایگاه می‌خواند.
 */
async function verifiedEmailOfTicket(ticket, { maxAgeMs = null, at = now() } = {}) {
  const t = parseTicket(ticket, at);
  if (!t) return null;
  if (maxAgeMs !== null && !(t.issuedAt >= at - maxAgeMs)) return null;
  const gen = await customers().ticketGen(t.email);
  return gen === t.gen ? t.email : null;
}

/**
 * هویتِ تأییدشده با اثباتش: بلیتِ تازه (`ticket`، نسل و انقضا)، وگرنه نشستِ حسابِ مشتری (`session`) — یا null.
 * ⇒ `{ email, customerId, proof, … }`؛ `customerId` حسابِ همان ایمیل ('' اگر هنوز حسابی نیست). حسابِ کهنهٔ فقط‌شماره
 * (بی ایمیل) تأییدشده نیست تا ایمیلش را وصل کند (`account/email`).
 * ⛔ بی شرطِ «نشست هست؟»: `auth` خودش نشستِ تهی/بی‌شکل را بی پرس‌وجو null می‌کند (CodeQL: user-controlled bypass).
 */
async function verification(ticket, session, at = now()) {
  const t = parseTicket(ticket, at);
  if (t && (await customers().ticketGen(t.email)) === t.gen) {
    const acct = await customers().byEmail(t.email);
    return { email: t.email, customerId: acct ? acct.id : '', proof: 'ticket', gen: t.gen, expiresAt: t.expiresAt };
  }
  const a = await customers().auth(session, at);
  return a && a.customer.email
    ? { email: a.customer.email, customerId: a.customer.id, proof: 'session', sessionId: a.sessionId, expiresAt: a.expiresAt }
    : null;
}

/**
 * آیا کد واقعاً به مشتری می‌رسد؟ در تولید، راهِ ایمیلِ `log` یعنی کد فقط در لاگِ سرور می‌نشیند ⇒ تأیید در دسترس
 * نیست و همین را صریح می‌گوییم (۲۳.۳ بندِ ۳). ⛔ حسابِ پیامک لازم نیست.
 */
async function verificationAvailable() {
  if (config.env !== 'production') return true;
  const { provider } = await require('./mailer').current();
  return Boolean(provider) && provider !== 'log';
}

/** ایمیلِ بدنه — `email`؛ شماره (`phone`) دیگر پذیرفته نیست (۴۰۰ ِ `email_required`). */
function emailOf(body) {
  const b = obj(body);
  const raw = String(b.email || '').trim();
  if (!raw) {
    throw new ApiError(400, 'email_required', b.phone
      ? 'تأیید فقط با ایمیل است؛ شماره فقط برای تماسِ پیک است — ایمیل را بنویسید'
      : 'ایمیل لازم است');
  }
  return require('./validate').email(raw, { required: true });
}

async function startVerification(body, { ip = '' } = {}) {
  const email = emailOf(body);
  if (!(await verificationAvailable())) {
    throw new ApiError(503, 'verification_unavailable',
      'تأییدِ ایمیل هنوز روی این سرور راه‌اندازی نشده است؛ فعلاً برای سفارش با شمارهٔ دکان تماس بگیرید');
  }
  tokenKey();
  const out = await require('./otp').request(email, { purpose: OTP_PURPOSE, ip, app: 'shop' });
  return {
    email, sent: out.sent === true, expiresAt: out.expiresAt, resendSeconds: out.resendSeconds,
    ...(out.devCode ? { devCode: out.devCode } : {}),
  };
}

async function checkVerification(body) {
  const b = obj(body);
  const email = emailOf(b);
  tokenKey();
  await require('./otp').verify(email, b.code, { purpose: OTP_PURPOSE });
  const gen = await customers().ticketGen(email);
  return { email, ...ticketOf(email, now(), gen) };
}

  return { ticketOf, parseTicket, verifiedEmailOfTicket, verification, verificationAvailable, startVerification, checkVerification };
};
