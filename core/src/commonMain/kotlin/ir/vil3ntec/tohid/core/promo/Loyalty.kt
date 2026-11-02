package ir.vil3ntec.tohid.core.promo

/**
 *  PRM-13 — امتیازِ وفاداری (کسب و مصرف) و سطحِ مشتری. خالص و فقط عددِ صحیح: نه ساعت، نه دفتر، نه Double.
 *
 *  ── قاعده ([Rule]) ──────────────────────────────────────────────
 *    • کسب: هر [Rule.everyAfn] افغانیِ **پرداخت‌شده** یک امتیاز، ضربِ ضریبِ سطحِ مشتری ([Tier.multiplierPct]، درصد)،
 *      همیشه رو به پایین ([earn]). بخشِ نسیهٔ فاکتور و بخشی که با امتیاز کم شد امتیاز نمی‌دهد.
 *    • سطح: از خریدِ ۱۲ ماهِ گذشتهٔ همان مشتری ([tier])؛ برنزی ×۱، نقره‌ای ×۱٫۲۵، طلایی ×۱٫۵ (آستانه‌ها قابلِ تنظیم).
 *    • مصرف: هر امتیاز [Rule.pointAfn] افغانی از فاکتور کم می‌کند — حداکثر تا ماندهٔ امتیاز **و** تا
 *      [Rule.maxSharePct] درصدِ فاکتور ([cap]). مصرف «تخفیفِ شبیهِ پرداخت» است و هرگز `Sale.discount` نمی‌شود.
 *
 *  ── دفتر ([Event]) ──────────────────────────────────────────────
 *  فقط افزودنی: [EARN]/[SPEND] با فاکتور، [EARN_BACK]/[SPEND_BACK] با برگشت یا لغو. مانده = جمعِ علامت‌دار ([balance]).
 *  برگشت به نسبتِ پولِ برگشته ([share])، با شمارشِ تجمعی: چند برگشتِ تکه‌تکه بیش از برگشتِ یک‌جا پس نمی‌گیرد و
 *  برگشتِ کامل دقیقاً همه را. یک دستگاه هرگز مانده را منفی نمی‌کند ([reverse])؛ دو دستگاهِ بی‌خبر ⇒ منفی و «نیازمندِ بررسی».
 */
object Loyalty {

  const val EARN = "earn"
  const val SPEND = "spend"
  const val EARN_BACK = "earn_back"
  const val SPEND_BACK = "spend_back"

  const val BRONZE = "bronze"
  const val SILVER = "silver"
  const val GOLD = "gold"

  /** سقفِ هر عدد — بیش از این خطای ورودی است، نه دکان */
  const val MAX_POINTS = 1_000_000_000L
  private const val MINOR = 100L

  data class Tier(val key: String, val fromAfn: Long, val multiplierPct: Int)

  data class Rule(
    /** هر چند افغانیِ پرداخت‌شده یک امتیاز */
    val everyAfn: Long = 100,
    /** ارزشِ هر امتیاز هنگامِ مصرف (افغانی) */
    val pointAfn: Long = 1,
    /** سقفِ سهمِ امتیاز از یک فاکتور (درصد) */
    val maxSharePct: Int = 20,
    /** آستانهٔ خریدِ ۱۲ ماهه برای نقره‌ای و طلایی (افغانی) */
    val silverAfn: Long = 50_000,
    val goldAfn: Long = 200_000,
  ) {
    val tiers: List<Tier>
      get() = listOf(Tier(BRONZE, 0, 100), Tier(SILVER, silverAfn, 125), Tier(GOLD, goldAfn, 150))

    val valid: Boolean
      get() = everyAfn in 1..1_000_000 && pointAfn in 1..1_000_000 && maxSharePct in 1..100 &&
        silverAfn in 1..1_000_000_000_000 && goldAfn in silverAfn + 1..1_000_000_000_000
  }

