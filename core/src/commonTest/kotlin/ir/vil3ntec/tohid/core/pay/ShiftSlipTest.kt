package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.pay.ShiftSlip.Kind
import ir.vil3ntec.tohid.core.pay.ShiftSlip.Row
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 *  گزارشِ X/Z برای صفحه و کاغذ (PAY-15)، نمای شمارشِ کور (PAY-13) و شمارشِ هر روش در بستن (PAY-16).
 *  شیفتِ S12: اولِ صندوق ۲٬۰۰۰، فروشِ نقدِ ۱۵٬۳۰۰، خروجِ نقدِ ۵۰۰ («خرید شیر») و فروشِ HesabPay ِ ۱٬۴۵۰.
 */
class ShiftSlipTest {

  private fun <T> ok(r: PayResult<T>): T = when (r) {
    is PayResult.Ok -> r.value
    is PayResult.Err -> fail("خطای هسته: ${r.code}")
  }

  private fun s12(): Shift {
    var s = ok(ShiftCore.open(OpenShiftInput(id = "open-1", at = 1_000, float = 200_000, by = "rahim")))
    s = ok(ShiftCore.record(s, ShiftEventInput(id = "sale-1", type = "sale", at = 2_000, rows = listOf(ShiftRow("cash", 1_530_000)))))
    s = ok(ShiftCore.record(s, ShiftEventInput(id = "sale-2", type = "sale", at = 3_000, rows = listOf(ShiftRow("hesabpay", 145_000)))))
    s = ok(ShiftCore.record(s, ShiftEventInput(id = "sale-3", type = "sale", at = 3_500, rows = listOf(ShiftRow(Pay.ACCOUNT, 30_000)))))
    s = ok(
      ShiftCore.record(
        s, ShiftEventInput(id = "out-1", type = "cash_out", at = 4_000, amount = 50_000, reason = "petty_expense", note = "خرید شیر"),
      ),
    )
    return s
  }

  /** شمارشِ کورِ S12: ۱۰×۱۰۰۰، ۱۲×۵۰۰، ۷×۱۰۰، ۱×۵۰، ۲×۲۰ = ۱۶٬۷۹۰ */
  private val s12Counts = mapOf("100000" to 10L, "50000" to 12L, "10000" to 7L, "5000" to 1L, "2000" to 2L)

  private fun x() = ok(ShiftCore.xReport(s12(), 5_000, "rahim"))

  private fun z(methodCounts: Map<String, Long> = emptyMap(), blind: Boolean = true, reason: String? = null): ShiftReport =
    ok(
      ShiftCore.close(
        s12(), CloseShiftInput(id = "close-1", at = 9_000, counts = s12Counts, methodCounts = methodCounts, by = "rahim", blind = blind, reason = reason),
      ),
    ).z!!

  @Test
  fun cashierXReportHidesExpectedCash() {
    val x = x()
    assertEquals(1_680_000L, x.expected!![Pay.CASH])
    //  همان باگِ صفحه: نمای صندوق‌دارِ هسته فقط Zِ کور را می‌پوشاند و X را دست‌نخورده می‌گذارد
    assertNotNull(ShiftCore.cashierView(x).expected)
    val seen = ShiftSlip.forViewer(x, seesExpected = false)
    assertNull(seen.expected)
    assertNull(seen.variance)
    assertTrue(ShiftSlip.rows(seen).none { it.kind == Kind.EXPECTED || it.kind == Kind.VARIANCE })
    assertEquals(x, ShiftSlip.forViewer(x, seesExpected = true))
  }

