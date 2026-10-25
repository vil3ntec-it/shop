'use strict';
/**
 * آمارِ planner برای PGlite (PERF-03، PERF-06؛ بخشِ ۲۹.۶ بند ۸).
 *
 * PostgreSQL آمارِ جدول‌ها را با autovacuum خودش تازه نگه می‌دارد. PGlite
 * تک‌کاربره است و **autovacuum ندارد**، و این سرور هیچ‌جا ANALYZE نمی‌زد؛ پس
 * روی سرورِ خانگی planner هیچ‌وقت آماری نداشت و برای هر join برآوردِ «۱ ردیف»
 * می‌کرد. نتیجهٔ سنجیده (`bench/server.mjs`): کاتالوگِ ویترین join ِ
 * `storefront_products` با `sync_rows` را حلقهٔ تو در توی بی‌کلید می‌دوید —
 * برای هر کالا همهٔ کالاهای دکان — یعنی O(n²): ۴۰۰ کالا ۰٫۸ ثانیه، ۱۰۰۰ کالا
 * ده‌ها ثانیه، و ۲۰ هزار کالا عملاً هرگز.
 *
 * این‌جا فقط همان کارِ autovacuum برای PGlite: یک `ANALYZE` کمی پس از بالا
 * آمدن و سپس هر چند ساعت. داده عوض نمی‌شود و نتیجهٔ هیچ پرس‌وجویی هم؛ فقط
 * planner اندازهٔ واقعیِ جدول‌ها را می‌داند. روی PostgreSQL هیچ کاری نمی‌کند.
 *
 *   PGLITE_ANALYZE_HOURS   فاصلهٔ گذرها (پیش‌فرض ۶؛ ۰ ⇒ خاموش)
 */
const { query, isPglite } = require('../db');

const DEFAULT_HOURS = 6;
const FIRST_DELAY_MS = 60_000;

function hours() {
  const raw = process.env.PGLITE_ANALYZE_HOURS;
  if (raw === undefined || raw === '') return DEFAULT_HOURS;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : DEFAULT_HOURS;
}

/**
 * یک گذر. ⇒ true اگر ANALYZE اجرا شد (فقط PGlite).
 * جدول به جدول، هر کدام یک دستورِ جدا: PGlite یک اتصال دارد و یک ANALYZE ِ یک‌جا روی
 * دادهٔ مرجع (۶۰۰ هزار ردیف) حدودِ ۱۱ ثانیه همهٔ درخواست‌ها را پشتِ خودش نگه می‌داشت؛
 * این‌طور درخواست‌ها میانِ جدول‌ها جا می‌گیرند.
 */
async function analyzeNow() {
  if (!isPglite()) return false;
  const tables = (await query(
    "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
  )).rows.map((r) => r.tablename);
  for (const t of tables) await query(`ANALYZE "${String(t).replace(/"/g, '""')}"`);
  return true;
}

/**
 * گذرِ اول پس از `firstDelayMs` (تا بالا آمدن کند نشود)، سپس هر `PGLITE_ANALYZE_HOURS`.
 * ⇒ { stop() } یا null (PostgreSQL، یا خاموش). زمان‌سنج‌ها فرایند را زنده نگه نمی‌دارند.
 */
function schedule({ firstDelayMs = FIRST_DELAY_MS, log = console } = {}) {
  const h = hours();
  if (!isPglite() || h <= 0) return null;
  const tick = async () => {
    const t0 = Date.now();
    try {
      await analyzeNow();
      const ms = Date.now() - t0;
      if (ms > 1000) log.log(`[pglite] آمارِ جدول‌ها تازه شد (${ms}ms)`);
    } catch (err) {
      log.error('[pglite] ANALYZE نشد:', err.message);
    }
  };
  const first = setTimeout(tick, firstDelayMs);
  const every = setInterval(tick, h * 3_600_000);
  if (first.unref) first.unref();
  if (every.unref) every.unref();
  return { stop() { clearTimeout(first); clearInterval(every); } };
}

module.exports = { analyzeNow, schedule, hours, DEFAULT_HOURS };
