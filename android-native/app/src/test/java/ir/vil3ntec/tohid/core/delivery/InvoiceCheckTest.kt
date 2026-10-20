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

  @Test fun `نقدِ COD نزدِ شرکت — صورت‌حسابِ درست هم «همه را بپرداز» نیست و خالص در دو جهت`() {
    val cod = listOf(
      StatementRow("C1", orderNo = 31, externalRef = "TRK-31", status = "delivered", fareMinor = 15_000, expectedCollectMinor = 1_240_000, cashCollectedMinor = 1_240_000),
      StatementRow("C2", orderNo = 32, externalRef = "TRK-32", status = "delivered", fareMinor = 15_000, cashCollectedMinor = 0),
      StatementRow("C3", orderNo = 33, externalRef = "TRK-33", status = "failed", fareMinor = 15_000, cashCollectedMinor = null),
    )
    val lines = InvoiceCheck.parse("TRK-31 150\nTRK-32 150").lines
    val r = InvoiceCheck.check(cod, invoiceTotalMinor = 30_000, invoiceCount = 2, lines = lines)
    assertTrue("کرایه‌ها و ریز درست است", r.invoiceMatches)
    assertFalse("ولی ۱۲٬۴۰۰ افغانیِ دکان نزدِ شرکت است ⇒ «یکی است، بپرداز» گفته نمی‌شود", r.ok)
    assertEquals(1_240_000, r.cashHeldMinor)
    assertEquals("شرکت به دکان می‌دهد", -1_210_000, r.netToCompanyByInvoiceMinor)
    assertEquals(-1_210_000, r.netToCompanyByLedgerMinor)
    assertEquals(1_240_000L, r.items.first { it.key == "TRK-31" }.cashMinor)

    //  بی نقد: صورت‌حسابِ درست یعنی همان را بپرداز
    val noCash = InvoiceCheck.check(cod.map { it.copy(cashCollectedMinor = 0) }, 30_000, 2, lines)
    assertTrue(noCash.ok)
    assertEquals("دکان به شرکت می‌دهد", 30_000, noCash.netToCompanyByInvoiceMinor)
  }

  @Test fun `سفارشِ دوباره‌سپرده — شمارهٔ سفارش به تلاشِ تحویل‌شده می‌خورد نه نخستین`() {
    val retried = listOf(
      StatementRow("R1", orderNo = 21, externalRef = "", status = "failed", fareMinor = 15_000, createdAt = 1),
      StatementRow("R2", orderNo = 21, externalRef = "", status = "delivered", fareMinor = 15_000, createdAt = 2),
    )
    val r = InvoiceCheck.check(retried, 15_000, 1, InvoiceCheck.parse("#21 150").lines)
    assertEquals(listOf(InvoiceCheck.Kind.MATCHED), r.items.map { it.kind })
    assertEquals("delivered", r.items.single().status)
    assertTrue(r.ok)
    //  دو خط برای همان سفارش: دومی به تلاشِ ناموفق می‌خورد؟ نه — همان تحویل‌شده ⇒ تکراری
    val twice = InvoiceCheck.check(retried, 30_000, 2, InvoiceCheck.parse("#21 150\n21 150").lines)
    assertEquals(listOf(InvoiceCheck.Kind.MATCHED, InvoiceCheck.Kind.DUPLICATE), twice.items.map { it.kind })
  }

  @Test fun `شمارهٔ سفارشِ بی # هم پیدا می‌شود، ولی رهگیری مقدم است`() {
    val r = InvoiceCheck.check(rows, 35_000, null, listOf(InvoiceCheck.Line("13", null), InvoiceCheck.Line("TRK-4", null)))
    assertEquals(listOf(InvoiceCheck.Kind.MATCHED, InvoiceCheck.Kind.MATCHED, InvoiceCheck.Kind.NOT_BILLED), r.items.map { it.kind })
    assertEquals(11L, r.items.last().orderNo)
  }
}
