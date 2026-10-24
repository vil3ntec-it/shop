package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Supplier

/** نوشته‌های برگهٔ تأمین‌کننده (`purchasing/suppliers/new|{id}/edit`) */
internal data class SupplierForm(val name: String = "", val phone: String = "", val address: String = "", val notes: String = "")

/** نوشته‌های برگهٔ پرداخت (`purchasing/suppliers/{id}/pay`) */
internal data class PayForm(val amount: String = "", val date: String = "", val notes: String = "")

/** نوشته‌های برگهٔ مصرف (`expenses/new`، `expenses/{id}`) */
internal data class ExpenseForm(val amount: String = "", val category: String = "", val title: String = "", val date: String = "")

/**
 *  فرم‌های تأمین‌کننده، پرداخت و مصرف — منطقِ خالص، زیرِ آزمون. ثبت همان `LedgerEngine` است؛
 *  این‌جا فقط نوشته ⇐ پیش‌نویسِ موتور و خطای درجا.
 */
internal object LedgerFormsModel {

  private fun num(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  fun of(s: Supplier?): SupplierForm = if (s == null) SupplierForm() else SupplierForm(s.name, s.phone, s.address, s.notes)

  fun nameError(f: SupplierForm): String? = if (f.name.isBlank()) StrKit.required else null

  fun draft(f: SupplierForm) = LedgerEngine.SupplierDraft(f.name, f.phone, f.address, f.notes)

  fun payAmount(f: PayForm): Double = num(f.amount) ?: 0.0

  fun payError(f: PayForm): String? = if (payAmount(f) <= 0) StrKit.notANumber else null

  /** «تسویهٔ کامل» ِ پرداخت: همهٔ بدهیِ ما، گرد؛ بدهکار نیستیم ⇒ null */
  fun settleAll(debt: Double): String? = if (debt > 0) Math.round(debt).toString() else null

  /* ------------------------------ مصرف ------------------------------ */

  fun of(e: Expense?, today: String): ExpenseForm =
    if (e == null) ExpenseForm(date = today)
    else ExpenseForm(
      amount = if (e.amount == Math.floor(e.amount)) e.amount.toLong().toString() else e.amount.toString(),
      category = e.category,
      title = if (e.title == e.category) "" else e.title,
      date = e.date,
    )

  fun expenseAmountError(f: ExpenseForm): String? = num(f.amount).let { if (it == null || it <= 0) StrKit.notANumber else null }

  fun draft(f: ExpenseForm) = LedgerEngine.ExpenseDraft(title = f.title, category = f.category, amount = num(f.amount) ?: 0.0, date = f.date)

  /** دسته‌های تراشه: دسته‌های دکان، و دستهٔ همین مصرف اگر در فهرست نیست */
  fun categories(all: List<String>, current: String): List<String> =
    if (current.isBlank() || current in all) all else all + current
}
