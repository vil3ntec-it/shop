package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.ShopColors

/**
 *  «رنگِ همراه» ِ هر میان‌برِ خانه (THEME-V2): هر کدام رنگِ ثابتِ خودش، آیکنِ رنگی روی
 *  «پس‌زمینهٔ نرمِ» همان رنگ. رنگ‌ها از تمِ جاری (`ShopColors.accents`، در هر سه تم یکی) و
 *  «فروش» primary ِ همان تم — هیچ رنگِ خامی این‌جا نیست.
 *
 *  @property index جایِ رنگ در `ThemeAccents.ALL` (آسمانی، بنفش، مرجانی، کهربایی، نعنایی،
 *    صورتی، لیمویی)؛ `null` = primary ِ تم
 */
enum class HomeAccent(val index: Int?) { PRIMARY(null), SKY(0), VIOLET(1), CORAL(2), AMBER(3), MINT(4), PINK(5), LIME(6) }

object HomeAccents {
  fun color(s: ShopColors, a: HomeAccent): Color = a.index?.let(s::accent) ?: s.brandPrimary

  /** پس‌زمینهٔ نرم: همان رنگ با آلفای ۱۲٪ (روشن) / ۲۰٪ (تیره) */
  fun soft(s: ShopColors, a: HomeAccent): Color =
    a.index?.let(s::accentSoft) ?: s.brandPrimary.copy(alpha = if (s.isDark) 0.20f else 0.12f)

  /** میان‌برها: فروش آبی، دریافت نعنایی، ورودِ کالا کهربایی، تاریخچه بنفش (THEME-V2) */
  val NEW_SALE = HomeAccent.PRIMARY
  val RECEIVE = HomeAccent.MINT
  val STOCK_ENTRY = HomeAccent.AMBER
  val HISTORY = HomeAccent.VIOLET
}
