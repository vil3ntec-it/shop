package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.SalesTestLedger.ledger
import ir.vil3ntec.tohid.ui.v3.sales.checkout.BalanceTone
import ir.vil3ntec.tohid.ui.v3.sales.checkout.CustomerPickerModel
import ir.vil3ntec.tohid.ui.v3.sales.draft.DraftModel
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickEmpty
import ir.vil3ntec.tohid.ui.v3.sales.quick.QuickSaleModel
import ir.vil3ntec.tohid.ui.v3.sales.scan.CameraAccess
import ir.vil3ntec.tohid.ui.v3.sales.scan.ScanModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** انتخابِ مشتری، فروشِ سریع، اسکن و فاکتورِ نیمه‌کاره */
class SalesPickersModelTest {

  /* ------------------------------ انتخابِ مشتری ------------------------------ */

  @Test
  fun `قرض داران به ترتیبِ دفتر با حالِ حساب`() {
    val rows = CustomerPickerModel.rows(ledger, "")
    assertEquals(listOf("d1", "d2", "d3"), rows.map { it.id })
    assertEquals(listOf(BalanceTone.OWES, BalanceTone.SETTLED, BalanceTone.CREDIT), rows.map { it.tone })
    assertEquals(1200.0, rows[0].balance, 0.0)
    assertTrue(CustomerPickerModel.label(1200.0).contains("1,200"))
    assertTrue(CustomerPickerModel.label(-300.0).contains("300"))
  }

  @Test
  fun `جست وجو در نام بی حساسیت به حروف`() {
    assertEquals(listOf("d3"), CustomerPickerModel.rows(ledger, " karim ").map { it.id })
    assertEquals(listOf("d1"), CustomerPickerModel.rows(ledger, "احمد").map { it.id })
    assertTrue(CustomerPickerModel.rows(ledger, "ناشناس").isEmpty())
    assertFalse(CustomerPickerModel.emptyText(ShopData()) == CustomerPickerModel.emptyText(ledger))
  }

  /* ------------------------------ فروشِ سریع ------------------------------ */

  @Test
  fun `فقط کالای فعال پرفروش ترین اول`() {
    val index = QuickSaleModel.index(ledger)
    val items = QuickSaleModel.items(ledger, index, "", null, mapOf("soap" to 2.0))
    assertEquals("tea", items.first().id)
    assertFalse(items.any { it.id == "old" })
    assertEquals(2.0, items.first { it.id == "soap" }.inCart, 0.0)
    assertTrue(items.first { it.id == "rice" }.weighed)
    assertEquals(3.0, items.first { it.id == "soap" }.stock, 0.0)
  }

  @Test
  fun `پیامِ خالیِ درست`() {
    val index = QuickSaleModel.index(ledger)
    assertEquals(QuickEmpty.NONE, QuickSaleModel.empty(index, 3, ""))
    assertEquals(QuickEmpty.NO_MATCH, QuickSaleModel.empty(index, 0, "zzz"))
    assertEquals(QuickEmpty.EMPTY_CATEGORY, QuickSaleModel.empty(index, 0, ""))
    assertEquals(QuickEmpty.NO_PRODUCTS, QuickSaleModel.empty(QuickSaleModel.index(ShopData()), 0, ""))
    assertNull(QuickSaleModel.keepCategory("رفته", listOf("نوشیدنی")))
    assertEquals("نوشیدنی", QuickSaleModel.keepCategory("نوشیدنی", listOf("نوشیدنی")))
  }

  /* ------------------------------ اسکن ------------------------------ */

  @Test
  fun `اجازهٔ دوربین سه حال دارد`() {
    assertEquals(CameraAccess.GRANTED, ScanModel.start(true))
    assertEquals(CameraAccess.ASK, ScanModel.start(false))
    assertEquals(CameraAccess.DENIED, ScanModel.afterAsk(false))
    assertEquals(CameraAccess.GRANTED, ScanModel.recheck(CameraAccess.DENIED, true))
    assertEquals(CameraAccess.DENIED, ScanModel.recheck(CameraAccess.DENIED, false))
    assertEquals("626001003", ScanModel.code(" 626001003\n"))
    assertNull(ScanModel.code("  "))
  }

  /* ------------------------------ فاکتورِ نیمه‌کاره ------------------------------ */

  @Test
  fun `متنِ فاکتورِ نیمه کاره`() {
    val plain = DraftModel.body(3, 1500.0, 0)
    assertTrue(plain.contains("3"))
    assertTrue(plain.contains("1,500"))
    val dropped = DraftModel.body(3, 1500.0, 2)
    assertTrue(dropped.length > plain.length)
  }
}
