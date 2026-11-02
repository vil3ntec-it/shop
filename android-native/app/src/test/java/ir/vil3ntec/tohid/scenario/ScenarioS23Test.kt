package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.DemandForecast
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseOrderDto
import ir.vil3ntec.tohid.data.PurchaseSuggest
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S23 — پیشنهادِ سفارشِ خرید (INV-13، INV-14، AI-07): فروشِ ۹۰ روزِ گذشته (جمعه ۲۴، روزهای دیگر ۱۰)، زمانِ تأمینِ
 *  سنجیدهٔ ۵ روز از سفارشِ پیشینِ همان تأمین‌کننده و موجودیِ ۶۰. سر تا ته روی دفترِ دکان: `PurchaseSuggest.rows`
 *  (⇐ `DemandForecast`) ⇐ «ساختِ پیش‌نویس» (`PurchaseSuggest.draftLines`) ⇐ قاعدهٔ وضعیتِ سفارش (`PurchaseOrderCore`).
 *
 *  همهٔ سطرهای «نتیجه» این‌جا سبز است؛ صفحهٔ «پیشنهادِ سفارش» و ساختِ پیش‌نویس در سرور پشتِ پرچمِ خاموشِ
 *  `shop.purchase_orders`.
 */
class ScenarioS23Test {

  /** دوشنبه */
  private val today = "2026-10-26"
  private val t = CivilDays.jdnOfIso(today)!!
  private val days = (t - 89..t).toList()
  private fun qty(jdn: Int) = if (DemandForecast.isoWeekday(jdn) == 5) 24.0 else 10.0

  private val d = ShopData(
    products = listOf(Product(id = "tea", name = "چای", unit = "عدد", purchasePrice = 100.0, supplierId = "s1")),
    suppliers = listOf(Supplier(id = "s1", name = "پخشِ کابل")),
    sales = days.map { Sale(id = "s$it", date = CivilDays.isoOf(it), status = "completed") },
    saleItems = days.map { SaleItem(id = "i$it", saleId = "s$it", productId = "tea", quantity = qty(it)) },
    //  موجودی = ورودی − فروش‌ها ⇒ ۶۰
    warehouseEntries = listOf(WarehouseEntry(id = "w", productId = "tea", units = 60.0 + days.sumOf { qty(it) })),
  )

  /** سفارشِ پیشینِ همین تأمین‌کننده: فرستاده و ۵ روز بعد نخستین رسید ⇒ زمانِ تأمینِ سنجیده */
  private val past = PurchaseOrderDto(
    id = "po0", no = 1, supplierId = "s1", state = "sent", status = "complete",
    sentAt = 1_780_000_000_000, firstReceiptAt = 1_780_000_000_000 + 5 * 86_400_000L,
  )

  @Test
  fun `S23 — پیشنهاد با مقدار، دلیل و اطمینان؛ «ساختِ سفارش» فقط پیش‌نویس و بی تأیید فرستاده نمی‌شود`() {
    val row = PurchaseSuggest.rows(d, listOf(past), today).single { it.product.id == "tea" }
    val f = row.forecast
    assertTrue(row.due)
    assertTrue(f.qtyMilli > 0)
    assertEquals(ReorderCore.Reason.REORDER_POINT, f.reason)
    assertEquals(ReorderCore.Confidence.HIGH, f.confidence)
    val why = DemandForecast.why(f)
    assertTrue(why, why.startsWith("میانگینِ روزانه ۱۲"))
    assertTrue(why, why.contains("روزِ جمعه ۲ برابر"))
    assertTrue(why, why.contains("۴ روز موجودی مانده"))
    assertTrue(why, why.contains("زمانِ تأمین ۵ روز") && !why.contains("سنجیده نشده"))

    //  «ساختِ سفارشِ خرید» ⇒ فقط ردیف‌های پیش‌نویس، با همان مقدار
    val lines = PurchaseSuggest.draftLines(listOf(row), "s1")
    assertEquals(listOf(f.qtyMilli), lines.map { it.qtyMilli })
    assertNull(PurchaseSuggest.check("s1", lines))
    val draft = PurchaseOrderCore.Order("s1", PurchaseOrderCore.State.DRAFT, lines.map { PurchaseOrderCore.Line(it.id, it.productId, it.qtyMilli, it.priceMinor) })
    assertEquals(PurchaseOrderCore.Status.DRAFT, PurchaseOrderCore.status(draft, emptyList()))
    //  هیچ سفارشی بی تأیید فرستاده نمی‌شود: پیش‌نویس رسید نمی‌گیرد و «فرستادن» کارِ جدای کاربر است
    val r = PurchaseOrderCore.receive(draft, emptyList(), PurchaseOrderCore.Receipt("r", listOf(PurchaseOrderCore.ReceiptLine(lines[0].id, 1_000, 0, 1))))
    assertEquals(PurchaseOrderCore.Code.NOT_SENT, (r as PurchaseOrderCore.ReceiveResult.Failed).failure.code)
    assertTrue(PurchaseOrderCore.Action.SEND in PurchaseOrderCore.allowed(PurchaseOrderCore.Status.DRAFT))
  }
}
