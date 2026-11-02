package ir.vil3ntec.tohid.core.pay

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Rounding

/*
 *  PAY-09 — گردکردنِ نقدی در صندوق (بخشِ ۳.۴: «ردیفِ جدا با نوعِ `rounding`، نه تغییرِ قیمت»).
 *
 *  حساب همان `Rounding.cashRounding` و `Pay.settle` (`cashRounding`) است؛ این‌جا فقط قاعده‌های صندوق، همه به پول:
 *  • پله فقط خاموش، ۵ یا ۱۰ افغانی ([STEPS])؛ هر مقدارِ دیگر ⇒ خاموش (پیش‌فرض، D-11).
 *  • فقط وقتی نقد فاکتور را می‌بندد: نقدِ کامل ⇒ [cash]؛ ترکیبی ⇒ `Pay.settle` فقط سهمِ نقد را گرد می‌کند
 *    (جمع − غیرنقد) و فقط اگر نقدِ دریافتی به مبلغِ گردشده برسد. نسیه/«به حساب» و غیرنقدِ تنها ⇒ هیچ.
 *  • ردیفِ گرد مالِ پرداخت است، نه کالا: قیمت و تخفیف دست نمی‌خورند؛ مبلغِ نهاییِ فاکتور = کالا − تخفیف + گرد.
 *  • برگشت ([refund]): جمعِ پس‌داده‌های یک فاکتور هرگز از پرداختیِ مشتری بیشتر نمی‌شود، و برگشتی که فاکتور را
 *    کامل برمی‌گرداند دقیقاً ماندهٔ پرداختی را پس می‌دهد — گردِ مثبت با آخرین قلم برمی‌گردد و گردِ منفی هرگز
 *    پولِ نگرفته را بیرون نمی‌برد.
 *
 *  کاتلینِ خالص (commonMain): نه اندروید، نه ساعت، نه ذخیره. هم‌زادِ JS ندارد (مثلِ `ShiftReview`)؛
 *  `pay-core.js` و `Pay.settle` دست نخوردند.
 */
object TillRounding {

  /** نوعِ ردیف — همان `Rounding.ROUNDING_LINE_TYPE` */
  const val TYPE: String = Rounding.ROUNDING_LINE_TYPE

  /** خاموش، ۵ و ۱۰ افغانی — به پول */
  val STEPS: List<Long> = listOf(0L, 500L, 1_000L)

  /** پلهٔ پذیرفته؛ نبود یا مقدارِ دیگر ⇒ ۰ (خاموش) */
  fun step(raw: Long?): Long = raw?.takeIf { it in STEPS } ?: 0L

  /**
   *  پلهٔ همین فاکتور: فاکتورِ کوچک‌تر از یک پله گرد نمی‌شود (۲ افغانی با پلهٔ ۵ به «هیچ» نمی‌رسد).
   *  همین را صندوق به `SettleInput.cashRounding` می‌دهد تا نقدِ کامل و ترکیبی یک قاعده داشته باشند.
   */
  fun stepFor(totalMinor: Long, stepMinor: Long): Long {
    val s = step(stepMinor)
    return if (s > 0L && totalMinor >= s) s else 0L
  }

  /**
   *  ردیفِ گردِ فاکتوری که همه‌اش نقد است (به پول، علامت‌دار): نیمه به سمتِ +∞ مثلِ `Pay.settle`.
   *  پلهٔ خاموش، فاکتورِ کوچک‌تر از پله یا مبلغِ مضربِ پله ⇒ ۰.
   */
  fun cash(totalMinor: Long, stepMinor: Long): Long {
    val s = stepFor(totalMinor, stepMinor)
    if (s == 0L) return 0L
    return Rounding.cashRounding(Money(totalMinor, Currency.AFN), s)?.amount?.minor ?: 0L
  }

  /**
   *  مبلغِ ردیف‌های یک برگشت از فاکتورِ گردشده (به پول، به ترتیبِ ردیف‌ها).
   *
   *  @param lines             پس‌دادنیِ هر ردیف از کالا (همان `ReturnShare`)، ≥ ۰
   *  @param paidMinor         پرداختیِ مشتری = مبلغِ نهاییِ فاکتور با گرد
   *  @param refundedBeforeMinor جمعِ پس‌داده‌های پیشینِ همین فاکتور
   *  @param completes         پس از این برگشت هیچ قلمی از فاکتور نمی‌ماند
   *  زیادی از ردیفِ آخر به اول کم می‌شود (همان سقفِ `ReturnFlow.quote`)؛ کسری در برگشتِ کامل به ردیفِ آخر.
   */
  fun refund(lines: List<Long>, paidMinor: Long, refundedBeforeMinor: Long, completes: Boolean): List<Long> {
    require(lines.all { it >= 0L }) { "مبلغِ برگشت منفی است: $lines" }
    if (lines.isEmpty()) return lines
    val room = maxOf(0L, paidMinor - maxOf(0L, refundedBeforeMinor))
    val sum = lines.sum()
    val target = if (completes) room else minOf(sum, room)
    val out = lines.toLongArray()
    var diff = target - sum
    if (diff > 0L) out[out.lastIndex] += diff
    var i = out.lastIndex
    while (diff < 0L && i >= 0) {
      val cut = minOf(-diff, out[i])
      out[i] -= cut
      diff += cut
      i--
    }
    return out.toList()
  }
}
