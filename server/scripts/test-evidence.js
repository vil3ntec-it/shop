'use strict';
/**
 * پروندهٔ شواهدِ هر آزمون — GATE-NEW-05 (§۳۵): «برای هر تست شناسه، build/commit، محیط،
 * دستگاه/نسخه، دادهٔ آزمونِ غیرحساس، گام‌های بازتولید، خروجیِ واقعی، log مناسب و نتیجه».
 *
 * گزارشگرِ اضافیِ `node --test` (کنارِ `test-failures.js`)، فقط وقتی `TEST_EVIDENCE_FILE`
 * داده شده (`scripts/test-run.js`، `scripts/test-pglite.js`؛ CI آن را به‌عنوانِ artifact
 * برمی‌دارد). خطِ اول `kind: "run"` (commit، شاخه، اجرای CI، Node، سیستم‌عامل، پایگاه)، سپس
 * برای هر آزمون یک خطِ `kind: "test"`:
 *   id       — `<فایل>#<نامِ کاملِ آزمون>`؛ پایدار میانِ اجراها
 *   result   — pass | fail | skip | todo
 *   repro    — دستورِ دقیقِ اجرای همان یک آزمون
 *   output   — برای شکست: پیام و دو قابِ پشته از کدِ خودمان (همان `test-failures.brief`)
 *
 * ⛔ هیچ راز و دادهٔ حساسی: نشانیِ پایگاه بی نام و رمز؛ هیچ متغیرِ محیطِ دیگری خوانده
 * نمی‌شود؛ متنِ خطا همان است که در لاگِ CI هم هست. ⛔ رفتارِ آزمون‌ها عوض نمی‌شود.
 */
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { brief } = require('./test-failures');

const ROOT = path.join(__dirname, '..');

/** نشانیِ پایگاه بی نام و رمز (و بی querystring) */
function dbLabel(url = process.env.TEST_DATABASE_URL || process.env.DATABASE_URL || '') {
  const s = String(url);
  if (!s) return '';
  if (/^pglite:/.test(s)) return s.split('?')[0];
  try {
    const u = new URL(s);
    return `${u.protocol}//${u.hostname}${u.port ? `:${u.port}` : ''}${u.pathname}`;
  } catch { return 'unparsed'; }
}

function commitOf() {
  if (process.env.GITHUB_SHA) return process.env.GITHUB_SHA;
  try { return execFileSync('git', ['rev-parse', 'HEAD'], { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); } catch { return ''; }
}

function runInfo() {
  const gh = process.env.GITHUB_RUN_ID
    ? `${process.env.GITHUB_SERVER_URL || 'https://github.com'}/${process.env.GITHUB_REPOSITORY || ''}/actions/runs/${process.env.GITHUB_RUN_ID}`
    : '';
  return {
    kind: 'run',
    commit: commitOf(),
    ref: process.env.GITHUB_HEAD_REF || process.env.GITHUB_REF_NAME || '',
    ci: gh,
    job: process.env.GITHUB_JOB || '',
    node: process.version,
    os: `${os.type()} ${os.release()} ${os.arch()}`,
    db: dbLabel(),
    at: new Date().toISOString(),
  };
}

const escapeRe = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

/** دستورِ اجرای همان یک آزمون (از پوشهٔ server) */
function reproOf(file, fullName) {
  const db = dbLabel();
  //  نشانیِ پایگاهِ واقعی رمز دارد ⇒ از محیطِ خودِ اجراکننده
  const env = db.startsWith('pglite') ? `TEST_DATABASE_URL=${db} ` : db ? 'TEST_DATABASE_URL="$TEST_DATABASE_URL" ' : '';
  return `cd server && ${env}node --test --test-concurrency=1 --test-name-pattern=${JSON.stringify(`^${escapeRe(fullName)}$`)} ${file}`;
}

const RESULT = { 'test:pass': 'pass', 'test:fail': 'fail' };

/** گزارشگر: خطِ «run» و برای هر آزمون یک خطِ JSON */
async function* reporter(source) {
  yield `${JSON.stringify(runInfo())}\n`;
  const stack = [];   // نامِ والدها به‌ازای عمق
  for await (const ev of source) {
    const d = ev.data || {};
    if (ev.type === 'test:start') { stack[d.nesting] = String(d.name || ''); stack.length = d.nesting + 1; continue; }
    if (!RESULT[ev.type]) continue;
    const err = d.details && d.details.error;
    if (err && err.failureType === 'subtestsFailed') continue;
    const names = stack.slice(0, d.nesting).concat(String(d.name || ''));
    const file = d.file ? path.relative(ROOT, d.file).split(path.sep).join('/') : '';
    const result = d.skip ? 'skip' : d.todo ? 'todo' : RESULT[ev.type];
    const cause = (err && err.cause) || err || {};
    const line = {
      kind: 'test',
      id: `${file}#${names.join(' > ')}`,
      file,
      line: d.line || null,
      name: names.join(' > '),
      result,
      durationMs: d.details && typeof d.details.duration_ms === 'number' ? Math.round(d.details.duration_ms) : null,
      repro: reproOf(file, names[names.length - 1]),
    };
    if (result === 'fail') line.output = brief(String((cause && (cause.stack || cause.message)) || (err && err.message) || ''));
    yield `${JSON.stringify(line)}\n`;
  }
}

module.exports = reporter;
module.exports.dbLabel = dbLabel;
module.exports.reproOf = reproOf;
module.exports.runInfo = runInfo;
