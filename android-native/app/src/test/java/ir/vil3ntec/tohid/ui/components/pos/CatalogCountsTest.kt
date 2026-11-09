package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 *  POS-42 — شمارِ فروشِ کاتالوگِ گوشی پس از هر فروش (`CatalogSearch.counts`، افزایشی) مو‌به‌مو همان `popularity` ِ کامل است:
 *  زنجیرهٔ فروش‌ها، دو ردیفِ یک کالا در یک فاکتور، قلمِ آزاد، فاکتورِ لغوشدهٔ پیشین و تازه، قلمِ یتیم و فاکتورِ دیررس، قلمِ
 *  تازه برای فاکتوری که پیش‌تر قلم داشت، لغو و برگشتِ میانِ راه، و رفت‌وبرگشت میانِ دو دفتر.
 */
class CatalogCountsTest {

  private fun sale(r: Random, id: String, cancelled: Boolean = false): Pair<Sale, List<SaleItem>> {
    val items = List(1 + r.nextInt(4)) { k ->
      SaleItem(id = "$id-$k", saleId = id, productId = if (r.nextInt(8) == 0) "" else "p${r.nextInt(12)}", quantity = 1.0)
    }
    return Sale(id = id, status = if (cancelled) "cancelled" else "completed") to items
  }

  @Test
  fun `هر گام همان شمارِ کامل است`() {
    repeat(150) { seed ->
      val r = Random(seed)
      var sales = emptyList<Sale>()
      var items = emptyList<SaleItem>()
      fun check(why: String) = assertEquals("seed $seed $why", CatalogSearch.popularity(sales, items), CatalogSearch.counts(sales, items))
      repeat(40) { n ->
        when (r.nextInt(12)) {
          //  قلمِ یتیم و سپس فاکتورش
          0 -> { items = items + SaleItem(id = "o$n", saleId = "late$n", productId = "p${r.nextInt(12)}"); check("یتیم") }
          1 -> { sales = sales + Sale(id = "late${n - 1}", status = if (r.nextBoolean()) "cancelled" else "completed"); check("دیررس") }
          //  قلمِ تازه برای فاکتوری که پیش‌تر قلم داشت (همان کالا ⇒ یک فروش)
          2 -> if (items.isNotEmpty()) { val o = items[r.nextInt(items.size)]; items = items + o.copy(id = "x$n"); check("قلمِ تازهٔ فاکتورِ کهنه") }
          //  لغوِ فاکتورِ پیشین (شیءِ تازه)
          3 -> if (sales.isNotEmpty()) { val i = r.nextInt(sales.size); sales = sales.mapIndexed { k, s -> if (k == i) s.copy(status = "cancelled") else s }; check("لغو") }
          else -> {
            val (s, it) = sale(r, "s$seed-$n", cancelled = r.nextInt(10) == 0)
            sales = sales + s
            items = items + it
            check("فروش $n")
          }
        }
      }
    }
  }

  @Test
  fun `رفت‌وبرگشت میانِ دو دفتر و نتیجهٔ پیشین دست نمی‌خورد`() {
    val r = Random(3)
    val (s1, i1) = sale(r, "a")
    val (s2, i2) = sale(r, "b")
    val before = CatalogSearch.counts(listOf(s1), i1)
    val snapshot = HashMap(before)
    val after = CatalogSearch.counts(listOf(s1, s2), i1 + i2)
    assertEquals("نگاشتِ پیشین همان ماند (صفحه‌ای که نمایهٔ پیشین را دارد)", snapshot, before)
    assertEquals(CatalogSearch.popularity(listOf(s1, s2), i1 + i2), after)
    //  دفترِ پیشین دوباره ⇒ کامل و درست
    assertEquals(snapshot, CatalogSearch.counts(listOf(s1), i1))
    assertEquals(CatalogSearch.popularity(listOf(s1, s2), i1 + i2), CatalogSearch.counts(listOf(s1, s2), i1 + i2))
  }

  @Test
  fun `POS-01 — کدِ کالا فقط با تنظیم در همان کادرِ جست‌وجو`() {
    val products = listOf(
      Product(id = "rice", name = "برنج", barcodes = listOf("6260001"), sku = "SKU-00042"),
      Product(id = "oil", name = "روغن", barcodes = listOf("6260002")),
    )
    val off = CatalogSearch.index(products, emptyMap())
    assertFalse(CatalogSearch.search(off, "SKU-00042", null).contains("rice"))
    val on = CatalogSearch.index(products, emptyMap(), codes = true)
    //  کامل ⇒ اولِ فهرست؛ بخشی از کد (از سه نویسه) ⇒ در نتیجه؛ رقمِ فارسی همان
    assertEquals(listOf("rice"), CatalogSearch.search(on, "sku-00042", null))
    assertTrue(CatalogSearch.search(on, "۰۰۰۴۲", null).contains("rice"))
    assertEquals(listOf("oil"), CatalogSearch.search(on, "6260002", null))
    //  خاموش دوباره ⇒ همان بارکدها (خانهٔ کلیدها تنظیم را هم می‌سنجد)
    assertFalse(CatalogSearch.search(CatalogSearch.index(products, emptyMap()), "SKU-00042", null).contains("rice"))
  }
}
