package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.data.DebtTransaction
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.DebtorListModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** فرم‌های پول: نوشته ⇐ پیش‌نویسِ موتور، خطای درجا، «تسویهٔ کامل» و گامِ «بیش از سقف» */
class MoneyFormsModelTest {

  private val ledger = ShopData(
    debtors = listOf(Debtor(id = "d", name = "نصیر", creditLimit = 1000.0), Debtor(id = "free", name = "آزاد")),
    transactions = listOf(DebtTransaction(id = "t", debtorId = "d", type = "give", amount = 900.0, date = "2026-10-01")),
  )

  @Test
  fun `قرض‌دار — سقفِ خالی بی سقف، نوشتهٔ بد NaN تا موتور رد کند`() {
    assertNull(DebtorFormsModel.parseLimit(" "))
    assertEquals(1500.0, DebtorFormsModel.parseLimit("۱۵۰۰")!!, 0.0)
    assertTrue(DebtorFormsModel.parseLimit("abc")!!.isNaN())
    assertNotNull(DebtorFormsModel.limitError(DebtorForm(creditLimit = "-5")))
    assertNull(DebtorFormsModel.limitError(DebtorForm(creditLimit = "")))
    assertNotNull(DebtorFormsModel.nameError(DebtorForm()))
    assertEquals("1000", DebtorFormsModel.of(ledger.debtors[0]).creditLimit)
  }

  @Test
  fun `قرض‌دار — تازه با شناسهٔ تازه، ویرایش با همان شناسه، نامِ خالی رد`() {
    val added = DebtorFormsModel.save(ledger, null, DebtorForm(name = "زرغونه", creditLimit = "500"), 1L) { "new" } as DebtorEngine.Result.Ok
    assertEquals("new", added.id)
    assertEquals(500.0, added.data.debtors.first { it.id == "new" }.creditLimit!!, 0.0)
    val edited = DebtorFormsModel.save(ledger, "d", DebtorFormsModel.of(ledger.debtors[0]).copy(notes = "عمده"), 1L) { "x" } as DebtorEngine.Result.Ok
    assertEquals("d", edited.id)
    assertEquals("عمده", edited.data.debtors.first { it.id == "d" }.notes)
    assertTrue(DebtorFormsModel.save(ledger, null, DebtorForm(name = " "), 1L) { "n" } is DebtorEngine.Result.Failed)
  }

  @Test
  fun `تراکنش — ماندهٔ پس از ثبت و تسویهٔ کامل`() {
    val give = TxForm(kind = DebtorEngine.Kind.GIVE, amount = "200")
    val receive = give.copy(kind = DebtorEngine.Kind.RECEIVE)
    assertEquals(1100.0, DebtorFormsModel.balanceAfter(900.0, give), 0.0)
    assertEquals(700.0, DebtorFormsModel.balanceAfter(900.0, receive), 0.0)
    assertEquals("900", DebtorFormsModel.settleAll(899.6))
    assertNull(DebtorFormsModel.settleAll(0.0))
    assertNotNull(DebtorFormsModel.amountError(TxForm(amount = "0")))
  }

  @Test
  fun `بیش از سقف — فقط بردی که از سقف می‌گذرد`() {
    assertNotNull(DebtorFormsModel.overLimit(ledger, "d", TxForm(amount = "200")))
    assertNull(DebtorFormsModel.overLimit(ledger, "d", TxForm(amount = "50")))
    assertNull(DebtorFormsModel.overLimit(ledger, "d", TxForm(kind = DebtorEngine.Kind.RECEIVE, amount = "5000")))
    assertNull(DebtorFormsModel.overLimit(ledger, "free", TxForm(amount = "99999")))
    //  بی تأییدِ صریح موتور رد می‌کند؛ با تأیید می‌پذیرد
    val f = TxForm(amount = "200", date = "2026-10-20")
    assertTrue(DebtorFormsModel.submit(ledger, "d", f, "2026-10-20", 1L, { "n" }, overLimit = false) is DebtorEngine.Result.Failed)
    assertTrue(DebtorFormsModel.submit(ledger, "d", f, "2026-10-20", 1L, { "n" }, overLimit = true) is DebtorEngine.Result.Ok)
  }

