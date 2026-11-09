package ir.vil3ntec.tohid.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 *  INV-16 — ارزشِ موجودی به بهای تمام‌شده در «تحلیلِ موجودی» (`ReportEngine.stockInsight`).
 *
 *  ── D-23 گرفته شد: «الف»، میانگینِ موزون ─────────────────────────
 *  موتورِ تازهٔ موجودی (`StockLedger.isOn`) بهای میانگینِ موزونِ هر کالا را دارد؛ تحلیل تا امروز همیشه `purchasePrice`
 *  (آخرین قیمتِ خرید) را ضرب می‌کرد، حتی وقتی موتور روشن بود — خلافِ شرحِ `StockAnalysis.ValueLine`. حالا موتور روشن ⇒
 *  بهای میانگینِ همان دفتر (کالای بی بهای معلوم ⇒ همان `purchasePrice`)؛ خاموش ⇒ بیت‌به‌بیت همان.
 *
 *  ── کیت دو بار شمرده نمی‌شود ──────────────────────────────────────
 *  کیت موجودیِ خودش را ندارد (موجودی‌اش از اجزاست، `StockPlaces.stockAt`/`StockLedger`)؛ ارزشِ آن همان ارزشِ اجزاست که
 *  جدا شمرده می‌شوند. فروشِ کیت در ABC می‌ماند.
 */
class StockValuationCostTest {
  private var n = 0
  private fun newId() = "id${++n}"
  private val today = "2026-08-20"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 150.0, salePrice = 200.0),
      Product(id = "p2", name = "شکر", unit = "عدد", purchasePrice = 40.0, salePrice = 60.0),
      Product(id = "k1", name = "بستهٔ روغن و شکر", unit = "بسته", purchasePrice = 185.0, salePrice = 250.0, kit = listOf(KitPart("p1", 1.0), KitPart("p2", 1.0))),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 10.0, price = 100.0, date = "2026-08-01", createdAt = 1000),
      WarehouseEntry(id = "w2", productId = "p1", units = 10.0, price = 140.0, date = "2026-08-02", createdAt = 2000),
      WarehouseEntry(id = "w3", productId = "p2", units = 5.0, price = 40.0, date = "2026-08-02", createdAt = 3000),
    ),
  )

  @Before fun flagOn() { StockLedger.serverFlag = true }
  @After fun flagReset() { StockLedger.serverFlag = false }

  @Test
  fun engineOn_costIsWeightedAverage_notLastPurchasePrice() {
    val d = StockActions.setMode(base, true, "مالک", today, 10, ::newId)
    val v = ReportEngine.stockInsight(d, "2026-08-01", "2026-08-31").valuation
    //  روغن: ۲۰ × میانگینِ ۱۲۰ (نه ۱۵۰)، شکر: ۵ × ۴۰ — به صدمِ افغانی
    assertEquals((20 * 120 + 5 * 40) * 100L, v.costMinor)
    assertEquals((20 * 200 + 5 * 60) * 100L, v.retailMinor)
    assertEquals(2, v.inStock)
  }

  @Test
  fun engineOn_valueIsTheLedgersExactValue_notQtyTimesRoundedUnitCost() {
    //  ۱ صابون به ۱۰ + ۲ صابون به ۵ = ۲۰ افغانی برای ۳ ⇒ میانگینِ ۶٫۶۶۶… (گرد ۶٫۶۷). ارزش همان ۲۰٫۰۰ ِ دفتر است،
    //  نه ۳ × ۶٫۶۷ = ۲۰٫۰۱ — و سودِ بالقوه از همان ارزش (۳ × ۹ − ۲۰)
    val soap = ShopData(
      products = listOf(Product(id = "s", name = "صابون", unit = "عدد", purchasePrice = 5.0, salePrice = 9.0)),
      warehouseEntries = listOf(
        WarehouseEntry(id = "a", productId = "s", units = 1.0, price = 10.0, date = "2026-08-01", createdAt = 1000),
        WarehouseEntry(id = "b", productId = "s", units = 2.0, price = 5.0, date = "2026-08-02", createdAt = 2000),
      ),
    )
    val v = ReportEngine.stockInsight(StockActions.setMode(soap, true, "مالک", today, 10, ::newId), "2026-08-01", "2026-08-31").valuation
    assertEquals(2000L, v.costMinor)
    assertEquals(2000L, v.lines.single().costMinor)
    assertEquals(3 * 900L - 2000L, v.potentialProfitMinor)
  }

  @Test
  fun engineOff_costStaysPurchasePrice() {
    val v = ReportEngine.stockInsight(base, "2026-08-01", "2026-08-31").valuation
    assertEquals((20 * 150 + 5 * 40) * 100L, v.costMinor)
  }

  @Test
  fun kitIsNotValuedOnTopOfItsParts() {
    //  ۵ بسته از اجزا ساخته می‌شود؛ ارزشِ انبار همان ۲۰ روغن و ۵ شکر است، نه به‌اضافهٔ ۵ بسته
    for (d in listOf(base, StockActions.setMode(base, true, "مالک", today, 10, ::newId))) {
      val s = ReportEngine.stockInsight(d, "2026-08-01", "2026-08-31")
      assertEquals(2, s.valuation.inStock)
      assertEquals(listOf("p1", "p2"), s.valuation.lines.map { it.id }.sorted())
      assertEquals((20 * 200 + 5 * 60) * 100L, s.valuation.retailMinor)
    }
  }
}
