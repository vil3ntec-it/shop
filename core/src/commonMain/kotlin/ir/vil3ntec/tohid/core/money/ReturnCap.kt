package ir.vil3ntec.tohid.core.money

/**
 *  ماندهٔ قابلِ برگشتِ یک قلمِ فاکتور — مقدار و پول (سفرِ JB5، `docs/JOURNEYS-fa.md`).
 *
 *  ⛔ هیچ‌کس — صاحبِ دکان هم — بیش از آنچه روی همان قلم فروخته شد برنمی‌گرداند. این درستی است، نه اجازه.
 *  دو دستگاهِ بی‌اینترنت هر کدام با شمارندهٔ خودش «جا» می‌دید و همان قلم دو بار برمی‌گشت؛ سرور حالا رد می‌کند
 *  (`server/src/lib/return-line-cap.js`، هم‌زادِ همین) و صفحهٔ برگشت با همین حساب از دادهٔ همگام‌شده نشان می‌دهد
 *  چقدر برگشته و بیشتر نمی‌پذیرد.
 *
 *  ⛔ هم‌زادِ `server/src/lib/return-line-cap.js` (`state`/`check`/`capMinor`)؛ هر دو با
 *  `license/test-vectors/return-cap-v1.json`. یکی را عوض کنی، دیگری و بردار هم.
 *
 *  واحدها: مقدار به هزارم (`milli`)، پول به یک‌صدمِ افغانی (`minor`) — بی Double در حساب.
 *    برگشته   بیشینهٔ (شمارندهٔ قلم `SaleItem.returnedQty`، جمعِ مقدارِ ردیف‌های برگشتِ همان قلم)
 *    مانده    فروخته − برگشته، و سقفِ پول − پس‌داده؛ هیچ‌کدام زیرِ صفر
 *    سقفِ پول round(جمعِ قلم × سهمِ پرداخت × ۱۰۰) یک‌صدم — دقیق تا پول، همان پایانِ `ReturnShare` (برگشتِ همهٔ
 *             قلم، O-22 «ب»؛ پیش‌تر گرد به افغانیِ کامل، تا نیم‌افغانی بیش از پرداختی)
 */
object ReturnCap {

  /** ردیفِ برگشت (پیشین یا همین): مقدار به هزارم، پول به یک‌صدم */
  data class Row(val qtyMilli: Long, val amountMinor: Long)

  data class State(
    val soldMilli: Long,
    val capMinor: Long,
    val returnedMilli: Long,
    val returnedMinor: Long,
    val leftMilli: Long,
    val leftMinor: Long,
  )

  enum class Verdict { OK, QTY, AMOUNT }

  /** مقدارِ دفتر (Double) ⇒ هزارم، با همان گردِ `ReturnShare` */
  fun milli(qty: Double): Long = if (qty.isFinite()) mathRound(qty * 1000.0) else 0L

  /** پولِ دفتر (افغانی، Double) ⇒ یک‌صدم */
  fun minor(afn: Double): Long = if (afn.isFinite()) mathRound(afn * 100.0) else 0L

  /** سقفِ پولِ یک قلم به یک‌صدم: round(جمعِ قلم × سهمِ پرداخت × ۱۰۰)، دقیق تا پول (O-22 ب)؛ ورودیِ نامعتبر یا منفی ⇒ ۰ */
  fun capMinor(lineTotal: Double, paidRatio: Double): Long {
    if (!lineTotal.isFinite() || !paidRatio.isFinite()) return 0L
    return maxOf(0L, mathRound(lineTotal * paidRatio * 100.0))
  }

  fun state(soldMilli: Long, capMinor: Long, prior: List<Row>, counterMilli: Long = 0L): State {
    val rowsQty = prior.sumOf { it.qtyMilli }
    val paid = prior.sumOf { it.amountMinor }
    val returned = maxOf(counterMilli, rowsQty)
    return State(
      soldMilli, capMinor, returned, paid,
      leftMilli = maxOf(0L, soldMilli - returned),
      leftMinor = maxOf(0L, capMinor - paid),
    )
  }

  /**
   *  همین برگشت ([req]) پس از [prior] جا دارد؟
   *  @param tolMinorPerRow گردِ قدیم برای هر ردیف (پیشین‌ها و همین): برنامهٔ پیش از #586 هر برگشت را جدا گرد می‌کرد و
   *                        برنامهٔ پیش از O-22 ب نیمه را رو به بالا؛ سرور ۵۰ می‌دهد، صفحهٔ برگشت ۰ (خودش `ReturnShare` دارد)
   *  @param extraMinor     گردِ نقدیِ مثبتِ فاکتور (`Sale.roundingMinor`، PAY-09): برگشتِ کامل ماندهٔ گرد را هم می‌برد
   */
  fun check(
    soldMilli: Long, capMinor: Long, prior: List<Row>, req: Row,
    tolMinorPerRow: Long = 0L, counterMilli: Long = 0L, extraMinor: Long = 0L,
  ): Verdict {
    val s = state(soldMilli, capMinor, prior, counterMilli)
    if (req.qtyMilli > s.leftMilli) return Verdict.QTY
    val room = capMinor + maxOf(0L, extraMinor) + maxOf(0L, tolMinorPerRow) * (prior.size + 1) - s.returnedMinor
    if (req.amountMinor > room) return Verdict.AMOUNT
    return Verdict.OK
  }
}
