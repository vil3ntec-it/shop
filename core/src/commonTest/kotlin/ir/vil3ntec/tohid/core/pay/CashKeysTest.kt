package ir.vil3ntec.tohid.core.pay

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PAY-03 (دکمه و کلیدِ اسکناس) و PAY-04 (نگهبانِ Enter ِ پنلِ پرداخت) — جدولِ «پنلِ پرداخت» در بخشِ ۵.۲.
 */
class CashKeysTest {

  @Test
  fun shiftDigitsOneToSixAreTheSixNotes() {
    assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 50_000L, 100_000L), CashKeys.NOTES)
    assertEquals(listOf(1_000L, 2_000L, 5_000L, 10_000L, 50_000L, 100_000L), (1..6).map { CashKeys.noteForDigit(it) })
    assertNull(CashKeys.noteForDigit(0))
    assertNull(CashKeys.noteForDigit(7))
    assertNull(CashKeys.noteForDigit(-1))
  }

  @Test
  fun eachNoteAddsToCashReceived() {
    //  فاکتورِ ۲۴۵۰: مشتری ۱۰۰۰ + ۱۰۰۰ + ۵۰۰ می‌دهد ⇒ دریافتیِ ۲۵۰۰
    var text = ""
    for (note in listOf(100_000L, 100_000L, 50_000L)) text = CashKeys.addNote(text, note)!!
    assertEquals("2500", text)
    assertEquals("10", CashKeys.addNote("", 1_000L))
    assertEquals("20", CashKeys.addNote("0", 2_000L))
    assertEquals("1050", CashKeys.addNote("1000", 5_000L))
  }

  @Test
  fun typedPulsAreKeptExactly() {
    assertEquals("909.6", CashKeys.addNote("899.6", 1_000L))
    assertEquals("10.05", CashKeys.addNote("0.05", 1_000L))
    assertEquals("1100.4", CashKeys.addNote("100.4", 100_000L))
    assertEquals("22", CashKeys.addNote("12.", 1_000L))
    assertEquals("10.5", CashKeys.addNote(".5", 1_000L))
    assertEquals("60", CashKeys.addNote(" 10 ", 5_000L))
  }

  @Test
  fun invalidTextIsLeftAlone() {
    for (bad in listOf("abc", "1.2.3", "-5", "1e5", "Infinity", "NaN", "0x10", "10d", ".", "۱۰", "1,000")) {
      assertNull(CashKeys.addNote(bad, 1_000L), "«$bad»")
      assertNull(CashKeys.cashMinor(bad), "«$bad»")
    }
  }

  @Test
  fun onlyTheSixNotesAreButtons() {
    for (n in listOf(0L, -1_000L, 100L, 200L, 500L, 1_500L, 20_000L, 200_000L)) {
      assertNull(CashKeys.addNote("100", n), "اسکناسِ $n")
    }
  }

  @Test
  fun sumBeyondTheExactRangeIsRefused() {
    //  ۲^۵۳−۱ پول = 90071992547409.91 افغانی
    assertEquals("90071992547409.91", CashKeys.addNote("90071992547399.91", 1_000L))
    assertNull(CashKeys.addNote("90071992547400", 1_000L))
    assertNull(CashKeys.addNote("90071992547409.91", 1_000L))
    assertNull(CashKeys.addNote("99999999999999999999", 1_000L))
  }

  @Test
  fun cashTextIsReadExactlyToPul() {
    assertEquals(0L, CashKeys.cashMinor(""))
    assertEquals(0L, CashKeys.cashMinor("   "))
    assertEquals(0L, CashKeys.cashMinor("0"))
    assertEquals(89_960L, CashKeys.cashMinor("899.6"))
    assertEquals(5L, CashKeys.cashMinor("0.05"))
    assertEquals(1_200L, CashKeys.cashMinor("12."))
    assertEquals(50L, CashKeys.cashMinor(".5"))
    assertEquals(700L, CashKeys.cashMinor("007"))
    assertEquals(1_250L, CashKeys.cashMinor("0000000000000000012.5"))
    //  سقفِ دقیق بی Double: در آیفون (Kotlin/Native) خواندنِ Double نزدیکِ ۲^۵۳ با JVM یکی نبود
    assertEquals(9_007_199_254_740_991L, CashKeys.cashMinor("90071992547409.91"))
    assertNull(CashKeys.cashMinor("90071992547409.92"))
    assertNull(CashKeys.cashMinor("99999999999999"))
    assertNull(CashKeys.cashMinor("999999999999999"))
    //  کمتر از یک پول پول نیست
    assertNull(CashKeys.cashMinor("1.005"))
    assertNull(CashKeys.cashMinor("0.001"))
    assertNull(CashKeys.addNote("1.005", 1_000L))
  }

  @Test
  fun enterWaitsFourHundredMillisecondsAfterOpening() {
    assertEquals(400L, CashKeys.ENTER_GUARD_MS)
    assertFalse(CashKeys.enterArmed(0))
    assertFalse(CashKeys.enterArmed(399))
    assertTrue(CashKeys.enterArmed(400))
    assertTrue(CashKeys.enterArmed(5_000))
    assertFalse(CashKeys.enterArmed(-1))
  }
}