  @Test
  fun xRowsFollowTheScreenOrder() {
    assertEquals(
      listOf(
        Row(Kind.OPENED, amount = 1_000),
        Row(Kind.AT, amount = 5_000),
        Row(Kind.BY, text = "rahim"),
        Row(Kind.FLOAT, amount = 200_000),
        Row(Kind.SALES, "cash", 1_530_000),
        Row(Kind.SALES, "hesabpay", 145_000),
        Row(Kind.SALES, Pay.ACCOUNT, 30_000),
        Row(Kind.CASH_IN, amount = 0),
        Row(Kind.CASH_OUT, amount = 50_000),
        Row(Kind.NO_SALE, count = 0),
        Row(Kind.EXPECTED, "cash", 1_680_000, strong = true),
        Row(Kind.EXPECTED, "hesabpay", 145_000),
      ),
      ShiftSlip.rows(x()),
    )
  }

  @Test
  fun zRowsCarryEveryMethodCount() {
    val z = z(methodCounts = mapOf("hesabpay" to 145_000L))
    val rows = ShiftSlip.rows(z)
    assertEquals(Row(Kind.CLOSED, amount = 9_000), rows[1])
    assertEquals(
      listOf(
        Row(Kind.DENOMINATION, "cash", 1_000_000, count = 10, note = 100_000),
        Row(Kind.DENOMINATION, "cash", 600_000, count = 12, note = 50_000),
        Row(Kind.DENOMINATION, "cash", 70_000, count = 7, note = 10_000),
        Row(Kind.DENOMINATION, "cash", 5_000, count = 1, note = 5_000),
        Row(Kind.DENOMINATION, "cash", 4_000, count = 2, note = 2_000),
        Row(Kind.COUNTED, "cash", 1_679_000, strong = true),
        Row(Kind.COUNTED, "hesabpay", 145_000),
        Row(Kind.VARIANCE, "cash", -1_000),
        Row(Kind.VARIANCE, "hesabpay", 0),
        Row(Kind.NEEDS_REVIEW),
      ),
      rows.dropWhile { it.kind != Kind.DENOMINATION },
    )
    //  شمارشِ کور: صندوق‌دار شمرده‌ها را می‌بیند، نه مورد انتظار و اختلاف
    val blind = ShiftSlip.rows(ShiftSlip.forViewer(z, seesExpected = false))
    assertTrue(blind.none { it.kind == Kind.EXPECTED || it.kind == Kind.VARIANCE })
    assertEquals(2, blind.count { it.kind == Kind.COUNTED })
  }

  @Test
  fun uncountedMethodStaysUncountedNotZero() {
    val rows = ShiftSlip.rows(z())
    assertEquals(Row(Kind.VARIANCE, "hesabpay", null), rows.single { it.kind == Kind.VARIANCE && it.method == "hesabpay" })
    assertEquals(1, rows.count { it.kind == Kind.COUNTED })
  }

  @Test
  fun reasonAndNoReviewWhenExact() {
    val exact = mapOf("100000" to 16L, "50000" to 1L, "10000" to 3L)  // ۱۶٬۸۰۰
    val z = ok(ShiftCore.close(s12(), CloseShiftInput(id = "c", at = 9_000, counts = exact, reason = "  شمارشِ دوباره "))).z!!
    val rows = ShiftSlip.rows(z)
    assertEquals(Row(Kind.REASON, text = "شمارشِ دوباره"), rows.single { it.kind == Kind.REASON })
    assertTrue(rows.none { it.kind == Kind.NEEDS_REVIEW })
  }

  @Test
  fun onlyNonCashMethodsWithActivityAreCounted() {
    assertEquals(listOf("hesabpay"), ShiftSlip.countable(x()))
    val cashOnly = ok(ShiftCore.open(OpenShiftInput(id = "o", at = 1, float = 0)))
    assertEquals(emptyList(), ShiftSlip.countable(ok(ShiftCore.xReport(cashOnly, 2))))
  }

