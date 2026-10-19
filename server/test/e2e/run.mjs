/**
 * اجراکنندهٔ سناریوهای سرتاسری — `npm run test:e2e`.
 *
 *   • هر `*.e2e.mjs` یک فرایندِ جدا با دیتابیسِ PGlite ِ خودش ⇒ موازی بی اثر روی هم
 *     (`E2E_CONCURRENCY`، پیش‌فرض ۳). با `E2E_DATABASE_URL` (Postgres ِ مشترک) ترتیبی.
 *   • خروجیِ هر سناریو در `<نتایج>/<نام>.log`؛ روی صفحه فقط جدولِ گام‌ها و تهِ لاگِ قرمزها.
 *   • هر سناریو سقفِ زمان دارد (`E2E_TIMEOUT_MS`، پیش‌فرض ۶ دقیقه).
 *   • قرمز = هر گامِ `fail`، یا سناریویی که بی نتیجه افتاد. «شکافِ شناخته» (`lib/known-gaps.mjs`)
 *     قرمز نیست ولی در جدول پیداست؛ «xpass» یعنی آن ردیف باید پاک شود.
 *
 *   node test/e2e/run.mjs              همه
 *   node test/e2e/run.mjs sync order   فقط همین‌ها
 *
 * پوشه‌ها: `E2E_RESULTS_DIR` (پیش‌فرض: پوشهٔ موقت) و `E2E_SHOTS_DIR` (عکس‌ها).
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const HERE = import.meta.dirname;
const want = process.argv.slice(2);
const files = fs.readdirSync(HERE).filter((f) => f.endsWith('.e2e.mjs')).sort()
  .filter((f) => !want.length || want.includes(f.replace(/\.e2e\.mjs$/, '')));
if (!files.length) { console.error(`سناریویی پیدا نشد: ${want.join(' ')}`); process.exit(1); }

const results = process.env.E2E_RESULTS_DIR || fs.mkdtempSync(path.join(os.tmpdir(), 'e2e-results-'));
const shots = process.env.E2E_SHOTS_DIR || path.join(results, 'shots');
fs.mkdirSync(results, { recursive: true });
fs.mkdirSync(shots, { recursive: true });
const sharedDb = !!process.env.E2E_DATABASE_URL;
const N = sharedDb ? 1 : Math.max(1, Number(process.env.E2E_CONCURRENCY) || Math.min(3, os.cpus().length));
const TIMEOUT = Number(process.env.E2E_TIMEOUT_MS) || 6 * 60_000;

function runOne(file) {
  const name = file.replace(/\.e2e\.mjs$/, '');
  const log = path.join(results, `${name}.log`);
  const out = fs.createWriteStream(log);
  const t0 = Date.now();
  return new Promise((resolve) => {
    const child = spawn(process.execPath, [path.join(HERE, file)], {
      cwd: path.join(HERE, '..', '..'),
      env: { ...process.env, E2E_RESULTS_DIR: results, E2E_SHOTS_DIR: shots },
      stdio: ['ignore', 'pipe', 'pipe'],
    });
    child.stdout.pipe(out);
    child.stderr.pipe(out);
    const timer = setTimeout(() => { out.write(`\n⏱ بیش از ${TIMEOUT / 1000} ثانیه — متوقف شد\n`); child.kill('SIGKILL'); }, TIMEOUT);
    child.on('exit', (code, signal) => {
      clearTimeout(timer);
      out.end();
      const ms = Date.now() - t0;
      let steps = [];
      try { steps = JSON.parse(fs.readFileSync(path.join(results, `${name}.json`), 'utf8')).results; } catch { /* افتاد پیش از نوشتن */ }
      console.log(`${code === 0 ? '✅' : '❌'} ${name} — ${(ms / 1000).toFixed(1)}s${signal ? ` (${signal})` : ''}`);
      resolve({ name, code, signal, ms, steps, log });
    });
  });
}

const t0 = Date.now();
console.log(`سناریوهای سرتاسری: ${files.length} تا، ${N} هم‌زمان${sharedDb ? ' (دیتابیسِ مشترک ⇒ ترتیبی)' : ' (هر کدام PGlite ِ خودش)'}\n`);
const queue = files.slice();
const done = [];
await Promise.all(Array.from({ length: Math.min(N, queue.length) }, async () => {
  while (queue.length) done.push(await runOne(queue.shift()));
}));
done.sort((a, b) => a.name.localeCompare(b.name));

/* ---------------- جدول ---------------- */
const ICON = { pass: '✅', fail: '❌', 'known-gap': '🟧', xpass: '⚠️', skip: '⏭' };
const tally = { pass: 0, fail: 0, 'known-gap': 0, xpass: 0, skip: 0 };
const lines = [];
lines.push('سناریو | گام | نتیجه | زمان | توضیح');
lines.push('--- | --- | --- | --- | ---');
for (const r of done) {
  if (!r.steps.length) {
    lines.push(`${r.name} | — | ❌ بی نتیجه (کد ${r.code}${r.signal ? `، ${r.signal}` : ''}) | ${(r.ms / 1000).toFixed(1)}s | لاگ: ${r.log}`);
    tally.fail++;
    continue;
  }
  for (const s of r.steps) {
    tally[s.status] = (tally[s.status] || 0) + 1;
    const why = s.status === 'pass' ? '' : (s.ref || s.error || '').replace(/\s+/g, ' ').replace(/\|/g, '¦').slice(0, 220);
    lines.push(`${r.name} | ${s.id} | ${ICON[s.status] || s.status} ${s.status} | ${(s.ms / 1000).toFixed(1)}s | ${why}`);
  }
}
const table = lines.join('\n');
console.log(`\n${table}\n`);
const crashed = done.filter((r) => r.code !== 0);
for (const r of crashed) {
  const tail = fs.readFileSync(r.log, 'utf8').split('\n').filter((l) => !/^(GET|POST|PUT|PATCH|DELETE) \//.test(l)).slice(-25).join('\n');
  console.log(`──── تهِ لاگِ ${r.name} (${r.log}) ────\n${tail}\n`);
}
const summary = `گام‌ها: ${tally.pass} سبز · ${tally.fail} قرمز · ${tally['known-gap']} شکافِ شناخته · ${tally.xpass} xpass · ${tally.skip} اجرا‌نشده — ${(((Date.now() - t0) / 1000)).toFixed(0)} ثانیه`;
console.log(summary);
fs.writeFileSync(path.join(results, 'SUMMARY.txt'), `${table}\n\n${summary}\n`);
fs.writeFileSync(path.join(results, 'summary.json'), JSON.stringify({ tally, scenarios: done.map(({ name, code, ms, steps }) => ({ name, code, ms, steps })) }, null, 2));
console.log(`نتیجه‌ها: ${results}\nعکس‌ها: ${shots}`);
process.exit(crashed.length || tally.fail ? 1 : 0);
