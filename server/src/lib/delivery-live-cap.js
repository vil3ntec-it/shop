'use strict';
/**
 * DLV-03: سقفِ کارِ هم‌زمانِ هر پیک — مهاجرتِ ۱۴۸ (`shop_couriers.max_live`).
 *
 *   NULL = بی‌سقف (همهٔ ردیف‌های پیشین ⇒ رفتارِ امروز). مأموریتِ زنده همان شمارشِ `cashStats` ِ `delivery.js`.
 *   • `parse` — مقدارِ ثبت/ویرایشِ پیک: `null`/'' = بی‌سقف، وگرنه عددِ صحیحِ ۱ تا ۵۰ (همان CHECK ِ مهاجرت).
 *   • `view`  — مقدارِ ردیف برای پاسخ (`maxLive`).
 *   • `check` — هنگامِ سپردن، زیرِ همان قفلِ ردیفِ پیک (`FOR UPDATE` در `delivery.assign`)، پس دو سپردنِ
 *     هم‌زمان از سقف بیرون نمی‌زنند. پیک به سقف رسیده ⇒ ۴۰۹ `courier_busy`، مگر مدیر آگاهانه
 *     `overrideLiveCap: true` بفرستد؛ آن‌گاه `true` برمی‌گردد و در رویدادِ «created» ثبت می‌شود.
 *
 * جدا از `delivery.js`، تا آن فایل از سقفِ اندازه‌اش در `tools/quality/baseline.json` بزرگ‌تر نشود.
 */
const { badRequest, conflict } = require('../middleware/errors');

const MAX_LIVE_LIMIT = 50;
const FIELD = 'سقفِ کارِ هم‌زمان';

/** `null`/'' ⇒ بی‌سقف؛ وگرنه همان پیام‌های `int()` ِ `delivery.js` (`bad_number`). */
function parse(v) {
  if (v === null || v === '') return null;
  if (typeof v !== 'number' || !Number.isSafeInteger(v)) throw badRequest(`${FIELD} باید عددِ صحیح باشد`, 'bad_number');
  if (v < 1 || v > MAX_LIVE_LIMIT) throw badRequest(`${FIELD} در بازهٔ مجاز نیست`, 'bad_number');
  return v;
}

/** مقدارِ ردیفِ `shop_couriers` برای پاسخ. */
const view = (row) => (row.max_live === null || row.max_live === undefined ? null : Number(row.max_live));

/** پیک به سقف رسیده؟ بی `override` ⇒ ۴۰۹؛ با آن ⇒ `true` (برای ثبت در رویداد). */
function check(courierRow, stats, override) {
  const cap = view(courierRow);
  const over = cap !== null && Number(stats.live || 0) >= cap;
  if (over && override !== true) {
    throw conflict(`این پیک ${cap} مأموریتِ هم‌زمان دارد (سقفش)؛ پیکِ دیگری بسپارید`, 'courier_busy');
  }
  return over;
}

module.exports = { MAX_LIVE_LIMIT, parse, view, check };
