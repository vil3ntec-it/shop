package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سفارشِ خرید ⇐ فاکتورِ خریدِ دفتر (PUR-02/04/14، S19): انبار و بدهی همان راهِ دفتر، ثبتِ دوباره
 *  بی اثر، دو دستگاه همان شناسه‌ها، و پاسخِ سرور با همان نام‌های فیلد.
 */
class PurchaseOrderBookTest {
  private var seq = 0
  private fun newId() = "x${seq++}"

  private val d0 = ShopData(
    products = listOf(
      Product(id = "pA", name = "کالای A", unit = "عدد", purchasePrice = 100.0, minStock = 5.0),
      Product(id = "pB", name = "کالای B", unit = "عدد", purchasePrice = 200.0),
      Product(id = "pC", name = "کالای C", unit = "عدد", purchasePrice = 50.0),
      Product(id = "pD", name = "کالای D", unit = "کیلو", purchasePrice = 33.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "شرکتِ نمونه"), Supplier(id = "s2", name = "دیگری")),
  )

  /** S19: سه قلم هر کدام ۱۰؛ رسیدِ اول A ۱۰، B ۷، C ۱۰ که ۲ خراب */
  private val po = PurchaseOrderDto(
    id = "po1", no = 7, supplierId = "s1", supplierName = "شرکتِ نمونه", state = "sent", status = "partial",
    lines = listOf(
      PoLineDto("l1", "pA", "کالای A", "عدد", 10_000, 10_000),
      PoLineDto("l2", "pB", "کالای B", "عدد", 10_000, 20_000),
      PoLineDto("l3", "pC", "کالای C", "عدد", 10_000, 5_000),
    ),
    receipts = listOf(
      PoReceiptDto(
        id = "01JS0000000000000000000R01", poId = "po1",
        lines = listOf(PoReceiptLineDto("l1", 10_000, 0, 10_000), PoReceiptLineDto("l2", 7_000, 0, 20_000), PoReceiptLineDto("l3", 10_000, 2_000, 5_000)),
        posted = listOf(
          PoPostedDto("l1", 10_000, 10_000, 0, 10_000, 0, 0, 10_000, 100_000),
          PoPostedDto("l2", 10_000, 7_000, 0, 7_000, 3_000, 0, 20_000, 140_000),
          PoPostedDto("l3", 10_000, 10_000, 2_000, 8_000, 0, 0, 5_000, 40_000),
        ),
        totalMinor = 280_000,
      ),
    ),
  )
  private val r1 get() = po.receipts.single()

  private fun posted(d: ShopData = d0): ShopData =
    (PurchaseOrderBook.post(d, po, r1, "2026-11-01", 1_000) as PurchaseOrderBook.Posting.Ok).data

  @Test fun `S19 — رسید یک فاکتورِ خریدِ سه‌ردیفه می‌سازد: موجودی +۱۰/+۷/+۸ و بدهیِ ۲۸۰۰`() {
    val r = PurchaseOrderBook.post(d0, po, r1, "2026-11-01", 1_000) as PurchaseOrderBook.Posting.Ok
    val d = r.data
    assertEquals(1, r.invoiceNo)
    assertEquals(3, r.rows)
    assertEquals(10.0, ShopStore.stock(d, "pA"), 0.0)
    assertEquals(7.0, ShopStore.stock(d, "pB"), 0.0)
    assertEquals(8.0, ShopStore.stock(d, "pC"), 0.0)
    assertEquals(2800.0, ShopStore.supplierDebt(d, "s1"), 0.0)
    assertEquals(0.0, ShopStore.supplierDebt(d, "s2"), 0.0)
    val inv = PurchaseBook.invoices(d).single()
    assertEquals(3, inv.lines.size)
    assertEquals(2800.0, inv.total, 0.0)
    assertEquals(0.0, inv.paid, 0.0)
    assertEquals(
      listOf("l1", "l2", "l3").map { PurchaseOrderBook.purchaseIdOf(r1.id, it) }.toSet(),
      d.purchases.map { it.id }.toSet(),
    )
    assertTrue(PurchaseOrderBook.isPosted(d, r1))
    assertTrue(PurchaseOrderBook.unposted(d, po).isEmpty())
    assertFalse(PurchaseOrderBook.isPosted(d0, r1))
  }

