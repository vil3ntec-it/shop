package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayout
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Row as NavRow
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.v3Pressable
import kotlin.math.roundToInt

private val ROW_H = 52.dp

/**
 *  ستونِ پیوستهٔ ویرایشگرِ نوار: سرخطِ هر بخش و ردیف‌های هم‌قد. با دستگیره کشیده
 *  می‌شود؛ ردیف‌های میانه با فنر کنار می‌روند تا جای افتادن پیدا باشد، و رها کردن
 *  `NavLayouts.move` را صدا می‌زند. زدنِ ردیف ⇐ برگهٔ «جای X» (`onTap`).
 */
@Composable
internal fun NavSlotList(layout: NavLayout, rows: List<NavRow>, allowed: Set<String>, onEdit: (NavLayouts.Edit) -> Unit, onTap: (String) -> Unit) {
  val c = PhoneV3.colors
  val rowPx = with(LocalDensity.current) { ROW_H.toPx() }
  var dragging by remember { mutableIntStateOf(-1) }
  var dragY by remember { mutableFloatStateOf(0f) }
  val target = if (dragging < 0) -1 else (dragging + (dragY / rowPx).roundToInt()).coerceIn(1, rows.lastIndex)
  Column {
    rows.forEachIndexed { i, row ->
      key(row) {
        val index by rememberUpdatedState(i)
        val latestRows by rememberUpdatedState(rows)
        val latestLayout by rememberUpdatedState(layout)
        val shift = when {
          dragging < 0 || i == dragging -> 0f
          dragging < i && i <= target -> -rowPx
          target <= i && i < dragging -> rowPx
          else -> 0f
        }
        val animShift by animateFloatAsState(shift, MotionSpec.indicator(), label = "rowShift")
        val lifted = i == dragging
        Box(
          Modifier.fillMaxWidth().height(ROW_H).zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = if (lifted) dragY else animShift },
        ) {
          when (row) {
            is NavRow.Header -> Text(
              slotTitle(row.slot), color = c.inkMuted, style = V3Type.subhead,
              modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 8.dp),
            )
            is NavRow.Item -> {
              val top = rows.getOrNull(i - 1) is NavRow.Header || lifted
              val bottom = rows.getOrNull(i + 1).let { it == null || it is NavRow.Header } || lifted
              val r = PhoneV3.groupRadius
              val shape = RoundedCornerShape(
                topStart = if (top) r else 0.dp, topEnd = if (top) r else 0.dp,
                bottomStart = if (bottom) r else 0.dp, bottomEnd = if (bottom) r else 0.dp,
              )
              SlotRow(
                id = row.id,
                modifier = Modifier.then(if (lifted) Modifier.shadow(12.dp, shape, clip = false) else Modifier).clip(shape).background(c.card),
                divider = !(rows.getOrNull(i - 1) is NavRow.Header) && !lifted,
                handle = Modifier.pointerInput(row) {
                  detectDragGestures(
                    onDragStart = { dragging = index; dragY = 0f },
                    onDrag = { change, amount -> change.consume(); dragY += amount.y },
                    onDragEnd = {
                      val to = (index + (dragY / rowPx).roundToInt()).coerceIn(1, latestRows.lastIndex)
                      if (to != index) onEdit(NavLayouts.move(latestLayout, latestRows, index, to, allowed))
                      dragging = -1; dragY = 0f
                    },
                    onDragCancel = { dragging = -1; dragY = 0f },
                  )
                },
                onTap = { onTap(row.id) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SlotRow(id: String, modifier: Modifier, divider: Boolean, handle: Modifier, onTap: () -> Unit) {
  val c = PhoneV3.colors
  val def = NavCatalog.of(id) ?: return
  Box(modifier.fillMaxSize()) {
    if (divider) Box(Modifier.align(Alignment.TopStart).padding(start = 52.dp).fillMaxWidth().height(1.dp).background(c.hairline))
    Row(
      Modifier.fillMaxSize().v3Pressable(label = StrSettings.placeOf(def.label), onClick = onTap).padding(start = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(PhoneNav.icon(id, false), contentDescription = null, tint = c.icon, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
      Text(def.label, color = c.ink, style = V3Type.body, modifier = Modifier.weight(1f))
      if (id == NavCatalog.MORE) Icon(Icons.Outlined.Lock, contentDescription = StrSettings.alwaysInPill, tint = c.chevron, modifier = Modifier.size(16.dp))
      Box(Modifier.size(ROW_H).then(handle), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.DragIndicator, contentDescription = StrSettings.drag, tint = c.chevron, modifier = Modifier.size(22.dp))
      }
    }
  }
}
