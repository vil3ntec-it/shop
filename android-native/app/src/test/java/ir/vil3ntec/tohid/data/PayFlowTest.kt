package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.pay.PayMethod
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.core.pay.SettleInput
import ir.vil3ntec.tohid.core.pay.Settlement
import ir.vil3ntec.tohid.core.pay.ShiftCore
import ir.vil3ntec.tohid.core.pay.Tender
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  سیم‌کشیِ پرداخت و صندوق (v3 A4؛ S01، S06، S12) — `PayFlow` (فقط برنامه‌های بومی).
 *
 *  همان بردارِ مرجعِ هسته (`core/test-vectors/pay-v1.json`) که `PayCoreTest` و
 *  `server/test/pay-core.test.js` می‌خوانند:
 *   - `legacyShift`: دفترِ بی فیلدِ تازه از راهِ سیم‌کشی همان عددِ `CashShift.totals`ِ امروز؛
 *     با `payments` همان `coreExpected`؛ Zِ ذخیره‌شده با لغوِ بعدی ثابت.
 *   - `settle`: فیلدهای فروش از هر تسویهٔ موفقِ بردار همان معنای امروز را دارند.
 *   - `SalesEngine.record` با تسویه همان فیلدها را می‌نویسد و بی آن هیچ فیلدِ تازه‌ای نه.
 */
class PayFlowTest {

  private fun vector(name: String): JsonObject {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/$name")
      if (f.isFile) return Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/$name پیدا نشد")
  }

  private val v: JsonObject = vector("pay-v1.json")

  private fun JsonObject.has(k: String) = this[k] != null && this[k] !is JsonNull
  private fun JsonObject.str(k: String): String? = if (has(k)) this[k]!!.jsonPrimitive.content else null
  private fun JsonObject.lng(k: String): Long? = if (has(k)) this[k]!!.jsonPrimitive.longOrNull else null
  private fun JsonObject.dbl(k: String): Double = this[k]!!.jsonPrimitive.doubleOrNull!!
  private fun JsonObject.arr(k: String): List<JsonObject> = if (has(k)) this[k]!!.jsonArray.map { it.jsonObject } else emptyList()

  private var seq = 0
  private val newId: () -> String = { "id${++seq}" }

  private fun <T> ok(o: PayFlow.Outcome<T>): T = when (o) {
    is PayFlow.Outcome.Ok -> o.value
    is PayFlow.Outcome.Bad -> throw AssertionError("انتظارِ موفقیت: ${o.problem}")
  }
  private fun <T> bad(o: PayFlow.Outcome<T>): PayFlow.Problem = when (o) {
    is PayFlow.Outcome.Bad -> o.problem
    is PayFlow.Outcome.Ok -> throw AssertionError("انتظارِ خطا: ${o.value}")
  }

  private fun ledgerOf(d: JsonObject) = ShopData(
    sales = d.arr("sales").mapIndexed { i, s -> Sale(id = "s$i", finalTotal = s.dbl("finalTotal"), debtGiven = s.dbl("debtGiven"), status = s.str("status")!!, createdAt = s.lng("createdAt")!!) },
    transactions = d.arr("transactions").mapIndexed { i, t -> DebtTransaction(id = "t$i", type = t.str("type")!!, amount = t.dbl("amount"), createdAt = t.lng("createdAt")!!) },
    saleReturns = d.arr("returns").mapIndexed { i, r -> SaleReturn(id = "r$i", amount = r.dbl("amount"), createdAt = r.lng("createdAt")!!) },
    expenses = d.arr("expenses").mapIndexed { i, e -> Expense(id = "e$i", amount = e.dbl("amount"), createdAt = e.lng("createdAt")!!) },
    supplierPayments = d.arr("supplierPayments").mapIndexed { i, p -> SupplierPayment(id = "p$i", amount = p.dbl("amount"), createdAt = p.lng("createdAt")!!) },
    purchases = d.arr("purchases").mapIndexed { i, p -> Purchase(id = "u$i", paidAmount = p.dbl("paidAmount"), createdAt = p.lng("createdAt")!!) },
  )
  private fun expectedOf(o: JsonObject): Map<String, Long> = o.entries.associate { (k, x) -> k to x.jsonPrimitive.longOrNull!! }

