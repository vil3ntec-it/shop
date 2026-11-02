package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.stock.CountBlind
import ir.vil3ntec.tohid.core.stock.CountMerge
import ir.vil3ntec.tohid.core.stock.ShrinkReport
import ir.vil3ntec.tohid.data.CountFilter
import ir.vil3ntec.tohid.data.CountReview
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
 *  `CountDuringSales`) و ثبت با `WarehouseEngine.stockCount`. در برنامه پشتِ تنظیم‌های خاموشِ `count_tools` و `count_blind`:
 *  همان پنجره صافیِ دسته (`CountFilter`، INV-05) و بازبینیِ شمرده‌نشده/منطبق/مغایر (`CountReview`، INV-09) دارد و ثبتش
 *  حرکتِ جدای «اصلاحِ شمارش» (`count_adjust`، `AdjustKind.COUNT`) می‌سازد؛ خاموش ⇒ همان «اصلاح موجودی» ِ پیشین.
 */
class ScenarioS20Test {

  private var seq = 0
  private fun newId() = "k${seq++}"

  private val x = Product(id = "x", name = "نوشابه", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0, category = "نوشیدنی")
  private val y = Product(id = "y", name = "آب‌میوه", unit = "عدد", purchasePrice = 50.0, salePrice = 80.0, category = "نوشیدنی")
  private val z = Product(id = "z", name = "آب", unit = "عدد", purchasePrice = 10.0, salePrice = 15.0, category = "نوشیدنی")
  private val bread = Product(id = "b", name = "نان", unit = "عدد", purchasePrice = 5.0, salePrice = 8.0, category = "نانوایی")
  private val d0 = ShopData(
    products = listOf(x, y, z, bread),
    warehouseEntries = listOf(
      WarehouseEntry(id = "wx", productId = "x", units = 20.0, createdAt = 100),
      WarehouseEntry(id = "wy", productId = "y", units = 6.0, createdAt = 100),
      WarehouseEntry(id = "wz", productId = "z", units = 12.0, createdAt = 100),
      WarehouseEntry(id = "wb", productId = "b", units = 30.0, createdAt = 100),
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

  @Test
  fun `S20 — دستهٔ نوشیدنی، بازبینیِ شمرده‌نشده و منطبق و مغایر، و فقط حرکتِ اصلاحِ شمارش`() {
    val d = (SalesEngine.record(d0, listOf(SalesEngine.CartLine("x", 3.0)), SalesEngine.Checkout(), "2026-11-02", 2_000, ::newId)
      as SalesEngine.Result.Ok).data
    //  صافیِ دسته (INV-05): فقط سه نوشیدنی دیده می‌شوند، نان نه
    assertEquals(listOf("نوشیدنی", "نانوایی").sorted(), CountFilter.categories(d.products))
    val scope = CountFilter.shown(d.products, "", category = "نوشیدنی")
    assertEquals(setOf("x", "y", "z"), scope.map { it.id }.toSet())

    //  دو گوشی: X ساعتِ ۱۰۰۰ و Y ساعتِ ۱۲۰۰؛ Z شمرده نشد
    val counted = mapOf("x" to 20.0, "y" to 5.0)
    val shift = CountShift.of(d, mapOf("x" to 1_000L, "y" to 1_200L))
    val review = CountReview.of(d, scope, counted, shift)
    assertEquals(listOf("z"), review.of(CountReview.Kind.UNCOUNTED).map { it.product.id })
    assertEquals(listOf("x"), review.of(CountReview.Kind.MATCHED).map { it.product.id })
    assertEquals(listOf("y"), review.of(CountReview.Kind.DIFF).map { it.product.id })
    assertEquals(Triple(1, 1, 1), Triple(review.uncounted, review.matched, review.differing))
    assertEquals(-50.0, review.value, 1e-9)
    //  «مورد انتظار» ِ X پس از لحاظِ فروش‌های حینِ شمارش ۲۰ است، نه ۱۷
    assertEquals(20.0, review.rows.single { it.product.id == "x" }.expected, 1e-9)

    //  ثبت با «انبارگردانیِ تازه»: فقط یک حرکت، از نوعِ جدای «اصلاحِ شمارش»
    val moves = d.stockMovements.size
    val out = (WarehouseEngine.stockCount(d, counted, "قفسهٔ نوشیدنی", "2026-11-02", 3_000, ::newId, shift, WarehouseEngine.AdjustKind.COUNT)
      as WarehouseEngine.Result.Ok).data
    val added = out.stockMovements.drop(moves).single()
    assertEquals("count_adjust", added.type)
    assertEquals("y", added.productId)
    assertEquals(-1.0, added.qty, 1e-9)
    assertTrue(added.notes.startsWith("انبارگردانی"))
    assertEquals("count", out.warehouseEntries.single { it.id == added.refId }.adjKind)
    //  اثرِ مالی در گزارشِ کسری همان «کسری» است؛ شمرده‌نشده (Z) و نان دست نخوردند
    assertEquals(ShrinkReport.Kind.SHORTAGE, ShrinkReport.classify(added.type, added.notes, null, -1_000))
    assertEquals(12.0, ShopStore.stock(out, "z"), 1e-9)
    assertEquals(30.0, ShopStore.stock(out, "b"), 1e-9)
    assertEquals(5.0, ShopStore.stock(out, "y"), 1e-9)
  }
}
