package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 *  INV-05 — صافیِ دسته و تأمین‌کننده در «انبارگردانی» (`StockCountSheet`): بی صافی همان فهرستِ پیشین
 *  (فعال‌ها، جست‌وجوی نام/بارکد، الفبایی)؛ با صافی فقط کالاهای همان دسته یا همان تأمین‌کننده.
 */
class CountFilterTest {

  private val products = listOf(
    Product(id = "rice", name = "برنج", category = "خوراک", supplierId = "s1", barcodes = listOf("111")),
    Product(id = "oil", name = "روغن", category = " خوراک ", supplierId = "s2"),
    Product(id = "soap", name = "صابون", category = "شوینده", supplierId = "s1"),
    Product(id = "pen", name = "خودکار", category = ""),
    Product(id = "old", name = "آرد", category = "قدیمی", supplierId = "s3", active = false),
  )
  private val suppliers = listOf(Supplier(id = "s2", name = "زرین"), Supplier(id = "s1", name = "البرز"), Supplier(id = "s3", name = "کهنه"), Supplier(id = "s9", name = "بی‌کالا"))

  @Test
  fun `بی صافی همان فهرستِ پیشین — فعال‌ها، جست‌وجو و ترتیبِ نام`() {
    val before = products.filter { it.active }.sortedBy { it.name }
    assertEquals(before, CountFilter.shown(products, ""))
    assertEquals(listOf("rice"), CountFilter.shown(products, " 111 ").map { it.id })
    assertEquals(listOf("soap"), CountFilter.shown(products, "صاب").map { it.id })
  }

  @Test
  fun `صافیِ دسته و تأمین‌کننده، تنها و با هم و با جست‌وجو`() {
    assertEquals(listOf("rice", "oil"), CountFilter.shown(products, "", category = "خوراک").map { it.id })
    assertEquals(listOf("rice", "soap"), CountFilter.shown(products, "", supplierId = "s1").map { it.id })
    assertEquals(listOf("rice"), CountFilter.shown(products, "", category = "خوراک", supplierId = "s1").map { it.id })
    assertEquals(emptyList<String>(), CountFilter.shown(products, "روغن", category = "شوینده").map { it.id })
    //  کالای غیرِفعال با صافی هم نمی‌آید
    assertEquals(emptyList<String>(), CountFilter.shown(products, "", supplierId = "s3").map { it.id })
  }

  @Test
  fun `گزینه‌ها فقط از کالاهای فعال — دسته یکتا و الفبایی، تأمین‌کنندهٔ دارای کالا به ترتیبِ نام`() {
    assertEquals(listOf("خوراک", "شوینده"), CountFilter.categories(products))
    assertEquals(listOf("s1", "s2"), CountFilter.suppliers(products, suppliers).map { it.id })
  }

  /** INV-05 — «بر اساسِ دسته، برند، تأمین‌کننده یا قفسه»: برند (PRD-06) و قفسه (PRD-26، راهرو + قفسه) هم صافی‌اند */
  @Test
  fun `صافیِ برند و قفسه، تنها و با هم؛ دکانِ بی برند و قفسه هیچ گزینهٔ تازه‌ای ندارد`() {
    val more = listOf(
      Product(id = "tea", name = "چای", brand = " احمد ", aisle = "۲", shelf = "۱", shelfLevel = "بالا"),
      Product(id = "tea2", name = "چای سبز", brand = "احمد", aisle = "۲", shelf = "۱", shelfLevel = "پایین"),
      Product(id = "salt", name = "نمک", brand = "گلستان", aisle = "۳"),
      Product(id = "sugar", name = "شکر", shelf = " ۴ "),
      Product(id = "gone", name = "کهنه", brand = "قدیم", aisle = "۹", active = false),
    )
    val all = products + more
    //  گزینه‌ها فقط از کالاهای فعال، یکتا (فاصله‌ها تمیز) و مرتب؛ طبقه جدا نمی‌شود
    assertEquals(listOf("احمد", "گلستان"), CountFilter.brands(all))
    val shelves = CountFilter.shelves(all)
    assertEquals(3, shelves.size)
    assertEquals(listOf("۲" to "۱", "۳" to "", "" to "۴").sortedBy { it.first + "\u001F" + it.second }, shelves.map(CountFilter::shelfParts))
    //  صافی
    assertEquals(listOf("tea", "tea2"), CountFilter.shown(all, "", brand = "احمد").map { it.id })
    val aisle2 = CountFilter.shelfKey(more[0])!!
    assertEquals(aisle2, CountFilter.shelfKey(more[1]))
    assertEquals(listOf("tea", "tea2"), CountFilter.shown(all, "", shelf = aisle2).map { it.id })
    assertEquals(listOf("sugar"), CountFilter.shown(all, "", shelf = CountFilter.shelfKey(more[3])).map { it.id })
    assertEquals(listOf("tea2"), CountFilter.shown(all, "سبز", brand = "احمد", shelf = aisle2).map { it.id })
    assertEquals(emptyList<String>(), CountFilter.shown(all, "", brand = "گلستان", shelf = aisle2).map { it.id })
    assertEquals(emptyList<String>(), CountFilter.shown(all, "", brand = "قدیم").map { it.id })
    //  بی برند و قفسه ⇒ هیچ گزینه، و بی صافی همان فهرستِ پیشین
    assertEquals(emptyList<String>(), CountFilter.brands(products))
    assertEquals(emptyList<String>(), CountFilter.shelves(products))
    assertEquals(CountFilter.shown(all, ""), CountFilter.shown(all, "", brand = null, shelf = null))
    assertEquals(null, CountFilter.shelfKey(products[0]))
  }

  @Test
  fun `هر دو پنجرهٔ انبارگردانی تراشهٔ برند و قفسه را می‌کشند`() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/$rel") }.first { it.isFile }.readText()
    for (rel in listOf("ui/screens/BulkSheets.kt", "ui/screens/StockCountTools.kt")) {
      val s = src(rel)
      org.junit.Assert.assertTrue(rel, s.contains("CountBrandShelfChips(brands, brand, { brand = it }, shelves, shelf, { shelf = it })"))
      org.junit.Assert.assertTrue(rel, s.contains("CountFilter.shown(d.products, term, category, supplierId, brand, shelf)"))
    }
    //  تراشه‌ها فقط با گزینه
    val chips = src("ui/screens/BulkSheets.kt")
    org.junit.Assert.assertTrue(chips.contains("if (brands.isNotEmpty()) CountFilterChips("))
    org.junit.Assert.assertTrue(chips.contains("if (shelves.isNotEmpty()) CountFilterChips("))
  }
}
