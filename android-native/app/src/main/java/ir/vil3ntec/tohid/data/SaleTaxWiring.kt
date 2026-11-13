package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.sale.SaleTax
import kotlin.math.max

/*
 *  POS-37 — مالیاتِ فاکتور در صندوقِ گوشی و کامپیوتر ⇐ `core/sale/SaleTax` (هم‌زادِ `license/sale-tax.js`؛ بردارِ
 *  `core/test-vectors/sale-tax-v1.json`).
 *
 *  ⛔ دو کلید با هم: پرچمِ سرورِ `shop.sale_tax` (`StockFlag`، کلیدِ `saleTax` ِ `GET /shop/stock-ledger/config`؛ پیش‌فرض
 *  خاموش) **و** تنظیمِ «مالیاتِ فاکتور» ِ دکان (`SaleTaxPref`، فقط صاحب و مدیر؛ بی نرخِ پیش‌فرض، D-11). هر کدام خاموش ⇒
 *  `SalesEngine.Checkout.tax` = `null` و فاکتور بیت‌به‌بیت همان امروز. فقط صندوق و فروشِ «تعویض» (هر دو از `TillGuards`) آن را
 *  می‌فرستند؛ تحویلِ سفارش، امانی و سفارشِ ویژه هرگز (همان قاعدهٔ گردِ نقدیِ PAY-09).
 */
object SaleTaxWiring {

  /** پرچمِ `shop.sale_tax` ِ دکانِ فعال (`StockFlag.load`) */
  @Volatile var serverFlag: Boolean = false

  /** تنظیمِ مؤثر: پرچمِ سرور و تنظیمِ دکان؛ هر کدام خاموش یا نادرست ⇒ `null` */
  fun active(pref: SaleTax.Config?, on: Boolean = serverFlag): SaleTax.Config? = if (on) SaleTax.clean(pref) else null

  /** ردیف‌های سبد ⇒ ورودیِ هسته: مبلغِ هر ردیف همان `SalesEngine.lineTotal` (به پول)؛ قلمِ آزاد و هزینهٔ خودکار کالا ندارند */
  fun lines(d: ShopData, cart: List<SalesEngine.CartLine>): List<SaleTax.Line> = cart.map { line ->
    val amount = if (line.free) SalesEngine.lineTotal(line.unitPrice ?: 0.0, line.quantity)
    else d.products.find { it.id == line.productId }?.let { p ->
      SalesEngine.lineTotal(line.unitPrice ?: ProductPlansMap.price(p), line.quantity)
    } ?: 0.0
    SaleTax.Line(if (line.free) null else line.productId, PayFlow.minor(amount))
  }

  /**
   *  `SalesEngine.totals` پس از تخفیف: مالیات روی (جمع − تخفیف)، پیش از گردِ نقدی؛ نهایی = max(۰، جمع − تخفیف) + مالیات.
   *  بی تنظیم ⇒ بیت‌به‌بیت همان `Totals` ِ پیشین؛ بیرون از بازهٔ دقیق ⇒ `taxOutOfRange` و `SalesEngine.record` رد می‌کند.
   */
  fun totals(
    d: ShopData, cart: List<SalesEngine.CartLine>, config: SaleTax.Config?, subtotal: Double, value: Double, discount: Double,
  ): SalesEngine.Totals {
    val t = tax(d, cart, discount, config)
    return SalesEngine.Totals(
      subtotal = subtotal, discountValue = value, discount = discount, finalTotal = max(0.0, subtotal - discount) + (t ?: 0.0),
      tax = t ?: 0.0, taxOutOfRange = t == null,
    )
  }

  /**
   *  سهمِ پرداخت ِ هر قلم برای برگشت (`SalesEngine.paidRatio`): مالیاتِ فاکتور هم سهمی از هر قلم است ⇒ [base] + مالیات ÷ جمع —
   *  همان `return-line-cap.paidRatio` ِ سرور (همان ترتیبِ عمل‌ها). فاکتورِ بی مالیات همان [base].
   */
  fun withTax(sale: Sale, base: Double): Double {
    val tax = PayFlow.major(sale.taxMinor ?: 0L)
    return if (sale.total > 0 && tax > 0) base + tax / sale.total else base
  }

  /** مالیاتِ سبد به افغانی (۰ بی تنظیم)؛ جمعِ بیرون از بازهٔ دقیق ⇒ `null` (ثبت رد می‌شود، نه عددِ گردشده) */
  fun tax(d: ShopData, cart: List<SalesEngine.CartLine>, discount: Double, config: SaleTax.Config?): Double? {
    if (config == null) return 0.0
    val r = SaleTax.compute(lines(d, cart), PayFlow.minor(discount), config) ?: return null
    return PayFlow.major(r.taxMinor)
  }
}
