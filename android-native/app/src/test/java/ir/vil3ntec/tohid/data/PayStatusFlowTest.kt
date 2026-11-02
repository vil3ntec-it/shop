package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.pay.PayStatus
import ir.vil3ntec.tohid.core.pay.ShiftRow
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v3.V3Convert
import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import ir.vil3ntec.tohid.ui.v3.sales.SalesPaths
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PAY-07 — وضعیتِ پرداختِ ردیف‌های غیرنقدِ فاکتور روی دفترِ دکان (`PayStatusFlow`): سندِ جدای `pay_status` و فاکتورِ
 *  دست‌نخورده، اجازهٔ صندوقدار و مدیر، رفت‌وبرگشتِ همگام‌سازی (v1 ⇐ v3 ⇐ دستگاهِ دیگر، همان شکلِ `doc-roles.js`)،
 *  پولِ دریافتیِ X/Z بی «در انتظار»/«ناموفق»، جمعِ هر وضعیت، بازپرداخت از برگشت، تراشه و فهرستِ «نیازمند بررسی».
 */
class PayStatusFlowTest {

  private val day = "2026-11-02"
  private val open = AuditEntry(id = "o1", type = CashShift.OPEN, date = day, amount = 0.0, createdAt = 1000)
  private val sale = Sale(
    id = "s1", invoiceNumber = 12, finalTotal = 2000.0, createdAt = 1100, date = day, paymentMethod = "mixed",
    payments = listOf(
      SalePayment("cash", "cash", 50_000, "confirmed"),
      SalePayment("afpay_card", "card", 150_000, "pending", "TRM-9", "manual"),
    ),
  )
  private val base = ShopData(sales = listOf(sale), auditLog = listOf(open))

  private fun req(to: String, sell: Boolean = true, manage: Boolean = false, index: Int = 1, by: String = "سارا") =
    PayStatusFlow.Request("s1", index, to, by, sell, manage)

  private fun moved(d: ShopData, key: String, r: PayStatusFlow.Request, now: Long = 1200): ShopData =
    when (val b = PayStatusFlow.build(d, key, r, day, now)) {
      is PayStatusFlow.Built.Ok -> d.copy(auditLog = d.auditLog + b.row)
      is PayStatusFlow.Built.Bad -> error("رد شد: ${b.code}")
    }

  private fun bad(d: ShopData, r: PayStatusFlow.Request, key: String = "k") =
    (PayStatusFlow.build(d, key, r, day, 1300) as PayStatusFlow.Built.Bad).code

  /** پولِ دریافتیِ هر روش در X (رویدادهای فروشِ صندوقِ [open] تا [to]) */
  private fun received(d: ShopData, to: Long = 5000): Map<String, Long> =
    PayFlow.ledgerEvents(d, open, to).filter { it.type == "sale" }.flatMap { it.rows.orEmpty() }
      .groupBy { it.method }.mapValues { (_, v) -> v.sumOf { it.amount } }

  @Test fun `سندِ جدا؛ فاکتور دست نمی‌خورد`() {
    val d = moved(base, "ps1", req("confirmed"))
    assertEquals(base.sales, d.sales)
    val row = d.auditLog.last()
    assertEquals(PayStatus.TYPE, row.type)
    assertEquals("s1", row.refId)
    assertEquals(1500.0, row.amount, 0.0)
    assertEquals(1, row.payRow)
    assertEquals("afpay_card", row.payMethod)
    assertEquals("pending", row.fromStatus)
    assertEquals("confirmed", row.toStatus)
    assertEquals("سارا", row.by)
    assertEquals(listOf("confirmed", "confirmed"), PayStatusFlow.current(d, sale))
  }

  @Test fun `صندوقدار تأیید و بررسی؛ ناموفق و لغو و حلِ بررسی فقط صاحب و مدیر`() {
    assertEquals("PERM_DENIED", bad(base, req("failed")))
    assertEquals("PERM_DENIED", bad(base, req("cancelled")))
    val flagged = moved(base, "ps1", req("needs_review"))
    assertEquals("PERM_DENIED", bad(flagged, req("confirmed")))
    val resolved = moved(flagged, "ps2", req("failed", sell = false, manage = true, by = "مدیر"))
    assertEquals(listOf("confirmed", "failed"), PayStatusFlow.current(resolved, sale))
    //  پایانی برنمی‌گردد؛ نقد دستی نیست؛ حسابدار (نه فروش، نه مدیریت) هیچ
    assertEquals("BAD_TRANSITION", bad(resolved, req("confirmed", manage = true)))
    assertEquals("NOT_MANUAL", bad(base, req("confirmed", index = 0, manage = true)))
    assertEquals("PERM_DENIED", bad(base, req("confirmed", sell = false)))
    //  همان کلید با همان محتوا ⇒ تکراری، نه ردیفِ دوم
    val again = PayStatusFlow.build(flagged, "ps1", req("needs_review"), day, 1300) as PayStatusFlow.Built.Ok
    assertEquals(flagged.auditLog.last().id, again.row.id)
  }

