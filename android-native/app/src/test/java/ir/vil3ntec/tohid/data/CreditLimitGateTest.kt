package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.StrSaleCaps
import ir.vil3ntec.tohid.core.perm.CreditLimitPerm
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.money.DebtorForm
import ir.vil3ntec.tohid.ui.v3.money.DebtorFormsModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-03 / §۱۰.۱ — برگهٔ مشتریِ نسخهٔ ۳ (`DebtorFormSheet` ⇐ `DebtorFormsModel.save`) و پروندهٔ کامپیوتر پیش از ذخیره
 *  «تغییرِ سقفِ اعتبار» (`customers.credit_limit`) را می‌پرسند: بسته با دلیل، تا سقفِ نقش، یا همان امروز با پرچمِ خاموش.
 */
class CreditLimitGateTest {

  private val key = CreditLimitPerm.PERM
  private val ledger = ShopData(debtors = listOf(Debtor(id = "d", name = "نصیر", creditLimit = 1000.0)))
  private val debtor = ledger.debtors[0]
  private val denyRules = CreditLimitPerm.Rules("staff", listOf(mapOf(key to mapOf("mode" to "deny"))), enforced = true)
  private val capRules = CreditLimitPerm.Rules("cashier", listOf(mapOf(key to mapOf("caps" to mapOf("maxAmount" to 500_000.0)))), enforced = true)
  private fun form(limit: String) = DebtorFormsModel.of(debtor).copy(creditLimit = limit)

  @Test fun helperSaysWhyTheFieldIsLockedOrHowFarItGoes() {
    assertEquals(StrSaleCaps.limitLocked, CreditLimitGate.helper(CreditLimitPerm.field(denyRules, existing = true)))
    assertEquals(StrSaleCaps.limitUpTo(money(5000.0)), CreditLimitGate.helper(CreditLimitPerm.field(capRules, existing = true)))
    assertNull(CreditLimitGate.helper(CreditLimitPerm.Field.OPEN))
    //  #۲۵ — مشتریِ تازه: کادرِ بسته با «سقفِ پیش‌فرضِ دکان»، تا سقفِ نقش باز
    assertEquals(StrSaleCaps.limitNewDefault, CreditLimitGate.helper(CreditLimitPerm.field(denyRules, existing = false), fresh = true))
    assertEquals(StrSaleCaps.limitUpTo(money(5000.0)), CreditLimitGate.helper(CreditLimitPerm.field(capRules, existing = false), fresh = true))
  }

  @Test fun errorNamesTheLockOrTheCap() {
    assertEquals(StrSaleCaps.limitLocked, CreditLimitGate.error(denyRules, debtor, 500.0))
    assertEquals(StrSaleCaps.limitOverCap(money(5000.0)), CreditLimitGate.error(capRules, debtor, 6000.0))
    assertEquals(StrSaleCaps.limitNoRemove(money(5000.0)), CreditLimitGate.error(capRules, debtor, null))
    assertNull(CreditLimitGate.error(capRules, debtor, 5000.0))
    assertNull(CreditLimitGate.error(denyRules, debtor, 1000.0))
    //  #۲۵ — مشتریِ تازه: جز سقفِ پیش‌فرض (صفر) بسته؛ بی سقف هم
    assertEquals(StrSaleCaps.limitNewDefault, CreditLimitGate.error(denyRules, null, 9000.0))
    assertEquals(StrSaleCaps.limitNewDefault, CreditLimitGate.error(denyRules, null, null))
    assertNull(CreditLimitGate.error(denyRules, null, 0.0))
    assertEquals(StrSaleCaps.limitNoRemove(money(5000.0)), CreditLimitGate.error(capRules, null, null))
    //  پرچمِ خاموش یا صاحبِ دکان ⇒ هیچ
    assertNull(CreditLimitGate.error(CreditLimitPerm.Rules.OFF, debtor, null))
    assertNull(CreditLimitGate.error(denyRules.copy(enforced = false), debtor, null))
    assertNull(CreditLimitGate.error(denyRules.copy(role = "owner"), debtor, null))
  }

  @Test fun formSaveRefusesTheChangeAndWritesNothing() {
    val locked = DebtorFormsModel.save(ledger, "d", form("500"), 1L, denyRules) { "x" }
    assertEquals(DebtorEngine.Result.Failed(StrSaleCaps.limitLocked), locked)
    assertEquals(DebtorEngine.Result.Failed(StrSaleCaps.limitNoRemove(money(5000.0))), DebtorFormsModel.save(ledger, "d", form(""), 1L, capRules) { "x" })
    assertEquals(StrSaleCaps.limitOverCap(money(5000.0)), DebtorFormsModel.limitPermError(capRules, debtor, form("6000")))
    //  نوشتهٔ نادرست را همان خطای امروز می‌گوید، نه این قاعده
    assertNull(DebtorFormsModel.limitPermError(capRules, debtor, form("abc")))
    assertTrue(DebtorFormsModel.save(ledger, "d", form("abc"), 1L, capRules) { "x" } is DebtorEngine.Result.Failed)
  }

