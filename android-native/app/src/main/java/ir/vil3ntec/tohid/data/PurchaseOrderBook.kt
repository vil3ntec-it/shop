package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.core.purchase.SupplierPricing
import ir.vil3ntec.tohid.sync.v1.OpHash
import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.max

/*
 *  سفارشِ خرید از تأمین‌کننده در برنامهٔ مدیریت — PUR-02، PUR-03، PUR-04، PUR-14 (§۶.۶، S19).
 *
 *  سفارش و رسیدهایش در سرور‌اند (`/api/shop/purchase-orders`، پرچمِ `shop.purchase_orders`)؛ قاعده‌ها
 *  در `core/purchase/PurchaseOrderCore` (همان بردارهای سرور). این فایل فقط دو کار دارد:
 *   ۱) پاسخِ سرور ⇐ مدل‌ها (DTO) و هسته؛
 *   ۲) رسیدِ ثبت‌شده ⇐ **فاکتورِ خریدِ دفتر** با همان `LedgerEngine.addPurchase` ِ امروز (انبار، حرکت،
 *      بدهیِ تأمین‌کننده، تاریخچهٔ قیمت). پس موجودی، بها و مانده همان راهِ دفتر را می‌روند.
 *
 *  ⛔ شناسهٔ هر ردیفی که یک رسید در دفتر می‌سازد **قطعی** است (هشِ «رسید|ردیف|شماره»)، نه تصادفی:
 *  ثبتِ دوباره (دو بار زدن، قطعِ برنامه پس از تأییدِ سرور، یا دستگاهِ دیگری که همان رسید را ثبت
 *  کند) همان ردیف‌ها را می‌سازد و همگام‌سازی آن‌ها را یکی می‌کند — هرگز دو خرید و دو بدهی.
 *  هر ردیفِ سفارش مولّدِ شناسهٔ خودش را دارد، تا تغییرِ قیمتِ خریدِ یک کالا (که دو شناسهٔ
 *  تاریخچه/سابقه می‌خواهد) شناسه‌های ردیفِ بعد را جابه‌جا نکند.
 */

@Serializable
data class PoLineDto(
  val id: String = "",
  val productId: String = "",
  val name: String = "",
  val unit: String = "",
  val qtyMilli: Long = 0,
  val priceMinor: Long = 0,
)

@Serializable
data class PoReceiptLineDto(val lineId: String = "", val receivedMilli: Long = 0, val damagedMilli: Long = 0, val priceMinor: Long = 0)

@Serializable
data class PoPostedDto(
  val lineId: String = "",
  val expectedMilli: Long = 0,
  val receivedMilli: Long = 0,
  val damagedMilli: Long = 0,
  val acceptedMilli: Long = 0,
  val shortMilli: Long = 0,
  val overMilli: Long = 0,
  val priceMinor: Long = 0,
  val totalMinor: Long = 0,
)

@Serializable
data class PoReceiptDto(
  val id: String = "",
  val poId: String = "",
  val lines: List<PoReceiptLineDto> = emptyList(),
  val posted: List<PoPostedDto> = emptyList(),
  val totalMinor: Long = 0,
  val note: String = "",
  val createdBy: String = "",
  val createdAt: Long = 0,
)

@Serializable
data class PoProgressDto(
  val lineId: String = "",
  val productId: String = "",
  val orderedMilli: Long = 0,
  val receivedMilli: Long = 0,
  val damagedMilli: Long = 0,
  val acceptedMilli: Long = 0,
  val remainingMilli: Long = 0,
  val overMilli: Long = 0,
)

@Serializable
data class PoTotalsDto(val orderedMinor: Long = 0, val acceptedMinor: Long = 0, val remainingMinor: Long = 0)

@Serializable
data class PurchaseOrderDto(
  val id: String = "",
  val no: Int = 0,
  val supplierId: String = "",
  val supplierName: String = "",
  /** ذخیره‌شده: draft | sent | cancelled */
  val state: String = "",
  /** نمایشی: draft | sent | partial | complete | cancelled */
  val status: String = "",
  val allowed: List<String> = emptyList(),
  val lines: List<PoLineDto> = emptyList(),
  val progress: List<PoProgressDto> = emptyList(),
  val totals: PoTotalsDto = PoTotalsDto(),
  val expectedDate: String? = null,
  val note: String = "",
  val version: Int = 0,
  val createdAt: Long = 0,
  val updatedAt: Long = 0,
  val sentAt: Long? = null,
  val cancelledAt: Long? = null,
  val cancelReason: String? = null,
  val receiptCount: Int = 0,
  /** نخستین رسید (زمانِ تأمین، PUR-10)؛ سرورِ پیش از آن ⇒ `null` */
  val firstReceiptAt: Long? = null,
  /** فقط در `GET /:id` و پاسخِ کارها */
  val receipts: List<PoReceiptDto> = emptyList(),
)

