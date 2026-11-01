package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.ui.components.pos.posLineSerials
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-28 — کالای سریال‌دار: بی سریالِ کامل فاکتور ثبت نمی‌شود، سریالِ فروخته‌شده دوباره فروخته نمی‌شود،
 *  سریال‌ها روی قلمِ فاکتور می‌نشینند، و کالای بی پرچم همان سبد، همان ثبت و همان JSON را دارد.
 */
class SerialSaleTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val phone = Product(id = "ph", name = "گوشی", unit = "دانه", salePrice = 9000.0, serialTracked = true)
  private val tea = Product(id = "tea", name = "چای", unit = "بسته", salePrice = 50.0)
  private val d = ShopData(
    products = listOf(phone, tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "ph", units = 5.0), WarehouseEntry(id = "w2", productId = "tea", units = 5.0)),
  )

  private fun sell(data: ShopData, vararg lines: SalesEngine.CartLine) =
    SalesEngine.record(data, lines.toList(), SalesEngine.Checkout(), "2026-11-01", 1_700_000_000_000, ::newId)

  @Test
  fun `بی سریالِ کامل فاکتور بسته نمی‌شود`() {
    val r = sell(d, SalesEngine.CartLine("ph", 2.0, serials = listOf("IMEI-1")))
    assertTrue(r is SalesEngine.Result.Failed && r.message.contains("1 از 2"))
    assertTrue(sell(d, SalesEngine.CartLine("ph", 2.0, serials = listOf("A", "A"))) is SalesEngine.Result.Failed)
    assertTrue(sell(d, SalesEngine.CartLine("ph", 1.5, serials = listOf("A"))) is SalesEngine.Result.Failed)
  }

  @Test
  fun `سریال‌ها روی قلم می‌نشینند و دوباره فروخته نمی‌شوند تا برنگردند`() {
    val ok = sell(d, SalesEngine.CartLine("ph", 2.0, serials = listOf("A", "B"))) as SalesEngine.Result.Ok
    val item = ok.data.saleItems.single()
    assertEquals(listOf("A", "B"), item.serials)
    assertEquals(setOf("A", "B"), SerialSale.sold(ok.data))
    val again = sell(ok.data, SalesEngine.CartLine("ph", 1.0, serials = listOf("B")))
    assertTrue(again is SalesEngine.Result.Failed && again.message.contains("B"))
    //  برگشت ⇒ آزاد
    val back = SalesEngine.recordReturn(ok.data, ok.saleId, mapOf(item.id to 1.0), "خراب", "2026-11-01", 1, ::newId) as SalesEngine.Result.Ok
    assertTrue(sell(back.data, SalesEngine.CartLine("ph", 1.0, serials = listOf("B"))) is SalesEngine.Result.Ok)
  }

  @Test
  fun `کالای بی پرچم همان ثبت و همان JSON`() {
    val ok = sell(d, SalesEngine.CartLine("tea", 2.0)) as SalesEngine.Result.Ok
    assertNull(ok.data.saleItems.single().serials)
    val json = LedgerDiff.toJson(ok.data)
    assertFalse(json["saleItems"]!!.jsonArray.single().jsonObject.containsKey("serials"))
    assertFalse(json["products"]!!.jsonArray.first { it.jsonObject["id"].toString().contains("tea") }.jsonObject.containsKey("serialTracked"))
    assertNull(posLineSerials(d, SalesEngine.CartLine("tea", 1.0)) {})
    assertTrue(posLineSerials(d, SalesEngine.CartLine("ph", 1.0)) {} != null)
  }

  @Test
  fun `سبد و پیش‌نویس سریال‌ها را نگه می‌دارند`() {
    val cart = CartSerials.set(listOf(SalesEngine.CartLine("ph", 2.0), SalesEngine.CartLine("tea", 1.0)), "ph", listOf("A", "B"))
    assertEquals(listOf("A", "B"), cart[0].serials)
    assertNull(cart[1].serials)
    assertTrue(CartSerials.set(cart, "ph", listOf("A", "B")) === cart)
    val back = SaleDraft.of("k", cart, null, 0).cart()
    assertEquals(cart, back)
    //  فرم پرچم را می‌خواند و می‌نویسد
    val p = ProductInfoMap.apply(tea, ProductInfo(serialTracked = true))
    assertEquals(true, p.serialTracked)
    assertEquals(null, ProductInfoMap.apply(p, ProductInfo()).serialTracked)
  }
}