  /** [customer] = شناسهٔ ماندگارِ مشتری؛ [saleId] = فاکتوری که رویداد از آن است (برگشت و لغو هم همان) */
  data class Event(val id: String, val kind: String, val customer: String, val points: Long, val saleId: String, val at: Long = 0)

  fun signed(e: Event): Long = when (e.kind) {
    EARN, SPEND_BACK -> e.points
    SPEND, EARN_BACK -> -e.points
    else -> 0
  }

  fun balance(events: List<Event>, customer: String): Long = events.filter { it.customer == customer }.sumOf(::signed)

  /** جمعِ یک نوع برای یک فاکتور */
  fun total(events: List<Event>, saleId: String, kind: String): Long =
    events.filter { it.saleId == saleId && it.kind == kind }.sumOf { it.points }

  fun tier(rule: Rule, spendAfn: Long): Tier = rule.tiers.last { spendAfn >= it.fromAfn }

  /** امتیازِ [paidMinor] (واحدِ کوچک، ۱۰۰ = یک افغانی) با ضریبِ [tier]؛ همیشه رو به پایین */
  fun earn(rule: Rule, tier: Tier, paidMinor: Long): Long {
    if (!rule.valid || paidMinor <= 0) return 0
    return mulDiv(paidMinor, tier.multiplierPct.toLong(), rule.everyAfn * MINOR * 100).coerceIn(0, MAX_POINTS)
  }

  /** بیشترین امتیازِ قابلِ مصرف روی فاکتورِ [invoiceMinor]: نه بیش از مانده، نه بیش از سهمِ مجاز */
  fun cap(rule: Rule, balance: Long, invoiceMinor: Long): Long {
    if (!rule.valid || balance <= 0 || invoiceMinor <= 0) return 0
    val share = mulDiv(invoiceMinor, rule.maxSharePct.toLong(), 100) / (rule.pointAfn * MINOR)
    return minOf(balance, share, MAX_POINTS)
  }

  /** ارزشِ [points] امتیاز به واحدِ کوچک */
  fun valueMinor(rule: Rule, points: Long): Long = points.coerceIn(0, MAX_POINTS) * rule.pointAfn * MINOR

  sealed interface Spend {
    data class Ok(val points: Long, val valueMinor: Long) : Spend
    data class Bad(val code: String) : Spend
  }

  object Code {
    const val BAD_RULE = "BAD_RULE"
    const val BAD_POINTS = "BAD_POINTS"
    const val OVER_BALANCE = "OVER_BALANCE"
    const val OVER_SHARE = "OVER_SHARE"
  }

  /** سنجشِ مصرفِ [points] امتیاز روی فاکتورِ [invoiceMinor] با ماندهٔ [balance] */
  fun spend(rule: Rule, balance: Long, invoiceMinor: Long, points: Long): Spend = when {
    !rule.valid -> Spend.Bad(Code.BAD_RULE)
    points <= 0 || points > MAX_POINTS -> Spend.Bad(Code.BAD_POINTS)
    points > balance -> Spend.Bad(Code.OVER_BALANCE)
    points > cap(rule, balance, invoiceMinor) -> Spend.Bad(Code.OVER_SHARE)
    else -> Spend.Ok(points, valueMinor(rule, points))
  }

  /** `floor(total × part ÷ whole)`، [part] تا [whole] بریده؛ کاملِ [whole] ⇒ همان [total] */
  fun share(total: Long, part: Long, whole: Long): Long {
    if (total <= 0 || part <= 0 || whole <= 0) return 0
    if (part >= whole) return total
    return mulDiv(total, part, whole).coerceIn(0, total)
  }

  /** آنچه با برگشت/لغو پس می‌رود: امتیازِ مصرف‌شده به مشتری، امتیازِ کسب‌شده از او */
  data class Reversal(val spendBack: Long, val earnBack: Long, val shortfall: Long)

