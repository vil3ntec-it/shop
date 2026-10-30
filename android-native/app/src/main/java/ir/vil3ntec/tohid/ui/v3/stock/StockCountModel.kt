package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.latinDigits

/**
 *  انبارگردانی — حالتِ خالص (آزمون: `StockCountModelTest`). خانهٔ خالی یعنی شمرده نشد؛
 *  رقمِ فارسی، «٫» و «,» ِ اعشار پذیرفته‌اند (همان صفحهٔ پیشین)؛ ناخوانا ⇒ NaN تا زیرِ
 *  همان ردیف خطا بیاید. اختلاف‌ها و ارزششان فقط از `WarehouseEngine.countLines`.
 */
data class CountState(val counts: Map<String, String> = emptyMap(), val note: String = "") {

  /** فقط خانه‌های پر: عدد، یا NaN برای نوشتهٔ نادرست */
  val parsed: Map<String, Double>
    get() = counts.mapNotNull { (id, text) ->
      val t = latinDigits(text.trim()).replace('٫', '.').replace(',', '.')
      if (t.isEmpty()) null else id to (t.toDoubleOrNull() ?: Double.NaN)
    }.toMap()

  /** شمارِ درستِ نامنفی — همان چیزی که به موتور می‌رود */
  val valid: Map<String, Double> get() = parsed.filterValues { !it.isNaN() && it >= 0 }

  val dirty: Boolean get() = counts.values.any { it.isNotBlank() } || note.isNotBlank()

  fun lines(d: ShopData): List<WarehouseEngine.CountLine> = WarehouseEngine.countLines(d, valid)

  /** نخستین کالایی که عددش درست نیست */
  fun firstInvalid(d: ShopData): Product? =
    parsed.entries.firstOrNull { it.value.isNaN() || it.value < 0 }?.let { bad -> d.products.find { it.id == bad.key } }

  fun invalid(id: String): Boolean = parsed[id]?.let { it.isNaN() || it < 0 } == true

  /** اختلافِ این ردیف (شمرده − برنامه)؛ null وقتی شمرده نشده یا درست نیست یا یکی است */
  fun diff(id: String, system: Double): Double? {
    val v = parsed[id] ?: return null
    if (v.isNaN() || v < 0) return null
    val d = v - system
    return if (kotlin.math.abs(d) < 0.0005) 0.0 else d
  }

  companion object {
    /** ردیف‌های شمارش: کالاهای فعال، به ترتیبِ نام، با جست‌وجو در نام و بارکد */
    fun rows(d: ShopData, query: String): List<Product> {
      val term = query.trim()
      return d.products.filter { it.active }
        .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) || it.barcodes.any { b -> b.contains(term) } }
        .sortedBy { it.name }
    }

    fun system(d: ShopData): Map<String, Double> = d.products.associate { it.id to ShopStore.stock(d, it.id) }
  }
}
