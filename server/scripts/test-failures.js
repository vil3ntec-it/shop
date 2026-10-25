'use strict';
/**
 * فهرستِ آزمون‌های ناموفق در پایانِ اجرا — برای لاگِ کوتاهِ CI.
 *
 * چرا: یک «not ok» در میانهٔ ۲۸٬۰۰۰ خطِ TAP گم می‌شود و ابزارِ لاگِ CI فقط
 * ۵۰۰۰ خطِ آخر را می‌دهد (#381: «۱ از ۱۵۹۵» بی نام). این فایل دو کار می‌کند:
 *
 *   ۱) گزارشگرِ اضافیِ `node --test` (همین ماژول، `--test-reporter`): هر
 *      `test:fail` را یک خطِ JSON در فایلِ مقصد می‌نویسد. گزارشگرِ اصلی (TAP یا
 *      spec، همان که بود) دست نمی‌خورد؛ این فقط یک مقصدِ دوم است.
 *   ۲) `summarize(file)`: همان خط‌ها را در پایان چاپ می‌کند — نام، فایل و چند
 *      خطِ اولِ خطا. شکستِ «زیرآزمون‌ها شکستند» ِ والد تکرار نمی‌شود.
 *
 * ⛔ رفتارِ آزمون‌ها عوض نمی‌شود: همان فایل‌ها، همان ترتیب، همان کدِ خروج.
 */
const fs = require('fs');
const path = require('path');

const MAX_LINES = 8;
const ROOT = path.join(__dirname, '..');

/** متنِ خطا + حداکثر دو قابِ پشته از کدِ خودمان (نه node:internal)؛ کلاً تا MAX_LINES خط. */
function brief(text) {
  const lines = text.split('\n');
  const msg = lines.filter((l) => !/^\s+at /.test(l));
  const frames = lines.filter((l) => /^\s+at /.test(l) && !/\(node:|\bnode:internal|node_modules/.test(l)).slice(0, 2);
  return [...msg, ...frames.map((l) => l.trim().split(ROOT + path.sep).join(''))].slice(0, MAX_LINES).join('\n');
}

/** گزارشگر: هر شکست ⇐ یک خطِ JSON. */
async function* reporter(source) {
  for await (const ev of source) {
    if (ev.type !== 'test:fail') continue;
    const d = ev.data || {};
    const err = d.details && d.details.error;
    //  والدی که فقط چون زیرآزمونش شکست ناموفق است، چیزی تازه نمی‌گوید
    if (err && err.failureType === 'subtestsFailed') continue;
    const cause = (err && err.cause) || err || {};
    yield JSON.stringify({
      name: String(d.name || ''),
      file: d.file ? path.relative(ROOT, d.file) : '',
      line: d.line || null,
      error: brief(String((cause && (cause.stack || cause.message)) || (err && err.message) || '')),
    }) + '\n';
  }
}

/** پایانِ اجرا: فهرستِ شکست‌ها از فایلِ گزارشگر ⇒ متن (بی شکست ⇒ ''). */
function summarize(file, { exitCode = 0 } = {}) {
  let rows = [];
  try {
    rows = fs.readFileSync(file, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
  } catch { rows = []; }
  if (!rows.length) {
    return exitCode ? `\n── آزمون‌ها با کدِ ${exitCode} پایان یافتند ولی هیچ «not ok»ی ثبت نشد (خطای بیرون از آزمون؟) ──\n` : '';
  }
  const out = [`\n── آزمون‌های ناموفق (${rows.length}) ──`];
  for (const r of rows) {
    out.push(`✖ ${r.name}${r.file ? `  —  ${r.file}${r.line ? `:${r.line}` : ''}` : ''}`);
    for (const l of r.error.split('\n')) if (l.trim()) out.push(`    ${l}`);
  }
  return out.join('\n') + '\n';
}

module.exports = reporter;
module.exports.summarize = summarize;
module.exports.MAX_LINES = MAX_LINES;
module.exports.brief = brief;
