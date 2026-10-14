package ir.vil3ntec.tohid.core.money

/*
 *  قاعدهٔ گردکردن — یک تابع، همه‌جا (بخشِ ۳.۴ِ مشخصات).
 *
 *  ⛔ هم‌زادِ `license/money.js`؛ بردارهای مرجع: `license/test-vectors/money-v1.json`.
 *
 *  تفاوت با موتورِ امروز (`SalesEngine.lineTotal` = `lineTotal`ِ وب): موتورِ امروز
 *  ضرب را با Double می‌کند و در نیمه‌های دقیق گاهی یک افغانی کم می‌آورد
 *  (۲۵ × ۰٫۵۸۰ = ۱۴٫۵ ⇒ امروز ۱۴، این‌جا ۱۵). جز همین نیمه‌ها هیچ فرقی نیست؛
 *  آزمونِ `MoneyTest` همه را می‌شمارد. تا تصمیمِ صاحبِ محصول هیچ موتوری به این
 *  وصل نمی‌شود.
 */
object Rounding {

  /** نوعِ ردیفِ «گردکردنِ نقدی» در فاکتور — ردیفی جدا، نه تغییرِ قیمت. */
  const val ROUNDING_LINE_TYPE: String = "rounding"

  /**
   *  `n / d` گردشده به نزدیک‌ترین عددِ صحیح، نیمه به سمتِ +∞ (معنای `Math.round`)،
   *  هم برای مثبت و هم برای منفی: `floorDiv(n + ⌊d/2⌋, d)`. برای `d`ِ فرد نیمهٔ
   *  دقیق ممکن نیست و همین فرمول درست است.
   */
  fun roundHalfUp(n: Long, d: Long): Long {
    require(d > 0) { "مقسوم‌علیه باید مثبت باشد: $d" }
    return addExact(n, d / 2).floorDiv(d)
  }

  /**
   *  مبلغِ ردیف به واحدِ کوچک، از عددهای خام:
   *  `floorDiv(qtyMilli × priceMinor + 500·step, 1000·step) × step`.
   *  برای AFN (پله ۱۰۰): `floorDiv(qtyMilli × priceMinor + 50_000, 100_000) × 100`.
   */
  fun lineTotalMinor(qtyMilli: Long, priceMinor: Long, stepMinor: Long): Long {
    require(stepMinor > 0) { "پلهٔ گردکردن باید مثبت باشد: $stepMinor" }
    val raw = mulExact(qtyMilli, priceMinor)              // به «هزارمِ واحدِ کوچک»
    return mulExact(roundHalfUp(raw, mulExact(Qty.SCALE, stepMinor)), stepMinor)
  }

  /** مبلغِ ردیف: مقدار × قیمتِ واحد، همان لحظه گرد به پلهٔ ارز. */
  fun lineTotal(qty: Qty, unitPrice: Money): Money =
    Money(lineTotalMinor(qty.milli, unitPrice.minor, unitPrice.currency.stepMinor), unitPrice.currency)

  /**
   *  پخشِ تخفیفِ فاکتور بینِ ردیف‌ها به روشِ «بزرگ‌ترین باقی‌مانده»، با پلهٔ ارز.
   *
   *  - سهمِ هر ردیف مضربِ پله است و جمعِ سهم‌ها **دقیقاً** برابرِ تخفیف.
   *  - پلهٔ اضافه به ردیفِ با باقی‌ماندهٔ بزرگ‌تر می‌رسد؛ برابری ⇒ ردیفِ جلوتر.
   *  - ردیفِ صفر هرگز سهم نمی‌گیرد و سهمِ هیچ ردیفی از مبلغش بیشتر نمی‌شود.
   *  - برگشت‌ها سهمشان را از همین خروجی می‌گیرند.
   *
   *  شرط‌ها: ارزِ همه یکی؛ ردیف‌ها ≥ ۰؛ ۰ ≤ تخفیف ≤ جمعِ ردیف‌ها؛ تخفیف مضربِ پله.
   */
  fun allocateDiscount(discount: Money, lines: List<Money>): List<Money> {
    val cur = discount.currency
    val step = cur.stepMinor
    lines.forEach { discount.same(it); require(it.minor >= 0) { "مبلغِ ردیف منفی است: ${it.minor}" } }
    require(discount.minor >= 0) { "تخفیف منفی است: ${discount.minor}" }
    require(discount.minor % step == 0L) { "تخفیف باید مضربِ پله ($step) باشد: ${discount.minor}" }
    val sum = lines.fold(0L) { acc, m -> addExact(acc, m.minor) }
    require(discount.minor <= sum) { "تخفیف (${discount.minor}) از جمعِ ردیف‌ها ($sum) بیشتر است" }
    if (discount.minor == 0L) return lines.map { Money.zero(cur) }

    val units = discount.minor / step
    val base = LongArray(lines.size)
    val rem = LongArray(lines.size)
    var given = 0L
    lines.forEachIndexed { i, m ->
      val num = mulExact(units, m.minor)
      base[i] = num / sum
      rem[i] = num % sum
      given += base[i]
    }
    val order = lines.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it })
    for (k in 0 until (units - given).toInt()) base[order[k]] += 1
    return base.map { Money(mulExact(it, step), cur) }
  }

  /** ردیفِ «گردکردنِ نقدی»: نوعش همیشه `rounding` است و مبلغش ممکن است منفی باشد. */
  data class RoundingLine(val amount: Money) {
    val type: String get() = ROUNDING_LINE_TYPE
  }

  /**
   *  گردکردنِ نقدیِ جمعِ فاکتور به پلهٔ دکان (مثلاً ۵ یا ۱۰ افغانی = ۵۰۰ یا ۱۰۰۰
   *  پول)، با همان قاعدهٔ نیمه به سمتِ +∞. خروجی ردیفِ جداست، نه تغییرِ قیمت؛
   *  وقتی جمع خودش مضربِ پله است `null`.
   */
  fun cashRounding(total: Money, stepMinor: Long): RoundingLine? {
    require(stepMinor > 0) { "پلهٔ گردکردنِ نقدی باید مثبت باشد: $stepMinor" }
    val rounded = mulExact(roundHalfUp(total.minor, stepMinor), stepMinor)
    val adjust = addExact(rounded, -total.minor)
    return if (adjust == 0L) null else RoundingLine(Money(adjust, total.currency))
  }
}
