package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.stock.CountScan
import ir.vil3ntec.tohid.data.CountFilter
import ir.vil3ntec.tohid.data.CountSheetModel
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

  /**
   *  بارکدخوان روی همین صفحه (`ScanRouter`: پنجرهٔ کامپیوتر بی کادرِ فوکوس‌دار، بارکدخوانِ وصل‌شده، گوشیِ جفت‌شده). پیش‌تر هیچ
   *  گیرنده‌ای نبود ⇒ صندوق باز می‌شد و شمارشِ ذخیره‌نشده با بسته شدنِ صفحه گم. [counting] («ابزارهای تازهٔ دستگاه»، `HwTools`)
   *  ⇒ +۱ به خانهٔ همان کالا (`CountSheetModel.scan`: وزنی، غیرفعال و ناشناس بی تغییر و با پیام)؛ خاموش ⇒ هیچ عددی عوض نمی‌شود
   *  و نتیجه `null` است. در هر دو حال صفحه و نوشته‌هایش سرِ جایشان می‌مانند.
   */
  fun scan(d: ShopData, code: String, index: Map<String, String>, counting: Boolean): Pair<CountState, CountScan.Result?> {
    if (!counting) return this to null
    val (next, r) = CountSheetModel.scan(counts, code, d, index)
    return copy(counts = next) to r
  }

  companion object {
    /** پیامِ پس از اسکن — همان پیام‌های «انبارگردانیِ تازه» (`StrCount`)؛ `null` (تنظیمِ خاموش) ⇒ «شمرده نشد، صفحه همان» */
    fun scanMessage(r: CountScan.Result?, d: ShopData): String {
      if (r == null) return StrCount.scanOff
      fun name(id: String) = d.products.firstOrNull { it.id == id }?.name.orEmpty()
      return when (r) {
        is CountScan.Result.Counted -> StrCount.scanned(name(r.productId), CountSheetModel.text(r.countedMilli))
        is CountScan.Result.Unknown -> StrCount.unknown(r.code)
        is CountScan.Result.Weighed -> StrCount.weighed(name(r.productId))
        is CountScan.Result.Inactive -> StrCount.inactive(name(r.productId))
      }
    }

    /**
     *  ردیف‌های شمارش: کالاهای فعال، به ترتیبِ نام، با جست‌وجو در نام و بارکد و صافیِ [scope] (INV-05) — همان
     *  `CountFilter.shown` ِ «انبارگردانیِ تازه»؛ بی صافی همان فهرستِ پیشین
     */
    fun rows(d: ShopData, query: String, scope: CountScope = CountScope()): List<Product> =
      CountFilter.shown(d.products, query, scope.category, scope.supplierId, scope.brand, scope.shelf)

    /** گزینه‌های تراشه‌های صافی (فقط از کالاهای فعال) — همان `CountFilter` و همان برچسبِ قفسه */
    fun options(d: ShopData): CountScopeOptions = CountScopeOptions(
      categories = CountFilter.categories(d.products).map { it to it },
      suppliers = CountFilter.suppliers(d.products, d.suppliers).map { it.id to it.name.ifBlank { it.id } },
      brands = CountFilter.brands(d.products).map { it to it },
      shelves = CountFilter.shelves(d.products).map { it to CountFilter.shelfLabel(it) },
    )

    fun system(d: ShopData): Map<String, Double> = d.products.associate { it.id to ShopStore.stock(d, it.id) }
  }
}

/**
 *  INV-05 — صافیِ «انبارگردانی»: دسته، تأمین‌کننده، برند و قفسه (کلیدِ `CountFilter.shelfKey`)؛ `null` ⇒ بی صافی. صافی فقط
 *  فهرستِ دیده‌شده را کم می‌کند؛ شمرده‌ها، جمع‌ها، CSV و ثبت همان‌اند (خانهٔ خالی یعنی شمرده نشد).
 */
data class CountScope(
  val category: String? = null,
  val supplierId: String? = null,
  val brand: String? = null,
  val shelf: String? = null,
) {
  val active: Boolean get() = category != null || supplierId != null || brand != null || shelf != null
}

/** گزینه‌های هر ردیفِ تراشه (شناسه ⇐ برچسب)؛ فهرستِ خالی ⇒ بی آن ردیف، همه خالی ⇒ همان صفحهٔ پیشین */
data class CountScopeOptions(
  val categories: List<Pair<String, String>>,
  val suppliers: List<Pair<String, String>>,
  val brands: List<Pair<String, String>>,
  val shelves: List<Pair<String, String>>,
) {
  val isEmpty: Boolean get() = categories.isEmpty() && suppliers.isEmpty() && brands.isEmpty() && shelves.isEmpty()
}
