'use strict';
/**
 * رازِ سرور برای هش و مهرِ کدها — یک تابع به جای کپی‌های `pepper()`.
 *
 * هر ماژول (کدِ وی‌آی‌پی، کدِ یک‌بارمصرف، کدِ پیوستن، …) پیش‌تر تابعِ
 * خودش را داشت: «اولین رازِ پُرِ این فهرست، وگرنه این رشتهٔ ثابت». ترتیبِ
 * فهرست و رشتهٔ ثابت از ماژولی به ماژولِ دیگر فرق دارد، و همین فرق در
 * هشِ ذخیره‌شده نشسته است.
 *
 * ⛔ پس ترتیب و رشتهٔ ثابت را خودِ صدازننده می‌دهد، دقیقاً همان که بود.
 * عوض کردنِ هر کدام یعنی هیچ کد/هشِ ذخیره‌شده‌ای دیگر جور نمی‌آید.
 * `test/server-secret.test.js` خروجیِ هر صدازننده را با مقدارِ پیش از این
 * تغییر می‌سنجد.
 */
const config = require('../config');

/**
 * اولین رازِ پُر از `order` (نام‌های `config.secrets`)، وگرنه `fallback`.
 * در هر بار صدا خوانده می‌شود، نه هنگامِ بارگذاری.
 *
 * @param {ReadonlyArray<'api'|'jwt'|'otp'>} order
 * @param {string} fallback
 * @returns {string}
 */
function serverSecret(order, fallback) {
  //  همهٔ نام‌ها پیش از خواندن سنجیده می‌شوند: غلطِ تایپیِ نامِ دوم هم خطاست، حتی وقتی نامِ اول پُر است
  const unknown = order.filter((name) => !Object.hasOwn(config.secrets, name));
  if (unknown.length) throw new Error(`unknown server secret: ${unknown.join(', ')}`);
  for (const name of order) {
    //  رازِ فقط‌فاصله هم راز است (مثلِ `||`ِ کپی‌های پیشین) — trim نمی‌شود
    const value = config.secrets[name];
    if (value) return value;
  }
  return fallback;
}

module.exports = { serverSecret };
