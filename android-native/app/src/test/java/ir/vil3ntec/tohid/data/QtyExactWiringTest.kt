package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRD-08 — کالای وزنی (۳ رقمِ اعشار) در موتورِ صندوقِ گوشی و کامپیوتر (`QtyExactWiring`، پرچمِ `shop.qty_exact`):
 *   • خاموش ⇒ `SalesEngine.lineTotal`، جمعِ سبد و ثبت بیت‌به‌بیت همان امروز (ستونِ legacy ِ بردارِ مشترک)؛
 *   • روشن ⇒ مبلغِ ردیفِ اعشاری همان `onMinor` ِ `core/test-vectors/qty-exact-v1.json` (= سفارشِ آنلاینِ سرور)، مقدارِ درست
 *     همان امروز، و مقدارِ سبد/قلم/حرکتِ انبار به هزارمِ دقیق؛ S04 (۱٫۲۵۰ و ۰٫۷۵) سرتاسری از سبد تا فاکتور.
 */
class QtyExactWiringTest {

  @After fun reset() { QtyExactWiring.serverFlag = false }

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/qty-exact-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/qty-exact-v1.json پیدا نشد")
  }

  private val meat = Product(id = "m", name = "گوشت", unit = "کیلو", purchasePrice = 80.0, salePrice = 100.0)
  private val rice = Product(id = "r", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val egg = Product(id = "e", name = "تخم", unit = "عدد", purchasePrice = 0.2, salePrice = 0.29)
  private val d = ShopData(
    products = listOf(meat, rice, egg),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "m", units = 10.0),
      WarehouseEntry(id = "w2", productId = "r", units = 0.1 + 0.2),
      WarehouseEntry(id = "w3", productId = "e", units = 100.0),
    ),
  )
  private var seq = 0
  private fun record(cart: List<SalesEngine.CartLine>) =
    SalesEngine.record(d, cart, SalesEngine.Checkout(), today = "2026-11-10", now = 1_700_000_000_000, newId = { "q${seq++}" })
      as? SalesEngine.Result.Ok ?: error("رد شد")

  @Test fun lineTotal_offIsToday_onIsTheSharedVector() {
    val rows = v.getValue("line").jsonArray.map { it.jsonObject }
    assertTrue(rows.size >= 20)
    for (r in rows) {
      val price = r.getValue("price").jsonPrimitive.double
      val qty = r.getValue("qty").jsonPrimitive.double
      val name = r.getValue("name").jsonPrimitive.content
      QtyExactWiring.serverFlag = false
      assertEquals(name, r.getValue("legacyMinor").jsonPrimitive.long / 100.0, SalesEngine.lineTotal(price, qty), 0.0)
      QtyExactWiring.serverFlag = true
      assertEquals(name, r.getValue("onMinor").jsonPrimitive.long / 100.0, SalesEngine.lineTotal(price, qty), 0.0)
    }
  }

  @Test fun exactHalf_recordedSaleMatchesTheServerRule() {
    val cart = listOf(SalesEngine.CartLine("m", 1.015))
    QtyExactWiring.serverFlag = false
    assertEquals(101.0, record(cart).data.saleItems.single().totalPrice, 0.0)   // امروز: Double ِ ۱۰۱٫۴۹۹… ⇒ ۱۰۱
    QtyExactWiring.serverFlag = true
    val r = record(cart)
    assertEquals(102.0, r.data.saleItems.single().totalPrice, 0.0)              // ۳.۴ و `order-states.lineTotalMinor`: ۱۰۲
    assertEquals(102.0, r.data.sales.single().finalTotal, 0.0)
    assertEquals(-1.015, r.data.stockMovements.single().qty, 0.0)
  }

  @Test fun wholeQuantity_isTheSameWithTheFlagOn() {
    //  کالای «عدد» اعشار نمی‌پذیرد ⇒ ۵۰ × ۰٫۲۹ = ۱۴٫۴۹۹… ⇒ ۱۴، چه پرچم روشن چه خاموش (قاعدهٔ دقیق ۱۵ می‌داد)
    val cart = listOf(SalesEngine.CartLine("e", 50.0))
    QtyExactWiring.serverFlag = false
    val off = record(cart).data
    QtyExactWiring.serverFlag = true
    val on = record(cart).data
    assertEquals(14.0, off.saleItems.single().totalPrice, 0.0)
    assertEquals(off.saleItems.single().totalPrice, on.saleItems.single().totalPrice, 0.0)
    assertEquals(off.sales.single().finalTotal, on.sales.single().finalTotal, 0.0)
    assertEquals(off.stockMovements.single().qty, on.stockMovements.single().qty, 0.0)
  }

  @Test fun s04_labelAndManualWeight_endToEnd() {
    QtyExactWiring.serverFlag = true
    var cart = SalesEngine.addToCartCapped(emptyList(), "r", 1.25, 10.0).cart   // برچسبِ ترازو ۱٫۲۵۰ کیلو
    cart = SalesEngine.addToCartCapped(cart, "m", 0.75, 10.0).cart              // وزنِ دستی ۰٫۷۵
    val big = d.copy(warehouseEntries = d.warehouseEntries + WarehouseEntry(id = "w4", productId = "r", units = 5.0))
    assertEquals(450.0, SalesEngine.cartTotal(big, cart), 0.0)
    val r = SalesEngine.record(big, cart, SalesEngine.Checkout(), today = "2026-11-10", now = 1, newId = { "s${seq++}" }) as SalesEngine.Result.Ok
    assertEquals(listOf(375.0, 75.0), r.data.saleItems.map { it.totalPrice })
    assertEquals(listOf(1.25, 0.75), r.data.saleItems.map { it.quantity })
    assertEquals(450.0, r.data.sales.single().finalTotal, 0.0)
  }

  @Test fun cartTaps_noFloatDriftWithTheFlag() {
    //  سه بار «+۰٫۱ کیلو» در سبد
    fun taps(): Double {
      var cart = emptyList<SalesEngine.CartLine>()
      repeat(3) { cart = SalesEngine.addToCartCapped(cart, "m", 0.1, 10.0).cart }
      return cart.single().quantity
    }
    QtyExactWiring.serverFlag = false
    assertEquals("0.30000000000000004", taps().toString())   // همان امروز
    QtyExactWiring.serverFlag = true
    assertEquals("0.3", taps().toString())
    //  موجودیِ رانش‌دار (۰٫۱ + ۰٫۲) و ۰٫۱ در سبد ⇒ جای خالی دقیقاً ۰٫۲
    val added = SalesEngine.addToCartCapped(listOf(SalesEngine.CartLine("r", 0.1)), "r", 0.5, 0.1 + 0.2)
    assertTrue(added.capped)
    assertEquals("0.3", added.cart.single().quantity.toString())
  }

  @Test fun shortageMessage_showsTheWeight() {
    QtyExactWiring.serverFlag = false
    val off = SalesEngine.shortageMessage(meat, 1.5, 1.25)
    QtyExactWiring.serverFlag = true
    val on = SalesEngine.shortageMessage(meat, 1.5, 1.25)
    assertTrue(on, on.contains("1.25") && on.contains("1.5"))
    assertTrue(off, !off.contains("1.25"))
    //  مقدارِ درست همان پیامِ امروز (با جداکنندهٔ هزارگان)
    val wholeOn = SalesEngine.shortageMessage(egg, 2000.0, 1500.0)
    QtyExactWiring.serverFlag = false
    assertEquals(SalesEngine.shortageMessage(egg, 2000.0, 1500.0), wholeOn)
    assertTrue(wholeOn, wholeOn.contains("1,500"))
  }

  @Test fun lineSheet_rejectsAFourthDecimalOnlyWithTheFlag() {
    val info = LineModel.info(d, SalesEngine.CartLine("m", 1.0))
    val form = LineModel.start(info).copy(qtyText = "1.2345")
    QtyExactWiring.serverFlag = false
    assertNull(LineModel.qtyError(form, info))
    QtyExactWiring.serverFlag = true
    assertEquals(StrSale.qtyThreeDecimals, LineModel.qtyError(form, info))
    assertNull(LineModel.qtyError(form.copy(qtyText = "1.2500"), info))
    assertEquals(102.0, LineModel.total(form.copy(qtyText = "1.015"), info, LineModel.start(info)), 0.0)
  }

  @Test fun desktopDialogPreview_offIsTheRawProduct() {
    QtyExactWiring.serverFlag = false
    assertEquals(100.0 * 1.015, QtyExactWiring.preview(100.0, 1.015), 0.0)
    assertTrue(QtyExactWiring.preview(Double.NaN, 1.0).isNaN())
    QtyExactWiring.serverFlag = true
    assertEquals(102.0, QtyExactWiring.preview(100.0, 1.015), 0.0)
    assertEquals(0.29 * 50.0, QtyExactWiring.preview(0.29, 50.0), 0.0)
    assertTrue(QtyExactWiring.preview(Double.NaN, 1.25).isNaN())
  }
}
