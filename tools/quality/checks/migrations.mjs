/**
 * (g) Migration numbers must be unique — the one blocking check. Two files
 * with one number make the apply order depend on their names.
 */
import { MIGRATIONS_DIR, MIGRATION_NAME } from '../rules.mjs';
import { duplicateMigrations } from '../lib/measure.mjs';
import { section, table } from '../lib/report.mjs';

export const id = 'migrations';
export const blocking = true;

export function run(ctx) {
  const prefix = `${MIGRATIONS_DIR}/`;
  const names = ctx.files.filter((f) => f.startsWith(prefix) && f.endsWith('.sql')).map((f) => f.slice(prefix.length));
  const dups = duplicateMigrations(names, MIGRATION_NAME);
  const badNames = names.filter((n) => !MIGRATION_NAME.test(n));
  const numbers = names.map((n) => MIGRATION_NAME.exec(n)).filter(Boolean).map((m) => Number(m[1]));
  const violations = dups.map((d) => ({
    file: `${prefix}${d.files[d.files.length - 1]}`,
    cause: 'introduced',
    message: `شمارهٔ مهاجرتِ ${d.number} را ${d.files.join('، ')} با هم دارند؛ شمارهٔ آزاد از سهم‌بندیِ docs/RULES-fa.md بگیرید`,
  }));
  const markdown = section({
    title: 'شمارهٔ مهاجرت‌ها (سد)',
    status: violations.length ? 'fail' : badNames.length ? 'warn' : 'ok',
    summary: `${names.length} مهاجرت، بزرگ‌ترین شماره ${numbers.length ? Math.max(...numbers) : '—'}؛ `
      + `${dups.length} شمارهٔ تکراری؛ ${badNames.length} نامِ ناهم‌شکل با NNN_name.sql.`,
    body: [
      table(['شماره', 'فایل‌ها'], dups.map((d) => [d.number, d.files.join('، ')])),
      table(['نامِ نامنتظر'], badNames.map((n) => [n])),
    ].join('\n'),
  });
  return { markdown, violations, metrics: { count: names.length, duplicates: dups.length } };
}
