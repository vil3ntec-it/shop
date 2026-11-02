package ir.vil3ntec.tohid.data

import kotlin.math.abs

/**
 *  S20 (INV-09، INV-05) — بازبینیِ انبارگردانی پیش از ثبت: کالاهای همان دامنه (صافیِ دسته/تأمین‌کننده، `CountFilter`)
 *  در سه دسته — شمرده‌نشده، منطبق، مغایر — با اثرِ مالیِ مغایرها. «مورد انتظار» همان `countLines` است: موجودیِ برنامه
 *  منهای فروش‌های پس از لحظهٔ شمردن (`CountShift`، INV-10). شمرده‌نشده دست نمی‌خورد (هشدارِ INV-09؛ صفر کردنش نه — D-19).
 *  پنجرهٔ «انبارگردانیِ تازه» (`CountToolsPref`) نشانش می‌دهد؛ شمارشِ کور فقط «شمرده‌نشده» را جدا می‌کند.
 */
object CountReview {

  enum class Kind { UNCOUNTED, MATCHED, DIFF }

  data class Row(val product: Product, val kind: Kind, val expected: Double, val counted: Double?) {
    val diff: Double get() = (counted ?: expected) - expected
    val value: Double get() = if (kind == Kind.DIFF) diff * product.purchasePrice else 0.0
  }

  data class Review(val rows: List<Row>) {
    fun of(kind: Kind): List<Row> = rows.filter { it.kind == kind }
    val uncounted: Int get() = rows.count { it.kind == Kind.UNCOUNTED }
    val matched: Int get() = rows.count { it.kind == Kind.MATCHED }
    val differing: Int get() = rows.count { it.kind == Kind.DIFF }
    /** اثرِ مالیِ مغایرها به بهای خرید (همان `CountLine.value`) */
    val value: Double get() = rows.sumOf { it.value }
  }

  /** @param scope کالاهای دامنهٔ شمارش (فهرستِ دیده‌شده) */
  fun of(d: ShopData, scope: List<Product>, counted: Map<String, Double>, shift: Map<String, Double> = emptyMap()): Review =
    Review(
      scope.map { p ->
        val expected = ShopStore.stock(d, p.id) - (shift[p.id] ?: 0.0)
        val c = counted[p.id]
        val kind = when {
          c == null -> Kind.UNCOUNTED
          abs(c - expected) < 0.0005 -> Kind.MATCHED
          else -> Kind.DIFF
        }
        Row(p, kind, expected, c)
      },
    )
}
