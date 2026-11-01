'use strict';
/**
 * GATE-NEW-05 — پروندهٔ شواهدِ هر آزمون (`scripts/test-evidence.js`).
 *
 *   ۱) خطِ «run»: commit، Node، سیستم‌عامل، پایگاه — بی نام و رمزِ پایگاه و بی رازِ محیط
 *   ۲) هر آزمون (و زیرآزمون) یک خط: شناسهٔ پایدار، نتیجه، زمان، دستورِ بازتولید؛ شکست با
 *      پیامِ خطا؛ والدی که فقط چون زیرآزمونش شکست قرمز است تکرار نمی‌شود
 *   ۳) `scripts/test-run.js` با `TEST_EVIDENCE_FILE` به همان پرونده **می‌افزاید** (دو اجرای
 *      `npm test`)، و بی آن هیچ پرونده‌ای نمی‌سازد؛ TAP و کدِ خروج همان
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const evidence = require('../scripts/test-evidence');

const SERVER = path.join(__dirname, '..');
const SAMPLE = path.join(__dirname, 'fixtures', 'test-failures-sample.js');
const RUN = path.join(SERVER, 'scripts', 'test-run.js');

function cleanEnv(extra = {}) {
  const env = { ...process.env, ...extra };
  delete env.NODE_TEST_CONTEXT;   // وگرنه `node --test` ِ تودرتو خودش را فرزند می‌پندارد
  return env;
}
const read = (f) => fs.readFileSync(f, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));

test('هر آزمون یک خط با شناسه، نتیجه، زمان و دستورِ بازتولید؛ خطِ run بی راز', () => {
  const file = path.join(os.tmpdir(), `ev-${process.pid}-${Date.now()}.jsonl`);
  const r = spawnSync(process.execPath, [RUN, '--test-concurrency=1', SAMPLE], {
    encoding: 'utf8', cwd: SERVER,
    env: cleanEnv({ TEST_EVIDENCE_FILE: file, TEST_DATABASE_URL: 'postgres://shop:Sup3rSecretPw@127.0.0.1:5432/shop_test?sslmode=disable' }),
  });
  try {
    assert.equal(r.status, 1, 'کدِ خروج همان');
    assert.match(r.stdout, /not ok \d+ - زیرآزمونِ قرمز/, 'TAP دست نخورده');
    const rows = read(file);
    const [run, ...tests] = rows;
    assert.equal(run.kind, 'run');
    assert.match(run.commit, /^[0-9a-f]{40}$/);
    assert.equal(run.node, process.version);
    assert.ok(run.os);
    assert.equal(run.db, 'postgres://127.0.0.1:5432/shop_test');
    const text = fs.readFileSync(file, 'utf8');
    for (const s of ['Sup3rSecretPw', 'shop:', process.env.API_SECRET, process.env.OTP_SECRET].filter(Boolean)) {
      assert.ok(!text.includes(s), `راز در پرونده: ${s}`);
    }

    const by = Object.fromEntries(tests.map((t) => [t.name, t]));
    assert.deepEqual(Object.keys(by).sort(), ['زیرآزمونِ قرمز', 'زیرآزمونِ سبز', 'سبزِ نمونه', 'قرمزِ سطحِ بالا']
      .map((n) => (n.startsWith('زیرآزمونِ') ? `گروهِ نمونه > ${n}` : n)).sort(), 'والدِ «زیرآزمونش شکست» تکرار نشد');
    const red = by['گروهِ نمونه > زیرآزمونِ قرمز'];
    assert.equal(red.result, 'fail');
    assert.equal(red.id, 'test/fixtures/test-failures-sample.js#گروهِ نمونه > زیرآزمونِ قرمز');
    assert.match(red.output, /جمعِ نادرست/);
    assert.ok(Number.isInteger(red.durationMs));
    assert.match(red.repro, /^cd server && TEST_DATABASE_URL="\$TEST_DATABASE_URL" node --test --test-concurrency=1 --test-name-pattern="\^زیرآزمونِ قرمز\$" test\/fixtures\/test-failures-sample\.js$/);
    assert.equal(by['سبزِ نمونه'].result, 'pass');
    assert.equal(by['سبزِ نمونه'].output, undefined, 'آزمونِ سبز خروجیِ خطا ندارد');
    assert.match(by['قرمزِ سطحِ بالا'].output, /خطای نمونه/);

    //  دستورِ بازتولید واقعاً همان یک آزمون را می‌زند
    const one = spawnSync('sh', ['-c', by['سبزِ نمونه'].repro.replace(/^cd server && /, '')], { encoding: 'utf8', cwd: SERVER, env: cleanEnv() });
    assert.equal(one.status, 0, one.stdout + one.stderr);
    assert.match(one.stdout, /# tests 1\b/);

    //  اجرای دوم (مثلِ `npm test`) به همان پرونده افزوده می‌شود
    const ok = path.join(os.tmpdir(), `ev-ok-${process.pid}.js`);
    fs.writeFileSync(ok, "require('node:test')('سبزِ دوم', () => {});\n");
    try {
      const g = spawnSync(process.execPath, [RUN, ok], { encoding: 'utf8', cwd: SERVER, env: cleanEnv({ TEST_EVIDENCE_FILE: file }) });
      assert.equal(g.status, 0);
    } finally { fs.unlinkSync(ok); }
    const all = read(file);
    assert.equal(all.filter((x) => x.kind === 'run').length, 2);
    assert.ok(all.some((x) => x.name === 'سبزِ دوم' && x.result === 'pass'));
    assert.ok(all.some((x) => x.name === 'سبزِ نمونه'), 'اجرای نخست رونویسی نشد');
  } finally { try { fs.unlinkSync(file); } catch { /* نبود */ } }
});

test('بی TEST_EVIDENCE_FILE هیچ پرونده‌ای ساخته نمی‌شود؛ pglite بی تغییر در دستورِ بازتولید', () => {
  const before = fs.readdirSync(os.tmpdir()).filter((f) => f.startsWith('test-evidence-')).length;
  const env = cleanEnv();
  delete env.TEST_EVIDENCE_FILE;
  const ok = path.join(os.tmpdir(), `ev-none-${process.pid}.js`);
  fs.writeFileSync(ok, "require('node:test')('سبز', () => {});\n");
  try {
    const g = spawnSync(process.execPath, [RUN, ok], { encoding: 'utf8', cwd: SERVER, env });
    assert.equal(g.status, 0);
  } finally { fs.unlinkSync(ok); }
  assert.equal(fs.readdirSync(os.tmpdir()).filter((f) => f.startsWith('test-evidence-')).length, before);
  assert.equal(evidence.dbLabel('pglite:memory'), 'pglite:memory');
  assert.equal(evidence.dbLabel('postgres://u:p@h:1/d'), 'postgres://h:1/d');
  assert.equal(evidence.dbLabel('nonsense'), 'unparsed');
});
