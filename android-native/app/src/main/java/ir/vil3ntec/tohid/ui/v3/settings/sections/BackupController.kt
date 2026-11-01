package ir.vil3ntec.tohid.ui.v3.settings.sections

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.AutoBackup
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.BackupClock
import ir.vil3ntec.tohid.data.BackupMirror
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.OwnerExport
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.auditId
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.BackupRepository
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.v3.V3Toasts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** یادداشتِ سابقهٔ عملیات برای خروجیِ کامل — دادهٔ دفتر است، با زبانِ صفحه عوض نمی‌شود */
private const val AUDIT_EXPORT_XLSX = "خروجی کامل داده — اکسل"
private const val AUDIT_EXPORT_JSON = "خروجی کامل داده — JSON"

/**
 *  کارهای صفحهٔ «پشتیبان و بازگردانی» — **همان** کارهای پیشینِ تنظیمات، بی منطقِ
 *  تازه: پشتیبانِ ZIP (`BackupBundle`) و فقط‌دفتر، خروجیِ کاملِ مالک (`OwnerExport`،
 *  OPS-08، با ردیفِ سابقه)، بازیابی (نسخهٔ ایمنی، عکس‌ها، `applyRestore`)، نسخه‌های
 *  خودکار، پشتیبانِ ابری (`AutoBackup.push`)، پوشه (`BackupMirror`)، فشرده‌سازی
 *  (`LedgerArchive`)، برگرداندن و پاک‌سازیِ کامل. صفحه فقط حالت را می‌کشد.
 */
