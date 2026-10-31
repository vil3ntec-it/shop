package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact

/*
 *  هستهٔ شیفت و صندوق — PAY-11..16، 19 (بخشِ ۳.۷ و ۴.۲ِ مشخصات، D-14).
 *
 *  ⛔ هم‌زادِ بخشِ شیفتِ `license/pay-core.js`؛ بردارها: `core/test-vectors/pay-v1.json`.
 *
 *  شیفت یک دفترِ فقط‌افزودنیِ رویداد است: باز کردن با موجودیِ اولیه، فروش و برگشت
 *  به تفکیکِ روش، رسیدِ حساب، ورود و خروجِ نقد با دلیلِ اجباری، بازکردنِ کشو بی
 *  فروش با مجوز. گزارشِ X عکسِ میانِ شیفت است؛ Z با شمارشِ اسکناس و شمارشِ هر روش
 *  می‌بندد. اختلاف ثبت می‌شود و هرگز خودکار اصلاح نمی‌شود؛ بالای آستانه دلیل
 *  می‌خواهد (جز شمارشِ کور، که پاسخش نباید به اختلاف بسته باشد) و هر اختلافی «نیازمند بررسیِ مدیر» است.
 *
 *  هر عملیات شناسهٔ پایدار (op_id) دارد (بخشِ ۱۵.۲ بندِ ۱ و ۲۹.۵ بندِ ۲): تکرارِ
 *  همان عملیات با همان محتوا همان نتیجه را می‌دهد (`duplicate`) و اثرِ دوم ندارد؛
 *  همان شناسه با محتوای دیگر `ID_CONFLICT` است.
 *
 *  هنوز به `CashShift` (دو ردیفِ سابقه) وصل نیست؛ تفاوت‌ها در `legacyShift`ِ بردارها.
 */

data class ShiftRow(val method: String, val amount: Long)

/** ورودیِ یک رویداد، همان شکلِ JS؛ سنجش در [ShiftCore.record] */
data class ShiftEventInput(
  val id: String?,
  /** sale | refund | receipt | cash_in | cash_out | no_sale */
  val type: String,
  val at: Long,
  val rows: List<ShiftRow>? = null,
  val method: String? = null,
  val amount: Long? = null,
  val reason: String? = null,
  val note: String? = null,
  val by: String? = null,
  val approvedBy: String? = null,
  /** null = ارزِ شیفت؛ ارزِ دیگر رد می‌شود (PAY-10) */
  val currency: String? = null,
)

data class ShiftEvent(
  val id: String,
  val type: String,
  val at: Long,
  val rows: List<ShiftRow> = emptyList(),
  val amount: Long = 0,
  val reason: String? = null,
  val note: String? = null,
  val by: String? = null,
  val approvedBy: String? = null,
)

data class OpenShiftInput(
  val id: String?,
  val at: Long,
  val float: Long,
  val by: String? = null,
  val threshold: Long = Pay.DEFAULT_VARIANCE_THRESHOLD,
  val coins: Boolean = false,
  val methods: List<PayMethod>? = null,
  val currency: String = "AFN",
)

data class CloseShiftInput(
  val id: String?,
  val at: Long,
  /** اسکناس (به پول، متنِ دهدهی) ⇐ تعداد */
  val counts: Map<String, Long> = emptyMap(),
  /** شمارشِ هر روشِ غیرنقد (PAY-16) */
  val methodCounts: Map<String, Long> = emptyMap(),
  val by: String? = null,
  val blind: Boolean = false,
  val reason: String? = null,
)

/** ورودیِ بستن پس از سنجش و یک‌دست‌شدن؛ برای تشخیصِ تکرارِ همان بستن */
data class CloseOp(
  val id: String,
  val at: Long,
  val by: String?,
  val counts: Map<String, Long>,
  val methodCounts: Map<String, Long>,
  val blind: Boolean,
  val reason: String?,
)

data class Shift(
  val id: String,
  val currency: String,
  val status: String,
  val openedAt: Long,
  val openedBy: String?,
  val float: Long,
  val threshold: Long,
  val coins: Boolean,
  val table: MethodTable,
  val events: List<ShiftEvent>,
  val closedAt: Long? = null,
  val closeOp: CloseOp? = null,
  val z: ShiftReport? = null,
)

data class MethodTotals(val sales: Long = 0, val refunds: Long = 0, val receipts: Long = 0, val net: Long = 0)

data class Moves(val cashIn: Long, val cashOut: Long)

