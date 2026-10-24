/**
 * Line coverage, report only: the server tests that need no database, run
 * with Node's built-in coverage (no extra packages). The full suite stays in
 * server.yml; this only shows how much of the pure code those tests reach.
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { COVERAGE_DIR, NEEDS_DATABASE } from '../rules.mjs';
import { ROOT } from '../lib/repo.mjs';
import { section, table } from '../lib/report.mjs';

export const id = 'coverage';

/** lcov text → [{file, found, hit}] */
export function parseLcov(text) {
  const out = [];
  let cur = null;
  for (const line of text.split('\n')) {
    if (line.startsWith('SF:')) cur = { file: line.slice(3), found: 0, hit: 0 };
    else if (cur && line.startsWith('LF:')) cur.found = Number(line.slice(3));
    else if (cur && line.startsWith('LH:')) cur.hit = Number(line.slice(3));
    else if (cur && line === 'end_of_record') {
      out.push(cur);
      cur = null;
    }
  }
  return out;
}

const pct = (hit, found) => (found ? `${((100 * hit) / found).toFixed(1)}%` : '—');

/** Report area: server/src/lib, server/scripts…, or the top folder elsewhere. */
const areaOf = (file) => (file.startsWith('server/')
  ? path.posix.dirname(file).split('/').slice(0, 3).join('/')
  : file.split('/')[0]);

function pureTests(ctx) {
  const prefix = `${COVERAGE_DIR}/test/`;
  return ctx.files
    .filter((f) => f.startsWith(prefix) && /^[^/]+\.test\.js$/.test(f.slice(prefix.length)))
    .filter((f) => !NEEDS_DATABASE.test(ctx.read(f) || ''))
    .map((f) => f.slice(COVERAGE_DIR.length + 1));
}

export function run(ctx) {
  const tests = pureTests(ctx);
  const lcov = path.join(os.tmpdir(), `quality-lcov-${process.pid}.info`);
  const r = spawnSync(process.execPath, [
    '--test', '--experimental-test-coverage',
    '--test-reporter=dot', '--test-reporter-destination=stdout',
    '--test-reporter=lcov', `--test-reporter-destination=${lcov}`,
    ...tests,
  ], { cwd: path.join(ROOT, COVERAGE_DIR), encoding: 'utf8', maxBuffer: 256 << 20, timeout: 150_000 });

  const records = fs.existsSync(lcov) ? parseLcov(fs.readFileSync(lcov, 'utf8')) : [];
  fs.rmSync(lcov, { force: true });
  const source = records
    .map((rec) => ({ ...rec, file: path.relative(ROOT, path.resolve(ROOT, COVERAGE_DIR, rec.file)).split(path.sep).join('/') }))
    .filter((rec) => !rec.file.startsWith('..') && !/(^|\/)(test|node_modules)\//.test(rec.file));
  const byArea = new Map();
  for (const rec of source) {
    const a = byArea.get(areaOf(rec.file)) || { found: 0, hit: 0, files: 0 };
    byArea.set(areaOf(rec.file), { found: a.found + rec.found, hit: a.hit + rec.hit, files: a.files + 1 });
  }
  const found = source.reduce((a, s) => a + s.found, 0);
  const hit = source.reduce((a, s) => a + s.hit, 0);
  const failed = r.status !== 0;
  const markdown = section({
    title: 'پوشش (فقط گزارش؛ آزمون‌های بی پایگاه‌دادهٔ سرور)',
    status: failed ? 'warn' : 'info',
    summary: `${tests.length} فایلِ آزمون`
      + `${failed ? ` — خروجِ ${r.status ?? r.signal}: بعضی رد شدند یا وقت تمام شد (سدِ واقعیِ آزمون server.yml است)` : ''}؛ `
      + `${source.length} فایلِ کد بار شد؛ خط‌ها ${pct(hit, found)} (${hit}/${found}).`,
    body: [
      table(['بخش', 'فایل', 'خط'], [...byArea].sort(([a], [b]) => a.localeCompare(b)).map(([a, d]) => [a, d.files, pct(d.hit, d.found)])),
      '',
      '<details><summary>کم‌پوشش‌ترین فایل‌ها</summary>\n',
      table(['فایل', 'خط'], source.filter((s) => s.found >= 20).sort((a, b) => a.hit / a.found - b.hit / b.found)
        .slice(0, 15).map((s) => [s.file, pct(s.hit, s.found)])),
      '\n</details>',
    ].join('\n'),
  });
  return { markdown, violations: [], metrics: { tests: tests.length, files: source.length, linePct: found ? hit / found : null } };
}
