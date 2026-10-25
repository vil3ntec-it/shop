package ir.vil3ntec.tohid

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.text.LocaleFormat

/**
 *  جانشینِ JVMِ `LocaleChoice`ِ برنامه، فقط برای ابزارِ انتقال.
 *
 *  نسخهٔ برنامه حالتِ Compose و `Context` دارد و این‌جا کامپایل نمی‌شود؛
 *  `Jalali.kt` فقط برای نامِ ماه به آن نگاه می‌کند. ابزار انتخابِ دکان را
 *  نمی‌داند، پس همان پیش‌فرض‌های برنامه را برمی‌گرداند (دری، رقمِ لاتین).
 */
object LocaleChoice {
  val digits: String = LocaleFormat.DIGITS_LATN
  val months: String = LocaleText.MONTHS_DARI
  val symbol: String = LocaleFormat.SYMBOL_WORD
  /** زبان و تقویمِ صفحه: ابزار همیشه فارسی و خورشیدی است (`displayDate` همان `formatDate`) */
  val lang: Lang = Lang.FA
  val calendar: String = I18n.CAL_AUTO
}
