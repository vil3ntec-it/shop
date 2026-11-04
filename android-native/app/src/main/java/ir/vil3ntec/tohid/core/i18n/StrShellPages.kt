package ir.vil3ntec.tohid.core.i18n

/**
 *  جزءهای مشترکِ قدیمی که نسخهٔ ۳ هنوز صدا می‌زند: کادر و دکمه‌های پایه (`ui/screens/Tohid.kt`)، فهرستِ موردهای نوارِ پایین
 *  (`NavCatalog`) و قرصِ «تمدیدِ اشتراک» (`PhoneNavBar`). این نوشته‌ها پیش‌تر متنِ خامِ فارسی بودند و از هیچ زبانی نمی‌گذشتند.
 *  فارسی کلمه‌به‌کلمه همان پیشین است؛ هر جا همان جمله در جدولِ دیگری بود، همان دوباره به کار رفته. قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrShellPages {
  // ── `Tohid.kt` ──
  val choosePlaceholder: String get() = t("— انتخاب کنید —", "— choose —", "— وټاکئ —")
  val tryAgain: String get() = t("تلاش دوباره", "Try again", "بیا هڅه")
  val loading: String get() = t("در حال بارگذاری", "Loading", "راځي")

  // ── `NavLayout.kt` ──

  // ── `PhoneNavBar.kt` ──
  val renewSubscription: String get() = t("تمدیدِ اشتراک", "Renew subscription", "ګډون نوي کول")
  val endsToday: String get() = t("امروز تمام می‌شود", "Ends today", "نن پای ته رسي")
  fun daysLeft(days: Any?): String = t("${asIs(days)} روز مانده", "$days days left", "$days ورځې پاتې")
}
