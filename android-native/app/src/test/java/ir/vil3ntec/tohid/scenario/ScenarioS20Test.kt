package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.stock.CountBlind
import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.data.CountSheetModel
import ir.vil3ntec.tohid.data.CountShift
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S20 ⌨ — انبارگردانیِ جزئی هم‌زمان با فروش (INV-05 تا INV-10): دسته «نوشیدنی» با دو گوشی (برگه‌های `CountMerge`)،
 *  وسطِ شمارش صندوق ۳ عدد از X می‌فروشد (`SalesEngine.record`)، اختلاف پس از لحاظِ همان فروش (`CountShift` ⇐
 *  `CountDuringSales`) و ثبت با `WarehouseEngine.stockCount`. در برنامه پشتِ تنظیم‌های خاموشِ `count_tools` و `count_blind`.
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • صفحهٔ بازبینی فقط «مغایر» را با ارزش نشان می‌دهد (`countLines`)؛ «شمرده‌نشده» و «منطبق» جدا نیستند (INV-09).
 *   • شمارشِ «دستهٔ نوشیدنی» صافیِ دسته ندارد (INV-05): هر کالای شمرده اصلاح می‌شود و شمرده‌نشده دست نمی‌خورد.
 *   • حرکتِ ثبت‌شده نوعِ عمومیِ «اصلاح موجودی» (`adjustment`) با شرحِ «انبارگردانی» است، نه نوعِ جدای «اصلاحِ شمارش».
 */
class ScenarioS20Test {

  private var seq = 0
  private fun newId() = "k${seq++}"

  private val x = Product(id = "x", name = "نوشابه", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0, category = "نوشیدنی")
  private val y = Product(id = "y", name = "آب‌میوه", unit = "عدد", purchasePrice = 50.0, salePrice = 80.0, category = "نوشیدنی")
  private val z = Product(id = "z", name = "آب", unit = "عدد", purchasePrice = 10.0, salePrice = 15.0, category = "نوشیدنی")
  private val d0 = ShopData(
    products = listOf(x, y, z),
    warehouseEntries = listOf(
      WarehouseEntry(id = "wx", productId = "x", units = 20.0, createdAt = 100),
      WarehouseEntry(id = "wy", productId = "y", units = 6.0, createdAt = 100),
      WarehouseEntry(id = "wz", productId = "z", units = 12.0, createdAt = 100),
    ),
  )

  @Test
  fun `S20 — دو گوشی، فروشِ ۳ عدد X وسطِ شمارش ⇒ اختلافِ X صفر، فقط اصلاح و اثرِ مالی، شمارشِ کور`() {
    //  گوشیِ ۱ در ساعتِ ۱۰۰۰ ۲۰ نوشابه شمرد؛ گوشیِ ۲ در ساعتِ ۱۲۰۰ ۵ آب‌میوه (برگه با فایل)
    val phone1 = CountMerge.Sheet("s1", "گوشیِ ۱", 1_000, mapOf("x" to 20_000L))
    val phone2 = CountMerge.decode(CountMerge.encode(CountMerge.Sheet("s2", "گوشیِ ۲", 1_200, mapOf("y" to 5_000L))))!!
    val total = CountSheetModel.total(phone1.counts, phone1.sheetId, listOf(phone2))
    assertEquals(mapOf("x" to 20_000L, "y" to 5_000L), total.counts)

    //  ساعتِ ۲۰۰۰ صندوق ۳ نوشابه فروخت
    val d = (SalesEngine.record(d0, listOf(SalesEngine.CartLine("x", 3.0)), SalesEngine.Checkout(), "2026-11-02", 2_000, ::newId)
      as SalesEngine.Result.Ok).data
    assertEquals(17.0, ShopStore.stock(d, "x"), 0.0)

    val at = mapOf("x" to phone1.at, "y" to phone2.at)
    val shift = CountShift.of(d, at)
    val counted = CountSheetModel.asDouble(total.counts)
    val lines = WarehouseEngine.countLines(d, counted, shift)
    //  X پس از لحاظِ فروش منطبق است؛ فقط Y مغایر (−۱) با اثرِ مالیِ −۵۰
    assertEquals(listOf("y"), lines.map { it.product.id })
    assertEquals(-1.0, lines.single().diff, 1e-9)
    assertEquals(-50.0, lines.single().value, 1e-9)

    val moves = d.stockMovements.size
    val out = (WarehouseEngine.stockCount(d, counted, "", "2026-11-02", 3_000, ::newId, shift) as WarehouseEngine.Result.Ok).data
    val added = out.stockMovements.drop(moves)
    assertEquals(1, added.size)
    assertTrue(added.all { it.type == "adjustment" && it.notes.startsWith("انبارگردانی") })
    assertEquals(17.0, ShopStore.stock(out, "x"), 1e-9)
    assertEquals(5.0, ShopStore.stock(out, "y"), 1e-9)
    assertEquals(12.0, ShopStore.stock(out, "z"), 1e-9)

    //  شمارشِ کور: موجودیِ برنامه و اختلاف پنهان
    assertEquals(CountBlind.Hint(null, null), CountBlind.hint(17_000, 20_000, blind = true))
    assertEquals(CountBlind.Hint(17_000, 3_000), CountBlind.hint(17_000, 20_000, blind = false))
  }
}
