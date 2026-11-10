'use strict';
/**
 * «تاریخِ تصحیح‌شده» ِ سند در Sync v3 — S28b، MASTER-SPEC بخشِ ۳.۸.۱ بندِ ۸ (SYN-05).
 *
 * ساعتِ خودِ سند (`createdAt`، ساعتِ دستگاه) بیش از `SKEW_MS` (۵ دقیقه) با «زمانِ تصحیح‌شده» فرق دارد ⇒
 * فیلدِ جدای `correctedAt` = دیواریِ HLCِ مؤثرِ همان op (HLCِ کج را سرور با ساعتِ خودش عوض کرده است).
 *   • `createdAt` و `date` ِ دستگاه هرگز بازنویسی نمی‌شوند؛
 *   • سندِ با ساعتِ درست هیچ فیلدِ تازه‌ای نمی‌گیرد (بایت‌به‌بایت همان)؛
 *   • `correctedAt` فقط از سرور است: نوشتهٔ دستگاه دور ریخته می‌شود.
 */
const hlc = require('./hlc');

/** دادهٔ درجِ سند ⇒ همان، با `correctedAt` ِ سرور در صورتِ اختلاف (روی همان شیء) */
function corrected(data, effHlc) {
  delete data.correctedAt;
  const at = Number(data.createdAt);
  if (!Number.isFinite(at) || at <= 0) return data;
  const wall = hlc.parse(effHlc).wall;
  if (Math.abs(wall - at) > hlc.SKEW_MS) data.correctedAt = wall;
  return data;
}

/**
 * همان `correctedAt` ِ سرور برای **دستگاهِ سازنده**، که Pull opِ خودش را برایش نمی‌آورد (SYN-05): در پاسخِ push
 * (`current`، `sync-v3-apply.js`) ⇒ برنامه روی دفتر و سایه می‌نشاند (`currentFix` ِ وب، `V3Current` ِ کاتلین).
 * @param fields آنچه دستگاه فرستاد؛ @param data ردیفِ سرور ⇒ `{correctedAt}` یا null
 */
function amendOf(fields, data) {
  const at = data ? data.correctedAt : undefined;
  return Number.isSafeInteger(at) && (!fields || fields.correctedAt !== at) ? { correctedAt: at } : null;
}

module.exports = { corrected, amendOf };
