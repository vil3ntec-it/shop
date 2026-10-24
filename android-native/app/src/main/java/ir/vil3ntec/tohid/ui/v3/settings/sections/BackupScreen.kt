package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.formatMillis
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.SettingsLayout
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.V3ConfirmSheet
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3TextButton
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.ROW_DIVIDER_START
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.theme.V3Tone

/**
 *  `settings/data/backup`: پشتیبانِ دستی، خروجی، بازیابی از فایل (برگهٔ تأیید با
 *  شمارِ پیش/پس و عکس‌ها)، نسخه‌های خودکار، پشتیبان روی سرور (نوارِ جای مصرف‌شده)،
 *  پشتیبانِ کامل در پوشه، نگه‌داری، و جدا در پایان «پاک‌سازیِ کامل» با تأییدِ خطر.
 *  کارها همه در `BackupController`.
 */
@Composable
internal fun RouteScope.BackupScreen(onBack: (() -> Unit)?) {
  val context = LocalContext.current
  val signedIn = remember { SyncStore(context).accessToken != null }
  val (ctrl, launch) = rememberBackupController(store, toasts, signedIn)
  val autoBackups = remember(data) { AutoBackup.list(context) }
  val plan = remember(data) { LedgerArchive.plan(data) }
  var ask by remember { mutableStateOf(if (SettingsShotState.wipeSheet) Ask.WIPE else null) }

  ctrl.pendingRestore?.let { RestoreSheet(current = data, incoming = it, photos = ctrl.restorePhotos, onConfirm = ctrl::confirmRestore, onDismiss = ctrl::cancelRestore) }
  when (ask) {
    Ask.COMPACT -> V3ConfirmSheet(StrSettings.compactQ, StrSettings.compactWarn(plan.total), StrSettings.compactDo, { ctrl.compact(data) }, { ask = null }, icon = Icons.Outlined.Compress, danger = false)
    Ask.WIPE -> V3ConfirmSheet(StrSettings.wipeQ, StrSettings.wipeWarn, StrSettings.wipe, { ctrl.wipe() }, { ask = null }, icon = Icons.Outlined.DeleteForever)
    null -> Unit
  }

  SettingsPageFrame(title = SettingsLayout.labelOf("داده‌ها"), subtitle = BackupClock.text(context), onBack = onBack) {
    item(key = "summary") { SettingsNote(StrSettings.dataSummary(data.products.size, data.sales.size, data.debtors.size)) }
    item(key = "manual") {
      V3Section(StrSettings.manualBackup) {
        V3ListRow(StrSettings.fullBackup, subtitle = StrSettings.fullBackupDesc, icon = Icons.Outlined.CloudUpload, tile = false, onClick = launch.full)
        V3Divider(start = ROW_DIVIDER_START)
        V3ListRow(StrSettings.ledgerOnly, subtitle = StrSettings.ledgerOnlyDesc, icon = Icons.Outlined.Description, tile = false, onClick = launch.ledger)
      }
    }
    item(key = "export") {
      V3Section(StrSettings.exportTitle) {
        V3ListRow(StrSettings.exportExcel, subtitle = StrSettings.exportExcelDesc, icon = Icons.Outlined.TableChart, tile = false, onClick = launch.excel)
        V3Divider(start = ROW_DIVIDER_START)
        V3ListRow(StrSettings.exportJson, subtitle = StrSettings.exportJsonDesc, icon = Icons.Outlined.Description, tile = false, onClick = launch.json)
      }
    }
    item(key = "restore") {
      V3Section(StrSettings.restoreFromFile) {
        V3ListRow(StrSettings.restoreFromFile, subtitle = StrSettings.restoreDesc, icon = Icons.Outlined.CloudDownload, tile = false, onClick = launch.restore)
      }
      ctrl.restoreError?.let { V3Message(V3Tone.ERROR, it, Modifier.padding(top = 8.dp)) }
    }
    if (autoBackups.isNotEmpty()) {
      item(key = "auto") {
        V3Section(StrSettings.autoTitle, footer = StrSettings.autoSnapshots) {
          autoBackups.forEachIndexed { i, snap ->
            if (i > 0) V3Divider(start = ROW_DIVIDER_START)
            V3ListRow(formatMillis(snap.at), subtitle = StrSettings.snapshotDesc((snap.bytes / 1024).toInt()), icon = Icons.Outlined.History, tile = false, onClick = { ctrl.readSnapshot(snap) })
          }
        }
      }
    }
    item(key = "cloud") { CloudSection(ctrl, signedIn) }
    item(key = "mirror") { MirrorSection(ctrl, signedIn) }
    if (plan.total > 0 || ctrl.canUndo) {
      item(key = "care") {
        V3Section(StrSettings.careTitle) {
          if (plan.total > 0) {
            V3ListRow(StrSettings.compactLedger, subtitle = StrSettings.compactDesc(plan.total), icon = Icons.Outlined.Compress, tile = false, onClick = { ask = Ask.COMPACT })
          }
          if (plan.total > 0 && ctrl.canUndo) V3Divider(start = ROW_DIVIDER_START)
          if (ctrl.canUndo) {
            V3ListRow(StrSettings.undoRestore, subtitle = StrSettings.undoRestoreDesc, icon = Icons.Outlined.Restore, tile = false, onClick = { ctrl.undoRestore() })
          }
        }
      }
    }
    item(key = "wipe") {
      Spacer(Modifier.height(12.dp))
      V3Section(null, footer = StrSettings.wipeWarn) {
        V3ListRow(StrSettings.wipeAll, icon = Icons.Outlined.DeleteForever, tile = false, danger = true, chevron = false, onClick = { ask = Ask.WIPE })
      }
    }
  }
}

