package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** کاتالوگِ صندوقِ گوشی: نرمال‌سازی، پرفروش‌ترین اول، بارکد، دسته و ورودیِ صفحه‌کلید */
class CatalogSearchTest {

  private val rice = Product("rice", name = "برنج هندی", category = "خوراکی", unit = "کیلوگرم", barcodes = listOf("626001000"))
  private val oil = Product("oil", name = "روغن آفتاب‌گردان ۵ لیتری", category = "خوراکی", barcodes = listOf("626001001"))
  private val tea = Product("tea", name = "چاي سبز", category = "نوشیدنی", barcodes = listOf("626001003"))
  private val soap = Product("soap", name = "صابون دست", category = "بهداشتی")
  private val cola = Product("cola", name = "كوكا كولا", category = "نوشیدنی", barcodes = listOf("5449000000996"))
  private val off = Product("off", name = "برنج قدیمی", category = "خوراکی", active = false)

  private val sales = listOf(Sale("s1"), Sale("s2"), Sale("s3"), Sale("s4", status = "cancelled"))
  private val items = listOf(
    SaleItem("i1", saleId = "s1", productId = "tea"),
    SaleItem("i2", saleId = "s2", productId = "tea"),
    SaleItem("i3", saleId = "s2", productId = "tea"),      // همان فاکتور ⇐ یک فروش
    SaleItem("i4", saleId = "s3", productId = "tea"),
    SaleItem("i5", saleId = "s1", productId = "oil"),
    SaleItem("i6", saleId = "s3", productId = "oil"),
    SaleItem("i7", saleId = "s2", productId = "soap"),
    SaleItem("i8", saleId = "s4", productId = "soap"),     // لغوشده
    SaleItem("i9", saleId = "s4", productId = "soap"),
    SaleItem("i10", saleId = "s1", productId = ""),        // قلمِ آزاد
  )
  private val all = listOf(rice, oil, tea, soap, cola, off)
  private val pop = CatalogSearch.popularity(sales, items)
  private val index = CatalogSearch.index(all, pop)

  @Test
  fun `شمارِ فروش — فاکتور نه ردیف، بی لغوشده و بی قلمِ آزاد`() {
    assertEquals(mapOf("tea" to 3, "oil" to 2, "soap" to 1), pop)
  }

  @Test
  fun `بی جست‌وجو — پرفروش‌ترین اول، بعد نام؛ غیرفعال نه`() {
    val ids = CatalogSearch.search(index, "", null)
    assertEquals(listOf("tea", "oil", "soap"), ids.take(3))
    assertEquals(setOf("rice", "cola"), ids.drop(3).toSet())
    assertTrue("off" !in ids)
    assertEquals(5, index.size)
  }

  @Test
  fun `پرفروش‌ها — فقط فروخته‌شده‌ها، حداکثر n`() {
    assertEquals(listOf("tea", "oil", "soap"), CatalogSearch.top(index))
    assertEquals(listOf("tea", "oil"), CatalogSearch.top(index, 2))
  }

  @Test
  fun `ي و ی، ك و ک، رقمِ فارسی و نیم‌فاصله یکی‌اند`() {
    assertEquals(listOf("tea"), CatalogSearch.search(index, "چای", null))
    assertEquals(listOf("cola"), CatalogSearch.search(index, "کوکا", null))
    assertEquals(listOf("oil"), CatalogSearch.search(index, "۵ لیتری", null))
    assertEquals(listOf("oil"), CatalogSearch.search(index, "آفتابگردان", null))
    assertEquals(listOf("cola"), CatalogSearch.search(index, "COLA".let { "كولا" }, null))
  }

  @Test
  fun `کلمه‌ها به هر ترتیب`() {
    assertEquals(listOf("tea"), CatalogSearch.search(index, "سبز چای", null))
    assertEquals(emptyList<String>(), CatalogSearch.search(index, "سبز قهوه", null))
  }

  @Test
  fun `بارکدِ کامل پیش از همه؛ بخشی از بارکد از سه رقم`() {
    //  «626001001» هم بخشی از هیچ نامی نیست؛ کامل ⇐ همان روغن
    assertEquals(listOf("oil"), CatalogSearch.search(index, "۶۲۶۰۰۱۰۰۱", null))
    //  «6260010» در سه بارکد هست ⇐ همه، به ترتیبِ پرفروش‌ترین
    assertEquals(listOf("tea", "oil", "rice"), CatalogSearch.search(index, "6260010", null))
    //  دو رقم بارکد را نمی‌گردد
    assertEquals(emptyList<String>(), CatalogSearch.search(index, "62", null))
    //  بارکدِ کامل حتی اگر کالای پرفروش‌تری هم جور باشد اول است
    val withNum = CatalogSearch.index(all + Product("n", name = "پاکت 626001000", category = "خوراکی"), pop + ("n" to 9))
    assertEquals(listOf("rice", "n"), CatalogSearch.search(withNum, "626001000", null))
  }

  @Test
  fun `دسته`() {
    assertEquals(listOf("tea", "cola"), CatalogSearch.search(index, "", "نوشیدنی"))
    assertEquals(listOf("tea"), CatalogSearch.search(index, "چای", "نوشیدنی"))
    assertEquals(emptyList<String>(), CatalogSearch.search(index, "چای", "خوراکی"))
  }

  @Test
  fun `صفحه‌کلید — یک نقطه، سه اعشار، بی صفرِ آغاز، ⌫`() {
    fun type(keys: String, decimals: Boolean = true) = keys.fold("") { t, k -> KeypadInput.press(t, k, decimals) }
    assertEquals("1.25", type("1.25"))
    assertEquals("0.5", type(".5"))
    assertEquals("1.255", type("1.2559"))
    assertEquals("1.2", type("1..2"))
    assertEquals("7", type("07"))
    assertEquals("12", type("1.2", decimals = false))
    assertEquals("1234567", type("12345678"))
    assertEquals("1.", KeypadInput.press("1.2", KeypadInput.BACKSPACE, true))
    assertEquals(1.25, KeypadInput.value("1.25"), 0.0)
    assertEquals(0.0, KeypadInput.value("."), 0.0)
    assertEquals(0.0, KeypadInput.value(""), 0.0)
  }
}
