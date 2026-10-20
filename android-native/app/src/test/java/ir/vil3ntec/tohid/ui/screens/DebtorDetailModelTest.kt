package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorDetailModel.Action
import ir.vil3ntec.tohid.ui.screens.DebtorDetailModel.PendingDelete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** صفحهٔ حسابِ یک قرض‌دار: سرخطِ روز، کارهای کشو، متنِ یادآوری و «حذف با برگرداندن» */
class DebtorDetailModelTest {

  private fun tx(id: String, date: String, type: String = "give", amount: Double = 100.0, at: Long = 0) =
    DebtTransaction(id = id, debtorId = "d", type = type, amount = amount, date = date, createdAt = at)

  @Test
  fun `روزها تازه‌ترین اول؛ ترتیبِ درونِ روز همان ترتیبِ حساب`() {
    //  Account.transactions تازه‌ترین ثبت اول است
    val list = listOf(
      tx("c", "2026-10-13", at = 30),
      tx("b", "2026-10-12", at = 20),
      tx("b2", "2026-10-12", at = 19),
      tx("a", "2026-10-10", at = 10),
    )
    val days = DebtorDetailModel.days(list)
    assertEquals(listOf("2026-10-13", "2026-10-12", "2026-10-10"), days.map { it.date })
    assertEquals(listOf("b", "b2"), days[1].rows.map { it.id })
  }

  @Test
  fun `ردیفِ در انتظارِ حذف پنهان؛ روزِ خالی هم`() {
    val list = listOf(tx("x", "2026-10-13"), tx("y", "2026-10-12"), tx("z", "2026-10-12"))
    val days = DebtorDetailModel.days(list, hidden = setOf("x", "y"))
    assertEquals(listOf("2026-10-12"), days.map { it.date })
    assertEquals(listOf("z"), days.single().rows.map { it.id })
  }

  @Test
  fun `بی‌تاریخ ته فهرست`() {
    val days = DebtorDetailModel.days(listOf(tx("old", ""), tx("new", "2026-10-01")))
    assertEquals(listOf("2026-10-01", DebtorDetailModel.UNDATED), days.map { it.date })
  }

  @Test
  fun `کارهای کشو — تماس فقط با شماره، صورت‌حساب فقط با تراکنش`() {
    assertEquals(
      listOf(Action.CALL, Action.SMS, Action.WHATSAPP, Action.EDIT, Action.STATEMENT_CSV, Action.PRINT, Action.DELETE),
      DebtorDetailModel.actions("0792236008", hasTransactions = true),
    )
    assertEquals(listOf(Action.EDIT, Action.DELETE), DebtorDetailModel.actions("", hasTransactions = false))
    //  شمارهٔ بی رقم (فقط نویسه) گرفتنی نیست
    assertEquals(listOf(Action.EDIT, Action.DELETE), DebtorDetailModel.actions("ندارد", hasTransactions = false))
  }

  @Test
  fun `شماره — رقمِ فارسی لاتین، فاصله‌ها حذف، واتساپ بی «+»`() {
    assertEquals("0792236008", DebtorDetailModel.dialable("۰۷۹ ۲۲۳ ۶۰۰۸"))
    assertEquals("+93792236008", DebtorDetailModel.dialable("+93 79 223 6008"))
    assertEquals("93792236008", DebtorDetailModel.whatsappNumber("+93 79 223 6008"))
  }

  @Test
  fun `متنِ یادآوری — با مانده، و بی مانده فقط سلام`() {
    assertEquals("سلام جلیل عزیز، یادآوری می‌کنم که 1,000 افغانی از حساب شما باقی مانده. ممنون.", DebtorDetailModel.reminder("جلیل", 1000.0))
    assertEquals("سلام جلیل عزیز،", DebtorDetailModel.reminder("جلیل", 0.0))
  }

  @Test
  fun `سه عدد از همان حساب`() {
    val d = ShopData(
      debtors = listOf(Debtor(id = "d", name = "haroon")),
      transactions = listOf(tx("g", "2026-10-13", "give", 600.0, 1), tx("r", "2026-10-14", "receive", 600.0, 2)),
    )
    val t = DebtorDetailModel.totals(DebtorEngine.account(d, "d")!!)
    assertEquals(600.0, t.given, 0.0)
    assertEquals(600.0, t.received, 0.0)
    assertTrue(t.settled)
    assertFalse(t.owes)
  }

  @Test
  fun `حذفِ در انتظار — ردیف و قرض‌دار جدا شمرده می‌شوند`() {
    val pending = listOf(PendingDelete.Transaction("t1"), PendingDelete.Debtor("d1"), PendingDelete.Transaction("t2"))
    assertEquals(setOf("t1", "t2"), DebtorDetailModel.hiddenTx(pending))
    assertEquals(setOf("d1"), DebtorDetailModel.hiddenDebtors(pending))
  }
}
