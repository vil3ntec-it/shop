'use strict';
/**
 * INV-11/INV-01 (فازِ ۴ ِ شعبه‌ها، `docs/BRANCHES-fa.md` §۱۰ «پایانِ فازِ ۴»): ردیفِ «انتقالِ میانِ شعبه» ِ دفترِ دستگاه
 * (`warehouseEntries` با `isAdjustment`، `adjKind = branch_transfer` و `refId` = شناسهٔ انتقال — `core/stock/BranchTransferLedger`،
 * `data/BranchTransferBookAndroid.kt`) فقط آینهٔ گامِ سرور است: حرکت‌های `bxfer:` همان دم در بخشِ هر سو نشستند. پس
 * `catalog.ingestTx` به آن سهم نمی‌دهد، وگرنه دو بار شمرده می‌شد.
 *
 * ⛔ فقط نشانِ یک انتقالِ **واقعیِ** همین دکان که همین شعبه یک سرش است؛ نشانِ ساختگی (یا انتقالِ شعبه/دکانِ دیگر) سهمِ عادی دارد —
 *    نشان به‌تنهایی چیزی را از موجودیِ سرور پنهان نمی‌کند.
 */
const cb = require('./catalog-branch');

/** نشانِ ردیفِ آینه (همان `BranchTransferLedger.ADJ_KIND`) */
const KIND = 'branch_transfer';
const ID_RE = /^bxf_[0-9a-f]{24}$/;

/** ردیف‌های ورودِ انبارِ این صفحه که آینهٔ انتقالِ واقعیِ همین شعبه‌اند ⇒ Set<row_id> */
async function mirroredIn(c, sk, entries, decode) {
  const want = new Map();
  for (const r of entries) {
    const d = decode(r.data) || {};
    if (d.isAdjustment === true && d.adjKind === KIND && typeof d.refId === 'string' && ID_RE.test(d.refId)) want.set(r.row_id, d.refId);
  }
  if (!want.size) return new Set();
  const real = new Set((await c.query(
    `SELECT id FROM branch_transfers WHERE shop_id=$1 AND id = ANY($2::text[]) AND (from_branch=$3 OR to_branch=$3)`,
    [cb.shopOf(sk), [...new Set(want.values())], cb.branchOf(sk)]
  )).rows.map((r) => r.id));
  return new Set([...want].filter(([, id]) => real.has(id)).map(([rowId]) => rowId));
}

module.exports = { KIND, mirroredIn };