  @Test fun `ثبتِ دوباره بی اثر است و چیزی دو بار نمی‌نشیند`() {
    val d = posted()
    assertEquals(PurchaseOrderBook.Posting.Already, PurchaseOrderBook.post(d, po, r1, "2026-11-02", 2_000))
    assertEquals(3, d.purchases.size)
    assertEquals(3, d.warehouseEntries.size)
  }

  @Test fun `دو دستگاه (یکی با فیِ خریدِ دیگر روی کالا) همان شناسه‌ها را می‌سازند`() {
    val a = posted(d0)
    //  دستگاهِ دوم: فیِ خریدِ کالاها با فیِ رسید یکی است ⇒ تاریخچهٔ قیمت و سابقهٔ «تغییرِ قیمت» نمی‌سازد
    val sameprice = d0.copy(products = d0.products.map { p ->
      when (p.id) { "pA" -> p.copy(purchasePrice = 100.0); "pB" -> p.copy(purchasePrice = 200.0); else -> p }
    })
    val pricier = d0.copy(products = d0.products.map { it.copy(purchasePrice = it.purchasePrice + 1) })
    val b = posted(pricier)
    val c = posted(sameprice)
    for (x in listOf(b, c)) {
      assertEquals(a.purchases.map { it.id }.toSet(), x.purchases.map { it.id }.toSet())
      assertEquals(a.warehouseEntries.map { it.id }.toSet(), x.warehouseEntries.map { it.id }.toSet())
    }
    assertTrue("دستگاهِ دوم تاریخچهٔ قیمت ساخت", b.priceHistory.size > c.priceHistory.size)
    assertTrue(a.purchases.all { it.id.length <= 80 })
  }

  @Test fun `همگام‌سازی بخشی از ردیف‌ها را آورده ⇒ فقط بقیه، با همان شمارهٔ فاکتور`() {
    val full = posted()
    val oneRow = PurchaseOrderBook.purchaseIdOf(r1.id, "l1")
    val synced = d0.copy(
      purchases = full.purchases.filter { it.id == oneRow },
      warehouseEntries = full.warehouseEntries.filter { it.purchaseId == oneRow },
      nextInvoiceNo = d0.nextInvoiceNo,
    )
    val r = PurchaseOrderBook.post(synced, po, r1, "2026-11-01", 1_000) as PurchaseOrderBook.Posting.Ok
    assertEquals(2, r.rows)
    assertEquals(setOf(1), r.data.purchases.map { it.invoiceNo }.toSet())
    assertEquals(3, r.data.purchases.size)
  }

  @Test fun `رسیدِ همه‌خراب چیزی برای دفتر ندارد؛ تأمین‌کنندهٔ ناشناس رد می‌شود`() {
    val allBad = r1.copy(id = "r-bad", posted = listOf(PoPostedDto("l1", 10_000, 3_000, 3_000, 0, 7_000, 0, 10_000, 0)))
    assertEquals(PurchaseOrderBook.Posting.Already, PurchaseOrderBook.post(d0, po, allBad, "2026-11-01", 1))
    assertTrue(PurchaseOrderBook.isPosted(d0, allBad))
    val f = PurchaseOrderBook.post(d0.copy(suppliers = emptyList()), po, r1, "2026-11-01", 1)
    assertTrue(f is PurchaseOrderBook.Posting.Failed)
  }

  @Test fun `تطبیق: پس از ثبت درست؛ بی ثبت «فاکتور در دفتر نیست»؛ گردِ افغانیِ دفتر در مرز`() {
    assertTrue(PurchaseOrderBook.match(posted(), po).matched)
    val before = PurchaseOrderBook.match(d0, po)
    assertFalse(before.matched)
    assertEquals(
      listOf(PurchaseOrderCore.Issue.BILLED_UNDER_RECEIVED),
      before.lines.first().issues,
    )
    //  ۲٫۵ کیلو × ۳۳ = ۸۲٫۵ ⇒ دفتر ۸۳ می‌نویسد؛ اختلافِ ۵۰ یک‌صدم در مرزِ ۱۰۰
    val kg = po.copy(
      lines = listOf(PoLineDto("l1", "pD", "کالای D", "کیلو", 2_500, 3_300)),
      receipts = listOf(PoReceiptDto(id = "rk", lines = listOf(PoReceiptLineDto("l1", 2_500, 0, 3_300)),
        posted = listOf(PoPostedDto("l1", 2_500, 2_500, 0, 2_500, 0, 0, 3_300, 8_250)), totalMinor = 8_250)),
    )
    val d = (PurchaseOrderBook.post(d0, kg, kg.receipts.single(), "2026-11-01", 1) as PurchaseOrderBook.Posting.Ok).data
    assertEquals(83.0, d.purchases.single().totalAmount, 0.0)
    assertTrue(PurchaseOrderBook.match(d, kg).matched)
  }

