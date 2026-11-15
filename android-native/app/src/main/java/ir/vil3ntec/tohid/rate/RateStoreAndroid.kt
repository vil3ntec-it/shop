package ir.vil3ntec.tohid.rate

import android.content.Context
import android.content.Intent
import android.net.Uri
import ir.vil3ntec.tohid.core.rate.RateAction
import ir.vil3ntec.tohid.core.rate.RateLink
import ir.vil3ntec.tohid.core.rate.RateLinks
import ir.vil3ntec.tohid.core.rate.RateTarget

/**
 *  RATE-01 — باز کردنِ فروشگاهِ همین ساخت پس از «ستاره می‌دهم» (قاعده‌ها: `core/rate/RatePrompt.kt`).
 *
 *   • Play: صفحهٔ برنامه (`market://details?id=` و بی برنامهٔ Play، `https://play.google.com/store/apps/details?id=`).
 *     ⛔ راهنمای Google: دکمه API ِ نظرِ درون‌برنامه را صدا نمی‌زند (سهمیه ⇒ شاید هیچ کارتی نیاید)؛ پس هیچ کتابخانهٔ Play
 *     در هیچ ساختی نیست (`PlayReviewSplitTest`).
 *   • کافه‌بازار: درِ رسمیِ نظر و امتیاز (`ACTION_EDIT` روی `bazaar://details?id=` با بستهٔ `com.farsitel.bazaar`)، و صفحهٔ برنامه.
 *   • سایت/گیت‌هاب، پیش‌نمایش و کامپیوتر: [target] همیشه `NONE` ⇒ برگه هرگز ساخته نمی‌شود.
 *  هیچ سنجه‌ای از اینکه چه کسی ستاره داد ثبت یا فرستاده نمی‌شود.
 */
object RateStoreAndroid {
  /** تنظیمِ رابطِ همین دستگاه (`tohid-ui`، DATA-SAFETY §۵ «مالِ همین دستگاه»): نه دفتر، نه همگام، نه مالی */
  const val PREFS = "tohid-ui"
  const val KEY = "rate_prompt"

  /** `SKStoreReviewController` ِ آیفون — میزبانِ آیفونِ دکان هنوز نمی‌نشاند ⇒ آن‌جا هم `NONE` */
  var appStoreReview: (() -> Unit)? = null

  /** فروشگاهِ همین ساخت؛ کامپیوتر و ساختِ بی فروشگاه `NONE` */
  fun target(store: String, preview: Boolean, desktop: Boolean): RateTarget {
    if (desktop) return RateTarget.NONE
    val t = RateTarget.of(store, preview)
    return if (t == RateTarget.APP_STORE && appStoreReview == null) RateTarget.NONE else t
  }

  fun open(context: Context, target: RateTarget) {
    val app = context.packageName
    when (target) {
      RateTarget.PLAY -> openFirst(context, RateLinks.playListing(app))
      RateTarget.BAZAAR -> openFirst(context, RateLinks.bazaar(app))
      RateTarget.APP_STORE -> appStoreReview?.invoke()
      RateTarget.NONE -> Unit
    }
  }

  /** نخستین درِ بازشدنی؛ هیچ فروشگاه و مرورگری ⇒ هیچ (هرگز از کار افتادن) */
  fun openFirst(context: Context, links: List<RateLink>): Boolean {
    for (l in links) {
      val intent = Intent(intentAction(l.action), Uri.parse(l.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      if (l.pkg != null) intent.setPackage(l.pkg)
      if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
  }

  /** `Intent.ACTION_EDIT` ِ کافه‌بازار — رشته، چون shim ِ کامپیوتر و آیفون آن ثابت را ندارند */
  fun intentAction(a: RateAction): String = if (a == RateAction.EDIT) "android.intent.action.EDIT" else Intent.ACTION_VIEW
}
