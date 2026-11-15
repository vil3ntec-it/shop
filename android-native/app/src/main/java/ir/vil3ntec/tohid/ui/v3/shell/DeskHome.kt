package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.data.ShopData

/**
 *  خانهٔ کامپیوتر (`home` با `LocalDeskLayout`). از e-desk-d1 (خواستِ صاحب‌کار ۱۴۰۵/۰۸/۲۴: «طراحیِ موبایل رو نمی‌تونی روی
 *  کامپیوتر بیاری») داشبوردِ ویژهٔ کامپیوتر است — `ui/desk/DeskDashboard.kt`: چهار کارتِ عددِ کلیدی، نمودارِ فروش و پرفروش‌ها،
 *  جدولِ تراکنش‌های امروز و کارهای سریع با میان‌بر. جای دو کادرِ گرادیانیِ گوشی‌گونه، چهار کاشیِ کوچک و فهرستِ «امروز» ِ پیشین؛
 *  همهٔ کارهای آن‌ها (گزارش، دریافت، فروشِ تازه، پرداخت، ورودِ کالا، تاریخچه، سفارش‌ها، «همه» ِ تراکنش‌ها، برگهٔ اقلام،
 *  کارهای فوری) در داشبورد هست. خانهٔ گوشی (`HomeScreen`) و پرونده‌های داشبوردِ گوشی دست نخوردند.
 */
@Composable
fun DeskHomeScreen(d: ShopData, onOpen: (String) -> Unit) {
  ir.vil3ntec.tohid.ui.desk.DeskDashboard(d, onOpen)
}
