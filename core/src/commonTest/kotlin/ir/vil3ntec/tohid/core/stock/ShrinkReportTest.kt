package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.ShrinkReport.Kind
import ir.vil3ntec.tohid.core.stock.ShrinkReport.Move
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** INV-17: ضایعات جدا از کسری، کسری منهای اضافهٔ شمارش، ارزش به بهای کالا، بی‌بها جدا شمرده. */
class ShrinkReportTest {

  //  بهای واحد به پول: شیر ۵۰ افغانی، برنج ۱۰۰ افغانی برای هر کیلو، تخم‌مرغ بی‌بها
  private val cost = mapOf("milk" to 5_000L, "rice" to 10_000L, "egg" to 0L)

  @Test fun classifyKeepsSalesPurchasesAndSupplierReturnsOut() {
    assertEquals(Kind.WASTE, ShrinkReport.classify("damage", "ضایعاتِ برگشتی B1-4 — شکسته", null, -1000))
    assertEquals(Kind.SHORTAGE, ShrinkReport.classify("adjustment", "انبارگردانی — قفسه ۱", null, -500))
    assertEquals(Kind.SURPLUS, ShrinkReport.classify("adjustment", " انبارگردانی", null, 2000))
    assertEquals(Kind.OTHER, ShrinkReport.classify("adjustment", "موش خورد", null, -1000))
    assertEquals(Kind.EXPIRED, ShrinkReport.classify("adjustment", "تاریخ گذشت", "expired", -1000))
    assertEquals(Kind.LOST, ShrinkReport.classify("adjustment", "پیدا نشد", "lost", -1000))
    assertEquals(Kind.WASTE, ShrinkReport.classify("adjustment", "شکست", "damage", -1000))
    //  نوعِ «count» بر یادداشت جلوتر است
    assertEquals(Kind.SHORTAGE, ShrinkReport.classify("adjustment", "هر چه", "count", -1))
    assertNull(ShrinkReport.classify("adjustment", "برگشت", "supplier_return", -1000))
    for (t in listOf("sale", "purchase_in", "customer_return", "supplier_return", "sale_cancel")) {
      assertNull(ShrinkReport.classify(t, "", null, -1000), t)
    }
    assertNull(ShrinkReport.classify("damage", "", null, 0))
  }

  @Test fun wasteAndShrinkAreSeparateAndSurplusOffsetsShortage() {
    val moves = listOf(
      Move("milk", "2026-10-02", -3_000, Kind.WASTE, "شکسته"),        // −۱۵۰
      Move("milk", "2026-10-03", -1_000, Kind.EXPIRED, "تاریخ گذشته"), // −۵۰
      Move("rice", "2026-10-05", -2_500, Kind.SHORTAGE, "انبارگردانی"), // −۲۵۰
      Move("rice", "2026-10-06", 500, Kind.SURPLUS, "انبارگردانی"),    // +۵۰
      Move("milk", "2026-10-07", -1_000, Kind.OTHER, "موش خورد"),       // −۵۰
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get, salesMinor = 1_000_000)
    assertEquals(20_000, r.wasteMinor)
    assertEquals(25_000 - 5_000 + 5_000, r.shrinkMinor)
    assertEquals(45_000, r.netLossMinor)
    assertEquals(5, r.rows)
    assertEquals(0, r.unpricedRows)
    //  ۴۵۰ از ۱۰٬۰۰۰ افغانی فروش ⇒ ۴٫۵٪
    assertEquals(450, r.rateBp)
    assertEquals(listOf(Kind.WASTE, Kind.EXPIRED, Kind.SHORTAGE, Kind.SURPLUS, Kind.OTHER), r.kinds.map { it.kind })
    val surplus = r.kinds.single { it.kind == Kind.SURPLUS }
    assertEquals(0, surplus.lossMinor)
    assertEquals(5_000, surplus.gainMinor)
  }

  @Test fun productsSortByNetLossAndKeepQuantitiesPerUnit() {
    val moves = listOf(
      Move("milk", "2026-10-02", -3_000, Kind.WASTE),
      Move("rice", "2026-10-05", -2_500, Kind.SHORTAGE),
      Move("rice", "2026-10-06", 500, Kind.SURPLUS),
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get)
    assertEquals(listOf("rice", "milk"), r.products.map { it.productId })
    val rice = r.products.first()
    assertEquals(2_500, rice.outMilli)
    assertEquals(500, rice.inMilli)
    assertEquals(20_000, rice.netLossMinor)
    assertEquals(listOf(Kind.SHORTAGE, Kind.SURPLUS), rice.kinds)
    assertNull(r.rateBp)
  }

