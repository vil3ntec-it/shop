package ir.vil3ntec.tohid.core.purchase

import ir.vil3ntec.tohid.core.money.MAX_EXACT
import ir.vil3ntec.tohid.core.pay.Pay
import ir.vil3ntec.tohid.core.safe.SafeCurrency

/**
 *  PUR-11 (تصمیمِ D-15 ب، ۱۴۰۵/۰۸/۲۶) — خرید به ارزِ دیگر با نرخِ ثبت‌شده؛ بهای تمام‌شده به افغانی.
 *
 *  ── قاعده ──────────────────────────────────────────────────────────
 *  • سند (فاکتورِ خرید) یک ارز و **یک نرخ** دارد: [Rate] = چند پول (یک‌صدمِ افغانی) برای [Rate.per] واحدِ اصلیِ ارز —
 *    همان شکلِ نرخِ PAY-10 (`FxRate`، `Pay.FX_PER`: ۱ دالر، ۱٬۰۰۰ کلدار، یک میلیون ریال). کد و اعشار همان ارزهای
 *    گاوصندوق (`SafeCurrency`: سه حرفِ لاتین؛ ریال بی اعشار، بقیه دو رقم).
 *  • فیِ ردیف به همان ارز است؛ جمعِ ردیف = مقدار × فی، **نیمه رو به بالا** به یک واحدِ کوچکِ همان ارز (سنت، ریال) — [lineFx].
 *  • معادلِ افغانی **یک بار برای کلِ سند**: [toAfn] ِ جمعِ ارزی، نیمه رو به بالا به یک **پول** (۰٫۰۱ افغانی). سهمِ هر ردیف
 *    از آن با «بزرگ‌ترین باقی‌مانده» به نسبتِ جمعِ ارزیِ ردیف‌ها ([allocate])، پس جمعِ سهم‌ها دقیقاً همان معادلِ سند است و
 *    پرداختِ کاملِ سند به همان نرخ اختلافِ صفر می‌دهد. بهای تمام‌شده (میانگینِ موزون، `StockLedger`) همین سهمِ افغانی است.
 *  • حسابِ تأمین‌کننده برای هر ارز جدا ([Account]): ماندهٔ ارزی (`fx`) و «ارزشِ دفتریِ» افغانیِ همان مانده (`afn`، جمعِ
 *    معادل‌های ثبت‌شده منهای آنچه پرداخت‌ها برداشتند). پرداخت به همان ارز یا به افغانی، هر کدام **با نرخِ خودش** ([payInFx]،
 *    [payInAfn]): ارزشِ دفتریِ برداشته‌شده ([Settle.carried]) به نسبتِ مانده (پرداختِ همهٔ مانده ⇒ همهٔ ارزشِ دفتری)، و
 *    **اختلافِ نرخ** = ارزشِ پرداخت به نرخِ خودش − ارزشِ دفتری ([Settle.diff]؛ مثبت = زیان، منفی = سود). همین سه عدد در
 *    رکوردِ پرداخت **ثابت** می‌شوند؛ نرخِ بعدی پرداختِ پیشین را عوض نمی‌کند.
 *  • هیچ Double: همه عددِ صحیح، ضرب و تقسیمِ دقیق (۱۲۸ بیتی) و گردِ نیمه رو به بالا (به سوی +∞، همان `Rounding.roundHalfUp`).
 *    حاصلِ بیرون از ±(۲^۵۳−۱) ⇒ `ArithmeticException` (صدازننده خطای «مبلغ درست نیست» می‌دهد).
 *  Kotlin ِ خالص (commonMain) — بی ساعت، بی ذخیره.
 */
object PurchaseFx {

  const val AFN = "AFN"

  /** نرخِ یک سند یا یک پرداخت: [rate] پول برای [per] واحدِ اصلیِ [currency] (۱ دالر = ۷۰٫۲۵ ⇒ 7025، per = 1) */
  data class Rate(val currency: String, val rate: Long, val per: Long)

