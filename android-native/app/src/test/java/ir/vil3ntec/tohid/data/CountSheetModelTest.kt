package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.core.stock.CountScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S20 روی دفترِ دکان: اسکنِ پیاپی (INV-06)، دو دستگاه هم‌زمان با برگه (INV-07)، و ثبتِ همان
 *  «اصلاحِ موجودی» ِ امروز برای جمعِ دو برگه — فقط کالاهای شمرده.
 */
class CountSheetModelTest {

  private var seq = 0
  private fun newId(): String = "id${seq++}"

  private val d = ShopData(
    products = listOf(
      Product(id = "cola", name = "نوشابه", unit = "عدد", purchasePrice = 30.0, barcodes = listOf("6260000000017")),
      Product(id = "juice", name = "آب‌میوه", unit = "عدد", purchasePrice = 40.0, barcodes = listOf("111")),
      Product(id = "rice", name = "برنج", unit = "کیلوگرم", purchasePrice = 100.0, barcodes = listOf("222")),
      Product(id = "water", name = "آب", unit = "عدد", purchasePrice = 10.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "cola", units = 10.0),
      WarehouseEntry(id = "w2", productId = "juice", units = 6.0),
      WarehouseEntry(id = "w3", productId = "rice", units = 5.0),
      WarehouseEntry(id = "w4", productId = "water", units = 20.0),
    ),
  )
  private val index = ShopStore.barcodeIndex(d)

  @Test
  fun `هر اسکن یک عدد؛ وزنی و ناشناس دست نمی‌خورند`() {
    var texts = mapOf<String, String>()
    repeat(3) { texts = CountSheetModel.scan(texts, "6260000000017", d, index).first }
    assertEquals("3", texts["cola"])
    val (t2, r) = CountSheetModel.scan(texts + ("juice" to "۲٫۵"), "111", d, index)
    assertEquals("3.5", t2["juice"])
    assertTrue(r is CountScan.Result.Counted)
    assertTrue(CountSheetModel.scan(t2, "222", d, index).second is CountScan.Result.Weighed)
    assertTrue(CountSheetModel.scan(t2, "999", d, index).second is CountScan.Result.Unknown)
    assertEquals(t2, CountSheetModel.scan(t2, "222", d, index).first)
  }

  @Test
  fun `دو دستگاه — برگه‌ها جمع می‌شوند و فقط شمرده‌ها اصلاح می‌شوند`() {
    //  گوشیِ ۱: قفسه — ۴ نوشابه، ۲ آب‌میوه. گوشیِ ۲: انبار — ۵ نوشابه
    val (own, bad) = CountSheetModel.own(mapOf("cola" to "4", "juice" to "2", "water" to " "))
    assertNull(bad)
    val other = CountMerge.decode(CountMerge.encode(CountMerge.Sheet("s2", "گوشیِ ۲", 5, mapOf("cola" to 5000L))))!!
    val total = CountSheetModel.total(own, "s1", listOf(other))
    assertEquals(mapOf("cola" to 9000L, "juice" to 2000L), total.counts)
    assertEquals(setOf("cola"), total.shared)

    val counted = CountSheetModel.asDouble(total.counts)
    val out = (WarehouseEngine.stockCount(d, counted, "", "2026-10-31", 1L, ::newId) as WarehouseEngine.Result.Ok).data
    assertEquals(9.0, ShopStore.stock(out, "cola"), 1e-9)
    assertEquals(2.0, ShopStore.stock(out, "juice"), 1e-9)
    //  شمرده‌نشده‌ها دست نمی‌خورند
    assertEquals(20.0, ShopStore.stock(out, "water"), 1e-9)
    assertEquals(5.0, ShopStore.stock(out, "rice"), 1e-9)
    assertEquals(2, out.stockMovements.size)
  }

  @Test
  fun `برگهٔ هم‌شناسه با این دستگاه جای شمارشِ خودش را نمی‌گیرد`() {
    val own = mapOf("cola" to 4000L)
    val echo = CountMerge.Sheet("s1", "خودم", 99, mapOf("cola" to 1000L))
    assertEquals(mapOf("cola" to 4000L), CountSheetModel.total(own, "s1", listOf(echo)).counts)
  }

  @Test
  fun `عددِ نادرست گزارش می‌شود و خالی شمرده نیست`() {
    val (own, bad) = CountSheetModel.own(linkedMapOf("cola" to "۳", "juice" to "x", "water" to "-1", "rice" to ""))
    assertEquals(mapOf("cola" to 3000L), own)
    assertEquals("juice", bad)
    assertEquals(CountSheetModel.Cell.Blank, CountSheetModel.parse("  "))
    assertEquals(CountSheetModel.Cell.Ok(1250), CountSheetModel.parse("1,25"))
    assertEquals("1.25", CountSheetModel.text(1250))
  }
}
