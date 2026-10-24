package ir.vil3ntec.tohid.ui.v3.settings.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.PhotoBot
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.v1.LiveSocket
import ir.vil3ntec.tohid.ui.screens.PhotoPrefs
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type

/** همگام‌سازیِ زنده: فروشِ هر دستگاه همان لحظه روی بقیه (`LiveSocket`) */
@Composable
internal fun LiveCard(highlight: Boolean) {
  val context = LocalContext.current
  val signedIn = remember { Backend.isReady(context) }
  val live = LiveSocket.current?.connected == true
  val (tone, text) = when {
    !signedIn -> V3Tone.WARNING to StrSettings.liveNoAccount
    live -> V3Tone.SUCCESS to StrSettings.liveOn
    else -> V3Tone.WARNING to StrSettings.liveOff
  }
  val c = PhoneV3.colors
  DeviceCard(StrSettings.liveSync, null, highlight, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.Top) {
      androidx.compose.foundation.layout.Box(
        Modifier.padding(top = 6.dp).size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(PhoneV3Roles.toneIcon(c, tone)),
      )
      Text(text, style = V3Type.callout, color = c.ink, modifier = Modifier.padding(start = 10.dp))
    }
  }
}

/** عکسِ کالاها: نمایش روی همین دستگاه (`PhotoPrefs`) و ربات عکس (`PhotoBot`) */
@Composable
internal fun PhotosCard(highlight: Boolean) {
  val context = LocalContext.current
  val c = PhoneV3.colors
  var auto by remember { mutableStateOf(PhotoBot.autoEnabled(context)) }
  DeviceCard(StrSettings.productPhotos, StrSettings.photosNote, highlight) {
    V3ToggleRow(StrSettings.showPhotos, checked = PhotoPrefs.show.value, onCheckedChange = { PhotoPrefs.set(context, it) }, icon = Icons.Outlined.Image)
    V3Divider(start = 64.dp)
    V3ToggleRow(StrSettings.photoBotAuto, checked = auto, onCheckedChange = { auto = it; PhotoBot.setAuto(context, it) }, subtitle = StrSettings.photoBotNote, icon = Icons.Outlined.AutoFixHigh)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
      val prog = PhotoBot.progress.value
      if (prog != null) {
        LinearProgressIndicator(progress = { if (prog.second == 0) 0f else prog.first.toFloat() / prog.second }, modifier = Modifier.fillMaxWidth(), color = PhoneV3Roles.focus(c), trackColor = c.tile)
        Text(StrSettings.ofCount(prog.first, prog.second), style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.padding(top = 6.dp))
      } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          V3Button(StrSettings.improveAll, { PhotoBot.start(context, onlyNew = false) }, Modifier.weight(1f), kind = V3ButtonKind.SECONDARY)
          if (PhotoBot.originalsCount(context) > 0) V3Button(StrSettings.restoreOriginals, { PhotoBot.restoreAll(context) }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
        }
      }
      PhotoBot.lastResult.value?.let { Text(it, style = V3Type.footnote, color = PhoneV3Roles.toneIcon(c, V3Tone.SUCCESS), modifier = Modifier.padding(top = 6.dp)) }
    }
  }
}
