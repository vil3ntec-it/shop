package ir.vil3ntec.tohid.core.staff

import ir.vil3ntec.tohid.core.money.Currency
import ir.vil3ntec.tohid.core.money.Money
import ir.vil3ntec.tohid.core.money.Rounding
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.mulExact

/**
 *  STF-08 — پورسانتِ فروش (§۶.۹). کاتلینِ خالص و همه به `Long` ِ واحدِ خرد، هرگز Double.
 *
 *  • پایه = مبلغِ **خالصِ** ردیف (پس از سهمِ تخفیفِ فاکتور، `Rounding.allocateDiscount`)؛ ردیفِ
 *    برگشت منفی است و به نامِ **فروشندهٔ فاکتورِ اصل** می‌آید (POS-23)، نه کسی که برگشت را زد.
 *    پس برگشت پورسانتِ همان فروش را پس می‌گیرد، ولی **جمعِ دوره زیرِ صفر نمی‌رود** (تصمیمِ صاحب‌کار O-05، ب: فروشنده
 *    به دکان بدهکار نمی‌شود)؛ پایهٔ منفی (`base`) دیده می‌ماند.
 *  • نرخ به «پایه‌امتیاز» است (۱ = ۰٫۰۱٪، ۰ تا ۱۰٬۰۰۰): نرخِ دسته > نرخِ فروشنده > نرخِ پیش‌فرض.
 *    دستهٔ کم‌سود با نرخِ ۰ از پورسانت بیرون می‌ماند.
 *  • گرد کردن یک بار و در پایان، روی جمعِ هر فروشنده (نیمه به سمتِ +∞، به پلهٔ ارز)؛ پس جمعِ
 *    ردیف‌های ریز با گرد کردنِ ردیف‌به‌ردیف کم و زیاد نمی‌شود.
 *  • ردیفِ بی فروشنده در پورسانت نمی‌آید و جدا (`unassignedBase`) گزارش می‌شود، نه پنهان.
 *  • همهٔ ردیف‌ها یک ارز دارند؛ ارزِ دیگر خطاست (`CurrencyMismatchException`).
 */
object Commission {

  const val FULL_BP: Int = 10_000

  data class Plan(
    val defaultBp: Int,
    val sellerBp: Map<String, Int> = emptyMap(),
    val categoryBp: Map<String, Int> = emptyMap(),
  ) {
    init {
      (listOf(defaultBp) + sellerBp.values + categoryBp.values).forEach {
        require(it in 0..FULL_BP) { "نرخِ پورسانت باید میانِ ۰ و $FULL_BP پایه‌امتیاز باشد: $it" }
      }
    }

    fun rateOf(sellerId: String, category: String?): Int =
      category?.let { categoryBp[it] } ?: sellerBp[sellerId] ?: defaultBp
  }

  data class Line(val sellerId: String, val category: String?, val net: Money)

  data class SellerTotal(val sellerId: String, val base: Money, val commission: Money)

  data class Result(val sellers: List<SellerTotal>, val unassignedBase: Money)

  /**
   *  درصدِ نوشته‌شده ⇒ پایه‌امتیاز: «۲٫۵» یا «2.5» ⇒ ۲۵۰. ارقامِ فارسی و عربی، `٫` و `,` و `.`
   *  پذیرفته‌اند؛ حداکثر دو رقمِ اعشار؛ بیرون از ۰ تا ۱۰۰ یا نوشتهٔ نادرست ⇒ `null` (نه گرد کردنِ بی‌صدا).
   */
  fun bpOfPercent(text: String): Int? {
    val s = buildString {
      for (c in text.trim()) append(
        when (c) {
          in '۰'..'۹' -> '0' + (c - '۰')
          in '٠'..'٩' -> '0' + (c - '٠')
          '٫', ',' -> '.'
          else -> c
        },
      )
    }
    val m = Regex("^([0-9]{1,3})(?:\\.([0-9]{1,2}))?$").matchEntire(s) ?: return null
    val whole = m.groupValues[1].toInt()
    val frac = m.groupValues[2].padEnd(2, '0').toInt()
    val bp = whole * 100 + frac
    return if (bp in 0..FULL_BP) bp else null
  }

  /** پایه‌امتیاز ⇒ درصدِ نمایشی با ارقامِ لاتین: ۲۵۰ ⇒ «2.5»، ۳۰۰ ⇒ «3» */
  fun percentOf(bp: Int): String {
    val whole = bp / 100
    val frac = bp % 100
    return if (frac == 0) "$whole" else "$whole." + frac.toString().padStart(2, '0').trimEnd('0')
  }

  /** فاکتورِ اصلِ یک برگشت: فروشنده‌اش و این‌که لغو شده یا نه */
  data class Origin(val sellerId: String, val cancelled: Boolean = false)

  /** یک ردیفِ برگشت: شناسهٔ فاکتورِ اصل، مبلغِ پس‌داده (مثبت) و دستهٔ کالای برگشتی (برای «نرخِ دسته»؛ نامعلوم ⇒ null) */
  data class Returned(val saleId: String, val amount: Money, val category: String? = null)

