#!/usr/bin/env node
'use strict';
/**
 * تمرینِ بازیابی با تطبیق (§۱۲؛ `docs/security/data-fa.md`، «تمرینِ بازیابی»):
 *
 *   npm run restore-drill                       پشتیبانِ تازه (دستی) ⇒ بازگرداندن در دیتابیسِ جدا ⇒ تطبیق
 *   npm run restore-drill -- <فایل>             همان، روی یک پشتیبانِ موجود (با manifest ِ meta.json اش)
 *   npm run restore-drill -- <فایل> --migrate   و مهاجرت‌های همین نسخه روی کپی، با تطبیقِ دوباره
 *       --target <DATABASE_URL>   دیتابیسِ خالیِ PostgreSQL برای بازگرداندن (بی آن: shop_drill_* می‌سازد و می‌اندازد)
 *       --keep                    دیتابیسِ ساخته‌شده نگه داشته شود
 *       --json                    خروجیِ ماشینی
 *
 * خروجی فقط عدد است (شمار، جمع، زمان) — بی هیچ دادهٔ دکان. کدِ خروج: ۰ برابر، ۱ نابرابر یا خطا.
 * ⛔ روی دیتابیسِ زنده نمی‌نویسد؛ پشتیبانِ PGlite در حافظه برمی‌گردد. پشتیبانِ رمزدار BACKUP_PASSPHRASE
 *    همان سرور را می‌خواهد (از محیط؛ هرگز در خطِ فرمان).
 */
const backup = require('../src/lib/backup');
const drill = require('../src/lib/restore-drill');
const { closeDb } = require('../src/db');

function args(argv) {
  const o = { file: '', migrate: false, keep: false, json: false, targetUrl: '' };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--migrate') o.migrate = true;
    else if (a === '--keep') o.keep = true;
    else if (a === '--json') o.json = true;
    else if (a === '--target') o.targetUrl = argv[++i] || '';
    else if (!a.startsWith('--')) o.file = a;
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  return o;
}

const sec = (ms) => (ms === null || ms === undefined ? '—' : `${(ms / 1000).toFixed(1)}s`);
const age = (ms) => (ms === null || ms === undefined ? '—' : ms < 3600e3 ? `${Math.round(ms / 60e3)} دقیقه` : `${(ms / 3600e3).toFixed(1)} ساعت`);

async function main() {
  const o = args(process.argv.slice(2));
  if (!o.file) {
    const made = await backup.run({ kind: 'manual' });
    o.file = made.file;
  }
  const r = await drill.drill(o);
  if (o.json) {
    console.log(JSON.stringify(r, null, 2));
  } else {
    console.log(`پشتیبان: ${r.file}  ⇒  ${r.where}`);
    console.log(`جدول‌ها ${r.summary.tables}، ردیف‌ها ${r.summary.rows}، ستونِ پول/مقدار ${r.summary.moneyColumns}، کلیدِ دفتر ${r.summary.ledgerKeys}`);
    console.log(`بازگرداندن (RTO): ${sec(r.restoredMs)} — با تطبیق: ${sec(r.verifiedMs)}؛ سنِ پشتیبان (RPO ِ همین فایل): ${age(r.backupAgeMs)}`);
    if (r.empty) console.log('✖ این پشتیبان هیچ جدولی برنگرداند — پشتیبانِ خالی پشتیبان نیست.');
    if (!r.restore) console.log('⚠️ این پشتیبان manifest ندارد (پیش از این نسخه گرفته شده یا دیتابیس هنگامِ پشتیبان عوض شد): فقط «برگشت»، نه «برابر».');
    else if (r.restore.equal) console.log(`✔ برابر با manifest ِ لحظهٔ پشتیبان (digest ${r.digest.slice(0, 12)}…)`);
    else {
      console.log(`✖ ${r.restore.diffs.length} تفاوت با manifest:`);
      for (const d of r.restore.diffs.slice(0, 30)) console.log(`   ${d.part} ${d.key}: ${d.expected} ⇒ ${d.actual}`);
    }
    if (r.migration) {
      console.log(`مهاجرت روی کپی: ${r.migration.applied.length ? r.migration.applied.join('، ') : 'هیچ مهاجرتِ تازه‌ای'}`);
      if (r.migration.added.length) console.log(`   تازه: ${r.migration.added.map((x) => x.key).slice(0, 20).join('، ')}`);
      if (r.migration.equal) console.log('   ✔ پس از مهاجرت هیچ شمار، پول یا مقداری گم یا عوض نشد');
      else for (const d of r.migration.diffs.slice(0, 30)) console.log(`   ✖ ${d.part} ${d.key}: ${d.expected} ⇒ ${d.actual}`);
    }
  }
  return r.ok;
}

main()
  .then(async (ok) => { await closeDb(); process.exit(ok ? 0 : 1); })
  .catch(async (e) => { console.error('✖', e.message); await closeDb().catch(() => {}); process.exit(1); });
