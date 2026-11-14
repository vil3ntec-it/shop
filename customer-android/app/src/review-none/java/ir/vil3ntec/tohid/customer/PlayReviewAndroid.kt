package ir.vil3ntec.tohid.customer

import android.app.Activity

/**
 *  RATE-01 — همتای بی‌کتابخانهٔ `src/review-play` (هر ساختِ برنامهٔ خرید جز `-Pstore=play`). ⛔ هیچ کدِ Google Play این‌جا
 *  نیست (`PlayReviewSplitTest`)؛ این ساخت‌ها هرگز برگه را نمی‌سازند (`RateAndroid.hook` تهی).
 */
object PlayReview {
  fun launch(activity: Activity, done: (shown: Boolean) -> Unit) = done(false)
}
