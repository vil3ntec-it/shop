package ir.vil3ntec.tohid.core.purchase

/**
 *  تاریخچهٔ قیمتِ خرید از هر تأمین‌کننده — PUR-09 (§۶.۶): «آخرین قیمت هنگامِ ورود نشان داده می‌شود».
 *
 *  ورودی ردیف‌های خریدِ دفتر است (برنامه از `purchases` می‌سازد): کالا، تأمین‌کننده، تاریخ، زمانِ ثبت،
 *  مقدار و فی. سندِ کسر (برگشت به تأمین‌کننده، مقدارِ منفی، #323) و ردیفِ بی مقدار قیمتِ خرید نیستند و
 *  کنار می‌روند. پول یک‌صدمِ افغانی و مقدار هزارم؛ درصدِ تغییر به ده‌هزارم (bp) با همان گردِ هسته.
 *  Kotlinِ خالص در commonMain (§۴۰.۳).
 */
object SupplierPricing {

  data class Buy(
    val supplierId: String,
    val productId: String,
    /** `yyyy-MM-dd` */
    val date: String,
    /** زمانِ ثبت (ترتیبِ دو خریدِ یک روز) */
    val at: Long,
    val qtyMilli: Long,
    val priceMinor: Long,
  )

  /** یک خرید با تغییرش نسبت به خریدِ پیشینِ **همان تأمین‌کننده** (نخستین خرید ⇒ `null`) */
  data class Point(
    val supplierId: String,
    val date: String,
    val at: Long,
    val qtyMilli: Long,
    val priceMinor: Long,
    val changeMinor: Long?,
    /** تغییر به ده‌هزارم از فیِ پیشین؛ فیِ پیشینِ صفر (یا بیش از ۲^۳۱−۱ یک‌صدم) ⇒ `null` */
    val changeBp: Long?,
  )

  private fun real(b: Buy) = b.qtyMilli > 0 && b.priceMinor >= 0

  private val oldestFirst = compareBy<Buy>({ it.date }, { it.at })

  /**
   *  تاریخچهٔ یک کالا، تازه‌ترین بالا. [supplierId] = `null` ⇒ همهٔ تأمین‌کنندگان (تغییرِ هر ردیف باز هم
   *  نسبت به خریدِ پیشینِ همان تأمین‌کننده است، نه دیگری).
   */
  fun history(buys: List<Buy>, productId: String, supplierId: String? = null): List<Point> {
    val mine = buys.filter { it.productId == productId && real(it) && (supplierId == null || it.supplierId == supplierId) }
    val out = ArrayList<Point>(mine.size)
    for ((sid, list) in mine.groupBy { it.supplierId }) {
      var prev: Buy? = null
      for (b in list.sortedWith(oldestFirst)) {
        val p = prev
        val change = p?.let { b.priceMinor - it.priceMinor }
        //  فیِ پیشین بیش از ۲۱ میلیون افغانی (مقسومِ ۳۱ بیتیِ هسته) ⇒ درصد گفته نمی‌شود، فقط مبلغ
        //  جهشِ بیش از سقفِ امن (فیِ پیشینِ ۱ یک‌صدم) هم فقط مبلغ
        val bp = if (p != null && p.priceMinor in 1..Int.MAX_VALUE.toLong()) {
          try { PurchaseOrderCore.mulDivRound(b.priceMinor - p.priceMinor, PurchaseOrderCore.BP, p.priceMinor) } catch (e: ArithmeticException) { null }
        } else null
        out += Point(sid, b.date, b.at, b.qtyMilli, b.priceMinor, change, bp)
        prev = b
      }
    }
    return out.sortedWith(compareByDescending<Point> { it.date }.thenByDescending { it.at }.thenBy { it.supplierId })
  }

  /** آخرین خریدِ این کالا از این تأمین‌کننده — پیش‌فرضِ فی در فرمِ خرید و سفارش */
  fun last(buys: List<Buy>, productId: String, supplierId: String): Point? = history(buys, productId, supplierId).firstOrNull()

  /** آخرین فیِ هر تأمین‌کننده برای این کالا، ارزان‌ترین بالا (برابر ⇒ تازه‌تر بالا) */
  fun bySupplier(buys: List<Buy>, productId: String): List<Point> =
    history(buys, productId).groupBy { it.supplierId }.values.map { it.first() }
      .sortedWith(compareBy<Point> { it.priceMinor }.thenByDescending { it.date }.thenByDescending { it.at })
}
