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

/*
 *  ⛔ پلنِ زمان‌دارِ صریح هرگز «دائمی» خوانده نمی‌شود، هر قدر هم پایانش دور
 *  باشد (۱۴۰۵/۰۷/۲۰، سنجهٔ ‎8د‎ی ‎pump-e2e‎ِ ریپوی server). پیش از این
 *  وی‌آی‌پی‌ای که از دائمی پایینش آورده بودند پایانِ ۲۰۷۹ را نگه می‌داشت و
 *  این‌جا «دائمی» می‌شد — یعنی یک سال بعد خدماتش بی‌صدا قطع می‌شد. «دائمی کن»
 *  حالا پلن را هم ‎perm‎ می‌کند (‎admin-sales.makePermanent‎)، پس فرضِ «پایانِ
 *  دور» فقط برای پلن‌های بی‌نام و قدیمی (‎custom‎، ‎m1‎…) می‌ماند.
 */
const TIMED_PLANS = new Set(['std', 'vip', 'trial']);

function isPermanent(sub, at = Date.now()) {
  if (!sub) return false;
  if (sub.plan === 'perm' || sub.plan === 'permanent') return true;
  if (TIMED_PLANS.has(sub.plan)) return false;
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

/**
 * تمدیدِ خدماتِ سرورِ یک اشتراکِ دائمی — ‎{amount, unit}‎ (از پایانِ فعلی اگر
 * هنوز زنده است) یا ‎{until}‎ (تاریخِ دلخواه؛ گذشته ⇒ همین حالا قطع).
 * ⛔ تنها پیاده‌سازی: پنلِ مدیر (‎admin-sales‎) و کدِ بی‌اینترنت
 * (‎offline-codes.redeem‎) هر دو همین را صدا می‌زنند — ردیفِ تاریخچه با هم.
 */
async function extendServices(id, { amount, unit, until }, by, note = 'خدماتِ سرور') {
  const { one, query, newId, now } = require('../db');
  const { badRequest, notFound } = require('../middleware/errors');
  const cur = await one('SELECT * FROM station_subscriptions WHERE id=$1', [id]);
  if (!cur) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
  const t = now();
  if (!isPermanent(cur, t)) {
    throw badRequest('خدماتِ جدا فقط برای اشتراکِ دائمی است', 'not_permanent');
  }
  const prev = servicesUntil(cur, t);
  let next;
  if (until !== null && until !== undefined) {
    next = Math.max(Number(until), t);
  } else {
    if (!['day', 'month', 'year'].includes(unit)) throw badRequest('واحدِ مدت معتبر نیست', 'bad_unit');
    next = plans.endOfPeriod(Math.max(prev, t), amount, unit);
  }
  const row = await one(
    `UPDATE station_subscriptions SET services_until=$2, updated_at=$3 WHERE id=$1 RETURNING *`,
    [cur.id, next, t]
  );
  await query(
    `INSERT INTO station_subscription_history
       (id, subscription_id, station_id, action, plan, prev_status, new_status, prev_ends_at, new_ends_at, actor, note, created_at)
     VALUES ($1,$2,$3,'services',$4,$5,$6,$7,$8,$9,$10,$11)`,
    [newId('sbh'), row.id, row.station_id, row.plan, cur.status, row.status, prev, next, by, note, t]
  );
  require('./panel-live').notifyPanel('customers');
  return { subscription: row, servicesUntil: next, previous: prev };
}

module.exports = { ONLINE_KEYS, TIMED_PLANS, isPermanent, servicesUntil, servicesActive, stripOnline, hasOnline, extendServices };
