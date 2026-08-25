'use strict';
/**
 * منطق اشتراک — تنها مرجع تصمیم‌گیری درباره‌ی «الان این کاربر چه دسترسی‌ای دارد».
 * همیشه با ساعت سرور کار می‌کند، هرگز با زمانی که کلاینت می‌فرستد.
 */
const { getDb, newId, now } = require('../db');
const time = require('./time');
const { sanitizeFeatures } = require('./features');
const config = require('../config');

const LIVE_STATUSES = ['active', 'suspended'];

function parseFeatures(json) {
  try { const v = JSON.parse(json); return Array.isArray(v) ? v : []; }
  catch { return []; }
}

/** اشتراک زنده‌ی کاربر (فعال یا معلق). اشتراک لغوشده/منقضی برنمی‌گردد. */
function getLiveSubscription(userId) {
  return getDb().prepare(`
    SELECT * FROM subscriptions
    WHERE user_id = ? AND status IN ('active','suspended')
    ORDER BY created_at DESC LIMIT 1
  `).get(userId) || null;
}

function getSubscriptionById(id) {
  return getDb().prepare('SELECT * FROM subscriptions WHERE id = ?').get(id) || null;
}

function listSubscriptions(userId) {
  return getDb().prepare(
    'SELECT * FROM subscriptions WHERE user_id = ? ORDER BY created_at DESC'
  ).all(userId);
}

/**
 * وضعیت مؤثر اشتراک در لحظه‌ی حال (بر اساس ساعت سرور).
 * خروجی:
 *   state: none | pending | active | grace | expired | suspended | cancelled
 *   features: قابلیت‌هایی که همین حالا مجازند (در حالت غیرفعال آرایه‌ی خالی)
 */
function evaluate(sub, atMs = now()) {
  if (!sub) {
    return { state: 'none', active: false, features: [], startsAt: null, endsAt: null,
             graceEndsAt: null, timezone: config.defaults.timezone, plan: null, maxDevices: 0 };
  }

  const graceMs = Math.max(0, sub.grace_days || 0) * 24 * 60 * 60 * 1000;
  const graceEndsAt = sub.ends_at + graceMs;
  const granted = sanitizeFeatures(parseFeatures(sub.features));

  const base = {
    subscriptionId: sub.id,
    plan: sub.plan,
    startsAt: sub.starts_at,
    endsAt: sub.ends_at,
    graceEndsAt,
    timezone: sub.timezone,
    maxDevices: sub.max_devices,
    licenseTtlDays: sub.license_ttl_days,
    graceDays: sub.grace_days,
  };

  if (sub.status === 'cancelled') return { ...base, state: 'cancelled', active: false, features: [] };
  if (sub.status === 'suspended') return { ...base, state: 'suspended', active: false, features: [] };
  if (sub.status === 'expired')   return { ...base, state: 'expired',   active: false, features: [] };

  if (atMs < sub.starts_at)  return { ...base, state: 'pending', active: false, features: [] };
  if (atMs <= sub.ends_at)   return { ...base, state: 'active',  active: true,  features: granted };
  if (atMs <= graceEndsAt)   return { ...base, state: 'grace',   active: true,  features: granted };
  return { ...base, state: 'expired', active: false, features: [] };
}

/** وضعیت مؤثر کاربر — همان چیزی که License از روی آن ساخته می‌شود. */
function evaluateUser(userId, atMs = now()) {
  return evaluate(getLiveSubscription(userId), atMs);
}

/**
 * ساخت/جایگزینی اشتراک کاربر.
 * اشتراک زنده‌ی قبلی به 'cancelled' می‌رود تا ایندکس یکتا نقض نشود و تاریخچه بماند.
 */
function createSubscription(userId, input, adminId) {
  const db = getDb();
  const t = now();
  const tz = time.isValidTimeZone(input.timezone) ? input.timezone : config.defaults.timezone;
  const { startsAt, endsAt } = resolvePeriod(input, tz, t);

  if (endsAt <= startsAt) throw new BadInput('تاریخ پایان باید بعد از تاریخ شروع باشد');

  const sub = {
    id: newId('sub'),
    user_id: userId,
    plan: String(input.plan || 'custom').slice(0, 40),
    status: 'active',
    starts_at: startsAt,
    ends_at: endsAt,
    timezone: tz,
    features: JSON.stringify(sanitizeFeatures(input.features)),
    max_devices: clampInt(input.maxDevices, 1, 100, config.defaults.maxDevices),
    grace_days: clampInt(input.graceDays, 0, 365, config.defaults.graceDays),
    license_ttl_days: input.licenseTtlDays === null || input.licenseTtlDays === undefined || input.licenseTtlDays === ''
      ? null : clampInt(input.licenseTtlDays, 1, 3650, 30),
    note: String(input.note || '').slice(0, 500),
    created_at: t,
    updated_at: t,
    created_by: adminId || null,
  };

  const tx = db.transaction(() => {
    db.prepare(`UPDATE subscriptions SET status='cancelled', updated_at=?
                WHERE user_id=? AND status IN ('active','suspended')`).run(t, userId);
    db.prepare(`
      INSERT INTO subscriptions
        (id,user_id,plan,status,starts_at,ends_at,timezone,features,max_devices,
         grace_days,license_ttl_days,note,created_at,updated_at,created_by)
      VALUES (@id,@user_id,@plan,@status,@starts_at,@ends_at,@timezone,@features,@max_devices,
              @grace_days,@license_ttl_days,@note,@created_at,@updated_at,@created_by)
    `).run(sub);
  });
  tx();
  return getSubscriptionById(sub.id);
}