private enum class Ask { COMPACT, WIPE }

/** پشتیبان روی سرور: نوارِ جای مصرف‌شده، «همین حالا روی سرور بگذار» و پنج نسخهٔ آخر */
@Composable
private fun CloudSection(ctrl: BackupController, signedIn: Boolean) {
  val c = PhoneV3.colors
  val cloud = ctrl.cloud
  V3Section(StrSettings.cloudBackup) {
    if (cloud != null && cloud.stats.quotaBytes > 0) {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val used = (cloud.stats.usedBytes.toFloat() / cloud.stats.quotaBytes).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { used }, modifier = Modifier.fillMaxWidth(), color = PhoneV3Roles.focus(c), trackColor = c.tile)
        Spacer(Modifier.height(6.dp))
        Text(
          StrSettings.cloudStats(cloud.stats.count, (cloud.stats.usedBytes / 1024 / 1024).toInt(), (cloud.stats.quotaBytes / 1024 / 1024).toInt()),
          style = V3Type.footnote, color = c.inkMuted,
        )
      }
      V3Divider(start = 16.dp)
    }
    V3ListRow(
      if (ctrl.cloudBusy) StrSettings.sendingDots else StrSettings.sendToServerNow,
      subtitle = when {
        !signedIn -> StrSettings.needSignIn
        cloud == null -> StrSettings.nothingSentOrOffline
        cloud.stats.count == 0 -> StrSettings.nothingSent
        else -> null
      },
      icon = Icons.Outlined.CloudUpload,
      tile = false,
      trailing = if (ctrl.cloudBusy) ({ Spinner() }) else null,
      onClick = { ctrl.pushCloud(signedIn) },
    )
    cloud?.items?.take(5)?.forEach { b ->
      V3Divider(start = ROW_DIVIDER_START)
      V3ListRow(
        formatMillis(b.createdAt),
        subtitle = StrSettings.kb((b.bytes / 1024).toInt()) + " · " + (if (b.kind == "manual") StrSettings.manual else StrSettings.auto),
        icon = Icons.Outlined.CloudDone,
        tile = false,
        chevron = false,
      )
    }
  }
  //  ⚠️ برگرداندن از ابر عمداً این‌جا نیست: باید از درِ «بازیابی از فایل» برود تا کاربر اول ببیند داخلش چیست
  if (!cloud?.items.isNullOrEmpty()) SettingsNote(StrSettings.restoreFromPanel)
}

/** پشتیبانِ کامل در پوشه: «همین حالا»، پوشه، و خودکار هر ۱۲ ساعت */
@Composable
private fun MirrorSection(ctrl: BackupController, signedIn: Boolean) {
  val context = LocalContext.current
  val dir = remember(ctrl.mirrorTick) { BackupMirror.folder(context)?.path ?: "—" }
  val files = remember(ctrl.mirrorTick) { BackupMirror.list(context) }
  V3Section(StrSettings.folderBackup, footer = StrSettings.folder(dir)) {
    V3ListRow(
      if (ctrl.mirrorBusy) StrSettings.buildingDots else StrSettings.mirrorNow,
      subtitle = StrSettings.mirrorDesc(files.size) + (files.firstOrNull()?.let { " · " + StrSettings.lastLabel + " " + formatMillis(it.lastModified()) } ?: ""),
      icon = Icons.Outlined.CloudUpload,
      tile = false,
      trailing = if (ctrl.mirrorBusy) ({ Spinner() }) else null,
      onClick = { ctrl.mirrorNow(signedIn) },
    )
    V3Divider(start = ROW_DIVIDER_START)
    V3ToggleRow(StrSettings.every12h, checked = ctrl.mirrorOn, onCheckedChange = ctrl::setMirror, icon = Icons.Outlined.History)
  }
  if (Platform.DESKTOP) {
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      V3TextButton(StrSettings.chooseFolder, { ctrl.pickFolder(dir) })
      V3TextButton(StrSettings.openFolder, { ctrl.openFolder(dir) })
    }
  }
}

@Composable
private fun Spinner() {
  CircularProgressIndicator(Modifier.size(18.dp), color = PhoneV3Roles.focus(PhoneV3.colors), strokeWidth = 2.dp)
}

