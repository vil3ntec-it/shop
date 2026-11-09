package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.ExpiryEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseSuggest
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.PlaceStock
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.v3.products.ProductsPaths

/** فیلترِ فهرستِ انبار — کلیدِ پرسشِ مسیر (`stock?filter=low`) */
enum class StockFilter(val key: String) {
  ALL("all"), LOW("low"), OUT("out"), EXPIRING("expiring"), EXPIRED("expired");

  companion object {
    /** کلیدِ ناشناس یا خالی ⇒ همه */
    fun of(key: String?): StockFilter = entries.firstOrNull { it.key == key } ?: ALL
  }
}

/** حالِ موجودیِ یک کالا — همان «out/low/ok» ِ `ShopStore.stockStatus` */
enum class StockLevel { OK, LOW, OUT }

/** یک ردیفِ فهرست: کالا و هر چه ردیف نشان می‌دهد، همه از موتورها */
data class StockRowUi(
  val product: Product,
  val stock: Double,
  /** کارتن‌های واردشده (`ShopStore.cartons`) — همان معنیِ کارتِ آمار */
  val cartons: Double,
  val level: StockLevel,
  /** محموله‌ای منقضی مانده است (`ExpiryEngine.alerts`) */
  val expired: Boolean,
  /** کمترین روزِ ماندهٔ محموله‌های نزدیک به انقضا؛ null یعنی نزدیک نیست */
  val expiresInDays: Long?,
  /**
   *  INV-14 — «این موجودی چند روز کافی است» (`DemandForecast.daysLeft` با روزِ هفته و فصل، همان عددِ
   *  «پیشنهادِ سفارش»)؛ بی فروش یا کالای غیرفعال ⇒ null. [coverBeyond] ⇒ بیش از سقفِ ۳۶۵ روز.
   */
  val coverDays: Int? = null,
  val coverBeyond: Boolean = false,
)

/** خلاصهٔ کلِ انبار: کارتِ آمار، شمارِ هر فیلتر و ردیف‌ها */
data class StockOverview(
  val summary: WarehouseEngine.Summary,
  val rows: List<StockRowUi>,
  val counts: Map<StockFilter, Int>,
  /**
   *  INV-01 — محل‌ها (اصلی اول) و موجودیِ هر کالا در هر محل به هزارم (`PlaceStock.all`: ورود با
   *  محل + انتقال − فروش؛ جمعِ محل‌های یک کالا همان موجودیِ کل). فقط با «محل‌های نگه‌داری» ِ همین دستگاه، وگرنه خالی.
   */
  val places: List<String> = emptyList(),
  val placeStock: Map<String, Map<String, Long>> = emptyMap(),
)

/**
 *  حالتِ خالصِ فهرستِ انبار (بی Compose): هر عدد از `WarehouseEngine.summary`،
 *  `ShopStore.stock/stockStatus/cartons` و `ExpiryEngine.alerts` می‌آید؛ این‌جا فقط
 *  کنارِ هم گذاشته و پالایش می‌شود. آزمون: `StockListModelTest`.
 */
object StockListModel {

  fun overview(d: ShopData, today: String, places: Boolean = false): StockOverview {
    val alerts = ExpiryEngine.alerts(d, today)
    val expired = alerts.filter { it.expired }.map { it.product.id }.toSet()
    val near = alerts.filter { !it.expired }.groupBy { it.product.id }.mapValues { (_, lots) -> lots.minOf { it.daysLeft } }
    //  INV-14: بی سفارشِ باز (فقط فروش و موجودی) — «چند روز» به زمانِ تأمین و «در راه» وابسته نیست
    val cover = PurchaseSuggest.rows(d, emptyList(), today).associate { it.product.id to it.forecast }
    val rows = d.products.map { p ->
      StockRowUi(
        product = p,
        stock = ShopStore.stock(d, p.id),
        cartons = ShopStore.cartons(d, p.id),
        level = when (ShopStore.stockStatus(d, p)) {
          "out" -> StockLevel.OUT
          "low" -> StockLevel.LOW
          else -> StockLevel.OK
        },
        expired = p.id in expired,
        expiresInDays = near[p.id],
        coverDays = cover[p.id]?.daysLeft,
        coverBeyond = cover[p.id]?.daysLeftBeyond == true,
      )
    }
    val counts = StockFilter.entries.associateWith { f -> rows.count { matches(it, f) } }
    val (where, at) = if (places) PlaceStock.all(d) else emptyList<String>() to emptyMap<String, Map<String, Long>>()
    return StockOverview(WarehouseEngine.summary(d), rows, counts, where, at)
  }

