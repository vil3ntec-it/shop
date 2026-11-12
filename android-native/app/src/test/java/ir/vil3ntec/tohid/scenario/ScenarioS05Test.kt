package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.keymap.KeyMap
import ir.vil3ntec.tohid.core.keymap.KeyName
import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.core.product.UnitSale
import ir.vil3ntec.tohid.data.PackScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.UnitLine
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosAction
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosKeys
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.v3.sales.cart.LineModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S05 — فروش با واحدِ بزرگ (POS-09، PRD-07): «روغن» با واحدِ پایهٔ «دانه» و «کارتن = ۱۲ دانه»، دو کارتن.
 *  راه‌های امروز: برگهٔ ردیفِ گوشی با «واحدِ فروشِ دوم» (`LineModel.switchUnit`) و بارکدِ کارتن با «۲*» در کامپیوتر
 *  (PRD-04، `PackBarcodes`). پشتِ `TillGoodsPref.units` (پیش‌فرض خاموش): Ctrl+U و برگهٔ ردیفِ رابطِ امروز با واحدهای
 *  کالا (`UnitLine` ⇐ `UnitSale`)، قیمتِ کارتن از لیستِ قیمتِ کالا (`ProductPack.priceMinor`) یا ۱۲ × قیمتِ دانه، و
 *  «فروشِ بخشی از کارتن مجاز نیست» (`TillGoodsPref.wholePacks`).
 */
class ScenarioS05Test {

  private var seq = 0
  private fun newId() = "u${seq++}"