  /**
   *  برگشتِ تجمعیِ [part] از [whole] (همان پولِ فاکتور): هدف = سهمِ تا امروز، منهای آنچه پیش‌تر پس رفت.
   *  مصرف اول به مشتری برمی‌گردد، بعد کسب از ماندهٔ همان لحظه کم می‌شود — هرگز زیرِ صفر ([shortfall] = آنچه کم نشد).
   */
  fun reverse(
    earned: Long, earnedBack: Long, spent: Long, spentBack: Long, part: Long, whole: Long, balance: Long,
  ): Reversal {
    val spendBack = (share(spent, part, whole) - spentBack).coerceAtLeast(0)
    val wanted = (share(earned, part, whole) - earnedBack).coerceAtLeast(0)
    val room = (balance + spendBack).coerceAtLeast(0)
    val earnBack = minOf(wanted, room)
    return Reversal(spendBack, earnBack, wanted - earnBack)
  }

  /** همه چیزِ ماندهٔ فاکتور (لغو) */
  fun reverseAll(earned: Long, earnedBack: Long, spent: Long, spentBack: Long, balance: Long): Reversal =
    reverse(earned, earnedBack, spent, spentBack, 1, 1, balance)

  /**
   *  تقسیمِ [valueAfn] افغانی میانِ ردیف‌ها به نسبتِ [linesAfn] (بیشترین باقی‌مانده؛ برابر ⇒ ردیفِ جلوتر).
   *  هیچ ردیف بیش از خودش کم نمی‌شود. جمعِ کمتر از [valueAfn] یا ورودیِ منفی ⇒ `null`.
   */
  fun split(linesAfn: List<Long>, valueAfn: Long): List<Long>? {
    if (valueAfn < 0 || linesAfn.any { it < 0 }) return null
    val whole = linesAfn.sum()
    if (valueAfn > whole) return null
    if (valueAfn == 0L) return linesAfn.map { 0L }
    val base = LongArray(linesAfn.size)
    val rest = LongArray(linesAfn.size)
    linesAfn.forEachIndexed { i, a ->
      base[i] = mulDiv(valueAfn, a, whole)
      rest[i] = mulMod(valueAfn, a, whole)
    }
    var left = valueAfn - base.sum()
    val order = linesAfn.indices.sortedWith(compareByDescending<Int> { rest[it] }.thenBy { it })
    for (i in order) {
      if (left <= 0) break
      if (base[i] < linesAfn[i]) { base[i] += 1; left -= 1 }
    }
    return if (left == 0L) base.toList() else null
  }

  // ── عددِ صحیحِ بی سرریز ─────────────────────────────────────────
  /** `floor(a × b ÷ c)` برای a، b ≥ ۰ و c > ۰ — دقیق و بی سرریز (حاصل همیشه ≤ max(a, b) وقتی یکی ≤ c است) */
  internal fun mulDiv(a: Long, b: Long, c: Long): Long = mulDivRem(a, b, c).first

  private fun mulMod(a: Long, b: Long, c: Long): Long = mulDivRem(a, b, c).second

  /** خارج‌قسمت و باقی‌ماندهٔ `a × b ÷ c`؛ ضربِ سرریزکننده بیت به بیت، با باقی‌ماندهٔ همیشه کمتر از c */
  private fun mulDivRem(a: Long, b: Long, c: Long): Pair<Long, Long> {
    require(a >= 0 && b >= 0 && c > 0)
    if (a == 0L || b == 0L) return 0L to 0L
    if (a <= Long.MAX_VALUE / b) return (a * b / c) to (a * b % c)
    val qb = b / c
    val rb = b % c
    var q = 0L
    var r = 0L
    for (bit in 62 downTo 0) {
      //  V ← 2V
      q = q shl 1
      if (r >= c - r) { r -= c - r; q += 1 } else r = r shl 1
      //  V ← V + b
      if ((a shr bit) and 1L == 1L) {
        q += qb
        if (r >= c - rb) { r -= c - rb; q += 1 } else r += rb
      }
    }
    return q to r
  }
}
