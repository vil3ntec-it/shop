package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.PaidRow
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.RefundInput
import ir.vil3ntec.tohid.core.text.LocaleFormat
import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import kotlin.math.max
import kotlin.math.roundToLong

/**
 *  برگشت و تعویض — RET-01..08، 10، 12، 13 (docs/spec/06-RET.md، سناریوهای S13–S15).
 *
 *  کاتلینِ خالص، مشترکِ گوشی و کامپیوتر؛ با بردارِ دستیِ
 *  `core/test-vectors/returns-v1.json` سنجیده می‌شود (`ReturnFlowTest`). وبِ مدیریتِ
 *  دکان دیگر ساخته نمی‌شود (دستورِ صاحبِ مخزن، ۱۴۰۵/۰۷/۲۷)، پس همزادِ JS ندارد.
 *
 *  فقط وقتی `ReturnSettings.enabled` روشن است صدا زده می‌شود؛ خاموش ⇒ همان
 *  `SalesEngine.recordReturn`ِ امروز (`SalesReturnTest` و آزمونِ رفتارِ امروز).
 *
 *  ── سندِ ثبت‌شده عوض نمی‌شود ───────────────────────────────────────
 *  برگشت سندِ تازه است: چند ردیفِ `SaleReturn` با یک `docId`، و کنارشان حرکتِ
 *  انبار، تراکنشِ حساب و سابقه. قیمت، اقلام، جمع و وضعیتِ فاکتور دست نمی‌خورد.
 *  تنها دو شمارنده بالا می‌روند — `SaleItem.returnedQty` و `Sale.debtSettled` —
 *  همان دو که برگشتِ امروز هم بالا می‌برد و همگام‌سازی آن‌ها را «افزایشی»
 *  می‌فرستد (`LedgerDiff.INC_FIELDS`)، نه بازنویسی.
 *
 *  ── پول ────────────────────────────────────────────────────────────
 *  مبلغِ هر قلم همان فرمولِ امروز است: قیمتِ همان قلم در همان فاکتور × سهمِ
 *  پرداخت‌شده پس از تخفیف (RET-03)، گرد به افغانیِ کامل. جمعِ همهٔ برگشت‌های
 *  یک فاکتور از مبلغِ نهاییِ آن بیشتر نمی‌شود (اضافهٔ گرد از قلمِ آخر کم می‌شود).
 *  تقسیمِ بازپرداخت با `Pay.refund`ِ هستهٔ پرداخت (#237): اول بدهیِ بازِ همین
 *  فاکتور (مثلِ امروز)، بقیه به روشِ اصلی، نقد یا اعتبارِ فروشگاه.
 *  اعتبارِ فروشگاه = «رسید» روی حسابِ مشتری (ماندهٔ منفی = طلبِ مشتری)؛ پس صندوق
 *  همان حسابِ امروز را دارد: برگشت از نقد کم می‌شود و رسید به آن اضافه، و فقط
 *  سهمِ نقد واقعاً از کشو بیرون می‌رود.
 */
object ReturnFlow {

  const val CODE_PREFIX = "TR1:"
  const val CODE_LABEL_PREFIX = "TR1#"
  /** بیشترین طولِ متنِ QR که `core/qr/QrCode` (نسخهٔ ۳، سطحِ M) می‌پذیرد */
  const val CODE_MAX = 42

  const val RESTOCK = "restock"
  const val DAMAGED = "damaged"

  const val ORIGINAL = "original"
  const val CASH = "cash"
  const val STORE_CREDIT = "store_credit"

  /**
   *  `DebtTransaction.kind`ِ رسیدِ حسابی که سندِ برگشت می‌سازد (واژهٔ #286) — سهمِ اعتبارِ فروشگاه
   *  هم، نه `store_credit`: کلِ مرجوعی از دخل کم می‌شود ([drawerAmount]) و همین رسید آن را خنثی
   *  می‌کند، ولی `store_credit` (پرداخت با اعتبار) را صندوق نقد نمی‌شمارد و دخل کم می‌آمد.
   *  `refId`ِ همهٔ ردیف‌های انبار و حسابِ سند = `docId` (نگهبانِ کارمندِ سرور، #339).
   */
  const val RECEIPT_KIND = "customer_return"

  /** RET-08 — فهرستِ پیش‌فرضِ دلیل‌ها؛ دلیل اجباری است */
  val REASONS: List<String> = listOf(
    "خراب یا معیوب",
    "اشتباه در فروش",
    "پشیمانیِ مشتری",
    "تاریخ گذشته",
    "اندازه یا مدلِ نامناسب",
    "دیگر",
  )

