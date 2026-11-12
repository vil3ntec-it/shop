package ir.vil3ntec.tohid.core.i18n

import ir.vil3ntec.tohid.core.orders.OrderRefundSlip

/**
 *  E2E-RET-11 — «رسیدِ اصلاحیِ بازپرداخت» ِ سفارشِ اینترنتی (`core/orders/OrderRefundSlip`): کاغذ، بخشِ صفحهٔ سفارش و
 *  پیام‌های صفِ چاپ. قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrRefundSlip : OrderRefundSlip.Texts {

  // کاغذ
  override fun title(no: String): String = t("رسیدِ اصلاحی ${asIs(no)}", "Correction receipt $no", "اصلاحي رسید $no")
  override fun forOrder(orderNo: String): String = t("بازپرداختِ سفارش #${asIs(orderNo)}", "Refund for order #$orderNo", "د فرمایش #$orderNo بېرته ورکړه")
  override fun issuedAt(at: String): String = t("ثبت: ${asIs(at)}", "Recorded: $at", "ثبت: $at")
  override fun orderTotal(amount: String): String = t("جمعِ سفارش: ${asIs(amount)} ؋", "Order total: $amount ؋", "د فرمایش ټوله: $amount ؋")
  override fun refundedBefore(amount: String): String = t("برگشته پیش از این: ${asIs(amount)} ؋", "Refunded before: $amount ؋", "له دې مخکې بېرته: $amount ؋")
  override fun thisRefund(amount: String): String = t("همین بازپرداخت: ${asIs(amount)} ؋", "This refund: $amount ؋", "همدا بېرته ورکړه: $amount ؋")
  override fun refundedAfter(amount: String): String = t("برگشته تا این رسید: ${asIs(amount)} ؋", "Refunded so far: $amount ؋", "تر دې رسیده بېرته: $amount ؋")
  override fun net(amount: String): String = t("مبلغِ درستِ سفارش: ${asIs(amount)} ؋", "Corrected order amount: $amount ؋", "د فرمایش سمه اندازه: $amount ؋")
  override val cash: String get() = t("روش: نقد", "Method: cash", "لاره: نغدې")
  override val hesabpay: String get() = t("روش: حساب‌پی", "Method: HesabPay", "لاره: حساب‌پې")
  override val closed: String get() = t("سفارش با همین رسید بسته شد", "The order was closed with this receipt", "فرمایش په همدې رسید وتړل شو")
  override fun reason(text: String): String = t("دلیل: ${asIs(text)}", "Reason: $text", "لامل: $text")
  override fun printedAt(at: String): String = t("چاپ: ${asIs(at)}", "Printed: $at", "چاپ: $at")

  // صفحهٔ سفارش
  val section: String get() = t("رسیدهای اصلاحی", "Correction receipts", "اصلاحي رسیدونه")
  fun row(amount: String, net: String): String = t(
    "بازپرداخت ${asIs(amount)} ؋ · مبلغِ درست ${asIs(net)} ؋",
    "Refund $amount ؋ · corrected $net ؋",
    "بېرته $amount ؋ · سمه اندازه $net ؋",
  )
  val print: String get() = t("چاپ", "Print", "چاپ")

  // صفِ چاپ
  fun jobTitle(no: String): String = t("رسیدِ اصلاحی ${asIs(no)}", "Correction receipt $no", "اصلاحي رسید $no")
  val printed: String get() = t("رسیدِ اصلاحی چاپ شد", "Correction receipt printed", "اصلاحي رسید چاپ شو")
  fun failed(why: String): String = t("رسیدِ اصلاحی چاپ نشد: ${asIs(why)}", "Correction receipt not printed: $why", "اصلاحي رسید چاپ نه شو: $why")
  val mismatch: String get() = t(
    "رسیدِ اصلاحی چاپ نشد: عددهای سرور با هم نمی‌خوانند — صفحه را تازه کنید",
    "Correction receipt not printed: the server's numbers don't add up — refresh the page",
    "اصلاحي رسید چاپ نه شو: د سرور شمېرې سره سمې نه دي — پاڼه تازه کړئ",
  )
}