  @Test fun `legacyShift - دفترِ بی فیلدِ تازه همان عددِ CashShift ِ امروز`() {
    for (c in v["legacyShift"]!!.jsonArray.map { it.jsonObject }) {
      val name = c.str("name")!!
      val d = ledgerOf(c["data"]!!.jsonObject)
      val open = AuditEntry(id = "sh-1", type = CashShift.OPEN, createdAt = c.lng("from")!!, amount = c.dbl("opening"))
      val legacy = CashShift.totals(d, c.lng("from")!!, c.lng("to")!!, c.dbl("opening")).expected
      assertEquals(name, c.dbl("legacyExpected"), legacy, 0.0)
      val r = ok(PayFlow.report(d, open, null, c.lng("to")!!))
      assertTrue(name, r.skipped.isEmpty())
      assertEquals(name, Math.round(legacy * 100), r.report.expected!!["cash"])
      if (c["same"]!!.jsonPrimitive.booleanOrNull == true) assertEquals(name, expectedOf(c["coreExpected"]!!.jsonObject), r.report.expected)
    }
  }

  @Test fun `legacyShift ۲ - فروشِ کیف‌پولی با payments همان coreExpected`() {
    val c = v["legacyShift"]!!.jsonArray.map { it.jsonObject }.first { it["coreExpected"]!!.jsonObject.has("hesabpay") }
    val base = ledgerOf(c["data"]!!.jsonObject)
    val d = base.copy(sales = base.sales.map { it.copy(payments = listOf(SalePayment("hesabpay", "wallet", 40000, "pending", "R1", "manual"))) })
    val r = ok(PayFlow.report(d, AuditEntry(id = "sh-1", createdAt = c.lng("from")!!, amount = c.dbl("opening")), null, c.lng("to")!!))
    assertEquals(expectedOf(c["coreExpected"]!!.jsonObject), r.report.expected)
    //  صندوقِ قدیمیِ همین برنامه (`CashShift`، پرچمِ خاموش) هم کیف پول را نقدِ دخل نمی‌شمارد
    val legacy = CashShift.totals(d, c.lng("from")!!, c.lng("to")!!, c.dbl("opening")).expected
    assertEquals(expectedOf(c["coreExpected"]!!.jsonObject)["cash"], Math.round(legacy * 100))
  }

  @Test fun `legacyShift ۳ - Zِ ذخیره‌شده با لغوِ بعدی ثابت؛ رفت‌وبرگشتِ JSON`() {
    val c = v["legacyShift"]!!.jsonArray.map { it.jsonObject }.first { s -> s["data"]!!.jsonObject.arr("sales").any { it.str("status") == "cancelled" } }
    val base = ledgerOf(c["data"]!!.jsonObject)
    val d = base.copy(sales = base.sales.map { it.copy(status = "completed") })
    val open = AuditEntry(id = "sh-1", createdAt = c.lng("from")!!, amount = c.dbl("opening"))
    val closed = ok(PayFlow.closeRow(d, open, PayFlow.Count(mapOf("50000" to 1L)), c.lng("to")!!, newId))
    assertEquals(expectedOf(c["coreExpected"]!!.jsonObject), closed.report.expected)
    assertEquals(500.0, closed.row.amount, 0.0)
    assertEquals(closed.report, PayFlow.reportOf(PayFlow.reportJson(closed.report)))
    val later = d.copy(sales = d.sales.map { it.copy(status = "cancelled") })
    val again = ok(PayFlow.report(later, open, closed.row, c.lng("to")!! + 1))
    assertTrue(again.stored)
    assertEquals(expectedOf(c["coreExpected"]!!.jsonObject), again.report.expected)
  }

  private fun settleInput(o: JsonObject) = SettleInput(
    total = o.lng("total")!!,
    tenders = o.arr("tenders").map { Tender(it.str("method").orEmpty(), it.lng("amount")!!, it.str("currency"), it.str("ref")) },
    currency = o.str("currency") ?: "AFN",
    cashRounding = o.lng("cashRounding") ?: 0,
    customerId = o.str("customerId"),
    methods = if (o.has("methods")) o.arr("methods").map { PayMethod(it.str("id")!!, it.str("kind")!!, it.str("name")!!) } else null,
  )

