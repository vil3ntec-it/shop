package ir.vil3ntec.tohid.data

/**
 *  RET-05 — اعدادِ فاکتورِ «تعویض» همان فاکتورِ صندوقِ همین دستگاه (`TillGuards`، `claude/e-rows-37e`):
 *  - POS-37 مالیاتِ فاکتور روی خودِ فاکتورِ تعویض (`Checkout.tax`): برگشتِ فاکتورِ مالیات‌دار سهمِ مالیاتش را پس می‌دهد
 *    (`SalesEngine.paidRatio`)، پس فروشِ تازه هم همان مالیات را می‌گیرد — بی آن تعویضِ همان کالا مالیات را به مشتری می‌داد.
 *  - PAY-09 گردِ نقدی فقط روی نقدی که مشتری **پس از نقدِ برگشتِ همین سند** می‌دهد (همان «سهمِ نقدی که فاکتور را می‌بندد» ِ
 *    پرداختِ ترکیبی، `Pay.settle`): نقدِ برگشت ⇒ `Checkout.cashCovered` و `SalesEngine.record` فقط باقی را با `TillRounding.cash`
 *    گرد می‌کند (ردیفِ جدای `Sale.roundingMinor` روی فاکتورِ تعویض). تفاوتِ پس‌دادنی هرگز گرد نمی‌شود (برگشت گرد ندارد).
 *  - فروشندهٔ ردیف (POS-23) کمبود نیست: سبدِ تعویض فروشندهٔ ردیف ندارد ⇒ هر قلم به نامِ فروشندهٔ فاکتور (کاربرِ صندوق،
 *    `TillUser.me`)، همان فاکتورِ صندوقی که ردیفش به کسی سپرده نشده.
 *  پیش‌نمایشِ هر دو برگه ([preview]) و ثبت ([sale] در `ReturnFlow.exchange`) یک حساب‌اند.
 */
object ExchangeTill {

  /** فروشِ تعویض پس از برگشتِ همان سند: نقدِ پس‌داده [split] پیش از گردِ نقدی از این فاکتور کم می‌شود */
  fun sale(checkout: SalesEngine.Checkout, split: ReturnFlow.Split): SalesEngine.Checkout = covered(checkout, split.cash)

  /** پیش‌نمایشِ برگهٔ برگشت: [goods] جمعِ کالا، [total] مبلغِ فاکتورِ تعویض (با مالیات و گرد)، [due] مثبت ⇒ بگیر، منفی ⇒ بده */
  data class Preview(val goods: Double, val tax: Double, val rounding: Double, val total: Double, val due: Double)

  /** همان اعدادی که [ReturnFlow.exchange] ثبت می‌کند؛ [cashBack] نقدِ برگشتِ همین سند (`ReturnFlow.split`، بی آن ۰) */
  fun preview(d: ShopData, cart: List<SalesEngine.CartLine>, checkout: SalesEngine.Checkout, cashBack: Double): Preview {
    val c = covered(checkout, cashBack)
    val t = SalesEngine.totals(d, cart, c)
    val rounding = PayFlow.major(SalesEngine.cashRoundingMinor(t.finalTotal, c))
    val total = t.finalTotal + rounding
    return Preview(t.subtotal, t.tax, rounding, total, total - cashBack)
  }

  private fun covered(c: SalesEngine.Checkout, cash: Double) = c.copy(cashCovered = PayFlow.minor(cash).coerceAtLeast(0L))
}
