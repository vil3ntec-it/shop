package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.Purchase
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.SupplierPayment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  پرداخت و صندوق — همان بردارهای مرجعی که `server/test/pay-core.test.js`
 *  برای `license/pay-core.js` می‌خواند (`license/test-vectors/pay-v1.json`).
 *  خروجیِ کاتلین به همان شکلِ JSONِ JS برگردانده و با بردار برابر سنجیده می‌شود.
 *
 *  `legacyShift`: خودِ `CashShift.totals`ِ امروز روی همان داده‌ها همان عددِ
 *  بردار را می‌دهد (همان عددی که `shiftTotals`ِ وب می‌دهد).
 *
 *  ورودیِ اعشاری (۱۰٫۵ پول) در کاتلین اصلاً ساختنی نیست (Long)؛ برای آن
 *  بردارها فقط سنجیده می‌شود که انتظار خطای INVALID_* است.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا می‌شود.
 */
class PayCoreTest {

  private val v: JsonObject = run {
    val f = listOf(
      "../../license/test-vectors/pay-v1.json",   // android-native/app
      "../license/test-vectors/pay-v1.json",      // desktop
      "license/test-vectors/pay-v1.json",         // ریشهٔ مخزن
    ).map { File(it) }.firstOrNull { it.isFile }
      ?: error("بردارهای پرداخت پیدا نشد (cwd=${File(".").absoluteFile})")
    Json.parseToJsonElement(f.readText()).jsonObject
  }

  // ── خواندنِ JSON ─────────────────────────────────────────
  private class NonInteger : RuntimeException()

  private fun JsonObject.has(k: String) = this[k] != null && this[k] !is JsonNull
  private fun JsonObject.str(k: String): String? = if (has(k)) this[k]!!.jsonPrimitive.content else null
  private fun JsonObject.bool(k: String): Boolean? = if (has(k)) this[k]!!.jsonPrimitive.booleanOrNull else null
  private fun JsonElement.lng(): Long {
    val p = jsonPrimitive
    return p.longOrNull ?: if (p.doubleOrNull != null) throw NonInteger() else error("عدد نیست: $p")
  }
  private fun JsonObject.lng(k: String): Long? = if (has(k)) this[k]!!.lng() else null
  private fun JsonObject.arr(k: String): List<JsonObject> = if (has(k)) this[k]!!.jsonArray.map { it.jsonObject } else emptyList()
  private fun JsonObject.obj(k: String): JsonObject? = if (has(k)) this[k]!!.jsonObject else null
  private fun longMap(o: JsonObject?): Map<String, Long> = o?.mapValues { it.value.lng() } ?: emptyMap()

  private fun methodsOf(a: List<JsonObject>) = a.map {
    PayMethod(
      id = it.str("id").orEmpty(), kind = it.str("kind").orEmpty(), name = it.str("name").orEmpty(),
      needsRef = it.bool("needsRef") ?: false, limit = it.lng("limit"), enabled = it.bool("enabled") ?: true,
    )
  }
  private fun methodsOrNull(o: JsonObject) = if (o.has("methods")) methodsOf(o.arr("methods")) else null

  private fun settleInput(o: JsonObject) = SettleInput(
    total = o.lng("total")!!,
    tenders = o.arr("tenders").map { Tender(it.str("method").orEmpty(), it.lng("amount")!!, it.str("currency"), it.str("ref")) },
    currency = o.str("currency") ?: "AFN",
    cashRounding = o.lng("cashRounding") ?: 0,
    customerId = o.str("customerId"),
    methods = methodsOrNull(o),
  )

