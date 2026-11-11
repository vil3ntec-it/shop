#!/usr/bin/env node
'use strict';
/**
 * O-15 الف (۳-۴ ِ `docs/OWNER-DECISIONS-fa.md`) — یک کپیِ بی‌نام از پشتیبانِ واقعی، روی سرورِ خودِ صاحب‌کار:
 *
 *   npm run anonymize -- <فایلِ پشتیبان> [--out <پوشه>] [--target <DATABASE_URL ِ خالی>] [--keep] [--no-verify] [--json]
 *
 *   ۱) پشتیبان در دیتابیسی **جدا** برمی‌گردد (همان `restore-drill.restoreIsolated`: `shop_drill_*` یا `--target`)؛
 *   ۲) اثرِ انگشتِ پیش (`restore-drill.fingerprint`: شمارِ ردیف‌ها و جمعِ پول، قرض و مقدار)؛
 *   ۳) `lib/anonymize.js` نام، تلفن، نشانی، یادداشت، ایمیل و رازها را جای‌گزینِ قطعیِ هم‌شکل می‌کند — اگر مقدارِ ماندگاری با
 *      یکی از آن‌ها یکی باشد (نشت)، هیچ فایلی بیرون نمی‌آید، کپی انداخته می‌شود و خروج ۱؛
 *   ۴) اثرِ انگشتِ پس باید همان پیش باشد (جز مختصاتِ گردشده و تلفنِ عددی، که نامشان گزارش می‌شود)؛
 *   ۵) پشتیبانِ تازه از همان کپی، در `--out` (پیش‌فرض `<BACKUP_PATH>/anonymized`)، با همان رمزِ BACKUP_PASSPHRASE ِ سرور
 *      و `meta.json` ِ خودش — با فرزندی که DATABASE_URL اش همان کپی است (`scripts/backup.js`)؛
 *   ۶) جز با `--no-verify`: همان فایل با `restore-drill` دوباره برگردانده و با manifest اش سنجیده می‌شود؛
 *   ۷) کپیِ جدا انداخته می‌شود (جز `--keep`).
 *
 * خروجی فقط شمار است (جدول، ردیف، شمارِ جای‌گزین‌ها به گونه)، هرگز مقدار. ⛔ روی دیتابیسِ زنده نمی‌نویسد؛ کلیدِ جای‌گزین
 * تصادفیِ همین اجراست و هیچ‌جا نمی‌ماند. پشتیبانِ PGlite (`.pglite.tar.gz`) پذیرفته نیست: پوشهٔ خامِ PGlite نسخه‌های کهنهٔ
 * ردیف و WAL را هم دارد، پس بی‌نامیِ آن را نمی‌شود تضمین کرد. فایلِ بی‌نام هم جای خصوصی بماند (نه مخزن، نه چت).
 */
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const { execFile } = require('child_process');

