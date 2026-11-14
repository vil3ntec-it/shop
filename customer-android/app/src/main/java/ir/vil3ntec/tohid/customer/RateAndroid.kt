package ir.vil3ntec.tohid.customer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import ir.vil3ntec.tohid.core.rate.RateAction
import ir.vil3ntec.tohid.core.rate.RateLinks
import ir.vil3ntec.tohid.core.rate.RateTarget
import ir.vil3ntec.tohid.customer.ui.RateHook

/**
 *  RATE-01 — مرزِ اندرویدِ «ستاره بدهید» ِ برنامهٔ خرید. برنامهٔ خرید فروشگاهِ بازار ندارد: فقط راهِ Play، و فقط در ساختِ
 *  `-Pstore=play`؛ هر ساختِ دیگر (پیش‌فرض) ⇒ `null` ⇒ برگه هرگز ساخته نمی‌شود. «ستاره می‌دهم» ⇒ نظرِ درون‌برنامهٔ Play
 *  ([PlayReview]، فقط در `src/review-play`)، و اگر نشد صفحهٔ برنامه (`market://` و سپس وبِ Play). هیچ سنجه‌ای فرستاده نمی‌شود.
 */
object RateAndroid {
  fun hook(activity: Activity): RateHook? {
    if (RateTarget.of(BuildConfig.STORE) != RateTarget.PLAY) return null
    return RateHook(RateTarget.PLAY) {
      PlayReview.launch(activity) { shown -> if (!shown) listing(activity) }
    }
  }

  private fun listing(activity: Activity) {
    for (l in RateLinks.playListing(activity.packageName)) {
      val intent = Intent(if (l.action == RateAction.EDIT) Intent.ACTION_EDIT else Intent.ACTION_VIEW, Uri.parse(l.uri))
      if (l.pkg != null) intent.setPackage(l.pkg)
      if (runCatching { activity.startActivity(intent) }.isSuccess) return
    }
  }
}