  @Test
  fun `رسید سررسید نمی‌گیرد`() {
    val f = TxForm(kind = DebtorEngine.Kind.RECEIVE, amount = "100", date = "2026-10-20", due = "2026-11-01")
    val ok = DebtorFormsModel.submit(ledger, "d", f, "2026-10-20", 1L, { "n" }, overLimit = false) as DebtorEngine.Result.Ok
    assertNull(ok.data.transactions.first { it.id == "n" }.dueDate)
  }

  @Test
  fun `پرداخت و مصرف — پیش‌نویس و خطا`() {
    assertEquals("1200", LedgerFormsModel.settleAll(1200.4))
    assertNull(LedgerFormsModel.settleAll(-3.0))
    assertNotNull(LedgerFormsModel.payError(PayForm(amount = "")))
    assertNull(LedgerFormsModel.payError(PayForm(amount = "50")))
    val e = Expense(id = "e", title = "برق", category = "برق", amount = 850.0, date = "2026-10-02")
    val form = LedgerFormsModel.of(e, "2026-10-20")
    assertEquals("850", form.amount)
    assertEquals("", form.title)
    assertEquals(850.0, LedgerFormsModel.draft(form).amount, 0.0)
    assertEquals("2026-10-20", LedgerFormsModel.of(null, "2026-10-20").date)
    assertEquals(listOf("کرایه", "برق"), LedgerFormsModel.categories(listOf("کرایه"), "برق"))
    assertNotNull(LedgerFormsModel.expenseAmountError(ExpenseForm(amount = "-1")))
  }

  @Test
  fun `پروندهٔ CRM — شمارهٔ دوم، برچسب و رضایت با همان ذخیره، همه یا هیچ`() {
    val f = DebtorForm(name = "زرغونه", phone = "0791112233", phone2 = "0702223344", tags = "عمده، همسایه", consent = true, consentSource = "حضوری")
    val ok = DebtorFormsModel.save(ledger, null, f, 1L) { "z" } as DebtorEngine.Result.Ok
    val z = ok.data.debtors.first { it.id == "z" }
    assertEquals("0702223344", z.phone2)
    assertEquals(listOf("عمده", "همسایه"), z.tags)
    assertEquals(true, z.marketingConsent)
    assertTrue(ok.data.auditLog.any { it.type == "marketing_consent" && it.refId == "z" })
    //  برگهٔ ویرایش همان‌ها را نشان می‌دهد؛ رضایت بی منبع را نمی‌پذیرد
    val back = DebtorFormsModel.of(z)
    assertEquals("0702223344", back.phone2)
    assertEquals("عمده، همسایه", back.tags)
    assertEquals(true, back.consent)
    assertNotNull(DebtorFormsModel.consentError(back))
    assertNull(DebtorFormsModel.consentError(back.copy(consent = false)))
    //  ردِ پرونده (شمارهٔ دوم = اول) ⇒ هیچ‌چیز، حتی قرض‌دارِ تازه، نوشته نمی‌شود
    val bad = DebtorFormsModel.save(ledger, null, f.copy(phone2 = "0791112233"), 1L) { "y" }
    assertTrue(bad is DebtorEngine.Result.Failed)
    val noSource = DebtorFormsModel.save(ledger, "d", DebtorFormsModel.of(ledger.debtors[0]).copy(consent = true), 1L) { "x" }
    assertTrue(noSource is DebtorEngine.Result.Failed)
  }

  @Test
  fun `فهرست و انتخاب‌گر — فقط مشتریِ فعال؛ جست‌وجو با شمارهٔ دوم و برچسب`() {
    val d = ledger.copy(
      debtors = ledger.debtors + Debtor(id = "old", name = "نصیر کهنه", mergedInto = "d") +
        Debtor(id = "t", name = "توریالی", phone2 = "0705556677", tags = listOf("عمده")),
    )
    val ids = DebtorListModel.rows(d, "", DebtorListModel.Sort.NAME, 0L).map { it.debtor.id }
    assertFalse("old" in ids)
    assertEquals(3, DebtorListModel.summary(d).people)
    assertEquals(listOf("t"), DebtorListModel.rows(d, "0705556677", DebtorListModel.Sort.NAME, 0L).map { it.debtor.id })
    assertEquals(listOf("t"), DebtorListModel.rows(d, "#عمده", DebtorListModel.Sort.NAME, 0L).map { it.debtor.id })
  }
}
