package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.i18n.StrGoods
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
  /**
   *  PRD-07 — ردیف به واحدِ خرید: `unit` نامِ همان واحد، `qtyMilli`/`priceMinor` و رسیدها به همان واحد، و این ضریب به واحدِ
   *  پایهٔ کالا (هزارم). ثبتِ رسید در دفتر مقدار × ضریب را به واحدِ پایه می‌برد (`post`). `null` = واحدِ پایه، همان پیشین
   *  (نوشته نمی‌شود؛ سرورِ پیش از آن هم نمی‌فرستد).
   */
  @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
  @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
  val factorMilli: Long? = null,
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
  /** PUR-06: کرایهٔ همین رسید به حمل‌کننده (نه تأمین‌کننده)؛ رسیدِ پیشین ۰ */
  val freightMinor: Long = 0,
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
  /** PUR-14: شمارِ فاکتورهای فعالِ تأمین‌کننده (فهرست هم دارد)؛ سرورِ پیشین ⇒ ۰ */
  val billCount: Int = 0,
  /** PUR-14: فاکتورهای جدای تأمین‌کننده با باطل‌شده‌ها — فقط در `GET /:id` و پاسخِ کارها */
  val bills: List<PoBillDto> = emptyList(),
)

/** ردیفِ فاکتورِ تأمین‌کننده (PUR-14): ردیفِ سفارش یا کالای بیرون از سفارش (`lineId = null`)؛ جمع همان که او نوشته */
@Serializable
data class PoBillLineDto(val lineId: String? = null, val productId: String = "", val qtyMilli: Long = 0, val totalMinor: Long = 0)

@Serializable
data class PoBillDto(
  val id: String = "",
  val poId: String = "",
  val billNo: String = "",
  val billDate: String? = null,
  val lines: List<PoBillLineDto> = emptyList(),
  val totalMinor: Long = 0,
  val note: String = "",
  val createdBy: String = "",
  val createdAt: Long = 0,
  val voidedAt: Long? = null,
  val voidReason: String? = null,
) {
  val active: Boolean get() = voidedAt == null
}

