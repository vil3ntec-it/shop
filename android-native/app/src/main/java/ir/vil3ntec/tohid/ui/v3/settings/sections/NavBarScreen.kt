package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.components.common.NavCatalog
import ir.vil3ntec.tohid.ui.components.common.NavLayouts
import ir.vil3ntec.tohid.ui.components.common.NavLayouts.Slot
import ir.vil3ntec.tohid.ui.components.common.PhoneNav
import ir.vil3ntec.tohid.ui.components.common.PhoneNavBar
import ir.vil3ntec.tohid.ui.screens.Haptics
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Scaffold
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  ویرایشگرِ نوارِ پایین (`settings/general/navbar`، r10؛ نگه‌داشتنِ نوار هم همین را
 *  باز می‌کند): پیش‌نمایشِ زندهٔ نوار، چهار بخشِ «دایرهٔ شروع / قرص / دایرهٔ پایان /
 *  بیرون از نوار» با دستگیرهٔ کشیدن، زدنِ ردیف ⇐ برگهٔ «جای X»، و «بازگرداندنِ
 *  پیش‌فرض». قاعده‌ها همان `NavLayouts` است؛ هر تغییر فوری ذخیره می‌شود.
 */
@Composable
internal fun NavBarScreen(allowed: Set<String>, onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  val layout = NavLayouts.normalize(PhoneNav.layout.value, allowed)
  val rows = NavLayouts.rows(layout, allowed)
  var problem by remember { mutableStateOf<NavLayouts.Problem?>(null) }
  var placing by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(problem) { if (problem != null) { kotlinx.coroutines.delay(2800); problem = null } }
  fun apply(e: NavLayouts.Edit) {
    when (e) {
      is NavLayouts.Edit.Ok -> { problem = null; PhoneNav.save(context, e.layout); Haptics.confirm(context) }
      is NavLayouts.Edit.Rejected -> problem = e.problem
    }
  }

  placing?.let { id ->
    PlaceSheet(id, NavLayouts.slotOf(layout, id), onPick = { apply(NavLayouts.place(layout, id, it, null, allowed)) }, onDismiss = { placing = null })
  }

  V3Scaffold(title = SettingsLayout.labelOf(SettingsLayout.NAV_BAR), onBack = onBack) { pad ->
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
      ) {
        //  پیش‌نمایشِ زنده (نوارِ واقعی، بی کار)
        Box(
          Modifier.padding(top = 8.dp).fillMaxWidth().height(116.dp)
            .clip(RoundedCornerShape(PhoneV3.groupRadius)).background(c.tile)
            .border(1.dp, c.hairline, RoundedCornerShape(PhoneV3.groupRadius)),
          contentAlignment = Alignment.Center,
        ) {
          PhoneNavBar(layout, current = "home", onPick = {}, onEdit = null, systemInsets = false, scrim = false)
        }
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
          Crossfade(targetState = problem, label = "navProblem") { pr ->
            Text(
              pr?.let(::problemText) ?: StrSettings.navHint,
              color = if (pr != null) PhoneV3Roles.errorText(c) else c.inkMuted,
              textAlign = TextAlign.Center,
              style = if (pr != null) V3Type.callout else V3Type.footnote,
            )
          }
        }
        NavSlotList(layout, rows, allowed, onEdit = ::apply, onTap = { placing = it })
        Spacer(Modifier.height(16.dp))
        V3Button(
          StrSettings.restoreDefault,
          { apply(NavLayouts.Edit.Ok(NavLayouts.normalize(NavLayouts.DEFAULT, allowed))) },
          Modifier.align(Alignment.CenterHorizontally),
          kind = V3ButtonKind.NEUTRAL,
          icon = Icons.Outlined.Restore,
        )
        Spacer(Modifier.height(24.dp))
      }
    }
  }
}

internal fun slotTitle(s: Slot): String = when (s) {
  Slot.START -> StrSettings.slotStart
  Slot.PILL -> StrSettings.slotPill(NavLayouts.MIN_PILL, NavLayouts.MAX_PILL)
  Slot.END -> StrSettings.slotEnd
  Slot.OUT -> StrSettings.slotOut
}

private fun slotAction(s: Slot): String = when (s) {
  Slot.START -> StrSettings.toStart
  Slot.PILL -> StrSettings.toPill
  Slot.END -> StrSettings.toEnd
  Slot.OUT -> StrSettings.toOut
}

private fun problemText(p: NavLayouts.Problem): String = when (p) {
  NavLayouts.Problem.MORE_LOCKED -> StrSettings.navMoreLocked
  NavLayouts.Problem.PILL_FULL -> StrSettings.navPillFull(NavLayouts.MAX_PILL)
  NavLayouts.Problem.PILL_MIN -> StrSettings.navPillMin(NavLayouts.MIN_PILL)
  NavLayouts.Problem.NOT_ALLOWED -> StrSettings.navNotAllowed
}

/** «جای X»: به دایرهٔ شروع / به قرص / به دایرهٔ پایان / بیرون (جای فعلی تیک دارد) */
@Composable
private fun PlaceSheet(id: String, current: Slot, onPick: (Slot) -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  val label = NavCatalog.of(id)?.label.orEmpty()
  V3Sheet(onDismiss = onDismiss, title = StrSettings.placeOf(label)) { closer ->
    V3Card {
      Slot.entries.forEachIndexed { i, s ->
        if (i > 0) V3Divider(start = 16.dp)
        V3ListRow(
          slotAction(s),
          tile = false,
          chevron = false,
          trailing = if (s == current) ({ Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
          onClick = { closer.close { if (s != current) onPick(s) } },
        )
      }
    }
  }
}