  @Test fun outsideTheRangeAndZeroRowsAreIgnored() {
    val moves = listOf(
      Move("milk", "2026-09-30", -1_000, Kind.WASTE),
      Move("milk", "2026-10-01", -1_000, Kind.WASTE),
      Move("milk", "2026-10-31", -1_000, Kind.WASTE),
      Move("milk", "2026-11-01", -1_000, Kind.WASTE),
      Move("milk", "", -1_000, Kind.WASTE),
      Move("milk", "2026-10-10", 0, Kind.WASTE),
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get)
    assertEquals(2, r.rows)
    assertEquals(10_000, r.wasteMinor)
    assertTrue(ShrinkReport.build(emptyList(), "2026-10-01", "2026-10-31", cost::get).isEmpty)
  }

  @Test fun unpricedProductIsCountedNotHidden() {
    val moves = listOf(
      Move("egg", "2026-10-02", -12_000, Kind.WASTE, "شکست"),
      Move("gone", "2026-10-02", -1_000, Kind.LOST),
      Move("milk", "2026-10-02", -1_000, Kind.WASTE, "شکست"),
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get)
    assertEquals(2, r.unpricedRows)
    assertEquals(5_000, r.netLossMinor)
    val egg = r.products.single { it.productId == "egg" }
    assertFalse(egg.priced)
    assertEquals(12_000, egg.outMilli)
    assertTrue(r.products.single { it.productId == "milk" }.priced)
  }

  @Test fun fractionalQuantitiesRoundToTheNearestPul() {
    //  ۰٫۳۳۳ کیلو برنجِ ۱۰۰ افغانیگی ⇒ ۳۳٫۳۰ افغانی؛ ۰٫۰۰۱ × ۰٫۰۵ افغانی ⇒ ۰ پول (گرد به نزدیک‌ترین)
    val r = ShrinkReport.build(
      listOf(Move("rice", "2026-10-02", -333, Kind.SHORTAGE), Move("p", "2026-10-02", -1, Kind.WASTE)),
      "2026-10-01", "2026-10-31", mapOf("rice" to 10_000L, "p" to 5L)::get,
    )
    assertEquals(3_330, r.shrinkMinor)
    assertEquals(0, r.wasteMinor)
  }

  @Test fun linesAreDatedSignedAndSumToTheNetLoss() {
    val moves = listOf(
      Move("rice", "2026-10-06", 500, Kind.SURPLUS),
      Move("egg", "2026-10-03", -1_000, Kind.WASTE),
      Move("milk", "2026-10-03", -1_000, Kind.WASTE),
      Move("milk", "2026-09-01", -1_000, Kind.WASTE),
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get)
    assertEquals(listOf("egg", "milk", "rice"), r.lines.map { it.move.productId })
    assertEquals(listOf(0L, 5_000L, -5_000L), r.lines.map { it.lossMinor })
    assertEquals(listOf(false, true, true), r.lines.map { it.priced })
    assertEquals(r.netLossMinor, r.lines.sumOf { it.lossMinor })
  }

  @Test fun reasonsRankByLossAndOnlyCountWhatLeft() {
    val moves = listOf(
      Move("milk", "2026-10-02", -1_000, Kind.WASTE, "شکسته"),
      Move("milk", "2026-10-03", -2_000, Kind.WASTE, " شکسته "),
      Move("rice", "2026-10-04", -1_000, Kind.OTHER, "موش"),
      Move("rice", "2026-10-05", 4_000, Kind.SURPLUS, "انبارگردانی"),
    )
    val r = ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get, topReasons = 5)
    assertEquals(listOf("شکسته" to 15_000L, "موش" to 10_000L), r.reasons.map { it.text to it.lossMinor })
    assertEquals(2, r.reasons.first().rows)
    //  اضافهٔ شمارش بیش از کسری ⇒ کسریِ خالصِ منفی، نه صفرِ بی‌صدا
    assertEquals(10_000 - 40_000, r.shrinkMinor)
    assertEquals(1, ShrinkReport.build(moves, "2026-10-01", "2026-10-31", cost::get, topReasons = 1).reasons.size)
  }
}
