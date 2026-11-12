package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.OrderFulfillment
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.TillSale
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.ui.screens.HomeSections
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 *  S96 (§۲۰.۱۲، `20-product-decisions.md`) «دکان بدون PC: ثبت فروش، افزودن کالا، مشتری قرض‌دار، گزارش روز و ثبت سفارش
 *  آنلاین فقط با گوشی» — یک روزِ کاملِ یک دکان روی **همان موتورهایی که صفحه‌های گوشی صدا می‌زنند**، پشتِ سرِ هم روی یک
 *  دفتر (هیچ کامپیوتری در کار نیست):
 *   ۸:۰۰  کالاهای تازه (`NewProductsScreen` ⇐ `WarehouseEngine.addProducts`) و ورودِ انبار (`StockRoutes` ⇐ `addEntry`)
 *  ۱۰:۰۰  فروشِ نقد با دوربینِ گوشی و کاشی (`CartModel.scan`/`addOne`/`addCount` ⇐ `TillSale.record` ِ `CheckoutRoute`)
 *  ۱۱:۰۰  مشتریِ تازه و فروشِ قرض (`DebtorEngine.add` ⇐ `TillSale.record` با `Payment.CREDIT`)
 *  ۱۳:۰۰  سفارشِ آنلاینِ ویترین «مشتری گرفت» ⇐ فروشِ O در دفترِ گوشی (`OrderFulfillment.fulfill` ِ `OrderFlowAndroid`)، یک بار
 *  ۱۶:۰۰  رسیدِ بخشی از قرض‌دار (`DebtorEngine.addTransaction`)
 *  ۲۰:۰۰  گزارشِ روز (گزارش‌ها ← «سود و فروش» = `ReportEngine.sales`، «قرض‌داران» = `ReportEngine.debtors`) و موجودی
 *  خانهٔ گوشی همهٔ این درها را دارد (`HomeSections.tiles`). سمتِ سرورِ سفارش (ویترین ⇐ سرور ⇐ گوشی) با همان قرینهٔ
 *  `fulfillOrder` در `server/test/e2e` سنجیده می‌شود. ⛔ فقط روی گوشیِ واقعی: دوربین، چاپگرِ بلوتوث و کارِ دست با صفحهٔ کوچک.
 */
class ScenarioS96Test {

  private var seq = 0
  private fun newId() = "s96-${seq++}"
  private val today = "2026-11-12"
  private fun at(h: Int, m: Int = 0): Long = LocalDate.parse(today).atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

  private fun ok(r: WarehouseEngine.Result): ShopData {
    assertTrue("$r", r is WarehouseEngine.Result.Ok)
    return (r as WarehouseEngine.Result.Ok).data
  }

  private fun sold(r: SalesEngine.Result): ShopData {
    assertTrue("$r", r is SalesEngine.Result.Ok)
    return (r as SalesEngine.Result.Ok).data
  }