data class EventCounts(
  val sales: Int = 0, val refunds: Int = 0, val receipts: Int = 0,
  val cashIn: Int = 0, val cashOut: Int = 0, val noSale: Int = 0,
)

data class ShiftReport(
  /** X | Z */
  val kind: String,
  val shiftId: String,
  val currency: String,
  val at: Long,
  val by: String?,
  val openedAt: Long,
  val openedBy: String?,
  val float: Long,
  /** به ترتیبِ نخستین دیدن */
  val methods: Map<String, MethodTotals>,
  val moves: Moves,
  /** null فقط در نمای صندوقدارِ شمارشِ کور */
  val expected: Map<String, Long>?,
  val events: EventCounts,
  // ── فقط Z ──
  val counted: Map<String, Long>? = null,
  val denominations: Map<String, Long>? = null,
  /** null یعنی «شمرده نشد» برای همان روش؛ خودِ نقشه null فقط در نمای کور */
  val variance: Map<String, Long?>? = null,
  val reason: String? = null,
  val needsReview: Boolean? = null,
  val blind: Boolean? = null,
)

object ShiftCore {

  val MOVE_REASONS: Map<String, List<String>> = mapOf(
    "cash_in" to listOf("deposit", "other"),
    "cash_out" to listOf("owner_withdrawal", "petty_expense", "other"),
  )

  private fun clean(s: String?): String? = s?.trim()?.ifEmpty { null }

  /** PAY-11 */
  fun open(p: OpenShiftInput): PayResult<Shift> {
    val id = clean(p.id) ?: return PayResult.Err("ID_REQUIRED")
    val cur = try { Currency.of(p.currency) } catch (e: IllegalArgumentException) { return PayResult.Err("UNKNOWN_CURRENCY") }
    if (!nonNegExact(p.at)) return PayResult.Err("INVALID_TIME")
    if (!nonNegExact(p.float)) return PayResult.Err("INVALID_AMOUNT")
    if (!nonNegExact(p.threshold)) return PayResult.Err("BAD_CONFIG")
    val t = when (val r = Pay.table(p.methods)) { is PayResult.Ok -> r.value; is PayResult.Err -> return r }
    return PayResult.Ok(Shift(id, cur.code, "open", p.at, clean(p.by), p.float, p.threshold, p.coins, t, emptyList()))
  }

  private fun rowsOf(t: MethodTable, list: List<ShiftRow>?, allowAccount: Boolean): PayResult<List<ShiftRow>> {
    if (list.isNullOrEmpty()) return PayResult.Err("BAD_EVENT")
    list.forEachIndexed { i, r ->
      val kind = t.kindOf(r.method)
      if (kind == null || (!allowAccount && kind == Pay.ACCOUNT)) return PayResult.Err("UNKNOWN_METHOD", index = i)
      if (!posExact(r.amount)) return PayResult.Err("INVALID_AMOUNT", index = i)
    }
    return PayResult.Ok(list.toList())
  }

  /** سنجش و یک‌دست‌کردنِ رویداد، بی نگاه به حالتِ شیفت */
  private fun build(s: Shift, id: String, e: ShiftEventInput): PayResult<ShiftEvent> {
    // PAY-10: شیفت یک ارز دارد؛ رویدادِ ارزِ دیگر پذیرفته نمی‌شود
    if ((e.currency ?: s.currency) != s.currency) return PayResult.Err("CURRENCY_MISMATCH")
    return buildChecked(s.table, id, e)
  }

  private fun buildChecked(t: MethodTable, id: String, e: ShiftEventInput): PayResult<ShiftEvent> = when (e.type) {
    "sale", "refund", "receipt" -> {
      val r = if (e.type == "receipt") rowsOf(t, listOf(ShiftRow(e.method.orEmpty(), e.amount ?: 0)), false) else rowsOf(t, e.rows, true)
      when (r) {
        is PayResult.Ok -> PayResult.Ok(ShiftEvent(id, e.type, e.at, r.value))
        is PayResult.Err -> r
      }
    }
    "cash_in", "cash_out" -> {
      val amount = e.amount ?: 0
      val note = clean(e.note)
      when {
        !posExact(amount) -> PayResult.Err("INVALID_AMOUNT")
        e.reason.isNullOrEmpty() -> PayResult.Err("REASON_REQUIRED")
        e.reason !in MOVE_REASONS.getValue(e.type) -> PayResult.Err("BAD_REASON")
        e.reason == "other" && note == null -> PayResult.Err("NOTE_REQUIRED")
        else -> PayResult.Ok(ShiftEvent(id, e.type, e.at, amount = amount, reason = e.reason, note = note, by = clean(e.by)))
      }
    }
    "no_sale" -> {
      val reason = clean(e.reason)
      val approver = clean(e.approvedBy)
      when {
        reason == null -> PayResult.Err("REASON_REQUIRED")
        approver == null -> PayResult.Err("APPROVAL_REQUIRED")
        else -> PayResult.Ok(ShiftEvent(id, "no_sale", e.at, reason = reason, by = clean(e.by), approvedBy = approver))
      }
    }
    else -> PayResult.Err("BAD_EVENT")
  }