  // ── برگرداندن به JSON ───────────────────────────────────
  private fun j(v: Any?): JsonElement = when (v) {
    null -> JsonNull
    is JsonElement -> v
    is String -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { (k, x) -> k.toString() to j(x) })
    is List<*> -> JsonArray(v.map { j(it) })
    else -> error("نوعِ ناشناخته: $v")
  }

  private fun <T> res(r: PayResult<T>, ok: (T) -> Map<String, Any?>): JsonElement = when (r) {
    is PayResult.Ok -> j(mapOf("ok" to true) + ok(r.value))
    is PayResult.Err -> j(
      linkedMapOf<String, Any?>("ok" to false, "error" to r.code).apply {
        r.index?.let { put("index", it) }
        r.field?.let { put("field", it) }
        r.method?.let { put("method", it) }
        r.denomination?.let { put("denomination", it) }
        r.refundable?.let { put("refundable", it) }
        r.variance?.let { put("variance", it) }
      }
    )
  }

  private fun methodJson(m: PayMethod) = mapOf(
    "id" to m.id, "kind" to m.kind, "name" to m.name, "needsRef" to m.needsRef, "limit" to m.limit, "enabled" to m.enabled,
  )

  private fun settlementJson(s: Settlement) = mapOf(
    "status" to s.status, "total" to s.total, "rounding" to s.rounding, "due" to s.due, "tendered" to s.tendered,
    "change" to s.change, "remaining" to s.remaining, "toAccount" to s.toAccount,
    "payments" to s.payments.map { mapOf("method" to it.method, "kind" to it.kind, "amount" to it.amount, "ref" to it.ref, "status" to it.status) },
  )

  private fun reportJson(r: ShiftReport): Map<String, Any?> {
    val m = linkedMapOf<String, Any?>(
      "kind" to r.kind, "at" to r.at, "by" to r.by, "openedAt" to r.openedAt, "openedBy" to r.openedBy, "float" to r.float,
      "methods" to r.methods.mapValues { (_, t) -> mapOf("sales" to t.sales, "refunds" to t.refunds, "receipts" to t.receipts, "net" to t.net) },
      "moves" to mapOf("in" to r.moves.cashIn, "out" to r.moves.cashOut),
      "events" to mapOf(
        "sales" to r.events.sales, "refunds" to r.events.refunds, "receipts" to r.events.receipts,
        "cashIn" to r.events.cashIn, "cashOut" to r.events.cashOut, "noSale" to r.events.noSale,
      ),
    )
    r.expected?.let { m["expected"] = it }
    if (r.kind == "Z") {
      m["counted"] = r.counted
      m["denominations"] = r.denominations
      r.variance?.let { m["variance"] = it }
      m["reason"] = r.reason
      m["needsReview"] = r.needsReview
      m["blind"] = r.blind
    }
    return m
  }

  /** ورودیِ اعشاری در کاتلین ساختنی نیست؛ انتظار باید خطای INVALID_* باشد */
  private fun orNonInteger(name: String, expect: JsonElement, block: () -> Unit) {
    try {
      block()
    } catch (e: NonInteger) {
      val o = expect.jsonObject
      assertEquals(name, JsonPrimitive(false), o["ok"])
      assertTrue(name, o.str("error") in setOf("INVALID_AMOUNT", "INVALID_RATE", "INVALID_COUNT"))
    }
  }

  // ── آزمون‌ها ──────────────────────────────────────────────
  @Test fun constants() {
    val c = v["constants"]!!.jsonObject
    assertEquals(c["notes"]!!.jsonArray.map { it.lng() }, Pay.NOTES)
    assertEquals(c["coins"]!!.jsonArray.map { it.lng() }, Pay.COINS)
    assertEquals(c["notes"]!!.jsonArray.map { it.lng() }, Pay.denominations(false))
    assertEquals(c.lng("defaultVarianceThreshold"), Pay.DEFAULT_VARIANCE_THRESHOLD)
    assertEquals(j(Pay.STATUSES), c["statuses"])
    assertEquals(j(Pay.TRANSITIONS), c["transitions"])
    assertEquals(j(ShiftCore.MOVE_REASONS), c["moveReasons"])
    assertEquals(j(Pay.DEFAULT_METHODS.map { methodJson(it) }), c["defaultMethods"])
    Pay.COINS.forEach { assertTrue(!Pay.isDenomination(it, false) && Pay.isDenomination(it, true)) }
  }

  @Test fun transitions() {
    val c = v["constants"]!!.jsonObject
    val statuses = c["statuses"]!!.jsonArray.map { it.jsonPrimitive.content }
    val allowed = c["transitions"]!!.jsonObject
    for (from in statuses) for (to in statuses) {
      val ok = allowed[from]!!.jsonArray.any { it.jsonPrimitive.content == to }
      assertEquals("$from → $to", ok, Pay.canTransition(from, to))
    }
    for (x in v["transitionErrors"]!!.jsonArray.map { it.jsonObject }) {
      assertEquals(x["expect"], res(Pay.transition(x.str("from")!!, x.str("to")!!)) { mapOf("status" to it) })
    }
  }

  @Test fun methodTables() {
    for (c in v["methodTables"]!!.jsonArray.map { it.jsonObject }) {
      val got = res(Pay.defineMethods(methodsOf(c.arr("input")))) { l -> mapOf("methods" to l.map { methodJson(it) }) }
      assertEquals(c.str("name"), c["expect"], got)
    }
  }

  @Test fun settle() {
    var n = 0
    for (c in v["settle"]!!.jsonArray.map { it.jsonObject }) orNonInteger(c.str("name")!!, c["expect"]!!) {
      val r = Pay.settle(settleInput(c.obj("input")!!))
      assertEquals(c.str("name"), c["expect"], res(r) { settlementJson(it) })
      if (r is PayResult.Ok) {
        val s = r.value
        val paid = s.payments.sumOf { it.amount }
        assertEquals(s.total + s.rounding, s.due)
        assertEquals(c.str("name"), s.due, paid + if (s.status == "short") s.remaining else 0)
      }
      n++
    }
    assertTrue(n >= 25)
  }

  @Test fun exactCash() {
    for (c in v["exactCash"]!!.jsonArray.map { it.jsonObject }) {
      val input = settleInput(c.obj("input")!!)
      val r = Pay.exactCash(input)
      assertEquals(c.str("name"), c["expect"], res(r) { mapOf("amount" to it) })
      if (r is PayResult.Ok) {
        val more = if (r.value > 0) listOf(Tender("cash", r.value)) else emptyList()
        val s = (Pay.settle(input.copy(tenders = input.tenders + more)) as PayResult.Ok).value
        assertEquals("paid", s.status)
        assertEquals(0L, s.change)
      }
    }
  }

  /** همان دنباله و همان ترتیبِ فراخوانیِ `sweepCases`ِ آزمونِ JS */
  @Test fun sweepDigestMatchesJs() {
    val sw = v["sweep"]!!.jsonObject
    var seed = sw.lng("seed")!!
    fun rnd(n: Long): Long { seed = (seed * 48271) % 2147483647; return seed % n }
    val wallets = listOf("hesabpay", "afpay_card", "hawala")
    val lines = ArrayList<String>()
    repeat(sw.lng("count")!!.toInt()) {
      val total = rnd(500) * 100 + rnd(3) * 50
      val tenders = ArrayList<Tender>()
      var k = rnd(4)
      while (k > 0) {
        if (rnd(2) == 1L) tenders += Tender("cash", Pay.NOTES[rnd(Pay.NOTES.size.toLong()).toInt()])
        else {
          val method = wallets[rnd(3).toInt()]
          tenders += Tender(method, 100 + rnd(maxOf(1L, total)), ref = "R$k")
        }
        k--
      }
      val cashRounding = listOf(0L, 500L, 1000L)[rnd(3).toInt()]
      val customer = if (rnd(2) == 1L) "c1" else null
      lines += when (val r = Pay.settle(SettleInput(total, tenders, cashRounding = cashRounding, customerId = customer))) {
        is PayResult.Err -> listOf("E", r.code, r.index?.toString() ?: "").joinToString("|")
        is PayResult.Ok -> with(r.value) {
          listOf(status, total, rounding, due, tendered, change, remaining, toAccount,
            payments.joinToString(",") { listOf(it.method, it.amount, it.ref ?: "", it.status).joinToString(":") }).joinToString("|")
        }
      }
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(lines.joinToString("\n").toByteArray(Charsets.UTF_8))
      .joinToString("") { "%02x".format(it) }
    assertEquals(sw.str("digest"), digest)
  }

  @Test fun exchange() {
    for (c in v["exchange"]!!.jsonArray.map { it.jsonObject }) orNonInteger(c.str("name")!!, c["expect"]!!) {
      val o = c.obj("input")!!
      val from = o.obj("from")!!
      val r = Pay.exchange(
        ExchangeInput(
          fromCurrency = from.str("currency")!!, fromAmount = from.lng("amount")!!, to = o.str("to") ?: "AFN",
          rate = o.lng("rate")!!, source = o.str("source"), at = o.lng("at"), approvedBy = o.str("approvedBy"),
        )
      )
      val got = res(r) { x ->
        mapOf(
          "record" to mapOf(
            "from" to mapOf("currency" to x.fromCurrency, "amount" to x.fromAmount),
            "to" to mapOf("currency" to x.toCurrency, "amount" to x.toAmount),
            "rate" to x.rate, "source" to x.source, "at" to x.at, "approvedBy" to x.approvedBy,
          )
        )
      }
      assertEquals(c.str("name"), c["expect"], got)
    }
  }

  @Test fun refund() {
    for (c in v["refund"]!!.jsonArray.map { it.jsonObject }) {
      val o = c.obj("input")!!
      val input = RefundInput(
        payments = o.arr("payments").map { PaidRow(it.str("method")!!, it.lng("amount")!!, it.lng("refunded") ?: 0, it.str("status")!!) },
        amount = o.lng("amount")!!, mode = o.str("mode")!!, approvedBy = o.str("approvedBy"),
        customerId = o.str("customerId"), accountOutstanding = o.lng("accountOutstanding"), methods = methodsOrNull(o),
      )
      val r = Pay.refund(input)
      val got = res(r) { x ->
        mapOf(
          "amount" to x.amount, "approvedBy" to x.approvedBy,
          "payouts" to x.payouts.map { mapOf("method" to it.method, "kind" to it.kind, "amount" to it.amount) },
          "payments" to x.payments.map { mapOf("method" to it.method, "amount" to it.amount, "refunded" to it.refunded, "status" to it.status) },
        )
      }
      assertEquals(c.str("name"), c["expect"], got)
      if (r is PayResult.Ok) {
        assertEquals(input.amount, r.value.payouts.sumOf { it.amount })
        assertEquals(input.amount, r.value.payments.sumOf { it.refunded } - input.payments.sumOf { it.refunded })
      }
    }
  }

  private fun runSteps(name: String, steps: List<JsonObject>): Shift? {
    var shift: Shift? = null
    var last: ShiftReport? = null
    steps.forEachIndexed { i, st ->
      val label = "$name — گامِ ${i + 1} (${st.str("op")})"
      val expect = st["expect"] ?: JsonObject(mapOf("ok" to JsonPrimitive(true)))
      val o = st.obj("input") ?: JsonObject(emptyMap())
      orNonInteger(label, expect) {
        val got: JsonElement = when (st.str("op")) {
          "open", "record" -> {
            val r = if (st.str("op") == "open") {
              ShiftCore.open(
                OpenShiftInput(
                  at = o.lng("at")!!, float = o.lng("float")!!, by = o.str("by"),
                  threshold = o.lng("threshold") ?: Pay.DEFAULT_VARIANCE_THRESHOLD, coins = o.bool("coins") ?: false,
                  methods = methodsOrNull(o),
                )
              )
            } else {
              ShiftCore.record(
                shift,
                ShiftEventInput(
                  type = o.str("type")!!, at = o.lng("at")!!,
                  rows = if (o.has("rows")) o.arr("rows").map { ShiftRow(it.str("method").orEmpty(), it.lng("amount")!!) } else null,
                  method = o.str("method"), amount = o.lng("amount"), reason = o.str("reason"), note = o.str("note"),
                  by = o.str("by"), approvedBy = o.str("approvedBy"),
                )
              )
            }
            if (r is PayResult.Ok) shift = r.value
            res(r) { emptyMap() }
          }
          "x" -> res(ShiftCore.xReport(shift, o.lng("at")!!, o.str("by"))) { mapOf("report" to reportJson(it)) }
          "close" -> {
            val r = ShiftCore.close(
              shift,
              CloseShiftInput(
                at = o.lng("at")!!, counts = longMap(o.obj("counts")), methodCounts = longMap(o.obj("methodCounts")),
                by = o.str("by"), blind = o.bool("blind") ?: false, reason = o.str("reason"),
              )
            )
            if (r is PayResult.Ok) { shift = r.value; last = r.value.z }
            res(r) { mapOf("report" to reportJson(it.z!!)) }
          }
          "z" -> {
            val r = ShiftCore.zReport(shift)
            if (r is PayResult.Ok) last = r.value
            res(r) { mapOf("report" to reportJson(it)) }
          }
          "cashierView" -> j(mapOf("report" to reportJson(ShiftCore.cashierView(last!!))))
          else -> error("گامِ ناشناخته: ${st.str("op")}")
        }
        assertEquals(label, expect, got)
      }
    }
    return shift
  }

  @Test fun shift() {
    for (c in v["shift"]!!.jsonArray.map { it.jsonObject }) runSteps(c.str("name")!!, c.arr("steps"))
  }

  @Test fun shiftIsImmutableAndVarianceNeverCorrected() {
    val s0 = (ShiftCore.open(OpenShiftInput(at = 0, float = 1000)) as PayResult.Ok).value
    val bad = ShiftCore.record(s0, ShiftEventInput("cash_out", 1, amount = 2000, reason = "owner_withdrawal"))
    assertEquals("INSUFFICIENT_CASH", (bad as PayResult.Err).code)
    val s1 = (ShiftCore.record(s0, ShiftEventInput("sale", 1, rows = listOf(ShiftRow("cash", 500)))) as PayResult.Ok).value
    assertEquals(0, s0.events.size)
    val closed = (ShiftCore.close(s1, CloseShiftInput(at = 2, counts = mapOf("1000" to 1L))) as PayResult.Ok).value
    assertEquals(-500L, closed.z!!.variance!!["cash"])
    assertEquals(1500L, closed.z!!.expected!!["cash"])
    assertEquals(closed.z, (ShiftCore.zReport(closed) as PayResult.Ok).value)
  }

  /** رفتارِ امروز: خودِ `CashShift.totals` روی همان داده‌ها؛ هسته روی رویدادهای هم‌ارز */
  @Test fun legacyShift() {
    for (c in v["legacyShift"]!!.jsonArray.map { it.jsonObject }) {
      val name = c.str("name")!!
      val d = c.obj("data")!!
      fun dbl(o: JsonObject, k: String) = o[k]!!.jsonPrimitive.doubleOrNull!!
      val data = ShopData(
        sales = d.arr("sales").mapIndexed { i, s ->
          Sale(id = "s$i", finalTotal = dbl(s, "finalTotal"), debtGiven = dbl(s, "debtGiven"), status = s.str("status")!!, createdAt = s.lng("createdAt")!!)
        },
        transactions = d.arr("transactions").mapIndexed { i, t ->
          DebtTransaction(id = "t$i", type = t.str("type")!!, amount = dbl(t, "amount"), createdAt = t.lng("createdAt")!!)
        },
        saleReturns = d.arr("returns").mapIndexed { i, r -> SaleReturn(id = "r$i", amount = dbl(r, "amount"), createdAt = r.lng("createdAt")!!) },
        expenses = d.arr("expenses").mapIndexed { i, e -> Expense(id = "e$i", amount = dbl(e, "amount"), createdAt = e.lng("createdAt")!!) },
        supplierPayments = d.arr("supplierPayments").mapIndexed { i, p -> SupplierPayment(id = "p$i", amount = dbl(p, "amount"), createdAt = p.lng("createdAt")!!) },
        purchases = d.arr("purchases").mapIndexed { i, p -> Purchase(id = "u$i", paidAmount = dbl(p, "paidAmount"), createdAt = p.lng("createdAt")!!) },
      )
      val legacy = CashShift.totals(data, c.lng("from")!!, c.lng("to")!!, dbl(c, "opening")).expected
      assertEquals("$name — موتورِ امروز", dbl(c, "legacyExpected"), legacy, 0.0)

      val shift = runSteps(name, c.arr("steps"))
      val x = (ShiftCore.xReport(shift, c.lng("to")!!) as PayResult.Ok).value
      assertEquals("$name — هسته", c["coreExpected"], j(x.expected))
      val coreCash = c.obj("coreExpected")!!.lng("cash")!!
      if (c.bool("same") == true) assertEquals(name, Math.round(legacy * 100), coreCash)
      else assertNotEquals(name, Math.round(legacy * 100), coreCash)
    }
  }
}
