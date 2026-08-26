'use strict';
/**
 * اعتبارسنجی ورودی‌ها.
 *
 * قاعده: به هیچ داده‌ای که از گوشی می‌آید اعتماد نمی‌شود. هر رشته
 * بریده و پاک می‌شود، هر عدد بررسی می‌شود و هر شناسه قالب مشخص دارد.
 */
const { badRequest } = require('../middleware/errors');

const DEFAULT_CC = process.env.DEFAULT_COUNTRY_CODE || '+93';

const str = (v) => (typeof v === 'string' ? v.trim() : '');

/** حذف کاراکترهای کنترلی که در متن دیده نمی‌شوند ولی نمایش را خراب می‌کنند. */
function stripControl(s) {
  let out = '';
  for (const ch of s) {
    const c = ch.codePointAt(0);
    if (c > 31 && c !== 127) out += ch;
  }
  return out;
}

/** متن ساده با سقف طول. */
function text(v, { max = 200, field = 'مقدار', required = false, min = 0 } = {}) {
  let s = stripControl(str(v));
  if (s.length > max) s = s.slice(0, max);
  if (required && s.length < Math.max(1, min)) throw badRequest(`${field} لازم است`);
  if (s.length && s.length < min) throw badRequest(`${field} خیلی کوتاه است`);
  return s;
}

/** ایمیل — به حروف کوچک تبدیل می‌شود تا Ali@x.com و ali@x.com یکی باشند. */
function email(v, { required = false } = {}) {
  const s = str(v).toLowerCase();
  if (!s) {
    if (required) throw badRequest('ایمیل لازم است');
    return null;
  }
  if (s.length > 190 || !/^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/.test(s)) {
    throw badRequest('ایمیل معتبر نیست');
  }
  return s;
}

/**
 * شماره‌ی موبایل — به قالب بین‌المللی نرمال می‌شود.
 * 0792236008 و +93792236008 و 0093792236008 هر سه یک شماره‌اند و
 * نباید سه حساب جدا بسازند.
 */
function phone(v, { required = false } = {}) {
  let s = str(v).replace(/[\s\-().]/g, '');
  s = s.replace(/[۰-۹]/g, d => String(d.charCodeAt(0) - 0x06F0))
       .replace(/[٠-٩]/g, d => String(d.charCodeAt(0) - 0x0660));
  s = s.replace(/^00/, '+');
  if (!s) {
    if (required) throw badRequest('شماره موبایل لازم است');
    return null;
  }
  if (s.startsWith('0')) s = DEFAULT_CC + s.slice(1);
  else if (!s.startsWith('+')) s = DEFAULT_CC + s;
  if (!/^\+[1-9]\d{7,14}$/.test(s)) throw badRequest('شماره موبایل معتبر نیست');
  return s;
}

/** شناسه‌ی رکورد — فقط حروف، رقم و چند نشانه‌ی بی‌خطر. */
function id(v, { field = 'شناسه', required = true, max = 80 } = {}) {
  const s = str(v);
  if (!s) {
    if (required) throw badRequest(`${field} لازم است`);
    return '';
  }
  if (s.length > max || !/^[A-Za-z0-9_.:-]+$/.test(s)) throw badRequest(`${field} معتبر نیست`);
  return s;
}

/** عدد پول/تعداد — منفی و NaN و بی‌نهایت رد می‌شوند. */
function amount(v, { field = 'مبلغ', min = 0, max = 1e15, required = false } = {}) {
  if (v === undefined || v === null || v === '') {
    if (required) throw badRequest(`${field} لازم است`);
    return 0;
  }
  const n = Number(v);
  if (!Number.isFinite(n)) throw badRequest(`${field} عدد نیست`);
  if (n < min) throw badRequest(`${field} نمی‌تواند کمتر از ${min} باشد`);
  if (n > max) throw badRequest(`${field} بیش از حد بزرگ است`);
  return n;
}

function integer(v, { field = 'عدد', min = 0, max = 1e9, def = 0 } = {}) {
  if (v === undefined || v === null || v === '') return def;
  const n = Math.trunc(Number(v));
  if (!Number.isFinite(n)) throw badRequest(`${field} عدد نیست`);
  if (n < min || n > max) throw badRequest(`${field} در بازه‌ی مجاز نیست`);
  return n;
}

function timestamp(v, { def = null } = {}) {
  if (v === undefined || v === null || v === '') return def;
  const n = Number(v);
  if (!Number.isFinite(n) || n < 0 || n > 4102444800000) return def;   // تا سال ۲۱۰۰
  return Math.trunc(n);
}

function bool(v, def = false) {
  if (v === undefined || v === null || v === '') return def;
  if (typeof v === 'boolean') return v;
  return /^(1|true|yes|on)$/i.test(String(v));
}

function oneOf(v, allowed, { field = 'مقدار', def = null } = {}) {
  const s = str(v);
  if (!s && def !== null) return def;
  if (!allowed.includes(s)) throw badRequest(`${field} معتبر نیست`);
  return s;
}

/** بدنه‌ی رکورد برنامه — باید شیء باشد و از سقف حجم رد نشود. */
function payload(v, { max = 64 * 1024, field = 'داده' } = {}) {
  if (v === undefined || v === null) return {};
  if (typeof v !== 'object' || Array.isArray(v)) throw badRequest(`${field} باید شیء باشد`);
  const json = JSON.stringify(v);
  if (Buffer.byteLength(json, 'utf8') > max) throw badRequest(`${field} بیش از حد بزرگ است`);
  return v;
}

module.exports = {
  text, email, phone, id, amount, integer, timestamp, bool, oneOf, payload,
  stripControl, DEFAULT_CC,
};
