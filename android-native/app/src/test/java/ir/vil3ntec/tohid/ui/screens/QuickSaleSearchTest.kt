package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.product.VisualSearch
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  فروشِ سریعِ پیشین (`QuickSale`، پیش‌نمایشِ گوشیِ کامپیوتر): «مشابه» ِ جست‌وجو با عکس (AI-11) نامِ رقم‌دار را با «1*» ِ صریح در
 *  کادر می‌گذارد (`VisualSearch.tillText`) ⇒ صافی باید همان بخشِ پس از پیشوندِ شمار را بجوید (`CartQuery`)، مثلِ نسخهٔ ۳
 *  (`CatalogModel.query`) و `DeskSale` (`PosSearch.parse`). پیش از این `p.name.contains("1*3x3 کابل")` ⇒ فهرستِ خالی.
 */
class QuickSaleSearchTest {

  private val cable = Product(id = "cable", name = "3x3 کابل", salePrice = 10.0)
  private val tea = Product(id = "tea", name = "چای سبز", salePrice = 5.0, barcodes = listOf("12x34"))
  private val off = Product(id = "off", name = "چای کهنه", salePrice = 5.0, active = false)
  private val d = ShopData(products = listOf(cable, tea, off))

  private fun ids(search: String) = QuickSaleSearch.shown(d, search, null, infoSearch = false).map { it.id }

  @Test
  fun `«مشابه» ِ نامِ رقم‌دار در کادرِ فروشِ سریع همان کالا را نشان می‌دهد`() {
    assertEquals("1*3x3 کابل", VisualSearch.tillText(cable.name))
    assertEquals(listOf("cable"), ids(VisualSearch.tillText(cable.name)))
    assertEquals("رقمِ فارسی در پیشوند", listOf("tea"), ids("۲*چای"))
  }

  @Test
  fun `جست‌وجوی ساده و بارکدِ کاملِ ستاره‌دار همان امروز است`() {
    assertEquals(listOf("cable"), ids("کابل"))
    //  «3x3» ِ تایپ‌شده همان پیشوندِ شمار است (مثلِ نسخهٔ ۳ و کامپیوتر) ⇒ «3» جست‌وجو می‌شود؛ کابل همچنان پیداست
    assertTrue("cable" in ids("3x3"))
    assertEquals("کالای غیرفعال نه", listOf("tea"), ids(" چای "))
    //  متنی که خودش بارکدِ کامل است پیشوند خوانده نمی‌شود (POS-03، `PosSearch.isFullCode`)
    assertEquals(listOf("tea"), ids("12x34"))
    assertEquals(listOf("cable", "tea"), ids(""))
  }
}
