package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrPurchaseOrders
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.stock.StockCore
import ir.vil3ntec.tohid.data.repo.PurchaseOrderRepository
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.qty
import kotlin.math.abs
import kotlin.math.floor

/*
 *  فرم‌های سفارشِ خرید — PUR-02 (فهرست و وضعیت، ساختن/ویرایش، فرستادن، لغو)، PUR-03 (ردیف از پیشنهادها و «همهٔ کالاهای
 *  این تأمین‌کننده»)، PUR-04 (دریافتِ کامل/جزئی با اسکن، خراب، باقی‌ماندهٔ باز) و PUR-14 (تطبیق). §۶.۶، S19.
 *
 *  پنجرهٔ کامپیوتر (`ui/screens/PurchaseOrders.kt`) و صفحه‌های گوشیِ نسخهٔ ۳ (`ui/v3/money/PurchaseOrderPages.kt`) هر دو همین
 *  یک منطق را صدا می‌زنند: سنجشِ فرم، مجوزِ هر کار، شمارشِ رسید و تطبیق از `PurchaseOrderCore` (همان بردارهای سرور) و
 *  `PurchaseOrderBook`. صفحه فقط می‌کشد. ⛔ سرور خودش دوباره می‌سنجد (نقش، وضعیت، اضافه، کلیدِ یکتا)؛ این‌جا فقط پیش از
 *  فرستادن همان را می‌گوید تا کاربر بی رفت‌وبرگشت بداند.
 */
object PoForms {

  /* ── حالتِ صفحه ─────────────────────────────────────────────────────── */

  enum class Gate { LOADING, OFFLINE, FAILED, OFF, DENIED, OPEN }

  /** بی اینترنت یا سرورِ دورازدسترس ⇒ «اینترنت وصل نیست»، نه خطای کلی */
  fun offline(f: ApiFailure?): Boolean = f is ApiFailure.Offline || f is ApiFailure.Unreachable || f is ApiFailure.Timeout

  /**
   *  پیش از دیدنِ داده: `GET /config` هنوز نیامده، نیامد (بی اینترنت یا خطا)، پرچمِ `shop.purchase_orders` خاموش، یا نقش
   *  کارِ همین صفحه را ندارد ([need]). همان درِ پنجرهٔ کامپیوتر (`PurchaseOrdersEntry`).
   */
  fun gate(cfg: PoConfigDto?, failure: ApiFailure?, need: (PoPermsDto) -> Boolean = { true }): Gate = when {
    cfg == null && failure != null -> if (offline(failure)) Gate.OFFLINE else Gate.FAILED
    cfg == null -> Gate.LOADING
    !cfg.enabled -> Gate.OFF
    !need(cfg.permissions) -> Gate.DENIED
    else -> Gate.OPEN
  }

  /** پس از درِ `config`: دادهٔ صفحه آمد ⇒ باز (حتی هنگامِ تازه‌کردن)؛ نیامد ⇒ بی اینترنت یا خطا؛ هنوز ⇒ در راه */
  fun loaded(configGate: Gate, has: Boolean, failure: ApiFailure?): Gate = when {
    configGate != Gate.OPEN -> configGate
    has -> Gate.OPEN
    failure != null -> if (offline(failure)) Gate.OFFLINE else Gate.FAILED
    else -> Gate.LOADING
  }

  /* ── فهرست: پالایه با وضعیتِ نمایشی ─────────────────────────────────── */

  enum class Filter(val wire: String?) {
    ALL(null), DRAFT("draft"), SENT("sent"), PARTIAL("partial"), COMPLETE("complete"), CANCELLED("cancelled");

    companion object {
      fun of(wire: String?): Filter = entries.firstOrNull { it.wire == wire } ?: ALL
    }
  }

  /**
   *  پالایهٔ سرور فقط وضعیتِ **ذخیره‌شده** را می‌شناسد (`draft`/`sent`/`cancelled`)؛ «بخشی» و «کامل» از رسیدها می‌آیند و
   *  هر دو «فرستاده»‌ی ذخیره‌شده‌اند ⇒ همان صفحهٔ «فرستاده» و پالایشِ دوم در برنامه ([shown]).
   */
  fun serverState(f: Filter): String? = when (f) {
    Filter.ALL -> null
    Filter.DRAFT -> PurchaseOrderCore.State.DRAFT
    Filter.SENT, Filter.PARTIAL, Filter.COMPLETE -> PurchaseOrderCore.State.SENT
    Filter.CANCELLED -> PurchaseOrderCore.State.CANCELLED
  }

