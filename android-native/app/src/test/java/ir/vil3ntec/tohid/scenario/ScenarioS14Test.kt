package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ReturnFlow
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S14 — برگشتِ بی رسید (RET-04، RET-10)، سر تا ته روی همان راهِ صندوق (`ReturnFlow.post`، پشتِ تنظیمِ خاموشِ برگشتِ تازه
 *  `ReturnSettings`) تا زبانهٔ «ضدتقلب» ِ گزارشِ تازه (`ReportBridge.ledgerOf` ⇐ `ReportCore.fraud`، RPT-09):
 *
 *   • صندوقدارِ بی‌مجوز ⇒ پنجرهٔ تأییدِ مدیر (بی تأیید، یا تأییدِ هم‌ردهٔ خودش ⇒ ثبت نمی‌شود)؛
 *   • بالای سقفِ مبلغِ نقش ⇒ رد حتی با تأییدِ مدیر — صاحب لازم است؛
 *   • دلیل اجباری؛
 *   • همه در گزارشِ ضدتقلب: برگشت به نامِ ثبت‌کننده، «با تأیید» برای او و «تأییدها» برای تأییدکننده.
 *
 *  پیش‌تر هر بند جدا سنجیده می‌شد (`ReturnFlowTest`، `ReportCoreVectorsTest`)؛ «ثبت در گزارشِ ضدتقلب» ِ همین برگشت نه.
 */
class ScenarioS14Test {

  private var seq = 0
  private fun newId() = "s14-${seq++}"
  private val today = "2026-11-03"
  private val now = 1_793_700_000_000L

  private val shoe = Product(id = "shoe", name = "کفش", unit = "جفت", salePrice = 300.0)
  private val coat = Product(id = "coat", name = "کرتی", unit = "عدد", salePrice = 1500.0)
  private val d0 = ShopData(
    products = listOf(shoe, coat),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "shoe", units = 5.0), WarehouseEntry(id = "w2", productId = "coat", units = 2.0)),
  )

  /** صندوقدار (نقشِ `cashier` ⇐ ردهٔ شاگرد) با سقفِ ۱٬۰۰۰ افغانی برای برگشتِ این دستگاه */
  private fun request(docId: String, product: Product, reason: String, approval: ReturnFlow.Approval?) = ReturnFlow.Request(
    docId = docId,
    saleId = null,
    free = listOf(ReturnFlow.FreeLine(product.id, 1.0, product.salePrice, ReturnFlow.RESTOCK)),
    reason = reason,
    mode = ReturnFlow.CASH,
    by = "رحیم",
    role = "cashier",
    limit = 1000.0,
    approval = approval,
  )

  private fun bad(d: ShopData, req: ReturnFlow.Request): String {
    val r = ReturnFlow.post(d, req, today, now, ::newId)
    assertTrue(r.toString(), r is ReturnFlow.Outcome.Bad)
    return (r as ReturnFlow.Outcome.Bad).code
  }

  private fun ok(d: ShopData, req: ReturnFlow.Request): ShopData {
    val r = ReturnFlow.post(d, req, today, now, ::newId)
    assertTrue(r.toString(), r is ReturnFlow.Outcome.Ok)
    return (r as ReturnFlow.Outcome.Ok).value.data
  }

  @Test
  fun `S14 — بی رسید — دلیل اجباری، تأییدِ مدیر، بالای سقف فقط صاحب، و همه در زبانهٔ ضدتقلب به نامِ ثبت‌کننده و تأییدکننده`() {
    val manager = ReturnFlow.Approval("مدیر", "manager")
    val owner = ReturnFlow.Approval("صاحب", "owner")

    //  کفشِ ۳۰۰ بی رسید
    val g = ReturnFlow.gate("cashier", null, 30_000, noReceipt = true, damaged = false, limitMinor = 100_000)
    assertEquals("approve", g.decision)
    assertEquals(listOf(ReturnFlow.WHY_NO_RECEIPT), g.reasons)
    assertEquals(ReturnFlow.Code.REASON_REQUIRED, bad(d0, request("k1", shoe, "  ", manager)))
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, bad(d0, request("k1", shoe, "اندازه کوچک بود", null)))
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, bad(d0, request("k1", shoe, "اندازه کوچک بود", ReturnFlow.Approval("کریم", "cashier"))))
    assertTrue("ردِ پیشین چیزی ننوشت", d0.saleReturns.isEmpty())
    val d1 = ok(d0, request("k1", shoe, "اندازه کوچک بود", manager))
    assertEquals(6.0, ShopStore.stock(d1, "shoe"), 0.0)

    //  کرتیِ ۱٬۵۰۰ بی رسید: بالای سقفِ ۱٬۰۰۰ ⇒ تأییدِ مدیر بس نیست
    assertEquals("owner", ReturnFlow.gate("cashier", null, 150_000, noReceipt = true, damaged = false, limitMinor = 100_000).approver)
    assertEquals(ReturnFlow.Code.APPROVAL_REQUIRED, bad(d1, request("k2", coat, "رنگ نخواست", manager)))
    val d2 = ok(d1, request("k2", coat, "رنگ نخواست", owner))

    //  زبانهٔ «ضدتقلب» ِ گزارشِ تازه، همان هسته‌ای که صفحه می‌خواند
    val f = ReportCore.fraud(ReportBridge.ledgerOf(d2), today, today)
    val rows = f.rows.associateBy { it.key }
    val cashier = rows.getValue("رحیم")
    assertEquals(2, cashier.returnCount)
    assertEquals(180_000L, cashier.returns)
    assertEquals(2, cashier.overrideCount)
    assertEquals(1, rows.getValue("مدیر").approvalCount)
    assertEquals(1, rows.getValue("صاحب").approvalCount)
    assertEquals(0, rows.getValue("مدیر").returnCount)
    assertEquals(2, f.totals.returnCount)
    assertEquals(180_000L, f.totals.returns)
  }
}
