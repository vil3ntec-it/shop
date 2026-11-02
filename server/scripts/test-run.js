#!/usr/bin/env node
'use strict';
/**
 * `node --test` با همان پرچم‌ها و فایل‌ها، به‌اضافهٔ فهرستِ «not ok»ها در پایان —
 * برای `npm test` (کارِ «آزمون‌های سرور روی PostgreSQL» در CI). همان کارِ
 * `test-pglite.js`، بی عوض کردنِ پایگاهِ داده.
 *
 *   node scripts/test-run.js [--پرچمِ node --test …] فایل‌ها…
 *
 * آرگومانِ «--…» پرچمِ `node --test` است و بقیه فایل (همان که شِل از glob ساخت،
 * به همان ترتیب). گزارشگرِ اصلی همان پیش‌فرضِ Node است (TTY ⇒ spec، وگرنه tap)؛
 * env و کدِ خروج همان. پرونده‌ای که بی‌صدا بماند را نگهبان (`test-watchdog.js`) نام می‌برد و می‌کشد.
 */
const fs = require('fs');
const os = require('os');
const path = require('path');
const failures = require('./test-failures');
const watchdog = require('./test-watchdog');

const args = process.argv.slice(2);
const flags = args.filter((a) => a.startsWith('--'));
const files = args.filter((a) => !a.startsWith('--'));

const failed = path.join(os.tmpdir(), `test-failures-${process.pid}-${Date.now()}.jsonl`);
//  `npm test` دو بار این را صدا می‌زند ⇒ هر اجرا به پروندهٔ شواهد افزوده می‌شود، نه رونویسی
const evidence = process.env.TEST_EVIDENCE_FILE ? path.join(os.tmpdir(), `test-evidence-${process.pid}-${Date.now()}.jsonl`) : '';
(async () => {
  const r = await watchdog.run([
    '--test', ...flags,
    `--test-reporter=${process.stdout.isTTY ? 'spec' : 'tap'}`, '--test-reporter-destination=stdout',
    `--test-reporter=${path.join(__dirname, 'test-failures.js')}`, `--test-reporter-destination=${failed}`,
    //  GATE-NEW-05: پروندهٔ شواهدِ هر آزمون (`test-evidence.js`) فقط اگر CI مقصد داده
    ...(evidence ? [`--test-reporter=${path.join(__dirname, 'test-evidence.js')}`, `--test-reporter-destination=${evidence}`] : []),
    ...files,
  ]);
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
