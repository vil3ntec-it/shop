/**
 * ESLint, correctness rules only, on the server/src files this change touched
 * (`--all`: every server/src file), plus an ES5 parse of the license/ cores.
 * Report only; never fixes or reformats.
 */
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { ESLINT_PACKAGE, ESLINT_CONFIG, ESLINT_SERVER_FILES, ES5_FILES, matchesAny } from '../rules.mjs';
import { ROOT } from '../lib/repo.mjs';
import { section, table } from '../lib/report.mjs';

export const id = 'eslint';

function lint(files) {
  const r = spawnSync('npx', ['--yes', ESLINT_PACKAGE, '--no-warn-ignored', '-c', ESLINT_CONFIG, '-f', 'json', ...files], {
    cwd: ROOT, encoding: 'utf8', maxBuffer: 256 << 20, timeout: 120_000,
  });
  try {
    return { results: JSON.parse(r.stdout) };
  } catch {
    return { error: (r.error?.message || r.stderr || 'بی خروجی').trim().split('\n').slice(-3).join(' ') };
  }
}

function targets(ctx, isServer) {
  if (ctx.options.all) return { files: ctx.files.filter(isServer), scope: 'همهٔ server/src' };
  if (!ctx.changed) return { files: [], scope: 'کامیتِ پدر نیست: server/src سنجیده نشد' };
  const files = [...ctx.changed.keys()].filter((f) => isServer(f) && ctx.read(f) != null);
  return { files, scope: 'فایل‌هایی که همین تغییر عوض کرده' };
}

export function run(ctx) {
  const isServer = matchesAny(ESLINT_SERVER_FILES);
  const isEs5 = matchesAny(ES5_FILES);
  const server = targets(ctx, isServer);
  const es5 = ctx.files.filter(isEs5);

  const { results, error } = lint([...server.files, ...es5]);
  if (error) {
    const markdown = section({ title: 'ESLint (فقط درستی)', status: 'warn', summary: `ESLint اجرا نشد: ${error}` });
    return { markdown, violations: [], metrics: {} };
  }

  const violations = [];
  const rows = [];
  let errors = 0;
  let warnings = 0;
  let es5Bad = 0;
  for (const res of results) {
    const file = path.relative(ROOT, res.filePath).split(path.sep).join('/');
    for (const m of res.messages) {
      const rule = m.ruleId || 'parse';
      if (isEs5(file)) {
        if (!m.fatal) continue;
        es5Bad++;
        rows.push([file, m.line, 'ES5', m.message]);
        violations.push({
          file, line: m.line, message: `ES5 نیست (گوشی‌های قدیمی): ${m.message}`,
          cause: !ctx.changed ? 'unknown' : ctx.changed.has(file) ? 'introduced' : 'pre-existing',
        });
        continue;
      }
      rows.push([file, m.line, rule, m.message]);
      if (m.severity !== 2) {
        warnings++;
        continue;
      }
      errors++;
      violations.push({ file, line: m.line, cause: 'changed-file', message: `${rule}: ${m.message}` });
    }
  }
  const markdown = section({
    title: 'ESLint (فقط درستی)',
    status: errors || es5Bad ? 'fail' : warnings ? 'warn' : 'ok',
    summary: `${server.files.length} فایلِ سرور (${server.scope}): ${errors} خطا، ${warnings} هشدار. `
      + `هسته‌های ES5: ${es5.length} فایل، ${es5Bad} خطای ES5.`,
    body: table(['فایل', 'خط', 'قاعده', 'پیام'], rows, 60),
  });
  return { markdown, violations, metrics: { serverFiles: server.files.length, errors, warnings, es5Files: es5.length, es5Bad } };
}
