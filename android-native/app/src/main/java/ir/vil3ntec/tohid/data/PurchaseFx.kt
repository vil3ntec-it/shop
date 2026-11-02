package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore

/**
 *  PUR-11 — خرید از تأمین‌کننده به ارزِ دیگر با نرخِ ثبت‌شده (تصمیمِ D-15)؛ فقط بخش‌های بی‌طرف:
 *
 *   • سند ارز و نرخِ خودش را نگه می‌دارد (`Purchase.fx…`)؛ فیِ اصلیِ ارزی و جمعِ ارزیِ ردیف هم می‌مانند.
 *   • فیِ ارزی همان لحظهٔ ثبت با همان نرخ به افغانی می‌رود و **همان** `purchasePrice` ِ امروز می‌شود — بهای میانگین،
 *     انبار، تاریخچهٔ قیمت و بدهیِ تأمین‌کننده (به افغانی) بی هیچ تغییری از آن حساب می‌شوند.
 *   • ⛔ این‌جا **نیست** (تصمیمِ صاحب/حسابدار، D-15/D-23): ارزیابیِ دوبارهٔ بدهیِ ارزی با نرخِ روزِ پرداخت، سود/زیانِ
 *     تسعیر، پرداخت به ارز، و فهرستِ نرخِ روز. بدهی همان افغانیِ روزِ ثبت است.
 *
 *  پول: هر دو ارز به «صدمِ واحد» (عددِ صحیح) — افغانی همان `StockLedger.minor`، ارزِ دیگر صدمِ واحدِ خودش (ارزِ
 *  بی‌اعشار، مثلاً ریال، فقط مضربِ ۱۰۰ می‌شود). نرخ = افغانی برای **یک** واحدِ ارز × ۱۰^۶ (عددِ صحیح، شش رقمِ اعشارِ دقیق).
 *  تبدیل: `afnMinor = round(foreignMinor × rateMicros / 10^6)`، گردِ نزدیک‌ترین با نیمه به سمتِ +∞ (برای مبلغِ نامنفی
 *  همان «نیمه به بالا») — همان `PurchaseOrderCore.mulDivRound` ِ سفارشِ خرید، دقیق و بی Double.
 */
object PurchaseFx {

  /** مقیاسِ نرخ: شش رقمِ اعشار */
  const val RATE_SCALE = 1_000_000L

  /** سقفِ نرخ: یک میلیون افغانی برای یک واحد (کمینه ۰٫۰۰۰۰۰۱) */
  const val MAX_RATE_MICROS = 1_000_000L * RATE_SCALE

  /** سقفِ فیِ ارزی: یک میلیارد واحد (به صدمِ واحد) */
  const val MAX_PRICE_MINOR = 100_000_000_000L

  const val AFN = "AFN"

  const val BAD_CURRENCY = "ارزِ فاکتور درست نیست — سه حرفِ لاتین، مثلاً USD"
  const val BAD_RATE = "نرخِ ارز درست نیست — عددِ مثبت تا شش رقمِ اعشار، حداکثر ۱٬۰۰۰٬۰۰۰ افغانی"
  const val BAD_PRICE = "فیِ ارزی درست نیست — تا دو رقمِ اعشار"
  const val TOO_LARGE = "مبلغ برای تبدیل بیش از اندازه است"

  /** فقط پیشنهادِ فهرست؛ هر کدِ سه‌حرفیِ دیگر هم پذیرفته است */
  val SUGGESTED: List<Pair<String, String>> = listOf(
    "USD" to "دلار آمریکا",
    "PKR" to "روپیهٔ پاکستان",
    "IRR" to "ریال ایران",
    "CNY" to "یوان چین",
    "AED" to "درهم امارات",
    "EUR" to "یورو",
    "TRY" to "لیر ترکیه",
    "INR" to "روپیهٔ هند",
    "UZS" to "سوم ازبکستان",
  )

  /** نرخِ یک سند */
  data class Rate(val currency: String, val rateMicros: Long)

  private val CODE = Regex("[A-Z]{3}")

  /** کدِ ارز: سه حرفِ لاتینِ بزرگ و نه خودِ افغانی؛ وگرنه `null` */
  fun code(raw: String): String? = raw.trim().uppercase().takeIf { CODE.matches(it) && it != AFN }

