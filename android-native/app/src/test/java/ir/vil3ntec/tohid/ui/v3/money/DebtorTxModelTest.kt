package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** برگهٔ یک تراکنش و کشوی حساب: خط‌ها از همان `DebtBook` و `DebtorEngine.Account` */
class DebtorTxModelTest {

  private val today = "2026-10-20"
  private fun tx(id: String, date: String, type: String, amount: Double, at: Long, due: String? = null, notes: String = "") =
    DebtTransaction(id = id, debtorId = "d", type = type, amount = amount, date = date, createdAt = at, dueDate = due, notes = notes)

  private val ledger = ShopData(
    debtors = listOf(Debtor(id = "d", name = "جلیل", phone = "0791112233", creditLimit = 1000.0)),
    transactions = listOf(
      tx("g1", "2026-09-01", "give", 600.0, 1, due = "2026-10-01"),
      tx("g2", "2026-10-10", "give", 300.0, 2, due = "2026-11-01", notes = "آرد"),
      tx("r1", "2026-10-12", "receive", 200.0, 3),
    ),
  )
  private val book = DebtBook.book(ledger, "d", today)!!

  @Test
  fun `بردِ عقب‌افتاده — باز، سررسیدِ گذشته، ماندهٔ پس از ردیف`() {
    val lines = DebtorTxModel.lines(ledger.transactions[0], book.entry("g1"), book)
    assertEquals(listOf("تاریخ", "وضعیت", "سررسید", "مانده پس از این ردیف"), lines.map { it.label })
    assertEquals(DebtorTxModel.Pill.WAIT, lines[1].pill)
    assertEquals(DebtorTxModel.Pill.OWE, lines[2].pill)
    assertTrue(lines[2].value.endsWith("گذشته"))
  }

  @Test
  fun `برد با یادداشت و سررسیدِ آینده`() {
    val lines = DebtorTxModel.lines(ledger.transactions[1], book.entry("g2"), book)
    assertEquals("آرد", lines.first { it.label == "یادداشت" }.value)
    assertEquals(DebtorTxModel.Pill.INFO, lines.first { it.label == "سررسید" }.pill)
  }

  @Test
  fun `رسید — «بابتِ» بردی که بست، بی وضعیت و سررسید`() {
    val lines = DebtorTxModel.lines(ledger.transactions[2], book.entry("r1"), book)
    assertTrue(lines.none { it.label == "سررسید" || it.label == "وضعیت" })
    assertTrue(lines.any { it.label == "بابتِ" })
    assertTrue(DebtorTxModel.items(book.entry("r1")).isEmpty())
  }

  @Test
  fun `سررسید — خالی یعنی ندارد، نوشتهٔ ناخوانا رد`() {
    assertEquals("", DebtorTxModel.parseDue(" "))
    assertNull(DebtorTxModel.parseDue("فردا"))
    assertEquals("2026-11-01", DebtorTxModel.parseDue("1405-08-10"))
  }

  @Test
  fun `کشو — مدتِ بدهی، عقب‌افتاده، سررسیدِ بعدی و سقف`() {
    val info = DebtorLedgerModel.info(DebtorEngine.account(ledger, "d")!!, book, now = ir.vil3ntec.tohid.isoMillis(today))
    assertEquals(
      listOf(DebtorLedgerModel.InfoTone.OWE, DebtorLedgerModel.InfoTone.OWE, DebtorLedgerModel.InfoTone.INFO, DebtorLedgerModel.InfoTone.NEUTRAL),
      info.map { it.tone },
    )
    assertTrue(info[1].text.startsWith("1 برد از سررسید گذشته"))
  }

  @Test
  fun `خلاصهٔ کشوی بسته — شماره و مدت`() {
    val s = DebtorLedgerModel.summary("0791112233", since = null)
    assertTrue(s.contains("0791112233"))
    assertTrue(s.endsWith("بدهی ندارد"))
    assertEquals("بدهی ندارد", DebtorLedgerModel.summary("", since = null))
  }

  @Test
  fun `مدت و حالِ حساب در انگلیسی هم ترجمه است — نه «۱ ماه in debt»`() {
    val day = 86_400_000L
    val now = 1_800_000_000_000L
    assertEquals("2 ماه بدهکار", DebtorLedgerModel.summary("", now - 65 * day, now))
    assertEquals("1,000 افغانی بدهکار", MoneyText.state(1000.0))
    //  فارسی همان جمله‌های موتور است، حرف‌به‌حرف
    val over = DebtBook.creditCheck(ledger, "d", DebtBook.minor(500.0))!!
    assertEquals(DebtBook.overLimitText(over), MoneyText.overLimit(over))
    assertEquals(DebtBook.state(DebtBook.minor(700.0)), MoneyText.state(DebtBook.minor(700.0)))
    I18n.source = { Lang.EN }
    try {
      assertEquals("2 months in debt", DebtorLedgerModel.summary("", now - 65 * day, now))
      assertEquals("Owes 1,000 AFN", MoneyText.state(1000.0))
      assertEquals("250 AFN in credit", MoneyText.state(-250.0))
      assertEquals("Settled", MoneyText.state(0.0))
      assertEquals("200 AFN over the credit limit (1,000) — current balance 700", MoneyText.overLimit(over))
      assertEquals("1 day in debt", DebtorLedgerModel.summary("", now - day, now))
    } finally {
      I18n.source = { Lang.FA }
    }
  }
}