  /** نرخِ درست یا null: ارزِ سه‌حرفیِ غیرِ افغانی، نرخ عددِ صحیحِ مثبت، [per] یکی از `Pay.FX_PER` */
  fun rate(currency: String?, rate: Long?, per: Long?): Rate? {
    val c = currency?.trim().orEmpty()
    if (!SafeCurrency.isCode(c) || c == AFN) return null
    if (rate == null || rate !in 1..MAX_EXACT) return null
    val p = per ?: 1L
    if (p !in Pay.FX_PER) return null
    return Rate(c, rate, p)
  }

  /** واحدِ نرخِ پیش‌فرضِ هر ارز (همان `FxTill.DEFAULT_PER`): یک دالر، هزار کلدار، یک میلیون ریال؛ بقیه یک */
  fun defaultPer(currency: String): Long = when (currency) { "PKR" -> 1_000L; "IRR" -> 1_000_000L; else -> 1L }

  private fun unit(r: Rate): Long = mul(SafeCurrency.minorPerMajor(r.currency), r.per)

  /** مبلغِ ارزی (واحدِ کوچک) ⇒ پول، نیمه رو به بالا: round(fx × rate ÷ (واحدِ ارز × per)) */
  fun toAfn(fxMinor: Long, r: Rate): Long = mulDivHalfUp(fxMinor, r.rate, unit(r))

  /** پول ⇒ مبلغِ ارزی (واحدِ کوچک)، نیمه رو به بالا: round(afn × واحدِ ارز × per ÷ rate) */
  fun fromAfn(afnMinor: Long, r: Rate): Long = mulDivHalfUp(afnMinor, unit(r), r.rate)

  /** جمعِ ردیف به واحدِ کوچکِ ارز: مقدار (هزارم) × فی، نیمه رو به بالا */
  fun lineFx(qtyMilli: Long, priceMinor: Long): Long = mulDivHalfUp(qtyMilli, priceMinor, 1000)

