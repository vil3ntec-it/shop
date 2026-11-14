package ir.vil3ntec.tohid.core.i18n

/**
 *  حالت‌های مشترکِ صفحه (دستهٔ ۶۳، e-rows-63i): متنِ پیش‌فرضِ خطای ساده وقتی متنِ رسیده فنی بود
 *  (`PlainError` ⇒ null) — یک عنوان و یک خطِ «چه کنید» برای هر گونه (بی اینترنت، سرور، دیگر).
 *  تصویرها تزئینی‌اند و متن ندارند. فارسی، انگلیسی و پشتو؛ دری = فارسی + لایهٔ `DariText`.
 */
@Suppress("unused")
object StrStates {

  /** عنوانِ خطا وقتی عنوانِ رسیده فنی بود */
  val errorTitle: String get() = t("کار انجام نشد", "That didn't work", "کار ونه شو")

  /** عنوانِ «بی اینترنت» */
  val offlineTitle: String get() = t("به اینترنت وصل نیستید", "You're offline", "انټرنېټ ته نښلول شوي نه یاست")

  /** عنوانِ «سرور» */
  val serverTitle: String get() = t("سرور فعلاً جواب نمی‌دهد", "The server isn't answering", "سرور اوس ځواب نه ورکوي")

  /** «چه کنید» ِ بی اینترنت */
  val offlineHint: String get() = t(
    "اینترنتِ دستگاه را ببینید و «دوباره» را بزنید.",
    "Check this device's internet, then tap Retry.",
    "د دې وسیلې انټرنېټ وګورئ، بیا «بیا هڅه» ووهئ.",
  )

  /** «چه کنید» ِ سرور */
  val serverHint: String get() = t(
    "چند لحظهٔ دیگر «دوباره» را بزنید؛ کارهای شما روی همین دستگاه می‌ماند.",
    "Tap Retry in a moment — your work stays on this device.",
    "یو څو شېبې وروسته «بیا هڅه» ووهئ؛ ستاسو کار په همدې وسیله پاتې کېږي.",
  )

  /** «چه کنید» ِ دیگر */
  val otherHint: String get() = t(
    "«دوباره» را بزنید؛ اگر باز هم نشد، به پشتیبانی بگویید.",
    "Tap Retry; if it still doesn't work, contact support.",
    "«بیا هڅه» ووهئ؛ که بیا هم ونه شو، ملاتړ ته ووایاست.",
  )
}
