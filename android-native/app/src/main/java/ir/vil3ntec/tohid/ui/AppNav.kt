package ir.vil3ntec.tohid.ui

import androidx.compose.runtime.mutableStateOf

/**
 *  ناوبری از بیرونِ صفحه‌ها — میان‌برهای صفحه‌کلیدِ برنامهٔ کامپیوتر.
 *
 *  پنجرهٔ کامپیوتر (`desktop/Main.kt`) با Ctrl+۱ تا ۹ این‌جا مقصد می‌گذارد و
 *  `AppRoot` همان را با `open()`ِ خودش باز می‌کند؛ پس همهٔ سدها (نقشِ شاگرد،
 *  قفلِ اشتراک) همان‌طور سرِ جایشان می‌مانند. روی گوشی کسی چیزی نمی‌گذارد.
 */
object AppNav {
  /** ترتیبِ میان‌برها: Ctrl+۱ داشبورد، Ctrl+۲ فروش، … */
  val SHORTCUTS = listOf(
    "dashboard" to "داشبورد",
    "sale" to "فروش",
    "debtors" to "قرض‌داران",
    "warehouse" to "انبار",
    "products" to "محصولات",
    "sales" to "تاریخچهٔ فروش",
    "purchasing" to "خرید",
    "reports" to "گزارش‌ها",
    "expenses" to "مصارف",
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
