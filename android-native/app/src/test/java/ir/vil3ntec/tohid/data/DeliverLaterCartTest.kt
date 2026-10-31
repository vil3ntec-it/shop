package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderLineDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.core.pos.DeliverLater
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-38 — سبدِ صندوق ⇐ سفارشِ ارسال، و همان سفارش پس از بیرون رفتن ⇐ یک فروش و یک بار کسرِ موجودی.
 *  پیش از این صندوق راهی به بخشِ تحویل نداشت و «سفارشِ دستی» قلم را بی شناسهٔ کالا می‌فرستاد، پس
 *  فروشِ تحویلش قلمِ آزاد بود و موجودی را هرگز کم نمی‌کرد.
 */
class DeliverLaterCartTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private val cola = Product(id = "p2", name = "نوشابه", unit = "دانه", saleUnit = "کارتن", unitFactor = 24.0, salePrice = 30.0)
  private val d = ShopData(
    products = listOf(rice, cola),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 10.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 48.0),
    ),
  )
  private val cart = listOf(
    SalesEngine.CartLine("p1", 2.5),
    SalesEngine.CartLine("p2", 24.0, unitPrice = 28.0),      // قیمتِ دستیِ ردیف
    SalesEngine.CartLine("free-1", 1.0, unitPrice = 10.5, label = "کیسه"),
    SalesEngine.CartLine("gone", 1.0),                        // کالای پاک‌شده
  )
  private val who = DeliverLater.Contact(name = "احمد", phone = "0791234567", addressText = "کارته سه", fee = "50")

  @Test
  fun `قلم‌های سبد با شناسهٔ کالا، واحدِ موجودی و قیمتِ ردیف`() {
    assertEquals(
      listOf(
        DeliverLater.Line("p1", "برنج", "کیلو", 2_500, 12_000),
        DeliverLater.Line("p2", "نوشابه", "دانه", 24_000, 2_800),
        DeliverLater.Line("", "کیسه", "", 1_000, 1_050),
      ),
      DeliverLaterCart.lines(d, cart),
    )
  }

  @Test
  fun `بدنهٔ سفارش شناسهٔ کالا و واحد دارد و سفارشِ دستی همان پیشین می‌ماند`() {
    val body = (DeliverLater.build(DeliverLaterCart.lines(d, cart), who) as DeliverLater.Result.Ok).body
    val json = OrdersRepository.createJson("k-1", body)
    val lines = json["lines"]!!.jsonArray.map { it.jsonObject }
    assertEquals("p1", lines[0]["productId"]!!.jsonPrimitive.content)
    assertEquals("کیلو", lines[0]["unit"]!!.jsonPrimitive.content)
    assertFalse(lines[2].containsKey("productId"))
    assertFalse(lines[2].containsKey("unit"))
    assertEquals("delivery", json["fulfillment"]!!.jsonPrimitive.content)
    assertEquals("manual", json["source"]!!.jsonPrimitive.content)
    assertEquals("cod", json["paymentMethod"]!!.jsonPrimitive.content)
    assertEquals(5_000L, json["deliveryFeeMinor"]!!.jsonPrimitive.long)

    //  فرمِ «سفارشِ دستی» (ORD-21) شناسه و واحد ندارد ⇒ همان کلیدهای پیشین، بی کلیدِ تازه
    val manual = (OrderDraft.build(OrderDraft.Form(name = "علی", lines = listOf(OrderDraft.LineInput("چای", "1", "50")))) as OrderDraft.Result.Ok).body
    val line = OrdersRepository.createJson("k-2", manual)["lines"]!!.jsonArray.single().jsonObject
    assertEquals(setOf("name", "qtyMilli", "unitPriceMinor"), line.keys)
  }

  @Test
  fun `سفارشِ صندوق پس از بیرون رفتن یک فروش است و همان کالاها را کم می‌کند`() {
    val r = DeliverLater.build(DeliverLaterCart.lines(d, cart), who) as DeliverLater.Result.Ok
    //  همان کاری که سرور با بدنه می‌کند: شمارهٔ قلم و جمعِ هر قلم
    val lines = r.body.lines.mapIndexed { i, l ->
      OrderLineDto(lineNo = i + 1, productId = l.productId, name = l.name, unit = l.unit, qtyMilli = l.qtyMilli,
        unitPriceMinor = l.unitPriceMinor, lineTotalMinor = DeliverLater.lineTotalMinor(l.qtyMilli, l.unitPriceMinor))
    }
    val o = OrderDto(
      id = "01POS", orderNo = 31, status = "out_for_delivery", source = "manual", fulfillment = "delivery",
      paymentMethod = "cod", paymentStatus = "unpaid", subtotalMinor = r.subtotalMinor, deliveryFeeMinor = r.body.deliveryFeeMinor,
      totalMinor = r.totalMinor, lines = lines,
      events = listOf(OrderEventDto(kind = "status", fromStatus = "ready", toStatus = "out_for_delivery", at = 9_000L)),
    )
    //  ۲٫۵ × ۱۲۰ = ۳۰۰ + ۲۴ × ۲۸ = ۶۷۲ + ۱۱ + ارسالِ ۵۰ = ۱۰۳۳
    assertEquals(103_300L, r.totalMinor)
    val done = OrderFulfillment.fulfill(d, o, "2026-10-31", 9_000L) as OrderFulfillment.Result.Ok
    val sale = done.data.sales.single()
    assertEquals(1_033.0, sale.total, 0.0)
    assertEquals(7.5, ShopStore.stock(done.data, "p1"), 1e-9)
    assertEquals(24.0, ShopStore.stock(done.data, "p2"), 1e-9)
    //  دوباره ⇒ همان فروش، نه دومی
    assertTrue(OrderFulfillment.fulfill(done.data, o, "2026-10-31", 9_500L) is OrderFulfillment.Result.Already)
  }
}
