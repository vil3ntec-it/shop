package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderArrivalDto
import ir.vil3ntec.tohid.core.model.OrderArrivalItemDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.orders.OrderFlow
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ۱۴۰۵/۰۸/۲۴ — «سفارشِ دستی» مثلِ سبد (`ManualOrderModel`) و تنها ردیفِ «راه‌اندازیِ سفارشِ آنلاین» (`OrderSetupModel`).
 *   • کالای برگزیده با +/− ِ همان `CartModel` ِ صندوق ⇒ قلمِ سفارش با `productId`، واحدِ موجودی و قیمتِ کالا؛
 *   • قلمِ دستیِ بیرون از فهرست ⇒ بدنهٔ `POST /shop/orders` بایت‌به‌بایت همان فرمِ پیشین (بی `productId`/`unit`)؛
 *   • اعتبار: دست‌کم یک قلم، نامِ مشتری لازم؛ جمع همان قاعدهٔ سرور.
 */
class ManualOrderModelTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "بوجی", salePrice = 120.0, purchasePrice = 90.0)
  private val tea = Product(id = "p2", name = "چای سبز", unit = "پاکت", salePrice = 125.5)
  private val d = ShopData(
    products = listOf(rice, tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0), WarehouseEntry(id = "w2", productId = "p2", units = 2.0)),
  )

  private fun lines(json: kotlinx.serialization.json.JsonObject) = json["lines"]!!.jsonArray.map { it.jsonObject }

  @Test
  fun `کالا با گام‌های سبد و قلمِ دستی یک سفارش با شناسهٔ کالا می‌شوند`() {
    var cart = emptyList<SalesEngine.CartLine>()
    repeat(2) { cart = CartModel.addOne(d, cart, "p2").lines ?: cart }
    cart = CartModel.addOne(d, cart, "p1").lines!!
    //  موجودیِ چای ۲ است: سومی «فقط … مانده» و سبد دست نمی‌خورد
    val capped = CartModel.addOne(d, cart, "p2")
    assertTrue(capped.capped)
    cart = CartModel.minusOne(d, cart, "p1")!!.let { CartModel.addOne(d, it, "p1").lines!! }
    val form = OrderDraft.Form(name = "مریم", lines = listOf(OrderDraft.LineInput("کیسه", "1", "10.5"), OrderDraft.LineInput(qty = "1")))
    val merged = ManualOrderModel.merged(form, d, cart)
    assertEquals(
      listOf(
        OrderDraft.LineInput("چای سبز", "2", "125.5", productId = "p2", unit = "پاکت"),
        OrderDraft.LineInput("برنج", "1", "120", productId = "p1", unit = "بوجی"),
        OrderDraft.LineInput("کیسه", "1", "10.5"),
      ),
      merged.lines,
    )
    assertEquals(3, ManualOrderModel.count(form, d, cart))
    //  ۲ × ۱۲۵٫۵ = ۲۵۱ + ۱۲۰ + ۱۰٫۵ ⇒ ۱۱ (گرد به افغانیِ کامل، همان سرور) ⇒ ۳۸۲
    assertEquals(38_200L, ManualOrderModel.totalMinor(merged))

    val body = (OrderDraft.build(merged) as OrderDraft.Result.Ok).body
    val json = lines(OrdersRepository.createJson("app-k", body))
    assertEquals("p2", json[0]["productId"]!!.jsonPrimitive.content)
    assertEquals("پاکت", json[0]["unit"]!!.jsonPrimitive.content)
    assertEquals(2_000L, json[0]["qtyMilli"]!!.jsonPrimitive.long)
    assertEquals(12_550L, json[0]["unitPriceMinor"]!!.jsonPrimitive.long)
    assertEquals("p1", json[1]["productId"]!!.jsonPrimitive.content)
    assertFalse("قلمِ دستی شناسهٔ کالا ندارد", json[2].containsKey("productId") || json[2].containsKey("unit"))
  }

  @Test
  fun `قلمِ دستیِ تنها همان بدنهٔ فرمِ پیشین است`() {
    val old = OrderDraft.Form(
      source = "whatsapp", fulfillment = "delivery", name = "فرید", phone = "0799123412", addressText = "کارتهٔ سه", deliveryFee = "50",
      note = "زنگ بزنید", lines = listOf(OrderDraft.LineInput("برنجِ دانه‌دراز", "2", "1450"), OrderDraft.LineInput("چای", "1.5", "125")),
    )
    val before = OrdersRepository.createJson("app-same", (OrderDraft.build(old) as OrderDraft.Result.Ok).body)
    val now = OrdersRepository.createJson("app-same", (OrderDraft.build(ManualOrderModel.merged(old, d, emptyList())) as OrderDraft.Result.Ok).body)
    assertEquals(before, now)
    assertEquals(
      """{"name":"برنجِ دانه‌دراز","qtyMilli":2000,"unitPriceMinor":145000}""",
      lines(now)[0].toString(),
    )
  }

  @Test
  fun `اعتبار - دست‌کم یک قلم و نامِ مشتری`() {
    val empty = ManualOrderModel.merged(OrderDraft.Form(name = "احمد"), d, emptyList())
    assertTrue(NewOrderModel.errors(empty).fields.containsKey(NewOrderModel.Field.LINES))
    val noName = ManualOrderModel.merged(OrderDraft.Form(), d, listOf(SalesEngine.CartLine("p1", 1.0)))
    val e = NewOrderModel.errors(noName)
    assertTrue(e.fields.containsKey(NewOrderModel.Field.NAME))
    assertFalse(e.fields.containsKey(NewOrderModel.Field.LINES))
    assertTrue(OrderDraft.build(noName) is OrderDraft.Result.Error)
    assertTrue(NewOrderModel.errors(noName.copy(name = "احمد")).isEmpty)
  }

  @Test
  fun `برگهٔ کالا مقدار و فی را عوض می‌کند؛ فیِ همان کالا دستی نیست؛ صفر ردیف را برمی‌دارد`() {
    val cart = listOf(SalesEngine.CartLine("p1", 1.0))
    val edited = ManualOrderModel.applyEdit(d, cart, "p1", OrderDraft.LineInput("برنج", "2.5", "110"))
    assertEquals(listOf(SalesEngine.CartLine("p1", 2.5, unitPrice = 110.0)), edited)
    assertEquals("110", ManualOrderModel.inputOf(d, edited, "p1")!!.price)
    val same = ManualOrderModel.applyEdit(d, edited, "p1", OrderDraft.LineInput("برنج", "3", "120"))
    assertEquals(listOf(SalesEngine.CartLine("p1", 3.0)), same)
    assertEquals(emptyList<SalesEngine.CartLine>(), ManualOrderModel.applyEdit(d, same, "p1", OrderDraft.LineInput("برنج", "0", "120")))
    //  مقدارِ اعشاری (کالای وزنی) از برگه، نه +۱
    assertEquals(listOf(SalesEngine.CartLine("p1", 0.75)), ManualOrderModel.applyEdit(d, emptyList(), "p1", OrderDraft.LineInput("برنج", "0.75", "120")))
    assertEquals("150.05", ManualOrderModel.minorText(15_005))
    assertEquals("150.5", ManualOrderModel.minorText(15_050))
  }

  @Test
  fun `راه‌اندازی - کارِ خودِ دکان فهرست می‌شود؛ پشتیبانی یک ردیف؛ هشدار هیچ`() {
    fun of(vararg b: Pair<String, String>, warn: List<String> = emptyList()) = OrderSetupModel.of(
      OrderArrivalDto(ok = b.isEmpty(), blockers = b.map { OrderArrivalItemDto(it.first, it.second) }, warnings = warn.map { OrderArrivalItemDto(it, "support") }),
    )
    assertEquals(OrderSetupModel.Status.Ready, OrderSetupModel.of(null))
    assertEquals(OrderSetupModel.Status.Ready, of(warn = listOf("alerts_off")))
    assertEquals(
      OrderSetupModel.Status.Todo(listOf(OrderSetupModel.Task("storefront_missing", OrderFlow.Fix.STOREFRONT))),
      of("storefront_missing" to "storefront", warn = listOf("alerts_off")),
    )
    assertEquals(
      OrderSetupModel.Status.Todo(listOf(OrderSetupModel.Task("storefront_hidden", OrderFlow.Fix.STOREFRONT), OrderSetupModel.Task("no_products", OrderFlow.Fix.PRODUCTS))),
      of("storefront_hidden" to "storefront", "no_products" to "products"),
    )
    //  پرچمِ خاموشِ صریح، سرور یا کدِ ناشناخته ⇒ یک ردیفِ «خاموش است ⇐ پشتیبانی»، بر کارهای خودِ دکان جلو
    assertEquals(OrderSetupModel.Status.Off("storefront_off"), of("storefront_off" to "support", "storefront_missing" to "storefront"))
    assertEquals(OrderSetupModel.Status.Off("orders_off"), of("orders_off" to "support"))
    assertEquals(OrderSetupModel.Status.Off("server_not_ready"), of("server_not_ready" to "server"))
    assertEquals(OrderSetupModel.Status.Off("brand_new"), of("brand_new" to "who-knows"))
    assertEquals("ساختنِ ویترین", taskLabel(OrderSetupModel.Task("storefront_missing", OrderFlow.Fix.STOREFRONT)))
  }
}