  @Test
  fun methodCountTextBecomesPul() {
    val allowed = listOf("hesabpay", "afpay_card")
    assertEquals(
      mapOf("hesabpay" to 145_000L, "afpay_card" to 0L),
      ok(ShiftSlip.methodCounts(linkedMapOf("hesabpay" to " 1450 ", "afpay_card" to "0"), allowed)),
    )
    assertEquals(mapOf("hesabpay" to 1_250L), ok(ShiftSlip.methodCounts(mapOf("hesabpay" to "12.5"), allowed)))
    assertEquals(mapOf("hesabpay" to 1L), ok(ShiftSlip.methodCounts(mapOf("hesabpay" to "0.005"), allowed)))
    //  کادرِ خالی یعنی «شمرده نشد»، نه صفر
    assertEquals(emptyMap(), ok(ShiftSlip.methodCounts(mapOf("hesabpay" to "  ", "afpay_card" to ""), allowed)))
  }

  @Test
  fun badMethodCountIsRefusedWithItsMethod() {
    for (bad in listOf("abc", "1e3", "-5", "1.2.3", ".", "Infinity", "۱۲")) {
      val r = ShiftSlip.methodCounts(mapOf("hesabpay" to bad), listOf("hesabpay"))
      assertEquals(PayResult.Err("INVALID_COUNT", method = "hesabpay"), r, "«$bad»")
    }
    assertEquals(
      PayResult.Err("UNKNOWN_METHOD", method = "m_paisa"),
      ShiftSlip.methodCounts(mapOf("m_paisa" to "10"), listOf("hesabpay")),
    )
  }

  @Test
  fun methodShortfallOverThresholdNeedsReason() {
    //  HesabPay ۱٬۳۵۰ شمرده شد: کسریِ ۱۰۰ از آستانهٔ ۵۰ بیشتر است ⇒ مدیر بی دلیل نمی‌بندد (PAY-14)؛ کور: بخشِ پایین
    val counts = ok(ShiftSlip.methodCounts(mapOf("hesabpay" to "1350"), ShiftSlip.countable(x())))
    val exact = mapOf("100000" to 16L, "50000" to 1L, "10000" to 3L)
    val r = ShiftCore.close(s12(), CloseShiftInput(id = "c", at = 9_000, counts = exact, methodCounts = counts))
    assertEquals("REASON_REQUIRED", (r as PayResult.Err).code)
    assertEquals(-10_000L, r.variance!!["hesabpay"])
    val z = ok(ShiftCore.close(s12(), CloseShiftInput(id = "c", at = 9_000, counts = exact, methodCounts = counts, reason = "رسیدِ گم‌شده"))).z!!
    assertEquals(-10_000L, z.variance!!["hesabpay"])
    assertEquals(0L, z.variance!![Pay.CASH])
  }

  /* ── ⛔ شمارشِ کور بی نشت (پس از #536): نه از X، نه از پاسخِ بستن، نه از Z ── */

  /** نقدِ موردِ انتظارِ S12 = ۲٬۰۰۰ + ۱۵٬۳۰۰ − ۵۰۰ */
  private val s12Expected = 1_680_000L

  /** شمرده‌ای که با موردِ انتظار [delta] پول فاصله دارد (اسکناسِ ۱ پولی نیست؛ هر ۵۰ پول = یک ۵۰ پولی) */
  private fun cashOff(delta: Long): Map<String, Long> = mapOf("5000" to (s12Expected + delta) / 5_000)

  private fun blindClose(cashDelta: Long, hesabpay: Long? = null) = ShiftCore.close(
    s12(), CloseShiftInput(id = "c", at = 9_000, counts = cashOff(cashDelta), methodCounts = hesabpay?.let { mapOf("hesabpay" to it) }.orEmpty(), blind = true),
  )

