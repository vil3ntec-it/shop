package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/**
 *  S125 — نمایه‌های کالا (`ShopStore.barcodeIndex/product/productsById`) برای همان فهرست یک بار ساخته می‌شوند و با فهرستِ
 *  تازه (ویرایشِ کالا) تازه می‌شوند؛ پاسخ همان `products.find { it.id == … }` است (شناسهٔ تکراری ⇒ نخستین).
 */
class ProductIndexesTest {

  private val a = Product(id = "a", name = "چای", unit = "عدد", salePrice = 10.0, barcodes = listOf("111", "112"))
  private val b = Product(id = "b", name = "قند", unit = "عدد", salePrice = 20.0, barcodes = listOf("222"))

  @Test fun `همان فهرست ⇒ همان نمایه؛ فهرستِ تازه ⇒ نمایهٔ تازه با کالای ویرایش‌شده`() {
    val d = ShopData(products = listOf(a, b))
    val idx = ShopStore.barcodeIndex(d)
    assertSame("اسکنِ دوم نمایه را از نو نمی‌سازد", idx, ShopStore.barcodeIndex(d.copy(sales = emptyList())))
    assertEquals(mapOf("111" to "a", "112" to "a", "222" to "b"), idx)
    assertSame(ShopStore.productsById(d.products), ShopStore.productsById(d.products))

    val edited = d.copy(products = d.products.map { if (it.id == "b") it.copy(name = "قندِ نو", barcodes = listOf("333")) else it })
    assertNotSame(idx, ShopStore.barcodeIndex(edited))
    assertEquals("b", ShopStore.barcodeIndex(edited)["333"])
    assertNull("بارکدِ کهنه از نمایهٔ تازه رفت", ShopStore.barcodeIndex(edited)["222"])
    assertEquals("قندِ نو", ShopStore.product(edited, "b")?.name)
    assertEquals("قند", ShopStore.product(d, "b")?.name)
    assertNull(ShopStore.product(d, "گم"))
  }

  @Test fun `پاسخ همان find است — شناسهٔ تکراری ⇒ نخستین`() {
    val twin = b.copy(name = "قندِ دوم")
    val d = ShopData(products = listOf(a, b, twin))
    for (id in listOf("a", "b", "x")) assertEquals(d.products.find { it.id == id }, ShopStore.product(d, id))
    assertEquals("قند", ShopStore.productsById(d.products)["b"]?.name)
  }
}