  /** درستیِ نرخ؛ `null` = درست */
  fun check(rate: Rate): String? = when {
    code(rate.currency) != rate.currency -> BAD_CURRENCY
    rate.rateMicros !in 1..MAX_RATE_MICROS -> BAD_RATE
    else -> null
  }

  /** «70.25» ⇒ 70_250_000. بیش از شش رقمِ اعشارِ غیرِصفر، صفر، منفی یا بیرون از سقف ⇒ `null` (هیچ گردکردنِ پنهانی) */
  fun parseRate(raw: String): Long? = parseScaled(raw, 6)?.takeIf { it in 1..MAX_RATE_MICROS }

  /** فیِ ارزی «12.5» ⇒ 1250 (صدمِ واحد). بیش از دو رقمِ اعشارِ غیرِصفر یا بیرون از سقف ⇒ `null` */
  fun parseMinor(raw: String): Long? = parseScaled(raw, 2)?.takeIf { it in 0..MAX_PRICE_MINOR }

  private val DECIMAL = Regex("""(\d+)(?:\.(\d*))?""")

  private fun parseScaled(raw: String, scale: Int): Long? {
    val m = DECIMAL.matchEntire(raw.trim()) ?: return null
    val frac = m.groupValues[2].trimEnd('0')
    if (frac.length > scale) return null
    val whole = m.groupValues[1].trimStart('0').ifEmpty { "0" }
    if (whole.length > 15) return null
    var mul = 1L
    repeat(scale) { mul *= 10 }
    return runCatching {
      Math.addExact(Math.multiplyExact(whole.toLong(), mul), frac.padEnd(scale, '0').ifEmpty { "0" }.toLong())
    }.getOrNull()
  }

  /** فیِ ارزی (صدمِ واحد) ⇒ افغانی (صدمِ افغانی) با همین نرخ؛ گردِ نزدیک‌ترین، نیمه به +∞. سرریز ⇒ `null` */
  fun toAfnMinor(foreignMinor: Long, rateMicros: Long): Long? =
    runCatching { PurchaseOrderCore.mulDivRound(foreignMinor, rateMicros, RATE_SCALE) }.getOrNull()

  /** جمعِ ارزیِ ردیف (صدمِ واحد) = round(مقدار × فی)، همان `PurchaseOrderCore.lineTotalMinor`. سرریز ⇒ `null` */
  fun lineTotalMinor(quantity: Double, priceMinor: Long): Long? =
    runCatching { PurchaseOrderCore.lineTotalMinor(StockLedger.milli(quantity), priceMinor) }.getOrNull()

  /** ردیفِ خریدِ ثبت‌شده + سندِ ارزی؛ فقط فیلدهای `fx…` اضافه می‌شوند */
  fun stamp(p: Purchase, rate: Rate, priceMinor: Long): Purchase = p.copy(
    fxCurrency = rate.currency,
    fxRateMicros = rate.rateMicros,
    fxPriceMinor = priceMinor,
    fxTotalMinor = lineTotalMinor(p.quantity, priceMinor),
  )

  /** خلاصهٔ ارزیِ یک فاکتور: فقط وقتی همهٔ ردیف‌ها یک ارز و یک نرخ دارند */
  data class Summary(val currency: String, val rateMicros: Long, val totalMinor: Long)

  fun summary(lines: List<Purchase>): Summary? {
    val first = lines.firstOrNull() ?: return null
    val cur = first.fxCurrency ?: return null
    val rate = first.fxRateMicros ?: return null
    var sum = 0L
    for (p in lines) {
      if (p.fxCurrency != cur || p.fxRateMicros != rate) return null
      sum = runCatching { Math.addExact(sum, p.fxTotalMinor ?: return null) }.getOrNull() ?: return null
    }
    return Summary(cur, rate, sum)
  }

  /** نرخ برای نمایش: 70_250_000 ⇒ «70.25» */
  fun rateText(rateMicros: Long): String = StockLedger.plain(rateMicros, RATE_SCALE.toInt())

  /** مبلغِ ارزی برای نمایش: 1250 ⇒ «12.5» */
  fun minorText(minor: Long): String = StockLedger.plain(minor, 100)
}
