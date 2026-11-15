'use strict';
/**
 * موجودیِ سرورِ هر شعبه (INV-01 «به تفکیکِ شعبه»؛ فازِ ۳ ِ `docs/BRANCHES-fa.md`) — کمکی‌های `lib/catalog.js` تا آن پرونده
 * زیرِ سقفِ اندازه بماند. «کلیدِ موجودی» همان شناسهٔ دکان (شعبهٔ اصلی، همان امروز) یا `<دکان>#<شعبه>` (`branch-key`):
 *
 *   اصلی     ⇐ همان `catalog_sync_state`، قالبِ دفترِ تنظیمات (v1/v3)، همان جدول‌های دستگاه و نگه‌داشتهٔ سفارشِ آنلاین.
 *   شعبه     ⇐ `catalog_branch_state` (مهاجرتِ ۹۰۰)؛ دفترِ بخشِ خودش (همیشه v3، `account_id` = همان کلید)؛ **بی** `products`
 *              (تعریفِ کالا مالِ کلِ دکان است و از اصلی می‌آید؛ ردیف‌های کالای بخشِ شعبه فقط آینه‌اند، `branch-catalog`)؛
 *              نگه‌داشتهٔ سفارشِ آنلاین ندارد (تصمیمِ ۱۱: فروشِ آنلاین فقط از شعبهٔ اصلی).
 */
const { one } = require('../db');
const bk = require('./branch-key');

const isBranch = (sk) => bk.isBranch(sk);
const shopOf = (sk) => bk.shopOf(sk);
const branchOf = (sk) => bk.branchOf(sk);

/** قالبِ دفترِ دستگاه برای این کلید */
const fmtOf = (sk, settings) => (isBranch(sk) || settings.syncSource === 'v3' ? 'v3' : 'v1');

/** جدول‌های دستگاه که موجودیِ این کلید از آن‌ها خوانده می‌شود */
const tablesOf = (sk, tables) => (isBranch(sk) ? tables.filter((t) => t !== 'products') : tables);

/** `{source, watermark, last_run_at}` یا undefined — درونِ تراکنش با همان اتصال */
async function stateOf(c, sk) {
  const run = c ? async (t, p) => (await c.query(t, p)).rows[0] : one;
  if (!isBranch(sk)) return run('SELECT source, watermark, last_run_at FROM catalog_sync_state WHERE shop_id=$1', [sk]);
  return run(
    "SELECT 'v3' AS source, watermark, last_run_at FROM catalog_branch_state WHERE shop_id=$1 AND branch_id=$2",
    [shopOf(sk), branchOf(sk)]
  );
}

async function saveState(c, sk, fmt, wm, at) {
  if (!isBranch(sk)) {
    await c.query(
      `INSERT INTO catalog_sync_state (shop_id, source, watermark, last_run_at) VALUES ($1,$2,$3,$4)
       ON CONFLICT (shop_id) DO UPDATE SET source=excluded.source, watermark=excluded.watermark, last_run_at=excluded.last_run_at`,
      [sk, fmt, wm, at]
    );
    return;
  }
  await c.query(
    `INSERT INTO catalog_branch_state (shop_id, branch_id, watermark, last_run_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (shop_id, branch_id) DO UPDATE SET watermark=excluded.watermark, last_run_at=excluded.last_run_at`,
    [shopOf(sk), branchOf(sk), wm, at]
  );
}

/** نگه‌داشتهٔ فعالِ سفارشِ آنلاین (فقط اصلی) ⇒ Map<pid, milli> */
async function heldOf(sk, ids) {
  if (isBranch(sk)) return new Map();
  return require('./stock-reserve').heldOf(null, sk, ids);
}

module.exports = { isBranch, shopOf, branchOf, fmtOf, tablesOf, stateOf, saveState, heldOf };
