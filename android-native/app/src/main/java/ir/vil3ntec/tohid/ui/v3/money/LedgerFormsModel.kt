package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.core.safe.SafeCurrency
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.SafeCurrencies
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Supplier

/** نوشته‌های برگهٔ تأمین‌کننده (`purchasing/suppliers/new|{id}/edit`) */
internal data class SupplierForm(val name: String = "", val phone: String = "", val address: String = "", val notes: String = "", val leadDays: String = "")

/**
 *  نوشته‌های برگهٔ پرداخت (`purchasing/suppliers/{id}/pay`). PUR-11: [account] = ارزِ حسابِ تأمین‌کننده (`null` = افغانی، همان
 *  برگهٔ پیشین)؛ حسابِ ارزی با [inAfn] به افغانی یا به همان ارز، با نرخِ خودِ این پرداخت ([rate] پول برای [per] واحد).
 */
internal data class PayForm(
  val amount: String = "", val date: String = "", val notes: String = "",
  val account: String? = null, val inAfn: Boolean = false, val rate: String = "", val per: Long = 1,
)

/** نوشته‌های برگهٔ مصرف (`expenses/new`، `expenses/{id}`) */
internal data class ExpenseForm(val amount: String = "", val category: String = "", val title: String = "", val date: String = "")

/**
 *  فرم‌های تأمین‌کننده، پرداخت و مصرف — منطقِ خالص، زیرِ آزمون. ثبت همان `LedgerEngine` است؛
 *  این‌جا فقط نوشته ⇐ پیش‌نویسِ موتور و خطای درجا.
 */
internal object LedgerFormsModel {

  private fun num(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  fun of(s: Supplier?): SupplierForm =
    if (s == null) SupplierForm() else SupplierForm(s.name, s.phone, s.address, s.notes, s.leadDays?.toString().orEmpty())

  fun nameError(f: SupplierForm): String? = if (f.name.isBlank()) StrKit.required else null

  /** PUR-10 — زمانِ تأمینِ دستی: خالی ⇒ null؛ عددِ کامل ۱ تا ۳۶۵ (رقمِ فارسی هم)؛ وگرنه -1 */
  fun leadDays(f: SupplierForm): Int? {
    val t = ir.vil3ntec.tohid.normalizeDigits(f.leadDays).trim()
    if (t.isEmpty()) return null
    return t.toIntOrNull()?.takeIf { it in 1..ir.vil3ntec.tohid.core.purchase.ReorderCore.MAX_MANUAL_LEAD_DAYS } ?: -1
  }

  fun leadError(f: SupplierForm): String? = if (leadDays(f) == -1) StrMoney.leadDaysBad else null

  fun draft(f: SupplierForm) = LedgerEngine.SupplierDraft(f.name, f.phone, f.address, f.notes, leadDays = leadDays(f)?.takeIf { it > 0 }, leadSet = true)

  fun payAmount(f: PayForm): Double = num(f.amount) ?: 0.0

  fun payError(f: PayForm): String? =
    if (f.account != null) (if (fxMinor(f) == null) StrKit.notANumber else if (fxRate(f) == null) StrPurchaseFx.rateBad else null)
    else if (payAmount(f) <= 0) StrKit.notANumber else null

  /* ── PUR-11: پرداختِ حسابِ ارزی ─────────────────────────────────── */

  /** نرخِ همین پرداخت (پول برای per واحد)؛ افغانی یا نادرست ⇒ null */
  fun fxRate(f: PayForm): PurchaseFx.Rate? {
    val code = f.account ?: return null
    val pul = FxTill.parseAmount(f.rate, PurchaseFx.AFN) ?: return null
    return PurchaseFx.rate(code, pul, f.per)
  }

  /** مبلغِ تایپی به واحدِ کوچک: به افغانی ⇒ پول، به ارز ⇒ واحدِ کوچکِ ارز؛ صفر یا نادرست ⇒ null */
  fun fxMinor(f: PayForm): Long? {
    val code = if (f.inAfn) PurchaseFx.AFN else f.account ?: return null
    return f.amount.takeIf { it.isNotBlank() }?.let { SafeCurrencies.parse(it, code) }?.takeIf { it > 0 }
  }

  /** پیش‌نمایشِ اثر (همان حسابِ ثبت، `SupplierFx.pay`): صاف‌شده، ارزش به نرخِ پرداخت و اختلاف؛ ورودیِ نادرست ⇒ null */
  fun fxSettle(f: PayForm, acc: PurchaseFx.Account?): PurchaseFx.Settle? {
    val a = acc ?: return null
    val r = fxRate(f) ?: return null
    val m = fxMinor(f) ?: return null
    return if (f.inAfn) PurchaseFx.payInAfn(a, m, r) else PurchaseFx.payInFx(a, m, r)
  }

  /**
   *  حسابِ برگه ⇒ فرم: ارزِ تازه مبلغ را پاک و نرخ را از [lastRate] پیش‌پُر می‌کند؛ افغانی ⇒ همان برگهٔ پیشین (بی نرخ).
   */
  fun setAccount(f: PayForm, code: String?, lastRate: PurchaseFx.Rate? = null): PayForm {
    val c = code?.takeIf { it != PurchaseFx.AFN }
    if (c == f.account) return f
    if (c == null) return f.copy(account = null, inAfn = false, rate = "", per = 1, amount = "")
    val r = lastRate?.takeIf { it.currency == c }
    return f.copy(account = c, inAfn = false, amount = "", rate = r?.let { FxTill.afnText(it.rate) }.orEmpty(), per = r?.per ?: PurchaseFx.defaultPer(c))
  }

  /** «تسویهٔ کامل» ِ حسابِ ارزی: به ارز ⇒ همهٔ مانده؛ به افغانی ⇒ همان مانده با نرخِ همین پرداخت؛ بدهکار نیستیم ⇒ null */
  fun settleAllFx(f: PayForm, acc: PurchaseFx.Account?): String? {
    val a = acc?.takeIf { it.fx > 0 } ?: return null
    return if (!f.inAfn) PoForms.num(SafeCurrency.major(a.fx, a.currency))
    else fxRate(f)?.let { r -> runCatching { PurchaseFx.toAfn(a.fx, r) }.getOrNull() }?.let { PoForms.num(it / 100.0) }
  }

  /** «تسویهٔ کامل» ِ پرداخت: همهٔ بدهیِ ما تا پول، بی گرد؛ بدهکار نیستیم ⇒ null */
  fun settleAll(debt: Double): String? = MoneyText.settleInput(debt)

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
