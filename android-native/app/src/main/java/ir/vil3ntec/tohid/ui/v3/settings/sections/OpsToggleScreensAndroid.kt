package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrSimpleAsk
import ir.vil3ntec.tohid.core.i18n.StrTraining
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.SimpleAskPref
import ir.vil3ntec.tohid.data.TrainingLedger
import ir.vil3ntec.tohid.data.TrainingMode
import ir.vil3ntec.tohid.data.TrainingPref
import ir.vil3ntec.tohid.print.EasySetupPref
import ir.vil3ntec.tohid.ui.screens.EASY_SETUP_NOTE
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

/*
 *  سه تنظیمِ تازهٔ رابطِ امروز (`SimpleAskSettingsWired`، `EasySetupSettingsWired`، `TrainingSettingsWired` در
 *  `ui/legacy/SettingsLegacy.kt`) — همان کلید، همان پیش‌فرضِ خاموش، همان نوشتن و همان مجوز (`SettingsPortTest`)؛
 *  فقط رویِ کیت. هر سه فقط همین دستگاه.
 */

/** `settings/sales/simple-ask`: پرسشِ ساده از گزارش‌ها (RPT-16) */
@Composable
internal fun RouteScope.SimpleAskScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  ToggleScreen(SettingsLayout.SIMPLE_ASK, onBack, SimpleAskPref.on(context), StrShell.switchOn, StrSimpleAsk.settingsNote, deviceOnly = true) {
    SimpleAskPref.set(context, it)
  }
}

/** `settings/devices/easy-setup`: راه‌اندازیِ آسانِ چاپگر و بارکدخوان (HW-12)؛ کارش در «دستگاه‌ها» */
@Composable
internal fun RouteScope.EasySetupScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val on = remember { EasySetupPref.load(context); EasySetupPref.on.value }
  ToggleScreen(SettingsLayout.EASY_SETUP, onBack, on, StrShell.switchOn, EASY_SETUP_NOTE, deviceOnly = true) { EasySetupPref.set(context, it) }
}

/**
 *  `settings/general/training`: حالتِ آموزشی (OPS-07) — همان `TrainingSettingsWired`: کلیدِ «نشان دادنِ ورود»
 *  (`TrainingPref.setOn`)؛ با روشن، «کارمند هم» که فقط صاحب/مدیر عوض می‌کند (`TrainingMode.canManage`)، و ورود با
 *  همان `TrainingLedger.enter` و همان سدِ نقش (`TrainingMode.canEnter`)، و پاک کردنِ دفترِ آموزشیِ ذخیره‌شده
 *  (`TrainingLedger.wipe`). خروج همان نوارِ `TrainingFrame` است که `AppRoot` ِ نسخهٔ ۳ هم در آموزش می‌کشد
 *  (`TrainingLedger.exit`)؛ در آموزش این صفحه درِ ورود ندارد، مثلِ رابطِ امروز.
 */
@Composable
internal fun RouteScope.TrainingScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val role = remember { ShopRole.current(context) }
  var on by remember { mutableStateOf(TrainingPref.on(context)) }
  var staff by remember { mutableStateOf(TrainingPref.staff(context)) }
  var saved by remember { mutableStateOf(TrainingLedger.hasSaved(context)) }
  val active by TrainingMode.active.collectAsState()
  SettingsPageFrame(
    title = SettingsLayout.labelOf(SettingsLayout.TRAINING),
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    onBack = onBack,
  ) {
    item(key = "choice") {
      SettingsBlock {
        PartTitle(StrTraining.showEntry)
        V3Segmented(SWITCH, if (on) 1 else 0, { on = it == 1; TrainingPref.setOn(context, on) })
      }
      SettingsNote(StrTraining.settingsNote)
      SettingsNote(StrShell.thisDeviceOnly)
    }
    if (on) {
      item(key = "staff") {
        if (TrainingMode.canManage(role)) {
          SettingsBlock {
            PartTitle(StrTraining.staffToo)
            V3Segmented(SWITCH, if (staff) 1 else 0, { staff = it == 1; TrainingPref.setStaff(context, staff) })
          }
        } else {
          SettingsNote(StrTraining.staffToo)
          SettingsNote(StrTraining.staffOnlyOwner)
        }
      }
      if (!active) {
        item(key = "enter") {
          val allowed = TrainingMode.canEnter(role, staff)
          if (!allowed) SettingsNote(StrTraining.refusedRole)
          if (saved) SettingsNote(StrTraining.resumeNote)
          Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            V3Button(
              text = StrTraining.enter,
              onClick = { scope.launch { TrainingLedger.enter(context, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), role) } },
              modifier = Modifier.weight(1f),
              enabled = allowed,
            )
            if (saved) {
              V3Button(
                text = StrTraining.wipeSaved,
                onClick = { if (TrainingLedger.wipe(context)) { saved = false; toasts.show(StrTraining.wiped) } },
                modifier = Modifier.weight(1f),
                kind = V3ButtonKind.NEUTRAL,
              )
            }
          }
        }
      }
    }
  }
}
