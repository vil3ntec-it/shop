package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 *  قراردادِ پایینِ صفحه در پوستهٔ دکان — خالص و آزمون‌شده (`ShellFrameTest`).
 *
 *  • کارِ یک‌دستی (صاحب‌کار ۱۴۰۵/۰۸/۲۳، جای REBUILD-SPEC اصلِ ۶): نوارِ شناورِ گوشی روی **هر بخشِ اصلی** است —
 *    ریشه‌های نوار و هر بخشی که از «همهٔ بخش‌ها»، نوار یا خانه باز می‌شود (`sections` = ورودی‌های ماژول‌ها)،
 *    صندوق هم. صفحهٔ درونی (جزئیات، فرم) سربرگِ نسخهٔ ۳ با برگشت دارد و نوار ندارد.
 *  • نوار فقط جایی که زیان دارد کنار می‌رود: دروازه، سبدِ بازِ صندوق، صفحه‌کلیدِ باز، فروشِ سریع و
 *    ویرایشگرِ نوار (`NO_NAV`)، و وقتی برگه‌ای روی صفحه است (تسویه و پرداخت، دوربینِ اسکن، هر برگهٔ دیگر):
 *    آن‌وقت نوار «پوشیده» است ([Bar.COVERED]) — کشیده نمی‌شود ولی جایش نگه داشته می‌شود تا صفحهٔ زیرِ
 *    برگه نپرد.
 *  • پوسته پایینِ **هر** صفحه را خودش نگه می‌دارد (AUDIT-NAV S1): جای نوار + نوارِ سیستم وقتی نوار
 *    هست، فقط نوارِ سیستم وقتی نیست؛ خانه عمداً زیرِ نوار می‌کشد و خودش فاصله می‌گذارد
 *    (`bottomReserve`). صفحه‌ها نه جای نوار را دوباره می‌شمارند نه نوارِ سیستم را (`LocalV3SystemBottom = 0`).
 */
object ShellFrame {
  /** صفحه‌هایی که نوارِ پایینِ گوشی رویشان نمی‌آید: فروشِ سریع (نوارِ سبدِ تمام‌قدِ خودش) و ویرایشگرِ نوار */
  val NO_NAV: Set<String> = setOf("sale/quick", ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths.NAVBAR)

  /** خانه زیرِ نوار می‌کشد و خودش فاصله می‌گذارد */
  const val UNDER_BAR = "home"

  /** حالِ نوارِ پایین روی یک صفحه */
  enum class Bar {
    /** کشیده می‌شود و جایش در پایینِ صفحه هست */
    SHOWN,
    /** برگه‌ای روی صفحه است: کشیده نمی‌شود، جایش می‌ماند (صفحهٔ زیرِ برگه نمی‌پرد) */
    COVERED,
    /** نیست و جایی هم نمی‌گیرد */
    NONE,
  }

  /**
   *  @param page مسیرِ (بی پرسشِ) صفحهٔ زیرِ برگه‌ها
   *  @param barTabs مسیرِ ریشه‌های نوارِ همین کاربر
   *  @param sections مسیرِ بخش‌های اصلی (`V3Modules.sections`)
   *  @param sheetOnTop برگه‌ای (`RouteKind.SHEET`: تسویه، پرداخت، اسکن، …) بالای صفحه است
   *  @param keyboard صفحه‌کلید باز است
   */
  fun bar(
    phone: Boolean,
    page: String,
    barTabs: Set<String>,
    sections: Set<String>,
    gate: Boolean,
    cartOpen: Boolean,
    sheetOnTop: Boolean,
    keyboard: Boolean,
  ): Bar = when {
    !phone || gate || cartOpen || keyboard -> Bar.NONE
    page in NO_NAV || (page !in barTabs && page !in sections) -> Bar.NONE
    sheetOnTop -> Bar.COVERED
    else -> Bar.SHOWN
  }

  /**
   *  دکمهٔ جست‌وجوی نوار روی این صفحه هست؟ (صفحه‌کلید، برگه و سبدِ باز فقط موقت می‌پوشانند و حساب نمی‌شوند) — ۶۳w:
   *  دکمهٔ نوار جست‌وجوی همان بخش را باز می‌کند (`V3ScopedSearch`)؛ نه ⇒ سربرگ ذره‌بینِ خودش را می‌گذارد تا جست‌وجو بی‌راه نماند.
   *  @param hasSearch چیدمانِ نوارِ همین کاربر دکمهٔ جست‌وجو دارد
   */
  fun navSearch(phone: Boolean, page: String, barTabs: Set<String>, sections: Set<String>, gate: Boolean, hasSearch: Boolean): Boolean =
    hasSearch && bar(phone, page, barTabs, sections, gate, cartOpen = false, sheetOnTop = false, keyboard = false) != Bar.NONE

  /** نوار کشیده می‌شود؟ */
  fun navVisible(bar: Bar): Boolean = bar == Bar.SHOWN

  fun pageBottom(phone: Boolean, bar: Bar, page: String, navReserve: Dp, systemBottom: Dp): Dp = when {
    !phone -> systemBottom
    bar != Bar.NONE && page == UNDER_BAR -> 0.dp
    bar != Bar.NONE -> navReserve + systemBottom
    else -> systemBottom
  }
}
