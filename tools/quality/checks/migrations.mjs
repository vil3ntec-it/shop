/**
 * (g) Migration numbers must be unique — the one blocking check, and it
 * blocks only the change that ADDS a file with a number already in use.
 * A duplicate that is already on main is a warning: renaming an applied
 * migration would make migrate.js (which records applied migrations by file
 * name) run it a second time, so the report never advises that.
 */
import { MIGRATIONS_DIR, MIGRATION_NUMBER, MIGRATION_NAME } from '../rules.mjs';
import { duplicateMigrations } from '../lib/measure.mjs';
import { section, table, causeLabel } from '../lib/report.mjs';

export const id = 'migrations';
export const blocking = true;

const PREFIX = `${MIGRATIONS_DIR}/`;

/** Did this change add the file (new, or renamed to this name)? null when unknown. */
function addedHere(changed, file) {
  if (!changed) return null;
  if (!changed.has(file)) return false;
  const base = changed.get(file);
  return base == null || base !== file;
}

/**
 * Pure: duplicate numbers among `.sql` names, each with the files this
 * change added. `changed` is the Map from lib/repo.mjs (or null).
 */
export function migrationFindings(files, changed) {
  const names = files.filter((f) => f.startsWith(PREFIX) && f.endsWith('.sql')).map((f) => f.slice(PREFIX.length));
  const dups = duplicateMigrations(names, MIGRATION_NUMBER).map((d) => {
    const added = d.files.filter((n) => addedHere(changed, PREFIX + n));
    const cause = !changed ? 'unknown' : added.length ? 'introduced' : 'pre-existing';
    return { ...d, added, cause };
  });
  const numbers = names.map((n) => MIGRATION_NUMBER.exec(n)).filter(Boolean).map((m) => Number(m[1]));
  return { names, dups, numbers, badNames: names.filter((n) => !MIGRATION_NAME.test(n)) };
}

function messageOf(d) {
  if (d.cause === 'introduced') {
    const others = d.files.filter((n) => !d.added.includes(n));
    return `این تغییر ${d.added.join('، ')} را با شمارهٔ ${d.number} می‌افزاید که ${others.length ? others.join('، ') : 'فایلِ دیگرِ همین تغییر'} هم دارد؛ `
      + 'به فایلِ تازهٔ همین PR شمارهٔ آزاد از سهم‌بندیِ docs/RULES-fa.md بدهید';
  }
  return `شمارهٔ ${d.number} را ${d.files.join('، ')} از پیش با هم دارند؛ هیچ‌کدام را تغییرِ نام ندهید `
    + '(migrate.js مهاجرتِ اجراشده را با نامِ فایل می‌شناسد و دوباره اجرایش می‌کند)';
}

export function run(ctx) {
  const { names, dups, numbers, badNames } = migrationFindings(ctx.files, ctx.changed);
  const violations = dups.map((d) => ({
    file: PREFIX + (d.added[0] || d.files[d.files.length - 1]),
    cause: d.cause,
    message: messageOf(d),
  }));
  const blocked = violations.some((v) => v.cause === 'introduced');
  const markdown = section({
    title: 'شمارهٔ مهاجرت‌ها (سد فقط برای فایلِ تازهٔ همین تغییر)',
    status: blocked ? 'fail' : violations.length || badNames.length ? 'warn' : 'ok',
    summary: `${names.length} مهاجرت، بزرگ‌ترین شماره ${numbers.length ? Math.max(...numbers) : '—'}؛ `
      + `${dups.length} شمارهٔ تکراری (${violations.filter((v) => v.cause === 'introduced').length} از همین تغییر)؛ `
      + `${badNames.length} نامِ ناهم‌شکل با NNN_name.sql.`,
    body: [
      table(['شماره', 'فایل‌ها', 'علت', 'چه کنید'], dups.map((d, i) => [d.number, d.files.join('، '), causeLabel(d.cause), violations[i].message])),
      table(['نامِ ناهم‌شکل (migrate.js باز هم اجرایش می‌کند)'], badNames.map((n) => [n])),
    ].join('\n'),
  });
  return { markdown, violations, metrics: { count: names.length, duplicates: dups.length } };
}