  @Test
  fun blindXCarriesNoShiftMoneyOnlyCounts() {
    val rows = ShiftSlip.rows(ShiftSlip.forViewer(x(), seesExpected = false))
    val money = setOf(Kind.FLOAT, Kind.SALES, Kind.RECEIPTS, Kind.REFUNDS, Kind.CASH_IN, Kind.CASH_OUT, Kind.EXPECTED, Kind.VARIANCE)
    //  اولِ صندوق + فروش/رسید/مرجوعیِ هر روش + ورود − خروج همان «مورد انتظار» است ⇒ هیچ‌کدام مبلغ ندارند
    assertEquals(emptyList(), rows.filter { it.kind in money && it.amount != null })
    assertTrue(rows.none { it.kind == Kind.FLOAT || it.method != null })
    assertEquals(
      listOf(
        Row(Kind.SALES, count = 3), Row(Kind.RECEIPTS, count = 0), Row(Kind.REFUNDS, count = 0),
        Row(Kind.CASH_IN, count = 0), Row(Kind.CASH_OUT, count = 1), Row(Kind.NO_SALE, count = 0),
      ),
      rows.filter { it.kind in money || it.kind == Kind.NO_SALE },
    )
    //  همان نما برای هر فیلدِ پولی که برنامه مستقیم بخواند
    val seen = ShiftSlip.forViewer(x(), seesExpected = false)
    assertEquals(0L, seen.float)
    assertEquals(emptyMap(), seen.methods)
    assertEquals(Moves(0, 0), seen.moves)
  }

  @Test
  fun blindCloseAnswersAlikeWhateverTheVariance() {
    //  آستانه ۵۰ افغانی (۵٬۰۰۰ پول): زیر، روی و بالای آن، نقد یا HesabPay — پاسخ یکی است، پس عوض کردنِ عدد چیزی را پیدا نمی‌کند
    val answers = listOf(
      blindClose(0), blindClose(-5_000), blindClose(-10_000), blindClose(+500_000), blindClose(0, hesabpay = 135_000), blindClose(0, hesabpay = 145_000),
    )
    assertTrue(answers.all { it is PayResult.Ok }, "$answers")
    //  بالای آستانه بی دلیل ⇒ پذیرفته، ولی برای مدیر علامت می‌خورد و اختلاف در Zِ کامل می‌ماند
    val z = ok(blindClose(-10_000)).z!!
    assertEquals(-10_000L, z.variance!![Pay.CASH])
    assertEquals(true, z.needsReview)
    assertNull(z.reason)
    //  بستنِ مدیر (نه کور) همان PAY-14 است
    val manager = ShiftCore.close(s12(), CloseShiftInput(id = "c", at = 9_000, counts = cashOff(-10_000)))
    assertEquals(PayResult.Err("REASON_REQUIRED", variance = mapOf(Pay.CASH to -10_000L, "hesabpay" to null)), manager)
  }

  @Test
  fun blindZShowsNeitherReviewNorReason() {
    for (delta in listOf(0L, -5_000L, -10_000L)) {
      val z = ok(
        ShiftCore.close(s12(), CloseShiftInput(id = "c", at = 9_000, counts = cashOff(delta), blind = true, reason = "پولِ خرد")),
      ).z!!
      val seen = ShiftSlip.forViewer(z, seesExpected = false)
      assertNull(seen.needsReview)
      assertNull(seen.reason)
      val rows = ShiftSlip.rows(seen)
      assertTrue(rows.none { it.kind == Kind.NEEDS_REVIEW || it.kind == Kind.REASON || it.kind == Kind.VARIANCE }, "$delta: $rows")
      assertEquals(Row(Kind.COUNTED, Pay.CASH, s12Expected + delta, strong = true), rows.single { it.kind == Kind.COUNTED })
      //  مدیر همه را می‌بیند
      assertEquals(delta != 0L, ShiftSlip.rows(z).any { it.kind == Kind.NEEDS_REVIEW })
      assertEquals(Row(Kind.REASON, text = "پولِ خرد"), ShiftSlip.rows(z).single { it.kind == Kind.REASON })
    }
    //  نمای کورِ هسته (`cashierView`) هم در ردیف‌ها نه بررسی را لو می‌دهد نه دلیل را
    val core = ShiftCore.cashierView(ok(blindClose(-10_000)).z!!)
    assertTrue(ShiftSlip.rows(core).none { it.kind == Kind.NEEDS_REVIEW || it.kind == Kind.REASON || it.kind == Kind.FLOAT })
  }
}
