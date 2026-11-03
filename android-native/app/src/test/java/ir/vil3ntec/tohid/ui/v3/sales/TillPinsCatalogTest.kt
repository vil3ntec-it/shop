package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-06 — چیدمانِ کاشی‌های صندوقِ گوشیِ همین دستگاه: سنجاق‌شده‌ها (`TillPinsPrefs` ⇐ `TillPins`) به ترتیبِ سنجاق سرِ شبکه،
 *  در «همه» و در هر دسته؛ هنگامِ جست‌وجو همان جورِ جست‌وجو؛ بی سنجاق همان شبکهٔ امروز.
 */
class TillPinsCatalogTest {

  private val index = CatalogModel.index(ledger)
  private fun ids(query: String = "", category: String? = null, pins: List<String> = emptyList()) =
    CatalogModel.items(ledger, index, query, category, emptyMap(), pins = pins).map { it.id }

  @Test
  fun `بی سنجاق همان ترتیبِ امروز`() {
    assertEquals(CatalogModel.items(ledger, index, "", null, emptyMap()).map { it.id }, ids())
    assertTrue(ids().size >= 3)
  }

  @Test
  fun `سنجاق‌شده‌ها به ترتیبِ سنجاق سرِ شبکه و بقیه همان ترتیب`() {
    val all = ids()
    val pins = listOf(all.last(), all[1])
    val got = ids(pins = pins)
    assertEquals(pins, got.take(2))
    assertEquals(all - pins.toSet(), got.drop(2))
  }

  @Test
  fun `در دسته فقط سنجاقِ همان دسته و هنگامِ جست‌وجو بی اثر`() {
    val d = ShopData(products = listOf(
      Product(id = "a", name = "شیر", unit = "عدد", salePrice = 70.0, category = "لبنیات"),
      Product(id = "b", name = "پنیر", unit = "عدد", salePrice = 260.0, category = "لبنیات"),
      Product(id = "c", name = "ماست", unit = "عدد", salePrice = 90.0, category = "لبنیات"),
      Product(id = "n", name = "نان", unit = "عدد", salePrice = 20.0, category = "نان"),
    ))
    val ix = CatalogModel.index(d)
    fun of(query: String = "", category: String? = null, pins: List<String> = emptyList()) =
      CatalogModel.items(d, ix, query, category, emptyMap(), pins = pins).map { it.id }
    val inCat = of(category = "لبنیات")
    val pinned = of(category = "لبنیات", pins = listOf("n", inCat.last()))
    assertEquals(inCat.last(), pinned.first())
    assertEquals(inCat.toSet(), pinned.toSet())
    assertEquals(listOf("n") + of().filter { it != "n" }, of(pins = listOf("n")))
    assertEquals(of(query = "پنیر"), of(query = "پنیر", pins = listOf("a")))
  }
}
