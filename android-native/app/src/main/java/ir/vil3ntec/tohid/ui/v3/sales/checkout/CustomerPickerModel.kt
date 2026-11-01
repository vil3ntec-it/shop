package ir.vil3ntec.tohid.ui.v3.sales.checkout

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money

/** حالِ حسابِ یک قرض‌دار — رنگ همیشه کنارِ برچسب، نه به‌تنهایی */
enum class BalanceTone { OWES, CREDIT, SETTLED }

/** یک ردیفِ برگهٔ «انتخابِ مشتری» */
data class CustomerRow(
  val id: String,
  val name: String,
  val phone: String,
  /** بدهی (مثبت) یا پیش‌پرداخت (منفی) — `ShopStore.debt` */
  val balance: Double,
) {
  val tone: BalanceTone get() = CustomerPickerModel.tone(balance)
}

/**
 *  برگهٔ «انتخابِ مشتری» (`sale/checkout/customer`) — همان `DebtorPicker` ِ پیشین: جست‌وجو
 *  در نام، حالِ حساب با همان جمله‌ها («… بدهکار»، «… موجودی دارد»، «حساب صاف است») و همان
 *  دو متنِ خالی. ترتیب همان ترتیبِ دفتر.
 */
object CustomerPickerModel {

  /** نتیجهٔ «بدون قرض‌دار» با `nav.finish` (شناسهٔ خالی) */
  const val NONE = ""

  fun rows(d: ShopData, query: String): List<CustomerRow> {
    val q = query.trim()
    return d.debtors
      .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
      .map { CustomerRow(it.id, it.name, it.phone, ShopStore.debt(d, it.id)) }
  }

  fun tone(balance: Double): BalanceTone = when {
    balance > 0 -> BalanceTone.OWES
    balance < 0 -> BalanceTone.CREDIT
    else -> BalanceTone.SETTLED
  }

  /** «۱٬۲۰۰ ؋ بدهکار» / «۳۰۰ ؋ موجودی دارد» / «حساب صاف است» */
  fun label(balance: Double): String = when (tone(balance)) {
    BalanceTone.OWES -> StrSale.owes(money(balance))
    BalanceTone.CREDIT -> StrSale.hasCredit(money(-balance))
    BalanceTone.SETTLED -> StrSale.settled
  }

  /** فهرست خالی است ⇒ کدام جمله */
  fun emptyText(d: ShopData): String = if (d.debtors.isEmpty()) StrSale.noDebtorsYet else StrSale.debtorNotFound
}