/** ویرایش اشتراک موجود — فقط فیلدهای داده‌شده تغییر می‌کنند. */
function updateSubscription(subId, input) {
  const sub = getSubscriptionById(subId);
  if (!sub) throw new BadInput('اشتراک پیدا نشد', 404);

  const tz = input.timezone !== undefined && time.isValidTimeZone(input.timezone)
    ? input.timezone : sub.timezone;

  const patch = { timezone: tz, updated_at: now() };

  if (input.plan !== undefined)     patch.plan = String(input.plan).slice(0, 40);
  if (input.note !== undefined)     patch.note = String(input.note).slice(0, 500);
  if (input.features !== undefined) patch.features = JSON.stringify(sanitizeFeatures(input.features));
  if (input.maxDevices !== undefined) patch.max_devices = clampInt(input.maxDevices, 1, 100, sub.max_devices);
  if (input.graceDays !== undefined)  patch.grace_days = clampInt(input.graceDays, 0, 365, sub.grace_days);
  if (input.licenseTtlDays !== undefined) {
    patch.license_ttl_days = (input.licenseTtlDays === null || input.licenseTtlDays === '')
      ? null : clampInt(input.licenseTtlDays, 1, 3650, 30);
  }
  if (input.status !== undefined) {
    if (!['active', 'suspended', 'cancelled', 'expired'].includes(input.status)) {
      throw new BadInput('وضعیت اشتراک نامعتبر است');
    }
    patch.status = input.status;
  }

  // تاریخ‌ها: یا هر دو دستی، یا با مدت از تاریخ شروع فعلی
  if (input.startDate !== undefined || input.endDate !== undefined ||
      input.amount !== undefined || input.unit !== undefined) {
    const { startsAt, endsAt } = resolvePeriod(
      { startDate: input.startDate, endDate: input.endDate, amount: input.amount, unit: input.unit },
      tz, now(), { fallbackStart: sub.starts_at }
    );
    if (endsAt <= startsAt) throw new BadInput('تاریخ پایان باید بعد از تاریخ شروع باشد');
    patch.starts_at = startsAt;
    patch.ends_at = endsAt;
  }

  const cols = Object.keys(patch);
  getDb().prepare(`UPDATE subscriptions SET ${cols.map(c => `${c}=@${c}`).join(', ')} WHERE id=@id`)
    .run({ ...patch, id: subId });
  return getSubscriptionById(subId);
}

/** تمدید: پایان اشتراک را به اندازه‌ی مدت داده‌شده جلو می‌برد. */
function renewSubscription(subId, amount, unit) {
  const sub = getSubscriptionById(subId);
  if (!sub) throw new BadInput('اشتراک پیدا نشد', 404);
  const t = now();
  // اگر اشتراک منقضی شده، تمدید از «حالا» شروع می‌شود، نه از تاریخ گذشته —
  // وگرنه تمدید یک‌ماهه‌ی یک اشتراکِ سه‌ماه‌پیش‌منقضی‌شده بی‌اثر می‌ماند.
  const from = Math.max(sub.ends_at, t);
  const endsAt = time.addDuration(from, amount, unit, sub.timezone);
  getDb().prepare(
    `UPDATE subscriptions SET ends_at=?, status=CASE WHEN status IN ('expired','cancelled') THEN 'active' ELSE status END,
     updated_at=? WHERE id=?`
  ).run(endsAt, t, subId);
  return getSubscriptionById(subId);
}

/**
 * تبدیل ورودی مدیر به بازه‌ی زمانی.
 * دو حالت: تاریخ شروع/پایان دستی، یا تاریخ شروع + مدت (روز/هفته/ماه/سال).
 */
function resolvePeriod(input, tz, tNow, opts = {}) {
  let startsAt;
  if (input.startDate) {
    const d = time.parseDateOnly(input.startDate);
    if (!d) throw new BadInput('تاریخ شروع نامعتبر است (قالب درست: YYYY-MM-DD)');
    startsAt = time.startOfZonedDay(d, tz);
  } else if (opts.fallbackStart !== undefined) {
    startsAt = opts.fallbackStart;
  } else {
    startsAt = tNow;
  }

  let endsAt;
  if (input.endDate) {
    const d = time.parseDateOnly(input.endDate);
    if (!d) throw new BadInput('تاریخ پایان نامعتبر است (قالب درست: YYYY-MM-DD)');
    endsAt = time.endOfZonedDay(d, tz);   // کل روز پایان جزو اشتراک است
  } else if (input.amount !== undefined && input.unit) {
    try { endsAt = time.addDuration(startsAt, input.amount, input.unit, tz); }
    catch (e) { throw new BadInput(e.message); }
  } else {
    throw new BadInput('یا تاریخ پایان را وارد کنید یا مدت اشتراک (amount + unit) را');
  }

  return { startsAt, endsAt };
}

function clampInt(v, min, max, dflt) {
  const n = Number(v);
  if (!Number.isFinite(n)) return dflt;
  return Math.min(max, Math.max(min, Math.round(n)));
}

class BadInput extends Error {
  constructor(message, status = 400) { super(message); this.status = status; this.expose = true; }
}

module.exports = {
  LIVE_STATUSES, getLiveSubscription, getSubscriptionById, listSubscriptions,
  evaluate, evaluateUser, createSubscription, updateSubscription, renewSubscription,
  resolvePeriod, parseFeatures, BadInput,
};