  @Test fun `پیشنهادِ ردیف: فقط کالاهای خریده از همین تأمین‌کننده، کم‌موجودها بالا، آخرین فیِ او`() {
    var d = d0
    for ((sup, pid, price) in listOf(Triple("s1", "pA", 120.0), Triple("s1", "pB", 210.0), Triple("s2", "pC", 55.0))) {
      d = (PurchaseBook.record(d, sup, listOf(PurchaseBook.Line(pid, 1.0, price)), 0.0, "2026-10-01", "", "2026-10-01", 1, ::newId)
        as PurchaseBook.Result.Ok).data
    }
    val picks = PurchaseOrderBook.supplierPicks(d, "s1")
    assertEquals(listOf("pA", "pB"), picks.map { it.productId })
    assertTrue("A زیرِ حدِ کم (۱ از ۵)", picks.first().low)
    assertEquals(12_000L, picks.first().priceMinor)
    assertEquals(4_000L, picks.first().qtyMilli)
    assertEquals(1_000L, picks[1].qtyMilli)
    assertEquals(listOf("pA"), PurchaseOrderBook.supplierPicks(d, "s1", onlyLow = true).map { it.productId })
    assertEquals(listOf("pC"), PurchaseOrderBook.supplierPicks(d, "s2").map { it.productId })
  }

  @Test fun `پاسخِ سرور با همان نام‌های فیلد خوانده می‌شود`() {
    val json = Json.parseToJsonElement(
      """{"order":{"id":"01JSPO","no":3,"supplierId":"s1","supplierName":"شرکت","state":"sent","status":"partial",
        "allowed":["cancel","receive"],"lines":[{"id":"l1","productId":"pA","name":"A","unit":"عدد","qtyMilli":10000,"priceMinor":10000}],
        "progress":[{"lineId":"l1","productId":"pA","orderedMilli":10000,"receivedMilli":10000,"damagedMilli":2000,"acceptedMilli":8000,"remainingMilli":2000,"overMilli":0}],
        "totals":{"orderedMinor":100000,"acceptedMinor":80000,"remainingMinor":20000},"expectedDate":null,"note":"","version":3,
        "createdBy":"u1","createdAt":1,"updatedAt":2,"sentAt":2,"cancelledAt":null,"cancelReason":null,"receiptCount":1,
        "receipts":[{"id":"r1","poId":"01JSPO","lines":[{"lineId":"l1","receivedMilli":10000,"damagedMilli":2000,"priceMinor":10000}],
          "posted":[{"lineId":"l1","expectedMilli":10000,"receivedMilli":10000,"damagedMilli":2000,"acceptedMilli":8000,"shortMilli":0,"overMilli":0,"priceMinor":10000,"totalMinor":80000}],
          "totalMinor":80000,"note":"","createdBy":"u1","createdAt":5}]},
        "receipt":{"id":"r1","poId":"01JSPO","lines":[],"posted":[],"totalMinor":80000,"note":"","createdBy":"u1","createdAt":5},"replayed":false}""",
    ) as JsonObject
    val got = ApiJson.decode<PoReceivedDto>(json)
    val o = got.order
    assertEquals(PurchaseOrderCore.Status.PARTIAL, PurchaseOrderBook.status(o))
    assertTrue(PurchaseOrderBook.can(o, PurchaseOrderCore.Action.RECEIVE))
    assertFalse(PurchaseOrderBook.can(o, PurchaseOrderCore.Action.SEND))
    assertEquals(8_000L, o.receipts.single().posted.single().acceptedMilli)
    assertEquals(2_000L, o.progress.single().remainingMilli)
    assertEquals(3, o.version)
    assertEquals("r1", got.receipt.id)
    //  هستهٔ برنامه همان وضعیتِ سرور را از همان داده می‌سازد
    assertEquals(PurchaseOrderCore.Status.PARTIAL, PurchaseOrderCore.status(PurchaseOrderBook.core(o), PurchaseOrderBook.coreReceipts(o)))
  }
}
