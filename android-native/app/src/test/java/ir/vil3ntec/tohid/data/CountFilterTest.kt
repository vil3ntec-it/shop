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
}
