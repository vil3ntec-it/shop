'use strict';
/**
 * `PGlite.create` با حلقهٔ رویدادِ زنده — راهِ ساختنِ PGlite در سرور و ابزارهایش (`db.js`،
 * `restore-drill.js`، `scripts/move-db.js`).
 *
 * ⛔ زیرِ Electron 38 (`ELECTRON_RUN_AS_NODE`: سرورِ خانگی زیرِ پنل، و کارِ «آزمون‌های سرور زیرِ
 * Electron» ِ CI) ساختنِ PGlite گاه برای همیشه می‌ایستد — بی خطا، بی CPU، بی هیچ خروجی. هنگامِ
 * کامپایل و راه‌اندازیِ ناهم‌زمانِ WASM اگر هیچ دستگیرهٔ ref‌داری در حلقهٔ libuv نباشد، uv_run
 * برمی‌گردد و Node در `NodePlatform::DrainTasks` منتظرِ کارهای رشته‌های پس‌زمینهٔ V8 می‌ماند؛ آن
 * کارها (رشته‌ها در futex) هرگز تمام نمی‌شوند. نشانه‌اش در کارِ Electron ِ CI همان «سکوت پس از آخرین
 * آزمونِ یک پرونده» تا ۴۵ دقیقه است: پروندهٔ **بعدی** در `h.start()` (نخستین پرس‌وجو ⇐ ساختنِ PGlite)
 * پیش از هر خروجی می‌ایستد.
 *
 * بازتولید (۱۴۰۵/۰۸/۱۱، Electron 38.8.6 با Node 22.22.0، زیرِ بار، هر بار فرآیندی تازه که فقط
 * `PGlite.create({ dataDir: 'memory://' })` می‌زند؛ سه نوبت، شاهد و درمان هم‌زمان): بی این یاور ۷ گیر
 * در ۵۳۰ اجرا، با آن ۰ در ۵۳۰. `scripts/migrate.js` روی پوشهٔ تازه (پیش از این یاور) ۲ گیر در ۸۶ اجرا،
 * و در اجرای کاملِ آزمون‌ها زیرِ Electron یک بار (زیرفرآیندِ migrate.js ِ move-db.test.js). gdb در هر
 * نُه گیر: بی هیچ خروجی، رشتهٔ اصلی در
 * `uv_cond_wait ← node::NodePlatform::DrainTasks ← node::SpinEventLoopInternal`. همان اسکریپت با Node
 * 22.22.0 ِ بی Electron: ۰ در ۶۰. با تایمرِ ref‌دار uv_run برنمی‌گردد، DrainTasks صدا زده نمی‌شود و
 * نتیجهٔ کارِ پس‌زمینه از راهِ خودِ حلقه می‌رسد. تایمر پس از ساختن (یا شکستش) برداشته می‌شود و فرآیند
 * را نگه نمی‌دارد. `test/pglite-create.test.js` همین شرط را می‌سنجد، نه خودِ گیر را (که در یک اجرا
 * بازتولید نمی‌شود).
 */

/** همان `PGlite.create(options)`؛ فقط حلقهٔ رویداد تا پایانش زنده می‌ماند. */
async function createPglite(options) {
  const { PGlite } = require('@electric-sql/pglite');
  const keepAlive = setInterval(() => {}, 60_000);
  try {
    return await PGlite.create(options);
  } finally {
    clearInterval(keepAlive);
  }
}

module.exports = { createPglite };