@Serializable
data class PoPermsDto(val manage: Boolean = false, val receive: Boolean = false)

@Serializable
data class PoConfigDto(val enabled: Boolean = false, val permissions: PoPermsDto = PoPermsDto())

@Serializable
data class PoPageDto(val orders: List<PurchaseOrderDto> = emptyList(), val next: String? = null)

@Serializable
data class PoOrderDto(val order: PurchaseOrderDto = PurchaseOrderDto(), val changed: Boolean = false, val replayed: Boolean = false)

@Serializable
data class PoReceivedDto(val order: PurchaseOrderDto = PurchaseOrderDto(), val receipt: PoReceiptDto = PoReceiptDto(), val replayed: Boolean = false)

object PurchaseOrderBook {

  /** مرزِ تطبیق با دفتر: فاکتورِ دفتر هر ردیف را به افغانیِ کامل گرد می‌کند (‎`Math.round(q × p)`‎) */
  val LEDGER_TOLERANCE = PurchaseOrderCore.Tolerance(qtyMilli = 0, priceBp = 0, absMinor = 100)

  fun minor(afn: Double): Long = Math.round(afn * 100.0)
  fun milli(units: Double): Long = Math.round(units * 1000.0)
  fun asAfn(minor: Long): Double = minor / 100.0
  fun units(milli: Long): Double = milli / 1000.0

  fun status(o: PurchaseOrderDto): PurchaseOrderCore.Status = PurchaseOrderCore.Status.of(o.status) ?: PurchaseOrderCore.Status.DRAFT

  fun label(s: PurchaseOrderCore.Status): String = when (s) {
    PurchaseOrderCore.Status.DRAFT -> "پیش‌نویس"
    PurchaseOrderCore.Status.SENT -> "فرستاده"
    PurchaseOrderCore.Status.PARTIAL -> "بخشی دریافت‌شده"
    PurchaseOrderCore.Status.COMPLETE -> "کامل"
    PurchaseOrderCore.Status.CANCELLED -> "لغو"
  }

  fun can(o: PurchaseOrderDto, action: PurchaseOrderCore.Action): Boolean = action.wire in o.allowed