  @Test fun `فاکتورِ لغوشده و اینترنتی وضعیتِ دستی ندارند`() {
    val cancelled = base.copy(sales = listOf(sale.copy(status = "cancelled")))
    assertEquals("SALE_CANCELLED", bad(cancelled, req("confirmed")))
    val online = base.copy(sales = listOf(sale.copy(series = ONLINE_SERIES)))
    assertEquals("SALE_ONLINE", bad(online, req("confirmed")))
    assertTrue(PayStatusFlow.view(online, online.sales.single()).isEmpty())
    assertEquals("SALE_REQUIRED", bad(base.copy(sales = emptyList()), req("confirmed")))
  }

  @Test fun `رفت‌وبرگشتِ همگام‌سازی همان سند و همان وضعیت را به دستگاهِ دیگر می‌رساند`() {
    val d = moved(base, "ps1", req("needs_review"))
    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), LedgerDiff.toJson(d), now = 1)
    val hlc = "001760000000000:000000:dev-x"
    val back = ops.map { op ->
      val r = V3Convert.fromLegacyOp(op, hlc)
      assertTrue("${op.table}: ${r.reason}", r.ok && r.native)
      val insert = r.ops.single()
      if (op.rowId == "ps1") {
        //  همان شکلی که `doc-roles.js` می‌سنجد (`payStatus`): مبلغ به پول، ردیف، روش و گذار
        val f = insert.getValue("fields") as JsonObject
        assertEquals("pay_status", f.getValue("type").jsonPrimitive.content)
        assertEquals("s1", f.getValue("refId").jsonPrimitive.content)
        assertEquals(1, f.getValue("payRow").jsonPrimitive.int)
        assertEquals("afpay_card", f.getValue("payMethod").jsonPrimitive.content)
        assertEquals("pending", f.getValue("fromStatus").jsonPrimitive.content)
        assertEquals("needs_review", f.getValue("toStatus").jsonPrimitive.content)
        assertEquals(150_000L, f.getValue("amountMinor").jsonPrimitive.long)
      }
      V3Convert.effectToLegacy(buildJsonObject {
        put("op_id", insert.getValue("op_id"))
        put("table", insert.getValue("table"))
        put("row_id", insert.getValue("row_id"))
        put("kind", insert.getValue("kind"))
        put("effect", buildJsonObject { put("set", insert.getValue("fields")) })
      })!!
    }
    val got = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(ShopData()), back))
    assertEquals(d.auditLog.last(), got.auditLog.single { it.id == "ps1" })
    assertEquals(d.sales, got.sales)
    assertEquals(listOf("confirmed", "needs_review"), PayStatusFlow.current(got, got.sales.single()))
    assertEquals(1, PayStatusFlow.review(got).size)
  }

  @Test fun `ردیفِ قدیمیِ سابقه همان JSON را می‌سازد`() {
    val plain = AuditEntry(id = "a", type = "sale", refId = "s1", notes = "ثبت فروش", createdAt = 5)
    val json = LedgerDiff.toJson(ShopData(auditLog = listOf(plain))).toString()
    for (k in listOf("payRow", "payMethod", "fromStatus", "toStatus")) assertTrue(k, k !in json)
  }

  @Test fun `X و Z فقط پولِ رسیده را می‌شمارند — در انتظار، ناموفق، لغو و بررسی بیرون`() {
    //  ثبتِ دستیِ «در انتظار»: نقد بله، کارت نه
    assertEquals(mapOf("cash" to 50_000L), received(base))
    val x0 = (PayFlow.report(base, open, null, 5000) as PayFlow.Outcome.Ok).value.report
    assertEquals(0L, x0.methods["afpay_card"]?.sales ?: 0L)
    //  «پول رسید» ⇒ همان لحظه در X
    val ok = moved(base, "ps1", req("confirmed"))
    assertEquals(mapOf("cash" to 50_000L, "afpay_card" to 150_000L), received(ok))
    //  مرزِ Z: تأییدی که پس از بستن آمد در همان پنجره نیست
    assertEquals(mapOf("cash" to 50_000L), received(ok, to = 1150))
    for (to in listOf("needs_review", "failed", "cancelled")) {
      val d = moved(base, "ps-$to", req(to, manage = true))
      assertEquals(to, mapOf("cash" to 50_000L), received(d))
    }
    //  نقدِ دخل (صندوقِ ساده) هیچ‌وقت به وضعیتِ کارت بند نیست
    assertEquals(500.0, CashShift.totals(base, 1000, 5000, 0.0, "o1").expected, 0.0)
    //  فاکتورِ بی `payments` (صندوقِ امروز) همان حسابِ پیشین
    val legacy = ShopData(sales = listOf(Sale(id = "L", finalTotal = 300.0, createdAt = 1100)), auditLog = listOf(open))
    assertEquals(mapOf("cash" to 30_000L), received(legacy))
  }

  @Test fun `اصلاحِ روش و وضعیت با هم — کارتِ در انتظاری که نقد شد نقد شمرده می‌شود`() {
    val fix = AuditEntry(
      id = "pf1", type = "pay_fix", date = day, refId = "s1", amount = 1500.0, createdAt = 1150,
      by = "مدیر", approvedBy = "مدیر", reason = "در واقع نقد داد", fromMethod = "afpay_card", toMethod = "cash",
    )
    val d = base.copy(auditLog = base.auditLog + fix)
    assertEquals(mapOf("cash" to 200_000L), received(d))
    assertEquals(listOf(ShiftRow("cash", 200_000)), PayStatusFlow.received(listOf(ShiftRow("cash", 200_000)), mapOf("afpay_card" to 150_000L)))
  }

  @Test fun `جمعِ هر وضعیت و یادداشتِ X`() {
    val other = Sale(
      id = "s2", invoiceNumber = 13, finalTotal = 300.0, createdAt = 1150, date = day,
      payments = listOf(SalePayment("hawala", "transfer", 30_000, "pending", "H-1", "manual")),
    )
    val d = moved(base.copy(sales = listOf(sale, other)), "ps1", req("needs_review"))
    assertEquals(mapOf("needs_review" to 150_000L, "pending" to 30_000L), PayStatusFlow.totals(d, 1000, 5000))
    assertEquals(180_000L, ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerNotes.unreceived(d, 1000, 5000))
    assertTrue(ir.vil3ntec.tohid.ui.v3.sales.drawer.DrawerNotes.unreceivedNotes(0).isEmpty())
    //  لغوشده و بیرون از پنجره شمرده نمی‌شوند
    assertEquals(emptyMap<String, Long>(), PayStatusFlow.totals(d, 2000, 5000))
  }

  @Test fun `بازپرداخت از راهِ برگشت کامل یا جزئی است و ردیفِ ناموفق به کارت پس داده نمی‌شود`() {
    val ok = moved(base, "ps1", req("confirmed"))
    val part = ok.copy(saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 500.0, createdAt = 1300, toMethods = mapOf("afpay_card" to 500.0))))
    assertEquals(listOf("confirmed", "partially_refunded"), PayStatusFlow.current(part, sale))
    val full = ok.copy(saleReturns = listOf(SaleReturn(id = "r1", saleId = "s1", amount = 1500.0, createdAt = 1300, toMethods = mapOf("afpay_card" to 1500.0))))
    assertEquals(listOf("confirmed", "refunded"), PayStatusFlow.current(full, sale))
    //  روشِ اصلی: کارتِ «در انتظار» مثلِ امروز پس‌دادنی است؛ «ناموفق» نه — پس فقط نقد می‌ماند
    val pending = ReturnFlow.split(sale, 2000.0, ReturnFlow.ORIGINAL, "مدیر", null) as ReturnFlow.Outcome.Ok
    assertEquals(1500.0, pending.value.methods["afpay_card"] ?: 0.0, 0.0)
    val failed = moved(base, "ps1", req("failed", manage = true))
    val changes = PayStatusFlow.bySale(failed)["s1"].orEmpty()
    assertTrue(ReturnFlow.split(sale, 2000.0, ReturnFlow.ORIGINAL, "مدیر", null, changes = changes) is ReturnFlow.Outcome.Bad)
    val cashOnly = ReturnFlow.split(sale, 500.0, ReturnFlow.ORIGINAL, "مدیر", null, changes = changes) as ReturnFlow.Outcome.Ok
    assertEquals(500.0, cashOnly.value.cash, 0.0)
    assertNull(cashOnly.value.methods["afpay_card"])
  }

  @Test fun `تراشهٔ فهرست، بخشِ فاکتور و فهرستِ نیازمند بررسی`() {
    assertEquals("pending", PayStatusFlow.chip(base, sale))
    val ok = moved(base, "ps1", req("confirmed"))
    assertNull(PayStatusFlow.chip(ok, sale))
    assertNull(PayStatusFlow.chip(base, Sale(id = "c", finalTotal = 10.0)))
    val view = PayStatusFlow.view(ok, sale).single()
    assertEquals(PayStatusFlow.RowView(1, "afpay_card", 150_000, "confirmed", manual = true), view)
    //  حساب‌پیِ خودکار (درگاه): دیده می‌شود، دستی نه
    val gw = sale.copy(id = "g", payments = listOf(SalePayment("hesabpay", "wallet", 10_000, "confirmed", "HP", "gateway")))
    assertEquals(false, PayStatusFlow.view(base, gw).single().manual)
    val flagged = moved(base, "ps1", req("needs_review"))
    assertEquals("needs_review", PayStatusFlow.chip(flagged, sale))
    assertEquals(listOf("s1" to 1), PayStatusFlow.review(flagged).map { it.sale.id to it.row.index })
    assertTrue(PayStatusFlow.review(ok).isEmpty())
  }

  @Test fun `مسیرهای وضعیت و فهرستِ بررسی ثبت‌اند و فاکتور را نمی‌پوشانند`() {
    val t = V3Modules.table
    assertEquals(SalesPaths.PAY_REVIEW, t.resolve(SalesPaths.PAY_REVIEW)!!.route.pattern)
    assertEquals("sales/{saleId}/pay/{i}", t.resolve(SalesPaths.payStatus("s1", 1))!!.route.pattern)
    assertEquals("sales/{saleId}", t.resolve(SalesPaths.invoice("s1"))!!.route.pattern)
  }
}