@Stable
internal class BackupController(
  private val context: Context,
  private val store: ShopStore,
  private val scope: CoroutineScope,
  private val toasts: V3Toasts,
) {
  /** فایلِ خوانده و سنجیده‌شده، پیش از آنکه دفتر عوض شود (برگهٔ تأیید) */
  var pendingRestore by mutableStateOf<ShopData?>(null)
    private set
  var restorePhotos by mutableIntStateOf(0)
    private set
  var restoreError by mutableStateOf<String?>(null)
    private set
  var canUndo by mutableStateOf(store.hasSafetyCopy())
    private set
  var cloud by mutableStateOf<BackupRepository.Listing?>(null)
    private set
  var cloudBusy by mutableStateOf(false)
    private set
  var cloudReload by mutableIntStateOf(0)
    private set
  var mirrorTick by mutableIntStateOf(0)
    private set
  var mirrorBusy by mutableStateOf(false)
    private set
  var mirrorOn by mutableStateOf(BackupMirror.enabled(context))
    private set

  private val storeName: String
    get() = context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getString("store_name", "") ?: ""

  private fun failText(e: Throwable) = e.message ?: Str.unknownReason

  /** پشتیبانِ کامل — دفتر و عکس‌ها در یک ZIP (بیرون از رشتهٔ اصلی) */
  fun writeFull(uri: Uri) = scope.launch {
    runCatching {
      withContext(Dispatchers.IO) {
        val ledger = store.exportBackup(storeName)
        val out = context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable)
        out.use { BackupBundle.write(context, it, ledger).getOrThrow() }
      }
    }.onSuccess { photos ->
      BackupClock.mark(context)
      toasts.show(if (photos > 0) StrSettings.backupMadeWithPhotos(photos) else StrSettings.backupMade)
    }.onFailure { toasts.show(StrSettings.backupFailed(failText(it))) }
  }

  /** فقط دفتر — همان فایلی که نسخهٔ وب می‌خواند */
  fun writeLedger(uri: Uri) = scope.launch {
    runCatching {
      val out = context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable)
      out.use { it.write(store.exportBackup(storeName).toByteArray(Charsets.UTF_8)) }
    }.onSuccess { BackupClock.mark(context); toasts.show(StrSettings.ledgerFileMade) }
      .onFailure { toasts.show(StrSettings.fileFailed(failText(it))) }
  }

  /** خروجیِ کاملِ داده برای مالک — فقط مالک/مدیر، با ثبت در سابقهٔ عملیات (۲۱.۲) */
  fun ownerExport(uri: Uri, excel: Boolean) {
    if (ShopRole.isStaff(context)) { toasts.show(StrSettings.exportOwnerOnly); return }
    scope.launch {
      runCatching {
        withContext(Dispatchers.IO) {
          val meta = OwnerExport.Meta(System.currentTimeMillis(), if (Platform.DESKTOP) "desktop" else "android", BuildConfig.VERSION_NAME, storeName)
          val archive = LedgerArchive.readArchive(context)
          val out = context.contentResolver.openOutputStream(uri) ?: error(StrSettings.pathNotWritable)
          out.use {
            if (excel) OwnerExport.writeXlsx(store.data.value, meta, archive, it)
            else OwnerExport.writeJson(store.data.value, meta, archive, it)
          }
        }
      }.onSuccess {
        val cur = store.data.value
        store.save(
          cur.copy(
            auditLog = cur.auditLog + AuditEntry(
              id = auditId(), type = "data_export", date = todayIso(),
              notes = if (excel) AUDIT_EXPORT_XLSX else AUDIT_EXPORT_JSON, createdAt = System.currentTimeMillis(),
            ),
          ),
        )
        toasts.show(if (excel) StrSettings.excelExportMade else StrSettings.jsonExportMade)
      }.onFailure { toasts.show(StrSettings.exportFailed(failText(it))) }
    }
  }

  /** بازیابی هر دو شکل را می‌شناسد: ZIPِ کامل و JSONِ ساده */
  fun readFile(uri: Uri) = scope.launch {
    withContext(Dispatchers.IO) {
      runCatching {
        val input = context.contentResolver.openInputStream(uri) ?: error(StrSettings.fileNotOpened)
        input.use { BackupBundle.read(context, it) { text -> store.parseBackup(text) }.getOrThrow() }
      }
    }.onSuccess { pendingRestore = it.data; restorePhotos = it.photos; restoreError = null }
      .onFailure { BackupBundle.dropStaging(context); restoreError = it.message ?: StrSettings.invalidBackup }
  }

  fun readSnapshot(snap: AutoBackup.Snapshot) = scope.launch {
    withContext(Dispatchers.IO) { runCatching { store.parseBackup(ir.vil3ntec.tohid.data.AtRest.readText(context, snap.file)).getOrThrow() } }
      .onSuccess { pendingRestore = it; restorePhotos = 0; restoreError = null }
      .onFailure { restoreError = StrSettings.snapshotUnreadable }
  }

  fun cancelRestore() {
    BackupBundle.dropStaging(context)
    pendingRestore = null
    restorePhotos = 0
  }

  fun confirmRestore() {
    val next = pendingRestore ?: return
    pendingRestore = null
    scope.launch {
      store.keepSafetyCopy()
      //  اول عکس‌ها سرِ جایشان، بعد پرچمِ `photo` با واقعیتِ دیسک یکی می‌شود
      val moved = withContext(Dispatchers.IO) { BackupBundle.commitPhotos(context) }
      val fixed = withContext(Dispatchers.IO) { BackupBundle.reconcilePhotoFlags(context, next) }
      //  ⚠️ بازیابیِ پشتیبان دادهٔ خودِ کاربر است؛ قفلِ نرمِ پایانِ اشتراک گروگانش نمی‌گیرد
      if (!store.applyRestore(fixed)) { toasts.show(StrSettings.restoreDiskFail); return@launch }
      canUndo = true
      restorePhotos = 0
      toasts.show(if (moved > 0) StrSettings.restoredWithPhotos(moved) else StrSettings.restored)
    }
  }

  /** پشتیبانِ ابری — فهرستِ همین دکان (شناسه از توکن)، بی‌صدا */
  suspend fun loadCloud(signedIn: Boolean) {
    if (!signedIn || !Backend.isReady(context)) { cloud = null; return }
    Backend.backups(context).list().onSuccess { cloud = it }.onFailure { cloud = null }
  }

  fun pushCloud(signedIn: Boolean) {
    if (cloudBusy) return
    if (!signedIn) { toasts.show(StrSettings.signInFirst); return }
    scope.launch {
      cloudBusy = true
      val sent = AutoBackup.push(context, manual = true)
      cloudBusy = false
      cloudReload++
      toasts.show(if (sent) StrSettings.cloudSent else StrSettings.cloudNotSent)
    }
  }

  /** پشتیبانِ کامل در پوشه + سرور با هم (`BackupMirror`) */
  fun mirrorNow(signedIn: Boolean) {
    if (mirrorBusy) return
    mirrorBusy = true
    scope.launch {
      val local = withContext(Dispatchers.IO) { BackupMirror.write(context) }
      val sent = if (signedIn) AutoBackup.push(context, manual = true) else false
      mirrorBusy = false
      mirrorTick++; cloudReload++
      toasts.show(
        local.fold({ StrSettings.savedToFolder }, { StrSettings.folder(it.message ?: Str.unknownReason) }) +
          if (signedIn) " · " + (if (sent) StrSettings.alsoOnServer else StrSettings.serverNotSent) else "",
      )
    }
  }

  fun setMirror(on: Boolean) { mirrorOn = on; BackupMirror.setEnabled(context, on) }

  fun pickFolder(current: String) {
    Platform.pickFolder(current)?.let { BackupMirror.setFolder(context, it); mirrorTick++ }
  }

  fun openFolder(path: String) { if (!Platform.openFolder(path)) toasts.show(StrSettings.folderNotOpened) }

  /** فشرده کردنِ دفتر — یک‌طرفه و دستی (شرحش سرِ `LedgerArchive`) */
  fun compact(d: ShopData) = scope.launch {
    withContext(Dispatchers.IO) { LedgerArchive.compact(context, d) }
      .onSuccess { store.save(it.data); toasts.show(StrSettings.archived(it.moved.total)) }
      .onFailure { toasts.show(StrSettings.compactFailed(failText(it))) }
  }

  fun undoRestore() = scope.launch {
    store.undoRestore()
      .onSuccess { canUndo = false; toasts.show(StrSettings.undone) }
      .onFailure { toasts.show(StrSettings.undoFailed) }
  }

  fun wipe() = scope.launch {
    store.save(ShopData())
    toasts.show(StrSettings.wiped)
  }
}

