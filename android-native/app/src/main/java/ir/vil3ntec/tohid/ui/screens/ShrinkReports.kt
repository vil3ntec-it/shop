package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShrink
import ir.vil3ntec.tohid.core.stock.ShrinkReport
import ir.vil3ntec.tohid.data.ReportEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShrinkLoss
import ir.vil3ntec.tohid.displayDate
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  گزارشِ ضایعات و کسری (INV-17) — بخشِ «گزارش‌ها»ی گوشی و کلیدِ «تنظیمات».
 *
 *  // i18n: سه‌زبانه — نوشته‌ها در `StrShrink` (LOC-06)؛ نامِ کالا و دلیلِ هر اصلاح داده‌اند و همان می‌مانند.
 *
 *  حساب در `core/stock/ShrinkReport` است (`ShrinkLoss` دفتر را به آن می‌دهد)؛ این‌جا فقط نشان داده می‌شود.
 *  پیش‌فرض خاموش (`ShrinkReportPref`)؛ فقط برای کسی که قیمتِ خرید را می‌بیند (`ShopRole.canSeeMoney`).
 */

/** نامِ هر نوع روی صفحه */
internal fun shrinkKindLabel(k: ShrinkReport.Kind): String = when (k) {
  ShrinkReport.Kind.WASTE -> StrShrink.waste
  ShrinkReport.Kind.EXPIRED -> StrShrink.expired
  ShrinkReport.Kind.LOST -> StrShrink.lost
  ShrinkReport.Kind.SHORTAGE -> StrShrink.shortage
  ShrinkReport.Kind.SURPLUS -> StrShrink.surplus
  ShrinkReport.Kind.OTHER -> StrShrink.other
}

/** مبلغ از پول به افغانیِ گرد — همان `money` ِ صفحه‌های دیگر */
internal fun shrinkMoney(minor: Long): String = "${money(minor / 100.0)} ؋"

/** ده‌هزارم ⇒ درصد با یک رقمِ اعشار (۴۵۰ ⇒ ۴٫۵) */
internal fun shrinkPercent(bp: Long): String {
  val tenths = Math.round(bp / 10.0)
  val a = kotlin.math.abs(tenths)
  val s = "${a / 10}.${a % 10}"
  return if (tenths < 0) "-$s" else s
}

internal fun shrinkSection(
  scope: LazyListScope,
  d: ShopData,
  range: ReportEngine.Range,
  from: String,
  to: String,
  onRange: (ReportEngine.Range) -> Unit,
) = with(scope) {
  val r = ShrinkLoss.report(d, from, to)
  val names = d.products.associate { it.id to it.name }
  val units = d.products.associate { it.id to it.unit }

  item {
    Row(
      Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      listOf(
        ReportEngine.Range.TODAY to StrShrink.today,
        ReportEngine.Range.WEEK to StrShrink.week,
        ReportEngine.Range.MONTH to StrShrink.month,
        ReportEngine.Range.ALL to StrShrink.all,
      ).forEach { (value, label) ->
        FilterChip(selected = range == value, onClick = { onRange(value) }, label = { Text(label) })
      }
    }
    if (range != ReportEngine.Range.ALL) {
      Spacer(Modifier.height(6.dp))
      Text(StrShrink.span(displayDate(from), displayDate(to)), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile(StrShrink.wasteTotal, shrinkMoney(r.wasteMinor), Shop.colors.warning, modifier = Modifier.weight(1f))
      StatTile(StrShrink.shrinkTotal, shrinkMoney(r.shrinkMinor), if (r.shrinkMinor > 0) Shop.colors.danger else Shop.colors.success, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    StatTile(
      StrShrink.netLoss,
      shrinkMoney(r.netLossMinor),
      if (r.netLossMinor > 0) Shop.colors.danger else Shop.colors.success,
      hint = r.rateBp?.let { StrShrink.ofSales(shrinkPercent(it)) } ?: StrShrink.noSales,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (r.unpricedRows > 0) {
      Text(StrShrink.unpriced(r.unpricedRows), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
      Spacer(Modifier.height(4.dp))
    }
    Text(StrShrink.basis, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(12.dp))
  }

  if (r.isEmpty) {
    item { Panel { EmptyNote(StrShrink.empty) } }
    return@with
  }

  item {
    Panel {
      Text(StrShrink.byKind, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(8.dp))
      r.kinds.forEach { k ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(shrinkKindLabel(k.kind), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
            Text(StrShrink.rows(k.rows), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          Text(
            shrinkMoney(k.netLossMinor),
            style = MaterialTheme.typography.bodyMedium,
            color = if (k.netLossMinor > 0) Shop.colors.danger else Shop.colors.success,
          )
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  if (r.reasons.isNotEmpty()) item {
    Panel {
      Text(StrShrink.topReasons, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
      Spacer(Modifier.height(8.dp))
      r.reasons.forEach { why ->
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(why.text.ifBlank { StrShrink.noReason }, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 2)
            Text("${shrinkKindLabel(why.kind)} · ${StrShrink.rows(why.rows)}", style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
          }
          Text(shrinkMoney(why.lossMinor), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.danger)
        }
      }
    }
    Spacer(Modifier.height(12.dp))
  }

  item {
    Text(StrShrink.byProduct, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
    Spacer(Modifier.height(8.dp))
  }
  items(r.products, key = { it.productId }) { p ->
    Row(
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(Radius.sm))
        .background(Shop.colors.surface)
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(Modifier.weight(1f)) {
        Text(names[p.productId] ?: StrShrink.deletedProduct, style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text, maxLines = 1)
        Text(
          p.kinds.joinToString("، ") { shrinkKindLabel(it) } + " · " +
            StrShrink.outIn(ShrinkLoss.qtyText(p.outMilli), ShrinkLoss.qtyText(p.inMilli), units[p.productId].orEmpty()),
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
      }
      Text(
        if (p.priced) shrinkMoney(p.netLossMinor) else "—",
        style = MaterialTheme.typography.titleSmall,
        color = if (p.netLossMinor > 0) Shop.colors.danger else Shop.colors.success,
      )
    }
    Spacer(Modifier.height(6.dp))
  }
}

/** بخشِ «تنظیمات» — خواندن و نوشتنِ کلید در مرزِ پلتفرم است (`ShrinkReportSettingsWired`) */
@Composable
fun ShrinkReportSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.TableChart,
    title = StrShrink.settingsTitle,
    subtitle = if (on) StrShrink.settingsOn else StrShrink.off,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrShrink.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShrink.off, "on" to StrShrink.on),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
