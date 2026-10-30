#!/usr/bin/env node
/**
 * Quality gate — report only, except duplicate migration numbers.
 * Never edits or reformats source files. Guide: docs/QUALITY-fa.md.
 *
 *   node tools/quality/quality.mjs                      ratchet: file size, re-declared helpers,
 *                                                       'pump'/'shop' literals, frozen index.html,
 *                                                       platform deps in Kotlin logic (iPhone)
 *   node tools/quality/quality.mjs --check migrations   duplicate migration numbers (blocking in CI)
 *   node tools/quality/quality.mjs --check eslint [--all]
 *   node tools/quality/quality.mjs --check coverage
 *   node tools/quality/quality.mjs --lower-baseline     lower baseline caps to today's values (never raises)
 *   node tools/quality/quality.mjs --snapshot-baseline  rewrite caps from today (only with a reason in the PR)
 *
 * Exit code 1 when a check finds something this change caused; CI shows the
 * step as failed but (except migrations) keeps the job green.
 */
import fs from 'node:fs';
import path from 'node:path';
import { ROOT, trackedFiles, changedFiles, read, readAtBase } from './lib/repo.mjs';
import { publish, annotate } from './lib/report.mjs';
import * as fileSize from './checks/file-size.mjs';
import * as duplicateHelpers from './checks/duplicate-helpers.mjs';
import * as productLiterals from './checks/product-literals.mjs';
import * as frozenWeb from './checks/frozen-web.mjs';
import * as migrations from './checks/migrations.mjs';
import * as eslint from './checks/eslint.mjs';
import * as coverage from './checks/coverage.mjs';
import * as platformDeps from './checks/platform-deps.mjs';

const GROUPS = {
  ratchet: [fileSize, duplicateHelpers, productLiterals, frozenWeb, platformDeps],
  migrations: [migrations],
  eslint: [eslint],
  coverage: [coverage],
};
const BASELINE = path.join(ROOT, 'tools', 'quality', 'baseline.json');
const BASELINE_DOC = 'Caps for tools/quality/quality.mjs. They may only go down: run --lower-baseline after an improvement. '
  + 'Raising a cap by hand needs a reason in the PR body. Guide: docs/QUALITY-fa.md';

function parseArgs(argv) {
  const options = { check: 'ratchet', all: false, lower: false, snapshot: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--check') options.check = argv[++i];
    else if (a === '--all') options.all = true;
    else if (a === '--lower-baseline') options.lower = true;
    else if (a === '--snapshot-baseline') options.snapshot = true;
    else throw new Error(`آرگومانِ ناشناخته: ${a}`);
  }
  if (!GROUPS[options.check]) throw new Error(`سنجهٔ ناشناخته: ${options.check} (${Object.keys(GROUPS).join(', ')})`);
  return options;
}

function context(options) {
  const cache = new Map();
  const cached = (f) => {
    if (!cache.has(f)) cache.set(f, read(f));
    return cache.get(f);
  };
  let baseline = null;
  return {
    options,
    /** read on first use only: the blocking migrations check never depends on baseline.json */
    get baseline() {
      baseline ??= fs.existsSync(BASELINE) ? JSON.parse(fs.readFileSync(BASELINE, 'utf8')) : {};
      return baseline;
    },
    files: trackedFiles(),
    changed: changedFiles(),
    read: cached,
    /** fn(text at base) for a base path, or null when the file did not exist there */
    atBase(base, fn) {
      const text = base ? readAtBase(base) : null;
      return text == null ? null : fn(text);
    },
  };
}

function writeBaseline(ctx, snapshot) {
  for (const check of GROUPS.ratchet) check.update(ctx.baseline, ctx, { snapshot });
  const { fileSize: size, frozen, productLiterals: literals, duplicateHelpers: helpers, platformDeps: platform } = ctx.baseline;
  const text = `${JSON.stringify({ _doc: BASELINE_DOC, fileSize: size, frozen, productLiterals: literals, duplicateHelpers: helpers,
    platformDeps: platform }, null, 2)}\n`;
  const old = fs.existsSync(BASELINE) ? fs.readFileSync(BASELINE, 'utf8') : '';
  if (text === old) return console.log('خط‌مبنا عوض نشد');
  fs.writeFileSync(BASELINE, text);
  console.log(`خط‌مبنا نوشته شد: ${path.relative(ROOT, BASELINE)}`);
}

function main() {
  const options = parseArgs(process.argv.slice(2));
  const ctx = context(options);
  if (options.lower || options.snapshot) return writeBaseline(ctx, options.snapshot);

  const parts = [
    `## سنجهٔ کیفیت · ${options.check}`,
    '',
    ctx.changed
      ? `مقایسه با کامیتِ پدر (${ctx.changed.size} فایل عوض شده). علت: **همین تغییر** = همین تغییر بدترش کرد؛ **از پیش** = پیش‌تر هم همین بود. راهنما: docs/QUALITY-fa.md`
      : 'کامیتِ پدری برای مقایسه نیست: علت‌ها نامعلوم‌اند.',
    '',
  ];
  let caused = 0;
  for (const check of GROUPS[options.check]) {
    const res = check.run(ctx);
    parts.push(res.markdown);
    //  a blocking check fails only on what this change surely caused; report-only checks also on «unknown»
    const counts = check.blocking ? (v) => v.cause === 'introduced' : (v) => v.cause !== 'pre-existing';
    for (const v of res.violations) {
      if (!counts(v)) continue;
      caused++;
      if (v.file) annotate(v.file, v.message, { line: v.line, level: check.blocking ? 'error' : 'warning' });
    }
  }
  publish(parts.join('\n'));
  process.exitCode = caused ? 1 : 0;
}

main();