  private val oil = Product(
    id = "oil", name = "روغن", unit = "دانه", salePrice = 150.0, purchasePrice = 120.0,
    barcodes = listOf("6260000000101"), saleUnit = "کارتن", unitFactor = 12.0,
    packs = listOf(ProductPack("کارتن", 12_000, listOf("6260000000118"))),
  )
  //  همان روغن با قیمتِ کارتن در لیستِ قیمت: ۱٬۷۰۰ (نه ۱۲ × ۱۵۰ = ۱٬۸۰۰)
  private val listed = oil.copy(packs = listOf(ProductPack("کارتن", 12_000, listOf("6260000000118"), priceMinor = 170_000)))
  private val d = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 60.0)))

  private fun sell(data: ShopData, cart: List<SalesEngine.CartLine>): ShopData =
    (SalesEngine.record(data, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, ::newId) as SalesEngine.Result.Ok).data

  @Test
  fun `S05 — گوشی دو کارتن در برگهٔ ردیف ⇒ ۲۴ دانه از موجودی و قیمت ۱۲ × دانه`() {
    val line = SalesEngine.CartLine("oil", 1.0)
    val info = LineModel.info(d, line)
    val form = LineModel.switchUnit(LineModel.start(info), info, toSale = true).copy(qtyText = "2")
    assertEquals("کارتن", LineModel.shownUnit(form, info))
    val qty = LineModel.quantity(form, info)!!
    assertEquals(24.0, qty, 0.0)
    val cart = SalesEngine.setCartQty(listOf(line), "oil", qty)
    assertEquals(2 * 12 * 150.0, SalesEngine.cartTotal(d, cart), 0.0)
    val after = sell(d, cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3600.0, after.sales.single().finalTotal, 0.0)
  }

  @Test
  fun `S05 — کامپیوتر «۲ ضرب» و بارکدِ کارتن ⇒ همان ۲۴ دانه`() {
    val hit = PosSearch.resolve("۲*6260000000118", ShopStore.barcodeIndex(d), d.products, emptyList(), 0, ShopStore.packIndex(d))
    assertEquals(PosHit.Add(oil, 24.0), hit)
    val cart = SalesEngine.addToCart(emptyList(), "oil", 24.0)
    val after = sell(d, cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3600.0, after.sales.single().finalTotal, 0.0)
  }

  @Test
  fun `S05 — Ctrl+U واحدِ ردیف را به کارتن می‌برد و تعدادِ ۲ یعنی ۲۴ دانه`() {
    assertEquals(PosAction.UNIT, PosKeys.screen(KeyName.U, primary = true))
    assertNull(PosKeys.screen(KeyName.U, primary = false))
    //  همان کلیدِ `keymap.json`
    assertTrue(KeyMap.SALE_UNIT.keys.any { it.key == KeyName.U && it.mod })
    //  یک دانه اسکن شده؛ Ctrl+U ⇒ برگه با «کارتن» و تعدادِ ۱
    val plan = UnitLine.plan(oil, wholeOnly = false, quantity = 1.0, unitPrice = null, next = true)!!
    assertEquals(listOf("دانه", "کارتن"), plan.units.map { it.name })
    assertEquals(1, plan.startAt)
    assertEquals(1.0, plan.startCount, 0.0)
    val carton = plan.units[plan.startAt]
    assertEquals(24.0, UnitSale.toBase(2.0, carton, plan.wholeOnly)!!, 0.0)
    //  ردیفِ ۲۴ دانه دوباره که باز شود همان «۲ کارتن» است؛ Ctrl+U ِ دوباره ⇒ دانه
    val again = UnitLine.plan(oil, wholeOnly = false, quantity = 24.0, unitPrice = null, next = false)!!
    assertEquals(1, again.startAt)
    assertEquals(2.0, again.startCount, 0.0)
    assertEquals(0, UnitLine.plan(oil, wholeOnly = false, quantity = 24.0, unitPrice = null, next = true)!!.startAt)
    //  کالای تک‌واحد ⇒ همان برگهٔ پیشین
    assertNull(UnitLine.plan(oil.copy(saleUnit = "", unitFactor = 1.0, packs = null), wholeOnly = false, quantity = 1.0, unitPrice = null, next = true))
  }

  @Test
  fun `S05 — قیمتِ کارتن از لیستِ قیمت، یا اگر تعریف نشده ۱۲ × قیمتِ دانه`() {
    //  لیستِ قیمت: ۲ کارتن = ۳٬۴۰۰ و موجودی ۲۴ دانه کم
    val carton = UnitLine.units(listed)[1]
    assertEquals(170_000L, carton.priceMinor)
    assertEquals(170_000L, UnitSale.priceMinor(carton, 15_000))
    val withList = listed.let { p -> d.copy(products = listOf(p)) }
    val cart = SalesEngine.setLinePrice(SalesEngine.setCartQty(listOf(SalesEngine.CartLine("oil", 1.0)), "oil", UnitSale.toBase(2.0, carton, false)!!), "oil", UnitSale.lineUnitPrice(carton))
    assertEquals(3400.0, SalesEngine.cartTotal(withList, cart), 0.0)
    val after = sell(withList, cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3400.0, after.sales.single().finalTotal, 0.0)
    //  ردیفِ با قیمتِ لیست «دستی» شمرده نمی‌شود و همان کارتن باز می‌شود
    assertTrue(UnitLine.plan(listed, false, 24.0, UnitSale.lineUnitPrice(carton), next = false)!!.packPriced)
    //  بی قیمتِ لیست: ۱۲ × ۱۵۰ و ردیف قیمتِ خودِ کالا را نگه می‌دارد
    val plain = UnitLine.units(oil)[1]
    assertNull(plain.priceMinor)
    assertEquals(180_000L, UnitSale.priceMinor(plain, 15_000))
    assertNull(UnitSale.lineUnitPrice(plain))
    //  فرمِ کالا: قیمتِ کارتن اختیاری است و عددِ نادرست رد می‌شود
    val ok = PackBarcodes.check(listOf(PackBarcodes.Draft("کارتن", "12", "6260000000118", "۱٬۷۰۰")), "دانه", false, emptyList(), null) { null }
    assertEquals(170_000L, (ok as PackBarcodes.Checked.Ok).packs.single().priceMinor)
    val none = PackBarcodes.check(listOf(PackBarcodes.Draft("کارتن", "12", "6260000000118")), "دانه", false, emptyList(), null) { null }
    assertNull((none as PackBarcodes.Checked.Ok).packs.single().priceMinor)
    assertTrue(PackBarcodes.check(listOf(PackBarcodes.Draft("کارتن", "12", "6260000000118", "صفر")), "دانه", false, emptyList(), null) { null } is PackBarcodes.Checked.Failed)
    assertEquals("1700", PackBarcodes.draftOf(ok.packs.single()).price)
  }

  @Test
  fun `S05 — اگر فروشِ بخشی از کارتن مجاز نباشد، ۱٫۵ کارتن رد می‌شود`() {
    val carton = UnitLine.units(oil)[1]
    assertNull(UnitSale.toBase(1.5, carton, wholeOnly = true))
    assertTrue(UnitSale.partRejected(1.5, carton, wholeOnly = true))
    assertEquals(24.0, UnitSale.toBase(2.0, carton, wholeOnly = true)!!, 0.0)
    //  مجاز ⇒ ۱۸ دانه، همان امروز؛ واحدِ پایه هیچ‌وقت «بخشی از بسته» نیست
    assertEquals(18.0, UnitSale.toBase(1.5, carton, wholeOnly = false)!!, 0.0)
    assertFalse(UnitSale.partRejected(1.5, UnitLine.units(oil)[0], wholeOnly = true))
    assertTrue(UnitLine.plan(oil, wholeOnly = true, quantity = 1.0, unitPrice = null, next = true)!!.wholeOnly)
  }
  @Test
  fun `S05 — اسکنِ کارتن با «۲ ضرب» در گوشی و کامپیوتر قیمتِ کارتن را از لیست می‌گیرد`() {
    //  همان راهِ `CartModel.scan` (گوشی) و `DeskSale.addPack` (کامپیوتر) با «واحدِ بزرگ در صندوق»
    val withList = d.copy(products = listOf(listed))
    val r = PackScan.scan(withList, emptyList(), "6260000000118", 2.0, ShopStore.barcodeIndex(withList), listPrice = true, wholeOnly = false)
    val cart = (r as PackScan.Result.Added).added.cart
    assertTrue(r.priced)
    assertEquals(3400.0, SalesEngine.cartTotal(withList, cart), 1e-6)
    val after = sell(withList, cart)
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3400.0, after.sales.single().finalTotal, 1e-6)
    //  بی قیمتِ لیست ⇒ ۱۲ × قیمتِ دانه
    val plain = PackScan.scan(d, emptyList(), "6260000000118", 2.0, ShopStore.barcodeIndex(d), listPrice = true, wholeOnly = false) as PackScan.Result.Added
    assertEquals(3600.0, SalesEngine.cartTotal(d, plain.added.cart), 1e-6)
    //  فقط بستهٔ کامل ⇒ ۱٫۵ کارتن رد
    assertTrue(PackScan.scan(d, emptyList(), "6260000000118", 1.5, ShopStore.barcodeIndex(d), listPrice = false, wholeOnly = true) is PackScan.Result.PartRejected)
  }
}
