package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.PriceSchedule
import ir.vil3ntec.tohid.core.product.ProductInfo
import ir.vil3ntec.tohid.core.product.ProductKit
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.ui.components.product.ProductInfoForm
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-10 (کیت: موجودیِ ساختگی از اجزا، فروش اجزا را کم می‌کند) و PRD-13 (قیمتِ زمان‌بندی‌شده) در دفتر،
 *  ثبتِ فروش و برگشت. کالای بی این دو همان JSON و همان قیمتِ پیشین را دارد.
 */
class ProductPlansMapTest {

  private var seq = 0
  private fun newId(): String = "i${seq++}"

  private val oil = Product(id = "oil", name = "روغن", unit = "دانه", purchasePrice = 100.0, salePrice = 130.0)
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", purchasePrice = 20.0, salePrice = 30.0)
  private val basket = Product(id = "kit", name = "سبدِ رمضان", unit = "دانه", salePrice = 400.0, kit = listOf(KitPart("oil", 2.0), KitPart("rice", 5.0)))
  private val base = ShopData(
    products = listOf(oil, rice, basket),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 7.0), WarehouseEntry(id = "w2", productId = "rice", units = 26.5)),
  )
  private val today = ir.vil3ntec.tohid.todayIso()

  private fun row(d: ShopData, table: String, id: String) =
    LedgerDiff.toJson(d)[table]!!.jsonArray.first { it.jsonObject["id"]!!.jsonPrimitive.content == id }.jsonObject

  private fun sell(d: ShopData, vararg lines: Pair<String, Double>) =
    SalesEngine.record(d, lines.map { SalesEngine.CartLine(it.first, it.second) }, SalesEngine.Checkout(), today, 1_700_000_000_000, ::newId)

  @Test
  fun `کالای عادی همان JSON و همان قیمت را دارد`() {
    val r = row(base, "products", "oil")
    assertFalse(r.containsKey("kit"))
    assertFalse(r.containsKey("priceWindows"))
    assertEquals(130.0, ProductPlansMap.price(oil), 0.0)
    assertEquals(listOf("oil" to 3.0), ProductPlansMap.moves(oil, 3.0))
    val ok = sell(base, "oil" to 1.0) as SalesEngine.Result.Ok
    assertFalse(row(ok.data, "saleItems", ok.data.saleItems.single().id).containsKey("parts"))
  }

  @Test
  fun `موجودیِ کیت کمیاب‌ترین جزء است و فروشش اجزا را کم می‌کند`() {
    //  روغن ۷ ÷ ۲ = ۳؛ برنج ۲۶٫۵ ÷ ۵ = ۵
    assertEquals(3.0, ShopStore.stock(base, "kit"), 0.0)
    val ok = sell(base, "kit" to 2.0) as SalesEngine.Result.Ok
    assertEquals(3.0, ShopStore.stock(ok.data, "oil"), 1e-9)
    assertEquals(16.5, ShopStore.stock(ok.data, "rice"), 1e-9)
    assertEquals(1.0, ShopStore.stock(ok.data, "kit"), 0.0)
    val item = ok.data.saleItems.single()
    assertEquals(basket.kit, item.parts)
    //  بهای قلم = ۲ × ۱۰۰ + ۵ × ۲۰
    assertEquals(300.0, item.purchasePrice, 1e-9)
    assertEquals(400.0, item.unitPrice, 0.0)
    assertEquals(setOf("oil" to -4.0, "rice" to -10.0), ok.data.stockMovements.map { it.productId to it.qty }.toSet())
    //  تغییرِ بعدیِ ترکیب فاکتورِ گذشته را عوض نمی‌کند
    val changed = ok.data.copy(products = ok.data.products.map { if (it.id == "kit") it.copy(kit = listOf(KitPart("oil", 1.0))) else it })
    assertEquals(3.0, ShopStore.stock(changed, "oil"), 1e-9)
  }

  @Test
  fun `نیازِ همهٔ سبد به جزء سنجیده می‌شود و بیش از موجودی رد`() {
    //  ۲ کیت (۴ روغن) + ۴ روغنِ جدا = ۸ > ۷
    val r = sell(base, "kit" to 2.0, "oil" to 4.0)
    assertTrue(r is SalesEngine.Result.Failed && r.message.contains("روغن"))
    //  کیتِ بیش از کیتِ کاملِ موجود
    assertTrue(sell(base, "kit" to 4.0) is SalesEngine.Result.Failed)
    assertTrue(sell(base, "kit" to 2.0, "oil" to 3.0) is SalesEngine.Result.Ok)
  }

  @Test
  fun `برگشت و اصلاحِ انبارِ کیت به اجزا می‌رسد`() {
    val ok = sell(base, "kit" to 2.0) as SalesEngine.Result.Ok
    val item = ok.data.saleItems.single()
    val back = SalesEngine.recordReturn(ok.data, ok.saleId, mapOf(item.id to 1.0), "عوض", today, 1, ::newId) as SalesEngine.Result.Ok
    assertEquals(5.0, ShopStore.stock(back.data, "oil"), 1e-9)
    //  ضایعاتِ برگشتی (ردیفِ اصلاحیِ منفیِ خودِ کیت) اجزا را کم می‌کند
    val damaged = back.data.copy(warehouseEntries = back.data.warehouseEntries + WarehouseEntry(id = "w9", productId = "kit", units = -1.0, isAdjustment = true))
    assertEquals(3.0, ShopStore.stock(damaged, "oil"), 1e-9)
    assertEquals(16.5, ShopStore.stock(damaged, "rice"), 1e-9)
  }

  @Test
  fun `کیتِ تو در تو و جزءِ خود رد می‌شود`() {
    val nested = ProductInfo(kit = listOf(ProductKit.Part("kit", 1000)))
    assertNotNull(ProductInfoMap.kitProblem(base, nested, "new"))
    assertNotNull(ProductInfoMap.kitProblem(base, ProductInfo(kit = listOf(ProductKit.Part("oil", 1000))), "oil"))
    //  روغن جزءِ سبد است ⇒ خودش کیت نمی‌شود
    assertNotNull(ProductInfoMap.kitProblem(base, ProductInfo(kit = listOf(ProductKit.Part("rice", 1000))), "oil"))
    val r = WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "سبدِ دو", salePrice = 1.0, info = nested), 0, ::newId)
    assertTrue(r is WarehouseEngine.Result.Failed)
  }

  @Test
  fun `قیمتِ زمان‌بندی‌شده در بازه قیمتِ فهرست است`() {
    val now = oil.copy(priceWindows = listOf(PriceWindow(today, today, 99.5)))
    val past = oil.copy(priceWindows = listOf(PriceWindow("2000-01-01", "2000-01-31", 1.0)))
    val d = base.copy(products = listOf(now, rice, basket))
    assertEquals(99.5, ProductPlansMap.price(now), 0.0)
    assertEquals(130.0, ProductPlansMap.price(past), 0.0)
    assertEquals(99.5, SalesEngine.linePrice(d, SalesEngine.CartLine("oil", 1.0)), 0.0)
    val ok = sell(d, "oil" to 2.0) as SalesEngine.Result.Ok
    assertEquals(99.5, ok.data.saleItems.single().unitPrice, 0.0)
    assertEquals(199.0, ok.data.sales.single().finalTotal, 1e-9)
    //  قیمتِ زمان‌بندی‌شده «پایین آوردنِ دستیِ قیمت» شمرده نمی‌شود
    assertEquals(0L, DiscountGate.reductionMinor(d, listOf(SalesEngine.CartLine("oil", 1.0, unitPrice = 99.5))))
  }

  @Test
  fun `فرم کیت و بازه‌ها را می‌خواند و ویرایش‌نشده دست نمی‌خورد`() {
    val info = ProductInfoForm(kitText = "روغن × 2\nبرنج × ۵", pricesText = "$today تا $today = 450")
      .toInfo(ProductInfoMap.refs(base), "new")
    assertEquals(listOf(ProductKit.Part("oil", 2000), ProductKit.Part("rice", 5000)), info.kit)
    assertEquals(listOf(PriceSchedule.Window(today, today, 45000)), info.prices)
    val p = ProductInfoMap.apply(Product(id = "new", name = "سبد"), info)
    assertEquals(listOf(KitPart("oil", 2.0), KitPart("rice", 5.0)), p.kit)
    assertEquals(listOf(PriceWindow(today, today, 450.0)), p.priceWindows)
    assertEquals(info, ProductInfoForm.of(ProductInfoMap.of(p)).toInfo(emptyList(), "new"))
  }
}
