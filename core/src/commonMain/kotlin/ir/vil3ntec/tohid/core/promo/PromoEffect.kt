package ir.vil3ntec.tohid.core.promo

import ir.vil3ntec.tohid.core.money.MAX_EXACT
import ir.vil3ntec.tohid.core.money.addExact
import ir.vil3ntec.tohid.core.money.checkExact

/**
 *  PRM-16 — اثرِ هر تخفیفِ خودکار روی فروش و سود، از قلم‌های فاکتور.
 *
 *  ورودی همان چیزی است که صندوق روی هر قلم می‌نویسد (`SaleItem.promo` = فهرستِ
 *  `{id, name, discountMinor}`، `PromoEngine.stamp`): مقدار به هزارم، مبلغِ خالصِ قلم
 *  (پس از همهٔ تخفیف‌ها) و بهای تمام‌شده به یک‌صدم. برای هر تخفیف:
 *
 *    • فاکتورها و قلم‌هایی که آن تخفیف را گرفتند، مقدارِ فروخته (منهای برگشتی)؛
 *    • تخفیفِ **خودِ همان** قاعده (جمعِ `discountMinor` ِ برچسب‌هایش)؛
 *    • فروشِ خالصِ قلم‌های تخفیف‌خورده و سودِ ناخالصشان (فروش − بها) و سهمشان از کلِ فروش؛
 *    • عمقِ تخفیف = تخفیف ÷ (فروش + تخفیف) به ده‌هزارم.
 *
 *  قاعده‌ها:
 *    • برگشتی (`returnedMilli`) فروش، بها و تخفیفِ همان قلم را به نسبتِ مقدارِ ماندگار کم
 *      می‌کند — با عددِ صحیح و گردِ نیمه به بالا؛ پول هرگز Double نیست.
 *    • قلمی که دو تخفیف گرفته در **هر دو** ردیف شمرده می‌شود (فروش و سودش)؛ پس جمعِ ستونِ
 *      فروشِ ردیف‌ها جمعِ فروش نیست — برای آن `promoRevenueMinor` (هر قلم یک بار) هست.
 *      تخفیفِ هر ردیف فقط مالِ خودش است، پس جمعِ تخفیفِ ردیف‌ها = `promoDiscountMinor`.
 *    • بهای نامعلوم (`costMinor = null`) سود را نمی‌سازد و شمرده می‌شود (`costMissingLines`)؛
 *      ردیفی که هیچ قلمش بها ندارد سودِ `null` دارد، نه صفر. بهای تخمینی (`costEstimated`، مثلاً فروش بیش از
 *      موجودی در میانگینِ موزون) در سود می‌آید ولی شمرده می‌شود (`costEstimatedLines`).
 *    • روشِ بها با صفحه است (`CostPolicy`، پیش‌فرض و مصوبِ D-23 میانگینِ موزون؛ بها از همان `ReportCore.costs`):
 *      سود فقط وقتی قطعی است که روش مصوب باشد (`costApproved`) و هیچ قلمِ تخفیف‌خورده‌ای بهای نامعلوم یا
 *      تخمینی نداشته باشد؛ وگرنه «تقریبی» — هرگز عددِ قطعیِ ساختگی.
 *    • نرخ با مخرجِ صفر ⇒ null. ترتیب: تخفیفِ بیشتر، سپس شناسه.
 *    • هر جمع زیرِ ۲^۵۳−۱ (همان سقفِ `Money`)؛ بیرون از آن خطا، نه عددِ غلط.
 *
 *  منطقِ خالص، بی `android.*`/`java.*` (§۴۰.۳)؛ خواندن از دفتر و صفحهٔ گزارش جداست.
 */
data class PromoTag(val id: String, val name: String = "", val discountMinor: Long)

data class EffectLine(
  val saleId: String,
  /** مقدارِ فروخته به هزارم */
  val qtyMilli: Long,
  /** مقدارِ برگشتیِ همین قلم به هزارم (۰ … qtyMilli) */
  val returnedMilli: Long = 0L,
  /** مبلغِ خالصِ قلم پس از همهٔ تخفیف‌ها، به یک‌صدم */
  val netMinor: Long,
  /** بهای تمام‌شدهٔ همهٔ `qtyMilli`، به یک‌صدم؛ null = نامعلوم */
  val costMinor: Long? = null,
  val promos: List<PromoTag> = emptyList(),
  /** بها هست ولی تخمینی است (همان `est` ِ هستهٔ گزارش) */
  val costEstimated: Boolean = false,
)

data class PromoEffectRow(
  val id: String,
  val name: String,
  val invoices: Int,
  val lines: Int,
  val qtyMilli: Long,
  val discountMinor: Long,
  val revenueMinor: Long,
  /** فروش − بها روی قلم‌های بهادار؛ null اگر هیچ قلمی بها نداشت */
  val profitMinor: Long?,
  val costMissingLines: Int,
  /** فروشِ این ردیف ÷ کلِ فروش، به ده‌هزارم */
  val revenueShareBp: Long?,
  /** تخفیف ÷ (فروش + تخفیف)، به ده‌هزارم */
  val discountDepthBp: Long?,
  /** قلم‌های بهادارِ این ردیف که بهایشان تخمینی است */
  val costEstimatedLines: Int = 0,
  /** سودِ همین ردیف «تقریبی» است: روشِ نامصوب، یا قلمِ بی‌بها/تخمینی */
  val profitApproximate: Boolean = true,
)

