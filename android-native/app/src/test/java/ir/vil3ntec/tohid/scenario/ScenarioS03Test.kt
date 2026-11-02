package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.LinePolicy
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S03 ⌨ — بارکدِ ناشناخته (POS-17)، روی صندوقِ گوشی (`CartModel.scan`) و کامپیوتر (`PosSearch.resolve`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • «ثبتِ کالا» با همان بارکد مجوز نمی‌سنجد: صندوقدارِ بی‌مجوز هم آن را می‌بیند (کلیدِ مجوزِ ثبتِ کالا در PermCore نیست).
 *   • پنجرهٔ «ثبتِ سریعِ کالا» با Enter و Ctrl+S در خودِ صندوق نیست: گوشی به صفحهٔ «کالای تازه» می‌رود و کامپیوتر به
 *     `onRegisterBarcode`؛ کالای ثبت‌شده خودکار به فاکتور برنمی‌گردد و چون موجودی ندارد اسکنِ دوباره هم آن را به سبد نمی‌برد.
 */
class ScenarioS03Test {

  private var seq = 0
  private fun newId() = "q${seq++}"

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("6260000000017"))
  private val d0 = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "rice", units = 20.0)))
  private val unknown = "6269999000042"

  @Test
  fun `S03 — بارکدِ ناشناس «ثبت نیست» است و همان کد برای ثبت می‌ماند، در گوشی و کامپیوتر`() {
    val index = ShopStore.barcodeIndex(d0)
    assertEquals(BarcodeMatch.Hit.None, BarcodeMatch.find(unknown, index))
    assertEquals(CartModel.Scan.Unknown(unknown), CartModel.scan(d0, index, emptyList(), unknown))
    assertEquals(PosHit.Unknown(unknown), PosSearch.resolve(unknown, index, d0.products, emptyList(), 0))
  }

  @Test
  fun `S03 — صندوقدار فقط قلمِ آزاد، و فقط اگر مجاز باشد`() {
    //  سقفِ قلمِ آزاد ۵۰۰ افغانی برای شاگرد؛ صفر ⇒ بسته؛ صاحب همیشه آزاد
    val staff = LinePolicy.policy(on = true, role = ShopRole.STAFF, direction = LinePricing.Direction.BOTH, freeLineCapMinor = 50_000, mayDiscount = false)
    assertNull(LinePricing.checkFreeLine(staff, 30_000))
    assertEquals(LinePricing.FREE_LINE_OVER_CAP, LinePricing.checkFreeLine(staff, 60_000))
    assertEquals(LinePricing.FREE_LINE_CLOSED, LinePricing.checkFreeLine(staff.copy(freeLineCapMinor = 0), 30_000))
    val owner = LinePolicy.policy(on = true, role = ShopRole.OWNER, direction = LinePricing.Direction.BOTH, freeLineCapMinor = 0, mayDiscount = true)
    assertNull(LinePricing.checkFreeLine(owner, 60_000))

    val cart = SalesEngine.addFreeLine(emptyList(), "کیسه", 300.0, 1.0, ::newId)
    val sold = SalesEngine.record(d0, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, ::newId) as SalesEngine.Result.Ok
    assertEquals(300.0, sold.data.sales.single().finalTotal, 0.0)
  }

  @Test
  fun `S03 — مدیر کالا را با همان بارکد، نام، قیمت و واحد ثبت می‌کند، بی شبکه`() {
    val r = WarehouseEngine.addProduct(
      d0, WarehouseEngine.ProductDraft(name = "زعفران", unit = "گرم", salePrice = 250.0, barcode = unknown), 2_000, ::newId,
    ) as WarehouseEngine.Result.Ok
    //  فقط دفترِ همین دستگاه نوشته شد — همان راهِ آفلاین
    val made = r.data.products.single { it.name == "زعفران" }
    assertEquals(listOf(unknown), made.barcodes)
    assertEquals(250.0, made.salePrice, 0.0)
    assertEquals("گرم", made.unit)
    val hit = BarcodeMatch.find(unknown, ShopStore.barcodeIndex(r.data))
    assertTrue(hit is BarcodeMatch.Hit.Product && hit.id == made.id && hit.exact)
  }
}
