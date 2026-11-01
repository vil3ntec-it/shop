package ir.vil3ntec.tohid.ui.v3.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrReports
import ir.vil3ntec.tohid.data.ReportCsv
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type

/** گونهٔ ستون‌های یک برگهٔ `ReportCsv`: عددی، تاریخ، جمع‌پذیر و پهنا (۰ = بقیهٔ جا) */
data class SheetColumns(val numeric: List<Boolean>, val dates: List<Boolean>, val summable: List<Boolean>, val widths: List<Int>)

/** خالص، با آزمون (`SheetModelTest`): همان قاعده‌های جدولِ امروزِ کامپیوتر */
object SheetModel {
  private val ISO = Regex("""^\d{4}-\d{2}-\d{2}$""")
  /** جمع فقط برای ستونِ مبلغ — نه شماره، تعداد یا روز */
  private val NO_SUM = listOf("شماره", "تعداد", "روز", "موجودی", "حداقل", "ردیف", "مقدار", "فی", "قیمت")

  fun columns(sheet: ReportCsv.Sheet): SheetColumns {
    val head = sheet.rows.firstOrNull().orEmpty()
    val body = sheet.rows.drop(1)
    fun cell(r: List<String>, c: Int) = r.getOrNull(c).orEmpty()
    val numeric = head.indices.map { c -> body.isNotEmpty() && body.all { cell(it, c).let { v -> v.isEmpty() || v.toDoubleOrNull() != null } } && body.any { cell(it, c).isNotEmpty() } }
    val dates = head.indices.map { c -> body.isNotEmpty() && body.all { cell(it, c).let { v -> v.isEmpty() || ISO.matches(v) } } }
    val summable = head.mapIndexed { c, t -> numeric[c] && NO_SUM.none { t.contains(it) } }
    val longest = head.indices.map { c -> maxOf(head[c].length, body.maxOfOrNull { cell(it, c).length } ?: 0) }
    val widths = head.indices.map { c ->
      when {
        dates[c] -> 110
        numeric[c] -> 120
        longest[c] <= 14 -> longest[c] * 9 + 30
        else -> 0
      }
    }.let { w -> if (w.isNotEmpty() && w.none { it == 0 }) w.mapIndexed { i, x -> if (i == numeric.indexOfFirst { !it }) 0 else x } else w }
    return SheetColumns(numeric, dates, summable, widths)
  }

  /** ردیفِ جمع وقتی ستونِ مبلغ هست و بیش از یک ردیف */
  fun footer(sheet: ReportCsv.Sheet, cols: SheetColumns, totalLabel: String): List<String>? {
    val body = sheet.rows.drop(1)
    if (cols.summable.none { it } || body.size <= 1) return null
    return cols.summable.indices.map { c ->
      when {
        cols.summable[c] -> money(body.sumOf { it.getOrNull(c)?.toDoubleOrNull() ?: 0.0 })
        c == 0 -> totalLabel
        else -> ""
      }
    }
  }
}

/** جدولِ برگهٔ گزارش روی کامپیوتر — همان فایلی که اکسل و چاپ می‌شود، با توکن‌های VILL3N */
@Composable
internal fun SheetTable(sheet: ReportCsv.Sheet, modifier: Modifier) {
  val c = PhoneV3.colors
  val cols = remember(sheet) { SheetModel.columns(sheet) }
  val head = sheet.rows.firstOrNull().orEmpty()
  val body = sheet.rows.drop(1)
  val footer = SheetModel.footer(sheet, cols, StrReports.total)
  val shape = RoundedCornerShape(12.dp)
  @Composable
  fun RowScope.cell(i: Int, text: String, bold: Boolean = false, muted: Boolean = false) {
    val w = cols.widths.getOrElse(i) { 0 }
    Box(
      (if (w == 0) Modifier.weight(1f) else Modifier.width(w.dp)).fillMaxHeight().padding(horizontal = 10.dp, vertical = 8.dp),
      contentAlignment = if (cols.numeric.getOrElse(i) { false }) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
      Text(text, style = V3Type.callout.copy(fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium), color = if (muted) c.inkMuted else c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
  fun display(i: Int, v: String) = when {
    v.isEmpty() -> v
    cols.dates.getOrElse(i) { false } -> uiDate(v)
    cols.numeric.getOrElse(i) { false } -> money(v.toDouble())
    else -> v
  }
  Column(modifier.clip(shape).background(c.card).border(1.dp, c.hairline, shape)) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.tile)) { head.forEachIndexed { i, t -> cell(i, t, bold = true, muted = true) } }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
    if (body.isEmpty()) {
      Text(StrReports.tableEmpty, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
    }
    LazyColumn(Modifier.weight(1f, fill = false)) {
      items(body.size) { r ->
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (r % 2 == 1) c.tile else c.card)) {
          head.indices.forEach { i -> cell(i, display(i, body[r].getOrNull(i).orEmpty()), bold = cols.summable.getOrElse(i) { false }) }
        }
      }
    }
    footer?.let { f ->
      Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
      Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(c.tile)) { f.forEachIndexed { i, t -> cell(i, t, bold = true) } }
    }
  }
}
