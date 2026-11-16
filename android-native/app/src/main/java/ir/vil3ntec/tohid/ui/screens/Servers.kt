package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.ui.components.common.ShopAlertDialog
import ir.vil3ntec.tohid.ui.components.common.ShopTextButton
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.i18n.StrAuth
import ir.vil3ntec.tohid.core.i18n.StrHome
import ir.vil3ntec.tohid.core.i18n.StrInvoices
import ir.vil3ntec.tohid.core.i18n.StrLabel
import ir.vil3ntec.tohid.core.i18n.StrPos
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrSetupPages
import ir.vil3ntec.tohid.core.server.ServerIdentity
import ir.vil3ntec.tohid.core.server.ServerProfile
import ir.vil3ntec.tohid.core.server.ServerProfiles
import ir.vil3ntec.tohid.platform.ServersController
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  بخشِ «سرورها» در تنظیمات (پرچمِ دستگاهیِ `app.multi_server`، پیش‌فرض خاموش) — `docs/SERVERS-fa.md`.
 *
 *  خاموش: فقط کلیدِ روشن کردن، و برنامه همان است که بود. روشن: فهرستِ سرورها، افزودن با نشانی یا کیو‌آر،
 *  بررسیِ واقعی (نام و اثرِ انگشتِ هویت)، تأیید، رفتن به سرورِ دیگر و برداشتن. هر تغییر فقط کارِ صاحبِ
 *  دکان است (`ServersController.canManage`)، با رمزِ برنامه اگر روشن است، و در سابقه می‌ماند.
 *
 *  ⛔ «هیچ جزئیاتِ فنی/زیرساخت در صفحه» (`docs/RULES-fa.md`): هیچ نشانیِ سرور (دستی، آموخته از خودِ سرور یا اعلام‌شده
 *  با `publicUrls`) و هیچ نسخهٔ سروری فهرست نمی‌شود؛ نشانیِ دستیِ صاحب فقط در کادرِ ویرایشِ خودش هنگامِ نوشتن دیده می‌شود.
 *  اثرِ انگشت می‌ماند: سنجشِ هویتِ سرور با چشمِ صاحب است (ضدِ جعل)، نه نشانی و نه راز.
 *
 *  ⛔ رفتن به سرورِ دیگر برنامه را **می‌بندد**: عوض شدن فقط در شروعِ بعدیِ برنامه، پیش از هر دفتر و
 *  درخواستی، انجام می‌شود (`ServerSwitchAndroid.applyPending`) تا دفترِ این سرور هرگز به آن سرور نرود.
 */
