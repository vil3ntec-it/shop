#!/usr/bin/env node
'use strict';
/**
 * همان آزمون‌ها، روی راه‌اندازِ درون‌فرآیندی (PGlite) — بی هیچ PostgreSQLی.
 *
 *   npm run test:pglite
 *
 * دو آزمون که خودِ pg_dump/psql را صدا می‌زنند (backup و pump-isolation)
 * این‌جا معنایی ندارند و رد می‌شوند؛ پشتیبانِ PGlite جداگانه سنجیده می‌شود
 * (pglite-driver.test.js).
 *
 * در پایان، اگر آزمونی ناموفق بود، فهرستش (نام، فایل، چند خطِ اولِ خطا) دوباره
 * چاپ می‌شود تا در ۵۰۰۰ خطِ آخرِ لاگِ CI دیده شود (`scripts/test-failures.js`).
 * گزارشگرِ اصلی همان پیش‌فرضِ Node است (TTY ⇒ spec، وگرنه tap).
 *
 * ⛔ `--test-force-exit`: هر پرونده در فرآیندِ خودش اجرا می‌شود و Node تا آن فرآیند خودش
 * بیرون نرود منتظر می‌ماند — سقفی برای هر پرونده ندارد. کارِ «آزمون‌های سرور زیرِ Electron»
 * گاه درست پس از آخرین آزمونِ یک پرونده (هر بار پرونده‌ای دیگر: reauth، staff-code،
 * admin-ops-image-bank) تا ۴۵ دقیقه ایستاد و لغو شد. یک دستگیرهٔ ref‌دار که پس از آخرین آزمون
 * بماند همین را می‌کند: فرآیندِ پرونده بیرون نمی‌رود، رویدادِ beforeExit نمی‌رسد و گزارشِ
 * پرونده هرگز بسته نمی‌شود. یکی از این‌ها پیدا شد و کوتاه است: کارهای آغازینِ `createApp`
 * (`setImmediate` ِ trial-sweep و station-access) در پرونده‌ای که آزمون‌هایش هیچ I/O ندارند
 * پس از `closeDb` ِ `h.stop()` می‌رسند، PGlite را دوباره می‌سازند و تایمرِ ۱۰ ثانیه‌ایِ
 * `setitimer` ِ آن (ref‌دار) فرآیند را نگه می‌دارد (`migrate-eol` زیرِ Electron و Node، ۱۴۰۵/۰۸/۰۹).
 * دستگیرهٔ همیشگی بازتولید نشد (۸۲ اجرای پرونده زیرِ Electron 38 با پیش‌بارِ ردیابِ دستگیره و تایمر:
 * ۷۰ پرونده، و هر یک از سه پروندهٔ نام‌برده چهار بار).
 * پس پرچم تورِ ایمنی است: پس از پایانِ همهٔ آزمون‌ها و قلاب‌های یک پرونده، فرآیندش بیرون می‌رود،
 * هر دستگیره‌ای که مانده باشد. هیچ آزمونی رد نمی‌شود و شکستِ هر آزمون همان کدِ خروجِ ناموفق است.
 * نگهبانِ «استخر/دیتابیس فرآیند را نگه نمی‌دارد» خودش جداست (`db-exit-idle.test.js`، فرآیندِ خودش).
 *
 * ⏱ با این همه کارِ Electron باز «پس از آخرین آزمونِ یک پرونده» تا ۴۵ دقیقه بی‌صدا ماند (main 171c7d65
 * پس از sync-epoch؛ دستهٔ نهم پس از api.test.js، ۱۴۰۵/۰۸/۱۱). گیری که بازتولید شد (gdb): ساختنِ PGlite
 * زیرِ Electron گاه برای همیشه می‌ایستد، رشتهٔ اصلی در `NodePlatform::DrainTasks`، پیش از هر خروجی —
 * همان نشانهٔ CI اگر پروندهٔ **بعدی** در `h.start()` بایستد (`src/lib/pglite-create.js`، که حالا راهِ
 * ساختن است). و دو تورِ ایمنی:
 *   • نگهبان (`scripts/test-watchdog.js`): پرونده‌ای که `TEST_WATCHDOG_SECONDS` (۱۸۰) ثانیه هیچ
 *     پیشرفتی نداشته باشد با نام، مرحله، آخرین آزمون، wchan و دستگیره‌های بازش گزارش و کشته می‌شود؛
 *     اجرا ادامه می‌یابد و ناموفق تمام می‌شود.
 *   • `--test-force-exit` گزارشِ آزمون‌های پایانیِ هر پرونده را با process.exit دور می‌ریخت (زیرِ بار
 *     ده‌ها آزمون، بی هیچ خطایی)؛ stdout ِ فرآیندِ پرونده حالا هم‌زمان است (`scripts/test-stdout-sync.js`).
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const failures = require('./test-failures');
const watchdog = require('./test-watchdog');

const dir = path.join(__dirname, '..', 'test');
const skip = new Set(['backup.test.js', 'pump-isolation.test.js']);
const all = fs.readdirSync(dir).filter((f) => f.endsWith('.test.js') && !skip.has(f)).sort()
  .map((f) => path.join('test', f));
//  ⏱ TEST_SHARD=k/n (کارهای CI ِ PGlite و Electron، دو تکهٔ هم‌زمان؛ `scripts/test-shard.js`) — نبود ⇒ همه
const files = require('./test-shard').shardOf(all, process.env.TEST_SHARD);

const failed = path.join(os.tmpdir(), `test-failures-${process.pid}-${Date.now()}.jsonl`);
//  `npm test` دو بار این را صدا می‌زند ⇒ هر اجرا به پروندهٔ شواهد افزوده می‌شود، نه رونویسی
const evidence = process.env.TEST_EVIDENCE_FILE ? path.join(os.tmpdir(), `test-evidence-${process.pid}-${Date.now()}.jsonl`) : '';
(async () => {
  const r = await watchdog.run([
    '--test', '--test-concurrency=1', '--test-force-exit',
    `--test-reporter=${process.stdout.isTTY ? 'spec' : 'tap'}`, '--test-reporter-destination=stdout',
    `--test-reporter=${path.join(__dirname, 'test-failures.js')}`, `--test-reporter-destination=${failed}`,
    //  GATE-NEW-05: پروندهٔ شواهدِ هر آزمون (`test-evidence.js`) فقط اگر CI مقصد داده
    ...(evidence ? [`--test-reporter=${path.join(__dirname, 'test-evidence.js')}`, `--test-reporter-destination=${evidence}`] : []),
    ...files,
  ], { env: { ...process.env, TEST_DATABASE_URL: 'pglite:memory' } });
  process.stdout.write(r.stats);
  process.stdout.write(failures.summarize(failed, { exitCode: r.status }));
  process.stdout.write(watchdog.summarize(r.hangs));
  try { fs.unlinkSync(failed); } catch { /* نبود */ }
  if (evidence) {
    try {
      fs.mkdirSync(path.dirname(path.resolve(process.env.TEST_EVIDENCE_FILE)), { recursive: true });
      fs.appendFileSync(process.env.TEST_EVIDENCE_FILE, fs.readFileSync(evidence));
      fs.unlinkSync(evidence);
    } catch { /* بی شواهد؛ نتیجهٔ آزمون همان */ }
  }
  process.exit(r.status);
})();
