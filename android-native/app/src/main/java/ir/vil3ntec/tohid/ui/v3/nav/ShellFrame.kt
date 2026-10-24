package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 *  قراردادِ پایینِ صفحه در پوستهٔ دکان — خالص و آزمون‌شده (`ShellFrameTest`).
 *
 *  • نوارِ شناورِ گوشی فقط روی **ریشهٔ تب‌ها**ی خودِ نوار است (REBUILD-SPEC اصلِ ۶؛ AUDIT-NAV S2):
 *    صفحهٔ درونی سربرگِ نسخهٔ ۳ با برگشت دارد و نوار ندارد. برگه روی ریشهٔ تب نوار را نمی‌برد
 *    (`page` همان صفحهٔ زیرِ برگه‌هاست). صندوق و ویرایشگرِ نوار هرگز نوار ندارند.
 *  • پوسته پایینِ **هر** صفحه را خودش نگه می‌دارد (AUDIT-NAV S1): جای نوار + نوارِ سیستم
 *    وقتی نوار هست، فقط نوارِ سیستم وقتی نیست؛ خانه عمداً زیرِ نوار می‌کشد و خودش فاصله
 *    می‌گذارد (`bottomReserve`). صفحه‌ها نوارِ سیستم را دوباره نمی‌شمارند (`LocalV3SystemBottom = 0`).
 */
object ShellFrame {
  /** صفحه‌هایی که نوارِ پایینِ گوشی رویشان نمی‌آید (صندوق نوارِ سبدِ خودش را دارد) */
  val NO_NAV: Set<String> = setOf("sale", "sale/quick", "settings/general/navbar")

  /** خانه زیرِ نوار می‌کشد و خودش فاصله می‌گذارد */
  const val UNDER_BAR = "home"

  /**
   *  @param page مسیرِ (بی پرسشِ) صفحهٔ زیرِ برگه‌ها
   *  @param barTabs مسیرِ ریشه‌های نوارِ همین کاربر
   */
  fun navVisible(phone: Boolean, page: String, barTabs: Set<String>, gate: Boolean, cartOpen: Boolean): Boolean =
    phone && page in barTabs && page !in NO_NAV && !gate && !cartOpen

  fun pageBottom(phone: Boolean, navVisible: Boolean, page: String, navReserve: Dp, systemBottom: Dp): Dp = when {
    !phone -> systemBottom
    navVisible && page == UNDER_BAR -> 0.dp
    navVisible -> navReserve + systemBottom
    else -> systemBottom
  }
}
