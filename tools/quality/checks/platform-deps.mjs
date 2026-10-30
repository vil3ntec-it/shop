/**
 * (f) Platform dependencies (android.*, java.*, Context, SharedPreferences …) in
 * Kotlin logic: per-file caps that may only go down; a new file outside a
 * boundary folder may have none (docs/IOS-PLAN-fa.md §۴ گامِ ۰).
 */
import { PLATFORM_DEPS } from '../rules.mjs';
import { findAll } from '../lib/measure.mjs';
import { checkPerFile, lowerPerFile, snapshotPerFile, attribute } from '../lib/ratchet.mjs';
import { section, table, causeLabel, statusOf } from '../lib/report.mjs';

export const id = 'platform-deps';

/** Hits per pattern id (only ids with hits). */
export function platformHits(text) {
  const byId = {};
  for (const { id: pid, pattern } of PLATFORM_DEPS.patterns) {
    const n = findAll(text, pattern).length;
    if (n) byId[pid] = n;
  }
  return byId;
}

const count = (text) => Object.values(platformHits(text)).reduce((a, b) => a + b, 0);
export const measured = (file) => PLATFORM_DEPS.applies(file) && !PLATFORM_DEPS.boundary(file);

function measure(ctx) {
  const current = new Map();
  for (const f of ctx.files) {
    if (!measured(f)) continue;
    const n = count(ctx.read(f));
    if (n > 0) current.set(f, n);
  }
  return current;
}

export function run(ctx) {
  const caps = ctx.baseline.platformDeps || {};
  const current = measure(ctx);
  const { over, lowerable } = checkPerFile(caps, current, 0);
  const violations = over.map((v) => {
    const kinds = Object.entries(platformHits(ctx.read(v.file))).map(([k, n]) => `${k}×${n}`).join('، ');
    return {
      ...v,
      kinds,
      cause: attribute(v.file, v.value, ctx.changed, (base) => ctx.atBase(base, count)),
      message: v.listed
        ? `${v.file}: ${v.value} وابستگیِ پلتفرمی (سقف ${v.cap}: ${kinds})؛ منطقِ تازه را در core/src/commonMain یا پشتِ رابط بگذارید`
        : `${v.file}: فایلِ تازه با وابستگیِ پلتفرمی (${kinds})؛ منطق در core/src/commonMain، پیاده‌سازیِ پلتفرم در پوشهٔ platform/`,
    };
  });
  const total = [...current.values()].reduce((a, b) => a + b, 0);
  const markdown = section({
    title: 'وابستگیِ پلتفرمی در منطقِ Kotlin (راهِ آیفون)',
    status: statusOf(violations),
    summary: `${total} مورد در ${current.size} فایل (در خط‌مبنا ${Object.keys(caps).length})؛ `
      + `${violations.length} فایل بالای سقف؛ ${lowerable.length} سقف را می‌شود پایین آورد.`,
    body: table(['فایل', 'شمار', 'سقف', 'نوع', 'علت'],
      violations.map((v) => [v.file, v.value, v.cap, v.kinds, causeLabel(v.cause)])),
  });
  return { markdown, violations, metrics: { total, files: current.size } };
}

export function update(baseline, ctx, { snapshot = false } = {}) {
  const current = measure(ctx);
  //  the first run writes today's values (no caps yet); after that caps only go down
  baseline.platformDeps = snapshot || !baseline.platformDeps
    ? snapshotPerFile(current, 0)
    : lowerPerFile(baseline.platformDeps, current, 0);
}
