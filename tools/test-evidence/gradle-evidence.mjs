#!/usr/bin/env node
/**
 * شواهدِ هر آزمونِ Gradle — GATE-NEW-05 (§۳۵): «برای هر تست شناسه، build/commit، محیط، دستگاه/نسخه، دادهٔ آزمونِ
 * غیرحساس، گام‌های بازتولید، خروجیِ واقعی، log مناسب و نتیجه». هم‌شکلِ `server/scripts/test-evidence.js` ِ سرور.
 *
 *   node tools/test-evidence/gradle-evidence.mjs --out <پرونده.jsonl> desktop=desktop/build/test-results/test core=core/build/test-results/jvmTest
 *
 * گزارش‌های JUnit XML ِ خودِ Gradle (`build/test-results/<کار>/TEST-*.xml`) را می‌خواند. خطِ اول `kind: "run"` (commit، شاخه،
 * اجرای CI، JDK، سیستم‌عامل)، سپس برای هر آزمون یک خطِ `kind: "test"`:
 *   id      — `<کلاسِ کامل>#<نامِ آزمون>`؛ پایدار میانِ اجراها (همان دو تکهٔ خطِ `KTEST` ِ لاگ)
 *   result  — pass | fail | skip
 *   repro   — دستورِ اجرای همان یک آزمون از ریشهٔ مخزن (نامِ ناامن برای الگوی `--tests` ⇒ همان کلاس)
 *   output  — برای شکست: پیام و چند خطِ اولِ پشته؛ برای SKIP: پیام
 * کدِ خروج ۱ فقط وقتی هیچ گزارشی نیست (شواهد گم شده)؛ آزمونِ سرخ را خودِ گامِ Gradle سرخ می‌کند.
 *
 * ⛔ هیچ راز و دادهٔ حساسی: فقط متغیرهای `GITHUB_*`/`RUNNER_*` ِ نام‌برده خوانده می‌شوند؛ متنِ خطا همان است که در لاگِ CI هم هست.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

/** دستورِ بازتولید برای هر دسته (از ریشهٔ مخزن)؛ همان دستورهای «کامپایل و آزمون» ِ tohid-desktop.yml */
export const SUITES = {
  desktop: (filter) => `./desktop/gradlew --no-daemon -p desktop test --tests ${filter}`,
  core: (filter) => `(cd core && ../desktop/gradlew --no-daemon jvmTest --tests ${filter})`,
};

const ENT = { amp: '&', lt: '<', gt: '>', quot: '"', apos: "'" };
export function unxml(s) {
  return String(s)
    .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
    .replace(/&(#x[0-9a-f]+|#\d+|amp|lt|gt|quot|apos);/gi, (m, e) => {
      if (e[0] !== '#') return ENT[e.toLowerCase()];
      const n = e[1] === 'x' || e[1] === 'X' ? parseInt(e.slice(2), 16) : parseInt(e.slice(1), 10);
      return Number.isFinite(n) ? String.fromCodePoint(n) : m;
    });
}

function attrs(s) {
  const out = {};
  for (const m of s.matchAll(/([\w:-]+)="([^"]*)"/g)) out[m[1]] = unxml(m[2]);
  return out;
}