  /** کدهای خطا — همان کدهای JS؛ متنِ فارسی با [message] */
  object Code {
    const val BAD_REQUEST = "BAD_REQUEST"
    const val ALREADY_POSTED = "ALREADY_POSTED"
    const val SALE_NOT_FOUND = "SALE_NOT_FOUND"
    const val SALE_CANCELLED = "SALE_CANCELLED"
    const val REASON_REQUIRED = "REASON_REQUIRED"
    const val NOTHING_TO_RETURN = "NOTHING_TO_RETURN"
    const val QTY_EXCEEDS = "QTY_EXCEEDS"
    const val BAD_QTY = "BAD_QTY"
    const val BAD_PRICE = "BAD_PRICE"
    const val UNKNOWN_PRODUCT = "UNKNOWN_PRODUCT"
    const val BAD_DISPOSITION = "BAD_DISPOSITION"
    const val BAD_MODE = "BAD_MODE"
    const val CUSTOMER_REQUIRED = "CUSTOMER_REQUIRED"
    const val RETURN_DENIED = "RETURN_DENIED"
    const val APPROVAL_REQUIRED = "APPROVAL_REQUIRED"
    const val REFUND_FAILED = "REFUND_FAILED"
    const val EXCHANGE_FAILED = "EXCHANGE_FAILED"
  }

  fun message(code: String, detail: String = ""): String = when (code) {
    Code.ALREADY_POSTED -> "این برگشت یک بار ثبت شده است و دوباره ثبت نمی‌شود"
    Code.SALE_NOT_FOUND -> "فاکتور پیدا نشد"
    Code.SALE_CANCELLED -> "این فاکتور لغو شده است؛ برگشت نمی‌پذیرد"
    Code.REASON_REQUIRED -> "دلیلِ برگشت را انتخاب کنید"
    Code.NOTHING_TO_RETURN -> "مقدار معتبری برای برگشت وارد نشده"
    Code.QTY_EXCEEDS -> "بیش از مقدارِ فروخته‌شده برنمی‌گردد" + if (detail.isNotEmpty()) " ($detail)" else ""
    Code.BAD_QTY -> "مقدار درست نیست" + if (detail.isNotEmpty()) " — $detail" else ""
    Code.BAD_PRICE -> "قیمتِ برگشت درست نیست"
    Code.UNKNOWN_PRODUCT -> "کالا پیدا نشد"
    Code.BAD_DISPOSITION -> "مقصدِ کالا را انتخاب کنید"
    Code.BAD_MODE -> "روشِ بازپرداخت درست نیست"
    Code.CUSTOMER_REQUIRED -> "برای اعتبارِ فروشگاه، مشتری را انتخاب کنید"
    Code.RETURN_DENIED -> "این برگشت برای شما مجاز نیست"
    Code.APPROVAL_REQUIRED -> "این برگشت تأیید لازم دارد"
    Code.REFUND_FAILED -> "بازپرداخت حساب نشد" + if (detail.isNotEmpty()) " ($detail)" else ""
    Code.EXCHANGE_FAILED -> detail.ifEmpty { "فروشِ تعویض ثبت نشد" }
    else -> "درخواستِ برگشت درست نیست"
  }

  sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Bad(val code: String, val detail: String = "") : Outcome<Nothing> {
      val message: String get() = message(code, detail)
    }
  }

  /* ============================ پیدا کردنِ رسید ============================ */

  /** متنِ QRِ رسیدِ فروش — شناسهٔ فاکتور؛ اگر بلند بود، شمارهٔ نمایشی */
  fun receiptCode(sale: Sale): String {
    val byId = CODE_PREFIX + sale.id
    if (byId.encodeToByteArray().size <= CODE_MAX) return byId
    return CODE_LABEL_PREFIX + (sale.invoiceLabel() ?: sale.id.takeLast(20))
  }

  /**
   *  RET-01 — اسکنِ QRِ رسید، شمارهٔ فاکتور (`B1-154`، `154`، `#154`، با رقمِ
   *  فارسی هم) یا نام/تلفنِ مشتری. تازه‌ترین اول. فاکتورِ لغوشده هم پیدا
   *  می‌شود (تا بگوییم چرا برنمی‌گردد)، ولی برگشت نمی‌پذیرد.
   */
  fun find(d: ShopData, query: String, limit: Int = 20): List<Sale> {
    val raw = LocaleFormat.asciiDigits(query).trim()
    if (raw.isEmpty()) return emptyList()
    if (raw.startsWith(CODE_PREFIX)) {
      val id = raw.removePrefix(CODE_PREFIX).trim()
      return d.sales.filter { it.id == id }
    }
    val label = raw.removePrefix(CODE_LABEL_PREFIX).removePrefix("#").trim()
    val byNumber = d.sales.filter { s ->
      val l = s.invoiceLabel() ?: return@filter false
      l.equals(label, ignoreCase = true) || (label.all { it.isDigit() } && s.invoiceNumber?.toString() == label)
    }
    if (byNumber.isNotEmpty()) return byNumber.sortedByDescending { it.createdAt }.take(limit)
    if (raw.startsWith(CODE_LABEL_PREFIX)) return emptyList()

    val phoneQuery = LocaleFormat.normalizePhone(raw)
    val who = d.debtors.filter { c ->
      TextNormalizer.matches(c.name, raw) ||
        (raw.length >= 4 && c.phone.isNotBlank() && (
          LocaleFormat.asciiDigits(c.phone).contains(raw) ||
            (phoneQuery != null && LocaleFormat.normalizePhone(c.phone) == phoneQuery)
          ))
    }.map { it.id }.toSet()
    if (who.isEmpty()) return emptyList()
    return d.sales.filter { it.debtorId in who }.sortedByDescending { it.createdAt }.take(limit)
  }

  /* ================================ مبلغ ================================ */

  data class Pick(val qty: Double, val disposition: String = RESTOCK)

  /** قلمِ برگشتِ بی رسید (RET-04) */
  data class FreeLine(val productId: String, val qty: Double, val unitPrice: Double, val disposition: String = RESTOCK)

  data class Line(
    val saleItemId: String,
    val productId: String,
    val name: String,
    val qty: Double,
    val unitPrice: Double,
    val amount: Double,
    val disposition: String,
  )

  data class Quote(val lines: List<Line>, val total: Double)

  fun returnable(item: SaleItem): Double = SalesEngine.returnable(item)

  /** جمعِ پس‌داده از یک فاکتور تا امروز (هر دو نوعِ برگشت) */
  fun returnedAmount(d: ShopData, saleId: String): Double =
    d.saleReturns.filter { it.saleId == saleId }.sumOf { it.amount }

  private fun okDisposition(v: String) = v == RESTOCK || v == DAMAGED

  /**
   *  مبلغِ برگشت از یک فاکتور — همان فرمولِ `recordReturn`ِ امروز (RET-02، 03).
   *  مقدارِ بیش از «قابلِ برگشت» رد می‌شود (نه بی‌صدا بریده): صندوقدار باید بداند.
   */
  fun quote(d: ShopData, saleId: String, picks: Map<String, Pick>): Outcome<Quote> {
    val sale = d.sales.find { it.id == saleId } ?: return Outcome.Bad(Code.SALE_NOT_FOUND)
    if (sale.status == "cancelled") return Outcome.Bad(Code.SALE_CANCELLED)
    val paidRatio =
      if (sale.total > 0 && sale.discount > 0) ((sale.total - sale.discount) / sale.total).coerceIn(0.0, 1.0) else 1.0
    val lines = ArrayList<Line>()
    for (item in d.saleItems) {
      if (item.saleId != saleId) continue
      val pick = picks[item.id] ?: continue
      val q = pick.qty
      if (q.isNaN() || q.isInfinite() || q < 0) return Outcome.Bad(Code.BAD_QTY)
      if (q == 0.0) continue
      if (!okDisposition(pick.disposition)) return Outcome.Bad(Code.BAD_DISPOSITION)
      val allowed = returnable(item)
      val name = SalesEngine.itemName(d, item)
      if (q > allowed + 1e-9) return Outcome.Bad(Code.QTY_EXCEEDS, "$name: ${qty(allowed)}")
      val product = d.products.find { it.id == item.productId }
      if (product != null) SalesEngine.quantityProblem(product, q)?.let { return Outcome.Bad(Code.BAD_QTY, it) }
      val amount = if (item.quantity > 0) (item.totalPrice / item.quantity * q * paidRatio).roundToLong().toDouble() else 0.0
      val unit = if (item.quantity > 0) item.totalPrice / item.quantity else item.unitPrice
      lines += Line(item.id, item.productId, name, q, unit, amount, pick.disposition)
    }
    if (lines.isEmpty()) return Outcome.Bad(Code.NOTHING_TO_RETURN)
    //  گردکردنِ هر قلم نباید جمعِ برگشت‌ها را از مبلغِ نهاییِ فاکتور بیشتر کند
    val cap = max(0.0, sale.finalTotal - returnedAmount(d, saleId))
    var total = lines.sumOf { it.amount }
    if (total > cap) {
      var extra = total - cap
      for (i in lines.indices.reversed()) {
        if (extra <= 0) break
        val cut = minOf(extra, lines[i].amount)
        lines[i] = lines[i].copy(amount = lines[i].amount - cut)
        extra -= cut
      }
      total = lines.sumOf { it.amount }
    }
    return Outcome.Ok(Quote(lines, total))
  }

  /** RET-04 — برگشتِ بی رسید: کالا از فهرست، قیمتِ واحد (پیش‌فرض قیمتِ امروزِ کالا) */
  fun quoteNoReceipt(d: ShopData, free: List<FreeLine>): Outcome<Quote> {
    val lines = ArrayList<Line>()
    for (f in free) {
      val product = d.products.find { it.id == f.productId } ?: return Outcome.Bad(Code.UNKNOWN_PRODUCT)
      if (f.qty.isNaN() || f.qty.isInfinite() || f.qty < 0) return Outcome.Bad(Code.BAD_QTY)
      if (f.qty == 0.0) continue
      SalesEngine.quantityProblem(product, f.qty)?.let { return Outcome.Bad(Code.BAD_QTY, it) }
      if (f.unitPrice.isNaN() || f.unitPrice.isInfinite() || f.unitPrice < 0) return Outcome.Bad(Code.BAD_PRICE)
      if (!okDisposition(f.disposition)) return Outcome.Bad(Code.BAD_DISPOSITION)
      val amount = (f.unitPrice * f.qty).roundToLong().toDouble()
      lines += Line("", product.id, product.name, f.qty, f.unitPrice, amount, f.disposition)
    }
    if (lines.isEmpty()) return Outcome.Bad(Code.NOTHING_TO_RETURN)
    return Outcome.Ok(Quote(lines, lines.sumOf { it.amount }))
  }

  /* ============================== مجوز ============================== */

  /**
   *  RET-04، RET-07 (ضایعات با مجوز)، RET-10 — چه کسی بی تأیید برمی‌گرداند.
   *
   *  `decision`: allow | approve | deny. `approver`: کمترین نقشی که می‌تواند تأیید
   *  کند (`manager` یا `owner`). بالای سقف فقط صاحبِ دکان (S14: «حتی با تأییدِ
   *  عادی رد»).
   */
  data class Gate(val decision: String, val reasons: List<String>, val approver: String)

  const val WHY_NO_RECEIPT = "no_receipt"
  const val WHY_DAMAGED = "damaged"
  const val WHY_OVER_LIMIT = "over_limit"
  const val WHY_PERM = "perm_denied"

  private val RANK = mapOf("staff" to 1, "manager" to 2, "owner" to 3)

  /** نقشِ ناشناخته ⇒ شاگرد (بسته‌به‌خطا)؛ خالی ⇒ صاحب (همان قاعدهٔ `ShopRole.current`) */
  fun roleOf(role: String?): String {
    val r = role?.trim()?.lowercase().orEmpty()
    if (r.isEmpty()) return "owner"
    return if (r in RANK) r else "staff"
  }

  /**
   *  @param perm تصمیمِ `PermCore` برای `returns.create` با همین مبلغ (allow |
   *    approve | deny)، یا `null` وقتی هسته هنوز این کلید را ندارد (پیش از PRِ
   *    اجرای مجوزها). صفحه آن را با [permDecision] می‌سازد.
   *  @param limitMinor سقفِ دکان برای مدیر و شاگرد (به پول)؛ ۰ = بی سقف.
   */
  fun gate(role: String?, perm: String?, amountMinor: Long, noReceipt: Boolean, damaged: Boolean, limitMinor: Long): Gate {
    val r = roleOf(role)
    if (perm == "deny") return Gate("deny", listOf(WHY_PERM), "owner")
    val why = ArrayList<String>()
    if (perm == "approve" || (r != "owner" && limitMinor > 0 && amountMinor > limitMinor)) why += WHY_OVER_LIMIT
    if (noReceipt && r == "staff") why += WHY_NO_RECEIPT
    if (damaged && r == "staff") why += WHY_DAMAGED
    val approver = if (WHY_OVER_LIMIT in why) "owner" else "manager"
    return Gate(if (why.isEmpty()) "allow" else "approve", why, approver)
  }

  /** آیا این نقش این برگشت را تأیید می‌کند */
  fun canApprove(gate: Gate, approverRole: String?): Boolean {
    if (gate.decision == "deny") return false
    if (gate.decision == "allow") return true
    return (RANK[roleOf(approverRole)] ?: 0) >= (RANK[gate.approver] ?: 3)
  }

  /**
   *  تصمیمِ هستهٔ مجوز برای `returns.create`؛ `null` وقتی این کلید هنوز در
   *  `PermCore` نیست. ⛔ قلابِ تأیید (STF-02): وقتی PRِ «اجرای مجوزها» برسد، همین
   *  تابع سقفِ نقش را از لایه‌های سرور می‌خواند و `ReturnApproval` برگهٔ امضاشده را.
   */
  fun permDecision(role: String?, layers: List<Any?>?, amountMinor: Long): String? {
    val core = ir.vil3ntec.tohid.core.perm.PermCore
    if ("returns.create" !in core.keys()) return null
    return core.evaluate(roleOf(role), layers, "returns.create", mapOf("amount" to amountMinor.toDouble())).decision
  }

  /** چه کسی تأیید کرد — نام و نقش */
  data class Approval(val by: String, val role: String)

  /* ============================ بازپرداخت ============================ */

  /**
   *  سهمِ هر مقصد (افغانی): از بدهیِ همین فاکتور، اعتبارِ فروشگاه، نقدِ دخل، و
   *  [methods] = روش‌های غیرنقدِ اصلی (شناسهٔ روش ⇒ مبلغ) که از دخل بیرون نمی‌روند.
   */
  data class Split(val account: Double, val credit: Double, val cash: Double, val methods: Map<String, Double> = emptyMap())

  fun minor(afn: Double): Long = Math.round(afn * 100)
  private fun major(m: Long): Double = m / 100.0

  /** وضع‌هایی که پولشان سرِ صندوق گرفته شده: «در انتظار»ِ ثبتِ دستیِ غیرنقد (PAY-08) هم */
  private val TAKEN = setOf("confirmed", "partially_refunded", "pending")

  /**
   *  ردیف‌های پرداختِ فاکتور از نگاهِ بازپرداخت.
   *
   *  با `Sale.payments` (پرداختِ ترکیبی، #286): هر ردیف به روشِ خودش؛ آنچه پیش‌تر به همان
   *  روشِ غیرنقد پس رفته (`SaleReturn.toMethods`ِ برگشت‌های قبلی) از آن کم می‌شود تا
   *  هیچ روشی بیش از دریافتی‌اش پس نگیرد. بی `payments` (فاکتورِ امروز): پرداختیِ همان
   *  لحظه = نقد، بدهی = «به حساب». بی رسید ⇒ فقط نقد.
   */
  private fun paidRows(sale: Sale?, amount: Double, prior: List<SaleReturn>): Pair<List<PaidRow>, List<PayMethod>?> {
    if (sale == null) return listOf(PaidRow(Pay.CASH, minor(amount), 0, "confirmed")) to null
    val list = sale.payments?.filter { it.amountMinor > 0 }
    if (list.isNullOrEmpty()) {
      val paid = minor(sale.paidAmount).coerceAtLeast(0)
      val debt = minor(sale.debtGiven).coerceAtLeast(0)
      return buildList {
        add(PaidRow(Pay.CASH, max(paid, if (debt == 0L) minor(sale.finalTotal) else paid), 0, "confirmed"))
        if (debt > 0) add(PaidRow(Pay.ACCOUNT, debt, 0, "confirmed"))
      } to null
    }
    val used = HashMap<String, Long>()
    prior.forEach { r -> r.toMethods?.forEach { (m, a) -> used[m] = (used[m] ?: 0L) + minor(a) } }
    val known = Pay.DEFAULT_METHODS.associateBy { it.id }
    val extra = LinkedHashMap<String, PayMethod>()
    val rows = list.map { p ->
      val id = when {
        p.kind == Pay.CASH -> Pay.CASH
        p.method == Pay.ACCOUNT || p.kind == Pay.ACCOUNT -> Pay.ACCOUNT
        else -> p.method
      }
      if (id != Pay.CASH && id != Pay.ACCOUNT && id !in known && id !in extra) {
        extra[id] = PayMethod(id, if (p.kind in Pay.KINDS && p.kind != Pay.CASH) p.kind else "other", id)
      }
      val status = if (p.status in TAKEN) "confirmed" else p.status
      val back = if (id == Pay.CASH || id == Pay.ACCOUNT) 0L else minOf(used[id] ?: 0L, p.amountMinor)
      if (back > 0) used[id] = (used[id] ?: 0L) - back
      PaidRow(id, p.amountMinor, back, status)
    }
    return rows to (if (extra.isEmpty()) null else Pay.DEFAULT_METHODS + extra.values)
  }

  /**
   *  RET-06 — تقسیمِ مبلغ میانِ مقصدها با `Pay.refund`: اول بدهیِ بازِ همین فاکتور (مثلِ
   *  امروز)، بقیه به روشِ اصلی (هر ردیف به روشِ خودش)، نقد یا اعتبارِ فروشگاه.
   *  [prior] = برگشت‌های پیشینِ همین فاکتور.
   */
  fun split(
    sale: Sale?,
    amount: Double,
    mode: String,
    approvedBy: String,
    customerId: String?,
    prior: List<SaleReturn> = emptyList(),
  ): Outcome<Split> {
    if (amount <= 0) return Outcome.Ok(Split(0.0, 0.0, 0.0))
    val m = if (sale == null && mode == ORIGINAL) CASH else mode
    if (m != ORIGINAL && m != CASH && m != STORE_CREDIT) return Outcome.Bad(Code.BAD_MODE)
    val (rows, methods) = paidRows(sale, amount, prior)
    val outstanding = if (sale == null) 0L else max(0L, minor(sale.debtGiven - sale.debtSettled))
    val r = Pay.refund(
      RefundInput(
        payments = rows,
        amount = minor(amount),
        mode = m,
        approvedBy = approvedBy.ifBlank { "owner" },
        customerId = customerId,
        accountOutstanding = outstanding,
        methods = methods,
      )
    )
    return when (r) {
      is PayResult.Err -> Outcome.Bad(if (r.code == "CUSTOMER_REQUIRED") Code.CUSTOMER_REQUIRED else Code.REFUND_FAILED, r.code)
      is PayResult.Ok -> {
        var account = 0L; var credit = 0L; var cash = 0L
        val other = LinkedHashMap<String, Long>()
        r.value.payouts.forEach {
          when (it.kind) {
            Pay.ACCOUNT -> account += it.amount
            Pay.STORE_CREDIT -> credit += it.amount
            Pay.CASH -> cash += it.amount
            else -> other[it.method] = (other[it.method] ?: 0L) + it.amount
          }
        }
        Outcome.Ok(Split(major(account), major(credit), major(cash), other.mapValues { major(it.value) }))
      }
    }
  }

  /** سهمِ نقدِ دخل از یک ردیفِ برگشت: مبلغ منهای آنچه به روش‌های غیرنقد پس رفت (`CashShift`) */
  fun drawerAmount(r: SaleReturn): Double = r.amount - (r.toMethods?.values?.sum() ?: 0.0)

  /* ================================ ثبت ================================ */

  data class Request(
    /** شناسهٔ سند، پیش از نخستین تلاش ساخته می‌شود (§۱۵.۲ بندِ ۱) */
    val docId: String,
    /** `null` ⇒ برگشتِ بی رسید */
    val saleId: String? = null,
    val picks: Map<String, Pick> = emptyMap(),
    val free: List<FreeLine> = emptyList(),
    val reason: String = "",
    val mode: String = ORIGINAL,
    /** قرض‌دارِ اعتبارِ فروشگاه؛ `null` ⇒ مشتریِ همان فاکتور */
    val customerId: String? = null,
    val by: String = "",
    val role: String = "owner",
    /** تصمیمِ `PermCore` ([permDecision])؛ `null` ⇒ کلید هنوز نیست */
    val perm: String? = null,
    val limit: Double = 0.0,
    val approval: Approval? = null,
    val exchangeSaleId: String? = null,
  )

  data class Posted(val data: ShopData, val docId: String, val total: Double, val split: Split, val gate: Gate)

  fun label(docId: String): String = "R-" + docId.takeLast(6).uppercase()

  /**
   *  ثبتِ یک سندِ برگشت. اگر چیزی درست نباشد دفتر دست‌نخورده می‌ماند (همه یا هیچ).
   */
  fun post(d: ShopData, req: Request, today: String, now: Long, newId: () -> String): Outcome<Posted> {
    if (req.docId.isBlank()) return Outcome.Bad(Code.BAD_REQUEST)
    if (d.saleReturns.any { it.docId == req.docId }) return Outcome.Bad(Code.ALREADY_POSTED)
    val reason = req.reason.trim()
    if (reason.isEmpty()) return Outcome.Bad(Code.REASON_REQUIRED)

    val sale = req.saleId?.let { id -> d.sales.find { it.id == id } ?: return Outcome.Bad(Code.SALE_NOT_FOUND) }
    val q = if (sale != null) quote(d, sale.id, req.picks) else quoteNoReceipt(d, req.free)
    val quote = when (q) { is Outcome.Bad -> return q; is Outcome.Ok -> q.value }

    if (req.mode != ORIGINAL && req.mode != CASH && req.mode != STORE_CREDIT) return Outcome.Bad(Code.BAD_MODE)
    val creditTo = if (req.mode == STORE_CREDIT) {
      val id = req.customerId?.trim()?.takeIf { it.isNotEmpty() } ?: sale?.debtorId
      if (id == null || d.debtors.none { it.id == id }) return Outcome.Bad(Code.CUSTOMER_REQUIRED)
      id
    } else null

    val g = gate(req.role, req.perm, minor(quote.total), sale == null, quote.lines.any { it.disposition == DAMAGED }, minor(req.limit))
    if (g.decision == "deny") return Outcome.Bad(Code.RETURN_DENIED)
    val approval = req.approval?.takeIf { it.by.isNotBlank() }
    if (g.decision == "approve" && (approval == null || !canApprove(g, approval.role))) return Outcome.Bad(Code.APPROVAL_REQUIRED)
    val approvedBy = if (g.decision == "approve") approval!!.by else req.by.trim()

    val prior = if (sale == null) emptyList() else d.saleReturns.filter { it.saleId == sale.id }
    val s = when (val r = split(sale, quote.total, req.mode, approvedBy, creditTo, prior)) {
      is Outcome.Bad -> return r
      is Outcome.Ok -> r.value
    }

    val tag = label(req.docId)
    val invoice = sale?.let { "فاکتور #${it.invoiceLabel() ?: "0"}" } ?: "بی رسید"
    val items = d.saleItems.toMutableList()
    val rows = ArrayList<SaleReturn>()
    val movements = ArrayList<StockMovement>()
    val entries = ArrayList<WarehouseEntry>()
    //  تقسیم به ردیف‌ها به پول (عددِ صحیح): بدهی، اعتبار، روش‌های غیرنقد، و بقیه نقد
    var leftAccount = minor(s.account)
    var leftCredit = minor(s.credit)
    val leftMethods = LinkedHashMap(s.methods.mapValues { minor(it.value) })

    for (line in quote.lines) {
      val rowId = newId()
      val amt = minor(line.amount)
      val accM = minOf(leftAccount, amt); leftAccount -= accM
      val creM = minOf(leftCredit, amt - accM); leftCredit -= creM
      var room = amt - accM - creM
      val here = LinkedHashMap<String, Double>()
      for ((m, left) in leftMethods) {
        if (room <= 0) break
        val take = minOf(left, room)
        if (take > 0) { here[m] = major(take); leftMethods[m] = left - take; room -= take }
      }
      val acc = major(accM)
      val cre = major(creM)
      rows += SaleReturn(
        id = rowId,
        saleId = sale?.id.orEmpty(),
        saleItemId = line.saleItemId,
        productId = line.productId,
        quantity = line.qty,
        amount = line.amount,
        reason = reason,
        date = today,
        createdAt = now,
        docId = req.docId,
        disposition = line.disposition,
        refund = req.mode,
        toAccount = acc.takeIf { it > 0 },
        toCredit = cre.takeIf { it > 0 },
        creditTo = creditTo?.takeIf { cre > 0 },
        toMethods = here.takeIf { it.isNotEmpty() },
        unitPrice = if (sale == null) line.unitPrice else null,
        by = req.by.trim().ifEmpty { null },
        approvedBy = if (g.decision == "approve") approvedBy else null,
        exchangeSaleId = req.exchangeSaleId,
        //  `channel` نه، حتی برای فروشِ آنلاین: `online` روی مرجوعی یعنی «بیرون از دخل» و `CashShift`
        //  کلِ ردیف را نمی‌شمارد، ولی نقدِ این برگشت از همین دخل رفت (#340)؛ درگاه از راهِ `toMethods`
      )
      if (sale != null) {
        val i = items.indexOfFirst { it.id == line.saleItemId }
        if (i >= 0) items[i] = items[i].copy(returnedQty = items[i].returnedQty + line.qty)
      }
      if (line.productId.isBlank()) continue
      val product = d.products.find { it.id == line.productId }
      //  گردشِ انبار: کالا از مشتری برگشت؛ اگر ضایعات است، همان لحظه از موجودی بیرون
      movements += StockMovement(
        id = newId(), productId = line.productId, type = "customer_return", qty = line.qty, date = today,
        notes = "برگشت $tag — $invoice — $reason", refId = rowId, createdAt = now,
      )
      when {
        //  با رسید: موجودی از `returnedQty` برمی‌گردد؛ ضایعات آن را با ردیفِ اصلاحی خنثی می‌کند
        sale != null && line.disposition == DAMAGED -> {
          entries += WarehouseEntry(
            id = newId(), productId = line.productId, units = -line.qty, unit = product?.unit.orEmpty(),
            date = today, notes = "ضایعاتِ برگشتی $tag — $reason", isAdjustment = true, createdAt = now,
            //  دفترِ موجودی (`StockLedger`) اصلاح را از همین نوع می‌شناسد
            adjKind = "damage", refId = req.docId,
          )
        }
        //  بی رسید: فروشی نیست که کم شود؛ کالای سالم با ردیفِ اصلاحی به موجودی برمی‌گردد
        sale == null && line.disposition == RESTOCK -> {
          entries += WarehouseEntry(
            id = newId(), productId = line.productId, units = line.qty, unit = product?.unit.orEmpty(),
            date = today, notes = "برگشتِ بی رسید $tag — $reason", isAdjustment = true, createdAt = now,
            refId = req.docId,
          )
        }
      }
      if (line.disposition == DAMAGED) movements += StockMovement(
        id = newId(), productId = line.productId, type = "damage", qty = -line.qty, date = today,
        notes = "ضایعاتِ برگشتی $tag — $reason", refId = rowId, createdAt = now,
      )
    }

    val transactions = d.transactions.toMutableList()
    var updatedSale = sale
    if (sale != null && s.account > 0 && sale.debtorId != null) {
      transactions += DebtTransaction(
        id = newId(), debtorId = sale.debtorId, type = "receive", amount = s.account, date = today,
        notes = "برگشت $tag — $invoice", createdAt = now, refId = req.docId, kind = RECEIPT_KIND,
      )
      updatedSale = sale.copy(debtSettled = sale.debtSettled + s.account)
    }
    if (creditTo != null && s.credit > 0) {
      transactions += DebtTransaction(
        id = newId(), debtorId = creditTo, type = "receive", amount = s.credit, date = today,
        notes = "اعتبارِ فروشگاه — برگشت $tag — $invoice", createdAt = now, refId = req.docId, kind = RECEIPT_KIND,
      )
    }

    val refundText = buildList {
      if (s.cash > 0) add("نقد ${money(s.cash)}")
      if (s.account > 0) add("از حساب ${money(s.account)}")
      if (s.credit > 0) add("اعتبار ${money(s.credit)}")
      s.methods.forEach { (m, a) -> add("${methodName(m)} ${money(a)}") }
    }.joinToString("، ")
    val notes = buildString {
      append("برگشت $tag به مبلغ ${money(quote.total)} افغانی — $invoice — دلیل: $reason")
      if (refundText.isNotEmpty()) append(" — بازپرداخت: $refundText")
      if (quote.lines.any { it.disposition == DAMAGED }) append(" — ضایعات")
      if (g.decision == "approve") append(" — تأیید: $approvedBy (${g.reasons.joinToString("+")})")
      if (req.by.isNotBlank()) append(" — ثبت: ${req.by.trim()}")
    }

    val out = d.copy(
      sales = if (updatedSale != null && updatedSale !== sale) d.sales.map { if (it.id == sale!!.id) updatedSale else it } else d.sales,
      saleItems = items,
      saleReturns = d.saleReturns + rows,
      stockMovements = d.stockMovements + movements,
      warehouseEntries = d.warehouseEntries + entries,
      transactions = transactions,
      auditLog = d.auditLog + AuditEntry(
        id = newId(),
        type = if (sale == null) "return_no_receipt" else "return",
        date = today,
        refId = sale?.id ?: req.docId,
        notes = notes,
        createdAt = now,
        amount = quote.total,
      ),
    )
    return Outcome.Ok(Posted(out, req.docId, quote.total, s, g))
  }

  /* ================================ تعویض ================================ */

  data class Exchanged(
    val data: ShopData,
    val docId: String,
    val saleId: String,
    val invoiceNumber: Int,
    val returned: Double,
    val sold: Double,
    /** مثبت: از مشتری بگیر؛ منفی: به مشتری بده */
    val due: Double,
    val split: Split,
  )

  /**
   *  RET-05 — برگشت و فروشِ تازه در یک سند و یک ذخیره: یا هر دو، یا هیچ‌کدام.
   *  فروشِ تازه همان `SalesEngine.record` است (با کلیدِ یکتای [saleKey]؛ تکرار ثبت
   *  نمی‌شود)، پس موجودی، تخفیف و نسیه همان قاعدهٔ فروش را دارند. برگشت اول
   *  ثبت می‌شود تا کالای سالمِ برگشتی همان لحظه فروختنی باشد.
   */
  fun exchange(
    d: ShopData,
    req: Request,
    cart: List<SalesEngine.CartLine>,
    checkout: SalesEngine.Checkout,
    saleKey: String,
    today: String,
    now: Long,
    newId: () -> String,
    series: String = "",
  ): Outcome<Exchanged> {
    if (cart.isEmpty() || saleKey.isBlank()) return Outcome.Bad(Code.BAD_REQUEST)
    if (d.sales.any { it.id == saleKey }) return Outcome.Bad(Code.ALREADY_POSTED)
    val posted = when (val r = post(d, req.copy(exchangeSaleId = saleKey), today, now, newId)) {
      is Outcome.Bad -> return r
      is Outcome.Ok -> r.value
    }
    return when (val s = SalesEngine.record(posted.data, cart, checkout, today, now, newId, series, saleId = saleKey)) {
      is SalesEngine.Result.Failed -> Outcome.Bad(Code.EXCHANGE_FAILED, s.message)
      is SalesEngine.Result.Ok -> {
        val sale = s.data.sales.first { it.id == saleKey }
        Outcome.Ok(
          Exchanged(
            data = s.data, docId = req.docId, saleId = saleKey, invoiceNumber = s.invoiceNumber,
            returned = posted.total, sold = sale.finalTotal,
            due = sale.paidAmount - posted.split.cash, split = posted.split,
          )
        )
      }
    }
  }

  /* ============================ نمای سند (رسید) ============================ */

  data class Doc(
    val docId: String,
    val label: String,
    val sale: Sale?,
    val rows: List<SaleReturn>,
    val total: Double,
    val split: Split,
    val reason: String,
    val by: String,
    val approvedBy: String,
    val date: String,
    val createdAt: Long,
    val exchange: Sale?,
  ) {
    /** فقط در تعویض: مثبت = گرفته شد، منفی = پس داده شد */
    val due: Double? get() = exchange?.let { it.paidAmount - split.cash }
  }

  fun doc(d: ShopData, docId: String): Doc? {
    val rows = d.saleReturns.filter { it.docId == docId }
    if (rows.isEmpty()) return null
    val first = rows.first()
    val total = rows.sumOf { it.amount }
    val account = rows.sumOf { it.toAccount ?: 0.0 }
    val credit = rows.sumOf { it.toCredit ?: 0.0 }
    val methods = LinkedHashMap<String, Double>()
    rows.forEach { r -> r.toMethods?.forEach { (m, a) -> methods[m] = (methods[m] ?: 0.0) + a } }
    return Doc(
      docId = docId,
      label = label(docId),
      sale = d.sales.find { it.id == first.saleId },
      rows = rows,
      total = total,
      split = Split(account, credit, total - account - credit - methods.values.sum(), methods),
      reason = first.reason,
      by = first.by.orEmpty(),
      approvedBy = first.approvedBy.orEmpty(),
      date = first.date,
      createdAt = first.createdAt,
      exchange = first.exchangeSaleId?.let { id -> d.sales.find { it.id == id } },
    )
  }

  /** نامِ قلمِ یک ردیفِ برگشت */
  fun rowName(d: ShopData, row: SaleReturn): String {
    d.saleItems.find { it.id == row.saleItemId }?.let { return SalesEngine.itemName(d, it) }
    return d.products.find { it.id == row.productId }?.name ?: "(کالا)"
  }

  /** نامِ روشِ پرداخت برای رسید — همان جدولِ پیش‌فرضِ هستهٔ پرداخت */
  fun methodName(id: String): String = Pay.DEFAULT_METHODS.firstOrNull { it.id == id }?.name ?: id

  fun dispositionName(v: String?): String = if (v == DAMAGED) "ضایعات" else "به موجودی"

  fun modeName(v: String?): String = when (v) {
    CASH -> "نقد"
    STORE_CREDIT -> "اعتبارِ فروشگاه"
    else -> "روشِ اصلی"
  }
}
