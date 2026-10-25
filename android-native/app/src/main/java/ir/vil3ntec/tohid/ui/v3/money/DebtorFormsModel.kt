package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.latinDigits

/** نوشته‌های برگهٔ قرض‌دار (`debtors/new`، `debtors/{id}/edit`) */
internal data class DebtorForm(val name: String = "", val phone: String = "", val notes: String = "", val creditLimit: String = "")

/** نوشته‌های برگهٔ تراکنش (`debtors/{id}/tx?kind=`) */
internal data class TxForm(
  val kind: DebtorEngine.Kind = DebtorEngine.Kind.GIVE,
  val amount: String = "",
  val date: String = "",
  val notes: String = "",
  /** سررسیدِ برد (`yyyy-MM-dd`)؛ خالی ⇒ ندارد */
  val due: String = "",
)

/**
 *  فرم‌های قرض‌دار و تراکنش — منطقِ خالص، زیرِ آزمون. سنجشِ درجا همان شرط‌های موتور است
 *  (`DebtorEngine.add/editWithLimit/addTransaction`)؛ تصمیمِ «بیش از سقف» همان `DebtBook.creditCheck`.
 */
internal object DebtorFormsModel {

  fun of(debtor: Debtor?): DebtorForm =
    if (debtor == null) DebtorForm()
    else DebtorForm(debtor.name, debtor.phone, debtor.notes, limitText(debtor.creditLimit))

  /** متنِ کادرِ سقف ⇒ عدد؛ خالی ⇒ null (بی سقف)؛ نوشتهٔ نامعتبر ⇒ NaN تا موتور ردش کند، نه این‌که بی‌صدا «بی سقف» شود */
  fun parseLimit(text: String): Double? =
    latinDigits(text).trim().takeIf { it.isNotEmpty() }?.let { it.toDoubleOrNull() ?: Double.NaN }

  fun limitText(v: Double?): String = v?.let { if (it == Math.floor(it)) it.toLong().toString() else it.toString() }.orEmpty()

  fun nameError(f: DebtorForm): String? = if (f.name.isBlank()) StrKit.required else null

  fun limitError(f: DebtorForm): String? = parseLimit(f.creditLimit)?.let { if (it.isNaN() || it < 0) StrKit.notANumber else null }

  fun draft(f: DebtorForm) = DebtorEngine.DebtorDraft(f.name, f.phone, f.notes, parseLimit(f.creditLimit))

  /** ذخیرهٔ برگه: قرض‌دارِ تازه (`add`) یا ویرایش (`editWithLimit`) */
  fun save(d: ShopData, editingId: String?, f: DebtorForm, now: Long, newId: () -> String): DebtorEngine.Result =
    if (editingId == null) DebtorEngine.add(d, draft(f), now, newId) else DebtorEngine.editWithLimit(d, editingId, draft(f))

  /* ------------------------------ تراکنش ------------------------------ */

  fun amountOf(f: TxForm): Double = f.amount.trim().toDoubleOrNull() ?: 0.0

  fun amountError(f: TxForm): String? = if (amountOf(f) <= 0) StrKit.notANumber else null

  /** «مانده پس از ثبت» — بدهی + برد یا − رسید */
  fun balanceAfter(balance: Double, f: TxForm): Double =
    if (f.kind == DebtorEngine.Kind.GIVE) balance + amountOf(f) else balance - amountOf(f)

  /** «تسویهٔ کامل» (فقط رسید و فقط وقتی بدهکار است): همهٔ مانده، گرد */
  fun settleAll(balance: Double): String? = if (balance > 0) Math.round(balance).toString() else null

  /** بردی که از سقف می‌گذرد ⇒ گامِ دوم با هشدار؛ رسید یا بی سقف ⇒ null */
  fun overLimit(d: ShopData, debtorId: String, f: TxForm): DebtBook.Credit? {
    if (f.kind != DebtorEngine.Kind.GIVE || amountOf(f) <= 0) return null
    return DebtBook.creditCheck(d, debtorId, DebtBook.minor(amountOf(f)))?.takeIf { it.over }
  }

  fun submit(d: ShopData, debtorId: String, f: TxForm, today: String, now: Long, newId: () -> String, overLimit: Boolean): DebtorEngine.Result =
    DebtorEngine.addTransaction(
      d, debtorId, f.kind, amountOf(f), f.date, f.notes, today, now, newId,
      dueDate = f.due.ifBlank { null }.takeIf { f.kind == DebtorEngine.Kind.GIVE },
      overLimit = overLimit,
    )
}
