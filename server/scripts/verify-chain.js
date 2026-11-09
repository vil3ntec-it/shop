#!/usr/bin/env node
'use strict';
/**
 * بررسیِ یکپارچگیِ زنجیرهٔ هش (SEC-02، STF-09) — فقط خواندنی.
 *
 *   node scripts/verify-chain.js                  همهٔ زنجیره‌ها
 *   node scripts/verify-chain.js --chain <نام>    فقط یکی (چند بار هم می‌شود)
 *   node scripts/verify-chain.js --no-rows        ردیف‌های audit_logs را نسنج
 *   node scripts/verify-chain.js --json           خروجیِ ماشینی
 *   node scripts/verify-chain.js --device-audit [<دکان>]
 *                                                 زنجیرهٔ سابقهٔ عملیاتِ دستگاه‌ها (STF-09) روی رونوشتِ همگام‌شده
 *                                                 (`sync_rows`، حذف‌شده‌ها هم) با لنگرِ گزارش‌شدهٔ هر دستگاه
 *                                                 (`device_audit_anchors`) — lib/device-audit-chain.js
 *
 * کدِ خروج: ۰ همه سالم · ۲ دست‌کم یک زنجیره شکسته · ۱ خطا (دیتابیس، ورودی)
 * برای هر زنجیرهٔ شکسته **نخستین** حلقهٔ شکسته و دلیلش چاپ می‌شود؛ برای
 * سالم‌ها «سر» (هشِ آخرین حلقه) تا جای دیگری یادداشت شود و بریدنِ دمِ
 * زنجیره هم بعداً پیدا شود.
 */

//  دلیل‌ها در خودِ کتابخانه‌اند تا پنل (lib/system-health.js) به پوشهٔ scripts تکیه نکند
const { REASONS } = require('../src/lib/hash-chain-store');

function parseArgs(argv) {
  const out = { chains: [], rows: true, json: false, device: false, shops: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--json') out.json = true;
    else if (a === '--device-audit') {
      out.device = true;
      while (argv[i + 1] && !argv[i + 1].startsWith('--')) out.shops.push(argv[++i]);
    }
    else if (a === '--no-rows') out.rows = false;
    else if (a === '--chain' && argv[i + 1]) out.chains.push(argv[++i]);
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  return out;
}

/** ⇒ کدِ خروج. `print` برای آزمون جایگزین می‌شود. */
async function main(argv, print = (s) => console.log(s)) {
  const opts = parseArgs(argv);
  if (opts.device) return deviceAudit(opts, print);
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

/** STF-09 — زنجیرهٔ سابقهٔ دستگاه؛ همان کدهای خروج */
async function deviceAudit(opts, print) {
  const dac = require('../src/lib/device-audit-chain');
  const report = await dac.verifyAllShops({ shops: opts.shops });
  if (opts.json) {
    print(JSON.stringify(report, null, 2));
  } else if (!report.results.length) {
    print('سابقهٔ زنجیره‌داری از دستگاه‌ها نرسیده است (پرچمِ shop.doc_chain خاموش است؟)');
  } else {
    for (const r of report.results) {
      const flagged = new Set(r.extra.map((b) => b.chain));
      for (const c of r.chains) {
        if (c.broken || flagged.has(c.chain)) continue;
        const anchor = c.anchor ? `، لنگر #${c.anchor.seq}` : '';
        const pending = c.pending ? `، ${c.pending} ردیفِ زیرِ لنگر هنوز در راه (صفِ دستگاه)` : '';
        print(`✔ ${r.shopId} ${c.chain} — ${c.rows} ردیف، سر ${c.head}${anchor}${pending}`);
      }
      const all = [...r.bad, ...r.chains.filter((c) => c.broken).map((c) => c.broken), ...r.extra];
      for (const b of all) print(`✖ ${r.shopId} ${b.chain} — ردیفِ #${b.seq}: ${dac.REASONS[b.reason] || b.reason} (${b.reason})`);
      if (r.legacy) print(`  ${r.shopId}: ${r.legacy} ردیفِ بی زنجیره (پیش از روشن شدن یا از وب)`);
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