data class PromoEffectReport(
  val rows: List<PromoEffectRow>,
  /** فروشِ خالصِ همهٔ قلم‌ها (منهای برگشتی) */
  val totalRevenueMinor: Long,
  /** فروشِ قلم‌هایی که دست‌کم یک تخفیف گرفتند — هر قلم یک بار */
  val promoRevenueMinor: Long,
  /** جمعِ همهٔ تخفیف‌های خودکار (= جمعِ `discountMinor` ِ ردیف‌ها) */
  val promoDiscountMinor: Long,
  /** روشِ بها نامصوب، یا قلمِ تخفیف‌خورده‌ای بی‌بها یا با بهای تخمینی ⇒ سود تقریبی */
  val profitApproximate: Boolean = true,
)

object PromoEffect {

  /** بیشترین مقدارِ یک قلم (یک میلیون واحد) — تا ضربِ میانیِ نسبت سرریز نکند */
  const val MAX_QTY_MILLI: Long = 1_000_000_000L

  private const val FULL_BP: Long = 10_000L

  private class Acc(val id: String) {
    var name = ""
    val invoices = HashSet<String>()
    var lines = 0
    var qty = 0L
    var discount = 0L
    var revenue = 0L
    var profit = 0L
    var costed = 0
    var missing = 0
    var estimated = 0
  }

  /** [costApproved] = روشِ بهایی که `costMinor` از آن آمد مصوب است (`CostPolicy.approved`)؛ پیش‌فرض نه ⇒ «تقریبی» */
  fun of(lines: List<EffectLine>, costApproved: Boolean = false): PromoEffectReport {
    val acc = LinkedHashMap<String, Acc>()
    var total = 0L
    var promoRevenue = 0L
    var promoDiscount = 0L
    for (l in lines) {
      if (l.qtyMilli <= 0L) continue
      if (l.qtyMilli > MAX_QTY_MILLI) throw ArithmeticException("مقدارِ قلم بیرون از بازه: ${l.qtyMilli}")
      val keep = l.qtyMilli - l.returnedMilli.coerceIn(0L, l.qtyMilli)
      val revenue = share(l.netMinor, keep, l.qtyMilli)
      val cost = l.costMinor?.let { share(it, keep, l.qtyMilli) }
      total = addExact(total, revenue)
      //  یک تخفیف دو بار روی یک قلم ⇒ یک برچسب با جمعِ هر دو
      val tags = LinkedHashMap<String, Pair<String, Long>>()
      for (t in l.promos) {
        if (t.id.isEmpty()) continue
        val prev = tags[t.id]
        tags[t.id] = (t.name.ifEmpty { prev?.first.orEmpty() }) to addExact(prev?.second ?: 0L, t.discountMinor)
      }
      if (tags.isEmpty()) continue
      promoRevenue = addExact(promoRevenue, revenue)
      for ((id, tag) in tags) {
        val a = acc.getOrPut(id) { Acc(id) }
        if (tag.first.isNotEmpty()) a.name = tag.first
        a.invoices += l.saleId
        a.lines += 1
        a.qty = addExact(a.qty, keep)
        val d = share(tag.second, keep, l.qtyMilli)
        a.discount = addExact(a.discount, d)
        promoDiscount = addExact(promoDiscount, d)
        a.revenue = addExact(a.revenue, revenue)
        if (cost == null) a.missing += 1 else {
          a.costed += 1
          if (l.costEstimated) a.estimated += 1
          a.profit = addExact(a.profit, addExact(revenue, -cost))
        }
      }
    }
    val rows = acc.values.map { a ->
      PromoEffectRow(
        id = a.id, name = a.name, invoices = a.invoices.size, lines = a.lines, qtyMilli = a.qty,
        discountMinor = a.discount, revenueMinor = a.revenue,
        profitMinor = if (a.costed == 0) null else a.profit,
        costMissingLines = a.missing,
        revenueShareBp = bp(a.revenue, total),
        discountDepthBp = bp(a.discount, addExact(a.revenue, a.discount)),
        costEstimatedLines = a.estimated,
        profitApproximate = !costApproved || a.missing > 0 || a.estimated > 0,
      )
    }.sortedWith(compareByDescending<PromoEffectRow> { it.discountMinor }.thenBy { it.id })
    return PromoEffectReport(rows, total, promoRevenue, promoDiscount, !costApproved || rows.any { it.profitApproximate })
  }

  /** x × keep ÷ qty با گردِ نیمه به بالا (برای منفی قرینه)، بی سرریزِ میانی. */
  internal fun share(x: Long, keep: Long, qty: Long): Long {
    checkExact(x, "مبلغ")
    if (keep == qty) return x
    if (keep <= 0L) return 0L
    val neg = x < 0
    val a = if (neg) -x else x
    val r = (a / qty) * keep + ((a % qty) * keep + qty / 2) / qty
    return checkExact(if (neg) -r else r, "نسبت")
  }

  /**
   *  part ÷ whole به ده‌هزارم، گردِ نیمه به بالا (برای منفی قرینه)؛ مخرجِ صفر یا منفی ⇒ null.
   *  تقسیمِ بلند رقم‌به‌رقم، تا `part × 10000` برای مبلغِ بزرگ سرریز نکند.
   */
  internal fun bp(part: Long, whole: Long): Long? {
    if (whole <= 0L) return null
    checkExact(part, "مبلغ"); checkExact(whole, "مبلغ")
    val neg = part < 0
    var r = if (neg) -part else part
    var q = r / whole
    if (q > MAX_EXACT / FULL_BP) throw ArithmeticException("نسبت بیرون از بازه: $part ÷ $whole")
    r %= whole
    var scale = 1L
    while (scale < FULL_BP) {
      r *= 10
      q = q * 10 + r / whole
      r %= whole
      scale *= 10
    }
    if (r * 2 >= whole) q += 1
    return if (neg) -q else q
  }
}
