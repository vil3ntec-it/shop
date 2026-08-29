package ir.vil3ntec.tohid.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
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
  val button = RoundedCornerShape(20.dp)
  val field = RoundedCornerShape(20.dp)
  val card = RoundedCornerShape(24.dp)
  val cardLarge = RoundedCornerShape(28.dp)
  val dialog = RoundedCornerShape(28.dp)
  val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val chip = RoundedCornerShape(16.dp)
  val badge = RoundedCornerShape(12.dp)
  /** ظرفِ گردِ آیکن‌ها در ردیف‌های تنظیمات */
  val icon = RoundedCornerShape(14.dp)
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
