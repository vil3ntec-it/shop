package ir.vil3ntec.tohid.data

import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.CodeCount
import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.CommandSequence
import ir.vil3ntec.tohid.core.hw.PendingScans
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanSaleRule
import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money

/**
 *  راهِ «الف» ِ پرداخت پس از اسکنِ پس‌زمینه (پیش‌فرض و امن): اسکن‌های فروش ⇒ **فاکتورِ نگه‌داشته** (POS-12)، نه فروش.
 *
 *  - همان بارکد N بار ⇒ یک ردیف با مقدارِ N؛ قیمت همان قیمتِ فروشِ امروزِ کالا (در صندوق دوباره حساب می‌شود).
 *  - بارکدِ ناشناس ردیف نمی‌شود و جدا برمی‌گردد (برای «ثبتِ کالا»).
 *  - بارکدِ فضای‌نامِ «کارتِ فرمان» هرگز کالا نیست (`CommandCard.isCommandLike`).
 *  - شناسهٔ فاکتور = `ScanSaleRule.key` ⇒ واردکردنِ دوباره همان فاکتور را دوباره نمی‌سازد و موتورِ فروش
 *    همان شناسه را دو بار ثبت نمی‌کند (POS-14).
 *  ⛔ ثبتِ فروش فقط پس از زدنِ «پرداخت» و از همان پنلِ پرداختِ صندوق است — این‌جا هیچ فروشی ثبت نمی‌شود.
 */
object ScanSaleHold {
  /** برچسبِ فاکتورِ نگه‌داشته — در فهرستِ نگه‌داشته‌ها و برگهٔ فاکتور دیده می‌شود */
  const val LABEL = "از بارکدخوان"

  data class Line(val productId: String, val name: String, val qty: Int, val totalMinor: Long)

  data class Invoice(val id: String, val lines: List<Line>, val totalMinor: Long, val scanIds: Set<String>)

  /**
   *  @param unknown کدهای بی کالا (با شمار) — به «ثبتِ کالا»
   *  @param settled اسکن‌های فاکتوری که پیش‌تر فروخته شده (واردکردنِ دوباره) — فقط برداشته می‌شوند
   */
  data class Plan(val invoices: List<Invoice>, val unknown: List<CodeCount>, val settled: Set<String> = emptySet()) {
    val totalMinor: Long get() = invoices.sumOf { it.totalMinor }
    val scanIds: Set<String> get() = invoices.flatMap { it.scanIds }.toSet()
  }

  fun minor(afn: Double): Long = Money.fromLegacy(afn, Currency.AFN).minor

  /**
   *  ردیف‌های یک فاکتور از اسکن‌هایش: همان کد N بار ⇒ مقدارِ N؛ یک کالا با دو بارکد ⇒ یک ردیف؛ کدِ بی کالا جدا.
   *  همان که «کارتِ فرمان» برای ثبتِ نقدی می‌خواند (`ScanCashSale`).
   */
  fun lines(d: ShopData, items: List<QueuedScan>): Pair<List<Line>, List<CodeCount>> {
    val index = ShopStore.barcodeIndex(d)
    val products = d.products.associateBy { it.id }
    val groups = PendingScans.group(items)
    val (known, missing) = PendingScans.split(groups) { code -> index[code]?.let(products::get) != null }
    val lines = known.map { g ->
      val p = products.getValue(index.getValue(g.code))
      val cart = listOf(SalesEngine.CartLine(p.id, g.count.toDouble()))
      Line(p.id, p.name, g.count, minor(SalesEngine.cartTotal(d, cart)))
    }
    val merged = lines.groupBy { it.productId }.map { (_, ls) -> ls.first().copy(qty = ls.sumOf { it.qty }, totalMinor = ls.sumOf { it.totalMinor }) }
    return merged to missing
  }

  /**
   *  اسکن‌های فروش ⇒ فاکتورها; بی «کارتِ فرمان» هر دنباله یک فاکتور است. با کارتِ روشن ([commandOf] = فرمانِ همین
   *  دستگاه) «فاکتورِ تازه» مرزِ دو فاکتور است و کدِ فرمان هرگز کالا نیست.
   */
  fun plan(d: ShopData, scans: List<QueuedScan>, commandOf: (String) -> CommandCard.Command? = { null }): Plan {
    val unknown = LinkedHashMap<String, CodeCount>()
    val invoices = CommandSequence.split(scans, commandOf = commandOf).batches.mapNotNull { batch ->
      val (merged, missing) = lines(d, batch.items)
      missing.forEach { m -> unknown[m.code] = unknown[m.code]?.let { it.copy(count = it.count + m.count) } ?: m }
      if (merged.isEmpty()) null
      else Invoice(ScanSaleRule.key(batch), merged, merged.sumOf { it.totalMinor }, batch.span.map { it.id }.toSet())
    }
    //  همان فاکتور پیش‌تر فروخته شده (POS-14) ⇒ دوباره ساخته نمی‌شود
    val (sold, open) = invoices.partition { SaleDraftRules.posted(d, it.id) }
    return Plan(open, unknown.values.toList(), sold.flatMap { it.scanIds }.toSet())
  }

  /** فاکتورِ نگه‌داشتهٔ همین [inv] — بی قیمتِ دستی (صندوق قیمتِ روز را می‌گذارد)؛ [note] چرای نگه‌داشتن */
  fun held(inv: Invoice, now: Long, label: String = LABEL, note: String = ""): HeldInvoice = HeldInvoice(
    id = inv.id,
    number = 0,
    label = label,
    lines = inv.lines.map { SaleDraft.Line(it.productId, it.qty.toDouble()) },
    note = note,
    heldAt = now,
  )

  /**
   *  «پرداخت» در پرسشِ اسکن‌های در انتظار: شناسهٔ فاکتورِ نگه‌داشته‌ای که صندوق باید باز کند و به پنلِ پرداخت برود.
   *  صندوق (`SaleRoute`) برش می‌دارد؛ فروش فقط با همان پنل و دستِ کاربر ثبت می‌شود.
   */
  val payRequest = mutableStateOf<String?>(null)
}
