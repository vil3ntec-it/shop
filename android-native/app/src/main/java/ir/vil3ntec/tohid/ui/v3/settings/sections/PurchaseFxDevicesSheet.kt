package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.staff.DeviceBuilds
import ir.vil3ntec.tohid.ui.desk.Desk
import ir.vil3ntec.tohid.ui.desk.DeskDialog
import ir.vil3ntec.tohid.ui.desk.DeskDialogAction
import ir.vil3ntec.tohid.ui.desk.DeskDialogSize
import ir.vil3ntec.tohid.ui.desk.DeskDialogText
import ir.vil3ntec.tohid.ui.desk.DeskDivider
import ir.vil3ntec.tohid.ui.desk.DeskSpace
import ir.vil3ntec.tohid.ui.desk.DeskTone
import ir.vil3ntec.tohid.ui.desk.DeskType
import ir.vil3ntec.tohid.ui.desk.deskTone
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3StatusPill
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.account.team.DeviceVersionModel
import ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout

/**
 *  پرسشِ «خرید به ارزِ دیگر» پیش از روشن کردن (`PurchaseFxScreen`، `DeviceBuilds.gate`): فهرستِ دستگاه‌های فعالی که به‌روز
 *  بودنشان ثابت نیست (کهنه، نامعلوم، «سنجیده نشد») با چرایش، یا — فهرست نرسید — همان هشدارِ «همه را به‌روز کنید». کلید فقط
 *  با «با این حال روشن کن» روشن می‌شود؛ «انصراف»، زدنِ بیرون و Esc همان خاموش. گوشی برگهٔ پایین، کامپیوتر `DeskDialog`.
 */
@Composable
internal fun PurchaseFxDevicesSheet(rows: List<DeviceVersionModel.GateRow>?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  val title = if (rows == null) StrPurchaseFx.noListTitle else StrPurchaseFx.devicesTitle
  val body = if (rows == null) listOf(StrPurchaseFx.noListNote, StrPurchaseFx.updateFirst) else listOf(StrPurchaseFx.devicesBody)
  val unchecked = rows.orEmpty().any { it.state == DeviceBuilds.State.UNCHECKED }
  if (LocalDeskLayout.current) {
    DeskDialog(
      title = title,
      onDismiss = onDismiss,
      icon = Icons.Outlined.SystemUpdate,
      danger = true,
      confirm = DeskDialogAction(StrPurchaseFx.turnOnAnyway, danger = true) { onDismiss(); onConfirm() },
      dismiss = DeskDialogAction(StrKit.cancel, onClick = onDismiss),
      width = DeskDialogSize.form,
    ) {
      body.forEach { DeskDialogText(it) }
      if (!rows.isNullOrEmpty()) {
        Column(Modifier.fillMaxWidth()) {
          rows.forEachIndexed { i, r ->
            if (i > 0) DeskDivider()
            DeskGateRow(r)
          }
        }
      }
      if (unchecked) DeskDialogText(StrPurchaseFx.notCheckedNote)
    }
    return
  }
  val c = PhoneV3.colors
  V3Sheet(onDismiss = onDismiss, detents = false) { closer ->
    val tone = PhoneV3Roles.tone(c, V3Tone.WARNING)
    Box(
      Modifier.align(Alignment.CenterHorizontally).size(56.dp).clip(RoundedCornerShape(50)).background(tone.bg),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = tone.fg, modifier = Modifier.size(26.dp)) }
    Spacer(Modifier.height(12.dp))
    Text(title, style = V3Type.title, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    body.forEach {
      Spacer(Modifier.height(6.dp))
      Text(it, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    if (!rows.isNullOrEmpty()) {
      Spacer(Modifier.height(12.dp))
      rows.forEachIndexed { i, r ->
        if (i > 0) V3Divider()
        PhoneGateRow(r)
      }
    }
    if (unchecked) {
      Spacer(Modifier.height(8.dp))
      Text(StrPurchaseFx.notCheckedNote, style = V3Type.footnote, color = c.inkMuted, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
    //  زیرِ هم، تمام‌پهنا: «با این حال روشن کن» در نیمهٔ ۳۶۰dp بریده می‌شد
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrPurchaseFx.turnOnAnyway, { closer.close(onConfirm) }, Modifier.fillMaxWidth(), kind = V3ButtonKind.DANGER, haptic = true)
      V3Button(StrKit.cancel, { closer.close() }, Modifier.fillMaxWidth(), kind = V3ButtonKind.NEUTRAL)
    }
  }
}

private fun pillOf(state: DeviceBuilds.State): V3Status = when (state) {
  DeviceBuilds.State.OLD -> V3Status.WARNING
  DeviceBuilds.State.UNKNOWN -> V3Status.INACTIVE
  else -> V3Status.REVIEW
}

@Composable
private fun PhoneGateRow(r: DeviceVersionModel.GateRow) {
  val c = PhoneV3.colors
  Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(r.title, style = V3Type.rowTitle, color = c.ink, maxLines = 2)
      if (r.version != null) Text(r.version, style = V3Type.footnote, color = c.inkMuted)
    }
    Spacer(Modifier.width(8.dp))
    V3StatusPill(pillOf(r.state), r.label)
  }
}

@Composable
private fun DeskGateRow(r: DeviceVersionModel.GateRow) {
  val c = Desk.colors
  val tone = c.deskTone(
    when (r.state) {
      DeviceBuilds.State.OLD -> DeskTone.WARNING
      DeviceBuilds.State.UNKNOWN -> DeskTone.NEUTRAL
      else -> DeskTone.INFO
    },
  )
  Row(Modifier.fillMaxWidth().padding(vertical = DeskSpace.x2), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text(r.title, style = DeskType.bodyStrong, color = c.ink, maxLines = 2)
      if (r.version != null) Text(r.version, style = DeskType.secondary, color = c.inkSecondary)
    }
    Spacer(Modifier.width(DeskSpace.x3))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(8.dp).clip(CircleShape).background(tone.solid))
      Spacer(Modifier.width(DeskSpace.x2))
      Text(r.label, style = DeskType.secondary, color = tone.ink)
    }
  }
}
