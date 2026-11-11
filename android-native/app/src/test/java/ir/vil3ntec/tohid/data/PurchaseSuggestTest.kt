package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.purchase.ReorderCore
import ir.vil3ntec.tohid.core.purchase.SmartDemand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** دفترِ دکان ⇐ ورودیِ `DemandForecast` (INV-13/14): فروشِ لغوشده نه، تأمین‌کننده، «با یک کلید» فقط نیازمندها */
class PurchaseSuggestTest {
  private val today = "2026-10-26"
  private val t = CivilDays.jdnOfIso(today)!!

  private val sales = (0 until 30).map { k -> Sale(id = "s$k", date = CivilDays.isoOf(t - k), status = "completed") } +
    Sale(id = "gone", date = today, status = "cancelled")
  private val items = (0 until 30).map { k -> SaleItem(id = "i$k", saleId = "s$k", productId = "pA", quantity = 2.0) } +
    SaleItem(id = "ig", saleId = "gone", productId = "pA", quantity = 50.0)

  private val d = ShopData(
    products = listOf(
      Product(id = "pA", name = "چای", unit = "عدد", purchasePrice = 100.0, supplierId = "s1"),
      Product(id = "pB", name = "بوره", unit = "عدد", purchasePrice = 40.0, minStock = 5.0),
      Product(id = "pC", name = "کهنه", unit = "عدد", active = false, supplierId = "s1", minStock = 9.0),
      Product(id = "pD", name = "ناشناس", unit = "عدد", supplierId = "zz", minStock = 1.0),
    ),
    suppliers = listOf(Supplier(id = "s1", name = "یک"), Supplier(id = "s2", name = "دو")),
    sales = sales,
    saleItems = items,
    purchases = listOf(Purchase(id = "u1", productId = "pB", supplierId = "s2", quantity = 1.0, purchasePrice = 50.0, date = "2026-01-01")),
    //  موجودی = ورودی − همهٔ فروش‌های لغونشده (۳۰ × ۲ = ۶۰)؛ ورودیِ آغازِ ۶۰ ⇒ موجودیِ pA صفر، همان فرضِ عددهای زیر
    warehouseEntries = listOf(WarehouseEntry(id = "w-open", productId = "pA", units = 60.0)),
  )

  @Test fun `فروش و تأمین‌کننده و ترتیب`() {
    val rows = PurchaseSuggest.rows(d, emptyList(), today)
    assertEquals(listOf("pA", "pB", "pD"), rows.map { it.product.id })
    val a = rows.first()
    assertEquals("s1", a.supplierId)
    //  ۳۰ روز × ۲ (فاکتورِ لغوشدهٔ ۵۰ نه)، زمانِ تأمینِ سنجیده نیست ⇒ ۷ + ۱۴ روز × ۲
    assertEquals(2_000L, a.forecast.dailyMilli)
    assertEquals(42_000L, a.forecast.qtyMilli)
    assertEquals(0, a.forecast.daysLeft)
    assertEquals(ReorderCore.Reason.REORDER_POINT, a.forecast.reason)
    val b = rows[1]
    assertEquals("s2", b.supplierId)
    assertEquals(5_000L, b.priceMinor)
    assertEquals(ReorderCore.Reason.BELOW_MIN, b.forecast.reason)
    assertNull(b.forecast.daysLeft)
    //  تأمین‌کنندهٔ ناشناس و بی خرید ⇒ بی «با یک کلید»
    assertNull(rows[2].supplierId)
  }

