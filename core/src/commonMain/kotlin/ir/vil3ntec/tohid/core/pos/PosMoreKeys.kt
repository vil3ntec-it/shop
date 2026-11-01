package ir.vil3ntec.tohid.core.pos

/**
 *  POS-41 — کلیدها و برچسب‌های ماندهٔ صندوقِ کامپیوتر (جدولِ ۵.۲)، پشتِ تنظیمِ دستگاهیِ
 *  **خاموشِ** «کلیدهای بیشترِ صندوق». خاموش ⇒ هر تابع `null`/`false` و صندوق بیت‌به‌بیت همان.
 *
 *   • Shift+F5 ⇒ کادرِ «تخفیفِ کلِ فاکتور» (`sale.discountInvoice`).
 *   • Esc در کادرِ اسکنِ **خالی** با سبدِ ناخالی ⇒ پرسشِ «خالی کردنِ فاکتور» با فوکوس روی «انصراف»
 *     (`sale.clear`: «پاک کردنِ کادر؛ خالی کردنِ فاکتور فقط با تأیید»). کادرِ نوشته‌دار همان «پاک
 *     کردنِ کادر» ِ امروز است؛ Esc هرگز بی پرسش چیزی پاک نمی‌کند.
 *   • برچسبِ کلید روی دکمه‌ها: F3 روی ردیفِ انتخاب‌شده، +/− و Delete کنارِ همان، Esc روی «خالی کردنِ
 *     سبد»، Shift+F5 کنارِ کادرِ تخفیف.
 *
 *  نامِ کلید این‌جا از `KeyName` ِ برنامه جداست (core به برنامه وابسته نیست): صفحه نگاشت می‌کند.
 *  Kotlin خالص است — بی java.*، بی android.*.
 */
object PosMoreKeys {

  enum class Key { F5, ESCAPE, OTHER }

  enum class Act { DISCOUNT_INVOICE, CLEAR_CART }

  /** پیرایه‌های فشرده‌شده؛ `primary` = Ctrl یا Cmd */
  data class Mods(val shift: Boolean = false, val primary: Boolean = false, val alt: Boolean = false)

  /** پیش‌نمایشِ کلید روی کلِ صفحه */
  fun screen(on: Boolean, key: Key, mods: Mods): Act? =
    if (on && key == Key.F5 && mods.shift && !mods.primary && !mods.alt) Act.DISCOUNT_INVOICE else null

  /** کادرِ اسکن: Esc ِ خالی ⇒ پرسشِ خالی کردن — فقط با سبدِ ناخالی و بی پیرایه */
  fun field(on: Boolean, key: Key, mods: Mods, queryEmpty: Boolean, cartEmpty: Boolean): Act? =
    if (on && key == Key.ESCAPE && mods == Mods() && queryEmpty && !cartEmpty) Act.CLEAR_CART else null

  /** برچسبِ کلیدِ هر دکمه دیده شود — همان تنظیم */
  fun hints(on: Boolean): Boolean = on
}
