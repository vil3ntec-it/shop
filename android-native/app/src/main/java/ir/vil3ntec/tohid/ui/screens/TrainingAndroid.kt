package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.i18n.StrTraining
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.TrainingLedger
import ir.vil3ntec.tohid.data.TrainingMode
import ir.vil3ntec.tohid.data.TrainingPref
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/** OPS-07 — عنوانِ ثابتِ بخش */
const val TRAINING_SETTINGS_TITLE = "حالتِ آموزشی"

/**
 *  OPS-07 — بخشِ «حالتِ آموزشی» در تنظیماتِ گوشی و کامپیوتر (`TrainingPref`، هر دو پیش‌فرض خاموش).
 *  خاموش ⇒ فقط همین کلید؛ هیچ درِ ورودی جای دیگری کشیده نمی‌شود. «کارمند هم» را فقط صاحب/مدیر عوض می‌کند.
 */
@Composable
fun TrainingSettingsWired(context: android.content.Context, onMessage: (String) -> Unit) {
  val scope = rememberCoroutineScope()
  val role = remember { ShopRole.current(context) }
  var on by remember { mutableStateOf(TrainingPref.on(context)) }
  var staff by remember { mutableStateOf(TrainingPref.staff(context)) }
  var saved by remember { mutableStateOf(TrainingLedger.hasSaved(context)) }
  val active by TrainingMode.active.collectAsState()
  SettingsSection(
    icon = Icons.Filled.School,
    title = TRAINING_SETTINGS_TITLE,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrTraining.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    Text(StrTraining.showEntry, style = MaterialTheme.typography.labelMedium)
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; TrainingPref.setOn(context, on) },
    )
    if (!on) return@SettingsSection
    Spacer(Modifier.height(8.dp))
    Text(StrTraining.staffToo, style = MaterialTheme.typography.labelMedium)
    if (TrainingMode.canManage(role)) {
      SegmentedChoice(
        options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
        selected = if (staff) "on" else "off",
        onSelect = { staff = it == "on"; TrainingPref.setStaff(context, staff) },
      )
    } else {
      Text(StrTraining.staffOnlyOwner, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
    if (active) return@SettingsSection
    Spacer(Modifier.height(12.dp))
    val allowed = TrainingMode.canEnter(role, staff)
    if (!allowed) Text(StrTraining.refusedRole, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
    if (saved) Text(StrTraining.resumeNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(
        enabled = allowed,
        onClick = { scope.launch { TrainingLedger.enter(context, ir.vil3ntec.tohid.todayIso(), System.currentTimeMillis(), role) } },
      ) { Text(StrTraining.enter) }
      if (saved) OutlinedButton(onClick = {
        if (TrainingLedger.wipe(context)) { saved = false; onMessage(StrTraining.wiped) }
      }) { Text(StrTraining.wipeSaved) }
    }
  }
}

/**
 *  OPS-07 — قابِ برنامه در حالتِ آموزشی: نوارِ همیشه‌پیدا بالای **هر** صفحه (گوشی و کامپیوتر) و دکمهٔ خروج با
 *  انتخابِ «دفترِ آموزشی هم پاک شود». `AppRoot` فقط وقتی دفترِ آموزشی فعال است این را می‌کشد.
 */
@Composable
fun TrainingFrame(content: @Composable () -> Unit) {
  val context = LocalContext.current
  var asking by remember { mutableStateOf(false) }
  var wipe by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().background(Shop.colors.status.warning.bg).padding(horizontal = 12.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(Icons.Filled.School, contentDescription = null, tint = Shop.colors.status.warning.fg)
      Text(
        StrTraining.banner, color = Shop.colors.status.warning.fg, fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
      )
      TextButton(onClick = { asking = true }) { Text(StrTraining.exit, color = Shop.colors.status.warning.fg) }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
  }
  if (asking) AlertDialog(
    onDismissRequest = { asking = false },
    title = { Text(StrTraining.exitTitle) },
    text = {
      Column {
        Text(StrTraining.exitNote)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = wipe, onCheckedChange = { wipe = it })
          Text(StrTraining.exitWipe)
        }
      }
    },
    confirmButton = { Button(onClick = { asking = false; TrainingLedger.exit(context, wipe) }) { Text(StrTraining.exit) } },
    dismissButton = { TextButton(onClick = { asking = false }) { Text(StrTraining.cancel) } },
  )
}
