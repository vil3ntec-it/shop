package ir.vil3ntec.tohid.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** OPS-02 — گام‌های صفحهٔ «عبارتِ بازیابی» (`RecoveryPhraseFlow`) */
class RecoveryPhraseFlowTest {

  private val fixed = "K7QM2-Z9P4X-DH6VN-B3RTC-8WJF2"
  private fun flow() = RecoveryPhraseFlow { fixed }

  @Test
  fun showOnceConfirmByTypingThenHandTheKeyMaterialOutOnce() {
    val f = flow()
    assertEquals(RecoveryPhraseFlow.Step.INTRO, f.step)
    assertNull(f.shown, "پیش از «نشان بده» هیچ")
    f.start()
    assertEquals(fixed, f.shown)
    assertEquals(listOf("K7QM2", "Z9P4X", "DH6VN", "B3RTC", "8WJF2"), f.groups)
    assertEquals(RecoveryPhraseFlow.Check.NOT_ASKED, f.confirm(fixed), "پیش از «نوشتم» تأیید نیست")
    f.written()
    assertNull(f.shown, "در گامِ تأیید عبارت دیده نمی‌شود")
    assertTrue(f.printRows("s", "d", "k", "h").isEmpty(), "چاپ فقط در گامِ نمایش")
    assertNull(f.takeConfirmed(), "پیش از تأیید هیچ")
    assertEquals(RecoveryPhraseFlow.Check.TYPO, f.confirm("K7QM2-Z9P4X-DH6VN-B3RTC-8WJF3"), "نویسهٔ وارسی")
    assertEquals(RecoveryPhraseFlow.Check.TYPO, f.confirm("K7QM2"))
    //  عبارتِ درستِ دیگر (وارسیِ درست) هم پذیرفته نیست
    val other = RecoveryPhrase.generate { n -> ByteArray(n) { 1 } }
    assertEquals(RecoveryPhraseFlow.Check.MISMATCH, f.confirm(other))
    assertEquals(RecoveryPhraseFlow.Step.CONFIRM, f.step)
    //  هر شکلِ نوشتن: کوچک، فاصله، رقمِ فارسی
    assertEquals(RecoveryPhraseFlow.Check.OK, f.confirm("k۷qm۲ z9p4x dh6vn b3rtc 8wjf2"))
    assertEquals(RecoveryPhraseFlow.Step.DONE, f.step)
    assertNull(f.shown, "پس از پایان هرگز دوباره دیده نمی‌شود")
    assertEquals(fixed.replace("-", ""), f.takeConfirmed())
    assertNull(f.takeConfirmed(), "یک بار")
  }

  @Test
  fun backBeforeConfirmShowsTheSamePhraseAndCancelDropsIt() {
    val f = flow()
    f.start(); f.written(); f.back()
    assertEquals(RecoveryPhraseFlow.Step.SHOW, f.step)
    assertEquals(fixed, f.shown, "همان عبارت، نه تازه")
    f.start()
    assertEquals(fixed, f.shown)
    f.cancel()
    assertEquals(RecoveryPhraseFlow.Step.CANCELLED, f.step)
    assertNull(f.shown)
    assertNull(f.takeConfirmed())
    assertEquals(RecoveryPhraseFlow.Check.NOT_ASKED, f.confirm(fixed))
  }

  @Test
  fun everyFlowMakesAFreshValidPhrase() {
    val a = RecoveryPhraseFlow().apply { start() }.shown!!
    val b = RecoveryPhraseFlow().apply { start() }.shown!!
    assertTrue(RecoveryPhrase.normalize(a) != null && RecoveryPhrase.normalize(b) != null)
    assertTrue(a != b)
  }

  @Test
  fun printSheetHasThePhraseOnTopAndTheKeepingAdvice() {
    val f = flow().apply { start() }
    val rows = f.printRows("دکانِ کریمی", "۱۴۰۵/۰۸/۱۹", "جای امن", "چگونه")
    assertEquals(listOf(fixed), rows.first(), "سرِ جدول خودِ عبارت")
    assertTrue(rows.all { it.size == 1 }, "یک ستونِ تمام‌عرض")
    assertEquals(listOf(listOf(fixed), listOf("دکانِ کریمی"), listOf("۱۴۰۵/۰۸/۱۹"), listOf("جای امن"), listOf("چگونه")), rows)
    assertEquals(4, flow().apply { start() }.printRows("", "d", "k", "h").size, "بی نامِ دکان")
  }

  /** OPS-02 — پنجرهٔ امن (`FLAG_SECURE`) فقط تا عبارت روی صفحه است: نشان دادن و نوشتنِ دوباره، نه «چرا» و نه پس از بستن */
  @Test
  fun windowIsSecureOnlyWhileThePhraseIsOnScreen() {
    val f = flow()
    val seen = mutableListOf(RecoveryPhraseFlow.Step.secret(f.step))
    f.start(); seen += RecoveryPhraseFlow.Step.secret(f.step)
    f.written(); seen += RecoveryPhraseFlow.Step.secret(f.step)
    f.cancel(); seen += RecoveryPhraseFlow.Step.secret(f.step)
    assertEquals(listOf(false, true, true, false), seen)
    assertEquals(
      setOf(RecoveryPhraseFlow.Step.SHOW, RecoveryPhraseFlow.Step.CONFIRM, RecoveryPhraseFlow.Step.DONE),
      RecoveryPhraseFlow.Step.entries.filter { RecoveryPhraseFlow.Step.secret(it) }.toSet(),
    )
  }
}
