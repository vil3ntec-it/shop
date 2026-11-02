package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.ui.v3.nav.V3Modules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** کارهای افزودهٔ «انبار» (`StockExtras`): همان دروازه‌های صفحهٔ انبارِ پیشین، و هر کدام به مسیرِ خودش */
class StockExtrasTest {
  private fun flags(
    places: Boolean = false, repack: Boolean = false, staff: Boolean = false, stockOn: Boolean = false, labelTools: Boolean = false,
  ) = StockExtras.Flags(places, repack, staff, stockOn, labelTools)

  @Test
  fun `همه خاموش ⇒ فقط ثبتِ چند کالا و ورودِ عکس`() {
    assertEquals(listOf(StockExtra.BULK_PRODUCTS, StockExtra.IMAGES), StockExtras.of(flags()))
  }

  @Test
  fun `محل‌ها با موتورِ تازه همان دفترِ موجودی است و بسته‌بندی می‌ماند`() {
    assertEquals(StockExtra.PLACES, StockExtras.of(flags(places = true)).first())
    assertEquals(false, StockExtra.PLACES in StockExtras.of(flags(places = true, stockOn = true)))
    assertEquals(true, StockExtra.PLACES in StockExtras.of(flags(places = true, repack = true, stockOn = true)))
    assertEquals(true, StockExtra.BOOK in StockExtras.of(flags(stockOn = true)))
  }

  @Test
  fun `صندوق‌دار نه محل‌ها و نه ورودِ گروهیِ عکس`() {
    val x = StockExtras.of(flags(places = true, repack = true, staff = true, labelTools = true))
    assertEquals(listOf(StockExtra.LABELS, StockExtra.BULK_PRODUCTS), x)
  }

  @Test
  fun `انبارگردانی — موتورِ تازه، ابزارِ تازه یا برگهٔ ساده`() {
    assertEquals("stock/book?tab=count", StockExtras.countRoute(stockOn = true, countTools = true))
    assertEquals(StockPaths.COUNT_TOOLS, StockExtras.countRoute(stockOn = false, countTools = true))
    assertEquals(StockPaths.COUNT, StockExtras.countRoute(stockOn = false, countTools = false))
    assertNull(StockExtras.bookTab("nope"))
    assertEquals(StockPaths.BOOK, StockPaths.book())
  }

  @Test
  fun `هر کار به مسیرِ خودش در جدول می‌رسد`() {
    val table = V3Modules.table
    (StockExtra.entries.map { it.route } + listOf(StockPaths.COUNT_TOOLS, StockPaths.IMPORT_HISTORY, StockPaths.book("ledger"), "stockbook"))
      .forEach { path -> assertEquals(path, table.canonical(path).substringBefore('?'), table.resolve(path)?.route?.pattern) }
  }
}
