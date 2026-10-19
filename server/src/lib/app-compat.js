'use strict';
/**
 * حداقل نسخهٔ پشتیبانی‌شدهٔ هر برنامه — v3 §۳۸.۹.
 *
 * تا امروز `min_app_version` در `/api/config` گفته می‌شد، ولی (۱) هیچ راهی
 * در پنل برای گذاشتنش نبود، (۲) یکی بود برای دکان و پمپ که شمارهٔ نسخه‌شان
 * هیچ ربطی به هم ندارد، و (۳) هیچ برنامه‌ای آن را نمی‌سنجید. حالا هر برنامه
 * کلیدِ خودش را دارد (`min_app_version_<app>`) و کلیدِ قدیمیِ سراسری فقط
 * پشتوانه است.
 *
 * ⛔ خالی ⇒ «حداقلی نیست» و رفتار همان پیشین است. سرور با این عدد هیچ
 *    درخواستی را رد نمی‌کند؛ فقط به برنامه می‌گوید و برنامه صادقانه می‌گوید
 *    «این نسخه دیگر پشتیبانی نمی‌شود». داده هرگز قفل نمی‌شود (D-05).
 */
const VERSION_RE = /^\d+(\.\d+){1,3}$/;

const valid = (v) => {
  const s = String(v ?? '').trim().replace(/^v/i, '');
  return VERSION_RE.test(s) ? s : '';
};

/** مقایسهٔ عددی: ۲٫۱۰ تازه‌تر از ۲٫۹ است. */
function compare(a, b) {
  const x = String(a).split('.').map(Number);
  const y = String(b).split('.').map(Number);
  for (let i = 0; i < Math.max(x.length, y.length); i++) {
    const d = (x[i] || 0) - (y[i] || 0);
    if (d) return d > 0 ? 1 : -1;
  }
  return 0;
}

/** حداقل نسخهٔ این برنامه از `app_config`؛ نادرست یا نبود ⇒ ''. */
function minVersionOf(cfg, app) {
  return valid(cfg?.[`min_app_version_${app}`]) || valid(cfg?.min_app_version);
}

module.exports = { VERSION_RE, valid, compare, minVersionOf };
