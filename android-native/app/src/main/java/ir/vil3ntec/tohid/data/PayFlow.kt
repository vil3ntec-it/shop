package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.CloseShiftInput
import ir.vil3ntec.tohid.core.pay.EventCounts
import ir.vil3ntec.tohid.core.pay.MethodTotals
import ir.vil3ntec.tohid.core.pay.Moves
import ir.vil3ntec.tohid.core.pay.OpenShiftInput
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.SettleInput
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.Shift
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.ShiftEventInput
import ir.vil3ntec.tohid.core.pay.ShiftReport
import ir.vil3ntec.tohid.core.pay.ShiftRow
import ir.vil3ntec.tohid.core.pay.Tender
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 *  سیم‌کشیِ پرداخت و صندوق — PAY-01..07، 11..15، 19 (v3 A4).
 *
 *  فقط برنامه‌های بومی (اندروید و کامپیوتر؛ دستورِ صاحبِ مخزن: سامانهٔ دکان با برنامه‌هاست،
 *  نه وب). روی هستهٔ `core/pay/{Payment,Shift}.kt` و با همان بردارِ مرجع
 *  (`core/test-vectors/pay-v1.json`) سنجیده می‌شود (`PayFlowTest`).
 *
 *  خالص است: نه Context، نه ساعت، نه دیسک. دفتر، ساعت و شناسه را صفحه می‌دهد.
 *
 *  ── پشتِ پرچم ─────────────────────────────────────────────────────
 *  هیچ‌کدام بی `ShopPolicy.payV2` صدا زده نمی‌شود؛ پیش‌فرض خاموش است و فروش و صندوقِ
 *  امروز (`SalesEngine` بی `settlement`، `CashShift`) دست نمی‌خورد.
 *
 *  ── شکلِ داده: فقط افزودن ────────────────────────────────────────
 *  فروش: `payments`، `tenderedMinor`، `changeMinor`؛ معنای `paymentMethod`،
 *  `paidAmount`، `remaining`، `debtGiven` همان امروز. صندوق: ردیف‌های سابقهٔ
 *  `cash_in`/`cash_out`/`no_sale` با `refId` = همان `shift_open`، و `shift_close`
 *  با `amount` = نقدِ شمرده (همان معنای امروز) و `z` = گزارشِ Zِ هسته در لحظهٔ بستن.
 */
object PayFlow {

  const val CASH = "cash"
  const val ACCOUNT = Pay.ACCOUNT

  // ── واحد ──────────────────────────────────────────────────
  /** افغانیِ دفتر ⇒ پول؛ همان `Money.fromLegacy` / `moneyFromLegacy` (Math.round(x × 100)) */
  fun minor(afn: Double): Long = if (afn.isNaN() || afn.isInfinite()) 0L else Math.round(afn * 100)

  fun major(m: Long): Double = m / 100.0

