'use strict';
/**
 * `scripts/test-shard.js` — تکه‌های کارِ CI هر پرونده را دقیقاً یک بار می‌دوانند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('node:child_process');
const { shardOf } = require('../scripts/test-shard');

const real = fs.readdirSync(__dirname).filter((f) => f.endsWith('.test.js')).sort();

test('بی TEST_SHARD ⇒ همهٔ پرونده‌ها، به همان ترتیب', () => {
  for (const spec of [undefined, null, '']) assert.deepEqual(shardOf(real, spec), real);
});

test('هر n: اجتماعِ تکه‌ها همهٔ پرونده‌هاست، بی هم‌پوشانی، و ترتیبِ هر تکه همان ترتیبِ اصلی', () => {
  for (const n of [1, 2, 3, 4]) {
    const parts = Array.from({ length: n }, (_, i) => shardOf(real, `${i + 1}/${n}`));
    const all = parts.flat();
    assert.equal(all.length, real.length, `n=${n}`);
    assert.deepEqual([...all].sort(), real, `n=${n}`);
    assert.equal(new Set(all).size, all.length, `n=${n}: پرونده‌ای دو بار`);
    for (const p of parts) assert.deepEqual(p, real.filter((f) => p.includes(f)), `n=${n}: ترتیب`);
    //  تکه‌ها هم‌اندازه‌اند (حداکثر یک پرونده فرق)
    const sizes = parts.map((p) => p.length);
    assert.ok(Math.max(...sizes) - Math.min(...sizes) <= 1, `n=${n}: ${sizes}`);
  }
});

test('مقدارِ ناجور ⇒ خطا، نه اجرای بی‌صدای هیچ یا همه', () => {
  for (const bad of ['0/2', '3/2', '1', '1/0', 'a/b', '1/2/3', ' 1/2', '-1/2', '1/2 ', '1000/1000']) {
    assert.throws(() => shardOf(real, bad), /TEST_SHARD نادرست/, bad);
  }
});

test('تکه‌بندی PostgreSQL فقط در اجرای بیرونی انتخاب می‌کند و اجرای درونی فهرست کامل خود را دارد', () => {
  const env = { ...process.env, TEST_SHARD: '2/2', TEST_POSTGRES_SHARD: '2/2' };
  delete env.NODE_TEST_CONTEXT;
  delete env.TEST_EVIDENCE_FILE;
  const result = spawnSync(process.execPath, [
    'scripts/test-run.js', '--test-concurrency=1',
    'test/fixtures/test-failures-sample.js', 'test/test-evidence.test.js',
  ], { cwd: path.join(__dirname, '..'), env, encoding: 'utf8', timeout: 30_000 });
  assert.equal(result.status, 0, result.stderr + result.stdout);
  assert.match(result.stdout, /^# pass 2$/m);
});

test('هر سه کارِ پایگاه داده در CI از همان تکه‌بندی استفاده می‌کنند', () => {
  const runner = fs.readFileSync(path.join(__dirname, '..', 'scripts', 'test-pglite.js'), 'utf8');
  assert.match(runner, /shardOf\(\s*all\s*,\s*process\.env\.TEST_SHARD\s*\)/);
  const yml = fs.readFileSync(path.join(__dirname, '..', '..', '.github', 'workflows', 'server.yml'), 'utf8');
  for (const job of ['pg', 'pglite', 'electron']) {
    const block = yml.split(new RegExp(`\\n  ${job}:\\n`))[1].split(/\n  [a-z0-9_-]+:\n/)[0];
    assert.match(block, /shard: \[1, 2\]/, `${job}: ماتریسِ دو تکه`);
    const shardVariable = job === 'pg' ? 'TEST_POSTGRES_SHARD' : 'TEST_SHARD';
    assert.ok(block.includes(shardVariable + ': ${{ matrix.shard }}/2'), job + ': ' + shardVariable);
    assert.match(block, /fail-fast: false/, `${job}: یک تکهٔ سرخ تکهٔ دیگر را لغو نکند`);
  }
  assert.match(yml, /name: test-evidence-postgres-\$\{\{ matrix\.shard \}\}/);
});
