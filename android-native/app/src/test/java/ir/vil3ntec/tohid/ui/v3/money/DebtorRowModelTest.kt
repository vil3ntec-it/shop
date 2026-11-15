package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import ir.vil3ntec.tohid.ui.v3.V3RowTone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  ردیفِ سادهٔ قرض‌داران (`claude/e-rows-63q`، `DebtorRowModel`): نام؛ «آخرین حرکت · شماره»؛ «مانده • وضعیت» به متنِ آرام —
 *  بدهی «قرض‌دار» (سرخِ بدهی)، بردِ سررسیدگذشتهٔ باز «سررسید گذشته» (کهربایی)، موجودی و تسویه آرام.
 */
class DebtorRowModelTest {

  @Before fun fa() { I18n.source = { Lang.FA } }
  @After fun reset() { I18n.source = { Lang.FA } }

  private val today = "2026-11-15"
  private val noon = 1_794_744_000_000L // 2026-11-15T12:00:00Z

  private val d = ShopData(
    debtors = listOf(
      Debtor(id = "owe", name = "احمد", phone = "0700000001"),
      Debtor(id = "late", name = "بصیر"),
      Debtor(id = "flat", name = "جمیله"),
      Debtor(id = "plus", name = "داوود"),
      Debtor(id = "paid-late", name = "زهره"),
    ),
    transactions = listOf(
      DebtTransaction("t1", "owe", "give", 300.0, today, createdAt = noon),
      DebtTransaction("t2", "late", "give", 500.0, "2026-10-01", createdAt = noon - 40L * 86_400_000, dueDate = "2026-10-15"),
      DebtTransaction("t3", "flat", "give", 100.0, "2026-11-10", createdAt = 1),
      DebtTransaction("t4", "flat", "receive", 100.0, "2026-11-14", createdAt = 2),
      DebtTransaction("t5", "plus", "receive", 50.0, "2026-11-14", createdAt = 3),
      //  سررسید گذشته ولی همان برد پس داده شد، و بدهیِ تازهٔ بی سررسید ⇒ قرض‌دار، نه عقب‌افتاده
      DebtTransaction("t6", "paid-late", "give", 200.0, "2026-10-01", createdAt = 4, dueDate = "2026-10-10"),
      DebtTransaction("t7", "paid-late", "receive", 200.0, "2026-10-05", createdAt = 5),
      DebtTransaction("t8", "paid-late", "give", 80.0, "2026-11-12", createdAt = 6),
    ),
  )

  private fun text(id: String) = DebtorRowModel.of(d, DebtorListModel.row(d, d.debtors.first { it.id == id }, noon), today, { 0 }, contacts = true)

  @Test fun aDebtIsQuietRedTextAndTheRowIsNamedNotNumbered() {
    val t = text("owe")
    assertEquals("احمد", t.title)
    assertEquals("300 ؋", t.amount)
    assertEquals(StrRows.owes, t.status.text)
    assertEquals(V3RowTone.DEBT, t.status.tone)
    //  آخرین حرکت کوتاه («امروز ۱۲:۰۰») و شماره کوچک پس از آن
    val sub = t.subtitle.orEmpty()
    assertTrue(sub, sub.startsWith("امروز 12:00 · "))
    assertTrue(sub, sub.contains("0700"))
  }

  @Test fun anOverdueDebtIsAmber() {
    val t = text("late")
    assertEquals("500 ؋", t.amount)
    assertEquals(StrRows.overdue, t.status.text)
    assertEquals(V3RowTone.ATTENTION, t.status.tone)
    //  روزِ همین سال بی سال و بی ساعتِ دروغ (سند ۱ اکتبر، ثبت ۶ اکتبر)
    assertFalse(t.subtitle.orEmpty(), t.subtitle.orEmpty().contains("1405") || t.subtitle.orEmpty().contains(":"))
  }

  @Test fun aPaidOffDueDateIsNotOverdue() {
    val t = text("paid-late")
    assertEquals(StrRows.owes, t.status.text)
    assertFalse(DebtorRowModel.overdue(d, "paid-late", 80.0, today))
    assertTrue(DebtorRowModel.overdue(d, "late", 500.0, today))
  }

  @Test fun settledAndCreditAreQuiet() {
    val flat = text("flat")
    assertNull(flat.amount)
    assertEquals(StrMoney.settled, flat.status.text)
    assertEquals(V3RowTone.QUIET, flat.status.tone)
    assertEquals("دیروز", flat.subtitle)
    val plus = text("plus")
    assertEquals("50 ؋", plus.amount)
    assertEquals(StrRows.inCredit, plus.status.text)
    assertEquals(V3RowTone.QUIET, plus.status.tone)
  }

  @Test fun aDebtorWithoutPhoneOrMovesHasNoSubtitle() {
    val empty = ShopData(debtors = listOf(Debtor(id = "n", name = "")))
    val t = DebtorRowModel.of(empty, DebtorListModel.row(empty, empty.debtors.single(), noon), today, { 0 }, contacts = true)
    assertEquals(StrMoney.noName, t.title)
    assertNull(t.subtitle)
  }
}
