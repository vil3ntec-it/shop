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
 *       --no-record               ثبت نشود (پیش‌فرض: هر تمرین، موفق یا ناموفق، در `<BACKUP_PATH>/drills.jsonl`)
 *
 * OPS-02@29: عددِ RTO/RPO ِ هر تمرین ثبت می‌شود (فقط عدد؛ `lib/restore-drill.js` ⇐ `record`) و «سلامت» و
 * `GET /api/admin/backups` آخرینش را نشان می‌دهند؛ تمرینِ ناموفق ⇒ «سلامت» زرد تا تمرینِ موفقِ بعدی.
 *
 * خروجی فقط عدد است (شمار، جمع، زمان) — بی هیچ دادهٔ دکان. کدِ خروج: ۰ برابر و در هدفِ D-24، ۱ نابرابر یا خطا،
 * ۲ برابر ولی بیرون از هدف (RPO > `BACKUP_RPO_HOURS` ۲۴، RTO > `BACKUP_RTO_HOURS` ۱۲، یا سنِ پشتیبان نامعلوم)، یا (F0-28)
 * برابر ولی رازهای مهرشده بی BACKUP_SECRETS_KEY باز نشدند؛ پاکتِ دست‌خورده یا کلیدِ دیگر ⇒ ۱.
 * ⛔ روی دیتابیسِ زنده نمی‌نویسد؛ پشتیبانِ PGlite در حافظه برمی‌گردد. پشتیبانِ رمزدار BACKUP_PASSPHRASE
 *    همان سرور را می‌خواهد (از محیط؛ هرگز در خطِ فرمان).
 */
const backup = require('../src/lib/backup');
const drill = require('../src/lib/restore-drill');
const { closeDb } = require('../src/db');

function args(argv) {
  const o = { file: '', migrate: false, keep: false, json: false, targetUrl: '', record: true };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--migrate') o.migrate = true;
    else if (a === '--keep') o.keep = true;
    else if (a === '--json') o.json = true;
    else if (a === '--no-record') o.record = false;
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
  const r = await (o.record ? drill.drillAndRecord(o) : drill.drill(o));
  if (o.json) {
    console.log(JSON.stringify(r, null, 2));
  } else {
    console.log(`پشتیبان: ${r.file}  ⇒  ${r.where}`);
    console.log(`جدول‌ها ${r.summary.tables}، ردیف‌ها ${r.summary.rows}، ستونِ پول/مقدار ${r.summary.moneyColumns}، کلیدِ دفتر ${r.summary.ledgerKeys}`);
    console.log(`بازگرداندن (RTO): ${sec(r.restoredMs)} — با تطبیق: ${sec(r.verifiedMs)}؛ سنِ پشتیبان (RPO ِ همین فایل): ${age(r.backupAgeMs)}`);
    const mark = (v) => (v === true ? '✔' : v === false ? '✖' : '؟');
    console.log(`هدفِ D-24: RPO ≤ ${age(r.targets.rpoMs)} ${mark(r.targets.rpoMet)}، RTO ≤ ${age(r.targets.rtoMs)} ${mark(r.targets.rtoMet)}`);
    if (o.record) console.log(r.recorded ? 'ثبت شد: «سلامت» و پنل همین تمرین را نشان می‌دهند' : '⚠️ تمرین ثبت نشد (پوشهٔ پشتیبان نوشتنی نیست)');
    if (r.empty) console.log('✖ این پشتیبان هیچ جدولی برنگرداند — پشتیبانِ خالی پشتیبان نیست.');
    if (!r.restore) console.log('⚠️ این پشتیبان manifest ندارد (پیش از این نسخه گرفته شده یا دیتابیس هنگامِ پشتیبان عوض شد): فقط «برگشت»، نه «برابر».');
    else if (r.restore.equal) console.log(`✔ برابر با manifest ِ لحظهٔ پشتیبان (digest ${r.digest.slice(0, 12)}…)`);
    else {
      console.log(`✖ ${r.restore.diffs.length} تفاوت با manifest:`);
      for (const d of r.restore.diffs.slice(0, 30)) console.log(`   ${d.part} ${d.key}: ${d.expected} ⇒ ${d.actual}`);
    }
    if (r.secrets) {
      if (r.secrets.ok) console.log(`✔ ${r.secrets.unsealed} رازِ مهرشده با BACKUP_SECRETS_KEY باز شد؛ کلیدِ مجوز: ${r.secrets.licenseKey === 'same' ? 'همان کلیدِ پیشین' : r.secrets.licenseKey}`);
      else console.log(`✖ رازهای پشتیبان: ${r.secrets.message || `کلیدِ مجوز ${r.secrets.licenseKey}`}`);
    }
    if (r.migration) {
      console.log(`مهاجرت روی کپی: ${r.migration.applied.length ? r.migration.applied.join('، ') : 'هیچ مهاجرتِ تازه‌ای'}`);
      if (r.migration.added.length) console.log(`   تازه: ${r.migration.added.map((x) => x.key).slice(0, 20).join('، ')}`);
      if (r.migration.equal) console.log('   ✔ پس از مهاجرت هیچ شمار، پول یا مقداری گم یا عوض نشد');
      else for (const d of r.migration.diffs.slice(0, 30)) console.log(`   ✖ ${d.part} ${d.key}: ${d.expected} ⇒ ${d.actual}`);
    }
  }
  //  F0-28: پاکتِ رد‌شده ⇒ ۱؛ بی کلید (داده برابر، رازها مهر) ⇒ ۲
  if (r.secrets && !r.secrets.ok) return r.secrets.missingKey && r.ok ? 2 : 1;
  return r.ok ? (r.withinTargets ? 0 : 2) : 1;
}

main()
  .then(async (code) => { await closeDb(); process.exit(code); })
  .catch(async (e) => { console.error('✖', e.message); await closeDb().catch(() => {}); process.exit(1); });
