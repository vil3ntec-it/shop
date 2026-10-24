package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  برگشت و تعویض (RET-01..08، 10، 12، 13؛ S13–S15) — `ReturnFlow`.
 *
 *  بخشِ اول بردارِ دستیِ `core/test-vectors/returns-v1.json` است؛ عددها از روی
 *  قاعدهٔ مشخصات نوشته شده‌اند (S13: ۲۵۰ − ۲۵ = ۲۲۵)، نه از خروجیِ کد. بخشِ دوم:
 *  تعویض روی `SalesEngine.record`، صندوق، لغوِ پس از برگشت، و رفتارِ امروز
 *  (تنظیم خاموش).
 */
class ReturnFlowTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/returns-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/returns-v1.json پیدا نشد")
  }

  private fun ledger(): ShopData = json.decodeFromJsonElement(ShopData.serializer(), v.getValue("ledger"))

  private var seq = 0
  private fun newId(): String = "n${seq++}"
  private val today = "2026-10-19"
  private val now = 1_760_000_000_000L

  // ── خواندنِ JSON ─────────────────────────────────────────
  private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
  private fun JsonObject.num(k: String): Double = (this[k] as? JsonPrimitive)?.doubleOrNull ?: 0.0
  private fun JsonObject.long(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: 0L
  private fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
  private fun JsonObject.obj(k: String): JsonObject = this.getValue(k).jsonObject
  private fun JsonObject.arr(k: String): JsonArray = this[k]?.jsonArray ?: JsonArray(emptyList())

  private fun picks(o: JsonObject?): Map<String, ReturnFlow.Pick> =
    o?.mapValues { (_, p) ->
      val x = p.jsonObject
      ReturnFlow.Pick(x.num("qty"), x.str("disposition") ?: ReturnFlow.RESTOCK)
    } ?: emptyMap()

  private fun free(a: JsonArray): List<ReturnFlow.FreeLine> = a.map {
    val x = it.jsonObject
    ReturnFlow.FreeLine(x.str("productId").orEmpty(), x.num("qty"), x.num("unitPrice"), x.str("disposition") ?: ReturnFlow.RESTOCK)
  }

  private fun request(o: JsonObject): ReturnFlow.Request = ReturnFlow.Request(
    docId = o.str("docId").orEmpty(),
    saleId = o.str("saleId"),
    picks = picks(o["picks"] as? JsonObject),
    free = free(o.arr("free")),
    reason = o.str("reason").orEmpty(),
    mode = o.str("mode") ?: ReturnFlow.ORIGINAL,
    customerId = o.str("customerId"),
    by = o.str("by").orEmpty(),
    role = o.str("role") ?: "owner",
    perm = o.str("perm"),
    limit = o.num("limit"),
    approval = (o["approval"] as? JsonObject)?.let { ReturnFlow.Approval(it.str("by").orEmpty(), it.str("role").orEmpty()) },
  )

  private fun stock(d: ShopData, pid: String) = ShopStore.stock(d, pid)

  /** `{ "hesabpay": 500 }` ⇐ نقشه؛ نبودن = خالی */
  private fun money(o: JsonObject, k: String): Map<String, Double> =
    (o[k] as? JsonObject)?.mapValues { (_, v) -> v.jsonPrimitive.doubleOrNull ?: 0.0 } ?: emptyMap()

  private fun split(o: JsonObject) = ReturnFlow.Split(o.num("account"), o.num("credit"), o.num("cash"), money(o, "methods"))

  // ══════════════════════════ بردارِ مشترک ══════════════════════════

  @Test
  fun `find — QR، شمارهٔ فاکتور با رقمِ فارسی، نام و تلفن`() {
    val d = ledger()
    v.arr("find").forEach {
      val c = it.jsonObject
      val want = c.arr("out").map { x -> x.jsonPrimitive.content }
      assertEquals(c.str("q"), want, ReturnFlow.find(d, c.str("q").orEmpty()).map { s -> s.id })
    }
  }

  @Test
  fun `quote — سهمِ تخفیف، مقدارِ مجاز، اعشار، لغوشده، سقفِ گردکردن`() {
    val d = ledger()
    v.arr("quote").forEach {
      val c = it.jsonObject
      val name = c.str("name")
      when (val r = ReturnFlow.quote(d, c.str("sale").orEmpty(), picks(c["picks"] as? JsonObject))) {
        is ReturnFlow.Outcome.Bad -> assertEquals(name, c.str("error"), r.code)
        is ReturnFlow.Outcome.Ok -> {
          assertNull(name, c.str("error"))
          val out = c.obj("out")
          assertEquals(name, out.num("total"), r.value.total, 0.0)
          val want = out.arr("lines").map { l ->
            val x = l.jsonObject
            listOf(x.str("saleItemId"), x.num("qty"), x.num("amount"), x.str("disposition"))
          }
          assertEquals(name, want, r.value.lines.map { l -> listOf(l.saleItemId, l.qty, l.amount, l.disposition) })
        }
      }
    }
  }

  @Test
  fun `quoteNoReceipt — بی رسید`() {
    val d = ledger()
    v.arr("quoteNoReceipt").forEach {
      val c = it.jsonObject
      when (val r = ReturnFlow.quoteNoReceipt(d, free(c.arr("free")))) {
        is ReturnFlow.Outcome.Bad -> assertEquals(c.str("name"), c.str("error"), r.code)
        is ReturnFlow.Outcome.Ok -> assertEquals(c.str("name"), c.obj("out").num("total"), r.value.total, 0.0)
      }
    }
  }

  @Test
  fun `gate و canApprove — بی رسید، ضایعات، سقفِ نقش`() {
    v.arr("gate").forEach {
      val c = it.jsonObject
      val g = ReturnFlow.gate(c.str("role"), c.str("perm"), c.long("amountMinor"), c.bool("noReceipt"), c.bool("damaged"), c.long("limitMinor"))
      val out = c.obj("out")
      assertEquals(c.toString(), out.str("decision"), g.decision)
      assertEquals(c.toString(), out.arr("reasons").map { x -> x.jsonPrimitive.content }, g.reasons)
      assertEquals(c.toString(), out.str("approver"), g.approver)
    }
    v.arr("canApprove").forEach {
      val c = it.jsonObject
      val g = c.obj("gate")
      val gate = ReturnFlow.Gate(g.str("decision")!!, g.arr("reasons").map { x -> x.jsonPrimitive.content }, g.str("approver")!!)
      assertEquals(c.toString(), c.bool("out"), ReturnFlow.canApprove(gate, c.str("role")))
    }
  }

  @Test
  fun `split — تقسیمِ بازپرداخت با هستهٔ پرداخت`() {
    val d = ledger()
    v.arr("split").forEach {
      val c = it.jsonObject
      val sale = c.str("sale")?.let { id -> d.sales.first { s -> s.id == id } }
      when (val r = ReturnFlow.split(sale, c.num("amount"), c.str("mode").orEmpty(), "owner", c.str("customer"))) {
        is ReturnFlow.Outcome.Bad -> assertEquals(c.str("name"), c.str("error"), r.code)
        is ReturnFlow.Outcome.Ok -> {
          assertEquals(c.str("name"), split(c.obj("out")), r.value)
        }
      }
    }
  }

  @Test
  fun `post — سندِ برگشت، انبار، حساب، سابقه، تکرار و تأیید`() {
    val cases = v.arr("post").map { it.jsonObject }
    val byId = cases.associateBy { it.str("id") }
    for (c in cases) {
      val name = c.str("name")
      var d = ledger()
      c.arr("after").forEach { p ->
        val prior = byId.getValue(p.jsonPrimitive.content)
        d = (ReturnFlow.post(d, request(prior.obj("req")), today, now, ::newId) as ReturnFlow.Outcome.Ok).value.data
      }
      val req = request(c.obj("req"))
      when (val r = ReturnFlow.post(d, req, today, now, ::newId)) {
        is ReturnFlow.Outcome.Bad -> assertEquals(name, c.str("error"), r.code)
        is ReturnFlow.Outcome.Ok -> {
          assertNull("$name: انتظارِ خطا", c.str("error"))
          val out = c.obj("out")
          val after = r.value.data
          assertEquals(name, out.num("total"), r.value.total, 0.0)
          assertEquals(name, split(out.obj("split")), r.value.split)
          assertEquals(name, out.str("audit"), after.auditLog.last().type)

          val rows = after.saleReturns.drop(d.saleReturns.size)
          assertEquals(name, out.arr("rows").map { x ->
            val o = x.jsonObject
            listOf(o.str("saleItemId"), o.str("productId"), o.num("quantity"), o.num("amount"), o.str("disposition"), o.num("toAccount"), o.num("toCredit"), money(o, "toMethods"))
          }, rows.map { listOf(it.saleItemId, it.productId, it.quantity, it.amount, it.disposition, it.toAccount ?: 0.0, it.toCredit ?: 0.0, it.toMethods ?: emptyMap<String, Double>()) })
          //  سهمِ دخل: کیف پول و کارت از نقدِ صندوق کم نمی‌شوند (`CashShift`)
          if (out.containsKey("drawer")) assertEquals(name, out.num("drawer"), rows.sumOf { ReturnFlow.drawerAmount(it) }, 1e-9)
          rows.forEach { assertEquals(req.docId, it.docId) }
          out.str("approvedBy")?.let { a -> rows.forEach { assertEquals(name, a, it.approvedBy) } }

          assertEquals(name, out.arr("movements").map { x ->
            val o = x.jsonObject; listOf(o.str("productId"), o.str("type"), o.num("qty"))
          }, after.stockMovements.drop(d.stockMovements.size).map { listOf(it.productId, it.type, it.qty) })
          assertEquals(name, out.arr("entries").map { x ->
            val o = x.jsonObject; listOf(o.str("productId"), o.num("units"), o.str("adjKind"), o.str("refId"))
          }, after.warehouseEntries.drop(d.warehouseEntries.size).map { listOf(it.productId, it.units, it.adjKind, it.refId) })
          assertEquals(name, out.arr("transactions").map { x ->
            val o = x.jsonObject; listOf(o.str("debtorId"), o.num("amount"), o.str("refId"), o.str("kind"))
          }, after.transactions.drop(d.transactions.size).map { listOf(it.debtorId, it.amount, it.refId, it.kind) })

          val returned = out.obj("returnedQty")
          after.saleItems.forEach { si ->
            val before = d.saleItems.first { it.id == si.id }.returnedQty
            assertEquals("$name ${si.id}", (returned[si.id] as? JsonPrimitive)?.doubleOrNull ?: 0.0, si.returnedQty - before, 1e-9)
          }
          val settled = after.sales.sumOf { it.debtSettled } - d.sales.sumOf { it.debtSettled }
          assertEquals(name, out.num("debtSettled"), settled, 1e-9)
          out.obj("stock").forEach { (pid, want) -> assertEquals("$name موجودیِ $pid", want.jsonPrimitive.doubleOrNull!!, stock(after, pid), 1e-9) }

          // سندِ ثبت‌شده عوض نمی‌شود: جز دو شمارنده، فاکتور و اقلامش همان‌اند
          d.sales.forEach { s -> assertEquals(name, s.copy(debtSettled = 0.0), after.sales.first { it.id == s.id }.copy(debtSettled = 0.0)) }
          d.saleItems.forEach { si -> assertEquals(name, si.copy(returnedQty = 0.0), after.saleItems.first { it.id == si.id }.copy(returnedQty = 0.0)) }

          val doc = ReturnFlow.doc(after, req.docId)!!
          assertEquals(name, r.value.total, doc.total, 0.0)
          assertEquals(name, r.value.split, doc.split)
        }
      }
    }
  }

  // ══════════════════════════ فقط کاتلین ══════════════════════════

  @Test
  fun `S15 تعویض — یک سند، یک ذخیره، دریافتِ ۲۰۰ تفاوت، موجودیِ هر دو کالا`() {
    val d = ledger()
    val req = ReturnFlow.Request(
      docId = "doc-x", saleId = "s30", picks = mapOf("i30" to ReturnFlow.Pick(1.0)),
      reason = "اندازه یا مدلِ نامناسب", mode = ReturnFlow.ORIGINAL, by = "صندوقدار",
    )
    val r = ReturnFlow.exchange(
      d, req, listOf(SalesEngine.CartLine("p42", 1.0)), SalesEngine.Checkout(seller = "صندوقدار"),
      saleKey = "sale-x", today = today, now = now, newId = ::newId,
    )
    assertTrue(r.toString(), r is ReturnFlow.Outcome.Ok)
    val x = (r as ReturnFlow.Outcome.Ok).value
    assertEquals(1000.0, x.returned, 0.0)
    assertEquals(1200.0, x.sold, 0.0)
    assertEquals(200.0, x.due, 0.0)
    assertEquals(5.0, stock(x.data, "p40"), 0.0)   // ۵ ورود − ۱ فروش + ۱ برگشت
    assertEquals(4.0, stock(x.data, "p42"), 0.0)
    val doc = ReturnFlow.doc(x.data, "doc-x")!!
    assertEquals("sale-x", doc.exchange?.id)
    assertEquals(200.0, doc.due!!, 0.0)
    // صندوق: +۱۲۰۰ فروش − ۱۰۰۰ برگشت = ۲۰۰
    val t = CashShift.totals(x.data, now, now, 0.0)
    assertEquals(200.0, t.expected, 0.0)
  }

  @Test
  fun `تعویض بی موجودی — هیچ‌چیز ثبت نمی‌شود`() {
    val d = ledger()
    val req = ReturnFlow.Request(docId = "doc-y", saleId = "s30", picks = mapOf("i30" to ReturnFlow.Pick(1.0)), reason = "دیگر")
    val r = ReturnFlow.exchange(
      d, req, listOf(SalesEngine.CartLine("p42", 9.0)), SalesEngine.Checkout(), "sale-y", today, now, ::newId,
    )
    assertTrue(r is ReturnFlow.Outcome.Bad)
    assertEquals(ReturnFlow.Code.EXCHANGE_FAILED, (r as ReturnFlow.Outcome.Bad).code)
    assertTrue(d.saleReturns.isEmpty())
    // تکرارِ همان کلیدِ فروش رد می‌شود
    val ok = ReturnFlow.exchange(d, req, listOf(SalesEngine.CartLine("p42", 1.0)), SalesEngine.Checkout(), "sale-y", today, now, ::newId)
    val again = ReturnFlow.exchange((ok as ReturnFlow.Outcome.Ok).value.data, req.copy(docId = "doc-z"), listOf(SalesEngine.CartLine("p42", 1.0)), SalesEngine.Checkout(), "sale-y", today, now, ::newId)
    assertEquals(ReturnFlow.Code.ALREADY_POSTED, (again as ReturnFlow.Outcome.Bad).code)
  }

  @Test
  fun `صندوق — نقد کم می‌شود، اعتبار و کسر از بدهی نه`() {
    val d = ledger()
    val cash = (ReturnFlow.post(d, ReturnFlow.Request(docId = "a", saleId = "s13", picks = mapOf("i13b" to ReturnFlow.Pick(1.0, ReturnFlow.DAMAGED)), reason = "خراب یا معیوب", mode = ReturnFlow.CASH), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(-225.0, CashShift.totals(cash.data, now, now, 0.0).expected, 0.0)
    val credit = (ReturnFlow.post(d, ReturnFlow.Request(docId = "b", saleId = "s30", picks = mapOf("i30" to ReturnFlow.Pick(1.0)), reason = "دیگر", mode = ReturnFlow.STORE_CREDIT), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(0.0, CashShift.totals(credit.data, now, now, 0.0).expected, 0.0)
    val debt = (ReturnFlow.post(d, ReturnFlow.Request(docId = "c", saleId = "s20", picks = mapOf("i20" to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(0.0, CashShift.totals(debt.data, now, now, 0.0).expected, 0.0)
    //  پرداختِ ترکیبی: ۵۰۰ به HesabPay پس رفت ⇒ دخل دست نمی‌خورد؛ ۸۰۰ ⇒ فقط ۲۰۰ِ نقد از دخل
    val wallet = (ReturnFlow.post(d, ReturnFlow.Request(docId = "w1", saleId = "s60", picks = mapOf("i60" to ReturnFlow.Pick(2.0)), reason = "دیگر"), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(0.0, CashShift.totals(wallet.data, now, now, 0.0).returns, 0.0)
    val more = (ReturnFlow.post(wallet.data, ReturnFlow.Request(docId = "w2", saleId = "s60", picks = mapOf("i60" to ReturnFlow.Pick(2.0)), reason = "دیگر"), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(400.0, CashShift.totals(more.data, now, now, 0.0).returns, 0.0)
    //  رسید (نمای سند) همان تقسیم را نشان می‌دهد
    assertEquals(mapOf("hesabpay" to 100.0), ReturnFlow.doc(more.data, "w2")!!.split.methods)
  }

  /**
   *  صندوقِ ترکیبی (`PayFlow.ledgerEvents`، پرچمِ payV2) همان نقدِ دخلِ `CashShift` را می‌دهد: ردیفِ
   *  نقدِ برگشت = [ReturnFlow.drawerAmount]؛ سهمِ کیف پول/کارت/حواله ردیفِ همان روش است و از دخل
   *  نیست؛ سهمِ حساب و اعتبار با رسیدِ `customer_return` خنثی می‌شود. عددها دستی‌اند.
   */
  @Test
  fun `صندوقِ ترکیبی — برگشتِ نقد، کیف پول، اعتبار، حساب و ترکیبی همان دخلِ CashShift`() {
    val d = ledger()
    fun post(on: ShopData, doc: String, sale: String, item: String, qty: Double, mode: String = ReturnFlow.ORIGINAL, damaged: Boolean = false) =
      (ReturnFlow.post(on, ReturnFlow.Request(docId = doc, saleId = sale, picks = mapOf(item to ReturnFlow.Pick(qty, if (damaged) ReturnFlow.DAMAGED else ReturnFlow.RESTOCK)), reason = "دیگر", mode = mode), today, now, ::newId) as ReturnFlow.Outcome.Ok).value.data
    //  دخلِ باز با ۱۰٬۰۰۰ افغانی، همان لحظهٔ برگشت؛ فروش‌های بردار پیش از آن‌اند
    val open = AuditEntry(id = "sh", type = CashShift.OPEN, createdAt = now, amount = 10_000.0)
    fun shift(x: ShopData): Map<String, Long> {
      val r = (PayFlow.report(x, open, null, now) as PayFlow.Outcome.Ok).value
      assertTrue(r.skipped.toString(), r.skipped.isEmpty())
      assertEquals("همان CashShift", Math.round(CashShift.totals(x, now, now, 10_000.0).expected * 100), r.report.expected!!["cash"])
      return r.report.expected!!
    }
    assertEquals(1_000_000L - 22_500, shift(post(d, "c", "s13", "i13b", 1.0, ReturnFlow.CASH, damaged = true))["cash"])
    //  ۵۰۰ به HesabPay پس رفت ⇒ دخل دست نمی‌خورد؛ کیف پول ۵۰۰ کم
    val wallet = post(d, "w1", "s60", "i60", 2.0)
    assertEquals(mapOf("cash" to 1_000_000L, "hesabpay" to -50_000L), shift(wallet))
    //  دومی: HesabPay فقط ۱۰۰ِ مانده، ۴۰۰ نقد از دخل
    assertEquals(mapOf("cash" to 1_000_000L - 40_000, "hesabpay" to -60_000L), shift(post(wallet, "w2", "s60", "i60", 2.0)))
    assertEquals(1_000_000L, shift(post(d, "cr", "s30", "i30", 1.0, ReturnFlow.STORE_CREDIT))["cash"])
    assertEquals(1_000_000L, shift(post(d, "ac", "s20", "i20", 1.0))["cash"])
  }

  @Test
  fun `پرداختِ ترکیبی — همان JSONِ #286 خوانده و بی تغییر نوشته می‌شود`() {
    val s60 = ledger().sales.first { it.id == "s60" }
    assertEquals(listOf("cash" to 40000L, "hesabpay" to 60000L), s60.payments!!.map { it.method to it.amountMinor })
    val text = Json.encodeToString(Sale.serializer(), s60)
    assertTrue(text, text.contains("\"amount\":60000") && !text.contains("amountMinor"))
  }

  @Test
  fun `لغوِ فاکتور پس از برگشتِ تازه — بدهی منفی نمی‌شود و موجودی دو بار برنمی‌گردد`() {
    val d = ledger()
    val r = (ReturnFlow.post(d, ReturnFlow.Request(docId = "a", saleId = "s20", picks = mapOf("i20" to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    val c = SalesEngine.cancel(r.data, "s20", today, now, ::newId) as SalesEngine.Result.Ok
    // بدهی ۵۰۰ = ۳۰۰ (برگشت) + ۲۰۰ (لغو)
    val received = c.data.transactions.filter { it.debtorId == "c1" && it.type == "receive" }.sumOf { it.amount }
    assertEquals(500.0, received, 0.0)
    assertEquals(100.0, stock(c.data, "pR"), 0.0)
  }

  // ══════════════════ رفتارِ امروز (تنظیم خاموش) — ثبتِ وضعِ موجود ══════════════════
  //  تنظیم خاموش ⇒ صفحه همان `SalesEngine.recordReturn` را می‌زند. این‌ها همان رفتار را
  //  روی دادهٔ S13 قفل می‌کنند تا هیچ تغییری در این PR بی‌صدا عوضش نکند.

  @Test
  fun `خاموش — S13 با recordReturn ِ امروز — ۲۲۵، به موجودی، بی سند و بی ضایعات`() {
    val d = ledger()
    val r = SalesEngine.recordReturn(d, "s13", mapOf("i13b" to 1.0), "خراب", today, now, ::newId) as SalesEngine.Result.Ok
    val row = r.data.saleReturns.single()
    assertEquals(225.0, row.amount, 0.0)
    assertNull(row.docId); assertNull(row.disposition); assertNull(row.refund)
    assertEquals(listOf("customer_return" to 1.0), r.data.stockMovements.map { it.type to it.qty })
    assertEquals(99.0, stock(r.data, "pB"), 0.0)        // امروز ضایعات ندارد: موجودی بالا می‌رود
    assertEquals(d.warehouseEntries, r.data.warehouseEntries)
    assertEquals(d.transactions, r.data.transactions)    // فروشِ نقدی: هیچ تراکنشی
    assertEquals("return", r.data.auditLog.last().type)
    // بیش از مقدار بی‌صدا بریده می‌شود (رفتارِ امروز؛ جریانِ تازه رد می‌کند)
    val more = SalesEngine.recordReturn(r.data, "s13", mapOf("i13b" to 5.0), "", today, now, ::newId) as SalesEngine.Result.Ok
    assertEquals(1.0, more.data.saleReturns.last().quantity, 0.0)
    // دلیل اختیاری است (رفتارِ امروز؛ جریانِ تازه اجباری می‌کند)
    assertEquals("", more.data.saleReturns.last().reason)
  }

  @Test
  fun `خاموش — نسیه با recordReturn ِ امروز همان کسر از بدهی است که جریانِ تازه به روشِ اصلی می‌کند`() {
    val d = ledger()
    val old = SalesEngine.recordReturn(d, "s20", mapOf("i20" to 1.0), "", today, now, ::newId) as SalesEngine.Result.Ok
    val new = (ReturnFlow.post(d, ReturnFlow.Request(docId = "q", saleId = "s20", picks = mapOf("i20" to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now, ::newId) as ReturnFlow.Outcome.Ok).value
    assertEquals(old.data.saleReturns.single().amount, new.total, 0.0)
    assertEquals(old.data.transactions.last().amount, new.data.transactions.last().amount, 0.0)
    assertEquals(old.data.sales.first { it.id == "s20" }.debtSettled, new.data.sales.first { it.id == "s20" }.debtSettled, 0.0)
    assertEquals(stock(old.data, "pR"), stock(new.data, "pR"), 0.0)
    val fail = SalesEngine.recordReturn(d, "s40", mapOf("i40" to 1.0), "", today, now, ::newId)
    assertTrue(fail is SalesEngine.Result.Failed)
  }

  @Test
  fun `ردیفِ برگشتِ امروز همان JSONِ قبلی را می‌نویسد — فیلدهای تازه null نوشته نمی‌شوند`() {
    val r = SaleReturn(id = "r1", saleId = "s", saleItemId = "i", productId = "p", quantity = 1.0, amount = 225.0, reason = "", date = "2026-10-19", createdAt = 1)
    val text = Json { encodeDefaults = true }.encodeToString(SaleReturn.serializer(), r)
    assertEquals(
      """{"id":"r1","saleId":"s","saleItemId":"i","productId":"p","quantity":1.0,"amount":225.0,"reason":"","date":"2026-10-19","createdAt":1}""",
      text,
    )
  }

  @Test
  fun `ردیفِ دستیِ انبار و حساب بی refId و kind همان JSONِ قبلی است`() {
    val j = Json { encodeDefaults = true }
    assertEquals(
      """{"id":"t1","debtorId":"c1","type":"receive","amount":300.0,"date":"2026-10-19","notes":"","createdAt":1}""",
      j.encodeToString(DebtTransaction.serializer(), DebtTransaction(id = "t1", debtorId = "c1", type = "receive", amount = 300.0, date = "2026-10-19", createdAt = 1)),
    )
    val e = j.encodeToString(WarehouseEntry.serializer(), WarehouseEntry(id = "w1", productId = "p", units = 2.0))
    assertFalse(e, e.contains("refId"))
    //  سندِ برگشت هر دو را می‌نویسد و برنامهٔ تازه همان را می‌خواند
    val t = DebtTransaction(id = "t2", refId = "doc", kind = "customer_return")
    assertEquals(t, json.decodeFromString(DebtTransaction.serializer(), j.encodeToString(DebtTransaction.serializer(), t)))
  }

  /**
   *  فاکتورِ آنلاین (`Sale.channel = online`): پولِ خودِ فاکتور به دخل نیامده (`CashShift.isOnline`)،
   *  ولی مرجوعیِ **دستیِ** همین صندوق پولی است که از همین دخل رفت ⇒ کم می‌شود (تصمیمِ صاحبِ
   *  مخزن در بازبینیِ #340). پس ردیفِ برگشتِ دستگاه `channel` نمی‌گیرد: `SaleReturn.channel = online`
   *  یعنی «بیرون از دخل» و `CashShift` کلِ آن ردیف را نمی‌شمارد. بازپرداخت به درگاه از راهِ
   *  `toMethods` بیرون از دخل است ([ReturnFlow.drawerAmount]). آنلاین بودن از `saleId` پیداست.
   */
  @Test
  fun `برگشتِ فروشِ آنلاین — نقدِ پیشخوان از دخل کم می‌شود، درگاه نه؛ ردیف channel نمی‌گیرد`() {
    val base = ledger()
    fun online(vararg pay: SalePayment) = base.copy(sales = base.sales.map {
      if (it.id == "s13") it.copy(channel = CashShift.ONLINE, payments = pay.toList()) else it
    })
    fun returned(d: ShopData, doc: String): ShopData {
      val r = ReturnFlow.post(d, ReturnFlow.Request(docId = doc, saleId = "s13", picks = mapOf("i13b" to ReturnFlow.Pick(1.0)), reason = "دیگر"), today, now, ::newId)
      return (r as ReturnFlow.Outcome.Ok).value.data
    }
    fun rows(d: ShopData, doc: String) = d.saleReturns.filter { it.docId == doc }
    fun drawer(d: ShopData) = CashShift.totals(d, now, now, 0.0).returns

    //  نقدِ هنگامِ تحویل که پیک آورد (`custody = courier`)؛ مشتری کالا را به پیشخوان پس آورد
    val cod = online(SalePayment(method = "cod", kind = "cash", amountMinor = 90_000, status = "confirmed", custody = "courier"))
    val cash = returned(cod, "on-cash")
    assertEquals(listOf(225.0), rows(cash, "on-cash").map { it.amount })
    assertEquals(listOf<String?>(null), rows(cash, "on-cash").map { it.channel })
    assertEquals(225.0, drawer(cash), 0.0)

    //  پرداختِ درگاه ⇒ به همان روش پس می‌رود، نه از دخل
    val gateway = online(SalePayment(method = "online", kind = "other", amountMinor = 90_000, status = "confirmed", verification = "provider"))
    val back = returned(gateway, "on-gw")
    assertEquals(listOf(mapOf("online" to 225.0)), rows(back, "on-gw").map { it.toMethods })
    assertEquals(listOf<String?>(null), rows(back, "on-gw").map { it.channel })
    assertEquals(0.0, drawer(back), 0.0)
  }

  @Test
  fun `کدِ رسید — شناسهٔ فاکتور، یا شمارهٔ نمایشی اگر بلند بود`() {
    val d = ledger()
    val s13 = d.sales.first { it.id == "s13" }
    assertEquals("TR1:s13", ReturnFlow.receiptCode(s13))
    assertEquals(listOf("s13"), ReturnFlow.find(d, ReturnFlow.receiptCode(s13)).map { it.id })
    val long = s13.copy(id = "x".repeat(60))
    assertEquals("TR1#B1-13", ReturnFlow.receiptCode(long))
    assertFalse(ReturnFlow.label("01JABCDEFGHJKMNPQRSTVWXYZ0").isBlank())
    assertEquals("R-WXYZ0".length + 1, ReturnFlow.label("01JABCDEFGHJKMNPQRSTVWXYZ0").length)
  }

  @Test fun `QR ِ فاکتورِ نسخهٔ ۲ — نشانیِ عمومی همان فروش را پیدا می‌کند`() {
    val d = ledger()
    val token = ir.vil3ntec.tohid.core.receipt.ReceiptTokens.new()
    val target = d.sales.first()
    val withToken = d.copy(sales = d.sales.map { if (it.id == target.id) it.copy(receiptToken = token) else it })
    assertEquals(listOf(target.id), ReturnFlow.find(withToken, "https://api.example.com/r/d2jcj71t/$token").map { it.id })
    assertEquals(emptyList<Sale>(), ReturnFlow.find(withToken, "https://api.example.com/r/d2jcj71t/${ir.vil3ntec.tohid.core.receipt.ReceiptTokens.new()}"))
    //  نشانِ همین دکان ⇒ پیدا؛ نشانِ دکانِ دیگر با همان توکن ⇒ هیچ، و «رسیدِ دکانِ دیگر»
    val url = "https://api.example.com/r/d2jcj71t/$token"
    assertEquals(listOf(target.id), ReturnFlow.find(withToken, url, shopTag = "d2jcj71t").map { it.id })
    assertEquals(emptyList<Sale>(), ReturnFlow.find(withToken, url, shopTag = "zzzzzzzz"))
    assertTrue(ReturnFlow.isForeignReceipt(url, "zzzzzzzz"))
    assertFalse(ReturnFlow.isForeignReceipt(url, "d2jcj71t"))
    assertFalse(ReturnFlow.isForeignReceipt("B1-13", "zzzzzzzz"))
  }
}