@Composable
fun ServersSettings(controller: ServersController, toast: (String) -> Unit) {
  if (!controller.canManage) return
  var on by remember { mutableStateOf(controller.enabled()) }
  var profiles by remember { mutableStateOf(controller.profiles()) }
  var adding by remember { mutableStateOf(false) }
  var switching by remember { mutableStateOf<ServerProfile?>(null) }
  var removing by remember { mutableStateOf<ServerProfile?>(null) }
  var turningOff by remember { mutableStateOf(false) }
  //  کارِ منتظرِ رمزِ برنامه
  var pinFor by remember { mutableStateOf<(() -> Unit)?>(null) }
  fun guarded(action: () -> Unit) { if (controller.pinRequired) pinFor = action else action() }
  fun reload() { profiles = controller.profiles() }
  fun toggle(v: String) {
    if (v == "on") {
      controller.setEnabled(true)
      on = true
      reload()
    } else if (profiles.activeId != "default") {
      turningOff = true
    } else {
      controller.setEnabled(false)
      on = false
    }
  }

  SettingsSection(
    icon = Icons.Filled.Dns,
    title = ir.vil3ntec.tohid.core.i18n.StrReach.servers,
    subtitle = if (on) StrSetupPages.activeServer(profiles.active.name) else StrSetupPages.mainServerOnly,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(
      StrSetupPages.serversIntro1 +
        StrSetupPages.serversIntro2,
      style = MaterialTheme.typography.labelSmall,
      color = Shop.colors.muted2,
    )
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrLabel.off, "on" to StrLabel.on),
      selected = if (on) "on" else "off",
      onSelect = { v -> guarded { toggle(v) } },
    )
    if (on) ServerList(controller, profiles, onUse = { p -> guarded { switching = p } }, onRemove = { p -> guarded { removing = p } }, onAdd = { guarded { adding = true } })
  }

  if (adding) {
    AddServerDialog(
      controller = controller,
      onDismiss = { adding = false },
      onAdded = { adding = false; reload(); toast(StrSetupPages.serverAdded) },
    )
  }

  //  کامپیوتر (`claude/e-desk-d3c`): همان پرسش‌ها و کارها روی `DeskDialog` (رفتن/برداشتن/خاموش سرخ با فوکوسِ آغاز روی «نه»)؛ گوشی همان
  val desk = ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout.current
  switching?.let { p ->
    if (desk) ir.vil3ntec.tohid.ui.desk.DeskDialog(
      title = StrSetupPages.switchTo(p.name), onDismiss = { switching = null },
      confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrSetupPages.goAndClose, danger = true) {
        val error = controller.switchTo(p.id)
        switching = null
        if (error != null) toast(error) else controller.restartNow()
      },
      dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrPos.no) { switching = null },
    ) { ir.vil3ntec.tohid.ui.desk.DeskDialogText(StrSetupPages.switchNote1 + StrSetupPages.switchNote2) }
    else ShopAlertDialog(
      danger = true,
      onDismissRequest = { switching = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSetupPages.switchTo(p.name), color = Shop.colors.text) },
      text = {
        Text(
          StrSetupPages.switchNote1 +
            StrSetupPages.switchNote2,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        ShopTextButton(onClick = {
          val error = controller.switchTo(p.id)
          switching = null
          if (error != null) toast(error) else controller.restartNow()
        }) { Text(StrSetupPages.goAndClose) }
      },
      dismissButton = { ShopTextButton(onClick = { switching = null }) { Text(StrPos.no) } },
    )
  }

  removing?.let { p ->
    if (desk) ir.vil3ntec.tohid.ui.desk.DeskDialog(
      title = StrSetupPages.removeServer(p.name), onDismiss = { removing = null },
      confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrAccount.removeConfirm, danger = true) {
        val error = controller.remove(p.id)
        removing = null
        if (error != null) toast(error) else reload()
      },
      dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrPos.no) { removing = null },
    ) { ir.vil3ntec.tohid.ui.desk.DeskDialogText(StrSetupPages.removeNote) }
    else ShopAlertDialog(
      danger = true,
      onDismissRequest = { removing = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSetupPages.removeServer(p.name), color = Shop.colors.text) },
      text = {
        Text(
          StrSetupPages.removeNote,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        ShopTextButton(onClick = {
          val error = controller.remove(p.id)
          removing = null
          if (error != null) toast(error) else reload()
        }) { Text(StrAccount.removeConfirm) }
      },
      dismissButton = { ShopTextButton(onClick = { removing = null }) { Text(StrPos.no) } },
    )
  }

  if (turningOff) {
    if (desk) ir.vil3ntec.tohid.ui.desk.DeskDialog(
      title = StrSetupPages.backToMain, onDismiss = { turningOff = false },
      confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrSetupPages.offAndClose, danger = true) {
        turningOff = false
        if (controller.setEnabled(false)) controller.restartNow()
        on = false
      },
      dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrPos.no) { turningOff = false },
    ) { ir.vil3ntec.tohid.ui.desk.DeskDialogText(StrSetupPages.backToMainNote) }
    else ShopAlertDialog(
      danger = true,
      onDismissRequest = { turningOff = false },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSetupPages.backToMain, color = Shop.colors.text) },
      text = {
        Text(
          StrSetupPages.backToMainNote,
          style = MaterialTheme.typography.bodySmall,
          color = Shop.colors.muted,
        )
      },
      confirmButton = {
        ShopTextButton(onClick = {
          turningOff = false
          if (controller.setEnabled(false)) controller.restartNow()
          on = false
        }) { Text(StrSetupPages.offAndClose) }
      },
      dismissButton = { ShopTextButton(onClick = { turningOff = false }) { Text(StrPos.no) } },
    )
  }

  pinFor?.let { action ->
    var pin by remember { mutableStateOf("") }
    if (desk) {
      val field = remember { androidx.compose.ui.focus.FocusRequester() }
      ir.vil3ntec.tohid.ui.desk.DeskDialog(
        title = StrSetupPages.appPin, onDismiss = { pinFor = null }, initialFocus = field,
        confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrAuth.confirm) { if (controller.pinOk(pin)) { pinFor = null; action() } else toast(StrAuth.lockWrong) },
        dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrPos.no) { pinFor = null },
      ) { ir.vil3ntec.tohid.ui.desk.DeskDialogField(StrSetupPages.appPin, pin, { pin = it.filter(Char::isDigit).take(12) }, focusRequester = field, password = true) }
    }
    else ShopAlertDialog(
      onDismissRequest = { pinFor = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSetupPages.appPin, color = Shop.colors.text) },
      text = {
        OutlinedTextField(
          value = pin,
          onValueChange = { pin = it.filter(Char::isDigit).take(12) },
          singleLine = true,
          visualTransformation = PasswordVisualTransformation(),
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        ShopTextButton(onClick = {
          if (controller.pinOk(pin)) { pinFor = null; action() } else toast(StrAuth.lockWrong)
        }) { Text(StrAuth.confirm) }
      },
      dismissButton = { ShopTextButton(onClick = { pinFor = null }) { Text(StrPos.no) } },
    )
  }

}

