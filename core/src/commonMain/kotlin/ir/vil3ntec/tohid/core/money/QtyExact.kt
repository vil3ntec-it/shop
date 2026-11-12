package ir.vil3ntec.tohid.core.money

/*
 *  PRD-08 — کالای وزنی (کیلو با ۳ رقمِ اعشار): یک قاعده برای مقدارِ اعشاری در صندوق، روی همهٔ سکوها.
 *
 *  ⛔ هم‌زادِ `qtyExact` ِ `license/money.js`؛ بردارِ مشترک `core/test-vectors/qty-exact-v1.json` (سازنده
 *  `server/scripts/qty-exact-vectors.js`؛ آزمون‌ها `QtyExactVectorsTest` و `server/test/qty-exact.test.js`).
 *  یکی را عوض کنی، دیگری و بردار هم.
 *
 *  - مقدار به هزارم = `Qty.fromLegacy` = پاک‌سازیِ `setCartQty`: `mathRound(q × 1000)`.
 *  - مبلغِ ردیف با پرچمِ روشن = `Rounding.lineTotalMinor(qtyMilli, priceMinor, 100)` = `order-states.lineTotalMinor` ِ سرور
 *    (`floorDiv(milli × minor + 50_000, 100_000) × 100`، نیمه به +∞، بی Double): ۱٫۰۱۵ کیلو × ۱۰۰ = ۱۰۲، نه ۱۰۱.
 *  - نسخهٔ ۲ (تصمیمِ ۳-۱۲ ِ صاحب‌کار): مقدارِ **درست** (۲ عدد، ۵۰ دانه) هم همان قاعده — ۵۰ × ۰٫۲۹ = ۱۵، نه ۱۴ ِ امروز؛ فاکتور
 *    با سفارشِ اینترنتیِ همان کالا یکی. پیش‌تر (نسخهٔ ۱) فقط هزارمِ نامضربِ ۱۰۰۰. عددِ نامعتبر یا بیرون از بازهٔ دقیق ⇒ همان
 *    حسابِ پیشین `Math.round(price × q)`. روشن/خاموش با صدازننده است (پرچمِ `shop.qty_exact`)؛ این‌جا فقط حساب.
 *
 *  Kotlin خالص (بی java.*)، تا روی آیفون همان عدد را بدهد.
 */
object QtyExact {

  const val VERSION = 2

  /** رقم‌های اعشارِ مقدار — همان `Keypad.decimals(QTY)` */
  const val DECIMALS = 3

  /** پلهٔ گردِ مبلغِ ردیف در AFN (یک افغانی) — همان `Currency.AFN.stepMinor` */
  const val STEP_MINOR: Long = 100

  /** Double ⇒ هزارم (`Qty.fromLegacy`)؛ NaN، بی‌نهایت یا بیرون از بازهٔ دقیق ⇒ `null` */
  fun milli(q: Double): Long? {
    if (q.isNaN() || q.isInfinite()) return null
    val m = mathRound(q * Qty.SCALE)
    return if (m > MAX_EXACT || m < -MAX_EXACT) null else m
  }

  /** هزارمی که مضربِ ۱۰۰۰ نیست = مقدارِ اعشاری */
  fun fractional(milli: Long): Boolean = milli % Qty.SCALE != 0L

  /** مبلغِ ردیف به پول با قاعدهٔ دقیق (بی شرطِ اعشار)؛ بیرون از بازهٔ دقیق ⇒ `null` */
  fun exactMinor(priceMinor: Long, qtyMilli: Long): Long? =
    try { Rounding.lineTotalMinor(qtyMilli, priceMinor, STEP_MINOR) } catch (e: ArithmeticException) { null }

  /**
   *  مبلغِ ردیفِ [price] (افغانی) × [quantity] به پول با قاعدهٔ دقیق — هر مقدار، اعشاری یا درست (نسخهٔ ۲).
   *  عددِ نامعتبر یا بیرون از بازه ⇒ `null` (صدازننده همان حسابِ پیشین را می‌کند).
   */
  fun lineMinorOrNull(price: Double, quantity: Double): Long? {
    val q = milli(quantity) ?: return null
    if (price.isNaN() || price.isInfinite()) return null
    val p = mathRound(price * 100)
    if (p > MAX_EXACT || p < -MAX_EXACT) return null
    return exactMinor(p, q)
  }

  /** حسابِ پیشینِ موتور (`SalesEngine.lineTotal` = `lineTotal` ِ وب): `Math.round(price × q)` افغانی، به پول */
  fun legacyMinor(price: Double, quantity: Double): Long = mulExact(mathRound(price * quantity), STEP_MINOR)

  /** دروازه: [on] ⇒ قاعدهٔ دقیق (هر مقدار)؛ وگرنه (یا عددِ نامعتبر) حسابِ پیشین. به پول. */
  fun lineMinor(price: Double, quantity: Double, on: Boolean): Long =
    (if (on) lineMinorOrNull(price, quantity) else null) ?: legacyMinor(price, quantity)

  /** [q] گرد به هزارم (۰٫۳۰۰۰۰۰۰۰۰۰۰۰۰۰۰۰۴ ⇒ ۰٫۳)؛ عددِ نامعتبر ⇒ همان */
  fun clean(q: Double): Double = milli(q)?.let { it / 1000.0 } ?: q

  /** جمعِ دو مقدار به هزارمِ دقیق (۰٫۲ + ۰٫۱ = ۰٫۳)؛ عددِ نامعتبر یا بیرون از بازه ⇒ همان جمعِ Double */
  fun plus(a: Double, b: Double): Double {
    val x = milli(a) ?: return a + b
    val y = milli(b) ?: return a + b
    val s = x + y
    return if (s > MAX_EXACT || s < -MAX_EXACT) a + b else s / 1000.0
  }

  /** جای خالیِ سبد تا موجودی، به هزارمِ دقیق و هرگز منفی؛ عددِ نامعتبر ⇒ همان تفریقِ Double */
  fun room(available: Double, inCart: Double): Double {
    val a = milli(available) ?: return (available - inCart).coerceAtLeast(0.0)
    val c = milli(inCart) ?: return (available - inCart).coerceAtLeast(0.0)
    return if (a > c) (a - c) / 1000.0 else 0.0
  }

  /**
   *  متنِ مقدار بیش از [DECIMALS] رقمِ معنادار پس از ممیز دارد («1.2345»، «۱٫۲۳۴۵»)؛ صفرِ پایانی شمرده نمی‌شود
   *  («1.2500» درست است). ممیز: «.»، «٫» یا «٬» (همان `latinDigits`).
   */
  fun tooPrecise(text: String): Boolean {
    val at = text.indexOfFirst { it == '.' || it == '٫' || it == '٬' }
    if (at < 0) return false
    val digits = StringBuilder()
    for (i in at + 1 until text.length) latin(text[i])?.let { digits.append(it) }
    return digits.toString().trimEnd('0').length > DECIMALS
  }

  private fun latin(c: Char): Char? = when (c) {
    in '0'..'9' -> c
    in '۰'..'۹' -> '0' + (c - '۰')
    in '٠'..'٩' -> '0' + (c - '٠')
    else -> null
  }
}
