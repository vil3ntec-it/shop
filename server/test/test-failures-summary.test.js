'use strict';
/**
 * فهرستِ آزمون‌های ناموفق در پایانِ اجرا (`scripts/test-failures.js`) — برای لاگِ
 * کوتاهِ CI (#381: یک «not ok» در میانهٔ ۲۸٬۰۰۰ خط پیدا نمی‌شد).
 *
 *   ۱) خروجیِ اصلی (TAP) همان است و «not ok»ها در آن می‌مانند؛ کدِ خروج همان.
 *   ۲) خلاصه هر شکستِ واقعی را با نام، فایل و خطِ اولِ خطا می‌دهد؛ والدی که فقط
 *      چون زیرآزمونش شکست قرمز است، تکرار نمی‌شود.
 *   ۳) بی شکست ⇒ خلاصه‌ای نیست؛ کدِ ناصفر بی «not ok» ⇒ یک خطِ هشدار.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const failures = require('../scripts/test-failures');

const REPORTER = path.join(__dirname, '..', 'scripts', 'test-failures.js');
const SAMPLE = path.join(__dirname, 'fixtures', 'test-failures-sample.js');

function run(file) {
  const dest = path.join(os.tmpdir(), `tf-${process.pid}-${Math.random().toString(36).slice(2)}.jsonl`);
  //  ⚠️ بی پاک کردنِ NODE_TEST_CONTEXT، `node --test` ِ تودرتو خودش را «فرزند» می‌پندارد
  const env = { ...process.env };
  delete env.NODE_TEST_CONTEXT;
  const r = spawnSync(process.execPath, ['--test', '--test-reporter=tap', '--test-reporter-destination=stdout',
    `--test-reporter=${REPORTER}`, `--test-reporter-destination=${dest}`, file], { encoding: 'utf8', env });
  const summary = failures.summarize(dest, { exitCode: r.status });
  try { fs.unlinkSync(dest); } catch { /* نبود */ }
  return { status: r.status, stdout: r.stdout, summary };
}

test('شکست‌ها با نام، فایل و خطِ اولِ خطا؛ خروجی و کدِ خروج همان', () => {
  const r = run(SAMPLE);
  assert.equal(r.status, 1);
  //  TAP دست نخورده
  assert.match(r.stdout, /not ok \d+ - زیرآزمونِ قرمز/);
  assert.match(r.stdout, /ok \d+ - سبزِ نمونه/);
  //  خلاصه: دو شکستِ واقعی، نه والدِ «گروهِ نمونه»
  assert.match(r.summary, /── آزمون‌های ناموفق \(2\) ──/);
  assert.match(r.summary, /✖ زیرآزمونِ قرمز {2}— {2}test\/fixtures\/test-failures-sample\.js:\d+/);
  assert.match(r.summary, /جمعِ نادرست/);
  assert.match(r.summary, /✖ قرمزِ سطحِ بالا/);
  assert.match(r.summary, /خطای نمونه/);
  assert.ok(!/✖ گروهِ نمونه/.test(r.summary), 'والد تکرار شد');
  assert.ok(!/سبزِ نمونه|زیرآزمونِ سبز/.test(r.summary), 'آزمونِ سبز در فهرستِ ناموفق');
});

test('بی شکست ⇒ خلاصه‌ای نیست؛ کدِ ناصفر بی not ok ⇒ هشدار', () => {
  const ok = path.join(os.tmpdir(), `tf-ok-${process.pid}.js`);
  fs.writeFileSync(ok, "require('node:test')('سبز', () => {});\n");
  try {
    const r = run(ok);
    assert.equal(r.status, 0);
    assert.equal(r.summary, '');
  } finally { fs.unlinkSync(ok); }
  assert.match(failures.summarize('/nonexistent/file.jsonl', { exitCode: 1 }), /هیچ «not ok»ی ثبت نشد/);
  assert.equal(failures.summarize('/nonexistent/file.jsonl', { exitCode: 0 }), '');
});

test('scripts/test-run.js (npm test): همان پرچم و فایل، TAP و کدِ خروج همان، خلاصه در پایان', () => {
  const env = { ...process.env };
  delete env.NODE_TEST_CONTEXT;
  const r = spawnSync(process.execPath, [path.join(__dirname, '..', 'scripts', 'test-run.js'), '--test-concurrency=1', SAMPLE],
    { encoding: 'utf8', env, cwd: path.join(__dirname, '..') });
  assert.equal(r.status, 1);
  assert.match(r.stdout, /not ok \d+ - زیرآزمونِ قرمز/);
  const tail = r.stdout.slice(r.stdout.lastIndexOf('── آزمون‌های ناموفق'));
  assert.match(tail, /^── آزمون‌های ناموفق \(2\) ──/);
  assert.match(tail, /✖ زیرآزمونِ قرمز {2}— {2}test\/fixtures\/test-failures-sample\.js:\d+/);
  assert.match(tail, /✖ قرمزِ سطحِ بالا/);
  //  سبز ⇒ خروجِ ۰ و بی خلاصه
  const ok = path.join(os.tmpdir(), `tf-run-ok-${process.pid}.js`);
  fs.writeFileSync(ok, "require('node:test')('سبز', () => {});\n");
  try {
    const g = spawnSync(process.execPath, [path.join(__dirname, '..', 'scripts', 'test-run.js'), ok], { encoding: 'utf8', env });
    assert.equal(g.status, 0);
    assert.ok(!g.stdout.includes('آزمون‌های ناموفق'));
  } finally { fs.unlinkSync(ok); }
});
