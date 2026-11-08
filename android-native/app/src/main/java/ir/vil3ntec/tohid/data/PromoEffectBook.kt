package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.EffectLine
import ir.vil3ntec.tohid.core.promo.PromoEffect
import ir.vil3ntec.tohid.core.promo.PromoEffectReport
import ir.vil3ntec.tohid.core.promo.PromoTag
import ir.vil3ntec.tohid.core.report.ReportClock
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.core.report.ReportLedger

/**
 *  PRM-16 — دفترِ برنامه ⇐ اثرِ هر تخفیفِ خودکار (`core/promo/PromoEffect`)، مشترکِ گوشی و کامپیوتر.
 *
 *  • قلم‌ها: هر قلمِ فاکتورِ **لغونشدهٔ** بازه (روزِ فاکتور همان قاعدهٔ `ReportCore`: با ساعت از `createdAt`، وگرنه
 *    `date`)، با برچسب‌های `SaleItem.promo` ِ همان قلم؛ مقدار و مبلغ همان تبدیلِ `ReportLedger` (هزارم و واحدِ کوچک).
 *  • برگشتی: جمعِ برگشتی‌های همان قلم (هر روزی)، و `PromoEffect` آن را به نسبت کم می‌کند.
 *  • بها: همان `ReportCore.costs` با روشِ صفحه (`CostPolicy`؛ پیش‌فرض و مصوب میانگینِ موزون) — پس سودِ تخفیف با سودِ
 *    «سود و حاشیه» یکی است. بهای تخمینیِ صفر (هیچ پایهٔ معلومی) ⇒ نامعلوم (`null`)، تخمینیِ دیگر ⇒ `costEstimated`.
 *  هیچ عددی این‌جا حساب نمی‌شود. آزمون: بردارِ F ِ `core/test-vectors/cost-v1.json` (`CostVectorsTest`) و `PromoFreshTest`.
 */
object PromoEffectBook {

  /** دفترِ برنامه یک‌بار به هستهٔ گزارش ([ReportBridge.ledgerOf])؛ [costing] و [approved] از `CostPolicy` ِ صفحه */
  fun of(
    d: ShopData, l: ReportLedger, from: String, to: String, costing: ReportCore.Costing, approved: Boolean, clock: ReportClock? = null,
  ): PromoEffectReport {
    val costs = ReportCore.costs(l, costing, approved)
    val returned = HashMap<String, Long>()
    for (r in l.returns) {
      if (l.saleById[r.saleId]?.cancelled == true) continue
      returned[r.saleItemId] = (returned[r.saleItemId] ?: 0L) + r.qty
    }
    val lines = ArrayList<EffectLine>()
    //  `ReportLedger.items` همان ترتیبِ `saleItems` ِ دفتر است (یک ردیف برای هر قلم)
    d.saleItems.forEachIndexed { i, si ->
      val li = l.items.getOrNull(i) ?: return@forEachIndexed
      val sale = l.saleById[li.saleId] ?: return@forEachIndexed
      if (sale.cancelled) return@forEachIndexed
      val day = if (clock != null && sale.createdAt > 0) clock.date(sale.createdAt) else sale.date
      if (day < from || day > to) return@forEachIndexed
      val cost = costs.line[li.id]
      val est = li.id in costs.est
      lines += EffectLine(
        saleId = li.saleId, qtyMilli = li.qty, returnedMilli = returned[li.id] ?: 0L, netMinor = li.total,
        costMinor = if (cost == null || (est && cost == 0L)) null else cost,
        promos = si.promo.map { PromoTag(it.id, it.name, it.discountMinor) },
        costEstimated = est && cost != null && cost != 0L,
      )
    }
    return PromoEffect.of(lines, approved)
  }

  /** آیا دفتر هیچ قلمِ تخفیفِ خودکار دارد؟ (زبانهٔ «اثرِ تخفیف» فقط آن‌گاه) */
  fun has(d: ShopData): Boolean = d.saleItems.any { si -> si.promo.any { it.id.isNotEmpty() } }
}
