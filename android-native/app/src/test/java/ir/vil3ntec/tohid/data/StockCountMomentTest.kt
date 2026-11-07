package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  INV-10 (S20) در «شمارش» ِ دفترِ موجودی (`StockActions.CountDraft`، موتورِ تازه): هر کالا لحظهٔ شمردنِ خودش را دارد.
 *
 *  پیش‌نویس فقط `snapshot` (موجودیِ لحظهٔ آغاز) داشت؛ کالایی که **پس از** فروشِ وسطِ شمارش شمرده می‌شد آن فروش را
 *  دو بار کم می‌کرد (یک بار در موجودیِ امروز، یک بار در اختلاف) و فروشِ آفلاینِ دستگاهِ دیگر که پس از آغاز رسید
 *  اختلافِ دروغ می‌ساخت. حالا کالای دارای لحظه با «موجودیِ امروز − گردشِ پس از همان لحظه» سنجیده می‌شود
 *  (`core/stock/CountDuringSales`)؛ پیش‌نویسِ نسخهٔ پیشین (بی لحظه) همان مقایسه با `snapshot` است.
 */
class StockCountMomentTest {
  private var n = 0
  private fun newId() = "id${++n}"
  private val today = "2026-10-15"
  private val t0 = 1_790_000_000_000L

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 130.0, salePrice = 200.0),
      Product(id = "p2", name = "شیر", unit = "عدد", purchasePrice = 10.0, salePrice = 30.0),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 15.0, price = 100.0, date = "2026-10-01", createdAt = 1000),
      WarehouseEntry(id = "w2", productId = "p2", units = 6.0, price = 10.0, date = "2026-10-01", createdAt = 2000),
    ),
  )
  private fun on(d: ShopData = base) = StockActions.setMode(d, true, "مالک", today, 10, ::newId)

  private fun sell(d: ShopData, qty: Double, at: Long): ShopData =
    (SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", qty)), SalesEngine.Checkout(), today, at, ::newId) as SalesEngine.Result.Ok).data

  /** همان کارِ پنجره: هر عددِ تازه با `stamp` ِ همان لحظه */
  private fun type(dr: StockActions.CountDraft, pid: String, raw: String, at: Long) =
    StockActions.stamp(dr, dr.copy(counts = dr.counts + (pid to raw)), at)

  @Before fun flagOn() { StockLedger.serverFlag = true }
  @After fun flagReset() { StockLedger.serverFlag = false }

  @Test
  fun countedAfterMidCountSale_isNotDeductedTwice() {
    var d = on()
    val start = StockActions.startCount(d, StockLedger.replay(d), t0)
    d = sell(d, 2.0, t0 + 60_000)
    //  شمارنده قفسه را پس از فروش شمرد: ۱۳ — همان موجودیِ واقعی
    val dr = type(start, "p1", "۱۳", t0 + 120_000)
    val review = StockActions.countReview(d, StockLedger.replay(d), dr)
    val line = review.lines.single()
    assertEquals(13_000L, line.systemMilli)
    assertEquals(0L, line.diffMilli)
    assertEquals(1, review.matched)
    assertEquals(0, review.variance)
    assertTrue(StockActions.saveCount(d, StockLedger.replay(d), dr, "کریم", today, t0 + 130_000, ::newId) is StockActions.Result.Failed)
    assertEquals(13.0, ShopStore.stock(d, "p1"), 0.0)
  }

  @Test
  fun countedBeforeMidCountSale_keepsTheSaleOutOfTheDifference() {
    var d = on()
    val start = StockActions.startCount(d, StockLedger.replay(d), t0)
    //  شمرده پیش از فروش: ۱۴ (یکی خراب)، سپس ۲ فروش
    val dr = type(start, "p1", "14", t0 + 30_000)
    d = sell(d, 2.0, t0 + 60_000)
    val line = StockActions.countReview(d, StockLedger.replay(d), dr).lines.single()
    assertEquals(15_000L, line.systemMilli)
    assertEquals(-1_000L, line.diffMilli)
    val r = StockActions.saveCount(d, StockLedger.replay(d), dr, "کریم", today, t0 + 90_000, ::newId) as StockActions.Result.Ok
    assertEquals(-1.0, r.data.warehouseEntries.last().units, 0.0)
    assertEquals(12.0, ShopStore.stock(r.data, "p1"), 0.0)
  }

  @Test
  fun offlineSaleThatArrivesAfterStart_isNotAFalseShortage() {
    var d = on()
    val start = StockActions.startCount(d, StockLedger.replay(d), t0)
    assertEquals(15_000L, start.snapshot["p1"])
    //  دستگاهِ دیگرِ آفلاین ۳ تا را پیش از آغاز فروخته بود؛ دفترش پس از آغاز رسید
    d = sell(d, 3.0, t0 - 600_000)
    val dr = type(start, "p1", "12", t0 + 120_000)
    val line = StockActions.countReview(d, StockLedger.replay(d), dr).lines.single()
    assertEquals(12_000L, line.systemMilli)
    assertEquals(0L, line.diffMilli)
  }

  @Test
  fun restoredDraftKeepsMoments_andOldDraftStaysOnSnapshot() {
    var d = on()
    val start = StockActions.startCount(d, StockLedger.replay(d), t0)
    d = sell(d, 2.0, t0 + 60_000)
    val dr = type(start, "p1", "13", t0 + 120_000)
    //  همان پیکربندیِ `StockCountDraftStore`
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    val back = json.decodeFromString(StockActions.CountDraft.serializer(), json.encodeToString(StockActions.CountDraft.serializer(), dr))
    assertEquals(mapOf("p1" to t0 + 120_000), back.countedAt)
    assertEquals(0L, StockActions.countReview(d, StockLedger.replay(d), back).lines.single().diffMilli)
    //  پیش‌نویسِ ذخیره‌شده با نسخهٔ پیشین (بی `countedAt`) ⇒ همان مقایسه با لحظهٔ آغاز
    val old = json.decodeFromString(
      StockActions.CountDraft.serializer(),
      """{"startedAt":$t0,"snapshot":{"p1":15000,"p2":6000},"counts":{"p1":"13"},"reasons":{},"note":""}""",
    )
    assertTrue(old.countedAt.isEmpty())
    assertEquals(-2_000L, StockActions.countReview(d, StockLedger.replay(d), old).lines.single().diffMilli)
  }

  @Test
  fun stamp_onlyChangedCountsMove_clearedCountDropsItsMoment_clockBeforeStartClamps() {
    val start = StockActions.CountDraft(startedAt = t0, snapshot = mapOf("p1" to 15_000L, "p2" to 6_000L))
    val a = type(start, "p1", "13", t0 + 10)
    val b = type(a, "p2", "6", t0 + 20)
    assertEquals(mapOf("p1" to t0 + 10, "p2" to t0 + 20), b.countedAt)
    //  یادداشت و دلیل لحظه را عوض نمی‌کنند
    val c = StockActions.stamp(b, b.copy(note = "قفسهٔ ۲", reasons = mapOf("p1" to "damage")), t0 + 30)
    assertEquals(b.countedAt, c.countedAt)
    //  عددِ پاک‌شده لحظه‌اش را هم می‌برد
    val e = StockActions.stamp(c, c.copy(counts = c.counts - "p2"), t0 + 40)
    assertEquals(mapOf("p1" to t0 + 10), e.countedAt)
    assertNull(e.countedAt["p2"])
    //  ساعتِ عقب‌رفته پیش از آغاز ⇒ همان لحظهٔ آغاز (نه گردشِ پیش از شمارش)
    val f = type(e, "p2", "5", t0 - 5_000)
    assertEquals(t0, f.countedAt["p2"])
    //  پیش‌نویسِ کالای بی‌لحظه (نسخهٔ پیشین) که دست نخورد بی‌لحظه می‌ماند
    val legacy = StockActions.CountDraft(startedAt = t0, counts = mapOf("p1" to "13"))
    assertTrue(StockActions.stamp(legacy, legacy.copy(note = "x"), t0 + 50).countedAt.isEmpty())
  }
}
