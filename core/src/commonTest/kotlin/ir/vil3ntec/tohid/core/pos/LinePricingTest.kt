package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.pos.LinePricing.Direction
import ir.vil3ntec.tohid.core.pos.LinePricing.Policy
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  قیمتِ ردیفِ صندوق — POS-08، POS-10، POS-20، POS-21.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class LinePricingTest {

  private val on = Policy(on = true)

  // ── مبلغِ ردیف: همان گردِ `SalesEngine.lineTotal` ──────────────────

  @Test
  fun grossRoundsHalfUpToWholeAfghaniLikeLineTotal() {
    assertEquals(17_300, LinePricing.grossMinor(11_500, 1_500))   // ۱٫۵ × ۱۱۵ = ۱۷۲٫۵ ⇒ ۱۷۳
    assertEquals(30_000, LinePricing.grossMinor(10_000, 3_000))
    assertEquals(6_500, LinePricing.grossMinor(26_000, 250))      // ۰٫۲۵ کیلو × ۲۶۰
    assertEquals(100, LinePricing.grossMinor(100, 500))           // ۰٫۵ ⇒ ۱
    assertEquals(0, LinePricing.grossMinor(100, 499))             // ۰٫۴۹۹ ⇒ ۰
    assertEquals(0, LinePricing.grossMinor(0, 7_000))
  }

  @Test
  fun grossDoesNotOverflowForLargeButRealisticLines() {
    //  ۱۰۰ هزار افغانی × ۱۰۰۰ عدد
    assertEquals(10_000_000_000, LinePricing.grossMinor(10_000_000, 1_000_000))
    assertFailsWith<ArithmeticException> { LinePricing.grossMinor(LinePricing.MAX_EXACT, 2_000) }
    assertFailsWith<ArithmeticException> { LinePricing.grossMinor(-1, 1_000) }
  }

  // ── تخفیفِ ردیف (POS-20) ─────────────────────────────────────

  @Test
  fun percentDiscountRoundsHalfUpToStepAndIsCappedAtGross() {
    assertEquals(3_000, LinePricing.discountMinor(30_000, true, 1_000))     // ۱۰٪ از ۳۰۰
    assertEquals(3_800, LinePricing.discountMinor(30_000, true, 1_250))     // ۳۷٫۵ ⇒ ۳۸
    assertEquals(3_300, LinePricing.discountMinor(10_000, true, 3_333))     // ۳۳٫۳۳ ⇒ ۳۳
    assertEquals(30_000, LinePricing.discountMinor(30_000, true, 10_000))   // ۱۰۰٪
    assertEquals(30_000, LinePricing.discountMinor(30_000, true, 25_000))   // بیش از ۱۰۰٪ ⇒ همهٔ ردیف
    assertEquals(30_000, LinePricing.discountMinor(30_000, true, LinePricing.MAX_EXACT))   // درصدِ بی‌معنا سرریز نمی‌کند
    assertEquals(0, LinePricing.discountMinor(30_000, true, 0))
    assertEquals(0, LinePricing.discountMinor(30_000, true, -500))
  }

  @Test
  fun amountDiscountRoundsHalfUpAndNeverExceedsGross() {
    assertEquals(1_300, LinePricing.discountMinor(30_000, false, 1_250))    // ۱۲٫۵ ⇒ ۱۳
    assertEquals(0, LinePricing.discountMinor(30_000, false, 49))
    assertEquals(100, LinePricing.discountMinor(30_000, false, 50))
    assertEquals(5_000, LinePricing.discountMinor(30_000, false, 5_000))
    assertEquals(30_000, LinePricing.discountMinor(30_000, false, 90_000))
    assertEquals(0, LinePricing.discountMinor(0, false, 5_000))
  }

  @Test
  fun netAfterDiscountIsAlwaysWholeAfghani() {
    for (gross in longArrayOf(100, 17_300, 30_000, 99_900, 123_400)) {
      for (bp in longArrayOf(1, 333, 1_250, 5_000, 9_999)) {
        val net = gross - LinePricing.discountMinor(gross, true, bp)
        assertEquals(0, net % LinePricing.STEP_MINOR, "gross=$gross bp=$bp")
        assertTrue(net in 0..gross)
      }
    }
  }

  @Test
  fun unitForNetReproducesTheNetThroughLineTotalRounding() {
    val quantities = longArrayOf(1_000, 3_000, 7_000, 1_500, 250, 333, 12_345, 999_000)
    val nets = longArrayOf(0, 100, 29_000, 48_300, 99_900, 1_000_000, 987_654_300)
    for (q in quantities) for (net in nets) {
      val unit = LinePricing.unitForNet(net, q)
      //  همان `SalesEngine.lineTotal`: round(قیمت × مقدار) به افغانی
      val total = (unit * (q / 1000.0)).roundToLong()
      assertEquals(net / 100, total, "qty=$q net=$net")
    }
    assertFailsWith<ArithmeticException> { LinePricing.unitForNet(100, 0) }
  }

  // ── کاهشِ ردیف و پایهٔ سقفِ تخفیف (POS-21) ──────────────────────

  @Test
  fun reductionCountsOnlySalesBelowListPrice() {
    assertEquals(50_000, LinePricing.reductionMinor(100_000, 50_000, 1_000))
    assertEquals(150_000, LinePricing.reductionMinor(100_000, 50_000, 3_000))
    assertEquals(0, LinePricing.reductionMinor(100_000, 100_000, 3_000))
    assertEquals(0, LinePricing.reductionMinor(100_000, 120_000, 3_000))
    //  هر دو طرف با همان گردِ ردیف: ۱٫۵ × ۱۱۵ (۱۷۳) − ۱٫۵ × ۱۱۰ (۱۶۵)
    assertEquals(800, LinePricing.reductionMinor(11_500, 11_000, 1_500))
  }

  @Test
  fun basisIsTodayWhenFlagIsOffOrNothingWasReduced() {
    assertEquals(LinePricing.Basis(5_000, 100_000), LinePricing.basis(Policy(), 5_000, 100_000, 40_000))
    assertEquals(LinePricing.Basis(5_000, 100_000), LinePricing.basis(on, 5_000, 100_000, 0))
  }

  @Test
  fun basisAddsLineReductionsToDiscountAndBase() {
    //  فهرست ۱۰۰۰، ردیف به ۵۰۰ فروخته شد، بی تخفیفِ فاکتور: ۵۰۰ از ۱۰۰۰ = ۵۰٪
    val b = LinePricing.basis(on, 0, 50_000, 50_000)
    assertEquals(LinePricing.Basis(50_000, 100_000), b)
    assertEquals(4_995, PermCore.discountBp(b.discountMinor, b.baseMinor))   // نیم‌افغانیِ گرد بخشیده
    //  با سقفِ ۱۰٪ ِ نقش ⇒ «نیازمند تأیید»، نه «مجاز» (پیش از این ۰ تخفیف دیده می‌شد)
    val layers = listOf(mapOf("sales.discount" to mapOf("mode" to "allow", "caps" to mapOf("maxPct" to 10))))
    val req = mapOf("pct" to PermCore.discountPct(b.discountMinor, b.baseMinor), "amount" to b.discountMinor.toDouble())
    assertEquals("approve", PermCore.evaluate("manager", layers, "sales.discount", req).decision)
    assertEquals("allow", PermCore.evaluate("manager", layers, "sales.discount", mapOf("pct" to 0.0, "amount" to 0.0)).decision)
  }

  @Test
  fun slipFromBasisAlwaysCoversWhatTheServerMeasures() {
    //  سرور (D، D÷S) می‌سنجد؛ برگهٔ صندوق (D+R، (D+R)÷(S+R)) است و باید همیشه بزرگ‌تر یا برابر باشد
    val values = longArrayOf(0, 1, 49, 50, 51, 100, 999, 10_000, 123_456, 9_999_900)
    for (s in values) for (d in values) for (r in values) {
      if (d > s) continue
      val b = LinePricing.basis(on, d, s, r)
      assertTrue(b.discountMinor >= d, "amount s=$s d=$d r=$r")
      assertTrue(PermCore.discountBp(b.discountMinor, b.baseMinor) >= PermCore.discountBp(d, s), "pct s=$s d=$d r=$r")
    }
  }

  // ── قاعدهٔ جهت (POS-08) ───────────────────────────────────────

  @Test
  fun directionCodesParseSafely() {
    assertEquals(Direction.BOTH, Direction.of(null))
    assertEquals(Direction.BOTH, Direction.of(""))
    assertEquals(Direction.UP, Direction.of("up"))
    assertEquals(Direction.DOWN, Direction.of(" down "))
    assertEquals(Direction.NONE, Direction.of("none"))
    //  کدِ ناشناخته (نسخهٔ تازه‌تر) ⇒ بسته‌ترین
    assertEquals(Direction.NONE, Direction.of("sideways"))
    for (d in Direction.entries) assertEquals(d, Direction.of(d.code))
  }

  @Test
  fun directionRuleBlocksOnlyTheForbiddenWay() {
    fun at(d: Direction) = on.copy(direction = d)
    assertNull(LinePricing.checkPrice(at(Direction.BOTH), 10_000, 12_000))
    assertNull(LinePricing.checkPrice(at(Direction.BOTH), 10_000, 8_000))
    assertNull(LinePricing.checkPrice(at(Direction.UP), 10_000, 12_000))
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePricing.checkPrice(at(Direction.UP), 10_000, 8_000))
    assertNull(LinePricing.checkPrice(at(Direction.DOWN), 10_000, 8_000))
    assertEquals(LinePricing.PRICE_UP_BLOCKED, LinePricing.checkPrice(at(Direction.DOWN), 10_000, 12_000))
    assertEquals(LinePricing.PRICE_UP_BLOCKED, LinePricing.checkPrice(at(Direction.NONE), 10_000, 10_001))
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePricing.checkPrice(at(Direction.NONE), 10_000, 9_999))
    //  برگشت به قیمتِ فهرست همیشه آزاد است
    for (d in Direction.entries) assertNull(LinePricing.checkPrice(at(d), 10_000, 10_000))
  }

  @Test
  fun reducingPriceIsADiscountForWhoeverMayNotDiscount() {
    val staff = on.copy(mayReduce = false)
    assertEquals(LinePricing.REDUCTION_DENIED, LinePricing.checkPrice(staff, 10_000, 9_000))
    assertNull(LinePricing.checkPrice(staff, 10_000, 11_000))
    assertEquals(LinePricing.REDUCTION_DENIED, LinePricing.checkDiscount(staff, 100))
    assertNull(LinePricing.checkDiscount(staff, 0))
    assertNull(LinePricing.checkDiscount(on, 100))
  }

  @Test
  fun flagOffOrOwnerKeepsTodaysFreedom() {
    val closed = Policy(on = true, direction = Direction.NONE, mayReduce = false, freeLineCapMinor = 0)
    for (p in listOf(closed.copy(on = false), closed.copy(exempt = true))) {
      assertNull(LinePricing.checkPrice(p, 10_000, 1))
      assertNull(LinePricing.checkPrice(p, 10_000, 99_000))
      assertNull(LinePricing.checkDiscount(p, 5_000))
      assertNull(LinePricing.checkFreeLine(p, 1_000_000))
    }
  }

  // ── کادرِ ردیف (POS-08، POS-20) ─────────────────────────────────

  @Test
  fun editWithFlagOffIgnoresDiscountAndRules() {
    val off = Policy(direction = Direction.NONE, mayReduce = false, freeLineCapMinor = 0)
    val e = LinePricing.edit(off, false, 10_000, 10_000, 5_000, 3_000, true, 5_000)
    assertEquals(LinePricing.Edit(null, 15_000, 0, null), e)
  }

  @Test
  fun editTurnsLineDiscountIntoTheUnitPrice() {
    val pct = LinePricing.edit(on, false, 10_000, 10_000, null, 3_000, true, 1_000)
    assertEquals(30_000, pct.grossMinor)
    assertEquals(3_000, pct.discountMinor)
    assertEquals(90.0, pct.netUnit)
    assertNull(pct.problem)
    //  مبلغ: ۱۰ افغانی از ۳ × ۱۰۰ ⇒ ۲۹۰، قیمتِ واحد ۹۶٫۶۶… و جمعِ ردیف دقیقاً ۲۹۰
    val amt = LinePricing.edit(on, false, 10_000, 10_000, null, 3_000, false, 1_000)
    assertEquals(1_000, amt.discountMinor)
    assertEquals(290, (amt.netUnit!! * 3.0).roundToLong())
    //  بی مقدار، بی تخفیف
    assertEquals(LinePricing.Edit(null, 0, 0, null), LinePricing.edit(on, false, 10_000, 10_000, null, 0, true, 1_000))
  }

  @Test
  fun editChecksOnlyANewPrice() {
    val upOnly = on.copy(direction = Direction.UP)
    //  قیمتِ پایین‌ترِ پیشین (پس از تخفیفِ ردیف) دست نخورد، فقط تعداد ⇒ پذیرفته
    assertNull(LinePricing.edit(upOnly, false, 10_000, 9_000, 9_000, 5_000, true, 0).problem)
    assertEquals(LinePricing.PRICE_DOWN_BLOCKED, LinePricing.edit(upOnly, false, 10_000, 9_000, 8_000, 5_000, true, 0).problem)
    assertNull(LinePricing.edit(upOnly, false, 10_000, 9_000, 12_000, 5_000, true, 0).problem)
    //  «قیمتِ خودِ کالا» همیشه پذیرفته است
    assertNull(LinePricing.edit(on.copy(direction = Direction.NONE), false, 10_000, 9_000, null, 5_000, true, 0).problem)
  }

  @Test
  fun editOfAFreeLineUsesItsCapNotTheDirection() {
    val capped = on.copy(direction = Direction.NONE, freeLineCapMinor = 5_000)
    assertNull(LinePricing.edit(capped, true, 4_000, 4_000, 5_000, 1_000, true, 0).problem)
    assertEquals(LinePricing.FREE_LINE_OVER_CAP, LinePricing.edit(capped, true, 4_000, 4_000, 5_100, 1_000, true, 0).problem)
    //  سقف پس از افزودن پایین آمد: قیمتِ دست‌نخورده رد نمی‌شود
    assertNull(LinePricing.edit(capped, true, 9_000, 9_000, 9_000, 2_000, true, 0).problem)
  }

  @Test
  fun editRefusesLineDiscountToWhoeverMayNotDiscount() {
    val staff = on.copy(mayReduce = false, direction = Direction.DOWN)
    assertEquals(LinePricing.REDUCTION_DENIED, LinePricing.edit(staff, false, 10_000, 10_000, null, 1_000, true, 500).problem)
    //  ردِ قیمت پیش از ردِ تخفیف گزارش می‌شود
    assertEquals(LinePricing.PRICE_UP_BLOCKED, LinePricing.edit(staff, false, 10_000, 10_000, 12_000, 1_000, true, 500).problem)
    assertNull(LinePricing.edit(staff, false, 10_000, 10_000, null, 1_000, true, 0).problem)
  }

  // ── قلمِ آزاد (POS-10) ────────────────────────────────────────

  @Test
  fun freeLineCapIsInclusiveAndZeroClosesIt() {
    assertNull(LinePricing.checkFreeLine(on, 9_999_999))
    val capped = on.copy(freeLineCapMinor = 5_000)
    assertNull(LinePricing.checkFreeLine(capped, 5_000))
    assertEquals(LinePricing.FREE_LINE_OVER_CAP, LinePricing.checkFreeLine(capped, 5_001))
    assertEquals(LinePricing.FREE_LINE_CLOSED, LinePricing.checkFreeLine(on.copy(freeLineCapMinor = 0), 100))
  }
}
