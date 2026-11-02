package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.report.SalesHeatmap
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  کارت‌ها و نقشهٔ رنگیِ زبانهٔ «ساعت و روز» (RPT-08) — فقط نمایش؛ عددها از `HeatmapModel` و `SalesHeatmap`.
 *  شدتِ رنگِ هر خانه همان `level` ِ هسته است (۰ تا ۴، عددِ صحیح)؛ خانهٔ بی فروش رنگِ مرز را دارد.
 */
@Composable
internal fun HeatmapSummary(h: SalesHeatmap) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      StatCard("پرفروش‌ترین ساعت", HeatmapModel.peakLabel(h) ?: "—", h.peak?.let { "${h.maxCount} فروش" } ?: "بی فروش", Modifier.weight(1f))
      StatCard("شمارِ فروش", h.total.count.toString(), "در این بازه", Modifier.weight(1f))
      StatCard("خالصِ مبلغ", "${money(h.total.totalMinor / 100.0)} ؋", "فروش − برگشت", Modifier.weight(1f))
    }
    HeatGrid(h)
  }
}

@Composable
private fun HeatGrid(h: SalesHeatmap) {
  val levels = 4
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.width(64.dp))
      for (hour in 0 until SalesHeatmap.HOURS) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) {
          if (hour % 3 == 0) Text(hour.toString(), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
        }
      }
    }
    h.weekdays.forEachIndexed { r, wd ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(HeatmapModel.dayName(wd), Modifier.width(64.dp), style = MaterialTheme.typography.labelSmall, color = Shop.colors.text)
        for (hour in 0 until SalesHeatmap.HOURS) {
          val lv = h.level(r, hour, levels)
          val tint = if (lv == 0) Shop.colors.border else Shop.colors.primary.copy(alpha = 0.2f + 0.8f * lv / levels)
          Box(Modifier.width(18.dp).height(16.dp).padding(1.dp).clip(RoundedCornerShape(3.dp)).background(tint))
        }
      }
    }
  }
}
