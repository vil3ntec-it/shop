package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.data.CountFilter
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.Supplier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-05 — «انبارگردانی» ِ سادهٔ نسخهٔ ۳ (`stock/count`، وقتی «انبارگردانیِ تازه» خاموش است) صافیِ دسته، تأمین‌کننده،
 *  برند و قفسه دارد، با همان `CountFilter` ِ «انبارگردانیِ تازه»: فقط فهرستِ دیده‌شده کم می‌شود؛ شمرده‌ها، جمع‌ها و ثبت
 *  همان‌اند و بی صافی همان فهرستِ پیشین. دکانی که هیچ دسته/تأمین‌کننده/برند/قفسه‌ای ندارد هیچ ردیفِ تراشه‌ای نمی‌بیند.
 */
class StockCountScopeTest {

  private val rice = Product(id = "rice", name = "برنج", category = "خوراک", supplierId = "s1", brand = "طلا", aisle = "۱", shelf = "۲", barcodes = listOf("111"))
  private val oil = Product(id = "oil", name = "روغن", category = " خوراک ", supplierId = "s2", brand = "لادن", aisle = "۱", shelf = "۳")
  private val soap = Product(id = "soap", name = "صابون", category = "شوینده", supplierId = "s1", brand = "طلا", aisle = "۲")
  private val gone = Product(id = "gone", name = "آرد", category = "قدیمی", supplierId = "s3", brand = "کهنه", aisle = "۹", active = false)
  private val d = ShopData(
    products = listOf(rice, oil, soap, gone),
    suppliers = listOf(Supplier(id = "s2", name = "زرین"), Supplier(id = "s1", name = "البرز"), Supplier(id = "s3", name = "کهنه")),
  )

  @Test
  fun `بی صافی همان فهرستِ پیشین`() {
    assertEquals(listOf("برنج", "روغن", "صابون"), CountState.rows(d, "").map { it.name })
    assertEquals(CountState.rows(d, ""), CountState.rows(d, "", CountScope()))
    assertEquals(listOf("rice"), CountState.rows(d, " 111 ", CountScope()).map { it.id })
    assertFalse(CountScope().active)
  }

  @Test
  fun `صافیِ دسته، تأمین‌کننده، برند و قفسه — تنها و با هم و با جست‌وجو`() {
    assertEquals(listOf("rice", "oil"), CountState.rows(d, "", CountScope(category = "خوراک")).map { it.id })
    assertEquals(listOf("rice", "soap"), CountState.rows(d, "", CountScope(supplierId = "s1")).map { it.id })
    assertEquals(listOf("rice", "soap"), CountState.rows(d, "", CountScope(brand = "طلا")).map { it.id })
    val shelf12 = CountFilter.shelfKey(rice)!!
    assertEquals(listOf("rice"), CountState.rows(d, "", CountScope(shelf = shelf12)).map { it.id })
    assertEquals(listOf("rice"), CountState.rows(d, "", CountScope(category = "خوراک", brand = "طلا")).map { it.id })
    assertEquals(emptyList<String>(), CountState.rows(d, "روغن", CountScope(supplierId = "s1")).map { it.id })
    //  کالای غیرِفعال با صافی هم نمی‌آید
    assertEquals(emptyList<String>(), CountState.rows(d, "", CountScope(brand = "کهنه")).map { it.id })
    assertTrue(CountScope(shelf = shelf12).active)
  }

  @Test
  fun `گزینه‌ها فقط از کالاهای فعال؛ دکانِ ساده هیچ تراشه‌ای ندارد`() {
    val o = CountState.options(d)
    assertEquals(listOf("خوراک", "شوینده"), o.categories.map { it.first })
    assertEquals(listOf("s1" to "البرز", "s2" to "زرین"), o.suppliers)
    assertEquals(listOf("طلا", "لادن"), o.brands.map { it.first })
    assertEquals(CountFilter.shelves(d.products), o.shelves.map { it.first })
    //  برچسبِ قفسه همان برچسبِ «انبارگردانیِ تازه»
    assertEquals(CountFilter.shelves(d.products).map(CountFilter::shelfLabel), o.shelves.map { it.second })
    assertFalse(o.isEmpty)
    val plain = CountState.options(ShopData(products = listOf(Product(id = "p", name = "خودکار"))))
    assertTrue(plain.isEmpty)
  }

  @Test
  fun `صافی فقط دیدن است — شمرده‌ها و اختلاف‌ها همان`() {
    val s = CountState(counts = mapOf("rice" to "5", "soap" to "2"))
    val shown = CountState.rows(d, "", CountScope(category = "خوراک")).map { it.id }
    assertFalse("soap" in shown)
    //  کالای بیرونِ صافی هم در ثبت و جمع می‌ماند
    assertEquals(setOf("rice", "soap"), s.lines(d).map { it.product.id }.toSet())
  }
}
