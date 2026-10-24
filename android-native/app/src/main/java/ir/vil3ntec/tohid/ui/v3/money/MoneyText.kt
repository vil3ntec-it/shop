package ir.vil3ntec.tohid.ui.v3.money

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

  /** تاریخِ خورشیدی، یا «—» برای ردیفِ بی‌تاریخ (پشتیبانِ خیلی قدیمی) */
  fun date(iso: String): String = if (iso.isBlank()) DebtorLedgerModel.UNDATED else formatDate(iso)

  /** `2026-10` ⇐ «میزان ۱۴۰۵» */
  fun month(month: String): String = formatMonth(month)

  /** مقدار با واحد: «20 کیلوگرم» */
  fun qtyUnit(quantity: Double, unit: String): String =
    ir.vil3ntec.tohid.qty(quantity) + if (unit.isNotBlank()) " $unit" else ""
}