  @Test fun `settle - فیلدهای فروش از هر تسویهٔ موفقِ بردار همان معنای امروز`() {
    var n = 0
    for (c in v["settle"]!!.jsonArray.map { it.jsonObject }) {
      if (c["expect"]!!.jsonObject["ok"]?.jsonPrimitive?.booleanOrNull != true) continue
      val name = c.str("name")!!
      val s = (Pay.settle(settleInput(c["input"]!!.jsonObject)) as PayResult.Ok).value
      if (s.status == "short") continue
      n++
      val f = PayFlow.saleFields(s)
      assertEquals(name, s.due, f.payments.sumOf { it.amountMinor })
      assertEquals(name, s.toAccount, Math.round(f.debtGiven * 100))
      assertEquals(name, s.total, Math.round(f.paidAmount * 100) + Math.round(f.debtGiven * 100))
      //  `paymentMethod`ِ قدیمی: به حساب ⇒ credit؛ بی غیرنقد ⇒ cash؛ وگرنه هرگز cash نیست
      val nonCash = s.payments.filter { it.kind != "cash" && it.kind != "account" }.map { it.kind }.toSet()
      val hasCash = s.payments.any { it.kind == "cash" }
      assertEquals(name, when {
        s.toAccount > 0 -> "credit"
        nonCash.isEmpty() -> "cash"
        nonCash.size == 1 && !hasCash -> nonCash.single()
        else -> "mixed"
      }, f.paymentMethod)
      assertEquals(name, s.change, f.changeMinor)
      f.payments.forEach {
        if (it.kind == "cash" || it.kind == "account") assertEquals(name, "confirmed", it.status)
        else { assertEquals(name, "pending", it.status); assertEquals(name, "manual", it.verification) }
      }
    }
    assertTrue("دست‌کم ده بردار: $n", n >= 10)
  }

  @Test fun `S01 - نقدِ دقیق با Enterِ خالی و پولِ پس از ۱۰۰۰`() {
    val draft = PayFlow.Draft(total = 850.0)
    assertEquals(850.0, ok(PayFlow.exactCash(draft)), 0.0)
    val s = ok(PayFlow.checkout(draft.copy(cash = "1000")))
    val f = PayFlow.saleFields(s)
    assertEquals(15000L, f.changeMinor)
    assertEquals(100000L, f.tenderedMinor)
    assertEquals(listOf(SalePayment("cash", "cash", 85000, "confirmed")), f.payments)
    assertEquals(850.0, f.paidAmount, 0.0)
    assertEquals("cash", f.paymentMethod)
  }