  @Test fun formSaveKeepsTodayWhenTheLimitIsUnchangedNewOrAllowed() {
    //  فقط نام/یادداشت عوض شد ⇒ ذخیره، حتی با «ممنوع»
    val notes = DebtorFormsModel.save(ledger, "d", DebtorFormsModel.of(debtor).copy(notes = "عمده"), 1L, denyRules) { "x" } as DebtorEngine.Result.Ok
    assertEquals("عمده", notes.data.debtors.first { it.id == "d" }.notes)
    assertEquals(1000.0, notes.data.debtors.first { it.id == "d" }.creditLimit!!, 0.0)
    //  مشتریِ تازه با پرچمِ خاموش یا نقشِ مجاز ⇒ همان امروز (بی سقف و هر سقف)
    val added = DebtorFormsModel.save(ledger, null, DebtorForm(name = "زرغونه", creditLimit = "9000"), 1L, denyRules.copy(enforced = false)) { "n" } as DebtorEngine.Result.Ok
    assertEquals(9000.0, added.data.debtors.first { it.id == "n" }.creditLimit!!, 0.0)
    assertEquals("", DebtorFormsModel.of(null, denyRules.copy(enforced = false)).creditLimit)
    //  تا سقفِ نقش
    val capped = DebtorFormsModel.save(ledger, "d", form("5000"), 1L, capRules) { "x" } as DebtorEngine.Result.Ok
    assertEquals(5000.0, capped.data.debtors.first { it.id == "d" }.creditLimit!!, 0.0)
    //  بی قاعده (همان صدای پیشین) ⇒ «بی سقف» هم مثلِ امروز
    val free = DebtorFormsModel.save(ledger, "d", form(""), 1L) { "x" } as DebtorEngine.Result.Ok
    assertNull(free.data.debtors.first { it.id == "d" }.creditLimit)
  }

  /**
   *  تصمیمِ ۱۴۰۵/۰۸/۲۳ (#۲۵) — بستنِ دورزدن: کارمندِ «ممنوع» مشتریِ تازه را فقط با سقفِ پیش‌فرضِ دکان (صفر) می‌سازد؛ برگه با
   *  همان «۰» باز می‌شود، سقفِ دیگر یا «بی سقف» رد و هیچ نوشتن؛ با سقفِ نقش تا همان سقف. راه‌های «یک نام» (صندوق، فهرستِ
   *  مشتریان) همان سقفِ پیش‌فرض را می‌گذارند (`CreditLimitPerm.newLimit`) تا سرور ردشان نکند.
   */
  @Test fun newCustomerByARestrictedRoleStartsAtTheShopDefault() {
    assertEquals("0", DebtorFormsModel.of(null, denyRules).creditLimit)
    assertEquals("0", DebtorFormsModel.of(null, capRules).creditLimit)
    val more = DebtorFormsModel.save(ledger, null, DebtorForm(name = "زرغونه", creditLimit = "9000"), 1L, denyRules) { "n" }
    assertEquals(DebtorEngine.Result.Failed(StrSaleCaps.limitNewDefault), more)
    val open = DebtorFormsModel.save(ledger, null, DebtorForm(name = "زرغونه", creditLimit = ""), 1L, denyRules) { "n" }
    assertEquals(DebtorEngine.Result.Failed(StrSaleCaps.limitNewDefault), open)
    val zero = DebtorFormsModel.save(ledger, null, DebtorFormsModel.of(null, denyRules).copy(name = "زرغونه"), 1L, denyRules) { "n" } as DebtorEngine.Result.Ok
    assertEquals(0.0, zero.data.debtors.first { it.id == "n" }.creditLimit!!, 0.0)
    val capped = DebtorFormsModel.save(ledger, null, DebtorForm(name = "پروین", creditLimit = "5000"), 1L, capRules) { "m" } as DebtorEngine.Result.Ok
    assertEquals(5000.0, capped.data.debtors.first { it.id == "m" }.creditLimit!!, 0.0)
    //  «مشتریِ تازه با یک نام»
    assertEquals(0.0, CreditLimitPerm.newLimit(denyRules)!!, 0.0)
    assertNull(CreditLimitPerm.newLimit(CreditLimitPerm.Rules.OFF))
    assertEquals(0.0, CreditLimitGate.quickDraft(denyRules, DebtorEngine.DebtorDraft(name = "نو")).creditLimit!!, 0.0)
    assertNull(CreditLimitGate.quickDraft(CreditLimitPerm.Rules.OFF, DebtorEngine.DebtorDraft(name = "نو")).creditLimit)
  }
}
