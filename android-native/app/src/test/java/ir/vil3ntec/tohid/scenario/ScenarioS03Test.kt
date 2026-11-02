package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.data.BarcodeMatch
import ir.vil3ntec.tohid.data.LinePolicy
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductInfoMap
import ir.vil3ntec.tohid.data.QuickRegister
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  S03 ⌨ — بارکدِ ناشناخته (POS-17)، روی صندوقِ گوشی (`CartModel.scan`، `SaleScreenLegacy`) و کامپیوتر (`PosSearch.resolve`،
 *  `DeskSaleScreen`). «ثبتِ سریع» پشتِ `TillGoodsPref.quickRegister` (پیش‌فرض خاموش): مجوز همان `canWrite(role, "products")` ِ
 *  سرور (`QuickRegister.allowed`)، پنجره در خودِ صندوق (Enter پس از بارکدِ ناشناس، Ctrl+S ثبت) و کالا با موجودیِ در دست
 *  همان‌جا به فاکتور. بخشِ Compose با آزمونِ JVM ساخته نمی‌شود، پس سیم‌کشی‌اش با متنِ همان پرونده‌ها سنجیده می‌شود.
 */
class ScenarioS03Test {

  private var seq = 0
  private fun newId() = "q${seq++}"

  private val rice = Product(id = "rice", name = "برنج", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("6260000000017"))
  private val d0 = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "rice", units = 20.0)))
  private val unknown = "6269999000042"

  private fun src(rel: String): String =
    listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test
  fun `S03 — بارکدِ ناشناس «ثبت نیست» است و همان کد برای ثبت می‌ماند، در گوشی و کامپیوتر`() {
    val index = ShopStore.barcodeIndex(d0)
    assertEquals(BarcodeMatch.Hit.None, BarcodeMatch.find(unknown, index))
    assertEquals(CartModel.Scan.Unknown(unknown), CartModel.scan(d0, index, emptyList(), unknown))
    assertEquals(PosHit.Unknown(unknown), PosSearch.resolve(unknown, index, d0.products, emptyList(), 0))
  }

  @Test
  fun `S03 — ثبتِ کالا فقط برای نقشی که کالا می‌نویسد، همان سنجشِ سرور`() {
    for (role in PermCore.ROLES) assertEquals(role, PermCore.canWrite(role, "products"), QuickRegister.allowed(role))
    assertFalse(QuickRegister.allowed("cashier"))
    assertFalse(QuickRegister.allowed("accountant"))
    assertTrue(QuickRegister.allowed("manager"))
    assertTrue(QuickRegister.allowed("storekeeper"))
    //  صاحب (نقشِ خالی) و نقشِ ناشناس مثلِ `RoleGate` باز؛ سرور خودش می‌سنجد
    assertTrue(QuickRegister.allowed(""))
    assertTrue(QuickRegister.allowed("future_role"))
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
    //  گزینهٔ «قلمِ آزاد» ِ پیامِ بارکدِ ناشناس فقط وقتی سقف بسته نیست
    assertTrue(QuickRegister.freeLineOpen(staff))
    assertFalse(QuickRegister.freeLineOpen(staff.copy(freeLineCapMinor = 0)))

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

  @Test
  fun `S03 — ثبتِ سریع در صندوق کالا را ثبت و همان‌جا به فاکتور می‌برد و فروش ثبت می‌شود`() {
    val r = QuickRegister.register(
      d0, unknown, QuickRegister.Draft(name = "زعفران", salePrice = "۲۵۰", unit = "گرم", stock = "3"), "2026-11-02", 2_000, ::newId,
    ) as QuickRegister.Result.Ok
    val made = r.data.products.single { it.id == r.productId }
    assertEquals(listOf(unknown), made.barcodes)
    assertEquals(250.0, made.salePrice, 0.0)
    assertEquals("گرم", made.unit)
    assertEquals(3.0, ShopStore.stock(r.data, made.id), 0.0)
    //  همان که صندوق می‌کند: کالای تازه با موجودیِ تازه به سبد
    val added = SalesEngine.addToCartCapped(emptyList(), r.productId, r.quantity, ProductInfoMap.sellable(r.data, r.productId))
    assertFalse(added.capped)
    assertEquals(listOf(SalesEngine.CartLine(r.productId, 1.0)), added.cart)
    val sold = SalesEngine.record(r.data, added.cart, SalesEngine.Checkout(), "2026-11-02", 3_000, ::newId) as SalesEngine.Result.Ok
    assertEquals(250.0, sold.data.sales.single().finalTotal, 0.0)
    assertEquals(2.0, ShopStore.stock(sold.data, made.id), 0.0)
    //  نام، قیمت، واحد و بارکدِ تکراری همان قاعده‌های فرمِ کالا
    assertTrue(QuickRegister.register(d0, unknown, QuickRegister.Draft(name = "زعفران", salePrice = ""), "2026-11-02", 2_000, ::newId) is QuickRegister.Result.Failed)
    assertTrue(QuickRegister.register(d0, "6260000000017", QuickRegister.Draft(name = "دیگر", salePrice = "10"), "2026-11-02", 2_000, ::newId) is QuickRegister.Result.Failed)
  }

  @Test
  fun `S03 — سیم‌کشیِ صندوقِ گوشی و کامپیوتر — پنجره در صندوق، Enter و Ctrl+S`() {
    val dialog = src("ir/vil3ntec/tohid/ui/components/pos/QuickRegisterDialog.kt")
    assertTrue(dialog.contains("e.isPrimaryModifier && e.key == Key.S"))
    val desk = src("ir/vil3ntec/tohid/ui/screens/DeskSale.kt")
    //  Enter پس از بارکدِ ناشناس پنجره را باز می‌کند؛ هر دو راهِ ناشناس (کادر و بارکدخوان) به ثبتِ سریع می‌روند
    assertTrue(desk.contains("if (quick?.enter(query) == true)"))
    assertEquals(2, Regex(Regex.escape("quick.unknown(")).findAll(desk).count())
    assertTrue(desk.contains("QuickTillDialog(quick, store, cartStore)"))
    val phone = src("ir/vil3ntec/tohid/ui/legacy/SaleLegacy.kt")
    assertTrue(phone.contains("if (quick != null) quick.unknown(code, scope, snackbar) { freeLine = true }"))
    assertTrue(phone.contains("QuickTillDialog(quick, store, cartStore)"))
    //  خاموش ⇒ همان راهِ پیشین (`onRegisterBarcode`) دست‌نخورده
    assertTrue(desk.contains("onRegisterBarcode(hit.code)") && phone.contains("onRegisterBarcode(code)"))
  }
}
