package ir.vil3ntec.tohid.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayout
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Row as NavRow
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Slot
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import kotlin.math.roundToInt

/**
 *  ویرایشگرِ نوارِ پایین — phone v3 §F2 (مسیر: `PhoneNav.EDITOR` = "nav-editor").
 *
 *  بالا پیش‌نمایشِ زندهٔ همان نوار، پایین یک ستون از چهار بخش: «دایرهٔ شروع»،
 *  «قرص»، «دایرهٔ پایان» و «بیرون از نوار». هر ردیف را با دستگیره‌اش بکشید (یا
 *  رویش بزنید و جا را انتخاب کنید)؛ قاعده‌ها همان `NavLayouts` است و پیامِ ردشدن
 *  همان‌جا دیده می‌شود. هر تغییر فوری ذخیره می‌شود (برای همین کاربر، روی همین
 *  دستگاه) و پیش‌نمایش با انیمیشن عوض می‌شود.
 */
@Composable
fun NavEditorScreen(allowed: Set<String>, onBack: () -> Unit) {
  val context = LocalContext.current
  val p = PhoneV3.colors
  val layout = NavLayouts.normalize(PhoneNav.layout.value, allowed)
  var problem by remember { mutableStateOf<NavLayouts.Problem?>(null) }
  LaunchedEffect(problem) { if (problem != null) { kotlinx.coroutines.delay(2800); problem = null } }
  fun applyEdit(e: NavLayouts.Edit) {
    when (e) {
      is NavLayouts.Edit.Ok -> { problem = null; PhoneNav.save(context, e.layout) }
      is NavLayouts.Edit.Rejected -> problem = e.problem
    }
  }
  val rows = NavLayouts.rows(layout, allowed)
  val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

  Column(Modifier.fillMaxSize().background(p.bg)) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
      RoundButton(Icons.Outlined.ChevronRight, "بازگشت", onBack)
      Text(
        "نوارِ پایین", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = p.ink,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
      )
      Spacer(Modifier.width(44.dp))
    }
    //  پیش‌نمایشِ زنده
    Box(
      Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(116.dp)
        .clip(RoundedCornerShape(PhoneV3.cardRadius)).background(p.tile),
      contentAlignment = Alignment.Center,
    ) {
      PhoneNavBar(layout, current = "dashboard", onPick = {}, onEdit = null, systemInsets = false)
    }
    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
      androidx.compose.animation.Crossfade(targetState = problem, label = "navProblem") { pr ->
        Text(
          pr?.message ?: "ردیف را با دستگیره بکشید، یا رویش بزنید و جا را انتخاب کنید",
          color = if (pr != null) p.negative else p.inkMuted,
          textAlign = TextAlign.Center,
          style = if (pr != null) MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold)
          else MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
        )
      }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
      EditorList(layout, rows, allowed, ::applyEdit)
      Spacer(Modifier.height(16.dp))
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PressBox(scaleTo = 0.95f, label = "بازگرداندنِ پیش‌فرض", onClick = { applyEdit(NavLayouts.Edit.Ok(NavLayouts.normalize(NavLayouts.DEFAULT, allowed))) }) {
          Row(
            Modifier.height(46.dp).clip(RoundedCornerShape(percent = 50)).background(p.card)
              .border(1.dp, p.hairline, RoundedCornerShape(percent = 50)).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(Icons.Outlined.Restore, contentDescription = null, tint = p.icon, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("بازگرداندنِ پیش‌فرض", color = p.ink, style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold))
          }
        }
      }
      Spacer(Modifier.height(28.dp + navBottom))
    }
  }
}

private val ROW_H = 52.dp

private fun slotTitle(s: Slot): String = when (s) {
  Slot.START -> "دایرهٔ شروع · یک مورد یا خاموش"
  Slot.PILL -> "قرص · ${NavLayouts.MIN_PILL} تا ${NavLayouts.MAX_PILL} مورد؛ «بیشتر» همیشه هست"
  Slot.END -> "دایرهٔ پایان · یک مورد یا خاموش"
  Slot.OUT -> "بیرون از نوار"
}

