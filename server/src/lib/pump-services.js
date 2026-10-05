'use strict';
/*
 *  ══ خدماتِ سرورِ پمپ — و دائمی که سالِ اولش رایگان است (۱۴۰۵/۰۷/۲۰) ══
 *
 *  «دائمی هم همه‌چی داشته باشه بدون محدودیت… ولی خدماتش از سرور باید
 *  تمدید بشه… کسی که دائمی می‌خره سالِ اول همه‌چی فعال باشه و رایگان یک
 *  سال، بعد اون باید من خودم برای خدمات اقدام کنم و از سرور بدم.»
 *
 *  ⛔ «خدماتِ سرور» یعنی همین کلیدها — هر چیزی که بی سرور کار نمی‌کند.
 *  دفتر، مفاد/ضرر، داشبورد و تاریخچه‌ها روی خودِ کامپیوترند و هرگز این‌جا
 *  نیستند.
 *  ⛔ فقط اشتراکِ **دائمی** پایانِ خدمات دارد؛ استاندارد و وی‌آی‌پی همان
 *  فهرستِ پلنشان‌اند.
 */
const plans = require('./plans');

const ONLINE_KEYS = ['kar_app', 'bot', 'messenger', 'cloud', 'cloudbackup'];
const DAY = 86_400_000;
const PERMANENT_AFTER = 10 * 365 * DAY;

function isPermanent(sub, at = Date.now()) {
  if (!sub) return false;
  if (sub.plan === 'perm' || sub.plan === 'permanent') return true;
  return Number(sub.ends_at) - at > PERMANENT_AFTER;
}

/**
 * پایانِ خدماتِ سرورِ این اشتراک — ۰ یعنی «محدودیتی نیست» (غیرِ دائمی).
 * ستونِ صریح جلوتر است؛ وگرنه یک سال از شروعِ اشتراک.
 */
function servicesUntil(sub, at = Date.now()) {
  if (!isPermanent(sub, at)) return 0;
  if (sub.services_until !== null && sub.services_until !== undefined && sub.services_until !== '') {
    return Number(sub.services_until);
  }
  return plans.endOfPeriod(Number(sub.starts_at), 1, 'year');
}

/** خدمات همین حالا باز است؟ (غیرِ دائمی ⇒ همیشه بله؛ تصمیم با فهرستِ پلن.) */
function servicesActive(sub, at = Date.now()) {
  const u = servicesUntil(sub, at);
  return u === 0 || u > at;
}

/** فهرستِ قابلیت‌ها بی خدماتِ سرور. */
function stripOnline(features) {
  return features.filter(k => !ONLINE_KEYS.includes(k));
}

/** آیا این فهرست دستِ‌کم یکی از خدماتِ سرور را دارد؟ */
function hasOnline(features) {
  return Array.isArray(features) && features.some(k => ONLINE_KEYS.includes(k));
}

module.exports = { ONLINE_KEYS, isPermanent, servicesUntil, servicesActive, stripOnline, hasOnline };
