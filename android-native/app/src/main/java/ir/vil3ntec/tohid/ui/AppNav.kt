package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.i18n.Str

/**
 *  ناوبری از بیرونِ صفحه‌ها — میان‌برهای صفحه‌کلیدِ برنامهٔ کامپیوتر.
 *
 *  پنجرهٔ کامپیوتر (`desktop/Main.kt`) با Ctrl+۱ تا ۹ این‌جا مقصد می‌گذارد و
 *  `AppRoot` همان را با `open()`ِ خودش باز می‌کند؛ پس همهٔ سدها (نقشِ شاگرد،
 *  قفلِ اشتراک) همان‌طور سرِ جایشان می‌مانند. روی گوشی کسی چیزی نمی‌گذارد.
 */
object AppNav {
  /** ترتیبِ میان‌برها: Ctrl+۱ خانه، Ctrl+۲ فروش، … */
  val SHORTCUTS: List<Pair<String, String>> get() = listOf(
    //  نامِ صفحهٔ اول همان سرخطش است: «خانه»، نه «داشبورد»
    "dashboard" to Str.home,
    "sale" to Str.sale,
    "debtors" to Str.debtors,
    "warehouse" to Str.warehouse,
    "products" to Str.products,
    "sales" to Str.salesHistoryAlt,
    "purchasing" to Str.purchases,
    "reports" to Str.reports,
    "expenses" to Str.expenses,
  )

  val request = mutableStateOf<String?>(null)

  fun go(target: String) { request.value = target }

  /** پنجرهٔ «میان‌برهای صفحه‌کلید» (F1 یا دکمهٔ ⌨ در سربرگ) — فقط کامپیوتر */
  val help = mutableStateOf(false)

  /** جست‌وجوی سریع (Ctrl+K روی کامپیوتر، دکمهٔ ذره‌بین در سربرگ) */
  val palette = mutableStateOf(false)

  /** درخواستِ حالتِ منوی کناری از نوارِ منوی کامپیوتر — `RailMode` */
  val rail = mutableStateOf<String?>(null)

  /** کارِ «تازه»ِ صفحهٔ باز (دکمهٔ +) — Ctrl+N روی کامپیوتر همین را صدا می‌زند */
  val addAction = mutableStateOf<(() -> Unit)?>(null)

  /** درخواستِ تم از نوارِ منوی کامپیوتر: "LIGHT" / "DARK" / "SYSTEM" */
  val theme = mutableStateOf<String?>(null)
}
