'use strict';
/**
 * کد یک‌بارمصرف ورود با شماره.
 *
 * کد هرگز به شکل متن ساده ذخیره نمی‌شود — فقط HMAC آن با راز سرور.
 * هر کد: مهلت دارد، تعداد تلاش محدود دارد، بعد از یک بار استفاده باطل
 * می‌شود و ارسال دوباره‌اش محدود است.
 *
 * راه ارسال پیامک از بیرون تعیین می‌شود (OTP_PROVIDER) و با عوض کردن
 * یک متغیر محیطی به سرویس دیگری می‌رود؛ هیچ سرویسی داخل کد قفل نشده.
 */
const { createHmac, randomInt, timingSafeEqual } = require('crypto');
const { query, one, newId, now } = require('../db');
const config = require('../config');
const { badRequest, tooMany, forbidden } = require('../middleware/errors');

function pepper() {
  return config.secrets.otp || config.secrets.api || 'shop-otp-pepper';
}

function hashCode(destination, code) {
  return createHmac('sha256', pepper()).update(`${destination}:${code}`).digest('hex');
}

function randomCode(digits) {
  let s = '';
  for (let i = 0; i < digits; i++) s += String(randomInt(10));
  // اولین رقم صفر نباشد تا کد کوتاه به نظر نرسد
  if (s[0] === '0') s = String(randomInt(1, 10)) + s.slice(1);
  return s;
}

// ---------- راه‌های ارسال ----------
const senders = {
  /** چاپ در لاگ سرور — برای سرور خانگی بدون سرویس پیامک. */
  async log(to, code) {
    console.log(`[otp] کد ورود برای ${to}: ${code}`);
    return { delivered: true, via: 'log' };
  },
  /**
   * فرستادن به یک آدرس دلخواه (هر سرویس پیامکی که وب‌هوک دارد).
   * بدنه: { to, code, message }
   */
  async webhook(to, code, message) {
    if (!config.otp.webhookUrl) throw new Error('OTP_WEBHOOK_URL تنظیم نشده است');
    const res = await fetch(config.otp.webhookUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(config.otp.webhookKey ? { 'X-Api-Key': config.otp.webhookKey } : {}),
      },
      body: JSON.stringify({ to, code, message }),
      signal: AbortSignal.timeout(10000),
    });
    if (!res.ok) throw new Error(`سرویس پیامک پاسخ ${res.status} داد`);
    return { delivered: true, via: 'webhook' };
  },
};

function sender() {
  return senders[config.otp.provider] || senders.log;
}

/**
 * ساخت و فرستادن کد.
 * @returns {{sent:boolean, expiresAt:number, resendAfter:number, devCode?:string}}
 */
async function request(destination, { purpose = 'login', ip = '' } = {}) {
  const t = now();

  // فاصله‌ی ارسال دوباره
  const last = await one(
    'SELECT created_at FROM otp_codes WHERE destination=$1 AND purpose=$2 ORDER BY created_at DESC LIMIT 1',
    [destination, purpose]
  );
  if (last && t - Number(last.created_at) < config.otp.resendMs) {
    const wait = Math.ceil((config.otp.resendMs - (t - Number(last.created_at))) / 1000);
    throw tooMany(`${wait} ثانیه دیگر می‌توانید کد تازه بخواهید`, 'otp_resend_wait');
  }

  // سقف روزانه برای یک شماره
  const { n } = await one(
    'SELECT COUNT(*)::int AS n FROM otp_codes WHERE destination=$1 AND created_at > $2',
    [destination, t - 24 * 3600 * 1000]
  );
  if (n >= config.otp.dailyMax) {
    throw tooMany('امروز درخواست کد برای این شماره زیاد بوده است', 'otp_daily_limit');
  }

  const code = randomCode(config.otp.digits);
  const expiresAt = t + config.otp.ttlMs;
  await query(
    `INSERT INTO otp_codes (id, purpose, destination, code_hash, attempts, max_attempts, expires_at, created_at, ip)
     VALUES ($1,$2,$3,$4,0,$5,$6,$7,$8)`,
    [newId('otp'), purpose, destination, hashCode(destination, code), config.otp.maxAttempts, expiresAt, t, ip]
  );

  const message = `کد ورود شما: ${code}`;
  await sender()(destination, code, message);

  const out = { sent: true, expiresAt, resendAfter: t + config.otp.resendMs };
  // فقط بیرون از حالت production و فقط وقتی راه ارسالی تنظیم نشده
  if (config.env !== 'production' && config.otp.provider === 'log') out.devCode = code;
  return out;
}

/** بررسی کد. در صورت درستی، همان لحظه باطل می‌شود. */
async function verify(destination, code, { purpose = 'login' } = {}) {
  const clean = String(code || '').replace(/\D/g, '');
  if (!clean) throw badRequest('کد را وارد کنید', 'otp_required');

  const row = await one(
    `SELECT * FROM otp_codes
      WHERE destination=$1 AND purpose=$2 AND consumed_at IS NULL
      ORDER BY created_at DESC LIMIT 1`,
    [destination, purpose]
  );
  if (!row) throw forbidden('کدی برای این شماره صادر نشده است', 'otp_not_found');
  if (Number(row.expires_at) < now()) throw forbidden('مهلت این کد تمام شده است', 'otp_expired');
  if (row.attempts >= row.max_attempts) throw forbidden('تعداد تلاش بیش از حد بود', 'otp_locked');

  await query('UPDATE otp_codes SET attempts = attempts + 1 WHERE id=$1', [row.id]);

  const expected = Buffer.from(row.code_hash, 'hex');
  const actual = Buffer.from(hashCode(destination, clean), 'hex');
  const ok = expected.length === actual.length && timingSafeEqual(expected, actual);
  if (!ok) throw forbidden('کد درست نیست', 'otp_wrong');

  await query('UPDATE otp_codes SET consumed_at=$2 WHERE id=$1', [row.id, now()]);
  return true;
}

module.exports = { request, verify, hashCode, senders };
