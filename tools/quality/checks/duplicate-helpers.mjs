/**
 * (c) Re-declared helpers: each rule's count of copies outside its canonical
 * home may only go down (AUDIT §1.5).
 */
import { DUPLICATE_HELPERS } from '../rules.mjs';
import { findAll } from '../lib/measure.mjs';
import { lowerTotals } from '../lib/ratchet.mjs';
import { section, table, statusOf } from '../lib/report.mjs';

export const id = 'duplicate-helpers';

const counts = (rule, file) => rule.applies(file) && !rule.home.includes(file);
const count = (rule, text) => findAll(text, rule.pattern, rule.confirm).length;

function measure(ctx) {
  const totals = {};
  const sites = [];
  for (const rule of DUPLICATE_HELPERS) {
    totals[rule.id] = 0;
    for (const f of ctx.files) {
      if (!counts(rule, f)) continue;
      for (const hit of findAll(ctx.read(f), rule.pattern, rule.confirm)) {
        totals[rule.id]++;
        sites.push({ rule: rule.id, file: f, line: hit.line });
      }
    }
  }
  return { totals, sites };
}

/**
 * How many copies this change added (sum over the files it touched), and the
 * first file that gained one; delta is null when there is no parent commit.
 */
function addedHere(rule, ctx) {
  if (!ctx.changed) return { delta: null };
  let delta = 0;
  let file = '';
  for (const [now, base] of ctx.changed) {
    if (!counts(rule, now)) continue;
    const d = count(rule, ctx.read(now)) - (ctx.atBase(base, (t) => count(rule, t)) ?? 0);
    if (d > 0 && !file) file = now;
    delta += d;
  }
  return { delta, file };
}

export function run(ctx) {
  const caps = ctx.baseline.duplicateHelpers || {};
  const { totals, sites } = measure(ctx);
  const violations = [];
  const rows = [];
  for (const rule of DUPLICATE_HELPERS) {
    const cap = caps[rule.id] ?? 0;
    const { delta, file } = addedHere(rule, ctx);
    rows.push([rule.id, totals[rule.id], cap, delta == null ? '?' : delta > 0 ? `+${delta}` : String(delta), rule.what]);
    if (totals[rule.id] <= cap) continue;
    const here = sites.filter((s) => s.rule === rule.id && s.file === file).pop();
    violations.push({
      file: here?.file || '',
      line: here?.line,
      cause: delta == null ? 'unknown' : delta > 0 ? 'introduced' : 'pre-existing',
      message: `${rule.id}: ${totals[rule.id]} نسخه، بیش از سقفِ ${cap}. کمکیِ موجود را به کار ببرید، دوباره تعریف نکنید.`,
    });
  }
  const markdown = section({
    title: 'کمکی‌های تکراری',
    status: statusOf(violations),
    summary: `${sites.length} نسخه از ${DUPLICATE_HELPERS.length} کمکی؛ ${violations.length} کمکی بالای سقف.`,
    body: [
      table(['کمکی', 'نسخه', 'سقف', 'این تغییر', 'چیست'], rows),
      '',
      '<details><summary>کجا</summary>\n',
      table(['کمکی', 'فایل', 'خط'], sites.map((s) => [s.rule, s.file, s.line]), 200),
      '\n</details>',
    ].join('\n'),
  });
  return { markdown, violations, metrics: totals };
}

export function update(baseline, ctx, { snapshot = false } = {}) {
  const { totals } = measure(ctx);
  baseline.duplicateHelpers = snapshot ? totals : lowerTotals(baseline.duplicateHelpers || {}, totals);
}
