package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.data.DebtBook
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.formatMonth
import ir.vil3ntec.tohid.money

/**
 *  قالبِ عدد و تاریخِ صفحه‌های پول — همان تابع‌های برنامه (`money`، `displayDate`، `formatMonth`)
 *  با نمادِ «؋»؛ هیچ حسابی این‌جا نیست، فقط شکلِ نوشتن.
 */
internal object MoneyText {
  /** مبلغ با «؋» — «1,200 ؋» */
  fun afn(value: Double): String = "${money(value)} ؋"

  /** مبلغِ علامت‌دار «+۱٬۲۰۰» / «−۴۰۰» — جداشده به چپ‌به‌راست تا علامت در صفحهٔ راست‌به‌چپ پیشِ عدد بماند */
  fun signed(value: Double, plus: Boolean): String = "\u2066${if (plus) "+" else "−"}${money(value)}\u2069"

  /** تاریخِ خورشیدی، یا «—» برای ردیفِ بی‌تاریخ (پشتیبانِ خیلی قدیمی) */
  fun date(iso: String): String = if (iso.isBlank()) DebtorLedgerModel.UNDATED else displayDate(iso)

  /** `2026-10` ⇐ «میزان ۱۴۰۵» */
  fun month(month: String): String = formatMonth(month)

  /** حالِ حساب سه‌زبانه — همان جمله‌های `DebtorEngine.stateText` (که فقط فارسی است) */
  fun state(balance: Double): String = when {
    balance > 0 -> StrMoney.owesAfn(money(balance))
    balance < 0 -> StrMoney.creditAfn(money(-balance))
    else -> StrMoney.accountSettled
  }

  /** همان، برای مانده‌های `DebtBook` (به جای `DebtBook.state`) */
  fun state(m: Money): String = state(m.minor.toDouble() / DebtBook.CURRENCY.minorPerMajor)

  /** «۲۰۰ افغانی بیش از سقفِ اعتبار (۱٬۰۰۰) — ماندهٔ فعلی ۹۰۰» سه‌زبانه (به جای `DebtBook.overLimitText`) */
  fun overLimit(c: DebtBook.Credit): String =
    "${StrMoney.overLimitBy(DebtBook.show(c.excess), DebtBook.show(c.limit))} — ${StrMoney.currentBalance(DebtBook.show(c.balance))}"

  /** مقدار با واحد: «20 کیلوگرم» */
  fun qtyUnit(quantity: Double, unit: String): String =
    ir.vil3ntec.tohid.qty(quantity) + if (unit.isNotBlank()) " $unit" else ""
}
