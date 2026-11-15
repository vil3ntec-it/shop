package ir.vil3ntec.tohid.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/*
 *  جدولِ کامپیوتر — سرِ جدول و ردیفِ ۴۴dp با زیرِ ماوس، زدن و خطِ مویی. فهرستِ بلند ردیف‌ها را در `LazyColumn` ِ خودش
 *  می‌گذارد (`items { DeskTableRow(...) }`)؛ سایه روی ردیف نه.
 */

/** پهنا/وزن و تراز یک ستونِ جدولِ کامپیوتر (`end` ⇒ عدد، ترازِ پایان) */
@Immutable
data class DeskColumn(val title: String, val weight: Float = 1f, val width: Dp? = null, val end: Boolean = false)

/** سرِ جدول: زمینهٔ [DeskColors.surfaceAlt]، نوشتهٔ ۱۳ نیم‌پررنگ */
@Composable
fun DeskTableHead(columns: List<DeskColumn>, modifier: Modifier = Modifier) {
  val c = Desk.colors
  Row(
    modifier.fillMaxWidth().height(DeskSize.tableHead).clip(DeskRadius.controlShape).background(c.surfaceAlt).padding(horizontal = DeskSpace.x3),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
  ) {
    columns.forEach { col ->
      Box(cellOf(col), contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(col.title, style = DeskType.tableHead, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

/** یک ردیفِ جدول (۴۴dp): زیرِ ماوس روشن، زدنی، خطِ مویی زیر؛ [cell] خانهٔ ستونِ i را می‌کشد */
@Composable
fun DeskTableRow(columns: List<DeskColumn>, label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier, cell: @Composable (Int) -> Unit) {
  val c = Desk.colors
  Column(modifier) {
    Row(
      Modifier
        .fillMaxWidth()
        .heightIn(min = DeskSize.tableRow)
        .then(if (onClick != null) Modifier.deskClickable(label = label, onClick = onClick) else Modifier)
        .padding(horizontal = DeskSpace.x3),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(DeskSpace.x3),
    ) {
      columns.forEachIndexed { i, col ->
        Box(cellOf(col), contentAlignment = if (col.end) Alignment.CenterEnd else Alignment.CenterStart) { cell(i) }
      }
    }
    DeskDivider(Modifier.padding(horizontal = DeskSpace.x3))
  }
}

/** پهنای ثابت یا سهمِ وزنیِ ستون */
private fun RowScope.cellOf(col: DeskColumn): Modifier = if (col.width != null) Modifier.width(col.width) else Modifier.weight(col.weight)
