package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.formatMonth
import ir.vil3ntec.tohid.money

/**
 *  قالبِ عدد و تاریخِ صفحه‌های پول — همان تابع‌های برنامه (`money`، `formatDate`، `formatMonth`)
 *  با نمادِ «؋»؛ هیچ حسابی این‌جا نیست، فقط شکلِ نوشتن.
 */
internal object MoneyText {
  /** مبلغ با «؋» — «1,200 ؋» */
  fun afn(value: Double): String = "${money(value)} ؋"

  /** مبلغِ علامت‌دار «+۱٬۲۰۰» / «−۴۰۰» — جداشده به چپ‌به‌راست تا علامت در صفحهٔ راست‌به‌چپ پیشِ عدد بماند */
  fun signed(value: Double, plus: Boolean): String = "\u2066${if (plus) "+" else "−"}${money(value)}\u2069"

  /** تاریخِ خورشیدی، یا «—» برای ردیفِ بی‌تاریخ (پشتیبانِ خیلی قدیمی) */
  fun date(iso: String): String = if (iso.isBlank()) DebtorLedgerModel.UNDATED else formatDate(iso)

  /** `2026-10` ⇐ «میزان ۱۴۰۵» */
  fun month(month: String): String = formatMonth(month)

  /** حالِ حساب سه‌زبانه — همان جمله‌های `DebtorEngine.stateText` (که فقط فارسی است) */
  fun state(balance: Double): String = when {
    balance > 0 -> StrMoney.owesAfn(money(balance))
    balance < 0 -> StrMoney.creditAfn(money(-balance))
    else -> StrMoney.accountSettled
  }

  /** چند روز به متنِ سه‌زبانه — همان حسابِ `daysText` (ماه ۳۰ روز، سال ۳۶۵ روز) */
  fun days(n: Long): String {
    val d = n.coerceAtLeast(0)
    return when {
      d < 1 -> Str.today
      d < 31 -> Str.days(d)
      d < 365 -> Str.months(d / 30)
      else -> if ((d % 365) / 30 == 0L) Str.years(d / 365) else Str.yearsMonths(d / 365, (d % 365) / 30)
    }
  }

  /** «چقدر گذشته» از یک لحظه — همان `spanText`، سه‌زبانه */
  fun span(from: Long, now: Long): String = if (from <= 0) "—" else days((now - from) / 86_400_000L)

  /** مقدار با واحد: «20 کیلوگرم» */
  fun qtyUnit(quantity: Double, unit: String): String =
    ir.vil3ntec.tohid.qty(quantity) + if (unit.isNotBlank()) " $unit" else ""
}
