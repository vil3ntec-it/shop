package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 *  نشانه‌های طراحی — فاصله، گِردی و سایه.
 *
 *  هر عددی که در رابط کاربری تکرار می‌شود باید یک اسم داشته باشد، وگرنه
 *  هر صفحه کم‌کم مقیاسِ خودش را می‌سازد و برنامه تکه‌تکه به نظر می‌رسد.
 *  این‌ها همان مقیاسی‌اند که برای همهٔ صفحه‌ها تصمیم گرفته شده.
 */

/** فاصله‌ها — ۴، ۸، ۱۲، ۱۶، ۲۰، ۲۴، ۳۲ و بس */
object Space {
  val xxs = 4.dp
  val xs = 8.dp
  val sm = 12.dp
  val md = 16.dp
  val lg = 20.dp
  val xl = 24.dp
  val xxl = 32.dp
}

/**
 *  گِردیِ گوشه‌ها، به تفکیکِ نوعِ عنصر.
 *
 *  کادر و شیت عمداً گِردتر از دکمه‌اند: چیزی که از لبهٔ صفحه بالا می‌آید
 *  اگر گوشهٔ تیز داشته باشد، به کلِ صفحه چسبیده به نظر می‌رسد.
 */
object Shape {
  //  VILL3N: دکمه و کادر ۱۰، کارت ۱۲، کارتِ ویژه/پنجره ۱۶ — جدولِ `Corner`
  val button = RoundedCornerShape(Corner.button.current)
  val field = RoundedCornerShape(Corner.field.current)
  val card = RoundedCornerShape(Corner.card.current)
  val cardLarge = RoundedCornerShape(Corner.cardLarge.current)
  val dialog = RoundedCornerShape(Corner.dialog.current)
  val sheet = RoundedCornerShape(topStart = Corner.dialog.current, topEnd = Corner.dialog.current)
  /** تراشه کاملاً قرصی است — نه مستطیلِ گردگوشه */
  val chip = RoundedCornerShape(999.dp)
  val badge = RoundedCornerShape(999.dp)
  /** ظرفِ گردِ آیکن‌ها در ردیف‌ها */
  val icon = RoundedCornerShape(Corner.icon.current)
}

/**
 *  جدولِ گِردی — تنها جایی که عددِ گوشه نوشته می‌شود (MASTER-SPEC ۵.۴).
 *
 *  **VILL3N (۱۴۰۵/۰۷/۲۸، دستورِ صاحب محصول، §۱۱):** دکمه و کادرِ ورودی ۱۰، کارت ۱۲،
 *  کارتِ ویژه و پنل/پنجره ۱۶، تراشه و نشان قرصیِ کامل (`Shape.chip`). یک پله برای
 *  کامپیوتر، گوشی و وب. پیش از این (نسخهٔ ۲): ۱۲/۱۶/۲۰؛ نسخهٔ ۱: کامپیوتر ۴ تا ۱۲ و
 *  گوشی ۱۶ تا ۲۸. `DesignTokensTest` همین عددها را می‌پاید و `WebPaletteParityTest`
 *  `--radius-*`ِ وب را. (گوشیِ نسخهٔ ۳ گِردیِ بزرگ‌ترِ تأییدشدهٔ خودش را برای شیت و
 *  کارتِ پروفایل نگه می‌دارد — `PhoneV3.kt`.)
 */
object Corner {
  class Token(val name: String, val desk: Dp, val touch: Dp) {
    /** عددِ همین برنامه — `Platform.DESKTOP` ثابت است */
    val current: Dp get() = if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) desk else touch
  }

  /** کنترل‌ها: دکمه، کادرِ ورودی، زبانه، شمارنده */
  val CONTROL = 10.dp
  /** کارت‌ها و کاشی‌ها */
  val CARD = 12.dp
  /** کارتِ ویژه، پنلِ بزرگ و پنجره */
  val PANEL = 16.dp

  //  نقش‌ها (`Shape`)
  val button = Token("button", CONTROL, CONTROL)
  val field = Token("field", CONTROL, CONTROL)
  val card = Token("card", CARD, CARD)
  val cardLarge = Token("cardLarge", PANEL, PANEL)
  /** پنجره و برگهٔ پایین */
  val dialog = Token("dialog", PANEL, PANEL)
  val icon = Token("icon", CONTROL, CONTROL)

  //  پنل‌ها (`Radius`) — همان `--radius-sm/md/lg`ِ وب
  val panelSm = Token("panelSm", CONTROL, CONTROL)
  val panelMd = Token("panelMd", CARD, CARD)
  val panelLg = Token("panelLg", PANEL, PANEL)

  //  اجزای آمادهٔ متریال (`ShopShapes`): کادرِ متن و منو از extraSmall، کارت از medium، پنجره از extraLarge.
  //  `small` (تراشه‌های متریال) عدد نیست: قرصیِ کامل، همان `Shape.chip`
  val extraSmall = Token("extraSmall", CONTROL, CONTROL)
  val medium = Token("medium", CARD, CARD)
  val large = Token("large", CARD, CARD)
  val extraLarge = Token("extraLarge", PANEL, PANEL)

  val ALL = listOf(button, field, card, cardLarge, dialog, icon, panelSm, panelMd, panelLg, extraSmall, medium, large, extraLarge)
}

/**
 *  سایه.
 *
 *  VILL3N §۱۱: سایهٔ کارت سبک است (`0 3px 12px rgba(15,23,42,.06)`) و کارتِ ویژه
 *  کمی بیشتر (`0 8px 24px rgba(15,23,42,.09)`). سایه **جای خطِ دور را نمی‌گیرد**:
 *  کارت همیشه `borderCard` دارد (`softCard`). رنگِ سایه `ShopColors.shadow`.
 *  پیش از این (نسخهٔ ۲): کارت ۶ و شناور ۱۴ با خطِ مویی.
 */
object Elevation {
  val flat = 0.dp
  val raised = 1.dp
  val floating = 6.dp
  val overlay = 10.dp
  /** سایهٔ سبکِ کارت (`softCard`) */
  val card = 3.dp
  /** کارتِ ویژه، نوارِ شناور، منوی باز، سبد */
  val lifted = 8.dp
}