@Serializable
data class PoBilledDto(
  val order: PurchaseOrderDto = PurchaseOrderDto(),
  val bill: PoBillDto = PoBillDto(),
  val replayed: Boolean = false,
  val changed: Boolean = false,
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
    PurchaseOrderCore.Status.DRAFT -> StrGoods.poDraft
    PurchaseOrderCore.Status.SENT -> StrGoods.poSent
    PurchaseOrderCore.Status.PARTIAL -> StrGoods.poPartial
    PurchaseOrderCore.Status.COMPLETE -> StrGoods.poComplete
    PurchaseOrderCore.Status.CANCELLED -> StrGoods.poCancelled
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
   *
   *  کرایهٔ رسید (PUR-06، `freightMinor`) به نسبتِ ارزشِ **همهٔ** ردیف‌های پذیرفته پخش می‌شود
   *  (`StockLedger.spreadLanded`، جمع دقیقاً همان کرایه) و سهمِ هر ردیف روی بهای همان ردیف می‌نشیند — همان
   *  `landedCost` ِ فاکتورِ خریدِ دستی؛ به بدهیِ تأمین‌کننده اضافه نمی‌شود. ثبتِ دوبارهٔ بخشی همان سهم‌ها را دارد.
   */
  fun post(d: ShopData, o: PurchaseOrderDto, r: PoReceiptDto, today: String, now: Long): Posting {
    val todo = toPost(r)
    if (todo.isEmpty()) return Posting.Already
    val existing = d.purchases.associateBy { it.id }
    val missing = todo.filter { purchaseIdOf(r.id, it.lineId) !in existing }
    if (missing.isEmpty()) return Posting.Already
    if (d.suppliers.none { it.id == o.supplierId }) {
      return Posting.Failed(StrGoods.poSupplierMissing(o.supplierName))
    }
    val byLine = o.lines.associateBy { it.id }
    val no = todo.firstNotNullOfOrNull { existing[purchaseIdOf(r.id, it.lineId)]?.invoiceNo?.takeIf { n -> n > 0 } }
      ?: PurchaseBook.nextNo(d)
    var cur = d
    val made = ArrayList<String>()
    for (p in missing) {
      val line = byLine[p.lineId] ?: return Posting.Failed(StrGoods.poLineMissing(p.lineId))
      var k = 0
      val res = LedgerEngine.addPurchase(
        cur,
        LedgerEngine.PurchaseDraft(
          supplierId = o.supplierId, productId = line.productId, quantity = units(p.acceptedMilli), unit = line.unit,
          purchasePrice = asAfn(p.priceMinor), paidAmount = 0.0, date = today,
          notes = "سفارشِ خرید #${o.no} — رسید",  // داده: یادداشتِ سابقهٔ دفتر (ذخیره و همگام می‌شود)
          //  PRD-07: ردیفِ به واحدِ خرید ⇒ مقدار × ضریب به واحدِ پایه، با جمعِ دقیقِ پذیرفته به فیِ رسید
          inUnit = line.factorMilli?.takeIf { it > 0 }?.let { f ->
            LedgerEngine.InUnit(line.unit, p.acceptedMilli, f, ir.vil3ntec.tohid.core.product.PurchaseUnits.lineTotalMinor(p.acceptedMilli, p.priceMinor))
          },
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
    cur = withFreight(cur, r, todo, set)
    return Posting.Ok(cur, no, made.size)
  }

  /** سقفِ کرایه = سقفِ فیِ سرور (`MAX_PRICE_MINOR` ِ `purchase-orders.js`) */
  const val MAX_FREIGHT_MINOR = 100_000_000_000L

  /** متنِ کادرِ «کرایه» (رقمِ فارسی هم) ⇐ صدمِ افغانی؛ خالی ⇒ ۰؛ نادرست، منفی یا بیش از سقف ⇒ null */
  fun freightMinorOf(raw: String): Long? {
    val t = ir.vil3ntec.tohid.normalizeDigits(raw).trim()
    if (t.isEmpty()) return 0L
    val v = t.toDoubleOrNull() ?: return null
    if (!v.isFinite() || v < 0) return null
    val m = minor(v)
    return if (m > MAX_FREIGHT_MINOR) null else m
  }

  /** سهمِ کرایهٔ هر ردیفِ پذیرفته (کلید: شناسهٔ ردیفِ خریدِ دفتر)؛ بی کرایه ⇒ تهی */
  fun freightShares(r: PoReceiptDto): Map<String, Long> {
    val todo = toPost(r)
    if (r.freightMinor <= 0 || todo.isEmpty()) return emptyMap()
    val shares = StockLedger.spreadLanded(r.freightMinor, todo.map { it.totalMinor })
    return todo.zip(shares).associate { (p, share) -> purchaseIdOf(r.id, p.lineId) to share }
  }

  private fun withFreight(d: ShopData, r: PoReceiptDto, todo: List<PoPostedDto>, made: Set<String>): ShopData {
    if (todo.isEmpty()) return d
    val landed = freightShares(r).filter { (id, share) -> id in made && share > 0 }
    if (landed.isEmpty()) return d
    return d.copy(
      purchases = d.purchases.map { p -> landed[p.id]?.let { p.copy(landedCost = asAfn(it), landedCostMinor = it) } ?: p },
      warehouseEntries = d.warehouseEntries.map { w ->
        w.purchaseId?.let { landed[it] }?.let { w.copy(landedCost = asAfn(it), landedCostMinor = it) } ?: w
      },
    )
  }

  /* ── تطبیقِ سه‌طرفه (PUR-14) ───────────────────────────────────────── */

  /**
   *  طرفِ سوم: **فاکتورِ جدای تأمین‌کننده** (سرور، مهاجرتِ ۲۰۸) وقتی دست‌کم یکی فعال است؛ وگرنه — مثلِ پیش از آن —
   *  ردیف‌های خریدِ دفتر که از رسیدهای همین سفارش آمده‌اند. سرورِ پیشین `bills` نمی‌فرستد ⇒ همان رفتارِ پیشین.
   */
  fun bills(d: ShopData, o: PurchaseOrderDto): List<PurchaseOrderCore.Bill> {
    if (hasSupplierBills(o)) return supplierBills(o)
    val byId = d.purchases.associateBy { it.id }
    return o.receipts.flatMap { r ->
      toPost(r).mapNotNull { p ->
        byId[purchaseIdOf(r.id, p.lineId)]?.let { pu -> PurchaseOrderCore.Bill(p.lineId, pu.productId, milli(pu.quantity), minor(pu.totalAmount)) }
      }
    }
  }

  fun match(d: ShopData, o: PurchaseOrderDto): PurchaseOrderCore.Match =
    PurchaseOrderCore.match(core(o), coreReceipts(o), bills(d, o), if (hasSupplierBills(o)) BILL_TOLERANCE else LEDGER_TOLERANCE)

  /**
   *  مرزِ تطبیق با فاکتورِ کاغذیِ تأمین‌کننده: مقدار دقیق؛ مبلغِ هر ردیف تا یک افغانی (گردِ دستیِ فاکتور).
   *  بیشتر از آن «فی بالاتر/پایین‌تر از سفارش» است.
   */
  val BILL_TOLERANCE = PurchaseOrderCore.Tolerance(qtyMilli = 0, priceBp = 0, absMinor = 100)

  fun hasSupplierBills(o: PurchaseOrderDto): Boolean = o.bills.any { it.active }

  /**
   *  «فاکتورِ تأمین‌کننده» و «باطل» فقط برای مدیر و صاحب (`permissions.manage` ِ `GET /config` = `stock.adjust` ِ سرور) و
   *  فقط پس از فرستادن (سرور: پیش‌نویس ⇒ ۴۰۹ `not_sent`). کامپیوتر (`PoDetail`) و گوشیِ نسخهٔ ۳ (`SupplierBillRoute`) همین را می‌پرسند.
   */
  fun canBill(o: PurchaseOrderDto, manage: Boolean): Boolean = manage && status(o) != PurchaseOrderCore.Status.DRAFT

  /** «باطل» ِ یک فاکتور: فقط مدیر و فقط فاکتورِ هنوز فعال (باطل‌شده دوباره باطل نمی‌شود) */
  fun canVoid(b: PoBillDto, manage: Boolean): Boolean = manage && b.active

  /**
   *  فهرستِ «فاکتورهای تأمین‌کننده» ِ گوشی (PUR-14): سفارش‌هایی که فاکتور می‌گیرند (هر وضعیتی جز پیش‌نویس، لغوشده هم چون
   *  فاکتورِ پیشینش باطل‌شدنی است)؛ آن‌که هنوز رسیده ولی فاکتورِ فعال ندارد بالا، بعد تازه‌ترین تغییر.
   */
  fun billable(orders: List<PurchaseOrderDto>): List<PurchaseOrderDto> =
    orders.filter { status(it) != PurchaseOrderCore.Status.DRAFT }
      .sortedWith(compareBy<PurchaseOrderDto>({ !(it.billCount == 0 && it.totals.acceptedMinor > 0) }, { -it.updatedAt }, { it.id }))

  /** ردیف‌های همهٔ فاکتورهای فعالِ تأمین‌کننده ⇐ هسته (باطل‌شده‌ها هرگز) */
  fun supplierBills(o: PurchaseOrderDto): List<PurchaseOrderCore.Bill> =
    o.bills.filter { it.active }.flatMap { b -> b.lines.map { PurchaseOrderCore.Bill(it.lineId, it.productId, it.qtyMilli, it.totalMinor) } }

  /** فرمِ فاکتور: هر ردیفِ سفارش با مقدارِ پذیرفتهٔ رسیدها و مبلغِ «مقدار × فیِ سفارش»؛ ردیفِ بی پذیرفته هم (مقدارِ سفارش) */
  data class BillRow(val lineId: String, val productId: String, val name: String, val unit: String, val qty: String, val total: String)

  fun billRows(o: PurchaseOrderDto): List<BillRow> {
    val prog = o.progress.associateBy { it.lineId }
    return o.lines.map { l ->
      val q = prog[l.id]?.acceptedMilli?.takeIf { it > 0 } ?: l.qtyMilli
      BillRow(l.id, l.productId, l.name, l.unit, plain(units(q)), plain(asAfn(PurchaseOrderCore.lineTotalMinor(q, l.priceMinor))))
    }
  }

  /** فرم ⇐ ردیف‌های فاکتور؛ ردیفِ خالی یا صفر کنار؛ عددِ نادرست ⇐ `null` (صفحه پیام می‌دهد و چیزی نمی‌فرستد) */
  fun billLines(rows: List<BillRow>): List<PoBillLineDto>? {
    val out = ArrayList<PoBillLineDto>()
    for (r in rows) {
      val qt = ir.vil3ntec.tohid.normalizeDigits(r.qty).trim()
      if (qt.isEmpty()) continue
      val q = qt.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: return null
      if (q == 0.0) continue
      val tt = ir.vil3ntec.tohid.normalizeDigits(r.total).trim()
      val t = (if (tt.isEmpty()) 0.0 else tt.toDoubleOrNull())?.takeIf { it.isFinite() && it >= 0 } ?: return null
      out += PoBillLineDto(r.lineId, r.productId, milli(q), minor(t))
    }
    return out
  }

  /** سطرِ فهرستِ فاکتورها: شماره، تاریخ، جمع (و «باطل» برای باطل‌شده) */
  fun billLine(b: PoBillDto): String = buildString {
    append(StrGoods.billNo(b.billNo))
    b.billDate?.let { append(" · ").append(ir.vil3ntec.tohid.formatDate(it)) }
    append(" · ").append(ir.vil3ntec.tohid.money(asAfn(b.totalMinor))).append(" ؋")
    if (!b.active) append(StrGoods.voided)
  }

  /** سرخطِ تطبیقِ درست — با فاکتورِ تأمین‌کننده یا فاکتورِ دفتر */
  fun matchTitle(supplier: Boolean): String =
    if (supplier) StrGoods.matchOkSupplier else StrGoods.matchOk

  /** متنِ هر ناهمخوانی؛ «فاکتور» همان طرفِ سوم است (تأمین‌کننده یا دفتر) */
  fun issueText(i: PurchaseOrderCore.Issue, supplier: Boolean = false): String = when (i) {
    PurchaseOrderCore.Issue.RECEIVED_OVER_ORDER -> StrGoods.overReceived
    PurchaseOrderCore.Issue.BILLED_OVER_RECEIVED -> if (supplier) StrGoods.billedOverSupplier else StrGoods.billedOver
    PurchaseOrderCore.Issue.BILLED_UNDER_RECEIVED -> if (supplier) StrGoods.billedUnderSupplier else StrGoods.billedUnder
    PurchaseOrderCore.Issue.PRICE_OVER_ORDER -> StrGoods.priceOver
    PurchaseOrderCore.Issue.PRICE_UNDER_ORDER -> StrGoods.priceUnder
    PurchaseOrderCore.Issue.NOT_ON_ORDER -> StrGoods.notOnOrder
  }

  /** «۱۲» نه «12.0»؛ رقمِ لاتین تا کادر همان را نشان دهد که کاربر می‌نویسد */
  private fun plain(x: Double): String = if (x == kotlin.math.floor(x) && kotlin.math.abs(x) < 1e15) x.toLong().toString() else x.toString()

  /* ── پیشنهادِ ردیف (PUR-03) ────────────────────────────────────────── */

  data class Pick(val productId: String, val name: String, val unit: String, val qtyMilli: Long, val priceMinor: Long, val low: Boolean)

  /** آخرین فیِ خریدِ یک کالا از همین تأمین‌کننده (`SupplierPricing.last`، PUR-09)؛ نبود ⇒ فیِ خریدِ کالا */
  fun lastPriceMinor(d: ShopData, supplierId: String, product: Product, history: List<SupplierPricing.Buy> = buys(d)): Long =
    SupplierPricing.last(history, product.id, supplierId)?.priceMinor ?: minor(product.purchasePrice)

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
    val all = buys(d)
    return d.products.filter { it.active && it.id in bought }.map { p ->
      val low = p.minStock > 0 && ShopStore.stock(d, p.id) <= p.minStock
      Pick(p.id, p.name, p.unit, suggestedQtyMilli(d, p), lastPriceMinor(d, supplierId, p, all), low)
    }.filter { !onlyLow || it.low }.sortedWith(compareByDescending<Pick> { it.low }.thenBy { it.name })
  }

  /* ── تاریخچهٔ قیمت (PUR-09) و پیشنهاد با فروش و زمانِ تأمین (PUR-10، PUR-03) ── */

  /** ردیف‌های خریدِ دفتر برای هستهٔ قیمت (سندِ کسرِ منفی را خودِ هسته کنار می‌گذارد) */
  fun buys(d: ShopData): List<SupplierPricing.Buy> = d.purchases.map {
    SupplierPricing.Buy(it.supplierId, it.productId, it.date, it.createdAt, milli(it.quantity), minor(it.purchasePrice))
  }

  /** «▲ ۵٪» / «▼ ۲٫۵٪» / «بی تغییر» نسبت به خریدِ پیشینِ همان تأمین‌کننده (PUR-09)؛ بی پیشین ⇒ "" */
  fun changeText(p: SupplierPricing.Point): String {
    val bp = p.changeBp ?: return ""
    if (bp == 0L) return StrGoods.unchanged
    val pct = kotlin.math.abs(bp) / 100.0
    return (if (bp > 0) " · ▲ " else " · ▼ ") + ir.vil3ntec.tohid.qty(pct) + "٪"
  }

  /**
   *  PUR-09: متنِ زیرِ ردیفِ فرمِ خرید (سفارشِ خرید و فاکتورِ خریدِ دستی) — آخرین فیِ **همین** تأمین‌کننده
   *  برای همین کالا، با تاریخ و تغییر؛ کالا یا تأمین‌کننده انتخاب نشده ⇒ `null`.
   */
  fun lastPriceHint(buys: List<SupplierPricing.Buy>, productId: String?, supplierId: String?): String? {
    if (productId.isNullOrBlank() || supplierId.isNullOrBlank()) return null
    val last = SupplierPricing.last(buys, productId, supplierId) ?: return NO_BUY_YET
    return StrGoods.lastPrice(ir.vil3ntec.tohid.money(asAfn(last.priceMinor)), ir.vil3ntec.tohid.formatDate(last.date)) + changeText(last)
  }

  val NO_BUY_YET: String get() = StrGoods.noBuyYet

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

  /**
   *  «در راه»: باقی‌ماندهٔ سفارش‌های فرستاده/بخشی برای هر کالا، **به واحدِ پایهٔ کالا** (همان واحدِ موجودی): ردیفِ به واحدِ
   *  خرید (PRD-07، `PoLineDto.factorMilli`) ⇒ باقی‌مانده × ضریب (۲ کارتنِ ۲۴ ⇒ ۴۸)؛ پیش‌تر ۲ ⇒ پیشنهاد بیش از نیاز.
   */
  fun onOrderMilli(orders: List<PurchaseOrderDto>): Map<String, Long> {
    val out = HashMap<String, Long>()
    for (o in orders) {
      val st = status(o)
      if (st != PurchaseOrderCore.Status.SENT && st != PurchaseOrderCore.Status.PARTIAL) continue
      val factor = o.lines.associate { it.id to it.factorMilli }
      for (p in o.progress) {
        val f = factor[p.lineId]?.takeIf { it > 0 }
        val base = if (f == null) p.remainingMilli else ir.vil3ntec.tohid.core.product.PurchaseUnits.baseQtyMilli(p.remainingMilli, f)
        out[p.productId] = (out[p.productId] ?: 0L) + base
      }
    }
    return out
  }

  /** `status` ِ سرور برای «فرستاده یا بخشی» (PUR-03: «در راه») */
  const val OPEN_STATUS = "open"

  /** سقفِ صفحه‌های «باز» در یک بار (۵۰ × ۴۰ = ۲۰۰۰ سفارشِ باز) */
  const val PLANNING_PAGES = 40

  /**
   *  PUR-03/10: سفارش‌های لازمِ پیشنهاد — صفحهٔ نخستِ فرستاده‌ها (تازه‌ترین تحویل‌ها برای [leadTime]) + **همهٔ** صفحه‌های
   *  «باز» (`status=open`، برای [onOrderMilli]) تا [PLANNING_PAGES]، بی تکرار (نسخهٔ صفحهٔ «باز» می‌ماند). [fetch]
   *  (نشانگر، باز؟) ⇒ یک صفحه یا `null` (شکست ⇒ آنچه رسید). سرورِ کهنه `status` نمی‌شناسد و همهٔ فرستاده‌ها را صفحه به صفحه
   *  می‌دهد ⇒ همان درست (کامل‌ها را [onOrderMilli] کنار می‌گذارد)، فقط بیشتر.
   */
  suspend fun planningOrders(fetch: suspend (cursor: String?, open: Boolean) -> PoPageDto?): List<PurchaseOrderDto> {
    val out = LinkedHashMap<String, PurchaseOrderDto>()
    fetch(null, false)?.orders?.forEach { out[it.id] = it }
    var cursor: String? = null
    for (i in 0 until PLANNING_PAGES) {
      val page = fetch(cursor, true) ?: break
      page.orders.forEach { out[it.id] = it }
      cursor = page.next ?: break
    }
    return out.values.toList()
  }

  /**
   *  زمانِ تأمینِ یک تأمین‌کننده از سفارش‌های فرستاده‌ای که رسید گرفته‌اند؛ [manualDays] (PUR-10،
   *  `Supplier.leadDays`) اگر نوشته شده بر آن می‌چربد.
   */
  fun leadTime(orders: List<PurchaseOrderDto>, supplierId: String, manualDays: Int? = null): ReorderCore.Lead =
    ReorderCore.leadTime(orders.filter { it.supplierId == supplierId }.mapNotNull { o ->
      val sent = o.sentAt ?: return@mapNotNull null
      val first = o.firstReceiptAt ?: return@mapNotNull null
      ReorderCore.Delivery(sent, first)
    }, manualDays)

  /** PUR-10 — زمانِ دستیِ تأمین‌کننده از دفتر */
  fun manualLead(d: ShopData, supplierId: String): Int? = d.suppliers.firstOrNull { it.id == supplierId }?.leadDays

  data class Planned(val pick: Pick, val suggestion: ReorderCore.Suggestion)

  /**
   *  پیشنهادِ ردیف با فروشِ واقعی و زمانِ تأمینِ همین تأمین‌کننده (و «در راه»)؛ فقط مقدارِ بیش از صفر.
   *  کالای وزنی (کیلو، لیتر…) هزارم، بقیه عددِ کامل. سفارش خودکار ثبت نمی‌شود — فقط ردیفِ فرم.
   *  ⚠️ ۳-۱۷ (PUR-03 الف): فرم‌های سفارش دیگر این حسابِ ۳۰ روزه را نمی‌پرسند — `PurchaseSuggest.forSupplier` (همان پیشنهادِ صفحهٔ
   *  «پیشنهادِ سفارش»)؛ این فقط مرجعِ `ReorderCore` ِ PUR-10 مانده است.
   */
  fun planned(d: ShopData, supplierId: String, orders: List<PurchaseOrderDto>, today: String): List<Planned> {
    val lead = leadTime(orders, supplierId, manualLead(d, supplierId))
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
  val WEIGHED_UNITS = setOf("کیلوگرم", "کیلو", "گرم", "لیتر", "متر")  // داده: نامِ واحدِ ذخیره‌شده برای سنجش
}