  /** PAY-12، 19 و فروش و برگشت. شیفتِ پیشین هرگز تغییر نمی‌کند؛ نتیجه شیفتی تازه است. */
  fun record(s: Shift?, e: ShiftEventInput): PayResult<Shift> {
    if (s == null) return PayResult.Err("SHIFT_CLOSED")
    val id = clean(e.id) ?: return PayResult.Err("ID_REQUIRED")
    val t = s.table
    val prior = s.events.firstOrNull { it.id == id }
    if (prior != null) {
      return when (val b = build(s, id, e)) {
        is PayResult.Ok -> if (b.value == prior) PayResult.Ok(s, duplicate = true) else PayResult.Err("ID_CONFLICT")
        is PayResult.Err -> b
      }
    }
    if (s.status != "open") return PayResult.Err("SHIFT_CLOSED")
    if (!nonNegExact(e.at) || e.at < s.openedAt) return PayResult.Err("INVALID_TIME")
    val ev = when (val b = build(s, id, e)) { is PayResult.Ok -> b.value; is PayResult.Err -> return b }
    if (ev.type == "cash_out" && ev.amount > totals(s).expected.getValue(t.cashId)) return PayResult.Err("INSUFFICIENT_CASH")
    return PayResult.Ok(s.copy(events = s.events + ev))
  }

  private class Totals(
    val methods: Map<String, MethodTotals>,
    val moves: Moves,
    val events: EventCounts,
    val expected: Map<String, Long>,
  )

  private fun totals(s: Shift): Totals {
    val methods = LinkedHashMap<String, MethodTotals>()
    var cashIn = 0L
    var cashOut = 0L
    var c = EventCounts()
    for (e in s.events) {
      when (e.type) {
        "sale", "refund", "receipt" -> {
          c = when (e.type) {
            "sale" -> c.copy(sales = c.sales + 1)
            "refund" -> c.copy(refunds = c.refunds + 1)
            else -> c.copy(receipts = c.receipts + 1)
          }
          for (r in e.rows) {
            val b = methods[r.method] ?: MethodTotals()
            methods[r.method] = when (e.type) {
              "sale" -> b.copy(sales = addExact(b.sales, r.amount), net = addExact(b.net, r.amount))
              "receipt" -> b.copy(receipts = addExact(b.receipts, r.amount), net = addExact(b.net, r.amount))
              else -> b.copy(refunds = addExact(b.refunds, r.amount), net = addExact(b.net, -r.amount))
            }
          }
        }
        "cash_in" -> { cashIn = addExact(cashIn, e.amount); c = c.copy(cashIn = c.cashIn + 1) }
        "cash_out" -> { cashOut = addExact(cashOut, e.amount); c = c.copy(cashOut = c.cashOut + 1) }
        "no_sale" -> c = c.copy(noSale = c.noSale + 1)
      }
    }
    val cashId = s.table.cashId
    val expected = LinkedHashMap<String, Long>()
    expected[cashId] = addExact(addExact(s.float, methods[cashId]?.net ?: 0L), cashIn - cashOut)
    for ((id, m) in methods) {
      if (id != cashId && s.table.kindOf(id) in Pay.COUNTABLE) expected[id] = m.net
    }
    return Totals(methods, Moves(cashIn, cashOut), c, expected)
  }

  private fun reportOf(s: Shift, kind: String, at: Long, by: String?): ShiftReport {
    val x = totals(s)
    return ShiftReport(kind, s.id, s.currency, at, clean(by), s.openedAt, s.openedBy, s.float, x.methods, x.moves, x.expected, x.events)
  }

  /** PAY-15 — گزارشِ X: عکسِ میانِ شیفت؛ چیزی را نمی‌بندد و هر چند بار گرفتنی است */
  fun xReport(s: Shift?, at: Long, by: String? = null): PayResult<ShiftReport> {
    if (s == null || s.status != "open") return PayResult.Err("SHIFT_CLOSED")
    if (!nonNegExact(at) || at < s.openedAt) return PayResult.Err("INVALID_TIME")
    return PayResult.Ok(reportOf(s, "X", at, by))
  }

