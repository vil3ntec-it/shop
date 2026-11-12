package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.net.ApiFailure
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
    val q = "val heldQty = ir.vil3ntec.tohid.ui.components.pos.rememberSellableHeld(held.store.book.collectAsState().value, d)"
    assertTrue(src.contains(q))
    assertTrue(src.indexOf(q) > src.indexOf("val held = ir.vil3ntec.tohid.ui.components.pos.rememberHeldInvoices("))
  }

  /**
   *  POS-05/INV-03 — رزروِ سفارشِ اینترنتی در صندوق: نگه‌داشتهٔ فعالِ سفارش‌های اینترنتی (`GET /api/shop/catalog/reserved`، پرچمِ
   *  خاموشِ `shop.stock_reserve`) هم از «قابل‌فروش» ِ کاشی کم می‌شود — با همان `HeldStock.sellable`، در گوشی و کامپیوتر؛ قلمی که فروشِ
   *  تحویلش در دفترِ همین دستگاه هست دو بار کم نمی‌شود. پیش‌تر فقط نگه‌داشته‌های همین صندوق.
   */
  @Test fun `POS-05 و INV-03 رزروِ سفارشِ اینترنتی هم از قابل‌فروشِ کاشی کم می‌شود؛ فروشِ تحویلِ رسیده دو بار نه`() {
    val server = 1_760_000_000_000L
    val body = kotlinx.serialization.json.Json.parseToJsonElement(
      """{"enabled":true,"now":$server,"lines":[{"productId":"cola","qtyMilli":2000,"saleItemId":"o-A-1","expiresAt":null},""" +
        """{"productId":"rice","qtyMilli":4000,"saleItemId":"o-B-1","expiresAt":${server + 60_000}}]}""",
    ) as kotlinx.serialization.json.JsonObject
    val snap = ir.vil3ntec.tohid.core.stock.OnlineHolds.parse(body, 5_000L)!!
    val online = ir.vil3ntec.tohid.core.stock.OnlineHolds.reserved(snap, 5_000L) { false }
    val local = HeldStock.reserved(HeldBook(items = listOf(held("h1", SaleDraft.Line("cola", 1.0)))))
    val all = HeldStock.withOnline(local, online)
    //  پنج نوشابه: یکی نگه‌داشتهٔ صندوق، دوتا سفارشِ اینترنتی ⇒ دو قابل‌فروش؛ ده برنج، چهارتا اینترنتی ⇒ شش
    assertEquals(2.0, HeldStock.sellable(ShopStore.stock(d, "cola"), "cola", all), 1e-9)
    assertEquals(6.0, HeldStock.sellable(ShopStore.stock(d, "rice"), "rice", all), 1e-9)
    assertEquals(2.0, CatalogModel.item(d, d.products.first { it.id == "cola" }, inCart = 0.0, held = all).stock, 1e-9)
    //  مهلتِ رزروِ برنج گذشت (یک دقیقه روی ساعتِ همین دستگاه) ⇒ ده
    val later = HeldStock.withOnline(local, ir.vil3ntec.tohid.core.stock.OnlineHolds.reserved(snap, 65_000L) { false })
    assertEquals(10.0, HeldStock.sellable(ShopStore.stock(d, "rice"), "rice", later), 1e-9)
    //  فروشِ تحویلِ o-A-1 در دفترِ دستگاه هست (موجودی همان را کم کرده) ⇒ رزروش دوباره کم نمی‌شود
    val sold = HeldStock.withOnline(local, ir.vil3ntec.tohid.core.stock.OnlineHolds.reserved(snap, 5_000L) { it == "o-A-1" })
    assertEquals(4.0, HeldStock.sellable(ShopStore.stock(d, "cola"), "cola", sold), 1e-9)
    //  پرچمِ خاموش ⇒ همان نگه‌داشته‌های صندوق، بایت‌به‌بایت
    assertEquals(local, HeldStock.withOnline(local, ir.vil3ntec.tohid.core.stock.OnlineHolds.reserved(null, 5_000L) { false }))

    //  هر دو صندوق از همان یک راه می‌خوانند (صفحه فقط CI)
    fun read(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val phone = read("ir/vil3ntec/tohid/ui/v3/sales/cart/SaleRoute.kt")
    assertTrue(phone.contains("val heldQty = ir.vil3ntec.tohid.ui.components.pos.rememberSellableHeld(heldBook, data)"))
    val ui = read("ir/vil3ntec/tohid/ui/components/pos/OnlineHoldsAndroid.kt")
    assertTrue(ui.contains("val local = remember(book) { HeldStock.reserved(book) }"))
    assertTrue(ui.contains("HeldStock.withOnline(local, OnlineHolds.reserved(snap, now) { it in sold })"))
    assertTrue(ui.contains("""d.saleItems.asSequence().map { it.id }.filter { it.startsWith("o-") }"""))
  }

  /** گذرا ⇒ آخرین نمای رزرو می‌ماند؛ ماندگار (نقش، کاتالوگِ خاموش، سرورِ پیشین، پاسخِ ناجور) ⇒ پاک ⇒ همان امروز */
  @Test fun `INV-03 خطای گذرا نمای رزرو را نگه می‌دارد و خطای ماندگار پاکش می‌کند`() {
    val keep = ir.vil3ntec.tohid.data.OnlineHoldsStore::keepsOnFailure
    assertTrue(keep(ApiFailure.Offline()))
    assertTrue(keep(ApiFailure.Timeout()))
    assertTrue(keep(ApiFailure.RateLimited("زیاد", "rate_limited")))
    assertTrue(keep(ApiFailure.ServerFault("خطا", "internal")))
    assertTrue(!keep(ApiFailure.Forbidden("نه", "permission_denied")))
    assertTrue(!keep(ApiFailure.Forbidden("نه", "catalog_disabled")))
    assertTrue(!keep(ApiFailure.NotFound("نیست", "not_found")))
    assertTrue(!keep(ApiFailure.InvalidResponse()))
    assertTrue(!keep(IllegalStateException("x")))
  }
}
