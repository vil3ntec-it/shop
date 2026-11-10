package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.money.QtyExact
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import kotlin.math.roundToLong

/*
 *  PRD-08 — کالای وزنی (کیلو با ۳ رقمِ اعشار) در موتورِ صندوقِ گوشی و کامپیوتر ⇐ `core/money/QtyExact`
 *  (هم‌زادِ `qtyExact` ِ `license/money.js`؛ بردارِ `core/test-vectors/qty-exact-v1.json`).
 *
 *  ⛔ پرچمِ سرورِ `shop.qty_exact` (`StockFlag`، کلیدِ `qtyExact` ِ `GET /shop/stock-ledger/config`؛ پیش‌فرض خاموش):
 *  خاموش ⇒ هر تابعِ این‌جا بیت‌به‌بیت همان حسابِ پیشینِ `SalesEngine` است. روشن ⇒
 *   • مبلغِ ردیفی که مقدارش اعشار دارد = قاعدهٔ ۳.۴ (`Rounding.lineTotalMinor`، همان سفارشِ آنلاینِ سرور)؛ مقدارِ درست همان امروز؛
 *   • جمعِ سبد، جای خالی تا موجودی و مقدارِ ثبت‌شدهٔ قلم و حرکتِ انبار به هزارمِ دقیق (۰٫۳، نه ۰٫۳۰۰۰۰۰۰۰۰۰۰۰۰۰۰۰۴)؛
 *   • پیامِ کمبودِ کالای وزنی با اعشار («۱٫۲۵ کیلو»، نه «۱»).
 *  همهٔ ۳۸ صدای `SalesEngine.lineTotal` (سبد، صفحه‌کلید، برگهٔ ردیف، امانی، تخفیف، رسید، …) از همین یک‌جا می‌گذرند.
 */
object QtyExactWiring {

  /** پرچمِ `shop.qty_exact` ِ دکانِ فعال (`StockFlag.load`) */
  @Volatile var serverFlag: Boolean = false

  /** `SalesEngine.lineTotal` — مبلغِ ردیف به افغانیِ کامل */
  fun lineTotal(price: Double, quantity: Double, on: Boolean = serverFlag): Double {
    if (on) QtyExact.lineMinorOrNull(price, quantity)?.let { return it / 100.0 }
    return (price * quantity).roundToLong().toDouble()
  }

  /**
   *  پیش‌نمایشِ مبلغ در برگهٔ ردیف و قلمِ آزادِ کامپیوتر (`CartLineDialog`؛ گرد در `money`): خاموش یا مقدارِ درست ⇒ همان
   *  `price × q` ِ پیشین؛ روشن و اعشاری ⇒ همان مبلغِ ثبت. NaN/بی‌نهایت مثلِ پیش بی خطا (`money` ⇒ ۰).
   */
  fun preview(price: Double, quantity: Double, on: Boolean = serverFlag): Double =
    (if (on) QtyExact.lineMinorOrNull(price, quantity)?.let { it / 100.0 } else null) ?: (price * quantity)

  /** مقدارِ ردیفِ تکراری در سبد (`SalesEngine.addToCart`) */
  fun plus(a: Double, b: Double, on: Boolean = serverFlag): Double = if (on) QtyExact.plus(a, b) else a + b

  /** جای خالیِ سبد تا موجودی (`SalesEngine.addToCartCapped`) */
  fun room(available: Double, inCart: Double, on: Boolean = serverFlag): Double =
    if (on) QtyExact.room(available, inCart) else (available - inCart).coerceAtLeast(0.0)

  /** مقدارِ قلمِ فاکتور و حرکتِ انبار هنگامِ ثبت (`SalesEngine.record`) */
  fun stored(quantity: Double, on: Boolean = serverFlag): Double = if (on) QtyExact.clean(quantity) else quantity

  /** عددِ پیامِ کمبود (`SalesEngine.shortageMessage`): خاموش یا مقدارِ درست ⇒ همان `money` */
  fun stockText(value: Double, on: Boolean = serverFlag): String =
    if (on && QtyExact.milli(value)?.let(QtyExact::fractional) == true) qty(value) else money(value)

  /** برگهٔ ردیف: بیش از سه رقمِ اعشار (`LineModel.qtyError`)؛ خاموش ⇒ همان پیشین (بی رد، گرد در `setCartQty`) */
  fun tooPrecise(text: String, on: Boolean = serverFlag): Boolean = on && QtyExact.tooPrecise(text)
}
