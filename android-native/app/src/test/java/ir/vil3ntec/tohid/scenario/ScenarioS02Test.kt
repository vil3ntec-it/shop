package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.hw.FieldScanGuard
import ir.vil3ntec.tohid.core.hw.KeyScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.scan.ScanKeys
import ir.vil3ntec.tohid.ui.components.pos.PosCatalog
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S02 ⌨ — اسکنِ تکراری و اسکنر با چیدمانِ فارسی (POS-02، HW-05، F0-06)، سر تا ته روی همان راهِ صندوقِ کامپیوتر:
 *  کلیدِ فیزیکی ⇐ `ScanKeys` (چیدمانِ فارسی/پشتو) ⇐ `KeyScan` (فاصلهٔ کلیدها) ⇐ `PosSearch.resolve` ⇐
 *  `SalesEngine.addToCartCapped`.
 *
 *  هر چهار سطرِ «نتیجه» این‌جا سنجیده می‌شود. نگهبانِ کادرِ عدد (`FieldScanGuard`) در برنامه پشتِ پرچمِ `hw_tools_v2` است.
 */
class ScenarioS02Test {

  private val a = Product(id = "A", name = "نوشابه", unit = "عدد", salePrice = 40.0, barcodes = listOf("5449000000996"))
  /** Code128 با حرف: کلیدِ Q در پشتو «ض» و C «ز» می‌سازد */
  private val c = Product(id = "C", name = "صابون", unit = "عدد", salePrice = 25.0, barcodes = listOf("QC4471"))
  private val tea = Product(id = "T", name = "چای", unit = "عدد", salePrice = 90.0, barcodes = listOf("12345678"))
  private val d = ShopData(
    products = listOf(a, c, tea),
    warehouseEntries = listOf(a, c, tea).map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = 50.0) },
  )
  private val index = ShopStore.barcodeIndex(d)

  /** نویسه‌ای که هر کلیدِ بارکدخوان با چیدمانِ فارسی/پشتو در کادر می‌گذارد، و آنچه `ScanKeys` از آن می‌خواند */
  private fun keys(code: String, letters: Map<Char, Char>): String = buildString {
    for (ch in code) {
      val got = when (ch) {
        in '0'..'9' -> ScanKeys.charOf(digit = ch - '0', letter = -1, shift = false, codePoint = ('۰' + (ch - '0')).code)
        in 'A'..'Z' -> ScanKeys.charOf(digit = -1, letter = ch - 'A', shift = true, codePoint = letters.getValue(ch).code)
        else -> ScanKeys.charOf(digit = -1, letter = -1, shift = false, codePoint = ch.code)
      }
      append(got ?: error("کلیدِ «$ch» گم شد"))
    }
  }

  /** اسکن: نویسه‌ها هر ۸ میلی‌ثانیه، Enter ِ پسوند، سپس همان Enter ِ کادرِ صندوق */
  private fun scan(cart: List<SalesEngine.CartLine>, code: String, letters: Map<Char, Char> = emptyMap()): List<SalesEngine.CartLine> {
    val k = KeyScan()
    var t = 50_000L
    keys(code, letters).forEach { ch -> k.feed(ch, t); t += 8 }
    val read = k.submit(t) ?: error("اسکن شناخته نشد")
    val hit = PosSearch.resolve(read, index, d.products, emptyList(), 0) as PosHit.Add
    return SalesEngine.addToCartCapped(cart, hit.product.id, hit.count, ShopStore.stock(d, hit.product.id)).cart
  }

  @Test
  fun `S02 — دو اسکنِ A با چیدمانِ فارسی یک ردیف با تعدادِ ۲ است، نه ردیفِ تکراری`() {
    assertEquals("5449000000996", keys("5449000000996", emptyMap()))
    val cart = scan(scan(emptyList(), "5449000000996"), "5449000000996")
    assertEquals(1, cart.size)
    assertEquals("A", cart.single().productId)
    assertEquals(2.0, cart.single().quantity, 0.0)
  }

  @Test
  fun `S02 — کالای C با چیدمانِ پشتو پیدا می‌شود`() {
    val pashto = mapOf('Q' to 'ض', 'C' to 'ز')
    assertEquals("QC4471", keys("QC4471", pashto))
    val cart = scan(emptyList(), "QC4471", pashto)
    assertEquals(listOf("C" to 1.0), cart.map { it.productId to it.quantity })
  }

  @Test
  fun `S02 — «۱۲۳۴» با رقمِ فارسی در کادرِ جست‌وجو بارکدِ لاتین را می‌یابد`() {
    assertEquals(listOf("T"), PosCatalog.matches(d.products, "۱۲۳۴").map { it.id })
    //  Enter با کدِ کامل به رقمِ فارسی همان کالا
    assertEquals(PosHit.Add(tea, 1.0), PosSearch.resolve("۱۲۳۴۵۶۷۸", index, d.products, emptyList(), 0))
  }

  @Test
  fun `S02 — اسکن در کادرِ قیمت یا تعداد نمی‌نشیند و به سبد می‌رود`() {
    val g = FieldScanGuard()
    var field = ""
    var t = 0L
    //  صندوقدار «۲۵۰» را در کادرِ قیمت آرام می‌نویسد
    for (ch in "250") { g.key(ch, t, field); field += ch; t += 300 }
    //  بارکدخوان در همان کادر اسکن می‌کند
    t += 2_000
    for (ch in "5449000000996") { g.key(ch, t, field); field += ch; t += 6 }
    val hit = g.enter(t) ?: error("اسکن در کادر شناخته نشد")
    assertEquals("250", hit.restore)
    assertEquals("5449000000996", hit.code)
    val added = PosSearch.resolve(hit.code, index, d.products, emptyList(), 0)
    assertTrue(added is PosHit.Add && added.product.id == "A")
  }
}