/** شلِ امن: درونِ '…' (و ' با '\'') */
export const sh = (s) => `'${String(s).replace(/'/g, `'\\''`)}'`;

/** الگوی `--tests`: کلاس.نام اگر نام نویسهٔ ویژهٔ الگو ندارد (* و نقطه ⇒ تکهٔ دیگر)، وگرنه همان کلاس */
export function filterOf(className, name) {
  return /[*.\n\r\\]/.test(name) || !name.trim() || name === 'classMethod' ? sh(className) : sh(`${className}.${name}`);
}

/** خلاصهٔ خروجی: پیام + چند خطِ اول، بی بیش از ۲۰۰۰ نویسه */
export function brief(message, body, lines = 8) {
  const head = String(body || '').split(/\r?\n/).map((l) => l.trimEnd()).filter(Boolean).slice(0, lines).join('\n');
  const text = head.includes(String(message || '').slice(0, 60)) ? head : [message, head].filter(Boolean).join('\n');
  return text.length > 2000 ? `${text.slice(0, 1999)}…` : text;
}

/** testcaseهای یک گزارشِ JUnit XML ⇒ [{className, name, result, durationMs, output?}] */
export function parseJUnit(xml) {
  const out = [];
  const re = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  for (const m of String(xml).matchAll(re)) {
    const a = attrs(m[1]);
    const inner = m[2] || '';
    const bad = /<(failure|error)\b([^>]*?)(?:\/>|>([\s\S]*?)<\/\1>)/.exec(inner);
    const skip = /<skipped\b([^>]*?)(?:\/>|>([\s\S]*?)<\/skipped>)/.exec(inner);
    //  KMP نامِ آزمونِ core را در گزارش با پسوندِ هدف («…[jvm]») می‌نویسد؛ خطِ KTEST و الگوی `--tests` نامِ خودِ متد را می‌خواهند
    const t = {
      className: a.classname || '',
      name: (a.name || '').replace(/\[jvm\]$/, ''),
      result: bad ? 'fail' : skip ? 'skip' : 'pass',
      durationMs: a.time != null && a.time !== '' && Number.isFinite(Number(a.time)) ? Math.round(Number(a.time) * 1000) : null,
    };
    if (bad) t.output = brief(attrs(bad[2]).message, unxml(bad[3] || ''));
    else if (skip) t.output = brief(attrs(skip[1]).message, '', 0);
    out.push(t);
  }
  return out;
}

function commitOf(root) {
  if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA;
  try { return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: root, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); } catch { return ''; }
}

/**
 * نسخهٔ JDK از خروجیِ `java -version`. ⛔ خطِ «Picked up JAVA_TOOL_OPTIONS/_JAVA_OPTIONS/JDK_JAVA_OPTIONS» کنار می‌رود:
 * گزینه‌های JVM ِ محیط (پراکسی، truststore و رمزش) شواهد نیستند و نباید در artifact بیایند.
 */
export function javaLine(text) {
  return String(text || '').split(/\r?\n/).map((l) => l.trim())
    .filter((l) => l && !/^(NOTE: )?Picked up /.test(l)).slice(0, 2).join(' · ');
}

function javaOf() {
  const r = spawnSync('java', ['-version'], { encoding: 'utf8' });
  return r.status === 0 ? javaLine(r.stderr || r.stdout) : '';
}

export function runInfo(root, suites) {
  const e = process.env;
  return {
    kind: 'run',
    commit: commitOf(root),
    ref: e.GITHUB_HEAD_REF || e.GITHUB_REF_NAME || '',
    ci: e.GITHUB_RUN_ID ? `${e.GITHUB_SERVER_URL || 'https://github.com'}/${e.GITHUB_REPOSITORY || ''}/actions/runs/${e.GITHUB_RUN_ID}` : '',
    job: e.GITHUB_JOB || '',
    java: javaOf(),
    os: `${os.type()} ${os.release()} ${os.arch()}`,
    runner: e.RUNNER_NAME ? `${e.RUNNER_NAME} (${e.RUNNER_OS || ''} ${e.RUNNER_ARCH || ''})`.trim() : '',
    suites,
    at: new Date().toISOString(),
  };
}

/** [«دسته=پوشه»] ⇒ خطوطِ JSONL و شمارها. پوشهٔ ناموجود ⇒ در `missing` (نه خطای بی‌صدا). */
export function collect(root, specs) {
  const lines = [];
  const counts = {};
  const missing = [];
  for (const spec of specs) {
    const [suite, dir] = spec.split('=');
    const repro = SUITES[suite];
    if (!repro || !dir) throw new Error(`دستهٔ ناشناخته یا بی پوشه: «${spec}» (دسته‌ها: ${Object.keys(SUITES).join('، ')})`);
    const abs = path.resolve(root, dir);
    const files = fs.existsSync(abs) ? fs.readdirSync(abs).filter((f) => /^TEST-.*\.xml$/.test(f)).sort() : [];
    if (!files.length) { missing.push(spec); continue; }
    const c = (counts[suite] = { pass: 0, fail: 0, skip: 0 });
    for (const f of files) {
      for (const t of parseJUnit(fs.readFileSync(path.join(abs, f), 'utf8'))) {
        c[t.result]++;
        const line = {
          kind: 'test',
          id: `${t.className}#${t.name}`,
          suite,
          className: t.className,
          name: t.name,
          result: t.result,
          durationMs: t.durationMs,
          repro: repro(filterOf(t.className, t.name)),
        };
        if (t.output) line.output = t.output;
        lines.push(line);
      }
    }
  }
  return { lines, counts, missing };
}

function main(argv) {
  const root = path.resolve(fileURLToPath(import.meta.url), '../../..');
  let out = '';
  const specs = [];
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--out') out = argv[++i];
    else specs.push(argv[i]);
  }
  if (!out || !specs.length) {
    console.error('کاربرد: gradle-evidence.mjs --out <پرونده.jsonl> desktop=<پوشه> [core=<پوشه>]');
    return 2;
  }
  const { lines, counts, missing } = collect(root, specs);
  fs.mkdirSync(path.dirname(path.resolve(out)), { recursive: true });
  const body = [runInfo(root, specs), ...lines].map((l) => JSON.stringify(l)).join('\n');
  fs.writeFileSync(out, `${body}\n`);
  const rows = Object.entries(counts).map(([s, c]) => `| ${s} | ${c.pass} | ${c.fail} | ${c.skip} |`);
  const md = ['### GATE-NEW-05 — شواهدِ آزمون‌های Gradle', '', '| دسته | PASS | FAIL | SKIP |', '|---|---|---|---|', ...rows,
    ...missing.map((m) => `\n⚠️ گزارشی نیست: \`${m}\``)].join('\n');
  console.log(md);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${md}\n`);
  if (!lines.length) { console.error('هیچ گزارشِ JUnit پیدا نشد ⇒ شواهدی نیست'); return 1; }
  if (missing.length) { console.error(`گزارشِ این دسته‌ها نیست: ${missing.join('، ')}`); return 1; }
  return 0;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === path.resolve(process.argv[1])) {
  process.exit(main(process.argv.slice(2)));
}
