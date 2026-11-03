package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PUR-14 — فاکتورِ جدای تأمین‌کننده (سرور، مهاجرتِ ۲۰۸) طرفِ سومِ تطبیق است: با فاکتورِ فعالِ تأمین‌کننده تطبیق با
 *  همان فاکتور است، نه ردیف‌های خریدِ دفتر؛ باطل‌شده هرگز؛ بی فاکتور همان رفتارِ پیشین (دفتر). فرمِ فاکتور پیش‌پُر از
 *  پذیرفتهٔ رسیدها و پاسخِ سرور با همان نام‌های فیلد.
 */
class PurchaseBillTest {

  private val d0 = ShopData(
    products = listOf(
      Product(id = "pA", name = "کالای A", unit = "عدد", purchasePrice = 100.0),
      Product(id = "pB", name = "کالای B", unit = "عدد", purchasePrice = 200.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "شرکتِ نمونه")),
  )

  /** دو قلم هر کدام ۱۰؛ رسید: A ۱۰ و B ۷ (پذیرفته) */
  private val po = PurchaseOrderDto(
    id = "po1", no = 4, supplierId = "s1", supplierName = "شرکتِ نمونه", state = "sent", status = "partial",
    lines = listOf(PoLineDto("l1", "pA", "کالای A", "عدد", 10_000, 10_000), PoLineDto("l2", "pB", "کالای B", "عدد", 10_000, 20_000)),
    progress = listOf(
      PoProgressDto("l1", "pA", 10_000, 10_000, 0, 10_000, 0, 0),
      PoProgressDto("l2", "pB", 10_000, 7_000, 0, 7_000, 3_000, 0),
    ),
    receipts = listOf(
      PoReceiptDto(
        id = "01JS0000000000000000000B01", poId = "po1",
        lines = listOf(PoReceiptLineDto("l1", 10_000, 0, 10_000), PoReceiptLineDto("l2", 7_000, 0, 20_000)),
        posted = listOf(
          PoPostedDto("l1", 10_000, 10_000, 0, 10_000, 0, 0, 10_000, 100_000),
          PoPostedDto("l2", 10_000, 7_000, 0, 7_000, 3_000, 0, 20_000, 140_000),
        ),
        totalMinor = 240_000,
      ),
    ),
  )

  private val exact = PoBillDto(
    id = "b1", poId = "po1", billNo = "F-1",
    lines = listOf(PoBillLineDto("l1", "pA", 10_000, 100_000), PoBillLineDto("l2", "pB", 7_000, 140_000)), totalMinor = 240_000,
  )

  private fun inLedger(): ShopData =
    (PurchaseOrderBook.post(d0, po, po.receipts.single(), "2026-11-01", 1) as PurchaseOrderBook.Posting.Ok).data

  @Test fun `بی فاکتورِ تأمین‌کننده همان تطبیقِ پیشین با دفتر است`() {
    assertFalse(PurchaseOrderBook.hasSupplierBills(po))
    assertTrue(PurchaseOrderBook.match(inLedger(), po).matched)
    assertEquals(
      listOf(PurchaseOrderCore.Issue.BILLED_UNDER_RECEIVED),
      PurchaseOrderBook.match(d0, po).lines.first().issues,
    )
  }

