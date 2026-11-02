'use strict';
/**
 * زمان‌بندِ تطبیقِ حساب‌پی — تورِ ایمنیِ وب‌هوکِ گم‌شده یا تأییدِ دومِ دیرکرده (#609). از `lib/payment-intents.js` جدا شد
 * تا زیرِ سقفِ اندازه بماند؛ رفتار همان. `reconcile` و `provider` از همان پرونده (بی‌حلقه: هنگامِ اجرا خوانده می‌شوند).
 */
const crypto = require('crypto');
const { one, query, now } = require('../db');
const reconcile = (...a) => require('./payment-intents').reconcile(...a);
const provider = () => require('./payment-intents').provider();


/*
 *  هر ۵ دقیقه قصدهای زنده و تازه‌منقضی (۲ ساعتِ اخیر؛ مهلتِ قصد ۱۵ دقیقه است) و هر ۱۲ نوبت (≈ ساعتی،
 *  و نخستین نوبت پس از راه‌اندازی) همان ۴۸ ساعتِ پیش‌فرض. فقط وقتی ارائه‌دهندهٔ فعال پیکربندی شده و درِ
 *  وضعیت دارد؛ بی کلید هیچ پرسشی نمی‌رود. دو نمونهٔ سرور هم‌زمان اجرا نمی‌کنند: قفلِ اجاره‌ای در
 *  `app_config` (`updated_at` = پایانِ اجاره؛ نمونه‌ای که وسطِ کار بمیرد، پس از LEASE_MS آزاد می‌شود).
 *  این کلید در فهرستِ سفیدِ درِ مدیریت (`plans.publicConfig`) نیست و بیرون نمی‌رود.
 */
const RECONCILE_TICK_MS = 5 * 60 * 1000;
const RECONCILE_RECENT_MS = 2 * 60 * 60 * 1000;
const RECONCILE_FULL_EVERY = 12;
const RECONCILE_BUDGET_MS = 4 * 60 * 1000;
const RECONCILE_LEASE_KEY = 'lease:payments.reconcile';
const RECONCILE_LEASE_MS = 10 * 60 * 1000;
const INSTANCE = `${process.pid}.${crypto.randomBytes(6).toString('hex')}`;

/**
 * تطبیق کاری دارد؟ = ارائه‌دهندهٔ فعال پیکربندی شده و درِ وضعیت دارد. حساب‌پی: کلیدِ رمزگذاریِ سرور هست و هر قصد
 * با آداپتورِ **دکانِ خودش** (`forShop`) پرسیده می‌شود (`payment-intents.reconcile`)؛ هرگز کلیدِ سکو.
 */
function reconcileReady() {
  const ad = provider();
  return Boolean(ad && ad.configured() && (typeof ad.fetchStatus === 'function' || typeof ad.forShop === 'function'));
}

/** اجارهٔ میانِ نمونه‌ها: فقط اگر آزاد، منقضی یا مالِ همین نمونه باشد. */
async function takeReconcileLease(at = now()) {
  const r = await one(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1, $2, $3)
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at
      WHERE app_config.updated_at <= $4 OR app_config.value = $2
     RETURNING key`,
    [RECONCILE_LEASE_KEY, INSTANCE, at + RECONCILE_LEASE_MS, at]
  );
  return Boolean(r);
}
const dropReconcileLease = () => query(
  'UPDATE app_config SET updated_at = 0 WHERE key = $1 AND value = $2', [RECONCILE_LEASE_KEY, INSTANCE]
);

let reconTimer = null;
let reconRunning = false;
let reconTicks = 0;

/**
 * OPS-01@29: نوبت‌های پیاپیِ ناموفقِ تطبیق (پرتاب، یا `errors > 0` ِ درِ وضعیت) — فقط در حافظهٔ همین نمونه و فقط
 * `lib/ops-paging.js` می‌خواند (سه نوبت ⇒ هشدارِ بحرانی). نوبتی که اجرا نشد (بی کلید، قفلِ نمونهٔ دیگر) چیزی نمی‌شمارد.
 */
const reconHealth = { streak: 0, lastOkAt: null, lastFailAt: null, lastError: '' };
function noteReconcile(fail, at = Date.now()) {
  if (!fail) { Object.assign(reconHealth, { streak: 0, lastOkAt: at, lastError: '' }); return; }
  const code = fail.code ? `:${String(fail.code).slice(0, 40)}` : '';
  Object.assign(reconHealth, { streak: reconHealth.streak + 1, lastFailAt: at, lastError: `${String(fail.name || 'Error').slice(0, 40)}${code}` });
}
const reconcileHealth = () => ({ ...reconHealth });
/**
 * یک نوبتِ زمان‌بند. null ⇒ اجرا نشد (پیکربندی نشده، نوبتِ قبلی هنوز در کار، یا نمونهٔ دیگری قفل دارد).
 * @returns {Promise<null|{checked, paid, closed, mismatched, errors, truncated, wide}>}
 */
async function reconcileTick({ wide } = {}) {
  if (reconRunning || !reconcileReady()) return null;
  reconRunning = true;
  try {
    if (!(await takeReconcileLease())) return null;
    try {
      const full = wide ?? (reconTicks++ % RECONCILE_FULL_EVERY === 0);
      let out;
      try {
        out = await reconcile({ ...(full ? {} : { sinceMs: RECONCILE_RECENT_MS }), budgetMs: RECONCILE_BUDGET_MS });
      } catch (err) { noteReconcile(err); throw err; }
      noteReconcile(out.errors ? { name: 'ReconcileErrors', code: String(out.errors) } : null);
      if (out.paid || out.closed || out.mismatched || out.errors || out.truncated || out.flagged) {
        console.log(`[payments] تطبیق: ${JSON.stringify(out)}`);
      }
      return { ...out, wide: full };
    } finally {
      await dropReconcileLease().catch(() => {});
    }
  } finally {
    reconRunning = false;
  }
}
function startReconciler() {
  if (reconTimer) return;
  reconTimer = setInterval(() => reconcileTick().catch((err) => console.error('[payments] تطبیق', err.message)), RECONCILE_TICK_MS);
  if (reconTimer.unref) reconTimer.unref();
}
function stopReconciler() {
  if (reconTimer) clearInterval(reconTimer);
  reconTimer = null;
}

module.exports = { reconcileTick, startReconciler, stopReconciler, RECONCILE_LEASE_KEY, reconcileHealth, noteReconcile };