  fun shown(orders: List<PurchaseOrderDto>, f: Filter): List<PurchaseOrderDto> =
    if (f == Filter.ALL) orders else orders.filter { PurchaseOrderBook.status(it).wire == f.wire }

  fun label(f: Filter): String =
    PurchaseOrderCore.Status.of(f.wire)?.let(PurchaseOrderBook::label) ?: Str.all

  /* ── مجوزِ هر کار (همان سرور: `stock.adjust` ⇒ manage، `stock.move` ⇒ receive) ── */

  data class Actions(val receive: Boolean, val send: Boolean, val edit: Boolean, val cancel: Boolean, val bill: Boolean)

  /** کار فقط وقتی هم نقش اجازه دارد و هم وضعیت (`allowed` ِ سرور از `PurchaseOrderCore.allowed`) */
  fun actions(o: PurchaseOrderDto, perms: PoPermsDto): Actions = Actions(
    receive = perms.receive && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.RECEIVE),
    send = perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.SEND),
    edit = perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.EDIT),
    cancel = perms.manage && PurchaseOrderBook.can(o, PurchaseOrderCore.Action.CANCEL),
    bill = PurchaseOrderBook.canBill(o, perms.manage),
  )

  /** لغوِ فرستاده/بخشی دلیل می‌خواهد، پیش‌نویس نه — همان `PurchaseOrderCore.cancel` */
  fun cancelNeedsReason(o: PurchaseOrderDto): Boolean =
    (PurchaseOrderCore.cancel(PurchaseOrderBook.core(o), PurchaseOrderBook.coreReceipts(o), null) as? PurchaseOrderCore.Outcome.Failed)
      ?.failure?.code == PurchaseOrderCore.Code.REASON_REQUIRED

  /* ── دو بار زدن ─────────────────────────────────────────────────────── */

  /**
   *  سدِ «دو بار زدن»: لمسِ دوم پیش از آن‌که صفحه دکمه را خاموش بکشد (همان فریم) هیچ درخواستی نمی‌فرستد. کلیدِ یکتای فرم
   *  (`Idempotency-Key` ِ ساختن، شناسهٔ رسید) جدا از این است و تلاشِ دوباره پس از قطعِ نت را همان سفارش/رسید نگه می‌دارد.
   */
  class SubmitGuard {
    var running: Boolean = false
      private set

    fun start(): Boolean {
      if (running) return false
      running = true
      return true
    }

    fun done() { running = false }
  }

  /* ── عدد ────────────────────────────────────────────────────────────── */

  /** عدد برای کادرِ ورودی: «۱۲» نه «12.0»، رقمِ لاتین (همان قاعدهٔ کادرهای انبار) */
  fun num(value: Double): String =
    if (value == floor(value) && abs(value) < 1e15) value.toLong().toString() else value.toString()

  private fun parse(raw: String): Double? = latinDigits(raw).trim().toDoubleOrNull()?.takeIf { it.isFinite() }

  /* ── ساختن و ویرایش (PUR-02/03) ─────────────────────────────────────── */

  data class EditRow(val key: Int, val productId: String, val name: String, val unit: String, val qty: String, val price: String)

  fun rowsOf(base: PurchaseOrderDto?): List<EditRow> = base?.lines.orEmpty().mapIndexed { i, l ->
    EditRow(i, l.productId, l.name, l.unit, num(PurchaseOrderBook.units(l.qtyMilli)), num(PurchaseOrderBook.asAfn(l.priceMinor)))
  }

  private fun nextKey(rows: List<EditRow>): Int = (rows.maxOfOrNull { it.key } ?: -1) + 1

  /** کالای تازه با مقدارِ پیشنهادی (تا «حدِ کم») و آخرین فیِ همین تأمین‌کننده؛ کالای پیش‌تر در سفارش ⇒ `null` */
  fun addProduct(d: ShopData, rows: List<EditRow>, supplierId: String, p: Product): List<EditRow>? {
    if (rows.any { it.productId == p.id }) return null
    val q = PurchaseOrderBook.suggestedQtyMilli(d, p)
    val pr = PurchaseOrderBook.lastPriceMinor(d, supplierId, p)
    return rows + EditRow(nextKey(rows), p.id, p.name, p.unit, num(PurchaseOrderBook.units(q)), num(PurchaseOrderBook.asAfn(pr)))
  }

  /** ردیف از پیشنهادها (`supplierPicks`/`planned`)؛ کالای پیش‌تر در سفارش کنار؛ هیچ تازه ⇒ همان ردیف‌ها */
  fun addPicks(rows: List<EditRow>, picks: List<PurchaseOrderBook.Pick>): List<EditRow> {
    var out = rows
    for (pk in picks) {
      if (out.any { it.productId == pk.productId }) continue
      out = out + EditRow(nextKey(out), pk.productId, pk.name, pk.unit, num(PurchaseOrderBook.units(pk.qtyMilli)), num(PurchaseOrderBook.asAfn(pk.priceMinor)))
    }
    return out
  }

  fun total(rows: List<EditRow>): Double = rows.sumOf { (parse(it.qty) ?: 0.0) * (parse(it.price) ?: 0.0) }

  sealed interface DraftCheck {
    data class Ok(val draft: PurchaseOrderRepository.Draft) : DraftCheck
    data class Bad(val message: String) : DraftCheck
  }

  /**
   *  فرم ⇐ پیش‌نویسِ سرور: تأمین‌کننده لازم، هر ردیف مقدارِ مثبت و فیِ نامنفی، و همان `checkOrder` ِ هسته؛ تاریخِ رسیدن
   *  همیشه ASCII `YYYY-MM-DD` (LOC-05) یا خالی.
   */
  fun draft(d: ShopData, supplierId: String, rows: List<EditRow>, expected: String, note: String): DraftCheck {
    val supplier = d.suppliers.find { it.id == supplierId } ?: return DraftCheck.Bad(StrPurchasePages.chooseSupplier)
    val lines = ArrayList<PoLineDto>(rows.size)
    for ((i, r) in rows.withIndex()) {
      val q = parse(r.qty)
      val p = parse(r.price)
      if (q == null || q <= 0) return DraftCheck.Bad(StrPurchasePages.rowBadQty(i + 1))
      if (p == null || p < 0) return DraftCheck.Bad(StrPurchasePages.rowBadPrice(i + 1))
      lines += PoLineDto(id = "l${i + 1}", productId = r.productId, name = r.name, unit = r.unit, qtyMilli = PurchaseOrderBook.milli(q), priceMinor = PurchaseOrderBook.minor(p))
    }
    val check = PurchaseOrderCore.checkOrder(
      PurchaseOrderCore.Order(supplier.id, PurchaseOrderCore.State.DRAFT, lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) }),
    )
    if (check != null) {
      return DraftCheck.Bad(if (check.code == PurchaseOrderCore.Code.LINES_REQUIRED) StrPurchasePages.needOneItem else StrPurchasePages.poInvalid(check.code))
    }
    val exp = latinDigits(expected).trim().ifBlank { null }
    if (exp != null && !StockCore.isoDateOk(exp)) return DraftCheck.Bad(StrPurchasePages.arrivalDateFormat)
    return DraftCheck.Ok(PurchaseOrderRepository.Draft(supplier.id, supplier.name, lines, exp, note.trim()))
  }

  /* ── دریافت (PUR-04) ────────────────────────────────────────────────── */

  data class RecvRow(val received: String = "", val damaged: String = "", val price: String = "")

  /** هر ردیف خالی، فی همان فیِ سفارش (شاگرد فی را عوض نمی‌کند؛ کادرش فقط برای مدیر است) */
  fun recvRows(o: PurchaseOrderDto): Map<String, RecvRow> =
    o.lines.associate { it.id to RecvRow(price = num(PurchaseOrderBook.asAfn(it.priceMinor))) }

  sealed interface Scan {
    data class Added(val rows: Map<String, RecvRow>, val lineId: String, val name: String, val received: String) : Scan
    data class NotInPo(val code: String) : Scan
  }

  /** اسکن: یک واحدِ کالای همان بارکد به «رسیده» ِ ردیفش؛ رقمِ فارسیِ بارکدخوان هم پذیرفته */
  fun scan(o: PurchaseOrderDto, rows: Map<String, RecvRow>, index: Map<String, String>, raw: String): Scan {
    val code = raw.trim()
    val pid = index[code] ?: index[latinDigits(code)]
    val line = o.lines.find { it.productId == pid } ?: return Scan.NotInPo(code)
    val cur = rows[line.id] ?: RecvRow()
    val next = num((parse(cur.received.ifBlank { "0" }) ?: 0.0) + 1.0)
    return Scan.Added(rows + (line.id to cur.copy(received = next)), line.id, line.name, next)
  }

  /** حالِ یک ردیف پس از همین رسید: مانده (باز)، کامل، یا بیش از باقی‌مانده — میلی‌واحد */
  data class After(val leftMilli: Long, val overMilli: Long) {
    val done: Boolean get() = leftMilli == 0L && overMilli == 0L
  }

  /** `null` ⇒ این ردیف چیزی نگرفته (یا عددش هنوز درست نیست) */
  fun after(o: PurchaseOrderDto, lineId: String, r: RecvRow): After? {
    val rec = parse(r.received.ifBlank { "0" }) ?: return null
    val dmg = parse(r.damaged.ifBlank { "0" }) ?: return null
    if (rec <= 0 || dmg < 0 || dmg > rec) return null
    val line = o.lines.find { it.id == lineId } ?: return null
    val before = o.progress.find { it.lineId == lineId }?.remainingMilli ?: line.qtyMilli
    val accepted = PurchaseOrderBook.milli(rec) - PurchaseOrderBook.milli(dmg)
    return After(leftMilli = maxOf(0L, before - accepted), overMilli = maxOf(0L, accepted - before))
  }

  sealed interface ReceiptCheck {
    data class Ok(
      val lines: List<PoReceiptLineDto>,
      /** ردیف‌های ثبت‌شدنی با اختلاف‌ها (کم/اضافه) از هسته؛ تکرارِ همان رسید ⇒ خالی */
      val posted: List<PurchaseOrderCore.Posted>,
      val totalMinor: Long,
      val freightMinor: Long,
    ) : ReceiptCheck
    data class Bad(val message: String) : ReceiptCheck
  }

  /**
   *  فرمِ رسید ⇐ ردیف‌های سرور، با همان `PurchaseOrderCore.receive` ِ سرور پیش از فرستادن: جزئی پذیرفته (باقی‌مانده باز)،
   *  خراب جدا از پذیرفته و هرگز بیش از رسیده، و بیش از باقی‌مانده فقط با [allowOver] (که سرور هم فقط از مدیر می‌پذیرد).
   *  ردیفِ صفر کنار می‌رود. [freight] کرایهٔ رسید (PUR-06، فقط مدیر؛ خالی = بی کرایه).
   */
  fun receipt(o: PurchaseOrderDto, rows: Map<String, RecvRow>, receiptId: String, allowOver: Boolean, freight: String = ""): ReceiptCheck {
    val lines = ArrayList<PoReceiptLineDto>()
    for ((i, l) in o.lines.withIndex()) {
      val r = rows[l.id] ?: continue
      val rec = parse(r.received.ifBlank { "0" })
      val dmg = parse(r.damaged.ifBlank { "0" })
      val price = parse(r.price)
      if (rec == null || rec < 0 || dmg == null || dmg < 0) return ReceiptCheck.Bad(StrPurchasePages.rowBadQty(i + 1))
      if (dmg > rec) return ReceiptCheck.Bad(StrPurchasePages.rowDamagedOver(i + 1))
      if (price == null || price < 0) return ReceiptCheck.Bad(StrPurchasePages.rowBadPrice(i + 1))
      if (rec == 0.0) continue
      lines += PoReceiptLineDto(l.id, PurchaseOrderBook.milli(rec), PurchaseOrderBook.milli(dmg), PurchaseOrderBook.minor(price))
    }
    if (lines.isEmpty()) return ReceiptCheck.Bad(StrPurchasePages.nothingEntered)
    val freightMinor = PurchaseOrderBook.freightMinorOf(freight) ?: return ReceiptCheck.Bad(StrPurchasePages.freightBad)
    val input = PurchaseOrderCore.Receipt(receiptId, lines.map { PurchaseOrderCore.ReceiptLine(it.lineId, it.receivedMilli, it.damagedMilli, it.priceMinor) })
    return when (val r = PurchaseOrderCore.receive(PurchaseOrderBook.core(o), PurchaseOrderBook.coreReceipts(o), input, allowOver)) {
      is PurchaseOrderCore.ReceiveResult.Ok -> ReceiptCheck.Ok(lines, r.lines, r.totalMinor, freightMinor)
      //  همان رسید پیش‌تر ثبت شده (تلاشِ دوباره پس از قطعِ نت) ⇒ سرور همان را برمی‌گرداند
      is PurchaseOrderCore.ReceiveResult.Duplicate -> ReceiptCheck.Ok(lines, emptyList(), 0L, freightMinor)
      is PurchaseOrderCore.ReceiveResult.Failed -> ReceiptCheck.Bad(refusal(o, r.failure))
    }
  }

  private fun refusal(o: PurchaseOrderDto, f: PurchaseOrderCore.Failure): String {
    val row = o.lines.indexOfFirst { it.id == f.lineId } + 1
    return when (f.code) {
      PurchaseOrderCore.Code.OVER_ORDERED -> {
        val left = o.progress.find { it.lineId == f.lineId }?.remainingMilli ?: o.lines.getOrNull(row - 1)?.qtyMilli ?: 0L
        StrPurchaseOrders.rowOver(row, qty(PurchaseOrderBook.units(left)))
      }
      PurchaseOrderCore.Code.DAMAGED_OVER_RECEIVED -> StrPurchasePages.rowDamagedOver(row)
      PurchaseOrderCore.Code.BAD_QTY -> StrPurchasePages.rowBadQty(row)
      PurchaseOrderCore.Code.BAD_PRICE -> StrPurchasePages.rowBadPrice(row)
      PurchaseOrderCore.Code.NOTHING_RECEIVED -> StrPurchasePages.nothingEntered
      PurchaseOrderCore.Code.NOT_SENT, PurchaseOrderCore.Code.CANCELLED, PurchaseOrderCore.Code.COMPLETE -> StrPurchaseOrders.notReceivable
      else -> if (row > 0) StrPurchaseOrders.rowRefused(row, f.code) else StrPurchasePages.poInvalid(f.code)
    }
  }

  /* ── تطبیق (PUR-14) ─────────────────────────────────────────────────── */

  data class MatchRow(
    val lineId: String?,
    val name: String,
    val unit: String,
    val orderedMilli: Long,
    val acceptedMilli: Long,
    val billedMilli: Long,
    /** مقدارِ فاکتورشده × فیِ سفارش */
    val expectedMinor: Long,
    val billedMinor: Long,
    /** متنِ هر اختلاف (خالی ⇒ درست) */
    val issues: List<String>,
  ) {
    val flagged: Boolean get() = issues.isNotEmpty()
  }

  data class MatchView(
    val rows: List<MatchRow>,
    val matched: Boolean,
    /** طرفِ سوم فاکتورِ خودِ تأمین‌کننده است (وگرنه فاکتورِ خریدِ دفتر از رسیدها) */
    val supplier: Boolean,
    val orderedMinor: Long,
    val acceptedMinor: Long,
    val billedMinor: Long,
    /** رسید یا فاکتوری هست؟ نه ⇒ چیزی برای تطبیق نیست */
    val hasData: Boolean,
  ) {
    val flaggedCount: Int get() = rows.count { it.flagged }
  }

  /** سفارش ⇔ رسید ⇔ فاکتور ردیف‌به‌ردیف، همان `PurchaseOrderBook.match` ِ کامپیوتر؛ ردیفِ اختلاف‌دار بالا */
  fun matchView(d: ShopData, o: PurchaseOrderDto): MatchView {
    val supplier = PurchaseOrderBook.hasSupplierBills(o)
    val m = PurchaseOrderBook.match(d, o)
    val names = d.products.associateBy { it.id }
    val rows = m.lines.map { ml ->
      val line = ml.lineId?.let { id -> o.lines.find { it.id == id } }
      val p = names[ml.productId]
      MatchRow(
        lineId = ml.lineId,
        name = line?.name?.ifBlank { null } ?: p?.name ?: ml.productId,
        unit = line?.unit ?: p?.unit.orEmpty(),
        orderedMilli = ml.orderedMilli, acceptedMilli = ml.acceptedMilli, billedMilli = ml.billedMilli,
        expectedMinor = ml.expectedMinor, billedMinor = ml.billedMinor,
        issues = ml.issues.map { PurchaseOrderBook.issueText(it, supplier) },
      )
    }.sortedBy { !it.flagged }
    return MatchView(rows, m.matched, supplier, m.orderedMinor, m.acceptedMinor, m.billedMinor, o.receipts.isNotEmpty() || supplier)
  }
}