  /**
   *  پخشِ [total] میانِ ردیف‌ها به نسبتِ [weights] به روشِ «بزرگ‌ترین باقی‌مانده»: جمعِ سهم‌ها **دقیقاً** [total]؛
   *  برابری ⇒ ردیفِ جلوتر. وزن‌ها هم‌علامتِ [total] (سندِ کسر هر دو منفی)؛ وزنِ صفر سهمِ صفر؛ همهٔ وزن‌ها صفر ⇒ همه به ردیفِ نخست.
   */
  fun allocate(total: Long, weights: List<Long>): List<Long> {
    if (weights.isEmpty()) return emptyList()
    val neg = total < 0
    val t = if (neg) -total else total
    val w = weights.map { if (it < 0) -it else it }
    val sum = w.fold(0L) { a, b -> add(a, b) }
    if (sum == 0L) return weights.indices.map { if (it == 0) total else 0L }
    val base = LongArray(w.size)
    val rem = LongArray(w.size)
    var left = t
    w.forEachIndexed { i, x ->
      val (q, r) = mulDivRem(t, x, sum)
      base[i] = q; rem[i] = r; left -= q
    }
    w.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it }).forEach { i ->
      if (left > 0 && w[i] > 0) { base[i] += 1; left -= 1 }
    }
    return base.map { if (neg) -it else it }
  }

  /** سندِ ارزی: ردیف‌ها به ارز و افغانی، و پرداختِ هنگامِ خرید (به همان ارز و همان نرخ، پس بی اختلاف) */
  data class Doc(
    val rate: Rate,
    val fxLines: List<Long>,
    val afnLines: List<Long>,
    val fxPaidLines: List<Long>,
    val afnPaidLines: List<Long>,
  ) {
    val fxTotal: Long get() = fxLines.fold(0L) { a, b -> add(a, b) }
    val afnTotal: Long get() = afnLines.fold(0L) { a, b -> add(a, b) }
    val fxPaid: Long get() = fxPaidLines.fold(0L) { a, b -> add(a, b) }
    val afnPaid: Long get() = afnPaidLines.fold(0L) { a, b -> add(a, b) }
  }

  /**
   *  سند از جمعِ ارزیِ ردیف‌ها ([fxLines]، ≥ ۰) و پرداختِ هنگامِ خرید ([fxPaid]، به همان ارز؛ بیش از جمع ⇒ همان جمع، منفی ⇒ ۰):
   *  معادلِ افغانیِ سند = [toAfn] ِ جمعِ ارزی، سهمِ ردیف‌ها [allocate]؛ پرداخت به ترتیبِ ردیف‌ها (همان پخشِ `PurchaseBook`) و
   *  سهمِ افغانیِ پرداختِ هر ردیف به نسبتِ همان ردیف — ردیفِ تمام‌پرداخت همان سهمِ کاملش، پس بدهیِ ردیف دقیقاً صفر می‌شود.
   */
  fun doc(rate: Rate, fxLines: List<Long>, fxPaid: Long): Doc {
    require(fxLines.all { it >= 0 }) { "purchase-fx: line totals must be ≥ 0" }
    val fxTotal = fxLines.fold(0L) { a, b -> add(a, b) }
    val afnLines = allocate(toAfn(fxTotal, rate), fxLines)
    var left = fxPaid.coerceIn(0L, fxTotal)
    val fxPaidLines = fxLines.map { l -> minOf(left, l).also { left -= it } }
    val afnPaidLines = fxLines.indices.map { i ->
      when {
        fxPaidLines[i] == 0L -> 0L
        fxPaidLines[i] == fxLines[i] -> afnLines[i]
        else -> mulDivHalfUp(afnLines[i], fxPaidLines[i], fxLines[i])
      }
    }
    return Doc(rate, fxLines, afnLines, fxPaidLines, afnPaidLines)
  }

  /** حسابِ یک ارزِ یک تأمین‌کننده: ماندهٔ ارزی و ارزشِ دفتریِ افغانیِ آن (مثبت = ما بدهکاریم؛ منفی = پیش‌پرداخت/طلب) */
  data class Account(val currency: String, val fx: Long, val afn: Long)

  /**
   *  اثرِ یک پرداخت: [fx] از ماندهٔ ارزی کم می‌شود، [carried] از ارزشِ دفتری (و از بدهیِ افغانیِ دفتر)؛ [value] ارزشِ همان
   *  پرداخت به نرخِ خودش؛ [afnPaid] پولِ افغانیِ داده‌شده اگر به افغانی پرداخت شد (وگرنه null، یعنی به همان ارز).
   */
  data class Settle(val fx: Long, val value: Long, val carried: Long, val afnPaid: Long?) {
    /** اختلافِ نرخ به پول: مثبت = زیان (بیش از ارزشِ دفتری دادیم)، منفی = سود */
    val diff: Long get() = value - carried
  }

  /**
   *  ارزشِ دفتریِ [fx] واحد از حسابِ [acc]: بی بدهی ⇒ همان [toAfn] به نرخِ پرداخت (پیش‌پرداخت، بی اختلاف)؛ کمتر از مانده ⇒
   *  round(afn × fx ÷ مانده)؛ همهٔ مانده یا بیشتر ⇒ همهٔ ارزشِ دفتری + بیشِ آن به نرخِ پرداخت.
   */
  fun carried(acc: Account, fx: Long, r: Rate): Long = when {
    acc.fx <= 0 -> toAfn(fx, r)
    fx < acc.fx -> mulDivHalfUp(acc.afn, fx, acc.fx)
    else -> add(acc.afn, toAfn(fx - acc.fx, r))
  }

  /** پرداختِ [fxAmount] (واحدِ کوچکِ همان ارز، > ۰) با نرخِ [r] ِ همین پرداخت؛ نادرست ⇒ null */
  fun payInFx(acc: Account, fxAmount: Long, r: Rate): Settle? {
    if (r.currency != acc.currency || fxAmount !in 1..MAX_EXACT) return null
    return try { Settle(fxAmount, toAfn(fxAmount, r), carried(acc, fxAmount, r), null) } catch (e: ArithmeticException) { null }
  }

  /**
   *  پرداختِ [afnAmount] پول (> ۰) برای حسابِ ارزیِ [acc] با نرخِ [r]: مبلغِ ارزیِ صاف‌شده = [fromAfn]؛ پولی که به همان نرخ
   *  همهٔ مانده است (یا بیشتر) دقیقاً همهٔ مانده را صاف می‌کند (+ بیشِ آن)، تا ماندهٔ کمتر از یک پول نماند. کمتر از یک واحدِ
   *  کوچکِ ارز ⇒ null.
   */
  fun payInAfn(acc: Account, afnAmount: Long, r: Rate): Settle? {
    if (r.currency != acc.currency || afnAmount !in 1..MAX_EXACT) return null
    return try {
      val all = if (acc.fx > 0) toAfn(acc.fx, r) else -1L
      val fx = when {
        acc.fx <= 0 -> fromAfn(afnAmount, r)
        afnAmount >= all -> add(acc.fx, fromAfn(afnAmount - all, r))
        else -> minOf(fromAfn(afnAmount, r), acc.fx)
      }
      if (fx <= 0) null else Settle(fx, afnAmount, carried(acc, fx, r), afnAmount)
    } catch (e: ArithmeticException) { null }
  }

  /** همان عدد با نرخِ [r] به‌صورتِ «1 USD = 70.25 AFN» (رقمِ لاتین؛ همان `FxTill.rateLine`) */
  fun rateLine(r: Rate): String = ir.vil3ntec.tohid.core.pay.FxTill.rateLine(r.rate, r.per, r.currency)

  // ── عددِ صحیحِ دقیق ─────────────────────────────────────────────
  private fun ovf(): Nothing = throw ArithmeticException("purchase-fx: بیرون از بازهٔ دقیق")

  private fun add(a: Long, b: Long): Long {
    val s = a + b
    if (((a xor s) and (b xor s)) < 0 || s > MAX_EXACT || s < -MAX_EXACT) ovf()
    return s
  }

  private fun mul(a: Long, b: Long): Long {
    if (a != 0L && b != 0L && (a > MAX_EXACT / b || a < -MAX_EXACT / b)) ovf()
    return a * b
  }

  /**
   *  round(a × b ÷ c)، نیمه به سوی +∞ (همان `Rounding.roundHalfUp`) — a، b با هر علامت، c > ۰؛ ضربِ میانی ۱۲۸ بیتی (بی سرریز)،
   *  حاصل باید در ±(۲^۵۳−۱) بماند.
   */
  internal fun mulDivHalfUp(a: Long, b: Long, c: Long): Long {
    require(c > 0) { "purchase-fx: divisor must be > 0" }
    if (a < -MAX_EXACT || a > MAX_EXACT || b < -MAX_EXACT || b > MAX_EXACT) ovf()
    val neg = (a < 0) != (b < 0)
    val (q, r) = mulDivRem(if (a < 0) -a else a, if (b < 0) -b else b, c)
    //  مثبت: q + (2r ≥ c)؛ منفی: −q − (2r > c) — یعنی floor(x + ½) برای هر دو
    val out = if (!neg) q + (if (r >= c - r) 1 else 0) else -q - (if (r > c - r) 1 else 0)
    if (out > MAX_EXACT || out < -MAX_EXACT) ovf()
    return out
  }

  /** خارج‌قسمت و باقی‌ماندهٔ a × b ÷ c برای a، b ≥ ۰ و c > ۰ — بیت‌به‌بیت، بی سرریز (باقی‌مانده همیشه کمتر از c) */
  private fun mulDivRem(a: Long, b: Long, c: Long): Pair<Long, Long> {
    if (a == 0L || b == 0L) return 0L to 0L
    if (a <= Long.MAX_VALUE / b) return (a * b / c) to (a * b % c)
    //  ⌊a/c⌋ × b ≤ حاصل < (⌊a/c⌋ + 1) × b: اولی از ۲^۵۳ گذشت ⇒ سرریز؛ وگرنه حاصل زیرِ ۲^۵۴ و حلقه بی سرریز
    val qa = a / c
    if (qa > 0 && b > MAX_EXACT / qa) ovf()
    val qb = b / c
    val rb = b % c
    var q = 0L
    var r = 0L
    for (bit in 62 downTo 0) {
      q = q shl 1
      if (r >= c - r) { r -= c - r; q += 1 } else r = r shl 1
      if ((a shr bit) and 1L == 1L) {
        q += qb
        if (r >= c - rb) { r -= c - rb; q += 1 } else r += rb
      }
    }
    return q to r
  }
}
