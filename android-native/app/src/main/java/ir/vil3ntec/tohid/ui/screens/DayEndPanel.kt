package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.i18n.StrSalePages
import ir.vil3ntec.tohid.core.report.ReportTables
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.components.common.PillTone
import ir.vil3ntec.tohid.ui.components.common.StatCard
import ir.vil3ntec.tohid.ui.components.common.StatusPill
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  «پایانِ روز» (RPT-04) در گزارشِ تازه — فقط نمایش؛ عددها از `DayEndModel`/`DayClose`.
 *  کامپیوتر: کارت‌ها و نشانِ وضعیت بالای دو جدول؛ گوشی: همان برای امروز.
 */

private fun toneOf(t: String) = when (t) {
  "success" -> PillTone.SUCCESS
  "warning" -> PillTone.WARNING
  "danger" -> PillTone.DANGER
  else -> PillTone.NEUTRAL
}

private fun af(v: Long) = "${money(v / 100.0)} ؋"

private val NOTE: String get() = StrSalePages.dayEndNote

@Composable
private fun StatusLine(b: DayEndModel.Built) {
  val r = b.report
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (r == null) StatusPill(b.error ?: StrSale.reportFailed, PillTone.DANGER)
    else {
      val (text, tone) = DayEndModel.status(r)
      StatusPill(text, toneOf(tone))
    }
  }
}

/** کامپیوتر: چهار کارت و وضعیتِ روز */
@Composable
internal fun DayEndSummary(b: DayEndModel.Built) {
  val r = b.report
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (r != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(StrSalePages.shiftsNet, af(r.total.shifts.net), StrSalePages.shiftsNetHow, Modifier.weight(1f))
        StatCard(StrSalePages.tillVariance, r.total.variance?.let(::af) ?: "—", StrSalePages.varianceHow, Modifier.weight(1f))
        StatCard(StrSalePages.outsideShifts, af(r.total.outside), StrSalePages.outsideHow, Modifier.weight(1f))
        StatCard(
          StrSalePages.shifts, r.shifts.size.toString(),
          if (r.openShifts > 0) StrSalePages.openCount(r.openShifts) else StrSalePages.allClosed, Modifier.weight(1f),
        )
      }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      StatusLine(b)
      Spacer(Modifier.width(8.dp))
      Text(NOTE, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
    }
  }
}

/** گوشی: پایانِ امروز، دو ردیفِ کارت و وضعیت */
@Composable
internal fun DayEndPhone(b: DayEndModel.Built) {
  val r = b.report
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(StrSalePages.endOfToday, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    StatusLine(b)
    if (r != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(StrSalePages.shiftsNet, af(r.total.shifts.net), Str.today, Modifier.weight(1f))
        StatCard(StrSalePages.tillVariance, r.total.variance?.let(::af) ?: "—", StrSalePages.varianceHow, Modifier.weight(1f))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard(StrSalePages.outsideShifts, af(r.total.outside), StrSalePages.outsideHow, Modifier.weight(1f))
        StatCard(
          StrSalePages.shifts, r.shifts.size.toString(),
          if (r.openShifts > 0) StrSalePages.openCount(r.openShifts) else StrSalePages.allClosed, Modifier.weight(1f),
        )
      }
    }
    Text(NOTE, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}

/** RPT-04 / S48 — «ریزِ تراکنش‌ها» ی یک روش (کلیک یا Enter روی ردیفِ روش)؛ Esc یا «بستن» برمی‌گردد */
@Composable
internal fun DayEndDetailDialog(
  t: ReportTables.Table,
  /** RPT-13: ریزِ ردیفِ تفکیکِ فروش نوشتهٔ خودش را دارد */
  note: String = StrSalePages.detailNote,
  onClose: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onClose,
    title = { Text(t.title, fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(note, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        FreshTable(t, Modifier.fillMaxWidth().heightIn(max = 420.dp))
      }
    },
    confirmButton = { TextButton(onClick = onClose) { Text(Str.close) } },
  )
}
