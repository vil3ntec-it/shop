package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.perm.IdleLock
import ir.vil3ntec.tohid.data.LockStore
import ir.vil3ntec.tohid.ui.screens.MotionSpec
import ir.vil3ntec.tohid.ui.screens.MotionTokens
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3IconTile
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPaths

/**
 *  `settings/security/app-lock`: کارتِ وضعیت، توضیح، گذاشتن / عوض کردن / برداشتنِ
 *  رمز (برداشتن با تأیید) و «قفلِ خودکار پس از بی‌کاری» در برگه (STF-05).
 *  رمز در برگهٔ `settings/security/app-lock/pin` زده می‌شود.
 */
@Composable
internal fun RouteScope.AppLockScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val lockOn = rememberLockOn()
  val store = remember(LockSignal.version) { LockStore(context) }
  var askRemove by remember { mutableStateOf(false) }
  var pickIdle by remember { mutableStateOf(false) }
  var idle by remember { mutableIntStateOf(store.idleMinutes) }
  if (askRemove) RemovePinSheet(onDismiss = { askRemove = false })
  if (pickIdle) IdleSheet(idle, onPick = { m -> store.idleMinutes = m; idle = m }, onDismiss = { pickIdle = false })

  SettingsPageFrame(title = SettingsLayout.labelOf("قفل برنامه"), onBack = onBack) {
    item(key = "status") { LockStatusCard(lockOn) }
    item(key = "note") { SettingsNote(StrSettings.lockNote) }
    if (lockOn && store.isLegacyLength) {
      item(key = "legacy") { V3Message(V3Tone.WARNING, StrSettings.oldPin4, Modifier.padding(top = 16.dp)) }
    }
    item(key = "actions") {
      Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (lockOn) {
          V3Button(StrSettings.changePin, { nav.open(SettingsPaths.PIN) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.SECONDARY)
          Spacer(Modifier.height(10.dp))
          V3Button(StrSettings.removePin, { askRemove = true }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, haptic = true)
        } else {
          V3Button(StrSettings.setPin, { nav.open(SettingsPaths.PIN) }, Modifier.fillMaxWidth(), haptic = true)
        }
      }
    }
    if (lockOn) {
      item(key = "idle") {
        V3Section(null) {
          V3ListRow(
            StrSettings.idleTitle,
            subtitle = StrSettings.idleNote,
            icon = Icons.Outlined.Timer,
            tile = false,
            value = idleLabel(idle),
            onClick = { pickIdle = true },
          )
        }
      }
    }
  }
}

@Composable
private fun LockStatusCard(on: Boolean) {
  val c = PhoneV3.colors
  SettingsBlock {
    AnimatedContent(on, transitionSpec = { fadeIn(MotionSpec.tweenOf(MotionTokens.MEDIUM)) togetherWith fadeOut(MotionSpec.tweenOf(MotionTokens.SHORT)) }, label = "lockStatus") { now ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        V3IconTile(if (now) Icons.Outlined.Lock else Icons.Outlined.LockOpen, hue = if (now) V3Hue.BRAND else null, size = 48.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
          Text(if (now) StrSettings.lockOnPin else StrSettings.off, style = V3Type.headline, color = if (now) PhoneV3Roles.brandText(c) else c.ink)
          Text(StrSettings.lockStatusNote, style = V3Type.footnote, color = c.inkMuted)
        }
      }
    }
  }
}

internal fun idleLabel(m: Int): String = if (m == 0) StrSettings.never else StrSettings.minutes(m)

/** «قفلِ خودکار پس از بی‌کاری»: هرگز / n دقیقه */
@Composable
private fun IdleSheet(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, title = StrSettings.idleTitle) { closer ->
    SettingsNote(StrSettings.idleNote, Modifier.padding(bottom = 12.dp))
    ir.vil3ntec.tohid.ui.v3.V3Card {
      IdleLock.CHOICES.forEachIndexed { i, m ->
        if (i > 0) V3Divider(start = 16.dp)
        V3ListRow(
          idleLabel(m),
          tile = false,
          chevron = false,
          trailing = if (m == current) ({ Icon(Icons.Outlined.Check, contentDescription = null, tint = PhoneV3Roles.brandText(c), modifier = Modifier.size(20.dp)) }) else null,
          onClick = { closer.close { onPick(m) } },
        )
      }
    }
  }
}
