/**
 * Output: one Markdown section per check, printed and appended to the GitHub
 * job summary; violations caused by this change also become annotations.
 */
import fs from 'node:fs';

const inActions = () => process.env.GITHUB_ACTIONS === 'true';

export const ICON = { ok: '✅', warn: '⚠️', fail: '❌', info: 'ℹ️' };

/** Who caused a finding (internal value → Persian label in the report). */
const CAUSE = { introduced: 'همین تغییر', 'pre-existing': 'از پیش', unknown: 'نامعلوم', 'changed-file': 'فایلِ عوض‌شده' };
export const causeLabel = (cause) => CAUSE[cause] || cause;

/** Status of a ratchet check from its violations. */
export const statusOf = (violations) =>
  violations.some((v) => v.cause !== 'pre-existing') ? 'fail' : violations.length ? 'warn' : 'ok';

/** Markdown table; `rows` are arrays of cells. */
export function table(head, rows, limit = 40) {
  if (!rows.length) return '';
  const esc = (c) => String(c).replace(/\|/g, '\\|').replace(/\n/g, ' ');
  const shown = rows.slice(0, limit);
  const lines = [`| ${head.map(esc).join(' | ')} |`, `|${head.map(() => '---').join('|')}|`];
  for (const r of shown) lines.push(`| ${r.map(esc).join(' | ')} |`);
  if (rows.length > limit) lines.push(`\n… و ${rows.length - limit} ردیفِ دیگر`);
  return lines.join('\n');
}

export function section({ title, status, summary, body = '' }) {
  return [`### ${ICON[status] || ''} ${title}`, '', summary, '', body, ''].join('\n');
}

export function publish(markdown) {
  process.stdout.write(markdown + '\n');
  const file = process.env.GITHUB_STEP_SUMMARY;
  if (file) fs.appendFileSync(file, markdown + '\n');
}

/** GitHub annotation (warning by default) shown on the PR diff. */
export function annotate(file, message, { line, level = 'warning' } = {}) {
  if (!inActions()) return;
  const where = [`file=${file}`, line ? `line=${line}` : ''].filter(Boolean).join(',');
  const clean = String(message).replace(/%/g, '%25').replace(/\r/g, '%0D').replace(/\n/g, '%0A');
  process.stdout.write(`::${level} ${where}::${clean}\n`);
}
