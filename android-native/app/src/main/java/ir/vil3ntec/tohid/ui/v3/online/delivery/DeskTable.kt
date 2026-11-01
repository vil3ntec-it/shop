package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Type

/*
 *  جدولِ برگهٔ «سپردن» روی کامپیوترِ پهن (online.md: «delivery desk tables keep TableHead») — همان
 *  ستون‌ها و وزن‌های میزِ پیشین، با سطح و نوشته‌های کیت. گوشی همان کارت‌ها را دارد.
 */

internal class DeskCol(val label: () -> String, val weight: Float)

internal val READY_COLS = listOf(
  DeskCol({ StrOnline.dlColOrder }, 0.7f), DeskCol({ StrOnline.dlColCustomer }, 1.5f), DeskCol({ StrOnline.dlColStatus }, 1.1f),
  DeskCol({ StrOnline.dlColLandmark }, 1.6f), DeskCol({ StrOnline.dlColCollect }, 1.1f), DeskCol({ "" }, 1.6f),
)

internal val MISSION_COLS = listOf(
  DeskCol({ StrOnline.dlColOrder }, 0.7f), DeskCol({ StrOnline.dlColCustomer }, 1.3f), DeskCol({ StrOnline.dlColHow }, 1.4f),
  DeskCol({ StrOnline.dlColStatus }, 1.2f), DeskCol({ StrOnline.dlColCollect }, 1.1f), DeskCol({ StrOnline.dlColFare }, 0.9f),
  DeskCol({ "" }, 2.2f),
)

@Composable
internal fun DeskTableHead(cols: List<DeskCol>) {
  val c = PhoneV3.colors
  Column(Modifier.padding(bottom = 6.dp)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
      cols.forEach { Text(it.label(), Modifier.weight(it.weight), style = V3Type.caption, color = c.inkMuted, maxLines = 1) }
    }
    V3Divider()
  }
}

@Composable
internal fun DeskTableRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
  V3Card(modifier.padding(bottom = 8.dp), padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = content)
  }
}

@Composable
internal fun RowScope.DeskCell(text: String, col: DeskCol, strong: Boolean = false, muted: Boolean = false) {
  val c = PhoneV3.colors
  Text(
    text, Modifier.weight(col.weight).padding(end = 8.dp),
    style = if (strong) V3Type.headline else V3Type.callout, color = if (muted) c.inkMuted else c.ink,
    maxLines = 1, overflow = TextOverflow.Ellipsis,
  )
}

/** خانهٔ دلخواه (قرص، دکمه‌ها) با همان وزنِ ستون */
@Composable
internal fun RowScope.DeskSlot(col: DeskCol, end: Boolean = false, content: @Composable () -> Unit) {
  Box(Modifier.weight(col.weight).padding(end = 8.dp), contentAlignment = if (end) Alignment.CenterEnd else Alignment.CenterStart) { content() }
}

/** دکمه‌های کنارِ هم در خانهٔ آخر */
@Composable
internal fun DeskButtons(content: @Composable RowScope.() -> Unit) {
  Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