@Composable
private fun ServerList(
  controller: ServersController,
  profiles: ServerProfiles,
  onUse: (ServerProfile) -> Unit,
  onRemove: (ServerProfile) -> Unit,
  onAdd: () -> Unit,
) {
  Column {
    Spacer(Modifier.height(12.dp))
    profiles.profiles.forEach { p ->
      ServerRow(
        p = p,
        active = p.id == profiles.activeId,
        blocked = controller.blocked(p.id),
        fingerprint = controller.fingerprintOf(p),
        onUse = { onUse(p) },
        onRemove = { onRemove(p) },
      )
      Spacer(Modifier.height(8.dp))
    }
    TohidButton(text = StrSetupPages.addServer, onClick = onAdd, modifier = Modifier.fillMaxWidth())

    val log = remember(profiles) { controller.log().take(8) }
    if (log.isNotEmpty()) {
      Spacer(Modifier.height(12.dp))
      Text(StrInvoices.history, style = MaterialTheme.typography.labelMedium, color = Shop.colors.muted)
      log.forEach { e ->
        //  ⛔ یادداشتِ «افزودن/نشانیِ تازه» خودِ نشانی است ⇒ فقط یادداشتِ «رفتن» (نامِ سرورِ پیشین) دیده می‌شود
        val note = e.note.takeIf { e.action == "schedule" && it.isNotBlank() }
        Text(
          "${actionText(e.action)} — ${e.name}" + (if (note != null) " ($note)" else ""),
          style = MaterialTheme.typography.labelSmall,
          color = Shop.colors.muted2,
        )
      }
    }
  }
}

