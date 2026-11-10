package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrdersConfigDto
import ir.vil3ntec.tohid.core.orders.OrderStates

/**
 *  FIN-01 — «ثبتِ بازپرداخت» با مبلغ در برگهٔ گذار (`orders/{id}/action?to=refunded_*`) وقتی دفترِ بازپرداختِ سرور روشن
 *  است (`refundLedger` ِ `/config`، پرچمِ خاموشِ `shop.orders.refunds`). آن‌وقت سرور گذارِ سادهٔ «بازپرداخت‌شده» را با ۴۰۹
 *  `refund_via_ledger` رد می‌کند و برنامه تا امروز هیچ راهی نداشت؛ حالا برگه مبلغ و دلیل می‌گیرد و به `POST /:id/refunds`
 *  می‌رود (کلیدِ یکتای همان برگه، فقط نقد). خاموش ⇒ همان گذارِ پیشین، بیت‌به‌بیت. آزمون: `OrderRefundModelTest`.
 *
 *  • «بازپرداختِ کامل»: مبلغ = ماندهٔ قابلِ برگشت (`refund.remainingMinor`، سرورِ بی آن ⇒ جمعِ سفارش)، از پیش پُر.
 *  • «بازپرداختِ بخشی»: بیش از صفر و کمتر از مانده؛ سفارش با همین ردیف بسته می‌شود (`final`).
 *  ⛔ هیچ مبلغی این‌جا گرد یا کم‌وزیاد نمی‌شود؛ سرور دوباره با مانده می‌سنجد (بیش ⇒ ۴۰۹ و هیچ ردیف).
 */
object OrderRefundModel {

  const val REASON_MAX = 300

  enum class Problem { AMOUNT, NOT_REMAINING, NOT_BELOW_REMAINING, REASON }

  fun ledger(cfg: OrdersConfigDto?, to: String): Boolean = cfg?.refundLedger == true && to in OrderStates.MONEY_TARGETS

  /** ORD-15 / تصمیمِ ۱-۶ ب: روش‌هایی که این برنامه می‌شناسد — «حساب‌پی» فقط ثبتِ پولی است که دکان خودش پس داد */
  val KNOWN_METHODS: List<String> = listOf("cash", "hesabpay")

  /** تراشه‌های روش فقط وقتی سرور بیش از نقد را می‌پذیرد (`refundMethods` ِ `/config`)؛ روشِ ناشناخته کنار می‌رود */
  fun methods(cfg: OrdersConfigDto?): List<String> =
    cfg?.refundMethods.orEmpty().filter { it in KNOWN_METHODS }.distinct().takeIf { it.size > 1 }.orEmpty()

  /** روشی که به سرور می‌رود: همان انتخاب اگر پذیرفته است، وگرنه نقد (سرورِ پیشین هم همین را می‌پذیرد) */
  fun methodFor(chosen: String, methods: List<String>): String = if (chosen in methods) chosen else "cash"

  fun remainingMinor(o: OrderDto): Long = o.refund?.remainingMinor ?: o.totalMinor

  /** کادرِ مبلغ در آغاز: «کامل» همان مانده؛ «بخشی» خالی تا کاربر خودش بنویسد */
  fun startAmount(o: OrderDto, to: String): String = if (to == "refunded_full") afnText(remainingMinor(o)) else ""

  /** واحدِ خرد ⇒ نوشتهٔ افغانی بی جداکننده: ۱۲۵۰۰۰ ⇒ «1250»، ۱۲۵۰۵۰ ⇒ «1250.5» */
  fun afnText(minor: Long): String {
    val whole = minor / 100
    val frac = minor % 100
    return if (frac == 0L) "$whole" else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
  }

  /**
   *  نوشتهٔ مبلغ (افغانی؛ رقمِ فارسی/عربی، «٫» و جداکنندهٔ هزار پذیرفته) ⇒ واحدِ خرد؛ حداکثر دو رقمِ اعشار؛ صفر، منفی یا
   *  نوشتهٔ نادرست ⇒ `null` (نه گرد کردنِ بی‌صدا).
   */
  fun amountMinor(text: String): Long? {
    //  جداکنندهٔ هزارِ عربی (٬) پیش از `normalizeDigits` برداشته می‌شود، چون آن را هم «.» می‌کند
    val s = ir.vil3ntec.tohid.normalizeDigits(text.replace("\u066C", "")).trim().replace(",", "")
    val m = Regex("^([0-9]{1,12})(?:\\.([0-9]{1,2}))?$").matchEntire(s) ?: return null
    val minor = m.groupValues[1].toLong() * 100 + m.groupValues[2].padEnd(2, '0').toLong()
    return minor.takeIf { it > 0 }
  }

  /** `null` = درست؛ وگرنه نخستین ایراد به ترتیبِ کادرها (مبلغ، سپس دلیل) */
  fun check(o: OrderDto, to: String, amount: String, reason: String): Problem? {
    val minor = amountMinor(amount) ?: return Problem.AMOUNT
    val remaining = remainingMinor(o)
    if (to == "refunded_full" && minor != remaining) return Problem.NOT_REMAINING
    if (to == "refunded_partial" && minor >= remaining) return Problem.NOT_BELOW_REMAINING
    if (reason.isBlank()) return Problem.REASON
    return null
  }

  /** «بخشی» سفارش را با همین ردیف می‌بندد؛ «کامل» با رسیدن به مانده خودش بسته می‌شود */
  fun final(to: String): Boolean = to == "refunded_partial"
}
