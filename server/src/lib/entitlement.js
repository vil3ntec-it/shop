'use strict';
/**
 * تصمیم نهایی درباره‌ی «این کاربر همین حالا چه چیزی باز دارد».
 *
 * ترتیب بررسی:
 *   ۱) اشتراک فعال      → قابلیت‌های همان اشتراک
 *   ۲) دوره آزمایشی باز → همه‌ی قابلیت‌های اشتراکی
 *   ۳) وگرنه            → فقط قابلیت‌های رایگان
 *
 * همه‌ی تاریخ‌ها با ساعت سرور حساب می‌شوند. ساعت گوشی هیچ نقشی ندارد،
 * پس عقب یا جلو بردن آن دوره آزمایشی را تمدید نمی‌کند.
 */
const { getDb, now } = require('../db');
const subs = require('./subscriptions');
const plansLib = require('./plans');
const { FREE_KEYS, CORE_KEYS, PAID_KEYS } = require('./features');

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * شروع دوره آزمایشی برای یک حساب تازه.
 * هر حساب فقط یک بار — trial_used جلوی تکرار را می‌گیرد، پس خروج و
 * ورود دوباره یا نصب مجدد برنامه دوره را از نو شروع نمی‌کند.
 */
function startTrialIfEligible(userId, atMs = now()) {
  const db = getDb();
  const u = db.prepare('SELECT trial_used, trial_started_at, trial_ends_at FROM users WHERE id=?').get(userId);
  if (!u) return null;
  if (u.trial_used) return { startedAt: u.trial_started_at, endsAt: u.trial_ends_at, fresh: false };

  const days = plansLib.trialDays(db);
  const startedAt = atMs;
  const endsAt = atMs + days * DAY_MS;
  db.prepare('UPDATE users SET trial_started_at=?, trial_ends_at=?, trial_used=1, updated_at=? WHERE id=?')
    .run(startedAt, endsAt, atMs, userId);
  return { startedAt, endsAt, fresh: true };
}

function trialStateOf(user, atMs = now()) {
  if (!user || !user.trial_used || !user.trial_ends_at) {
    return { used: false, active: false, startedAt: null, endsAt: null, daysLeft: 0, msLeft: 0 };
  }
  const msLeft = user.trial_ends_at - atMs;
  return {
    used: true,
    active: msLeft > 0,
    startedAt: user.trial_started_at,
    endsAt: user.trial_ends_at,
    msLeft: Math.max(0, msLeft),
    daysLeft: Math.max(0, Math.ceil(msLeft / DAY_MS)),
  };
}

/** پیام کوتاه وضعیت، همان‌طور که در برنامه نشان داده می‌شود. */
function trialMessage(trial) {
  if (!trial.used) return '';
  if (!trial.active) {
    return 'دوره آزمایشی شما به پایان رسیده است. برای ادامه استفاده، یک اشتراک انتخاب کنید.';
  }
  if (trial.msLeft < DAY_MS) return 'کمتر از یک روز از دوره آزمایشی شما باقی مانده است';
  const d = trial.daysLeft.toLocaleString('fa-IR');
  return `${d} روز از دوره آزمایشی شما باقی مانده است`;
}

/**
 * وضعیت کامل دسترسی کاربر.
 * @returns {{source, features, trial, subscription, isPaid, serverTime}}
 */
function entitlementOf(userId, atMs = now()) {
  const db = getDb();
  const user = db.prepare('SELECT * FROM users WHERE id=?').get(userId);
  const trial = trialStateOf(user, atMs);
  const subState = subs.evaluateUser(userId, atMs);

  if (subState.active) {
    // قابلیت‌های رایگان همیشه اضافه می‌شوند: مشترکِ پولی نباید چیزی
    // کمتر از یک کاربر رایگان داشته باشد، حتی اگر پلنش آن را فهرست نکرده باشد.
    const merged = Array.from(new Set(subState.features.concat(FREE_KEYS)));
    return {
      source: 'subscription',
      features: merged,
      free: FREE_KEYS.slice(), core: CORE_KEYS.slice(),
      trial, subscription: subState, isPaid: true,
      message: '', serverTime: atMs,
    };
  }

  if (trial.active) {
    return {
      source: 'trial',
      // دوره آزمایشی همه‌ی قابلیت‌های اشتراکی را باز می‌کند
      features: PAID_KEYS.concat(FREE_KEYS),
      free: FREE_KEYS.slice(), core: CORE_KEYS.slice(),
      trial, subscription: subState, isPaid: false,
      message: trialMessage(trial), serverTime: atMs,
    };
  }

  return {
    source: 'free',
    features: FREE_KEYS.slice(),
    free: FREE_KEYS.slice(), core: CORE_KEYS.slice(),
    trial, subscription: subState, isPaid: false,
    message: trialMessage(trial), serverTime: atMs,
  };
}

/** آیا این قابلیت همین حالا برای کاربر باز است؟ */
function allows(userId, featureKey, atMs = now()) {
  if (CORE_KEYS.includes(featureKey)) return true;
  const e = entitlementOf(userId, atMs);
  return e.features.includes(featureKey);
}

/** دسترسی مهمان (بدون حساب) — فقط رایگان‌ها. */
function guestEntitlement(atMs = now()) {
  return {
    source: 'guest',
    features: FREE_KEYS.slice(),
    free: FREE_KEYS.slice(), core: CORE_KEYS.slice(),
    trial: { used: false, active: false, daysLeft: 0, msLeft: 0, startedAt: null, endsAt: null },
    subscription: { state: 'none', active: false },
    isPaid: false,
    message: '', serverTime: atMs,
  };
}

module.exports = {
  DAY_MS, startTrialIfEligible, trialStateOf, trialMessage,
  entitlementOf, allows, guestEntitlement,
};
