package ir.vil3ntec.tohid.core.returns

import ir.vil3ntec.tohid.core.returns.ReturnPolicy.Item
import ir.vil3ntec.tohid.core.returns.ReturnPolicy.Policy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  سیاستِ برگشت: مهلت به روز و اقلامِ غیرقابلِ برگشت — RET-09.
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class ReturnPolicyTest {

  private val rice = Item("rice", name = "برنج", category = "خوراکی", barcodes = listOf("6261234567890"))
  private val soap = Item("soap", name = "صابون", category = "بهداشتی", subcategory = "شخصی")
  private val card = Item("card", name = "کارتِ شارژ", category = "کارت")

  @Test
  fun emptyPolicyIsOffAndAcceptsEverything() {
    assertFalse(Policy().on)
    val v = ReturnPolicy.check(Policy(), "2020-01-01", "2026-10-31", listOf(rice, soap, card))
    assertTrue(v.ok)
    assertEquals(ReturnPolicy.Verdict(), v)
  }

  @Test
  fun windowCountsSaleDayAsZeroAndLastDayIsStillInside() {
    val p = Policy(days = 7)
    assertTrue(ReturnPolicy.check(p, "2026-10-31", "2026-10-31", listOf(rice)).ok)
    assertTrue(ReturnPolicy.check(p, "2026-10-24", "2026-10-31", listOf(rice)).ok)
    val late = ReturnPolicy.check(p, "2026-10-23", "2026-10-31", listOf(rice))
    assertTrue(late.late)
    assertEquals(8, late.ageDays)
    assertEquals(listOf(ReturnPolicy.LATE), late.reasons)
  }

  @Test
  fun windowCrossesMonthAndYearAndLeapDay() {
    assertEquals(1, ReturnPolicy.age("2024-02-28", "2024-02-29"))
    assertEquals(2, ReturnPolicy.age("2024-02-28", "2024-03-01"))
    assertEquals(1, ReturnPolicy.age("2025-12-31", "2026-01-01"))
    assertEquals(30, ReturnPolicy.age("2026-10-01T10:00:00", "2026-10-31"))
    assertNull(ReturnPolicy.age("", "2026-10-31"))
    assertNull(ReturnPolicy.age("2026-02-30", "2026-10-31"))
  }

  @Test
  fun noReceiptFutureAndBrokenDatesAreNotLate() {
    val p = Policy(days = 3)
    assertTrue(ReturnPolicy.check(p, null, "2026-10-31", listOf(rice)).ok)
    //  ساعتِ دستگاه عقب: فاکتور «فردا» است — دیر نیست
    assertTrue(ReturnPolicy.check(p, "2026-11-01", "2026-10-31", listOf(rice)).ok)
    assertTrue(ReturnPolicy.check(p, "not-a-date", "2026-10-31", listOf(rice)).ok)
  }

  @Test
  fun blockedByCategorySubcategoryNameOrBarcode() {
    val items = listOf(rice, soap, card)
    assertEquals(listOf("soap"), ReturnPolicy.check(Policy(blocked = listOf("بهداشتی")), null, "2026-10-31", items).blockedIds)
    assertEquals(listOf("soap"), ReturnPolicy.check(Policy(blocked = listOf("شخصی")), null, "2026-10-31", items).blockedIds)
    assertEquals(listOf("card"), ReturnPolicy.check(Policy(blocked = listOf("کارت شارژ")), null, "2026-10-31", items).blockedIds)
    assertEquals(listOf("rice"), ReturnPolicy.check(Policy(blocked = listOf("۶۲۶۱۲۳۴۵۶۷۸۹۰")), null, "2026-10-31", items).blockedIds)
    //  حروفِ عربی و نیم‌فاصله همان کلید را دارند
    assertEquals(listOf("card"), ReturnPolicy.check(Policy(blocked = listOf("كارت")), null, "2026-10-31", items).blockedIds)
    //  بخشی از نام نمی‌گیرد
    assertTrue(ReturnPolicy.check(Policy(blocked = listOf("برن")), null, "2026-10-31", items).ok)
  }

  @Test
  fun bothReasonsTogetherAndNoDuplicates() {
    val v = ReturnPolicy.check(Policy(days = 1, blocked = listOf("بهداشتی", "صابون")), "2026-10-01", "2026-10-31", listOf(soap, soap, rice))
    assertEquals(listOf("soap"), v.blockedIds)
    assertEquals(listOf(ReturnPolicy.LATE, ReturnPolicy.NOT_RETURNABLE), v.reasons)
    assertFalse(v.ok)
  }

  @Test
  fun daysParsing() {
    assertEquals(0, ReturnPolicy.days(null))
    assertEquals(0, ReturnPolicy.days(""))
    assertEquals(0, ReturnPolicy.days("0"))
    assertEquals(0, ReturnPolicy.days("-3"))
    assertEquals(0, ReturnPolicy.days("7.5"))
    assertEquals(7, ReturnPolicy.days(" ۷ "))
    assertEquals(14, ReturnPolicy.days("14"))
    assertEquals(ReturnPolicy.MAX_DAYS, ReturnPolicy.days("99999"))
    assertEquals(0, ReturnPolicy.days("12345678"))
  }

  @Test
  fun rulesParsingDropsBlankLongAndDuplicateLines() {
    val long = "x".repeat(ReturnPolicy.MAX_RULE_LENGTH + 1)
    //  نوشتهٔ کاربر همان می‌ماند (فاصله‌ها یکی)؛ یکی‌سازیِ حروف فقط در کلیدِ مقایسه است
    assertEquals(listOf("بهداشتی", "كارت شارژ"), ReturnPolicy.parseRules("  بهداشتی \n\n$long\nكارت   شارژ\r\nبهداشتي"))
    assertEquals(emptyList<String>(), ReturnPolicy.parseRules(null))
    assertEquals(ReturnPolicy.MAX_RULES, ReturnPolicy.parseRules((1..500).joinToString("\n") { "r$it" }).size)
    assertEquals("بهداشتی\nکارت", ReturnPolicy.formatRules(listOf("بهداشتی", "", "کارت", "بهداشتی")))
  }
}
