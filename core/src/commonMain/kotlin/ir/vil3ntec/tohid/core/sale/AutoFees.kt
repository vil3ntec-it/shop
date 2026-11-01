package ir.vil3ntec.tohid.core.sale

/*
 *  هزینهٔ خودکارِ ردیف یا فاکتور — POS-36، §۶.۱ («بسته‌بندی»، «کیسه»).
 *
 *  هر قاعده یک نام و یک مبلغ دارد:
 *  • **فاکتور**: یک بار برای هر فاکتوری که دستِ‌کم یک قلم دارد («کیسه = 5»).
 *  • **ردیف**: برای هر واحدِ کالاهای نام‌برده («برنج، شکر: بسته‌بندی = 2» ⇒ ۳ کیلو برنج = ۳ × ۲).
 *  هزینه در سبد یک قلمِ آزاد است با کلیدِ ثابتِ `fee:…` ([PREFIX])، پس جمع، تخفیف، رسید و گزارش همان راهِ
 *  قلمِ آزادند و موجودی دست نمی‌خورد. با هر تغییرِ سبد از نو ساخته می‌شود (قلمِ هزینهٔ پیشین بیرون، تازه
 *  ته سبد)؛ سبدِ بی قلم هزینه ندارد. بی قاعده (پیش‌فرض) ⇒ همان سبد، بی هیچ تغییر.
 *
 *  کادرِ تنظیم: هر خط «نام = مبلغ» یا «کالا، کالا: نام = مبلغ» (کالا با نام یا بارکد).
 *  منطقِ خالص (commonMain)؛ آزمون: `AutoFeesTest`.
 */
object AutoFees {

  const val PREFIX = "fee:"
  const val MAX_RULES = 20
  const val MAX_LABEL = 40

  /** [productIds] تهی ⇒ هزینهٔ هر فاکتور؛ وگرنه هزینهٔ هر واحدِ همین کالاها */
  data class Rule(val label: String, val amountMinor: Long, val productIds: List<String> = emptyList())

  /** یک قلمِ سبد: کلید (شناسهٔ کالا یا قلمِ آزاد)، مقدار به هزارم، و کالا (`null` برای قلمِ آزاد) */
  data class Line(val key: String, val qtyMilli: Long, val productId: String?)

  /** قلمِ هزینه: کلید، نام، مقدار به هزارم و مبلغِ واحد */
  data class Fee(val key: String, val label: String, val qtyMilli: Long, val unitMinor: Long)

  enum class Problem { BAD_LINE, BAD_AMOUNT, UNKNOWN_ITEM, TOO_MANY }

  data class Parsed(val rules: List<Rule>, val problems: List<Pair<String, Problem>>)

  fun isFee(key: String): Boolean = key.startsWith(PREFIX)

  /** هزینه‌های این سبد به ترتیبِ قاعده‌ها؛ قلم‌های هزینهٔ پیشین به حساب نمی‌آیند */
  fun fees(lines: List<Line>, rules: List<Rule>): List<Fee> {
    val real = lines.filter { !isFee(it.key) && it.qtyMilli > 0 }
    if (real.isEmpty()) return emptyList()
    return rules.mapIndexedNotNull { i, r ->
      val key = "$PREFIX${i + 1}"
      if (r.productIds.isEmpty()) Fee(key, r.label, 1000, r.amountMinor)
      else real.filter { it.productId != null && it.productId in r.productIds }.sumOf { it.qtyMilli }
        .takeIf { it > 0 }?.let { Fee(key, r.label, it, r.amountMinor) }
    }.filter { it.unitMinor > 0 }
  }

  /**
   *  کادرِ تنظیم ⇒ قاعده‌ها. [resolve]: نام یا بارکدِ کالا ⇒ شناسه. مبلغ با `DecimalText` (رقمِ فارسی، «٬»).
   *  خطِ نادرست جدا گفته می‌شود و در قاعده‌ها نمی‌آید.
   */
  fun parse(text: String, resolve: (String) -> String?): Parsed {
    val rules = ArrayList<Rule>()
    val bad = ArrayList<Pair<String, Problem>>()
    for (raw in text.split('\n', '\r')) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      val eq = line.lastIndexOf('=')
      if (eq < 0) { bad += line to Problem.BAD_LINE; continue }
      val head = line.substring(0, eq)
      val colon = head.lastIndexOf(':')
      val label = (if (colon < 0) head else head.substring(colon + 1)).trim().take(MAX_LABEL)
      val items = if (colon < 0) emptyList() else head.substring(0, colon).split(',', '،').map { it.trim() }.filter { it.isNotEmpty() }
      val ids = items.map(resolve)
      val amount = ir.vil3ntec.tohid.core.product.DecimalText.scaled(line.substring(eq + 1).trim(), 2)
      when {
        label.isEmpty() || (colon >= 0 && items.isEmpty()) -> bad += line to Problem.BAD_LINE
        amount == null || amount <= 0 -> bad += line to Problem.BAD_AMOUNT
        ids.any { it == null } -> bad += line to Problem.UNKNOWN_ITEM
        rules.size == MAX_RULES -> bad += line to Problem.TOO_MANY
        else -> rules += Rule(label, amount, ids.filterNotNull().distinct())
      }
    }
    return Parsed(rules, bad)
  }

  /** قاعده‌ها ⇒ کادرِ تنظیم؛ [nameOf] نامِ کالا، [money] مبلغِ خوانا */
  fun format(rules: List<Rule>, nameOf: (String) -> String, money: (Long) -> String): String = rules.joinToString("\n") { r ->
    (if (r.productIds.isEmpty()) "" else r.productIds.joinToString("، ", transform = nameOf) + ": ") + "${r.label} = ${money(r.amountMinor)}"
  }
}
