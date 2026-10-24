/**
 * (a) File size: no new non-test source file over MAX_LINES; files already
 * over it (listed in the baseline) may not grow.
 */
import { MAX_LINES, isSource } from '../rules.mjs';
import { lineCount } from '../lib/measure.mjs';
import { checkPerFile, lowerPerFile, snapshotPerFile, attribute } from '../lib/ratchet.mjs';
import { section, table, causeLabel, statusOf } from '../lib/report.mjs';

export const id = 'file-size';

function measure(ctx) {
  const current = new Map();
  for (const f of ctx.files) if (isSource(f)) current.set(f, lineCount(ctx.read(f)));
  return current;
}

export function run(ctx) {
  const caps = ctx.baseline.fileSize || {};
  const current = measure(ctx);
  const { over, lowerable } = checkPerFile(caps, current, MAX_LINES);
  const violations = over.map((v) => ({
    ...v,
    cause: attribute(v.file, v.value, ctx.changed, (base) => ctx.atBase(base, lineCount)),
    message: v.listed
      ? `${v.file} به ${v.value} خط رسید (سقف ${v.cap})؛ به‌جای بزرگ‌تر کردن، فایلِ تازه کنارش بسازید`
      : `${v.file} ${v.value} خط دارد (بیش از ${MAX_LINES})؛ آن را به فایل‌های کوچک‌تر بشکنید`,
  }));
  const big = [...current.values()].filter((n) => n > MAX_LINES).length;
  const markdown = section({
    title: `اندازهٔ فایل (فایلِ تازه حداکثر ${MAX_LINES} خط)`,
    status: statusOf(violations),
    summary: `${big} فایلِ کد بیش از ${MAX_LINES} خط (در خط‌مبنا ${Object.keys(caps).length})؛ `
      + `${violations.length} از سقفِ خود گذشته؛ ${lowerable.length} سقف را می‌شود پایین آورد.`,
    body: table(['فایل', 'خط', 'سقف', 'علت'], violations.map((v) => [v.file, v.value, v.cap, causeLabel(v.cause)])),
  });
  return { markdown, violations, metrics: { over800: big, overCap: violations.length } };
}

export function update(baseline, ctx, { snapshot = false } = {}) {
  const current = measure(ctx);
  baseline.fileSize = snapshot ? snapshotPerFile(current, MAX_LINES) : lowerPerFile(baseline.fileSize || {}, current, MAX_LINES);
}
