package ir.vil3ntec.tohid.core.promo

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 *  PRM-10 — نوعِ «نزدیکِ انقضا» (`expiry`): درصد فقط روی واحدهای محموله‌های تا `nearDays` روز
 *  مانده به انقضا، شمارِ واحد به FEFO، منقضی بیرون، مرزِ بازه، ردیفِ بی محموله، و ترکیب با
 *  نوع‌های دیگر به همان قاعدهٔ PRM-12. همان بردارهای `promo-v1.json` (که JS هم می‌دواند)،
 *  این‌جا با سازه‌های Kotlin تا `gradle -p core jvmTest` هم بسنجد.
 */
class PromoExpiryTest {

  /** A = ۱۰۰ افغانی، بها ۶۰ */
  private fun a(qtyMilli: Long, vararg lots: Pair<Long, Long>, key: String = "a") = PromoCore.Line(
    key = key, productId = "A", qtyMilli = qtyMilli, unitMinor = 10_000, costMinor = 6_000, category = "drinks",
    lots = lots.map { PromoCore.Lot(it.first, it.second) },
  )
  /** C = ۳۵۰ افغانی، بها نامعلوم */
  private fun c(qtyMilli: Long, vararg lots: Pair<Long, Long>, key: String = "c") = PromoCore.Line(
    key = key, productId = "C", qtyMilli = qtyMilli, unitMinor = 35_000, category = "drinks",
    lots = lots.map { PromoCore.Lot(it.first, it.second) },
  )
  private fun ex(bp: Long = 3_000, nearDays: Long = 7, stack: String = "best") =
    PromoCore.Rule(id = "ex", name = "نزدیکِ انقضا", kind = "expiry", bp = bp, nearDays = nearDays, stack = stack)
  private val p10 = PromoCore.Rule(id = "p10", name = "ده درصد", kind = "percent", bp = 1_000)

  private fun run(rules: List<PromoCore.Rule>, vararg lines: PromoCore.Line) = PromoCore.evaluate(PromoCore.Cart(lines.toList()), rules)
  private fun disc(r: PromoCore.Result) = r.lines.map { it.discountMinor }

  @Test
  fun validatesDaysAndPercent() {
    assertEquals(emptyList(), PromoCore.validate(ex()))
    assertEquals(emptyList(), PromoCore.validate(ex(nearDays = 1)))
    assertEquals(emptyList(), PromoCore.validate(ex(nearDays = 365)))
    assertEquals(listOf("expiry"), PromoCore.validate(ex(nearDays = 0)))
    assertEquals(listOf("expiry"), PromoCore.validate(ex(nearDays = 366)))
    assertEquals(listOf("bp"), PromoCore.validate(ex(bp = 0)))
    assertEquals(listOf("bp", "expiry"), PromoCore.validate(ex(bp = 10_001, nearDays = -1)))
    //  نوعِ دیگر `nearDays` را نمی‌خواند
    assertEquals(emptyList(), PromoCore.validate(p10.copy(nearDays = 999)))
    assertEquals(7L, PromoCore.ruleOf(mapOf("kind" to "expiry", "nearDays" to 7.0)).nearDays)
    assertEquals(0L, PromoCore.ruleOf(mapOf("kind" to "expiry", "nearDays" to "7")).nearDays)
  }

  @Test
  fun fefoDiscountsOnlyNearLotUnits() {
    //  ۵ واحد؛ ۲ در محمولهٔ ۳روزه، بقیه ۴۰روزه ⇒ ۳۰٪ از ۲۰۰ = ۶۰ افغانی
    val r = run(listOf(ex()), a(5_000, 3L to 2_000L, 40L to 10_000L))
    assertEquals(listOf(6_000L), disc(r))
    assertEquals(listOf(PromoCore.Applied("ex", "نزدیکِ انقضا", 6_000)), r.applied)
  }

  @Test
  fun expiredLotsGetNothingButDayZeroDoes() {
    val r = run(listOf(ex()), a(5_000, -2L to 3_000L, -1L to 1_000L, 0L to 1_000L))
    assertEquals(listOf(3_000L), disc(r), "فقط ۱ واحدِ روزِ صفر")
    assertEquals(listOf(0L), disc(run(listOf(ex()), a(5_000, -1L to 5_000L))), "همه منقضی ⇒ هیچ")
  }

  @Test
  fun windowBoundaryIsInclusive() {
    assertEquals(listOf(3_000L), disc(run(listOf(ex(nearDays = 7)), a(5_000, 7L to 1_000L, 8L to 2_000L))))
    assertEquals(listOf(9_000L), disc(run(listOf(ex(nearDays = 8)), a(5_000, 7L to 1_000L, 8L to 2_000L))))
  }