  @Test fun `S06 - ۱۰۰۰ نقد و ۱۴۵۰ HesabPay؛ دو ردیف، ثبتِ دستی؛ شیفت جدا`() {
    val draft = PayFlow.Draft(total = 2450.0, cash = "1000", rows = listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1450")))
    val f = PayFlow.saleFields(ok(PayFlow.checkout(draft)))
    assertEquals(
      listOf(SalePayment("cash", "cash", 100000, "confirmed"), SalePayment("hesabpay", "wallet", 145000, "pending", "HP-77", "manual")),
      f.payments,
    )
    assertEquals(245000L, f.payments.sumOf { it.amountMinor })
    assertEquals("mixed", f.paymentMethod)
    val noRef = bad(PayFlow.preview(draft.copy(rows = listOf(PayFlow.DraftRow("hesabpay", "", "1450")))))
    assertEquals("REF_REQUIRED", noRef.code)
    assertEquals(0, noRef.index)
    assertTrue(PayFlow.message(noRef).contains("HesabPay"))
    val d = ShopData(sales = listOf(Sale(id = "S06", finalTotal = 2450.0, createdAt = 100, payments = f.payments)))
    val x = ok(PayFlow.report(d, AuditEntry(id = "sh", createdAt = 0), null, 200)).report
    assertEquals(100000L, x.methods.getValue("cash").sales)
    assertEquals(145000L, x.methods.getValue("hesabpay").sales)
    assertEquals(mapOf("cash" to 100000L, "hesabpay" to 145000L), x.expected)
  }

  @Test fun `پرداختِ جزئی به حساب؛ بی مشتری رد؛ پولِ پس فقط از نقد`() {
    val part = PayFlow.saleFields(ok(PayFlow.checkout(PayFlow.Draft(2000.0, "500", customerId = "d1"))))
    assertEquals("credit", part.paymentMethod)
    assertEquals(1500.0, part.debtGiven, 0.0)
    assertEquals(500.0, part.paidAmount, 0.0)
    assertEquals(listOf("cash" to 50000L, "account" to 150000L), part.payments.map { it.method to it.amountMinor })
    val short = bad(PayFlow.checkout(PayFlow.Draft(2000.0, "500")))
    assertEquals("SHORT", short.code)
    assertEquals(150000L, short.remaining)
    assertEquals("NON_CASH_OVERPAY", bad(PayFlow.preview(PayFlow.Draft(1000.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C1", "1200"))))).code)
    assertEquals(2000.0, PayFlow.saleFields(ok(PayFlow.checkout(PayFlow.Draft(2000.0, "0", customerId = "d1")))).debtGiven, 0.0)
    assertEquals(1000.0, ok(PayFlow.exactCash(PayFlow.Draft(2450.0, rows = listOf(PayFlow.DraftRow("hawala", "H", "1450"))))), 0.0)
  }

  @Test fun `paymentMethodِ قدیمی - غیرنقد هرگز cash نیست؛ ردیف‌های دقیق در payments`() {
    fun m(d: PayFlow.Draft) = PayFlow.saleFields(ok(PayFlow.checkout(d))).paymentMethod
    assertEquals("cash", m(PayFlow.Draft(850.0, "1000")))
    assertEquals("card", m(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C1", "1200")))))
    assertEquals("transfer", m(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("hawala", "H1", "1200")))))
    assertEquals("wallet", m(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("hesabpay", "P1", "700"), PayFlow.DraftRow("m_paisa", "P2", "500")))))
    assertEquals("mixed", m(PayFlow.Draft(1200.0, "200", listOf(PayFlow.DraftRow("afpay_card", "C1", "1000")))))
    assertEquals("mixed", m(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("hesabpay", "P1", "700"), PayFlow.DraftRow("afpay_card", "C1", "500")))))
    //  بدهی همان credit می‌ماند — SalesEngine بدهی را با همین می‌سازد
    assertEquals("credit", m(PayFlow.Draft(2000.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C1", "500")), customerId = "d1")))
  }

  @Test fun `برچسب و گروهِ روشِ پرداخت - از payments، وگرنه از paymentMethod؛ ردیف‌به‌ردیف روی فاکتور و رسید`() {
    fun sale(d: PayFlow.Draft): Sale {
      val f = PayFlow.saleFields(ok(PayFlow.checkout(d)))
      return Sale(id = "x", finalTotal = d.total, paymentMethod = f.paymentMethod, payments = f.payments, tenderedMinor = f.tenderedMinor, changeMinor = f.changeMinor)
    }
    val cash = sale(PayFlow.Draft(850.0, "1000"))
    val card = sale(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("afpay_card", "C1", "1200"))))
    val hawala = sale(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("hawala", "H1", "1200"))))
    val wallet = sale(PayFlow.Draft(1200.0, rows = listOf(PayFlow.DraftRow("hesabpay", "P1", "1200"))))
    val mixed = sale(PayFlow.Draft(2450.0, "1000", listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1450"))))
    val part = sale(PayFlow.Draft(2000.0, "500", customerId = "d1"))
    val onAccount = sale(PayFlow.Draft(2000.0, "0", customerId = "d1"))
    val all = listOf(cash, card, hawala, wallet, mixed, part, onAccount)
    assertEquals(listOf("نقد", "کارت", "حواله", "کیف پول", "ترکیبی", "ترکیبی", "نسیه"), all.map { PayFlow.payLabel(it) })
    //  فروشِ کاملاً به حسابِ payV2 همان «نسیه»ِ فاکتورِ قدیمیِ هم‌شکل است
    assertEquals(PayFlow.payLabel(Sale(id = "o", finalTotal = 2000.0, paymentMethod = "credit")), PayFlow.payLabel(onAccount))
    assertEquals(listOf("cash", "noncash", "noncash", "noncash", "noncash", "credit", "credit"), all.map { PayFlow.payGroup(it) })
    assertEquals("نقدی", PayFlow.payLabel(cash, cash = "نقدی"))
    //  ردیف‌ها بر `paymentMethod` مقدم‌اند (فاکتورِ پیش از اصلاحِ legacyMethod: کارت با cash)
    assertEquals("کارت", PayFlow.payLabel(card.copy(paymentMethod = "cash")))
    assertEquals(PayFlow.NON_CASH, PayFlow.payGroup(card.copy(paymentMethod = "cash")))
    //  بی `payments` (فاکتورِ امروز، برنامهٔ دیگر): از `paymentMethod`؛ مقدارِ ناشناخته مثلِ امروز نقد
    fun legacy(m: String) = Sale(id = "l", finalTotal = 100.0, paymentMethod = m)
    val keys = listOf("cash", "credit", "card", "wallet", "transfer", "mixed", "store_credit", "")
    assertEquals(
      listOf("نقدی", "نسیه", "کارت", "کیف پول", "حواله", "ترکیبی", "اعتبار فروشگاه", "نقدی"),
      keys.map { PayFlow.payLabel(legacy(it), cash = "نقدی") },
    )
    assertEquals(listOf("cash", "credit", "noncash", "noncash", "noncash", "noncash", "noncash", "cash"), keys.map { PayFlow.payGroup(legacy(it)) })
    //  جزئیات و رسید: ردیف به ردیف (به حساب هم)، بعد دریافتی و پولِ پس
    assertEquals(listOf("نقد" to 500.0, "به حسابِ مشتری" to 1500.0, "دریافتی" to 500.0), PayFlow.receiptLines(part))
    assertEquals(listOf("نقد" to 1000.0, "HesabPay (ثبتِ دستی) — HP-77" to 1450.0, "دریافتی" to 2450.0), PayFlow.receiptLines(mixed))
    //  روشِ ناشناختهٔ جدولِ دکان ⇒ نامِ نوعش، نه شناسهٔ خام
    assertEquals(listOf("کارت (ثبتِ دستی)" to 10.0), PayFlow.receiptLines(Sale(id = "u", payments = listOf(SalePayment("my_bank", "card", 1000, "pending", verification = "manual")))))
  }

  @Test fun `برچسب‌ها همان جدولِ «روش پرداخت»ِ گزارش‌اند (بردارِ مشترکِ report-v1، JS و Kotlin)`() {
    val r = vector("report-v1.json")
    for (ledger in listOf("payKinds", "payOnline")) {
      val sales = r["ledgers"]!!.jsonObject[ledger]!!.jsonObject.arr("sales")
      val byAmount = sales.associate { String.format(java.util.Locale.US, "%.2f", it.dbl("finalTotal")) to it.str("paymentMethod")!! }
      val case = r["cases"]!!.jsonArray.map { it.jsonObject }.first { it.str("ledger") == ledger }
      val table = case["expect"]!!.jsonArray.map { it.jsonObject }.first { it.str("id") == "sales.payment" }
      val rows = table["rows"]!!.jsonArray.map { row -> row.jsonArray.map { it.jsonPrimitive.content } }
      assertEquals(ledger, sales.size, rows.size)
      rows.forEach { assertEquals(ledger, it[0], PayFlow.KIND_LABELS[byAmount.getValue(it[5])]) }
      //  فاکتورِ هم‌شکل در فهرست همان برچسبِ گزارش را می‌گیرد
      sales.forEach { j ->
        val sale = json.decodeFromString<Sale>(j.toString())
        assertEquals(ledger, PayFlow.KIND_LABELS[j.str("paymentMethod")], PayFlow.payLabel(sale))
      }
    }
  }

  /** همان ردیفی که `server/src/lib/order-ledger.js` (#321) روی فاکتورِ W می‌نویسد */
  private val serverW = """{"id":"w-o2","invoiceNumber":2,"series":"W","total":700,"discount":0,"finalTotal":700,
    "paymentMethod":"cod","debtorId":null,"paidAmount":700,"remaining":0,"status":"completed","seller":"",
    "date":"2026-10-19","createdAt":130,"channel":"online","orderId":"o2","orderNo":2,
    "payments":[{"method":"cod","kind":"cash","amount":70000,"ref":null,"status":"pending","verification":null,"custody":"courier"}]}"""

  @Test fun `فاکتورِ W ِ سرور (#321) خوانده می‌شود؛ «نقد هنگامِ تحویل» غیرنقد است و به دخل نمی‌آید`() {
    val w = json.decodeFromString<Sale>(serverW)
    assertEquals(listOf(SalePayment("cod", "cash", 70000, "pending", custody = "courier")), w.payments)
    assertEquals("نقد هنگامِ تحویل", PayFlow.payLabel(w))
    assertEquals(PayFlow.NON_CASH, PayFlow.payGroup(w))
    assertEquals(listOf("نقد هنگامِ تحویل (در انتظار)" to 700.0), PayFlow.receiptLines(w))
    val online = json.decodeFromString<Sale>(serverW.replace("\"cod\"", "\"online\"").replace("\"kind\":\"cash\"", "\"kind\":\"other\"")
      .replace("\"pending\"", "\"confirmed\"").replace("\"verification\":null", "\"verification\":\"provider\"").replace(",\"custody\":\"courier\"", ""))
    assertEquals("پرداختِ آنلاین", PayFlow.payLabel(online))
    assertEquals(PayFlow.NON_CASH, PayFlow.payGroup(online))
    assertEquals(listOf("پرداختِ آنلاین" to 700.0), PayFlow.receiptLines(online))
    //  رفت‌وبرگشت: مبلغ همان `amount` و امانت (`custody`) گم نمی‌شود
    val back = json.parseToJsonElement(json.encodeToString(w)).jsonObject["payments"]!!.jsonArray.single().jsonObject
    assertEquals(70000L, back["amount"]!!.jsonPrimitive.longOrNull)
    assertEquals("courier", back["custody"]!!.jsonPrimitive.content)
    assertFalse(back.containsKey("amountMinor"))
    //  صندوقِ پرچم‌دار (هسته) هم مثلِ CashShift: فاکتور و مرجوعیِ سرورِ W در دخل نیست، بی هشدارِ skipped
    val d = ShopData(
      sales = listOf(Sale(id = "s1", finalTotal = 500.0, createdAt = 110), w, online.copy(id = "w-o1")),
      saleReturns = listOf(
        SaleReturn(id = "w-o2-1-r", saleId = "w-o2", amount = 700.0, createdAt = 140, channel = "online"),
        SaleReturn(id = "r-pos", saleId = "s1", amount = 20.0, createdAt = 141),
      ),
    )
    val open = AuditEntry(id = "sh", type = CashShift.OPEN, createdAt = 100, amount = 1000.0)
    val r = ok(PayFlow.report(d, open, null, 200))
    assertTrue(r.skipped.isEmpty())
    assertEquals(mapOf("cash" to 148000L), r.report.expected)
    assertEquals(1480.0, CashShift.totals(d, 100, 200, 1000.0).expected, 1e-9)
  }

  private fun s12(): Pair<ShopData, AuditEntry> {
    val open = AuditEntry(id = "sh12", type = CashShift.OPEN, createdAt = 1000, amount = 2000.0)
    val d = ShopData(sales = listOf(Sale(id = "a", finalTotal = 15300.0, createdAt = 1100)), auditLog = listOf(open))
    val mv = ok(PayFlow.moveRow(d, open, PayFlow.Move("cash_out", "500", "petty_expense", "خرید شیر", by = "صندوقدار"), 1200, newId))
    return d.copy(auditLog = d.auditLog + mv) to open
  }

  @Test fun `S12 - کسریِ ۱۰ زیرِ آستانه، شمارشِ کور، اختلاف اصلاح نمی‌شود`() {
    val (d, open) = s12()
    assertEquals(16800.0, PayFlow.expectedCash(d, open, 1300), 0.0)
    val counts = mapOf("100000" to 10L, "50000" to 12L, "10000" to 7L, "5000" to 1L, "2000" to 2L)
    assertEquals(1679000L, PayFlow.countTotal(counts))
    val r = ok(PayFlow.closeRow(d, open, PayFlow.Count(counts, blind = true, by = "صندوقدار"), 1300, newId))
    assertEquals(1680000L, r.report.expected!!["cash"])
    assertEquals(1679000L, r.report.counted!!["cash"])
    assertEquals(-1000L, r.report.variance!!["cash"])
    assertEquals(true, r.report.needsReview)
    assertEquals(16790.0, r.row.amount, 0.0)
    assertEquals("sh12", r.row.refId)
    val view = ShiftCore.cashierView(r.report)
    assertNull(view.expected)
    assertNull(view.variance)
  }

  @Test fun `S12b - کسریِ ۱۰۰ بالای آستانه بی دلیل بسته نمی‌شود`() {
    val (d, open) = s12()
    val counts = mapOf("100000" to 10L, "50000" to 12L, "10000" to 7L)
    val p = bad(PayFlow.closeRow(d, open, PayFlow.Count(counts, blind = true), 1300, newId))
    assertEquals("REASON_REQUIRED", p.code)
    assertNull("شمارشِ کور اختلاف را لو نمی‌دهد", p.variance)
    val r = ok(PayFlow.closeRow(d, open, PayFlow.Count(counts, blind = true, reason = "پولِ خرد کم بود"), 1300, newId))
    assertEquals(-10000L, r.report.variance!!["cash"])
    assertEquals("پولِ خرد کم بود", r.row.reason)
  }

  @Test fun `ورود و خروجِ نقد و کشو بی فروش`() {
    val open = AuditEntry(id = "sh", createdAt = 0, amount = 100.0)
    var d = ShopData(auditLog = listOf(open))
    assertEquals("INSUFFICIENT_CASH", bad(PayFlow.moveRow(d, open, PayFlow.Move("cash_out", "200", "owner_withdrawal"), 10, newId)).code)
    assertEquals("NOTE_REQUIRED", bad(PayFlow.moveRow(d, open, PayFlow.Move("cash_out", "50", "other"), 10, newId)).code)
    assertEquals("REASON_REQUIRED", bad(PayFlow.moveRow(d, open, PayFlow.Move("cash_out", "50"), 10, newId)).code)
    assertEquals("INVALID_AMOUNT", bad(PayFlow.moveRow(d, open, PayFlow.Move("cash_in", "0", "deposit"), 10, newId)).code)
    assertEquals("APPROVAL_REQUIRED", bad(PayFlow.moveRow(d, open, PayFlow.Move("no_sale", reason = "پولِ خرد"), 10, newId)).code)
    val ns = ok(PayFlow.moveRow(d, open, PayFlow.Move("no_sale", reason = "پولِ خرد", by = "علی", approvedBy = "مالک"), 10, newId))
    assertEquals(listOf("no_sale", "sh", "مالک"), listOf(ns.type, ns.refId, ns.approvedBy))
    d = d.copy(auditLog = d.auditLog + ns)
    val dep = ok(PayFlow.moveRow(d, open, PayFlow.Move("cash_in", "250", "deposit"), 20, newId))
    d = d.copy(auditLog = d.auditLog + dep)
    val x = ok(PayFlow.report(d, open, null, 30)).report
    assertEquals(25000L, x.moves.cashIn)
    assertEquals(1, x.events.noSale)
    assertEquals(35000L, x.expected!!["cash"])
    d = d.copy(auditLog = d.auditLog + dep.copy(id = "x", refId = "other"))
    assertEquals(35000L, ok(PayFlow.report(d, open, null, 30)).report.expected!!["cash"])
  }

  @Test fun `مصرفی بیش از نقدِ دخل در skipped می‌آید`() {
    val d = ShopData(expenses = listOf(Expense(id = "e", amount = 300.0, createdAt = 5)), sales = listOf(Sale(id = "s", finalTotal = 100.0, createdAt = 6)))
    val r = ok(PayFlow.report(d, AuditEntry(id = "sh", createdAt = 0), null, 10))
    assertEquals(listOf("exp:e" to "INSUFFICIENT_CASH"), r.skipped.map { it.id to it.code })
    assertEquals(10000L, r.report.expected!!["cash"])
  }

  // ── SalesEngine با و بی تسویه ───────────────────────────
  private val shop = ShopData(
    products = listOf(Product(id = "p2", name = "روغن", salePrice = 2450.0, purchasePrice = 2000.0, unit = "عدد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "p2", units = 10.0)),
    debtors = listOf(Debtor(id = "d1", name = "نوشا")),
  )
  private val cart = listOf(SalesEngine.CartLine("p2", 1.0))
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

  private fun settled(d: PayFlow.Draft): Settlement = ok(PayFlow.checkout(d))

  @Test fun `SalesEngine با تسویهٔ S06 همان ردیف‌ها و همان معنای فیلدهای قدیمی را می‌نویسد`() {
    val st = settled(PayFlow.Draft(2450.0, "1000", listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1450"))))
    val r = SalesEngine.record(shop, cart, SalesEngine.Checkout(settlement = st), "2026-10-19", 100, newId) as SalesEngine.Result.Ok
    val s = r.data.sales.single()
    assertEquals("mixed", s.paymentMethod)
    assertEquals(2450.0, s.paidAmount, 0.0)
    assertEquals(0.0, s.debtGiven, 0.0)
    assertEquals(2, s.payments!!.size)
    assertEquals(245000L, s.tenderedMinor)
    assertEquals(0L, s.changeMinor)
    val o = json.parseToJsonElement(json.encodeToString(s)).jsonObject
    assertEquals("pending", o["payments"]!!.jsonArray[1].jsonObject["status"]!!.jsonPrimitive.content)
  }

  @Test fun `SalesEngine با تسویهٔ جزئی بدهی را با همان منطقِ قرض‌داران می‌سازد؛ بی مشتری رد`() {
    val st = settled(PayFlow.Draft(2450.0, "450", customerId = "d1"))
    val r = SalesEngine.record(shop, cart, SalesEngine.Checkout(settlement = st, debtorId = "d1"), "2026-10-19", 100, newId) as SalesEngine.Result.Ok
    val s = r.data.sales.single()
    assertEquals("credit", s.paymentMethod)
    assertEquals(2000.0, s.debtGiven, 0.0)
    assertEquals(450.0, s.paidAmount, 0.0)
    assertEquals(2000.0, r.data.transactions.single { it.type == "give" }.amount, 0.0)
    //  تسویه‌ای که با مبلغِ فاکتور نمی‌خواند ثبت نمی‌شود
    val wrong = settled(PayFlow.Draft(999.0, "999"))
    assertTrue(SalesEngine.record(shop, cart, SalesEngine.Checkout(settlement = wrong), "2026-10-19", 100, newId) is SalesEngine.Result.Failed)
  }

  @Test fun `بی پرچم (بی تسویه) هیچ فیلدِ تازه‌ای نوشته نمی‌شود`() {
    val r = SalesEngine.record(shop, cart, SalesEngine.Checkout(), "2026-10-19", 100, newId) as SalesEngine.Result.Ok
    val s = r.data.sales.single()
    assertNull(s.payments)
    val o = json.parseToJsonElement(json.encodeToString(s)).jsonObject
    for (k in listOf("payments", "tenderedMinor", "changeMinor")) assertFalse(k, o.containsKey(k))
    val a = json.parseToJsonElement(json.encodeToString(AuditEntry(id = "x", type = "sale"))).jsonObject
    for (k in listOf("by", "approvedBy", "reason", "note", "z")) assertFalse(k, a.containsKey(k))
  }

  @Test fun `فاکتورِ ترکیبیِ دستگاهِ دیگر (JSON) خوانده می‌شود و چیزی گم نمی‌شود`() {
    val web = """{"id":"w1","finalTotal":2450,"paymentMethod":"mixed","paidAmount":2450,"remaining":0,"debtGiven":0,
      "payments":[{"method":"cash","kind":"cash","amount":100000,"status":"confirmed"},
      {"method":"hesabpay","kind":"wallet","amount":145000,"status":"pending","ref":"HP-77","verification":"manual"}],
      "tenderedMinor":245000,"changeMinor":0}"""
    val s = json.decodeFromString<Sale>(web)
    assertEquals(listOf(ShiftRowOf("cash", 100000), ShiftRowOf("hesabpay", 145000)), PayFlow.saleRows(s).map { ShiftRowOf(it.method, it.amount) })
    val back = json.parseToJsonElement(json.encodeToString(s)).jsonObject
    val orig = json.parseToJsonElement(web).jsonObject
    assertEquals(orig["payments"], back["payments"])
    assertEquals(orig["tenderedMinor"], back["tenderedMinor"])
    //  نامِ پیشینِ مبلغ در همین شاخه (`amountMinor`) هم خوانده می‌شود و `amount` نوشته می‌شود
    val old = json.decodeFromString<Sale>(web.replace("\"amount\"", "\"amountMinor\""))
    assertEquals(s.payments, old.payments)
    assertEquals(orig["payments"], json.parseToJsonElement(json.encodeToString(old)).jsonObject["payments"])
  }

  @Test fun `ردیف‌های رسید - غیرنقد «ثبتِ دستی» با مرجع، دریافتی و پولِ پس؛ فاکتورِ امروز هیچ`() {
    val f = PayFlow.saleFields(ok(PayFlow.checkout(PayFlow.Draft(2450.0, "1100", listOf(PayFlow.DraftRow("hesabpay", "HP-77", "1450"))))))
    val s = Sale(id = "r", finalTotal = 2450.0, payments = f.payments, tenderedMinor = f.tenderedMinor, changeMinor = f.changeMinor)
    assertEquals(
      listOf("نقد" to 1000.0, "HesabPay (ثبتِ دستی) — HP-77" to 1450.0, "دریافتی" to 2550.0, "پولِ پس" to 100.0),
      PayFlow.receiptLines(s),
    )
    assertTrue(PayFlow.receiptLines(Sale(id = "old", finalTotal = 100.0)).isEmpty())
  }

  private data class ShiftRowOf(val method: String, val amount: Long)
}