  /** INV-01: تراشه‌های محل فقط وقتی جز محلِ اصلی محلی هست */
  fun placeChips(o: StockOverview): List<String> = if (o.places.size > 1) o.places else emptyList()

  /**
   *  INV-01 «موجودی به تفکیکِ محل»: [place] ⇒ فقط کالاهایی که در همان محل عدد دارند، با موجودیِ **همان محل**؛ حالِ کم/تمام
   *  همان حالِ کلِ کالاست. `null` یا محلی که دیگر نیست ⇒ همان ردیف‌ها. فقط خواندنی.
   */
  fun atPlace(o: StockOverview, place: String?): List<StockRowUi> {
    if (place == null || place !in o.places) return o.rows
    return o.rows.mapNotNull { r -> o.placeStock[r.product.id]?.get(place)?.takeIf { it != 0L }?.let { r.copy(stock = it / 1000.0) } }
  }

  /** INV-14: روزهای کافی که ردیف نشان می‌دهد — تمام‌شده («تمام» خودش قرص دارد) و بی فروش ⇒ null */
  fun coverShown(row: StockRowUi): Int? = row.coverDays?.takeIf { it > 0 && row.level != StockLevel.OUT }

  fun matches(row: StockRowUi, filter: StockFilter): Boolean = when (filter) {
    StockFilter.ALL -> true
    StockFilter.LOW -> row.level == StockLevel.LOW
    StockFilter.OUT -> row.level == StockLevel.OUT
    StockFilter.EXPIRING -> row.expiresInDays != null
    StockFilter.EXPIRED -> row.expired
  }

  /** جست‌وجو در نام و بارکد (همان قاعدهٔ صفحهٔ پیشین) + فیلتر + دسته */
  fun visible(rows: List<StockRowUi>, filter: StockFilter, query: String, category: String? = null): List<StockRowUi> {
    val term = query.trim()
    return rows.filter { r ->
      matches(r, filter) &&
        (category == null || r.product.category == category) &&
        (term.isEmpty() || r.product.name.contains(term, ignoreCase = true) || r.product.barcodes.any { it.contains(term) })
    }
  }

  /** دسته‌هایی که دست‌کم یک کالا دارند — به ترتیبِ فهرستِ دسته‌های دکان */
  fun usedCategories(d: ShopData): List<String> {
    val used = d.products.map { it.category }.filter { it.isNotBlank() }.toSet()
    return d.productCategories.filter { it in used } + used.filter { it !in d.productCategories }.sorted()
  }

  /** فیلترهای انقضا فقط وقتی چیزی دارند دیده می‌شوند (یا همین حالا انتخاب‌اند) */
  fun shownFilters(counts: Map<StockFilter, Int>, current: StockFilter): List<StockFilter> =
    StockFilter.entries.filter { f ->
      f == current || f == StockFilter.ALL || f == StockFilter.LOW || f == StockFilter.OUT || (counts[f] ?: 0) > 0
    }

  /** بارکدِ اسکن‌شده در انبار: کالای ثبت‌شده ⇐ ورودِ همان؛ ناشناس ⇐ فرمِ کالای تازه با همین بارکد */
  fun scanTarget(d: ShopData, code: String): String {
    val clean = code.trim()
    val id = ShopStore.barcodeIndex(d)[clean]
    return if (id != null) StockPaths.entry(product = id) else ProductsPaths.new(barcode = clean)
  }
}