  @Test
  fun lineWithoutLotsGetsNothing() {
    val r = run(listOf(ex()), a(5_000), c(1_000))
    assertEquals(listOf(0L, 0L), disc(r))
    assertEquals(emptyList(), r.applied)
    assertEquals(0L, r.discountMinor)
  }

  @Test
  fun wholeLineNearEqualsPercent() {
    val near = run(listOf(ex()), a(2_000, 2L to 5_000L))
    val pct = run(listOf(p10.copy(bp = 3_000)), a(2_000))
    assertEquals(disc(pct), disc(near))
  }

  @Test
  fun linesOfOneProductShareTheNearStockInCartOrder() {
    val r = run(listOf(ex(bp = 5_000, nearDays = 3)), c(2_000, 1L to 3_000L, key = "c1"), c(2_000, 1L to 3_000L, key = "c2"))
    assertEquals(listOf(35_000L, 17_500L), disc(r))
  }

  @Test
  fun weighedLinePartialUnits() {
    //  ۱٫۵ کیلو × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳؛ نیم کیلوی نزدیک = ۵۷٫۵ ⇒ ۵۸؛ ۲۰٪ = ۱۱٫۶ ⇒ ۱۲
    val w = PromoCore.Line(key = "w", productId = "W", qtyMilli = 1_500, unitMinor = 11_500, lots = listOf(PromoCore.Lot(5, 500)))
    val r = run(listOf(ex(bp = 2_000)), w)
    assertEquals(17_300L, r.lines.single().amountMinor)
    assertEquals(listOf(1_200L), disc(r))
  }

  @Test
  fun costFloorStillHolds() {
    //  ۹۰٪ روی ۵ واحدِ نزدیک: ۴۵۰ ولی کف = ۵ × ۶۰ ⇒ حداکثر ۲۰۰
    assertEquals(listOf(20_000L), disc(run(listOf(ex(bp = 9_000)), a(5_000, 1L to 5_000L))))
    assertEquals(listOf(45_000L), disc(run(listOf(ex(bp = 9_000).copy(allowBelowCost = true)), a(5_000, 1L to 5_000L))))
  }

  @Test
  fun manualPriceLineGetsNothing() {
    assertEquals(listOf(0L), disc(run(listOf(ex()), a(5_000, 1L to 5_000L).copy(noPromo = true))))
  }

  @Test
  fun bestStackKeepsOnlyTheLarger() {
    val r = run(listOf(ex(bp = 5_000), p10), a(5_000, 3L to 2_000L), c(1_000, 1L to 1_000L))
    assertEquals(listOf(10_000L, 17_500L), disc(r))
    assertEquals(listOf("ex"), r.applied.map { it.id })
    //  ده درصد بیشتر بدهد ⇒ همان می‌ماند
    val small = run(listOf(ex(bp = 500), p10), a(5_000, 3L to 1_000L))
    assertEquals(listOf("p10"), small.applied.map { it.id })
  }

  @Test
  fun combineAppliesOnRemainderWithCostFloor() {
    val r = run(listOf(ex(bp = 8_000, stack = "combine"), p10), a(5_000, 3L to 2_000L), c(1_000, 1L to 1_000L))
    assertEquals(listOf(20_000L, 31_500L), disc(r))
    assertEquals(listOf(PromoCore.Applied("ex", "نزدیکِ انقضا", 44_000), PromoCore.Applied("p10", "ده درصد", 7_500)), r.applied)
  }

  @Test
  fun exclusiveWinsOverAll() {
    val r = run(listOf(ex(bp = 1_000, stack = "exclusive"), p10.copy(bp = 5_000, stack = "combine")), a(5_000, 3L to 2_000L))
    assertEquals(listOf("ex"), r.applied.map { it.id })
    assertEquals(listOf(2_000L), disc(r))
  }

  @Test
  fun cartOfReadsLots() {
    val cart = PromoCore.cartOf(
      mapOf("lines" to listOf(mapOf("key" to "a", "productId" to "A", "qtyMilli" to 1000.0, "unitMinor" to 100.0,
        "lots" to listOf(mapOf("daysLeft" to 2.0, "qtyMilli" to 500.0), mapOf("daysLeft" to "x"))))),
    )
    assertEquals(listOf(PromoCore.Lot(2, 500), PromoCore.Lot(-1, 0)), cart.lines.single().lots)
    assertEquals(emptyList(), PromoCore.cartOf(mapOf("lines" to listOf(mapOf("key" to "a")))).lines.single().lots)
  }
}