/**
 *  یک ستونِ پیوسته با ردیف‌های هم‌قد — کشیدن با دستگیره؛ ردیف‌های میانه کنار
 *  می‌روند تا جای افتادن پیدا باشد، و رها کردن `NavLayouts.move` را صدا می‌زند.
 */
@Composable
private fun EditorList(layout: NavLayout, rows: List<NavRow>, allowed: Set<String>, onEdit: (NavLayouts.Edit) -> Unit) {
  val p = PhoneV3.colors
  val rowPx = with(LocalDensity.current) { ROW_H.toPx() }
  var dragging by remember { mutableStateOf(-1) }
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
        val animShift by animateFloatAsState(shift, if (Motion.enabled) spring(stiffness = 700f) else snap(), label = "rowShift")
        val lifted = i == dragging
        Box(
          Modifier
            .fillMaxWidth()
            .height(ROW_H)
            .zIndex(if (lifted) 1f else 0f)
            .graphicsLayer { translationY = if (lifted) dragY else animShift },
        ) {
          when (row) {
            is NavRow.Header -> Text(
              slotTitle(row.slot), color = p.inkMuted,
              style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
              modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = 8.dp),
            )
            is NavRow.Item -> {
              val prevHeader = rows.getOrNull(i - 1) is NavRow.Header
              val nextHeader = rows.getOrNull(i + 1).let { it == null || it is NavRow.Header }
              val shape = RoundedCornerShape(
                topStart = if (prevHeader || lifted) PhoneV3.groupRadius else 0.dp,
                topEnd = if (prevHeader || lifted) PhoneV3.groupRadius else 0.dp,
                bottomStart = if (nextHeader || lifted) PhoneV3.groupRadius else 0.dp,
                bottomEnd = if (nextHeader || lifted) PhoneV3.groupRadius else 0.dp,
              )
              ItemRow(
                id = row.id,
                slot = NavLayouts.slotOf(layout, row.id),
                modifier = Modifier
                  .then(if (lifted) Modifier.shadow(12.dp, shape, clip = false) else Modifier)
                  .clip(shape)
                  .background(p.card),
                divider = !prevHeader && !lifted,
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
                onPlace = { slot -> onEdit(NavLayouts.place(latestLayout, row.id, slot, null, allowed)) },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ItemRow(
  id: String,
  slot: Slot,
  modifier: Modifier,
  divider: Boolean,
  handle: Modifier,
  onPlace: (Slot) -> Unit,
) {
  val p = PhoneV3.colors
  val def = NavCatalog.of(id) ?: return
  var menu by remember { mutableStateOf(false) }
  Box(modifier.fillMaxSize()) {
    if (divider) Box(Modifier.align(Alignment.TopStart).padding(start = 52.dp).fillMaxWidth().height(1.dp).background(p.hairline))
    Row(
      Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "جای «${def.label}»") { menu = true }.padding(start = 16.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(PhoneNav.icon(id, false), contentDescription = null, tint = p.icon, modifier = Modifier.size(22.dp))
      Spacer(Modifier.width(14.dp))
      Text(def.label, color = p.ink, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp))
      if (id == NavCatalog.MORE) Icon(Icons.Outlined.Lock, contentDescription = "همیشه در قرص", tint = p.chevron, modifier = Modifier.size(16.dp))
      Box(Modifier.size(ROW_H).then(handle), contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.DragIndicator, contentDescription = "کشیدن", tint = p.chevron, modifier = Modifier.size(22.dp))
      }
    }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      Slot.entries.filter { it != slot }.forEach { s ->
        DropdownMenuItem(
          text = { Text(when (s) { Slot.START -> "به دایرهٔ شروع"; Slot.PILL -> "به قرص"; Slot.END -> "به دایرهٔ پایان"; Slot.OUT -> "بیرون از نوار" }) },
          onClick = { menu = false; onPlace(s) },
        )
      }
    }
  }
}
