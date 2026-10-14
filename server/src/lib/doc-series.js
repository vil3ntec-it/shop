'use strict';
/**
 * «سری سند» هر دستگاه — F0-03 (فاز ۰؛ طرحِ کامل در ۳.۵ِ مشخصات).
 *
 * هر دستگاهِ یک دکان یک سریِ کوتاه و یکتا می‌گیرد: A1، B1، … Z1، A2، …
 * شمارهٔ نمایشیِ فاکتور `{سری}-{شماره}` می‌شود (مثلاً `B1-154`)، پس دو
 * دستگاه که هر دو به ۱۵۴ رسیده‌اند دیگر یک شماره نمی‌سازند.
 *
 *  - سری ثابت است: همان دستگاه همیشه همان را می‌گیرد (ردیف پاک نمی‌شود).
 *  - I و O نیستند (با ۱ و ۰ اشتباه می‌شوند)؛ W برای سفارشِ اینترنتی کنار
 *    گذاشته شده (۳.۵: «سفارش اینترنتی سری سرور W دارد»).
 *  - سرور هرگز خودِ شماره را عوض نمی‌کند؛ شماره‌های قبلی همان‌طور می‌مانند.
 */
const { tx, now } = require('../db');

const LETTERS = 'ABCDEFGHJKLMNPQRSTUVXYZ';

/** k امین سری (از صفر): A1 … Z1، A2 … */
function codeOf(k) {
  const n = Math.max(0, Math.floor(Number(k) || 0));
  return LETTERS[n % LETTERS.length] + String(Math.floor(n / LETTERS.length) + 1);
}

/**
 * سریِ این دستگاه در این دکان — اگر نداشته باشد، همین‌جا ساخته می‌شود.
 * @returns {Promise<{series: string, created: boolean}>}
 */
async function seriesFor(shopId, deviceId, userId = '') {
  return tx(async (c) => {
    //  قفلِ دکان: دو دستگاه که هم‌زمان می‌پیوندند یک سری نمی‌گیرند
    await c.query('SELECT id FROM shops WHERE id=$1 FOR UPDATE', [shopId]);
    const have = (await c.query(
      'SELECT series FROM shop_doc_series WHERE shop_id=$1 AND device_id=$2', [shopId, deviceId]
    )).rows[0];
    if (have) return { series: have.series, created: false };

    const used = Number((await c.query(
      'SELECT COUNT(*)::int AS n FROM shop_doc_series WHERE shop_id=$1', [shopId]
    )).rows[0].n) || 0;
    for (let k = used; k < used + 1000; k++) {
      const code = codeOf(k);
      const r = await c.query(
        `INSERT INTO shop_doc_series (shop_id, device_id, series, user_id, created_at)
         VALUES ($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING RETURNING series`,
        [shopId, deviceId, code, userId || '', now()]
      );
      if (r.rows[0]) return { series: code, created: true };
    }
    throw new Error('سریِ آزاد پیدا نشد');
  });
}

module.exports = { codeOf, seriesFor, LETTERS };