  /** یک قلمِ فاکتور برای `saleLines`: دستهٔ کالا (بی دسته ⇒ null یا خالی) و مبلغِ ناخالصِ قلم */
  data class Item(val category: String?, val gross: Money)

  /**
   *  STF-08 — یک فاکتور ⇒ ردیف‌های پورسانت به تفکیکِ دسته، تا «نرخِ دسته» اثر کند. پایهٔ کل همان `finalTotal` ِ
   *  فاکتور است (همان پیشین) و تفاوتِ جمعِ قلم‌ها با آن (تخفیفِ فاکتور) به نسبتِ مبلغِ قلم‌ها، به واحدِ خرد و با
   *  «بزرگ‌ترین باقی‌مانده» (همان روشِ `Rounding.allocateDiscount`، بی پلهٔ ارز)، از قلم‌ها کم می‌شود؛ قلم‌های
   *  هم‌دسته یک ردیف‌اند، به ترتیبِ نخستین قلم. بی دسته در همهٔ قلم‌ها، بی قلم، جمعِ صفر، ارزِ دیگر، قلمِ منفی،
   *  فاکتورِ بیش از جمعِ قلم‌ها یا عددِ بیرون از بازهٔ دقیق ⇒ همان یک ردیفِ بی دسته (رفتارِ پیشین). پس بی `categoryBp`
   *  پورسانت همان پیشین است.
   */
  fun saleLines(sellerId: String, finalTotal: Money, items: List<Item>): List<Line> {
    val whole = listOf(Line(sellerId, null, finalTotal))
    val cur = finalTotal.currency
    if (items.all { it.category.isNullOrBlank() } || items.any { it.gross.currency.code != cur.code || it.gross.minor < 0 }) return whole
    return try {
      val sum = items.fold(0L) { acc, i -> addExact(acc, i.gross.minor) }
      val off = sum - finalTotal.minor
      if (sum <= 0L || off < 0L) return whole
      val share = LongArray(items.size)
      val rem = LongArray(items.size)
      var given = 0L
      items.forEachIndexed { i, it ->
        val num = mulExact(off, it.gross.minor)
        share[i] = num / sum
        rem[i] = num % sum
        given += share[i]
      }
      items.indices.sortedWith(compareByDescending<Int> { rem[it] }.thenBy { it }).take((off - given).toInt()).forEach { share[it] += 1 }
      val net = LinkedHashMap<String?, Long>()
      items.forEachIndexed { i, it ->
        val c = it.category?.trim()?.takeIf(String::isNotEmpty)
        net[c] = addExact(net[c] ?: 0L, it.gross.minor - share[i])
      }
      net.map { (c, m) -> Line(sellerId, c, Money(m, cur)) }
    } catch (_: ArithmeticException) {
      whole
    }
  }

  /**
   *  ردیف‌های **منفیِ** برگشت برای `compute`، به نامِ فروشندهٔ فاکتورِ اصل (نه کسی که برگشت را زد؛ POS-23)،
   *  حتی اگر خودِ فروش در دورهٔ پیش بوده — پس پورسانتِ همان فروش پس گرفته می‌شود.
   *  • فاکتورِ اصلِ لغوشده ⇒ بیرون: فروشش هم شمرده نشده بود (همان قاعدهٔ `ReportEngine.sales`).
   *  • فاکتورِ اصلِ ناپیدا یا بی فروشنده ⇒ ردیفِ بی فروشنده (`unassignedBase`)، نه پنهان.
   */
  fun returnLines(returns: List<Returned>, origins: Map<String, Origin>): List<Line> =
    returns.mapNotNull { r ->
      val o = origins[r.saleId]
      if (o?.cancelled == true) null else Line(o?.sellerId?.trim().orEmpty(), r.category?.trim()?.takeIf(String::isNotEmpty), -r.amount)
    }

  fun compute(lines: List<Line>, plan: Plan, currency: Currency): Result {
    val base = HashMap<String, Long>()
    val scaled = HashMap<String, Long>()          // مبلغ × پایه‌امتیاز، بی گرد کردن
    var unassigned = Money.zero(currency)
    for (l in lines) {
      val m = Money.zero(currency) + l.net         // ارزِ دیگر ⇒ CurrencyMismatchException
      if (l.sellerId.isBlank()) { unassigned += m; continue }
      base[l.sellerId] = addExact(base[l.sellerId] ?: 0L, m.minor)
      scaled[l.sellerId] = addExact(scaled[l.sellerId] ?: 0L, mulExact(m.minor, plan.rateOf(l.sellerId, l.category).toLong()))
    }
    val step = currency.stepMinor
    val sellers = base.keys.sorted().map { id ->
      //  O-05 (ب): جمعِ دوره زیرِ صفر نمی‌رود
      val c = mulExact(Rounding.roundHalfUp(scaled.getValue(id), mulExact(FULL_BP.toLong(), step)), step).coerceAtLeast(0L)
      SellerTotal(id, Money(base.getValue(id), currency), Money(c, currency))
    }
    return Result(sellers, unassigned)
  }
}