/** راه‌اندازهای پرونده — بی‌قید و شرط ساخته می‌شوند (ترتیبِ فراخوانیِ Compose) */
internal class BackupLaunchers(
  val full: () -> Unit,
  val ledger: () -> Unit,
  val excel: () -> Unit,
  val json: () -> Unit,
  val restore: () -> Unit,
)

@Composable
internal fun rememberBackupController(store: ShopStore, toasts: V3Toasts, signedIn: Boolean): Pair<BackupController, BackupLaunchers> {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val ctrl = remember(store) { BackupController(context, store, scope, toasts) }
  LaunchedEffect(signedIn, ctrl.cloudReload) { ctrl.loadCloud(signedIn) }
  val full = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(ctrl::writeFull) }
  val ledger = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(ctrl::writeLedger) }
  val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { u -> ctrl.ownerExport(u, excel = false) } }
  val excel = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) {
    it?.let { u -> ctrl.ownerExport(u, excel = true) }
  }
  val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { it?.let(ctrl::readFile) }
  val launchers = BackupLaunchers(
    full = { full.launch("tohid-backup-${todayIso()}.zip") },
    ledger = { ledger.launch("tohid-shop-backup-${todayIso()}.json") },
    excel = { excel.launch("tohid-export-${todayIso()}.xlsx") },
    json = { json.launch("tohid-export-${todayIso()}.json") },
    restore = { pick.launch("*/*") },
  )
  return ctrl to launchers
}
