package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

/**
 *  POS-42 — جدولِ موجودی پس از یک فروش (`ShopStore.index` ⇐ `StockIndex.extend`) **بیت‌به‌بیت** همان ساختنِ کامل است:
 *  موجودی، فروخته و کارتنِ هر کالا روی ۲۰۰ دفترِ تصادفیِ قطعی با کیت، برگشتِ جزئی، فاکتورِ لغوشده، قلمِ یتیم و قلمِ آزاد، و
 *  زنجیره‌ای از فروش‌های پیاپی. هر تغییرِ دیگرِ دفتر (لغو، برگشت، ورودِ انبار، کالا، فاکتورِ تازهٔ لغوشده) راهِ افزایشی را
 *  نمی‌گیرد و از نو ساخته می‌شود.
 */
class StockIndexAppendTest {

  private fun ledger(seed: Int): ShopData {
    val r = Random(seed)
    val products = List(30) { i ->
      Product(id = "p$i", name = "کالا $i", salePrice = 10.0 + i, minStock = r.nextInt(4).toDouble())
    } + Product(id = "kit", name = "کیت", kit = listOf(KitPart("p1", 2.0), KitPart("p2", 0.5)))
    val entries = List(40) { i ->
      WarehouseEntry(id = "w$i", productId = products[r.nextInt(products.size)].id, cartons = r.nextInt(3).toDouble(), units = r.nextDouble(1.0, 50.0))
    }
    var d = ShopData(products = products, warehouseEntries = entries)
    repeat(25) { n -> d = sell(d, r, "s$seed-$n", cancelled = r.nextInt(8) == 0) }
    //  قلمِ یتیم (فاکتورش هنوز نرسیده)
    d = d.copy(saleItems = d.saleItems + SaleItem(id = "orphan", saleId = "late-$seed", productId = "p3", quantity = 1.5))
    return d
  }

  private fun sell(d: ShopData, r: Random, id: String, cancelled: Boolean = false): ShopData {
    val items = List(1 + r.nextInt(4)) { k ->
      val pid = when (r.nextInt(10)) {
        0 -> "kit"
        1 -> ""
        else -> "p${r.nextInt(30)}"
      }
      val q = if (r.nextBoolean()) r.nextInt(1, 5).toDouble() else r.nextDouble(0.1, 3.0)
      SaleItem(
        id = "$id-$k", saleId = id, productId = pid, quantity = q,
        returnedQty = if (r.nextInt(6) == 0) q / 3 else 0.0,
        parts = if (pid == "kit") listOf(KitPart("p1", 2.0), KitPart("p2", 0.5)) else null,
      )
    }
    val sale = Sale(id = id, status = if (cancelled) "cancelled" else "completed")
    return d.copy(sales = d.sales + sale, saleItems = d.saleItems + items)
  }

  private fun same(full: ShopStore.Companion.StockIndex, inc: ShopStore.Companion.StockIndex, d: ShopData, why: String) {
    for (p in d.products + Product(id = "")) {
      //  برابریِ دقیقِ بیت‌ها، نه «نزدیک»: جمع به همان ترتیب
      assertEquals("$why stock ${p.id}", full.stock(p.id).toRawBits(), inc.stock(p.id).toRawBits())
      assertEquals("$why sold ${p.id}", full.soldQty(p.id).toRawBits(), inc.soldQty(p.id).toRawBits())
      assertEquals("$why cartons ${p.id}", full.cartons(p.id).toRawBits(), inc.cartons(p.id).toRawBits())
      assertEquals("$why status ${p.id}", full.status(p), inc.status(p))
    }
  }

  @Test
  fun `زنجیرهٔ فروش‌های پیاپی همان جدولِ کامل را می‌سازد`() {
    repeat(200) { seed ->
      val r = Random(seed * 31 + 7)
      var d = ledger(seed)
      var inc = ShopStore.Companion.StockIndex(d)
      repeat(6) { n ->
        val next = sell(d, r, "t$seed-$n")
        val grown = inc.extend(d, next)
        assertNotNull("seed $seed فروشِ $n باید افزایشی باشد", grown)
        same(ShopStore.Companion.StockIndex(next), grown!!, next, "seed $seed فروشِ $n")
        d = next
        inc = grown
      }
      //  فاکتورِ تازهٔ بی قلم (همگام‌سازی پیش از اقلامش) ⇒ همان جدول، بی رونوشت
      val bare = d.copy(sales = d.sales + Sale(id = "bare-$seed"))
      assertSame(inc, inc.extend(d, bare))
      //  قلمِ یتیمی که فاکتورش اکنون رسید
      val late = bare.copy(sales = bare.sales + Sale(id = "late-$seed"))
      same(ShopStore.Companion.StockIndex(late), inc.extend(bare, late)!!, late, "seed $seed فاکتورِ دیررس")
    }
  }

  @Test
  fun `هر تغییری جز افزودنِ فروش از نو ساخته می‌شود`() {
    val d = ledger(1)
    val idx = ShopStore.Companion.StockIndex(d)
    val sale = d.sales.first { it.status != "cancelled" }
    //  لغوِ فاکتورِ پیشین (شیءِ تازه در همان جا)
    assertNull(idx.extend(d, d.copy(sales = d.sales.map { if (it.id == sale.id) it.copy(status = "cancelled") else it })))
    //  برگشتِ قلم
    assertNull(idx.extend(d, d.copy(saleItems = d.saleItems.mapIndexed { i, it -> if (i == 0) it.copy(returnedQty = it.quantity) else it })))
    //  ورودِ انبار، کالا (کیت) و حذفِ فاکتور
    assertNull(idx.extend(d, d.copy(warehouseEntries = d.warehouseEntries + WarehouseEntry(id = "w-new", productId = "p1", units = 3.0))))
    assertNull(idx.extend(d, d.copy(products = d.products.toList())))
    assertNull(idx.extend(d, d.copy(sales = d.sales.drop(1))))
    //  فاکتورِ تازهٔ لغوشده: قلمِ یتیمِ پیشینش از جمع بیرون می‌رود ⇒ از نو
    assertNull(idx.extend(d, d.copy(sales = d.sales + Sale(id = "late-1", status = "cancelled"))))
  }

  @Test
  fun `ShopStore_index پس از فروش همان عددِ ساختنِ کامل را می‌دهد`() {
    val r = Random(99)
    var d = ledger(5)
    ShopStore.index(d)
    repeat(10) { n ->
      d = sell(d, r, "idx-$n")
      same(ShopStore.Companion.StockIndex(d), ShopStore.index(d), d, "فروشِ $n")
    }
    //  لغو ⇒ ساختنِ کامل (عددِ لغو در جدول)
    val cancelled = d.copy(sales = d.sales.map { if (it.id == "idx-3") it.copy(status = "cancelled") else it })
    same(ShopStore.Companion.StockIndex(cancelled), ShopStore.index(cancelled), cancelled, "لغو")
  }
}
