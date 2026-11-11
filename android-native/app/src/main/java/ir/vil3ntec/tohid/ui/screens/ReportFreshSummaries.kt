package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.cost.CostMethod
import ir.vil3ntec.tohid.core.i18n.StrReportPages
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.core.i18n.StrStockPages
import ir.vil3ntec.tohid.core.report.ReportCore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.SegmentTab
import ir.vil3ntec.tohid.ui.components.common.SegmentedTabs
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  سرِ زبانه‌های «اثرِ تخفیف» و «بهای میانگین» ِ «گزارشِ تازه» ِ کامپیوتر (`DeskFreshReports`) — بی تغییر از `ReportFresh.kt`
 *  جدا شد تا آن پرونده زیرِ سقفِ اندازه بماند.
 */

/** PRM-16 — سرِ زبانهٔ «اثرِ تخفیف»: روشِ بها (همان انتخابِ «سود و حاشیه»)، قطعی/تقریبی و قاعده */
@Composable
internal fun PromoSummary(v: PromoFresh.View) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    v.report?.let { r ->
      val p = if (v.from == v.to) formatDate(v.from) else if (v.from == "0000-01-01") StrReports.allDates else StrSimpleAsk.range(formatDate(v.from), formatDate(v.to))
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(StrReportPages.colPromoDiscount, "${money(r.promoDiscountMinor / 100.0)} ؋", p, Modifier.weight(1f))
        StatCard(StrReportPages.colNetSales, "${money(r.promoRevenueMinor / 100.0)} ؋", p, Modifier.weight(1f))
        StatCard(StrReportPages.colShare, ReportCore.bp(r.promoRevenueMinor, r.totalRevenueMinor)?.let(ReportFreshModel::percent) ?: "—", p, Modifier.weight(1f))
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(StrReportPages.costMethod, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
      SegmentedTabs(CostMethod.entries.map { SegmentTab(it, it.label) }, v.method, { ReportFresh.setCost(it) })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      val r = v.report
      if (r != null && r.rows.isNotEmpty()) {
        if (r.profitApproximate) StatusPill(StrReportPages.profitApprox, PillTone.WARNING) else StatusPill(StrReportPages.profitFinal, PillTone.SUCCESS)
        Spacer(Modifier.width(8.dp))
      }
      Text(PromoFresh.note(v), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
  }
}

/** PRD-15 — سرِ زبانهٔ «بهای میانگین»: ارزشِ موجودی به بهای میانگین و قاعده (فقط خواندنی) */
@Composable
internal fun StockCostSummary(c: ReportCore.StockCost) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatCard(StrStockPages.stockValueAvg, "${money(c.totals.valueMinor / 100.0)} ؋", if (c.totals.approximate) StrReportPages.colApprox else StrReportPages.untilToday, Modifier.weight(1f))
      StatCard(StrReportPages.colProduct, c.rows.count { it.qtyMilli > 0 }.toString(), StrReportPages.untilToday, Modifier.weight(1f))
    }
    Text(StockCostFresh.note(c), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}
