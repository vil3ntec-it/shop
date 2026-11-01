package ir.vil3ntec.tohid.core.pos

/**
 *  حالتِ لمسیِ کامل در کامپیوتر (POS-31، §۵.۳) — چه وقت صندوقِ کامپیوتر لمسی است و
 *  دکمه‌های «صفحهٔ بعد / قبل» ِ هر فهرست به کجا می‌برند.
 *
 *  ── پیش‌فرض خاموش ─────────────────────────────────────────────────
 *  تنظیمِ همین دستگاه ([on]، پیش‌فرض خاموش): خاموش ⇒ کامپیوتر همان صندوقِ موشی و کلیدیِ امروز
 *  (هدفِ ۳۶، بی کلیدِ حالتِ لمسی، بی صفحه‌کلیدِ عددی و بی دکمهٔ صفحه). گوشی و تبلت همیشه لمسی‌اند،
 *  مثلِ امروز. روشن ⇒ کلیدِ «حالتِ لمسی» در نوارِ بالای صندوق دیده می‌شود و [active] (انتخابِ همان
 *  کلید، برای همین دستگاه) لمسی بودن را تعیین می‌کند.
 *
 *  ⚠️ Compose Desktop در ویندوز لمس را رویدادِ موس می‌دهد (§۵.۳)؛ تشخیصِ خودکارِ صفحهٔ لمسی
 *  ممکن نیست و انتخاب با همین کلید است. کشیدنِ انگشت فهرست را اسکرول نمی‌کند، پس هر فهرستِ
 *  صندوق در حالتِ لمسی دکمهٔ صفحه دارد ([page]).
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object TouchMode {

  /** کمینهٔ هدفِ لمس و ارتفاعِ ردیف در حالتِ لمسی (§۵.۳) */
  const val TOUCH_TARGET_DP = 48

  /** فاصلهٔ کمینه میانِ دکمه‌های خطرناک در حالتِ لمسی (§۵.۳) */
  const val DANGER_GAP_DP = 8

  /** صندوق لمسی است؟ گوشی/تبلت همیشه؛ کامپیوتر فقط با تنظیمِ روشن و کلیدِ روشن */
  fun touch(desktop: Boolean, on: Boolean, active: Boolean): Boolean = !desktop || (on && active)

  /** کلیدِ «حالتِ لمسی» در نوارِ بالای صندوقِ کامپیوتر — فقط با تنظیمِ روشن */
  fun showToggle(desktop: Boolean, on: Boolean): Boolean = desktop && on

  /**
   *  صفحه‌کلیدِ عددیِ روی صفحه و دکمه‌های صفحه — فقط کامپیوترِ لمسی. گوشی صفحه‌کلیدِ عددیِ
   *  دستگاه و کشیدنِ انگشت را دارد و صندوقِ گوشی دست نمی‌خورد.
   */
  fun touchExtras(desktop: Boolean, on: Boolean, active: Boolean): Boolean = desktop && on && active

  /**
   *  نخستین ردیفِ صفحهٔ بعد ([forward]) یا قبل در فهرستی با [total] ردیف که از [first] شروع
   *  شده و [visible] ردیفش پیداست. یک صفحه = همان شمارِ ردیفِ پیدا (کمینه یک).
   */
  fun page(first: Int, visible: Int, total: Int, forward: Boolean): Int {
    if (total <= 0) return 0
    val step = visible.coerceAtLeast(1)
    val start = first.coerceIn(0, total - 1)
    val next = if (forward) start + step else start - step
    return next.coerceIn(0, total - 1)
  }

  /** دکمهٔ صفحه کاری دارد؟ (آخرِ فهرست پیداست ⇒ «بعد» خاموش؛ آغاز ⇒ «قبل» خاموش) */
  fun canPage(first: Int, visible: Int, total: Int, forward: Boolean): Boolean =
    if (forward) first + visible.coerceAtLeast(1) < total else first > 0
}
