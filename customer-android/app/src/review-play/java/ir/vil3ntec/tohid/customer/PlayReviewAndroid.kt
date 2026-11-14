package ir.vil3ntec.tohid.customer

import android.app.Activity
import android.os.SystemClock
import com.google.android.play.core.review.ReviewManagerFactory
import ir.vil3ntec.tohid.core.rate.RatePrompt

/**
 *  RATE-01 — نظرِ درون‌برنامهٔ Google Play ِ برنامهٔ خرید، **فقط** با `-Pstore=play` (`src/review-play` در `app/build.gradle.kts`)؛
 *  همان راهِ برنامهٔ دکان (`android-native/app/src/review-play`). `done(false)` ⇒ درخواست یا باز کردن نشد، یا زودتر از
 *  [RatePrompt.PLAY_QUICK_MS] تمام شد (سهمیهٔ Play) ⇒ `RateAndroid` صفحهٔ برنامه در Play را باز می‌کند.
 */
object PlayReview {
  fun launch(activity: Activity, done: (shown: Boolean) -> Unit) {
    val manager = runCatching { ReviewManagerFactory.create(activity) }.getOrElse { return done(false) }
    manager.requestReviewFlow().addOnCompleteListener { request ->
      if (!request.isSuccessful || activity.isFinishing) {
        done(false)
        return@addOnCompleteListener
      }
      val started = SystemClock.elapsedRealtime()
      runCatching { manager.launchReviewFlow(activity, request.result) }
        .onFailure { done(false) }
        .onSuccess { flow -> flow.addOnCompleteListener { done(SystemClock.elapsedRealtime() - started >= RatePrompt.PLAY_QUICK_MS) } }
    }
  }
}