  @Test fun `فاکتورِ تأمین‌کننده طرفِ سوم است — دفترِ خالی هم درست؛ فیِ بالاتر و کالای بیرون از سفارش دیده می‌شوند`() {
    val withBill = po.copy(bills = listOf(exact))
    assertTrue(PurchaseOrderBook.hasSupplierBills(withBill))
    assertTrue("دفتر هنوز رسید را ندارد، ولی فاکتورِ تأمین‌کننده با رسید یکی است", PurchaseOrderBook.match(d0, withBill).matched)

    //  B را ۲۱۰ حساب کرده (۱۴۷۰ به جای ۱۴۰۰) و یک کالای بیرون از سفارش آورده
    val dear = exact.copy(lines = listOf(PoBillLineDto("l1", "pA", 10_000, 100_000), PoBillLineDto("l2", "pB", 7_000, 147_000),
      PoBillLineDto(null, "pZ", 1_000, 5_000)))
    val m = PurchaseOrderBook.match(inLedger(), po.copy(bills = listOf(dear)))
    assertFalse("ردیف‌های دفتر (درست) جای فاکتورِ تأمین‌کننده را نمی‌گیرند", m.matched)
    assertEquals(listOf(PurchaseOrderCore.Issue.PRICE_OVER_ORDER), m.lines.single { it.lineId == "l2" }.issues)
    assertEquals(listOf(PurchaseOrderCore.Issue.NOT_ON_ORDER), m.lines.single { it.productId == "pZ" }.issues)
    assertEquals(252_000L, m.billedMinor)
  }

  @Test fun `گردِ یک افغانی در فاکتورِ کاغذی پذیرفته؛ بیشتر نه`() {
    val round = exact.copy(lines = listOf(PoBillLineDto("l1", "pA", 10_000, 100_100), PoBillLineDto("l2", "pB", 7_000, 140_000)))
    assertTrue(PurchaseOrderBook.match(d0, po.copy(bills = listOf(round))).matched)
    val off = exact.copy(lines = listOf(PoBillLineDto("l1", "pA", 10_000, 100_101), PoBillLineDto("l2", "pB", 7_000, 140_000)))
    assertFalse(PurchaseOrderBook.match(d0, po.copy(bills = listOf(off))).matched)
  }

  @Test fun `فاکتورِ باطل‌شده به حساب نمی‌آید — همهٔ فاکتورها باطل ⇒ دوباره دفتر`() {
    val voided = exact.copy(voidedAt = 9, voidReason = "شمارهٔ اشتباه")
    val onlyVoid = po.copy(bills = listOf(voided))
    assertFalse(PurchaseOrderBook.hasSupplierBills(onlyVoid))
    assertTrue(PurchaseOrderBook.supplierBills(onlyVoid).isEmpty())
    assertFalse("بی دفتر و بی فاکتورِ فعال", PurchaseOrderBook.match(d0, onlyVoid).matched)
    //  باطل + فاکتورِ درستِ تازه ⇒ فقط فاکتورِ تازه، یک بار
    val both = po.copy(bills = listOf(voided, exact.copy(id = "b2")))
    assertEquals(2, PurchaseOrderBook.supplierBills(both).size)
    assertTrue(PurchaseOrderBook.match(d0, both).matched)
    assertTrue(PurchaseOrderBook.billLine(voided).endsWith("باطل"))
  }

  @Test fun `فرمِ فاکتور — پیش‌پُر از پذیرفته × فیِ سفارش؛ رقمِ فارسی؛ خالی و صفر کنار؛ نادرست null`() {
    val rows = PurchaseOrderBook.billRows(po)
    assertEquals(listOf("10", "7"), rows.map { it.qty })
    assertEquals(listOf("1000", "1400"), rows.map { it.total })
    assertEquals(
      listOf(PoBillLineDto("l1", "pA", 10_000, 100_000), PoBillLineDto("l2", "pB", 7_000, 140_000)),
      PurchaseOrderBook.billLines(rows),
    )
    val edited = listOf(rows[0].copy(qty = "۲٫۵", total = "۲۵۰"), rows[1].copy(qty = "0"))
    assertEquals(listOf(PoBillLineDto("l1", "pA", 2_500, 25_000)), PurchaseOrderBook.billLines(edited))
    assertEquals(emptyList<PoBillLineDto>(), PurchaseOrderBook.billLines(listOf(rows[0].copy(qty = ""))))
    assertNull(PurchaseOrderBook.billLines(listOf(rows[0].copy(qty = "دو"))))
    assertNull(PurchaseOrderBook.billLines(listOf(rows[0].copy(total = "-5"))))
    //  ردیفِ بی رسید: مقدارِ سفارش
    assertEquals("10", PurchaseOrderBook.billRows(po.copy(progress = emptyList()))[1].qty)
  }

