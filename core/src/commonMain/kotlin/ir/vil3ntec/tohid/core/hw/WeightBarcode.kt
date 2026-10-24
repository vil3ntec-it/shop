package ir.vil3ntec.tohid.core.hw

/**
 *  بارکدِ ترازو — برچسبی که ترازوی دکان برای کالای وزنی چاپ می‌کند
 *  (POS-29، سناریوی S04، HW-09).
 *
 *  شکلِ رایج: EAN-13 که با ۲۰ تا ۲۹ شروع می‌شود:
 *
 *      21 00150 01250 7
 *      ── ───── ───── ─
 *      │    │     │   └ رقمِ کنترلِ EAN-13
 *      │    │     └ وزن (۱٫۲۵۰ کیلو) یا قیمت
 *      │    └ کدِ کالا روی ترازو (PLU)
 *      └ پیشوند
 *
 *  قاعده‌ها دستِ دکان است (کدام پیشوند، چند رقم، وزن یا قیمت)، چون هر
 *  ترازو یک‌جور تنظیم می‌شود. **بی‌قاعده هیچ بارکدی وزنی خوانده نمی‌شود** —
 *  پیش‌فرض خاموش است و بارکدِ معمولیِ ۲۰… دست نمی‌خورد.
 */
object WeightBarcode {

  enum class Kind(val key: String) {
    WEIGHT("weight"),
    PRICE("price");

    companion object {
      fun of(key: String?): Kind = entries.firstOrNull { it.key == key } ?: WEIGHT
    }
  }

  data class Rule(
    /** دو رقم، ۲۰ تا ۲۹ */
    val prefix: String,
    val itemDigits: Int = 5,
    val valueDigits: Int = 5,
    val kind: Kind = Kind.WEIGHT,
    /** وزن: ۳ ⇒ گرم به کیلو. قیمت: ۰ ⇒ افغانیِ درست */
    val decimals: Int = 3,
  ) {
    /** قاعده‌ای که با EAN-13 جور نیست پذیرفته نمی‌شود */
    val valid: Boolean
      get() = prefix.length in 1..3 && prefix.all { it in '0'..'9' } && prefix.startsWith("2") &&
        itemDigits >= 1 && valueDigits >= 1 && prefix.length + itemDigits + valueDigits == 12 &&
        decimals in 0..4
  }

  /** پیش‌نهادِ آماده: ۲۰ تا ۲۹، کدِ ۵ رقمی، وزنِ ۵ رقمی به گرم */
  fun standardRules(): List<Rule> = (20..29).map { Rule(prefix = it.toString()) }

  sealed interface Result {
    /** `value` وزن به کیلو یا قیمت به افغانی */
    data class Hit(val rule: Rule, val itemCode: String, val value: Double, val raw: Long) : Result
    /** شکلِ برچسبِ ترازو را دارد ولی رقمِ کنترلش غلط است — رد می‌شود، نه «کالای ناشناس» */
    data class BadCheck(val code: String) : Result
    /** برچسبِ ترازو نیست — راهِ معمولیِ بارکد */
    data object NotScale : Result
  }

  fun parse(code: String, rules: List<Rule>): Result {
    val c = code.trim()
    if (c.length != 13 || c.any { it !in '0'..'9' }) return Result.NotScale
    val rule = rules.filter { it.valid }.firstOrNull { c.startsWith(it.prefix) } ?: return Result.NotScale
    if (!Ean13.isValid(c)) return Result.BadCheck(c)
    val p = rule.prefix.length
    val item = c.substring(p, p + rule.itemDigits)
    val rawText = c.substring(p + rule.itemDigits, p + rule.itemDigits + rule.valueDigits)
    val raw = rawText.toLong()
    var div = 1.0
    repeat(rule.decimals) { div *= 10.0 }
    return Result.Hit(rule, item, raw / div, raw)
  }

  /** ساختنِ برچسبِ ترازو — برای آزمون و برگهٔ نمونه؛ رقمِ کنترل خودش حساب می‌شود */
  fun build(rule: Rule, itemCode: String, raw: Long): String {
    require(rule.valid)
    val body = rule.prefix + itemCode.padStart(rule.itemDigits, '0') + raw.toString().padStart(rule.valueDigits, '0')
    require(body.length == 12) { "کد یا مقدار بلند است" }
    return body + Ean13.checkDigit(body)
  }
}
