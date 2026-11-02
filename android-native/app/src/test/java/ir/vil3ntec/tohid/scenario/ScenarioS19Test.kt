package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.product.LabelPlan
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Line
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Order
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Receipt
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.ReceiptLine
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.State
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore.Status
import ir.vil3ntec.tohid.data.PoLineDto
import ir.vil3ntec.tohid.data.PoPostedDto
import ir.vil3ntec.tohid.data.PoReceiptDto
import ir.vil3ntec.tohid.data.PoReceiptLineDto
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderBook
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  S19 ⌨ — دریافتِ ناقصِ سفارشِ خرید با کالای خراب (PUR-02، PUR-04، PRD-15، PUR-13). سر تا ته: سفارشِ ۳ قلمی در هسته
 *  (`PurchaseOrderCore`: پیش‌نویس ⇐ فرستاده ⇐ رسیدِ اسکن با خراب) ⇐ همان رسید به شکلِ پاسخِ سرور ⇐ فاکتورِ خریدِ دفتر
 *  (`PurchaseOrderBook.post`) ⇐ برچسب‌های پذیرفته (`LabelPlan`). در برنامه پشتِ پرچمِ خاموشِ `shop.purchase_orders`.
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • کرایهٔ ۲۸۰ افغانی (پرداخت به حمل‌کننده) ثبت و به نسبتِ ارزشِ پذیرفته سرشکن نمی‌شود: نه کادرِ کرایه در رسید هست و نه
 *     بهای تمام‌شدهٔ سربار؛ بهای واحد همان ۱۰۰ / ۲۰۰ / ۵۰ می‌ماند، نه ۱۱۰ / ۲۲۰ / ۵۵ (PRD-15 فقط میانگینِ موزون است).
 *   • دکمهٔ «چاپِ برچسب» ِ رسید (`PurchaseOrders.printLabels`) صفحه است و فقط در CI کامپایل می‌شود؛ این‌جا شمارِ برچسب‌ها
 *     از همان واحدهای پذیرفته با `LabelPlan` سنجیده می‌شود.
 */
class ScenarioS19Test {

  private val d0 = ShopData(
    products = listOf(
      Product(id = "pA", name = "کالای A", unit = "عدد", purchasePrice = 100.0, barcodes = listOf("1001")),
      Product(id = "pB", name = "کالای B", unit = "عدد", purchasePrice = 200.0, barcodes = listOf("1002")),
      Product(id = "pC", name = "کالای C", unit = "عدد", purchasePrice = 50.0, barcodes = listOf("1003")),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "شرکتِ نمونه")),
  )
  private val order = Order(
    supplierId = "s1", state = State.DRAFT,
    lines = listOf(Line("l1", "pA", 10_000, 10_000), Line("l2", "pB", 10_000, 20_000), Line("l3", "pC", 10_000, 5_000)),
  )

  @Test
  fun `S19 — رسیدِ ناقص با ۲ خراب ⇒ موجودی +۱۰ +۷ +۸، B باز، بدهیِ ۲٬۸۰۰ و ۲۵ برچسب`() {
    val sent = order.copy(state = (PurchaseOrderCore.send(order, emptyList()) as PurchaseOrderCore.Outcome.Changed).state)
    //  دریافت با اسکن: A ۱۰، B ۷، C ۱۰ که ۲ تای آن خراب و در همان رسید برگشت
    val receipt = Receipt("01JS1900000000000000000R01", listOf(
      ReceiptLine("l1", 10_000, 0, 10_000), ReceiptLine("l2", 7_000, 0, 20_000), ReceiptLine("l3", 10_000, 2_000, 5_000),
    ))
    val got = PurchaseOrderCore.receive(sent, emptyList(), receipt) as PurchaseOrderCore.ReceiveResult.Ok
    assertEquals(listOf(10_000L, 7_000L, 8_000L), got.lines.map { it.acceptedMilli })
    assertEquals(2_000L, got.lines.single { it.lineId == "l3" }.damagedMilli)
    assertEquals(Status.PARTIAL, got.status)
    val open = PurchaseOrderCore.progress(sent, listOf(got.receipt)).associate { it.lineId to it.remainingMilli }
    assertEquals(3_000L, open["l2"])

    //  همان رسید به شکلِ پاسخِ سرور ⇐ فاکتورِ خریدِ دفتر
    val dto = PurchaseOrderDto(
      id = "po19", no = 19, supplierId = "s1", supplierName = "شرکتِ نمونه", state = "sent", status = "partial",
      lines = order.lines.map { PoLineDto(it.id, it.productId, "", "عدد", it.qtyMilli, it.priceMinor) },
    )
    val rdto = PoReceiptDto(
      id = receipt.id, poId = "po19",
      lines = receipt.lines.map { PoReceiptLineDto(it.lineId, it.receivedMilli, it.damagedMilli, it.priceMinor) },
      posted = got.lines.map {
        PoPostedDto(it.lineId, it.expectedMilli, it.receivedMilli, it.damagedMilli, it.acceptedMilli, it.shortMilli, it.overMilli, it.priceMinor, it.totalMinor)
      },
      totalMinor = got.totalMinor,
    )
    val d = (PurchaseOrderBook.post(d0, dto, rdto, "2026-11-02", 1_000) as PurchaseOrderBook.Posting.Ok).data
    assertEquals(10.0, ShopStore.stock(d, "pA"), 0.0)
    assertEquals(7.0, ShopStore.stock(d, "pB"), 0.0)
    assertEquals(8.0, ShopStore.stock(d, "pC"), 0.0)
    assertEquals(2800.0, ShopStore.supplierDebt(d, "s1"), 0.0)

    //  «چاپِ برچسب»: یک برچسب برای هر واحدِ پذیرفته ⇒ ۲۵
    val plan = LabelPlan.plan(
      rdto.posted.map { p ->
        val line = order.lines.single { it.id == p.lineId }
        LabelPlan.Item(line.productId, ((p.acceptedMilli + 999) / 1000).toInt(), hasCode = true)
      },
      LabelPlan.Kind.BARCODE,
    )
    assertEquals(25, plan.labels.size)
    assertEquals(listOf("pA" to 10, "pB" to 7, "pC" to 8), LabelPlan.runs(plan.labels))
  }
}
