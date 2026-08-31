'use strict';
/**
 * تنظیمات سرویس پیامک — در دیتابیس، نه فقط در .env.
 *
 * تا امروز فقط از متغیرهای محیطی خوانده می‌شد، یعنی برای هر تغییر باید
 * کسی به سرور SSH می‌زد و فایل را دست می‌کاری می‌کرد و سرور را دوباره
 * راه می‌انداخت. صاحب سامانه گوشی دستش است، نه ترمینال.
 *
 * حالا از دیتابیس خوانده می‌شود و از برنامه‌ی مدیریت تنظیم می‌شود.
 * مقدارهای .env سر جایشان می‌مانند و پیش‌فرض حساب می‌شوند: نصب‌های
 * موجود نمی‌شکنند، و اگر کسی چیزی در پنل نگذاشته باشد همان .env کار
 * می‌کند.
 *
 * کلید سرویس در دیتابیس می‌ماند — روی سرور، نه در گوشی. به بیرون هرگز
 * کامل برنمی‌گردد؛ فقط چهار رقم آخرش نشان داده می‌شود.
 */
const plans = require('./plans');
const config = require('../config');

const PREFIX = 'sms_';

/** کلیدهایی که تنظیم می‌شوند، و اینکه هر کدام از کدام متغیر محیطی پیش‌فرض می‌گیرد */
const FIELDS = {
  provider: () => config.otp.provider,
  url:      () => config.sms.url,
  method:   () => config.sms.method,
  key:      () => config.sms.key,
  sender:   () => config.sms.sender,
  headers:  () => config.sms.headers,
  body:     () => config.sms.body,
  template: () => config.sms.template,
};

//  خواندن از دیتابیس در هر پیامک، یک رفت‌وبرگشت اضافه است. چند ثانیه
//  کش، بارِ دیتابیس را کم می‌کند و تغییرِ تنظیمات هم زود دیده می‌شود.
let cache = { at: 0, value: null };
const TTL_MS = 10 * 1000;

/** پاک کردن کش — بعد از هر ذخیره صدا زده می‌شود */
function invalidate() { cache = { at: 0, value: null }; }

/** تنظیمات کامل، با کلید. فقط داخل سرور استفاده می‌شود. */
async function current() {
  const t = Date.now();
  if (cache.value && t - cache.at < TTL_MS) return cache.value;

  const rows = await plans.allConfig();
  const out = {};
  for (const [name, fallback] of Object.entries(FIELDS)) {
    const stored = rows[PREFIX + name];
    out[name] = stored !== undefined && stored !== '' ? stored : fallback();
  }
  out.method = String(out.method || 'POST').toUpperCase();

  cache = { at: t, value: out };
  return out;
}

/** ذخیره‌ی آنچه مدیر فرستاده. فقط کلیدهای شناخته‌شده. */
async function save(patch = {}) {
  for (const [name] of Object.entries(FIELDS)) {
    if (!(name in patch)) continue;
    const value = patch[name];
    if (value === undefined || value === null) continue;
    await plans.setConfig(PREFIX + name, String(value));
  }
  invalidate();
  return current();
}

/**
 * همان تنظیمات، اما امن برای فرستادن به برنامه‌ی مدیریت.
 *
 * کلید کامل هرگز بیرون نمی‌رود — نه به برنامه، نه به لاگ. اگر می‌رفت،
 * هر کسی که یک بار به آن گوشی دست پیدا می‌کرد کلید سرویس پیامک را
 * داشت.
 */
async function masked() {
  const s = await current();
  return {
    provider: s.provider,
    url: s.url,
    method: s.method,
    sender: s.sender,
    headers: s.headers,
    body: s.body,
    template: s.template,
    keySet: !!s.key,
    keyHint: s.key ? `••••${String(s.key).slice(-4)}` : '',
  };
}

module.exports = { current, save, masked, invalidate, FIELDS };
