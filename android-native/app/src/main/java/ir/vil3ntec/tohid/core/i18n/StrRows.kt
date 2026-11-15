package ir.vil3ntec.tohid.core.i18n

/**
 *  ردیف‌های سادهٔ فهرست‌های نسخهٔ ۳ (`claude/e-rows-63d`): عنوانِ معنادار به‌جای شماره، و جملهٔ کوتاهِ زیرِ آن.
 *  قاعده‌ها سرِ `I18n.kt`؛ «امروز»/«دیروز» همان `Str.today` و `StrKit.yesterday`.
 */
@Suppress("unused")
object StrRows {
  /** جداکنندهٔ نام‌های کالا در عنوانِ فاکتورِ نقدی («برنج، روغن») */
  val namesSep: String get() = t("، ", ", ", "، ")

  /** «برنج، روغن و ۲ قلمِ دیگر» — عنوانِ فاکتورِ بی مشتری */
  fun andMore(names: Any, n: Any): String = t(
    "${asIs(names)} و ${asIs(n)} قلمِ دیگر",
    "$names and $n ${pl(n, "more item", "more items")}",
    "$names او $n نور توکي",
  )

  /** «۵ ردیف» — شمارِ ردیف‌های سفارشِ خرید */
  fun rows(n: Any): String = t("${asIs(n)} ردیف", "$n ${pl(n, "row", "rows")}", "$n قطارونه")

  /** «باقی‌مانده ۱٬۰۰۰ ؋» — فقط وقتی سفارش نیمه رسیده است */
  fun remaining(amount: Any): String = t("باقی‌مانده ${asIs(amount)} ؋", "remaining $amount ؋", "پاتې $amount ؋")

  /** «رسیدن ۲۸ میزان» — روزِ رسیدنِ سفارش، کوتاه */
  fun arrives(day: Any): String = t("رسیدن ${asIs(day)}", "arrives $day", "رسېدل $day")

  /* ── ارجاعِ فاکتور (`claude/e-rows-63q`، `InvoiceRef`) ── */

  /** «فاکتور #۱۲ · پیدا نشد» — ارجاعی که فاکتورش در این دکان نیست */
  val invoiceMissing: String get() = t("پیدا نشد", "not found", "ونه موندل شو")
}
