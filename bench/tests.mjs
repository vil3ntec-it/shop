/**
 * bench — شمارِ آزمون‌ها در هر مجموعه (F0-26).
 *
 *   node bench/tests.mjs                # شمارشِ ایستا از روی کد (چند ثانیه)
 *   node bench/tests.mjs --run-server   # + اجرای واقعیِ آزمون‌های سرور با PGlite (حدودِ ۸ دقیقه)
 *   node bench/tests.mjs --json out.json
 *
 * شمارشِ ایستا تقریبی است: آزمونی که در حلقه ساخته شود یک بار شمرده
 * می‌شود. عددِ قطعیِ سرور فقط با --run-server می‌آید (خطِ `# tests` ِ
 * خودِ node:test). آزمون‌های Kotlin این‌جا اجرا نمی‌شوند (Gradle و SDK
 * اندروید لازم است) — عددِ اجراشده‌شان در گزارشِ CI ِ tohid-native و
 * tohid-desktop است.
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const ROOT = path.join(import.meta.dirname, '..');

function walk(dir, pick, out = []) {
  if (!fs.existsSync(dir)) return out;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === 'node_modules' || e.name === 'build' || e.name.startsWith('.')) continue;
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, pick, out); else if (pick(p)) out.push(p);
  }
  return out;
}
function count(files, re) {
  let n = 0;
  for (const f of files) n += (fs.readFileSync(f, 'utf8').match(re) || []).length;
  return n;
}

const T = path.join(ROOT, 'server', 'test');
const nodeFiles = walk(T, (p) => p.endsWith('.test.js'));
const browserFiles = walk(T, (p) => p.endsWith('.mjs') && !p.endsWith('browser-guard.mjs'));
const kt = (dir) => walk(path.join(ROOT, dir), (p) => p.endsWith('.kt') && /^\s*@Test\b/m.test(fs.readFileSync(p, 'utf8')));

const suites = [
  { suite: 'سرور (node:test)', files: nodeFiles.length, tests: count(nodeFiles, /^\s*(?:await\s+)?(?:test|it|t\.test)\(/gm) },
  { suite: 'مرورگر: پنل/وب/پورتال (Playwright)', files: browserFiles.length, tests: count(browserFiles, /^\s*(?:await\s+)?step\(/gm) },
  { suite: 'android-native (JVM unit)', files: kt('android-native').length, tests: count(kt('android-native'), /^\s*@Test\b/gm) },
  { suite: 'desktop (خودش؛ + همهٔ بالایی)', files: kt('desktop').length, tests: count(kt('desktop'), /^\s*@Test\b/gm) },
  { suite: 'android (WebView قدیمی)', files: kt('android').length, tests: count(kt('android'), /^\s*@Test\b/gm) },
];

const out = { at: new Date().toISOString(), static: suites };
console.log('شمارشِ ایستا (تعریف‌ها در کد):');
for (const s of suites) console.log(`  ${s.suite.padEnd(40)} ${String(s.tests).padStart(5)} در ${s.files} فایل`);

if (process.argv.includes('--run-server')) {
  const t0 = Date.now();
  const r = spawnSync(process.execPath, ['scripts/test-pglite.js'], { cwd: path.join(ROOT, 'server'), encoding: 'utf8', maxBuffer: 1 << 28 });
  const grab = (k) => Number(((r.stdout || '').match(new RegExp(`^# ${k} (\\d+)`, 'm')) || [])[1]);
  out.serverRun = { tests: grab('tests'), pass: grab('pass'), fail: grab('fail'), skipped: grab('skipped'), seconds: Math.round((Date.now() - t0) / 1000), exit: r.status };
  console.log(`\nاجرای واقعیِ سرور: ${out.serverRun.tests} آزمون، ${out.serverRun.pass} سبز، ${out.serverRun.fail} قرمز، ${out.serverRun.seconds}s (کدِ خروج ${r.status})`);
}

const i = process.argv.indexOf('--json');
if (i > 0 && process.argv[i + 1]) fs.writeFileSync(process.argv[i + 1], JSON.stringify(out, null, 2));