@Composable
private fun ServerRow(
  p: ServerProfile,
  active: Boolean,
  blocked: Boolean,
  fingerprint: String,
  onUse: () -> Unit,
  onRemove: () -> Unit,
) {
  Column(
    Modifier
      .fillMaxWidth()
      .border(1.dp, if (active) Shop.colors.success else Shop.colors.border, RoundedCornerShape(12.dp))
      .padding(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(p.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text)
      Spacer(Modifier.width(8.dp))
      if (active) Text(StrSettings.active, style = MaterialTheme.typography.labelSmall, color = Shop.colors.success)
      if (p.lan) Text(StrSetupPages.localNetwork, style = MaterialTheme.typography.labelSmall, color = Shop.colors.info)
    }
    //  ⛔ نشانی‌ها (دستی، آموخته یا اعلام‌شده) و نسخهٔ سرور فهرست نمی‌شوند — فقط نام، اثرِ انگشت و «قدیمی»
    if (p.lastInfo?.legacy == true) {
      Text(StrSetupPages.legacyServer.trimStart(' ', '—'), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
    if (fingerprint.isNotEmpty()) {
      Text(StrSetupPages.fingerprint(fingerprint), style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    }
    if (blocked) {
      Spacer(Modifier.height(4.dp))
      Text(ServerIdentity.MISMATCH_MESSAGE, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
    }
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
      if (!active && !blocked) TextButton(onClick = onUse) { Text(StrSetupPages.useThisServer) }
      if (!active && !p.isDefault) TextButton(onClick = onRemove) { Text(StrAccount.remove, color = Shop.colors.danger) }
    }
  }
}

@Composable
private fun AddServerDialog(controller: ServersController, onDismiss: () -> Unit, onAdded: () -> Unit) {
  val scope = rememberCoroutineScope()
  var input by remember { mutableStateOf("") }
  var lan by remember { mutableStateOf(false) }
  var busy by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var scanning by remember { mutableStateOf(false) }
  var certToTrust by remember { mutableStateOf<String?>(null) }
  var trusted by remember { mutableStateOf<String?>(null) }
  var ready by remember { mutableStateOf<ServersController.Candidate?>(null) }

  fun check() {
    busy = true
    error = null
    scope.launch {
      when (val r = controller.test(input.trim(), lan, trusted)) {
        is ServersController.Test.Ready -> ready = r.candidate
        is ServersController.Test.Failed -> error = r.message
        is ServersController.Test.NeedsCertTrust -> certToTrust = r.fingerprint
      }
      busy = false
    }
  }

  ShopAlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSetupPages.addServer, color = Shop.colors.text) },
    text = {
      Column {
        val c = ready
        if (c == null) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null; trusted = null },
            label = { Text(StrSetupPages.addressOrQr) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
          )
          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = lan, onCheckedChange = { lan = it })
            Text(StrSetupPages.lanServer, style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
          }
          if (!Platform.DESKTOP) {
            ShopTextButton(onClick = { scanning = true }) {
              androidx.compose.material3.Icon(Icons.Filled.QrCodeScanner, null)
              Spacer(Modifier.width(6.dp))
              Text(StrSetupPages.scanServerQr)
            }
          }
          Text(
            StrSetupPages.httpsOnly,
            style = MaterialTheme.typography.labelSmall,
            color = Shop.colors.muted2,
          )
        } else {
          //  ⛔ نشانی‌ها و نسخهٔ سرور این‌جا نیستند؛ نشانیِ دستی فقط در همان کادرِ ویرایشِ صاحب دیده می‌شد
          Text(c.name, style = MaterialTheme.typography.titleSmall, color = Shop.colors.text)
          if (c.info.legacy) {
            Text(
              StrSetupPages.legacyWarn,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.warning,
            )
          } else {
            Spacer(Modifier.height(6.dp))
            Text(StrSetupPages.serverFingerprint(c.fingerprint), style = MaterialTheme.typography.bodySmall, color = Shop.colors.text)
            Text(
              StrSetupPages.fingerprintCompare,
              style = MaterialTheme.typography.labelSmall,
              color = Shop.colors.muted2,
            )
          }
          c.alternateOf?.let { p ->
            Spacer(Modifier.height(6.dp))
            Text(StrSetupPages.sameServer(p.name), style = MaterialTheme.typography.labelSmall, color = Shop.colors.info)
          }
        }
        error?.let {
          Spacer(Modifier.height(6.dp))
          Text(it, style = MaterialTheme.typography.labelSmall, color = Shop.colors.danger)
        }
      }
    },
    confirmButton = {
      val c = ready
      if (c == null) {
        ShopTextButton(enabled = !busy && input.isNotBlank(), onClick = { check() }) { Text(if (busy) StrAuth.verifying else StrHome.actionCheck) }
      } else {
        ShopTextButton(onClick = {
          val e = controller.add(c)
          if (e != null) error = e else onAdded()
        }) { Text(StrSetupPages.confirmAndAdd) }
      }
    },
    dismissButton = { ShopTextButton(onClick = onDismiss) { Text(Str.close) } },
  )

  certToTrust?.let { fp ->
    if (ir.vil3ntec.tohid.ui.v3.nav.LocalDeskLayout.current) ir.vil3ntec.tohid.ui.desk.DeskDialog(
      title = StrSetupPages.unknownCert, onDismiss = { certToTrust = null },
      confirm = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrSetupPages.sameConfirm, danger = true) { trusted = fp; certToTrust = null; check() },
      dismiss = ir.vil3ntec.tohid.ui.desk.DeskDialogAction(StrPos.no) { certToTrust = null },
    ) {
      ir.vil3ntec.tohid.ui.desk.DeskDialogText(StrSetupPages.selfSignedNote)
      ir.vil3ntec.tohid.ui.desk.DeskDialogText(fp.chunked(2).joinToString(":").uppercase(), strong = true)
    }
    else ShopAlertDialog(
      danger = true,
      onDismissRequest = { certToTrust = null },
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSetupPages.unknownCert, color = Shop.colors.text) },
      text = {
        Column {
          Text(
            StrSetupPages.selfSignedNote,
            style = MaterialTheme.typography.bodySmall,
            color = Shop.colors.muted,
          )
          Spacer(Modifier.height(6.dp))
          Text(fp.chunked(2).joinToString(":").uppercase(), style = MaterialTheme.typography.labelSmall, color = Shop.colors.text)
        }
      },
      confirmButton = {
        ShopTextButton(onClick = { trusted = fp; certToTrust = null; check() }) { Text(StrSetupPages.sameConfirm) }
      },
      dismissButton = { ShopTextButton(onClick = { certToTrust = null }) { Text(StrPos.no) } },
    )
  }

  if (scanning) {
    BarcodeScanSheet(
      onDismiss = { scanning = false },
      onCode = { code -> input = code; scanning = false; if (code.trim().startsWith("tohid-server:")) check() },
      title = StrSetupPages.serverQr,
      qr = true,
    )
  }
}

private fun actionText(action: String): String = when (action) {
  "add" -> StrSetupPages.histAdded
  "add_url" -> StrSetupPages.histAddUrl
  "learn_url" -> StrSetupPages.histLearnUrl
  "remove" -> StrSettings.whatsNewKindRemoved
  "schedule" -> StrSetupPages.histSchedule
  "switch" -> StrSetupPages.histSwitch
  "flag_on" -> StrSetupPages.histFlagOn
  "flag_off" -> StrSetupPages.histFlagOff
  else -> action
}
