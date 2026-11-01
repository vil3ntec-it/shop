'use strict';
/**
 * نبضِ کارهای پس‌زمینه — پنلِ مرکزی ۴.۹ («زمان‌بندها و کارهای پس‌زمینه، بی سبزِ ساختگی»).
 *
 * تا امروز هیچ‌جا ثبت نمی‌شد که صفِ ایمیل، زمان‌سنجِ سفارش، اعلان‌ها یا خانه‌تکانی **واقعاً**
 * اجرا شده‌اند یا نه؛ پنل فقط می‌توانست «روشن است» را حدس بزند. این‌جا هر دورِ هر کار ثبت
 * می‌شود: کی شروع شد، کی موفق تمام شد، آخرین خطا (فقط نام و کدِ خطا — پیامِ خام نه).
 *
 *   wrap(name, everyMs, fn) ⇒ تابعی که همان fn را اجرا و نتیجه را ثبت می‌کند
 *   snapshot(now)            ⇒ [{ name, everyMs, registeredAt, lastRunAt, lastOkAt, lastFailAt, lastError, runs, fails, status }]
 *
 * وضعیت (تعریفِ صریح، همان که پنل نشان می‌دهد):
 *   ok       آخرین دور موفق بود و از آخرین موفقیت (یا از راه‌اندازی، اگر هنوز نوبتش نرسیده) کمتر از
 *            «مهلت» = ۲ × فاصله + ۶۰ ثانیه گذشته
 *   warn     آخرین دور شکست خورد، یا از مهلت گذشته و دوری اجرا نشده (کار گیر کرده یا ایستاده)
 *   unknown  در این پروسه راه نیفتاده (مثلاً در آزمون یا وقتی خاموشش کرده‌اند) — نه سبز، نه سرخ
 *
 * ⚠️ در حافظه است: پس از راه‌اندازیِ دوباره از نو شروع می‌شود و `registeredAt` همین را می‌گوید.
 */
const jobs = new Map();

function entry(name, everyMs) {
  let j = jobs.get(name);
  if (!j) {
    j = { name, everyMs: Number(everyMs) || 0, registeredAt: Date.now(), lastRunAt: null, lastOkAt: null,
          lastFailAt: null, lastError: '', runs: 0, fails: 0, running: false };
    jobs.set(name, j);
  } else if (everyMs) {
    j.everyMs = Number(everyMs) || j.everyMs;
  }
  return j;
}

/** فقط نام و کد؛ پیامِ خطا ممکن است نشانی یا تکه‌ای از پرس‌وجو داشته باشد. */
function errLabel(err) {
  const name = String(err?.name || 'Error').slice(0, 40);
  const code = err?.code ? `:${String(err.code).slice(0, 40)}` : '';
  return `${name}${code}`;
}

function wrap(name, everyMs, fn) {
  entry(name, everyMs);
  return async function run(...args) {
    const j = entry(name);
    j.lastRunAt = Date.now();
    j.runs += 1;
    j.running = true;
    try {
      const out = await fn(...args);
      j.lastOkAt = Date.now();
      j.lastError = '';
      return out;
    } catch (err) {
      j.lastFailAt = Date.now();
      j.fails += 1;
      j.lastError = errLabel(err);
      throw err;
    } finally {
      j.running = false;
    }
  };
}

/** ثبت در لحظهٔ زمان‌بندی (پیش از نخستین دور). */
function register(name, everyMs) { entry(name, everyMs); }

/** کاری که بی `await` چند زیرکار را راه می‌اندازد (خانه‌تکانی): فقط «اجرا شد». */
function ran(name, everyMs) {
  const j = entry(name, everyMs);
  j.lastRunAt = Date.now();
  j.lastOkAt = j.lastRunAt;
  j.runs += 1;
}

/** خطای یک زیرکار — همان کار را «هشدار» می‌کند تا دورِ موفقِ بعدی. */
function failed(name, err) {
  const j = entry(name);
  j.lastFailAt = Date.now();
  j.fails += 1;
  j.lastError = errLabel(err);
}

function statusOf(j, t) {
  const grace = 2 * j.everyMs + 60_000;
  if (j.lastFailAt && (!j.lastOkAt || j.lastFailAt > j.lastOkAt)) return 'warn';
  const since = j.lastOkAt || j.registeredAt;
  return t - since > grace ? 'warn' : 'ok';
}

function snapshot(t = Date.now()) {
  return [...jobs.values()].map((j) => ({
    name: j.name, everyMs: j.everyMs, registeredAt: j.registeredAt, lastRunAt: j.lastRunAt, lastOkAt: j.lastOkAt,
    lastFailAt: j.lastFailAt, lastError: j.lastError, runs: j.runs, fails: j.fails, running: j.running,
    status: statusOf(j, t),
  })).sort((a, b) => a.name.localeCompare(b.name));
}

/** کارهایی که این سرور دارد — هر کدام که در این پروسه ثبت نشده ⇒ `unknown`. */
const KNOWN = Object.freeze([
  'login-outbox', 'order-notify', 'order-timeout', 'notices', 'housekeeping', 'ops-alerts',
]);

function report(t = Date.now()) {
  const seen = snapshot(t);
  const names = new Set(seen.map((j) => j.name));
  const missing = KNOWN.filter((n) => !names.has(n)).map((name) => ({ name, status: 'unknown', registeredAt: null }));
  return [...seen, ...missing];
}

function _reset() { jobs.clear(); }

module.exports = { wrap, register, ran, failed, snapshot, report, statusOf, KNOWN, _reset };
