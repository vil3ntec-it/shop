package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه — نوشته‌ها در `StrReports`؛ شماره، سری و تاریخ داده‌اند و همان می‌مانند.

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.DocGapsSheet
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  RPT-17 — زبانهٔ «شماره‌های جاافتاده» در «گزارش‌ها»ی گوشی، همان گزارشِ کامپیوتر (`DocGapsSheet`) و پشتِ همان
 *  تنظیمِ خاموشِ `DocGapsPref`. چهار شمار بالا و هر ردیفِ برگه یک کارت؛ خروجیِ CSV و چاپ همان دکمه‌های بالای صفحه‌اند
 *  (`ReportCsv.of("gaps")`). فقط خواندنی؛ بی بازهٔ تاریخ، مثلِ کامپیوتر.
 */
internal fun gapsSection(scope: LazyListScope, d: ShopData, today: String) = with(scope) {
  val r = DocGapsSheet.report(d)
  //  ردیفِ نخست سرستون است
  val rows = DocGapsSheet.sheet(d, today).rows.drop(1)

  item {
    Text(StrReports.gapsNow, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile(StrReports.kpiMissing, "${r.missing}", if (r.missing > 0) Shop.colors.danger else Shop.colors.success, modifier = Modifier.weight(1f))
      StatTile(StrReports.kpiJumped, "${r.jumped}", Shop.colors.text, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      StatTile(StrReports.kpiDuplicates, "${r.duplicates}", if (r.duplicates > 0) Shop.colors.warning else Shop.colors.text, modifier = Modifier.weight(1f))
      StatTile(StrReports.kpiCancelled, "${r.cancelled}", Shop.colors.muted, modifier = Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
  }

  if (rows.isEmpty()) {
    item { Panel { EmptyNote(StrReports.gapsNone) } }
    return@with
  }

  item {
    Panel {
      rows.forEach { row -> GapRow(row) }
    }
  }
}

/** یک ردیفِ برگه: نوع، سری، از/تا، تعداد، وضعیت، روزِ سندِ پیش و پس (همان ستون‌های `DocGapsSheet.sheet`) */
@androidx.compose.runtime.Composable
private fun GapRow(row: List<String>) {
  fun c(i: Int) = row.getOrNull(i).orEmpty()
  Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
    val span = if (c(2) == c(3)) c(2) else "${c(2)} – ${c(3)}"
    Text(listOf(c(0), c(1), span).filter { it.isNotBlank() }.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = Shop.colors.text)
    val status = listOf(c(5), c(4).takeIf { it.isNotBlank() }?.let { "${StrReports.colCount}: $it" }.orEmpty()).filter { it.isNotBlank() }
    if (status.isNotEmpty()) Text(status.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Shop.colors.warning)
    val dates = listOf(c(6), c(7)).filter { it.isNotBlank() }
    if (dates.isNotEmpty()) Text(dates.joinToString(" … "), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted)
  }
}
