package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.CartQuery
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-04 — جست‌وجوی مقاومِ صندوقِ کامپیوتر (پشتِ `PosSearchPrefs`، پیش‌فرض خاموش).
 *  خاموش ⇒ همان `PosCatalog.matches` که «كوكا» (ک ِ عربی) و «چای سبز» (بی نیم‌فاصله) را پیدا نمی‌کرد.
 */
class PosSmartSearchTest {

  private val products = listOf(
    Product(id = "coke", name = "کوکاکولا", salePrice = 60.0, barcodes = listOf("6260001000017")),
    Product(id = "tea", name = "چای‌سبز احمد", salePrice = 250.0, barcodes = listOf("5011000000123")),
    Product(id = "old", name = "کوکاکولای قدیمی", salePrice = 50.0, active = false),
  )
  private val d = ShopData(products = products)
  private val index = PosCatalog.searchIndex(products)
  private val byId = products.associateBy { it.id }

  private fun smart(term: String) = PosCatalog.smartMatches(index, byId, term).map { it.id }

  @Test fun `خاموش همان رفتارِ پیشین است — حرفِ عربی و فاصله پیدا نمی‌شوند`() {
    assertTrue(PosCatalog.matches(products, "كوكا").isEmpty())
    assertTrue(PosCatalog.matches(products, "چای سبز").isEmpty())
  }

  @Test fun `روشن — حرفِ عربی، نیم‌فاصله، کلمه به هر ترتیب و غلطِ تایپی`() {
    assertEquals(listOf("coke"), smart("كوكا"))
    assertEquals(listOf("tea"), smart("چای سبز"))
    assertEquals(listOf("tea"), smart("احمد چای"))
    assertEquals(listOf("coke"), smart("کوکاکلا"))
    assertTrue(smart("").isEmpty())
  }

  @Test fun `کالای غیرفعال در جست‌وجوی مقاوم هم نمی‌آید (PRD-18)`() {
    assertEquals(listOf("coke"), smart("کوکاکولا"))
    //  کالایی که پس از ساختنِ نمایه غیرفعال شد هم نمی‌آید
    val off = byId + ("coke" to products[0].copy(active = false))
    assertTrue(PosCatalog.smartMatches(index, off, "کوکا").isEmpty())
  }

  @Test fun `Enter نخستینِ همان فهرست را برمی‌دارد`() {
    val term = CartQuery.parse("2*كوكا").term.trim()
    val hit = PosSearch.resolve("2*كوكا", ShopStore.barcodeIndex(d), products, PosCatalog.smartMatches(index, byId, term), 0)
    assertEquals(PosHit.Add(products[0], 2.0), hit)
  }
}
