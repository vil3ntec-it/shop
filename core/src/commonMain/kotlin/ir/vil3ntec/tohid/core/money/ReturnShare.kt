package ir.vil3ntec.tohid.core.money

/**
 *  مبلغِ برگشتِ بخشی از یک قلمِ فاکتور — «گردِ تجمعی» (SEC-DATA-01، docs/security/data-fa.md).
 *
 *  ⛔ هم‌زادِ `returnShare` در `license/money.js`؛ هر دو با `license/test-vectors/return-share-v1.json`.
 *  یکی را عوض کنی، دیگری و بردار هم.
 *
 *  تا این‌جا هر برگشت جدا گرد می‌شد: `round(جمعِ قلم ÷ مقدار × q × سهمِ پرداخت)`. برگشتِ تکه‌تکه
 *  بیش از پولِ گرفته‌شده پس می‌داد: دو عدد به ۲٫۵ (جمعِ ۵) ⇒ ۳ + ۳ = ۶؛ سه عدد به جمعِ ۲ ⇒ ۱ + ۱ + ۱ = ۳.
 *  حالا مبلغِ هر برگشت «پس‌دادنیِ تا پایانِ این برگشت» منهای «پس‌دادنیِ تا پیش از آن» است، هر دو از
 *  یک تابعِ یکنوا؛ پس جمعِ برگشت‌های یک قلم همیشه دقیقاً همان است که برگشتِ یک‌جای همان مقدار می‌داد،
 *  و برگشتِ همهٔ قلم = `round(جمعِ قلم × سهمِ پرداخت)` — نه بیشتر.
 *
 *  گرد به افغانیِ کامل با همان `mathRound` ِ `Money.fromLegacy` (نیمه به سمتِ +∞، روی JVM و Native یکی).
 *  عددها Double ِ دفترِ امروزند (مرز)، ولی ترتیبِ عمل در هر دو زبان یکی است، پس خروجی بیت‌به‌بیت یکی.
 */
object ReturnShare {

  /**
   *  @param lineTotal      `SaleItem.totalPrice` (افغانی)
   *  @param quantity       `SaleItem.quantity`
   *  @param returnedBefore `SaleItem.returnedQty` پیش از همین برگشت
   *  @param qty            مقدارِ همین برگشت
   *  @param paidRatio      سهمِ پرداخت‌شده پس از تخفیفِ فاکتور (۰..۱؛ بی تخفیف ۱)
   *  @return مبلغِ همین برگشت به افغانیِ کامل؛ ورودیِ نامعتبر یا مقدارِ فروشِ صفر ⇒ ۰
   */
  fun amount(lineTotal: Double, quantity: Double, returnedBefore: Double, qty: Double, paidRatio: Double): Double {
    if (!(quantity > 0.0) || !quantity.isFinite() || !lineTotal.isFinite() || !paidRatio.isFinite()) return 0.0
    if (!qty.isFinite() || !(qty > 0.0)) return 0.0
    val before = if (returnedBefore.isFinite() && returnedBefore > 0.0) returnedBefore else 0.0
    val net = lineTotal * paidRatio
    return (upTo(net, quantity, before + qty) - upTo(net, quantity, before)).toDouble()
  }

  /**
   *  همان مبلغ، ولی با «پولی که برای همین قلم واقعاً پس داده شده» (#586، تاریخچهٔ قاطی):
   *  `max(0, پس‌دادنیِ تا پایانِ این برگشت − جمعِ amount ِ برگشت‌های پیشینِ همین قلم)`.
   *
   *  برگشت‌های پیش از به‌روزرسانی با فرمولِ جداجدا ثبت شده‌اند و جمعشان می‌تواند از «پس‌دادنیِ تا پیش از
   *  آن» بیشتر باشد: قلمِ ۲ افغانی برای ۳ عدد، دو برگشتِ ۱ + ۱ = ۲؛ برگشتِ سوم با تفاضلِ تنها یکِ دیگر
   *  می‌داد (جمع ۳ > ۲). حالا جمعِ پس‌داده هرگز از پس‌دادنیِ تا پایان (و در آخر از `round(جمعِ قلم × سهم)`)
   *  بیشتر نمی‌شود. تاریخچهٔ یک‌دست (همه با گردِ تجمعی) دقیقاً همان `amount` ِ پنج‌ورودی است.
   *
   *  @param refundedBefore جمعِ `SaleReturn.amount` ِ برگشت‌های پیشینِ همین `saleItemId`
   *  @param refundedQty    جمعِ `SaleReturn.quantity` ِ همان ردیف‌ها. اگر از `returnedBefore` کمتر باشد
   *                        (ردیفِ برگشتِ کهنه بی `saleItemId` یا هنوز همگام‌نشده)، بخشِ بی‌ردیف را
   *                        «پس‌دادنیِ تا پیش از این» فرض می‌کند — هرگز صفر، تا پولِ دوباره بیرون نرود.
   */
  fun amount(
    lineTotal: Double, quantity: Double, returnedBefore: Double, qty: Double, paidRatio: Double,
    refundedBefore: Double, refundedQty: Double,
  ): Double {
    if (!(quantity > 0.0) || !quantity.isFinite() || !lineTotal.isFinite() || !paidRatio.isFinite()) return 0.0
    if (!qty.isFinite() || !(qty > 0.0)) return 0.0
    val before = if (returnedBefore.isFinite() && returnedBefore > 0.0) returnedBefore else 0.0
    val net = lineTotal * paidRatio
    val paid = if (refundedBefore.isFinite() && refundedBefore > 0.0) refundedBefore else 0.0
    val rowsQty = if (refundedQty.isFinite() && refundedQty > 0.0) refundedQty else 0.0
    val base = if (rowsQty + 1e-9 < before) maxOf(paid, upTo(net, quantity, before).toDouble()) else paid
    return maxOf(0.0, upTo(net, quantity, before + qty).toDouble() - base)
  }

  /** پس‌دادنیِ «تا k» از قلمی با مبلغِ پرداخت‌شدهٔ `net` و مقدارِ `quantity` — یکنوا در k */
  private fun upTo(net: Double, quantity: Double, k: Double): Long = when {
    k <= 0.0 -> 0L
    k >= quantity -> mathRound(net)
    else -> mathRound(net * k / quantity)
  }
}
