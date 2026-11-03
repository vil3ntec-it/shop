package ir.vil3ntec.tohid.core.pos

import ir.vil3ntec.tohid.core.pos.TouchKeyboard.Key
import ir.vil3ntec.tohid.core.pos.TouchKeyboard.Layout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  صفحه‌کلیدِ متنیِ روی‌صفحهٔ صندوقِ کامپیوترِ لمسی — POS-31، S18 گامِ ۳ (§۵.۳).
 *  نام‌ها camelCase اند: کامپایلرِ iOS نامِ آزمون با فاصله و نشانه نمی‌پذیرد.
 */
class TouchKeyboardTest {

  private fun letters(layout: Layout): Set<String> =
    TouchKeyboard.rows(layout).flatten().filterIsInstance<Key.Letter>().map { it.text }.toSet()

  private fun type(text: String, layout: Layout = Layout.FA, start: String = ""): String {
    val keys = letters(layout)
    var out = start
    for (ch in text) {
      val key = if (ch == ' ') Key.Space else if (ch.toString() == TouchKeyboard.ZWNJ) Key.HalfSpace else Key.Letter(ch.toString()).also {
        assertTrue(it.text in keys, "«$ch» در چیدمانِ $layout نیست")
      }
      out = TouchKeyboard.apply(out, key)
    }
    return out
  }

  @Test
  fun persianDariLayoutHasEveryPersianLetterAndDigit() {
    val fa = letters(Layout.FA)
    for (ch in "آابپتثجچحخدذرزژسشصضطظعغفقکگلمنوهیئ۰۱۲۳۴۵۶۷۸۹") assertTrue(ch.toString() in fa, "«$ch»")
    assertEquals("پنیر", type("پنیر"))
    assertEquals("گل${TouchKeyboard.ZWNJ}احمد", type("گل${TouchKeyboard.ZWNJ}احمد"))
  }

  @Test
  fun pashtoLayoutHasPashtoLetters() {
    val ps = letters(Layout.PS)
    for (ch in "ټډړږښځڅڼېۍګئ") assertTrue(ch.toString() in ps, "«$ch»")
    assertEquals("ګل ډوډۍ", type("ګل ډوډۍ", Layout.PS))
  }

  @Test
  fun latinLayoutHasLettersAndDigitsButNoHalfSpace() {
    val en = letters(Layout.LATIN)
    for (ch in "abcdefghijklmnopqrstuvwxyz0123456789") assertTrue(ch.toString() in en, "«$ch»")
    assertFalse(Key.HalfSpace in TouchKeyboard.rows(Layout.LATIN).last())
    assertTrue(Key.HalfSpace in TouchKeyboard.rows(Layout.FA).last())
  }

  @Test
  fun specialRowIsLastAndHasLayoutSpaceBackspaceClear() {
    for (l in Layout.entries) {
      val last = TouchKeyboard.rows(l).last()
      assertTrue(last.containsAll(listOf(Key.NextLayout, Key.Space, Key.Backspace, Key.Clear)), "$l")
      assertTrue(TouchKeyboard.rows(l).dropLast(1).flatten().all { it is Key.Letter }, "$l")
    }
  }

  @Test
  fun spaceAndHalfSpaceNeverLeadOrRepeat() {
    assertEquals("", TouchKeyboard.apply("", Key.Space))
    assertEquals("", TouchKeyboard.apply("", Key.HalfSpace))
    assertEquals("نان ", TouchKeyboard.apply("نان ", Key.Space))
    assertEquals("نان ", TouchKeyboard.apply("نان ", Key.HalfSpace))
    assertEquals("می${TouchKeyboard.ZWNJ}", TouchKeyboard.apply("می${TouchKeyboard.ZWNJ}", Key.Space))
    assertEquals("نان ", TouchKeyboard.apply("نان", Key.Space))
  }

  @Test
  fun backspaceClearAndLayoutKeys() {
    assertEquals("پنی", TouchKeyboard.apply("پنیر", Key.Backspace))
    assertEquals("", TouchKeyboard.apply("", Key.Backspace))
    assertEquals("گل", TouchKeyboard.apply("گل${TouchKeyboard.ZWNJ}", Key.Backspace))
    assertEquals("", TouchKeyboard.apply("پنیر", Key.Clear))
    assertEquals("پنیر", TouchKeyboard.apply("پنیر", Key.NextLayout))
  }

  @Test
  fun maxLengthIsNeverPassed() {
    val full = "ا".repeat(TouchKeyboard.MAX)
    assertEquals(full, TouchKeyboard.apply(full, Key.Letter("ب")))
    assertEquals(full, TouchKeyboard.apply(full, Key.Space))
    assertEquals("اب", TouchKeyboard.apply("ا", Key.Letter("ب"), max = 2))
    assertEquals("اب", TouchKeyboard.apply("اب", Key.Letter("پ"), max = 2))
  }

  @Test
  fun layoutCyclesAndStartsFromAppLanguage() {
    assertEquals(Layout.PS, TouchKeyboard.next(Layout.FA))
    assertEquals(Layout.LATIN, TouchKeyboard.next(Layout.PS))
    assertEquals(Layout.FA, TouchKeyboard.next(Layout.LATIN))
    assertEquals(Layout.FA, TouchKeyboard.initial("fa"))
    assertEquals(Layout.FA, TouchKeyboard.initial("prs"))
    assertEquals(Layout.PS, TouchKeyboard.initial("ps"))
    assertEquals(Layout.LATIN, TouchKeyboard.initial("EN"))
  }
}