  private val DECIMAL = Regex("^[1-9][0-9]*$")

  /** سنجش و یک‌دست‌کردنِ ورودیِ بستن، بی نگاه به حالتِ شیفت; دومی جمعِ نقدِ شمرده‌شده است */
  private fun buildClose(s: Shift, id: String, c: CloseShiftInput): PayResult<Pair<CloseOp, Long>> {
    val t = s.table
    val denoms = LinkedHashMap<String, Long>()
    var cash = 0L
    for ((k, n) in c.counts) {
      val d = if (DECIMAL.matches(k)) k.toLongOrNull() else null
      if (d == null || !Pay.isDenomination(d, s.coins)) return PayResult.Err("BAD_DENOMINATION", denomination = k)
      if (!nonNegExact(n)) return PayResult.Err("INVALID_COUNT")
      if (n > 0) { denoms[k] = n; cash = addExact(cash, mulExact(d, n)) }
    }
    val methodCounts = LinkedHashMap<String, Long>()
    for ((k, v) in c.methodCounts) {
      val kind = t.kindOf(k)
      if (kind == null || k == t.cashId || kind !in Pay.COUNTABLE) return PayResult.Err("UNKNOWN_METHOD", method = k)
      if (!nonNegExact(v)) return PayResult.Err("INVALID_COUNT")
      methodCounts[k] = v
    }
    return PayResult.Ok(CloseOp(id, c.at, clean(c.by), denoms, methodCounts, c.blind, clean(c.reason)) to cash)
  }

  /** PAY-13، 14، 16 — بستن با شمارشِ اسکناس و هر روش؛ نتیجه شیفتِ بسته با Z است */
  fun close(s: Shift?, c: CloseShiftInput): PayResult<Shift> {
    if (s == null) return PayResult.Err("SHIFT_CLOSED")
    val id = clean(c.id) ?: return PayResult.Err("ID_REQUIRED")
    if (s.status != "open") {
      if (s.closeOp?.id != id) return PayResult.Err("SHIFT_CLOSED")
      return when (val b = buildClose(s, id, c)) {
        is PayResult.Ok -> if (b.value.first == s.closeOp) PayResult.Ok(s, duplicate = true) else PayResult.Err("ID_CONFLICT")
        is PayResult.Err -> b
      }
    }
    if (!nonNegExact(c.at) || c.at < s.openedAt) return PayResult.Err("INVALID_TIME")
    val (op, cash) = when (val b = buildClose(s, id, c)) { is PayResult.Ok -> b.value; is PayResult.Err -> return b }
    val counted = LinkedHashMap<String, Long>()
    counted[s.table.cashId] = cash
    counted.putAll(op.methodCounts)
    val rep = reportOf(s, "Z", op.at, op.by)
    val expected = rep.expected!!
    val variance = LinkedHashMap<String, Long?>()
    var over = false
    var review = false
    for (m in expected.keys + counted.keys) {
      if (m in variance) continue
      val n = counted[m]
      if (n == null) { variance[m] = null; continue }
      val v = addExact(n, -(expected[m] ?: 0L))
      variance[m] = v
      if (v != 0L) review = true
      if ((if (v < 0) -v else v) > s.threshold) over = true
    }
    //  ⛔ شمارشِ کور هرگز به‌خاطرِ اختلاف رد نمی‌شود: ردِ «بالای آستانه» یک پیشگوست (شاگرد با عوض کردنِ عدد
    //  موردِ انتظار را تا ±آستانه پیدا می‌کرد). بی دلیل هم بسته می‌شود؛ اختلاف در Z و `needsReview` برای مدیر
    if (over && op.reason == null && !op.blind) return PayResult.Err("REASON_REQUIRED", variance = variance)
    val z = rep.copy(
      counted = counted, denominations = op.counts, variance = variance,
      reason = op.reason, needsReview = review, blind = op.blind,
    )
    return PayResult.Ok(s.copy(status = "closed", closedAt = op.at, closeOp = op, z = z))
  }

  fun zReport(s: Shift?): PayResult<ShiftReport> =
    if (s?.status == "closed" && s.z != null) PayResult.Ok(s.z) else PayResult.Err("SHIFT_OPEN")

  /** شمارشِ کور: صندوقدار نه مبلغِ مورد انتظار را می‌بیند نه اختلاف را */
  fun cashierView(r: ShiftReport): ShiftReport = if (r.blind == true) r.copy(expected = null, variance = null) else r
}
