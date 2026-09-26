'use strict';
/**
 * ══ رباتِ دورهٔ آزمایشی — هر پمپ، همیشه ════════════════════════════════
 *
 * خواستهٔ صریحِ صاحبِ سامانه (۱۴۰۵/۰۷/۱۴): «یک ربات بزار که هر حساب رو
 * چک کنه؛ اگه نداشت براش آزمایشیِ یک ماه داده بشه — برای هر حسابِ جدید،
 * یا قدیمی‌ها اگه توی تاریخچه‌شون نبود.»
 *
 * همان قاعدهٔ مهاجرتِ ۰۳۲، و **فقط** همان — ولی نه یک بار: سرِ بالا آمدنِ
 * سرور و هر شش ساعت (`app.js` ⇒ housekeeping). هر پمپی که هنوز آغازِ
 * دوره ندارد (`trial_started_at IS NULL`) مُهر می‌خورد:
 *   • دوره‌اش بی‌آن‌که دیده شود گذشته و **هیچ** اشتراکِ غیرِ pending
 *     نداشته ⇒ سی روزِ تازه از همین حالا؛
 *   • وگرنه ⇒ همان روزِ ساخته شدن (دست نمی‌خورد).
 *
 * ⛔ هرگز مُهری را عوض نمی‌کند — فقط خالی را پر می‌کند؛ پس دوره هیچ‌وقت
 * خودبه‌خود دوباره باز نمی‌شود و «حذف و نصبِ دوباره» آزمایشیِ تازه نمی‌دهد.
 * ⛔ و هیچ ورودی‌ای از برنامه نمی‌گیرد.
 */
const { query } = require('../db');

const SWEEP = `
UPDATE stations s
   SET trial_started_at = CASE
     WHEN s.created_at + COALESCE(
            (SELECT NULLIF(value, '')::bigint FROM app_config WHERE key = 'pump_trial_days'), 30
          ) * 86400000 < (EXTRACT(EPOCH FROM now()) * 1000)::bigint
      AND NOT EXISTS (
            SELECT 1 FROM station_subscriptions x
             WHERE x.station_id = s.id AND x.status <> 'pending')
     THEN (EXTRACT(EPOCH FROM now()) * 1000)::bigint
     ELSE s.created_at
   END
 WHERE s.trial_started_at IS NULL`;

/** @returns {Promise<number>} شمارِ پمپ‌هایی که مُهر خوردند */
async function sweep() {
  const r = await query(SWEEP);
  const n = Number(r?.rowCount || 0);
  if (n > 0) console.log(`[trial-sweep] ${n} پمپ آغازِ دورهٔ آزمایشی گرفت`);
  return n;
}

module.exports = { sweep, SWEEP };
