package ir.vil3ntec.tohid.core.i18n

/**
 *  صفحه‌خوان (TalkBack، VoiceOver، Narrator) در رابطِ نسخهٔ ۳: حالتِ «باز/بسته» ِ ردیف‌ها و کادرهای تاشو، نامِ کارِ
 *  نگه‌داشتن، نامِ QR ِ پرداخت و شمارِ «نیاز به توجه» ِ تب‌ها (ممیزیِ دسترس‌پذیری، WCAG 2.1 AA). فقط برای صفحه‌خوان
 *  است و روی صفحه دیده نمی‌شود. فارسی، انگلیسی و پشتو؛ دری = فارسی + لایهٔ `DariText`؛ هر مقدار از `asIs` می‌گذرد.
 */
@Suppress("unused")
object StrA11y {

  /** حالتِ کادر یا ردیفِ تاشو وقتی باز است */
  val expanded: String get() = t("باز", "Expanded", "پرانیستی")

  /** حالتِ کادر یا ردیفِ تاشو وقتی بسته است */
  val collapsed: String get() = t("بسته", "Collapsed", "تړلی")

  /** نامِ کارِ «نگه‌داشتن» ِ ردیف (برگهٔ کارها) */
  val moreActions: String get() = t("کارهای بیشتر", "More actions", "نورې کړنې")

  /** کادرِ QR ِ پرداخت در صندوق */
  val payQr: String get() = t("کدِ QR ِ پرداخت", "Payment QR code", "د ورکړې QR کوډ")

  /** تبی که شمارش کارِ فوری است («تازه، ۳، نیاز به توجه») — رنگ و نقطهٔ شمار برای صفحه‌خوان نوشته می‌شوند */
  fun tabNeedsAttention(tab: Any, n: Any): String =
    t("${asIs(tab)}، ${asIs(n)}، نیاز به توجه", "$tab, $n, needs attention", "$tab، $n، پاملرنې ته اړتیا")
}