  @Test fun `پاسخِ سرور — فاکتور و شمارش با همان نام‌ها؛ سرورِ پیشین بی bills همان صفر`() {
    val json = Json.parseToJsonElement(
      """{"order":{"id":"po1","no":4,"supplierId":"s1","state":"sent","status":"partial","billCount":1,
        "bills":[{"id":"b1","poId":"po1","billNo":"F-1","billDate":"2026-11-04","lines":[{"lineId":"l1","productId":"pA","qtyMilli":10000,"totalMinor":100000},
          {"lineId":null,"productId":"pZ","qtyMilli":1000,"totalMinor":5000}],"totalMinor":105000,"note":"","createdBy":"u1","createdAt":5,
          "voidedAt":null,"voidedBy":null,"voidReason":null}]},
        "bill":{"id":"b1","poId":"po1","billNo":"F-1","lines":[],"totalMinor":105000,"voidedAt":null},"replayed":false,"changed":false}""",
    ) as JsonObject
    val got = ApiJson.decode<PoBilledDto>(json)
    assertEquals(1, got.order.billCount)
    val b = got.order.bills.single()
    assertTrue(b.active)
    assertEquals("2026-11-04", b.billDate)
    assertNull(b.lines[1].lineId)
    assertEquals("F-1", got.bill.billNo)
    val old = ApiJson.decode<PoOrderDto>(Json.parseToJsonElement("""{"order":{"id":"po1","no":4}}""") as JsonObject)
    assertEquals(0, old.order.billCount)
    assertTrue(old.order.bills.isEmpty())
  }

  @Test fun `گوشی و کامپیوتر — فاکتور و باطل فقط برای مدیر و فقط پس از فرستادن`() {
    assertTrue(PurchaseOrderBook.canBill(po, manage = true))
    //  شاگرد (بی `stock.adjust`) نه فرم دارد نه «باطل»
    assertFalse(PurchaseOrderBook.canBill(po, manage = false))
    assertFalse(PurchaseOrderBook.canVoid(exact, manage = false))
    //  پیش‌نویس هنوز فاکتور نمی‌گیرد (سرور: ۴۰۹ `not_sent`)؛ لغوشده می‌گیرد (فاکتورِ پیشین باطل‌شدنی است)
    assertFalse(PurchaseOrderBook.canBill(po.copy(state = "draft", status = "draft"), manage = true))
    assertTrue(PurchaseOrderBook.canBill(po.copy(state = "cancelled", status = "cancelled"), manage = true))
    //  باطل‌شده دوباره باطل نمی‌شود
    assertTrue(PurchaseOrderBook.canVoid(exact, manage = true))
    assertFalse(PurchaseOrderBook.canVoid(exact.copy(voidedAt = 9, voidReason = "اشتباه"), manage = true))
  }

  @Test fun `فهرستِ گوشی — بی پیش‌نویس؛ رسیده و بی فاکتور بالا، بعد تازه‌ترین`() {
    val draft = po.copy(id = "d", state = "draft", status = "draft", updatedAt = 99)
    val billed = po.copy(id = "b", billCount = 1, totals = PoTotalsDto(acceptedMinor = 240_000), updatedAt = 50)
    val waiting = po.copy(id = "w", billCount = 0, totals = PoTotalsDto(acceptedMinor = 240_000), updatedAt = 10)
    val nothingYet = po.copy(id = "n", status = "sent", billCount = 0, totals = PoTotalsDto(acceptedMinor = 0), updatedAt = 70)
    val cancelled = po.copy(id = "c", state = "cancelled", status = "cancelled", billCount = 1, updatedAt = 5)
    assertEquals(listOf("w", "n", "b", "c"), PurchaseOrderBook.billable(listOf(draft, billed, waiting, nothingYet, cancelled)).map { it.id })
    assertTrue(PurchaseOrderBook.billable(listOf(draft)).isEmpty())
  }
}
