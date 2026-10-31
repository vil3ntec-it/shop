package ir.vil3ntec.tohid.core.receipt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  CRM-05 — صورت‌حسابِ مشتری به شکلِ تصویر (`StatementLayout`). عددها از دفترِ نمونه نوشته شده‌اند:
 *  برد ۱٬۰۰۰ ⇐ مانده ۱٬۰۰۰؛ رسید ۴۰۰٫۵۰ ⇐ ۵۹۹٫۵۰؛ برد ۲۰۰ ⇐ ۷۹۹٫۵۰.
 */
class StatementLayoutTest {

  private val fmt = object : ReceiptFormat {
    override fun money(afn: Double) = "M[$afn]"
    override fun amount(afn: Double) = "A[$afn]"
    override fun qty(q: Double) = "Q[$q]"
    override fun digits(text: String) = "D[$text]"
    override fun phone(text: String) = "P[$text]"
    override fun date(iso: String) = "T[$iso]"
  }

  private val lines = listOf(
    StatementLine("2026-10-01", "جنس", giveMinor = 100_000, receiveMinor = 0, balanceMinor = 100_000),
    StatementLine("2026-10-05", "", giveMinor = 0, receiveMinor = 40_050, balanceMinor = 59_950),
    StatementLine("2026-10-09", "  برنج  ", giveMinor = 20_000, receiveMinor = 0, balanceMinor = 79_950),
  )

  private fun input(lines: List<StatementLine>) = StatementInput(
    shop = ShopHeader("دکانِ نمونه", address = "کابل", phone = "0700123456", footer = ""),
    customer = " احمد ", phone = "0799111222", todayIso = "2026-10-30", lines = lines,
  )

  private fun doc(lines: List<StatementLine>) = StatementLayout.build(input(lines), 576, fmt)

  @Test
  fun headerNamesShopCustomerPhoneAndDate() {
    val b = doc(lines).blocks
    assertEquals(ReceiptBlock.Text("دکانِ نمونه", TextStyle.TITLE), b[0])
    assertTrue(ReceiptBlock.Text("صورت‌حسابِ مشتری", TextStyle.TOTAL) in b)
    assertTrue(ReceiptBlock.Row("مشتری", "احمد") in b)
    assertTrue(ReceiptBlock.Row("تلفن", "P[0799111222]") in b)
    assertTrue(ReceiptBlock.Row("تاریخ", "T[2026-10-30]") in b)
  }

  @Test
  fun everyLineKeepsItsOrderAmountAndBalanceInPul() {
    val items = doc(lines).blocks.filterIsInstance<ReceiptBlock.Item>()
    assertEquals(
      listOf(
        ReceiptBlock.Item("T[2026-10-01] — جنس", "برد: M[1000.0]", "مانده: M[1000.0]"),
        //  رسید بی شرح ⇐ نامِ نوع؛ ۴۰۰٫۵۰ بی گرد
        ReceiptBlock.Item("T[2026-10-05] — رسید", "رسید: M[400.5]", "مانده: M[599.5]"),
        ReceiptBlock.Item("T[2026-10-09] — برنج", "برد: M[200.0]", "مانده: M[799.5]"),
      ),
      items,
    )
  }

  @Test
  fun totalsAndFinalBalanceComeFromTheLines() {
    val b = doc(lines).blocks
    assertTrue(ReceiptBlock.Row("جمعِ برد", "M[1200.0]") in b)
    assertTrue(ReceiptBlock.Row("جمعِ رسید", "M[400.5]") in b)
    assertTrue(ReceiptBlock.Row("مانده (بدهکار)", "M[799.5]", TextStyle.TOTAL) in b)
    assertTrue(ReceiptBlock.Text("D[3] ردیف", TextStyle.SMALL, center = false) in b)
    assertEquals(79_950L, StatementLayout.finalMinor(lines))
  }

  @Test
  fun creditSettledAndEmptyStatements() {
    val credit = listOf(StatementLine("2026-10-01", "پیش‌پرداخت", 0, 5_000, -5_000))
    assertTrue(ReceiptBlock.Row("مانده (طلبکار)", "M[50.0]", TextStyle.TOTAL) in doc(credit).blocks)
    val settled = listOf(StatementLine("2026-10-01", "", 5_000, 0, 5_000), StatementLine("2026-10-02", "", 0, 5_000, 0))
    assertTrue(ReceiptBlock.Row("مانده (صاف)", "M[0.0]", TextStyle.TOTAL) in doc(settled).blocks)
    val empty = doc(emptyList()).blocks
    assertTrue(ReceiptBlock.Text("هنوز ردیفی نیست", TextStyle.SMALL) in empty)
    assertTrue(empty.none { it is ReceiptBlock.Item })
    assertEquals(0L, StatementLayout.finalMinor(emptyList()))
  }

  @Test
  fun widthPicksPaperMetrics() {
    assertEquals(ReceiptMetrics.MM80, doc(lines).metrics)
    assertEquals(ReceiptMetrics.MM58, StatementLayout.build(input(lines), 384, fmt).metrics)
  }
}
