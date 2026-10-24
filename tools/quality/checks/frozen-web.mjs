/**
 * (f) The frozen shop web app (index.html): size reported against the
 * baseline so growth is visible. Report only.
 */
import { FROZEN_FILES } from '../rules.mjs';
import { lineCount } from '../lib/measure.mjs';
import { attribute } from '../lib/ratchet.mjs';
import { section, table, statusOf } from '../lib/report.mjs';

export const id = 'frozen-web';

const size = (text) => (text == null ? null : { lines: lineCount(text), bytes: Buffer.byteLength(text) });

export function run(ctx) {
  const caps = ctx.baseline.frozen || {};
  const rows = [];
  const violations = [];
  for (const file of FROZEN_FILES) {
    const now = size(ctx.read(file));
    if (!now) continue;
    const cap = caps[file] || { lines: 0, bytes: 0 };
    rows.push([file, now.lines, cap.lines, now.bytes, cap.bytes, now.lines - cap.lines]);
    if (now.lines <= cap.lines && now.bytes <= cap.bytes) continue;
    violations.push({
      file,
      cause: attribute(file, now.bytes, ctx.changed, (base) => ctx.atBase(base, (t) => size(t).bytes)),
      message: `${file} یخ‌زده است ولی به ${now.lines} خط / ${now.bytes} بایت رسید (مبنا ${cap.lines} / ${cap.bytes})`,
    });
  }
  const markdown = section({
    title: 'اندازهٔ برنامهٔ وبِ یخ‌زده (فقط گزارش)',
    status: statusOf(violations),
    summary: violations.length ? 'برنامهٔ یخ‌زده بزرگ‌تر شد.' : 'بی رشد.',
    body: table(['فایل', 'خط', 'خطِ مبنا', 'بایت', 'بایتِ مبنا', 'Δ خط'], rows),
  });
  return { markdown, violations, metrics: Object.fromEntries(rows.map((r) => [r[0], { lines: r[1], bytes: r[3] }])) };
}

export function update(baseline, ctx, { snapshot = false } = {}) {
  const next = {};
  for (const file of FROZEN_FILES) {
    const now = size(ctx.read(file));
    if (!now) continue;
    const cap = baseline.frozen?.[file];
    next[file] = snapshot || !cap ? now : { lines: Math.min(cap.lines, now.lines), bytes: Math.min(cap.bytes, now.bytes) };
  }
  baseline.frozen = next;
}
