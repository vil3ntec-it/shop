package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.report.ReportCore
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  RPT-09 — «سابقهٔ ضدتقلب»: آنچه برنامه می‌نویسد به همان کاربر در زبانهٔ «ضدتقلب» می‌رسد.
 *
 *  دفتر با موتورهای واقعی ساخته می‌شود (`SalesEngine.record`/`cancel`/`recordReturn`،
 *  `FraudTrail.lineDeleteRow`) و از همان پلِ صفحه (`ReportBridge.ledgerOf`) به هسته می‌رود.
 *  خاموش (`by = null`) ⇒ دفتر بیت‌به‌بیت همان پیشین است.
 */
class FraudTrailTest {
  private val tea = Product(id = "p1", name = "چای", unit = "عدد", purchasePrice = 10.0, salePrice = 15.0)
  private val day = "2026-10-20"
  private var seq = 0
  private fun newId(): String = "t${seq++}"
  private val json = Json { encodeDefaults = true }

  private fun stocked() = ShopData(products = listOf(tea), warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "p1", units = 100.0)))

  private fun ok(r: SalesEngine.Result) = r as SalesEngine.Result.Ok

  private fun sold(d: ShopData, qty: Double, seller: String) =
    ok(SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", qty)), SalesEngine.Checkout(seller = seller), day, 1_000, ::newId))

  @Test
  fun `ردیفِ حذف‌شده همانی است که در سبدِ تازه نیست — ردیفِ آزاد هم`() {
    val rice = SalesEngine.CartLine("p1", 2.0)
    val bag = SalesEngine.CartLine("f1", 1.0, unitPrice = 10.0, label = "کیسه")
    assertEquals(listOf(bag), FraudTrail.removed(listOf(rice, bag), listOf(rice)))
    assertEquals(listOf(rice, bag), FraudTrail.removed(listOf(rice, bag), emptyList()))
    //  کم کردنِ مقدار حذف نیست
    assertEquals(emptyList<SalesEngine.CartLine>(), FraudTrail.removed(listOf(rice), listOf(rice.copy(quantity = 1.0))))
  }

  @Test
  fun `ردیفِ سابقهٔ حذف — مبلغِ ردیف، نامِ کالا، کلیدِ فاکتور و کاربر`() {
    val d = stocked()
    val row = FraudTrail.lineDeleteRow(d, SalesEngine.CartLine("p1", 3.0), "k1", "سارا", day, 5_000, "a1")
    assertEquals("line_delete", row.type)
    assertEquals(45.0, row.amount, 0.0)
    assertEquals("k1", row.refId)
    assertEquals("سارا", row.by)
    assertEquals(day, row.date)
    assertEquals(5_000L, row.createdAt)
    assertTrue(row.notes, row.notes.contains("چای") && row.notes.contains("3"))
    //  ردیفِ آزاد: نامِ خودش و قیمتِ دستیِ خودش؛ ۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳ (همان `lineTotal`)
    val free = FraudTrail.lineDeleteRow(d, SalesEngine.CartLine("f1", 1.5, unitPrice = 115.0, label = "کیسه"), null, null, day, 5_000, "a2")
    assertEquals(173.0, free.amount, 0.0)
    assertTrue(free.notes.contains("کیسه"))
    assertNull(free.refId)
    assertNull(free.by)
  }

  @Test
  fun `روشن — لغو، مرجوعی و حذفِ ردیف به نامِ همان کاربر در ضدتقلب`() {
    val a = sold(stocked(), 4.0, "سارا")                // ۶۰
    val b = sold(a.data, 2.0, "سارا")                    // ۳۰ — لغو می‌شود
    var d = ok(SalesEngine.cancel(b.data, b.saleId, day, 2_000, ::newId, reason = "اشتباه", by = "نادر")).data
    val itemId = d.saleItems.first { it.saleId == a.saleId }.id
    d = ok(SalesEngine.recordReturn(d, a.saleId, mapOf(itemId to 1.0), "خراب", day, 3_000, ::newId, by = "نادر")).data
    d = d.copy(auditLog = d.auditLog + FraudTrail.lineDeleteRow(d, SalesEngine.CartLine("p1", 3.0), "k1", "سارا", day, 4_000, "a-del"))

    assertEquals("نادر", d.auditLog.single { it.type == "cancel_sale" }.by)
    assertEquals("نادر", d.saleReturns.single().by)

    val f = ReportCore.fraud(ReportBridge.ledgerOf(d), day, day)
    val nader = f.rows.single { it.key == "نادر" }
    assertEquals(1, nader.voidCount)
    assertEquals(3_000L, nader.voids)
    assertEquals(1, nader.returnCount)
    assertEquals(1_500L, nader.returns)
    val sara = f.rows.single { it.key == "سارا" }
    assertEquals(1, sara.invoices)
    assertEquals(6_000L, sara.gross)
    assertEquals(1, sara.lineDeleteCount)
    assertEquals(4_500L, sara.lineDeletes)
    //  هیچ رویدادی «نامعلوم» نماند
    assertTrue(f.rows.none { it.key == "" })
  }

  @Test
  fun `خاموش — همان دفترِ پیشین، و ضدتقلب لغو و برگشت را نامعلوم می‌بیند`() {
    val a = sold(stocked(), 4.0, "سارا")
    val b = sold(a.data, 2.0, "سارا")
    val before = ok(SalesEngine.cancel(b.data, b.saleId, day, 2_000, { "x" }, reason = "اشتباه")).data
    val explicitNull = ok(SalesEngine.cancel(b.data, b.saleId, day, 2_000, { "x" }, reason = "اشتباه", by = null)).data
    assertEquals(json.encodeToString(ShopData.serializer(), before), json.encodeToString(ShopData.serializer(), explicitNull))
    val cancelRow = before.auditLog.single { it.type == "cancel_sale" }
    assertNull(cancelRow.by)
    assertFalse("by ِ null نوشته نمی‌شود", json.encodeToString(AuditEntry.serializer(), cancelRow).contains("\"by\""))

    val itemId = before.saleItems.first { it.saleId == a.saleId }.id
    val d = ok(SalesEngine.recordReturn(before, a.saleId, mapOf(itemId to 1.0), "خراب", day, 3_000, ::newId)).data
    assertNull(d.saleReturns.single().by)
    assertFalse(json.encodeToString(SaleReturn.serializer(), d.saleReturns.single()).contains("\"by\""))

    val unknown = ReportCore.fraud(ReportBridge.ledgerOf(d), day, day).rows.single { it.key == "" }
    assertEquals(1, unknown.voidCount)
    assertEquals(1, unknown.returnCount)
    assertEquals(0, unknown.lineDeleteCount)
  }
}
