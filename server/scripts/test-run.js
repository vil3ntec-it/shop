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
 * env و کدِ خروج همان.
 */
const { spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const failures = require('./test-failures');

const args = process.argv.slice(2);
const flags = args.filter((a) => a.startsWith('--'));
const files = args.filter((a) => !a.startsWith('--'));

const failed = path.join(os.tmpdir(), `test-failures-${process.pid}-${Date.now()}.jsonl`);
const r = spawnSync(process.execPath, [
  '--test', ...flags,
  `--test-reporter=${process.stdout.isTTY ? 'spec' : 'tap'}`, '--test-reporter-destination=stdout',
  `--test-reporter=${path.join(__dirname, 'test-failures.js')}`, `--test-reporter-destination=${failed}`,
  ...files,
], { stdio: 'inherit' });
process.stdout.write(failures.summarize(failed, { exitCode: r.status ?? 1 }));
try { fs.unlinkSync(failed); } catch { /* نبود */ }
process.exit(r.status ?? 1);
