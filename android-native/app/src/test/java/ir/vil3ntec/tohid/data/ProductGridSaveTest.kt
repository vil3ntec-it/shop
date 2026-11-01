package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.DeskGrid
import ir.vil3ntec.tohid.core.product.DeskGrid.Col
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-22 — ثبتِ جدولِ ویرایشِ درجا روی دفتر: یا همه یا هیچ، فقط خانه‌های عوض‌شده، بارکدهای دیگرِ کالا
 *  سرِ جایشان، و تاریخچهٔ قیمت با کاربر و دلیل.
 */
class ProductGridSaveTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val tea = Product(id = "a", name = "چای", unit = "بسته", purchasePrice = 40.0, salePrice = 50.0, barcodes = listOf("111", "112"))
  private val sugar = Product(id = "b", name = "شکر", unit = "کیلو", purchasePrice = 60.0, salePrice = 70.0)
  private val salt = Product(id = "c", name = "نمک", unit = "بسته", salePrice = 10.0)
  private val base = ShopData(products = listOf(tea, sugar, salt))

  private fun sheetOf(vararg ids: String) = DeskGrid.sheet(ProductGridSave.rows(ids.map { id -> base.products.first { it.id == id } }))

  @Test
  fun `ردیف‌ها همان مقدارهای کالا را نشان می‌دهند`() {
    val r = ProductGridSave.rows(listOf(tea)).single()
    assertEquals(listOf("چای", "", "بسته", "40", "50", "0", "111"), r.cells)
  }

  @Test
  fun `فقط خانه‌های عوض‌شده نوشته می‌شوند و بقیه از دفترِ همان لحظه`() {
    val s = sheetOf("a", "b").set(0, Col.SELL, "۵۵").paste(1, Col.CATEGORY, "مواد\tکیلوگرم").sheet
    //  دستگاهِ دیگر میان‌راه قیمتِ خریدِ چای را عوض کرد
    val now = base.copy(products = listOf(tea.copy(purchasePrice = 45.0), sugar, salt))
    val r = ProductGridSave.apply(now, s, "2026-11-01", 0, ::newId, "علی") as ProductGridSave.Result.Ok
    assertEquals(2, r.saved)
    val t = r.data.products.first { it.id == "a" }
    assertEquals(55.0, t.salePrice, 0.0)
    assertEquals(45.0, t.purchasePrice, 0.0)
    //  بارکدِ دوم نیفتاد
    assertEquals(listOf("111", "112"), t.barcodes)
    val g = r.data.products.first { it.id == "b" }
    assertEquals("مواد", g.category)
    assertEquals("کیلوگرم", g.unit)
    //  کالای بیرونِ جدول دست نخورد
    assertEquals(salt, r.data.products.first { it.id == "c" })
  }

  @Test
  fun `تغییرِ قیمتِ خرید با کاربر و دلیل در تاریخچه می‌نشیند`() {
    val s = sheetOf("b").set(0, Col.BUY, "65")
    val r = ProductGridSave.apply(base, s, "2026-11-01", 0, ::newId, "علی") as ProductGridSave.Result.Ok
    val h = r.data.priceHistory.single()
    assertEquals(65.0, h.newPrice, 0.0)
    assertEquals("علی", h.by)
    assertEquals(ProductGridSave.REASON, h.reason)
  }

  @Test
  fun `ایرادِ جدول یا ردِ موتور یعنی هیچ‌چیز ثبت نمی‌شود`() {
    //  نامِ کالای بیرونِ جدول
    val dup = sheetOf("a").set(0, Col.NAME, "نمک")
    assertTrue(ProductGridSave.problems(base, dup).isNotEmpty())
    assertTrue(ProductGridSave.apply(base, dup, "2026-11-01", 0, ::newId, null) is ProductGridSave.Result.Failed)
    //  ردیفِ دوم را موتور رد می‌کند (کالا میان‌راه حذف شد) ⇒ ردیفِ اول هم ثبت نمی‌شود
    val s = sheetOf("a", "b").set(0, Col.SELL, "60").set(1, Col.SELL, "80")
    val gone = base.copy(products = listOf(tea, salt))
    val r = ProductGridSave.apply(gone, s, "2026-11-01", 0, ::newId, null)
    assertTrue(r is ProductGridSave.Result.Failed && r.productId == "b")
    //  بارکدِ کالای دیگر
    val code = sheetOf("b").set(0, Col.BARCODE, "112")
    assertTrue(ProductGridSave.problems(base, code).values.contains(DeskGrid.Problem.BARCODE_TAKEN))
  }

  @Test
  fun `جدولِ بی تغییر چیزی نمی‌نویسد`() {
    val r = ProductGridSave.apply(base, sheetOf("a", "b", "c"), "2026-11-01", 0, ::newId, null) as ProductGridSave.Result.Ok
    assertEquals(0, r.saved)
    assertEquals(base, r.data)
  }
}