  fun core(o: PurchaseOrderDto): PurchaseOrderCore.Order =
    PurchaseOrderCore.Order(o.supplierId, o.state, o.lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) })

  fun coreReceipts(o: PurchaseOrderDto): List<PurchaseOrderCore.Receipt> = o.receipts.map { r ->
    PurchaseOrderCore.Receipt(r.id, r.lines.map { PurchaseOrderCore.ReceiptLine(it.lineId, it.receivedMilli, it.damagedMilli, it.priceMinor) })
  }

  /* ── رسید ⇐ فاکتورِ خریدِ دفتر ─────────────────────────────────────── */

  /** شناسهٔ قطعیِ k-امین ردیفی که ردیفِ [lineId] ِ رسیدِ [receiptId] در دفتر می‌سازد (≤ ۸۰ نویسهٔ همگام‌سازی) */
  fun ledgerId(receiptId: String, lineId: String, k: Int): String =
    "po-" + OpHash.sha256Hex("$receiptId|$lineId|$k").substring(0, 32)

  /** نخستین شناسهٔ `addPurchase` همان ردیفِ خرید است */
  fun purchaseIdOf(receiptId: String, lineId: String): String = ledgerId(receiptId, lineId, 0)

  private fun toPost(r: PoReceiptDto) = r.posted.filter { it.acceptedMilli > 0 }

  /** همهٔ ردیف‌های پذیرفتهٔ این رسید در دفترِ این دستگاه هست؟ (رسیدِ همه‌خراب چیزی برای ثبت ندارد) */
  fun isPosted(d: ShopData, r: PoReceiptDto): Boolean {
    val ids = d.purchases.mapTo(HashSet()) { it.id }
    return toPost(r).all { purchaseIdOf(r.id, it.lineId) in ids }
  }

  /** رسیدهایی که هنوز فاکتورِ خریدشان در دفترِ این دستگاه نیست */
  fun unposted(d: ShopData, o: PurchaseOrderDto): List<PoReceiptDto> = o.receipts.filter { !isPosted(d, it) }

  sealed interface Posting {
    data class Ok(val data: ShopData, val invoiceNo: Int, val rows: Int) : Posting
    /** چیزی برای ثبت نیست: پیش‌تر ثبت شده یا همهٔ رسید خراب بوده */
    data object Already : Posting
    data class Failed(val message: String) : Posting
  }

  /**
   *  رسید ⇐ یک فاکتورِ خریدِ چندردیفه (همان `invoiceNo` ِ `PurchaseBook`)، بی پرداخت: بدهیِ تأمین‌کننده
   *  همان جمعِ پذیرفته به فیِ رسید است و پرداخت بعداً از راهِ امروز. ردیفِ پیش‌تر نشسته (همان دستگاه یا
   *  همگام‌سازی) دوباره ساخته نمی‌شود و شمارهٔ فاکتورش برای بقیه می‌ماند.
   */
  fun post(d: ShopData, o: PurchaseOrderDto, r: PoReceiptDto, today: String, now: Long): Posting {
    val todo = toPost(r)
    if (todo.isEmpty()) return Posting.Already
    val existing = d.purchases.associateBy { it.id }
    val missing = todo.filter { purchaseIdOf(r.id, it.lineId) !in existing }
    if (missing.isEmpty()) return Posting.Already
    if (d.suppliers.none { it.id == o.supplierId }) {
      return Posting.Failed("تأمین‌کنندهٔ «${o.supplierName}» در دفترِ این دستگاه نیست — اول همگام‌سازی کنید")
    }
    val byLine = o.lines.associateBy { it.id }
    val no = todo.firstNotNullOfOrNull { existing[purchaseIdOf(r.id, it.lineId)]?.invoiceNo?.takeIf { n -> n > 0 } }
      ?: PurchaseBook.nextNo(d)
    var cur = d
    val made = ArrayList<String>()
    for (p in missing) {
      val line = byLine[p.lineId] ?: return Posting.Failed("ردیفِ «${p.lineId}» در سفارش نیست")
      var k = 0
      val res = LedgerEngine.addPurchase(
        cur,
        LedgerEngine.PurchaseDraft(
          supplierId = o.supplierId, productId = line.productId, quantity = units(p.acceptedMilli), unit = line.unit,
          purchasePrice = asAfn(p.priceMinor), paidAmount = 0.0, date = today,
          notes = "سفارشِ خرید #${o.no} — رسید",
        ),
        today, now,
      ) { ledgerId(r.id, p.lineId, k++) }
      when (res) {
        is LedgerEngine.Result.Failed -> return Posting.Failed("«${line.name.ifBlank { line.productId }}»: ${res.message}")
        is LedgerEngine.Result.Ok -> { cur = res.data; made += res.id }
      }
    }
    val set = made.toSet()
    cur = cur.copy(purchases = cur.purchases.map { if (it.id in set) it.copy(invoiceNo = no) else it })
    return Posting.Ok(cur, no, made.size)
  }

  /* ── تطبیقِ سه‌طرفه (PUR-14) ───────────────────────────────────────── */

  /** فاکتورِ تأمین‌کننده در این برنامه = ردیف‌های خریدِ دفتر که از رسیدهای همین سفارش آمده‌اند */
  fun bills(d: ShopData, o: PurchaseOrderDto): List<PurchaseOrderCore.Bill> {
    val byId = d.purchases.associateBy { it.id }
    return o.receipts.flatMap { r ->
      toPost(r).mapNotNull { p ->
        byId[purchaseIdOf(r.id, p.lineId)]?.let { pu -> PurchaseOrderCore.Bill(p.lineId, pu.productId, milli(pu.quantity), minor(pu.totalAmount)) }
      }
    }
  }

  fun match(d: ShopData, o: PurchaseOrderDto): PurchaseOrderCore.Match =
    PurchaseOrderCore.match(core(o), coreReceipts(o), bills(d, o), LEDGER_TOLERANCE)

  /* ── پیشنهادِ ردیف (PUR-03) ────────────────────────────────────────── */

  data class Pick(val productId: String, val name: String, val unit: String, val qtyMilli: Long, val priceMinor: Long, val low: Boolean)

  /** آخرین فیِ خریدِ یک کالا از همین تأمین‌کننده؛ نبود ⇒ فیِ خریدِ کالا */
  fun lastPriceMinor(d: ShopData, supplierId: String, product: Product): Long =
    minor(d.purchases.filter { it.supplierId == supplierId && it.productId == product.id && it.quantity > 0 }
      .maxByOrNull { it.createdAt }?.purchasePrice ?: product.purchasePrice)

  /** مقدارِ پیشنهادی: تا «حدِ کم» و دست‌کم یک واحد (عددِ کامل) */
  fun suggestedQtyMilli(d: ShopData, product: Product): Long {
    val need = product.minStock - ShopStore.stock(d, product.id)
    return milli(max(1.0, ceil(need)))
  }

  /**
   *  «افزودنِ همهٔ کالاهای این تأمین‌کننده»: هر کالای فعالی که دست‌کم یک بار از او خریده شده، با
   *  آخرین فیِ او؛ `onlyLow` ⇒ فقط کالاهای به «حدِ کم» رسیده (پیشنهادِ امروزِ برنامه). کم‌موجودها بالا.
   */
  fun supplierPicks(d: ShopData, supplierId: String, onlyLow: Boolean = false): List<Pick> {
    val bought = d.purchases.filter { it.supplierId == supplierId && it.quantity > 0 }.mapTo(HashSet()) { it.productId }
    return d.products.filter { it.active && it.id in bought }.map { p ->
      val low = p.minStock > 0 && ShopStore.stock(d, p.id) <= p.minStock
      Pick(p.id, p.name, p.unit, suggestedQtyMilli(d, p), lastPriceMinor(d, supplierId, p), low)
    }.filter { !onlyLow || it.low }.sortedWith(compareByDescending<Pick> { it.low }.thenBy { it.name })
  }

  /* ── تاریخچهٔ قیمت (PUR-09) و پیشنهاد با فروش و زمانِ تأمین (PUR-10، PUR-03) ── */

  /** ردیف‌های خریدِ دفتر برای هستهٔ قیمت (سندِ کسرِ منفی را خودِ هسته کنار می‌گذارد) */
  fun buys(d: ShopData): List<SupplierPricing.Buy> = d.purchases.map {
    SupplierPricing.Buy(it.supplierId, it.productId, it.date, it.createdAt, milli(it.quantity), minor(it.purchasePrice))
  }

  /** فروشِ خالصِ (فروش − برگشتِ همان قلم) [windowDays] روزِ آخر تا امروز، بی فاکتورِ لغوشده */
  fun soldMilli(d: ShopData, today: String, windowDays: Int = ReorderCore.DEFAULT_WINDOW_DAYS): Map<String, Long> {
    val from = CivilDays.addDays(today, -(windowDays - 1)) ?: return emptyMap()
    val live = d.sales.filter { it.status != "cancelled" && it.date >= from && it.date <= today }.mapTo(HashSet()) { it.id }
    val out = HashMap<String, Long>()
    for (i in d.saleItems) {
      if (i.productId.isBlank() || i.saleId !in live) continue
      out[i.productId] = (out[i.productId] ?: 0L) + milli(i.quantity - i.returnedQty)
    }
    return out
  }

  /** «در راه»: باقی‌ماندهٔ سفارش‌های فرستاده/بخشی برای هر کالا */
  fun onOrderMilli(orders: List<PurchaseOrderDto>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (o in orders) {
      val st = status(o)
      if (st != PurchaseOrderCore.Status.SENT && st != PurchaseOrderCore.Status.PARTIAL) continue
      for (p in o.progress) out[p.productId] = (out[p.productId] ?: 0L) + p.remainingMilli
    }
    return out
  }

  /** زمانِ تأمینِ یک تأمین‌کننده از سفارش‌های فرستاده‌ای که رسید گرفته‌اند */
  fun leadTime(orders: List<PurchaseOrderDto>, supplierId: String): ReorderCore.Lead =
    ReorderCore.leadTime(orders.filter { it.supplierId == supplierId }.mapNotNull { o ->
      val sent = o.sentAt ?: return@mapNotNull null
      val first = o.firstReceiptAt ?: return@mapNotNull null
      ReorderCore.Delivery(sent, first)
    })

  data class Planned(val pick: Pick, val suggestion: ReorderCore.Suggestion)

  /**
   *  پیشنهادِ ردیف با فروشِ واقعی و زمانِ تأمینِ همین تأمین‌کننده (و «در راه»)؛ فقط مقدارِ بیش از صفر.
   *  کالای وزنی (کیلو، لیتر…) هزارم، بقیه عددِ کامل. سفارش خودکار ثبت نمی‌شود — فقط ردیفِ فرم.
   */
  fun planned(d: ShopData, supplierId: String, orders: List<PurchaseOrderDto>, today: String): List<Planned> {
    val lead = leadTime(orders, supplierId)
    val sold = soldMilli(d, today)
    val onOrder = onOrderMilli(orders)
    return supplierPicks(d, supplierId).mapNotNull { pk ->
      val p = d.products.find { it.id == pk.productId } ?: return@mapNotNull null
      val s = ReorderCore.suggest(
        ReorderCore.Input(
          onHandMilli = milli(ShopStore.stock(d, p.id)), onOrderMilli = onOrder[p.id] ?: 0L, minStockMilli = milli(p.minStock),
          soldMilli = sold[p.id] ?: 0L, leadDays = lead.days, wholeUnits = p.unit !in WEIGHED_UNITS,
        ),
      )
      if (s.qtyMilli > 0) Planned(pk.copy(qtyMilli = s.qtyMilli), s) else null
    }
  }

  /** واحدهایی که مقدارشان کسری است */
  val WEIGHED_UNITS = setOf("کیلوگرم", "کیلو", "گرم", "لیتر", "متر")
}
