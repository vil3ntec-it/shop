package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldStock
import ir.vil3ntec.tohid.data.HeldTag
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-03 — «قابل‌فروش» در کاتالوگِ صندوقِ گوشی: موجودی منهای آنچه در فاکتورهای **نگه‌داشتهٔ** همین دستگاه کنار رفته (نه سبدِ
 *  جاری، نه قلمِ آزاد). بی نگه‌داشته (پیش‌فرض: «چند فاکتورِ هم‌زمان» خاموش) همان موجودیِ امروز.
 */
class HeldStockTest {

  private val d = ShopData(
    products = listOf(
      Product(id = "cola", name = "نوشابه", unit = "عدد", salePrice = 60.0),
      Product(id = "rice", name = "برنج", unit = "کیلو", salePrice = 120.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "cola", units = 5.0),
      WarehouseEntry(id = "w2", productId = "rice", units = 10.0),
    ),
  )

  private fun held(id: String, vararg lines: SaleDraft.Line) = HeldInvoice(id = id, number = 1, lines = lines.toList())

  @Test fun `نگه‌داشته‌ها جمع می‌شوند؛ قلمِ آزاد و مقدارِ نادرست بیرون؛ سبدِ جاری در دفترِ نگه‌داشته نیست`() {
    val book = HeldBook(
      items = listOf(
        held("h1", SaleDraft.Line("cola", 2.0), SaleDraft.Line("rice", 1.25)),
        held("h2", SaleDraft.Line("cola", 1.0), SaleDraft.Line("", 3.0, unitPrice = 50.0), SaleDraft.Line("rice", Double.NaN), SaleDraft.Line("rice", -1.0)),
      ),
      current = HeldTag("cart", 3),
    )
    assertEquals(mapOf("cola" to 3.0, "rice" to 1.25), HeldStock.reserved(book))
    assertTrue(HeldStock.reserved(HeldBook()).isEmpty())
  }

  @Test fun `کاشیِ صندوق موجودیِ قابل‌فروش را نشان می‌دهد و بی نگه‌داشته همان موجودی`() {
    val cola = d.products.first { it.id == "cola" }
    val reserved = mapOf("cola" to 4.0)
    //  پنج دانه، چهارتا برای مشتریِ دیگری نگه‌داشته ⇒ یکی قابل‌فروش
    assertEquals(1.0, CatalogModel.item(d, cola, inCart = 0.0, held = reserved).stock, 1e-9)
    assertEquals(ShopStore.stock(d, "cola"), CatalogModel.item(d, cola, inCart = 0.0).stock, 1e-9)
    //  فهرست و «پرفروش‌ها» همان قاعده
    val index = CatalogModel.index(d)
    assertEquals(1.0, CatalogModel.items(d, index, "", null, emptyMap(), reserved).first { it.id == "cola" }.stock, 1e-9)
    assertEquals(10.0, CatalogModel.items(d, index, "", null, emptyMap(), reserved).first { it.id == "rice" }.stock, 1e-9)
  }

  /**
   *  POS-05: کاشیِ صندوقِ کامپیوتر (`ProductTile`/`ProductTable` با `PosProductCallbacks.stockOf`) همان «قابل‌فروش» ِ
   *  گوشی را نشان می‌دهد — یک قاعده (`HeldStock.sellable`) برای هر دو؛ پیش‌تر کامپیوتر موجودیِ کل را نشان می‌داد.
   */
  @Test fun `POS-05 کاشیِ صندوقِ کامپیوتر هم موجودیِ قابل‌فروش را با همان قاعدهٔ گوشی نشان می‌دهد`() {
    val book = HeldBook(items = listOf(held("h1", SaleDraft.Line("cola", 3.0)), held("h2", SaleDraft.Line("cola", 1.0))))
    val reserved = HeldStock.reserved(book)
    assertEquals(1.0, HeldStock.sellable(ShopStore.stock(d, "cola"), "cola", reserved), 1e-9)
    assertEquals(10.0, HeldStock.sellable(ShopStore.stock(d, "rice"), "rice", reserved), 1e-9)
    assertEquals(5.0, HeldStock.sellable(ShopStore.stock(d, "cola"), "cola", emptyMap()), 1e-9)

    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, "ir/vil3ntec/tohid/ui/screens/DeskSale.kt") }.firstOrNull { it.isFile }?.readText()
      ?: error("DeskSale.kt پیدا نشد")
    val cb = src.substringAfter("val productCb = PosProductCallbacks(").substringBefore("qtyOf")
    assertTrue(cb.contains("HeldStock.sellable(ShopStore.stock(d, it), it, heldQty)"))
    //  همان دفترِ نگه‌داشته‌های همین صندوق (نه سبدِ جاری)
    val q = "val heldQty = held.store.book.collectAsState().value.let { b -> remember(b) { ir.vil3ntec.tohid.data.HeldStock.reserved(b) } }"
    assertTrue(src.contains(q))
    assertTrue(src.indexOf(q) > src.indexOf("val held = ir.vil3ntec.tohid.ui.components.pos.rememberHeldInvoices("))
  }
}
