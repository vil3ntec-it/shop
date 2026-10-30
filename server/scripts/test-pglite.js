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
 */
const { spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const failures = require('./test-failures');

const dir = path.join(__dirname, '..', 'test');
const skip = new Set(['backup.test.js', 'pump-isolation.test.js']);
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.test.js') && !skip.has(f)).sort()
  .map((f) => path.join('test', f));

const failed = path.join(os.tmpdir(), `test-failures-${process.pid}-${Date.now()}.jsonl`);
//  `npm test` دو بار این را صدا می‌زند ⇒ هر اجرا به پروندهٔ شواهد افزوده می‌شود، نه رونویسی
const evidence = process.env.TEST_EVIDENCE_FILE ? path.join(os.tmpdir(), `test-evidence-${process.pid}-${Date.now()}.jsonl`) : '';
const r = spawnSync(process.execPath, [
  '--test', '--test-concurrency=1',
  `--test-reporter=${process.stdout.isTTY ? 'spec' : 'tap'}`, '--test-reporter-destination=stdout',
  `--test-reporter=${path.join(__dirname, 'test-failures.js')}`, `--test-reporter-destination=${failed}`,
  //  GATE-NEW-05: پروندهٔ شواهدِ هر آزمون (`test-evidence.js`) فقط اگر CI مقصد داده
  ...(evidence ? [`--test-reporter=${path.join(__dirname, 'test-evidence.js')}`, `--test-reporter-destination=${evidence}`] : []),
  ...files,
], {
  stdio: 'inherit',
  env: { ...process.env, TEST_DATABASE_URL: 'pglite:memory' },
});
process.stdout.write(failures.summarize(failed, { exitCode: r.status ?? 1 }));
try { fs.unlinkSync(failed); } catch { /* نبود */ }
if (evidence) {
  try {
    fs.mkdirSync(path.dirname(path.resolve(process.env.TEST_EVIDENCE_FILE)), { recursive: true });
    fs.appendFileSync(process.env.TEST_EVIDENCE_FILE, fs.readFileSync(evidence));
    fs.unlinkSync(evidence);
  } catch { /* بی شواهد؛ نتیجهٔ آزمون همان */ }
}
process.exit(r.status ?? 1);
