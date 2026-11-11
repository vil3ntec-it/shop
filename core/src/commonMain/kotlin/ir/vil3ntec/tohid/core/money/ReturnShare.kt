package ir.vil3ntec.tohid.core.money

import kotlin.math.floor

/**
 *  مبلغِ برگشتِ بخشی از یک قلمِ فاکتور — «گردِ تجمعی» (SEC-DATA-01، docs/security/data-fa.md).
 *
 *  ⛔ هم‌زادِ `returnShare` در `license/money.js`؛ هر دو با `license/test-vectors/return-share-v2.json`.
 *  یکی را عوض کنی، دیگری و بردار هم.
 *
 *  مبلغِ هر برگشت «پس‌دادنیِ تا پایانِ این برگشت» منهای «پس‌دادنیِ تا پیش از آن» است، هر دو از یک تابعِ
 *  یکنوا؛ پس جمعِ برگشت‌های یک قلم همیشه دقیقاً همان است که برگشتِ یک‌جای همان مقدار می‌داد (پیش از #586
 *  هر برگشت جدا گرد می‌شد: دو عدد به ۲٫۵ ⇒ ۳ + ۳ = ۶).
 *
 *  گرد — O-22 «ب» ِ صاحب‌کار (۱۴۰۵/۰۸/۲۰، `docs/OWNER-DECISIONS-fa.md` ۱-۲): پس‌دادنیِ «تا k» پیش از آخرین عدد
 *  سهمِ دقیق **رو به پایین تا افغانیِ کامل** است و برگشتی که قلم را تمام می‌کند **دقیقاً باقی‌مانده** را می‌گیرد،
 *  تا یک‌صدمِ افغانی: ۳ عدد × ۵ با ۱۰٪ تخفیف (۱۳٫۵۰) یکی‌یکی ⇒ ۴، ۵، ۴٫۵۰. در هیچ لحظه‌ای بیش از پولِ گرفته‌شده
 *  پس نمی‌رود و نقد جز برگشتِ آخر افغانیِ کامل است. (الف ِ پیشین: گرد به افغانیِ کامل، نیمه رو به بالا ⇒ ۵، ۴، ۵ =
 *  ۱۴، نیم‌افغانی بیش از پرداختی.) برگشت‌های ثبت‌شده با همان مبلغِ خودشان می‌مانند.
 *
 *  پولِ قلم `round(جمعِ قلم × سهمِ پرداخت × ۱۰۰)` یک‌صدم با همان `mathRound` ِ `Money.fromLegacy`؛ کفِ میانه بی
 *  Double (مقدار به هزارم، همان `QtyExact`). عددهای ورودی Double ِ دفترِ امروزند (مرز)، ولی ترتیبِ عمل در هر دو
 *  زبان یکی است، پس خروجی بیت‌به‌بیت یکی.
 */
object ReturnShare {

  /**
   *  @param lineTotal      `SaleItem.totalPrice` (افغانی)
   *  @param quantity       `SaleItem.quantity`
   *  @param returnedBefore `SaleItem.returnedQty` پیش از همین برگشت
   *  @param qty            مقدارِ همین برگشت
   *  @param paidRatio      سهمِ پرداخت‌شده پس از تخفیفِ فاکتور (۰..۱؛ بی تخفیف ۱)
   *  @return مبلغِ همین برگشت به افغانی (جز برگشتِ پایانِ قلم، افغانیِ کامل)؛ ورودیِ نامعتبر، مقدارِ فروشِ صفر
   *          یا پولِ قلمِ صفر و منفی ⇒ ۰
   */
  fun amount(lineTotal: Double, quantity: Double, returnedBefore: Double, qty: Double, paidRatio: Double): Double {
    if (!(quantity > 0.0) || !quantity.isFinite() || !lineTotal.isFinite() || !paidRatio.isFinite()) return 0.0
    if (!qty.isFinite() || !(qty > 0.0)) return 0.0
    val before = if (returnedBefore.isFinite() && returnedBefore > 0.0) returnedBefore else 0.0
    val net = netMinor(lineTotal, paidRatio) ?: return 0.0
    return (upTo(net, quantity, before + qty) - upTo(net, quantity, before)).toDouble() / 100.0
  }

  /**
   *  همان مبلغ، ولی با «پولی که برای همین قلم واقعاً پس داده شده» (#586، تاریخچهٔ قاطی):
   *  `max(0, پس‌دادنیِ تا پایانِ این برگشت − جمعِ amount ِ برگشت‌های پیشینِ همین قلم)`.
   *
   *  برگشت‌های پیش از به‌روزرسانی با فرمولِ جداجدا (یا گردِ «الف») ثبت شده‌اند و جمعشان می‌تواند از «پس‌دادنیِ تا
   *  پیش از آن» بیشتر باشد: قلمِ ۲ افغانی برای ۳ عدد، دو برگشتِ ۱ + ۱ = ۲؛ برگشتِ سوم با تفاضلِ تنها یکِ دیگر
   *  می‌داد (جمع ۳ > ۲). حالا جمعِ پس‌داده هرگز از پس‌دادنیِ تا پایان (و در آخر از پولِ قلم) بیشتر نمی‌شود.
   *  تاریخچهٔ یک‌دست دقیقاً همان `amount` ِ پنج‌ورودی است.
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
    val net = netMinor(lineTotal, paidRatio) ?: return 0.0
    val paid = if (refundedBefore.isFinite() && refundedBefore > 0.0) mathRound(refundedBefore * 100.0) else 0L
    val rowsQty = if (refundedQty.isFinite() && refundedQty > 0.0) refundedQty else 0.0
    val base = if (rowsQty + 1e-9 < before) maxOf(paid, upTo(net, quantity, before)) else paid
    return maxOf(0L, upTo(net, quantity, before + qty) - base).toDouble() / 100.0
  }

  /** پولِ پرداخت‌شدهٔ قلم به یک‌صدم؛ صفر، منفی یا بیرون از بازهٔ دقیق ⇒ null (چیزی پس نمی‌رود) */
  private fun netMinor(lineTotal: Double, paidRatio: Double): Long? {
    val n = mathRound(lineTotal * paidRatio * 100.0)
    return if (n in 1..MAX_EXACT) n else null
  }

  /**
   *  پس‌دادنیِ «تا k» به یک‌صدم از قلمی با پولِ `net` و مقدارِ `quantity` — یکنوا در k: پیش از پایان
   *  ⌊net × k ÷ مقدار⌋ رو به پایین تا افغانیِ کامل (هزارم، بی Double)، پایان دقیقاً `net`.
   */
  private fun upTo(net: Long, quantity: Double, k: Double): Long {
    if (k <= 0.0) return 0L
    if (k >= quantity) return net
    val q = mathRound(quantity * 1000.0)
    val m = mathRound(k * 1000.0)
    if (q in 1..MAX_EXACT && m in 0..q && (m == 0L || net <= MAX_EXACT / m)) return net * m / q / 100L * 100L
    //  بیرون از بازهٔ دقیق (قلمِ بسیار گران یا مقدارِ بی‌اندازه): همان کف با Double، هرگز بیش از net
    val whole = floor(net.toDouble() * k / quantity / 100.0)
    return if (whole * 100.0 >= net.toDouble()) net else (whole * 100.0).toLong()
  }
}
