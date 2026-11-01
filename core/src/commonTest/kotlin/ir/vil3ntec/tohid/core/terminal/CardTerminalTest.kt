package ir.vil3ntec.tohid.core.terminal

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** کارت‌خوانِ دستی: فقط شمارهٔ مرجعِ درست «تأیید» است؛ جای خالیِ «برنامه به برنامه» هرگز چیزی نمی‌سازد */
class CardTerminalTest {

  private fun <T> runSync(block: suspend () -> T): T {
    var out: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { out = it })
    return out!!.getOrThrow()
  }

  private fun manual(answer: ManualCardTerminal.Answer, seen: MutableList<TerminalRequest> = ArrayList()) =
    ManualCardTerminal { req -> seen += req; answer }

  @Test
  fun approvedWithValidReferenceAndLastFour() {
    val seen = ArrayList<TerminalRequest>()
    val r = runSync { manual(ManualCardTerminal.Answer.Approved(" ۱۲۳ ۴۵۶ ", "۴۳۲۱"), seen).requestPayment(145000, "sale1") }
    assertEquals(TerminalResult.Approved("123456", "•••• 4321"), r)
    assertEquals(listOf(TerminalRequest(145000, "sale1", "AFN")), seen)
    assertEquals("123456 (•••• 4321)", ManualCardTerminal.refText(r as TerminalResult.Approved))
    assertEquals("AB-77", ManualCardTerminal.refText(TerminalResult.Approved("AB-77")))
  }

  @Test
  fun badReferenceOrCardIsNeverApproved() {
    for (bad in listOf("", "12", "۱۲۳", "abc!", "x".repeat(33), "١٢٣٤٥؟")) {
      assertEquals(TerminalResult.Declined("bad_reference"), runSync { manual(ManualCardTerminal.Answer.Approved(bad)).requestPayment(100, "s") })
    }
    assertEquals(TerminalResult.Declined("bad_card"), runSync { manual(ManualCardTerminal.Answer.Approved("1234", "12345")).requestPayment(100, "s") })
    assertEquals(TerminalResult.Declined("bad_card"), runSync { manual(ManualCardTerminal.Answer.Approved("1234", "12a4")).requestPayment(100, "s") })
    //  چهار رقمِ خالی یعنی «نداده»
    assertEquals(TerminalResult.Approved("1234"), runSync { manual(ManualCardTerminal.Answer.Approved("1234", " ")).requestPayment(100, "s") })
  }

  @Test
  fun declinedCancelledAndBadAmount() {
    assertEquals(TerminalResult.Declined("insufficient"), runSync { manual(ManualCardTerminal.Answer.Declined(" insufficient ")).requestPayment(100, "s") })
    assertEquals(TerminalResult.Cancelled, runSync { manual(ManualCardTerminal.Answer.Cancelled).requestPayment(100, "s") })
    val seen = ArrayList<TerminalRequest>()
    assertEquals(TerminalResult.Unavailable("bad_amount"), runSync { manual(ManualCardTerminal.Answer.Cancelled, seen).requestPayment(0, "s") })
    assertEquals(0, seen.size)
  }

  @Test
  fun vendorAppPlaceholderIsAlwaysUnavailable() {
    val r = runSync { VendorAppTerminal.requestPayment(145000, "sale1") }
    assertEquals(TerminalResult.Unavailable("vendor_integration_document_required"), r)
  }

  @Test
  fun referenceNormalisation() {
    assertEquals("AB12-9", ManualCardTerminal.reference(" ab12-9 "))
    assertEquals("0123", ManualCardTerminal.reference("٠١٢٣"))
    assertNull(ManualCardTerminal.reference("12 3"))
  }
}
