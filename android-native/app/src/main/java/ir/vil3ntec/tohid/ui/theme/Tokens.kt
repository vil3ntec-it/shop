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
  //  کامپیوتر: گوشه‌های کم‌گرد — شرح سرِ `ShopShapes`
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
 *  `Shape`، `Radius` و `ShopShapes` همه از این‌جا می‌خوانند. هر نقش دو عدد دارد:
 *  کامپیوتر (۴ تا ۱۲ پیکسل، RULES «ظاهرِ کامپیوتری») و لمسی (گوشی و تبلت).
 *  `DesignTokensTest` بازه‌ها را می‌پاید و عددهای پیش از یکی شدن را نگه می‌دارد.
 */
object Corner {
  class Token(val name: String, val desk: Dp, val touch: Dp) {
    /** عددِ همین برنامه — `Platform.DESKTOP` ثابت است */
    val current: Dp get() = if (ir.vil3ntec.tohid.ui.Platform.DESKTOP) desk else touch
  }

  //  نقش‌ها (`Shape`)
  val button = Token("button", 6.dp, 18.dp)
  val field = Token("field", 6.dp, 16.dp)
  val card = Token("card", 8.dp, 20.dp)
  val cardLarge = Token("cardLarge", 10.dp, 24.dp)
  /** پنجره و برگهٔ پایین */
  val dialog = Token("dialog", 10.dp, 28.dp)
  val icon = Token("icon", 6.dp, 12.dp)

  //  پنل‌ها (`Radius`) — لمسی‌اش همان `--radius-sm/md/lg`ِ وب است
  val panelSm = Token("panelSm", 6.dp, 16.dp)
  val panelMd = Token("panelMd", 8.dp, 20.dp)
  val panelLg = Token("panelLg", 12.dp, 28.dp)

  //  اجزای آمادهٔ متریال (`ShopShapes`)
  val extraSmall = Token("extraSmall", 4.dp, 12.dp)
  val small = Token("small", 6.dp, 16.dp)
  val medium = Token("medium", 8.dp, 18.dp)
  val large = Token("large", 10.dp, 20.dp)
  val extraLarge = Token("extraLarge", 12.dp, 28.dp)

  val ALL = listOf(button, field, card, cardLarge, dialog, icon, panelSm, panelMd, panelLg, extraSmall, small, medium, large, extraLarge)
}

/**
 *  سایه — کم و کم‌تر.
 *
 *  در صفحه‌ای که پر از کارت است، سایهٔ زیاد همه‌چیز را شناور و شلوغ نشان
 *  می‌دهد. جداکردنِ کارت‌ها کارِ حاشیه و رنگِ زمینه است، نه سایه؛ سایه
 *  فقط برای چیزی که واقعاً روی بقیه است — نوارِ سبد، شیت، منو.
 */
object Elevation {
  val flat = 0.dp
  val raised = 1.dp
  val floating = 6.dp
  val overlay = 10.dp
}
