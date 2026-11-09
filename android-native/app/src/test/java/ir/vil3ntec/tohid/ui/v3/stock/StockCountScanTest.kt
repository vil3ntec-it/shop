package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.i18n.StrCount
import ir.vil3ntec.tohid.core.stock.CountScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  «انبارگردانی» (`stock/count`) و بارکدخوان: در کامپیوتر بی کادرِ فوکوس‌دار، هجومِ بارکدخوان به `ScanRouter.dispatch` ِ
 *  `Main.kt` می‌رسید و چون این صفحه گیرنده نداشت صندوق باز می‌شد (`AppNav.go("sale")`) — شمارشِ ذخیره‌نشده (`remember`)
 *  گم می‌شد. حالا صفحه گیرنده دارد: «ابزارهای تازهٔ دستگاه» روشن ⇒ +۱ به همان کالا؛ خاموش ⇒ هیچ عددی عوض نمی‌شود و فقط
 *  پیام (امن‌تر از امروز: نه صفحه بسته می‌شود، نه سبدِ صندوق چیزی می‌گیرد).
 */
class StockCountScanTest {

  private val soap = Product(id = "soap", name = "صابون", unit = "دانه", barcodes = listOf("6260001"))
  private val rice = Product(id = "rice", name = "برنج", unit = "کیلو", barcodes = listOf("6260002"))
  private val old = Product(id = "old", name = "کهنه", unit = "دانه", barcodes = listOf("6260003"), active = false)
  private val d = ShopData(products = listOf(soap, rice, old))
  private val index = ShopStore.barcodeIndex(d)

  @Test
  fun `روشن — هر اسکن یک عدد به همان کالا و بقیهٔ نوشته‌ها دست نمی‌خورند`() {
    val start = CountState(counts = mapOf("soap" to "۱۰", "rice" to "2.5"), note = "قفسهٔ دوم")
    val (once, r) = start.scan(d, "6260001", index, counting = true)
    assertEquals(CountScan.Result.Counted("soap", 11_000), r)
    assertEquals(mapOf("soap" to "11", "rice" to "2.5"), once.counts)
    assertEquals("قفسهٔ دوم", once.note)
    assertEquals(StrCount.scanned("صابون", "11"), CountState.scanMessage(r, d))
    //  رقمِ فارسی و فاصلهٔ دو سرِ کد (بارکدخوانِ چیدمانِ فارسی)
    assertEquals("12", once.scan(d, " ۶۲۶۰۰۰۱ ", index, counting = true).first.counts["soap"])
    //  خانهٔ خالی ⇒ ۱؛ همان که ثبت می‌شود
    val fresh = CountState().scan(d, "6260001", index, counting = true).first
    assertEquals(mapOf("soap" to 1.0), fresh.valid)
    assertTrue(fresh.dirty)
  }

  @Test
  fun `روشن — وزنی، غیرفعال و ناشناس هیچ عددی را عوض نمی‌کنند و پیامِ روشن دارند`() {
    val start = CountState(counts = mapOf("rice" to "2.5"))
    val cases = listOf(
      "6260002" to CountScan.Result.Weighed("rice"),
      "6260003" to CountScan.Result.Inactive("old"),
      "999" to CountScan.Result.Unknown("999"),
    )
    for ((code, want) in cases) {
      val (next, r) = start.scan(d, code, index, counting = true)
      assertEquals(code, want, r)
      assertEquals(code, start, next)
    }
    assertEquals(StrCount.weighed("برنج"), CountState.scanMessage(CountScan.Result.Weighed("rice"), d))
    assertEquals(StrCount.inactive("کهنه"), CountState.scanMessage(CountScan.Result.Inactive("old"), d))
    assertEquals(StrCount.unknown("999"), CountState.scanMessage(CountScan.Result.Unknown("999"), d))
  }

  @Test
  fun `خاموش — اسکن برداشته می‌شود ولی هیچ عددی عوض نمی‌شود`() {
    val start = CountState(counts = mapOf("soap" to "4"), note = "یادداشت")
    val (next, r) = start.scan(d, "6260001", index, counting = false)
    assertSame(start, next)
    assertNull(r)
    assertEquals(StrCount.scanOff, CountState.scanMessage(null, d))
  }

  @Test
  fun `سیم‌کشی — صفحه تا باز است گیرنده دارد و کد را همیشه برمی‌دارد، پس صندوق باز نمی‌شود`() {
    val rel = "ir/vil3ntec/tohid/ui/v3/stock/StockCountScreen.kt"
    val s = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")
    val reg = s.indexOf("ir.vil3ntec.tohid.scan.ScanRouter.register(scanOwner) { code -> onScan(code); true }")
    assertTrue("گیرنده", reg > 0)
    assertTrue("برداشتن با بسته شدنِ صفحه", s.indexOf("onDispose { ir.vil3ntec.tohid.scan.ScanRouter.unregister(scanOwner) }") > reg)
    //  شمردن فقط با «ابزارهای تازهٔ دستگاه»، با همان دادهٔ تازه (`rememberUpdatedState`) و لحظهٔ اسکن (INV-10)
    assertTrue(s.contains("state.scan(data, code, index, ir.vil3ntec.tohid.print.HwTools.on.value)"))
    assertTrue(s.contains("if (r is CountScan.Result.Counted) { state = next; at = at + (r.productId to clock()); failure = null }"))
    assertTrue(s.contains("rememberUpdatedState<(String) -> Unit>"))
  }
}