  /** null = خالی؛ NaN = نامعتبر؛ وگرنه عدد (افغانی) */
  fun amountOf(text: String?): Double? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty()) return null
    val n = t.toDoubleOrNull() ?: return Double.NaN
    return if (n.isNaN() || n.isInfinite() || n < 0) Double.NaN else n
  }

  // ── روش‌ها ────────────────────────────────────────────────
  fun method(id: String): PayMethod? =
    if (id == ACCOUNT) PayMethod(ACCOUNT, ACCOUNT, "به حسابِ مشتری") else Pay.DEFAULT_METHODS.firstOrNull { it.id == id }

  fun methodName(id: String): String = method(id)?.name ?: id

  /** روش‌های ردیفِ پنل: روشن و غیرنقد (کیف پول، کارت، حواله) */
  fun panelMethods(): List<PayMethod> = Pay.DEFAULT_METHODS.filter { it.enabled && it.kind != CASH }

  // ── خطا و پیام ─────────────────────────────────────────
  data class Problem(
    val code: String,
    /** ردیفِ پنل که خطا دارد؛ ‎-1 یعنی نقد یا کلِ فاکتور */
    val index: Int = -1,
    val method: String? = null,
    val remaining: Long = 0,
    val variance: Map<String, Long?>? = null,
  )

  fun message(p: Problem): String {
    val row = p.method?.let { "«${methodName(it)}»" }
    return when (p.code) {
      "REF_REQUIRED" -> "شمارهٔ مرجعِ ${row ?: "این روش"} لازم است"
      "NON_CASH_OVERPAY" -> "پرداختِ غیرنقد بیش از مبلغِ فاکتور است؛ پولِ پس فقط از نقد داده می‌شود"
      "METHOD_LIMIT" -> "مبلغِ ${row ?: "این روش"} از سقفِ همان روش بیشتر است"
      "METHOD_DISABLED", "UNKNOWN_METHOD" -> "روشِ پرداخت شناخته نیست"
      "SHORT" -> "باقی‌مانده ${fmt(p.remaining)} افغانی است — مشتری را انتخاب کنید یا مبلغ را کامل کنید"
      "TOTAL_MISMATCH" -> "مبلغِ پرداخت با فاکتور نمی‌خواند؛ دوباره باز کنید"
      "INSUFFICIENT_CASH" -> "نقدِ دخل برای این خروج کافی نیست"
      "REASON_REQUIRED" -> "دلیل لازم است"
      "NOTE_REQUIRED" -> "برای «دیگر» توضیح بنویسید"
      "BAD_REASON" -> "دلیل درست نیست"
      "APPROVAL_REQUIRED" -> "باز کردنِ کشو بی فروش فقط با تأییدِ صاحب یا مدیر"
      "SHIFT_CLOSED" -> "صندوقِ بازی نیست"
      "SHIFT_OPEN_ALREADY" -> "صندوق از قبل باز است"
      "INVALID_TIME" -> "ساعتِ این دستگاه از زمانِ باز شدنِ صندوق عقب‌تر است"
      "BAD_DENOMINATION", "INVALID_COUNT" -> "شمارِ اسکناس درست نیست"
      else -> "مبلغ درست نیست"
    }
  }

  private fun fmt(m: Long): String = (if (m < 0) "−" else "") + ir.vil3ntec.tohid.money(kotlin.math.abs(m) / 100.0)

  // ── پنلِ پرداخت (PAY-01، 02، 04، 05، 06، 07) ─────────────
  data class DraftRow(val method: String, val ref: String = "", val amount: String = "")

  /** متنِ همان کادرها؛ `total` همان `finalTotal`ِ فاکتور (افغانی) */
  data class Draft(
    val total: Double,
    val cash: String = "",
    val rows: List<DraftRow> = emptyList(),
    val customerId: String? = null,
  )

  sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Bad(val problem: Problem) : Outcome<Nothing>

    /** مقدار، یا `null` اگر خطا بود */
    fun valueOrNull(): T? = if (this is Ok) value else null
  }

  private class Built(val input: SettleInput, val at: List<Int>)

  /** ردیفِ بی مبلغ و بی مرجع نادیده گرفته می‌شود (ردیفِ خالیِ تازه) */
  private fun input(d: Draft, withCash: Boolean): Outcome<Built> {
    val tenders = ArrayList<Tender>()
    val at = ArrayList<Int>()
    if (withCash) {
      val c = amountOf(d.cash)
      if (c != null && c.isNaN()) return Outcome.Bad(Problem("INVALID_AMOUNT"))
      if (c != null && c > 0) { tenders += Tender(CASH, minor(c)); at += -1 }
    }
    d.rows.forEachIndexed { i, r ->
      val a = amountOf(r.amount)
      val ref = r.ref.trim()
      if (a != null && a.isNaN()) return Outcome.Bad(Problem("INVALID_AMOUNT", i, r.method))
      if (a == null || a == 0.0) {
        if (ref.isEmpty()) return@forEachIndexed
        return Outcome.Bad(Problem("INVALID_AMOUNT", i, r.method))
      }
      tenders += Tender(r.method, minor(a), ref = ref.ifEmpty { null })
      at += i
    }
    return Outcome.Ok(Built(SettleInput(total = minor(d.total), tenders = tenders, customerId = d.customerId?.trim()?.ifEmpty { null }), at))
  }

  /** پیش‌نمایشِ زنده: همان `settle`ِ هسته؛ خطا با شمارهٔ ردیف و روش */
  fun preview(d: Draft): Outcome<Settlement> {
    val b = when (val x = input(d, true)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val s = Pay.settle(b.input)) {
      is PayResult.Ok -> Outcome.Ok(s.value)
      is PayResult.Err -> {
        val i = s.index?.takeIf { it >= 0 }?.let { b.at[it] } ?: -1
        Outcome.Bad(Problem(s.code, i, if (i >= 0) d.rows[i].method else null))
      }
    }
  }

  /** PAY-04 — «نقدِ دقیق»: نقدی که با ردیف‌های غیرنقدِ فعلی فاکتور را می‌بندد (افغانی؛ ۰ یعنی لازم نیست) */
  fun exactCash(d: Draft): Outcome<Double> {
    val b = when (val x = input(d, false)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val r = Pay.exactCash(b.input)) {
      is PayResult.Ok -> Outcome.Ok(major(r.value))
      is PayResult.Err -> Outcome.Bad(Problem(r.code))
    }
  }

  /** فیلدهای فروش از تسویه — همان معنای امروزِ فیلدهای قدیمی + سه فیلدِ تازه */
  data class SaleFields(
    val paymentMethod: String,
    val paidAmount: Double,
    val remaining: Double,
    val debtGiven: Double,
    val payments: List<SalePayment>,
    val tenderedMinor: Long,
    val changeMinor: Long,
  )

  fun saleFields(s: Settlement): SaleFields = SaleFields(
    paymentMethod = if (s.toAccount > 0) "credit" else "cash",
    paidAmount = major(s.total - s.toAccount),
    remaining = major(s.toAccount),
    debtGiven = major(s.toAccount),
    payments = s.payments.map { SalePayment(it.method, it.kind, it.amount, it.status, it.ref, it.verification) },
    tenderedMinor = s.tendered,
    changeMinor = s.change,
  )

  /** تسویهٔ نهایی برای ثبت: فروشِ ناتمام (باقی‌ماندهٔ بی مشتری) رد می‌شود */
  fun checkout(d: Draft): Outcome<Settlement> = when (val s = preview(d)) {
    is Outcome.Bad -> s
    is Outcome.Ok -> if (s.value.status == "short") Outcome.Bad(Problem("SHORT", remaining = s.value.remaining)) else s
  }

  /**
   *  ردیف‌های پرداختِ ترکیبی روی فاکتور و رسید (برچسب، افغانی).
   *  فاکتورِ بی `payments` (امروز، یا پرچمِ خاموش) هیچ ردیفی نمی‌گیرد.
   */
  fun receiptLines(s: Sale): List<Pair<String, Double>> {
    val list = s.payments?.takeIf { it.isNotEmpty() } ?: return emptyList()
    return buildList {
      list.filter { it.method != ACCOUNT }.forEach { p ->
        add(methodName(p.method) + (if (p.status == "pending") " (ثبتِ دستی)" else "") + (p.ref?.let { " — $it" } ?: "") to major(p.amountMinor))
      }
      s.tenderedMinor?.takeIf { it > 0 }?.let { add("دریافتی" to major(it)) }
      s.changeMinor?.takeIf { it > 0 }?.let { add("پولِ پس" to major(it)) }
    }
  }

  // ── صندوق (PAY-11..15، 19) ───────────────────────────────
  private val AUDIT_TYPES = setOf("cash_in", "cash_out", "no_sale")

  /** ردیف‌های فروشِ یک فاکتور: `payments` اگر هست، وگرنه همان حسابِ امروز */
  fun saleRows(s: Sale): List<ShiftRow> {
    s.payments?.takeIf { it.isNotEmpty() }?.let { list ->
      return list.filter { it.amountMinor > 0 }.map { ShiftRow(it.method, it.amountMinor) }
    }
    val debt = s.debtGiven.coerceAtLeast(0.0)
    val cash = (s.finalTotal - debt).coerceAtLeast(0.0)
    return buildList {
      if (minor(cash) > 0) add(ShiftRow(CASH, minor(cash)))
      if (minor(debt) > 0) add(ShiftRow(ACCOUNT, minor(debt)))
    }
  }

  /**
   *  دفتر ⇒ رویدادهای هسته. ترتیب: ورودی‌ها، خروجِ ثبت‌شده در صندوق، برگشت، خروج‌های دفتر.
   *  هسته ترتیبِ زمانی نمی‌خواهد؛ این ترتیب فقط نمی‌گذارد خروجی که در لحظهٔ خودش سنجیده و
   *  پذیرفته شده، این‌جا به‌خاطرِ رویدادِ دیرتر رد شود. همان `ledgerEvents`ِ JS.
   */
  fun ledgerEvents(d: ShopData, open: AuditEntry, to: Long): List<ShiftEventInput> {
    val from = open.createdAt
    fun inWindow(at: Long) = at in from..to
    val ev = ArrayList<Pair<Int, ShiftEventInput>>()
    d.sales.forEach { s ->
      if (s.status == "cancelled" || !inWindow(s.createdAt)) return@forEach
      val rows = saleRows(s)
      if (rows.isNotEmpty()) ev += 0 to ShiftEventInput(id = "sale:${s.id}", type = "sale", at = s.createdAt, rows = rows)
    }
    d.transactions.forEach { t ->
      if (t.type != "receive" || !inWindow(t.createdAt) || minor(t.amount) <= 0) return@forEach
      ev += 0 to ShiftEventInput(id = "rcv:${t.id}", type = "receipt", at = t.createdAt, method = CASH, amount = minor(t.amount))
    }
    d.auditLog.forEach { a ->
      if (a.type !in AUDIT_TYPES || a.refId != open.id) return@forEach
      val by = a.by?.ifEmpty { null }
      if (a.type == "no_sale") {
        ev += 1 to ShiftEventInput(id = "a:${a.id}", type = "no_sale", at = a.createdAt, by = by, reason = a.reason, approvedBy = a.approvedBy)
        return@forEach
      }
      ev += (if (a.type == "cash_in") 0 else 1) to ShiftEventInput(
        id = "a:${a.id}", type = a.type, at = a.createdAt, by = by,
        amount = minor(a.amount), reason = a.reason, note = a.note?.ifEmpty { null },
      )
    }
    d.saleReturns.forEach { r ->
      if (!inWindow(r.createdAt) || minor(r.amount) <= 0) return@forEach
      ev += 2 to ShiftEventInput(id = "ret:${r.id}", type = "refund", at = r.createdAt, rows = listOf(ShiftRow(CASH, minor(r.amount))))
    }
    fun out(id: String, at: Long, amount: Double, reason: String, note: String?) {
      if (!inWindow(at) || minor(amount) <= 0) return
      ev += 3 to ShiftEventInput(id = id, type = "cash_out", at = at, amount = minor(amount), reason = reason, note = note)
    }
    d.expenses.forEach { out("exp:${it.id}", it.createdAt, it.amount, "petty_expense", null) }
    d.supplierPayments.forEach { out("sup:${it.id}", it.createdAt, it.amount, "other", "پرداخت به تأمین‌کننده") }
    d.purchases.forEach { out("pur:${it.id}", it.createdAt, it.paidAmount, "other", "پیش‌پرداختِ خرید") }
    return ev.sortedWith(compareBy<Pair<Int, ShiftEventInput>>({ it.first }, { it.second.at }, { it.second.id })).map { it.second }
  }

  data class Skipped(val id: String, val type: String, val code: String, val amount: Long)
  data class Replay(val shift: Shift, val skipped: List<Skipped>)

  /** شیفتِ هسته از روی دفتر؛ رویدادی که هسته نپذیرد در `skipped` می‌آید و صفحه هشدار می‌دهد */
  fun replay(d: ShopData, open: AuditEntry, now: Long, threshold: Long = Pay.DEFAULT_VARIANCE_THRESHOLD): Outcome<Replay> {
    var shift = when (
      val o = ShiftCore.open(OpenShiftInput(id = open.id, at = open.createdAt, float = minor(open.amount), by = open.by, threshold = threshold))
    ) {
      is PayResult.Ok -> o.value
      is PayResult.Err -> return Outcome.Bad(Problem(o.code))
    }
    val skipped = ArrayList<Skipped>()
    ledgerEvents(d, open, now).forEach { e ->
      when (val r = ShiftCore.record(shift, e)) {
        is PayResult.Ok -> shift = r.value
        is PayResult.Err -> skipped += Skipped(e.id.orEmpty(), e.type, r.code, e.amount ?: 0)
      }
    }
    return Outcome.Ok(Replay(shift, skipped))
  }

  data class Report(val report: ShiftReport, val stored: Boolean, val skipped: List<Skipped> = emptyList(), val shift: Shift? = null)

  /** گزارشِ X (یا Zِ ذخیره‌شدهٔ صندوقِ بسته) */
  fun report(d: ShopData, open: AuditEntry, close: AuditEntry?, now: Long, by: String? = null): Outcome<Report> {
    if (close != null) {
      val z = close.z?.let { reportOf(it) } ?: return Outcome.Bad(Problem("LEGACY_CLOSE"))
      return Outcome.Ok(Report(z, stored = true))
    }
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    return when (val x = ShiftCore.xReport(r.shift, maxOf(now, open.createdAt), by?.trim()?.ifEmpty { null })) {
      is PayResult.Ok -> Outcome.Ok(Report(x.value, stored = false, skipped = r.skipped, shift = r.shift))
      is PayResult.Err -> Outcome.Bad(Problem(x.code))
    }
  }

  /** نقدِ مورد انتظار (افغانی) برای سرخطِ صندوق؛ NaN اگر حساب نشد */
  fun expectedCash(d: ShopData, open: AuditEntry, now: Long): Double = when (val r = report(d, open, null, now)) {
    is Outcome.Ok -> major(r.value.report.expected?.get(CASH) ?: 0)
    is Outcome.Bad -> Double.NaN
  }

  /** ورود/خروجِ نقد یا کشو بی فروش — `type` = cash_in | cash_out | no_sale؛ `amount` متنِ کادر */
  data class Move(
    val type: String,
    val amount: String = "",
    val reason: String? = null,
    val note: String = "",
    val by: String = "",
    val approvedBy: String = "",
  )

  /** هسته می‌سنجد؛ ردیفِ سابقه (بی `notes` و `date` — آن‌ها را صفحه می‌گذارد) برمی‌گردد */
  fun moveRow(d: ShopData, open: AuditEntry, m: Move, now: Long, newId: () -> String): Outcome<AuditEntry> {
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    val id = newId()
    val by = m.by.trim().ifEmpty { null }
    val e: ShiftEventInput
    val row: AuditEntry
    if (m.type == "no_sale") {
      val reason = m.reason?.trim().orEmpty()
      val approver = m.approvedBy.trim()
      e = ShiftEventInput(id = "a:$id", type = "no_sale", at = now, by = by, reason = reason, approvedBy = approver)
      row = AuditEntry(id = id, type = "no_sale", refId = open.id, createdAt = now, by = by, reason = reason, approvedBy = approver)
    } else {
      val a = amountOf(m.amount)
      if (a == null || a.isNaN() || minor(a) <= 0) return Outcome.Bad(Problem("INVALID_AMOUNT"))
      val note = m.note.trim().ifEmpty { null }
      e = ShiftEventInput(id = "a:$id", type = m.type, at = now, by = by, amount = minor(a), reason = m.reason, note = note)
      row = AuditEntry(id = id, type = m.type, refId = open.id, createdAt = now, amount = major(minor(a)), by = by, reason = m.reason, note = note)
    }
    return when (val x = ShiftCore.record(r.shift, e)) {
      is PayResult.Ok -> Outcome.Ok(row)
      is PayResult.Err -> Outcome.Bad(Problem(x.code))
    }
  }

  /** شمارشِ بستن: اسکناس (پول، متنِ دهدهی) ⇒ تعداد */
  data class Count(
    val counts: Map<String, Long>,
    val methodCounts: Map<String, Long> = emptyMap(),
    val by: String = "",
    val blind: Boolean = false,
    val reason: String = "",
  )

  data class Closed(val row: AuditEntry, val report: ShiftReport, val skipped: List<Skipped>)

  /** PAY-13، 14 — بستن با شمارشِ اسکناس؛ اختلافِ بالای آستانه بی دلیل بسته نمی‌شود؛ اختلاف اصلاح نمی‌شود */
  fun closeRow(d: ShopData, open: AuditEntry, c: Count, now: Long, newId: () -> String): Outcome<Closed> {
    val r = when (val x = replay(d, open, now)) { is Outcome.Ok -> x.value; is Outcome.Bad -> return x }
    val id = newId()
    val by = c.by.trim().ifEmpty { null }
    val res = ShiftCore.close(
      r.shift,
      CloseShiftInput(id = id, at = now, counts = c.counts, methodCounts = c.methodCounts, by = by, blind = c.blind, reason = c.reason.trim().ifEmpty { null }),
    )
    val z = when (res) {
      is PayResult.Ok -> res.value.z ?: return Outcome.Bad(Problem("SHIFT_CLOSED"))
      is PayResult.Err -> return Outcome.Bad(Problem(res.code, variance = res.variance))
    }
    val row = AuditEntry(
      id = id, type = CashShift.CLOSE, refId = open.id, createdAt = now,
      amount = major(z.counted?.get(CASH) ?: 0), by = by, reason = z.reason, z = reportJson(z),
    )
    return Outcome.Ok(Closed(row, z, r.skipped))
  }

  /** شمارِ اسکناس ⇒ جمع (پول) — برای جمعِ زندهٔ فرمِ شمارش */
  fun countTotal(counts: Map<String, Long>): Long = counts.entries.sumOf { (k, n) -> (k.toLongOrNull() ?: 0L) * n.coerceAtLeast(0) }

  // ── Zِ ذخیره‌شده: همان شکلِ JSِ گزارشِ هسته ───────────────
  private fun longs(m: Map<String, Long>) = buildJsonObject { m.forEach { (k, v) -> put(k, v) } }
  private fun str(v: String?): JsonElement = v?.let { JsonPrimitive(it) } ?: JsonNull

  fun reportJson(r: ShiftReport): JsonObject = buildJsonObject {
    put("kind", r.kind); put("shiftId", r.shiftId); put("currency", r.currency); put("at", r.at)
    put("by", str(r.by)); put("openedAt", r.openedAt); put("openedBy", str(r.openedBy)); put("float", r.float)
    put("methods", buildJsonObject {
      r.methods.forEach { (k, m) -> put(k, buildJsonObject { put("sales", m.sales); put("refunds", m.refunds); put("receipts", m.receipts); put("net", m.net) }) }
    })
    put("moves", buildJsonObject { put("in", r.moves.cashIn); put("out", r.moves.cashOut) })
    r.expected?.let { put("expected", longs(it)) }
    put("events", buildJsonObject {
      put("sales", r.events.sales); put("refunds", r.events.refunds); put("receipts", r.events.receipts)
      put("cashIn", r.events.cashIn); put("cashOut", r.events.cashOut); put("noSale", r.events.noSale)
    })
    if (r.kind == "Z") {
      r.counted?.let { put("counted", longs(it)) }
      r.denominations?.let { put("denominations", longs(it)) }
      r.variance?.let { v -> put("variance", buildJsonObject { v.forEach { (k, x) -> put(k, x?.let { JsonPrimitive(it) } ?: JsonNull) } }) }
      put("reason", str(r.reason))
      r.needsReview?.let { put("needsReview", it) }
      r.blind?.let { put("blind", it) }
    }
  }

  /** Zِ ذخیره‌شده (از هر دستگاه) ⇒ گزارش؛ `null` اگر خوانا نیست */
  fun reportOf(j: JsonObject): ShiftReport? = runCatching {
    fun JsonObject.l(k: String) = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
    fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    fun JsonObject.map(k: String): Map<String, Long>? =
      (this[k] as? JsonObject)?.entries?.associate { (key, v) -> key to (v.jsonPrimitive.longOrNull ?: 0L) }
    val ev = j["events"]?.jsonObject ?: JsonObject(emptyMap())
    ShiftReport(
      kind = j.s("kind") ?: "Z", shiftId = j.s("shiftId").orEmpty(), currency = j.s("currency") ?: "AFN",
      at = j.l("at"), by = j.s("by"), openedAt = j.l("openedAt"), openedBy = j.s("openedBy"), float = j.l("float"),
      methods = (j["methods"] as? JsonObject)?.entries?.associate { (k, v) ->
        val o = v.jsonObject
        k to MethodTotals(o.l("sales"), o.l("refunds"), o.l("receipts"), o.l("net"))
      } ?: emptyMap(),
      moves = (j["moves"] as? JsonObject).let { Moves(it?.l("in") ?: 0, it?.l("out") ?: 0) },
      expected = j.map("expected"),
      events = EventCounts(
        ev.l("sales").toInt(), ev.l("refunds").toInt(), ev.l("receipts").toInt(),
        ev.l("cashIn").toInt(), ev.l("cashOut").toInt(), ev.l("noSale").toInt(),
      ),
      counted = j.map("counted"),
      denominations = j.map("denominations"),
      variance = (j["variance"] as? JsonObject)?.entries?.associate { (k, v) -> k to (v as? JsonPrimitive)?.longOrNull },
      reason = j.s("reason"),
      needsReview = (j["needsReview"] as? JsonPrimitive)?.booleanOrNull,
      blind = (j["blind"] as? JsonPrimitive)?.booleanOrNull,
    )
  }.getOrNull()
}
