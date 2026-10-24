package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.ui.graphics.Color
import ir.vil3ntec.tohid.ui.theme.PhoneV3Colors
import ir.vil3ntec.tohid.ui.theme.PhoneV3Palette

/**
 *  «رنگِ همراه» ِ هر میان‌بر و هر بخشِ خانه (THEME-V2): هر کدام رنگِ ثابتِ خودش را دارد،
 *  آیکنِ رنگی روی «پس‌زمینهٔ نرمِ» همان رنگ — نه همه یک رنگ. نام‌ها همان رنگ‌های همراهِ
 *  THEME-V2اند؛ تا تمِ تازهٔ پایه برسد هر کدام به نزدیک‌ترین رنگِ امروزِ کیت (`PhoneV3Palette`)
 *  می‌رود و فقط `color` عوض می‌شود.
 */
enum class HomeAccent { PRIMARY, SKY, VIOLET, CORAL, AMBER, MINT, PINK, LIME }

object HomeAccents {
  fun color(c: PhoneV3Colors, a: HomeAccent): Color = PhoneV3Palette.glyph(
    c,
    when (a) {
      HomeAccent.PRIMARY -> PhoneV3Palette.Hue.BRAND
      HomeAccent.SKY -> PhoneV3Palette.Hue.BLUE
      HomeAccent.VIOLET, HomeAccent.PINK -> PhoneV3Palette.Hue.PURPLE
      HomeAccent.CORAL, HomeAccent.AMBER -> PhoneV3Palette.Hue.ORANGE
      HomeAccent.MINT, HomeAccent.LIME -> PhoneV3Palette.Hue.TEAL
    },
  )

  /** پس‌زمینهٔ نرم: همان رنگ با آلفای ۱۲٪ (روشن) / ۲۰٪ (تیره) */
  fun soft(c: PhoneV3Colors, a: HomeAccent, dark: Boolean): Color = color(c, a).copy(alpha = if (dark) 0.20f else 0.12f)

  /** میان‌برها: فروش آبی، دریافت نعنایی، ورودِ کالا کهربایی، تاریخچه بنفش (THEME-V2) */
  val NEW_SALE = HomeAccent.PRIMARY
  val RECEIVE = HomeAccent.MINT
  val STOCK_ENTRY = HomeAccent.AMBER
  val HISTORY = HomeAccent.VIOLET

  /** بخش‌ها (`HomeSections.Tile.id`) — هر کاشی رنگِ خودش، هشتمی («بیشتر») primary */
  fun ofTile(id: String): HomeAccent = when (id) {
    "products" -> HomeAccent.SKY
    "warehouse" -> HomeAccent.AMBER
    "debtors" -> HomeAccent.CORAL
    "expenses" -> HomeAccent.PINK
    "reports" -> HomeAccent.SKY
    "team" -> HomeAccent.LIME
    "orders" -> HomeAccent.CORAL
    "purchasing" -> HomeAccent.MINT
    "sales" -> HomeAccent.VIOLET
    "receipts" -> HomeAccent.LIME
    else -> HomeAccent.PRIMARY
  }
}
