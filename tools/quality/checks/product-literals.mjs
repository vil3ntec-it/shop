/**
 * (d) Inline 'pump' / 'shop' literals compared in the server: per-file caps
 * that may only go down; a new file may have none (AUDIT §1.8, §2 "a product").
 */
import { PRODUCT_LITERAL } from '../rules.mjs';
import { findAll } from '../lib/measure.mjs';
import { checkPerFile, lowerPerFile, snapshotPerFile, attribute } from '../lib/ratchet.mjs';
import { section, table, causeLabel, statusOf } from '../lib/report.mjs';

export const id = 'product-literals';

const count = (text) => findAll(text, PRODUCT_LITERAL.pattern).length;

function measure(ctx) {
  const current = new Map();
  for (const f of ctx.files) {
    if (!PRODUCT_LITERAL.applies(f)) continue;
    const n = count(ctx.read(f));
    if (n > 0) current.set(f, n);
  }
  return current;
}

export function run(ctx) {
  const caps = ctx.baseline.productLiterals || {};
  const current = measure(ctx);
  const { over, lowerable } = checkPerFile(caps, current, 0);
  const violations = over.map((v) => ({
    ...v,
    cause: attribute(v.file, v.value, ctx.changed, (base) => ctx.atBase(base, count)),
    message: `${v.file}: ${v.value} مقایسهٔ درجای 'pump'/'shop' (سقف ${v.cap})؛ مقایسهٔ تازه نیفزایید`,
  }));
  const total = [...current.values()].reduce((a, b) => a + b, 0);
  const markdown = section({
    title: "رشته‌های 'pump' / 'shop' در server/src",
    status: statusOf(violations),
    summary: `${total} مقایسه در ${current.size} فایل؛ ${violations.length} فایل بالای سقف؛ ${lowerable.length} سقف را می‌شود پایین آورد.`,
    body: table(['فایل', 'شمار', 'سقف', 'علت'], violations.map((v) => [v.file, v.value, v.cap, causeLabel(v.cause)])),
  });
  return { markdown, violations, metrics: { total, files: current.size } };
}

export function update(baseline, ctx, { snapshot = false } = {}) {
  const current = measure(ctx);
  baseline.productLiterals = snapshot ? snapshotPerFile(current, 0) : lowerPerFile(baseline.productLiterals || {}, current, 0);
}
