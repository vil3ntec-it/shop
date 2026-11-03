package ir.vil3ntec.tohid.data

import kotlin.math.abs

/**
 *  S20 (INV-09، INV-05) — بازبینیِ انبارگردانی پیش از ثبت: کالاهای همان دامنه (صافیِ دسته/تأمین‌کننده، `CountFilter`)
 *  در سه دسته — شمرده‌نشده، منطبق، مغایر — با اثرِ مالیِ مغایرها. «مورد انتظار» همان `countLines` است: موجودیِ برنامه
 *  منهای فروش‌های پس از لحظهٔ شمردن (`CountShift`، INV-10). شمرده‌نشده دست نمی‌خورد (هشدارِ INV-09؛ صفر کردنش نه — D-19).
 *  پنجرهٔ «انبارگردانیِ تازه» (`CountToolsPref`) نشانش می‌دهد؛ شمارشِ کور فقط «شمرده‌نشده» را جدا می‌کند. «مستثنا» و
 *  «شمارشِ دوباره» (INV-09) هم این‌جا: مستثنا هرگز اصلاح نمی‌شود و مغایرها با یک دکمه دوباره شمرده می‌شوند.
 */
object CountReview {

  /** EXCLUDED (INV-09): کالایی که صاحب‌کار صریحاً از این شمارش بیرون گذاشت — نه شمرده‌نشده، نه اصلاح؛ موجودی دست نمی‌خورد */
  enum class Kind { UNCOUNTED, MATCHED, DIFF, EXCLUDED }

  data class Row(val product: Product, val kind: Kind, val expected: Double, val counted: Double?) {
    val diff: Double get() = (counted ?: expected) - expected
    val value: Double get() = if (kind == Kind.DIFF) diff * product.purchasePrice else 0.0
  }

  data class Review(val rows: List<Row>) {
    fun of(kind: Kind): List<Row> = rows.filter { it.kind == kind }
    val uncounted: Int get() = rows.count { it.kind == Kind.UNCOUNTED }
    val matched: Int get() = rows.count { it.kind == Kind.MATCHED }
    val differing: Int get() = rows.count { it.kind == Kind.DIFF }
    val excluded: Int get() = rows.count { it.kind == Kind.EXCLUDED }
    /** اثرِ مالیِ مغایرها به بهای خرید (همان `CountLine.value`) */
    val value: Double get() = rows.sumOf { it.value }
  }

  /**
   *  @param scope کالاهای دامنهٔ شمارش (فهرستِ دیده‌شده)
   *  @param excluded INV-09: کالاهای «مستثنا» ِ همین شمارش — هر عددی هم شمرده شده باشد اصلاح نمی‌شوند
   */
  fun of(d: ShopData, scope: List<Product>, counted: Map<String, Double>, shift: Map<String, Double> = emptyMap(), excluded: Set<String> = emptySet()): Review =
    Review(
      scope.map { p ->
        val expected = ShopStore.stock(d, p.id) - (shift[p.id] ?: 0.0)
        val c = counted[p.id]
        val kind = when {
          p.id in excluded -> Kind.EXCLUDED
          c == null -> Kind.UNCOUNTED
          abs(c - expected) < 0.0005 -> Kind.MATCHED
          else -> Kind.DIFF
        }
        Row(p, kind, expected, if (kind == Kind.EXCLUDED) null else c)
      },
    )

  /**
   *  INV-09 — آنچه ثبت می‌شود: شمرده‌ها منهای «مستثنا»ها. مستثنا مثلِ شمرده‌نشده دست نمی‌خورد (هرگز صفر نمی‌شود — D-19)،
   *  حتی اگر پیش از مستثنا کردن عددی برایش نوشته شده بود.
   */
  fun saved(counted: Map<String, Double>, excluded: Set<String>): Map<String, Double> =
    if (excluded.isEmpty()) counted else counted.filterKeys { it !in excluded }

  /** INV-09 — «شمارشِ دوباره»: کالاهای مغایرِ همین بازبینی (به ترتیبِ فهرست) که باید دوباره شمرده شوند */
  fun recount(review: Review): Set<String> = review.of(Kind.DIFF).mapTo(LinkedHashSet()) { it.product.id }

  /**
   *  INV-09 — شمرده‌ها پس از «شمارشِ دوباره»: برای کالاهای [recount] عددِ پیشین (این دستگاه یا برگهٔ دستگاهِ دیگر) کنار
   *  می‌رود و فقط عددِ تازهٔ همین دستگاه ([fresh]) می‌ماند؛ هنوز شمرده نشده ⇒ «شمرده‌نشده» و دست نمی‌خورد. بی شمارشِ
   *  دوباره همان [counted].
   */
  fun afterRecount(counted: Map<String, Double>, fresh: Map<String, Double>, recount: Set<String>): Map<String, Double> {
    if (recount.isEmpty()) return counted
    return counted.filterKeys { it !in recount } + fresh.filterKeys { it in recount }
  }
}
