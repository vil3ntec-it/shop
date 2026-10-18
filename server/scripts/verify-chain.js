#!/usr/bin/env node
'use strict';
/**
 * بررسیِ یکپارچگیِ زنجیرهٔ هش (SEC-02، STF-09) — فقط خواندنی.
 *
 *   node scripts/verify-chain.js                  همهٔ زنجیره‌ها
 *   node scripts/verify-chain.js --chain <نام>    فقط یکی (چند بار هم می‌شود)
 *   node scripts/verify-chain.js --no-rows        ردیف‌های audit_logs را نسنج
 *   node scripts/verify-chain.js --json           خروجیِ ماشینی
 *
 * کدِ خروج: ۰ همه سالم · ۲ دست‌کم یک زنجیره شکسته · ۱ خطا (دیتابیس، ورودی)
 * برای هر زنجیرهٔ شکسته **نخستین** حلقهٔ شکسته و دلیلش چاپ می‌شود؛ برای
 * سالم‌ها «سر» (هشِ آخرین حلقه) تا جای دیگری یادداشت شود و بریدنِ دمِ
 * زنجیره هم بعداً پیدا شود.
 */

const REASONS = {
  seq_gap: 'حلقه‌ای جا افتاده',
  prev_mismatch: 'پیوند به حلقهٔ قبلی نمی‌خورد',
  bad_body: 'متنِ حلقه خوانا نیست',
  not_canonical: 'متنِ حلقه دست‌کاری شده (قالب)',
  body_mismatch: 'حلقه جابه‌جا شده',
  hash_mismatch: 'متنِ حلقه با هشش نمی‌خواند',
  row_missing: 'ردیفِ سابقه پاک شده',
  row_changed: 'ردیفِ سابقه دست‌کاری شده',
};

function parseArgs(argv) {
  const out = { chains: [], rows: true, json: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') out.json = true;
    else if (a === '--no-rows') out.rows = false;
    else if (a === '--chain' && argv[i + 1]) out.chains.push(argv[++i]);
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  return out;
}

/** ⇒ کدِ خروج. `print` برای آزمون جایگزین می‌شود. */
async function main(argv, print = (s) => console.log(s)) {
  const opts = parseArgs(argv);
  const { verifyAll } = require('../src/lib/hash-chain-store');
  const report = await verifyAll({ chains: opts.chains, rows: opts.rows });
  if (opts.json) {
    print(JSON.stringify(report, null, 2));
  } else if (!report.results.length) {
    print('زنجیره‌ای نیست (پرچمِ security / hashchain.audit خاموش است؟)');
  } else {
    for (const r of report.results) {
      if (r.ok) print(`✔ ${r.chain} — ${r.length} حلقه، سر ${r.head}`);
      else print(`✖ ${r.chain} — نخستین حلقهٔ شکسته #${r.seq}: ${REASONS[r.reason] || r.reason} (${r.reason})؛ ${r.length} حلقهٔ پیش از آن سالم`);
    }
  }
  return report.ok ? 0 : 2;
}

if (require.main === module) {
  const { closeDb } = require('../src/db');
  main(process.argv.slice(2))
    .then(async (code) => { await closeDb(); process.exit(code); })
    .catch(async (err) => {
      console.error('✖', err.message);
      try { await closeDb(); } catch { /* بسته بود */ }
      process.exit(1);
    });
}

module.exports = { main, parseArgs, REASONS };
