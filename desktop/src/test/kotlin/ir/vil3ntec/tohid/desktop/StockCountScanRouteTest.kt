package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.stock.CountScan
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.scan.ScanRouter
import ir.vil3ntec.tohid.ui.v3.stock.CountState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 *  پنجرهٔ کامپیوتر بی کادرِ فوکوس‌دار: `Main.kt` هجومِ بارکدخوان را به `ScanRouter.dispatch(code) { AppNav.go("sale") }` می‌دهد.
 *  با گیرندهٔ «انبارگردانی» (همان که `StockCountScreen` ثبت می‌کند، `StockCountScanTest` سیم‌کشی‌اش را می‌سنجد) صندوق باز
 *  نمی‌شود و شمارشِ ذخیره‌نشده سرِ جایش می‌ماند — «ابزارهای تازهٔ دستگاه» روشن ⇒ +۱، خاموش ⇒ همان عدد.
 */
class StockCountScanRouteTest {

  @Test fun `اسکن روی انبارگردانی به صندوق نمی‌رود و شمارش گم نمی‌شود`() {
    val d = ShopData(products = listOf(Product(id = "soap", name = "صابون", unit = "دانه", barcodes = listOf("6260001"))))
    val index = ShopStore.barcodeIndex(d)
    for (counting in listOf(true, false)) {
      var state = CountState(counts = mapOf("soap" to "3"), note = "قفسهٔ دوم")
      val owner = Any()
      ScanRouter.register(owner) { code ->
        val (next, r) = state.scan(d, code, index, counting)
        if (r is CountScan.Result.Counted) state = next
        true
      }
      try {
        var opened = false
        assertEquals("page", ScanRouter.dispatch("6260001") { opened = true })
        assertFalse("صندوق باز نشد", opened)
        assertEquals(if (counting) "4" else "3", state.counts["soap"])
        assertEquals("قفسهٔ دوم", state.note)
      } finally {
        ScanRouter.unregister(owner)
      }
    }
  }
}
