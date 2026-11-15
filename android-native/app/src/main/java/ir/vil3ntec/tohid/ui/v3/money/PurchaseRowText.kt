package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.LocaleChoice
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrHistory
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchases
import ir.vil3ntec.tohid.core.i18n.StrRows
import ir.vil3ntec.tohid.data.PurchasePeriods
import ir.vil3ntec.tohid.data.PurchaseViews
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.v3.RowWhen
import ir.vil3ntec.tohid.ui.v3.V3RowMeta
import ir.vil3ntec.tohid.ui.v3.V3RowStatus
import ir.vil3ntec.tohid.ui.v3.V3RowTone

/**
 *  متن‌های آمادهٔ ردیفِ «خریدها» (e-rows-63b، قاعدهٔ ردیفِ ۶۳d): عنوان نامِ تأمین‌کننده؛ زیرِ آن روزِ کوتاه و اقلام
 *  («امروز · آرد، روغن و ۳ قلمِ دیگر»). [pay]/[receipt] نوشتهٔ کاملِ جزئیات («نیمه‌پرداخت ۲٬۰۰۰ ؋ مانده • رسیده»)؛ [meta]
 *  خطِ مبلغِ یک‌خطیِ فهرست (۶۸c، کالبدِ `V3DataRow`): «۱۲٬۰۰۰ ؋ • ۲٬۰۰۰ ؋ مانده» — یک وضعیتِ کوتاهِ پرداخت و فقط وقتی کالا
 *  هنوز نرسیده «نرسیده»؛ «رسیده» ِ عادی در فهرست نمی‌آید (در جزئیات هست). رنگ فقط برای «سررسید گذشته». خالص، بی Compose.
 */
