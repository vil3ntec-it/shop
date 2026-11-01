package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.text.CatalogMatch
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.product.ProductInfoForm
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-03 (کدِ کالا، یکتا، با ساختِ خودکار) و PRD-09 (کالای خدماتی یا بی موجودی) در مدلِ دفتر، موتورِ
 *  انبار، جست‌وجو و ثبتِ فروش. کالای بی این دو همان JSON و همان سنجشِ موجودیِ پیشین را دارد.
 */
class ProductSkuMapTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val tea = Product(id = "p1", name = "چای", unit = "بسته", salePrice = 50.0)
  private val sew = Product(id = "p2", name = "دوختِ شلوار", unit = "دانه", salePrice = 300.0)
  private val base = ShopData(products = listOf(tea, sew))

  private fun rowOf(d: ShopData, id: String) =
    LedgerDiff.toJson(d)["products"]!!.jsonArray.first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject

  private fun ok(r: WarehouseEngine.Result) = r as WarehouseEngine.Result.Ok

  @Test
  fun `کالای بی کد و بی پرچمِ خدماتی همان JSON را دارد`() {
    val row = rowOf(base, "p1")
    assertFalse(row.containsKey("sku"))
    assertFalse(row.containsKey("stockless"))
    assertTrue(ProductInfoMap.hasNone(tea))
    assertEquals(tea, ProductInfoMap.apply(tea, ProductInfoForm().toInfo(emptyList(), null)))
    //  کالای عادی همان موجودی را سقف دارد
    assertEquals(ShopStore.stock(base, "p1"), ProductInfoMap.sellable(base, "p1"), 0.0)
  }

  @Test
  fun `کد ثبت می‌شود و کدِ کالای دیگر رد`() {
    val a = ok(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "شکر", salePrice = 1.0, info = ProductInfo(sku = "SKU-00001")), 0, ::newId))
    val sugar = a.data.products.first { it.name == "شکر" }
    assertEquals("SKU-00001", sugar.sku)
    assertEquals("SKU-00001", rowOf(a.data, sugar.id)["sku"]!!.jsonPrimitive.content)
    //  همان کد (با حروفِ کوچک و رقمِ فارسی) برای کالای دیگر ⇒ رد، با نامِ دارنده
    val dup = WarehouseEngine.addProduct(a.data, WarehouseEngine.ProductDraft(name = "نمک", salePrice = 1.0, info = ProductInfo.of(null, null, null, null, null, null, null, sku = "sku-۰۰۰۰۱")), 0, ::newId)
    assertTrue(dup is WarehouseEngine.Result.Failed && dup.message.contains("شکر"))
    //  کدِ خودکارِ بعدی
    assertEquals("SKU-00002", ir.vil3ntec.tohid.core.product.ProductSku.next(ProductInfoMap.skus(a.data).values))
    //  ویرایشِ خودِ کالا با همان کد آزاد است، و ردپا دارد
    val e = ok(WarehouseEngine.editProduct(a.data, "p1", WarehouseEngine.ProductDraft(name = "چای", unit = "بسته", salePrice = 50.0, info = ProductInfo(sku = "CH-1")), "2026-11-01", 0, ::newId))
    assertTrue(e.data.auditLog.last().notes.contains("کدِ کالا"))
    val again = WarehouseEngine.editProduct(e.data, sugar.id, WarehouseEngine.ProductDraft(name = "شکر", salePrice = 1.0, info = ProductInfo(sku = "SKU-00001")), "2026-11-01", 0, ::newId)
    assertTrue(again is WarehouseEngine.Result.Ok)
  }

  @Test
  fun `تعارضِ آفلاینِ کد ویرایشِ نام را قفل نمی‌کند`() {
    //  دو دستگاه یک کد ساختند و دفترها جمع شدند
    val d = base.copy(products = listOf(tea.copy(sku = "SKU-00001"), sew.copy(sku = "SKU-00001")))
    val r = WarehouseEngine.editProduct(d, "p1", WarehouseEngine.ProductDraft(name = "چای سبز", unit = "بسته", salePrice = 50.0, info = ProductInfoMap.of(d.products[0])), "2026-11-01", 0, ::newId)
    assertTrue(r is WarehouseEngine.Result.Ok)
    //  ولی کدِ تازه که مالِ دیگری است رد
    val r2 = WarehouseEngine.editProduct(d.copy(products = listOf(tea, sew.copy(sku = "X"))), "p1", WarehouseEngine.ProductDraft(name = "چای", unit = "بسته", salePrice = 50.0, info = ProductInfo(sku = "x")), "2026-11-01", 0, ::newId)
    assertTrue(r2 is WarehouseEngine.Result.Failed)
  }

  @Test
  fun `جست‌وجو کد را مثلِ بارکد پیدا می‌کند و فقط با تنظیمِ روشن`() {
    val products = listOf(tea.copy(sku = "CH-500"), sew)
    assertEquals(emptyList<Product>(), PosCatalog.matches(products, "ch-5"))
    assertEquals(listOf("p1"), PosCatalog.matches(products, "ch-5", extra = true).map { it.id })
    assertTrue(ProductListModel.matches(products[0], "CH-500", extra = true))
    assertEquals(listOf("p1"), CatalogMatch.search(PosCatalog.searchIndex(products, extra = true), "ch-500"))
    assertEquals(emptyList<String>(), CatalogMatch.search(PosCatalog.searchIndex(products), "ch-500"))
  }

  @Test
  fun `کالای خدماتی بی موجودی فروخته می‌شود و کالای عادی نه`() {
    val form = ProductInfoForm(stockless = true)
    val p = ProductInfoMap.apply(sew.copy(minStock = 5.0, reorderPoint = 6.0, maxStock = 10.0), form.toInfo(emptyList(), "p2"))
    assertEquals(true, p.stockless)
    //  حدِ موجودی ندارد ⇒ هشدار و پیشنهادِ سفارش هرگز
    assertEquals(0.0, p.minStock, 0.0)
    assertNull(p.reorderPoint)
    assertNull(p.maxStock)
    val d = base.copy(products = listOf(tea, p))
    assertTrue(rowOf(d, "p2")["stockless"]!!.jsonPrimitive.content == "true")
    assertEquals(ProductInfoMap.STOCKLESS, ProductInfoMap.sellable(d, "p2"), 0.0)
    assertEquals("ok", ShopStore.stockStatus(d, p))
    assertEquals(ProductListModel.Status.ACTIVE, ProductListModel.status(p, -3.0))

    val checkout = SalesEngine.Checkout()
    val sold = SalesEngine.record(d, listOf(SalesEngine.CartLine("p2", 2.0)), checkout, "2026-11-01", 1_700_000_000_000, ::newId)
    assertTrue(sold.toString(), sold is SalesEngine.Result.Ok)
    //  سبد سقف ندارد
    assertFalse(SalesEngine.addToCartCapped(emptyList(), "p2", 3.0, ProductInfoMap.sellable(d, "p2")).capped)
    //  کالای عادیِ بی موجودی همان رد ِ پیشین
    val tea0 = SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 1.0)), checkout, "2026-11-01", 1_700_000_000_000, ::newId)
    assertTrue(tea0 is SalesEngine.Result.Failed)
    assertTrue(SalesEngine.addToCartCapped(emptyList(), "p1", 1.0, ProductInfoMap.sellable(d, "p1")).capped)
  }

  @Test
  fun `فرم کد و پرچمِ خدماتی را برمی‌گرداند`() {
    val info = ProductInfo(sku = "SKU-00007", stockless = true)
    val form = ProductInfoForm.of(info)
    assertEquals("SKU-00007", form.sku)
    assertTrue(form.stockless)
    assertEquals(info, form.toInfo(emptyList(), null))
    assertEquals(info, ProductInfoMap.of(ProductInfoMap.apply(tea, info)))
  }
}
