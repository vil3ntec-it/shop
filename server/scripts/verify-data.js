#!/usr/bin/env node
'use strict';
/**
 * بررسیِ ناوردایی‌های داده (DATA-*، بخشِ ۲۹.۵) — **فقط خواندنی**.
 *
 *   npm run verify-data                         همهٔ بررسی‌ها، همهٔ دکان‌ها (DATABASE_URL)
 *   npm run verify-data -- --shop <id>          فقط یک دکان
 *   npm run verify-data -- --check sum          فقط یک خانواده یا یک بررسی (چند بار هم می‌شود)
 *   npm run verify-data -- --json               خروجیِ ماشینی
 *   npm run verify-data -- --list               فهرستِ بررسی‌ها
 *   npm run verify-data -- --samples 20         نمونه‌های بیشتر از هر تخلف
 *
 * کدِ خروج: ۰ بی خطا (هشدار ممکن است) · ۲ دست‌کم یک تخلفِ «خطا» · ۱ خطای اجرا.
 * همه در یک تراکنشِ READ ONLY؛ هیچ چیزی نوشته یا «درست» نمی‌شود. نمونه‌ها فقط
 * شناسه‌اند — نام، تلفن یا متنی از مشتری چاپ نمی‌شود.
 */

function parseArgs(argv) {
  const out = { shop: null, checks: [], json: false, list: false, samples: 5 };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') out.json = true;
    else if (a === '--list') out.list = true;
    else if (a === '--shop' && argv[i + 1]) out.shop = argv[++i];
    else if (a === '--check' && argv[i + 1]) out.checks.push(argv[++i]);
    else if (a === '--samples' && argv[i + 1]) out.samples = Math.min(100, Math.max(1, Number(argv[++i]) || 5));
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  return out;
}

/** ⇒ کدِ خروج. `print` برای آزمون جایگزین می‌شود. */
async function main(argv, print = (s) => console.log(s)) {
  const opts = parseArgs(argv);
  const vd = require('../src/lib/verify-data');
  if (opts.list) {
    for (const c of vd.listChecks()) print(`${c.severity === 'warn' ? '⚠' : '✖'} ${c.id} — ${c.title}`);
    return 0;
  }
  const report = await vd.verify({ shopId: opts.shop, only: opts.checks, samples: opts.samples });
  if (opts.json) {
    print(JSON.stringify(report, null, 2));
  } else {
    for (const c of report.checks) {
      const mark = c.count === 0 ? '✔' : c.severity === 'warn' ? '⚠' : '✖';
      print(`${mark} ${c.id} — ${c.count === 0 ? 'سالم' : `${c.count} مورد: ${c.title}`} (${c.ms}ms)`);
      for (const s of c.samples) print(`    ${s.shop ? s.shop + ' ' : ''}${s.ref}${s.value !== null ? ` = ${s.value}` : ''}`);
    }
    print(report.ok
      ? `\nسالم: هیچ خطایی نیست${report.warnings ? ` (${report.warnings} هشدار)` : ''}.`
      : `\n${report.errors} خطا و ${report.warnings} هشدار — جزئیات بالا. هیچ چیزی تغییر نکرد.`);
  }
  return report.ok ? 0 : 2;
}

if (require.main === module) {
  const { closeDb } = require('../src/db');
  main(process.argv.slice(2))
    .then(async (code) => { await closeDb(); process.exit(code); })
    .catch(async (err) => {
      console.error('بررسی انجام نشد:', err.message);
      try { await closeDb(); } catch { /* بسته */ }
      process.exit(1);
    });
}

module.exports = { main, parseArgs };
