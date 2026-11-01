package ir.vil3ntec.tohid.core.debt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  POS-39: دریافتِ قسط یا بدهیِ مشتری از خودِ صندوق. پیش‌فرض همهٔ بدهی (تا پول، بی گردِ افغانی)؛
 *  بیش از بدهی فقط با تأییدِ «پیش‌پرداخت»؛ ثبتِ دوباره با همان کلید اثرِ دوم ندارد.
 */
class TillReceiveTest {

  @Test
  fun suggestedAmountIsWholeDebtToThePul() {
    assertEquals(89_960L, TillReceive.suggestedMinor(89_960))
    assertEquals(0L, TillReceive.suggestedMinor(0))
    //  بستانکار (پیش‌پرداخت دارد) ⇒ پیشنهادی نیست
    assertEquals(0L, TillReceive.suggestedMinor(-5_000))
  }

  @Test
  fun badAmountsAreRefused() {
    assertEquals(TillReceive.Verdict.Bad(TillReceive.Problem.NOT_A_NUMBER), TillReceive.judge(10_000, null))
    assertEquals(TillReceive.Verdict.Bad(TillReceive.Problem.NOT_POSITIVE), TillReceive.judge(10_000, 0))
    assertEquals(TillReceive.Verdict.Bad(TillReceive.Problem.NOT_POSITIVE), TillReceive.judge(10_000, -1))
    assertEquals(TillReceive.Verdict.Bad(TillReceive.Problem.TOO_LARGE), TillReceive.judge(10_000, TillReceive.MAX_MINOR + 1))
  }

  @Test
  fun partialAndFullPaymentNeedNoConfirm() {
    val part = TillReceive.judge(10_000, 2_500)
    assertEquals(TillReceive.Verdict.Ok(amountMinor = 2_500, prepayMinor = 0, leftMinor = 7_500), part)
    assertFalse(TillReceive.needsConfirm(part))
    val full = TillReceive.judge(10_000, 10_000)
    assertEquals(TillReceive.Verdict.Ok(amountMinor = 10_000, prepayMinor = 0, leftMinor = 0), full)
    assertFalse(TillReceive.needsConfirm(full))
  }

  @Test
  fun overpaymentIsPrepaymentAndNeedsConfirm() {
    val over = TillReceive.judge(10_000, 100_000)
    assertEquals(TillReceive.Verdict.Ok(amountMinor = 100_000, prepayMinor = 90_000, leftMinor = 0), over)
    assertTrue(TillReceive.needsConfirm(over))
    //  بی بدهی، همه‌اش پیش‌پرداخت است
    assertEquals(TillReceive.Verdict.Ok(amountMinor = 3_000, prepayMinor = 3_000, leftMinor = 0), TillReceive.judge(0, 3_000))
    assertEquals(TillReceive.Verdict.Ok(amountMinor = 3_000, prepayMinor = 3_000, leftMinor = 0), TillReceive.judge(-2_000, 3_000))
    assertTrue(TillReceive.needsConfirm(TillReceive.judge(-2_000, 3_000)))
    assertFalse(TillReceive.needsConfirm(TillReceive.judge(10_000, null)))
  }

  @Test
  fun sameKeyIsRecordedOnce() {
    assertFalse(TillReceive.alreadyRecorded(listOf("a", "b"), "k1"))
    assertTrue(TillReceive.alreadyRecorded(listOf("a", "k1"), "k1"))
    assertFalse(TillReceive.alreadyRecorded(emptyList(), ""))
    //  کلیدِ خالی هرگز «ثبت‌شده» نیست، حتی اگر ردیفی بی شناسه در دفتر باشد
    assertFalse(TillReceive.alreadyRecorded(listOf("", "a"), ""))
  }
}