internal class PurchaseRowText(
  val title: String,
  val subtitle: String,
  val amount: String,
  val pay: V3RowStatus?,
  val receipt: V3RowStatus?,
  /** خطِ مبلغِ فهرست */
  val meta: V3RowMeta = V3RowMeta(amount, pay, receipt),
) {
  companion object {
    fun of(v: PurchaseViews.View, today: String): PurchaseRowText {
      val day = MoneyText.short(v.date, today)
      val head = listOfNotNull(day, v.no.takeIf { it > 0 && v.isOrder }?.let(StrPurchases::orderNo)).joinToString(" · ")
      val amount = amount(v)
      return PurchaseRowText(
        title = v.supplierName.ifBlank { StrMoney.supplierLabel },
        subtitle = "$head · ${items(v)}",
        amount = amount,
        pay = pay(v, today),
        receipt = receipt(v),
        meta = V3RowMeta(amount, rowPay(v, today), rowReceipt(v)),
      )
    }

    /**
     *  وضعیتِ کوتاهِ پرداخت در خطِ مبلغِ فهرست: «پرداخت‌شده»، «۳٬۳۲۰ ؋ مانده»، «سررسید ۴ قوس» (نسیه)، «نسیه»، «سررسید گذشته»
     *  (تنها رنگِ ردیف)، «برگشت». همان شدّتِ [pay].
     */
    fun rowPay(v: PurchaseViews.View, today: String): V3RowStatus? = when (v.pay) {
      PurchaseViews.Pay.PARTIAL -> V3RowStatus(StrPurchases.leftAll(money(v.remaining)), V3RowTone.ATTENTION)
      PurchaseViews.Pay.CREDIT -> V3RowStatus(v.dueDate?.let { StrPurchases.dueOn(RowWhen.of(it, today)) } ?: StrPurchases.credit, V3RowTone.ATTENTION)
      PurchaseViews.Pay.OVERDUE -> V3RowStatus(StrPurchases.overdueRow, V3RowTone.DANGER)
      else -> pay(v, today)
    }

    /**
     *  وضعیتِ دومِ خطِ مبلغ: سررسیدگذشتهٔ نیمه‌پرداخت ⇒ «۲٬۱۰۰ ؋ مانده»؛ وگرنه رسیدن فقط وقتی کار مانده («نیمه‌رسیده»، «هنوز
     *  نرسیده»)؛ «رسیده» ِ عادی ⇒ null.
     */
    fun rowReceipt(v: PurchaseViews.View): V3RowStatus? = when {
      v.pay == PurchaseViews.Pay.OVERDUE && v.remaining < kotlin.math.abs(v.total) - 0.5 -> V3RowStatus(StrPurchases.leftAll(money(v.remaining)))
      else -> receipt(v)?.takeIf { v.receipt != PurchaseViews.Receipt.RECEIVED }
    }

    /** «۱۲٬۰۰۰ ؋»؛ سندِ کسر «−۴۰۰ ؋» */
    fun amount(v: PurchaseViews.View): String =
      if (v.kind == PurchaseViews.Kind.RETURN) MoneyText.signed(-v.total, plus = false) + " ؋" else MoneyText.afn(v.total)

    /** «آرد»، «آرد، روغن»، «آرد، روغن و ۳ قلمِ دیگر»؛ کالای حذف‌شده ⇒ «(محصول حذف‌شده)» */
    fun items(v: PurchaseViews.View): String {
      val names = v.names
      if (names.isEmpty()) return StrMoney.deletedProduct
      val shown = names.take(2).joinToString(StrRows.namesSep)
      return if (names.size <= 2) shown else StrRows.andMore(shown, names.size - 2)
    }

    fun pay(v: PurchaseViews.View, today: String): V3RowStatus? = when (v.pay) {
      PurchaseViews.Pay.PAID -> V3RowStatus(StrPurchases.paid)
      PurchaseViews.Pay.PARTIAL -> V3RowStatus(StrPurchases.partly(money(v.remaining)), V3RowTone.ATTENTION)
      PurchaseViews.Pay.CREDIT -> V3RowStatus(
        v.dueDate?.let { StrPurchases.creditDue(RowWhen.of(it, today)) } ?: StrPurchases.credit,
        V3RowTone.ATTENTION,
      )
      PurchaseViews.Pay.OVERDUE -> V3RowStatus(StrPurchases.overdue(money(v.remaining)), V3RowTone.DANGER)
      PurchaseViews.Pay.NONE -> if (v.kind == PurchaseViews.Kind.RETURN) V3RowStatus(StrPurchases.returned) else null
    }

    fun receipt(v: PurchaseViews.View): V3RowStatus? = when {
      v.kind == PurchaseViews.Kind.RETURN -> null
      v.receipt == PurchaseViews.Receipt.RECEIVED -> V3RowStatus(StrPurchases.received)
      v.receipt == PurchaseViews.Receipt.PARTIAL -> V3RowStatus(StrPurchases.partReceived, V3RowTone.ATTENTION)
      else -> V3RowStatus(StrPurchases.notReceived, V3RowTone.ATTENTION)
    }

    /** نوشتهٔ کادرِ دوره: «همهٔ زمان‌ها»، «امروز»، «این ماه»، «میزان ۱۴۰۵»، «امسال»، «۱۴۰۴» */
    fun period(key: String?, today: String, gregorian: Boolean = gregorian()): String {
      if (key == null) return StrPurchases.allTime
      return when (PurchasePeriods.kind(key, today, gregorian)) {
        PurchasePeriods.Kind.TODAY -> Str.today
        PurchasePeriods.Kind.THIS_MONTH -> StrHistory.thisMonth
        PurchasePeriods.Kind.THIS_YEAR -> StrHistory.thisYear
        PurchasePeriods.Kind.MONTH -> PurchasePeriods.yearMonth(key)?.let { (y, m) ->
          val names = if (gregorian) I18n.gregorianMonths(I18n.lang) else I18n.solarMonths(I18n.lang, LocaleChoice.months)
          names.getOrNull(m - 1)?.let { "$it $y" }
        } ?: key.substringAfter(':')
        PurchasePeriods.Kind.YEAR, PurchasePeriods.Kind.OTHER -> key.substringAfter(':')
      }
    }

    fun gregorian(): Boolean = I18n.gregorian(I18n.lang, LocaleChoice.calendar)
  }
}
