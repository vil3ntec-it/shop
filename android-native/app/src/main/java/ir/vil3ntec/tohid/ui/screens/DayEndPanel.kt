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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

private const val NOTE =
  "هر شیفت مالِ روزی است که باز شد؛ Z ِ بسته ثابت است و پولی که هیچ شیفتی نشمرده جدا، در «بیرون از شیفت‌ها»، می‌آید."

@Composable
private fun StatusLine(b: DayEndModel.Built) {
  val r = b.report
  Row(verticalAlignment = Alignment.CenterVertically) {
    if (r == null) StatusPill(b.error ?: "گزارش ساخته نشد", PillTone.DANGER)
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
        StatCard("خالص شیفت‌ها", af(r.total.shifts.net), "فروش + رسید − برگشت", Modifier.weight(1f))
        StatCard("اختلاف صندوق", r.total.variance?.let(::af) ?: "—", "شمرده − مورد انتظار", Modifier.weight(1f))
        StatCard("بیرون از شیفت‌ها", af(r.total.outside), "دفتر − شیفت‌ها", Modifier.weight(1f))
        StatCard(
          "شیفت‌ها", r.shifts.size.toString(),
          if (r.openShifts > 0) "${r.openShifts} باز" else "همه بسته", Modifier.weight(1f),
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
    Text("پایان امروز", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Shop.colors.text)
    StatusLine(b)
    if (r != null) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("خالص شیفت‌ها", af(r.total.shifts.net), "امروز", Modifier.weight(1f))
        StatCard("اختلاف صندوق", r.total.variance?.let(::af) ?: "—", "شمرده − مورد انتظار", Modifier.weight(1f))
      }
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatCard("بیرون از شیفت‌ها", af(r.total.outside), "دفتر − شیفت‌ها", Modifier.weight(1f))
        StatCard(
          "شیفت‌ها", r.shifts.size.toString(),
          if (r.openShifts > 0) "${r.openShifts} باز" else "همه بسته", Modifier.weight(1f),
        )
      }
    }
    Text(NOTE, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
  }
}
