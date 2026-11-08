'use strict';
/**
 * پرچمِ دکانِ **پیش‌فرض روشن** — همان جدول و همان قاعدهٔ `orders.flagOf` (مقدارِ دکان بر `all` جلو؛ «غیرفعال
 * کردنِ سرویس» ِ `shop_holds` هر مقداری را می‌بُرد)، با یک فرق: نبودنِ ردیف یا مقدارِ نادرست ⇒ **روشن**؛ فقط `false` ِ
 * صریح خاموش است. برای رفعِ ایمنیِ داده‌ای که آزموده و روشن شده ولی هنوز باید خاموش‌شدنی بماند (۱۴۰۵/۰۸/۱۸):
 * `shop.ledger_merge` (برنامهٔ تازه) و `sync.pull_own_newer` (`docs/DATA-SAFETY-fa.md`).
 *
 * همان حافظهٔ کوتاهِ `hot-cache` (فضای `flag`، کلیدِ `<دکان>\0<کلید>\0d`)؛ هر نوشتنِ پرچم/نگه‌داشت همان دم باطلش می‌کند.
 */
const { many } = require('../db');

const decode = (text) => { try { return JSON.parse(text); } catch { return null; } };

async function read(shopId, key) {
  const rows = await many(
    `SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)
      UNION ALL
     SELECT '#hold', 'false' FROM shop_holds
      WHERE shop_id=$3 AND kind='service_disabled' AND service=$1 AND lifted_at IS NULL`,
    [key, 'all', String(shopId)],
  );
  if (rows.some((r) => r.scope === '#hold')) return false;
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v !== false;
}

/** روشن مگر `false` ِ صریح (دکان بر `all`) یا نگه‌داشتِ سرویس */
function defaultOn(shopId, key) {
  return require('./hot-cache').get('flag', `${String(shopId)}\u0000${key}\u0000d`, () => read(shopId, key));
}

module.exports = { defaultOn };
