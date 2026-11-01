package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.Keypad.Entry
import ir.vil3ntec.tohid.core.pos.Keypad.Key
import ir.vil3ntec.tohid.core.pos.Keypad.Mode
import ir.vil3ntec.tohid.core.pos.Keypad.State
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 *  صفحه‌کلیدِ عددیِ لمسی با حالت‌های تعداد، قیمت و تخفیف — POS-07 (§۵.۳).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class KeypadTest {

  private fun type(s: State, keys: String): State {
    var x = s
    for (ch in keys) x = Keypad.press(
      x,
      when (ch) {
        '.' -> Key.DOT
        '<' -> Key.BACK
        'C' -> Key.CLEAR
        'Z' -> Key.D00
        else -> Key.valueOf("D$ch")
      },
    )
    return x
  }

  private fun qty(keys: String) = type(State(Mode.QTY), keys)
  private fun price(keys: String) = type(State(Mode.PRICE), keys)
  private fun disc(keys: String, percent: Boolean = true) = type(State(Mode.DISCOUNT, percent = percent), keys)

  // ── تایپ ────────────────────────────────────────────────────────

  @Test
  fun digitsAppendAndLeadingZeroIsDropped() {
    assertEquals("125", qty("125").text)
    assertEquals("5", qty("05").text)
    assertEquals("0", qty("00").text)
    assertEquals("0", qty("Z").text)
    assertEquals("100", qty("1Z").text)
  }

  @Test
  fun dotOnceAndLeadingDotBecomesZeroDot() {
    assertEquals("0.5", qty(".5").text)
    assertEquals("1.25", qty("1.2.5").text)
    assertEquals("0.", qty(".").text)
  }

  @Test
  fun decimalsAreCappedPerMode() {
    assertEquals("1.255", qty("1.2559").text)
    assertEquals("12.50", price("12.509").text)
    assertEquals("9.99", disc("9.999").text)
    //  «00» پس از ممیز فقط تا جای خالی
    assertEquals("1.50", price("1.5Z").text)
  }

  @Test
  fun wholeDigitsAreCapped() {
    assertEquals("999999999", qty("9999999999").text)
    assertEquals(Keypad.MAX_WHOLE_DIGITS, qty("12345678901234").text.length)
  }

  @Test
  fun backAndClear() {
    assertEquals("12", qty("123<").text)
    assertEquals("", qty("1<<").text)
    assertEquals("", qty("12C").text)
    assertEquals("7", qty("12C7").text)
  }

  @Test
  fun switchingModeClearsTheBufferButKeepsPercentChoice() {
    val s = Keypad.percent(type(State(Mode.DISCOUNT), "15"), false)
    val p = Keypad.mode(s, Mode.PRICE)
    assertEquals(Mode.PRICE, p.mode)
    assertEquals("", p.text)
    assertEquals(false, p.percent)
    //  همان حالت ⇒ دست نمی‌خورد
    assertEquals(s, Keypad.mode(s, Mode.DISCOUNT))
  }

  @Test
  fun unknownModeCodeIsQuantity() {
    assertEquals(Mode.QTY, Mode.of(null))
    assertEquals(Mode.QTY, Mode.of("x"))
    assertEquals(Mode.DISCOUNT, Mode.of(" discount "))
  }

  // ── عدد ────────────────────────────────────────────────────────

  @Test
  fun scaledReadsDigitByDigitWithoutDouble() {
    assertEquals(1_250L, Keypad.scaled("12.5", 2))
    assertEquals(1_250L, Keypad.scaled("1.25", 3))
    assertEquals(500L, Keypad.scaled(".5", 3))
    assertEquals(7_000L, Keypad.scaled("7", 3))
    assertEquals(0L, Keypad.scaled("0", 2))
    assertEquals(999_999_999_000L, Keypad.scaled("999999999", 3))
    assertNull(Keypad.scaled("", 2))
    assertNull(Keypad.scaled(".", 2))
    assertNull(Keypad.scaled("1.234", 2))
    assertNull(Keypad.scaled("1.2.3", 2))
    assertNull(Keypad.scaled("12a", 2))
    assertNull(Keypad.scaled("-5", 2))
    assertNull(Keypad.scaled("۱۲", 2))
    assertNull(Keypad.scaled("1234567890", 0))
  }

  @Test
  fun entryPerMode() {
    assertEquals(Entry.Empty, Keypad.entry(qty("")))
    assertEquals(Entry.Qty(2_500), Keypad.entry(qty("2.5")))
    assertEquals(Entry.Qty(250), Keypad.entry(qty(".25")))
    assertEquals(Entry.Price(11_550), Keypad.entry(price("115.5")))
    assertEquals(Entry.Price(0), Keypad.entry(price("0")))
    assertEquals(Entry.Discount(true, 1_000), Keypad.entry(disc("10")))
    assertEquals(Entry.Discount(false, 5_000), Keypad.entry(disc("50", percent = false)))
  }

  @Test
  fun zeroQuantityAndZeroDiscountAreRejected() {
    assertEquals(Entry.Invalid(Keypad.ZERO), Keypad.entry(qty("0")))
    assertEquals(Entry.Invalid(Keypad.ZERO), Keypad.entry(qty("0.000")))
    assertEquals(Entry.Invalid(Keypad.ZERO), Keypad.entry(disc("0")))
    //  «0.» همان صفر است
    assertEquals(Entry.Invalid(Keypad.ZERO), Keypad.entry(qty(".")))
    //  متنی که کلیدها نمی‌سازند (از بیرون) ⇒ عدد نیست
    assertEquals(Entry.Invalid(Keypad.BAD_NUMBER), Keypad.entry(State(Mode.PRICE, text = "1.2.3")))
    assertEquals(Entry.Invalid(Keypad.BAD_NUMBER), Keypad.entry(State(Mode.PRICE, text = "1.234")))
  }

  @Test
  fun percentOverHundredIsRejectedButAmountIsNot() {
    assertEquals(Entry.Discount(true, LinePricing.FULL_BP), Keypad.entry(disc("100")))
    assertEquals(Entry.Invalid(Keypad.PERCENT_OVER), Keypad.entry(disc("100.01")))
    assertEquals(Entry.Discount(false, 15_000), Keypad.entry(disc("150", percent = false)))
  }

  @Test
  fun plainRoundTrips() {
    assertEquals("12.5", Keypad.plain(1_250, 2))
    assertEquals("12", Keypad.plain(1_200, 2))
    assertEquals("0.05", Keypad.plain(5, 2))
    assertEquals("1.255", Keypad.plain(1_255, 3))
    assertEquals("7", Keypad.plain(7, 0))
    for (v in listOf(0L, 1L, 99L, 100L, 12_345L, 999_999_999_99L)) assertEquals(v, Keypad.scaled(Keypad.plain(v, 2), 2))
  }
}