  @Test fun `۳-۱۷ — «پیشنهاد با فروش و زمانِ تأمین» ِ فرمِ سفارش همان پیشنهادِ صفحهٔ «پیشنهادِ سفارش» است`() {
    //  کالاهای فرم = آنچه از همان تأمین‌کننده خریده شده (`supplierPicks`) ⇒ pA یک بار از s1
    val bought = d.copy(purchases = d.purchases + Purchase(id = "u2", productId = "pA", supplierId = "s1", quantity = 1.0, purchasePrice = 100.0, date = "2026-01-02"))
    for (smart in listOf(false, true)) {
      val page = PurchaseSuggest.rows(bought, emptyList(), today, smart).associateBy { it.product.id }
      for ((sid, pid) in listOf("s1" to "pA", "s2" to "pB")) {
        val row = page.getValue(pid)
        val plans = PurchaseSuggest.forSupplier(bought, sid, emptyList(), today, smart)
        assertEquals("$sid/$pid smart=$smart", if (row.due) listOf(row.forecast) else emptyList(), plans.map { it.forecast })
        assertEquals("$sid/$pid smart=$smart", if (row.due) listOf(row.forecast.qtyMilli) else emptyList(), plans.map { it.pick.qtyMilli })
      }
    }
    assertTrue(PurchaseSuggest.rows(bought, emptyList(), today).first { it.product.id == "pA" }.due)
    //  «در راه» ِ همان سفارش‌های باز هم مثلِ صفحه کم می‌شود. با خریدِ ۲۰۲۶/۰۱/۰۲ پنجره ۹۰ روز است ⇒ روزانه ۰٫۶۶۷، نقطهٔ سفارش
    //  ۴٫۶۶۵ و پیشنهادِ بی سفارشِ باز ۱۴ ⇒ ۲ در راه هنوز نیازمند (۱۴ − ۲ = ۱۲)، ۱۰ در راه بس است و فرم هم چیزی پیشنهاد نمی‌دهد
    fun open(remaining: Long) = PurchaseOrderDto(
      id = "po1", no = 1, supplierId = "s1", state = "sent", status = "sent",
      lines = listOf(PoLineDto("l1", "pA", "چای", "عدد", 40_000, 10_000)),
      progress = listOf(PoProgressDto(lineId = "l1", productId = "pA", orderedMilli = 40_000, remainingMilli = remaining)),
    )
    val few = PurchaseSuggest.rows(bought, listOf(open(2_000)), today).first { it.product.id == "pA" }
    assertTrue(few.due)
    assertEquals(12_000L, few.forecast.qtyMilli)
    assertEquals(few.forecast, PurchaseSuggest.forSupplier(bought, "s1", listOf(open(2_000)), today).single().forecast)
    val enough = PurchaseSuggest.rows(bought, listOf(open(10_000)), today).first { it.product.id == "pA" }
    assertFalse(enough.due)
    assertTrue(PurchaseSuggest.forSupplier(bought, "s1", listOf(open(10_000)), today).isEmpty())
    assertTrue(PurchaseSuggest.forSupplier(bought, "nobody", emptyList(), today).isEmpty())
  }

  @Test fun `پیش‌نویسِ با یک کلید فقط نیازمندهای همان تأمین‌کننده`() {
    val rows = PurchaseSuggest.rows(d, emptyList(), today)
    val lines = PurchaseSuggest.draftLines(rows, "s1")
    assertEquals(listOf(PoLineDto("l1", "pA", "چای", "عدد", 42_000, 10_000)), lines)
    assertNull(PurchaseSuggest.check("s1", lines))
    assertTrue(PurchaseSuggest.draftLines(rows, "nobody").isEmpty())
  }

  @Test fun `در راهِ سفارشِ باز از مقدار کم می‌شود`() {
    val open = PurchaseOrderDto(
      id = "po1", no = 1, supplierId = "s1", state = "sent", status = "sent",
      lines = listOf(PoLineDto("l1", "pA", "چای", "عدد", 40_000, 10_000)),
      progress = listOf(PoProgressDto(lineId = "l1", productId = "pA", orderedMilli = 40_000, remainingMilli = 10_000)),
    )
    val a = PurchaseSuggest.rows(d, listOf(open), today).first { it.product.id == "pA" }
    assertEquals(10_000L, a.forecast.onOrderMilli)
    //  نقطهٔ سفارش ۱۴ (۷ روز × ۲)؛ جایگاه ۱۰ ⇒ ۴۲ − ۱۰ در راه
    assertEquals(32_000L, a.forecast.qtyMilli)
  }

  @Test fun `پیش‌بینیِ تقاضای خاموش همان پیشنهاد است و روشن روش و پیش‌بینیِ هر روز دارد`() {
    //  AI-07: تنظیمِ خاموش ⇒ ردیف به ردیف همان، بی `smart`
    val off = PurchaseSuggest.rows(d, emptyList(), today)
    assertEquals(off, PurchaseSuggest.rows(d, emptyList(), today, smart = false))
    assertTrue(off.all { it.forecast.smart == null })
    val a = PurchaseSuggest.rows(d, emptyList(), today, smart = true).first { it.product.id == "pA" }
    val s = a.forecast.smart!!
    //  ۳۰ روز هر روز ۲ ⇒ میانگینِ متحرکِ ۲۸ روز همان ۲، هر روزِ آینده ۲ و همان مقدارِ ۴۲
    assertEquals(SmartDemand.Method.MOVING_AVERAGE, s.method)
    assertEquals(2_000L, a.forecast.dailyMilli)
    assertEquals(List(SmartDemand.FORECAST_DAYS) { 2_000L }, s.perDayMilli)
    assertEquals(42_000L, a.forecast.qtyMilli)
    assertEquals(0, s.cvPermille)
  }
}
