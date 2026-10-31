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
 *    پس برگشت پورسانتِ همان فروش را پس می‌گیرد و پورسانتِ دوره می‌تواند منفی باشد.
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
      val c = mulExact(Rounding.roundHalfUp(scaled.getValue(id), mulExact(FULL_BP.toLong(), step)), step)
      SellerTotal(id, Money(base.getValue(id), currency), Money(c, currency))
    }
    return Result(sellers, unassigned)
  }
}
