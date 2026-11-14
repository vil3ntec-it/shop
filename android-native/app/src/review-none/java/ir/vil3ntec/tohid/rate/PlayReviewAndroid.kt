package ir.vil3ntec.tohid.rate

import android.content.Context

/**
 *  RATE-01 — همتای بی‌کتابخانهٔ `src/review-play` برای APK ِ سایت/گیت‌هاب و کافه‌بازار (هر `-Pstore` جز `play`).
 *  ⛔ هیچ کدِ Google Play این‌جا نیست (`PlayReviewSplitTest`). این ساخت‌ها هرگز `RateTarget.PLAY` نیستند؛ اگر روزی شد،
 *  `false` ⇒ همان صفحهٔ برنامه.
 */
object PlayReview {
  fun launch(context: Context, done: (shown: Boolean) -> Unit) = done(false)
}
