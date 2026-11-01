package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.data.CustomerInsight
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.Radius
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  گزارش‌های مشتری در «گزارش‌ها»ی گوشی — سنِ بدهی (CRM-04) و تحلیلِ دفعات و مبلغِ خرید، RFM (CRM-14).
 *
 *  همان حسابِ زبانه‌های کامپیوتر (`CustomerInsight.aging` روی `ReportCore.debts`، و `CustomerInsight.rfm`)،
 *  همان خروجیِ اکسل و چاپ (`ReportCsv.of("aging"|"rfm")`)؛ این‌جا فقط کارتِ هر مشتری. پشتِ همان تنظیمِ
 *  خاموشِ «گزارش‌های مشتری» (`ShopPolicy.crmReports`)؛ RFM مبلغِ فروش دارد، پس فقط با `canSeeMoney`.
 */

/** زبانه‌های مشتریِ گوشی؛ تنظیم خاموش ⇒ هیچ */
internal fun crmTabs(crmOn: Boolean, canSeeMoney: Boolean): Map<String, String> = when {
  !crmOn -> emptyMap()
  canSeeMoney -> linkedMapOf("aging" to "سنِ بدهی", "rfm" to "تحلیلِ خرید (RFM)")
  else -> linkedMapOf("aging" to "سنِ بدهی")
}

private val cardShape = RoundedCornerShape(Radius.md)

internal fun agingSection(scope: LazyListScope, d: ShopData, today: String) = with(scope) {
  val a = CustomerInsight.aging(d, today)
  item {
    StatTile("ماندهٔ کل", "${money(a.balance)} ؋", Shop.colors.warning, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CustomerInsight.BUCKET_LABELS.zip(a.totals).forEach { (label, v) ->
        StatTile(label, money(v), if (label == CustomerInsight.BUCKET_LABELS.last() && v > 0.5) Shop.colors.danger else Shop.colors.text, modifier = Modifier.weight(1f))
      }
    }
    Spacer(Modifier.height(12.dp))
  }
  val open = a.lines.filter { it.balance > 0.5 || it.credit > 0.5 }
  if (open.isEmpty()) {
    item { Panel { EmptyNote("بدهیِ بازی نیست") } }
  } else {
    items(open, key = { it.id }) { l ->
      Column(
        Modifier.fillMaxWidth().clip(cardShape).background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, cardShape).padding(14.dp),
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(l.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          Text(
            if (l.balance > 0.5) "${money(l.balance)} ؋" else "${money(l.credit)} ؋ پیش‌پرداخت",
            style = MaterialTheme.typography.titleSmall,
            color = if (l.balance > 0.5) Shop.colors.warning else Shop.colors.success,
          )
        }
        Spacer(Modifier.height(4.dp))
        Text(
          CustomerInsight.BUCKET_LABELS.zip(l.buckets).filter { it.second > 0.5 }.joinToString(" · ") { (k, v) -> "$k: ${money(v)}" }
            .ifEmpty { "بی بردِ باز" } +
            (l.oldestDays?.takeIf { it >= 0 }?.let { " — کهنه‌ترین $it روز" } ?: ""),
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}

internal fun rfmSection(scope: LazyListScope, d: ShopData, today: String) = with(scope) {
  val rows = CustomerInsight.rfm(d, today)
  item {
    Text("مشتریانِ خریدارِ یک سالِ گذشته: امتیازِ ۱ تا ۵ برای تازگی (R)، دفعات (F) و مبلغ (M).",
      style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile("خریدار", "${rows.size}", Shop.colors.primary, modifier = Modifier.weight(1f))
      CustomerInsight.segmentCounts(rows).take(3).forEach { (s, n) -> StatTile(s.label, "$n", Shop.colors.text, modifier = Modifier.weight(1f)) }
    }
    Spacer(Modifier.height(12.dp))
  }
  if (rows.isEmpty()) {
    item { Panel { EmptyNote("در یک سالِ گذشته خریدی به نامِ مشتری ثبت نشده") } }
  } else {
    items(rows, key = { it.id }) { x ->
      Column(
        Modifier.fillMaxWidth().clip(cardShape).background(Shop.colors.surface)
          .border(1.dp, Shop.colors.border, cardShape).padding(14.dp),
      ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Text(x.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          Text("${x.segment.label} · ${x.code}", style = MaterialTheme.typography.labelMedium, color = Shop.colors.primary)
        }
        Spacer(Modifier.height(4.dp))
        Text(
          "${x.frequency} خرید · ${money(x.monetary)} ؋ · آخرین خرید ${x.recencyDays} روز پیش",
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted,
        )
      }
      Spacer(Modifier.height(8.dp))
    }
  }
}