  @Test
  fun `S96 — یک روزِ کامل فقط با گوشی؛ کالا، انبار، فروش، قرض‌دار، سفارشِ آنلاین و گزارشِ روز`() {
    //  خانهٔ گوشی: کالا، انبار، قرض‌داران، گزارش‌ها و (سفارشِ آنلاینِ روشن) سفارش‌ها
    val tiles = HomeSections.tiles(canManage = true, ordersLive = true).map { it.id }
    assertTrue("$tiles", tiles.containsAll(listOf("products", "warehouse", "debtors", "reports", "orders")))

    //  ۸:۰۰ — سه کالای تازه از فرمِ گوشی و ورودِ انبار
    var d = ok(
      WarehouseEngine.addProducts(
        ShopData(),
        listOf(
          WarehouseEngine.ProductDraft(name = "برنج سیلا", unit = "کیلو", purchasePrice = 70.0, salePrice = 90.0, barcode = "6261111000011"),
          WarehouseEngine.ProductDraft(name = "روغن نباتی", unit = "عدد", purchasePrice = 150.0, salePrice = 180.0, barcode = "6261111000028"),
          WarehouseEngine.ProductDraft(name = "چای سبز", unit = "بسته", purchasePrice = 40.0, salePrice = 55.0, barcode = "6261111000035"),
        ),
        at(8), ::newId,
      ),
    )
    val rice = d.products.single { it.name == "برنج سیلا" }
    val oil = d.products.single { it.name == "روغن نباتی" }
    val tea = d.products.single { it.name == "چای سبز" }
    for ((p, n) in listOf(rice to 50.0, oil to 20.0, tea to 30.0)) {
      d = ok(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = p.id, units = n, unit = p.unit, price = p.purchasePrice), today, at(8, 10), ::newId))
    }
    assertEquals(50.0, ShopStore.stock(d, rice.id), 1e-9)

    //  ۱۰:۰۰ — فروشِ نقد: «۵*» و دوربین روی برنج، کاشیِ روغن
    val scanned = CartModel.scan(d, ShopStore.barcodeIndex(d), emptyList(), "6261111000011", multiplier = 5.0)
    assertTrue("$scanned", scanned is CartModel.Scan.Added && !scanned.capped)
    var cart = (scanned as CartModel.Scan.Added).lines
    cart = CartModel.addOne(d, cart, oil.id).lines!!
    d = sold(TillSale.record(d, cart, SalesEngine.Checkout(seller = "صاحب"), null, null, null, today, at(10), ::newId))

    //  ۱۱:۰۰ — مشتریِ تازه و فروشِ قرض: ۴ بستهٔ چای، هیچ نقد
    val added = DebtorEngine.add(d, DebtorEngine.DebtorDraft(name = "حاجی کریم", phone = "0700123456"), at(11), ::newId)
    assertTrue("$added", added is DebtorEngine.Result.Ok)
    d = (added as DebtorEngine.Result.Ok).data
    val karim = added.id
    cart = CartModel.addCount(d, emptyList(), tea.id, 4.0).lines!!
    d = sold(TillSale.record(d, cart, SalesEngine.Checkout(seller = "صاحب", payment = SalesEngine.Payment.CREDIT, debtorId = karim), null, null, null, today, at(11, 5), ::newId))
    assertEquals(220.0, ShopStore.debt(d, karim), 1e-9)

    //  ۱۳:۰۰ — سفارشِ آنلاینِ ویترین (۲ کیلو برنج، تحویل در دکان، نقدِ سرِ پیشخوان) «مشتری گرفت»
    val order = OrderDto(
      id = "01S96ORDER", orderNo = 41, status = "picked_up", fulfillment = "pickup", paymentMethod = "cod", paymentStatus = "unpaid",
      customer = OrderCustomerDto(name = "ناهید", phone = null),
      subtotalMinor = 18_000, discountMinor = 0, deliveryFeeMinor = 0, totalMinor = 18_000,
      lines = listOf(OrderLineDto(lineNo = 1, productId = rice.id, name = rice.name, unit = rice.unit, qtyMilli = 2_000, unitPriceMinor = 9_000, lineTotalMinor = 18_000)),
      events = listOf(OrderEventDto(kind = "status", fromStatus = "ready_for_pickup", toStatus = "picked_up", at = at(13))),
    )
    val fulfilled = OrderFulfillment.fulfill(d, order, today, at(13), seller = "صاحب")
    assertTrue("$fulfilled", fulfilled is OrderFulfillment.Result.Ok)
    d = (fulfilled as OrderFulfillment.Result.Ok).data
    //  گوشی دوباره همان سفارش را می‌بیند (همگام‌سازی، باز کردنِ دوباره) ⇒ هیچ فروشِ دومی
    assertTrue(OrderFulfillment.fulfill(d, order, today, at(13, 30), seller = "صاحب") is OrderFulfillment.Result.Already)

    //  ۱۶:۰۰ — حاجی کریم ۱۰۰ ؋ از قرضش را می‌دهد
    val paid = DebtorEngine.addTransaction(d, karim, DebtorEngine.Kind.RECEIVE, 100.0, today, "", today, at(16), ::newId)
    assertTrue("$paid", paid is DebtorEngine.Result.Ok)
    d = (paid as DebtorEngine.Result.Ok).data

    //  ۲۰:۰۰ — گزارشِ روز: سه فروش (نقد، قرض، سفارشِ آنلاین)
    val day = ReportEngine.sales(d, today, today)
    assertEquals(3, day.count)
    assertEquals(5 * 90.0 + 180.0 + 4 * 55.0 + 2 * 90.0, day.net, 1e-9)
    assertEquals(5 * 70.0 + 150.0 + 4 * 40.0 + 2 * 70.0, day.cogs, 1e-9)
    assertEquals(day.net - day.cogs, day.grossProfit, 1e-9)
    assertEquals(listOf("o-01S96ORDER"), d.sales.filter { it.orderId == "01S96ORDER" }.map { it.id })
    //  قرض‌داران: داده ۲۲۰، گرفته ۱۰۰، مانده ۱۲۰
    val k = ReportEngine.debtors(d).single { it.debtor.id == karim }
    assertEquals(Triple(220.0, 100.0, 120.0), Triple(k.given, k.received, k.remaining))
    //  موجودی پس از روز: برنج ۵۰ − ۵ − ۲، روغن ۲۰ − ۱، چای ۳۰ − ۴
    assertEquals(listOf(43.0, 19.0, 26.0), listOf(rice, oil, tea).map { ShopStore.stock(d, it.id) })
    //  روز در گزارشِ ماه هم همان است (بازهٔ بزرگ‌تر چیزی کم و زیاد نمی‌کند)
    assertEquals(day.net, ReportEngine.sales(d, "2026-11-01", "2026-11-30").net, 1e-9)
  }
}
