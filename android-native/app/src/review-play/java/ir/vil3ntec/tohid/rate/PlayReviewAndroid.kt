package ir.vil3ntec.tohid.rate

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import com.google.android.play.core.review.ReviewManagerFactory
import ir.vil3ntec.tohid.core.rate.RatePrompt

/**
 *  RATE-01 — نظرِ درون‌برنامهٔ Google Play (`com.google.android.play:review`)، **فقط** در نسخهٔ Play (`-Pstore=play`؛
 *  `src/review-play` در `app/build.gradle.kts`). APK ِ سایت و بازار همتای بی‌کتابخانهٔ `src/review-none` را دارند.
 *
 *  Play خودش تصمیم می‌گیرد کارت را نشان دهد یا نه و هرگز نمی‌گوید کاربر چه کرد. `done(false)` ⇒ درخواست یا باز کردن نشد،
 *  یا کار زودتر از [RatePrompt.PLAY_QUICK_MS] تمام شد (سهمیهٔ Play: کارتی دیده نشد) ⇒ `RateStoreAndroid` صفحهٔ برنامه را
 *  باز می‌کند، تا «ستاره می‌دهم» هرگز بی‌اثر نماند.
 */
object PlayReview {
  fun launch(context: Context, done: (shown: Boolean) -> Unit) {
    val activity = context.activity() ?: return done(false)
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

  private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
  }
}