function args(argv) {
  const o = { file: '', out: '', targetUrl: '', keep: false, verify: true, json: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--out') o.out = argv[++i] || '';
    else if (a === '--target') o.targetUrl = argv[++i] || '';
    else if (a === '--keep') o.keep = true;
    else if (a === '--no-verify') o.verify = false;
    else if (a === '--json') o.json = true;
    else if (!a.startsWith('--')) o.file = a;
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  return o;
}

/** پشتیبانِ همان کپی با فرزندی که DATABASE_URL اش کپی است ⇒ نشانیِ فایلِ تازه */
function backupOf(url, out) {
  return new Promise((resolve, reject) => {
    const env = { ...process.env, DATABASE_URL: url, BACKUP_PATH: out };
    execFile(process.execPath, [path.join(__dirname, 'backup.js'), 'manual'], { env, maxBuffer: 1 << 20 }, (err, stdout, stderr) => {
      if (err) return reject(new Error(`پشتیبانِ کپی ساخته نشد: ${String(stderr || err.message).split('\n')[0]}`));
      const dir = path.join(out, 'manual');
      const files = fs.readdirSync(dir).filter((f) => /^shop-.*\.sql\.gz(\.enc)?$/.test(f)).sort();
      if (!files.length) return reject(new Error('پشتیبانِ کپی پیدا نشد'));
      return resolve(path.join(dir, files[files.length - 1]));
    });
  });
}

async function run(o) {
  const config = require('../src/config');
  const drill = require('../src/lib/restore-drill');
  const anon = require('../src/lib/anonymize');
  if (!o.file) throw new Error('فایلِ پشتیبان لازم است: npm run anonymize -- <فایل>');
  if (/\.pglite\.tar\.gz(\.enc)?$/.test(o.file)) throw new Error('پشتیبانِ PGlite پذیرفته نیست (نسخه‌های کهنهٔ ردیف و WAL در پوشهٔ خام می‌مانند)؛ پشتیبانِ PostgreSQL بدهید');
  const out = path.resolve(o.out || path.join(config.backup.dir, 'anonymized'));
  await fsp.mkdir(out, { recursive: true });
  const iso = await drill.restoreIsolated(o.file, { targetUrl: o.targetUrl, keep: true });
  const r = { file: path.basename(o.file), where: iso.where };
  try {
    anon.assertNotLive(iso.url);
    const before = await drill.fingerprint(iso.q);
    const { Client } = require('pg');
    const c = new Client({ connectionString: iso.url });
    await c.connect();
    let a;
    try { a = await anon.anonymize((t, p) => c.query(t, p)); } finally { await c.end(); }
    Object.assign(r, { tables: a.tables, rows: a.rows, changed: a.changed, numKeys: a.numKeys, leaks: a.leaks });
    if (a.leaks.length) { r.ok = false; r.why = 'leak'; return r; }   //  کپی انداخته می‌شود؛ هیچ فایلی بیرون نمی‌آید
    const after = await drill.fingerprint(iso.q);
    const cmp = drill.compare(before, after);
    r.same = cmp.equal || anon.onlyNumKeys(cmp.diffs, a.numKeys);
    r.diffs = cmp.diffs.length;
    if (!r.same) { r.ok = false; r.why = 'fingerprint'; return r; }
    await iso.pool.end();
    r.out = await backupOf(iso.url, out);
    r.encrypted = r.out.endsWith('.enc');
    if (o.verify) {
      const v = await drill.drill({ file: r.out });
      r.verified = v.ok;
      if (!v.ok) { r.ok = false; r.why = 'verify'; return r; }
    }
    r.ok = true;
    return r;
  } finally {
    await iso.pool.end().catch(() => {});
    if (!o.keep) await iso.drop().catch(() => {});
  }
}

async function main() {
  const o = args(process.argv.slice(2));
  const r = await run(o);
  if (o.json) console.log(JSON.stringify(r, null, 2));
  else {
    console.log(`پشتیبان: ${r.file}  ⇒  ${r.where}`);
    if (r.tables !== undefined) console.log(`جدول‌ها ${r.tables}، ردیف‌ها ${r.rows}`);
    if (r.leaks && r.leaks.length) {
      console.log('✖ نشت: این ستون‌ها مقدارِ ماندگاری دارند که جای دیگری نام/تلفن/راز است — هیچ فایلی ساخته نشد:');
      for (const l of r.leaks.slice(0, 40)) console.log(`   ${l.where} (${l.count})`);
    }
    if (r.changed && Object.keys(r.changed).length) console.log(`جای‌گزین: ${Object.entries(r.changed).map(([k, n]) => `${k} ${n}`).join('، ')}`);
    if (r.numKeys && r.numKeys.length) console.log(`عددهای عوض‌شده (فقط این کلیدها): ${r.numKeys.join('، ')}`);
    if (r.same !== undefined) console.log(r.same ? '✔ شمار و جمعِ پول، قرض و مقدار همان پیش است' : `✖ اثرِ انگشت عوض شد (${r.diffs} تفاوت)`);
    if (r.out) console.log(`فایلِ بی‌نام: ${r.out}${r.encrypted ? ' — رمزشده' : ' — بی رمز (BACKUP_PASSPHRASE)'}`);
    if (r.verified !== undefined) console.log(r.verified ? '✔ همان فایل برگشت و با manifest اش یکی است' : '✖ بازگرداندنِ فایلِ بی‌نام نابرابر بود');
    console.log(r.ok ? 'سالم: این فایل را فقط جای خصوصی نگه دارید.' : `✖ ناموفق (${r.why})`);
  }
  process.exitCode = r.ok ? 0 : 1;
}

if (require.main === module) {
  main().catch((e) => { console.error('✖', e.message); process.exitCode = 1; })
    .finally(() => require('../src/db').closeDb().catch(() => {}));
}

module.exports = { run, args };
