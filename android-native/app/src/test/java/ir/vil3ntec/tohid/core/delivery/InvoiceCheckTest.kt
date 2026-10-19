package ir.vil3ntec.tohid.core.delivery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** تطبیقِ صورت‌حسابِ شرکتِ بیرونی (DLV-19، DEL-02): هیچ اختلافی پنهان نمی‌ماند */
class InvoiceCheckTest {

  private val rows = listOf(
    StatementRow("A1", orderNo = 11, externalRef = "TRK-1", status = "delivered", fareMinor = 15_000),
    StatementRow("A2", orderNo = 12, externalRef = "TRK-2", status = "failed", fareMinor = 18_000),
    StatementRow("A3", orderNo = 13, externalRef = "", status = "delivered", fareMinor = 20_000),
    StatementRow("A4", orderNo = 14, externalRef = "TRK-4", status = "delivered", fareMinor = 15_000),
  )

  @Test fun `خواندنِ خط‌ها با رقمِ فارسی و جداکننده`() {
    val p = InvoiceCheck.parse("TRK-1 150\n\n#13\t۲۰۰\ntrk-4;1,500.50\n")
    assertTrue(p.errors.isEmpty())
    assertEquals(listOf("TRK-1", "#13", "trk-4"), p.lines.map { it.key })
    assertEquals(listOf(15_000L, 20_000L, 150_050L), p.lines.map { it.amountMinor })
    val bad = InvoiceCheck.parse("TRK-1 abc\nTRK-2 1 2")
    assertEquals(2, bad.errors.size)
    assertTrue(bad.errors[0].startsWith("خطِ ۱") || bad.errors[0].startsWith("خطِ 1"))
    assertTrue(bad.lines.isEmpty())
  }

  @Test fun `فقط جمع‌ها — دفترِ ما = تحویل‌شده‌ها`() {
    val ok = InvoiceCheck.check(rows, invoiceTotalMinor = 50_000, invoiceCount = 3, lines = emptyList())
    assertEquals(3, ok.ourDeliveredCount)
    assertEquals(50_000, ok.ourDeliveredFareMinor)
    assertEquals(0, ok.amountDiffMinor)
    assertEquals(0, ok.countDiff)
    assertTrue(ok.ok)

    val more = InvoiceCheck.check(rows, invoiceTotalMinor = 68_000, invoiceCount = 4, lines = emptyList())
    assertEquals(18_000, more.amountDiffMinor)
    assertEquals(1, more.countDiff)
    assertFalse(more.ok)

    val noCount = InvoiceCheck.check(rows, 50_000, null, emptyList())
    assertNull(noCount.countDiff)
    assertTrue(noCount.ok)
  }

  @Test fun `ریز — درست، مبلغِ دیگر، تحویل‌نشده، ناشناخته، تکراری، نیامده`() {
    val lines = InvoiceCheck.parse(
      """
      TRK-1 150
      TRK-2 180
      #13 250
      TRK-99 100
      trk-1 150
      """.trimIndent()
    ).lines
    val r = InvoiceCheck.check(rows, invoiceTotalMinor = 83_000, invoiceCount = 5, lines = lines)
    val kinds = r.items.map { it.key to it.kind }
    assertEquals(
      listOf(
        "TRK-1" to InvoiceCheck.Kind.MATCHED,
        "TRK-2" to InvoiceCheck.Kind.NOT_DELIVERED,
        "#13" to InvoiceCheck.Kind.AMOUNT_DIFF,
        "TRK-99" to InvoiceCheck.Kind.UNKNOWN,
        "trk-1" to InvoiceCheck.Kind.DUPLICATE,
        "TRK-4" to InvoiceCheck.Kind.NOT_BILLED,
      ),
      kinds,
    )
    val diff = r.items.single { it.kind == InvoiceCheck.Kind.AMOUNT_DIFF }
    assertEquals(20_000L, diff.ourFareMinor)
    assertEquals(25_000L, diff.billedMinor)
    assertEquals(13L, diff.orderNo)
    assertEquals(5, r.problems.size)
    assertFalse(r.ok)
  }

  @Test fun `شمارهٔ سفارشِ بی # هم پیدا می‌شود، ولی رهگیری مقدم است`() {
    val r = InvoiceCheck.check(rows, 35_000, null, listOf(InvoiceCheck.Line("13", null), InvoiceCheck.Line("TRK-4", null)))
    assertEquals(listOf(InvoiceCheck.Kind.MATCHED, InvoiceCheck.Kind.MATCHED, InvoiceCheck.Kind.NOT_BILLED), r.items.map { it.kind })
    assertEquals(11L, r.items.last().orderNo)
  }
}
