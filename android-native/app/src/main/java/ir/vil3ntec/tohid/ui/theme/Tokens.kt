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
  //  نسخهٔ ۲: کنترل ۱۲، کارت ۱۶، پنل/پنجره ۲۰ — جدولِ `Corner`
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
 *  **نسخهٔ ۲ (۱۴۰۵/۰۷/۲۷، نقدِ صاحب محصول: «همهٔ جعبه‌ها چهارگوش و تیزند»).**
 *  یک پلهٔ گِردی برای کامپیوتر، گوشی، تبلت و وب: کنترل (دکمه، کادر، زبانه) ۱۲،
 *  کارت ۱۶، پنل/پنجره ۲۰، تراشه و نشان قرصیِ کامل (`Shape.chip`). نسخهٔ ۱ روی
 *  کامپیوتر ۴ تا ۱۲ و روی گوشی ۱۶ تا ۲۸ بود. `DesignTokensTest` همین عددها را
 *  می‌پاید و `WebPaletteParityTest` `--radius-*`ِ وب را.
 */
object Corner {
  class Token(val name: String, val desk: Dp, val touch: Dp) {
    /** عددِ همین برنامه — `Platform.DESKTOP` ثابت است */
    val current: Dp get() = if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) desk else touch
  }

  /** کنترل‌ها: دکمه، کادرِ ورودی، زبانه، شمارنده */
  val CONTROL = 12.dp
  /** کارت‌ها و کاشی‌ها */
  val CARD = 16.dp
  /** پنل‌های بزرگ و پنجره */
  val PANEL = 20.dp

  //  نقش‌ها (`Shape`)
  val button = Token("button", CONTROL, CONTROL)
  val field = Token("field", CONTROL, CONTROL)
  val card = Token("card", CARD, CARD)
  val cardLarge = Token("cardLarge", PANEL, PANEL)
  /** پنجره و برگهٔ پایین */
  val dialog = Token("dialog", PANEL, PANEL)
  val icon = Token("icon", 10.dp, 10.dp)

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
 *  نسخهٔ ۱ کارت‌ها را با خطِ دورِ تیره جدا می‌کرد و صاحب محصول دید «همه‌جا خط
 *  است». نسخهٔ ۲: کارت با سایهٔ **پهن و کم‌رنگ** (`card` با رنگِ `ShopColors.shadow`)
 *  از زمینه جدا می‌شود و خطِ دور فقط مویی است. چیزی که واقعاً روی بقیه است —
 *  نوارِ شناور، سبد، منو — `lifted`.
 */
object Elevation {
  val flat = 0.dp
  val raised = 1.dp
  val floating = 6.dp
  val overlay = 10.dp
  /** نسخهٔ ۲: سایهٔ نرمِ کارت (`softCard`) — پهن و کم‌رنگ، به‌جای خطِ دورِ تیره */
  val card = 6.dp
  /** نوارِ شناور، منوی باز، سبد */
  val lifted = 14.dp
}
