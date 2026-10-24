package ir.vil3ntec.tohid.ui.v3.products

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Badge
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Empty
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Filter
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Layout
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Query
import ir.vil3ntec.tohid.ui.v3.products.ProductsListModel.Sort
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فهرستِ کالاها: پرسشِ مسیر، فیلتر و مرتب‌سازی، آمار، نشانِ ردیف، اسکن */
class ProductsListModelTest {
  private val milk = Product("m", name = "شیر", category = "لبنیات", unit = "عدد", purchasePrice = 40.0, salePrice = 50.0, minStock = 5.0, barcodes = listOf("6260001"), createdAt = 3)
  private val tea = Product("t", name = "چای", category = "نوشیدنی", purchasePrice = 100.0, salePrice = 130.0, createdAt = 1)
  private val soap = Product("s", name = "صابون", category = "شوینده", purchasePrice = 10.0, salePrice = 15.0, minStock = 2.0, createdAt = 2)
  private val old = Product("o", name = "کبریت", category = "شوینده", salePrice = 5.0, active = false)
  private val all = listOf(milk, tea, soap, old)
  private val stock = mapOf("m" to 3.0, "t" to 0.0, "s" to 20.0, "o" to 7.0)
  private val stockOf: (String) -> Double = { stock[it] ?: 0.0 }

  private fun ids(q: Query) = ProductsListModel.rows(all, stockOf, q).map { it.id }

  @Test
  fun `پرسشِ مسیر حالتِ آغازین می‌شود و مقدارِ ناشناخته پیش‌فرض است`() {
    val q = ProductsListModel.fromArgs("شیر", "", "low", "stock_desc")
    assertEquals(Query("شیر", null, Filter.LOW, Sort.MOST_STOCK), q)
    assertEquals(Query(), ProductsListModel.fromArgs(null, null, "nope", "nope"))
    assertEquals(Filter.ALL, Filter.parse(null))
    assertEquals(Layout.LIST, Layout.parse("x"))
    assertEquals(Layout.GRID, Layout.parse("grid"))
    assertFalse(Query(sort = Sort.NEWEST).narrowed)
    assertTrue(Query(category = "لبنیات").narrowed)
  }

  @Test
  fun `وضعیت همان تعریفِ زنگ است — کم، تمام‌شده، غیرفعال`() {
    assertEquals(listOf("m"), ids(Query(filter = Filter.LOW)))
    assertEquals(listOf("t"), ids(Query(filter = Filter.OUT)))
    assertEquals(listOf("o"), ids(Query(filter = Filter.INACTIVE)))
    val counts = ProductsListModel.counts(all, stockOf)
    assertEquals(4, counts[Filter.ALL])
    assertEquals(1, counts[Filter.LOW])
    assertEquals(1, counts[Filter.OUT])
    assertEquals(1, counts[Filter.INACTIVE])
  }

  @Test
  fun `دسته و جست‌وجو (با رقمِ فارسی) با هم`() {
    assertEquals(listOf("o", "s"), ids(Query(category = "شوینده")).sorted())
    assertEquals(listOf("m"), ids(Query(text = "۶۲۶۰")))
    assertEquals(emptyList<String>(), ids(Query(text = "چای", category = "لبنیات")))
  }

  @Test
  fun `پنج مرتب‌سازیِ صفحهٔ پیشین`() {
    assertEquals(listOf("t", "m", "o", "s"), ids(Query(sort = Sort.LOW_FIRST)))
    assertEquals(listOf("s", "o", "m", "t"), ids(Query(sort = Sort.MOST_STOCK)))
    assertEquals(listOf("t", "m", "s", "o"), ids(Query(sort = Sort.PRICIEST)))
    assertEquals(listOf("m", "s", "t", "o"), ids(Query(sort = Sort.NEWEST)))
    assertEquals(all.sortedBy { it.name }.map { it.id }, ids(Query()))
  }

  @Test
  fun `آمار همان حسابِ پیشین است — موجودی × فروش منهای موجودی × خرید`() {
    val s = ProductsListModel.stats(all, stockOf)
    assertEquals(4, s.items)
    //  شیر ۳×۱۰ + صابون ۲۰×۵ + کبریت ۷×۵
    assertEquals(30.0 + 100.0 + 35.0, s.potentialProfit, 1e-9)
    //  موجودیِ منفی (فروشِ بیش از ورود) هم مثلِ امروز در جمع می‌آید
    assertEquals(-10.0, ProductsListModel.stats(listOf(soap), { -2.0 }).potentialProfit, 1e-9)
  }

  @Test
  fun `نشانِ ردیف فقط وقتی گفتنی است`() {
    assertEquals(Badge.LOW, ProductsListModel.badge(milk, 3.0))
    assertEquals(Badge.OUT, ProductsListModel.badge(tea, 0.0))
    assertEquals(Badge.INACTIVE, ProductsListModel.badge(old, 0.0))
    assertEquals(Badge.NONE, ProductsListModel.badge(soap, 20.0))
  }

  @Test
  fun `فهرستِ خالی: بی کالا یا بی نتیجه`() {
    assertEquals(Empty.NO_PRODUCTS, ProductsListModel.empty(0, 0))
    assertEquals(Empty.NO_MATCH, ProductsListModel.empty(4, 0))
    assertEquals(Empty.NONE, ProductsListModel.empty(4, 2))
  }

  @Test
  fun `اسکن: شناخته ⇐ صفحهٔ کالا؛ ناشناس ⇐ کالای تازه با همان بارکد`() {
    val index = mapOf("6260001" to "m")
    assertEquals("products/m", ProductsListModel.scanTarget(index, " 6260001 "))
    assertEquals("products/new?barcode=999", ProductsListModel.scanTarget(index, "999"))
  }

  @Test
  fun `کالاهای تازه‌ثبت از تفاوتِ دو دفتر`() {
    val before = ShopData(products = listOf(milk))
    val after = ShopData(products = listOf(milk, tea, soap))
    assertEquals(listOf("t", "s"), ProductsListModel.added(before, after))
    assertTrue(ProductsListModel.added(after, after).isEmpty())
    assertNull(ProductsListModel.added(before, before).firstOrNull())
  }
}
