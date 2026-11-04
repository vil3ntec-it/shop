package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.PackScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ProductPack
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.SalesEngine.CartLine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.cart.CartModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRD-04 و S05 در صندوقِ گوشیِ نسخهٔ ۳ (`CartModel.scan` ⇐ `PackScan`): بارکدِ کارتن پیش‌تر «ناشناس» بود (فقط
 *  `barcodeIndex` ِ کالاها خوانده می‌شد) و برگهٔ «ثبتِ سریع» را با کدِ کارتن باز می‌کرد. حالا اندازهٔ بسته به سبد می‌رود و با
 *  «واحدِ بزرگ در صندوق» قیمتِ لیستِ بسته؛ ردیفِ با قیمتِ دیگر بی‌صدا عوض نمی‌شود؛ قیمتِ لیستِ بسته تخفیف شمرده نمی‌شود.
 */
class PackScanTest {

  private val oil = Product(
    id = "oil", name = "روغن", unit = "دانه", salePrice = 150.0, purchasePrice = 120.0,
    barcodes = listOf("6260000000101"),
    packs = listOf(ProductPack("کارتن", 12_000, listOf("6260000000118"), priceMinor = 170_000), ProductPack("شل", 6_000, listOf("6260000000125"))),
  )
  private val tea = Product(id = "tea", name = "چای", unit = "بسته", salePrice = 90.0, barcodes = listOf("6260000000200"))
  private val d = ShopData(
    products = listOf(oil, tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "oil", units = 60.0), WarehouseEntry(id = "w2", productId = "tea", units = 5.0)),
  )
  private val index = ShopStore.barcodeIndex(d)
  private var seq = 0
  private fun newId() = "u${seq++}"

  private fun added(s: CartModel.Scan?): CartModel.Scan.Added = s as CartModel.Scan.Added

  @Test
  fun `بارکدِ کارتن در صندوقِ گوشی اندازهٔ بسته را به سبد می‌برد، نه ناشناس`() {
    val one = added(CartModel.scan(d, index, emptyList(), "6260000000118"))
    assertEquals(listOf(CartLine("oil", 12.0)), one.lines)
    assertEquals("کارتن", one.pack)
    assertEquals(1.0, one.packCount, 0.0)
    //  «۲×» ⇒ ۲۴ دانه؛ رقمِ فارسی هم (همان `PackBarcodes.Index`)
    val two = added(CartModel.scan(d, index, emptyList(), "۶۲۶۰۰۰۰۰۰۰۱۱۸", multiplier = 2.0))
    assertEquals(listOf(CartLine("oil", 24.0)), two.lines)
    //  بارکدِ خودِ کالا همان یک دانه
    assertEquals(listOf(CartLine("oil", 25.0)), added(CartModel.scan(d, index, two.lines, "6260000000101")).lines)
    //  پیام: «۲ کارتن «روغن» به سبد اضافه شد»
    I18n.source = { Lang.FA }
    val note = CartModel.scanNote(two)
    assertTrue(note, note.startsWith("2 کارتن") && note.contains("روغن"))
  }

  @Test
  fun `با واحدِ بزرگ قیمتِ کارتن از لیستِ قیمت می‌آید و ثبت ۲۴ دانه و ۳۴۰۰ است`() {
    val s = added(CartModel.scan(d, index, emptyList(), "6260000000118", multiplier = 2.0, packPrice = true))
    assertEquals(24.0, s.lines.single().quantity, 0.0)
    assertEquals(1700.0 / 12, s.lines.single().unitPrice!!, 1e-9)
    assertEquals(3400.0, SalesEngine.cartTotal(d, s.lines), 1e-6)
    //  همان بسته با همان قیمت جمع می‌شود
    val three = added(CartModel.scan(d, index, s.lines, "6260000000118", packPrice = true))
    assertEquals(36.0, three.lines.single().quantity, 0.0)
    assertEquals(5100.0, SalesEngine.cartTotal(d, three.lines), 1e-6)
    val after = (SalesEngine.record(d, s.lines, SalesEngine.Checkout(), "2026-11-04", 1_000, ::newId) as SalesEngine.Result.Ok).data
    assertEquals(36.0, ShopStore.stock(after, "oil"), 0.0)
    assertEquals(3400.0, after.sales.single().finalTotal, 1e-6)
    //  بسته‌ای که قیمتِ لیست ندارد (شل) ⇒ اندازه × قیمتِ دانه، بی قیمتِ ردیف
    val shell = added(CartModel.scan(d, index, emptyList(), "6260000000125", packPrice = true))
    assertNull(shell.lines.single().unitPrice)
    assertEquals(900.0, SalesEngine.cartTotal(d, shell.lines), 1e-6)
  }

  @Test
  fun `بی واحدِ بزرگ همان اندازه × قیمتِ دانه ِ پیشین`() {
    val s = added(CartModel.scan(d, index, emptyList(), "6260000000118", multiplier = 2.0))
    assertNull(s.lines.single().unitPrice)
    assertEquals(3600.0, SalesEngine.cartTotal(d, s.lines), 1e-6)
  }

  @Test
  fun `ردیفِ با قیمتِ دیگر بی‌صدا عوض نمی‌شود و چیزی افزوده نمی‌شود`() {
    //  یک دانه به قیمتِ خودِ کالا در سبد ⇒ کارتن با قیمتِ لیست جمع نمی‌شود
    val single = listOf(CartLine("oil", 1.0))
    assertEquals(CartModel.Scan.PackClash(oil, "کارتن"), CartModel.scan(d, index, single, "6260000000118", packPrice = true))
    //  قیمتِ دستیِ دیگر هم همین
    val manual = listOf(CartLine("oil", 12.0, unitPrice = 140.0))
    assertEquals(CartModel.Scan.PackClash(oil, "کارتن"), CartModel.scan(d, index, manual, "6260000000118", packPrice = true))
    //  بی قیمتِ لیست (تنظیمِ خاموش) همان جمعِ پیشین با قیمتِ ردیف
    assertEquals(listOf(CartLine("oil", 13.0)), added(CartModel.scan(d, index, single, "6260000000118")).lines)
    I18n.source = { Lang.FA }
    assertTrue(ir.vil3ntec.tohid.core.i18n.StrSale.packPriceClash("روغن", "کارتن").contains("به سبد نرفت"))
    //  کامپیوتر همان جمله را از `PackScan.refusal` می‌گیرد
    val carton = ShopStore.coreOf(oil.packs!!.first())
    assertEquals(ir.vil3ntec.tohid.core.i18n.StrSale.packPriceClash("روغن", "کارتن"), PackScan.refusal(PackScan.Result.Clash(oil, carton)))
    assertTrue(PackScan.refusal(PackScan.Result.Ambiguous("6260000000118")).contains("6260000000118"))
  }

  @Test
  fun `بارکدِ مبهم، کالای غیرفعال و بخشی از بسته هیچ نمی‌افزایند`() {
    //  تعارضِ آفلاین: کدِ کارتنِ روغن بارکدِ کالای چای هم شد
    val clash = d.copy(products = listOf(oil, tea.copy(barcodes = listOf("6260000000118"))))
    assertEquals(CartModel.Scan.Ambiguous("6260000000118"), CartModel.scan(clash, ShopStore.barcodeIndex(clash), emptyList(), "6260000000118"))
    val off = d.copy(products = listOf(oil.copy(active = false), tea))
    assertEquals(CartModel.Scan.Inactive(oil.copy(active = false)), CartModel.scan(off, index, emptyList(), "6260000000118"))
    //  «فقط بستهٔ کامل»: ۱٫۵ کارتن رد
    assertEquals(CartModel.Scan.PackPart("کارتن"), CartModel.scan(d, index, emptyList(), "6260000000118", multiplier = 1.5, wholeOnly = true))
    assertEquals(18.0, added(CartModel.scan(d, index, emptyList(), "6260000000118", multiplier = 1.5)).lines.single().quantity, 0.0)
  }

  @Test
  fun `تا موجودی و نه بیشتر`() {
    val s = added(CartModel.scan(d, index, emptyList(), "6260000000118", multiplier = 6.0, packPrice = true))
    assertTrue(s.capped)
    assertEquals(60.0, s.lines.single().quantity, 0.0)
  }

  @Test
  fun `قیمتِ لیستِ بسته برای مضربِ درستِ بسته تخفیفِ ردیف نیست`() {
    val carton = PackScan.linePrice(ShopStore.coreOf(oil.packs!!.first()))!!
    //  ۲۴ دانه به نرخِ کارتن ⇒ قیمتِ لیست، کاهش صفر
    assertEquals(0L, DiscountGate.reductionMinor(d, listOf(CartLine("oil", 24.0, unitPrice = carton))))
    //  ۱۳ دانه به نرخِ کارتن ⇒ یک دانه زیرِ قیمت، هنوز کاهش است
    assertTrue(DiscountGate.reductionMinor(d, listOf(CartLine("oil", 13.0, unitPrice = carton))) > 0)
    //  قیمتِ دستیِ زیرِ فهرست همان کاهشِ پیشین
    assertEquals(12 * 1000L, DiscountGate.reductionMinor(d, listOf(CartLine("oil", 12.0, unitPrice = 140.0))))
  }

  @Test
  fun `صندوقِ گوشی و کامپیوتر هر دو از همان PackScan می‌گذرند`() {
    fun src(rel: String) = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid/$rel") }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    val phone = src("ui/v3/sales/cart/CartFeedback.kt")
    assertTrue(phone.contains("val packPrice = TillGoodsPref.units(context)"))
    assertTrue(phone.contains("CartModel.scan(d, index, cart.lines.value, code, multiplier, packPrice, wholeOnly)"))
    val desk = src("ui/screens/DeskSale.kt")
    //  اسکنِ بارکدخوان و Enter با «۲*» ِ کادر
    assertTrue(desk.contains("if (addPack(code, 1.0, dd, items)) return true"))
    assertTrue(desk.contains("if (addPack(parsed.second, parsed.first, d, index)) { query = \"\"; return }"))
    assertTrue(desk.contains("ir.vil3ntec.tohid.data.TillGoodsPref.units(context), ir.vil3ntec.tohid.data.TillGoodsPref.wholePacks(context)"))
  }
}
